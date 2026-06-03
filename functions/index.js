/**
 * 뉴스 브리핑 - 속보 자동 발송 Cloud Function
 *
 * 동작: 15분마다 RSS를 가져와 '속보 키워드'가 포함된 새 기사를 찾고,
 *       Firestore로 중복 발송을 막은 뒤 FCM 'breaking' 토픽으로 전체 발송.
 *
 * 배포: firebase deploy --only functions
 * 요금: Blaze 플랜 필요(저트래픽이면 사실상 무료 범위).
 */
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");

initializeApp();
const db = getFirestore();

// 속보로 간주할 RSS 소스 (연합뉴스 등)
const FEEDS = [
  "https://www.yna.co.kr/rss/news.xml",
  "https://www.yna.co.kr/rss/international.xml",
];

// 속보 키워드 (앱의 BreakingNewsWorker.BREAKING_KEYWORDS와 일치 권장)
const BREAKING_KEYWORDS = [
  "속보", "긴급", "지진", "강진", "폭발", "붕괴", "테러", "사망",
];

// 하루 전체 발송 상한 (스팸 방지)
const DAILY_LIMIT = 6;
// 한 번 실행에 보낼 최대 건수 (동시에 우르르 오는 것 방지 → 15분 간격으로 띄엄띄엄)
const PER_RUN_LIMIT = 1;

// 초대: 한 기기가 카운트에 기여할 수 있는 최대 횟수
const MAX_PER_DEVICE = 5;

exports.checkBreakingNews = onSchedule(
  {
    schedule: "every 15 minutes",
    region: "asia-northeast3", // 서울
    timeoutSeconds: 120,
    memory: "256MiB",
  },
  async () => {
    const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
    const counterRef = db.collection("breaking_counter").doc(today);
    const counterSnap = await counterRef.get();
    const sentToday = counterSnap.exists ? counterSnap.data().count || 0 : 0;
    if (sentToday >= DAILY_LIMIT) {
      logger.info(`오늘 발송 상한(${DAILY_LIMIT}) 도달 — 스킵`);
      return;
    }

    const items = [];
    for (const url of FEEDS) {
      try {
        const res = await fetch(url);
        const xml = await res.text();
        items.push(...parseRss(xml));
      } catch (e) {
        logger.warn(`피드 실패: ${url}`, e);
      }
    }

    const now = Date.now();
    const cutoff = now - 30 * 60 * 1000; // 최근 30분 기사만

    // 키워드 + 최신성 필터
    const candidates = items.filter((it) => {
      const fresh = it.pubDate ? it.pubDate >= cutoff : true;
      const hit = BREAKING_KEYWORDS.some((k) => it.title.includes(k));
      return fresh && hit;
    });

    let remaining = DAILY_LIMIT - sentToday;
    let sentThisRun = 0;
    for (const item of candidates) {
      if (remaining <= 0 || sentThisRun >= PER_RUN_LIMIT) break;

      const id = hash(item.link || item.title);
      const ref = db.collection("sent_breaking").doc(id);
      const snap = await ref.get();
      if (snap.exists) continue; // 이미 보냄

      await getMessaging().send({
        topic: "breaking",
        data: {
          type: "breaking",
          title: "🔴 속보",
          body: item.title,
          articleId: "", // 서버는 앱 내부 기사 id를 모름 → 앱 홈으로 진입
        },
        android: { priority: "high" },
      });

      await ref.set({ title: item.title, sentAt: Date.now() });
      remaining -= 1;
      sentThisRun += 1;
      logger.info(`속보 발송: ${item.title}`);
    }

    const sentNow = DAILY_LIMIT - sentToday - remaining;
    if (sentNow > 0) {
      await counterRef.set({ count: sentToday + sentNow }, { merge: true });
    }
  }
);

/**
 * 초대 등록 (서버 검증).
 * 클라이언트가 직접 Firestore를 쓰지 않고 이 함수를 호출 → 기기당 1회만 카운트되도록 보장.
 * 요청: { inviterCode, deviceCode }
 */
exports.registerReferral = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    const inviterCode = String(request.data?.inviterCode || "");
    const deviceCode = String(request.data?.deviceCode || "");

    // 형식 검증
    const valid = (c) => /^u[a-z0-9]{10,15}$/.test(c);
    if (!valid(inviterCode) || !valid(deviceCode)) {
      throw new HttpsError("invalid-argument", "코드 형식 오류");
    }
    if (inviterCode === deviceCode) {
      throw new HttpsError("invalid-argument", "자기 자신 초대 불가");
    }

    const deviceRef = db.collection("referral_devices").doc(deviceCode);
    const inviterRef = db.collection("referrals").doc(inviterCode);

    // 기기당 최대 MAX_PER_DEVICE회까지만 카운트 (무한 부풀리기 방지, 재설치 등은 허용)
    const result = await db.runTransaction(async (tx) => {
      const deviceSnap = await tx.get(deviceRef);
      const calls = deviceSnap.exists ? (deviceSnap.data().calls || 0) : 0;
      if (calls >= MAX_PER_DEVICE) return { counted: false, reason: "limit" };

      tx.set(
        deviceRef,
        { inviter: inviterCode, lastAt: Date.now(), calls: calls + 1 },
        { merge: true }
      );
      tx.set(inviterRef, { count: FieldValue.increment(1) }, { merge: true });
      return { counted: true };
    });

    logger.info(`초대 등록: ${inviterCode} <- ${deviceCode} (${JSON.stringify(result)})`);
    return result;
  }
);

// ── 아주 단순한 RSS 파서 (의존성 없이) ─────────────────────────
function parseRss(xml) {
  const out = [];
  const itemRegex = /<item[\s\S]*?<\/item>/g;
  const items = xml.match(itemRegex) || [];
  for (const block of items) {
    const title = clean(extract(block, "title"));
    const link = clean(extract(block, "link"));
    const pubRaw = extract(block, "pubDate");
    const pubDate = pubRaw ? Date.parse(pubRaw) : null;
    if (title) out.push({ title, link, pubDate });
  }
  return out;
}

function extract(block, tag) {
  const m = block.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`));
  return m ? m[1] : "";
}

function clean(s) {
  return s
    .replace(/<!\[CDATA\[/g, "")
    .replace(/\]\]>/g, "")
    .replace(/<[^>]+>/g, "")
    .trim();
}

function hash(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) {
    h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
  }
  return "b" + (h >>> 0).toString(36);
}
