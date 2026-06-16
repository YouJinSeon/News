package com.teddyjs.news.presentation.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.data.remote.GeminiResult
import com.teddyjs.news.data.remote.GeminiService
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsArticle
import com.teddyjs.news.domain.model.RewardedFeature
import com.teddyjs.news.domain.model.UserPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val userPrefs: UserPreferencesDataStore,
    private val geminiService: GeminiService,  // ← 추가
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    val userPlan: StateFlow<UserPlan> = repository.userPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPlan.FREE)

    val followedTopics: StateFlow<List<String>> = userPrefs.followedTopics
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 관점 비교 남은 사용권 (광고로 충전, 성공 시 1회 차감)
    val perspectiveUses: StateFlow<Int> = repository.adUsesFlow(RewardedFeature.PERSPECTIVE_COMPARE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _webViewUrl = MutableStateFlow<String?>(null)
    val webViewUrl: StateFlow<String?> = _webViewUrl.asStateFlow()

    // 인앱 리뷰 요청 이벤트 (좋은 타이밍에 평점 다이얼로그)
    private val _reviewRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewRequest = _reviewRequest.asSharedFlow()

    fun onArticleOpenedForReview() {
        viewModelScope.launch {
            if (userPrefs.incrementReviewActionAndShouldAsk()) _reviewRequest.tryEmit(Unit)
        }
    }

    fun adUsesFlow(feature: RewardedFeature) = repository.adUsesFlow(feature)

    fun loadArticle(articleId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            userPrefs.setCurrentArticleId(articleId)

            val article = repository.getArticleById(articleId)
            _uiState.update { it.copy(article = article, isLoading = false) }
            article?.let { launch { fetchFullContent(it.url) } }
        }
    }

    // ── AI 간략 요약 (횟수 차감) ────────────────────────────
    fun requestQuickSummary(article: NewsArticle) {
        if (_uiState.value.isQuickSummaryLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isQuickSummaryLoading = true) }
            runCatching {
                val prompt = """
                다음 뉴스를 알기쉽게 딱 2문장으로 핵심만 요약해줘.
                40자 이내로. 마크다운 없이 순수 텍스트만.
                
                제목: ${article.title}
                
                (전체 기사를 보려면 '전체 기사 읽기' 버튼을 이용하세요)
            """.trimIndent()
                geminiService.callGeminiRaw(prompt)
            }.onSuccess { result ->
                repository.consumeAdUse(RewardedFeature.AI_SUMMARY)
                _uiState.update { it.copy(quickSummary = result, isQuickSummaryLoading = false) }
            }.onFailure {
                Timber.e(it, "간략 요약 실패")
                _uiState.update { it.copy(isQuickSummaryLoading = false) }
            }
        }
    }
    // 광고 보고 간략 요약 (+3회 충전 후 실행)
    fun onAdRewardedAndQuickSummary(article: NewsArticle) {
        viewModelScope.launch {
            repository.grantAdReward(RewardedFeature.AI_SUMMARY)
            requestQuickSummary(article)
        }
    }

    // ── AI 심층 분석 (무조건 광고) ───────────────────────────
    fun requestDeepAnalysis(article: NewsArticle) {
        if (_uiState.value.isAiLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            when (val result = repository.getAiSummary(article)) {
                is GeminiResult.Success -> {
                    Timber.d("심층 분석 결과: summary=${result.summary}")
                    Timber.d("투자 시사점: ${result.investmentInsight}")  // ← 추가
                    Timber.d("키워드: ${result.keywords}")
                    _uiState.update {
                        it.copy(
                            aiSummary = result.summary,
                            investmentInsight = result.investmentInsight,
                            keywords = result.keywords,
                            isAiLoading = false,
                        )
                    }
                }
                is GeminiResult.Error -> _uiState.update {
                    it.copy(isAiLoading = false, error = result.message)
                }
            }
        }
    }

    // 광고 보고 심층 분석
    fun onAdRewardedAndDeepAnalysis(article: NewsArticle) {
        viewModelScope.launch {
            repository.grantAdReward(RewardedFeature.KEYWORD_EXTRACT)
            requestDeepAnalysis(article)
        }
    }

    // ── 관점 비교 (언론사별 시각) ─────────────────────────────
    fun requestPerspectiveCompare(article: NewsArticle) {
        if (_uiState.value.isPerspectiveLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPerspectiveLoading = true, perspectiveError = null) }
            runCatching {
                repository.comparePerspectives(article)
            }.onSuccess { result ->
                if (result == null) {
                    // 관련 보도가 부족해 실패 → 사용권을 차감하지 않음(광고로 받은 권리 보전)
                    _uiState.update {
                        it.copy(
                            isPerspectiveLoading = false,
                            perspectiveError = "이 사안을 다룬 다른 언론사 보도를 충분히 찾지 못했어요. " +
                                "(사용권은 그대로 남아 있어요)",
                        )
                    }
                } else {
                    // 성공했을 때만 1회 차감 (프리미엄은 무제한)
                    if (userPlan.value != UserPlan.PREMIUM) {
                        repository.consumeAdUse(RewardedFeature.PERSPECTIVE_COMPARE)
                    }
                    _uiState.update { it.copy(perspective = result, isPerspectiveLoading = false) }
                }
            }.onFailure {
                Timber.e(it, "관점 비교 실패")
                _uiState.update {
                    it.copy(
                        isPerspectiveLoading = false,
                        perspectiveError = "관점 비교 중 오류가 발생했어요. (사용권은 그대로 남아 있어요)",
                    )
                }
            }
        }
    }

    // 광고 보고 관점 비교
    fun onAdRewardedAndPerspective(article: NewsArticle) {
        viewModelScope.launch {
            repository.grantAdReward(RewardedFeature.PERSPECTIVE_COMPARE)
            requestPerspectiveCompare(article)
        }
    }

    // ── AI에게 질문하기 (대화형) ─────────────────────────────
    fun askQuestion(article: NewsArticle, question: String) {
        if (question.isBlank() || _uiState.value.isQnaLoading) return
        viewModelScope.launch {
            // 프리미엄은 무제한, 무료는 AI 사용권 1회 차감
            val premium = userPlan.value == UserPlan.PREMIUM
            if (!premium) {
                val ok = repository.consumeAdUse(RewardedFeature.AI_SUMMARY)
                if (!ok) {
                    _uiState.update {
                        it.copy(
                            qnaQuestion = question,
                            qnaAnswer = "AI 사용권이 부족해요. 위 'AI 요약'에서 광고를 보고 충전하거나, 프리미엄으로 무제한 이용해보세요.",
                        )
                    }
                    return@launch
                }
            }
            _uiState.update { it.copy(isQnaLoading = true, qnaQuestion = question, qnaAnswer = null) }
            runCatching {
                geminiService.askAboutArticle(article.title, article.summary, question)
            }.onSuccess { answer ->
                _uiState.update {
                    it.copy(
                        qnaAnswer = answer ?: "답변을 가져오지 못했어요. 잠시 후 다시 시도해주세요.",
                        isQnaLoading = false,
                    )
                }
            }.onFailure {
                Timber.e(it, "AI 질문 실패")
                _uiState.update {
                    it.copy(qnaAnswer = "답변 중 오류가 발생했어요.", isQnaLoading = false)
                }
            }
        }
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            repository.toggleBookmark(articleId)
            loadArticle(articleId)
        }
    }

    fun onAdRewarded(feature: RewardedFeature) {
        viewModelScope.launch { repository.grantAdReward(feature) }
    }

    fun toggleFollowTopic(topic: String) {
        viewModelScope.launch {
            if (followedTopics.value.contains(topic)) {
                userPrefs.unfollowTopic(topic)
            } else {
                userPrefs.followTopic(topic)
            }
        }
    }

    private suspend fun fetchFullContent(url: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 TeddyNewsApp/1.0")
                    .build()
            }
        }.onFailure { Timber.e(it, "본문 파싱 실패") }
    }

    fun saveWebViewUrl(url: String) {
        _webViewUrl.value = url
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            userPrefs.setCurrentArticleId(null)
        }
    }
}

data class DetailUiState(
    val article: NewsArticle? = null,
    val isLoading: Boolean = true,
    val quickSummary: String? = null,
    val isQuickSummaryLoading: Boolean = false,
    val aiSummary: String? = null,
    val isAiLoading: Boolean = false,
    val investmentInsight: String? = null,
    val keywords: List<String> = emptyList(),
    val qnaQuestion: String? = null,
    val qnaAnswer: String? = null,
    val isQnaLoading: Boolean = false,
    val perspective: com.teddyjs.news.data.remote.PerspectiveResult? = null,
    val isPerspectiveLoading: Boolean = false,
    val perspectiveError: String? = null,
    val error: String? = null,
)