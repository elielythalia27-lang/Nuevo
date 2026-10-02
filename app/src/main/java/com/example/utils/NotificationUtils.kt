package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.download.DownloadForegroundService
import com.example.data.download.DownloadHelper
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import java.util.Locale

object NotificationUtils {

    const val CHANNEL_PROGRESS_ID = "channel_download_progress_v6"
    const val CHANNEL_SUCCESS_ID = "channel_download_success_v6"
    const val CHANNEL_ERROR_ID = "channel_download_error_v6"

    const val GROUP_KEY_DOWNLOADS = "group_key_downloads"
    private const val GROUP_DOWNLOADS_ID = "group_download_management"

    /**
     * Inicializa los canales de notificación en Android 8.0+ (API 26+)
     */
    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val downloadsGroup = NotificationChannelGroup(GROUP_DOWNLOADS_ID, "Gestión de Descargas")
            notificationManager.createNotificationChannelGroups(listOf(downloadsGroup))

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Progreso de descargas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Velocidad, porcentaje y tiempo restante"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val successChannel = NotificationChannel(
                CHANNEL_SUCCESS_ID,
                "Descargas completadas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Avisos al finalizar las descargas"
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val errorChannel = NotificationChannel(
                CHANNEL_ERROR_ID,
                "Errores de descarga",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Alertas de conexión o almacenamiento"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(progressChannel, successChannel, errorChannel)
            )
        }
    }

    /**
     * PendingIntent que abre la app en la pestaña de descargas al pulsar la notificación
     */
    fun createOpenDownloadsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("initial_tab", 1)
            putExtra("skip_splash", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 1001, intent, flags)
    }

    /**
     * PendingIntent de los botones de la notificación. Lleva un Uri único en data:
     * un PendingIntent se distingue por action+data, NO por los extras.
     */
    private fun serviceActionIntent(
        context: Context,
        intentAction: String,
        downloadId: String,
        asForeground: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = intentAction
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, downloadId)
            downloadId.toLongOrNull()?.let { putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, it) }
            data = Uri.parse("downloadfree://action/$intentAction/$downloadId")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val requestCode = intentAction.hashCode() * 31 + downloadId.hashCode()
        return if (asForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }

    private fun serviceActionIntent(
        context: Context,
        intentAction: String,
        downloadId: Long,
        asForeground: Boolean = false
    ): PendingIntent = serviceActionIntent(context, intentAction, downloadId.toString(), asForeground)

    private fun displayTitle(item: DownloadItem): String {
        return if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }
    }

    fun formatSpeed(speed: Long): String {
        return when {
            speed <= 0L -> "Conectando..."
            speed >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB/s", speed / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f KB/s", speed / 1024.0)
        }
    }

    /**
     * Notificación RESUMEN estilo IDM: es la del servicio en primer plano.
     * Agrupa las notificaciones individuales y muestra velocidad total y lista de archivos.
     */
    fun buildSummaryNotification(context: Context, items: List<DownloadItem>): Notification {
        initNotificationChannels(context)

        val active = items.filter { it.status == DownloadStatus.DOWNLOADING }
        val count = active.size
        val totalSpeed = active.sumOf { it.speedBytesPerSec }

        val known = active.filter { it.totalBytes > 0L }
        val sumTotal = known.sumOf { it.totalBytes }
        val sumDone = known.sumOf { it.downloadedBytes.coerceAtMost(it.totalBytes) }
        val overall = if (sumTotal > 0L) ((sumDone * 100L) / sumTotal).toInt().coerceIn(0, 100) else 0
        val indeterminate = sumTotal <= 0L

        val title = when (count) {
            0 -> "Preparando descargas..."
            1 -> "Descargando 1 archivo"
            else -> "Descargando $count archivos"
        }
        val text = if (indeterminate) formatSpeed(totalSpeed) else "$overall% • ${formatSpeed(totalSpeed)}"

        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(text)
        active.take(5).forEach { item ->
            val detail = if (item.totalBytes > 0L) "${item.progress}%" else formatByteSize(item.downloadedBytes)
            inbox.addLine("${displayTitle(item)} • $detail")
        }
        if (count > 5) inbox.addLine("y ${count - 5} más…")

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(inbox)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setColor(0xFF00897B.toInt())
            .setProgress(100, overall, indeterminate)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(GROUP_KEY_DOWNLOADS)
            .setGroupSummary(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pausar todo",
                serviceActionIntent(context, DownloadForegroundService.ACTION_PAUSE_ALL, -1L)
            )
            .build()
    }

    /**
     * Construye la notificación activa de descarga en progreso (con botones Pausar y Cancelar)
     */
    fun buildProgressNotification(
        context: Context,
        item: DownloadItem,
        progress: Int,
        speed: Long,
        eta: Long
    ): Notification {
        initNotificationChannels(context)

        val title = displayTitle(item)

        val sizeStr = if (item.totalBytes > 0L) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }

        val speedStr = formatSpeed(speed)

        val etaStr = if (eta > 0L) {
            val hours = eta / 3600
            val minutes = (eta % 3600) / 60
            val seconds = eta % 60
            when {
                hours > 0 -> "${hours}h ${minutes}m restantes"
                minutes > 0 -> "${minutes}m ${seconds}s restantes"
                else -> "${seconds}s restantes"
            }
        } else if (item.totalBytes > 0L) {
            "Calculando tiempo..."
        } else {
            "Iniciando..."
        }

        val isIndeterminate = item.totalBytes <= 0L
        val subtitle = if (isIndeterminate) {
            "$sizeStr • $speedStr"
        } else {
            "$progress% • $sizeStr • $speedStr • $etaStr"
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setContentTitle("Descargando: $title")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setSmallIcon(R.drawable.ic_notification_download)
            .setColor(0xFF00897B.toInt())
            .setProgress(100, if (isIndeterminate) 0 else progress, isIndeterminate)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(GROUP_KEY_DOWNLOADS)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pausar",
                serviceActionIntent(context, DownloadHelper.ACTION_PAUSE_DOWNLOAD, item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                serviceActionIntent(context, DownloadHelper.ACTION_CANCEL_DOWNLOAD, item.id)
            )
            .build()
    }

    /**
     * Construye la notificación cuando la descarga está pausada (Reanudar y Cancelar)
     */
    fun buildPausedNotification(
        context: Context,
        item: DownloadItem
    ): Notification {
        initNotificationChannels(context)

        val title = displayTitle(item)

        val sizeInfo = if (item.totalBytes > 0L) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }
        val subtitle = "${item.progress}% • $sizeInfo • En pausa"

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle("En pausa: $title")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setSmallIcon(R.drawable.ic_notification_pause)
            .setColor(0xFFF59E0B.toInt())
            .setProgress(100, item.progress, false)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(false)
            .setOngoing(false)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_KEY_DOWNLOADS)
            .addAction(
                android.R.drawable.ic_media_play,
                "Reanudar",
                serviceActionIntent(context, DownloadHelper.ACTION_START_DOWNLOAD, item.id, asForeground = true)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                serviceActionIntent(context, DownloadHelper.ACTION_CANCEL_DOWNLOAD, item.id)
            )
            .build()
    }

    /**
     * Notificación de descarga fallida (Reintentar y Cancelar). Texto genérico, sin datos del servidor.
     */
    fun buildFailedNotification(
        context: Context,
        item: DownloadItem
    ): Notification {
        initNotificationChannels(context)

        val title = displayTitle(item)
        val sizeInfo = if (item.totalBytes > 0L) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }
        val detail = "Se interrumpió la descarga • $sizeInfo. Reintenta para continuar donde quedó."

        return NotificationCompat.Builder(context, CHANNEL_ERROR_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle("Descarga fallida: $title")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(0xFFEF4444.toInt())
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSortKey("download_${item.id}")
            .addAction(
                android.R.drawable.ic_media_play,
                "Reintentar",
                serviceActionIntent(context, DownloadHelper.ACTION_START_DOWNLOAD, item.id, asForeground = true)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                serviceActionIntent(context, DownloadHelper.ACTION_CANCEL_DOWNLOAD, item.id)
            )
            .build()
    }

    /**
     * Construye la notificación de descarga finalizada con éxito
     */
    fun buildCompletedNotification(
        context: Context,
        item: DownloadItem
    ): Notification {
        initNotificationChannels(context)

        val title = displayTitle(item)
        val sizeStr = formatByteSize(item.totalBytes.coerceAtLeast(item.downloadedBytes))
        val detail = "$sizeStr • Descarga completada. Lista para ver sin conexión."

        return NotificationCompat.Builder(context, CHANNEL_SUCCESS_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle("Descarga completada")
            .setContentText("$title • $sizeStr")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$detail"))
            .setSmallIcon(R.drawable.ic_notification_done)
            .setColor(0xFF10B981.toInt())
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .build()
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
