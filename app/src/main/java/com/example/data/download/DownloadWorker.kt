package com.example.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.PeliculaPreferences
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.formatByteSize
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.utils.PermissionHelper
import com.example.utils.VpnProxyDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val preferences = PeliculaPreferences(appContext)
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Video"
        val coverUrl = inputData.getString(KEY_COVER_URL) ?: ""
        val year = inputData.getString(KEY_YEAR) ?: ""
        val type = inputData.getString(KEY_TYPE) ?: "pl"
        val localFilePath = inputData.getString(KEY_LOCAL_FILE_PATH) ?: ""

        val destFile = File(localFilePath)

        // Check VPN / Proxy
        if (VpnProxyDetector.isVpnOrProxyActive(appContext)) {
            handlePausedOnVpn(downloadId, title, coverUrl, year, type, destFile)
            return@withContext Result.success()
        }

        // Initialize Foreground Info
        val initialItem = DownloadItem(
            id = downloadId,
            title = title,
            originalVideoUrl = videoUrl,
            coverUrl = coverUrl,
            year = year,
            type = type,
            localFilePath = localFilePath,
            status = DownloadStatus.DOWNLOADING,
            progress = 0
        )

        try {
            setForeground(createForegroundInfo(initialItem, 0, 0L, 0L))
        } catch (_: Exception) {}

        var retryCount = 0
        val maxRetries = 3
        var completedSuccessfully = false

        DownloadBandwidthCoordinator.registerStream(downloadId)

        var actualFile = destFile
        val safeDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir

        // Proactive write access validation
        try {
            actualFile.parentFile?.mkdirs()
            if (!actualFile.exists()) {
                actualFile.createNewFile()
                actualFile.delete()
            }
        } catch (_: Exception) {
            safeDir.mkdirs()
            actualFile = File(safeDir, destFile.name)
            preferences.addOrUpdateDownload(initialItem.copy(localFilePath = actualFile.absolutePath))
        }

        while (isActive && retryCount < maxRetries && !completedSuccessfully && !isStopped) {
            if (VpnProxyDetector.isVpnOrProxyActive(appContext)) {
                DownloadBandwidthCoordinator.unregisterStream(downloadId)
                handlePausedOnVpn(downloadId, title, coverUrl, year, type, actualFile)
                return@withContext Result.success()
            }

            var input: InputStream? = null
            var raf: RandomAccessFile? = null
            var downloaded = if (actualFile.exists()) actualFile.length() else 0L

            try {
                try {
                    actualFile.parentFile?.mkdirs()
                    raf = RandomAccessFile(actualFile, "rw")
                } catch (e: Exception) {
                    if (e.message?.contains("EACCES", ignoreCase = true) == true ||
                        e.message?.contains("Permission denied", ignoreCase = true) == true ||
                        e is SecurityException
                    ) {
                        safeDir.mkdirs()
                        actualFile = File(safeDir, destFile.name)
                        preferences.addOrUpdateDownload(initialItem.copy(localFilePath = actualFile.absolutePath))
                        downloaded = if (actualFile.exists()) actualFile.length() else 0L
                        raf = RandomAccessFile(actualFile, "rw")
                    } else {
                        throw e
                    }
                }

                val requestBuilder = Request.Builder().url(videoUrl)
                if (downloaded > 0) {
                    requestBuilder.addHeader("Range", "bytes=$downloaded-")
                }

                val call = okHttpClient.newCall(requestBuilder.build())
                val response = call.execute()

                if (!response.isSuccessful && response.code != 206) {
                    if (response.code == 416) {
                        // Range Not Satisfiable -> check if already complete
                        if (downloaded > 1024) {
                            completedSuccessfully = true
                        } else {
                            downloaded = 0L
                            actualFile.delete()
                        }
                    } else {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                }

                if (completedSuccessfully) {
                    DownloadBandwidthCoordinator.unregisterStream(downloadId)
                    val finalSize = actualFile.length()
                    val completed = initialItem.copy(
                        localFilePath = actualFile.absolutePath,
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        downloadedBytes = finalSize,
                        totalBytes = finalSize,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(completed)
                    notificationManager.cancel(getNotificationId(downloadId))
                    showCompletedNotification(completed)
                    DownloadHelper.getActiveInstance(appContext).notifyTaskFinished(downloadId)
                    return@withContext Result.success()
                }

                val body = response.body ?: throw Exception("Cuerpo de respuesta vacío")
                val contentLength = body.contentLength()

                val contentRangeHeader = response.header("Content-Range")
                val parsedTotalFromHeader = contentRangeHeader?.substringAfterLast('/', "")?.toLongOrNull() ?: -1L

                val totalBytes = when {
                    parsedTotalFromHeader > 0L -> parsedTotalFromHeader
                    response.code == 206 && contentLength > 0L -> downloaded + contentLength
                    contentLength > 0L -> contentLength
                    else -> 0L
                }

                if (response.code == 206) {
                    raf.seek(downloaded)
                } else {
                    raf.setLength(0)
                    downloaded = 0L
                }

                input = body.byteStream()
                val buffer = ByteArray(8 * 1024)
                var bytesRead = 0
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L
                var lastSpeed = 0L
                var lastEta = 0L

                while (isActive && !isStopped) {
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    raf.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    // Equitable Bandwidth Balancer: pace stream dynamically to ensure equal speed share across all active downloads
                    DownloadBandwidthCoordinator.paceTransfer(downloadId, bytesRead)

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastTime

                    // Update cadence: exactly every 700 milliseconds
                    if (timeDiff >= 700L) {
                        lastSpeed = (bytesSinceLastUpdate * 1000L) / timeDiff.coerceAtLeast(1L)
                        val remainingBytes = (totalBytes - downloaded).coerceAtLeast(0L)
                        lastEta = if (lastSpeed > 2048 && remainingBytes > 0) remainingBytes / lastSpeed else 0L

                        val currentProgress = if (totalBytes > 0) {
                            ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                        } else {
                            0
                        }

                        val progressItem = initialItem.copy(
                            status = DownloadStatus.DOWNLOADING,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            progress = currentProgress,
                            speedBytesPerSec = lastSpeed,
                            etaSeconds = lastEta
                        )

                        // Report to in-memory Live Progress StateFlow tracker for strict 700ms smooth UI rendering
                        DownloadHelper.getActiveInstance(appContext).reportProgress(
                            downloadId = downloadId,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            progress = currentProgress,
                            speedBytesPerSec = lastSpeed,
                            etaSeconds = lastEta
                        )

                        // Update notification directly via NotificationManager to avoid service flickering
                        updateProgressNotification(progressItem, currentProgress, lastSpeed, lastEta)

                        bytesSinceLastUpdate = 0L
                        lastTime = now
                    }
                }

                if (bytesRead == -1 && !isStopped) {
                    DownloadBandwidthCoordinator.unregisterStream(downloadId)
                    DownloadHelper.getActiveInstance(appContext).clearLiveProgress(downloadId)
                    val finalSize = destFile.length()
                    val completed = initialItem.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        downloadedBytes = finalSize,
                        totalBytes = finalSize,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(completed)
                    notificationManager.cancel(getNotificationId(downloadId))
                    showCompletedNotification(completed)
                    DownloadHelper.getActiveInstance(appContext).notifyTaskFinished(downloadId)
                    completedSuccessfully = true
                    return@withContext Result.success()
                }

            } catch (e: CancellationException) {
                // Handled in finally / outer
                break
            } catch (e: Exception) {
                if (isStopped) break
                retryCount++
                if (retryCount < maxRetries) {
                    delay(1500L)
                } else {
                    DownloadBandwidthCoordinator.unregisterStream(downloadId)
                    DownloadHelper.getActiveInstance(appContext).clearLiveProgress(downloadId)
                    val failedItem = initialItem.copy(
                        localFilePath = actualFile.absolutePath,
                        status = DownloadStatus.FAILED,
                        downloadedBytes = if (actualFile.exists()) actualFile.length() else 0L,
                        totalBytes = if (actualFile.exists()) actualFile.length() else 0L,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(failedItem)
                    notificationManager.cancel(getNotificationId(downloadId))
                    showFailedNotification(failedItem, e.localizedMessage ?: "Error de red")
                    DownloadHelper.getActiveInstance(appContext).notifyTaskFinished(downloadId)
                    return@withContext Result.failure()
                }
            } finally {
                try { input?.close() } catch (_: Exception) {}
                try { raf?.close() } catch (_: Exception) {}
            }
        }

        DownloadBandwidthCoordinator.unregisterStream(downloadId)

        if (isStopped) {
            DownloadHelper.getActiveInstance(appContext).clearLiveProgress(downloadId)
            val currentList = preferences.downloads.first()
            val existing = currentList.find { it.id == downloadId }

            if (existing == null || existing.status == DownloadStatus.CANCELLED) {
                // Item was cancelled/deleted: dismiss progress notification and do NOT recreate
                notificationManager.cancel(getNotificationId(downloadId))
            } else if (existing.status == DownloadStatus.PAUSED) {
                val finalDownloaded = if (actualFile.exists()) actualFile.length() else existing.downloadedBytes
                val total = if (existing.totalBytes > 0) existing.totalBytes else finalDownloaded
                val progress = if (total > 0) ((finalDownloaded * 100) / total).toInt().coerceIn(0, 99) else existing.progress
                val pausedItem = existing.copy(
                    localFilePath = actualFile.absolutePath,
                    downloadedBytes = finalDownloaded,
                    totalBytes = total,
                    progress = progress,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
                preferences.addOrUpdateDownload(pausedItem)
                showPausedNotification(pausedItem)
            } else if (existing.status == DownloadStatus.PENDING) {
                // Item was queued / demoted: dismiss progress notification
                notificationManager.cancel(getNotificationId(downloadId))
            }
            DownloadHelper.getActiveInstance(appContext).notifyTaskFinished(downloadId)
        }

        Result.success()
    }

    private suspend fun handlePausedOnVpn(
        downloadId: String,
        title: String,
        coverUrl: String,
        year: String,
        type: String,
        destFile: File
    ) {
        val downloaded = if (destFile.exists()) destFile.length() else 0L
        val paused = DownloadItem(
            id = downloadId,
            title = title,
            originalVideoUrl = "",
            coverUrl = coverUrl,
            year = year,
            type = type,
            localFilePath = destFile.absolutePath,
            status = DownloadStatus.PAUSED,
            downloadedBytes = downloaded,
            progress = 0,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        preferences.addOrUpdateDownload(paused)
        showPausedNotification(paused)
        AppToastManager.show("Descarga en pausa: VPN o Proxy detectado", ToastType.WARNING)
        DownloadHelper.getActiveInstance(appContext).notifyTaskFinished(downloadId)
    }

    private fun buildProgressNotification(
        item: DownloadItem,
        progress: Int,
        speed: Long,
        eta: Long
    ): Notification {
        ensureNotificationChannels()

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }

        val speedStr = if (speed > 0) formatByteSize(speed) + "/s" else ""
        val etaStr = if (eta > 0) {
            val m = eta / 60
            val s = eta % 60
            if (m > 0) "${m}m ${s}s restantes" else "${s}s restantes"
        } else ""

        val subtitle = listOfNotNull(
            speedStr.ifBlank { null },
            "$progress%",
            etaStr.ifBlank { null }
        ).joinToString(" • ").ifBlank { "Descargando..." }

        return NotificationCompat.Builder(appContext, DownloadHelper.CHANNEL_PROGRESS_ID)
            .setContentTitle(displayTitle)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(0xFF00897B.toInt())
            .setProgress(100, progress, item.totalBytes <= 0)
            .setContentIntent(getContentPendingIntent())
            .addAction(
                R.drawable.ic_notification_pause,
                "Pausar",
                getPausePendingIntent(item.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                getCancelPendingIntent(item.id)
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setGroup("active_downloads_group")
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateProgressNotification(
        item: DownloadItem,
        progress: Int,
        speed: Long,
        eta: Long
    ) {
        if (!PermissionHelper.hasNotificationPermission(appContext)) return
        try {
            val notif = buildProgressNotification(item, progress, speed, eta)
            notificationManager.notify(getNotificationId(item.id), notif)
        } catch (_: Exception) {}
    }

    private fun createForegroundInfo(
        item: DownloadItem,
        progress: Int,
        speed: Long,
        eta: Long
    ): ForegroundInfo {
        ensureNotificationChannels()

        val summaryNotification = NotificationCompat.Builder(appContext, DownloadHelper.CHANNEL_PROGRESS_ID)
            .setContentTitle("Download Free")
            .setContentText("Servicio de descargas activo")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(0xFF00897B.toInt())
            .setGroup("active_downloads_group")
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setContentIntent(getContentPendingIntent())
            .build()

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                summaryNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                DownloadHelper.FOREGROUND_SERVICE_NOTIFICATION_ID,
                summaryNotification
            )
        }

        updateProgressNotification(item, progress, speed, eta)
        return foregroundInfo
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                DownloadHelper.CHANNEL_PROGRESS_ID,
                DownloadHelper.CHANNEL_PROGRESS_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra la velocidad, tiempo estimado y porcentaje de descargas"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val alertsChannel = NotificationChannel(
                DownloadHelper.CHANNEL_ALERTS_ID,
                DownloadHelper.CHANNEL_ALERTS_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de descargas completadas y errores"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    private fun getContentPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("initial_tab", 1)
            putExtra("skip_splash", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(appContext, 1001, intent, flags)
    }

    private fun getPausePendingIntent(itemId: String): PendingIntent {
        val intent = Intent(appContext, DownloadActionReceiver::class.java).apply {
            action = DownloadHelper.ACTION_PAUSE_DOWNLOAD
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(appContext, (itemId + "_pause").hashCode(), intent, flags)
    }

    private fun getResumePendingIntent(itemId: String): PendingIntent {
        val intent = Intent(appContext, DownloadActionReceiver::class.java).apply {
            action = DownloadHelper.ACTION_RESUME_DOWNLOAD
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(appContext, (itemId + "_resume").hashCode(), intent, flags)
    }

    private fun getCancelPendingIntent(itemId: String): PendingIntent {
        val intent = Intent(appContext, DownloadActionReceiver::class.java).apply {
            action = DownloadHelper.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(appContext, (itemId + "_cancel").hashCode(), intent, flags)
    }

    private fun showCompletedNotification(item: DownloadItem) {
        if (!PermissionHelper.hasNotificationPermission(appContext)) return
        try {
            val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            val notification = NotificationCompat.Builder(appContext, DownloadHelper.CHANNEL_ALERTS_ID)
                .setContentTitle("Descarga completada")
                .setContentText(displayTitle)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setColor(0xFF10B981.toInt())
                .setContentIntent(getContentPendingIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            notificationManager.notify(getNotificationId(item.id), notification)
        } catch (_: Exception) {}
    }

    private fun showPausedNotification(item: DownloadItem) {
        if (!PermissionHelper.hasNotificationPermission(appContext)) return
        try {
            val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            val subtitle = "${item.progress}% • Pausada"
            val notification = NotificationCompat.Builder(appContext, DownloadHelper.CHANNEL_PROGRESS_ID)
                .setContentTitle(displayTitle)
                .setContentText(subtitle)
                .setSmallIcon(R.drawable.ic_notification_pause)
                .setColor(0xFFF59E0B.toInt())
                .setProgress(100, item.progress, item.totalBytes <= 0)
                .setContentIntent(getContentPendingIntent())
                .addAction(
                    android.R.drawable.ic_media_play,
                    "Reanudar",
                    getResumePendingIntent(item.id)
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancelar",
                    getCancelPendingIntent(item.id)
                )
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(false)
                .setWhen(0L)
                .setSortKey("download_${item.id}")
                .setGroup("active_downloads_group")
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setOnlyAlertOnce(true)
                .build()
            notificationManager.notify(getNotificationId(item.id), notification)
        } catch (_: Exception) {}
    }

    private fun showFailedNotification(item: DownloadItem, error: String) {
        if (!PermissionHelper.hasNotificationPermission(appContext)) return
        try {
            val notification = NotificationCompat.Builder(appContext, DownloadHelper.CHANNEL_ALERTS_ID)
                .setContentTitle("Error al descargar")
                .setContentText("${item.title}: $error")
                .setSmallIcon(R.drawable.ic_notification_error)
                .setColor(0xFFEF4444.toInt())
                .setContentIntent(getContentPendingIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            notificationManager.notify(getNotificationId(item.id), notification)
        } catch (_: Exception) {}
    }

    private fun getNotificationId(id: String): Int = id.hashCode()

    companion object {
        const val KEY_DOWNLOAD_ID = "key_download_id"
        const val KEY_VIDEO_URL = "key_video_url"
        const val KEY_TITLE = "key_title"
        const val KEY_COVER_URL = "key_cover_url"
        const val KEY_YEAR = "key_year"
        const val KEY_TYPE = "key_type"
        const val KEY_LOCAL_FILE_PATH = "key_local_file_path"
    }
}
