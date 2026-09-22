package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Modern Sleek Animated Linear Progress Bar with glowing multi-stop gradient fill,
 * traveling sheen highlight, and dynamic light/dark mode adaptation.
 */
@Composable
fun SleekLinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    isDarkTheme: Boolean = true,
    trackColor: Color? = null,
    color: Color? = null,
    gradientColors: List<Color> = listOf(
        Color(0xFF38BDF8),
        Color(0xFF2563EB),
        Color(0xFF8B5CF6)
    )
) {
    val effectiveTrackColor = trackColor ?: if (isDarkTheme) Color(0x33334155) else Color(0xFFE2E8F0)
    val effectiveGradient = if (color != null) listOf(color, color) else gradientColors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "progress_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val sheenTranslate by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheen_translate"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(effectiveTrackColor)
    ) {
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(height / 2))
                    .background(Brush.horizontalGradient(effectiveGradient))
            ) {
                // Subtle traveling highlight sheen for premium polish
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.4f),
                                    Color.Transparent
                                ),
                                start = Offset(sheenTranslate - 120f, 0f),
                                end = Offset(sheenTranslate, 0f)
                            )
                        )
                )
            }
        }
    }
}

/**
 * Determinate circular progress indicator with gradient stroke and smooth animation.
 */
@Composable
fun SleekDeterminateCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    strokeWidth: Dp = 3.5.dp,
    isDarkTheme: Boolean = true,
    gradientColors: List<Color> = listOf(
        Color(0xFF38BDF8),
        Color(0xFF2563EB)
    ),
    trackColor: Color? = null
) {
    val effectiveTrackColor = trackColor ?: if (isDarkTheme) Color(0x2E38BDF8) else Color(0x33000000)
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "determinate_circ_progress"
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        // Base Track
        drawCircle(
            color = effectiveTrackColor,
            style = stroke
        )
        // Filled Sweep
        if (animatedProgress > 0.01f) {
            drawArc(
                brush = Brush.sweepGradient(gradientColors),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = stroke
            )
        }
    }
}

/**
 * Custom High-End Circular Loading Spinner with gradient sweep and glowing round cap.
 */
@Composable
fun SleekCircularProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    strokeWidth: Dp = 3.dp,
    isDarkTheme: Boolean = true,
    gradientColors: List<Color> = listOf(
        Color(0xFF38BDF8),
        Color(0xFF2563EB),
        Color(0xFF8B5CF6)
    ),
    trackColor: Color? = null
) {
    val effectiveTrackColor = trackColor ?: if (isDarkTheme) Color(0x2238BDF8) else Color(0x22000000)
    val transition = rememberInfiniteTransition(label = "circular_transition")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "rotation_anim"
    )

    Canvas(modifier = modifier.size(size)) {
        val sweepAngle = 270f
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        // Track ring
        drawCircle(
            color = effectiveTrackColor,
            style = stroke
        )
        // Animated gradient arc
        drawArc(
            brush = Brush.sweepGradient(gradientColors),
            startAngle = rotation,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = stroke
        )
    }
}

/**
 * Premium Interactive Video Player Progress Scrub Bar with multi-layer buffered track,
 * glowing gradient played progress, animated thumb magnification, and floating seek time bubble.
 */
@Composable
fun SleekVideoPlayerProgressBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    bufferedColor: Color = Color.White.copy(alpha = 0.38f),
    trackColor: Color = Color.White.copy(alpha = 0.20f),
    onSeekingChange: (Boolean) -> Unit = {}
) {
    val totalDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (isDragging) dragFraction else (positionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / totalDuration).coerceIn(0f, 1f)

    val animatedBarHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = tween(150),
        label = "scrub_height"
    )
    val animatedThumbSize by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 13.dp,
        animationSpec = tween(150),
        label = "thumb_size"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        // Floating timestamp bubble when dragging
        if (isDragging) {
            val previewMs = (dragFraction * totalDuration).toLong()
            val thumbXPx = (dragFraction * widthPx).coerceIn(0f, widthPx)
            Box(
                modifier = Modifier
                    .offset { IntOffset((thumbXPx - 30.dp.roundToPx()).toInt(), -32.dp.roundToPx()) }
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xEE0F172A),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = formatPlayerScrubTime(previewMs),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Gesture detection area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(totalDuration) {
                    detectTapGestures { offset ->
                        val targetFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        val targetMs = (targetFraction * totalDuration).toLong()
                        onSeek(targetMs)
                    }
                }
                .pointerInput(totalDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            onSeekingChange(true)
                            dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val targetMs = (dragFraction * totalDuration).toLong()
                            onSeek(targetMs)
                            isDragging = false
                            onSeekingChange(false)
                        },
                        onDragCancel = {
                            isDragging = false
                            onSeekingChange(false)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Background and Progress tracks
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedBarHeight)
            ) {
                val cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                // Inactive base track
                drawRoundRect(
                    color = trackColor,
                    size = size,
                    cornerRadius = cornerRadius
                )
                // Buffered track
                if (bufferedFraction > 0.005f) {
                    drawRoundRect(
                        color = bufferedColor,
                        size = Size(size.width * bufferedFraction, size.height),
                        cornerRadius = cornerRadius
                    )
                }
                // Active played track
                if (currentFraction > 0.002f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                activeColor,
                                Color(0xFF38BDF8)
                            )
                        ),
                        size = Size(size.width * currentFraction, size.height),
                        cornerRadius = cornerRadius
                    )
                }
            }

            // Draggable Thumb
            val thumbOffsetPx = (currentFraction * widthPx).coerceIn(0f, widthPx)
            Box(
                modifier = Modifier
                    .offset { IntOffset((thumbOffsetPx - (animatedThumbSize / 2).roundToPx()).toInt(), 0) }
                    .size(animatedThumbSize)
                    .shadow(elevation = if (isDragging) 6.dp else 3.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                // Inner colored core
                Box(
                    modifier = Modifier
                        .size(if (isDragging) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(activeColor)
                )
            }
        }
    }
}

private fun formatPlayerScrubTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d:%02d", 0, minutes, seconds)
    }
}
