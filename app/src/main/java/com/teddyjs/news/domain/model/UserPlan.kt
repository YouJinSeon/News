package com.teddyjs.news.domain.model

enum class UserPlan { FREE, PREMIUM }

data class AdReward(
    val feature: RewardedFeature,
    val usesGranted: Int,
    val expiresAt: Long, // 자정 타임스탬프
)

enum class RewardedFeature(val label: String, val usesPerAd: Int) {
    AI_SUMMARY("AI 간략 요약", 3),
    TASTE_FEED("AI 취향 분석 피드", 1),
    KEYWORD_EXTRACT("키워드 자동 추출", 3),
    WEEKLY_REPORT("주간 패턴 리포트", 1),
}

data class StockQuote(
    val ticker: String,
    val price: String,
    val change: String,
    val isUp: Boolean,
)

data class WeeklyReport(
    val weekLabel: String,
    val categoryDistribution: Map<String, Int>, // 카테고리명 -> 퍼센트
    val topKeywords: List<String>,
    val aiInsight: String,
    val nextWeekWatch: List<String>,
)
