package com.teddyjs.news.presentation.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teddyjs.news.data.repository.NewsRepository
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: NewsRepository,
) : ViewModel() {
    val selected = mutableStateListOf<NewsCategory>()

    fun toggle(category: NewsCategory) {
        if (selected.contains(category)) selected.remove(category)
        else selected.add(category)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.updateSubscribedCategories(selected.toList())
            onDone()
        }
    }
}

data class CategoryOption(
    val category: NewsCategory,
    val emoji: String,
    val desc: String,
)

val categoryOptions = listOf(
    CategoryOption(NewsCategory.STOCK, "📈", "주식·펀드·ETF"),
    CategoryOption(NewsCategory.POLITICS_ECONOMY, "🏛️", "정치·거시경제"),
    CategoryOption(NewsCategory.GLOBAL, "🌍", "해외·국제뉴스"),
    CategoryOption(NewsCategory.SPORTS, "⚽", "스포츠·엔터"),
)

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val selected = viewModel.selected
    val context = LocalContext.current

    // 알림 권한 요청 후(허용/거부 무관) 온보딩 완료 → 콜드스타트가 아닌
    // "맥락 있는 타이밍"에 물어봐 허용률을 높인다.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.save(onDone) }

    val finishOnboarding: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.save(onDone)
        }
    }

    var step by remember { mutableIntStateOf(0) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                0 -> OnboardingIntro(onNext = { step = 1 })
                else -> OnboardingCategoryStep(
                    selected = selected,
                    onToggle = viewModel::toggle,
                    onFinish = finishOnboarding,
                )
            }
        }
    }
}

// ── 1단계: 가치 데모 (AI가 뭘 해주는지 보여주기) ──────────────
@Composable
private fun ColumnScope.OnboardingIntro(onNext: () -> Unit) {
    // 콘텐츠는 스크롤 영역(큰 글자/작은 화면 대응), 버튼은 하단 고정
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            "AI가 매일\n핵심 뉴스만 골라드려요",
            fontSize = 26.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, lineHeight = 36.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "관심사만 고르면, 복잡한 뉴스를 AI가 요약해줘요",
            fontSize = 14.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(28.dp))

        // AI 요약 예시 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp, MaterialTheme.colorScheme.outline
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📰 한국은행, 기준금리 동결 결정",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Surface(shape = RoundedCornerShape(10.dp), color = Blue50) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🤖 AI 요약", fontSize = 11.sp,
                            fontWeight = FontWeight.Medium, color = Blue400)
                        Text(
                            "한국은행이 기준금리를 3.5%로 동결했어요. 물가 둔화와 경기 부담을 함께 고려한 결정으로, 당분간 추가 인상은 어려울 전망이에요.",
                            fontSize = 13.sp, lineHeight = 19.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        OnboardingFeatureRow("⚡", "키워드 속보 알림", "관심 키워드가 뜨면 즉시 알려줘요")
        Spacer(Modifier.height(10.dp))
        OnboardingFeatureRow("🔍", "AI 심층 분석", "기사 하나를 깊이 있게 풀어줘요")
        Spacer(Modifier.height(10.dp))
        OnboardingFeatureRow("📊", "주간 리포트", "내 관심사를 매주 정리해줘요")
        Spacer(Modifier.height(20.dp))
    }

    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("다음", fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun OnboardingFeatureRow(emoji: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

// ── 2단계: 관심사 선택 (+ 알림 권한) ────────────────────────
@Composable
private fun ColumnScope.OnboardingCategoryStep(
    selected: List<NewsCategory>,
    onToggle: (NewsCategory) -> Unit,
    onFinish: () -> Unit,
) {
    Spacer(Modifier.height(48.dp))
    Text("관심 뉴스를\n선택해주세요", fontSize = 26.sp, fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center, lineHeight = 36.sp)
    Spacer(Modifier.height(8.dp))
    Text("최소 1개 이상 선택하면 시작할 수 있어요",
        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    Spacer(Modifier.height(32.dp))

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f),
    ) {
        items(categoryOptions) { option ->
            CategoryCard(
                option = option,
                isSelected = selected.contains(option.category),
                onClick = { onToggle(option.category) },
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    // 권한 요청 직전 맥락 제공 (시스템 팝업 거부율 감소)
    Text(
        "🔔 다음 단계에서 알림을 허용하면\n속보·아침 브리핑을 놓치지 않아요",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        lineHeight = 17.sp,
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onFinish,
        enabled = selected.size >= 1,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            if (selected.isEmpty()) "관심사를 선택해주세요"
            else if (selected.size == 1) "1개 선택 · 시작하기"
            else "${selected.size}개 선택 · 시작하기",
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
fun CategoryCard(option: CategoryOption, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
        ),
        border = if (isSelected) null
        else androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(option.emoji, fontSize = 28.sp)
                Text(
                    option.category.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    option.desc,
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}