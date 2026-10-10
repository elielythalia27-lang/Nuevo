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
import com.example.data.download.DownloadHelper
import com.example.data.download.DownloadActionReceiver
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.formatDownloadSpeed
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
    private fun receiverActionIntent(
        context: Context,
        intentAction: String,
        downloadId: String
    ): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = intentAction
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, downloadId)
            data = Uri.parse("downloadfree://notification/$intentAction/$downloadId")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val requestCode = intentAction.hashCode() * 31 + downloadId.hashCode()
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun receiverActionIntent(
        context: Context,
        intentAction: String,
        downloadId: Long
    ): PendingIntent = receiverActionIntent(context, intentAction, downloadId.toString())

    /**
     * Android 14+ requires UIDT to be scheduled while the app is visible (or under
     * a documented exemption). Therefore a Resume action launches MainActivity,
     * which then schedules the UIDT job while the app is visible.
     */
    private fun resumeActionIntent(
        context: Context,
        downloadId: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NOTIFICATION_RESUME_DOWNLOAD
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, downloadId)
            data = Uri.parse("downloadfree://resume/$downloadId")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent.getActivity(
                context,
                ("resume:$downloadId").hashCode(),
                intent,
                flags
            )
        } else {
            receiverActionIntent(
                context,
                DownloadHelper.ACTION_RESUME_DOWNLOAD,
                downloadId
            )
        }
    }

    private fun resumeActionIntent(
        context: Context,
        downloadId: Long
    ): PendingIntent = resumeActionIntent(context, downloadId.toString())

    private fun displayTitle(item: DownloadItem): String {
        return if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }
    }

    /** "1h 05m", "4m 12s", "35s" */
    fun formatEta(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val sec = seconds % 60
        return when {
            h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
            m > 0 -> "${m}m ${sec.toString().padStart(2, '0')}s"
            else -> "${sec}s"
        }
    }

    /**
     * Al tocar la notificación de una descarga terminada se abre el vídeo directamente
     * (como hace IDM). Si el archivo ya no existe o no se puede compartir con FileProvider,
     * se abre la pestaña Descargas de la app.
     */
    private fun openFilePendingIntent(context: Context, item: DownloadItem): PendingIntent {
        val file = java.io.File(item.localFilePath)
        if (!file.exists()) return createOpenDownloadsPendingIntent(context)
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            PendingIntent.getActivity(context, "open:${item.id}".hashCode(), intent, flags)
        } catch (_: Exception) {
            createOpenDownloadsPendingIntent(context)
        }
    }

    fun formatSpeed(speed: Long): String {
        return when {
            speed <= 0L -> "Conectando..."
            else -> formatDownloadSpeed(speed)
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
        val remainingAll = (sumTotal - sumDone).coerceAtLeast(0L)
        val etaAll = if (totalSpeed > 2048L && remainingAll > 0L) remainingAll / totalSpeed else 0L
        val text = when {
            indeterminate -> formatSpeed(totalSpeed)
            etaAll > 0L -> "$overall% • ${formatSpeed(totalSpeed)} • ${formatEta(etaAll)}"
            else -> "$overall% • ${formatSpeed(totalSpeed)}"
        }

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
                receiverActionIntent(context, DownloadHelper.ACTION_PAUSE_ALL, -1L)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar todo",
                receiverActionIntent(context, DownloadHelper.ACTION_CANCEL_ALL, -1L)
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
        val isIndeterminate = item.totalBytes <= 0L

        val sizeStr = if (!isIndeterminate) {
            "${formatByteSize(item.downloadedBytes)} de ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }
        val speedStr = formatSpeed(speed)
        val etaStr = when {
            eta > 0L -> formatEta(eta)
            speed <= 0L -> "Conectando…"
            else -> "Calculando…"
        }

        // Línea compacta (estilo IDM): "42% • 12.4 MB/s • 1m 20s"
        val compact = if (isIndeterminate) "$sizeStr • $speedStr" else "$progress% • $speedStr • $etaStr"
        // Vista expandida: una línea por dato
        val expanded = buildString {
            append(sizeStr)
            if (!isIndeterminate) append("  ($progress%)")
            append("\nVelocidad: ").append(speedStr)
            if (!isIndeterminate) append("\nTiempo restante: ").append(etaStr)
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setContentTitle(title)
            .setContentText(compact)
            .setSubText("Descargando")
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(expanded))
            .setSmallIcon(R.drawable.ic_notification_download)
            .setColor(0xFF00897B.toInt())
            .setProgress(100, if (isIndeterminate) 0 else progress.coerceIn(0, 100), isIndeterminate)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSortKey("download_${item.id}")
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(GROUP_KEY_DOWNLOADS)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pausar",
                receiverActionIntent(context, DownloadHelper.ACTION_PAUSE_DOWNLOAD, item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                receiverActionIntent(context, DownloadHelper.ACTION_CANCEL_DOWNLOAD, item.id)
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
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("En pausa")
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setSmallIcon(R.drawable.ic_notification_pause)
            .setColor(0xFFF59E0B.toInt())
            .setProgress(100, item.progress, false)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(false)
            .setOngoing(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_KEY_DOWNLOADS)
            .addAction(
                android.R.drawable.ic_media_play,
                "Reanudar",
                resumeActionIntent(context, item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                receiverActionIntent(context, DownloadHelper.ACTION_CANCEL_DOWNLOAD, item.id)
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
        val detail = "Se interrumpió • $sizeInfo. Reintenta para continuar donde quedó."

        return NotificationCompat.Builder(context, CHANNEL_ERROR_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(title)
            .setContentText(detail)
            .setSubText("Descarga fallida")
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
                resumeActionIntent(context, item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                receiverActionIntent(context, DownloadHelper.ACTION_CANCEL_DOWNLOAD, item.id)
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
        val detail = "Descarga completa • $sizeStr"

        return NotificationCompat.Builder(context, CHANNEL_SUCCESS_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(title)
            .setContentText(detail)
            .setSubText("Completada")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText("$detail\nToca para reproducir")
            )
            .setSmallIcon(R.drawable.ic_notification_done)
            .setColor(0xFF10B981.toInt())
            .setContentIntent(openFilePendingIntent(context, item))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSortKey("download_${item.id}")
            .addAction(
                android.R.drawable.ic_media_play,
                "Abrir",
                openFilePendingIntent(context, item)
            )
            .addAction(
                android.R.drawable.ic_menu_agenda,
                "Descargas",
                createOpenDownloadsPendingIntent(context)
            )
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
