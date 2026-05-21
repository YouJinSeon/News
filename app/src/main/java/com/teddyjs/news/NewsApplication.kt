package com.teddyjs.news

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.teddyjs.news.service.NewsFeedService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NewsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Firebase 초기화 (google-services.json 필요)
        FirebaseApp.initializeApp(this)

        // Crashlytics 설정
        FirebaseCrashlytics.getInstance().apply {
            // DEBUG 빌드에서는 크래시 리포트 비활성화 (배포 빌드에서만 수집)
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }

        // Timber 초기화 - DEBUG는 LogCat, RELEASE는 Crashlytics로 전송
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }

        // 알림 채널
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 피드 서비스 채널 (상시)
            NotificationChannel("feed_service", "뉴스 업데이트", NotificationManager.IMPORTANCE_MIN)
                .apply {
                    description = "백그라운드 뉴스 업데이트"
                    setShowBadge(false)  // 앱 아이콘 뱃지 숨김
                }
                .also { manager.createNotificationChannel(it) }

            // 속보 채널
            NotificationChannel("breaking_news", "속보 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) }
                .also { manager.createNotificationChannel(it) }

            // 정기 브리핑 채널
            NotificationChannel("daily_briefing", "일일 브리핑", NotificationManager.IMPORTANCE_DEFAULT)
                .also { manager.createNotificationChannel(it) }
        }

        // 서비스 시작
        NewsFeedService.start(this)

        // Timber 초기화
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // AdMob 초기화
        MobileAds.initialize(this) {
            Timber.d("AdMob initialized")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Timber Tree - RELEASE 빌드에서 Crashlytics로 로그/크래시 전송
     */
    class CrashlyticsTree : Timber.Tree() {
        private val crashlytics = FirebaseCrashlytics.getInstance()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // 로그 메시지를 Crashlytics 커스텀 키로 남김 (크래시 발생 시 함께 보임)
            crashlytics.log("$tag: $message")

            // Exception이 있으면 Crashlytics에 기록
            t?.let {
                crashlytics.recordException(it)
            }
        }
    }

}
