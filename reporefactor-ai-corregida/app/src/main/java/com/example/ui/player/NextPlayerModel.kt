package com.example.ui.player

import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale

enum class NextPlayerResizeMode(val displayName: String, val mode: Int) {
    FIT("Ajustar a pantalla", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Rellenar / Recortar", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("16:9 Estirar", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

data class NextPlayerAudioTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String,
    val isSelected: Boolean
)

data class NextPlayerSubtitleTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String,
    val isSelected: Boolean
)

fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatPlayerDeltaTime(deltaMs: Long): String {
    val prefix = if (deltaMs >= 0) "+" else "-"
    val absSeconds = kotlin.math.abs(deltaMs) / 1000L
    val minutes = absSeconds / 60L
    val seconds = absSeconds % 60L
    return "$prefix${String.format(Locale.US, "%02d:%02d", minutes, seconds)}"
}
