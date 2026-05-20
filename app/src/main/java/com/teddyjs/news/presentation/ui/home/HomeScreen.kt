package com.teddyjs.news.presentation.ui.home

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.teddyjs.news.BuildConfig
import com.teddyjs.news.domain.model.*
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.presentation.ui.admob.AdManager
import com.teddyjs.news.presentation.ui.admob.BannerAdView
import com.teddyjs.news.presentation.ui.common.WeatherStockRow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onArticleClick: (String) -> Unit,
    onPaywallClick: () -> Unit,
    onTasteFeedClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val userPlan by viewModel.userPlan.collectAsState()
    val categories by viewModel.subscribedCategories.collectAsState()
    val stocks by viewModel.stocks.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val weather by viewModel.weather.collectAsState()

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.fetchWeatherData(context)
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.savedScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.savedScrollOffset,
    )

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            viewModel.saveScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(Unit) {
        AdManager.preload(activity)
        // 위치 권한 확인
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            viewModel.fetchWeatherData(context)
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    //카테고리 변경
    LaunchedEffect(uiState.selectedCategory) {
        if (uiState.selectedCategory != null) {
            listState.animateScrollToItem(0)
        }
    }

    val unlockedMore by viewModel.unlockedMore.collectAsState()
    val unlockedCount by viewModel.unlockedCount.collectAsState()
    val displayArticles = when {
        userPlan == UserPlan.PREMIUM -> uiState.articles
        else -> uiState.articles.take(unlockedCount)
    }

    val isRefreshing by remember { derivedStateOf { uiState.isRefreshing } }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    )

    var showTasteFeedSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("오늘의 브리핑", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                            Text(
                                getTodayString(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    },
                    actions = {
                        if (userPlan == UserPlan.FREE) {
                            IconButton(onClick = onPaywallClick) {
                                Icon(Icons.Filled.WorkspacePremium, contentDescription = "프리미엄", tint = Amber400)
                            }
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pullRefresh(pullRefreshState),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 130.dp),
                ) {
                    item {
                        WeatherStockRow(
                            weather = weather,
                            stocks = stocks,
                        )
                    }

                    item {
                        CategoryTabRow(
                            categories = categories,
                            selectedCategory = uiState.selectedCategory,
                            userPlan = userPlan,
                            onCategorySelected = viewModel::selectCategory,
                            onTasteFeedClick = {
                                AdManager.showRewardedAd(
                                    activity = activity,
                                    onRewarded = {
                                        viewModel.onAdRewarded(RewardedFeature.TASTE_FEED)
                                        showTasteFeedSheet = true
                                    },
                                    onDismissed = {},
                                    onFailed = { showTasteFeedSheet = true },
                                )
                            },
                        )
                    }

                    if (uiState.isLoading) {
                        items(5) { SkeletonNewsCard() }
                        return@LazyColumn
                    }

                    if (uiState.selectedCategory != null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "${uiState.selectedCategory!!.label} 뉴스만 보는 중",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.selectCategory(null) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        "전체 보기",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        if (displayArticles.isNotEmpty()) {
                            val headlineArticle = displayArticles
                                .filter { it.imageUrl != null }
                                .firstOrNull() ?: displayArticles.firstOrNull()

                            if (headlineArticle != null) {
                                SectionHeader(title = "🔥 오늘의 헤드라인")
                                HeadlineCard(
                                    article = headlineArticle,
                                    rank = 1,
                                    onClick = {
                                        viewModel.onArticleClick(headlineArticle.id)
                                        onArticleClick(headlineArticle.id)
                                    },
                                    onBookmark = { viewModel.toggleBookmark(headlineArticle.id) },
                                )
                            }
                        }
                    }

                    item {
                        val stockArticles = uiState.articles
                            .filter { it.category == NewsCategory.STOCK }
                            .take(5)
                        CategoryHorizontalSection(
                            title = "📈 주식/투자 핫이슈",
                            articles = stockArticles,
                            onArticleClick = onArticleClick,
                            onMoreClick = { viewModel.selectCategory(NewsCategory.STOCK) },
                        )
                    }

                    item {
                        val polArticles = uiState.articles
                            .filter { it.category == NewsCategory.POLITICS_ECONOMY }
                            .take(5)
                        CategoryHorizontalSection(
                            title = "🏛️ 정치/경제 주요뉴스",
                            articles = polArticles,
                            onArticleClick = onArticleClick,
                            onMoreClick = { viewModel.selectCategory(NewsCategory.POLITICS_ECONOMY) },
                        )
                    }

                    item {
                        if (displayArticles.size >= 2) {
                            SectionHeader(title = "📊 지금 주목받는 뉴스")
                        }
                    }

                    items(
                        displayArticles.drop(1).take(5),
                        key = { it.id }
                    ) { article ->
                        val rank = displayArticles.indexOf(article) + 1
                        RankedNewsCard(
                            article = article,
                            rank = rank,
                            onClick = {
                                viewModel.onArticleClick(article.id)
                                onArticleClick(article.id)
                            },
                            onBookmark = { viewModel.toggleBookmark(article.id) },
                        )
                    }

                    item {
                        if (displayArticles.size > 6) {
                            SectionHeader(title = "🕐 최신 뉴스")
                        }
                    }

                    items(
                        displayArticles.drop(6),
                        key = { it.id }
                    ) { article ->
                        NewsCard(
                            article = article,
                            userPlan = userPlan,
                            onClick = {
                                viewModel.onArticleClick(article.id)
                                onArticleClick(article.id)
                            },
                            onBookmark = { viewModel.toggleBookmark(article.id) },
                        )
                    }

                    if (userPlan == UserPlan.FREE && uiState.articles.size > unlockedCount) {
                        item {
                            LockedMoreCard(
                                isUnlocked = unlockedMore,
                                onPaywallClick = onPaywallClick,
                                onAdClick = {
                                    AdManager.showRewardedAd(
                                        activity = activity,
                                        onRewarded = { viewModel.unlockMoreArticles() },
                                        onDismissed = {},
                                        onFailed = { onPaywallClick() },
                                    )
                                }
                            )
                        }
                    }
                }

                // Pull to Refresh 인디케이터
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )

                if (userPlan == UserPlan.FREE && !BuildConfig.DEBUG) {
                    BannerAdView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    )
                }
            }
            if (showTasteFeedSheet) {
                TasteFeedBottomSheet(
                    onDismiss = { showTasteFeedSheet = false },
                    onArticleClick = { articleId ->
                        showTasteFeedSheet = false
                        onArticleClick(articleId)
                    },
                    viewModel = viewModel,
                )
            }
        }

        // 새로고침 버튼 로딩바
        if (uiState.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

// ── 섹션 헤더 ──────────────────────────────────────────────
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

// ── 헤드라인 큰 카드 (1위) ─────────────────────────────────
@Composable
fun HeadlineCard(
    article: NewsArticle,
    rank: Int,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 순위 뱃지
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "TOP $rank",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    CategoryBadge(category = article.category)
                }
                Text(
                    timeAgo(article.publishedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }

            Text(
                text = article.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // 썸네일
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(categoryEmoji(article.category), fontSize = 48.sp)
                }
            }

            Text(
                text = article.summary,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    article.source,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                IconButton(onClick = onBookmark, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (article.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "즐겨찾기",
                        modifier = Modifier.size(18.dp),
                        tint = if (article.isBookmarked) Amber400 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

// ── 순위 카드 (2~6위) ──────────────────────────────────────
@Composable
fun RankedNewsCard(
    article: NewsArticle,
    rank: Int,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 순위 번호
            Text(
                text = "$rank",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = if (rank <= 3) Amber400
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.width(28.dp),
            )

            // 제목 + 카테고리 + 출처
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CategoryBadge(category = article.category)
                Text(
                    text = article.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        article.source,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("·", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(
                        timeAgo(article.publishedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }

            // 오른쪽 — 썸네일 + 북마크
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(56.dp),
            ) {
                if (article.imageUrl != null) {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                IconButton(
                    onClick = onBookmark,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        if (article.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "즐겨찾기",
                        modifier = Modifier.size(16.dp),
                        tint = if (article.isBookmarked) Amber400
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

// ── 일반 카드 (7위~) ───────────────────────────────────────
@Composable
fun NewsCard(
    article: NewsArticle,
    userPlan: UserPlan,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
) {
    if (article.imageUrl != null) {
        // 이미지 있으면 배너형
        ImageNewsCard(article = article, onClick = onClick, onBookmark = onBookmark)
    } else {
        // 이미지 없으면 텍스트형
        TextNewsCard(article = article, onClick = onClick, onBookmark = onBookmark)
    }
}

// ── 카테고리 가로 스크롤 섹션 ─────────────────────────────
@Composable
fun CategoryHorizontalSection(
    title: String,
    articles: List<NewsArticle>,
    onArticleClick: (String) -> Unit,
    onMoreClick: () -> Unit = {},
) {
    if (articles.isEmpty()) return
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                "더보기",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.clickable { onMoreClick() },
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(articles, key = { it.id }) { article ->
                HorizontalNewsCard(article = article, onClick = { onArticleClick(article.id) })
            }
        }
    }
}

@Composable
fun HorizontalNewsCard(article: NewsArticle, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(180.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(categoryEmoji(article.category), fontSize = 28.sp)
                }
            }
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    article.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                Text(
                    "${article.source} · ${timeAgo(article.publishedAt)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── 카테고리 탭 ────────────────────────────────────────────
@Composable
fun CategoryTabRow(
    categories: List<NewsCategory>,
    selectedCategory: NewsCategory?,
    userPlan: UserPlan,
    onCategorySelected: (NewsCategory?) -> Unit,
    onTasteFeedClick: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            CategoryChip(label = "전체", selected = selectedCategory == null, onClick = { onCategorySelected(null) })
        }
        items(categories) { category ->
            CategoryChip(
                label = category.label,
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onTasteFeedClick,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = Blue400)
                        Text("맞춤 피드", fontSize = 12.sp, color = Blue400)
                    }
                },
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}

@Composable
fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else null,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            selectedBorderWidth = 0.dp,
        ),
    )
}

@Composable
fun CategoryBadge(category: NewsCategory) {
    val (bg, fg) = when (category) {
        NewsCategory.STOCK -> Green50 to Color(0xFF27500A)
        NewsCategory.POLITICS_ECONOMY -> Blue50 to Color(0xFF0C447C)
        NewsCategory.GLOBAL -> Color(0xFFFBEAF0) to Color(0xFF72243E)
        NewsCategory.SPORTS -> Color(0xFFE1F5EE) to Color(0xFF085041)
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            text = category.label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = fg,
        )
    }
}

@Composable
fun LockedMoreCard(
    isUnlocked: Boolean = false,
    onPaywallClick: () -> Unit,
    onAdClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (isUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isUnlocked) Green400
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                Text(
                    if (isUnlocked) "광고 보고 20개 더 보기"
                    else "더 많은 기사 보기",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
            if (!isUnlocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onAdClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null,
                            modifier = Modifier.size(14.dp), tint = Blue400)
                        Spacer(Modifier.width(4.dp))
                        Text("광고 보기", fontSize = 12.sp, color = Blue400)
                    }
                    Button(
                        onClick = onPaywallClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber50),
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, null,
                            modifier = Modifier.size(14.dp), tint = Amber400)
                        Spacer(Modifier.width(4.dp))
                        Text("프리미엄", fontSize = 12.sp, color = Amber400)
                    }
                }
            } else {
                // 광고 후 → 프리미엄 유도만
                Button(
                    onClick = onPaywallClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber50),
                ) {
                    Icon(Icons.Filled.WorkspacePremium, null,
                        modifier = Modifier.size(14.dp), tint = Amber400)
                    Spacer(Modifier.width(4.dp))
                    Text("프리미엄으로 무제한 보기", fontSize = 12.sp, color = Amber400)
                }
            }
        }
    }
}
@Composable
fun SkeletonNewsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(width = 60.dp, height = 10.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

private fun categoryEmoji(category: NewsCategory) = when (category) {
    NewsCategory.STOCK -> "📈"
    NewsCategory.POLITICS_ECONOMY -> "🏛️"
    NewsCategory.GLOBAL -> "🌍"
    NewsCategory.SPORTS -> "⚽"
}

private fun getTodayString(): String {
    val sdf = SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREAN)
    return sdf.format(Date())
}

private fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000

    return when {
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}

@Composable
fun ImageNewsCard(article: NewsArticle, onClick: () -> Unit, onBookmark: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(category = article.category)
                    Spacer(Modifier.weight(1f))
                    Text(
                        timeAgo(article.publishedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
                Text(
                    text = article.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        article.source,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    IconButton(onClick = onBookmark, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (article.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "즐겨찾기",
                            modifier = Modifier.size(16.dp),
                            tint = if (article.isBookmarked) Amber400
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextNewsCard(article: NewsArticle, onClick: () -> Unit, onBookmark: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 카테고리 색상 인디케이터
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor(article.category))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(category = article.category)
                    Spacer(Modifier.weight(1f))
                    Text(
                        timeAgo(article.publishedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
                Text(
                    text = article.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Text(
                    text = article.source,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            // 북마크
            IconButton(onClick = onBookmark, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (article.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "즐겨찾기",
                    modifier = Modifier.size(18.dp),
                    tint = if (article.isBookmarked) Amber400
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasteFeedBottomSheet(
    onDismiss: () -> Unit,
    onArticleClick: (String) -> Unit,
    viewModel: HomeViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tasteFeedArticles by viewModel.tasteFeedArticles.collectAsState()
    val isTasteFeedLoading by viewModel.isTasteFeedLoading.collectAsState()
    val userPlan by viewModel.userPlan.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadTasteFeed() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, null,
                    modifier = Modifier.size(18.dp), tint = Blue400)
                Text("맞춤 피드", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("AI 취향 분석", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            HorizontalDivider()

            when {
                isTasteFeedLoading -> {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("AI가 취향을 분석 중...", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }

                tasteFeedArticles.isEmpty() -> {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.BookmarkBorder, null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Text("즐겨찾기나 검색 기록이 있어야\nAI가 취향을 분석할 수 있어요",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 500.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(tasteFeedArticles, key = { it.id }) { article ->
                            TextNewsCard(
                                article = article,
                                onClick = { onArticleClick(article.id) },
                                onBookmark = { viewModel.toggleBookmark(article.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// 카테고리별 색상 인디케이터
private fun categoryColor(category: NewsCategory) = when (category) {
    NewsCategory.STOCK -> Green400
    NewsCategory.POLITICS_ECONOMY -> Blue400
    NewsCategory.GLOBAL -> Color(0xFF8A1A3A)
    NewsCategory.SPORTS -> Color(0xFF1A6A3A)
}