package com.example.ui.screens

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.components.SleekVideoPlayerProgressBar
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class VideoResizeMode(val displayName: String, val mode: Int) {
    FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Rellenar", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("16:9 Estirar", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    coverUrl: String,
    year: String,
    type: String,
    initialPositionMs: Long = 0L,
    onBack: () -> Unit,
    onSavePosition: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isLockIconVisible by remember { mutableStateOf(false) }
    var currentResizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }

    // Fast-forward (Press & Hold) state
    var isHoldingFastForward by remember { mutableStateOf(false) }

    // Double tap ripple/badge feedback
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    // Gestures state: Brightness, Volume, Horizontal Scrub
    val configuration = LocalConfiguration.current
    val isOrientationLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isLandscape by remember { mutableStateOf(false) }
    val initialBrightness = remember {
        try {
            val winBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
            if (winBrightness in 0.05f..1f) {
                winBrightness
            } else {
                val sysBrightness = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    128
                )
                (sysBrightness / 255f).coerceIn(0.05f, 1f)
            }
        } catch (e: Exception) {
            0.5f
        }
    }
    var brightnessLevel by remember { mutableFloatStateOf(initialBrightness) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var volumeHudCounter by remember { mutableIntStateOf(0) }

    // Horizontal Seek swipe state
    var isSeekingHorizontal by remember { mutableStateOf(false) }
    var horizontalSeekTargetMs by remember { mutableLongStateOf(0L) }
    var horizontalSeekDeltaMs by remember { mutableLongStateOf(0L) }

    // Volume state synced with system
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    var volumeAccumulator by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    // Controls visibility
    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val uri = when {
                    videoUrl.startsWith("http://") || videoUrl.startsWith("https://") -> Uri.parse(videoUrl)
                    videoUrl.startsWith("content://") || videoUrl.startsWith("file://") -> Uri.parse(videoUrl)
                    else -> Uri.fromFile(File(videoUrl))
                }
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                if (initialPositionMs > 0) {
                    seekTo(initialPositionMs)
                }
            }
    }

    // Lifecycle handling: pause on background, save position
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.playWhenReady = false
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep screen on and configure system bars for seamless video playback
    val window = activity?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        insetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes?.let { lp ->
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Synchronize system notification bar and navigation bar with controls visibility
    LaunchedEffect(areControlsVisible) {
        insetsController?.let { controller ->
            if (areControlsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Intercept physical volume buttons with BroadcastReceiver for VOLUME_CHANGED_ACTION
    DisposableEffect(context) {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                currentVolume = newVol
                showVolumeHud = true
                volumeHudCounter++
            }
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            // ignore if not permitted
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // Auto-hide volume HUD after physical button press
    LaunchedEffect(volumeHudCounter) {
        if (volumeHudCounter > 0) {
            showVolumeHud = true
            delay(1800)
            showVolumeHud = false
        }
    }

    // Player event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    isBuffering = false
                    playbackError = null
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    isBuffering = false
                    isPlaying = false
                    areControlsVisible = true
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
                playbackError = "Error al reproducir el video (${error.errorCodeName}). Verifica tu conexión o intenta con otra película."
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.release()
        }
    }

    // Progress update loop
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (!isDraggingSlider && !isSeekingHorizontal) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                if (dur > 0) {
                    durationMs = dur
                    sliderPosition = (currentPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(350)
        }
    }

    // Auto-hide controls after user interaction or when playback resumes
    LaunchedEffect(areControlsVisible, lastInteractionTime, isScreenLocked, isHoldingFastForward, isPlaying) {
        if (areControlsVisible && isPlaying && !isScreenLocked && !isHoldingFastForward) {
            delay(3500)
            areControlsVisible = false
        }
    }

    // Dismiss double tap feedback badge
    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(800)
            doubleTapFeedback = null
        }
    }

    // Auto-hide lock icon when locked so it doesn't disturb viewing experience
    LaunchedEffect(isLockIconVisible, isScreenLocked) {
        if (isScreenLocked && isLockIconVisible) {
            delay(2500)
            isLockIconVisible = false
        }
    }

    fun resetControlsTimer() {
        areControlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    BackHandler {
        if (isScreenLocked) {
            isScreenLocked = false
            isLockIconVisible = false
            areControlsVisible = true
            resetControlsTimer()
        } else {
            onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
            onBack()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Drag state holders
        var dragStartX by remember { mutableFloatStateOf(0f) }
        var dragStartY by remember { mutableFloatStateOf(0f) }
        var initialSeekPosMs by remember { mutableLongStateOf(0L) }
        var isDragDirectionDetermined by remember { mutableStateOf(false) }
        var isHorizontalDrag by remember { mutableStateOf(false) }

        // Video Surface & Touch Gesture System
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 1. Tap, Double-Tap and Press & Hold (Fast forward 2x)
                .pointerInput(isScreenLocked) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (!isScreenLocked) {
                                val isRightSide = offset.x > (size.width / 2)
                                if (isRightSide) {
                                    val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(newPos)
                                    doubleTapFeedback = "+10s ▶▶"
                                } else {
                                    val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    doubleTapFeedback = "◀◀ -10s"
                                }
                                resetControlsTimer()
                            }
                        },
                        onPress = {
                            if (!isScreenLocked) {
                                // Press & Hold for fast forward (2x speed like YouTube)
                                val holdJob = coroutineScope.launch {
                                    delay(350)
                                    if (isPlaying) {
                                        isHoldingFastForward = true
                                        exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                                    }
                                }
                                try {
                                    awaitRelease()
                                } finally {
                                    holdJob.cancel()
                                    if (isHoldingFastForward) {
                                        isHoldingFastForward = false
                                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                    }
                                }
                            }
                        },
                        onTap = {
                            if (isScreenLocked) {
                                isLockIconVisible = !isLockIconVisible
                            } else {
                                areControlsVisible = !areControlsVisible
                                if (areControlsVisible) resetControlsTimer()
                            }
                        }
                    )
                }
                // 2. Drag gestures: Horizontal (Swipe to Seek) & Vertical (Left=Brightness, Right=Volume)
                .pointerInput(isScreenLocked) {
                    if (!isScreenLocked) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartX = offset.x
                                dragStartY = offset.y
                                initialSeekPosMs = exoPlayer.currentPosition
                                isDragDirectionDetermined = false
                                isHorizontalDrag = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val totalDeltaX = change.position.x - dragStartX
                                val totalDeltaY = change.position.y - dragStartY

                                if (!isDragDirectionDetermined) {
                                    if (Math.abs(totalDeltaX) > 25f || Math.abs(totalDeltaY) > 25f) {
                                        isDragDirectionDetermined = true
                                        isHorizontalDrag = Math.abs(totalDeltaX) > Math.abs(totalDeltaY)
                                    }
                                }

                                if (isDragDirectionDetermined) {
                                    if (isHorizontalDrag) {
                                        // Horizontal swipe: Scrub seek position
                                        isSeekingHorizontal = true
                                        val seekSeconds = (totalDeltaX / 12f).toLong()
                                        val targetMs = (initialSeekPosMs + (seekSeconds * 1000L))
                                            .coerceIn(0L, durationMs.coerceAtLeast(1000L))
                                        horizontalSeekTargetMs = targetMs
                                        horizontalSeekDeltaMs = seekSeconds
                                    } else {
                                        // Vertical swipe: Left=Brightness, Right=Volume
                                        val isRightSide = dragStartX > (size.width / 2)
                                        val verticalDelta = -dragAmount.y / 260f

                                        if (isRightSide) {
                                            showVolumeHud = true
                                            showBrightnessHud = false
                                            volumeAccumulator = (volumeAccumulator + (verticalDelta * maxVolume))
                                                .coerceIn(0f, maxVolume.toFloat())
                                            val newVol = volumeAccumulator.toInt().coerceIn(0, maxVolume)
                                            if (newVol != currentVolume) {
                                                currentVolume = newVol
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                                            }
                                        } else {
                                            showBrightnessHud = true
                                            showVolumeHud = false
                                            brightnessLevel = (brightnessLevel + verticalDelta).coerceIn(0.05f, 1.0f)
                                            activity?.window?.attributes?.let { lp ->
                                                lp.screenBrightness = brightnessLevel
                                                activity.window.attributes = lp
                                            }
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                if (isSeekingHorizontal) {
                                    exoPlayer.seekTo(horizontalSeekTargetMs)
                                    currentPositionMs = horizontalSeekTargetMs
                                    isSeekingHorizontal = false
                                }
                                showVolumeHud = false
                                showBrightnessHud = false
                                isDragDirectionDetermined = false
                            },
                            onDragCancel = {
                                isSeekingHorizontal = false
                                showVolumeHud = false
                                showBrightnessHud = false
                                isDragDirectionDetermined = false
                            }
                        )
                    }
                }
        ) {
            // ExoPlayer View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = currentResizeMode.mode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerViewRef = this
                    }
                },
                update = { view ->
                    view.resizeMode = currentResizeMode.mode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Modern Cinematic Buffering Indicator with smooth orbital spinner and glowing core
            AnimatedVisibility(
                visible = isBuffering && playbackError == null,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                PlayerBufferingIndicator()
            }

            // Playback Error Banner
            playbackError?.let { errText ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Error de reproducción",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = errText,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    playbackError = null
                                    isBuffering = true
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }

            // Press & Hold Fast Forward (2x) Top Floating Pill
            AnimatedVisibility(
                visible = isHoldingFastForward,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2X Avance Rápido",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Horizontal Swipe Seek HUD Overlay
            if (isSeekingHorizontal) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (horizontalSeekDeltaMs >= 0) "+${horizontalSeekDeltaMs}s" else "${horizontalSeekDeltaMs}s",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatTimestamp(horizontalSeekTargetMs),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = " / ${formatTimestamp(durationMs)}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Vertical HUD: Volume Bar (System-synced with progress bar)
            AnimatedVisibility(
                visible = showVolumeHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (currentVolume == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "Volumen",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // Vertical progress bar for volume
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(90.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val fraction = (currentVolume.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${((currentVolume.toFloat() / maxVolume.toFloat()) * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Vertical HUD: Brightness Bar (System-synced with progress bar)
            AnimatedVisibility(
                visible = showBrightnessHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "Brillo",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // Vertical progress bar for brightness
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(90.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(brightnessLevel.coerceIn(0f, 1f))
                                    .background(Color(0xFFF59E0B))
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${(brightnessLevel * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Double tap feedback badge (+10s or -10s) in corresponding corner
            doubleTapFeedback?.let { feedback ->
                val isRewind = feedback.startsWith("-")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp),
                    contentAlignment = if (isRewind) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = feedback,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // Lock Screen Floating Button
            val showLockButton = if (isScreenLocked) isLockIconVisible else (areControlsVisible && !isBuffering)
            AnimatedVisibility(
                visible = showLockButton,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isScreenLocked) Color(0xFFEF4444).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            if (isScreenLocked) {
                                isScreenLocked = false
                                isLockIconVisible = false
                                areControlsVisible = true
                                resetControlsTimer()
                            } else {
                                isScreenLocked = true
                                isLockIconVisible = true
                                areControlsVisible = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isScreenLocked) "Desbloquear controles" else "Bloquear controles",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Controls UI (Hidden when screen is locked or when buffering)
            if (!isScreenLocked) {
                AnimatedVisibility(
                    visible = areControlsVisible && !isBuffering,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top Gradient Bar: Back, Title, Resize mode, Speed, Rotate
                        val topBarPaddingTop = if (isOrientationLandscape || isLandscape) 8.dp else 40.dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)
                                    )
                                )
                                .padding(start = 14.dp, end = 14.dp, top = topBarPaddingTop, bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
                                        onBack()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Regresar",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                val playerDisplayTitle = remember(title, year) {
                                    val y = year.trim()
                                    if (y.isNotBlank() && !title.contains("($y)")) {
                                        "$title ($y)"
                                    } else {
                                        title
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playerDisplayTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Aspect ratio toggle button (Ajustar / Rellenar / Estirar)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clickable {
                                            val modes = VideoResizeMode.values()
                                            val nextIndex = (currentResizeMode.ordinal + 1) % modes.size
                                            currentResizeMode = modes[nextIndex]
                                            playerViewRef?.resizeMode = currentResizeMode.mode
                                            resetControlsTimer()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Escala de pantalla",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = currentResizeMode.displayName,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Speed button
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.clickable {
                                            showSpeedMenu = true
                                            resetControlsTimer()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${playbackSpeed}x",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showSpeedMenu,
                                        onDismissRequest = { showSpeedMenu = false }
                                    ) {
                                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "${speed}x ${if (speed == 1.0f) "(Normal)" else ""}",
                                                        fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    playbackSpeed = speed
                                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                                    showSpeedMenu = false
                                                    resetControlsTimer()
                                                }
                                            )
                                        }
                                    }
                                }

                                // Screen rotation / orientation switch
                                IconButton(
                                    onClick = {
                                        isLandscape = !isLandscape
                                        activity?.requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                        resetControlsTimer()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Rotar pantalla",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Center Controls: Rewind (-10s), Play/Pause/Replay, Fast Forward (+10s)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            // Rewind 10s
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable {
                                        val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(newPos)
                                        currentPositionMs = newPos
                                        doubleTapFeedback = "◀◀ -10s"
                                        resetControlsTimer()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Retroceder 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "10s",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Center Play / Pause / Replay button
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                            exoPlayer.seekTo(0L)
                                            exoPlayer.play()
                                        } else {
                                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        }
                                        resetControlsTimer()
                                    }
                                    .testTag("play_pause_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            // Forward 10s
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable {
                                        val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration)
                                        exoPlayer.seekTo(newPos)
                                        currentPositionMs = newPos
                                        doubleTapFeedback = "+10s ▶▶"
                                        resetControlsTimer()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Adelantar 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "10s",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Bottom Gradient Bar with Timeline Scrub Bar and Controls
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Sleek Interactive Video Player Scrub Bar
                                SleekVideoPlayerProgressBar(
                                    positionMs = currentPositionMs,
                                    durationMs = durationMs,
                                    bufferedPositionMs = bufferedPositionMs,
                                    onSeek = { targetMs ->
                                        currentPositionMs = targetMs
                                        exoPlayer.seekTo(targetMs)
                                        resetControlsTimer()
                                    },
                                    onSeekingChange = { isSeeking ->
                                        isDraggingSlider = isSeeking
                                        if (isSeeking) {
                                            resetControlsTimer()
                                        }
                                    },
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("player_progress_slider")
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Time and Status Info
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = formatTimestamp(currentPositionMs),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "/",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = formatTimestamp(durationMs),
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    // Quick Fullscreen toggle
                                    IconButton(
                                        onClick = {
                                            isLandscape = !isLandscape
                                            activity?.requestedOrientation = if (isLandscape) {
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            }
                                            resetControlsTimer()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = "Pantalla completa",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun PlayerBufferingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "player_buffering")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inner_pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer rotating gradient arc
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(rotationZ = rotation)
                ) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                primaryColor.copy(alpha = 0.05f),
                                primaryColor.copy(alpha = 0.4f),
                                primaryColor,
                                Color(0xFF38BDF8)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner counter-rotating breathing glowing ring
                Canvas(
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale,
                            rotationZ = -rotation * 0.6f
                        )
                ) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.6f),
                        startAngle = 180f,
                        sweepAngle = 160f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Center luminous core dot
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
            }

            Text(
                text = "Cargando...",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
        }
    }
}
