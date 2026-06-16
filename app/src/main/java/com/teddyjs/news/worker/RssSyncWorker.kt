package com.teddyjs.news.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.widget.NewsWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class RssSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NewsRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            // 오래된 기사 삭제
            val cutoff = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
            repository.deleteOldArticles(cutoff)

            val categories = repository.subscribedCategories.first()
            repository.fetchAndRefreshFeed(categories)

            // 위젯 업데이트
            updateWidget()

            Timber.d("RSS sync completed")
            Result.success()
        }.getOrElse {
            Timber.e(it, "RSS sync failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "rss_sync_periodic"

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // WorkManager 주기 최소값은 15분 — 10분은 자동으로 15분으로 클램프되므로 명시.
            val request = PeriodicWorkRequestBuilder<RssSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runOnce(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RssSyncWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueue(request)
        }
    }

    private suspend fun updateWidget() {
        runCatching {
            val subscribedCategories = repository.subscribedCategories.first()
            val followedTopics = repository.getFollowedTopics()
            val now = System.currentTimeMillis()

            val articles = repository.getNewsFeed(
                NewsCategory.entries.toList()
            ).first()
                .filter { subscribedCategories.contains(it.category) }
                .sortedWith(
                    compareByDescending { article: com.teddyjs.news.domain.model.NewsArticle ->
                        val recencyScore = when {
                            now - article.publishedAt < 60 * 60 * 1000L -> 50.0
                            now - article.publishedAt < 3 * 60 * 60 * 1000L -> 35.0
                            now - article.publishedAt < 6 * 60 * 60 * 1000L -> 20.0
                            else -> 5.0
                        }
                        val topicScore = followedTopics.count { topic ->
                            article.title.contains(topic, ignoreCase = true)
                        } * 10.0
                        recencyScore + topicScore
                    }
                )
                .take(5)
                .map { Triple(it.title, it.source, it.id) }

            // SharedPreferences 저장
            NewsWidget.updateWidget(applicationContext, articles)

            // Glance 위젯 강제 갱신
            val glanceIds = androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
                .getGlanceIds(NewsWidget::class.java)
            glanceIds.forEach { glanceId ->
                NewsWidget().update(applicationContext, glanceId)
            }
        }.onFailure {
            Timber.e(it, "위젯 업데이트 실패")
        }
    }
}
