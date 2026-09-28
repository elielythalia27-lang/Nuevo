package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dynamic App Logo Vector:
 * - Pure White Background
 * - Dynamically synchronizes with the active theme's chosen primary color
 * - Features the iconic red play button and dynamic theme media loop
 */
@Composable
fun AppLogoVector(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF009688),
    isDark: Boolean = true
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Dynamic Anchor Pillar (Synchronized with chosen theme color)
        val spineGradient = Brush.linearGradient(
            colors = listOf(
                primaryColor,
                primaryColor.copy(alpha = 0.85f),
                if (isDark) primaryColor.copy(alpha = 0.65f) else primaryColor.copy(alpha = 0.90f)
            ),
            start = Offset(w * 0.24f, h * 0.25f),
            end = Offset(w * 0.36f, h * 0.75f)
        )
        drawRoundRect(
            brush = spineGradient,
            topLeft = Offset(w * 0.24f, h * 0.25f),
            size = Size(w * 0.11f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.035f, h * 0.035f)
        )

        // 2. Dynamic Streamline Media Curve Loop (Synchronized with chosen theme color)
        val loopGradient = Brush.linearGradient(
            colors = listOf(
                primaryColor,
                primaryColor.copy(alpha = 0.90f),
                if (isDark) primaryColor.copy(alpha = 0.70f) else primaryColor.copy(alpha = 0.85f)
            ),
            start = Offset(w * 0.34f, h * 0.25f),
            end = Offset(w * 0.82f, h * 0.75f)
        )
        val loopPath = Path().apply {
            moveTo(w * 0.34f, h * 0.25f)
            lineTo(w * 0.55f, h * 0.25f)
            cubicTo(w * 0.72f, h * 0.25f, w * 0.82f, h * 0.38f, w * 0.82f, h * 0.50f)
            cubicTo(w * 0.82f, h * 0.64f, w * 0.72f, h * 0.75f, w * 0.55f, h * 0.75f)
            lineTo(w * 0.34f, h * 0.75f)
            lineTo(w * 0.34f, h * 0.67f)
            lineTo(w * 0.55f, h * 0.67f)
            cubicTo(w * 0.65f, h * 0.67f, w * 0.73f, h * 0.59f, w * 0.73f, h * 0.50f)
            cubicTo(w * 0.73f, h * 0.41f, w * 0.65f, h * 0.33f, w * 0.55f, h * 0.33f)
            lineTo(w * 0.34f, h * 0.33f)
            close()
        }
        drawPath(path = loopPath, brush = loopGradient)

        // 3. Dynamic Download Arrow Pointer
        val arrowPath = Path().apply {
            moveTo(w * 0.54f, h * 0.67f)
            lineTo(w * 0.63f, h * 0.75f)
            lineTo(w * 0.51f, h * 0.77f)
            close()
        }
        val arrowGradient = Brush.linearGradient(
            colors = listOf(primaryColor, primaryColor.copy(alpha = 0.75f)),
            start = Offset(w * 0.51f, h * 0.67f),
            end = Offset(w * 0.63f, h * 0.77f)
        )
        drawPath(path = arrowPath, brush = arrowGradient)

        // 4. Ruby Red Cinema Play Button
        val rubyBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF1744),
                Color(0xFFE53935),
                Color(0xFFC62828)
            ),
            start = Offset(w * 0.40f, h * 0.38f),
            end = Offset(w * 0.68f, h * 0.62f)
        )
        val playPath = Path().apply {
            moveTo(w * 0.42f, h * 0.39f)
            lineTo(w * 0.65f, h * 0.50f)
            lineTo(w * 0.42f, h * 0.61f)
            close()
        }
        drawPath(path = playPath, brush = rubyBrush)

        // Inner Red Facet for depth
        val playInnerPath = Path().apply {
            moveTo(w * 0.43f, h * 0.42f)
            lineTo(w * 0.60f, h * 0.50f)
            lineTo(w * 0.43f, h * 0.58f)
            close()
        }
        drawPath(path = playInnerPath, color = Color(0xFFD32F2F))
    }
}

/**
 * Startup Screen synchronized with the selected App Theme and Color:
 * Exclusively displays the App Icon centered on screen, adapting dynamically
 * to the user's chosen theme color and dark/light mode.
 */
@Composable
fun SplashScreen(
    isDarkTheme: Boolean = true,
    onTimeout: () -> Unit
) {
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeBackground = MaterialTheme.colorScheme.background

    val logoScale = remember { Animatable(0.65f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Smooth entrance animation
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        // Display for a couple seconds before opening the app
        delay(1600)

        // Seamless exit transition
        launch {
            logoScale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
            logoAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        }

        delay(250)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBackground)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // App Icon Emblem: White background container with dynamic theme colors
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            shadowElevation = if (isDarkTheme) 20.dp else 12.dp,
            modifier = Modifier
                .size(136.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
                .shadow(
                    elevation = if (isDarkTheme) 24.dp else 14.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = themePrimary.copy(alpha = 0.45f),
                    ambientColor = if (isDarkTheme) themePrimary.copy(alpha = 0.25f) else Color(0xFFE53935).copy(alpha = 0.20f)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                AppLogoVector(
                    primaryColor = themePrimary,
                    isDark = isDarkTheme,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
