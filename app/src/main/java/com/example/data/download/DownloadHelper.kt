package com.example.data.download

import android.app.NotificationManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Build
import android.util.Log
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
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
    @Volatile
    private var cachedMaxLimit: Int = 3

    // In-memory Single Source of Truth for 0ms latency UI updates
    private val _liveDownloadsState = MutableStateFlow<List<DownloadItem>>(emptyList())
    val liveDownloadsState: StateFlow<List<DownloadItem>> = _liveDownloadsState.asStateFlow()
    private val isLoadedState = MutableStateFlow(false)

    fun isLoaded(): Boolean = isLoadedState.value

    suspend fun awaitLoaded() {
        if (!isLoadedState.value) {
            isLoadedState.first { it }
        }
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.downloadfree.ACTION_START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.downloadfree.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.downloadfree.ACTION_RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.downloadfree.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.downloadfree.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.downloadfree.ACTION_RESUME_ALL"
        const val ACTION_CANCEL_ALL = "com.downloadfree.ACTION_CANCEL_ALL"
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

        // Migrar descargas existentes a la carpeta pública para que sean visibles en el Explorador
        scope.launch(Dispatchers.IO) {
            delay(1200L)
            migrateExistingDownloadsToPublicDirectory()
        }

        // Load persisted downloads from DataStore on startup
        scope.launch(Dispatchers.IO) {
            val initial = preferences.downloads.first()
            _liveDownloadsState.value = initial
            isLoadedState.value = true

            preferences.downloads.collect { storedList ->
                _liveDownloadsState.update { currentMemList ->
                    if (currentMemList.isEmpty()) {
                        storedList
                    } else {
                        val mappedStored = storedList.map { storedItem ->
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
                        // Preserve any newly started downloads in memory that haven't finished saving to DataStore yet
                        val inFlightMemoryOnly = currentMemList.filter { mem -> storedList.none { it.id == mem.id } }
                        mappedStored + inFlightMemoryOnly
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
                cachedMaxLimit = limit.coerceIn(1, 5)
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

    /**
     * Called when the app becomes visible. Downloads that were DOWNLOADING before
     * process death are safe to restart because the engine resumes from the .part file.
     * Explicitly PAUSED items are never auto-resumed.
     */
    fun recoverDownloadsWhenVisible() {
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            return
        }
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                val interrupted = _liveDownloadsState.value.filter {
                    it.status == DownloadStatus.DOWNLOADING && liveProgressMap[it.id] == null
                }
                for (item in interrupted) {
                    // Re-scheduling the same UIDT job id replaces a stale job and lets
                    // the downloader continue from the existing .part file.
                    DownloadForegroundService.startDownload(context, item)
                }
            }
        }
    }

    fun isPaused(id: String): Boolean {
        return _liveDownloadsState.value.find { it.id == id }?.status == DownloadStatus.PAUSED
    }

    fun isCancelled(id: String): Boolean {
        return _liveDownloadsState.value.none { it.id == id }
    }

    /** Called when Android stops a UIDT job. Persist a resumable state. */
    fun markStoppedForUidt(id: String) {
        val item = getItem(id) ?: return
        if (item.status == DownloadStatus.DOWNLOADING) {
            val part = File(item.localFilePath + ".part")
            val bytes = if (part.exists()) part.length() else item.downloadedBytes
            val progress = if (item.totalBytes > 0L) ((bytes * 100L) / item.totalBytes).toInt().coerceIn(0, 99) else item.progress
            updateAndPersist(item.copy(
                status = DownloadStatus.PAUSED,
                downloadedBytes = bytes,
                progress = progress,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            ))
        }
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

    fun updateAndPersist(item: DownloadItem) {
        updateItemState(item)
        scope.launch(Dispatchers.IO) {
            preferences.addOrUpdateDownload(item)
        }
    }

    fun removeItemFromState(id: String) {
        clearLiveProgress(id)
        _liveDownloadsState.update { list ->
            list.filterNot { it.id == id }
        }
        scope.launch(Dispatchers.IO) {
            preferences.removeDownload(id)
            checkAndStartNextPending()
        }
    }

    fun getMaxConcurrentLimit(): Int = cachedMaxLimit

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
                if (existing != null) {
                    when (existing.status) {
                        DownloadStatus.COMPLETED -> {
                            val f = File(existing.localFilePath)
                            if (f.exists() && f.length() > 0L) {
                                AppToastManager.show("Esta película ya fue descargada", ToastType.INFO)
                                return@launch
                            }
                        }
                        DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                            AppToastManager.show("La descarga ya está en curso o en cola", ToastType.INFO)
                            return@launch
                        }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                            // Keep the original path so the existing .part file can be resumed.
                            resumeDownload(existing)
                            return@launch
                        }
                        else -> Unit
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
        val partFile = File(item.localFilePath + ".part")
        val partialLen = if (partFile.exists()) partFile.length() else 0L
        val file = File(item.localFilePath)
        val fileLen = if (file.exists()) file.length() else 0L

        val currentDownloaded = when {
            partialLen > 0L -> partialLen
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
        // Se pasa la ruta explícitamente: el item ya no está en la lista, así que el servicio
        // no podría averiguarla y el archivo .part quedaría huérfano ocupando espacio.
        DownloadForegroundService.cancelDownload(context, item.id, item.localFilePath)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                // UIDT must be scheduled while the app is visible (unless a documented
                // background-start exemption applies). Leave it pending until next resume.
                return@launch
            }
            if (VpnProxyDetector.isVpnOrProxyActive(context)) {
                AppToastManager.show("Desactiva la VPN o Proxy para iniciar la descarga", ToastType.WARNING)
                return@launch
            }
            val isWifiOnlyPref = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
            if (isWifiOnlyPref && !NetworkUtils.isWifiOrEthernet(context)) {
                AppToastManager.show("Solo Wi-Fi activado. Conéctate a Wi-Fi para descargar.", ToastType.ERROR)
                return@launch
            }
            queueMutex.withLock {
                val limit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                val currentList = _liveDownloadsState.value
                val activeDownloads = currentList.filter { it.status == DownloadStatus.DOWNLOADING && it.id != download.id }
                if (activeDownloads.size >= limit) {
                    AppToastManager.show("Límite de $limit descargas alcanzado. Espera a que termine una descarga.", ToastType.WARNING)
                    return@launch
                }
                val resuming = download.copy(status = DownloadStatus.DOWNLOADING)
                updateItemState(resuming)
                preferences.addOrUpdateDownload(resuming)
                DownloadForegroundService.startDownload(context, resuming)
            }
        }
    }

    fun pauseAllDownloads() {
        DownloadForegroundService.pauseAll(context)
    }

    fun resumeAllDownloads() {
        if (VpnProxyDetector.isVpnOrProxyActive(context)) {
            AppToastManager.show("Desactiva la VPN o Proxy para reanudar descargas", ToastType.WARNING)
            return
        }
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                val isWifiOnlyPref = try { preferences.wifiOnly.first() } catch (_: Exception) { false }
                if (isWifiOnlyPref && !NetworkUtils.isWifiOrEthernet(context)) {
                    AppToastManager.show("Solo Wi-Fi activado. Conéctate a Wi-Fi para reanudar.", ToastType.ERROR)
                    return@launch
                }

                val limit = preferences.maxConcurrentDownloads.first().coerceIn(1, 5)
                val currentList = _liveDownloadsState.value
                val pausedOrPending = currentList.filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING }
                var activeCount = currentList.count { it.status == DownloadStatus.DOWNLOADING }

                for (item in pausedOrPending) {
                    if (activeCount < limit) {
                        val resuming = item.copy(status = DownloadStatus.DOWNLOADING)
                        updateItemState(resuming)
                        preferences.addOrUpdateDownload(resuming)
                        DownloadForegroundService.startDownload(context, resuming)
                        activeCount++
                    } else {
                        val pending = item.copy(status = DownloadStatus.PENDING)
                        updateItemState(pending)
                        preferences.addOrUpdateDownload(pending)
                    }
                }
            }
        }
    }

    fun cancelAllDownloads() {
        scope.launch(Dispatchers.IO) {
            val list = _liveDownloadsState.value
            list.forEach { item ->
                cancelDownload(item)
            }
        }
    }

    fun deleteMultipleDownloads(items: List<DownloadItem>) {
        items.forEach { item ->
            cancelDownload(item)
        }
    }

    /**
     * Persists a safe resumable state when Android terminates the dataSync
     * foreground-service window (Android 15+).
     */
    suspend fun pauseDownloadsForServiceTimeout() {
        val current = _liveDownloadsState.value
        val paused = current.map { item ->
            if (item.status == DownloadStatus.DOWNLOADING) {
                val part = File(item.localFilePath + ".part")
                val actualBytes = if (part.exists()) part.length() else item.downloadedBytes
                val progress = if (item.totalBytes > 0L) {
                    ((actualBytes * 100L) / item.totalBytes).toInt().coerceIn(0, 99)
                } else {
                    item.progress.coerceIn(0, 99)
                }
                clearLiveProgress(item.id)
                item.copy(
                    status = DownloadStatus.PAUSED,
                    downloadedBytes = actualBytes,
                    progress = progress,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
            } else {
                item
            }
        }
        _liveDownloadsState.value = paused
        preferences.updateMultipleDownloads(
            paused.filter { it.status == DownloadStatus.PAUSED }
        )
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

    fun resolveDestinationDirectory(savedFolderPath: String): File {
        // 1. Si el usuario configuró una carpeta en Ajustes, intentamos usarla directamente
        if (savedFolderPath.isNotBlank()) {
            try {
                val customDir = File(savedFolderPath)
                if (!customDir.exists()) {
                    customDir.mkdirs()
                }
                if (customDir.exists() && isDirectoryWritable(customDir)) {
                    return customDir
                }
            } catch (e: Exception) {
                Log.w("DownloadHelper", "No se pudo usar carpeta personalizada: $savedFolderPath", e)
            }
        }

        // 2. Carpeta pública estándar de Descargas del teléfono (visible en el Explorador de Xiaomi HyperOS / Redmi)
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicAppDir = File(publicDownloads, "Download Free")
            if (!publicAppDir.exists()) {
                publicAppDir.mkdirs()
            }
            if (publicAppDir.exists() && isDirectoryWritable(publicAppDir)) {
                return publicAppDir
            }
            if (publicDownloads.exists() && isDirectoryWritable(publicDownloads)) {
                return publicDownloads
            }
        } catch (e: Exception) {
            Log.w("DownloadHelper", "No se pudo usar carpeta pública de descargas", e)
        }

        // 3. Respaldo seguro en almacenamiento específico si el sistema restringe el acceso directo
        val fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download Free")
        fallback.mkdirs()
        return fallback
    }

    private fun isDirectoryWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val probe = File(dir, ".probe_${System.currentTimeMillis()}.tmp")
            if (probe.createNewFile()) {
                probe.delete()
                true
            } else {
                dir.canWrite()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveDestinationFile(title: String, secondaryTag: String, savedFolderPath: String): File {
        val fileName = buildSafeFileName(title, secondaryTag)
        val dir = resolveDestinationDirectory(savedFolderPath)

        // Never silently overwrite another completed download with the same title.
        var candidate = File(dir, fileName)
        if (!candidate.exists() && !File(candidate.absolutePath + ".part").exists()) {
            return candidate
        }

        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (index < 10_000) {
            candidate = File(dir, "$stem ($index)$ext")
            if (!candidate.exists() && !File(candidate.absolutePath + ".part").exists()) {
                return candidate
            }
            index++
        }
        return File(dir, "$stem (${System.currentTimeMillis()})$ext")
    }

    suspend fun migrateExistingDownloadsToPublicDirectory() {
        try {
            val savedFolderPath = try { preferences.downloadFolderPath.first() } catch (_: Exception) { "" }
            val targetDir = resolveDestinationDirectory(savedFolderPath)

            val currentList = _liveDownloadsState.value
            val updatedItems = mutableListOf<DownloadItem>()

            currentList.forEach { item ->
                if (item.status == DownloadStatus.COMPLETED) {
                    val currentFile = File(item.localFilePath)
                    if (currentFile.exists() && currentFile.absolutePath.contains("/Android/data/")) {
                        val destinationFile = File(targetDir, currentFile.name)
                        try {
                            if (!destinationFile.exists() || destinationFile.length() != currentFile.length()) {
                                currentFile.copyTo(destinationFile, overwrite = true)
                            }
                            currentFile.delete()
                            val updated = item.copy(localFilePath = destinationFile.absolutePath)
                            updatedItems.add(updated)

                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(destinationFile.absolutePath),
                                arrayOf("video/mp4")
                            ) { path, uri ->
                                Log.d("DownloadHelper", "MediaScanner migrado: $path -> $uri")
                            }
                        } catch (e: Exception) {
                            Log.e("DownloadHelper", "Error migrando archivo ${currentFile.name}", e)
                        }
                    }
                }
            }

            if (updatedItems.isNotEmpty()) {
                preferences.updateMultipleDownloads(updatedItems)
                _liveDownloadsState.update { current ->
                    current.map { existing ->
                        updatedItems.find { it.id == existing.id } ?: existing
                    }
                }
            }

            // También migrar archivos huérfanos que hayan quedado en el directorio privado
            val oldPrivateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (oldPrivateDir != null && oldPrivateDir.exists()) {
                oldPrivateDir.listFiles { f -> f.extension.equals("mp4", ignoreCase = true) }?.forEach { orphanedFile ->
                    try {
                        val destination = File(targetDir, orphanedFile.name)
                        if (!destination.exists() || destination.length() != orphanedFile.length()) {
                            orphanedFile.copyTo(destination, overwrite = true)
                        }
                        orphanedFile.delete()
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(destination.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadHelper", "Error en migrateExistingDownloadsToPublicDirectory", e)
        }
    }
}
