package com.teddyjs.news.data.remote

import android.util.Log
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.teddyjs.news.data.local.entity.ArticleEntity
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssParser @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),  // ← 이게 한경 형식
        SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),   // ← 일자 한자리
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
    )

    suspend fun fetchCategory(category: NewsCategory): List<ArticleEntity> =
        withContext(Dispatchers.IO) {
            val articles = category.rssFeeds.flatMap { url ->
                runCatching { fetchFeed(url, category) }.getOrElse {
                    Timber.e(it, "RSS fetch failed: $url")
                    emptyList()
                }
            }.distinctBy { it.id }

            // OG 이미지 병렬 파싱 (상위 10개)
            val (needImage, hasImage) = articles.partition { it.imageUrl == null }
            val withImages = needImage.take(10).map { article ->
                async { article.copy(imageUrl = fetchOgImage(article.url)) }
            }.awaitAll()

            (withImages + needImage.drop(10) + hasImage)
                .sortedByDescending { it.publishedAt }
        }

    private fun fetchFeed(url: String, category: NewsCategory): List<ArticleEntity> {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 TeddyNewsApp/1.0")
            .build()
        val body = okHttpClient.newCall(request).execute().use { it.body?.string() ?: return emptyList() }
        return parseRss(body, category)
    }

    private fun fetchOgImage(articleUrl: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(articleUrl)
                .header("User-Agent", "Mozilla/5.0 TeddyNewsApp/1.0")
                .build()
            val html = okHttpClient.newCall(request).execute().use {
                it.body?.string() ?: return null
            }
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

    private fun parseRss(xml: String, category: NewsCategory): List<ArticleEntity> {
        val articles = mutableListOf<ArticleEntity>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(xml.reader())

        var inItem = false
        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""
        var imageUrl: String? = null
        var sourceName = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName == "item" || tagName == "entry" -> {
                            inItem = true
                            title = ""; link = ""; description = ""; pubDate = ""; imageUrl = null
                        }
                        tagName == "title" && !inItem -> sourceName = parser.nextText().trim()
                        tagName == "title" && inItem -> title = stripHtml(parser.nextText().trim())
                        tagName == "link" && inItem -> {
                            link = if (parser.attributeCount > 0) {
                                parser.getAttributeValue(null, "href") ?: parser.nextText().trim()
                            } else parser.nextText().trim()
                        }
                        (tagName == "description" || tagName == "summary" || tagName == "content:encoded") && inItem -> {
                            val rawText = parser.nextText().trim()
                            // 이미지 URL 먼저 추출 (stripHtml 전에)
                            if (imageUrl == null) {
                                val imgRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                                imageUrl = imgRegex.find(rawText)?.groupValues?.get(1)
                            }
                            description = stripHtml(rawText)
                        }
                        tagName == "pubdate" || tagName == "published" || tagName == "updated" ->
                            if (inItem) pubDate = parser.nextText().trim()
                        tagName == "media:thumbnail" || tagName == "media:content" ->
                            imageUrl = parser.getAttributeValue(null, "url")
                        tagName == "enclosure" ->
                            if (parser.getAttributeValue(null, "type")?.startsWith("image") == true)
                                imageUrl = parser.getAttributeValue(null, "url")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        if (title.isNotBlank() && link.isNotBlank()) {
                            // pubDate 없으면 스킵
                            if (pubDate.isBlank()) {
                             } else if (isRelevantArticle(title, description, category)) {
                                articles.add(
                                    ArticleEntity(
                                        id = md5(link),
                                        title = title,
                                        summary = description.take(3000),
                                        url = link,
                                        imageUrl = imageUrl,
                                        source = sourceName.ifBlank { category.label },
                                        category = category.name,
                                        publishedAt = parseDate(pubDate),
                                        fetchedAt = System.currentTimeMillis(),
                                    )
                                )
                            }
                        }
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&apos;", "'")
            // 숫자 엔티티 변환 (&#8592; 같은 것들)
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null) code.toChar().toString() else ""
            }
            // 16진수 엔티티 변환 (&#x2192; 같은 것들)
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null) code.toChar().toString() else ""
            }
            .trim()

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        for (fmt in dateFormats) {
            runCatching {
                val result = fmt.parse(dateStr)!!.time
                return result
            }
        }
        Timber.e("parseDate failed: $dateStr")  // ← 파싱 실패 시
        return System.currentTimeMillis()  // ← 실패하면 현재 시간 반환이라 0분전 나옴
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isRelevantArticle(
        title: String,
        description: String,
        category: NewsCategory,
    ): Boolean {
        val text = (title + description).lowercase()

        return when (category) {
            NewsCategory.SPORTS -> {
                val sportsKeywords = listOf(
                    "축구", "야구", "농구", "배구", "골프", "테니스", "수영",
                    "올림픽", "월드컵", "선수", "경기", "리그", "감독", "코치",
                    "스포츠", "k리그", "kbo", "nba", "프로", "대표팀", "우승",
                    "결승", "시합", "훈련", "득점", "안타", "골", "승리"
                )
                val excludeKeywords = listOf("코스피", "주가", "환율", "금리", "증시")
                sportsKeywords.any { text.contains(it) } &&
                        excludeKeywords.none { text.contains(it) }
            }
            NewsCategory.GLOBAL -> {
                val globalKeywords = listOf(
                    "미국", "중국", "일본", "유럽", "러시아", "북한", "국제",
                    "외교", "글로벌", "해외", "세계", "트럼프", "바이든", "전쟁",
                    "유엔", "nato", "g7", "g20"
                )
                globalKeywords.any { text.contains(it) }
            }
            else -> true  // 다른 카테고리는 필터 없음
        }
    }
}
