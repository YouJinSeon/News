package com.teddyjs.news.presentation.ui.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.util.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val userPrefs: UserPreferencesDataStore,
) : ViewModel() {

    private val _variant = MutableStateFlow("A")
    val variant: StateFlow<String> = _variant.asStateFlow()

    init {
        viewModelScope.launch {
            val v = userPrefs.getOrAssignPaywallVariant()
            _variant.value = v
            // 변형별 노출 측정 (전환율 비교 기준)
            AnalyticsHelper.log(AnalyticsHelper.PAYWALL_VIEW, mapOf("variant" to v))
        }
    }
}
