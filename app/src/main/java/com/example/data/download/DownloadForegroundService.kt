package com.example.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R

class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        // Guarantee immediate startForeground in onCreate so the OS never throws
        // ForegroundServiceDidNotStartInTimeException under any timing circumstance
        ensureImmediateForeground()

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DownloadFree:ForegroundDownloadLock"
            )?.apply {
                setReferenceCounted(false)
                // 30 minute safety maximum, refreshed as needed
                acquire(30 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForegroundServiceInternal()
            return START_NOT_STICKY
        }

        // Re-ensure foreground is attached to current notification state
        ensureImmediateForeground()

        return START_STICKY
    }

    private fun ensureImmediateForeground() {
        try {
            ensureNotificationChannel()
            val initialNotification = buildImmediateNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    DownloadHelper.SUMMARY_NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(DownloadHelper.SUMMARY_NOTIFICATION_ID, initialNotification)
            }
        } catch (_: Exception) {}
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null && notificationManager.getNotificationChannel(DownloadHelper.CHANNEL_PROGRESS_ID) == null) {
                val progressChannel = NotificationChannel(
                    DownloadHelper.CHANNEL_PROGRESS_ID,
                    DownloadHelper.CHANNEL_PROGRESS_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Muestra la velocidad, tiempo estimado y barra de porcentaje"
                    setShowBadge(false)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(progressChannel)
            }
        }
    }

    private fun buildImmediateNotification(): Notification {
        return try {
            DownloadHelper.getActiveInstance(applicationContext).buildPlaceholderSummaryNotification()
        } catch (_: Exception) {
            NotificationCompat.Builder(this, DownloadHelper.CHANNEL_PROGRESS_ID)
                .setContentTitle("Gestor de Descargas")
                .setContentText("Descargando en segundo plano...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(0xFF0284C7.toInt())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }

    private fun stopForegroundServiceInternal() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(DownloadHelper.SUMMARY_NOTIFICATION_ID)
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_SERVICE = "com.downloadfree.START_DOWNLOAD_SERVICE"
        const val ACTION_STOP_SERVICE = "com.downloadfree.STOP_DOWNLOAD_SERVICE"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
