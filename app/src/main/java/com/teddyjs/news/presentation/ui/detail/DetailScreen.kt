package com.teddyjs.news.presentation.ui.detail

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teddyjs.news.domain.model.RewardedFeature
import com.teddyjs.news.domain.model.UserPlan
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.presentation.ui.admob.AdManager

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
    val context = LocalContext.current
    val activity = context as Activity
    val followedTopics by viewModel.followedTopics.collectAsState()

    LaunchedEffect(articleId) { viewModel.loadArticle(articleId) }

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
                        IconButton(onClick = { viewModel.toggleBookmark(article.id) }) {
                            Icon(
                                if (article.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "즐겨찾기",
                                tint = if (article.isBookmarked) Amber400
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}")
                            }
                            context.startActivity(Intent.createChooser(intent, "공유하기"))
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

            // ── AI 간략 요약 (무료 3회/일) ───────────────────
            AiQuickSummarySection(
                summary = uiState.quickSummary,          // ← quickSummary
                isLoading = uiState.isQuickSummaryLoading, // ← 별도 로딩
                adUses = adUsesSummary,
                userPlan = userPlan,
                onRequestFree = { viewModel.requestQuickSummary(article) },
                onWatchAd = {
                    AdManager.showRewardedAd(
                        activity = activity,
                        onRewarded = { viewModel.onAdRewardedAndQuickSummary(article) },
                        onDismissed = {},
                        onFailed = {},
                    )
                },
            )

            HorizontalDivider()

            // ── 본문 ─────────────────────────────────────────
            val summary = article.summary.trim()

            if (summary.isNotBlank()) {
                HighlightedText(
                    text = summary,
                    keywords = uiState.keywords,
                    fontSize = 14.sp,
                )
            }

            if (summary.length < 100) {
                // 짧을 때
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
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            "💡 요약 내용이 짧아요.\n전체 기사는 아래 '전체 기사 읽기'를,\n핵심 내용은 'AI 심층 분석'을 이용해보세요!",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                // 충분히 길 때
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
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            "* RSS 기반 요약이에요. 전체 내용은 기사 읽기를, 더 정확한 분석은 AI 심층 분석을 이용해주세요.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── AI 심층 분석 (무조건 광고 1회) ──────────────
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

            HorizontalDivider()

            // ── 전체 기사 읽기 ───────────────────────────────
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

            Spacer(Modifier.height(20.dp))
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