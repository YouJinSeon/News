package com.teddyjs.news.util

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import timber.log.Timber

/**
 * Google Play 인앱 리뷰(평점) 요청.
 *
 * 긍정 경험 직후 자연스럽게 평점 다이얼로그를 띄운다.
 * 실제 표시 여부·빈도는 Google이 내부적으로 제어하므로(쿼터),
 * 우리는 "좋은 타이밍"에 요청만 하면 된다.
 */
object ReviewHelper {
    fun requestReview(activity: Activity) {
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                    Timber.d("인앱 리뷰 플로우 요청")
                } else {
                    Timber.w(task.exception, "리뷰 플로우 요청 실패")
                }
            }
        }.onFailure { Timber.e(it, "리뷰 요청 오류") }
    }
}
