package com.teddyjs.news.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.teddyjs.news.MainActivity
import com.teddyjs.news.R
import com.teddyjs.news.util.appLargeIcon
import com.teddyjs.news.worker.BreakingNewsWorker
import com.teddyjs.news.worker.DailyBriefingWorker
import com.teddyjs.news.worker.RssSyncWorker
import timber.log.Timber

/**
 * FCM 수신 서비스 — 서버(Cloud Functions)가 보낸 속보를 받아 알림으로 띄운다.
 *
 * 서버는 data 메시지로 보냄(앱이 백그라운드/종료 상태여도 onMessageReceived 호출되어
 * 채널·아이콘을 우리가 직접 제어 가능). payload 키: type, title, body, articleId
 */
class NewsMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        // 무음 신호 — 절전 중에도 깨워서: 피드 갱신 + 정기 브리핑 슬롯 체크 + 토픽 알림
        if (data["type"] == "sync") {
            val wm = WorkManager.getInstance(applicationContext)
            RssSyncWorker.runOnce(wm)        // 최신 뉴스 받아오기
            DailyBriefingWorker.runOnce(wm)  // 아침/점심/저녁 브리핑(슬롯당 1회)
            BreakingNewsWorker.runOnce(wm)   // 팔로우 토픽 알림
            Timber.d("FCM feed_sync 수신 → 갱신·브리핑·토픽 체크")
            return
        }

        val title = data["title"] ?: message.notification?.title ?: "🔴 속보"
        val body = data["body"] ?: message.notification?.body ?: ""
        val articleId = data["articleId"] ?: ""
        val type = data["type"] ?: "breaking"

        val channelId = if (type == "breaking") "breaking_news" else "daily_briefing"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (articleId.isNotBlank()) putExtra("articleId", articleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (articleId.ifBlank { title }).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appLargeIcon(this))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify((articleId.ifBlank { title }).hashCode(), notification)
        }.onFailure { Timber.w(it, "FCM 알림 표시 실패(권한?)") }
        Timber.d("FCM 속보 수신: $title")
    }

    override fun onNewToken(token: String) {
        // 토픽 기반 발송이라 토큰 저장은 필수 아님. 구독만 보장.
        // breaking(속보) + feed_sync(정기 브리핑·피드 갱신 트리거) 둘 다 재구독.
        FirebaseMessaging.getInstance().subscribeToTopic("breaking")
        FirebaseMessaging.getInstance().subscribeToTopic("feed_sync")
        Timber.d("FCM 새 토큰 발급, breaking·feed_sync 토픽 재구독")
    }
}
