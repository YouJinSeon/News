package com.teddyjs.news.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 브랜드 메인 — 오렌지
val Orange   = Color(0xFFFF8F00)
val Orange50 = Color(0xFFFFF3E0)
// 카테고리/매크로 팔레트
val Green400 = Color(0xFF1D9E75)
val Green50  = Color(0xFFE3F4EC)
val Blue400  = Color(0xFF378ADD)
val Blue50   = Color(0xFFE9F1FB)
val Amber400 = Color(0xFFEF9F27)
val Amber50  = Color(0xFFFFF3E0)
val Red400   = Color(0xFFE24B4A)
val Red50    = Color(0xFFFCEBEB)
val Gray50   = Color(0xFFF2F3F5)
val Gray400  = Color(0xFF8A8D93)

private val LightColorScheme = lightColorScheme(
    primary = Orange,                       // 메인 강조 = 오렌지(CTA·선택 상태)
    onPrimary = Color.White,
    primaryContainer = Orange50,
    onPrimaryContainer = Color(0xFF8A4B00),
    secondary = Green400,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),          // 쿨 화이트 배경
    surface = Color.White,                   // 카드 = 흰색
    onBackground = Color(0xFF1A1B1E),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Gray50,                 // 칩·노트 배경
    onSurfaceVariant = Color(0xFF5A5D63),
    outline = Color(0xFFECEDEF),             // 얇은 헤어라인 보더
    error = Red400,
)

private val DarkColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2A10),
    onPrimaryContainer = Orange50,
    secondary = Green400,
    onSecondary = Color.White,
    background = Color(0xFF121316),
    surface = Color(0xFF1C1D21),
    onBackground = Color(0xFFE8E8EA),
    onSurface = Color(0xFFE8E8EA),
    surfaceVariant = Color(0xFF2A2B30),
    onSurfaceVariant = Color(0xFFB5B7BD),
    outline = Color(0xFF34353A),
    error = Red400,
)

@Composable
fun NewsAppTheme(
    darkTheme: Boolean = false,  // ← 기본값
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
