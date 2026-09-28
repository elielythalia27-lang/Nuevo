package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
 * - Synchronizes with the active theme's chosen primary color and Dark/Light mode
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

        // 0. 3D Soft Ambient Ground Shadow
        val groundShadow = Brush.radialGradient(
            colors = listOf(
                if (isDark) Color(0x50000000) else Color(0x35004D40),
                Color.Transparent
            ),
            center = Offset(w * 0.50f, h * 0.88f),
            radius = w * 0.40f
        )
        drawOval(
            brush = groundShadow,
            topLeft = Offset(w * 0.15f, h * 0.82f),
            size = Size(w * 0.70f, h * 0.12f)
        )

        // 1. 3D Dynamic Anchor Pillar - Back Depth Layer
        drawRoundRect(
            color = if (isDark) Color(0xFF00382E) else Color(0xFF004D40),
            topLeft = Offset(w * 0.25f, h * 0.26f),
            size = Size(w * 0.11f, h * 0.51f),
            cornerRadius = CornerRadius(w * 0.035f, h * 0.035f)
        )

        // 1b. 3D Dynamic Anchor Pillar - Front Face with Gloss Gradient
        val spineGradient = Brush.linearGradient(
            colors = listOf(
                primaryColor.copy(alpha = 1f),
                primaryColor.copy(alpha = 0.85f),
                Color(0xFF00695C)
            ),
            start = Offset(w * 0.23f, h * 0.24f),
            end = Offset(w * 0.35f, h * 0.75f)
        )
        drawRoundRect(
            brush = spineGradient,
            topLeft = Offset(w * 0.23f, h * 0.24f),
            size = Size(w * 0.11f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.035f, h * 0.035f)
        )

        // 1c. 3D Anchor Pillar - Top Chamfer Highlight
        drawRoundRect(
            color = Color(0x70FFFFFF),
            topLeft = Offset(w * 0.23f, h * 0.24f),
            size = Size(w * 0.11f, h * 0.06f),
            cornerRadius = CornerRadius(w * 0.035f, h * 0.035f)
        )

        // 2. 3D Dynamic Media Curve Loop - Depth Layer
        val loopDepthPath = Path().apply {
            moveTo(w * 0.35f, h * 0.27f)
            lineTo(w * 0.56f, h * 0.27f)
            cubicTo(w * 0.73f, h * 0.27f, w * 0.83f, h * 0.40f, w * 0.83f, h * 0.52f)
            cubicTo(w * 0.83f, h * 0.66f, w * 0.73f, h * 0.77f, w * 0.56f, h * 0.77f)
            lineTo(w * 0.35f, h * 0.77f)
            lineTo(w * 0.35f, h * 0.69f)
            lineTo(w * 0.56f, h * 0.69f)
            cubicTo(w * 0.66f, h * 0.69f, w * 0.74f, h * 0.61f, w * 0.74f, h * 0.52f)
            cubicTo(w * 0.74f, h * 0.43f, w * 0.66f, h * 0.35f, w * 0.56f, h * 0.35f)
            lineTo(w * 0.35f, h * 0.35f)
            close()
        }
        drawPath(path = loopDepthPath, color = Color(0xFF00382E))

        // 2b. 3D Dynamic Media Curve Loop - Main Face
        val loopGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF1DE9B6),
                primaryColor,
                Color(0xFF004D40)
            ),
            start = Offset(w * 0.33f, h * 0.24f),
            end = Offset(w * 0.82f, h * 0.76f)
        )
        val loopPath = Path().apply {
            moveTo(w * 0.33f, h * 0.24f)
            lineTo(w * 0.55f, h * 0.24f)
            cubicTo(w * 0.72f, h * 0.24f, w * 0.82f, h * 0.37f, w * 0.82f, h * 0.49f)
            cubicTo(w * 0.82f, h * 0.63f, w * 0.72f, h * 0.74f, w * 0.55f, h * 0.74f)
            lineTo(w * 0.33f, h * 0.74f)
            lineTo(w * 0.33f, h * 0.66f)
            lineTo(w * 0.55f, h * 0.66f)
            cubicTo(w * 0.65f, h * 0.66f, w * 0.73f, h * 0.58f, w * 0.73f, h * 0.49f)
            cubicTo(w * 0.73f, h * 0.40f, w * 0.65f, h * 0.32f, w * 0.55f, h * 0.32f)
            lineTo(w * 0.33f, h * 0.32f)
            close()
        }
        drawPath(path = loopPath, brush = loopGradient)

        // 2c. 3D Loop Top Specular Highlight
        val highlightPath = Path().apply {
            moveTo(w * 0.34f, h * 0.24f)
            lineTo(w * 0.55f, h * 0.24f)
            cubicTo(w * 0.68f, h * 0.24f, w * 0.78f, h * 0.33f, w * 0.80f, h * 0.45f)
            cubicTo(w * 0.77f, h * 0.35f, w * 0.65f, h * 0.27f, w * 0.55f, h * 0.27f)
            lineTo(w * 0.34f, h * 0.27f)
            close()
        }
        drawPath(path = highlightPath, color = Color(0x90FFFFFF))

        // 3. 3D Download Arrow Pointer - Depth Edge
        val arrowDepthPath = Path().apply {
            moveTo(w * 0.55f, h * 0.68f)
            lineTo(w * 0.65f, h * 0.77f)
            lineTo(w * 0.52f, h * 0.79f)
            close()
        }
        drawPath(path = arrowDepthPath, color = Color(0xFF00332A))

        // 3b. 3D Download Arrow Pointer - Front Face
        val arrowPath = Path().apply {
            moveTo(w * 0.54f, h * 0.66f)
            lineTo(w * 0.64f, h * 0.75f)
            lineTo(w * 0.51f, h * 0.77f)
            close()
        }
        val arrowGradient = Brush.linearGradient(
            colors = listOf(Color(0xFF00E676), primaryColor, Color(0xFF00796B)),
            start = Offset(w * 0.51f, h * 0.66f),
            end = Offset(w * 0.64f, h * 0.77f)
        )
        drawPath(path = arrowPath, brush = arrowGradient)

        // 4. 3D Ruby Red Cinema Play Button - Soft Shadow
        val rubyShadowPath = Path().apply {
            moveTo(w * 0.43f, h * 0.40f)
            lineTo(w * 0.67f, h * 0.52f)
            lineTo(w * 0.43f, h * 0.64f)
            close()
        }
        drawPath(path = rubyShadowPath, color = Color(0x40000000))

        // 4b. 3D Ruby Red Cinema Play Button - 3D Jewel Body
        val rubyBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF5252),
                Color(0xFFFF1744),
                Color(0xFFD50000),
                Color(0xFF8B0000)
            ),
            start = Offset(w * 0.41f, h * 0.38f),
            end = Offset(w * 0.66f, h * 0.63f)
        )
        val playPath = Path().apply {
            moveTo(w * 0.41f, h * 0.38f)
            lineTo(w * 0.65f, h * 0.50f)
            lineTo(w * 0.41f, h * 0.62f)
            close()
        }
        drawPath(path = playPath, brush = rubyBrush)

        // 4c. 3D Ruby Red - Top Specular Bevel
        val playGlossPath = Path().apply {
            moveTo(w * 0.41f, h * 0.38f)
            lineTo(w * 0.65f, h * 0.50f)
            lineTo(w * 0.41f, h * 0.50f)
            close()
        }
        val glossBrush = Brush.linearGradient(
            colors = listOf(Color(0x80FFFFFF), Color.Transparent),
            start = Offset(w * 0.41f, h * 0.38f),
            end = Offset(w * 0.65f, h * 0.50f)
        )
        drawPath(path = playGlossPath, brush = glossBrush)

        // 4d. Specular Center Sparkle
        val playInnerPath = Path().apply {
            moveTo(w * 0.43f, h * 0.42f)
            lineTo(w * 0.54f, h * 0.48f)
            lineTo(w * 0.43f, h * 0.54f)
            close()
        }
        drawPath(path = playInnerPath, color = Color(0x35FFFFFF))
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
        val emblemBg = if (isDarkTheme) Color(0xFF131D33) else Color.White
        val emblemBorder = if (isDarkTheme) {
            BorderStroke(1.5.dp, Color.White.copy(alpha = 0.12f))
        } else {
            BorderStroke(1.dp, Color(0xFFE2E8F0))
        }

        // App Icon Emblem: Adapts background and accents cleanly according to current theme
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = emblemBg,
            border = emblemBorder,
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
