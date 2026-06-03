package com.teddyjs.news.util

import android.content.Context
import android.content.Intent
import com.teddyjs.news.domain.model.NewsArticle

/**
 * 공유/초대 헬퍼.
 *
 * 모든 공유 텍스트 끝에 앱 설치 링크를 붙여 "공유 = 신규 유입(바이럴 루프)"로 만든다.
 * (시장 분석 결론: 트래픽을 키워야 직접광고 수익이 가능 → 공유가 가장 싼 획득 채널)
 */
object ShareUtils {

    const val PACKAGE_NAME = "com.teddyjs.news"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$PACKAGE_NAME"
    private const val APP_NAME = "뉴스 브리핑"

    /** 기사 공유 — AI 요약(있으면)을 미리보기로 보여주고 설치 링크를 붙인다 */
    fun shareArticle(context: Context, article: NewsArticle) {
        val aiLine = (article.aiSummary ?: article.summary)
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { "\n\n🤖 AI 요약\n${it.take(200)}" }
            ?: ""

        val text = buildString {
            append("📰 ${article.title}")
            append(aiLine)
            append("\n\n🔗 기사 보기\n${article.url}")
            append("\n\n────────")
            append("\n$APP_NAME 앱에서 AI가 매일 골라주는 맞춤 뉴스를 받아보세요 👇")
            append("\n$PLAY_STORE_URL")
        }
        AnalyticsHelper.log(AnalyticsHelper.SHARE_CLICKED)
        send(context, text, subject = article.title)
    }

    /** 친구 초대 — 앱 자체를 공유. referralCode가 있으면 설치 추적용으로 링크에 실음 */
    fun inviteApp(context: Context, referralCode: String? = null) {
        val link = if (referralCode.isNullOrBlank()) PLAY_STORE_URL
        else "$PLAY_STORE_URL&referrer=$referralCode"
        val text = buildString {
            append("📰 $APP_NAME — AI 맞춤 뉴스 브리핑")
            append("\n\n관심사만 고르면 AI가 매일 핵심 뉴스를 골라 요약해줘요.")
            append("\n속보 알림, AI 심층 분석, 주간 리포트까지 무료로!")
            append("\n\n👇 지금 받아보기")
            append("\n$link")
        }
        AnalyticsHelper.log(AnalyticsHelper.INVITE_CLICKED)
        send(context, text, subject = "$APP_NAME 추천")
    }

    private fun send(context: Context, text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "공유하기"))
    }
}
