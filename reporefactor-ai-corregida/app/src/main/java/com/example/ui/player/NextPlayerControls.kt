package com.example.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NextPlayer Top Bar: Back button, Clean Video Title, Resize Mode, Audio, Subtitles, Speed, Lock
 */
@Composable
fun NextPlayerTopBar(
    title: String,
    resizeMode: NextPlayerResizeMode,
    currentSpeed: Float,
    hasAudioTracks: Boolean,
    hasSubtitleTracks: Boolean,
    onBack: () -> Unit,
    onCycleResizeMode: () -> Unit,
    onShowAudioTracks: () -> Unit,
    onShowSubtitles: () -> Unit,
    onShowSpeedMenu: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.40f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back button + Video title
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Right: Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Resize Mode Cycle button
                IconButton(onClick = onCycleResizeMode, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = when (resizeMode) {
                            NextPlayerResizeMode.FIT -> Icons.Default.FitScreen
                            NextPlayerResizeMode.ZOOM -> Icons.Default.AspectRatio
                            NextPlayerResizeMode.STRETCH -> Icons.Default.AspectRatio
                        },
                        contentDescription = "Modo de pantalla: ${resizeMode.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Audio Tracks button
                IconButton(onClick = onShowAudioTracks, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Pistas de audio",
                        tint = if (hasAudioTracks) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Subtitle Tracks button
                IconButton(onClick = onShowSubtitles, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Subtítulos",
                        tint = if (hasSubtitleTracks) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Speed button (pill)
                Surface(
                    onClick = onShowSpeedMenu,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentSpeed == 1.0f) "1.0x" else "${currentSpeed}x",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Screen Lock button
                IconButton(onClick = onToggleLock, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Bloquear pantalla",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * NextPlayer Center Controls: Rewind 10s, Large Circular Play/Pause (64dp), Forward 10s
 */
@Composable
fun NextPlayerCenterControls(
    isPlaying: Boolean,
    enabled: Boolean = true,
    onPlayPause: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        // Rewind 10s
        Surface(
            onClick = { if (enabled) onRewind10() },
            enabled = enabled,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(48.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Retroceder 10 segundos",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Center Large Play/Pause (64dp)
        Surface(
            onClick = { if (enabled) onPlayPause() },
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier.size(64.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Forward 10s
        Surface(
            onClick = { if (enabled) onForward10() },
            enabled = enabled,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(48.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Adelantar 10 segundos",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * NextPlayer Bottom Bar: Progress bar with buffer bar, Timestamps, Orientation toggle, PiP
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextPlayerBottomBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    isLandscape: Boolean,
    isPiPSupported: Boolean,
    enabled: Boolean = true,
    onSeekStart: () -> Unit = {},
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit = {},
    onToggleOrientation: () -> Unit,
    onEnterPiP: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDur = durationMs.coerceAtLeast(1L)
    var scrubbingProgress by remember { mutableStateOf<Float?>(null) }
    val currentScrub = scrubbingProgress
    val displayProgress = currentScrub ?: (currentPositionMs.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)
    val displayTimeMs = if (currentScrub != null) (currentScrub * totalDur).toLong() else currentPositionMs
    val bufferedProgress = (bufferedPositionMs.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.40f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Clean YouTube/MX Player style Seekbar with zero dots at the end!
            NextPlayerProgressBar(
                progress = displayProgress,
                bufferedProgress = bufferedProgress,
                enabled = enabled,
                onSeekStart = {
                    if (scrubbingProgress == null) {
                        onSeekStart()
                    }
                },
                onSeekProgress = { frac ->
                    scrubbingProgress = frac
                },
                onSeekEnd = {
                    val finalScrub = scrubbingProgress
                    if (finalScrub != null) {
                        val targetMs = (finalScrub * totalDur).toLong().coerceIn(0L, totalDur)
                        onSeek(targetMs)
                    }
                    scrubbingProgress = null
                    onSeekEnd()
                }
            )

            // Bottom row: Timestamps + Secondary controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Timestamps: 00:12:45 / 01:30:00 (shows live scrubbing time while dragging)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatPlayerTime(displayTimeMs),
                        color = if (scrubbingProgress != null) MaterialTheme.colorScheme.primary else Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "/",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatPlayerTime(durationMs),
                        color = Color.LightGray,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Right actions: Orientation toggle, PiP
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPiPSupported) {
                        IconButton(onClick = onEnterPiP, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Modo PiP",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    IconButton(onClick = onToggleOrientation, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = if (isLandscape) "Cambiar a vertical" else "Cambiar a horizontal",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * NextPlayer Locked Overlay: Floating unlock button
 */
@Composable
fun NextPlayerLockOverlay(
    visible: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            onClick = onUnlock,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 8.dp,
            modifier = Modifier.size(54.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Desbloquear pantalla",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * NextPlayer Initial Loading Screen:
 * CRITICAL FIX: Only displayed before first frame rendered!
 * Displays a clean single title (no duplicate year or YouTuber name), poster/backdrop, and centered spinner.
 */
@Composable
fun NextPlayerInitialLoadingOverlay(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.size(42.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Center Spinner
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                strokeWidth = 3.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Text(
                text = "Cargando video...",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.5.sp
            )
        }
    }
}

/**
 * Custom ultra-sleek Video Progress Bar.
 * Completely eliminates the Material 3 Slider stop-indicator dots and clunkiness.
 */
@Composable
fun NextPlayerProgressBar(
    progress: Float,
    bufferedProgress: Float,
    enabled: Boolean,
    onSeekStart: () -> Unit,
    onSeekProgress: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = tween(150),
        label = "track_h"
    )

    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 6.dp,
        animationSpec = tween(150),
        label = "thumb_r"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        onSeekStart()
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekProgress(frac)
                        tryAwaitRelease()
                        isDragging = false
                        onSeekEnd()
                    }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onSeekStart()
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekProgress(frac)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekProgress(frac)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeekEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        onSeekEnd()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            val centerY = size.height / 2f
            val hPx = with(density) { trackHeight.toPx() }
            val radiusPx = with(density) { thumbRadius.toPx() }

            // 1. Inactive background track (full width)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, centerY - hPx / 2f),
                size = Size(size.width, hPx),
                cornerRadius = CornerRadius(hPx / 2f, hPx / 2f)
            )

            // 2. Buffered track
            if (bufferedProgress > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(0f, centerY - hPx / 2f),
                    size = Size(size.width * bufferedProgress.coerceIn(0f, 1f), hPx),
                    cornerRadius = CornerRadius(hPx / 2f, hPx / 2f)
                )
            }

            // 3. Active played track
            val playedWidth = (size.width * progress.coerceIn(0f, 1f)).coerceIn(0f, size.width)
            if (playedWidth > 0f) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(0f, centerY - hPx / 2f),
                    size = Size(playedWidth, hPx),
                    cornerRadius = CornerRadius(hPx / 2f, hPx / 2f)
                )
            }

            // 4. Scrubber Thumb (Zero dots at the end! Only at the exact current position!)
            val thumbCenterX = playedWidth.coerceIn(radiusPx, size.width - radiusPx)
            // Outer subtle shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = radiusPx + with(density) { 1.5.dp.toPx() },
                center = Offset(thumbCenterX, centerY)
            )
            // Primary Thumb
            drawCircle(
                color = primaryColor,
                radius = radiusPx,
                center = Offset(thumbCenterX, centerY)
            )
            // Crisp White Inner Dot
            if (isDragging) {
                drawCircle(
                    color = Color.White,
                    radius = radiusPx * 0.45f,
                    center = Offset(thumbCenterX, centerY)
                )
            }
        }
    }
}
