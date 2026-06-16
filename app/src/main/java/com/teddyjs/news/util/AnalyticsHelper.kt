package com.teddyjs.news.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * 핵심 행동 측정 헬퍼.
 *
 * DAU·리텐션은 Firebase가 자동 수집하고, 여기서는 "어디서 이탈/전환하는지"를
 * 보기 위한 핵심 행동만 찍는다. (성장 의사결정의 기반)
 *
 * 사용: NewsApplication에서 init(context) 1회 → 이후 어디서든 AnalyticsHelper.log("이벤트명").
 */
object AnalyticsHelper {

    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        fa = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun log(event: String, params: Map<String, String>? = null) {
        val bundle = params?.let { p ->
            Bundle().apply { p.forEach { (k, v) -> putString(k, v) } }
        }
        fa?.logEvent(event, bundle)
    }

    // 핵심 이벤트 상수
    const val ARTICLE_READ = "article_read"
    const val SHARE_CLICKED = "share_clicked"
    const val INVITE_CLICKED = "invite_clicked"
    const val PAYWALL_VIEW = "paywall_view"
    const val SUBSCRIBE_START = "subscribe_start"
    const val SUBSCRIBE_TAP = "subscribe_tap"
    const val AI_SUMMARY_USED = "ai_summary_used"
    const val PERSPECTIVE_COMPARE_USED = "perspective_compare_used"
}
