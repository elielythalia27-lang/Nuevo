package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.download.DownloadActionReceiver
import com.example.data.download.DownloadForegroundService
import com.example.data.download.DownloadHelper
import com.example.data.model.DownloadItem

/**
 * Robust Notification Utility for Android 5.0 through Android 15.
 * Features:
 * - Dedicated low-importance channel for download progress (silent, no annoying vibrations or popups).
 * - High-clarity IDM-style progress cards with Pause and Cancel quick actions.
 * - Full Android 12+ PendingIntent immutability support.
 * - Zero flickering on status bar icons and notifications.
 */
object NotificationUtils {

    const val CHANNEL_PROGRESS_ID = "channel_download_progress_v6"
    const val CHANNEL_SUCCESS_ID = "channel_download_success_v6"
    const val CHANNEL_ERROR_ID = "channel_download_error_v6"
    const val CHANNEL_NOTICES_ID = "channel_app_notices_v6"

    const val GROUP_DOWNLOADS = "com.downloadfree.GROUP_DOWNLOADS"
    const val SUMMARY_NOTIFICATION_ID = 69696

    private const val GROUP_DOWNLOADS_ID = "group_download_management"
    private const val GROUP_SYSTEM_ID = "group_system_management"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val downloadsGroup = NotificationChannelGroup(GROUP_DOWNLOADS_ID, "Gestión de Descargas")
            val systemGroup = NotificationChannelGroup(GROUP_SYSTEM_ID, "Sistema y Avisos")
            notificationManager.createNotificationChannelGroups(listOf(downloadsGroup, systemGroup))

            // Channel: Progress (Silent, Ongoing, Public lockscreen)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Progreso en tiempo real",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Velocidad, tiempo restante y barra de descarga interactiva"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel: Completed (Default importance with notification sound/badge)
            val successChannel = NotificationChannel(
                CHANNEL_SUCCESS_ID,
                "Descargas completadas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Avisos al finalizar las descargas de películas y series"
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel: Errors & Alerts
            val errorChannel = NotificationChannel(
                CHANNEL_ERROR_ID,
                "Errores y alertas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Alertas de conexión, VPN o almacenamiento"
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            // Channel: Notices
            val noticesChannel = NotificationChannel(
                CHANNEL_NOTICES_ID,
                "Avisos de la app",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                group = GROUP_SYSTEM_ID
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(progressChannel, successChannel, errorChannel, noticesChannel)
            )
        }
    }

    fun createCompatBuilder(
        context: Context,
        channelId: String
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channelId)

        when (channelId) {
            CHANNEL_PROGRESS_ID -> {
                builder.setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVibrate(longArrayOf(0L))
                    .setSound(null)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            CHANNEL_SUCCESS_ID -> {
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            CHANNEL_ERROR_ID -> {
                builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            }
            else -> {
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            }
        }

        return builder
    }

    fun buildProgressNotification(
        context: Context,
        item: DownloadItem,
        progress: Int,
        speed: Long,
        eta: Long
    ): Notification {
        initNotificationChannels(context)

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }

        val sizeStr = if (item.totalBytes > 0L) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }

        val speedStr = if (speed > 0L) {
            if (speed >= 1024 * 1024) String.format(java.util.Locale.US, "%.1f MB/s", speed / (1024.0 * 1024.0))
            else "${speed / 1024} KB/s"
        } else "Conectando..."
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

        return createCompatBuilder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle("Descargando: $displayTitle")
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
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildPausedNotification(
        context: Context,
        item: DownloadItem
    ): Notification {
        initNotificationChannels(context)

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }

        val sizeInfo = if (item.totalBytes > 0L) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }
        val subtitle = "${item.progress}% • $sizeInfo • En pausa"

        return createCompatBuilder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle("En pausa: $displayTitle")
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
            .build()
    }

    fun buildCompletedNotification(
        context: Context,
        item: DownloadItem
    ): Notification {
        initNotificationChannels(context)

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }
        val sizeStr = formatByteSize(item.totalBytes.coerceAtLeast(item.downloadedBytes))
        val detail = "$sizeStr • Descarga completada. Lista para ver sin conexión."

        return createCompatBuilder(context, CHANNEL_SUCCESS_ID)
            .setContentTitle("Descarga completada")
            .setContentText("$displayTitle • $sizeStr")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$displayTitle\n$detail"))
            .setSmallIcon(R.drawable.ic_notification_done)
            .setColor(0xFF10B981.toInt())
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun sanitizeErrorMessage(rawMessage: String?): String {
        if (rawMessage.isNullOrBlank()) return "Error de conexión"
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("wi-fi") || lower.contains("wifi") -> "Conexión Wi-Fi requerida"
            lower.contains("vpn") || lower.contains("proxy") -> "En pausa por VPN activa"
            lower.contains("sin conexión") || lower.contains("internet") || lower.contains("connect") || lower.contains("timeout") || lower.contains("host") || lower.contains("socket") -> "Sin conexión a internet"
            else -> "Error de conexión con el servidor"
        }
    }

    fun buildErrorNotification(
        context: Context,
        item: DownloadItem,
        errorMessage: String
    ): Notification {
        initNotificationChannels(context)

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }
        val cleanError = sanitizeErrorMessage(errorMessage)
        val subtitle = "$displayTitle • $cleanError"

        return createCompatBuilder(context, CHANNEL_ERROR_ID)
            .setContentTitle("Descarga detenida")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$displayTitle\n$cleanError. Puedes reanudar cuando recuperes conexión."))
            .setSmallIcon(R.drawable.ic_notification_error)
            .setColor(0xFFEF4444.toInt())
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
    }

    fun buildSummaryNotification(context: Context): Notification {
        initNotificationChannels(context)

        return createCompatBuilder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle("Download Free")
            .setContentText("Servicio de descargas activo en segundo plano")
            .setSmallIcon(R.drawable.ic_notification_download)
            .setColor(0xFF00897B.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .build()
    }

    fun createOpenDownloadsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("initial_tab", 1)
            putExtra("skip_splash", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 1001, intent, flags)
    }

    fun createPausePendingIntent(context: Context, itemId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadHelper.ACTION_PAUSE_DOWNLOAD
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, (itemId + "_pause").hashCode(), intent, flags)
    }

    fun createResumePendingIntent(context: Context, itemId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadHelper.ACTION_RESUME_DOWNLOAD
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, (itemId + "_resume").hashCode(), intent, flags)
    }

    fun createCancelPendingIntent(context: Context, itemId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadHelper.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, (itemId + "_cancel").hashCode(), intent, flags)
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "${Math.round(gb)} GB"
            mb >= 1.0 -> "${Math.round(mb)} MB"
            kb >= 1.0 -> "${Math.round(kb)} KB"
            else -> "$bytes B"
        }
    }

    fun getNotificationId(id: String): Int = (id.hashCode() and 0x7FFFFFFF).coerceAtLeast(1000)
}
