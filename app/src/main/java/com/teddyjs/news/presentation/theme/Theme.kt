package com.teddyjs.news.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green400 = Color(0xFF1D9E75)
val Green50  = Color(0xFFEAF3DE)
val Blue400  = Color(0xFF378ADD)
val Blue50   = Color(0xFFE6F1FB)
val Amber400 = Color(0xFFEF9F27)
val Amber50  = Color(0xFFFAEEDA)
val Red400   = Color(0xFFE24B4A)
val Red50    = Color(0xFFFCEBEB)
val Gray50   = Color(0xFFF1EFE8)
val Gray400  = Color(0xFF888780)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    secondary = Green400,
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Gray50,
    outline = Color(0xFFE0E0E0),
    error = Red400,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF1A1A1A),
    secondary = Green400,
    onSecondary = Color.White,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2C2C2C),
    outline = Color(0xFF3A3A3A),
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
