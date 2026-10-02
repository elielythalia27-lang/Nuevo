package com.example

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.BottomBarScrollBehavior
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.data.model.ThemeMode
import com.example.ui.components.AppBottomNav
import kotlinx.coroutines.launch
import com.example.ui.components.CustomToastHost
import com.example.ui.components.PermissionRequestDialog
import com.example.ui.components.VpnBlockedScreen
import com.example.ui.screens.AjustesScreen
import com.example.ui.screens.DescargasScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.AppThemeColor
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.NotificationUtils
import com.example.utils.PermissionHelper
import com.example.utils.VpnProxyDetector
import com.example.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    private var isPlayerActive: Boolean = false
    private var onPlayerVolumeKey: ((Int) -> Unit)? = null

    fun registerPlayerVolumeHandler(handler: (Int) -> Unit) {
        isPlayerActive = true
        onPlayerVolumeKey = handler
    }

    fun unregisterPlayerVolumeHandler() {
        isPlayerActive = false
        onPlayerVolumeKey = null
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isPlayerActive && onPlayerVolumeKey != null) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        onPlayerVolumeKey?.invoke(1)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        onPlayerVolumeKey?.invoke(-1)
                        return true
                    }
                }
            } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                    event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                ) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationUtils.initNotificationChannels(this)

        val syncPrefs = getSharedPreferences("theme_sync_prefs", Context.MODE_PRIVATE)
        val savedThemeMode = syncPrefs.getString("theme_mode", null)
        val savedColor = syncPrefs.getString("theme_color", null)
        val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val initialIsDark = when (savedThemeMode) {
            ThemeMode.DARK.name -> true
            ThemeMode.LIGHT.name -> false
            ThemeMode.SYSTEM.name -> systemIsDark
            else -> {
                if (savedColor != null && savedColor.startsWith("light_")) false
                else if (savedColor != null && savedColor.startsWith("dark_")) true
                else systemIsDark
            }
        }
        val windowBgColor = if (initialIsDark) android.graphics.Color.parseColor("#0B1120") else android.graphics.Color.parseColor("#F8FAFC")
        window.setBackgroundDrawable(ColorDrawable(windowBgColor))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setTaskDescription(
                    ActivityManager.TaskDescription.Builder()
                        .setPrimaryColor(android.graphics.Color.WHITE)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                setTaskDescription(
                    ActivityManager.TaskDescription(
                        getString(R.string.app_name),
                        null,
                        android.graphics.Color.WHITE
                    )
                )
            }
        } catch (_: Exception) {
        }

        val skipSplash = intent.getBooleanExtra("skip_splash", false)
        val initialTab = intent.getIntExtra("initial_tab", 0)

        setContent {
            val homeViewModel: HomeViewModel = viewModel()
            val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

            val isDark = when (uiState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            val context = LocalContext.current

            LaunchedEffect(isDark) {
                val windowBgColor = if (isDark) android.graphics.Color.parseColor("#0B1120") else android.graphics.Color.parseColor("#F8FAFC")
                (context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(windowBgColor))
            }

            val handleThemeModeChange: (ThemeMode) -> Unit = { newMode ->
                if (newMode != uiState.themeMode) {
                    val systemIsDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val willBeDark = when (newMode) {
                        ThemeMode.SYSTEM -> systemIsDark
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                    }

                    val activity = context as? Activity
                    if (activity != null) {
                        val currentUiMode = activity.resources.configuration.uiMode
                        val newUi = when (newMode) {
                            ThemeMode.DARK -> (currentUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                            ThemeMode.LIGHT -> (currentUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                            ThemeMode.SYSTEM -> currentUiMode
                        }
                        activity.resources.configuration.uiMode = newUi
                    }

                    homeViewModel.setThemeMode(newMode, willBeDark)
                }
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
                            skipSplash = skipSplash,
                            initialTab = initialTab,
                            onThemeModeChange = handleThemeModeChange,
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
    skipSplash: Boolean = false,
    initialTab: Int = 0,
    onThemeModeChange: (ThemeMode) -> Unit = {},
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

    val activity = LocalContext.current as? MainActivity

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
            initialDurationMs = activePlayback.initialDurationMs,
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
            startDestination = "main",
            modifier = modifier
        ) {
            composable("main") {
                val pagerState = rememberPagerState(
                    initialPage = initialTab,
                    pageCount = { 3 }
                )
                var showExitAppDialog by remember { mutableStateOf(false) }
                var lastSettledPage by remember { mutableIntStateOf(pagerState.settledPage) }

                val density = LocalDensity.current
                val barBehavior = remember { BottomBarScrollBehavior(with(density) { 24.dp.toPx() }) }

                // Synchronize page switching with bottom navigation visibility
                LaunchedEffect(pagerState.isScrollInProgress) {
                    barBehavior.isChangingPage = pagerState.isScrollInProgress
                    if (pagerState.isScrollInProgress) {
                        barBehavior.show()
                    }
                }

                LaunchedEffect(pagerState.currentPage, pagerState.settledPage, pagerState.targetPage) {
                    barBehavior.show()
                }

                BackHandler {
                    if (pagerState.settledPage != 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = 0,
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                            )
                        }
                    } else {
                        showExitAppDialog = true
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nestedScroll(barBehavior)
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
                                    onLayoutModeChange = { viewModel.setCatalogLayoutMode(it) },
                                    barBehavior = barBehavior,
                                    isCurrentPage = !pagerState.isScrollInProgress && pagerState.settledPage == 0,
                                    onBottomNavVisibilityChange = { if (it) barBehavior.show() },
                                    onPauseDownload = { viewModel.pauseDownload(it) },
                                    onResumeDownload = { viewModel.resumeDownload(it) },
                                    onCancelDownload = { viewModel.cancelDownload(it) }
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
                                    },
                                    barBehavior = barBehavior,
                                    isCurrentPage = !pagerState.isScrollInProgress && pagerState.settledPage == 1,
                                    onBottomNavVisibilityChange = { if (it) barBehavior.show() }
                                )
                            }

                            // Section 2: Ajustes
                            2 -> {
                                AjustesScreen(
                                    isDarkTheme = isDark,
                                    onDarkThemeChange = { viewModel.setDarkTheme(it) },
                                    themeMode = uiState.themeMode,
                                    onThemeModeChange = onThemeModeChange,
                                    themeColor = uiState.themeColor,
                                    onThemeColorChange = { viewModel.setThemeColor(it) },
                                    defaultFilterType = uiState.selectedType,
                                    onDefaultFilterTypeChange = { viewModel.setDefaultFilterType(it) },
                                    sortOption = uiState.sortOption,
                                    onSortOptionChange = { viewModel.setSortOption(it) },
                                    maxConcurrentDownloads = uiState.maxConcurrentDownloads,
                                    onMaxConcurrentDownloadsChange = { viewModel.setMaxConcurrentDownloads(it) },
                                    catalogLayoutMode = uiState.catalogLayoutMode,
                                    onCatalogLayoutModeChange = { viewModel.setCatalogLayoutMode(it) },
                                    downloadFolderName = uiState.downloadFolderName,
                                    downloadFolderPath = uiState.downloadFolderPath,
                                    onDownloadFolderChange = { name, path ->
                                        viewModel.setDownloadFolder(name, path)
                                    },
                                    wifiOnly = uiState.wifiOnly,
                                    onWifiOnlyChange = { viewModel.setWifiOnly(it) },
                                    barBehavior = barBehavior,
                                    isCurrentPage = !pagerState.isScrollInProgress && pagerState.settledPage == 2,
                                    onBottomNavVisibilityChange = { if (it) barBehavior.show() }
                                )
                            }
                        }
                    }

                    // Floating Modern Navigation Pill Bar over the content
                    AppBottomNav(
                        currentPage = pagerState.targetPage,
                        onNavigate = { targetPage ->
                            barBehavior.show()
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = targetPage,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        downloadsCount = uiState.activeDownloadsCount,
                        isDarkTheme = isDark,
                        isVisible = barBehavior.isVisible,
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

                    if (showExitAppDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitAppDialog = false },
                            title = {
                                Text(
                                    text = "¿Salir de la aplicación?",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Text(
                                    text = "¿Estás seguro de que deseas salir de PelisFree?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showExitAppDialog = false
                                        (context as? Activity)?.finish()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Salir", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { showExitAppDialog = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancelar", fontWeight = FontWeight.Medium)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        )
                    }
                }
            }
        }
    }
}
