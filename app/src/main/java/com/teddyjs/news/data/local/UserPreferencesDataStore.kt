package com.teddyjs.news.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.domain.model.RewardedFeature
import com.teddyjs.news.domain.model.UserPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    companion object {
        val USER_PLAN = stringPreferencesKey("user_plan")
        val SUBSCRIBED_CATEGORIES = stringSetPreferencesKey("subscribed_categories")

        // 광고 보상 횟수 (기능별)
        val AD_USES_AI_SUMMARY = intPreferencesKey("ad_uses_ai_summary")
        val AD_USES_TASTE_FEED = intPreferencesKey("ad_uses_taste_feed")
        val AD_USES_KEYWORD = intPreferencesKey("ad_uses_keyword")
        val AD_USES_WEEKLY_REPORT = intPreferencesKey("ad_uses_weekly_report")

        // 마지막 리셋 날짜
        val AD_RESET_DATE = longPreferencesKey("ad_reset_date")
        val WEEKLY_REPORT_RESET = longPreferencesKey("weekly_report_reset")

        val BOOKMARK_COUNT = intPreferencesKey("bookmark_count")
        val SEARCH_HISTORY = stringSetPreferencesKey("search_history")
        val CLICKED_ARTICLES = stringSetPreferencesKey("clicked_articles")
        val BREAKING_NOTIFICATION = booleanPreferencesKey("breaking_notification")
        val DAILY_NOTIFICATION = booleanPreferencesKey("daily_notification")
        val DARK_MODE = booleanPreferencesKey("dark_mode")

        val FOLLOWED_TOPICS = stringSetPreferencesKey("followed_topics")

        val CUSTOM_BREAKING_KEYWORDS = stringSetPreferencesKey("custom_breaking_keywords")
        val NOTIFIED_ARTICLE_IDS = stringSetPreferencesKey("notified_article_ids")
        val NOTIFIED_DATE = stringPreferencesKey("notified_date")

        val CURRENT_ARTICLE_ID = stringPreferencesKey("current_article_id")

        val NIGHT_NOTIFICATION = booleanPreferencesKey("night_notification")
        private val SUBSCRIBED_PRODUCT_ID = stringPreferencesKey("subscribed_product_id")

        private val LAST_BREAKING_TIME = longPreferencesKey("last_breaking_time")
        private val LAST_TOPIC_TIME = longPreferencesKey("last_topic_time")

        // 정기 브리핑 슬롯 중복 발송 방지 (아침/점심/저녁 각 1회)
        private val BRIEFING_SENT_SLOTS = stringSetPreferencesKey("briefing_sent_slots")
        private val BRIEFING_SENT_DATE = stringPreferencesKey("briefing_sent_date")

        // 초대 보상
        private val MY_REFERRAL_CODE = stringPreferencesKey("my_referral_code")
        private val REFERRAL_ATTRIBUTION_DONE = booleanPreferencesKey("referral_attribution_done")
        private val REFERRAL_CLAIMED_MILESTONES = intPreferencesKey("referral_claimed_milestones")
        // 초대 보상으로 받은 AI 사용권 (매일 리셋에 영향받지 않는 별도 풀)
        private val BONUS_AI_SUMMARY = intPreferencesKey("bonus_ai_summary")

        // 페이월 A/B 변형 (최초 1회 배정 후 고정)
        private val PAYWALL_VARIANT = stringPreferencesKey("paywall_variant")

        // 인앱 리뷰 요청
        private val REVIEW_ACTION_COUNT = intPreferencesKey("review_action_count")
        private val REVIEW_REQUESTED = booleanPreferencesKey("review_requested")
    }

    /**
     * 긍정 행동(기사 읽기 등) 카운트 증가. 임계값 도달 + 아직 미요청이면 true 반환(1회만).
     */
    suspend fun incrementReviewActionAndShouldAsk(threshold: Int = 5): Boolean {
        var shouldAsk = false
        dataStore.edit { prefs ->
            if (prefs[REVIEW_REQUESTED] == true) return@edit
            val count = (prefs[REVIEW_ACTION_COUNT] ?: 0) + 1
            prefs[REVIEW_ACTION_COUNT] = count
            if (count >= threshold) {
                prefs[REVIEW_REQUESTED] = true
                shouldAsk = true
            }
        }
        return shouldAsk
    }

    /** 페이월 A/B 변형 배정(50:50, 최초 1회 후 고정) */
    suspend fun getOrAssignPaywallVariant(): String {
        val existing = dataStore.data.first()[PAYWALL_VARIANT]
        if (!existing.isNullOrBlank()) return existing
        val v = if ((0..1).random() == 0) "A" else "B"
        dataStore.edit { it[PAYWALL_VARIANT] = v }
        return v
    }

    /** 페이월 변형 강제 설정(테스트용) */
    suspend fun setPaywallVariant(v: String) {
        dataStore.edit { it[PAYWALL_VARIANT] = v }
    }

    // ── 초대 보상 ──────────────────────────────────────────
    /** 내 초대 코드(최초 1회 생성, 이후 고정) */
    suspend fun getOrCreateReferralCode(): String {
        val existing = dataStore.data.first()[MY_REFERRAL_CODE]
        if (!existing.isNullOrBlank()) return existing
        val code = "u" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        dataStore.edit { it[MY_REFERRAL_CODE] = code }
        return code
    }

    suspend fun isAttributionDone(): Boolean =
        dataStore.data.first()[REFERRAL_ATTRIBUTION_DONE] ?: false

    suspend fun markAttributionDone() {
        dataStore.edit { it[REFERRAL_ATTRIBUTION_DONE] = true }
    }

    suspend fun getClaimedMilestones(): Int =
        dataStore.data.first()[REFERRAL_CLAIMED_MILESTONES] ?: 0

    suspend fun setClaimedMilestones(n: Int) {
        dataStore.edit { it[REFERRAL_CLAIMED_MILESTONES] = n }
    }

    /** 초대 보상 지급: 보너스 AI 사용권 충전 (매일 리셋 무관) */
    suspend fun grantReferralReward(aiUses: Int) {
        dataStore.edit { prefs ->
            prefs[BONUS_AI_SUMMARY] = (prefs[BONUS_AI_SUMMARY] ?: 0) + aiUses
        }
    }

    private fun todayString(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

    /** 오늘 해당 브리핑 슬롯(morning/lunch/evening)이 이미 발송됐는지 */
    suspend fun isBriefingSlotSent(slot: String): Boolean {
        val prefs = dataStore.data.first()
        val savedDate = prefs[BRIEFING_SENT_DATE] ?: ""
        if (savedDate != todayString()) return false
        return (prefs[BRIEFING_SENT_SLOTS] ?: emptySet()).contains(slot)
    }

    /** 브리핑 슬롯 발송 기록 (날짜 바뀌면 자동 초기화) */
    suspend fun markBriefingSlotSent(slot: String) {
        val today = todayString()
        dataStore.edit { prefs ->
            val savedDate = prefs[BRIEFING_SENT_DATE] ?: ""
            val current = if (savedDate == today) {
                (prefs[BRIEFING_SENT_SLOTS] ?: emptySet()).toMutableSet()
            } else {
                mutableSetOf()
            }
            current.add(slot)
            prefs[BRIEFING_SENT_SLOTS] = current
            prefs[BRIEFING_SENT_DATE] = today
        }
    }

    val followedTopics: Flow<List<String>> = dataStore.data.map {
        it[FOLLOWED_TOPICS]?.toList() ?: emptyList()
    }

    val userPlan: Flow<UserPlan> = dataStore.data.map { prefs ->
        UserPlan.valueOf(prefs[USER_PLAN] ?: UserPlan.FREE.name)
    }

    val subscribedCategories: Flow<List<NewsCategory>> = dataStore.data.map { prefs ->
        val saved = prefs[SUBSCRIBED_CATEGORIES]?.toList() ?: emptyList()
        saved.mapNotNull {
            runCatching { NewsCategory.valueOf(it) }.getOrNull()
        }.ifEmpty {
            listOf(NewsCategory.STOCK, NewsCategory.POLITICS_ECONOMY)
        }
    }

    val darkModeFlow: Flow<Boolean> = dataStore.data.map {
        it[DARK_MODE] ?: false
    }

    val customBreakingKeywords: Flow<List<String>> = dataStore.data.map {
        it[CUSTOM_BREAKING_KEYWORDS]?.toList() ?: emptyList()
    }

    val searchHistoryFlow: Flow<List<String>> = dataStore.data.map {
        it[SEARCH_HISTORY]?.toList() ?: emptyList()
    }

    val clickedKeywordsFlow: Flow<List<String>> = dataStore.data.map {
        it[CLICKED_ARTICLES]?.toList() ?: emptyList()
    }

    val nightNotificationFlow: Flow<Boolean> = dataStore.data.map {
        it[NIGHT_NOTIFICATION] ?: false
    }

    val subscribedProductIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[SUBSCRIBED_PRODUCT_ID]
    }

    fun adUsesFlow(feature: RewardedFeature): Flow<Int> = dataStore.data.map { prefs ->
        checkAndResetDailyIfNeeded(prefs)
        val base = prefs[feature.toKey()] ?: 0
        // AI 요약은 초대 보상 보너스 풀도 합산해서 표시
        val bonus = if (feature == RewardedFeature.AI_SUMMARY) (prefs[BONUS_AI_SUMMARY] ?: 0) else 0
        base + bonus
    }

    suspend fun consumeAdUse(feature: RewardedFeature): Boolean {
        var consumed = false
        dataStore.edit { prefs ->
            resetDailyIfNeeded(prefs)
            val current = prefs[feature.toKey()] ?: 0
            if (current > 0) {
                prefs[feature.toKey()] = current - 1
                consumed = true
            } else if (feature == RewardedFeature.AI_SUMMARY) {
                // 일일 사용권 소진 시 초대 보상 보너스에서 차감
                val bonus = prefs[BONUS_AI_SUMMARY] ?: 0
                if (bonus > 0) {
                    prefs[BONUS_AI_SUMMARY] = bonus - 1
                    consumed = true
                }
            }
        }
        return consumed
    }

    suspend fun grantAdReward(feature: RewardedFeature) {
        dataStore.edit { prefs ->
            val key = when (feature) {
                RewardedFeature.AI_SUMMARY -> AD_USES_AI_SUMMARY
                RewardedFeature.TASTE_FEED -> AD_USES_TASTE_FEED
                RewardedFeature.KEYWORD_EXTRACT -> AD_USES_KEYWORD
                RewardedFeature.WEEKLY_REPORT -> AD_USES_WEEKLY_REPORT
            }
            val current = prefs[key] ?: 0
            prefs[key] = current + feature.usesPerAd  // usesPerAd 만큼 충전
        }
    }

    suspend fun setUserPlan(plan: UserPlan) {
        dataStore.edit { it[USER_PLAN] = plan.name }
    }

    suspend fun updateSubscribedCategories(categories: List<NewsCategory>) {
        dataStore.edit { it[SUBSCRIBED_CATEGORIES] = categories.map { c -> c.name }.toSet() }
    }

    private fun checkAndResetDailyIfNeeded(prefs: Preferences): Boolean {
        val lastReset = prefs[AD_RESET_DATE] ?: 0L
        return !isSameDay(lastReset, System.currentTimeMillis())
    }

    private fun resetDailyIfNeeded(prefs: MutablePreferences) {
        val lastReset = prefs[AD_RESET_DATE] ?: 0L
        if (!isSameDay(lastReset, System.currentTimeMillis())) {
            prefs[AD_USES_AI_SUMMARY] = 0
            prefs[AD_USES_TASTE_FEED] = 0
            prefs[AD_USES_KEYWORD] = 0
            prefs[AD_RESET_DATE] = System.currentTimeMillis()
        }
        // 주간 리포트는 월요일 리셋
        val lastWeeklyReset = prefs[WEEKLY_REPORT_RESET] ?: 0L
        if (!isSameWeek(lastWeeklyReset, System.currentTimeMillis())) {
            prefs[AD_USES_WEEKLY_REPORT] = 0
            prefs[WEEKLY_REPORT_RESET] = System.currentTimeMillis()
        }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
                c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }

    private fun isSameWeek(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.WEEK_OF_YEAR) == c2.get(Calendar.WEEK_OF_YEAR) &&
                c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }

    private fun RewardedFeature.toKey() = when (this) {
        RewardedFeature.AI_SUMMARY -> AD_USES_AI_SUMMARY
        RewardedFeature.TASTE_FEED -> AD_USES_TASTE_FEED
        RewardedFeature.KEYWORD_EXTRACT -> AD_USES_KEYWORD
        RewardedFeature.WEEKLY_REPORT -> AD_USES_WEEKLY_REPORT
    }

    suspend fun saveSearchKeyword(keyword: String) {
        dataStore.edit { prefs ->
            val current = prefs[SEARCH_HISTORY]?.toMutableSet() ?: mutableSetOf()
            current.remove(keyword)  // 중복 제거
            current.add(keyword)
            val trimmed = if (current.size > 20) current.drop(current.size - 20) else current
            prefs[SEARCH_HISTORY] = trimmed.toSet()
        }
    }

    suspend fun getSearchHistory(): List<String> {
        return dataStore.data.first()[SEARCH_HISTORY]?.toList() ?: emptyList()
    }

    // 클릭한 기사 키워드 저장 (따옴표·문장부호 제거, 의미 있는 토큰만)
    suspend fun saveClickedKeywords(title: String) {
        val stopwords = setOf(
            "이런", "저런", "그런", "관련", "위해", "대한", "오늘", "내일",
            "있다", "한다", "했다", "된다", "면서", "에서", "으로",
        )
        // 한글/영문/숫자 덩어리만 추출 → "8만원?", "‘초격차", "\"이런" 같은 부호 섞임 방지
        val words = Regex("[가-힣A-Za-z0-9]+")
            .findAll(title)
            .map { it.value }
            .filter { w -> w.length >= 2 && w.any { it.isLetter() } && w !in stopwords }
            .take(3)
            .toList()
        if (words.isEmpty()) return
        dataStore.edit { prefs ->
            val current = (prefs[CLICKED_ARTICLES] ?: emptySet()).toMutableList()
            current.addAll(words)
            val trimmed = if (current.size > 50) current.drop(current.size - 50) else current
            prefs[CLICKED_ARTICLES] = trimmed.toSet()
        }
    }

    suspend fun getClickedKeywords(): List<String> {
        return dataStore.data.first()[CLICKED_ARTICLES]?.toList() ?: emptyList()
    }

    suspend fun getBreakingNotification() =
        dataStore.data.first()[BREAKING_NOTIFICATION] ?: true

    suspend fun setBreakingNotification(enabled: Boolean) {
        dataStore.edit { it[BREAKING_NOTIFICATION] = enabled }
    }

    suspend fun getDailyNotification() =
        dataStore.data.first()[DAILY_NOTIFICATION] ?: true

    suspend fun setDailyNotification(enabled: Boolean) {
        dataStore.edit { it[DAILY_NOTIFICATION] = enabled }
    }

    suspend fun getDarkMode() =
        dataStore.data.first()[DARK_MODE] ?: false

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[DARK_MODE] = enabled }
    }

    // 토픽 팔로우
    suspend fun followTopic(topic: String) {
        dataStore.edit { prefs ->
            val current = prefs[FOLLOWED_TOPICS]?.toMutableSet() ?: mutableSetOf()
            current.add(topic)
            prefs[FOLLOWED_TOPICS] = current
        }
    }

    // 토픽 언팔로우
    suspend fun unfollowTopic(topic: String) {
        dataStore.edit { prefs ->
            val current = prefs[FOLLOWED_TOPICS]?.toMutableSet() ?: mutableSetOf()
            current.remove(topic)
            prefs[FOLLOWED_TOPICS] = current
        }
    }

    suspend fun clearAlgorithmData() {
        dataStore.edit { prefs ->
            prefs.remove(SEARCH_HISTORY)
            prefs.remove(CLICKED_ARTICLES)
        }
    }

    suspend fun addBreakingKeyword(keyword: String) {
        dataStore.edit { prefs ->
            val current = prefs[CUSTOM_BREAKING_KEYWORDS]?.toMutableSet() ?: mutableSetOf()
            current.add(keyword)
            prefs[CUSTOM_BREAKING_KEYWORDS] = current
        }
    }

    suspend fun removeBreakingKeyword(keyword: String) {
        dataStore.edit { prefs ->
            val current = prefs[CUSTOM_BREAKING_KEYWORDS]?.toMutableSet() ?: mutableSetOf()
            current.remove(keyword)
            prefs[CUSTOM_BREAKING_KEYWORDS] = current
        }
    }

    suspend fun getNotifiedArticleIds(): Set<String> {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val savedDate = dataStore.data.first()[NOTIFIED_DATE] ?: ""

        // 날짜 바뀌면 초기화
        if (today != savedDate) {
            dataStore.edit { prefs ->
                prefs[NOTIFIED_ARTICLE_IDS] = emptySet()
                prefs[NOTIFIED_DATE] = today
                prefs[LAST_BREAKING_TIME] = 0L
                prefs[LAST_TOPIC_TIME] = 0L
            }
            return emptySet()
        }
        return dataStore.data.first()[NOTIFIED_ARTICLE_IDS] ?: emptySet()
    }

    suspend fun addNotifiedArticleId(id: String) {
        dataStore.edit { prefs ->
            val current = (prefs[NOTIFIED_ARTICLE_IDS] ?: emptySet()).toMutableList()
            current.add(id)
            val trimmed = if (current.size > 100) current.drop(current.size - 100) else current
            prefs[NOTIFIED_ARTICLE_IDS] = trimmed.toSet()
        }
    }

    // 현재 보고 있는 뉴스
    suspend fun setCurrentArticleId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(CURRENT_ARTICLE_ID)
            else prefs[CURRENT_ARTICLE_ID] = id
        }
    }

    suspend fun getCurrentArticleId(): String? {
        return dataStore.data.first()[CURRENT_ARTICLE_ID]
    }

    // 야간 알림
    suspend fun setNightNotification(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[NIGHT_NOTIFICATION] = enabled
        }
    }

    suspend fun setSubscribedProductId(productId: String?) {
        dataStore.edit {
            if (productId == null) it.remove(SUBSCRIBED_PRODUCT_ID)
            else it[SUBSCRIBED_PRODUCT_ID] = productId
        }
    }

    suspend fun getLastBreakingTime(): Long {
        return dataStore.data.first()[LAST_BREAKING_TIME] ?: 0L
    }

    suspend fun setLastBreakingTime() {
        dataStore.edit { it[LAST_BREAKING_TIME] = System.currentTimeMillis() }
    }

    suspend fun getLastTopicTime(): Long {
        return dataStore.data.first()[LAST_TOPIC_TIME] ?: 0L
    }

    suspend fun setLastTopicTime() {
        dataStore.edit { it[LAST_TOPIC_TIME] = System.currentTimeMillis() }
    }
}
