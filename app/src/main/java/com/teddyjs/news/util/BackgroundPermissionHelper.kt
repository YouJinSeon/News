package com.teddyjs.news.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/**
 * 백그라운드 실행 허용 도우미.
 *
 * 삼성·샤오미 등은 OEM 절전/자동실행 차단 때문에 재부팅 후나 백그라운드에서
 * 알람·WorkManager가 죽는다. 표준 API로는 강제할 수 없어, 사용자를 적절한
 * 시스템 설정 화면으로 보내 직접 예외를 켜도록 돕는다.
 * (Play 정책상 권한이 필요 없는 '설정 화면 열기' 방식만 사용)
 */
object BackgroundPermissionHelper {

    /** 배터리 최적화 예외가 이미 적용돼 있는지 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 배터리 최적화 예외 설정 화면 열기 (앱 목록에서 사용자가 직접 예외 처리) */
    fun openBatteryOptimizationSettings(context: Context) {
        val tried = startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        if (!tried) openAppDetails(context)
    }

    /**
     * OEM 자동 실행(autostart) 관리 화면 열기 — 제조사별 액티비티를 차례로 시도.
     * (Android 11+ 패키지 가시성 제한으로 resolveActivity 가 막히므로, 직접 실행을 시도하고
     *  실패하면 다음 후보로 넘어간다. 모두 실패하면 앱 상세 설정으로 폴백.)
     */
    fun openAutoStartSettings(context: Context) {
        for (cn in AUTOSTART_COMPONENTS) {
            if (startSafely(context, Intent().setComponent(cn))) return
        }
        openAppDetails(context)
    }

    /** 앱 상세 설정 화면 (배터리·권한을 여기서도 조정 가능) */
    fun openAppDetails(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        startSafely(context, intent)
    }

    private fun startSafely(context: Context, intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrElse {
        Timber.w(it, "설정 화면 열기 실패: ${intent.component ?: intent.action}")
        false
    }

    // 제조사별 자동실행 관리 액티비티 (dontkillmyapp 기준)
    private val AUTOSTART_COMPONENTS = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
    )
}
