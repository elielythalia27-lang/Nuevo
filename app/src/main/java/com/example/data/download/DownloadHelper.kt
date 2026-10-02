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
import com.example.utils.NetworkMonitor
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
        const val ACTION_START_DOWNLOAD = "com.downloadfree.ACTION_START_DOWNLOAD"
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
            preferences.maxConcurrentDownloads.collect { limit ->
                syncConcurrentDownloadsLimit(limit)
            }
        }

        // Observe wifiOnly settings changes
        scope.launch(Dispatchers.IO) {
            preferences.wifiOnly.collect { enabled ->
                handleWifiOnlyChange(enabled)
            }
        }

        // Observe network changes to automatically resume pending downloads upon connecting to Wi-Fi
        val networkMonitor = NetworkMonitor(context)
        scope.launch(Dispatchers.IO) {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    val isWifiOnlyPref = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
                    if (isWifiOnlyPref) {
                        if (NetworkUtils.isWifiOrEthernet(context)) {
                            syncConcurrentDownloadsLimit()
                        } else {
                            handleWifiOnlyChange(true)
                        }
                    } else {
                        syncConcurrentDownloadsLimit()
                    }
                }
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
        val current = _liveDownloadsState.value.find { it.id == downloadId } ?: return
        if (current.status == DownloadStatus.PAUSED) return

        liveProgressMap[downloadId] = LiveProgressUpdate(
            downloadId = downloadId,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progress = progress,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds
        )

        // Keep in-memory SSOT state updated immediately so UI and pause calls never drop to 0
        _liveDownloadsState.update { list ->
            list.map { item ->
                if (item.id == downloadId) {
                    if (item.status == DownloadStatus.PAUSED) {
                        item
                    } else {
                        item.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (totalBytes > 0L) totalBytes else item.totalBytes,
                            progress = progress,
                            speedBytesPerSec = speedBytesPerSec,
                            etaSeconds = etaSeconds,
                            status = DownloadStatus.DOWNLOADING
                        )
                    }
                } else item
            }
        }
    }

    fun reportProgress(
        downloadId: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        progress: Int,
        speedBytesPerSec: Long,
        etaSeconds: Long
    ) {
        reportProgress(
            downloadId.toString(),
            downloadedBytes,
            totalBytes,
            progress,
            speedBytesPerSec,
            etaSeconds
        )
    }

    fun getLiveProgress(downloadId: String): LiveProgressUpdate? = liveProgressMap[downloadId]

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
                val targetFile = resolveDestinationFile(pelicula.safeTitle, pelicula.secondaryTag, savedFolderPath)

                val maxLimit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                val activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING }

                val initialStatus = if (activeCount >= maxLimit) DownloadStatus.PENDING else DownloadStatus.DOWNLOADING

                val downloadItem = DownloadItem(
                    id = pelicula.id,
                    title = pelicula.safeTitle,
                    originalVideoUrl = videoUrl,
                    coverUrl = pelicula.safeCoverUrl,
                    year = pelicula.secondaryTag,
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
        val live = liveProgressMap[item.id]
        val file = File(item.localFilePath)
        val fileLen = if (file.exists()) file.length() else 0L

        val currentDownloaded = when {
            fileLen > 0L -> fileLen
            live != null && live.downloadedBytes > 0L -> live.downloadedBytes
            item.downloadedBytes > 0L -> item.downloadedBytes
            else -> 0L
        }

        val total = when {
            live != null && live.totalBytes > 0L -> live.totalBytes
            item.totalBytes > 0L -> item.totalBytes
            else -> 0L
        }

        val progress = when {
            total > 0L && currentDownloaded > 0L -> ((currentDownloaded * 100L) / total).toInt().coerceIn(0, 99)
            live != null && live.progress > 0 -> live.progress
            item.progress > 0 -> item.progress
            else -> 0
        }

        clearLiveProgress(item.id)

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

    fun handleWifiOnlyChange(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                if (enabled && !NetworkUtils.isWifiOrEthernet(context)) {
                    val currentList = _liveDownloadsState.value
                    val activeDownloads = currentList.filter { it.status == DownloadStatus.DOWNLOADING }
                    if (activeDownloads.isNotEmpty()) {
                        for (item in activeDownloads) {
                            val queuedItem = item.copy(
                                status = DownloadStatus.PENDING,
                                speedBytesPerSec = 0L,
                                etaSeconds = 0L
                            )
                            updateItemState(queuedItem)
                            preferences.addOrUpdateDownload(queuedItem)
                            DownloadForegroundService.queueDownload(context, item.id)
                        }
                        AppToastManager.show("Solo Wi-Fi activado: descargas en datos móviles en espera", ToastType.INFO)
                    }
                } else if (!enabled || NetworkUtils.isWifiOrEthernet(context)) {
                    val limit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                    val currentList = _liveDownloadsState.value
                    val activeDownloads = currentList.filter { it.status == DownloadStatus.DOWNLOADING }
                    var slotsAvailable = limit - activeDownloads.size
                    if (slotsAvailable > 0) {
                        val pendingDownloads = currentList.filter { it.status == DownloadStatus.PENDING }
                        for (pending in pendingDownloads) {
                            if (slotsAvailable <= 0) break
                            forceStartPendingDownload(pending)
                            slotsAvailable--
                        }
                    }
                }
            }
        }
    }

    fun forceStartPendingDownload(download: DownloadItem) {
        scope.launch(Dispatchers.IO) {
            val isWifiOnlyPref = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
            if (isWifiOnlyPref && !NetworkUtils.isWifiOrEthernet(context)) {
                return@launch
            }
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

    fun syncConcurrentDownloadsLimit(newLimit: Int? = null) {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                val isWifiOnlyPref = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
                if (isWifiOnlyPref && !NetworkUtils.isWifiOrEthernet(context)) {
                    val currentList = _liveDownloadsState.value
                    val activeDownloads = currentList.filter { it.status == DownloadStatus.DOWNLOADING }
                    for (item in activeDownloads) {
                        val queuedItem = item.copy(
                            status = DownloadStatus.PENDING,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        updateItemState(queuedItem)
                        preferences.addOrUpdateDownload(queuedItem)
                        DownloadForegroundService.queueDownload(context, item.id)
                    }
                    return@launch
                }

                val limit = (newLimit ?: preferences.maxConcurrentDownloads.first()).coerceIn(1, 5)
                val currentList = _liveDownloadsState.value
                val activeDownloads = currentList.filter { it.status == DownloadStatus.DOWNLOADING }

                if (activeDownloads.size > limit) {
                    // Lowered limit: gracefully requeue excess downloads in real-time to PENDING
                    val excessCount = activeDownloads.size - limit
                    val toQueue = activeDownloads.takeLast(excessCount)
                    for (item in toQueue) {
                        val queuedItem = item.copy(
                            status = DownloadStatus.PENDING,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        updateItemState(queuedItem)
                        preferences.addOrUpdateDownload(queuedItem)
                        DownloadForegroundService.queueDownload(context, item.id)
                    }
                } else if (activeDownloads.size < limit) {
                    // Raised limit: start as many pending downloads as slots are open
                    var slotsAvailable = limit - activeDownloads.size
                    val pendingDownloads = currentList.filter { it.status == DownloadStatus.PENDING }
                    for (pending in pendingDownloads) {
                        if (slotsAvailable <= 0) break
                        forceStartPendingDownload(pending)
                        slotsAvailable--
                    }
                }
            }
        }
    }

    fun checkAndStartNextPending() {
        syncConcurrentDownloadsLimit()
    }

    fun buildSafeFileName(title: String, secondaryTag: String): String {
        val cleanTitle = title.replace("[\"*/:<>?\\\\|]".toRegex(), "").trim()
        val cleanTag = secondaryTag.replace("[\"*/:<>?\\\\|()]".toRegex(), "").trim()
        val baseName = if (cleanTag.isNotBlank() && !cleanTitle.contains("($cleanTag)")) {
            "$cleanTitle ($cleanTag)"
        } else {
            cleanTitle
        }
        return if (baseName.endsWith(".mp4", ignoreCase = true)) baseName else "$baseName.mp4"
    }

    private fun resolveDestinationFile(title: String, secondaryTag: String, savedFolderPath: String): File {
        val fileName = buildSafeFileName(title, secondaryTag)

        if (savedFolderPath.isNotBlank()) {
            try {
                val customDir = File(savedFolderPath)
                if (customDir.exists() && customDir.canWrite()) {
                    return File(customDir, fileName)
                }
            } catch (_: Exception) {}
        }

        val safeAppDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download Free")
        safeAppDir.mkdirs()
        return File(safeAppDir, fileName)
    }
}
