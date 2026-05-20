package com.teddyjs.news.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import com.teddyjs.news.MainActivity
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsArticle
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.widget.NewsWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class NewsFeedService : Service() {

    @Inject lateinit var repository: NewsRepository
    @Inject lateinit var userPrefs: UserPreferencesDataStore

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var feedJob: Job? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startFeedRefresh()
        startNotificationScheduler()

        // 앱 시작 시 위젯 즉시 업데이트
        serviceScope.launch {
            runCatching { updateWidget() }
        }
    }

    // ── 위젯 업데이트 (알고리즘 반영) ───────────────────────
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
                .take(3)
                .map { Triple(it.title, it.source, it.id) }  // ← id 추가

            NewsWidget.updateWidget(applicationContext, articles)
            NewsWidget().updateAll(applicationContext)
            Timber.d("위젯 업데이트: ${articles.size}개")
        }
    }

    // ── 10분마다 피드 갱신 ──────────────────────────────────
    private fun startFeedRefresh() {
        feedJob?.cancel()
        feedJob = serviceScope.launch {
            delay(10 * 60 * 1000L)
            while (true) {
                try {
                    val categories = repository.subscribedCategories.first()
                    repository.fetchAndRefreshFeed(categories)
                    updateWidget()
                    Timber.d("피드 갱신 완료")
                } catch (e: Exception) {
                    Timber.e(e, "피드 갱신 실패")
                }
                delay(10 * 60 * 1000L)
            }
        }
    }

    // ── 정기 알림 스케줄러 ──────────────────────────────────
    private fun startNotificationScheduler() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (true) {
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)

                if (minute == 0) {
                    when (hour) {
                        8 -> sendBriefing("☀️ 오늘의 아침 브리핑")
                        12 -> sendBriefing("🌤️ 점심 뉴스 브리핑")
                        19 -> sendBriefing("🌙 오늘의 저녁 브리핑")
                    }
                }

                checkBreakingNews()
                delay(60 * 1000L)
            }
        }
    }

    private suspend fun sendBriefing(title: String) {
        runCatching {
            if (isNightTime()) return

            val subscribedCategories = repository.subscribedCategories.first()
            val followedTopics = repository.getFollowedTopics()
            val now = System.currentTimeMillis()

            // 알고리즘 적용된 기사 선택
            val articles = repository.getNewsFeed(
                NewsCategory.entries.toList()
            ).first()
                .filter { subscribedCategories.contains(it.category) }
                .sortedWith(
                    compareByDescending { article: NewsArticle ->
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
                .take(3)

            if (articles.isEmpty()) return

            // 위젯도 함께 업데이트
            updateWidget()

            val firstArticleId = articles.firstOrNull()?.id ?: ""

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("articleId", firstArticleId)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, title.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationManager = getSystemService(NotificationManager::class.java)

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText("오늘의 주요 뉴스")

            articles.forEachIndexed { i, article ->
                inboxStyle.addLine("${i + 1}. ${article.title}")
            }

            val notification = NotificationCompat.Builder(this, "daily_briefing")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(articles.firstOrNull()?.title ?: "")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(title.hashCode(), notification)
            Timber.d("브리핑 알림 발송: $title")
        }
    }

    private suspend fun checkBreakingNews() {
        runCatching {
            if (isNightTime()) return

            val notifiedIds = userPrefs.getNotifiedArticleIds().toMutableSet()
            val customKeywords = repository.getCustomBreakingKeywords()
            val allKeywords = BREAKING_KEYWORDS + customKeywords

            val cutoff = System.currentTimeMillis() - 30 * 60 * 1000L
            val allArticles = repository.getNewsFeed(
                repository.subscribedCategories.first()
            ).first()

            // 속보 — 하루 최대 5개
            val breakingCount = notifiedIds.count { !it.startsWith("topic_") }
            if (breakingCount < 5 && allKeywords.isNotEmpty()) {
                allArticles.filter { article ->
                    article.publishedAt > cutoff &&
                            !notifiedIds.contains(article.id) &&
                            allKeywords.any { keyword ->
                                article.title.contains(keyword, ignoreCase = true)
                            }
                }.take(1).forEach { article ->
                    sendBreakingNotification(article)
                    userPrefs.addNotifiedArticleId(article.id)
                }
            }

            // 토픽 — 하루 최대 3개
            val topicCount = notifiedIds.count { it.startsWith("topic_") }
            if (topicCount < 3) {
                val followedTopics = repository.getFollowedTopics()
                if (followedTopics.isNotEmpty()) {
                    allArticles.filter { article ->
                        article.publishedAt > cutoff &&
                                !notifiedIds.contains("topic_${article.id}") &&
                                followedTopics.any { topic ->
                                    article.title.contains(topic, ignoreCase = true)
                                }
                    }.take(1).forEach { article ->
                        sendTopicNotification(article, followedTopics)
                        userPrefs.addNotifiedArticleId("topic_${article.id}")
                    }
                }
            }
        }
    }

    private fun sendBreakingNotification(article: NewsArticle) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("articleId", article.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, article.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "breaking_news")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔴 속보")
            .setContentText(article.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(article.title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(article.id.hashCode(), notification)
    }

    private fun sendTopicNotification(
        article: NewsArticle,
        followedTopics: List<String>,
    ) {
        val matchedTopic = followedTopics.first { topic ->
            article.title.contains(topic, ignoreCase = true)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("articleId", article.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, article.id.hashCode() + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "breaking_news")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 #$matchedTopic 새 소식")
            .setContentText(article.title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(article.id.hashCode() + 1, notification)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "feed_service")
            .setContentTitle("뉴스 브리핑")
            .setContentText("뉴스를 업데이트하고 있어요")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private suspend fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour in 22..23 || hour in 0..7
        if (!isNight) return false
        return !userPrefs.nightNotificationFlow.first()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 9999
        val BREAKING_KEYWORDS = emptyList<String>()

        fun start(context: Context) {
            val intent = Intent(context, NewsFeedService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NewsFeedService::class.java))
        }
    }
}