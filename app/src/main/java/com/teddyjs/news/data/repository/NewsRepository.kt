package com.teddyjs.news.data.repository

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.data.local.dao.ArticleDao
import com.teddyjs.news.data.local.entity.ArticleEntity
import com.teddyjs.news.data.remote.GeminiResult
import com.teddyjs.news.data.remote.GeminiService
import com.teddyjs.news.data.remote.RssParser
import com.teddyjs.news.domain.model.*
import com.teddyjs.news.service.NaverNewsService
import com.teddyjs.news.widget.NewsWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository @Inject constructor(
    private val articleDao: ArticleDao,
    private val naverNewsService: NaverNewsService,
    private val rssParser: RssParser,  // ← 다시 추가
    private val geminiService: GeminiService,
    private val userPrefs: UserPreferencesDataStore,
    @ApplicationContext private val context: Context,
) {
    val userPlan: Flow<UserPlan> = userPrefs.userPlan
    val subscribedCategories: Flow<List<NewsCategory>> = userPrefs.subscribedCategories

    fun getNewsFeed(categories: List<NewsCategory>): Flow<List<NewsArticle>> {
        val categoryNames = categories.map { it.name }
        return articleDao.getArticlesByCategories(categoryNames).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getBookmarks(): Flow<List<NewsArticle>> =
        articleDao.getBookmarkedArticles().map { it.map { e -> e.toDomain() } }

    suspend fun fetchAndRefreshFeed(categories: List<NewsCategory>) {
        val newArticles = mutableListOf<ArticleEntity>()

        categories.forEach { category ->
            runCatching {
                val naverArticles = naverNewsService.fetchCategory(category)
                val rssArticles = rssParser.fetchCategory(category)

                // 네이버 기사 먼저 Map으로
                val naverMap = naverArticles.associateBy {
                    it.title.replace(Regex("[^가-힣a-zA-Z0-9]"), "").take(20)
                }

                // RSS는 이미지 없는 네이버 기사 보완용으로만
                val rssForImage = rssArticles.mapNotNull { rssArticle ->
                    val key = rssArticle.title
                        .replace(Regex("[^가-힣a-zA-Z0-9]"), "").take(20)
                    val naverArticle = naverMap[key]
                    if (naverArticle != null && naverArticle.imageUrl == null && rssArticle.imageUrl != null) {
                        // 네이버 기사에 RSS 이미지 붙이기
                        naverArticle.copy(imageUrl = rssArticle.imageUrl)
                    } else null
                }

                // 네이버 기사 업데이트 (이미지 보완된 것 포함)
                val updatedNaverMap = naverMap.toMutableMap()
                rssForImage.forEach { article ->
                    val key = article.title
                        .replace(Regex("[^가-힣a-zA-Z0-9]"), "").take(20)
                    updatedNaverMap[key] = article
                }

                // RSS 전용 기사 (네이버에 없는 것)
                val rssOnly = rssArticles.filter { rssArticle ->
                    val key = rssArticle.title
                        .replace(Regex("[^가-힣a-zA-Z0-9]"), "").take(20)
                    !naverMap.containsKey(key)
                }

                val combined = (updatedNaverMap.values + rssOnly)
                    .sortedByDescending { it.publishedAt }

                newArticles.addAll(combined)
                Timber.d("${category.name}: 네이버 ${naverArticles.size}개 + RSS전용 ${rssOnly.size}개")
            }.onFailure { Timber.e(it, "fetch failed: ${category.name}") }
        }

        if (newArticles.isNotEmpty()) {
            val currentArticleId = userPrefs.getCurrentArticleId() ?: ""
            articleDao.deleteAllExceptBookmarked(currentArticleId)
            articleDao.upsertArticles(newArticles)
            Timber.d("총 ${newArticles.size}개 저장 완료")

            updateWidgetFromRepo()
        }
    }

    suspend fun getArticleById(id: String): NewsArticle? =
        articleDao.getArticleById(id)?.toDomain()

    suspend fun toggleBookmark(articleId: String) {
        val article = articleDao.getArticleById(articleId) ?: return
        articleDao.setBookmark(articleId, !article.isBookmarked)
    }
    suspend fun getAiSummary(article: NewsArticle): GeminiResult {
        // 이미 요약이 있으면 캐시 반환
        article.aiSummary?.let {
            return GeminiResult.Success(
                summary = it,
                investmentInsight = null,
                sentiment = "neutral",
                keywords = article.keywords,
            )
        }
        val result = geminiService.summarizeArticle(article.title, article.summary)
        if (result is GeminiResult.Success) {
            articleDao.updateAiSummary(
                id = article.id,
                summary = result.summary,
                keywords = result.keywords.joinToString(","),
            )
        }
        return result
    }

    suspend fun getTasteFeedKeywords(): List<String> {
        val bookmarks = articleDao.getBookmarkedArticles().first()

        // 클릭 기록 키워드도 가져오기
        val clickedKeywords = userPrefs.getClickedKeywords()

        if (bookmarks.isEmpty() && clickedKeywords.isEmpty()) return emptyList()

        // 즐겨찾기 제목 + 클릭 키워드 합산
        val bookmarkTitles = bookmarks.map { it.title }
        val clickedTitles = clickedKeywords.take(20) // 최근 20개

        val json = geminiService.analyzeTasteFeed(
            bookmarkedTitles = bookmarkTitles,
            clickedKeywords = clickedTitles, // ← 추가
        )

        return runCatching {
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("interestKeywords")
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }
    suspend fun generateWeeklyReport(): WeeklyReport {
        val bookmarks = articleDao.getBookmarkedArticles().first()
        val distribution = bookmarks
            .groupBy { it.category }
            .mapValues { (_, v) -> (v.size * 100 / bookmarks.size.coerceAtLeast(1)) }

        val json = geminiService.generateWeeklyReport(
            bookmarkedTitles = bookmarks.map { it.title },
            categoryDistribution = distribution,
        )

        return runCatching {
            val obj = JSONObject(json)
            val keywords = obj.getJSONArray("topKeywords").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            val nextWatch = obj.getJSONArray("nextWeekWatch").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            WeeklyReport(
                weekLabel = getWeekLabel(),
                categoryDistribution = distribution,
                topKeywords = keywords,
                aiInsight = obj.getString("aiInsight"),
                nextWeekWatch = nextWatch,
            )
        }.getOrElse {
            WeeklyReport(
                weekLabel = getWeekLabel(),
                categoryDistribution = distribution,
                topKeywords = emptyList(),
                aiInsight = "분석 중 오류가 발생했습니다.",
                nextWeekWatch = emptyList(),
            )
        }
    }

    suspend fun updateSubscribedCategories(categories: List<NewsCategory>) {
        userPrefs.updateSubscribedCategories(categories)
    }

    fun adUsesFlow(feature: com.teddyjs.news.domain.model.RewardedFeature) =
        userPrefs.adUsesFlow(feature)

    suspend fun grantAdReward(feature: com.teddyjs.news.domain.model.RewardedFeature) =
        userPrefs.grantAdReward(feature)

    suspend fun consumeAdUse(feature: com.teddyjs.news.domain.model.RewardedFeature) =
        userPrefs.consumeAdUse(feature)

    private fun getWeekLabel(): String {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val week = cal.get(java.util.Calendar.WEEK_OF_MONTH)
        return "${month}월 ${week}주차"
    }

    private fun ArticleEntity.toDomain() = NewsArticle(
        id = id,
        title = title,
        summary = summary,
        aiSummary = aiSummary,
        url = url,
        imageUrl = imageUrl,
        source = source,
        category = NewsCategory.valueOf(category),
        publishedAt = publishedAt,
        isBookmarked = isBookmarked,
        keywords = if (keywords.isBlank()) emptyList() else keywords.split(","),
        viewCount = viewCount,
    )

    suspend fun saveSearchResults(articles: List<NewsArticle>) {
        val entities = articles.map { article ->
            ArticleEntity(
                id = article.id,
                title = article.title,
                summary = article.summary,
                url = article.url,
                source = article.source,
                category = article.category.name,
                publishedAt = article.publishedAt,
                fetchedAt = System.currentTimeMillis(),
                viewCount = 0,
            )
        }
        articleDao.upsertArticles(entities)
    }

    suspend fun deleteOldArticles(cutoff: Long) {
        articleDao.deleteOldArticles(cutoff)
    }

    suspend fun getFollowedTopics(): List<String> {
        return userPrefs.followedTopics.first()
    }

    suspend fun getCustomBreakingKeywords(): List<String> {
        return userPrefs.customBreakingKeywords.first()
    }

    suspend fun incrementViewCount(articleId: String) {
        articleDao.incrementViewCount(articleId)
    }

    private suspend fun updateWidgetFromRepo() {
        runCatching {
            val subscribedCategories = userPrefs.subscribedCategories.first()
            val followedTopics = userPrefs.followedTopics.first()
            val now = System.currentTimeMillis()

            val articles = articleDao.getArticlesByCategories(
                subscribedCategories.map { it.name }
            ).first()
                .filter { subscribedCategories.map { c -> c.name }.contains(it.category) }
                .sortedWith(
                    compareByDescending { article ->
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

            // SharedPrefs 저장
            NewsWidget.updateWidget(context, articles)

            // Glance 위젯 강제 갱신
            val glanceIds = GlanceAppWidgetManager(context)
                .getGlanceIds(NewsWidget::class.java)
            glanceIds.forEach { glanceId ->
                NewsWidget().update(context, glanceId)
            }
            Timber.d("위젯 자동 업데이트 완료: ${articles.size}개")
        }.onFailure {
            Timber.e(it, "위젯 자동 업데이트 실패")
        }
    }
}
