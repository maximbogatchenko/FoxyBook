package com.foxybook.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
fun AgonAppTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme() // "system"
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
