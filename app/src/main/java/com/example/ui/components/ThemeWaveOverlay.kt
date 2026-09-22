package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

data class ThemeWaveData(
    val origin: Offset,
    val targetIsDark: Boolean,
    val accentColor: Color
)

/**
 * Subtle and elegant circular ripple wave overlay that expands smoothly
 * from the touched position, transitioning the theme without visual jarring.
 */
@Composable
fun ThemeWaveOverlay(
    waveData: ThemeWaveData?,
    onWaveHalfway: () -> Unit,
    onWaveFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (waveData == null) return

    val progress = remember(waveData) { Animatable(0f) }
    var halfwayTriggered by remember(waveData) { mutableStateOf(false) }

    val smoothEasing = remember { CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f) }

    LaunchedEffect(waveData) {
        progress.snapTo(0f)
        halfwayTriggered = false
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 480, easing = smoothEasing)
        ) {
            if (value >= 0.25f && !halfwayTriggered) {
                halfwayTriggered = true
                onWaveHalfway()
            }
        }
        onWaveFinished()
    }

    val accent = waveData.accentColor

    Canvas(modifier = modifier.fillMaxSize()) {
        val maxRadius = hypot(size.width, size.height) * 1.1f
        val currentRadius = maxRadius * progress.value
        val origin = if (waveData.origin == Offset.Zero) {
            Offset(size.width / 2f, size.height / 2f)
        } else {
            waveData.origin
        }

        val p = progress.value

        // 1. Subtle, translucent accent halo that gently sweeps across
        val haloAlpha = (0.16f * (1f - p)).coerceIn(0f, 0.16f)
        if (haloAlpha > 0f) {
            drawCircle(
                color = accent.copy(alpha = haloAlpha),
                radius = currentRadius,
                center = origin
            )
        }

        // 2. Refined, thin accent ring (clean outline wave, not thick/excessive)
        val ringAlpha = (0.55f * (1f - p)).coerceIn(0f, 0.55f)
        if (ringAlpha > 0f) {
            drawCircle(
                color = accent.copy(alpha = ringAlpha),
                radius = currentRadius,
                center = origin,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

