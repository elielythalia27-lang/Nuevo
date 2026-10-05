package com.example.viewmodel

import android.app.Application
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
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
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.ui.theme.AppThemeColor
import com.example.utils.BatteryOptimizationHelper
import com.example.utils.NetworkMonitor
import com.example.utils.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackTarget(
    val videoUrl: String,
    val title: String,
    val coverUrl: String,
    val year: String,
    val type: String,
    val initialPositionMs: Long = 0L,
    val initialDurationMs: Long = 0L
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
    val wifiOnly: Boolean = false,
    val activePlayback: PlaybackTarget? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PeliculaPreferences(application)
    private val apiService = ApiService.create()
    val repository = PeliculaRepository(application, apiService, preferences)
    val downloadHelper = DownloadHelper.getActiveInstance(application)
    private val networkMonitor = NetworkMonitor(application)

    private fun calculateActiveDownloadsCount(items: List<DownloadItem>): Int {
        return items.count { it.status == DownloadStatus.DOWNLOADING }
    }

    private val initialThemeMode = repository.getSyncThemeMode()
    private val initialThemeColor = AppThemeColor.fromId(repository.getSyncThemeColor())
    private val initialIsDark = when (initialThemeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val uiMode = application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            uiMode == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private val _uiState = MutableStateFlow(
        HomeUiState(
            themeMode = initialThemeMode,
            themeColor = initialThemeColor,
            isDarkTheme = initialIsDark
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _showBatteryDownloadNotice = MutableStateFlow(false)
    val showBatteryDownloadNotice: StateFlow<Boolean> = _showBatteryDownloadNotice.asStateFlow()

    fun dismissBatteryDownloadNotice() {
        _showBatteryDownloadNotice.value = false
    }

    private fun checkAndTriggerBatteryNoticeOnDownload() {
        viewModelScope.launch {
            val isRestricted = !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(getApplication())
            val alreadyShown = preferences.batteryNoticeOnDownloadShown.first()
            if (isRestricted && !alreadyShown) {
                preferences.setBatteryNoticeOnDownloadShown(true)
                _showBatteryDownloadNotice.value = true
            }
        }
    }

    val batteryOptDontShowAgain = preferences.batteryOptDontShowAgain
    val batteryOptLastPromptTimestamp = preferences.batteryOptLastPromptTimestamp

    fun setBatteryOptDontShowAgain(dontShow: Boolean) {
        viewModelScope.launch {
            preferences.setBatteryOptDontShowAgain(dontShow)
        }
    }

    fun setBatteryOptLastPromptTimestamp(timestamp: Long) {
        viewModelScope.launch {
            preferences.setBatteryOptLastPromptTimestamp(timestamp)
        }
    }

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

        // Collect downloads reactively with strict 700ms StateFlow cycle from downloadHelper
        viewModelScope.launch {
            downloadHelper.liveDownloadsState.collectLatest { downloadList ->
                _uiState.update { state ->
                    state.copy(
                        downloads = downloadList,
                        activeDownloadsCount = calculateActiveDownloadsCount(downloadList)
                    )
                }
            }
        }

        // Collect theme mode as single source of truth
        viewModelScope.launch {
            repository.themeMode.collectLatest { mode ->
                val systemIsDark = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val isDark = when (mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> systemIsDark
                }
                _uiState.update { it.copy(themeMode = mode, isDarkTheme = isDark) }
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

        // Collect wifi only
        viewModelScope.launch {
            repository.wifiOnly.collectLatest { enabled ->
                _uiState.update { it.copy(wifiOnly = enabled) }
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
                                filteredPeliculas = emptyList(),
                                isDataOffline = true
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
            downloadHelper.syncConcurrentDownloadsLimit(count)
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
        checkAndTriggerBatteryNoticeOnDownload()
        if (_uiState.value.wifiOnly && !NetworkUtils.isWifiOrEthernet(getApplication())) {
            AppToastManager.show(
                "Descarga bloqueada: 'Solo Wi-Fi' está activo y estás en datos móviles",
                ToastType.ERROR
            )
            return
        }
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

            val updated = state.downloads.filterNot { it.id == pelicula.id } + optimisticItem
            state.copy(
                downloads = updated,
                activeDownloadsCount = calculateActiveDownloadsCount(updated)
            )
        }
        downloadHelper.startDownload(pelicula)
    }

    fun pauseDownload(item: DownloadItem) {
        downloadHelper.pauseDownload(item)
    }

    fun resumeDownload(item: DownloadItem) {
        if (_uiState.value.wifiOnly && !NetworkUtils.isWifiOrEthernet(getApplication())) {
            AppToastManager.show(
                "Descarga en pausa: 'Solo Wi-Fi' está activo y estás en datos móviles",
                ToastType.ERROR
            )
            return
        }
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
            val previousItem = _uiState.value.continueWatching?.takeIf { it.videoUrl == videoUrl }
            val effectiveDuration = if (durationMs > 0L) {
                durationMs
            } else {
                previousItem?.durationMs ?: 0L
            }
            if (positionMs > 1000L && (effectiveDuration <= 0L || positionMs < effectiveDuration - 1000L)) {
                repository.saveContinueWatching(
                    ContinueWatchingItem(
                        videoUrl = videoUrl,
                        title = title,
                        coverUrl = coverUrl,
                        year = year,
                        type = type,
                        positionMs = positionMs,
                        durationMs = effectiveDuration
                    )
                )
            } else if (effectiveDuration > 0 && positionMs >= effectiveDuration - 1000L) {
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

        // 1. Persist synchronously in SharedPreferences BEFORE any activity recreation
        repository.saveSyncTheme(mode, correspondingColor.id)

        val appCompatMode = when (mode) {
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(appCompatMode)

        _uiState.update {
            it.copy(
                themeMode = mode,
                isDarkTheme = isDark,
                themeColor = correspondingColor
            )
        }
        viewModelScope.launch {
            repository.setThemeMode(mode)
            repository.setThemeColor(correspondingColor.id)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        _uiState.update { it.copy(wifiOnly = enabled) }
        viewModelScope.launch {
            repository.setWifiOnly(enabled)
            downloadHelper.handleWifiOnlyChange(enabled)
        }
    }

    fun setSortOption(sortOption: SortOption) {
        viewModelScope.launch {
            repository.setSortOption(sortOption)
        }
    }

    fun playPelicula(pelicula: Pelicula, initialPositionMs: Long = 0L) {
        val isOnline = pelicula.safeVideoUrl.startsWith("http", ignoreCase = true)
        if (isOnline) {
            if (!NetworkUtils.isConnected(getApplication())) {
                AppToastManager.show(
                    "Sin conexión a internet. No se puede reproducir en línea.",
                    ToastType.ERROR
                )
                return
            }
            if (_uiState.value.wifiOnly && !NetworkUtils.isWifiOrEthernet(getApplication())) {
                AppToastManager.show(
                    "Reproducción bloqueada: 'Solo Wi-Fi' está activo y estás conectado por datos móviles",
                    ToastType.ERROR
                )
                return
            }
        }
        val cw = _uiState.value.continueWatching?.takeIf { it.videoUrl == pelicula.safeVideoUrl }
        val savedPos = if (initialPositionMs > 0L) initialPositionMs else (cw?.positionMs ?: 0L)
        val savedDur = cw?.durationMs ?: 0L
        val tag = if (pelicula.isVideo) pelicula.youtuberName else pelicula.safeYear
        _uiState.update {
            it.copy(
                activePlayback = PlaybackTarget(
                    videoUrl = pelicula.safeVideoUrl,
                    title = pelicula.safeTitle,
                    coverUrl = pelicula.safeCoverUrl,
                    year = tag,
                    type = pelicula.tp ?: "pl",
                    initialPositionMs = savedPos,
                    initialDurationMs = savedDur
                )
            )
        }
    }

    fun playDownload(download: DownloadItem) {
        val cw = _uiState.value.continueWatching?.takeIf {
            it.videoUrl == download.originalVideoUrl || it.videoUrl == download.localFilePath
        }
        val savedPos = cw?.positionMs ?: 0L
        val savedDur = cw?.durationMs ?: 0L
        _uiState.update {
            it.copy(
                activePlayback = PlaybackTarget(
                    videoUrl = download.localFilePath,
                    title = download.title,
                    coverUrl = download.coverUrl,
                    year = download.year,
                    type = download.type,
                    initialPositionMs = savedPos,
                    initialDurationMs = savedDur
                )
            )
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(activePlayback = null) }
    }

    fun setThemeColor(color: AppThemeColor) {
        repository.saveSyncThemeColor(color.id)
        _uiState.update { it.copy(themeColor = color) }
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
