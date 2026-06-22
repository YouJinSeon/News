package com.teddyjs.news.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import timber.log.Timber
import java.util.Calendar

/**
 * 정기 브리핑 정시 알람 스케줄러.
 *
 * WorkManager 주기 작업은 OEM 절전(삼성·샤오미 등)과 Doze에서 실행이 미뤄져
 * 아침 8시 브리핑이 누락되는 문제가 있다. AlarmManager.setAndAllowWhileIdle 는
 * Doze를 관통해 거의 정시에 기기를 깨우며, setExactAndAllowWhileIdle 와 달리
 * USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM 권한이 필요 없어 Play 정책 리스크가 없다.
 * (브리핑이 몇 분 늦을 수 있으나 누락은 방지된다.)
 *
 * 실제 슬롯 판정·야간 가드·중복 방지는 [DailyBriefingWorker] 가 담당하므로,
 * 알람은 "다음 슬롯 시각에 깨워서 워커를 1회 돌리는" 역할만 한다.
 */
object BriefingAlarmScheduler {

    /** 정기 브리핑 시각(시). DailyBriefingWorker 의 슬롯 범위(7~9·11~13·18~20)와 정합. */
    private val SLOT_HOURS = intArrayOf(8, 12, 19)

    const val ACTION = "com.teddyjs.news.action.BRIEFING_ALARM"
    private const val REQUEST_CODE = 4801

    /** 다음 브리핑 슬롯 시각에 알람 1개를 예약한다(가장 가까운 8/12/19시). */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = nextTriggerMillis()
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
            Timber.d("브리핑 알람 예약: ${java.util.Date(triggerAt)}")
        } catch (e: Exception) {
            Timber.e(e, "브리핑 알람 예약 실패")
        }
    }

    /** 일일 알림을 끄거나 재예약 전 기존 알람을 취소한다. */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
        Timber.d("브리핑 알람 취소")
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BriefingAlarmReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 지금 이후 가장 가까운 슬롯 시각(밀리초). 오늘 슬롯이 모두 지났으면 내일 첫 슬롯. */
    private fun nextTriggerMillis(): Long {
        val now = System.currentTimeMillis()
        for (h in SLOT_HOURS) {
            val c = slotCalendar(h, dayOffset = 0)
            // 약간의 여유(1초)를 둬 경계 시각 재트리거 방지
            if (c.timeInMillis > now + 1_000L) return c.timeInMillis
        }
        return slotCalendar(SLOT_HOURS.first(), dayOffset = 1).timeInMillis
    }

    private fun slotCalendar(hour: Int, dayOffset: Int): Calendar =
        Calendar.getInstance().apply {
            if (dayOffset != 0) add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
