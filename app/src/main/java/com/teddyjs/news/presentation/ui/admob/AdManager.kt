package com.teddyjs.news.presentation.ui.admob

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.teddyjs.news.BuildConfig
import timber.log.Timber

object AdManager {

    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-1691492105013314/2967206587"
    private const val BANNER_AD_UNIT_ID   = "ca-app-pub-1691492105013314/8092523294"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun preload(activity: Activity) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        RewardedAd.load(
            activity,
            REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    Timber.d("Rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    Timber.e("Rewarded ad failed: ${error.message}")
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit,
        onFailed: () -> Unit,
    ) {
        if (BuildConfig.DEBUG) {
            onRewarded()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            onFailed()
            preload(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preload(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                onFailed()
            }
        }

        ad.show(activity) { _ ->
            onRewarded()
        }
    }

    fun isReady() = rewardedAd != null

    fun createBannerAd(context: Context): AdView {
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }
}

@Composable
fun BannerAdView(modifier: Modifier = Modifier.fillMaxWidth()) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdManager.createBannerAd(context)
        }
    )
}