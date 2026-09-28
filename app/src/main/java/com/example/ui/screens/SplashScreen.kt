package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElielFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clean & Refined App Emblem Vector
 */
@Composable
fun AppLogoVector(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF00F5D4),
    isDark: Boolean = true
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Film strip spine
        val spineGradient = Brush.linearGradient(
            colors = listOf(
                primaryColor,
                primaryColor.copy(alpha = 0.85f),
                if (isDark) Color(0xFF0F766E) else Color(0xFF0D9488)
            ),
            start = Offset(w * 0.22f, h * 0.24f),
            end = Offset(w * 0.36f, h * 0.76f)
        )
        drawRoundRect(
            brush = spineGradient,
            topLeft = Offset(w * 0.22f, h * 0.24f),
            size = Size(w * 0.12f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.04f, h * 0.04f)
        )

        // Sprocket holes
        val sprocketColor = if (isDark) Color(0xFF0A0F1D) else Color(0xFFE2E8F0)
        listOf(0.28f, 0.42f, 0.56f).forEach { pos ->
            drawRoundRect(
                color = sprocketColor,
                topLeft = Offset(w * 0.245f, h * pos),
                size = Size(w * 0.045f, h * 0.06f),
                cornerRadius = CornerRadius(w * 0.012f, h * 0.012f)
            )
        }

        // Dynamic Streamline "D" Ribbon
        val loopGradient = Brush.linearGradient(
            colors = listOf(
                primaryColor,
                Color(0xFF00F5D4),
                Color(0xFF06B6D4)
            ),
            start = Offset(w * 0.32f, h * 0.24f),
            end = Offset(w * 0.84f, h * 0.76f)
        )
        val loopPath = Path().apply {
            moveTo(w * 0.32f, h * 0.24f)
            lineTo(w * 0.54f, h * 0.24f)
            cubicTo(w * 0.72f, h * 0.24f, w * 0.84f, h * 0.37f, w * 0.84f, h * 0.50f)
            cubicTo(w * 0.84f, h * 0.65f, w * 0.72f, h * 0.76f, w * 0.54f, h * 0.76f)
            lineTo(w * 0.32f, h * 0.76f)
            lineTo(w * 0.32f, h * 0.68f)
            lineTo(w * 0.54f, h * 0.68f)
            cubicTo(w * 0.65f, h * 0.68f, w * 0.74f, h * 0.60f, w * 0.74f, h * 0.50f)
            cubicTo(w * 0.74f, h * 0.40f, w * 0.65f, h * 0.32f, w * 0.54f, h * 0.32f)
            lineTo(w * 0.32f, h * 0.32f)
            close()
        }
        drawPath(path = loopPath, brush = loopGradient)

        // Ruby Crimson Play Core
        val rubyBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF3366),
                Color(0xFFE50914),
                Color(0xFFB71C1C)
            ),
            start = Offset(w * 0.38f, h * 0.36f),
            end = Offset(w * 0.70f, h * 0.64f)
        )
        val playPath = Path().apply {
            moveTo(w * 0.42f, h * 0.38f)
            lineTo(w * 0.64f, h * 0.50f)
            lineTo(w * 0.42f, h * 0.62f)
            close()
        }
        drawPath(path = playPath, brush = rubyBrush)

        // Download Chevron Accent
        val arrowHead = Path().apply {
            moveTo(w * 0.48f, h * 0.70f)
            lineTo(w * 0.53f, h * 0.76f)
            lineTo(w * 0.58f, h * 0.70f)
        }
        drawPath(
            path = arrowHead,
            color = Color.White.copy(alpha = 0.95f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Fast, Clean & Snappy Startup Screen.
 * Automatically respects active dark/light theme and color palette.
 */
@Composable
fun SplashScreen(
    isDarkTheme: Boolean = true,
    onTimeout: () -> Unit
) {
    val themePrimary = MaterialTheme.colorScheme.primary

    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Entrance animation
        launch {
            logoAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
        launch {
            delay(150)
            contentAlpha.animateTo(1f, tween(250))
        }
        launch {
            progressAnim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }

        delay(700)
        // Quick exit
        launch {
            logoAlpha.animateTo(0f, tween(180))
            contentAlpha.animateTo(0f, tween(150))
        }
        delay(180)
        onTimeout()
    }

    val bgGradient = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0C1322),
                Color(0xFF070B14)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF8FAFC),
                Color(0xFFEEF2F6)
            )
        )
    }

    val plateBg = if (isDarkTheme) Color(0xFF131D31) else Color.White
    val titleTextColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val subtitleTextColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Emblem Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = plateBg,
                border = BorderStroke(
                    1.2.dp,
                    if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)
                ),
                shadowElevation = if (isDarkTheme) 14.dp else 6.dp,
                modifier = Modifier
                    .size(112.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
                    .shadow(
                        elevation = if (isDarkTheme) 16.dp else 8.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = themePrimary.copy(alpha = 0.35f)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppLogoVector(
                        modifier = Modifier.fillMaxSize(),
                        primaryColor = themePrimary,
                        isDark = isDarkTheme
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Title & Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(contentAlpha.value)
            ) {
                Text(
                    text = "Download Free",
                    fontFamily = ElielFont,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleTextColor,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Películas, Series & Videos",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = subtitleTextColor
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Snappy Slim Progress Line
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isDarkTheme) Color.White.copy(alpha = 0.10f) else Color(0xFFCBD5E1)
                    )
                    .alpha(contentAlpha.value)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressAnim.value)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(themePrimary, Color(0xFFFF3366))
                            )
                        )
                )
            }
        }
    }
}
