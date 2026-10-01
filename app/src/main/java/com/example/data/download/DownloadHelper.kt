package com.example.data.download

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import com.example.data.local.PeliculaPreferences
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.utils.NetworkUtils
import com.example.utils.NotificationUtils
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
import kotlinx.coroutines.flow.update
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

/**
 * DownloadHelper:
 * Reactive mediator and Single Source of Truth for the UI and Foreground Service.
 * Maintains zero-latency in-memory state flow for Jetpack Compose, persists to DataStore,
 * and delegates actual network execution to DownloadForegroundService.
 */
class DownloadHelper(
    private val context: Context,
    private val preferences: PeliculaPreferences,
    private val scope: CoroutineScope
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val queueMutex = Mutex()
    private val liveProgressMap = ConcurrentHashMap<String, LiveProgressUpdate>()

    // In-memory Single Source of Truth for 0ms latency UI updates
    private val _liveDownloadsState = MutableStateFlow<List<DownloadItem>>(emptyList())
    val liveDownloadsState: StateFlow<List<DownloadItem>> = _liveDownloadsState.asStateFlow()

    companion object {
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
        NotificationUtils.initNotificationChannels(context)

        // Load persisted downloads from DataStore on startup
        scope.launch(Dispatchers.IO) {
            val initial = preferences.downloads.first()
            _liveDownloadsState.value = initial

            preferences.downloads.collect { storedList ->
                _liveDownloadsState.update { currentMemList ->
                    if (currentMemList.isEmpty()) {
                        storedList
                    } else {
                        storedList.map { storedItem ->
                            val memItem = currentMemList.find { it.id == storedItem.id }
                            if (memItem != null) {
                                when (memItem.status) {
                                    DownloadStatus.PAUSED -> memItem
                                    DownloadStatus.DOWNLOADING -> {
                                        val live = liveProgressMap[storedItem.id]
                                        if (live != null) {
                                            memItem.copy(
                                                downloadedBytes = live.downloadedBytes,
                                                totalBytes = if (live.totalBytes > 0L) live.totalBytes else memItem.totalBytes,
                                                progress = live.progress,
                                                speedBytesPerSec = live.speedBytesPerSec,
                                                etaSeconds = live.etaSeconds
                                            )
                                        } else {
                                            storedItem
                                        }
                                    }
                                    else -> storedItem
                                }
                            } else {
                                storedItem
                            }
                        }
                    }
                }
            }
        }

        // Throttle UI flow collection and periodic persistence
        scope.launch(Dispatchers.Default) {
            var lastDbPersistTime = 0L
            while (isActive) {
                delay(800L)
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
                                    totalBytes = if (live.totalBytes > 0L) live.totalBytes else item.totalBytes,
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
                            if (now - lastDbPersistTime >= 1600L) {
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

        // Observe max concurrency settings changes
        scope.launch(Dispatchers.IO) {
            preferences.maxConcurrentDownloads.collect {
                checkAndStartNextPending()
            }
        }
    }

    fun getItem(id: String): DownloadItem? {
        return _liveDownloadsState.value.find { it.id == id }
    }

    fun isPaused(id: String): Boolean {
        return _liveDownloadsState.value.find { it.id == id }?.status == DownloadStatus.PAUSED
    }

    fun isCancelled(id: String): Boolean {
        return _liveDownloadsState.value.none { it.id == id }
    }

    fun updateItemState(item: DownloadItem) {
        _liveDownloadsState.update { list ->
            val exists = list.any { it.id == item.id }
            if (exists) {
                list.map { if (it.id == item.id) item else it }
            } else {
                list + item
            }
        }
    }

    fun removeItemFromState(id: String) {
        clearLiveProgress(id)
        _liveDownloadsState.update { list ->
            list.filterNot { it.id == id }
        }
    }

    fun markCompleted(completedItem: DownloadItem) {
        clearLiveProgress(completedItem.id)
        updateItemState(completedItem)
        scope.launch(Dispatchers.IO) {
            preferences.addOrUpdateDownload(completedItem)
            checkAndStartNextPending()
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

    fun startDownload(pelicula: Pelicula, chosenQualityUrl: String? = null) {
        val videoUrl = chosenQualityUrl ?: pelicula.safeVideoUrl
        if (videoUrl.isBlank()) {
            AppToastManager.show("URL de video no disponible", ToastType.ERROR)
            return
        }

        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Desactiva la VPN o Proxy para iniciar descargas", ToastType.WARNING)
            return
        }

        if (!NetworkUtils.isConnected(context)) {
            AppToastManager.show("Sin conexión a internet", ToastType.WARNING)
            return
        }

        scope.launch(Dispatchers.IO) {
            val isWifiOnlyPref = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
            if (isWifiOnlyPref && !NetworkUtils.isWifiOrEthernet(context)) {
                AppToastManager.show("Solo Wi-Fi activado. Conéctate a Wi-Fi para descargar.", ToastType.ERROR)
                return@launch
            }

            queueMutex.withLock {
                val currentList = _liveDownloadsState.value
                val existing = currentList.find { it.id == pelicula.id }
                if (existing != null && existing.status == DownloadStatus.COMPLETED) {
                    val f = File(existing.localFilePath)
                    if (f.exists() && f.length() > 0L) {
                        AppToastManager.show("Esta película ya fue descargada", ToastType.INFO)
                        return@launch
                    }
                }

                val savedFolderPath = try { preferences.downloadFolderPath.first() } catch (_: Exception) { "" }
                val targetFile = resolveDestinationFile(pelicula.safeTitle, pelicula.safeYear, savedFolderPath)

                val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                val activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING }

                val initialStatus = if (activeCount >= maxLimit) DownloadStatus.PENDING else DownloadStatus.DOWNLOADING

                val downloadItem = DownloadItem(
                    id = pelicula.id,
                    title = pelicula.safeTitle,
                    originalVideoUrl = videoUrl,
                    coverUrl = pelicula.safeCoverUrl,
                    year = pelicula.safeYear,
                    type = pelicula.tp ?: "pl",
                    localFilePath = targetFile.absolutePath,
                    status = initialStatus,
                    progress = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )

                updateItemState(downloadItem)
                preferences.addOrUpdateDownload(downloadItem)

                if (initialStatus == DownloadStatus.DOWNLOADING) {
                    DownloadForegroundService.startDownload(context, downloadItem)
                    AppToastManager.show("Descarga iniciada", ToastType.SUCCESS)
                } else {
                    AppToastManager.show("En cola (máximo $maxLimit descargas activas)", ToastType.INFO)
                }
            }
        }
    }

    fun pauseDownload(item: DownloadItem) {
        clearLiveProgress(item.id)
        val file = File(item.localFilePath)
        val currentDownloaded = if (file.exists()) file.length() else item.downloadedBytes
        val total = item.totalBytes.coerceAtLeast(0L)
        val progress = if (total > 0L && currentDownloaded > 0L) {
            ((currentDownloaded * 100L) / total).toInt().coerceIn(0, 99)
        } else {
            0
        }

        val pausedItem = item.copy(
            status = DownloadStatus.PAUSED,
            downloadedBytes = currentDownloaded,
            totalBytes = total,
            progress = progress,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )

        updateItemState(pausedItem)
        scope.launch(Dispatchers.IO) {
            preferences.addOrUpdateDownload(pausedItem)
            DownloadForegroundService.pauseDownload(context, item.id)
            checkAndStartNextPending()
        }
    }

    fun resumeDownload(item: DownloadItem) {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Desactiva la VPN o Proxy para reanudar la descarga", ToastType.WARNING)
            return
        }

        if (!NetworkUtils.isConnected(context)) {
            AppToastManager.show("Sin conexión a internet", ToastType.WARNING)
            return
        }

        scope.launch(Dispatchers.IO) {
            val isWifiOnlyPref = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
            if (isWifiOnlyPref && !NetworkUtils.isWifiOrEthernet(context)) {
                AppToastManager.show("Solo Wi-Fi activado. Conéctate a Wi-Fi para reanudar.", ToastType.ERROR)
                return@launch
            }

            queueMutex.withLock {
                val currentList = _liveDownloadsState.value
                val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                val activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING && it.id != item.id }

                if (activeCount >= maxLimit) {
                    val pendingItem = item.copy(status = DownloadStatus.PENDING)
                    updateItemState(pendingItem)
                    preferences.addOrUpdateDownload(pendingItem)
                    AppToastManager.show("En cola: máximo $maxLimit descargas activas", ToastType.INFO)
                } else {
                    val resumingItem = item.copy(status = DownloadStatus.DOWNLOADING)
                    updateItemState(resumingItem)
                    preferences.addOrUpdateDownload(resumingItem)
                    DownloadForegroundService.resumeDownload(context, item.id)
                }
            }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        clearLiveProgress(item.id)
        removeItemFromState(item.id)
        DownloadForegroundService.cancelDownload(context, item.id)
    }

    fun forceStartPending(item: DownloadItem) {
        forceStartPendingDownload(item)
    }

    fun forceStartPendingDownload(download: DownloadItem) {
        scope.launch(Dispatchers.IO) {
            val resuming = download.copy(status = DownloadStatus.DOWNLOADING)
            updateItemState(resuming)
            preferences.addOrUpdateDownload(resuming)
            DownloadForegroundService.startDownload(context, resuming)
        }
    }

    fun pauseAllDownloads() {
        DownloadForegroundService.pauseAll(context)
    }

    fun resumeAllDownloads() {
        DownloadForegroundService.resumeAll(context)
    }

    fun cancelAllDownloads() {
        DownloadForegroundService.cancelAll(context)
    }

    fun deleteMultipleDownloads(items: List<DownloadItem>) {
        items.forEach { item ->
            cancelDownload(item)
        }
    }

    fun syncConcurrentDownloadsLimit(count: Int) {
        checkAndStartNextPending()
    }

    fun checkAndStartNextPending() {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                val currentList = _liveDownloadsState.value
                val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                val activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING }

                if (activeCount < maxLimit) {
                    val nextPending = currentList.find { it.status == DownloadStatus.PENDING }
                    if (nextPending != null) {
                        val starting = nextPending.copy(status = DownloadStatus.DOWNLOADING)
                        updateItemState(starting)
                        preferences.addOrUpdateDownload(starting)
                        DownloadForegroundService.startDownload(context, starting)
                    }
                }
            }
        }
    }

    private fun resolveDestinationFile(title: String, year: String, savedFolderPath: String): File {
        val sanitizedTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val fileName = if (year.isNotBlank()) "${sanitizedTitle}_${year}.mp4" else "${sanitizedTitle}.mp4"

        if (savedFolderPath.isNotBlank()) {
            val customDir = File(savedFolderPath)
            if (customDir.exists() && customDir.canWrite()) {
                return File(customDir, fileName)
            }
        }

        val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appFolder = File(publicDownloadDir, "Download Free")
        if (appFolder.exists() || appFolder.mkdirs()) {
            return File(appFolder, fileName)
        }

        val safeAppDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(safeAppDir, fileName)
    }
}
