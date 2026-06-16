package com.teddyjs.news.presentation.ui.detail

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teddyjs.news.data.remote.PerspectiveResult
import com.teddyjs.news.domain.model.RewardedFeature
import com.teddyjs.news.domain.model.UserPlan
import com.teddyjs.news.BuildConfig
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.presentation.ui.admob.AdManager
import com.teddyjs.news.presentation.ui.admob.BannerAdView
import com.teddyjs.news.presentation.ui.common.rememberTtsController
import com.teddyjs.news.util.AnalyticsHelper
import com.teddyjs.news.util.ReviewHelper
import com.teddyjs.news.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    articleId: String,
    onBack: () -> Unit,
    onPaywallClick: () -> Unit,
    onWebViewClick: (url: String, title: String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val userPlan by viewModel.userPlan.collectAsState()
    val adUsesSummary by viewModel.adUsesFlow(RewardedFeature.AI_SUMMARY).collectAsState(initial = 0)
    val perspectiveUses by viewModel.perspectiveUses.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val followedTopics by viewModel.followedTopics.collectAsState()
    val tts = rememberTtsController()

    LaunchedEffect(articleId) {
        viewModel.loadArticle(articleId)
        AnalyticsHelper.log(AnalyticsHelper.ARTICLE_READ)
        // 기사 N회 열람마다 전면 광고(프리미엄 제외)
        AdManager.maybeShowInterstitialOnArticleOpen(activity, userPlan == UserPlan.PREMIUM)
        // 충분히 읽은 사용자에게 평점 요청(1회)
        viewModel.onArticleOpenedForReview()
    }

    // 평점 요청 이벤트 수신 → Play 인앱 리뷰 플로우
    LaunchedEffect(Unit) {
        viewModel.reviewRequest.collect { ReviewHelper.requestReview(activity) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    uiState.article?.let { article ->
                        IconButton(onClick = {
                            val body = uiState.aiSummary ?: uiState.quickSummary ?: article.summary
                            tts.toggle("${article.title}. $body")
                        }) {
                            Icon(
                                if (tts.isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                contentDescription = "듣기",
                                tint = if (tts.isSpeaking) Blue400
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { viewModel.toggleBookmark(article.id) }) {
                            Icon(
                                if (article.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "즐겨찾기",
                                tint = if (article.isBookmarked) Amber400
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            ShareUtils.shareArticle(context, article)
                        }) {
                            Icon(Icons.Filled.Share, "공유하기")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        val article = uiState.article

        if (article == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (uiState.isLoading) CircularProgressIndicator()
                else Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("기사를 불러올 수 없어요", fontSize = 14.sp)
                    TextButton(onClick = onBack) { Text("돌아가기") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 카테고리 + 출처 ─────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(4.dp), color = Green50) {
                    Text(
                        article.category.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        color = Green400,
                    )
                }
                Text(
                    article.source,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            // ── 제목 ────────────────────────────────────────
            Text(
                article.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
            )

            // ── 본문 (기사 먼저) ─────────────────────────────
            val summary = article.summary.trim()
            if (summary.isNotBlank()) {
                HighlightedText(text = summary, keywords = uiState.keywords, fontSize = 14.sp)
            }
            val looksTruncated = summary.trimEnd().endsWith("…") ||
                summary.trimEnd().endsWith("...")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Info, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    Text(
                        if (summary.length < 100 || looksTruncated)
                            "이 매체는 RSS로 요약만 제공해요. 전체 내용은 '전체 기사 읽기'로 보세요."
                        else
                            "RSS 기반 요약이에요. 전체 내용은 '전체 기사 읽기'를 이용해주세요.",
                        fontSize = 11.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.saveWebViewUrl(article.url)
                    onWebViewClick(article.id, article.title)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Article, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("전체 기사 읽기", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            HorizontalDivider()

            // ── AI 도구 탭 (요약 / 심층 분석 / 질문) ──────────
            var selectedTab by remember { mutableStateOf(0) }
            AiTabRow(selectedTab = selectedTab, onSelect = { selectedTab = it })

            when (selectedTab) {
                0 -> {
                    AiQuickSummarySection(
                        summary = uiState.quickSummary,
                        isLoading = uiState.isQuickSummaryLoading,
                        adUses = adUsesSummary,
                        userPlan = userPlan,
                        onRequestFree = {
                            AnalyticsHelper.log(AnalyticsHelper.AI_SUMMARY_USED)
                            viewModel.requestQuickSummary(article)
                        },
                        onWatchAd = {
                            AdManager.showRewardedAd(
                                activity = activity,
                                onRewarded = {
                                    AnalyticsHelper.log(AnalyticsHelper.AI_SUMMARY_USED)
                                    viewModel.onAdRewardedAndQuickSummary(article)
                                },
                                onDismissed = {},
                                onFailed = {},
                            )
                        },
                    )
                }

                // ── AI 심층 분석 ──
                1 -> {
                    AiDeepAnalysisSection(
                        aiSummary = uiState.aiSummary,
                        investmentInsight = uiState.investmentInsight,
                        keywords = uiState.keywords,
                        isLoading = uiState.isAiLoading,
                        followedTopics = followedTopics,
                        onFollowTopic = viewModel::toggleFollowTopic,
                        userPlan = userPlan,
                        onWatchAd = {
                            AdManager.showRewardedAd(
                                activity = activity,
                                onRewarded = { viewModel.onAdRewardedAndDeepAnalysis(article) },
                                onDismissed = {},
                                onFailed = { onPaywallClick() },
                            )
                        },
                        onPremiumRequest = { viewModel.onAdRewardedAndDeepAnalysis(article) },
                    )
                }

                // ── AI 질문 ──
                else -> {
                    AiQnaSection(
                        question = uiState.qnaQuestion,
                        answer = uiState.qnaAnswer,
                        isLoading = uiState.isQnaLoading,
                        onAsk = { q -> viewModel.askQuestion(article, q) },
                    )
                }
            }

            HorizontalDivider()

            // ── 관점 비교 (언론사별 시각) — 차별화 기능 ──────────
            PerspectiveCompareSection(
                result = uiState.perspective,
                isLoading = uiState.isPerspectiveLoading,
                errorMessage = uiState.perspectiveError,
                userPlan = userPlan,
                availableUses = perspectiveUses,
                onUseCredit = {
                    AnalyticsHelper.log(AnalyticsHelper.PERSPECTIVE_COMPARE_USED)
                    viewModel.requestPerspectiveCompare(article)
                },
                onWatchAd = {
                    AdManager.showRewardedAd(
                        activity = activity,
                        onRewarded = {
                            AnalyticsHelper.log(AnalyticsHelper.PERSPECTIVE_COMPARE_USED)
                            viewModel.onAdRewardedAndPerspective(article)
                        },
                        onDismissed = {},
                        onFailed = { onPaywallClick() },
                    )
                },
                onPremiumRequest = {
                    AnalyticsHelper.log(AnalyticsHelper.PERSPECTIVE_COMPARE_USED)
                    viewModel.requestPerspectiveCompare(article)
                },
                onOpenSource = { url ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                        )
                    }
                },
            )

            // 하단 배너 (FREE 전용, 디버그 제외)
            if (userPlan == UserPlan.FREE && !BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "스폰서",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                )
                BannerAdView(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── AI 탭 (요약 / 심층 분석 / 질문) ────────────────────────
@Composable
fun AiTabRow(selectedTab: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("AI 요약", "AI 심층 분석", "AI 질문")
    Row(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, label ->
            val selected = i == selectedTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(2.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                ) {}
            }
        }
    }
}

// ── AI 간략 요약 (무료 3회/일, 광고 보면 +3회) ─────────────
@Composable
fun AiQuickSummarySection(
    summary: String?,
    isLoading: Boolean,
    adUses: Int,
    userPlan: UserPlan,
    onRequestFree: () -> Unit,
    onWatchAd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = Blue400)
            Text("AI 간략 요약", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            // 남은 횟수 표시
            if (userPlan == UserPlan.FREE && summary == null) {
                Surface(shape = RoundedCornerShape(10.dp), color = Blue50) {
                    Text(
                        if (adUses > 0) "오늘 ${adUses}회 남음" else "오늘 0회",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        color = Blue400,
                    )
                }
            }
        }

        when {
            isLoading -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Blue50,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = Blue400,
                        )
                        Text("AI 요약 중...", fontSize = 12.sp, color = Blue400)
                    }
                }
            }

            summary != null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Blue50,
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Bolt, null,
                            modifier = Modifier.size(14.dp), tint = Blue400)
                        Text(
                            summary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0C447C),
                        )
                    }
                }
                Text(
                    "* 간략 요약은 RSS 기반이에요. 더 정확한 분석은 AI 심층 분석을, 전체 내용은 기사 읽기를 이용해주세요.",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }

            else -> {
                // 무료 횟수 남아있으면 바로 보기
                if (adUses > 0 || userPlan == UserPlan.PREMIUM) {
                    OutlinedButton(
                        onClick = onRequestFree,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null,
                            modifier = Modifier.size(14.dp), tint = Blue400)
                        Spacer(Modifier.width(6.dp))
                        Text("AI 요약 보기 (${adUses}회 남음)", fontSize = 13.sp, color = Blue400)
                    }
                } else {
                    // 횟수 소진 시 광고 보고 +3회
                    OutlinedButton(
                        onClick = onWatchAd,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null,
                            modifier = Modifier.size(14.dp), tint = Blue400)
                        Spacer(Modifier.width(6.dp))
                        Text("광고 보고 +3회 충전", fontSize = 13.sp, color = Blue400)
                    }
                }
            }
        }
    }
}

