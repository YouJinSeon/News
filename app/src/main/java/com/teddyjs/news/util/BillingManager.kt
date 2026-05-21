package com.teddyjs.news.util

import android.app.Activity
import com.android.billingclient.api.*
import com.teddyjs.news.BuildConfig
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.domain.model.UserPlan
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    private val userPrefs: UserPreferencesDataStore,
) {
    companion object {
        const val PRODUCT_PREMIUM_MONTHLY = "premiummonthly"   // 월 6,900원
        const val PRODUCT_PREMIUM_YEARLY = "premiumyearly"     // 연 58,800원
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState

    private lateinit var billingClient: BillingClient
    private var reconnectJob: Job? = null
    private lateinit var billingClientStateListener: BillingClientStateListener

    fun init(activity: Activity) {
        val listener = object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.d("Billing connected")
                    scope.launch { queryExistingPurchases() }
                }
            }
            override fun onBillingServiceDisconnected() {
                Timber.w("Billing disconnected - 재연결 시도")
                reconnectJob?.cancel()
                reconnectJob = scope.launch {
                    delay(2000)
                    billingClient.startConnection(this@BillingManager.billingClientStateListener)
                }
            }
        }
        billingClientStateListener = listener

        billingClient = BillingClient.newBuilder(activity)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(listener)
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        scope.launch {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                ).build()

            Timber.d("구매 시도: $productId")
            Timber.d("BillingClient 연결 상태: ${billingClient.isReady}")

            if (!billingClient.isReady) {
                Timber.e("BillingClient 준비 안됨!")
                _billingState.value = BillingState.Error("결제 서비스 연결 중입니다. 잠시 후 다시 시도해주세요.")
                return@launch
            }

            val result = billingClient.queryProductDetails(params)
            Timber.d("상품 조회 결과: ${result.billingResult.responseCode}")
            Timber.d("상품 목록: ${result.productDetailsList?.size}개")
            Timber.d("상품 목록 내용: ${result.productDetailsList}")

            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _billingState.value = BillingState.Error("상품 조회 실패")
                return@launch
            }

            val productDetails = result.productDetailsList?.firstOrNull()
            if (productDetails == null) {
                Timber.e("productDetails null - 상품이 조회되지 않음")
                _billingState.value = BillingState.Error("상품을 찾을 수 없어요. 잠시 후 다시 시도해주세요.")
                return@launch
            }
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return@launch

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )
                ).build()

            withContext(Dispatchers.Main) {
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            scope.launch {
                // 구매 확인 (Acknowledge)
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams)
                }
                userPrefs.setUserPlan(UserPlan.PREMIUM)
                userPrefs.setSubscribedProductId(purchase.products.firstOrNull())
                _billingState.value = BillingState.Purchased
                Timber.d("Purchase acknowledged: ${purchase.products}")
            }
        }
    }

    private suspend fun queryExistingPurchases() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val activePurchase = result.purchasesList.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (activePurchase != null) {
            userPrefs.setUserPlan(UserPlan.PREMIUM)
            userPrefs.setSubscribedProductId(activePurchase.products.firstOrNull())
        } else if (!BuildConfig.DEBUG) {
            userPrefs.setUserPlan(UserPlan.FREE)
            userPrefs.setSubscribedProductId(null)
        }
    }
}

sealed class BillingState {
    data object Idle : BillingState()
    data object Purchased : BillingState()
    data class Error(val message: String) : BillingState()
}
