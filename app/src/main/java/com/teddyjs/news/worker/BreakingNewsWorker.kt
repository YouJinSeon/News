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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class BreakingNewsWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NewsRepository,
    private val userPrefs: UserPreferencesDataStore,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            // 야간 알림 체크
            val nightEnabled = userPrefs.nightNotificationFlow.first()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isNightTime = hour >= 22 || hour < 8
            if (!nightEnabled && isNightTime) return Result.success()

            // 속보 설정 확인
            val breakingEnabled = userPrefs.getBreakingNotification()
            if (!breakingEnabled) return Result.success()

            val now = System.currentTimeMillis()
            val notifiedIds = userPrefs.getNotifiedArticleIds().toMutableSet()
            val cutoff = now - 60 * 60 * 1000L
            val categories = repository.subscribedCategories.first()
            val allArticles = repository.getNewsFeed(categories).first()

            // 속보(글로벌 키워드)는 서버(Cloud Functions) + FCM 'breaking' 토픽으로 일원화됨.
            // 앱 폴링 발송은 중복 방지를 위해 제거. 아래는 사용자가 직접 '팔로우한 토픽'(개인화) 알림만 처리.

            // 토픽 - 하루 2개 + 4시간 간격 (과도 알림으로 인한 이탈 방지)
            val topicCount = notifiedIds.count { it.startsWith("topic_") }
            val lastTopicTime = userPrefs.getLastTopicTime()
            val topicCooldown = 4 * 60 * 60 * 1000L

            if (topicCount < 2 && now - lastTopicTime > topicCooldown) {
                val followedTopics = repository.getFollowedTopics()
                if (followedTopics.isNotEmpty()) {
                    allArticles.filter { article ->
                        article.publishedAt > cutoff
                                && !notifiedIds.contains("topic_${article.id}")
                                && followedTopics.any { topic ->
                            article.title.contains(topic, ignoreCase = true)
                        }
                    }.take(1).forEach { article ->
                        sendTopicNotification(article, followedTopics)
                        notifiedIds.add("topic_${article.id}")
                        userPrefs.addNotifiedArticleId("topic_${article.id}")
                        userPrefs.setLastTopicTime()
                        Timber.d("토픽 알림 발송: ${article.title}")
                    }
                }
            }

            Result.success()
        }.getOrElse {
            Timber.e(it, "BreakingNewsWorker 실패")
            // 영구 실패에 무한 재시도하지 않도록 상한 (배터리 보호)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun sendBreakingNotification(article: NewsArticle) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("articleId", article.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, article.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, "breaking_news")
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appLargeIcon(context))
            .setContentTitle("🔴 속보")
            .setContentText(article.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(article.title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(article.id.hashCode(), notification)
        }.onFailure { Timber.w(it, "속보 알림 표시 실패(권한?)") }
    }

    private fun sendTopicNotification(article: NewsArticle, followedTopics: List<String>) {
        val matchedTopic = followedTopics.firstOrNull { topic ->
            article.title.contains(topic, ignoreCase = true)
        } ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("articleId", article.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, article.id.hashCode() + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, "breaking_news")
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appLargeIcon(context))
            .setContentTitle("🔔 #$matchedTopic 새 소식")
            .setContentText(article.title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(article.id.hashCode() + 1, notification)
        }.onFailure { Timber.w(it, "토픽 알림 표시 실패(권한?)") }
    }

    companion object {
        const val WORK_NAME = "breaking_news"
        val BREAKING_KEYWORDS = listOf(
            "속보", "긴급", "대규모 사망", "폭발 사고",
            "강진", "붕괴", "테러",
        )

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BreakingNewsWorker>(
                repeatInterval = 30,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                // 앱 실행 직후 즉시 푸시 방지 (첫 실행을 15분 뒤로)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** FCM 신호 등으로 즉시 1회 토픽 알림 체크 */
        fun runOnce(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<BreakingNewsWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            workManager.enqueue(request)
        }
    }
}