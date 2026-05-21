package com.teddyjs.news.presentation.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teddyjs.news.BuildConfig
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsArticle
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.presentation.ui.home.CategoryBadge
import com.teddyjs.news.presentation.ui.home.NewsCard
import com.teddyjs.news.domain.model.UserPlan
import com.teddyjs.news.presentation.theme.Amber400
import com.teddyjs.news.presentation.theme.Amber50
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val userPrefs: UserPreferencesDataStore,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<NewsArticle>>(emptyList())
    val results: StateFlow<List<NewsArticle>> = _results.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    val userPlan: StateFlow<UserPlan> = repository.userPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPlan.FREE)


    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    init { loadHistory() }

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.length >= 2) {
            // DB에서만 빠르게 검색 (Gemini 호출 X)
            viewModelScope.launch {
                val allCategories = NewsCategory.entries.toList()
                val dbResults = repository.getNewsFeed(allCategories).first().filter { article ->
                    article.title.contains(q, ignoreCase = true) ||
                            article.summary.contains(q, ignoreCase = true)
                }
                _results.value = dbResults
            }
        } else {
            _results.value = emptyList()
        }
    }

    // 엔터/검색 버튼 → Gemini 외부 검색
    fun search(q: String = _query.value) {
        if (q.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            _results.value = emptyList()
            saveHistory(q)
            userPrefs.saveSearchKeyword(q)
            val result = searchNewsWithGemini(q)

            // DB에 저장 — 클릭 시 무한 로딩 방지
            if (result.isNotEmpty()) {
                repository.saveSearchResults(result)
            }

            _results.value = result
            _isSearching.value = false
        }
    }
    fun clearQuery() {
        _query.value = ""
        _results.value = emptyList()
    }

    fun removeHistory(q: String) {
        _searchHistory.value = _searchHistory.value.filter { it != q }
        saveHistoryList(_searchHistory.value)
    }

    fun clearHistory() {
        _searchHistory.value = emptyList()
        saveHistoryList(emptyList())
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            repository.toggleBookmark(articleId)
            _results.update { list ->
                list.map { article ->
                    if (article.id == articleId) article.copy(isBookmarked = !article.isBookmarked)
                    else article
                }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            // DataStore에서 검색 기록 로드 (간단히 메모리로 관리)
            _searchHistory.value = listOf() // 실제 구현 시 DataStore 연동
        }
    }

    private fun saveHistory(q: String) {
        val updated = (_searchHistory.value.filter { it != q } + q)
            .takeLast(10) // 최근 10개만
        _searchHistory.value = updated
        saveHistoryList(updated)
    }

    private fun saveHistoryList(list: List<String>) {
        // DataStore 저장 — UserPreferencesDataStore에 검색 기록 키 추가 가능
    }

    private suspend fun searchNewsWithGemini(query: String): List<NewsArticle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", """
                                    "$query" 관련 최신 한국 뉴스를 검색해서 5건만 JSON 배열로 반환해줘.
                                    마크다운 없이 순수 JSON만.
                                    [{"title":"제목","summary":"2문장 요약","source":"출처","url":"https://...","publishedAt":"2024-01-01"}]
                                """.trimIndent())
                                })
                            })
                        })
                    })
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("google_search", JSONObject())
                        })
                    })
                }.toString()

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val resBody = response.body?.string() ?: return@runCatching emptyList()
                val json = JSONObject(resBody)

                val text = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .replace("```json", "").replace("```", "").trim()

                val arr = JSONArray(
                    text.let {
                        val s = it.indexOf("[")
                        val e = it.lastIndexOf("]")
                        if (s != -1 && e != -1) it.substring(s, e + 1) else it
                    }
                )

                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    NewsArticle(
                        id = obj.optString("url").md5(),
                        title = obj.optString("title"),
                        summary = obj.optString("summary"),
                        url = obj.optString("url"),
                        source = obj.optString("source", "검색결과"),
                        category = NewsCategory.STOCK, // 검색 결과는 카테고리 무관
                        publishedAt = System.currentTimeMillis(),
                        imageUrl = null,
                    )
                }
            }.getOrDefault(emptyList())
        }

    private fun String.md5(): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    val popularKeywords: StateFlow<List<String>> = _searchHistory
        .map { history ->
            history.groupBy { it }
                .mapValues { it.value.size }
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(5)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onArticleClick: (String) -> Unit,
    onPaywallClick: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val history by viewModel.searchHistory.collectAsState()
    val userPlan by viewModel.userPlan.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val isSearching by viewModel.isSearching.collectAsState()

    val popularKeywords by viewModel.popularKeywords.collectAsState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { viewModel.search() }
                        ),
                        placeholder = { Text("뉴스 검색...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearQuery) {
                                    Icon(Icons.Filled.Close, "지우기", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            if (query.isNotEmpty()) {
                // 로딩 중
                if (isSearching) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text("최신 뉴스 검색 중...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                } else if (results.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Search, null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                Text("저장된 기사가 없어요",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("검색 버튼을 눌러 최신 뉴스를 찾아보세요",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                // 검색 버튼
                                Button(
                                    onClick = { viewModel.search(query) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Amber50),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Search, null,
                                        modifier = Modifier.size(16.dp), tint = Amber400,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI로 최신 뉴스 검색", color = Amber400, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "검색 결과 ${results.size}건",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    items(results, key = { it.id }) { article ->
                        NewsCard(
                            article = article,
                            userPlan = userPlan,
                            onClick = { onArticleClick(article.id) },
                            onBookmark = { viewModel.toggleBookmark(article.id) },
                        )
                    }
                }
            } else {
                // 자주 검색한 키워드
                if (popularKeywords.isNotEmpty()) {
                    item {
                        Text(
                            "자주 검색한 키워드",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            items(popularKeywords) { keyword ->
                                SuggestionChip(
                                    onClick = { viewModel.onQueryChange(keyword) },
                                    label = { Text(keyword, fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                }

                // 최근 검색 기록
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("최근 검색", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            TextButton(onClick = viewModel::clearHistory) {
                                Text("전체 삭제", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                    items(history.reversed()) { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onQueryChange(q) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                Text(q, fontSize = 14.sp)
                            }
                            IconButton(onClick = { viewModel.removeHistory(q) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, "삭제", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("검색어를 입력하세요", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}