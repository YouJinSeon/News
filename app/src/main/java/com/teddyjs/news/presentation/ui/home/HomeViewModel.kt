package com.teddyjs.news.presentation.ui.home

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.*
import com.teddyjs.news.worker.RssSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.work.WorkManager
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.presentation.ui.common.StockItem
import com.teddyjs.news.presentation.ui.common.WeatherInfo
import com.teddyjs.news.presentation.ui.common.fetchStockPrice
import com.teddyjs.news.presentation.ui.common.fetchWeather
import com.teddyjs.news.presentation.ui.common.sampleStocks
import com.teddyjs.news.worker.BreakingNewsWorker
import com.teddyjs.news.worker.DailyBriefingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val workManager: WorkManager,
    private val userPrefs: UserPreferencesDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val userPlan: StateFlow<UserPlan> = repository.userPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPlan.FREE)

    val subscribedCategories: StateFlow<List<NewsCategory>> = repository.subscribedCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(NewsCategory.STOCK, NewsCategory.POLITICS_ECONOMY))

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather.asStateFlow()
    private var selectCategoryJob: kotlinx.coroutines.Job? = null

    private val _unlockedMore = MutableStateFlow(false)
    val unlockedMore: StateFlow<Boolean> = _unlockedMore.asStateFlow()

    var savedScrollIndex by mutableIntStateOf(0)
        private set
    var savedScrollOffset by mutableIntStateOf(0)
        private set

    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    private val _tasteFeedArticles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val tasteFeedArticles: StateFlow<List<NewsArticle>> = _tasteFeedArticles.asStateFlow()

    private val _isTasteFeedLoading = MutableStateFlow(false)
    val isTasteFeedLoading: StateFlow<Boolean> = _isTasteFeedLoading.asStateFlow()

    private val _unlockedCount = MutableStateFlow(10)  // 기본 10개
    val unlockedCount: StateFlow<Int> = _unlockedCount.asStateFlow()

    // 오늘의 나를 위한 브리핑 (AI 일일 다이제스트)
    private val _dailyDigest = MutableStateFlow<String?>(null)
    val dailyDigest: StateFlow<String?> = _dailyDigest.asStateFlow()
    private val _isDigestLoading = MutableStateFlow(false)
    val isDigestLoading: StateFlow<Boolean> = _isDigestLoading.asStateFlow()

    // 브리핑 펼침/접힘 상태 (화면 재진입에도 유지)
    private val _digestExpanded = MutableStateFlow(true)
    val digestExpanded: StateFlow<Boolean> = _digestExpanded.asStateFlow()
    fun toggleDigestExpanded() { _digestExpanded.value = !_digestExpanded.value }

    fun loadDailyDigest(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // 캐시 먼저(즉시 표시)
            if (!forceRefresh) {
                val cached = userPrefs.getCachedDigest()
                if (cached != null) { _dailyDigest.value = cached; return@launch }
            }
            _isDigestLoading.value = true
            runCatching { repository.getDailyDigest(forceRefresh) }
                .onSuccess { _dailyDigest.value = it }
                .onFailure { Timber.e(it, "다이제스트 생성 실패") }
            _isDigestLoading.value = false
        }
    }

    private var hasInitialRefresh = false


    init {
        observeFeed()
        refreshMissingCategories()
        fetchStocks()
        startAutoRefresh()   // 앱 켜둔 동안 10분마다 피드 자동 갱신
        RssSyncWorker.schedule(workManager)
    }

    private val _stocks = MutableStateFlow(sampleStocks)
    val stocks: StateFlow<List<StockItem>> = _stocks.asStateFlow()

    private fun fetchStocks() {
        viewModelScope.launch {
            val symbols = listOf(
                "^KS11",   // KOSPI
                "^IXIC",   // 나스닥
                "KRW=X",   // 원/달러
            )
            val result = symbols.mapNotNull { fetchStockPrice(it) }
            if (result.isNotEmpty()) _stocks.value = result
        }
    }

    fun fetchWeatherData(context: Context) {
        viewModelScope.launch {
            _weather.value = fetchWeather(context)
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    private fun observeFeed() {
        viewModelScope.launch {
            combine(
                repository.getNewsFeed(NewsCategory.entries.toList()),
                _uiState.map { it.selectedCategory }.distinctUntilChanged(),
            ) { articles, selectedCategory ->
                Timber.d("observeFeed 트리거: ${articles.size}개")
                Pair(articles, selectedCategory)
            }.collectLatest { (articles, selectedCategory) ->
                val filtered = if (selectedCategory == null) articles
                else articles.filter { it.category == selectedCategory }

                val searchKeywords = userPrefs.getSearchHistory()
                val clickedKeywords = userPrefs.getClickedKeywords()
                val followedTopics = userPrefs.followedTopics.first()
                val subscribedCategories = userPrefs.subscribedCategories.first()
                val now = System.currentTimeMillis()

                val sorted = if (searchKeywords.isEmpty() && clickedKeywords.isEmpty() && followedTopics.isEmpty()) {
                    // 키워드 없으면 완전 최신순
                    filtered.sortedWith(
                        compareByDescending<NewsArticle> { it.publishedAt }
                            .thenByDescending { it.imageUrl != null }
                    )
                } else {
                    filtered.sortedWith(
                        compareByDescending<NewsArticle> { article ->
                            val recencyScore = when {
                                now - article.publishedAt < 60 * 60 * 1000L -> 50.0
                                now - article.publishedAt < 3 * 60 * 60 * 1000L -> 35.0
                                now - article.publishedAt < 6 * 60 * 60 * 1000L -> 20.0
                                now - article.publishedAt < 12 * 60 * 60 * 1000L -> 10.0
                                now - article.publishedAt < 24 * 60 * 60 * 1000L -> 3.0
                                else -> 0.0
                            }
                            val searchScore = searchKeywords.count { keyword ->
                                article.title.contains(keyword, ignoreCase = true)
                            } * 6.0
                            val clickScore = clickedKeywords.count { keyword ->
                                article.title.contains(keyword, ignoreCase = true)
                            } * 4.0
                            val topicScore = followedTopics.count { topic ->
                                article.title.contains(topic, ignoreCase = true)
                            } * 10.0

                            recencyScore + searchScore + clickScore + topicScore
                        }
                            .thenByDescending { it.imageUrl != null }
                            .thenByDescending { it.publishedAt }
                    )
                }

                // 구독 카테고리별 최신 기사 최소 1개씩 보장
                val guaranteed = subscribedCategories.mapNotNull { category ->
                    filtered
                        .filter { it.category == category }
                        .maxByOrNull { it.publishedAt }
                }

                // 보장 기사 중 sorted에 없는 것만 상위에 추가
                val sortedIds = sorted.take(10).map { it.id }.toSet()
                val missingGuaranteed = guaranteed.filter { it.id !in sortedIds }

                val finalSorted = if (missingGuaranteed.isEmpty()) {
                    sorted
                } else {
                    // 보장 기사를 상위 10개 안에 끼워넣기
                    val top10 = sorted.take(10).toMutableList()
                    missingGuaranteed.forEach { article ->
                        // 마지막 자리에 넣고 최신순 재정렬
                        top10.add(article)
                    }
                    val reorderedTop = top10.sortedWith(
                        compareByDescending<NewsArticle> { it.publishedAt }
                    ).distinctBy { it.id }

                    reorderedTop + sorted.drop(10).filter { it.id !in reorderedTop.map { a -> a.id }.toSet() }
                }

                _uiState.update { it.copy(articles = finalSorted.distinctBy { it.id }, isLoading = false) }

                // 오늘 이미 생성된 브리핑이 있으면 표시만(자동 생성 X — 사용자가 탭하면 생성)
                if (_dailyDigest.value == null && finalSorted.isNotEmpty()) {
                    userPrefs.getCachedDigest()?.let { _dailyDigest.value = it }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val categories = repository.subscribedCategories
                .filter { it.isNotEmpty() }
                .first()
            repository.fetchAndRefreshFeed(categories)
            fetchStocks()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun refreshMissingCategories() {
        if (hasInitialRefresh) return  // ← 이미 실행했으면 스킵
        hasInitialRefresh = true

        viewModelScope.launch {
            val subscribedCategories = repository.subscribedCategories
                .filter { it.isNotEmpty() }
                .first()

            val hasAnyArticles = subscribedCategories.any { category ->
                repository.getNewsFeed(listOf(category)).first().isNotEmpty()
            }

            // 기사가 하나도 없을 때만 fetch (구독 카테고리 전체를 한 번에 → 카테고리별 덮어쓰기 방지)
            if (!hasAnyArticles) {
                _uiState.update { it.copy(isRefreshing = true) }
                runCatching {
                    repository.fetchAndRefreshFeed(subscribedCategories)
                }
                _uiState.update { it.copy(isRefreshing = false) }
            }

            fetchStocks()
        }
    }


    fun selectCategory(category: NewsCategory?) {
        selectCategoryJob?.cancel()
        selectCategoryJob = viewModelScope.launch {
            _uiState.update { it.copy(selectedCategory = category) }

            if (category != null) {
                val existing = repository.getNewsFeed(listOf(category)).first()
                if (existing.isEmpty()) {
                    // 로딩바 표시 + 다른 카테고리는 보존(replaceExisting=false)
                    _uiState.update { it.copy(isRefreshing = true) }
                    runCatching {
                        repository.fetchAndRefreshFeed(listOf(category), replaceExisting = false)
                    }
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch { repository.toggleBookmark(articleId) }
    }

    fun adUsesFlow(feature: RewardedFeature) = repository.adUsesFlow(feature)

    fun onAdRewarded(feature: RewardedFeature) {
        viewModelScope.launch { repository.grantAdReward(feature) }
    }

    fun onArticleClick(articleId: String) {
        viewModelScope.launch {
            val article = repository.getArticleById(articleId) ?: return@launch
            userPrefs.saveClickedKeywords(article.title)
            repository.incrementViewCount(articleId)
        }
    }

    // 앱 포그라운드 상태에서 10분마다 자동 갱신
    fun startAutoRefresh() {
        autoRefreshJob?.cancel()  // 기존 잡 취소
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                refresh()               // 앱(화면) 진입 즉시 1회 갱신
                delay(10 * 60 * 1000L)  // 이후 10분마다
            }
        }
    }

    fun unlockMoreArticles() {
        _unlockedCount.value += 20  // 광고 볼 때마다 20개씩 추가
    }

    fun loadTasteFeed() {
        viewModelScope.launch {
            _isTasteFeedLoading.value = true
            runCatching {
                val keywords = repository.getTasteFeedKeywords()
                if (keywords.isEmpty()) {
                    _tasteFeedArticles.value = emptyList()
                    return@runCatching
                }
                val allArticles = repository.getNewsFeed(
                    NewsCategory.entries.toList()
                ).first()

                _tasteFeedArticles.value = allArticles.filter { article ->
                    keywords.any { keyword ->
                        article.title.contains(keyword, ignoreCase = true) ||
                                article.summary.contains(keyword, ignoreCase = true)
                    }
                }.take(20)
            }
            _isTasteFeedLoading.value = false
        }
    }

}

data class HomeUiState(
    val articles: List<NewsArticle> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedCategory: NewsCategory? = null,
    val error: String? = null,
)
