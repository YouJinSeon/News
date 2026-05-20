package com.teddyjs.news.presentation.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    val isSelected = selected.contains(option.category)
                    CategoryCard(
                        option = option,
                        isSelected = isSelected,
                        onClick = { viewModel.toggle(option.category) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.save(onDone) },
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
    }
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