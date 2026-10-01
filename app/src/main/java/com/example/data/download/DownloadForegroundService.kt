package com.example.data.download

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.data.local.PeliculaPreferences
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.utils.NetworkUtils
import com.example.utils.NotificationUtils
import com.example.utils.PermissionHelper
import com.example.utils.VpnProxyDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance, 100% Android-compliant Foreground Service for downloads.
 * Modeled after professional download engines (IDM / 1DM, Seal, NewPipe):
 * - Direct HTTP streaming with resume support (Range headers).
 * - Stable, flicker-free notification channel and updates.
 * - Accurate speed and ETA calculation without false 99% jumps.
 * - Instant cancellation with file cleanup and immediate notification dismissal.
 * - Proper lifecycle management with startForeground and stopSelf.
 */
class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var preferences: PeliculaPreferences

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeItems = ConcurrentHashMap<String, DownloadItem>()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.downloadfree.ACTION_START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.downloadfree.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.downloadfree.ACTION_RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.downloadfree.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.downloadfree.ACTION_PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.downloadfree.ACTION_RESUME_ALL"
        const val ACTION_CANCEL_ALL = "com.downloadfree.ACTION_CANCEL_ALL"

        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        const val FOREGROUND_SERVICE_NOTIFICATION_ID = 88888

        fun startDownload(context: Context, item: DownloadItem) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, item.id)
            }
            startServiceCompat(context, intent)
        }

        fun pauseDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            startServiceCompat(context, intent)
        }

        fun resumeDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            startServiceCompat(context, intent)
        }

        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            startServiceCompat(context, intent)
        }

        fun pauseAll(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            startServiceCompat(context, intent)
        }

        fun resumeAll(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME_ALL
            }
            startServiceCompat(context, intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            startServiceCompat(context, intent)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        preferences = PeliculaPreferences(applicationContext)
        NotificationUtils.initNotificationChannels(applicationContext)

        // Promote to Foreground Service immediately to satisfy Android OS requirements
        startInForeground()
    }

    private fun startInForeground() {
        try {
            val summaryNotif = NotificationUtils.buildSummaryNotification(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_SERVICE_NOTIFICATION_ID,
                    summaryNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_SERVICE_NOTIFICATION_ID, summaryNotif)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            checkServiceLiveness()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (!id.isNullOrBlank()) {
                    val helper = DownloadHelper.getActiveInstance(applicationContext)
                    val item = helper.getItem(id)
                    if (item != null) {
                        handleStartDownload(item)
                    }
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (!id.isNullOrBlank()) {
                    handlePauseDownload(id)
                }
            }
            ACTION_RESUME_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (!id.isNullOrBlank()) {
                    handleResumeDownload(id)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (!id.isNullOrBlank()) {
                    handleCancelDownload(id)
                }
            }
            ACTION_PAUSE_ALL -> {
                handlePauseAll()
            }
            ACTION_RESUME_ALL -> {
                handleResumeAll()
            }
            ACTION_CANCEL_ALL -> {
                handleCancelAll()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartDownload(item: DownloadItem) {
        // Cancel any existing job for this item
        activeJobs.remove(item.id)?.cancel()
        activeItems[item.id] = item

        val job = serviceScope.launch {
            executeDownload(item)
        }
        activeJobs[item.id] = job
        updateForegroundSummary()
    }

    private fun handlePauseDownload(downloadId: String) {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        val job = activeJobs.remove(downloadId)
        val item = activeItems.remove(downloadId) ?: helper.getItem(downloadId)

        job?.cancel()
        helper.clearLiveProgress(downloadId)

        if (item != null) {
            val file = File(item.localFilePath)
            val downloadedBytes = if (file.exists()) file.length() else item.downloadedBytes
            val totalBytes = item.totalBytes.coerceAtLeast(0L)
            val progress = if (totalBytes > 0L && downloadedBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
            } else {
                0
            }

            val pausedItem = item.copy(
                status = DownloadStatus.PAUSED,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                progress = progress,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )

            helper.updateItemState(pausedItem)
            serviceScope.launch {
                preferences.addOrUpdateDownload(pausedItem)
                helper.checkAndStartNextPending()
            }

            if (PermissionHelper.hasNotificationPermission(applicationContext)) {
                try {
                    val notif = NotificationUtils.buildPausedNotification(applicationContext, pausedItem)
                    notificationManager.notify(NotificationUtils.getNotificationId(downloadId), notif)
                } catch (_: Exception) {}
            }
        }

        checkServiceLiveness()
    }

    private fun handleResumeDownload(downloadId: String) {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        val item = helper.getItem(downloadId) ?: return

        if (VpnProxyDetector.isVpnOrProxyActive(applicationContext)) {
            AppToastManager.show("Desactiva la VPN o Proxy para reanudar la descarga", ToastType.WARNING)
            return
        }

        if (!NetworkUtils.isConnected(applicationContext)) {
            AppToastManager.show("Sin conexión a internet", ToastType.WARNING)
            return
        }

        handleStartDownload(item.copy(status = DownloadStatus.DOWNLOADING))
    }

    private fun handleCancelDownload(downloadId: String) {
        val helper = DownloadHelper.getActiveInstance(applicationContext)

        // Cancel job and clear tracking immediately
        activeJobs.remove(downloadId)?.cancel()
        val item = activeItems.remove(downloadId) ?: helper.getItem(downloadId)
        helper.clearLiveProgress(downloadId)

        // Immediately dismiss the notification so it never lingers or shows 99%
        try {
            notificationManager.cancel(NotificationUtils.getNotificationId(downloadId))
        } catch (_: Exception) {}

        helper.removeItemFromState(downloadId)

        serviceScope.launch {
            if (item != null) {
                try {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            preferences.removeDownload(downloadId)
            helper.checkAndStartNextPending()
            checkServiceLiveness()
        }
    }

    private fun handlePauseAll() {
        val currentActiveIds = activeJobs.keys().toList()
        currentActiveIds.forEach { id ->
            handlePauseDownload(id)
        }
    }

    private fun handleResumeAll() {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        val pausedOrPending = helper.liveDownloadsState.value.filter {
            it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING
        }
        pausedOrPending.forEach { item ->
            handleResumeDownload(item.id)
        }
    }

    private fun handleCancelAll() {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        val activeOrPaused = helper.liveDownloadsState.value.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING
        }
        activeOrPaused.forEach { item ->
            handleCancelDownload(item.id)
        }
    }

    private suspend fun executeDownload(initialItem: DownloadItem) {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        val downloadId = initialItem.id
        val videoUrl = initialItem.originalVideoUrl

        if (videoUrl.isBlank()) {
            handleDownloadError(initialItem, "URL de video no válida")
            return
        }

        // Check VPN / Proxy
        if (VpnProxyDetector.isVpnOrProxyActive(applicationContext)) {
            handlePausedOnVpn(initialItem)
            return
        }

        var actualFile = File(initialItem.localFilePath)
        val safeDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: applicationContext.filesDir

        // Validate write access
        try {
            actualFile.parentFile?.mkdirs()
            if (!actualFile.exists()) {
                actualFile.createNewFile()
                actualFile.delete()
            }
        } catch (_: Exception) {
            safeDir.mkdirs()
            actualFile = File(safeDir, actualFile.name)
        }

        var downloadedBytes = if (actualFile.exists()) actualFile.length() else 0L
        var totalBytes = initialItem.totalBytes.coerceAtLeast(0L)

        // Show initial connecting / indeterminate progress notification (0%, NEVER 99%)
        val connectingItem = initialItem.copy(
            localFilePath = actualFile.absolutePath,
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progress = if (totalBytes > 0L) ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99) else 0,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        helper.updateItemState(connectingItem)
        postProgressNotification(connectingItem, connectingItem.progress, 0L, 0L)

        DownloadBandwidthCoordinator.registerStream(downloadId)

        var retryCount = 0
        val maxRetries = 3
        var completed = false

        while (serviceScope.isActive && retryCount < maxRetries && !completed) {
            if (!activeJobs.containsKey(downloadId)) break

            var input: InputStream? = null
            var raf: RandomAccessFile? = null

            try {
                if (VpnProxyDetector.isVpnOrProxyActive(applicationContext)) {
                    DownloadBandwidthCoordinator.unregisterStream(downloadId)
                    handlePausedOnVpn(connectingItem)
                    return
                }

                downloadedBytes = if (actualFile.exists()) actualFile.length() else 0L

                val requestBuilder = Request.Builder().url(videoUrl)
                if (downloadedBytes > 0L) {
                    requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    if (response.code == 416) {
                        // Range Not Satisfiable: file might already be complete
                        if (downloadedBytes > 1024L) {
                            completed = true
                        } else {
                            downloadedBytes = 0L
                            actualFile.delete()
                        }
                    } else {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                }

                if (completed) {
                    markDownloadSuccess(connectingItem, actualFile)
                    return
                }

                val body = response.body ?: throw Exception("Cuerpo de respuesta vacío")
                val contentLength = body.contentLength()
                val contentRangeHeader = response.header("Content-Range")
                val parsedTotal = contentRangeHeader?.substringAfterLast('/', "")?.toLongOrNull() ?: -1L

                totalBytes = when {
                    parsedTotal > 0L -> parsedTotal
                    response.code == 206 && contentLength > 0L -> downloadedBytes + contentLength
                    contentLength > 0L -> contentLength
                    else -> 0L
                }

                actualFile.parentFile?.mkdirs()
                raf = RandomAccessFile(actualFile, "rw")
                if (response.code == 206) {
                    raf.seek(downloadedBytes)
                } else {
                    raf.setLength(0)
                    downloadedBytes = 0L
                }

                input = body.byteStream()
                val buffer = ByteArray(32 * 1024)
                var bytesRead = 0
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L
                var lastSpeed = 0L
                var lastEta = 0L

                while (serviceScope.isActive && activeJobs.containsKey(downloadId)) {
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    raf.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastTime

                    // Throttle notification and UI updates to exactly 800ms
                    if (timeDiff >= 800L) {
                        val isWifiOnly = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
                        if (isWifiOnly && !NetworkUtils.isWifiOrEthernet(applicationContext)) {
                            throw Exception("Conexión Wi-Fi requerida")
                        }
                        if (!NetworkUtils.isConnected(applicationContext)) {
                            throw Exception("Conexión a internet perdida")
                        }

                        lastSpeed = (bytesSinceLastUpdate * 1000L) / timeDiff.coerceAtLeast(1L)
                        val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                        lastEta = if (lastSpeed > 2048 && remainingBytes > 0) remainingBytes / lastSpeed else 0L

                        // Progress calculation: ALWAYS 0 if totalBytes is unknown/zero, NEVER 99%
                        val currentProgress = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                        } else {
                            0
                        }

                        val progressItem = connectingItem.copy(
                            localFilePath = actualFile.absolutePath,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = currentProgress,
                            speedBytesPerSec = lastSpeed,
                            etaSeconds = lastEta
                        )

                        // Report to SSOT for Compose UI
                        helper.reportProgress(
                            downloadId = downloadId,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = currentProgress,
                            speedBytesPerSec = lastSpeed,
                            etaSeconds = lastEta
                        )

                        // Update Notification
                        postProgressNotification(progressItem, currentProgress, lastSpeed, lastEta)

                        bytesSinceLastUpdate = 0L
                        lastTime = now
                    }
                }

                if (bytesRead == -1 && activeJobs.containsKey(downloadId)) {
                    markDownloadSuccess(connectingItem.copy(totalBytes = downloadedBytes), actualFile)
                    completed = true
                    return
                }

            } catch (e: CancellationException) {
                // Cancelled or paused by user
                break
            } catch (e: Exception) {
                if (!activeJobs.containsKey(downloadId)) break
                retryCount++
                if (retryCount < maxRetries) {
                    delay(1500L)
                } else {
                    handleDownloadError(connectingItem, e.localizedMessage ?: "Error de red")
                    return
                }
            } finally {
                try { input?.close() } catch (_: Exception) {}
                try { raf?.close() } catch (_: Exception) {}
            }
        }

        DownloadBandwidthCoordinator.unregisterStream(downloadId)
        checkServiceLiveness()
    }

    private fun postProgressNotification(
        item: DownloadItem,
        progress: Int,
        speed: Long,
        eta: Long
    ) {
        if (!PermissionHelper.hasNotificationPermission(applicationContext)) return
        if (!activeJobs.containsKey(item.id)) return

        try {
            val notif = NotificationUtils.buildProgressNotification(
                applicationContext,
                item,
                progress,
                speed,
                eta
            )
            notificationManager.notify(NotificationUtils.getNotificationId(item.id), notif)
        } catch (_: Exception) {}
    }

    private fun markDownloadSuccess(item: DownloadItem, actualFile: File) {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        DownloadBandwidthCoordinator.unregisterStream(item.id)
        activeJobs.remove(item.id)
        activeItems.remove(item.id)

        val finalSize = actualFile.length()
        val completedItem = item.copy(
            localFilePath = actualFile.absolutePath,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            downloadedBytes = finalSize,
            totalBytes = finalSize,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )

        helper.markCompleted(completedItem)

        // Dismiss progress notification and post completed notification
        try {
            notificationManager.cancel(NotificationUtils.getNotificationId(item.id))
            if (PermissionHelper.hasNotificationPermission(applicationContext)) {
                val notif = NotificationUtils.buildCompletedNotification(applicationContext, completedItem)
                notificationManager.notify(NotificationUtils.getNotificationId(item.id), notif)
            }
        } catch (_: Exception) {}

        checkServiceLiveness()
    }

    private fun handlePausedOnVpn(item: DownloadItem) {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        activeJobs.remove(item.id)
        activeItems.remove(item.id)
        helper.clearLiveProgress(item.id)

        val pausedItem = item.copy(
            status = DownloadStatus.PAUSED,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        helper.updateItemState(pausedItem)
        serviceScope.launch {
            preferences.addOrUpdateDownload(pausedItem)
        }

        try {
            val notif = NotificationUtils.buildPausedNotification(applicationContext, pausedItem)
            notificationManager.notify(NotificationUtils.getNotificationId(item.id), notif)
        } catch (_: Exception) {}

        AppToastManager.show("Descarga en pausa: VPN o Proxy detectado", ToastType.WARNING)
        checkServiceLiveness()
    }

    private fun handleDownloadError(item: DownloadItem, errorMessage: String) {
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        activeJobs.remove(item.id)
        activeItems.remove(item.id)
        helper.clearLiveProgress(item.id)

        val failedItem = item.copy(
            status = DownloadStatus.FAILED,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        helper.updateItemState(failedItem)
        serviceScope.launch {
            preferences.addOrUpdateDownload(failedItem)
        }

        try {
            notificationManager.cancel(NotificationUtils.getNotificationId(item.id))
            if (PermissionHelper.hasNotificationPermission(applicationContext)) {
                val notif = NotificationUtils.buildErrorNotification(applicationContext, failedItem, errorMessage)
                notificationManager.notify(NotificationUtils.getNotificationId(item.id), notif)
            }
        } catch (_: Exception) {}

        checkServiceLiveness()
    }

    private fun updateForegroundSummary() {
        try {
            val summaryNotif = NotificationUtils.buildSummaryNotification(applicationContext)
            notificationManager.notify(FOREGROUND_SERVICE_NOTIFICATION_ID, summaryNotif)
        } catch (_: Exception) {}
    }

    private fun checkServiceLiveness() {
        if (activeJobs.isEmpty()) {
            serviceScope.launch {
                delay(1200L)
                if (activeJobs.isEmpty()) {
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {}
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
