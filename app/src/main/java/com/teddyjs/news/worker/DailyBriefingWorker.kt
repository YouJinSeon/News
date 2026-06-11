package com.teddyjs.news.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.teddyjs.news.MainActivity
import com.teddyjs.news.R
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.util.appLargeIcon
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsArticle
import com.teddyjs.news.domain.model.NewsCategory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyBriefingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NewsRepository,
    private val userPrefs: UserPreferencesDataStore,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val dailyEnabled = userPrefs.getDailyNotification()
            if (!dailyEnabled) return Result.success()

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            val nightEnabled = userPrefs.nightNotificationFlow.first()
            val isNightTime = hour >= 22 || hour < 8
            if (!nightEnabled && isNightTime) return Result.success()

            // slot: 시간대별 식별자. 같은 슬롯은 하루 1회만 발송 (7·8·9시 중복 방지)
            val (slot, title, emoji) = when (hour) {
                in 7..9   -> Triple("morning", "오늘의 아침 브리핑", "☀️")
                in 11..13 -> Triple("lunch", "점심 뉴스 브리핑", "🌤️")
                in 18..20 -> Triple("evening", "오늘의 저녁 브리핑", "🌙")
                else -> return Result.success()
            }

            // 이미 오늘 이 슬롯을 보냈으면 스킵
            if (userPrefs.isBriefingSlotSent(slot)) return Result.success()

            val articles = repository.getNewsFeed(
                NewsCategory.entries.toList()
            ).first().take(3)

            if (articles.isEmpty()) return Result.success()

            sendBriefingNotification(
                title = "$emoji $title",
                articles = articles,
            )
            userPrefs.markBriefingSlotSent(slot)

            Result.success()
        }.getOrElse {
            Timber.e(it, "브리핑 워커 실패")
            Result.retry()
        }
    }

    private fun sendBriefingNotification(
        title: String,
        articles: List<NewsArticle>,
    ) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("오늘의 주요 뉴스")

        articles.take(3).forEachIndexed { i, article ->
            inboxStyle.addLine("${i + 1}. ${article.title}")
        }

        val notification = NotificationCompat.Builder(applicationContext, "daily_briefing")
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appLargeIcon(applicationContext))
            .setContentTitle(title)
            .setContentText(articles.firstOrNull()?.title ?: "")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val WORK_NAME = "daily_briefing"

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1시간마다 실행 — 실행될 때 시간대 체크해서 아침/점심/저녁 판단
            val request = PeriodicWorkRequestBuilder<DailyBriefingWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                // 앱 실행 직후 즉시 브리핑 방지 (첫 실행을 15분 뒤로)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** FCM 신호 등으로 즉시 1회 브리핑 슬롯 체크 (도즈에도 안정적) */
        fun runOnce(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<DailyBriefingWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            workManager.enqueue(request)
        }
    }
}