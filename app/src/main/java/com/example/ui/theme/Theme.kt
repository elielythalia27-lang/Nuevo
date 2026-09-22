package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // isAppearanceLightStatusBars: true = dark icons (light theme), false = white icons (dark theme)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme

                // Sincronizar el color de la ventana nativa (decorView) exactamente con el fondo del tema
                // para evitar cualquier destello o parpadeo del sistema Android
                window.decorView.setBackgroundColor(colorScheme.background.toArgb())
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
