package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.download.DownloadActionReceiver
import com.example.data.download.DownloadHelper
import com.example.data.model.DownloadItem

/**
 * Robust Notification Channel Utility Class for Android 8.0+ (API 26+)
 * with full backward compatibility down to Android 5.0 (API 21+).
 *
 * Categorizes notifications into dedicated channels:
 * - Download Progress (Low importance, ongoing, silent, grouped, zero vibration)
 * - Download Completed / Success (Default importance, vibration, badge, clickable action)
 * - Download Errors / Warnings (High importance, alert, vibration, badge)
 * - App Updates / General Notices (Default importance)
 */
object NotificationUtils {

    // Notification Channel IDs
    const val CHANNEL_PROGRESS_ID = "channel_download_progress_v5"
    const val CHANNEL_SUCCESS_ID = "channel_download_success_v5"
    const val CHANNEL_ERROR_ID = "channel_download_error_v5"
    const val CHANNEL_NOTICES_ID = "channel_app_notices_v5"

    // Grouping constants
    const val GROUP_DOWNLOADS = "com.downloadfree.GROUP_DOWNLOADS"
    const val GROUP_ERRORS = "com.downloadfree.GROUP_ERRORS"
    const val SUMMARY_NOTIFICATION_ID = 69696

    // Channel Groups (Android 8.0+)
    private const val GROUP_DOWNLOADS_ID = "group_download_management"
    private const val GROUP_SYSTEM_ID = "group_system_management"

    /**
     * Initializes all categorized notification channels and channel groups on Android 8.0+ (API 26+).
     * On older Android versions, this method safely does nothing while ensuring compatibility properties.
     */
    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Create Channel Groups (organizes channels in Android Settings)
            val downloadsGroup = NotificationChannelGroup(GROUP_DOWNLOADS_ID, "Gestión de Descargas")
            val systemGroup = NotificationChannelGroup(GROUP_SYSTEM_ID, "Sistema y Avisos")
            notificationManager.createNotificationChannelGroups(listOf(downloadsGroup, systemGroup))

            // 2. Channel: Download Progress (Low Importance, Silent, Lockscreen Public)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Progreso en tiempo real",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Muestra la velocidad, tiempo restante y barra de descarga en curso"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 3. Channel: Download Completed / Success (Default Importance with sound and badge)
            val successChannel = NotificationChannel(
                CHANNEL_SUCCESS_ID,
                "Descargas completadas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Avisos cuando una película o video ha finalizado su descarga con éxito"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 4. Channel: Download Errors (High Importance for network drop or disk space issues)
            val errorChannel = NotificationChannel(
                CHANNEL_ERROR_ID,
                "Errores y alertas de descarga",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Avisos importantes de errores de conexión, VPN o almacenamiento insuficiente"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            // 5. Channel: General App Notices
            val noticesChannel = NotificationChannel(
                CHANNEL_NOTICES_ID,
                "Avisos generales",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                group = GROUP_SYSTEM_ID
                description = "Actualizaciones de catálogo y recordatorios generales"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(progressChannel, successChannel, errorChannel, noticesChannel)
            )
        }
    }

    /**
     * Creates a standard Builder with full Android API 21+ backwards compatibility
     * (correctly sets priority, lockscreen visibility, sound & vibration on pre-Oreo).
     */
    fun createCompatBuilder(
        context: Context,
        channelId: String,
        importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channelId)

        // Backward compatibility for Android 7.1 and lower (API < 26)
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

    /**
     * Builds the interactive progress notification for an active download.
     */
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

        val sizeStr = if (item.totalBytes > 0) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }

        val speedStr = if (speed > 0) formatByteSize(speed) + "/s" else "0 KB/s"
        val etaStr = if (eta > 0) {
            val hours = eta / 3600
            val minutes = (eta % 3600) / 60
            val seconds = eta % 60
            when {
                hours > 0 -> "${hours}h ${minutes}m restantes"
                minutes > 0 -> "${minutes}m ${seconds}s restantes"
                else -> "${seconds}s restantes"
            }
        } else "Calculando tiempo..."

        val subtitle = "$progress% • $sizeStr • $speedStr • $etaStr"

        return createCompatBuilder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle("Descargando: $displayTitle")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(0xFF00897B.toInt())
            .setProgress(100, progress, item.totalBytes <= 0)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .addAction(
                R.drawable.ic_notification_pause,
                "Pausar",
                createPausePendingIntent(context, item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                createCancelPendingIntent(context, item.id)
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setGroup(GROUP_DOWNLOADS)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Builds the paused download notification (interactive, with Resume & Cancel buttons).
     */
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

        val sizeInfo = if (item.totalBytes > 0) {
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
            .setProgress(100, item.progress, item.totalBytes <= 0)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .addAction(
                android.R.drawable.ic_media_play,
                "Reanudar",
                createResumePendingIntent(context, item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                createCancelPendingIntent(context, item.id)
            )
            .setAutoCancel(false)
            .setOngoing(false)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Builds the completed / success notification.
     */
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
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setColor(0xFF10B981.toInt())
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    /**
     * Builds the error notification.
     */
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
        val subtitle = "$displayTitle: Conexión interrumpida (Progreso guardado)"

        return createCompatBuilder(context, CHANNEL_ERROR_ID)
            .setContentTitle("Descarga detenida")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$displayTitle\n$errorMessage. El progreso ha sido guardado para reanudar."))
            .setSmallIcon(R.drawable.ic_notification_error)
            .setColor(0xFFEF4444.toInt())
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
    }

    /**
     * Builds the summary notification for foreground service grouping.
     */
    fun buildSummaryNotification(context: Context): Notification {
        initNotificationChannels(context)

        return createCompatBuilder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle("Download Free")
            .setContentText("Servicio de descargas activo")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(0xFF00897B.toInt())
            .setGroup(GROUP_DOWNLOADS)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .build()
    }

    // PendingIntent Builders with API 23+ FLAG_IMMUTABLE compatibility
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
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun getNotificationId(id: String): Int = id.hashCode()
}
