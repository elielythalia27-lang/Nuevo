package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    themeColor: AppThemeColor = AppThemeColor.TEAL,
    content: @Composable () -> Unit
) {
    val darkPrimary = themeColor.primaryForTheme(isDarkTheme = true)
    val darkVariant = themeColor.primaryVariantForTheme(isDarkTheme = true)
    val darkOnPrimary = darkPrimary.contrastingTextColor()
    val darkOnPrimaryContainer = darkVariant.contrastingTextColor()

    val lightPrimary = themeColor.primaryForTheme(isDarkTheme = false)
    val lightVariant = themeColor.primaryVariantForTheme(isDarkTheme = false)
    val lightOnPrimary = lightPrimary.contrastingTextColor()
    val lightOnPrimaryContainer = lightVariant.contrastingTextColor()

    val darkColorScheme = darkColorScheme(
        primary = darkPrimary,
        onPrimary = darkOnPrimary,
        primaryContainer = if (darkPrimary.luminance() > 0.8f) Color(0xFF1E293B) else darkVariant,
        onPrimaryContainer = if (darkPrimary.luminance() > 0.8f) Color.White else darkOnPrimaryContainer,
        secondary = darkPrimary,
        onSecondary = darkOnPrimary,
        secondaryContainer = if (darkPrimary.luminance() > 0.8f) Color(0xFF334155) else darkPrimary.copy(alpha = 0.22f),
        onSecondaryContainer = Color.White,
        tertiary = CineYellow,
        onTertiary = Color.Black,
        error = CineRed,
        onError = Color.White,
        background = DarkBg,
        onBackground = DarkTextPrimary,
        surface = DarkSurface,
        onSurface = DarkTextPrimary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkTextSecondary
    )

    val lightColorScheme = lightColorScheme(
        primary = lightPrimary,
        onPrimary = lightOnPrimary,
        primaryContainer = if (lightPrimary.luminance() < 0.2f) Color(0xFFE2E8F0) else lightVariant,
        onPrimaryContainer = if (lightPrimary.luminance() < 0.2f) Color(0xFF0F172A) else lightOnPrimaryContainer,
        secondary = lightPrimary,
        onSecondary = lightOnPrimary,
        secondaryContainer = if (lightPrimary.luminance() < 0.2f) Color(0xFFF1F5F9) else lightPrimary.copy(alpha = 0.15f),
        onSecondaryContainer = LightTextPrimary,
        tertiary = Color(0xFFB45309),
        onTertiary = Color.White,
        error = CineRed,
        onError = Color.White,
        background = LightBg,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightTextSecondary
    )

    val rawTargetScheme = if (darkTheme) darkColorScheme else lightColorScheme

    val animSpec = tween<Color>(durationMillis = 240)
    val animatedPrimary by animateColorAsState(rawTargetScheme.primary, animSpec, label = "theme_primary")
    val animatedOnPrimary by animateColorAsState(rawTargetScheme.onPrimary, animSpec, label = "theme_onPrimary")
    val animatedPrimaryContainer by animateColorAsState(rawTargetScheme.primaryContainer, animSpec, label = "theme_primaryContainer")
    val animatedOnPrimaryContainer by animateColorAsState(rawTargetScheme.onPrimaryContainer, animSpec, label = "theme_onPrimaryContainer")
    val animatedSecondary by animateColorAsState(rawTargetScheme.secondary, animSpec, label = "theme_secondary")
    val animatedOnSecondary by animateColorAsState(rawTargetScheme.onSecondary, animSpec, label = "theme_onSecondary")
    val animatedSecondaryContainer by animateColorAsState(rawTargetScheme.secondaryContainer, animSpec, label = "theme_secondaryContainer")
    val animatedOnSecondaryContainer by animateColorAsState(rawTargetScheme.onSecondaryContainer, animSpec, label = "theme_onSecondaryContainer")
    val animatedBackground by animateColorAsState(rawTargetScheme.background, animSpec, label = "theme_background")
    val animatedOnBackground by animateColorAsState(rawTargetScheme.onBackground, animSpec, label = "theme_onBackground")
    val animatedSurface by animateColorAsState(rawTargetScheme.surface, animSpec, label = "theme_surface")
    val animatedOnSurface by animateColorAsState(rawTargetScheme.onSurface, animSpec, label = "theme_onSurface")
    val animatedSurfaceVariant by animateColorAsState(rawTargetScheme.surfaceVariant, animSpec, label = "theme_surfaceVariant")
    val animatedOnSurfaceVariant by animateColorAsState(rawTargetScheme.onSurfaceVariant, animSpec, label = "theme_onSurfaceVariant")

    val colorScheme = rawTargetScheme.copy(
        primary = animatedPrimary,
        onPrimary = animatedOnPrimary,
        primaryContainer = animatedPrimaryContainer,
        onPrimaryContainer = animatedOnPrimaryContainer,
        secondary = animatedSecondary,
        onSecondary = animatedOnSecondary,
        secondaryContainer = animatedSecondaryContainer,
        onSecondaryContainer = animatedOnSecondaryContainer,
        background = animatedBackground,
        onBackground = animatedOnBackground,
        surface = animatedSurface,
        onSurface = animatedOnSurface,
        surfaceVariant = animatedSurfaceVariant,
        onSurfaceVariant = animatedOnSurfaceVariant
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            // isAppearanceLightStatusBars: true = dark icons (light theme), false = white icons (dark theme)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
