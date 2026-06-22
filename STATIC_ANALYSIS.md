# 정적 분석 리포트 (2026-06-15)

> 빌드 환경이 불가해 실제 lint/detekt 대신 코드 정독으로 분석함.
> 각 항목은 실제 파일·라인과 대조해 **검증된 것만** 기재. 과장된 자동분석 결과는 하단 "정정" 참고.

---

## 🔴 HIGH — 사용자에게 직접 피해

### 1. 유료 구독자가 일시 오류로 FREE로 강등됨
- **위치:** `util/BillingManager.kt:183-200` (`queryExistingPurchases`)
- **문제:** `result.purchasesList`만 보고 `result.billingResult.responseCode`를 확인하지 않음.
  Play 서비스 일시 오류·네트워크 문제로 조회가 실패하면 목록이 비고, release 빌드에서
  `setUserPlan(FREE)` + `setSubscribedProductId(null)`로 **결제한 사용자의 프리미엄이 박탈**됨.
- **수정:** `responseCode == BillingResponseCode.OK`일 때만 FREE 처리. 그 외(오류)에는 기존 플랜 유지.

```kotlin
val ok = result.billingResult.responseCode == BillingClient.BillingResponseCode.OK
val active = result.purchasesList.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
when {
    active != null -> { /* PREMIUM 설정 */ }
    ok && !BuildConfig.DEBUG -> { /* 진짜 구독 없음 → FREE */ }
    else -> Timber.w("구매 조회 실패(${result.billingResult.responseCode}) → 플랜 유지")
}
```

---

## 🟠 MAJOR — 기능 결함 / 정책 / 누수

### 2. BreakingNewsWorker 무한 재시도
- **위치:** `worker/BreakingNewsWorker.kt:77-80`
- **문제:** `getOrElse { Result.retry() }` — 영구 실패에도 매번 retry. `RssSyncWorker`와 달리
  `runAttemptCount` 상한이 없어 백오프로 무한 재시도 → 배터리 소모.
- **수정:** `if (runAttemptCount < 3) Result.retry() else Result.failure()`.

### 3. 피드 삭제→삽입이 원자적이지 않음 + null 폴백 위험
- **위치:** `data/repository/NewsRepository.kt:142-146`
- **문제:** `deleteAllExceptBookmarked(currentArticleId)` 직후 `upsertArticles` — 트랜잭션으로
  묶이지 않아 그 사이 UI Flow가 **빈 피드를 잠깐 방출(깜빡임)**. 또 `getCurrentArticleId() ?: ""`라
  현재 보던 기사가 없으면 `""` 기준으로 비북마크 전체 삭제.
- **수정:** 삭제+삽입을 Room `@Transaction` 함수로 묶고, currentArticleId가 null이면 except 절 생략.

### 4. 구독 가격 하드코딩 (CLAUDE.md 위반 + 페이월 정책 리스크)
- **위치:** `util/BillingManager.kt:35-36`
- **문제:** `_monthlyPrice = MutableStateFlow("₩6,900")`, `_yearlyPrice = "₩58,800"`.
  `queryProductPrices()`가 느리거나 실패하면 **실제 Play 가격과 다른 하드코딩 값**이 페이월에 노출됨.
  지금 진행 중인 "정기결제 정책 위반(약관 불명확)"과도 연결 — 표시 가격이 실제와 어긋나면 오도로 간주될 수 있음.
- **수정:** 기본값을 빈 문자열/플레이스홀더로 두고, 가격 로딩 전까지 로딩 표시. 통화 금액을 절대 하드코딩하지 않음.

### 5. AdManager(object 싱글톤)가 Activity를 보유 → 누수 가능
- **위치:** `presentation/ui/admob/AdManager.kt:17, 31-51, 53~`
- **문제:** 프로세스 수명 `object`가 `preload(activity)`로 Activity를 광고 로드/콜백에 넘기고
  `rewardedAd`/`interstitialAd`를 보관. 회전·종료된 Activity가 다음 로드까지 누수될 수 있음.
