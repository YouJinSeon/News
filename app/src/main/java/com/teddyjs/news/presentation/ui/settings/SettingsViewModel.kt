package com.teddyjs.news.presentation.ui.settings

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.teddyjs.news.MainActivity
import com.teddyjs.news.R
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.util.appLargeIcon
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.domain.model.UserPlan
import com.teddyjs.news.worker.BreakingNewsWorker
import com.teddyjs.news.worker.DailyBriefingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NewsRepository,
    private val userPrefs: UserPreferencesDataStore,
    private val workManager: WorkManager,
    private val referralManager: com.teddyjs.news.util.ReferralManager,
) : ViewModel() {

    private val _referralCode = MutableStateFlow("")
    val referralCode: StateFlow<String> = _referralCode.asStateFlow()

    private val _inviteCount = MutableStateFlow(0)
    val inviteCount: StateFlow<Int> = _inviteCount.asStateFlow()

    /** 다음 보상까지 필요한 기준 인원 */
    val rewardThreshold: Int = com.teddyjs.news.util.ReferralManager.REWARD_THRESHOLD

    fun loadReferral() {
        viewModelScope.launch { _referralCode.value = referralManager.referralCode() }
        referralManager.fetchInviteCount { _inviteCount.value = it }
    }

    private val _paywallVariant = MutableStateFlow("A")
    val paywallVariant: StateFlow<String> = _paywallVariant.asStateFlow()

    fun loadPaywallVariant() {
        viewModelScope.launch { _paywallVariant.value = userPrefs.getOrAssignPaywallVariant() }
    }

    /** 테스트용: 페이월 변형 A↔B 전환 */
    fun togglePaywallVariant() {
        viewModelScope.launch {
            val next = if (userPrefs.getOrAssignPaywallVariant() == "A") "B" else "A"
            userPrefs.setPaywallVariant(next)
            _paywallVariant.value = next
        }
    }

    val userPlan: StateFlow<UserPlan> = repository.userPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPlan.FREE)

    val subscribedCategories: StateFlow<List<NewsCategory>> = repository.subscribedCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _breakingNotification = MutableStateFlow(true)
    val breakingNotification: StateFlow<Boolean> = _breakingNotification.asStateFlow()

    private val _dailyNotification = MutableStateFlow(true)
    val dailyNotification: StateFlow<Boolean> = _dailyNotification.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    val customBreakingKeywords: StateFlow<List<String>> = userPrefs.customBreakingKeywords
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val nightNotification: StateFlow<Boolean> = userPrefs.nightNotificationFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val followedTopics: StateFlow<List<String>> = userPrefs.followedTopics
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _algorithmResetDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val algorithmResetDone = _algorithmResetDone.asSharedFlow()

    val subscribedProductId = userPrefs.subscribedProductIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            _breakingNotification.value = userPrefs.getBreakingNotification()
            _dailyNotification.value = userPrefs.getDailyNotification()
            _darkMode.value = userPrefs.getDarkMode()
        }
    }

    fun toggleBreakingNotification(enabled: Boolean) {
        _breakingNotification.value = enabled
        viewModelScope.launch {
            userPrefs.setBreakingNotification(enabled)
            if (enabled) BreakingNewsWorker.schedule(workManager)
            else workManager.cancelUniqueWork(BreakingNewsWorker.WORK_NAME)
        }
    }

    fun toggleDailyNotification(enabled: Boolean) {
        _dailyNotification.value = enabled
        viewModelScope.launch {
            userPrefs.setDailyNotification(enabled)
            if (enabled) DailyBriefingWorker.schedule(workManager)
            else workManager.cancelUniqueWork(DailyBriefingWorker.WORK_NAME)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        viewModelScope.launch { userPrefs.setDarkMode(enabled) }
    }

    fun toggleCategory(category: NewsCategory) {
        viewModelScope.launch {
            val current = subscribedCategories.value.toMutableList()
            if (current.contains(category)) {
                if (current.size > 1) current.remove(category)
            } else {
                current.add(category)
            }
            repository.updateSubscribedCategories(current)
        }
    }

    fun testNotification(context: Context) {
        viewModelScope.launch {
            // Worker 통하지 않고 직접 알림 발송
            sendTestNotification(context, "☀️ 아침 브리핑 테스트", "오늘의 주요 뉴스를 확인하세요")
            sendTestNotification(context, "🔴 속보 테스트", "긴급 속보가 발생했습니다")
        }
    }

    private fun sendTestNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, title.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (title.contains("속보")) "breaking_news" else "daily_briefing"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appLargeIcon(context))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(title.hashCode(), notification)
    }

    fun clearAlgorithmData() {
        viewModelScope.launch {
            userPrefs.clearAlgorithmData()
            _algorithmResetDone.tryEmit(Unit)
        }
    }

    fun addBreakingKeyword(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch { userPrefs.addBreakingKeyword(keyword) }
    }

    fun removeBreakingKeyword(keyword: String) {
        viewModelScope.launch { userPrefs.removeBreakingKeyword(keyword) }
    }

    fun setNightNotification(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setNightNotification(enabled) }
    }

    fun removeTopic(topic: String) {
        viewModelScope.launch { userPrefs.unfollowTopic(topic) }
    }

    fun clearAllTopics() {
        viewModelScope.launch {
            userPrefs.followedTopics.first().forEach { topic ->
                userPrefs.unfollowTopic(topic)
            }
        }
    }

    fun togglePlanForDebug() {
        viewModelScope.launch {
            val current = userPrefs.userPlan.first()
            userPrefs.setUserPlan(
                if (current == UserPlan.PREMIUM) UserPlan.FREE else UserPlan.PREMIUM
            )
        }
    }

}