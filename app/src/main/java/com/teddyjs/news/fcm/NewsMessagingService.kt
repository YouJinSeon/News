package com.teddyjs.news.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.teddyjs.news.MainActivity
import com.teddyjs.news.R
import com.teddyjs.news.util.appLargeIcon
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

        getSystemService(NotificationManager::class.java)
            .notify((articleId.ifBlank { title }).hashCode(), notification)
        Timber.d("FCM 속보 수신: $title")
    }

    override fun onNewToken(token: String) {
        // 토픽 기반 발송이라 토큰 저장은 필수 아님. 구독만 보장.
        FirebaseMessaging.getInstance().subscribeToTopic("breaking")
        Timber.d("FCM 새 토큰 발급, breaking 토픽 재구독")
    }
}
