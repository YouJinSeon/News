package com.teddyjs.news.domain.model

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,           // RSS description
    val aiSummary: String? = null, // Gemini 요약
    val url: String,
    val imageUrl: String? = null,
    val source: String,
    val category: NewsCategory,
    val publishedAt: Long,
    val isBookmarked: Boolean = false,
    val keywords: List<String> = emptyList(),
    val viewCount: Int = 0,
)

enum class NewsCategory(val label: String, val rssFeeds: List<String>) {
    STOCK(
        label = "주식/투자",
        rssFeeds = listOf(
            "https://www.hankyung.com/feed/finance",     // 한경 — description 길어요
            "https://www.mk.co.kr/rss/40300001/",        // 매경
        )
    ),
    POLITICS_ECONOMY(
        label = "정치/경제",
        rssFeeds = listOf(
            "https://www.hankyung.com/feed/economy",     // 한경
            "https://www.khan.co.kr/rss/rssdata/economy_news.xml",  // 경향
            "https://www.hani.co.kr/rss/economy/",      // 한겨레 — description 길어요
            "https://rss.donga.com/economy.xml",
        )
    ),
    GLOBAL(
        label = "글로벌",
        rssFeeds = listOf(
            "https://feeds.bbci.co.uk/korean/rss.xml",  // BBC 코리아
            "https://www.yna.co.kr/rss/international.xml",  // 연합뉴스 국제
            "https://www.hani.co.kr/rss/international/",  // 한겨레 국제
            "https://rss.donga.com/international.xml",  // 동아 국제
        )
    ),
    SPORTS(
        label = "스포츠",
        rssFeeds = listOf(
            "https://www.yonhapnewstv.co.kr/category/news/sports/feed/",
            "https://www.yna.co.kr/rss/sports.xml",  // 연합뉴스 스포츠
            "https://www.hani.co.kr/rss/sports/",  // 한겨레 스포츠
        )
    );

    companion object {
        fun fromLabel(label: String) = entries.find { it.label == label } ?: STOCK
    }
}
