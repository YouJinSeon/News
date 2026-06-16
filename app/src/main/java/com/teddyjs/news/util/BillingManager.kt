package com.teddyjs.news.util

import android.app.Activity
import com.android.billingclient.api.*
import com.teddyjs.news.BuildConfig
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.domain.model.UserPlan
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    private val userPrefs: UserPreferencesDataStore,
) {
    companion object {
        const val PRODUCT_PREMIUM_MONTHLY = "premiummonthly"
        const val PRODUCT_PREMIUM_YEARLY = "premiumyearly"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState

    private lateinit var billingClient: BillingClient
    private var reconnectJob: Job? = null
    private lateinit var billingClientStateListener: BillingClientStateListener

    // 가격은 절대 하드코딩하지 않음 — queryProductPrices()로 실시간 조회. 조회 전까지 빈 값(로딩 상태)
    private val _monthlyPrice = MutableStateFlow<String>("")
    private val _yearlyPrice = MutableStateFlow<String>("")
    private val _yearlyPricePerMonth = MutableStateFlow<String>("")
    val monthlyPrice = _monthlyPrice.asStateFlow()
    val yearlyPrice = _yearlyPrice.asStateFlow()
    /** 연간 구독의 월 환산가 (실시간 micros 기반 계산, 하드코딩 아님) */
    val yearlyPricePerMonth = _yearlyPricePerMonth.asStateFlow()

    fun init(activity: Activity) {
        val listener = object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.d("Billing connected")
                    scope.launch {
                        queryExistingPurchases()
                        queryProductPrices()
                    }
                } else {
                    Timber.e("Billing setup failed: ${result.responseCode}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Timber.w("Billing disconnected - 재연결 시도")
                reconnectJob?.cancel()
                reconnectJob = scope.launch {
                    delay(2000)
                    if (::billingClient.isInitialized) {
                        billingClient.startConnection(billingClientStateListener)
                    }
                }
            }
        }
        billingClientStateListener = listener

        billingClient = BillingClient.newBuilder(activity)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { handlePurchase(it) }
                } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    Timber.d("사용자가 결제를 취소했습니다.")
                    _billingState.value = BillingState.Idle
                } else {
                    Timber.e("결제 실패: ${result.responseCode}")
                    _billingState.value = BillingState.Error("결제에 실패했어요. 다시 시도해주세요.")
                }
            }
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(listener)
    }

    fun resetState() {
        _billingState.value = BillingState.Idle
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        AnalyticsHelper.log(AnalyticsHelper.SUBSCRIBE_START, mapOf("product" to productId))
        // 이전 에러 상태 초기화
        _billingState.value = BillingState.Idle

        scope.launch {
            Timber.d("구매 시도: $productId")
            Timber.d("BillingClient 연결 상태: ${billingClient.isReady}")

            if (!billingClient.isReady) {
                Timber.e("BillingClient 준비 안됨!")
                _billingState.value = BillingState.Error("결제 서비스 연결 중입니다. 잠시 후 다시 시도해주세요.")
                return@launch
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                ).build()

            val result = billingClient.queryProductDetails(params)
            Timber.d("상품 조회 결과: ${result.billingResult.responseCode}")
            Timber.d("상품 목록: ${result.productDetailsList?.size}개")

            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.e("상품 조회 실패: ${result.billingResult.responseCode}")
                _billingState.value = BillingState.Error("상품 정보를 불러올 수 없어요. 잠시 후 다시 시도해주세요.")
                return@launch
            }

            val productDetails = result.productDetailsList?.firstOrNull()
            if (productDetails == null) {
                Timber.e("productDetails null - 상품이 조회되지 않음")
                _billingState.value = BillingState.Error("상품을 찾을 수 없어요. 잠시 후 다시 시도해주세요.")
                return@launch
            }

            // 무료 체험(₩0) 단계가 있는 오퍼를 우선 선택 → 무료 체험이 실제로 적용됨.
            // (체험 자격이 없는 사용자는 그 오퍼가 안 내려와 기본 요금제로 자동 폴백 = 바로 결제)
            val offers = productDetails.subscriptionOfferDetails
            val offerToken = (
                offers?.firstOrNull { offer ->
                    offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
                } ?: offers?.firstOrNull()
            )?.offerToken
            if (offerToken == null) {
                Timber.e("offerToken null")
                _billingState.value = BillingState.Error("결제 정보를 불러올 수 없어요. 잠시 후 다시 시도해주세요.")
                return@launch
            }

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
                val billingResult = billingClient.launchBillingFlow(activity, flowParams)
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Timber.e("launchBillingFlow 실패: ${billingResult.responseCode}")
                    _billingState.value = BillingState.Error("결제창을 열 수 없어요. 잠시 후 다시 시도해주세요.")
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            scope.launch {
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val ackResult = billingClient.acknowledgePurchase(ackParams)
                    if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        Timber.e("Acknowledge 실패: ${ackResult.responseCode}")
                    }
                }
                userPrefs.setUserPlan(UserPlan.PREMIUM)
                userPrefs.setSubscribedProductId(purchase.products.firstOrNull())
                _billingState.value = BillingState.Purchased
                Timber.d("Purchase acknowledged: ${purchase.products}")
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Timber.d("구매 대기 중: ${purchase.products}")
            _billingState.value = BillingState.Error("결제가 대기 중이에요. Google Play에서 확인해주세요.")
        }
    }

    private suspend fun queryExistingPurchases() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val querySucceeded =
            result.billingResult.responseCode == BillingClient.BillingResponseCode.OK
        val activePurchase = result.purchasesList.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        when {
            activePurchase != null -> {
                userPrefs.setUserPlan(UserPlan.PREMIUM)
                userPrefs.setSubscribedProductId(activePurchase.products.firstOrNull())
                Timber.d("기존 구독 확인: ${activePurchase.products}")
            }
            // 조회가 '성공'했고 진짜 구독이 없을 때만 FREE 처리.
            // 일시적 조회 오류(네트워크/Play)에 유료 구독자를 FREE로 강등하는 버그 방지.
            querySucceeded && !BuildConfig.DEBUG -> {
                userPrefs.setUserPlan(UserPlan.FREE)
                userPrefs.setSubscribedProductId(null)
                Timber.d("구독 없음 → FREE")
            }
            else -> Timber.w("구매 조회 실패(${result.billingResult.responseCode}) → 기존 플랜 유지")
        }
    }

    private suspend fun queryProductPrices() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_PREMIUM_MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_PREMIUM_YEARLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                )
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        result.productDetailsList?.forEach { product ->
            // 무료체험(₩0) 단계를 건너뛰고 실제 유료 단계를 선택
            val paidPhase = product.subscriptionOfferDetails
                ?.flatMap { it.pricingPhases.pricingPhaseList }
                ?.firstOrNull { it.priceAmountMicros > 0 }
                ?: return@forEach
            val price = paidPhase.formattedPrice

            when (product.productId) {
                PRODUCT_PREMIUM_MONTHLY -> {
                    _monthlyPrice.value = price
                    Timber.d("월간 가격: $price")
                }
                PRODUCT_PREMIUM_YEARLY -> {
                    _yearlyPrice.value = price
                    // 월 환산가 = 연간가 / 12 (실시간 micros 기반)
                    _yearlyPricePerMonth.value = formatMicros(
                        paidPhase.priceAmountMicros / 12,
                        paidPhase.priceCurrencyCode,
                    )
                    Timber.d("연간 가격: $price (월환산 ${_yearlyPricePerMonth.value})")
                }
            }
        }
    }

    private fun formatMicros(micros: Long, currencyCode: String): String = runCatching {
        val amount = micros / 1_000_000.0
        val nf = java.text.NumberFormat.getCurrencyInstance()
        nf.currency = java.util.Currency.getInstance(currencyCode)
        if (currencyCode == "KRW") nf.maximumFractionDigits = 0
        nf.format(amount)
    }.getOrDefault("")

    /** 구매 복원 — 기기 변경/재설치 후 기존 구독을 되살림 */
    fun restorePurchases() {
        scope.launch {
            if (!billingClient.isReady) {
                _billingState.value = BillingState.Error("결제 서비스 연결 중이에요. 잠시 후 다시 시도해주세요.")
                return@launch
            }
            queryExistingPurchases()
            val plan = userPrefs.userPlan.first()
            if (plan == UserPlan.PREMIUM) {
                _billingState.value = BillingState.Purchased
            } else {
                _billingState.value = BillingState.Error("복원할 구독이 없어요.")
            }
        }
    }
}

sealed class BillingState {
    data object Idle : BillingState()
    data object Purchased : BillingState()
    data class Error(val message: String) : BillingState()
}