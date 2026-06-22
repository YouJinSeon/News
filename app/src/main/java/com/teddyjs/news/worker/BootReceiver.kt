package com.teddyjs.news.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * 재부팅·앱 업데이트 후 알람 복구.
 *
 * AlarmManager 알람은 기기 재부팅 시 모두 삭제되므로, BOOT_COMPLETED 를 받아
 * 정기 브리핑 알람을 다시 예약한다. (WorkManager 주기 작업은 자체 복구되지만
 * 정시 알람은 직접 재등록이 필요하다.)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Timber.d("부팅/업데이트 감지 → 브리핑 알람 복구")
                BriefingAlarmScheduler.scheduleNext(context)
            }
        }
    }
}
