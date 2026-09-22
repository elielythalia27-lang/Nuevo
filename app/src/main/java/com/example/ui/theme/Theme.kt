package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val duration = 220
    val spec = tween<Color>(durationMillis = duration, easing = FastOutSlowInEasing)

    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val onSecondary by animateColorAsState(target.onSecondary, spec, label = "onSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, spec, label = "onTertiary")
    val background by animateColorAsState(target.background, spec, label = "background")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "onBackground")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant")
    val outline by animateColorAsState(target.outline, spec, label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "outlineVariant")
    val error by animateColorAsState(target.error, spec, label = "error")
    val onError by animateColorAsState(target.onError, spec, label = "onError")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        error = error,
        onError = onError
    )
}

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

    val rawColorScheme = if (darkTheme) darkColorScheme else lightColorScheme
    val colorScheme = animateColorScheme(rawColorScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // isAppearanceLightStatusBars: true = iconos oscuros (tema claro), false = iconos blancos (tema oscuro)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
