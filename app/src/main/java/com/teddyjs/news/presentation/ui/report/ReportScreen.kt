package com.teddyjs.news.presentation.ui.report

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.*
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.presentation.ui.admob.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {

    val userPlan: StateFlow<UserPlan> = repository.userPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPlan.FREE)

    private val _report = MutableStateFlow<WeeklyReport?>(null)
    val report: StateFlow<WeeklyReport?> = _report.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun adUsesFlow(feature: RewardedFeature) = repository.adUsesFlow(feature)

    fun onAdRewarded(feature: RewardedFeature) {
        viewModelScope.launch { repository.grantAdReward(feature) }
    }

    fun generateReport() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.consumeAdUse(RewardedFeature.WEEKLY_REPORT)
            _report.value = repository.generateWeeklyReport()
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onPaywallClick: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val userPlan by viewModel.userPlan.collectAsState()
    val report by viewModel.report.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val adUses by viewModel.adUsesFlow(RewardedFeature.WEEKLY_REPORT).collectAsState(initial = 0)
    val context = LocalContext.current
    val activity = context as Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("주간 리포트", fontWeight = FontWeight.Medium) },
                actions = {
                    if (userPlan == UserPlan.FREE) {
                        Surface(
                            modifier = Modifier.padding(end = 12.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (adUses > 0) Green50 else Amber50,
                        ) {
                            Text(
                                if (adUses > 0) "이번 주 1회 남음" else "광고 1회",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 11.sp,
                                color = if (adUses > 0) Green400 else Amber400,
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Gemini가 이번 주를 분석하는 중...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
                report != null -> {
                    // 데이터 없을 때 별도 처리
                    if (report!!.topKeywords.isEmpty() && report!!.categoryDistribution.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.BarChart, null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                )
                                Text(
                                    "아직 분석할 데이터가 없어요",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "기사를 즐겨찾기하고\n검색을 해보면 분석이 시작돼요",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp,
                                )
                                // 다시 생성 버튼
                                TextButton(onClick = { viewModel.generateReport() }) {
                                    Text("다시 시도하기")
                                }
                            }
                        }
                    } else {
                        // 정상 데이터
                        ReportContent(report = report!!)
                        if (userPlan == UserPlan.FREE) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Amber50,
                            ) {
                                Text(
                                    "프리미엄에서는 매일 리포트를 받을 수 있어요",
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 12.sp,
                                    color = Amber400,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                            Button(
                                onClick = onPaywallClick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("프리미엄 업그레이드")
                            }
                        }
                    }
                }
                else -> {
                    // 리포트 미생성 상태
                    ReportEmptyState(
                        adUses = adUses,
                        userPlan = userPlan,
                        onGenerate = {
                            if (userPlan == UserPlan.PREMIUM || adUses > 0) {
                                viewModel.generateReport()
                            } else {
                                AdManager.showRewardedAd(
                                    activity = activity,
                                    onRewarded = {
                                        viewModel.onAdRewarded(RewardedFeature.WEEKLY_REPORT)
                                        viewModel.generateReport()
                                    },
                                    onDismissed = {},
                                    onFailed = { onPaywallClick() },
                                )
                            }
                        },
                        onPaywallClick = onPaywallClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportContent(report: WeeklyReport) {
    // 헤더
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.BarChart, contentDescription = null, tint = Blue400)
        Column {
            Text("이번 주 브리핑", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(report.weekLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }

    // 카테고리 분포
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("관심사 분포", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            CategoryDonut(report.categoryDistribution)
        }
    }

    // AI 취향 분석
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Blue50),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFB5D4F4)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = Blue400)
                Text("AI 취향 분석", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF0C447C))
            }
            Text(report.aiInsight, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF185FA5))
        }
    }

    // 상위 키워드
    if (report.topKeywords.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("이번 주 핵심 키워드", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                report.topKeywords.take(5).forEachIndexed { i, kw ->
                    Surface(shape = RoundedCornerShape(12.dp), color = if (i == 0) Blue50 else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "#$kw",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 12.sp,
                            color = if (i == 0) Blue400 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }

    // 다음 주 주목
    if (report.nextWeekWatch.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("다음 주 주목할 이슈", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                report.nextWeekWatch.forEach { issue ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(14.dp).padding(top = 2.dp), tint = Amber400)
                        Text(issue, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

// ── 관심사 분포 도넛 차트 ──────────────────────────────────
private fun catColor(key: String): Color = when {
    key.contains("STOCK") || key.contains("주식") -> Green400
    key.contains("POLIT") || key.contains("정치") || key.contains("경제") -> Blue400
    key.contains("GLOBAL") || key.contains("글로벌") -> Color(0xFFE24B4A)
    key.contains("SPORT") || key.contains("스포") -> Amber400
    else -> Gray400
}

private fun catLabel(key: String): String = when {
    key.contains("STOCK") -> "주식/투자"
    key.contains("POLIT") -> "정치/경제"
    key.contains("GLOBAL") -> "글로벌"
    key.contains("SPORT") -> "스포츠"
    else -> key
}

@Composable
fun CategoryDonut(distribution: Map<String, Int>) {
    val entries = distribution.entries.sortedByDescending { it.value }
    val total = entries.sumOf { it.value }.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Canvas(modifier = Modifier.size(116.dp)) {
            val strokeW = 24.dp.toPx()
            var startAngle = -90f
            entries.forEach { (k, v) ->
                val sweep = 360f * v / total
                drawArc(
                    color = catColor(k),
                    startAngle = startAngle,
                    sweepAngle = (sweep - 3f).coerceAtLeast(0f),
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    topLeft = Offset(strokeW / 2, strokeW / 2),
                    size = Size(size.width - strokeW, size.height - strokeW),
                )
                startAngle += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            entries.forEach { (k, v) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(catColor(k)))
                    Text(catLabel(k), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    Text("$v%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ReportEmptyState(adUses: Int, userPlan: UserPlan, onGenerate: () -> Unit, onPaywallClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("이번 주 리포트를 생성해보세요", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    "즐겨찾기한 기사를 분석해서\n나만의 뉴스 취향 리포트를 만들어드려요",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp,
                )
            }
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Amber50),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Amber400, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (userPlan == UserPlan.PREMIUM || adUses > 0) "리포트 생성하기"
                    else "광고 1회 보고 리포트 생성",
                    color = Amber400,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,        // 추가: 고정 폰트 크기
                    maxLines = 1,            // 추가: 한 줄 강제
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
