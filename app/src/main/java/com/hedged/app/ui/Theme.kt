package com.hedged.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Evergreen = Color(0xFF2E3440)
val Forest = Color(0xFF5E81AC)
val Mint = Color(0xFFA3BE8C)
val Sage = Color(0xFF8FBCBB)
val Canvas = Color(0xFFECEFF4)
val Ink = Color(0xFF2E3440)
val Muted = Color(0xFF4C566A)
val Line = Color(0xFFD8DEE9)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5E81AC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF88C0D0),
    onPrimaryContainer = Color(0xFF2E3440),
    secondary = Color(0xFF8FBCBB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8DEE9),
    onSecondaryContainer = Color(0xFF2E3440),
    tertiary = Color(0xFFB48EAD),
    tertiaryContainer = Color(0xFFEBCB8B),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFD8DEE9),
    onSurfaceVariant = Muted,
    outline = Color(0xFF81A1C1),
    outlineVariant = Line,
    error = Color(0xFFBF616A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF5E81AC),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF8FBCBB),
    onSecondary = Color(0xFF2E3440),
    background = Color(0xFF2E3440),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF3B4252),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF434C5E),
    onSurfaceVariant = Color(0xFFD8DEE9),
    outline = Color(0xFF81A1C1),
    outlineVariant = Color(0xFF4C566A),
)

private val HedgedTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)

@Composable
fun HedgedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = HedgedTypography,
        content = content,
    )
}
