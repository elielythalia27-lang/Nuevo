package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
 * AppLauncherIconVector:
 * Exactly identical to the launcher icon (ic_launcher_foreground.xml).
 * Shows the emerald/teal anchor spine & download loop with the centered 3D ruby red play jewel.
 * Premium, clean vector drawing without black colors.
 */
@Composable
fun AppLauncherIconVector(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF009688),
    primaryVariant: Color = Color(0xFF004D40),
    isDark: Boolean = true
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 0. Soft Ambient Ground Shadow (Tonal, zero black)
        val groundShadow = Brush.radialGradient(
            colors = listOf(
                if (isDark) primaryVariant.copy(alpha = 0.35f) else primaryVariant.copy(alpha = 0.20f),
                Color.Transparent
            ),
            center = Offset(w * 0.50f, h * 0.77f),
            radius = w * 0.35f
        )
        drawOval(
            brush = groundShadow,
            topLeft = Offset(w * 0.28f, h * 0.73f),
            size = Size(w * 0.44f, h * 0.08f)
        )

        // 1a. 3D Isometric Anchor Spine - Back Depth Layer
        drawRoundRect(
            color = primaryVariant,
            topLeft = Offset(w * 0.287f, h * 0.273f),
            size = Size(w * 0.106f, h * 0.444f),
            cornerRadius = CornerRadius(w * 0.035f, h * 0.035f)
        )

        // 1b. 3D Isometric Anchor Spine - Front Face
        val spineGradient = Brush.linearGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.85f),
                primaryColor,
                primaryVariant
            ),
            start = Offset(w * 0.277f, h * 0.259f),
            end = Offset(w * 0.380f, h * 0.704f)
        )
        drawRoundRect(
            brush = spineGradient,
            topLeft = Offset(w * 0.277f, h * 0.259f),
            size = Size(w * 0.097f, h * 0.444f),
            cornerRadius = CornerRadius(w * 0.035f, h * 0.035f)
        )

        // 1c. Spine Top Highlight Chamfer
        drawRoundRect(
            color = Color(0x80FFFFFF),
            topLeft = Offset(w * 0.287f, h * 0.268f),
            size = Size(w * 0.088f, h * 0.023f),
            cornerRadius = CornerRadius(w * 0.02f, h * 0.02f)
        )

        // 2a. 3D Download/Media Loop - Depth Shadow Layer
        val loopDepthPath = Path().apply {
            moveTo(w * 0.380f, h * 0.282f)
            cubicTo(w * 0.528f, h * 0.282f, w * 0.690f, h * 0.329f, w * 0.727f, h * 0.458f)
            cubicTo(w * 0.764f, h * 0.579f, w * 0.699f, h * 0.681f, w * 0.597f, h * 0.727f)
            cubicTo(w * 0.523f, h * 0.755f, w * 0.444f, h * 0.736f, w * 0.389f, h * 0.736f)
            lineTo(w * 0.407f, h * 0.662f)
            cubicTo(w * 0.444f, h * 0.662f, w * 0.500f, h * 0.681f, w * 0.560f, h * 0.653f)
            cubicTo(w * 0.634f, h * 0.616f, w * 0.671f, h * 0.542f, w * 0.644f, h * 0.458f)
            cubicTo(w * 0.616f, h * 0.375f, w * 0.495f, h * 0.338f, w * 0.389f, h * 0.347f)
            close()
        }
        drawPath(path = loopDepthPath, color = primaryVariant)

        // 2b. 3D Download/Media Loop - Main Body
        val loopGradient = Brush.linearGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.85f),
                primaryColor,
                primaryVariant
            ),
            start = Offset(w * 0.370f, h * 0.259f),
            end = Offset(w * 0.722f, h * 0.713f)
        )
        val loopPath = Path().apply {
            moveTo(w * 0.370f, h * 0.259f)
            cubicTo(w * 0.519f, h * 0.259f, w * 0.681f, h * 0.306f, w * 0.718f, h * 0.435f)
            cubicTo(w * 0.755f, h * 0.556f, w * 0.690f, h * 0.657f, w * 0.588f, h * 0.704f)
            cubicTo(w * 0.514f, h * 0.731f, w * 0.435f, h * 0.713f, w * 0.380f, h * 0.713f)
            lineTo(w * 0.398f, h * 0.639f)
            cubicTo(w * 0.435f, h * 0.639f, w * 0.491f, h * 0.657f, w * 0.551f, h * 0.630f)
            cubicTo(w * 0.625f, h * 0.593f, w * 0.662f, h * 0.519f, w * 0.634f, h * 0.435f)
            cubicTo(w * 0.606f, h * 0.352f, w * 0.486f, h * 0.315f, w * 0.380f, h * 0.324f)
            close()
        }
        drawPath(path = loopPath, brush = loopGradient)

        // 2c. 3D Loop Top Specular Edge Highlight
        val loopHighlightPath = Path().apply {
            moveTo(w * 0.380f, h * 0.259f)
            cubicTo(w * 0.509f, h * 0.259f, w * 0.657f, h * 0.301f, w * 0.699f, h * 0.417f)
            cubicTo(w * 0.681f, h * 0.324f, w * 0.556f, h * 0.282f, w * 0.380f, h * 0.282f)
            close()
        }
        drawPath(path = loopHighlightPath, color = Color(0x90FFFFFF))

        // 3a. 3D Download Arrow Pointer - Depth Edge
        val arrowDepthPath = Path().apply {
            moveTo(w * 0.519f, h * 0.648f)
            lineTo(w * 0.606f, h * 0.727f)
            lineTo(w * 0.491f, h * 0.745f)
            lineTo(w * 0.509f, h * 0.657f)
            close()
        }
        drawPath(path = arrowDepthPath, color = primaryVariant)

        // 3b. 3D Download Arrow Pointer - Front
        val arrowFrontGradient = Brush.linearGradient(
            colors = listOf(primaryColor.copy(alpha = 0.85f), primaryColor, primaryVariant),
            start = Offset(w * 0.481f, h * 0.634f),
            end = Offset(w * 0.593f, h * 0.727f)
        )
        val arrowFrontPath = Path().apply {
            moveTo(w * 0.509f, h * 0.634f)
            lineTo(w * 0.593f, h * 0.708f)
            lineTo(w * 0.481f, h * 0.727f)
            lineTo(w * 0.500f, h * 0.644f)
            close()
        }
        drawPath(path = arrowFrontPath, brush = arrowFrontGradient)

        // 4a. 3D Ruby Red Play Button Jewel - Soft Colored Drop Shadow (Zero black)
        val playShadowBrush = Brush.linearGradient(
            colors = listOf(Color(0x35B71C1C), Color(0x10B71C1C)),
            start = Offset(w * 0.431f, h * 0.389f),
            end = Offset(w * 0.634f, h * 0.620f)
        )
        val playShadowPath = Path().apply {
            moveTo(w * 0.449f, h * 0.403f)
            cubicTo(w * 0.458f, h * 0.394f, w * 0.472f, h * 0.394f, w * 0.481f, h * 0.403f)
            lineTo(w * 0.625f, h * 0.486f)
            cubicTo(w * 0.639f, h * 0.495f, w * 0.639f, h * 0.519f, w * 0.625f, h * 0.528f)
            lineTo(w * 0.481f, h * 0.611f)
            cubicTo(w * 0.472f, h * 0.620f, w * 0.458f, h * 0.620f, w * 0.449f, h * 0.611f)
            cubicTo(w * 0.440f, h * 0.602f, w * 0.435f, h * 0.593f, w * 0.435f, h * 0.579f)
            lineTo(w * 0.435f, h * 0.435f)
            cubicTo(w * 0.435f, h * 0.421f, w * 0.440f, h * 0.412f, w * 0.449f, h * 0.403f)
            close()
        }
        drawPath(path = playShadowPath, brush = playShadowBrush)

        // 4b. 3D Ruby Red Play Button Jewel - 3D Bevel Body (Centered, vibrant red tones)
        val rubyBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF5252),
                Color(0xFFFF1744),
                Color(0xFFE53935),
                Color(0xFFC62828)
            ),
            start = Offset(w * 0.426f, h * 0.375f),
            end = Offset(w * 0.630f, h * 0.593f)
        )
        val playPath = Path().apply {
            moveTo(w * 0.440f, h * 0.384f)
            cubicTo(w * 0.449f, h * 0.375f, w * 0.463f, h * 0.375f, w * 0.472f, h * 0.384f)
            lineTo(w * 0.616f, h * 0.468f)
            cubicTo(w * 0.630f, h * 0.477f, w * 0.630f, h * 0.500f, w * 0.616f, h * 0.509f)
            lineTo(w * 0.472f, h * 0.593f)
            cubicTo(w * 0.463f, h * 0.602f, w * 0.449f, h * 0.602f, w * 0.440f, h * 0.593f)
            cubicTo(w * 0.431f, h * 0.583f, w * 0.426f, h * 0.574f, w * 0.426f, h * 0.560f)
            lineTo(w * 0.426f, h * 0.417f)
            cubicTo(w * 0.426f, h * 0.403f, w * 0.431f, h * 0.394f, w * 0.440f, h * 0.384f)
            close()
        }
        drawPath(path = playPath, brush = rubyBrush)

        // 4c. 3D Ruby Red Top Gloss Facet
        val glossBrush = Brush.linearGradient(
            colors = listOf(Color(0x80FFFFFF), Color(0x00FFFFFF)),
            start = Offset(w * 0.440f, h * 0.384f),
            end = Offset(w * 0.611f, h * 0.486f)
        )
        val playGlossPath = Path().apply {
            moveTo(w * 0.440f, h * 0.384f)
            lineTo(w * 0.611f, h * 0.486f)
            lineTo(w * 0.440f, h * 0.486f)
            close()
        }
        drawPath(path = playGlossPath, brush = glossBrush)

        // 4d. Specular Center Highlight Dot
        val sparklePath = Path().apply {
            moveTo(w * 0.454f, h * 0.412f)
            cubicTo(w * 0.454f, h * 0.405f, w * 0.461f, h * 0.405f, w * 0.466f, h * 0.409f)
            lineTo(w * 0.523f, h * 0.444f)
            cubicTo(w * 0.528f, h * 0.448f, w * 0.528f, h * 0.454f, w * 0.523f, h * 0.457f)
            lineTo(w * 0.466f, h * 0.493f)
            cubicTo(w * 0.461f, h * 0.497f, w * 0.454f, h * 0.497f, w * 0.454f, h * 0.490f)
            close()
        }
        drawPath(path = sparklePath, color = Color(0x40FFFFFF))
    }
}

