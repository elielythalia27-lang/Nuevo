package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.ui.player.NextPlayerAudioTrack
import com.example.ui.player.NextPlayerAudioTrackDialog
import com.example.ui.player.NextPlayerBottomBar
import com.example.ui.player.NextPlayerBrightnessHud
import com.example.ui.player.NextPlayerBufferingSpinner
import com.example.ui.player.NextPlayerCenterControls
import com.example.ui.player.NextPlayerDoubleTapBadge
import com.example.ui.player.NextPlayerInitialLoadingOverlay
import com.example.ui.player.NextPlayerLockOverlay
import com.example.ui.player.NextPlayerResizeMode
import com.example.ui.player.NextPlayerSeekHud
import com.example.ui.player.NextPlayerSpeedBoostBadge
import com.example.ui.player.NextPlayerSpeedDialog
import com.example.ui.player.NextPlayerSubtitleDialog
import com.example.ui.player.NextPlayerSubtitleTrack
import com.example.ui.player.NextPlayerTopBar
import com.example.ui.player.NextPlayerVolumeHud
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    coverUrl: String,
    year: String,
    type: String,
    initialPositionMs: Long = 0L,
    volumeKeyTrigger: Int = 0,
    onBack: () -> Unit,
    onSavePosition: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainActivity = context as? MainActivity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    // Title display: clean, single title showing channel or year in parentheses
    val displayTitle = remember(title, year) {
        val y = year.trim()
        if (y.isNotBlank() && !title.contains("($y)")) {
            "$title ($y)"
        } else {
            title
        }
    }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasFirstFrameRendered by remember { mutableStateOf(false) }
    var playbackError by remember {
        val isOnline = videoUrl.startsWith("http://", ignoreCase = true) || videoUrl.startsWith("https://", ignoreCase = true)
        if (isOnline && !com.example.utils.NetworkUtils.isConnected(context)) {
            mutableStateOf<String?>("Sin conexión a internet. Conéctate a una red Wi-Fi o datos para reproducir este video.")
        } else {
            mutableStateOf<String?>(null)
        }
    }
    var currentPositionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentResizeMode by remember { mutableStateOf(NextPlayerResizeMode.FIT) }

    // Screen locking
    var isScreenLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }

    // Dialogs state
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    // Tracks
    val audioTracks = remember { mutableStateListOf<NextPlayerAudioTrack>() }
    val subtitleTracks = remember { mutableStateListOf<NextPlayerSubtitleTrack>() }
    var areSubtitlesDisabled by remember { mutableStateOf(true) }

    // Controls visibility: do not show controls during initial loading
    var areControlsVisible by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Gestures state: Brightness, Volume, Horizontal Seek Scrub
    val configuration = LocalConfiguration.current
    var isLandscape by remember {
        mutableStateOf(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var volumeAccumulator by remember { mutableFloatStateOf(currentVolume.toFloat()) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var volumeHudCounter by remember { mutableIntStateOf(0) }

    val sharedPrefs = remember { context.getSharedPreferences("VideoPlayerPrefs", Context.MODE_PRIVATE) }
    val initialBrightness = remember {
        try {
            if (sharedPrefs.contains("brillo_global")) {
                val savedPercent = sharedPrefs.getInt("brillo_global", 50)
                (savedPercent / 100f).coerceIn(0.05f, 1f)
            } else {
                val winBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                if (winBrightness in 0.05f..1f) winBrightness else 0.5f
            }
        } catch (_: Exception) {
            0.5f
        }
    }
    var brightnessLevel by remember { mutableFloatStateOf(initialBrightness) }
    var showBrightnessHud by remember { mutableStateOf(false) }

    // Horizontal Seek swipe state
    var isSeekingHorizontal by remember { mutableStateOf(false) }
    var horizontalSeekTargetMs by remember { mutableLongStateOf(0L) }
    var horizontalSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var lastLiveSeekTime by remember { mutableLongStateOf(0L) }

    // Double-tap & Speed Boost feedback
    var doubleTapFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // isForward to seconds
    var isSpeedBoosting by remember { mutableStateOf(false) }

    // Parse video URI directly without artificial manipulation
    val mediaUri = remember(videoUrl) {
        when {
            videoUrl.startsWith("http://", ignoreCase = true) || videoUrl.startsWith("https://", ignoreCase = true) -> {
                Uri.parse(videoUrl)
            }
            videoUrl.startsWith("content://", ignoreCase = true) || videoUrl.startsWith("file://", ignoreCase = true) -> {
                Uri.parse(videoUrl)
            }
            else -> {
                val f = File(videoUrl)
                if (f.exists()) Uri.fromFile(f) else Uri.parse(videoUrl)
            }
        }
    }
    val mediaItem = remember(mediaUri) { MediaItem.fromUri(mediaUri) }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                if (initialPositionMs > 0L) {
                    seekTo(initialPositionMs)
                }
            }
    }

    // Physical Volume Keys interception via MainActivity
    DisposableEffect(mainActivity) {
        mainActivity?.registerPlayerVolumeHandler { direction ->
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (curVol + direction).coerceIn(0, maxVol)
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            } catch (_: Exception) {}
            currentVolume = targetVol
            volumeAccumulator = targetVol.toFloat()
            showVolumeHud = true
            volumeHudCounter++
        }
        onDispose {
            mainActivity?.unregisterPlayerVolumeHandler()
        }
    }

    // Auto-hide volume HUD
    LaunchedEffect(volumeHudCounter) {
        if (volumeHudCounter > 0) {
            showVolumeHud = true
            delay(1800L)
            showVolumeHud = false
        }
    }

    // Window, Screen On, Immersive System Bars and Default Landscape
    val insetsController = remember(activity) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(Unit) {
        try {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                activity?.window?.attributes?.let { lp ->
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    activity.window.attributes = lp
                }
            }

            // NextPlayer defaults to Landscape
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isLandscape = true

            insetsController?.let { controller ->
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    activity?.window?.attributes?.let { lp ->
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        activity.window.attributes = lp
                    }
                }
                activity?.window?.attributes?.let { lp ->
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity.window.attributes = lp
                }
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } catch (_: Exception) {}
        }
    }

    // Apply brightness to window
    LaunchedEffect(brightnessLevel) {
        try {
            activity?.window?.attributes?.let { lp ->
                lp.screenBrightness = brightnessLevel.coerceIn(0.05f, 1f)
                activity.window.attributes = lp
            }
        } catch (_: Exception) {}
    }

    // Auto-hide controls timer (3.5 seconds) - paused during seekbar dragging
    LaunchedEffect(areControlsVisible, lastInteractionTime, isPlaying, isScreenLocked, isDraggingSlider) {
        if (areControlsVisible && isPlaying && !isScreenLocked && !isDraggingSlider) {
            delay(3500L)
            areControlsVisible = false
        }
    }

    // Auto-hide unlock button when locked
    LaunchedEffect(showUnlockButton) {
        if (showUnlockButton) {
            delay(3000L)
            showUnlockButton = false
        }
    }

    // Player Event Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasFirstFrameRendered = true
                isBuffering = false
                areControlsVisible = true
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    hasFirstFrameRendered = true
                    isBuffering = false
                    playbackError = null
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    isBuffering = false
                    isPlaying = false
                    // Auto-exit back to the previous screen when video ends
                    onSavePosition(0L, durationMs)
                    onBack()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Populate audio tracks
                audioTracks.clear()
                var audioIndex = 1
                tracks.groups.forEachIndexed { groupIdx, group ->
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIdx in 0 until group.length) {
                            val format = group.getTrackFormat(trackIdx)
                            val trackName = format.label ?: "Pista $audioIndex"
                            val lang = format.language ?: ""
                            val isSelected = group.isTrackSelected(trackIdx)
                            audioTracks.add(
                                NextPlayerAudioTrack(
                                    groupIndex = groupIdx,
                                    trackIndex = trackIdx,
                                    name = trackName,
                                    language = lang,
                                    isSelected = isSelected
                                )
                            )
                            audioIndex++
                        }
                    }
                }

                // Populate subtitle tracks
                subtitleTracks.clear()
                var subIndex = 1
                var anySubSelected = false
                tracks.groups.forEachIndexed { groupIdx, group ->
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (trackIdx in 0 until group.length) {
                            val format = group.getTrackFormat(trackIdx)
                            val trackName = format.label ?: "Subtítulo $subIndex"
                            val lang = format.language ?: ""
                            val isSelected = group.isTrackSelected(trackIdx)
                            if (isSelected) anySubSelected = true
                            subtitleTracks.add(
                                NextPlayerSubtitleTrack(
                                    groupIndex = groupIdx,
                                    trackIndex = trackIdx,
                                    name = trackName,
                                    language = lang,
                                    isSelected = isSelected
                                )
                            )
                            subIndex++
                        }
                    }
                }
                areSubtitlesDisabled = !anySubSelected
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
                val isOnline = videoUrl.startsWith("http://", ignoreCase = true) || videoUrl.startsWith("https://", ignoreCase = true)
                val msg = when {
                    isOnline && !com.example.utils.NetworkUtils.isConnected(context) ->
                        "Sin conexión a internet. Conéctate a una red Wi-Fi o datos para reproducir este video."
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Error de conexión con el servidor. Revisa tu red o intenta de nuevo."
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "El archivo de video no existe o fue eliminado del almacenamiento."
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Formato o códec de video no compatible con el dispositivo."
                    else ->
                        "Error al reproducir video (${error.errorCodeName}). Revisa tu conexión o el archivo."
                }
                playbackError = msg
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.release()
        }
    }

    // Time ticker loop
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (!isDraggingSlider && !isSeekingHorizontal) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                if (dur > 0) {
                    durationMs = dur
                }
            }
            delay(250L)
        }
    }

    // Back handler
    BackHandler {
        if (isScreenLocked) {
            showUnlockButton = true
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
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        var totalDeltaX by remember { mutableFloatStateOf(0f) }
        var isDragDirectionDetermined by remember { mutableStateOf(false) }
        var isVerticalDrag by remember { mutableStateOf(false) }
        var isLeftScreenDrag by remember { mutableStateOf(false) }

        // Root Gestures Overlay (Tap, Double Tap, Drag, Press & Hold)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isScreenLocked, isPlaying, playbackSpeed, hasFirstFrameRendered) {
                    if (!hasFirstFrameRendered) {
                        return@pointerInput
                    }
                    if (isScreenLocked) {
                        detectTapGestures(
                            onTap = {
                                showUnlockButton = !showUnlockButton
                            }
                        )
                    } else {
                        detectTapGestures(
                            onTap = {
                                areControlsVisible = !areControlsVisible
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onDoubleTap = { offset ->
                                val xFrac = offset.x / size.width
                                when {
                                    xFrac < 0.35f -> {
                                        // Left 1/3: Rewind 10s
                                        val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(newPos)
                                        currentPositionMs = newPos
                                        doubleTapFeedback = false to 10
                                        coroutineScope.launch {
                                            delay(700L)
                                            doubleTapFeedback = null
                                        }
                                    }
                                    xFrac > 0.65f -> {
                                        // Right 1/3: Forward 10s
                                        val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs)
                                        exoPlayer.seekTo(newPos)
                                        currentPositionMs = newPos
                                        doubleTapFeedback = true to 10
                                        coroutineScope.launch {
                                            delay(700L)
                                            doubleTapFeedback = null
                                        }
                                    }
                                    else -> {
                                        // Center: Toggle Play / Pause
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                            isPlaying = false
                                        } else {
                                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                                exoPlayer.seekTo(0L)
                                            }
                                            exoPlayer.play()
                                            isPlaying = true
                                        }
                                        areControlsVisible = true
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                }
                            },
                            onPress = {
                                // NextPlayer Press & Hold for 2X Speed Boost
                                val pressStartTime = System.currentTimeMillis()
                                try {
                                    val isReleased = tryAwaitRelease()
                                    if (System.currentTimeMillis() - pressStartTime > 400L && isSpeedBoosting) {
                                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                        isSpeedBoosting = false
                                    }
                                } catch (_: Exception) {
                                    if (isSpeedBoosting) {
                                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                        isSpeedBoosting = false
                                    }
                                }
                            },
                            onLongPress = {
                                if (isPlaying) {
                                    isSpeedBoosting = true
                                    exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                                }
                            }
                        )
                    }
                }
                .pointerInput(isScreenLocked, hasFirstFrameRendered) {
                    if (!hasFirstFrameRendered) {
                        return@pointerInput
                    }
                    if (!isScreenLocked) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                totalDeltaX = 0f
                                isDragDirectionDetermined = false
                                isVerticalDrag = false
                                isLeftScreenDrag = offset.x < size.width / 2f
                                volumeAccumulator = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                lastInteractionTime = System.currentTimeMillis()

                                if (!isDragDirectionDetermined) {
                                    val absX = kotlin.math.abs(dragAmount.x)
                                    val absY = kotlin.math.abs(dragAmount.y)
                                    if (absX > 6f || absY > 6f) {
                                        isVerticalDrag = absY > absX
                                        isDragDirectionDetermined = true
                                    }
                                }

                                if (isDragDirectionDetermined) {
                                    if (!isVerticalDrag) {
                                        // Horizontal Seek Scrubbing
                                        isSeekingHorizontal = true
                                        totalDeltaX += dragAmount.x
                                        val deltaMs = (totalDeltaX / 8f).toLong() * 1000L
                                        val targetMs = (currentPositionMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(1L))
                                        horizontalSeekDeltaMs = deltaMs
                                        horizontalSeekTargetMs = targetMs

                                        // Throttled Live Video Seeking under finger
                                        val now = System.currentTimeMillis()
                                        if (now - lastLiveSeekTime > 90L) {
                                            exoPlayer.seekTo(targetMs)
                                            lastLiveSeekTime = now
                                        }
                                    } else {
                                        // Vertical Drag: Left = Brightness, Right = Volume
                                        val deltaPercent = -dragAmount.y / (size.height * 0.75f)
                                        if (isLeftScreenDrag) {
                                            val newBrightness = (brightnessLevel + deltaPercent).coerceIn(0.05f, 1f)
                                            brightnessLevel = newBrightness
                                            showBrightnessHud = true
                                            sharedPrefs.edit().putInt("brillo_global", (newBrightness * 100).toInt()).apply()
                                        } else {
                                            volumeAccumulator = (volumeAccumulator + deltaPercent * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                            val newVol = volumeAccumulator.toInt().coerceIn(0, maxVolume)
                                            if (newVol != currentVolume) {
                                                currentVolume = newVol
                                                try {
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                                } catch (_: Exception) {}
                                            }
                                            showVolumeHud = true
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
            // ExoPlayer Video Surface
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
                    }
                },
                update = { view ->
                    view.resizeMode = currentResizeMode.mode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Initial Cinematic Loading Screen (ONLY shown before first frame)
            AnimatedVisibility(
                visible = !hasFirstFrameRendered && isBuffering && playbackError == null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(250))
            ) {
                NextPlayerInitialLoadingOverlay(
                    title = displayTitle,
                    onBack = {
                        onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
                        onBack()
                    }
                )
            }

            // Mid-Stream Buffering Spinner (Video frame remains completely visible underneath!)
            AnimatedVisibility(
                visible = hasFirstFrameRendered && isBuffering && playbackError == null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                NextPlayerBufferingSpinner()
            }

            // Playback Error Overlay
            playbackError?.let { errText ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.90f),
                        border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(alpha = 0.7f)),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                fontSize = 13.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
                                        onBack()
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("SALIR", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        playbackError = null
                                        isBuffering = true
                                        exoPlayer.setMediaItem(mediaItem)
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("REINTENTAR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // HUDs
            // Brightness HUD on Left Edge
            NextPlayerBrightnessHud(
                brightness = brightnessLevel,
                visible = showBrightnessHud,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            )

            // Volume HUD on Right Edge
            NextPlayerVolumeHud(
                volume = currentVolume,
                maxVolume = maxVolume,
                visible = showVolumeHud,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            )

            // Horizontal Seek HUD in Center
            NextPlayerSeekHud(
                targetPositionMs = horizontalSeekTargetMs,
                deltaMs = horizontalSeekDeltaMs,
                durationMs = durationMs,
                visible = isSeekingHorizontal,
                modifier = Modifier.align(Alignment.Center)
            )

            // Speed Boost Badge at Top Center
            NextPlayerSpeedBoostBadge(
                visible = isSpeedBoosting,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )

            // Double Tap Seeking Ripple Feedback
            doubleTapFeedback?.let { (isForward, secs) ->
                NextPlayerDoubleTapBadge(
                    isForward = isForward,
                    seconds = secs,
                    visible = true,
                    modifier = Modifier.align(if (isForward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 48.dp)
                )
            }

            // NextPlayer Main Controls (Top Bar, Center Play/Pause, Bottom Bar)
            val isActivityInPip = activity?.isInPictureInPictureMode == true
            AnimatedVisibility(
                visible = hasFirstFrameRendered && areControlsVisible && !isScreenLocked && !isActivityInPip,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220))
            ) {
                val controlsEnabled = playbackError == null

                Box(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    NextPlayerTopBar(
                        title = displayTitle,
                        resizeMode = currentResizeMode,
                        currentSpeed = playbackSpeed,
                        hasAudioTracks = audioTracks.size > 1,
                        hasSubtitleTracks = subtitleTracks.isNotEmpty(),
                        onBack = {
                            onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
                            onBack()
                        },
                        onCycleResizeMode = {
                            currentResizeMode = when (currentResizeMode) {
                                NextPlayerResizeMode.FIT -> NextPlayerResizeMode.ZOOM
                                NextPlayerResizeMode.ZOOM -> NextPlayerResizeMode.STRETCH
                                NextPlayerResizeMode.STRETCH -> NextPlayerResizeMode.FIT
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onShowAudioTracks = {
                            showAudioTrackDialog = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onShowSubtitles = {
                            showSubtitleDialog = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onShowSpeedMenu = {
                            showSpeedDialog = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onToggleLock = {
                            isScreenLocked = true
                            areControlsVisible = false
                            showUnlockButton = true
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // Center Controls
                    NextPlayerCenterControls(
                        isPlaying = isPlaying,
                        enabled = controlsEnabled,
                        onPlayPause = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    exoPlayer.seekTo(0L)
                                }
                                exoPlayer.play()
                                isPlaying = true
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onRewind10 = {
                            val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onForward10 = {
                            val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Bottom Bar
                    NextPlayerBottomBar(
                        currentPositionMs = currentPositionMs,
                        bufferedPositionMs = bufferedPositionMs,
                        durationMs = durationMs,
                        isLandscape = isLandscape,
                        isPiPSupported = true,
                        enabled = controlsEnabled,
                        onSeekStart = {
                            isDraggingSlider = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onSeek = { targetMs ->
                            exoPlayer.seekTo(targetMs)
                            currentPositionMs = targetMs
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onSeekEnd = {
                            isDraggingSlider = false
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onToggleOrientation = {
                            if (isLandscape) {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                isLandscape = false
                            } else {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                isLandscape = true
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onEnterPiP = {
                            // Immediately hide controls before PiP transitions so minimized window is 100% clean
                            areControlsVisible = false
                            showVolumeHud = false
                            showBrightnessHud = false
                            doubleTapFeedback = null
                            isSeekingHorizontal = false
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                try {
                                    val params = android.app.PictureInPictureParams.Builder().build()
                                    activity?.enterPictureInPictureMode(params)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // Locked Screen Floating Unlock Button
            NextPlayerLockOverlay(
                visible = isScreenLocked && showUnlockButton,
                onUnlock = {
                    isScreenLocked = false
                    areControlsVisible = true
                    showUnlockButton = false
                    lastInteractionTime = System.currentTimeMillis()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            )
        }
    }

    // Dialogs
    if (showSpeedDialog) {
        NextPlayerSpeedDialog(
            currentSpeed = playbackSpeed,
            onSpeedSelected = { speed ->
                playbackSpeed = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showAudioTrackDialog) {
        NextPlayerAudioTrackDialog(
            tracks = audioTracks,
            onTrackSelected = { track ->
                val trackGroup = exoPlayer.currentTracks.groups[track.groupIndex].mediaTrackGroup
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(
                        TrackSelectionOverride(trackGroup, listOf(track.trackIndex))
                    )
                    .build()
            },
            onDismiss = { showAudioTrackDialog = false }
        )
    }

    if (showSubtitleDialog) {
        NextPlayerSubtitleDialog(
            tracks = subtitleTracks,
            areSubtitlesDisabled = areSubtitlesDisabled,
            onDisableSubtitles = {
                areSubtitlesDisabled = true
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            },
            onTrackSelected = { track ->
                areSubtitlesDisabled = false
                val trackGroup = exoPlayer.currentTracks.groups[track.groupIndex].mediaTrackGroup
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        TrackSelectionOverride(trackGroup, listOf(track.trackIndex))
                    )
                    .build()
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }
}