// ── 관점 비교 (언론사별 시각) — 차별화 기능 ────────────────────
@Composable
fun PerspectiveCompareSection(
    result: PerspectiveResult?,
    isLoading: Boolean,
    errorMessage: String?,
    userPlan: UserPlan,
    availableUses: Int,
    onUseCredit: () -> Unit,
    onWatchAd: () -> Unit,
    onPremiumRequest: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    // 기사마다 자동 호출하면 API 비용·노이즈가 커지므로, 누구나 '탭'으로 실행한다.
    // 무료: 남은 사용권이 있으면 광고 없이, 없으면 광고 1회. (성공 시에만 1회 차감)
    val isPremium = userPlan == UserPlan.PREMIUM
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("⚖️", fontSize = 15.sp)
            Text("관점 비교 · 언론사별 시각", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            if (result == null && !isLoading && !isPremium) {
                Surface(shape = RoundedCornerShape(10.dp), color = Amber50) {
                    Text(
                        if (availableUses > 0) "남은 ${availableUses}회" else "광고 1회",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        color = Amber400,
                    )
                }
            }
        }

        Text(
            if (result != null && result.sourceLinks.isNotEmpty())
                "${result.sourceLinks.size}개 매체의 보도를 비교했어요."
            else
                "같은 사안을 언론사들이 어떻게 다르게 보도하는지 AI가 비교해드려요.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        when {
            isLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "여러 매체를 비교하는 중...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            result != null -> {
                // 공통 사실
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("✓ 공통 사실", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = Green400)
                        Text(result.commonFacts, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }

                // 매체별 강조점
                Text("언론사별 강조점", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                result.outletViews.forEach { view ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp, MaterialTheme.colorScheme.outline,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(view.press, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = Blue400)
                            Text(view.angle, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }

                // 가장 큰 시각 차이
                if (result.divergence.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Amber50,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("🔍 가장 큰 시각 차이", fontSize = 11.sp,
                                fontWeight = FontWeight.Medium, color = Amber400)
                            Text(result.divergence, fontSize = 12.sp, lineHeight = 18.sp,
                                color = Color(0xFF8A5A00))
                        }
                    }
                }

                // 비교한 기사 원문 — 탭하면 해당 언론사 기사로 이동 (신뢰도·검증)
                if (result.sourceLinks.isNotEmpty()) {
                    Text("비교한 기사 원문", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    result.sourceLinks.forEach { link ->
                        Surface(
                            onClick = { onOpenSource(link.url) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(link.press, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        color = Blue400)
                                    Text(link.title, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2)
                                }
                                Icon(Icons.Filled.OpenInNew, "원문 열기",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                Text(
                    "AI가 여러 보도를 비교한 결과예요. 원문도 함께 확인해보세요.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }

            errorMessage != null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        errorMessage,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                // 재시도 — 프리미엄/남은 사용권이 있으면 광고 없이, 없으면 광고 1회
                TextButton(
                    onClick = when {
                        isPremium -> onPremiumRequest
                        availableUses > 0 -> onUseCredit
                        else -> onWatchAd
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp), tint = Amber400)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPremium || availableUses > 0) "다시 시도" else "광고 1회 보고 다시 시도",
                        color = Amber400, fontSize = 13.sp,
                    )
                }
            }

            else -> {
                Button(
                    onClick = when {
                        isPremium -> onPremiumRequest
                        availableUses > 0 -> onUseCredit
                        else -> onWatchAd
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber50),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("⚖️", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            isPremium -> "관점 비교 보기"
                            availableUses > 0 -> "관점 비교 보기 (남은 ${availableUses}회)"
                            else -> "광고 1회 보고 관점 비교"
                        },
                        color = Amber400, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ── AI 심층 분석 (무조건 광고 1회) ──────────────────────────
@Composable
fun AiDeepAnalysisSection(
    aiSummary: String?,
    investmentInsight: String?,
    keywords: List<String>,
    isLoading: Boolean,
    followedTopics: List<String>,
    onFollowTopic: (String) -> Unit,
    userPlan: UserPlan,
    onWatchAd: () -> Unit,
    onPremiumRequest: () -> Unit,
) {
    // 프리미엄이면 광고 없이 자동 실행
    LaunchedEffect(userPlan) {
        if (userPlan == UserPlan.PREMIUM && aiSummary == null && !isLoading) {
            onPremiumRequest()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.TrendingUp, null, modifier = Modifier.size(16.dp), tint = Green400)
            Text("AI 심층 분석", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            // 프리미엄이면 "광고 1회" 뱃지 숨김
            if (aiSummary == null && !isLoading && userPlan != UserPlan.PREMIUM) {
                Surface(shape = RoundedCornerShape(10.dp), color = Amber50) {
                    Text(
                        "광고 1회",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        color = Amber400,
                    )
                }
            }
        }

        when {
            isLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("분석 중...", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            aiSummary != null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("📋 요약", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(aiSummary, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                if (investmentInsight != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Green50,
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.TrendingUp, null,
                                modifier = Modifier.size(16.dp), tint = Green400)
                            Text(investmentInsight, fontSize = 12.sp, lineHeight = 18.sp,
                                color = Color(0xFF27500A))
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(modifier = Modifier.padding(10.dp)) {
                            Icon(Icons.Filled.Info, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("이 기사는 투자 관련 시사점이 없어요", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
                if (keywords.isNotEmpty()) {
                    Text("토픽 팔로우", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(keywords.take(5)) { keyword ->
                            TopicChip(
                                keyword = keyword,
                                isFollowed = followedTopics.contains(keyword),
                                onToggle = { onFollowTopic(keyword) },
                            )
                        }
                    }
                    Text("팔로우하면 관련 속보 알림을 받아요",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }

            else -> {
                Button(
                    onClick = if (userPlan == UserPlan.PREMIUM) onPremiumRequest else onWatchAd,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber50),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Amber400,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (userPlan == UserPlan.PREMIUM) "AI 심층 분석 보기"
                        else "광고 1회 보고 심층 분석 보기",
                        color = Amber400, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ── AI에게 질문하기 ─────────────────────────────────────────
@Composable
fun AiQnaSection(
    question: String?,
    answer: String?,
    isLoading: Boolean,
    onAsk: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val presets = listOf("쉽게 설명해줘", "왜 중요해?", "배경이 뭐야?", "다음은 어떻게 돼?")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = Blue400)
            Text("AI에게 질문하기", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
        Text(
            "이 기사에 대해 궁금한 걸 물어보세요",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { q ->
                SuggestionChip(
                    onClick = { onAsk(q) },
                    label = { Text(q, fontSize = 12.sp) },
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("직접 질문하기...", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (input.isNotBlank()) { onAsk(input.trim()); input = "" }
            }),
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { onAsk(input.trim()); input = "" }) {
                        Icon(Icons.Filled.Send, null, tint = Blue400)
                    }
                }
            },
            shape = RoundedCornerShape(10.dp),
        )

        when {
            isLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("AI가 답하는 중...", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            answer != null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Blue50,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (question != null) {
                            Text("Q. $question", fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, color = Blue400)
                        }
                        Text(answer, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

// ── 하이라이팅 텍스트 ────────────────────────────────────────
@Composable
fun HighlightedText(
    text: String,
    keywords: List<String>,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
) {
    if (keywords.isEmpty()) {
        Text(text = text, modifier = modifier, fontSize = fontSize, lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        return
    }

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        val lowerText = text.lowercase()
        val matches = mutableListOf<Pair<Int, Int>>()
        keywords.forEach { keyword ->
            var startIndex = 0
            while (true) {
                val idx = lowerText.indexOf(keyword.lowercase(), startIndex)
                if (idx == -1) break
                matches.add(Pair(idx, idx + keyword.length))
                startIndex = idx + 1
            }
        }
        val sorted = matches.sortedBy { it.first }
        val merged = mutableListOf<Pair<Int, Int>>()
        sorted.forEach { range ->
            if (merged.isEmpty() || merged.last().second < range.first) {
                merged.add(range)
            } else {
                merged[merged.size - 1] = Pair(
                    merged.last().first, maxOf(merged.last().second, range.second))
            }
        }
        merged.forEach { (start, end) ->
            append(text.substring(lastIndex, start))
            withStyle(SpanStyle(
                background = Color(0xFFFFEB3B).copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium,
                color = Color(0xFF795548),
            )) { append(text.substring(start, end)) }
            lastIndex = end
        }
        append(text.substring(lastIndex))
    }

    Text(text = annotatedString, modifier = modifier, fontSize = fontSize, lineHeight = 24.sp)
}

// ── 토픽 칩 ─────────────────────────────────────────────────
@Composable
fun TopicChip(keyword: String, isFollowed: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = isFollowed,
        onClick = onToggle,
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("#$keyword", fontSize = 12.sp)
                if (isFollowed) {
                    Icon(Icons.Filled.NotificationsActive, null,
                        modifier = Modifier.size(12.dp))
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Blue50,
            selectedLabelColor = Blue400,
        ),
        shape = RoundedCornerShape(20.dp),
    )
}