- **수정:** 로드는 `activity.applicationContext` 사용, `onDismissed`에서 광고/콜백 정리, 싱글톤에 Activity 비보유.

### 6. 알림 권한 미확인 상태로 notify() 호출
- **위치:** `worker/BreakingNewsWorker.kt`, `worker/DailyBriefingWorker.kt`, `fcm/NewsMessagingService.kt`
- **문제:** Android 13+에서 `POST_NOTIFICATIONS` 미허용 시 `notify()`가 조용히 무시됨. 권한 체크/예외 처리 없음.
- **수정:** `NotificationManagerCompat.areNotificationsEnabled()` 가드 + `notify()` try/catch + 누락 시 로깅.

---

## 🟡 MINOR — 견고성 / 정리

### 7. RssSyncWorker 주기 10분 → 15분으로 자동 클램프
- `worker/RssSyncWorker.kt` (periodic 최소 15분). 의도(10분)와 실제(15분)가 달라 혼동 소지. 15분으로 명시 권장.

### 8. parseDate 실패 시 현재시각 반환
- `data/remote/RssParser.kt`, `service/NaverNewsService.kt` — 날짜 파싱 실패 기사가 "방금"으로 최상단 노출.
  실패 시 `fetchedAt`나 `0L` 등 sentinel 사용 + 1회 로깅 권장.

### 9. 관점 비교 캐시 무제한 증가 (이번 추가 코드)
- `data/repository/NewsRepository.kt` `perspectiveCache` — 세션이 길면 기사 수만큼 누적.
  간단한 LRU 상한(예: 30개)로 제한 권장.

### 10. 빈 카테고리 리스트 → `IN ()` 빈 결과
- `data/local/dao/ArticleDao.kt` — 카테고리 비었을 때 빈 피드. 호출부에서 빈 리스트 단락 처리.

---

## ⚪ 정정 — 자동분석이 과장한 항목 (실제로는 문제 아님/경미)

- **launchBillingFlow "크래시" 주장:** 실제 코드(`BillingManager.kt:150-156`)는 이미 `responseCode`를
  확인하고 에러 상태를 세팅함. 크래시 아님. `activity.isFinishing` 체크만 더하면 충분(경미).
- **"메인스레드 디스크 읽기" 주장:** Room/DataStore의 `Flow.first()`는 내부적으로 백그라운드
  디스패처에서 동작해 메인스레드를 블로킹하지 않음. 대부분 사실 아님.
- **OkHttp 응답 누수:** 전부 `.use { }`로 닫고 있음 — 문제 없음.
- **MIGRATION_1_2:** `di/AppModule.kt`에 등록돼 있음. `fallbackToDestructiveMigration()`로 인한
  북마크 유실 위험은 **✅ 수정됨** — 디버그 빌드에만 파괴적 마이그레이션 허용(릴리스는 누락 시 fail-fast).
