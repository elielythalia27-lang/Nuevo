package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Velocidad total estabilizada para la tarjeta "Velocidad de descarga".
 * Suma las velocidades de las descargas activas, la suaviza (50% anterior + 50% nueva)
 * y solo cambia cada 2 segundos, para que no salte cuando hay varias descargas a la vez.
 * Si no hay ninguna descarga en estado DOWNLOADING, devuelve 0 de inmediato.
 */
@Composable
fun rememberStabilizedTotalSpeed(downloads: List<DownloadItem>): Long {
    val currentDownloads by rememberUpdatedState(downloads)
    var stabilized by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var smoothed = 0L
        while (isActive) {
            val downloading = currentDownloads.filter { it.status == DownloadStatus.DOWNLOADING }
            if (downloading.isEmpty()) {
                smoothed = 0L
                stabilized = 0L
            } else {
                val target = downloading.sumOf { it.speedBytesPerSec }
                smoothed = if (smoothed <= 0L) {
                    target
                } else {
                    ((smoothed * 0.50) + (target * 0.50)).toLong()
                }
                stabilized = smoothed
            }
            delay(2000L)
        }
    }

    val hasDownloading = downloads.any { it.status == DownloadStatus.DOWNLOADING }
    return if (!hasDownloading) {
        0L
    } else if (stabilized > 0L) {
        stabilized
    } else {
        // Primeros instantes, antes de la primera muestra suavizada
        downloads
            .filter { it.status == DownloadStatus.DOWNLOADING }
            .sumOf { it.speedBytesPerSec }
    }
}
