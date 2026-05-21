package com.teddyjs.news.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.teddyjs.news.MainActivity
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsCategory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class BreakingNewsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NewsRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val categories = repository.subscribedCategories.first()

            // 새 기사 fetch
            repository.fetchAndRefreshFeed(categories)

            // 최근 30분 내 기사 중 속보 키워드 포함된 것 찾기
            val cutoff = System.currentTimeMillis() - 30 * 60 * 1000L
            val allArticles = repository.getNewsFeed(
                NewsCategory.entries.toList()
            ).first()

            val breakingArticles = allArticles.filter { article ->
                article.publishedAt > cutoff &&
                        BREAKING_KEYWORDS.any { keyword ->
                            article.title.contains(keyword, ignoreCase = true)
                        }
            }

            breakingArticles.take(3).forEach { article ->
                sendBreakingNotification(article.title, article.summary)
            }

            Timber.d("속보 체크 완료: ${breakingArticles.size}개")
            Result.success()
        }.getOrElse {
            Timber.e(it, "속보 워커 실패")
            Result.retry()
        }
    }

    private fun sendBreakingNotification(title: String, summary: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "breaking_news")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔴 속보 · $title")
            .setContentText(summary.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.notify(title.hashCode(), notification)
    }

    companion object {
        const val WORK_NAME = "breaking_news_check"

        val BREAKING_KEYWORDS = listOf("속보", "긴급", "사망", "폭발", "지진", "사고", "붕괴", "테러")
        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BreakingNewsWorker>(
                repeatInterval = 5,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
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