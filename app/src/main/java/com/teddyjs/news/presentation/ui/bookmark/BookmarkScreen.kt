package com.teddyjs.news.presentation.ui.bookmark

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.*
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.presentation.ui.admob.AdManager
import com.teddyjs.news.presentation.ui.home.CategoryBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val userPrefs: UserPreferencesDataStore,
) : ViewModel() {

    val bookmarks: StateFlow<List<NewsArticle>> = repository.getBookmarks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val userPlan: StateFlow<UserPlan> = repository.userPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPlan.FREE)

    fun adUsesFlow(feature: RewardedFeature) = repository.adUsesFlow(feature)

    private val _extractedKeywords = MutableStateFlow<List<String>>(emptyList())
    val extractedKeywords: StateFlow<List<String>> = _extractedKeywords.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    val searchKeywords: StateFlow<List<String>> = userPrefs.searchHistoryFlow
        .map { it.takeLast(5) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val clickedKeywords: StateFlow<List<String>> = userPrefs.clickedKeywordsFlow
        .map { it.takeLast(5) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun extractKeywords() {
        viewModelScope.launch {
            _isExtracting.value = true
            repository.consumeAdUse(RewardedFeature.KEYWORD_EXTRACT)
            val keywords = repository.getTasteFeedKeywords()
            _extractedKeywords.value = keywords
            _isExtracting.value = false
        }
    }

    fun onAdRewarded(feature: RewardedFeature) {
        viewModelScope.launch { repository.grantAdReward(feature) }
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch { repository.toggleBookmark(articleId) }
    }

    fun removeBookmark(articleId: String) {
        viewModelScope.launch { repository.toggleBookmark(articleId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    onArticleClick: (String) -> Unit,
    onPaywallClick: () -> Unit,
    viewModel: BookmarkViewModel = hiltViewModel(),
) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    val userPlan by viewModel.userPlan.collectAsState()
    val extractedKeywords by viewModel.extractedKeywords.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val searchKeywords by viewModel.searchKeywords.collectAsState()
    val clickedKeywords by viewModel.clickedKeywords.collectAsState()
    val keywordAdUses by viewModel.adUsesFlow(RewardedFeature.KEYWORD_EXTRACT).collectAsState(initial = 0)
    val activity = LocalContext.current as Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("즐겨찾기", fontWeight = FontWeight.Medium) },
                actions = {
                    if (userPlan == UserPlan.FREE) {
                        Text(
                            "${bookmarks.size}/20",
                            modifier = Modifier.padding(end = 16.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // 키워드 추출 섹션
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Blue50),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFB5D4F4)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 헤더
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null,
                                modifier = Modifier.size(14.dp), tint = Blue400)
                            Text("내 관심사 키워드",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color(0xFF0C447C),
                            )
                            Spacer(Modifier.weight(1f))
                            // 키워드 추출 버튼
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (keywordAdUses > 0) Blue400 else Color(0xFFB5D4F4),
                                modifier = Modifier.clickable {
                                    if (keywordAdUses > 0) {
                                        viewModel.extractKeywords()
                                    } else {
                                        AdManager.showRewardedAd(
                                            activity = activity,
                                            onRewarded = {
                                                viewModel.onAdRewarded(RewardedFeature.KEYWORD_EXTRACT)
                                                viewModel.extractKeywords()
                                            },
                                            onDismissed = {},
                                            onFailed = {},
                                        )
                                    }
                                }
                            ) {
                                Text(
                                    if (keywordAdUses > 0) "새로고침 (${keywordAdUses}회)" else "광고 보고 추출",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    color = Color.White,
                                )
                            }
                        }

                        if (isExtracting) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Blue400,
                                trackColor = Color(0xFFB5D4F4),
                            )
                        }

                        // 추출된 키워드 or 기본 키워드
                        if (extractedKeywords.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(extractedKeywords) { keyword ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Blue400,
                                    ) {
                                        Text(
                                            "#$keyword",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFB5D4F4))

                        // 알고리즘 설명 + 현재 반영 키워드
                        Text(
                            "홈 피드 반영 키워드",
                            fontSize = 11.sp,
                            color = Color(0xFF185FA5).copy(alpha = 0.7f),
                        )

                        if (searchKeywords.isEmpty() && clickedKeywords.isEmpty()) {
                            Text(
                                "기사를 검색하거나 클릭하면 피드가 맞춤 정렬돼요",
                                fontSize = 11.sp,
                                color = Color(0xFF185FA5).copy(alpha = 0.5f),
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (searchKeywords.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(Icons.Filled.Search, null,
                                                modifier = Modifier.size(11.dp),
                                                tint = Blue400.copy(alpha = 0.6f))
                                            Text("검색", fontSize = 10.sp,
                                                color = Color(0xFF185FA5).copy(alpha = 0.6f))
                                        }
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            items(searchKeywords.take(5)) { keyword ->
                                                Surface(
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = Color(0xFFB5D4F4).copy(alpha = 0.5f),
                                                ) {
                                                    Text(keyword,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF0C447C))
                                                }
                                            }
                                        }
                                    }
                                }
                                if (clickedKeywords.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(Icons.Filled.TouchApp, null,
                                                modifier = Modifier.size(11.dp),
                                                tint = Blue400.copy(alpha = 0.6f))
                                            Text("클릭", fontSize = 10.sp,
                                                color = Color(0xFF185FA5).copy(alpha = 0.6f))
                                        }
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            items(clickedKeywords.take(5)) { keyword ->
                                                Surface(
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = Color(0xFFB5D4F4).copy(alpha = 0.5f),
                                                ) {
                                                    Text(keyword,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF0C447C))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (bookmarks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.StarBorder, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Text("즐겨찾기한 기사가 없어요", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            } else {
                items(bookmarks, key = { it.id }) { article ->
                    BookmarkCard(
                        article = article,
                        onClick = { onArticleClick(article.id) },
                        onRemove = { viewModel.removeBookmark(article.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun KeywordExtractSection(
    keywords: List<Pair<String, Int>>,
    isExtracting: Boolean,
    adUses: Int,
    userPlan: UserPlan,
    onExtract: () -> Unit,
    onPaywallClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(16.dp), tint = Blue400)
                Text("키워드 자동 추출", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                if (userPlan == UserPlan.FREE) {
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(10.dp), color = Amber50) {
                        Text(
                            if (adUses > 0) "${adUses}회 남음" else "광고 1회 → 3회",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp, color = Amber400,
                        )
                    }
                }
            }

            if (keywords.isNotEmpty()) {
                // 키워드 칩
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keywords.take(5).forEach { (kw, count) ->
                        Surface(shape = RoundedCornerShape(12.dp), color = Blue50) {
                            Text(
                                "$kw ×$count",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp, color = Color(0xFF0C447C),
                                fontWeight = if (count >= 6) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            if (isExtracting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("키워드 추출 중...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                OutlinedButton(
                    onClick = onExtract,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Amber400),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (userPlan == UserPlan.PREMIUM || adUses > 0) "키워드 추출하기"
                        else "광고 1회 보고 키워드 추출 (3회 제공)",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkCard(
    article: NewsArticle,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 2.dp), tint = Amber400)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CategoryBadge(category = article.category)
                Text(article.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(article.source, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("·", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "삭제", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }
    }
}

private val Color = androidx.compose.ui.graphics.Color
