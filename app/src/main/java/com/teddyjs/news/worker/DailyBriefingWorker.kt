package com.teddyjs.news.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.teddyjs.news.MainActivity
import com.teddyjs.news.data.local.UserPreferencesDataStore
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

            val (title, emoji) = when (hour) {
                in 7..9   -> "오늘의 아침 브리핑" to "☀️"
                in 11..13 -> "점심 뉴스 브리핑" to "🌤️"
                in 18..20 -> "오늘의 저녁 브리핑" to "🌙"
                else -> return Result.success()
            }

            val articles = repository.getNewsFeed(
                NewsCategory.entries.toList()
            ).first().take(3)

            if (articles.isEmpty()) return Result.success()

            sendBriefingNotification(
                title = "$emoji $title",
                articles = articles,
            )

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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}