package com.foxybook.app.ui.theme

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = FoxPrimary,
    onPrimary = FoxOnPrimary,
    primaryContainer = FoxPrimaryContainer,
    onPrimaryContainer = FoxOnPrimaryContainer,
    secondary = FoxSecondary,
    onSecondary = FoxOnSecondary,
    secondaryContainer = FoxSecondaryContainer,
    onSecondaryContainer = FoxOnSecondaryContainer,
    tertiary = FoxTertiary,
    onTertiary = FoxOnTertiary,
    tertiaryContainer = FoxTertiaryContainer,
    onTertiaryContainer = FoxOnTertiaryContainer,
    background = FoxBackground,
    onBackground = FoxOnBackground,
    surface = FoxSurface,
    onSurface = FoxOnSurface,
    surfaceVariant = FoxSurfaceVariant,
    onSurfaceVariant = FoxOnSurfaceVariant,
    outline = FoxOutline,
    outlineVariant = FoxOutlineVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = FoxPrimaryDark,
    onPrimary = FoxOnPrimaryDark,
    primaryContainer = FoxPrimaryContainerDark,
    onPrimaryContainer = FoxOnPrimaryContainerDark,
    secondary = FoxSecondaryDark,
    onSecondary = FoxOnSecondaryDark,
    secondaryContainer = FoxSecondaryContainerDark,
    onSecondaryContainer = FoxOnSecondaryContainerDark,
    tertiary = FoxTertiaryDark,
    onTertiary = FoxOnTertiaryDark,
    tertiaryContainer = FoxTertiaryContainerDark,
    onTertiaryContainer = FoxOnTertiaryContainerDark,
    background = FoxBackgroundDark,
    onBackground = FoxOnBackgroundDark,
    surface = FoxSurfaceDark,
    onSurface = FoxOnSurfaceDark,
    surfaceVariant = FoxSurfaceVariantDark,
    onSurfaceVariant = FoxOnSurfaceVariantDark,
    outline = FoxOutlineDark,
    outlineVariant = FoxOutlineVariantDark,
)

private val AmoledColorScheme = darkColorScheme(
    primary = FoxPrimaryDark,
    onPrimary = FoxOnPrimaryDark,
    primaryContainer = FoxPrimaryContainerDark,
    onPrimaryContainer = FoxOnPrimaryContainerDark,
    secondary = FoxSecondaryDark,
    onSecondary = FoxOnSecondaryDark,
    secondaryContainer = FoxSecondaryContainerDark,
    onSecondaryContainer = FoxOnSecondaryContainerDark,
    tertiary = FoxTertiaryDark,
    onTertiary = FoxOnTertiaryDark,
    tertiaryContainer = FoxTertiaryContainerDark,
    onTertiaryContainer = FoxOnTertiaryContainerDark,
    background = FoxBackgroundAmoled,
    onBackground = FoxOnBackgroundAmoled,
    surface = FoxSurfaceAmoled,
    onSurface = FoxOnSurfaceAmoled,
    surfaceVariant = FoxSurfaceVariantAmoled,
    onSurfaceVariant = FoxOnSurfaceVariantAmoled,
    outline = FoxOutlineDark,
    outlineVariant = FoxOutlineVariantDark,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
fun FoxyBookAppTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        "amoled" -> true
        else -> isSystemInDarkTheme() // "system"
    }

    val colorScheme = when (themeMode) {
        "amoled" -> AmoledColorScheme
        "dark" -> DarkColorScheme
        "light" -> LightColorScheme
        else -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    // Sync status bar icon appearance with the app theme
    LaunchedEffect(useDarkTheme) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
    }

    // Theme switch animation — a brief fade-through overlay
    val overlayAlpha = remember { Animatable(0f) }
    LaunchedEffect(useDarkTheme) {
        overlayAlpha.snapTo(1f)
        overlayAlpha.animateTo(0f, animationSpec = tween(400))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
        if (overlayAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (useDarkTheme) Color.Black.copy(alpha = overlayAlpha.value * 0.7f)
                        else Color.White.copy(alpha = overlayAlpha.value * 0.7f)
                    )
            )
        }
    }
}
