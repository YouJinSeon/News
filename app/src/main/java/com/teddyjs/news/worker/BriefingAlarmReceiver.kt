package com.teddyjs.news.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import timber.log.Timber

/**
 * 정기 브리핑 알람 수신부.
 *
 * 1) 브리핑을 1회 실행한다(슬롯·야간·중복 판정은 [DailyBriefingWorker] 가 담당).
 * 2) 다음 슬롯 알람을 즉시 재예약한다 — setAndAllowWhileIdle 는 1회성이라
 *    매 발송 후 다시 걸어줘야 연쇄적으로 동작한다.
 */
class BriefingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("브리핑 알람 수신: ${intent.action}")
        runCatching {
            DailyBriefingWorker.runOnce(WorkManager.getInstance(context))
        }.onFailure { Timber.e(it, "브리핑 실행 실패") }

        // 다음 슬롯 재예약 (실패해도 부팅/앱 실행 시 재등록되므로 안전)
        BriefingAlarmScheduler.scheduleNext(context)
    }
}
