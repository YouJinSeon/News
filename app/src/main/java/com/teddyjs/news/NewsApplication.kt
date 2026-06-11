package com.teddyjs.news

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.teddyjs.news.worker.BreakingNewsWorker
import com.teddyjs.news.worker.DailyBriefingWorker
import com.teddyjs.news.worker.RssSyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NewsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Firebase 초기화
        FirebaseApp.initializeApp(this)

        // App Check — 우리 앱에서 온 요청만 Firestore/Functions가 받도록(남용 방지).
        // 디버그는 디버그 공급자, 릴리스는 Play Integrity. (콘솔에서 enforcement 켜기 전엔 비차단)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // FCM 토픽 구독: breaking(속보 알림) + feed_sync(백그라운드 피드 갱신 신호)
        FirebaseMessaging.getInstance().subscribeToTopic("breaking")
        FirebaseMessaging.getInstance().subscribeToTopic("feed_sync")

        // Analytics 핵심 이벤트 측정 초기화
        com.teddyjs.news.util.AnalyticsHelper.init(this)

        // Crashlytics 설정
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }

        // Timber 초기화 (중복 제거 - 한 번만)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }

        // 알림 채널
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            NotificationChannel("breaking_news", "속보 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) }
                .also { manager.createNotificationChannel(it) }

            NotificationChannel("daily_briefing", "일일 브리핑", NotificationManager.IMPORTANCE_DEFAULT)
                .also { manager.createNotificationChannel(it) }
        }

        // 포그라운드 서비스 제거: 피드 갱신·알림은 모두 WorkManager로 일원화
        // (앱이 꺼져 있어도 워커가 백그라운드 실행 → 동일 목적 달성, 배터리·심사 부담 ↓)

        // AdMob 초기화
        // 광고 등급을 앱 콘텐츠 등급(전체이용가)에 맞춰 PG 이하로 제한.
        // 아동 대상 앱이 아님을 명시(Families Policy 미적용) → 광고-등급 불일치 거절 방지.
        val adConfig = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
            .setTagForChildDirectedTreatment(
                RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
            )
            .setTagForUnderAgeOfConsent(
                RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
            )
            .build()
        MobileAds.setRequestConfiguration(adConfig)
        MobileAds.initialize(this) {
            Timber.d("AdMob initialized (maxAdContentRating=PG)")
        }

        // WorkManager 스케줄 등록 (앱 꺼져도 실행)
        val workManager = WorkManager.getInstance(this)
        RssSyncWorker.schedule(workManager)        // 피드 주기 갱신 (구 NewsFeedService 역할)
        BreakingNewsWorker.schedule(workManager)
        DailyBriefingWorker.schedule(workManager)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    class CrashlyticsTree : Timber.Tree() {
        private val crashlytics = FirebaseCrashlytics.getInstance()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            crashlytics.log("$tag: $message")
            t?.let { crashlytics.recordException(it) }
        }
    }
}