package com.teddyjs.news.service

import com.teddyjs.news.BuildConfig
import com.teddyjs.news.data.local.entity.ArticleEntity
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.util.StringUtils.cleanHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaverNewsService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val clientId = BuildConfig.NAVER_CLIENT_ID
    private val clientSecret = BuildConfig.NAVER_CLIENT_SECRET

    // 카테고리별 검색 키워드
    private val categoryQueries = mapOf(
        NewsCategory.STOCK to listOf("주식", "코스피", "증시", "투자", "ETF", "펀드"),
        NewsCategory.POLITICS_ECONOMY to listOf("경제", "정치", "금리", "환율", "부동산"),
        NewsCategory.GLOBAL to listOf("미국", "중국", "글로벌", "국제", "외교"),
        NewsCategory.SPORTS to listOf("스포츠", "축구", "야구", "농구", "올림픽"),
    )

    suspend fun fetchCategory(category: NewsCategory): List<ArticleEntity> =
        withContext(Dispatchers.IO) {
            val queries = categoryQueries[category] ?: return@withContext emptyList()

            val articles = queries.flatMap { query ->
                runCatching { fetchNews(query, category) }.getOrElse { emptyList() }
            }.distinctBy {
                it.title.replace(Regex("[^가-힣a-zA-Z0-9]"), "").take(20)
            }.sortedByDescending { it.publishedAt }
                .take(30)

            // 상위 15개만 OG 이미지 파싱
            val (needImage, hasImage) = articles.partition { it.imageUrl == null }
            val withImages = needImage.take(15).map { article ->
                async {
                    val imageUrl = fetchOgImage(article.url)
                    if (imageUrl != null) article.copy(imageUrl = imageUrl) else article
                }
            }.awaitAll()

            (withImages + needImage.drop(15) + hasImage)
                .sortedByDescending { it.publishedAt }
        }

    private fun fetchOgImage(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 TeddyNewsApp/1.0")
                .build()
            val html = okHttpClient.newCall(request).execute()
                .use { it.body?.string() ?: return null }

            val ogRegex = Regex(
                """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            )
            val ogRegex2 = Regex(
                """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""",
                RegexOption.IGNORE_CASE
            )
            ogRegex.find(html)?.groupValues?.get(1)
                ?: ogRegex2.find(html)?.groupValues?.get(1)
        }.getOrNull()
    }

    private fun fetchNews(query: String, category: NewsCategory): List<ArticleEntity> {
        val url = "https://openapi.naver.com/v1/search/news.json" +
                "?query=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&display=20&sort=date"

        val request = Request.Builder()
            .url(url)
            .header("X-Naver-Client-Id", clientId)
            .header("X-Naver-Client-Secret", clientSecret)
            .build()

        val body = okHttpClient.newCall(request).execute()
            .use { it.body?.string() ?: return emptyList() }

        val json = JSONObject(body)
        val items = json.getJSONArray("items")

        if (json.has("errorCode")) {
            val errorCode = json.getString("errorCode")
            val errorMessage = json.getString("errorMessage")
            Timber.e("네이버 API 에러: $errorCode / $errorMessage")
            return emptyList()  // RSS로 fallback
        }

        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)

            val title = cleanHtml(item.getString("title"))
            val description = cleanHtml(item.getString("description")).ifBlank { title }

            val link = item.getString("link")
            val pubDate = item.getString("pubDate")
            val source = runCatching {
                item.getString("originallink")
                    .replace("https://", "").replace("http://", "")
                    .split("/").first()
            }.getOrDefault("네이버 뉴스")

            ArticleEntity(
                id = md5(link),
                title = title,
                summary = description.take(3000),
                url = link,
                imageUrl = null,
                source = source,
                category = category.name,
                publishedAt = parseDate(pubDate),
                fetchedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()

        val formats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
        )

        for (fmt in formats) {
            runCatching {
                val result = fmt.parse(dateStr)?.time
                if (result != null) {
                    return result
                }
            }
        }

        return System.currentTimeMillis()
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}