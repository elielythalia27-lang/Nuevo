package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiService
import com.example.data.download.DownloadHelper
import com.example.data.local.PeliculaPreferences
import com.example.data.model.ContinueWatchingItem
import com.example.data.model.DownloadItem
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

        // Collect downloads
        viewModelScope.launch {
            repository.downloads.collectLatest { downloadList ->
                _uiState.update { it.copy(downloads = downloadList) }
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
        downloadHelper.startDownload(pelicula)
    }

    fun pauseDownload(item: DownloadItem) {
        downloadHelper.pauseDownload(item)
    }

    fun resumeDownload(item: DownloadItem) {
        downloadHelper.resumeDownload(item)
    }

    fun cancelDownload(item: DownloadItem) {
        downloadHelper.cancelDownload(item)
    }

    fun deleteMultipleDownloads(items: List<DownloadItem>) {
        downloadHelper.deleteMultipleDownloads(items)
    }

    fun forceStartPendingDownload(item: DownloadItem) {
        downloadHelper.forceStartPending(item)
    }

    fun pauseAllDownloads() {
        downloadHelper.pauseAllDownloads()
    }

    fun resumeAllDownloads() {
        downloadHelper.resumeAllDownloads()
    }

    fun cancelAllDownloads() {
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
        val correspondingColor = AppThemeColor.getCorrespondingColor(current.themeColor, isDark)
        _uiState.update { it.copy(themeMode = mode, themeColor = correspondingColor, isDarkTheme = isDark) }
        viewModelScope.launch {
            repository.setThemeMode(mode)
            if (correspondingColor.id != current.themeColor.id) {
                repository.setThemeColor(correspondingColor.id)
            }
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
