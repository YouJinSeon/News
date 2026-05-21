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
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.util.BillingManager
import com.teddyjs.news.util.BillingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    billingManager: BillingManager,
) {
    val context = LocalContext.current
    val activity = context as Activity

    val billingState by billingManager.billingState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "닫기") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(48.dp), tint = Amber400)
                Text("더 스마트하게 읽기", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(
                    "광고 없이 모든 기능을 무제한으로",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            // 플랜 카드
            PlanCard(
                name = "프리미엄",
                price = "₩6,900",
                period = "/월",
                isPopular = true,
                features = listOf(
                    "뉴스 피드 무제한",
                    "AI 심층 분석 무제한",
                    "AI 취향 분석 피드 무제한",
                    "키워드 자동 추출 무제한",
                    "주간 리포트 매일 발행",
                    "키워드 속보 알림",
                    "관심사 · 즐겨찾기 무제한",
                    "광고 없음",
                ),
                onSubscribe = {
                    billingManager.launchPurchaseFlow(activity, BillingManager.PRODUCT_PREMIUM_MONTHLY)
                },
                ctaText = "7일 무료 체험 시작",
            )

            PlanCard(
                name = "연간 프리미엄",
                price = "₩4,900",
                period = "/월 (연 ₩58,800)",
                isPopular = false,
                features = listOf(
                    "프리미엄 전체 포함",
                    "포트폴리오 연동",
                    "연간 브리핑 리포트 PDF",
                    "29% 절약",
                ),
                onSubscribe = {
                    billingManager.launchPurchaseFlow(activity, BillingManager.PRODUCT_PREMIUM_YEARLY)
                },
                ctaText = "연간 구독",
            )

            // 안내
            Text(
                "7일 무료 체험 후 자동 결제 · 언제든 해지 가능\nGoogle Play에서 구독 관리",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (isPopular) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Blue50) {
                        Text("인기", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = Blue400)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(price, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text(period, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 2.dp))
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.forEach { feat ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Green400)
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
                    ButtonDefaults.outlinedButtonColors().let {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    },
            ) {
                Text(ctaText, fontWeight = FontWeight.Medium)
            }
        }
    }
}
