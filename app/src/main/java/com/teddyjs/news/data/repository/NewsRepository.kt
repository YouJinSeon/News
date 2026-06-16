package com.teddyjs.news.data.repository

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.room.withTransaction
import com.teddyjs.news.data.local.NewsDatabase
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
    private val database: NewsDatabase,
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
        // 카테고리가 비면 IN () 가 되어 빈 결과가 나오므로 전체 조회로 단락
        val articlesFlow = if (categoryNames.isEmpty())
            articleDao.getAllArticles()
        else
            articleDao.getArticlesByCategories(categoryNames)
        // 행동 기반 개인화: 읽은 기사 키워드 + 팔로우 토픽 + 최신성으로 가중 정렬.
        // (쓸수록 추천이 똑똑해지는 체감 → 리텐션 차별화)
        return combine(
            articlesFlow,
            userPrefs.clickedKeywordsFlow,
            userPrefs.followedTopics,
        ) { entities, clickedKeywords, followedTopics ->
            val now = System.currentTimeMillis()
            // 자주 클릭한 키워드일수록 가중치 ↑ (빈도 기반)
            val keywordWeight = clickedKeywords.groupingBy { it }.eachCount()
            entities.map { it.toDomain() }
                .sortedByDescending { article ->
                    personalizedScore(article, now, keywordWeight, followedTopics)
                }
        }
    }

    private fun personalizedScore(
        article: NewsArticle,
        now: Long,
        keywordWeight: Map<String, Int>,
        followedTopics: List<String>,
    ): Double {
        // 최신성: 기본 점수의 큰 축 (신선한 뉴스가 우선)
        val recencyScore = when {
            now - article.publishedAt < 60 * 60 * 1000L -> 50.0
            now - article.publishedAt < 3 * 60 * 60 * 1000L -> 35.0
            now - article.publishedAt < 6 * 60 * 60 * 1000L -> 20.0
            now - article.publishedAt < 24 * 60 * 60 * 1000L -> 10.0
            else -> 3.0
        }
        // 관심 키워드 매칭 (빈도 가중, 과도한 편향 방지 위해 상한)
        val interestScore = keywordWeight.entries.sumOf { (kw, cnt) ->
            if (kw.length >= 2 && article.title.contains(kw, ignoreCase = true)) {
                cnt.coerceAtMost(5) * 4.0
            } else 0.0
        }
        // 팔로우한 토픽 매칭 (가장 강한 시그널)
        val topicScore = followedTopics.count { topic ->
            article.title.contains(topic, ignoreCase = true)
        } * 12.0
        return recencyScore + interestScore + topicScore
    }

    fun getBookmarks(): Flow<List<NewsArticle>> =
        articleDao.getBookmarkedArticles().map { it.map { e -> e.toDomain() } }

    suspend fun fetchAndRefreshFeed(
        categories: List<NewsCategory>,
        replaceExisting: Boolean = true,
    ) {
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

                // 최종 중복 제거: RSS 소스끼리도 같은 기사가 여러 매체에서 들어오므로
                // 정규화 제목 키로 distinct 처리(네이버 우선). 너무 짧은/빈 제목은 제외.
                val combined = (updatedNaverMap.values + rssOnly)
                    .distinctBy { it.title.replace(Regex("[^가-힣a-zA-Z0-9]"), "").take(20) }
                    .filter { it.title.replace(Regex("[^가-힣a-zA-Z0-9]"), "").length >= 6 }
                    .sortedByDescending { it.publishedAt }

                newArticles.addAll(combined)
                Timber.d("${category.name}: 네이버 ${naverArticles.size}개 + RSS전용 ${rssOnly.size}개 → 중복제거 후 ${combined.size}개")
            }.onFailure { Timber.e(it, "fetch failed: ${category.name}") }
        }

        if (newArticles.isNotEmpty()) {
            // replaceExisting=true: 전체 갱신(오래된 것 정리). false: 다른 카테고리는 보존하고 추가만.
            if (replaceExisting) {
                val currentArticleId = userPrefs.getCurrentArticleId()
                // 삭제+삽입을 한 트랜잭션으로 묶어 그 사이 빈 피드가 방출되는 깜빡임을 방지
                database.withTransaction {
                    if (currentArticleId != null) {
                        articleDao.deleteAllExceptBookmarked(currentArticleId)
                    } else {
                        articleDao.deleteAllExceptBookmarked()
                    }
                    articleDao.upsertArticles(newArticles)
                }
            } else {
                articleDao.upsertArticles(newArticles)
            }
            Timber.d("총 ${newArticles.size}개 저장 완료 (replaceExisting=$replaceExisting)")

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

    /** 오늘의 나를 위한 브리핑 — 캐시 우선, 없으면 상위 헤드라인으로 생성 */
    suspend fun getDailyDigest(forceRefresh: Boolean = false): String? {
        if (!forceRefresh) {
            userPrefs.getCachedDigest()?.let { return it }
        }
        val categories = userPrefs.subscribedCategories.first()
        val headlines = getNewsFeed(categories).first().take(8).map { it.title }
        if (headlines.isEmpty()) return userPrefs.getCachedDigest()
        val digest = geminiService.generateDailyDigest(headlines) ?: return userPrefs.getCachedDigest()
        userPrefs.saveDigest(digest)
        return digest
    }

    suspend fun getCachedDigest(): String? = userPrefs.getCachedDigest()

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

    // 같은 기사를 다시 열 때 재호출 방지 (앱 세션 내 메모리 캐시).
    // LRU 30개로 상한을 둬 장시간 사용 시 메모리 누적 방지.
    private val perspectiveCache: MutableMap<String, com.teddyjs.news.data.remote.PerspectiveResult> =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, com.teddyjs.news.data.remote.PerspectiveResult>(16, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, com.teddyjs.news.data.remote.PerspectiveResult>,
                ): Boolean = size > 30
            }
        )

    /**
     * 관점 비교 — 동일 사안 기사를 여러 언론사에서 모아 Gemini로 시각 차이를 분석.
     * 매체가 2곳 미만이면(비교 불가) null 반환. 성공 결과는 세션 캐시에 저장.
     */
    suspend fun comparePerspectives(
        article: NewsArticle,
    ): com.teddyjs.news.data.remote.PerspectiveResult? {
        perspectiveCache[article.id]?.let { return it }

        val query = buildPerspectiveQuery(article.title)
        val related = naverNewsService.searchRelated(query = query, excludeUrl = article.url, maxOutlets = 4)
            // 동일 기사를 그대로 받아온 매체(제목 거의 같은 통신사 전재)는 비교 가치가 낮아 제외
            .filterNot { normalizeTitle(it.title) == normalizeTitle(article.title) }
        if (related.size < 2) return null

        val sources = related.map {
            com.teddyjs.news.data.remote.OutletSource(
                press = it.source,
                headline = it.title,
                excerpt = it.summary,
            )
        }
        val result = geminiService.comparePerspectives(topic = article.title, sources = sources)
            ?.copy(
                sourceLinks = related.map {
                    com.teddyjs.news.data.remote.SourceLink(
                        press = it.source, title = it.title, url = it.url,
                    )
                },
            )
        if (result != null) perspectiveCache[article.id] = result
        return result
    }

    /** 검색 정확도 향상 — [속보]·[단독] 머리표, 따옴표, 말줄임 제거 후 핵심만 */
    private fun buildPerspectiveQuery(title: String): String =
        title
            .replace(Regex("\\[[^\\]]*\\]"), " ")   // [속보] [단독] 등 대괄호 토큰 제거
            .replace(Regex("[\"'“”‘’]"), " ") // 따옴표 제거
            .replace("…", " ").replace("...", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(50)

    private fun normalizeTitle(t: String): String =
        t.replace(Regex("[^\\p{L}\\p{N}]"), "").lowercase()

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
