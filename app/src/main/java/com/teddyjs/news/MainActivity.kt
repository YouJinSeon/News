package com.teddyjs.news

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import com.teddyjs.news.data.local.UserPreferencesDataStore
import com.teddyjs.news.presentation.navigation.MainScreen
import com.teddyjs.news.presentation.theme.NewsAppTheme
import com.teddyjs.news.util.BillingManager
import com.teddyjs.news.util.ReferralManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var userPrefs: UserPreferencesDataStore

    @Inject
    lateinit var referralManager: ReferralManager

    private lateinit var appUpdateManager: AppUpdateManager
    private val _showUpdateReady = MutableStateFlow(false)
    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Timber.w("인앱 업데이트 취소 또는 실패")
        }
    }

    private val _pendingArticleId = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        billingManager.init(this)

        // 친구 초대 귀속 + 보상 확인
        referralManager.process()

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdate()

        intent.getStringExtra("articleId")?.let { articleId ->
            if (articleId.isNotBlank()) {
                _pendingArticleId.tryEmit(articleId)
            }
        }

        setContent {
            val darkMode by userPrefs.darkModeFlow.collectAsState(initial = false)
            val showUpdateReady by _showUpdateReady.collectAsState()

            NewsAppTheme(darkTheme = darkMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        billingManager = billingManager,
                        pendingArticleFlow = _pendingArticleId,
                    )

                    // 업데이트 준비 완료 배너
                    AnimatedVisibility(
                        visible = showUpdateReady,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            tonalElevation = 4.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "새 버전이 준비됐어요!",
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        appUpdateManager.completeUpdate()
                                        _showUpdateReady.value = false
                                    },
                                ) {
                                    Text(
                                        "지금 설치",
                                        color = MaterialTheme.colorScheme.inversePrimary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 알림 권한 요청은 온보딩 마지막 단계로 이동(OnboardingScreen).
        // 콜드스타트에서 맥락 없이 묻지 않도록 함 → 허용률·리텐션 개선.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 앱 실행 중일 때 알림 클릭
        setIntent(intent)
        intent.getStringExtra("articleId")?.let { articleId ->
            if (articleId.isNotBlank()) {
                _pendingArticleId.tryEmit(articleId)
            }
        }
    }

    private fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            when {
                // 업데이트 available + 우선순위 높음 → 즉시 업데이트
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.updatePriority() >= 4
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateResultLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }
                // 업데이트 available → 유연한 업데이트
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateResultLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                    // 다운로드 완료 감지
                    appUpdateManager.registerListener(flexibleUpdateListener)
                }
                // 다운로드 완료됐는데 설치 안 됨 → 설치 유도
                appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED -> {
                    showUpdateSnackbar()
                }
            }
        }
    }

    private val flexibleUpdateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            showUpdateSnackbar()
        }
    }
    private fun showUpdateSnackbar() {
        _showUpdateReady.value = true
    }

    override fun onResume() {
        super.onResume()
        // 즉시 업데이트 중 앱 복귀 시 재확인
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(flexibleUpdateListener)
    }

}
