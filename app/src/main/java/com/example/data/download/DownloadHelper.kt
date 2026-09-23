package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.PeliculaPreferences
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.data.model.formatByteSize
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.utils.PermissionHelper
import com.example.utils.VpnProxyDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class VpnDetectedException(message: String) : Exception(message)

class DownloadHelper(
    private val context: Context,
    private val preferences: PeliculaPreferences,
    private val scope: CoroutineScope
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private val manuallyPausedIds = ConcurrentHashMap.newKeySet<String>()
    private val manuallyCanceledIds = ConcurrentHashMap.newKeySet<String>()
    private val demotedIds = ConcurrentHashMap.newKeySet<String>()
    private val lastNotificationUpdate = ConcurrentHashMap<String, Long>()
    private val totalBandwidthBytesPerSec = AtomicLong(4 * 1024 * 1024L)
    @Volatile
    private var lastSummaryPostTime = 0L
    @Volatile
    private var lastSummaryActiveCount = -1

    private class ActiveTaskInfo(
        val item: DownloadItem,
        @Volatile var downloadedBytes: Long,
        @Volatile var totalBytes: Long,
        val bytesInInterval: AtomicLong = AtomicLong(0L),
        @Volatile var lastCalculatedSpeed: Long = 0L,
        @Volatile var lastCalculatedEta: Long = 0L
    )

    private val activeTrackers = ConcurrentHashMap<String, ActiveTaskInfo>()
    @Volatile
    private var progressTickerJob: Job? = null

    @Synchronized
    private fun ensureProgressTickerRunning() {
        if (progressTickerJob?.isActive == true) return
        progressTickerJob = scope.launch(Dispatchers.IO) {
            val recentSpeedSamples = ArrayDeque<Long>(4)
            var smoothedTotalSpeedBps = 0L

            while (isActive && activeTrackers.isNotEmpty()) {
                val tickStartTime = System.currentTimeMillis()
                delay(1500L)
                val elapsed = (System.currentTimeMillis() - tickStartTime).coerceAtLeast(1L)

                if (activeTrackers.isEmpty()) break

                val currentTrackers = activeTrackers.values.toList()
                if (currentTrackers.isEmpty()) break

                // Sum of bytes read across ALL active downloads in this exact 700ms tick
                var totalBytesInTick = 0L
                for (tracker in currentTrackers) {
                    val bytes = tracker.bytesInInterval.getAndSet(0L)
                    totalBytesInTick += bytes
                }

                // Global aggregate instantaneous speed
                val instantTotalSpeed = (totalBytesInTick * 1000L) / elapsed
                if (instantTotalSpeed > 0) {
                    if (recentSpeedSamples.size >= 4) {
                        recentSpeedSamples.removeFirst()
                    }
                    recentSpeedSamples.addLast(instantTotalSpeed)
                }

                val sampleAvg = if (recentSpeedSamples.isNotEmpty()) recentSpeedSamples.average().toLong() else instantTotalSpeed
                smoothedTotalSpeedBps = when {
                    smoothedTotalSpeedBps <= 0L -> sampleAvg
                    sampleAvg > 0 -> ((smoothedTotalSpeedBps * 0.35) + (sampleAvg * 0.65)).toLong()
                    else -> (smoothedTotalSpeedBps * 0.80).toLong()
                }

                val activeCount = currentTrackers.size.coerceAtLeast(1)
                // Split speed equally among all active downloads
                val calculatedSpeedPerTask = if (smoothedTotalSpeedBps > 0) {
                    smoothedTotalSpeedBps / activeCount
                } else if (instantTotalSpeed > 0) {
                    instantTotalSpeed / activeCount
                } else {
                    0L
                }

                totalBandwidthBytesPerSec.set(smoothedTotalSpeedBps.coerceAtLeast(512 * 1024L))

                val updatedList = mutableListOf<DownloadItem>()
                for (tracker in currentTrackers) {
                    if (manuallyPausedIds.contains(tracker.item.id) || !activeTrackers.containsKey(tracker.item.id)) {
                        continue
                    }
                    val currentDownloaded = tracker.downloadedBytes
                    val currentTotal = tracker.totalBytes
                    val remainingBytes = (currentTotal - currentDownloaded).coerceAtLeast(0L)
                    val taskSpeed = calculatedSpeedPerTask.coerceAtLeast(if (totalBytesInTick > 0) 1024L else 0L)
                    val eta = if (taskSpeed > 2048 && remainingBytes > 0) {
                        remainingBytes / taskSpeed
                    } else {
                        0L
                    }
                    val progress = if (currentTotal > 0) {
                        ((currentDownloaded * 100) / currentTotal).toInt().coerceIn(0, 99)
                    } else {
                        0
                    }

                    tracker.lastCalculatedSpeed = taskSpeed
                    tracker.lastCalculatedEta = eta

                    val updated = tracker.item.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = progress,
                        downloadedBytes = currentDownloaded,
                        totalBytes = currentTotal,
                        speedBytesPerSec = taskSpeed,
                        etaSeconds = eta
                    )
                    updatedList.add(updated)
                    updateProgressNotification(updated)
                }

                // Update grouped master summary notification
                updateGroupSummaryNotification(currentTrackers, smoothedTotalSpeedBps)

                if (updatedList.isNotEmpty() && isActive) {
                    preferences.updateMultipleDownloads(updatedList)
                }
            }

            if (activeTrackers.isEmpty()) {
                updateGroupSummaryNotification(emptyList(), 0L)
            }
        }
    }

    companion object {
        const val CHANNEL_PROGRESS_ID = "downloads_progress_channel_v4"
        const val CHANNEL_PROGRESS_NAME = "Progreso de descargas"
        const val CHANNEL_ALERTS_ID = "downloads_alerts_channel_v4"
        const val CHANNEL_ALERTS_NAME = "Avisos de descargas finalizadas"

        const val ACTION_PAUSE_DOWNLOAD = "com.downloadfree.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.downloadfree.ACTION_RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.downloadfree.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        const val GROUP_KEY_DOWNLOADS = "com.downloadfree.DOWNLOADS_GROUP"
        const val SUMMARY_NOTIFICATION_ID = 9001

        @Volatile
        private var instance: DownloadHelper? = null

        fun getActiveInstance(context: Context): DownloadHelper {
            return instance ?: synchronized(this) {
                instance ?: DownloadHelper(
                    context.applicationContext,
                    PeliculaPreferences(context.applicationContext),
                    CoroutineScope(Dispatchers.IO + SupervisorJob())
                ).also { instance = it }
            }
        }
    }

    init {
        instance = this
        createNotificationChannels()
        scope.launch(Dispatchers.IO) {
            preferences.maxConcurrentDownloads.collect {
                checkAndStartNextPending()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para progreso continuo (sin sonido repetitivo ni vibración)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                CHANNEL_PROGRESS_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra la velocidad, tiempo estimado y barra de porcentaje"
                setShowBadge(false)
                enableVibration(false)
            }

            // Canal para avisos importantes y finalización (con alerta visual y vibración/sonido)
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                CHANNEL_ALERTS_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de películas listas para reproducir o errores de red"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    private fun getContentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "descargas")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun getPausePendingIntent(itemId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_PAUSE_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, (itemId + "_pause").hashCode(), intent, flags)
    }

    private fun getResumePendingIntent(itemId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_RESUME_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, (itemId + "_resume").hashCode(), intent, flags)
    }

    private fun getCancelPendingIntent(itemId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, itemId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, (itemId + "_cancel").hashCode(), intent, flags)
    }

    private fun getNotificationId(id: String): Int {
        return id.hashCode()
    }

    fun pauseDownloadById(id: String) {
        scope.launch(Dispatchers.IO) {
            val list = preferences.downloads.first()
            val item = list.find { it.id == id } ?: return@launch
            pauseDownload(item)
        }
    }

    fun resumeDownloadById(id: String) {
        scope.launch(Dispatchers.IO) {
            val list = preferences.downloads.first()
            val item = list.find { it.id == id } ?: return@launch
            resumeDownload(item)
        }
    }

    fun cancelDownloadById(id: String) {
        scope.launch(Dispatchers.IO) {
            val list = preferences.downloads.first()
            val item = list.find { it.id == id } ?: return@launch
            cancelDownload(item)
        }
    }

    fun startDownload(pelicula: Pelicula) {
        val videoUrl = pelicula.safeVideoUrl
        if (videoUrl.isEmpty()) {
            AppToastManager.show("Enlace multimedia no válido", ToastType.ERROR)
            return
        }

        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Las transferencias no están permitidas con VPN o Proxy activo", ToastType.WARNING)
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val extraTag = if (pelicula.isVideo) pelicula.youtuberName else pelicula.safeYear
                val displayTitleWithTag = if (extraTag.isNotBlank() && !pelicula.safeTitle.contains("($extraTag)")) {
                    "${pelicula.safeTitle} ($extraTag)"
                } else {
                    pelicula.safeTitle
                }
                val cleanForFile = displayTitleWithTag
                    .replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim(' ', '.')
                    .ifBlank { "Video" }
                val fileName = "$cleanForFile.mp4"
                val savedFolderPath = try {
                    preferences.downloadFolderPath.first()
                } catch (_: Exception) {
                    ""
                }
                val targetDir = if (savedFolderPath.isNotBlank()) {
                    val customDir = File(savedFolderPath)
                    if (customDir.exists() || customDir.mkdirs()) {
                        customDir
                    } else {
                        val fallback = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Download Free")
                        if (!fallback.exists()) fallback.mkdirs()
                        fallback
                    }
                } else {
                    val publicDir = try {
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Download Free")
                    } catch (_: Exception) {
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    }
                    if (!publicDir.exists()) {
                        publicDir.mkdirs()
                    }
                    publicDir
                }
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val destFile = File(targetDir, fileName)

                val currentList = preferences.downloads.first()
                val existing = currentList.find { it.id == pelicula.id }
                if (existing != null) {
                    when (existing.status) {
                        DownloadStatus.COMPLETED -> {
                            val itemDesc = "Este título ya se encuentra en tu biblioteca de Descargas"
                            AppToastManager.show(itemDesc, ToastType.INFO)
                            return@launch
                        }
                        DownloadStatus.DOWNLOADING -> {
                            val itemDesc = "Este título ya se encuentra en descarga activa"
                            AppToastManager.show(itemDesc, ToastType.INFO)
                            return@launch
                        }
                        DownloadStatus.PENDING -> {
                            val itemDesc = "Este título ya está programado en la cola de descarga"
                            AppToastManager.show(itemDesc, ToastType.INFO)
                            return@launch
                        }
                        DownloadStatus.PAUSED -> {
                            AppToastManager.show("Reanudando descarga...", ToastType.DOWNLOAD)
                            resumeDownload(existing)
                            return@launch
                        }
                        DownloadStatus.FAILED,
                        DownloadStatus.CANCELLED -> {
                            // Allowed to retry
                        }
                    }
                }

                val maxLimit = preferences.maxConcurrentDownloads.first()
                val activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING }

                if (activeCount >= maxLimit) {
                    val pendingItem = DownloadItem(
                        id = pelicula.id,
                        title = displayTitleWithTag,
                        originalVideoUrl = videoUrl,
                        coverUrl = pelicula.safeCoverUrl,
                        year = extraTag,
                        type = pelicula.tp ?: "pl",
                        localFilePath = destFile.absolutePath,
                        status = DownloadStatus.PENDING,
                        progress = 0
                    )
                    preferences.addOrUpdateDownload(pendingItem)
                    AppToastManager.show(
                        "En cola: límite de $maxLimit descargas simultáneas",
                        ToastType.INFO
                    )
                } else {
                    val downloadItem = DownloadItem(
                        id = pelicula.id,
                        title = displayTitleWithTag,
                        originalVideoUrl = videoUrl,
                        coverUrl = pelicula.safeCoverUrl,
                        year = extraTag,
                        type = pelicula.tp ?: "pl",
                        localFilePath = destFile.absolutePath,
                        status = DownloadStatus.DOWNLOADING,
                        progress = 0
                    )
                    preferences.addOrUpdateDownload(downloadItem)
                    AppToastManager.show("Iniciando descarga...", ToastType.DOWNLOAD)
                    launchDownloadJob(downloadItem, destFile)
                }
            } catch (e: Exception) {
                // Keep error feedback safe
            }
        }
    }

    fun buildPlaceholderSummaryNotification(): android.app.Notification {
        val active = activeTrackers.values.firstOrNull()
        if (active != null) {
            val item = active.item
            val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
                .setContentTitle(displayTitle)
                .setContentText("Iniciando descarga...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(0xFF0284C7.toInt())
                .setProgress(100, item.progress, true)
                .setContentIntent(getContentPendingIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setContentTitle("Descargando")
            .setContentText("Preparando descarga...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(0xFF0284C7.toInt())
            .setContentIntent(getContentPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateGroupSummaryNotification(
        activeTrackersList: List<ActiveTaskInfo>,
        totalSpeed: Long
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        if (activeTrackersList.isEmpty()) {
            DownloadForegroundService.stopService(context)
            lastSummaryActiveCount = 0
            return
        }

        if (activeTrackersList.size < 2) {
            lastSummaryActiveCount = activeTrackersList.size
            // When only 1 active download, it handles SUMMARY_NOTIFICATION_ID directly
            // Do NOT cancel SUMMARY_NOTIFICATION_ID as it is attached to the foreground service!
            return
        }

        val now = System.currentTimeMillis()
        val count = activeTrackersList.size

        // Throttle group summary updates so Android SystemUI doesn't collapse the expanded notification shade
        if (count == lastSummaryActiveCount && (now - lastSummaryPostTime) < 3500L) {
            return
        }

        try {
            lastSummaryActiveCount = count
            lastSummaryPostTime = now

            val formattedSpeed = formatByteSize(totalSpeed) + "/s"
            val title = "$count descargas en curso"
            val text = "Velocidad total: $formattedSpeed"

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText(formattedSpeed)

            for (tracker in activeTrackersList) {
                val speed = formatByteSize(tracker.lastCalculatedSpeed) + "/s"
                val displayTitle = if (tracker.item.year.isNotBlank() && !tracker.item.title.contains("(${tracker.item.year})")) {
                    "${tracker.item.title} (${tracker.item.year})"
                } else {
                    tracker.item.title
                }
                inboxStyle.addLine("$displayTitle (${tracker.item.progress}% • $speed)")
            }

            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(0xFF0284C7.toInt())
                .setStyle(inboxStyle)
                .setGroup(GROUP_KEY_DOWNLOADS)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setContentIntent(getContentPendingIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        } catch (_: Exception) {}
    }

    fun pauseDownload(item: DownloadItem) {
        scope.launch(Dispatchers.IO) {
            try {
                manuallyPausedIds.add(item.id)
                demotedIds.remove(item.id)
                activeTrackers.remove(item.id)
                activeCalls.remove(item.id)?.cancel()
                activeJobs.remove(item.id)?.cancel()

                val file = File(item.localFilePath)
                val currentDownloaded = if (file.exists()) file.length() else item.downloadedBytes
                val total = if (item.totalBytes > 0) item.totalBytes else currentDownloaded
                val progress = if (total > 0) ((currentDownloaded * 100) / total).toInt().coerceIn(0, 99) else item.progress

                val pausedItem = item.copy(
                    status = DownloadStatus.PAUSED,
                    downloadedBytes = currentDownloaded,
                    totalBytes = total,
                    progress = progress,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
                preferences.addOrUpdateDownload(pausedItem)
                notificationManager.cancel(getNotificationId(item.id))

                if (activeTrackers.isEmpty()) {
                    DownloadForegroundService.stopService(context)
                    notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
                } else {
                    updateGroupSummaryNotification(activeTrackers.values.toList(), totalBandwidthBytesPerSec.get())
                }
                showPausedNotification(pausedItem)
                checkAndStartNextPending()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun resumeDownload(item: DownloadItem) {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Desactiva la VPN o Proxy para reanudar la descarga", ToastType.WARNING)
            return
        }
        manuallyPausedIds.remove(item.id)
        manuallyCanceledIds.remove(item.id)
        demotedIds.remove(item.id)
        notificationManager.cancel(getNotificationId(item.id))
        scope.launch(Dispatchers.IO) {
            try {
                val currentList = preferences.downloads.first()
                val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                val activeCount = currentList.count {
                    it.status == DownloadStatus.DOWNLOADING &&
                    !manuallyCanceledIds.contains(it.id) &&
                    !manuallyPausedIds.contains(it.id)
                }
                val file = File(item.localFilePath)

                if (activeCount >= maxLimit) {
                    val pendingItem = item.copy(
                        status = DownloadStatus.PENDING,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(pendingItem)
                } else {
                    val resumingItem = item.copy(status = DownloadStatus.DOWNLOADING)
                    preferences.addOrUpdateDownload(resumingItem)
                    launchDownloadJob(resumingItem, file)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        scope.launch(Dispatchers.IO) {
            try {
                manuallyCanceledIds.add(item.id)
                manuallyPausedIds.remove(item.id)
                demotedIds.remove(item.id)
                activeTrackers.remove(item.id)
                activeCalls.remove(item.id)?.cancel()
                activeJobs.remove(item.id)?.cancel()
                val file = File(item.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                notificationManager.cancel(getNotificationId(item.id))
                preferences.removeDownload(item.id)
                if (activeTrackers.isEmpty()) {
                    DownloadForegroundService.stopService(context)
                    notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
                } else {
                    updateGroupSummaryNotification(activeTrackers.values.toList(), totalBandwidthBytesPerSec.get())
                }
                checkAndStartNextPending()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun deleteMultipleDownloads(items: List<DownloadItem>) {
        scope.launch(Dispatchers.IO) {
            try {
                val ids = items.map { it.id }.toSet()
                items.forEach { item ->
                    try {
                        manuallyCanceledIds.add(item.id)
                        activeTrackers.remove(item.id)
                        activeCalls.remove(item.id)?.cancel()
                        activeJobs.remove(item.id)?.cancel()
                        val file = File(item.localFilePath)
                        if (file.exists()) {
                            file.delete()
                        }
                        notificationManager.cancel(getNotificationId(item.id))
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                preferences.removeDownloads(ids)
                updateGroupSummaryNotification(activeTrackers.values.toList(), totalBandwidthBytesPerSec.get())
                checkAndStartNextPending()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun forceStartPending(item: DownloadItem) {
        resumeDownload(item)
    }

    fun pauseAllDownloads() {
        scope.launch(Dispatchers.IO) {
            try {
                val currentList = preferences.downloads.first()
                val downloadingOrPending = currentList.filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }
                downloadingOrPending.forEach { item ->
                    manuallyPausedIds.add(item.id)
                    demotedIds.remove(item.id)
                    activeTrackers.remove(item.id)
                    activeCalls.remove(item.id)?.cancel()
                    activeJobs.remove(item.id)?.cancel()
                    val file = File(item.localFilePath)
                    val currentDownloaded = if (file.exists()) file.length() else item.downloadedBytes
                    val total = if (item.totalBytes > 0) item.totalBytes else currentDownloaded
                    val progress = if (total > 0) ((currentDownloaded * 100) / total).toInt().coerceIn(0, 99) else item.progress
                    val pausedItem = item.copy(
                        status = DownloadStatus.PAUSED,
                        downloadedBytes = currentDownloaded,
                        totalBytes = total,
                        progress = progress,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(pausedItem)
                    notificationManager.cancel(getNotificationId(item.id))
                    showPausedNotification(pausedItem)
                }
                if (activeTrackers.isEmpty()) {
                    DownloadForegroundService.stopService(context)
                    notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
                } else {
                    updateGroupSummaryNotification(activeTrackers.values.toList(), totalBandwidthBytesPerSec.get())
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun resumeAllDownloads() {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Desactiva la conexión VPN o Proxy para reanudar descargas", ToastType.WARNING)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val currentList = preferences.downloads.first()
                val pausedOrPending = currentList.filter {
                    it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.FAILED
                }
                val maxLimit = preferences.maxConcurrentDownloads.first()
                var activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING }

                pausedOrPending.forEach { item ->
                    manuallyPausedIds.remove(item.id)
                    manuallyCanceledIds.remove(item.id)
                    demotedIds.remove(item.id)
                    val file = File(item.localFilePath)
                    if (activeCount < maxLimit) {
                        activeCount++
                        val resumingItem = item.copy(status = DownloadStatus.DOWNLOADING)
                        preferences.addOrUpdateDownload(resumingItem)
                        launchDownloadJob(resumingItem, file)
                    } else {
                        val pendingItem = item.copy(
                            status = DownloadStatus.PENDING,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(pendingItem)
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun cancelAllDownloads() {
        scope.launch(Dispatchers.IO) {
            try {
                val currentList = preferences.downloads.first()
                val activeItems = currentList.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.PAUSED ||
                    it.status == DownloadStatus.PENDING ||
                    it.status == DownloadStatus.FAILED
                }
                activeItems.forEach { item ->
                    activeTrackers.remove(item.id)
                    activeJobs[item.id]?.cancel()
                    activeJobs.remove(item.id)
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    notificationManager.cancel(getNotificationId(item.id))
                    preferences.removeDownload(item.id)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun launchDownloadJob(item: DownloadItem, destFile: File) {
        manuallyPausedIds.remove(item.id)
        manuallyCanceledIds.remove(item.id)
        demotedIds.remove(item.id)
        activeTrackers.remove(item.id)
        activeJobs[item.id]?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            if (VpnProxyDetector.isVpnOrProxyActive(context)) {
                val paused = item.copy(
                    status = DownloadStatus.PAUSED,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
                preferences.addOrUpdateDownload(paused)
                showPausedNotification(paused)
                AppToastManager.show("Descarga detenida: VPN o Proxy detectado", ToastType.WARNING)
                return@launch
            }

            DownloadForegroundService.startService(context)

            var retryCount = 0
            val maxRetries = 3
            var completedSuccessfully = false

            while (isActive && retryCount < maxRetries && !completedSuccessfully) {
                if (manuallyPausedIds.contains(item.id) || manuallyCanceledIds.contains(item.id) || demotedIds.contains(item.id)) break
                if (VpnProxyDetector.isVpnOrProxyActive(context)) {
                    val paused = item.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(paused)
                    notificationManager.cancel(getNotificationId(item.id))
                    showPausedNotification(paused)
                    AppToastManager.show("Descarga detenida: VPN o Proxy detectado", ToastType.WARNING)
                    break
                }

                var input: InputStream? = null
                var raf: RandomAccessFile? = null
                var downloaded = if (destFile.exists()) destFile.length() else 0L

                try {
                    val requestBuilder = Request.Builder().url(item.originalVideoUrl)
                    if (downloaded > 0) {
                        requestBuilder.addHeader("Range", "bytes=$downloaded-")
                    }

                    val call = okHttpClient.newCall(requestBuilder.build())
                    activeCalls[item.id] = call
                    val response = call.execute()

                    if (!response.isSuccessful && response.code != 206) {
                        if (response.code == 416) {
                            downloaded = 0L
                            destFile.delete()
                        } else {
                            throw Exception("HTTP ${response.code}: ${response.message}")
                        }
                    }

                    val body = response.body ?: throw Exception("Cuerpo de respuesta vacío")
                    val contentLength = body.contentLength()
                    val totalBytes = if (response.code == 206) downloaded + contentLength else if (contentLength > 0) contentLength else item.totalBytes

                    raf = RandomAccessFile(destFile, "rw")
                    if (response.code == 206) {
                        raf.seek(downloaded)
                    } else {
                        raf.setLength(0)
                        downloaded = 0L
                    }

                    input = body.byteStream()
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead = 0

                    val tracker = ActiveTaskInfo(
                        item = item,
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes
                    )
                    activeTrackers[item.id] = tracker
                    ensureProgressTickerRunning()

                    while (isActive && input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        tracker.downloadedBytes = downloaded
                        tracker.bytesInInterval.addAndGet(bytesRead.toLong())
                        tracker.totalBytes = totalBytes
                    }

                    if (isActive && activeJobs.containsKey(item.id)) {
                        activeTrackers.remove(item.id)
                        val finalSize = destFile.length()
                        val completed = item.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 100,
                            downloadedBytes = finalSize,
                            totalBytes = finalSize,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(completed)
                        notificationManager.cancel(getNotificationId(item.id))
                        showCompletedNotification(completed)
                        updateGroupSummaryNotification(activeTrackers.values.toList(), totalBandwidthBytesPerSec.get())
                        completedSuccessfully = true
                        checkAndStartNextPending()
                        break
                    }
                } catch (e: VpnDetectedException) {
                    activeTrackers.remove(item.id)
                    val paused = item.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(paused)
                    notificationManager.cancel(getNotificationId(item.id))
                    showPausedNotification(paused)
                    AppToastManager.show("Descarga pausada: se detectó uso de VPN o Proxy", ToastType.WARNING)
                    pauseAllDownloads()
                    break
                } catch (e: CancellationException) {
                    activeTrackers.remove(item.id)
                    if (manuallyPausedIds.contains(item.id)) {
                        val finalDownloaded = if (destFile.exists()) destFile.length() else item.downloadedBytes
                        val total = if (item.totalBytes > 0) item.totalBytes else finalDownloaded
                        val progress = if (total > 0) ((finalDownloaded * 100) / total).toInt().coerceIn(0, 99) else item.progress
                        val paused = item.copy(
                            status = DownloadStatus.PAUSED,
                            downloadedBytes = finalDownloaded,
                            totalBytes = total,
                            progress = progress,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(paused)
                        notificationManager.cancel(getNotificationId(item.id))
                        showPausedNotification(paused)
                    }
                    break
                } catch (e: Exception) {
                    activeTrackers.remove(item.id)
                    if (demotedIds.remove(item.id)) {
                        break
                    }
                    if (manuallyPausedIds.contains(item.id)) {
                        val finalDownloaded = if (destFile.exists()) destFile.length() else item.downloadedBytes
                        val total = if (item.totalBytes > 0) item.totalBytes else finalDownloaded
                        val progress = if (total > 0) ((finalDownloaded * 100) / total).toInt().coerceIn(0, 99) else item.progress
                        val paused = item.copy(
                            status = DownloadStatus.PAUSED,
                            downloadedBytes = finalDownloaded,
                            totalBytes = total,
                            progress = progress,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(paused)
                        notificationManager.cancel(getNotificationId(item.id))
                        showPausedNotification(paused)
                        break
                    } else if (manuallyCanceledIds.remove(item.id)) {
                        break
                    } else {
                        retryCount++
                        if (retryCount < maxRetries && isActive) {
                            delay(1500L)
                        } else {
                            val failed = item.copy(
                                status = DownloadStatus.FAILED,
                                speedBytesPerSec = 0L,
                                etaSeconds = 0L
                            )
                            preferences.addOrUpdateDownload(failed)
                            notificationManager.cancel(getNotificationId(item.id))
                            showFailedNotification(failed, e.localizedMessage ?: "Error de red")
                            checkAndStartNextPending()
                            break
                        }
                    }
                } finally {
                    try { input?.close() } catch (_: Exception) {}
                    try { raf?.close() } catch (_: Exception) {}
                }
            }
            if (activeTrackers.isEmpty()) {
                DownloadForegroundService.stopService(context)
            }
        }
        activeJobs[item.id] = job
    }

    private fun updateProgressNotification(item: DownloadItem) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        val now = System.currentTimeMillis()
        val lastUpdate = lastNotificationUpdate[item.id] ?: 0L
        if (now - lastUpdate < 1400L) return
        lastNotificationUpdate[item.id] = now

        try {
            val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            val isSingle = activeTrackers.size <= 1

            val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
                .setContentTitle(displayTitle)
                .setContentText("${item.formattedSpeed} • ${item.progress}% • ${item.formattedEta}")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(0xFF0284C7.toInt())
                .setProgress(100, item.progress, item.totalBytes <= 0)
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
                .setOnlyAlertOnce(true)

            if (isSingle) {
                // Update foreground notification directly - prevents duplicate notification and eliminate flicker
                notificationManager.notify(SUMMARY_NOTIFICATION_ID, builder.build())
                notificationManager.cancel(getNotificationId(item.id))
            } else {
                builder.setGroup(GROUP_KEY_DOWNLOADS)
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                notificationManager.notify(getNotificationId(item.id), builder.build())
            }
        } catch (e: Exception) {
            // Notification safety
        }
    }

    private fun showCompletedNotification(item: DownloadItem) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        try {
            val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setContentTitle("Descarga completada")
                .setContentText(displayTitle)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setColor(0xFF10B981.toInt())
                .setContentIntent(getContentPendingIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            notificationManager.notify(getNotificationId(item.id), notification)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun showPausedNotification(item: DownloadItem) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        try {
            val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
                .setContentTitle("Descarga pausada")
                .setContentText("$displayTitle (${item.progress}%)")
                .setSmallIcon(R.drawable.ic_notification_pause)
                .setColor(0xFFF59E0B.toInt())
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
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .build()
            notificationManager.notify(getNotificationId(item.id), notification)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun sanitizeErrorMessage(rawError: String?): String {
        if (rawError.isNullOrBlank()) return "Error de conexión con el servidor"
        var clean = rawError
        clean = clean.replace("moodle.instec.cu", "servidor", ignoreCase = true)
        clean = clean.replace(Regex("https?://[^\\s/$.?#].[^\\s]*", RegexOption.IGNORE_CASE), "servidor")
        clean = clean.replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "servidor")
        clean = clean.replace(Regex("Unable to resolve host.*", RegexOption.IGNORE_CASE), "No se pudo conectar con el servidor")
        clean = clean.replace(Regex("Failed to connect to.*", RegexOption.IGNORE_CASE), "Fallo de conexión con el servidor")
        clean = clean.replace(Regex("HTTP 404.*", RegexOption.IGNORE_CASE), "Archivo no encontrado en el servidor")
        clean = clean.replace(Regex("HTTP 403.*", RegexOption.IGNORE_CASE), "Acceso denegado por el servidor")
        clean = clean.replace(Regex("HTTP 5\\d{2}.*", RegexOption.IGNORE_CASE), "El servidor no responde")
        if (clean.length > 70) {
            clean = clean.take(70) + "..."
        }
        return clean
    }

    private fun showFailedNotification(item: DownloadItem, error: String) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        try {
            val safeError = sanitizeErrorMessage(error)
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setContentTitle("Error al descargar")
                .setContentText("${item.title}: $safeError")
                .setSmallIcon(R.drawable.ic_notification_error)
                .setColor(0xFFEF4444.toInt())
                .setContentIntent(getContentPendingIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            notificationManager.notify(getNotificationId(item.id), notification)
        } catch (e: Exception) {
            // ignore
        }
    }

    private suspend fun checkAndStartNextPending() {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) return
        try {
            val list = preferences.downloads.first()
            val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
            val downloadingList = list.filter {
                it.status == DownloadStatus.DOWNLOADING &&
                !manuallyCanceledIds.contains(it.id) &&
                !manuallyPausedIds.contains(it.id) &&
                !demotedIds.contains(it.id)
            }
            val activeCount = downloadingList.size

            if (activeCount > maxLimit) {
                // Si el usuario redujo el límite en Ajustes mientras había más descargas activas,
                // pausamos y pasamos a cola (PENDING) las descargas activas excedentes más recientes
                val excessCount = activeCount - maxLimit
                val itemsToDemote = downloadingList.takeLast(excessCount)
                for (excessItem in itemsToDemote) {
                    demotedIds.add(excessItem.id)
                    activeTrackers.remove(excessItem.id)
                    activeCalls.remove(excessItem.id)?.cancel()
                    activeJobs.remove(excessItem.id)?.cancel()
                    notificationManager.cancel(getNotificationId(excessItem.id))
                    val pendingItem = excessItem.copy(
                        status = DownloadStatus.PENDING,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(pendingItem)
                }
                updateGroupSummaryNotification(activeTrackers.values.toList(), totalBandwidthBytesPerSec.get())
            } else if (activeCount < maxLimit) {
                val availableSlots = maxLimit - activeCount
                val pendingList = list.filter {
                    it.status == DownloadStatus.PENDING &&
                    !manuallyCanceledIds.contains(it.id) &&
                    !manuallyPausedIds.contains(it.id)
                }.take(availableSlots)
                for (nextPending in pendingList) {
                    manuallyPausedIds.remove(nextPending.id)
                    manuallyCanceledIds.remove(nextPending.id)
                    demotedIds.remove(nextPending.id)
                    val file = File(nextPending.localFilePath)
                    val toStart = nextPending.copy(status = DownloadStatus.DOWNLOADING)
                    preferences.addOrUpdateDownload(toStart)
                    launchDownloadJob(toStart, file)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
