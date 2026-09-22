package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.data.model.ThemeMode
import com.example.ui.components.AppBottomNav
import com.example.ui.components.ThemeWaveData
import com.example.ui.components.ThemeWaveOverlay
import kotlinx.coroutines.launch
import com.example.ui.components.CustomToastHost
import com.example.ui.components.PermissionRequestDialog
import com.example.ui.components.VpnBlockedScreen
import com.example.ui.screens.AjustesScreen
import com.example.ui.screens.DescargasScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.AppThemeColor
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.PermissionHelper
import com.example.utils.VpnProxyDetector
import com.example.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val homeViewModel: HomeViewModel = viewModel()
            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

            val isDark = when (uiState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            MyApplicationTheme(
                darkTheme = isDark,
                themeColor = uiState.themeColor
            ) {
                @OptIn(ExperimentalFoundationApi::class)
                CompositionLocalProvider(
                    LocalOverscrollConfiguration provides null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        MainAppNavigation(
                            viewModel = homeViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        CustomToastHost(isDarkTheme = isDark)
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppNavigation(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val requiredPermissions = remember { PermissionHelper.getRequiredAppPermissions() }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingDownloadPelicula by remember { mutableStateOf<Pelicula?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val granted = PermissionHelper.hasAllRequiredPermissions(context)
        if (granted) {
            showPermissionDialog = false
            pendingDownloadPelicula?.let {
                viewModel.startDownload(it)
                pendingDownloadPelicula = null
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasAllRequiredPermissions(context)) {
            showPermissionDialog = true
        }
    }

    var retryKey by remember { mutableStateOf(0) }
    val vpnStatus by produceState(
        initialValue = remember { VpnProxyDetector.checkStatus(context) },
        context,
        retryKey
    ) {
        value = VpnProxyDetector.checkStatus(context)
        VpnProxyDetector.observeVpnAndProxy(context).collect {
            value = it
        }
    }

    val isDark = when (uiState.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val activePlayback = uiState.activePlayback

    if (vpnStatus.isBlocked) {
        VpnBlockedScreen(
            status = vpnStatus,
            onRetryCheck = {
                retryKey++
            },
            isDark = isDark
        )
    } else if (activePlayback != null) {
        PlayerScreen(
            videoUrl = activePlayback.videoUrl,
            title = activePlayback.title,
            coverUrl = activePlayback.coverUrl,
            year = activePlayback.year,
            type = activePlayback.type,
            initialPositionMs = activePlayback.initialPositionMs,
            onBack = { viewModel.closePlayer() },
            onSavePosition = { pos, dur ->
                viewModel.savePlaybackPosition(
                    videoUrl = activePlayback.videoUrl,
                    title = activePlayback.title,
                    coverUrl = activePlayback.coverUrl,
                    year = activePlayback.year,
                    type = activePlayback.type,
                    positionMs = pos,
                    durationMs = dur
                )
            }
        )
    } else {
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = modifier
        ) {
            composable("splash") {
                SplashScreen(
                    isDarkTheme = isDark,
                    onTimeout = {
                        navController.navigate("main") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("main") {
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { 3 }
                )

                var themeWaveData by remember { mutableStateOf<ThemeWaveData?>(null) }
                var pendingThemeMode by remember { mutableStateOf<ThemeMode?>(null) }
                val currentPrimary = MaterialTheme.colorScheme.primary

                BackHandler(enabled = pagerState.currentPage != 0) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            page = 0,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapAnimationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        when (pageIndex) {
                            // Section 0: Catálogo
                            0 -> {
                                HomeScreen(
                                    uiState = uiState,
                                    isDarkTheme = isDark,
                                    onSearchChange = { viewModel.onSearchQueryChange(it) },
                                    onTypeSelect = { viewModel.onTypeSelected(it) },
                                    onClearFilters = { viewModel.clearFilters() },
                                    onPlayPelicula = { pelicula, pos ->
                                        viewModel.playPelicula(pelicula, pos)
                                    },
                                    onDownloadPelicula = { pelicula ->
                                        if (PermissionHelper.hasAllRequiredPermissions(context)) {
                                            viewModel.startDownload(pelicula)
                                        } else {
                                            pendingDownloadPelicula = pelicula
                                            showPermissionDialog = true
                                        }
                                    },
                                    onRefresh = { viewModel.loadPeliculas(forceRefresh = true) },
                                    onOpenSettings = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                page = 2,
                                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    },
                                    onDismissContinueWatching = { viewModel.clearContinueWatching() },
                                    onLayoutModeChange = { viewModel.setCatalogLayoutMode(it) }
                                )
                            }

                            // Section 1: Descargas (Activas con Pausa/Reanudar/Cancelar y Notificaciones, y Offline)
                            1 -> {
                                DescargasScreen(
                                    downloads = uiState.downloads,
                                    maxConcurrentDownloads = uiState.maxConcurrentDownloads,
                                    onPlayOffline = { download ->
                                        viewModel.playDownload(download)
                                    },
                                    onPauseDownload = { download ->
                                        viewModel.pauseDownload(download)
                                    },
                                    onResumeDownload = { download ->
                                        viewModel.resumeDownload(download)
                                    },
                                    onCancelDownload = { download ->
                                        viewModel.cancelDownload(download)
                                    },
                                    onDeleteMultiple = { items ->
                                        viewModel.deleteMultipleDownloads(items)
                                    },
                                    onPauseAll = { viewModel.pauseAllDownloads() },
                                    onResumeAll = { viewModel.resumeAllDownloads() },
                                    onCancelAll = { viewModel.cancelAllDownloads() },
                                    onForceStartPending = { download ->
                                        viewModel.forceStartPendingDownload(download)
                                    },
                                    onExploreClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                page = 0,
                                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    },
                                    isDarkTheme = isDark,
                                    onRequestPermissions = {
                                        showPermissionDialog = true
                                    }
                                )
                            }

                            // Section 2: Ajustes
                            2 -> {
                                AjustesScreen(
                                    isDarkTheme = isDark,
                                    onDarkThemeChange = { viewModel.setDarkTheme(it) },
                                    themeMode = uiState.themeMode,
                                    onThemeModeChange = { newMode, tapOffset ->
                                        if (newMode == uiState.themeMode) {
                                            // Ya está seleccionado este modo, no hacer nada
                                            return@AjustesScreen
                                        }

                                        val systemIsDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                                        val willBeDark = when (newMode) {
                                            ThemeMode.SYSTEM -> systemIsDark
                                            ThemeMode.DARK -> true
                                            ThemeMode.LIGHT -> false
                                        }

                                        // Si el color visual efectivo (isDark) no cambia, aplicar directamente sin onda
                                        val visualThemeChanges = (willBeDark != isDark)

                                        if (tapOffset != null && visualThemeChanges) {
                                            pendingThemeMode = newMode
                                            themeWaveData = ThemeWaveData(
                                                origin = tapOffset,
                                                targetIsDark = willBeDark,
                                                accentColor = currentPrimary
                                            )
                                        } else {
                                            viewModel.setThemeMode(newMode, willBeDark)
                                        }
                                    },
                                    themeColor = uiState.themeColor,
                                    onThemeColorChange = { viewModel.setThemeColor(it) },
                                    defaultFilterType = uiState.selectedType,
                                    onDefaultFilterTypeChange = { viewModel.setDefaultFilterType(it) },
                                    sortOption = uiState.sortOption,
                                    onSortOptionChange = { viewModel.setSortOption(it) },
                                    onClearCache = { viewModel.clearCache() },
                                    maxConcurrentDownloads = uiState.maxConcurrentDownloads,
                                    onMaxConcurrentDownloadsChange = { viewModel.setMaxConcurrentDownloads(it) },
                                    catalogLayoutMode = uiState.catalogLayoutMode,
                                    onCatalogLayoutModeChange = { viewModel.setCatalogLayoutMode(it) },
                                    downloadFolderName = uiState.downloadFolderName,
                                    downloadFolderPath = uiState.downloadFolderPath,
                                    onDownloadFolderChange = { name, path ->
                                        viewModel.setDownloadFolder(name, path)
                                    }
                                )
                            }
                        }
                    }

                    // Wave overlay triggered when changing theme mode
                    ThemeWaveOverlay(
                        waveData = themeWaveData,
                        onWaveHalfway = {
                            pendingThemeMode?.let { mode ->
                                val systemIsDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                                val willBeDark = when (mode) {
                                    ThemeMode.SYSTEM -> systemIsDark
                                    ThemeMode.DARK -> true
                                    ThemeMode.LIGHT -> false
                                }
                                viewModel.setThemeMode(mode, willBeDark)
                            }
                        },
                        onWaveFinished = {
                            themeWaveData = null
                            pendingThemeMode = null
                        }
                    )

                    // Floating Modern Navigation Pill Bar over the content
                    val downCount = uiState.downloads.count { it.status == DownloadStatus.DOWNLOADING }
                    AppBottomNav(
                        currentPage = pagerState.currentPage,
                        onNavigate = { targetPage ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = targetPage,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        downloadsCount = downCount,
                        isDarkTheme = isDark,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    if (showPermissionDialog) {
                        PermissionRequestDialog(
                            isDark = isDark,
                            onGrantClick = {
                                permissionLauncher.launch(requiredPermissions.toTypedArray())
                                showPermissionDialog = false
                            },
                            onDismiss = {
                                showPermissionDialog = false
                                pendingDownloadPelicula = null
                            }
                        )
                    }
                }
            }
        }
    }
}
