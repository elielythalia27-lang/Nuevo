package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.data.local.PeliculaPreferences
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.utils.NetworkUtils
import com.example.utils.PermissionHelper
import com.example.utils.VpnProxyDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class LiveProgressUpdate(
    val downloadId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Int,
    val speedBytesPerSec: Long,
    val etaSeconds: Long
)

class DownloadHelper(
    private val context: Context,
    private val preferences: PeliculaPreferences,
    private val scope: CoroutineScope
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val workManager = WorkManager.getInstance(context)
    private val itemMutexes = ConcurrentHashMap<String, Mutex>()
    private val liveProgressMap = ConcurrentHashMap<String, LiveProgressUpdate>()

    // Custom StateFlow for smooth 700ms UI progress rendering
    private val _liveDownloadsState = MutableStateFlow<List<DownloadItem>>(emptyList())
    val liveDownloadsState: StateFlow<List<DownloadItem>> = _liveDownloadsState.asStateFlow()

    private fun getMutexFor(id: String): Mutex {
        return itemMutexes.computeIfAbsent(id) { Mutex() }
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

        // Sync initial persisted list
        scope.launch(Dispatchers.IO) {
            preferences.downloads.collect { storedList ->
                val currentLive = liveProgressMap
                val merged = storedList.map { item ->
                    val live = currentLive[item.id]
                    if (live != null && item.status == DownloadStatus.DOWNLOADING) {
                        item.copy(
                            downloadedBytes = live.downloadedBytes,
                            totalBytes = if (live.totalBytes > 0) live.totalBytes else item.totalBytes,
                            progress = live.progress,
                            speedBytesPerSec = live.speedBytesPerSec,
                            etaSeconds = live.etaSeconds
                        )
                    } else {
                        item
                    }
                }
                _liveDownloadsState.value = merged
            }
        }

        // Strict 700ms Coroutine delay strategy for UI progress rendering
        scope.launch(Dispatchers.Default) {
            var lastDbPersistTime = 0L
            while (isActive) {
                delay(700L) // Enforce strict 700ms UI update cycle
                if (liveProgressMap.isNotEmpty()) {
                    val currentList = _liveDownloadsState.value
                    if (currentList.isNotEmpty()) {
                        var hasChanges = false
                        val updatedList = currentList.map { item ->
                            val live = liveProgressMap[item.id]
                            if (live != null && item.status == DownloadStatus.DOWNLOADING) {
                                hasChanges = true
                                item.copy(
                                    downloadedBytes = live.downloadedBytes,
                                    totalBytes = if (live.totalBytes > 0) live.totalBytes else item.totalBytes,
                                    progress = live.progress,
                                    speedBytesPerSec = live.speedBytesPerSec,
                                    etaSeconds = live.etaSeconds
                                )
                            } else {
                                item
                            }
                        }

                        if (hasChanges) {
                            _liveDownloadsState.value = updatedList
                            val now = System.currentTimeMillis()
                            // Throttle disk I/O to avoid redundant writes while UI renders smoothly every 700ms
                            if (now - lastDbPersistTime >= 1400L) {
                                lastDbPersistTime = now
                                preferences.updateMultipleDownloads(
                                    updatedList.filter { it.status == DownloadStatus.DOWNLOADING }
                                )
                            }
                        }
                    }
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            preferences.maxConcurrentDownloads.collect {
                checkAndStartNextPending()
            }
        }
    }

    fun reportProgress(
        downloadId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        progress: Int,
        speedBytesPerSec: Long,
        etaSeconds: Long
    ) {
        liveProgressMap[downloadId] = LiveProgressUpdate(
            downloadId = downloadId,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progress = progress,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds
        )
    }

    fun clearLiveProgress(downloadId: String) {
        liveProgressMap.remove(downloadId)
        DownloadBandwidthCoordinator.unregisterStream(downloadId)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                CHANNEL_PROGRESS_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra la velocidad, tiempo estimado y barra de porcentaje"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

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

    fun getWorkName(downloadId: String): String = "download_work_$downloadId"

    private fun getNotificationId(id: String): Int = id.hashCode()

    private fun resolveDestinationFile(item: DownloadItem, savedFolderPath: String): File {
        if (item.localFilePath.isNotBlank()) {
            val f = File(item.localFilePath)
            if (f.exists() || f.parentFile?.exists() == true) {
                return f
            }
        }
        val cleanTitle = item.title
            .replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.')
            .ifBlank { "Video" }
        val fileName = "$cleanTitle.mp4"
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
        return File(targetDir, fileName)
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

        val isWifiOnlySync = try { kotlinx.coroutines.runBlocking { preferences.wifiOnly.first() } } catch (_: Exception) { false }
        if (isWifiOnlySync && !NetworkUtils.isWifiOrEthernet(context)) {
            AppToastManager.show("Descarga bloqueada: 'Solo Wi-Fi' está activo y estás conectado a datos móviles", ToastType.ERROR)
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

                val savedFolderPath = try { preferences.downloadFolderPath.first() } catch (_: Exception) { "" }
                val dummyItem = DownloadItem(
                    id = pelicula.id,
                    title = displayTitleWithTag,
                    originalVideoUrl = videoUrl,
                    coverUrl = pelicula.safeCoverUrl,
                    year = extraTag,
                    type = pelicula.tp ?: "pl",
                    localFilePath = "",
                    status = DownloadStatus.PENDING,
                    progress = 0
                )
                val destFile = resolveDestinationFile(dummyItem, savedFolderPath)

                val currentList = preferences.downloads.first()
                val existing = currentList.find { it.id == pelicula.id }
                if (existing != null) {
                    when (existing.status) {
                        DownloadStatus.COMPLETED -> {
                            AppToastManager.show("Este título ya se encuentra en tu biblioteca de Descargas", ToastType.INFO)
                            return@launch
                        }
                        DownloadStatus.DOWNLOADING -> {
                            AppToastManager.show("Este título ya se encuentra en descarga activa", ToastType.INFO)
                            return@launch
                        }
                        DownloadStatus.PENDING -> {
                            AppToastManager.show("Este título ya está programado en la cola de descarga", ToastType.INFO)
                            return@launch
                        }
                        DownloadStatus.PAUSED -> {
                            AppToastManager.show("Reanudando descarga...", ToastType.DOWNLOAD)
                            resumeDownload(existing)
                            return@launch
                        }
                        DownloadStatus.FAILED,
                        DownloadStatus.CANCELLED -> {
                            // Retry
                        }
                    }
                }

                val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
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
                        "En cola: límite de $maxLimit descargas simultáneas alcanzado",
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
                    enqueueWorker(downloadItem, destFile)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun enqueueWorker(item: DownloadItem, destFile: File) {
        val isWifiOnly = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
        val networkType = if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_DOWNLOAD_ID, item.id)
            .putString(DownloadWorker.KEY_VIDEO_URL, item.originalVideoUrl)
            .putString(DownloadWorker.KEY_TITLE, item.title)
            .putString(DownloadWorker.KEY_COVER_URL, item.coverUrl)
            .putString(DownloadWorker.KEY_YEAR, item.year)
            .putString(DownloadWorker.KEY_TYPE, item.type)
            .putString(DownloadWorker.KEY_LOCAL_FILE_PATH, destFile.absolutePath)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            getWorkName(item.id),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
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

    fun pauseDownload(item: DownloadItem) {
        scope.launch(Dispatchers.IO) {
            getMutexFor(item.id).withLock {
                try {
                    workManager.cancelUniqueWork(getWorkName(item.id))
                    clearLiveProgress(item.id)
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
                    showPausedNotification(pausedItem)

                    // WorkManager cleans up foreground notifications asynchronously.
                    // Delayed re-post ensures paused notification remains firmly in the tray with resume/cancel actions.
                    delay(300L)
                    showPausedNotification(pausedItem)
                    delay(500L)
                    showPausedNotification(pausedItem)

                    checkAndStartNextPending()
                } catch (_: Exception) {}
            }
        }
    }

    fun resumeDownload(item: DownloadItem) {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Desactiva la VPN o Proxy para reanudar la descarga", ToastType.WARNING)
            return
        }

        scope.launch(Dispatchers.IO) {
            getMutexFor(item.id).withLock {
                try {
                    val currentList = preferences.downloads.first()
                    val currentItem = currentList.find { it.id == item.id } ?: item
                    val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                    val activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING && it.id != item.id }

                    val savedFolderPath = try { preferences.downloadFolderPath.first() } catch (_: Exception) { "" }
                    val targetFile = resolveDestinationFile(currentItem, savedFolderPath)

                    if (activeCount >= maxLimit) {
                        val pendingItem = currentItem.copy(
                            localFilePath = targetFile.absolutePath,
                            status = DownloadStatus.PENDING,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(pendingItem)
                        AppToastManager.show("En cola: máximo $maxLimit descargas activas", ToastType.INFO)
                    } else {
                        val resumingItem = currentItem.copy(
                            localFilePath = targetFile.absolutePath,
                            status = DownloadStatus.DOWNLOADING,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(resumingItem)
                        enqueueWorker(resumingItem, targetFile)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        scope.launch(Dispatchers.IO) {
            getMutexFor(item.id).withLock {
                try {
                    workManager.cancelUniqueWork(getWorkName(item.id))
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    notificationManager.cancel(getNotificationId(item.id))
                    preferences.removeDownload(item.id)
                    checkAndStartNextPending()
                } catch (_: Exception) {}
            }
        }
    }

    fun deleteMultipleDownloads(items: List<DownloadItem>) {
        scope.launch(Dispatchers.IO) {
            try {
                val ids = items.map { it.id }.toSet()
                items.forEach { item ->
                    try {
                        workManager.cancelUniqueWork(getWorkName(item.id))
                        val file = File(item.localFilePath)
                        if (file.exists()) {
                            file.delete()
                        }
                        notificationManager.cancel(getNotificationId(item.id))
                    } catch (_: Exception) {}
                }
                preferences.removeDownloads(ids)
                checkAndStartNextPending()
            } catch (_: Exception) {}
        }
    }

    fun forceStartPending(item: DownloadItem) {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Desactiva la VPN o Proxy para iniciar la descarga", ToastType.WARNING)
            return
        }
        scope.launch(Dispatchers.IO) {
            getMutexFor(item.id).withLock {
                try {
                    val currentList = preferences.downloads.first()
                    val currentItem = currentList.find { it.id == item.id } ?: item
                    val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                    val activeList = currentList.filter { it.status == DownloadStatus.DOWNLOADING && it.id != item.id }

                    if (activeList.size >= maxLimit) {
                        val itemToDemote = activeList.last()
                        workManager.cancelUniqueWork(getWorkName(itemToDemote.id))
                        notificationManager.cancel(getNotificationId(itemToDemote.id))
                        val demotedPending = itemToDemote.copy(
                            status = DownloadStatus.PENDING,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(demotedPending)
                    }

                    val savedFolderPath = try { preferences.downloadFolderPath.first() } catch (_: Exception) { "" }
                    val targetFile = resolveDestinationFile(currentItem, savedFolderPath)
                    val toStart = currentItem.copy(
                        localFilePath = targetFile.absolutePath,
                        status = DownloadStatus.DOWNLOADING,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    preferences.addOrUpdateDownload(toStart)
                    enqueueWorker(toStart, targetFile)
                } catch (_: Exception) {}
            }
        }
    }

    fun pauseAllDownloads() {
        scope.launch(Dispatchers.IO) {
            try {
                val currentList = preferences.downloads.first()
                val downloadingOrPending = currentList.filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }
                val pausedItems = mutableListOf<DownloadItem>()
                downloadingOrPending.forEach { item ->
                    workManager.cancelUniqueWork(getWorkName(item.id))
                    clearLiveProgress(item.id)
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
                    pausedItems.add(pausedItem)
                    showPausedNotification(pausedItem)
                }

                delay(300L)
                pausedItems.forEach { showPausedNotification(it) }
                delay(500L)
                pausedItems.forEach { showPausedNotification(it) }
            } catch (_: Exception) {}
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
                val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                var activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING }
                val savedFolderPath = try { preferences.downloadFolderPath.first() } catch (_: Exception) { "" }

                pausedOrPending.forEach { item ->
                    val targetFile = resolveDestinationFile(item, savedFolderPath)
                    if (activeCount < maxLimit) {
                        activeCount++
                        val resumingItem = item.copy(
                            localFilePath = targetFile.absolutePath,
                            status = DownloadStatus.DOWNLOADING
                        )
                        preferences.addOrUpdateDownload(resumingItem)
                        enqueueWorker(resumingItem, targetFile)
                    } else {
                        val pendingItem = item.copy(
                            localFilePath = targetFile.absolutePath,
                            status = DownloadStatus.PENDING,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        preferences.addOrUpdateDownload(pendingItem)
                    }
                }
            } catch (_: Exception) {}
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
                    workManager.cancelUniqueWork(getWorkName(item.id))
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    notificationManager.cancel(getNotificationId(item.id))
                    preferences.removeDownload(item.id)
                }
            } catch (_: Exception) {}
        }
    }

    fun notifyTaskFinished(downloadId: String) {
        scope.launch(Dispatchers.IO) {
            checkAndStartNextPending()
        }
    }

    private suspend fun checkAndStartNextPending() {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) return
        try {
            val list = preferences.downloads.first()
            val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
            val downloadingList = list.filter { it.status == DownloadStatus.DOWNLOADING }
            val activeCount = downloadingList.size

            if (activeCount < maxLimit) {
                val availableSlots = maxLimit - activeCount
                val pendingList = list.filter { it.status == DownloadStatus.PENDING }.take(availableSlots)
                val savedFolderPath = try { preferences.downloadFolderPath.first() } catch (_: Exception) { "" }

                for (nextPending in pendingList) {
                    val targetFile = resolveDestinationFile(nextPending, savedFolderPath)
                    val toStart = nextPending.copy(
                        localFilePath = targetFile.absolutePath,
                        status = DownloadStatus.DOWNLOADING
                    )
                    preferences.addOrUpdateDownload(toStart)
                    enqueueWorker(toStart, targetFile)
                }
            }
        } catch (_: Exception) {}
    }

    private fun showPausedNotification(item: DownloadItem) {
        if (!PermissionHelper.hasNotificationPermission(context)) return
        try {
            val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
                "${item.title} (${item.year})"
            } else {
                item.title
            }
            val subtitle = "${item.progress}% • Pausada"
            val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
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

    private fun getContentPendingIntent(): PendingIntent {
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
        return PendingIntent.getActivity(context, 1002, intent, flags)
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
}
