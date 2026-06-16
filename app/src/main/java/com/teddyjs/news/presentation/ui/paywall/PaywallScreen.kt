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

    // 가격은 BillingManager 실시간 조회값. 로딩 전(빈 값)에는 구매를 막아 잘못된 가격 노출 방지.
    val pricesReady = monthlyPrice.isNotBlank() && yearlyPrice.isNotBlank()
    val monthlyLabel = if (monthlyPrice.isBlank()) "불러오는 중…" else "$monthlyPrice/월"
    val yearlyLabel = if (yearlyPrice.isBlank()) "불러오는 중…" else "$yearlyPrice/년"

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

            // ── 플랜 선택 ──────────────────────────────────
            var selectedPlan by remember { mutableStateOf("monthly") }
            PlanRow(
                selected = selectedPlan == "monthly",
                title = "월간 프리미엄",
                price = monthlyLabel,
                sub = "7일 무료 후 자동 결제 · 언제든 해지",
                badge = "인기",
                onClick = { selectedPlan = "monthly" },
            )
            PlanRow(
                selected = selectedPlan == "yearly",
                title = "연간 프리미엄",
                price = yearlyLabel,
                sub = "14일 무료 후 자동 결제 · 29% 절약",
                badge = "29% 절약",
                onClick = { selectedPlan = "yearly" },
            )

            // ── 공통 혜택 ──────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    "AI 요약·심층 분석 무제한",
                    "관심사·즐겨찾기 무제한",
                    "광고 없는 깔끔한 이용",
                    "프리미엄 전용 리포트",
                ).forEach { benefit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp), tint = Green400)
                        Text(benefit, fontSize = 14.sp)
                    }
                }
            }

            // ── 단일 구독 버튼 ─────────────────────────────
            // 선택한 플랜의 실제 약관 값 (체험 일수·체험 후 청구 가격)
            val trialDays = if (selectedPlan == "monthly") 7 else 14
            val planPriceLabel = if (selectedPlan == "monthly") monthlyLabel else yearlyLabel
            Button(
                onClick = {
                    AnalyticsHelper.log(
                        AnalyticsHelper.SUBSCRIBE_TAP,
                        mapOf("variant" to variant, "product" to selectedPlan),
                    )
                    val product = if (selectedPlan == "monthly")
                        BillingManager.PRODUCT_PREMIUM_MONTHLY
                    else BillingManager.PRODUCT_PREMIUM_YEARLY
                    billingManager.launchPurchaseFlow(activity, product)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                // 가격 로딩 전에는 구매 비활성화 (잘못된 가격으로 구매 방지)
                enabled = pricesReady,
            ) {
                Text(
                    if (pricesReady) "${trialDays}일 무료 체험 시작" else "가격 불러오는 중…",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }

            // 결제 버튼 바로 아래에 핵심 약관을 "또렷한 박스"로 표기 (Play 정기결제 정책 준수)
            // ① 체험 종료 시점 ② 종료 후 정확한 가격 ③ 자동 갱신 ④ 해지 방법 — 한눈에 보이게
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                ),
            ) {
                Text(
                    "${trialDays}일간 무료로 이용한 뒤, 체험이 끝나면 $planPriceLabel 이(가) 자동 결제됩니다. " +
                    "체험 기간 중 해지하면 요금이 청구되지 않습니다. " +
                    "구독은 해지 전까지 매 기간 자동 갱신되며, Google Play › 구독에서 언제든지 해지할 수 있습니다.",
                    modifier = Modifier.padding(14.dp),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }

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
                        "구독 약관 안내",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                    Text(
                        "• 월간 프리미엄: 7일 무료 체험 후 $monthlyLabel 자동 청구\n" +
                        "• 연간 프리미엄: 14일 무료 체험 후 $yearlyLabel 자동 청구\n" +
                        "• 무료 체험 기간 중 해지하면 요금이 청구되지 않습니다.\n" +
                        "• 구독은 해지 전까지 기간이 끝날 때마다 자동 갱신됩니다.\n" +
                        "• 해지는 언제든 Google Play › 구독에서 할 수 있으며, 다음 결제일 이후 갱신이 중단됩니다.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}

// ── 선택형 플랜 행 ─────────────────────────────────────────
@Composable
fun PlanRow(
    selected: Boolean,
    title: String,
    price: String,
    sub: String,
    badge: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (badge != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Amber50) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp, color = Amber400,
                            )
                        }
                    }
                }
                Text(sub, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Text(price, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