- **MainActivity 인앱 업데이트:** `registerListener`는 onCreate에서 1회만 등록, onDestroy에서 해제 →
  중복 등록·누수 없음(자동분석 #17은 과장, **문제 아님**).
- **BannerAdView 컨텍스트 / articleOpenCount:** 실사용상 무해해 의도적으로 미변경.

---

## ✅ 수정 적용됨 (2026-06-16)

| # | 항목 | 상태 | 파일 |
|---|------|------|------|
| 1 | 프리미엄 박탈 | ✅ responseCode==OK일 때만 FREE | `BillingManager.kt` |
| 2 | 워커 무한 재시도 | ✅ runAttemptCount<3 상한 | `BreakingNewsWorker.kt` |
| 3 | 피드 깜빡임 | ✅ `database.withTransaction`로 묶음 + null 폴백 제거 | `NewsRepository.kt` |
| 4 | 가격 하드코딩 | ✅ 기본값 빈 값, 로딩 전 구매 버튼 비활성 | `BillingManager.kt`, `PaywallScreen.kt` |
| 5 | 광고 Activity 누수 | ✅ applicationContext 로드 + 콜백 정리 + show try/catch | `AdManager.kt` |
| 6 | 알림 권한 미확인 | ✅ 모든 notify() runCatching 가드 | 워커·FCM·설정 |
| 7 | RssSync 주기 | ✅ 15분 명시 | `RssSyncWorker.kt` |
| 8 | parseDate 오염 | ✅ 실패 시 하루 전 처리 | `RssParser.kt`, `NaverNewsService.kt` |
| 9 | 관점 캐시 무제한 | ✅ LRU 30개 상한 | `NewsRepository.kt` |
| 10 | 빈 카테고리 IN() | ✅ 전체 조회로 단락 | `NewsRepository.kt` |

> 빌드 환경이 없어 컴파일 검증은 못 함. 파일 도구로 구조·참조·괄호 균형은 확인. Android Studio 빌드로 최종 확인 필요.

---

## 2차 분석 (2026-06-16) — 미검토 화면·최근 추가분

### 🔴 검증됨 (실제 코드 확인)

1. **알림 딥링크가 콜드 스타트에서 유실** — `MainActivity.kt:70-91`
   `_pendingArticleId`가 `replay=0` SharedFlow인데, `onCreate`에서 `setContent`(구독) 전에 emit해서
   값이 사라짐 → 앱이 꺼진 상태에서 푸시를 누르면 기사로 안 가고 홈으로 감. **수정:** `replay = 1`.
2. **홈 자동 새로고침 무한 루프** — `HomeViewModel.kt:298-306`
   `while(true){ refresh(); delay(10분) }`가 `viewModelScope`에서 돌아 **백그라운드에서도 10분마다 네트워크 호출**.
   RssSyncWorker(15분)+화면 진입 refresh와 중복 → 배터리/데이터 낭비. **수정:** 생명주기(STARTED) 기반으로.
3. **검색 디바운스·취소 없음** — `SearchScreen.kt:75-90`
   키 입력마다 전체 피드 로드 후 메모리 필터, 이전 작업 취소 없음 → 빠르게 타이핑 시 중복 쿼리·결과 꼬임.
   **수정:** `debounce(250ms)` + 직전 job 취소(또는 Room `LIKE` 쿼리).
4. **OkHttpClient 매 호출 생성** — `CommonComponents.kt:50,286,297`
   주가·날씨 호출마다 `OkHttpClient()` 새로 생성(주입된 공용 클라이언트 미사용) → 스레드풀 낭비.
   **수정:** 주입된 OkHttpClient 재사용.

### 🟠 리포트 지적 (확인 권장)

5. **FCM articleId 검증 없이 네비게이션** — 위협은 낮지만(서버 키 필요), `articleId`를 라우트로 쓰기 전
   로컬 기사 존재·문자셋 검증하면 안전. (방어적 보강)
6. **위젯 클릭 인텐트 비고유** — `NewsWidget.kt` 행마다 고유 `data` Uri 없으면 모든 행이 같은 기사 열 수 있음. (확인 권장)
7. **홈/위젯 `indexOf` O(n²)** — `HomeScreen.kt:312`, `NewsWidget.kt` — `itemsIndexed`로 교체 권장.
8. `context as Activity` 비가드 캐스트(홈/리포트/북마크), TTS 화면 전환 시 끊김 — 경미.

> 1차 때 지적된 것들(빌링·워커·피드·광고 등)은 이미 ✅ 수정 완료. 위 1~4는 신규 발견.

---

## 권장 수정 순서
1. (HIGH) #1 프리미엄 박탈 — 결제 신뢰성 직결
2. (MAJOR) #4 가격 하드코딩 — 현재 정책 심사와 연결
3. (MAJOR) #2 무한 재시도, #3 피드 깜빡임
4. (MAJOR) #5 광고 누수, #6 알림 권한 가드
5. (MINOR) 나머지
