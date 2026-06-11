package com.teddyjs.news.presentation.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.util.AnalyticsHelper
import com.teddyjs.news.util.BillingManager
import com.teddyjs.news.util.BillingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    billingManager: BillingManager,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as Activity
    val variant by viewModel.variant.collectAsState()

    val billingState by billingManager.billingState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val monthlyPrice by billingManager.monthlyPrice.collectAsState()
    val yearlyPrice by billingManager.yearlyPrice.collectAsState()
    val yearlyPerMonth by billingManager.yearlyPricePerMonth.collectAsState()

    // 화면 열릴 때 상태 초기화 (paywall_view 측정은 ViewModel에서 변형과 함께 기록)
    LaunchedEffect(Unit) {
        billingManager.resetState()
    }

    LaunchedEffect(billingState) {
        when (billingState) {
            is BillingState.Purchased -> {
                scope.launch {
                    snackbarHostState.showSnackbar("프리미엄 구독이 완료되었어요 🎉")
                }
                delay(1500)
                onBack()
            }
            is BillingState.Error -> {
                val msg = (billingState as BillingState.Error).message
                snackbarHostState.showSnackbar(msg)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, "닫기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
        // SnackbarHost 추가
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 헤더
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Amber400,
                )
                Text("더 스마트하게 읽기", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(
                    "광고 없이 모든 기능을 무제한으로",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            // A/B 변형 B: 긴급/안심 배너 (전환율 비교용)
            if (variant == "B") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Amber50,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🔥", fontSize = 18.sp)
                        Text(
                            "지금 시작하면 7일 무료 — 부담 없이 체험하고 마음에 안 들면 언제든 해지",
                            fontSize = 12.sp,
                            color = Color(0xFF8A5A00),
                            lineHeight = 17.sp,
                        )
                    }
                }
            }

            // 월간 플랜
            PlanCard(
                name = "프리미엄",
                price = monthlyPrice,
                period = "/월",
                isPopular = true,
                features = listOf(
                    "뉴스 피드 무제한",
                    "AI 심층 분석 무제한",
                    "AI 취향 분석 피드 무제한",
                    "키워드 자동 추출 무제한",
                    "관심사 · 즐겨찾기 무제한",
                    "광고 없음",
                ),
                onSubscribe = {
                    AnalyticsHelper.log(
                        AnalyticsHelper.SUBSCRIBE_TAP,
                        mapOf("variant" to variant, "product" to "monthly"),
                    )
                    billingManager.launchPurchaseFlow(activity, BillingManager.PRODUCT_PREMIUM_MONTHLY)
                },
                ctaText = "7일 무료 체험 시작",
                subPriceText = "체험 후 $monthlyPrice/월 · 언제든 해지",
            )

            // 연간 플랜 — 월 환산가 + 절약 강조
            PlanCard(
                name = "연간 프리미엄",
                price = yearlyPrice,
                period = "/년",
                isPopular = false,
                badgeText = "29% 절약",
                features = listOf(
                    "프리미엄 모든 혜택 그대로",
                    "월간 대비 29% 저렴",
                ),
                onSubscribe = {
                    AnalyticsHelper.log(
                        AnalyticsHelper.SUBSCRIBE_TAP,
                        mapOf("variant" to variant, "product" to "yearly"),
                    )
                    billingManager.launchPurchaseFlow(activity, BillingManager.PRODUCT_PREMIUM_YEARLY)
                },
                ctaText = "연간 구독",
                subPriceText = yearlyPerMonth.takeIf { it.isNotBlank() }?.let { "월 ${it}꼴 · 가장 경제적" },
            )

            // 구매 복원 (기기 변경/재설치 대응)
            TextButton(onClick = { billingManager.restorePurchases() }) {
                Text(
                    "이미 구독 중이신가요? 구매 복원",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // 구독 약관 안내 (무료체험·자동결제 조건 명확히 — Play 정책 준수)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "구독 안내",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        "• 월간 프리미엄은 7일 무료 체험 후 ${monthlyPrice}/월이 자동 청구됩니다.\n" +
                        "• 무료 체험 기간 중 해지하면 요금이 청구되지 않습니다.\n" +
                        "• 연간 프리미엄은 ${yearlyPrice}/년이 즉시 결제됩니다(무료 체험 미포함).\n" +
                        "• 구독은 해지 전까지 기간이 끝날 때마다 자동 갱신됩니다.\n" +
                        "• 해지는 언제든 Google Play › 구독에서 할 수 있으며, 다음 결제일 이후 갱신이 중단됩니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun PlanCard(
    name: String,
    price: String,
    period: String,
    isPopular: Boolean,
    features: List<String>,
    onSubscribe: () -> Unit,
    ctaText: String,
    subPriceText: String? = null,
    badgeText: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isPopular)
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF185FA5))
        else
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (isPopular) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Blue50) {
                        Text(
                            "인기",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = Blue400,
                        )
                    }
                }
                if (badgeText != null) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Green50) {
                        Text(
                            badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            color = Green400,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(price, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text(
                    period,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            if (subPriceText != null) {
                Text(
                    subPriceText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.forEach { feat ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Green400,
                        )
                        Text(feat, fontSize = 13.sp)
                    }
                }
            }
            Button(
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = if (isPopular)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                else
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Text(ctaText, fontWeight = FontWeight.Medium)
            }
        }
    }
}