/**
 * Startup Splash Screen synchronized with the selected App Theme and Color:
 * Displays the exact App Launcher Icon centered on screen, adapting smoothly
 * to dark and light mode without flash or artifacts.
 */
@Composable
fun SplashScreen(
    isDarkTheme: Boolean = true,
    themeColor: com.example.ui.theme.AppThemeColor = com.example.ui.theme.AppThemeColor.TEAL,
    onTimeout: () -> Unit
) {
    val themePrimary = themeColor.primaryForTheme(isDarkTheme)
    val themeVariant = themeColor.primaryVariantForTheme(isDarkTheme)
    val themeBackground = if (isDarkTheme) Color(0xFF0B1120) else Color(0xFFF8FAFC)

    val logoScale = remember { Animatable(0.70f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Smooth entrance animation
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
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

        // Display before transitioning to main content
        delay(1500)

        // Seamless exit transition
        launch {
            logoScale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
            logoAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
        }

        delay(240)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBackground)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        val emblemBg = if (isDarkTheme) Color(0xFF131D31) else Color.White
        val emblemBorder = if (isDarkTheme) {
            BorderStroke(1.5.dp, themePrimary.copy(alpha = 0.35f))
        } else {
            BorderStroke(1.5.dp, themePrimary.copy(alpha = 0.25f))
        }

        // App Icon Emblem: Matches the exact app launcher icon styling with theme-compatible background
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = emblemBg,
            border = emblemBorder,
            shadowElevation = if (isDarkTheme) 16.dp else 10.dp,
            modifier = Modifier
                .size(136.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
                .shadow(
                    elevation = if (isDarkTheme) 20.dp else 12.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = themePrimary.copy(alpha = 0.40f),
                    ambientColor = if (isDarkTheme) themePrimary.copy(alpha = 0.20f) else Color(0x15000000)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AppLauncherIconVector(
                    primaryColor = themePrimary,
                    primaryVariant = themeVariant,
                    isDark = isDarkTheme,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
