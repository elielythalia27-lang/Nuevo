package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiService
import com.example.data.download.DownloadHelper
import com.example.data.local.PeliculaPreferences
import com.example.data.model.ContinueWatchingItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.data.model.SortOption
import com.example.data.model.ThemeMode
import com.example.data.repository.PeliculaRepository
import com.example.data.repository.Resource
import com.example.ui.theme.AppThemeColor
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class PlaybackTarget(
    val videoUrl: String,
    val title: String,
    val coverUrl: String,
    val year: String,
    val type: String,
    val initialPositionMs: Long = 0L
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isDataOffline: Boolean = false,
    val isNetworkOnline: Boolean = true,
    val allPeliculas: List<Pelicula> = emptyList(),
    val filteredPeliculas: List<Pelicula> = emptyList(),
    val searchQuery: String = "",
    val selectedType: String = "ALL", // "ALL", "MOVIE", "VIDEO"
    val onlyFavorites: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val continueWatching: ContinueWatchingItem? = null,
    val downloads: List<DownloadItem> = emptyList(),
    val activeDownloadsCount: Int = 0,
    val isDarkTheme: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: AppThemeColor = AppThemeColor.TEAL,
    val sortOption: SortOption = SortOption.NAME_AZ, // Default: Nombre (A-Z)
    val maxConcurrentDownloads: Int = 3,
    val catalogLayoutMode: String = "GRID_2",
    val downloadFolderName: String = "Download Free",
    val downloadFolderPath: String = "",
    val activePlayback: PlaybackTarget? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PeliculaPreferences(application)
    private val apiService = ApiService.create()
    val repository = PeliculaRepository(application, apiService, preferences)
    val downloadHelper = DownloadHelper.getActiveInstance(application)
    private val networkMonitor = NetworkMonitor(application)

    private val pendingPausedIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val pendingResumedIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun calculateActiveDownloadsCount(items: List<DownloadItem>): Int {
        return items.count { it.status == DownloadStatus.DOWNLOADING }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Collect network state
        viewModelScope.launch {
            networkMonitor.isOnline.collectLatest { online: Boolean ->
                _uiState.update { it.copy(isNetworkOnline = online) }
            }
        }

        // Collect favorites
        viewModelScope.launch {
            repository.favoriteIds.collectLatest { favs ->
                _uiState.update { state ->
                    val updated = state.copy(favoriteIds = favs)
                    applyFilter(updated)
                }
            }
        }

        // Collect continue watching
        viewModelScope.launch {
            repository.continueWatching.collectLatest { item ->
                _uiState.update { it.copy(continueWatching = item) }
            }
        }

        // Collect downloads with real-time state reconciliation
        viewModelScope.launch {
            repository.downloads.collectLatest { downloadList ->
                val reconciledList = downloadList.map { item ->
                    when {
                        pendingPausedIds.contains(item.id) -> {
                            if (item.status == DownloadStatus.PAUSED) {
                                pendingPausedIds.remove(item.id)
                                item
                            } else {
                                item.copy(
                                    status = DownloadStatus.PAUSED,
                                    speedBytesPerSec = 0L,
                                    etaSeconds = 0L
                                )
                            }
                        }
                        pendingResumedIds.contains(item.id) -> {
                            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PENDING) {
                                pendingResumedIds.remove(item.id)
                                item
                            } else {
                                item.copy(status = DownloadStatus.DOWNLOADING)
                            }
                        }
                        else -> item
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        downloads = reconciledList,
                        activeDownloadsCount = calculateActiveDownloadsCount(reconciledList)
                    )
                }
            }
        }

        // Collect dark theme & theme mode
        viewModelScope.launch {
            repository.isDarkTheme.collectLatest { dark ->
                _uiState.update { it.copy(isDarkTheme = dark) }
            }
        }

        viewModelScope.launch {
            repository.themeMode.collectLatest { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }

        // Collect theme color
        viewModelScope.launch {
            repository.themeColor.collectLatest { colorId ->
                _uiState.update { it.copy(themeColor = AppThemeColor.fromId(colorId)) }
            }
        }

        // Collect default filter type
        viewModelScope.launch {
            repository.defaultFilterType.collectLatest { filterType ->
                _uiState.update { state ->
                    val updated = state.copy(selectedType = filterType)
                    applyFilter(updated)
                }
            }
        }

        // Collect sort option (default is NAME_AZ)
        viewModelScope.launch {
            repository.sortOption.collectLatest { sort ->
                _uiState.update { state ->
                    val updated = state.copy(sortOption = sort)
                    applyFilter(updated)
                }
            }
        }

        // Collect max concurrent downloads
        viewModelScope.launch {
            repository.maxConcurrentDownloads.collectLatest { limit ->
                _uiState.update { it.copy(maxConcurrentDownloads = limit) }
            }
        }

        // Collect catalog layout mode
        viewModelScope.launch {
            repository.catalogLayoutMode.collectLatest { mode ->
                _uiState.update { it.copy(catalogLayoutMode = mode) }
            }
        }

        // Collect download folder
        viewModelScope.launch {
            repository.downloadFolderName.collectLatest { name ->
                _uiState.update { it.copy(downloadFolderName = name) }
            }
        }
        viewModelScope.launch {
            repository.downloadFolderPath.collectLatest { path ->
                _uiState.update { it.copy(downloadFolderPath = path) }
            }
        }

        loadPeliculas()
    }

    fun loadPeliculas(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.getPeliculasFlow(forceRefresh).collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }

                    is Resource.Success -> {
                        val items = resource.data
                        _uiState.update { state ->
                            val updated = state.copy(
                                isLoading = false,
                                isDataOffline = resource.isOffline,
                                errorMessage = null,
                                allPeliculas = items
                            )
                            applyFilter(updated)
                        }
                    }

                    is Resource.Error -> {
                        _uiState.update { state ->
                            if (state.allPeliculas.isNotEmpty()) {
                                // Conservamos las películas cargadas previamente en caché
                                state.copy(
                                    isLoading = false,
                                    isDataOffline = true,
                                    errorMessage = null
                                )
                            } else {
                                state.copy(
                                    isLoading = false,
                                    errorMessage = resource.message,
                                    allPeliculas = emptyList(),
                                    filteredPeliculas = emptyList()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val updated = state.copy(searchQuery = query)
            applyFilter(updated)
        }
    }

    fun onTypeSelected(type: String) {
        _uiState.update { state ->
            val updated = state.copy(selectedType = type)
            applyFilter(updated)
        }
        viewModelScope.launch {
            repository.setDefaultFilterType(type)
        }
    }

    fun onToggleOnlyFavorites(enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.copy(onlyFavorites = enabled)
            applyFilter(updated)
        }
    }

    fun clearFilters() {
        _uiState.update { state ->
            val updated = state.copy(
                searchQuery = "",
                selectedType = "ALL",
                onlyFavorites = false
            )
            applyFilter(updated)
        }
        viewModelScope.launch {
            repository.setDefaultFilterType("ALL")
        }
    }

    private fun applyFilter(state: HomeUiState): HomeUiState {
        var list = state.allPeliculas

        // Search query filter
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.safeTitle.lowercase().contains(query) ||
                        it.safeYear.lowercase().contains(query)
            }
        }

        // Type filter ("ALL", "MOVIE", "VIDEO")
        when (state.selectedType) {
            "MOVIE" -> list = list.filter { it.isMovie }
            "VIDEO" -> list = list.filter { it.isVideo }
        }

        // Only favorites filter
        if (state.onlyFavorites) {
            list = list.filter { state.favoriteIds.contains(it.id) }
        }

        // Sort option - NAME_AZ, NAME_ZA
        list = when (state.sortOption) {
            SortOption.NAME_AZ -> list.sortedBy { it.safeTitle.lowercase() }
            SortOption.NAME_ZA -> list.sortedByDescending { it.safeTitle.lowercase() }
        }

        return state.copy(filteredPeliculas = list)
    }

    fun setMaxConcurrentDownloads(count: Int) {
        viewModelScope.launch {
            repository.setMaxConcurrentDownloads(count)
        }
    }

    fun setCatalogLayoutMode(mode: String) {
        viewModelScope.launch {
            repository.setCatalogLayoutMode(mode)
        }
    }

    fun setDownloadFolder(name: String, path: String) {
        viewModelScope.launch {
            repository.setDownloadFolder(name, path)
        }
    }

    fun toggleFavorite(pelicula: Pelicula) {
        viewModelScope.launch {
            repository.toggleFavorite(pelicula.id)
        }
    }

    fun startDownload(pelicula: Pelicula) {
        val existing = _uiState.value.downloads.find { it.id == pelicula.id }
        if (existing != null && existing.status == DownloadStatus.PAUSED) {
            resumeDownload(existing)
            return
        }
        _uiState.update { state ->
            val isAlreadyActive = state.downloads.any {
                it.id == pelicula.id && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.COMPLETED)
            }
            if (isAlreadyActive) return@update state

            val currentActive = state.downloads.count { it.status == DownloadStatus.DOWNLOADING }
            val nextStatus = if (currentActive < state.maxConcurrentDownloads) DownloadStatus.DOWNLOADING else DownloadStatus.PENDING
            val extraTag = if (pelicula.isVideo) pelicula.youtuberName else pelicula.safeYear
            val displayTitleWithTag = if (extraTag.isNotBlank() && !pelicula.safeTitle.contains("($extraTag)")) {
                "${pelicula.safeTitle} ($extraTag)"
            } else {
                pelicula.safeTitle
            }

            val optimisticItem = DownloadItem(
                id = pelicula.id,
                title = displayTitleWithTag,
                originalVideoUrl = pelicula.safeVideoUrl,
                coverUrl = pelicula.safeCoverUrl,
                year = extraTag,
                type = pelicula.tp ?: "pl",
                localFilePath = "",
                status = nextStatus,
                progress = 0
            )

            if (nextStatus == DownloadStatus.DOWNLOADING) {
                pendingResumedIds.add(pelicula.id)
                pendingPausedIds.remove(pelicula.id)
            }

            val updated = state.downloads.filterNot { it.id == pelicula.id } + optimisticItem
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.startDownload(pelicula)
    }

    fun pauseDownload(item: DownloadItem) {
        pendingPausedIds.add(item.id)
        pendingResumedIds.remove(item.id)
        _uiState.update { state ->
            val updated = state.downloads.map {
                if (it.id == item.id) {
                    it.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                } else it
            }
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.pauseDownload(item)
    }

    fun resumeDownload(item: DownloadItem) {
        pendingResumedIds.add(item.id)
        pendingPausedIds.remove(item.id)
        _uiState.update { state ->
            val currentActive = state.downloads.count { it.status == DownloadStatus.DOWNLOADING && it.id != item.id }
            val nextStatus = if (currentActive < state.maxConcurrentDownloads) {
                DownloadStatus.DOWNLOADING
            } else {
                DownloadStatus.PENDING
            }
            val updated = state.downloads.map {
                if (it.id == item.id) {
                    it.copy(
                        status = nextStatus,
                        speedBytesPerSec = if (nextStatus == DownloadStatus.DOWNLOADING) it.speedBytesPerSec else 0L
                    )
                } else it
            }
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.resumeDownload(item)
    }

    fun cancelDownload(item: DownloadItem) {
        pendingPausedIds.remove(item.id)
        pendingResumedIds.remove(item.id)
        _uiState.update { state ->
            val updated = state.downloads.filterNot { it.id == item.id }
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.cancelDownload(item)
    }

    fun deleteMultipleDownloads(items: List<DownloadItem>) {
        val idsToDelete = items.map { it.id }.toSet()
        idsToDelete.forEach { id ->
            pendingPausedIds.remove(id)
            pendingResumedIds.remove(id)
        }
        _uiState.update { state ->
            val updated = state.downloads.filterNot { idsToDelete.contains(it.id) }
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.deleteMultipleDownloads(items)
    }

    fun forceStartPendingDownload(item: DownloadItem) {
        pendingPausedIds.remove(item.id)
        pendingResumedIds.add(item.id)
        _uiState.update { state ->
            val updated = state.downloads.map {
                if (it.id == item.id) it.copy(status = DownloadStatus.DOWNLOADING)
                else it
            }
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.forceStartPending(item)
    }

    fun pauseAllDownloads() {
        _uiState.update { state ->
            state.downloads.forEach {
                if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING) {
                    pendingPausedIds.add(it.id)
                    pendingResumedIds.remove(it.id)
                }
            }
            val updated = state.downloads.map {
                if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING) {
                    it.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                } else it
            }
            state.copy(
                downloads = updated,
                activeDownloadsCount = 0
            )
        }
        downloadHelper.pauseAllDownloads()
    }

    fun resumeAllDownloads() {
        pendingPausedIds.clear()
        _uiState.update { state ->
            var activeCount = 0
            val maxLimit = state.maxConcurrentDownloads
            val updated = state.downloads.map {
                if (it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.FAILED) {
                    if (activeCount < maxLimit) {
                        activeCount++
                        pendingResumedIds.add(it.id)
                        it.copy(status = DownloadStatus.DOWNLOADING)
                    } else {
                        it.copy(status = DownloadStatus.PENDING, speedBytesPerSec = 0L, etaSeconds = 0L)
                    }
                } else {
                    if (it.status == DownloadStatus.DOWNLOADING) activeCount++
                    it
                }
            }
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.resumeAllDownloads()
    }

    fun cancelAllDownloads() {
        pendingPausedIds.clear()
        pendingResumedIds.clear()
        _uiState.update { state ->
            val updated = state.downloads.filter { it.status == DownloadStatus.COMPLETED }
            state.copy(
                downloads = updated,
                activeDownloadsCount = 0
            )
        }
        downloadHelper.cancelAllDownloads()
    }

    fun savePlaybackPosition(
        videoUrl: String,
        title: String,
        coverUrl: String,
        year: String,
        type: String,
        positionMs: Long,
        durationMs: Long
    ) {
        viewModelScope.launch {
            if (positionMs > 1000L && (durationMs <= 0L || positionMs < durationMs - 5000L)) {
                repository.saveContinueWatching(
                    ContinueWatchingItem(
                        videoUrl = videoUrl,
                        title = title,
                        coverUrl = coverUrl,
                        year = year,
                        type = type,
                        positionMs = positionMs,
                        durationMs = durationMs
                    )
                )
            } else if (durationMs > 0 && positionMs >= durationMs - 5000L) {
                repository.clearContinueWatching()
            }
        }
    }

    fun clearContinueWatching() {
        viewModelScope.launch {
            repository.clearContinueWatching()
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode, targetIsDark: Boolean? = null) {
        val current = _uiState.value
        val isDark = targetIsDark ?: when (mode) {
            ThemeMode.SYSTEM -> current.isDarkTheme
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        _uiState.update { it.copy(themeMode = mode, isDarkTheme = isDark) }
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setSortOption(sortOption: SortOption) {
        viewModelScope.launch {
            repository.setSortOption(sortOption)
        }
    }

    fun playPelicula(pelicula: Pelicula, initialPositionMs: Long = 0L) {
        val savedPos = if (initialPositionMs > 0L) initialPositionMs else {
            val cw = _uiState.value.continueWatching
            if (cw != null && cw.videoUrl == pelicula.safeVideoUrl) cw.positionMs else 0L
        }
        _uiState.update {
            it.copy(
                activePlayback = PlaybackTarget(
                    videoUrl = pelicula.safeVideoUrl,
                    title = pelicula.safeTitle,
                    coverUrl = pelicula.safeCoverUrl,
                    year = pelicula.safeYear,
                    type = pelicula.tp ?: "pl",
                    initialPositionMs = savedPos
                )
            )
        }
    }

    fun playDownload(download: DownloadItem) {
        val savedPos = _uiState.value.continueWatching?.let {
            if (it.videoUrl == download.originalVideoUrl || it.videoUrl == download.localFilePath) it.positionMs else 0L
        } ?: 0L
        _uiState.update {
            it.copy(
                activePlayback = PlaybackTarget(
                    videoUrl = download.localFilePath,
                    title = download.title,
                    coverUrl = download.coverUrl,
                    year = download.year,
                    type = download.type,
                    initialPositionMs = savedPos
                )
            )
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(activePlayback = null) }
    }

    fun setThemeColor(color: AppThemeColor) {
        viewModelScope.launch {
            repository.setThemeColor(color.id)
        }
    }

    fun setDefaultFilterType(type: String) {
        viewModelScope.launch {
            repository.setDefaultFilterType(type)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            loadPeliculas(forceRefresh = true)
        }
    }
}
