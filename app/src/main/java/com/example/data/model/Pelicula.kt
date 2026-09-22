package com.example.data.model

import com.google.gson.annotations.SerializedName
import java.util.Locale

data class Pelicula(
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("nombre")
    val nombre: String? = null,
    @SerializedName("año", alternate = ["anio", "a", "year"])
    val anio: String? = null,
    @SerializedName("peli")
    val peli: String? = null,
    @SerializedName("tp")
    val tp: String? = null
) {
    val id: String
        get() = peli?.trim() ?: (nombre?.trim() ?: "")

    val safeTitle: String
        get() = nombre?.trim().takeUnless { it.isNullOrEmpty() } ?: "Sin título"

    val safeYear: String
        get() {
            if (isVideo) return ""
            val raw = anio?.trim() ?: ""
            val yearMatch = Regex("(19|20)\\d{2}").find(raw)
            if (yearMatch != null) return yearMatch.value
            val titleMatch = Regex("\\b(19|20)\\d{2}\\b").find(safeTitle)
            if (titleMatch != null) return titleMatch.value
            return if (raw.isNotEmpty() && raw.all { it.isDigit() } && raw.length == 4) raw else ""
        }

    val youtuberName: String
        get() {
            val raw = anio?.trim() ?: ""
            return if (raw.isNotEmpty()) raw else "YouTube"
        }

    val safeCoverUrl: String
        get() = url?.trim() ?: ""

    val safeVideoUrl: String
        get() = peli?.trim() ?: ""

    val isMovie: Boolean
        get() = tp?.trim()?.lowercase() == "pl"

    val isVideo: Boolean
        get() = tp?.trim()?.lowercase() == "yt"

    val typeLabel: String
        get() = if (isMovie) "Película" else "YouTube"

    val secondaryTag: String
        get() = if (isVideo) youtuberName else safeYear

    val formattedDisplayTitle: String
        get() {
            val tag = secondaryTag.trim()
            return if (tag.isNotEmpty() && !safeTitle.contains("($tag)")) {
                "$safeTitle ($tag)"
            } else {
                safeTitle
            }
        }
}

data class ContinueWatchingItem(
    val videoUrl: String,
    val title: String,
    val coverUrl: String,
    val year: String,
    val type: String,
    val positionMs: Long,
    val durationMs: Long
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DownloadItem(
    val id: String,
    val title: String,
    val originalVideoUrl: String,
    val coverUrl: String,
    val year: String,
    val type: String,
    val localFilePath: String,
    val downloadId: Long = -1L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L
) {
    val formattedSpeed: String
        get() {
            if (status == DownloadStatus.PAUSED) return "En pausa"
            if (status != DownloadStatus.DOWNLOADING) return "-- KB/s"
            if (speedBytesPerSec <= 0L) return "Iniciando..."
            return when {
                speedBytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
                else -> String.format(Locale.US, "%d KB/s", (speedBytesPerSec / 1024).coerceAtLeast(1L))
            }
        }

    val formattedDownloadedSize: String
        get() {
            val downloaded = if (downloadedBytes > 0) downloadedBytes else if (totalBytes > 0) (totalBytes * progress / 100) else 0L
            return formatByteSize(downloaded)
        }

    val formattedTotalSize: String
        get() = if (totalBytes > 0) formatByteSize(totalBytes) else if (downloadedBytes > 0) formatByteSize(downloadedBytes) else "Calculando..."

    val formattedEta: String
        get() {
            if (status == DownloadStatus.COMPLETED) return "Completada"
            if (status == DownloadStatus.PAUSED) return "Descarga pausada"
            if (status == DownloadStatus.FAILED) return "Error de descarga"
            if (status == DownloadStatus.PENDING) return "En cola de espera"
            if (status != DownloadStatus.DOWNLOADING) return "Pausada"
            if (etaSeconds <= 0L) return "Calculando..."
            val hours = etaSeconds / 3600
            val mins = (etaSeconds % 3600) / 60
            val secs = etaSeconds % 60
            return when {
                hours > 0 -> "Restante: ${hours}h ${mins}m"
                mins > 0 -> "Restante: ${mins}m ${secs}s"
                else -> "Restante: ${secs}s"
            }
        }

    val formattedDisplayTitle: String
        get() {
            val tag = year.trim()
            return if (tag.isNotEmpty() && !title.contains("($tag)")) {
                "$title ($tag)"
            } else {
                title
            }
        }
}

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${Math.round(gb)} GB"
        mb >= 1.0 -> "${Math.round(mb)} MB"
        else -> "${Math.round(kb)} KB"
    }
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class SortOption(val displayName: String) {
    NAME_AZ("Nombre (A - Z)"),
    NAME_ZA("Nombre (Z - A)")
}

enum class CatalogLayoutMode(val displayName: String) {
    GRID_2("Cuadrícula (2 col)"),
    GRID_3("Compacto (3 col)"),
    LIST("Lista detallada")
}
