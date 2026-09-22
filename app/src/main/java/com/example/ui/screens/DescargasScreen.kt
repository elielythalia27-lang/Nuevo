package com.example.ui.screens

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.formatByteSize
import com.example.ui.components.SleekLinearProgressBar
import com.example.ui.components.shimmerEffect
import com.example.utils.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescargasScreen(
    downloads: List<DownloadItem>,
    maxConcurrentDownloads: Int = 3,
    onPlayOffline: (DownloadItem) -> Unit,
    onPauseDownload: (DownloadItem) -> Unit,
    onResumeDownload: (DownloadItem) -> Unit,
    onCancelDownload: (DownloadItem) -> Unit,
    onDeleteMultiple: (List<DownloadItem>) -> Unit = {},
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onCancelAll: () -> Unit = {},
    onForceStartPending: (DownloadItem) -> Unit = {},
    onExploreClick: () -> Unit,
    isDarkTheme: Boolean = true,
    onRequestPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationsEnabled by remember {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationsEnabled = PermissionHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationsEnabled = PermissionHelper.hasNotificationPermission(context)
        if (!isGranted) {
            PermissionHelper.openNotificationSettings(context)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var itemToCancel by remember { mutableStateOf<DownloadItem?>(null) }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var showCancelAllConfirm by remember { mutableStateOf(false) }

    val screenBg = if (isDarkTheme) Color(0xFF070B18) else Color(0xFFF1F5F9)
    val cardBg = if (isDarkTheme) Color(0xFF10192C) else Color.White
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFA0AEC0)
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF334155)
    val tabBg = if (isDarkTheme) Color(0xFF10182C) else Color(0xFFE2E8F0)

    val activeList = remember(downloads) {
        downloads.filter {
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.PAUSED ||
            it.status == DownloadStatus.PENDING
        }
    }

    val downloadedList = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }
    }

    // Storage capacity info (safely calculated with fallback)
    val freeStorageText = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
            formatByteSize(bytesAvailable)
        } catch (e: Exception) {
            "Almacenamiento disponible"
        }
    }

    // Reactive download speed ticker calculated directly from active list updates
    val stabilizedTotalSpeed = remember(activeList) {
        val hasDownloading = activeList.any { it.status == DownloadStatus.DOWNLOADING }
        if (hasDownloading) {
            activeList
                .filter { it.status == DownloadStatus.DOWNLOADING }
                .sumOf { it.speedBytesPerSec }
        } else {
            0L
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "tab_pulse")
    val tabPulseScale by pulseTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tab_pulse_scale"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = screenBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.2f else 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Centro de Descargas",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            color = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Aviso interactivo si las notificaciones están desactivadas
            if (!hasNotificationsEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDarkTheme) Color(0xFF2C1618) else Color(0xFFFFEBEE),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDarkTheme) Color(0xFFE53935).copy(alpha = 0.4f) else Color(0xFFFFCDD2)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notificaciones desactivadas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDarkTheme) Color.White else Color(0xFFB71C1C)
                            )
                            Text(
                                text = "Actívalas para ver la velocidad y aviso al finalizar.",
                                fontSize = 11.5.sp,
                                color = if (isDarkTheme) Color(0xFFEF9A9A) else Color(0xFFC62828)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val isGranted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!isGranted) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        PermissionHelper.openNotificationSettings(context)
                                    }
                                } else {
                                    PermissionHelper.openNotificationSettings(context)
                                }
                                onRequestPermissions()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935)
                            )
                        ) {
                            Text("Activar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Modern 2-Tab Navigation Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = tabBg
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    // Tab 0: Activas & Pausadas
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_active_downloads"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                val isDownloading = activeList.any { it.status == DownloadStatus.DOWNLOADING }
                                if (isDownloading) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .scale(tabPulseScale)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else textSecondary
                                    )
                                }
                                Text(
                                    text = "Activas (${activeList.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else textSecondary
                                )
                            }
                        }
                    )

                    // Tab 1: Descargadas (Completadas)
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_completed_downloads"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 1) Color(0xFF10B981) else textSecondary
                                )
                                Text(
                                    text = "Completadas (${downloadedList.size})",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = if (selectedTab == 1) Color(0xFF10B981) else textSecondary
                                )
                            }
                        }
                    )
                }
            }

            // Animated Tab Content Transition
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                label = "tab_content_anim",
                modifier = Modifier.fillMaxSize()
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> {
                        // TAB 1: DESCARGAS ACTIVAS, PAUSADAS Y EN COLA
                        ActiveDownloadsTab(
                            activeList = activeList,
                            maxConcurrentDownloads = maxConcurrentDownloads,
                            totalSpeed = stabilizedTotalSpeed,
                            onPause = onPauseDownload,
                            onResume = onResumeDownload,
                            onCancel = { itemToCancel = it },
                            onPauseAll = onPauseAll,
                            onResumeAll = onResumeAll,
                            onCancelAll = { showCancelAllConfirm = true },
                            onForceStart = onForceStartPending,
                            onExploreClick = onExploreClick,
                            isDarkTheme = isDarkTheme,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                    1 -> {
                        // TAB 2: PELÍCULAS DESCARGADAS (OFFLINE)
                        DownloadedTab(
                            downloadedList = downloadedList,
                            onPlayOffline = onPlayOffline,
                            onDelete = { itemToDelete = it },
                            onDeleteMultiple = onDeleteMultiple,
                            onExploreClick = onExploreClick,
                            isDarkTheme = isDarkTheme,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }
        }

        // Cancel Download Dialog
        itemToCancel?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToCancel = null },
                containerColor = cardBg,
                shape = RoundedCornerShape(16.dp),
                titleContentColor = textPrimary,
                textContentColor = textSecondary,
                title = { Text("Cancelar descarga", fontWeight = FontWeight.Bold) },
                text = {
                    Text("¿Deseas interrumpir la descarga de «${item.title}»? Se descartarán los datos temporales transferidos.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onCancelDownload(item)
                            itemToCancel = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar descarga", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { itemToCancel = null },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Conservar", color = textSecondary)
                    }
                }
            )
        }

        // Delete Completed Movie Dialog
        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                containerColor = cardBg,
                shape = RoundedCornerShape(16.dp),
                titleContentColor = textPrimary,
                textContentColor = textSecondary,
                title = { Text("Eliminar archivo", fontWeight = FontWeight.Bold) },
                text = {
                    Text("¿Deseas eliminar «${item.title}» del almacenamiento del dispositivo? Esta acción liberará espacio en memoria.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onCancelDownload(item)
                            itemToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Eliminar", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { itemToDelete = null },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Conservar", color = textSecondary)
                    }
                }
            )
        }

        // Cancel All Downloads Dialog
        if (showCancelAllConfirm) {
            AlertDialog(
                onDismissRequest = { showCancelAllConfirm = false },
                containerColor = cardBg,
                shape = RoundedCornerShape(16.dp),
                titleContentColor = textPrimary,
                textContentColor = textSecondary,
                title = { Text("Cancelar transferencias", fontWeight = FontWeight.Bold) },
                text = {
                    Text("¿Deseas interrumpir todas las descargas activas y en espera? Se eliminarán los datos temporales asociados.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onCancelAll()
                            showCancelAllConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar todas", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCancelAllConfirm = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continuar", color = textSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun ActiveDownloadsTab(
    activeList: List<DownloadItem>,
    maxConcurrentDownloads: Int,
    totalSpeed: Long,
    onPause: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onCancelAll: () -> Unit = {},
    onForceStart: (DownloadItem) -> Unit,
    onExploreClick: () -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val downloadingOrPaused = activeList.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED }
    val pendingItems = activeList.filter { it.status == DownloadStatus.PENDING }
    val failedItems = activeList.filter { it.status == DownloadStatus.FAILED }

    val hasActiveDownloads = activeList.any { it.status == DownloadStatus.DOWNLOADING }
    val hasPausedOrPending = activeList.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING || it.status == DownloadStatus.FAILED }

    if (activeList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.15f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sin transferencias activas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Las descargas que inicies se mostrarán aquí con velocidad en tiempo real y controles de gestión.",
                    fontSize = 13.5.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onExploreClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explorar catálogo", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("active_downloads_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Speed indicator card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cardBorder),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.2f else 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Velocidad de descarga",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                                val speedStr = if (totalSpeed > 0L) {
                                    if (totalSpeed >= 1024 * 1024) String.format(java.util.Locale.US, "%.1f MB/s", totalSpeed / (1024.0 * 1024.0))
                                    else "${totalSpeed / 1024} KB/s"
                                } else {
                                    if (activeList.any { it.status == DownloadStatus.DOWNLOADING }) "Iniciando..." else "En pausa"
                                }
                                Text(
                                    text = speedStr,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalSpeed > 0L) Color(0xFF10B981) else textPrimary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.15f else 0.12f)
                        ) {
                            Text(
                                text = "Límite: $maxConcurrentDownloads simultáneas",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Bulk actions: Pausar todo, Reanudar todo, Cancelar todo
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pausar todo
                    Surface(
                        onClick = onPauseAll,
                        enabled = hasActiveDownloads,
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasActiveDownloads) {
                            Color(0xFFF59E0B).copy(alpha = if (isDarkTheme) 0.18f else 0.12f)
                        } else {
                            if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFE2E8F0).copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (hasActiveDownloads) Color(0xFFF59E0B).copy(alpha = 0.4f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_pause_all")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pausar todo",
                                tint = if (hasActiveDownloads) Color(0xFFF59E0B) else textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pausar todo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasActiveDownloads) {
                                    if (isDarkTheme) Color.White else Color(0xFFB45309)
                                } else textSecondary.copy(alpha = 0.4f),
                                maxLines = 1
                            )
                        }
                    }

                    // Reanudar todo
                    Surface(
                        onClick = onResumeAll,
                        enabled = hasPausedOrPending,
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasPausedOrPending) {
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.12f)
                        } else {
                            if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFE2E8F0).copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (hasPausedOrPending) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_resume_all")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Reanudar todo",
                                tint = if (hasPausedOrPending) MaterialTheme.colorScheme.primary else textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reanudar todo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasPausedOrPending) {
                                    if (isDarkTheme) Color.White else MaterialTheme.colorScheme.primary
                                } else textSecondary.copy(alpha = 0.4f),
                                maxLines = 1
                            )
                        }
                    }

                    // Cancelar todo
                    Surface(
                        onClick = onCancelAll,
                        enabled = activeList.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeList.isNotEmpty()) {
                            Color(0xFFEF4444).copy(alpha = if (isDarkTheme) 0.18f else 0.12f)
                        } else {
                            if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFE2E8F0).copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeList.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.4f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_cancel_all")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar todo",
                                tint = if (activeList.isNotEmpty()) Color(0xFFEF4444) else textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Cancelar todo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeList.isNotEmpty()) {
                                    if (isDarkTheme) Color.White else Color(0xFFB91C1C)
                                } else textSecondary.copy(alpha = 0.4f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Downloading & Paused list
            if (downloadingOrPaused.isNotEmpty()) {
                items(downloadingOrPaused, key = { it.id }) { item ->
                    ActiveDownloadingCard(
                        item = item,
                        onPause = { onPause(item) },
                        onResume = { onResume(item) },
                        onCancel = { onCancel(item) },
                        isDarkTheme = isDarkTheme,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }
            }

            // Pending Queue
            if (pendingItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "EN COLA DE ESPERA (${pendingItems.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B),
                            letterSpacing = 1.sp
                        )
                    }
                }

                items(pendingItems, key = { it.id }) { item ->
                    val queueIndex = pendingItems.indexOf(item) + 1
                    PendingQueueCard(
                        item = item,
                        queueIndex = queueIndex,
                        onForceStart = { onForceStart(item) },
                        onCancel = { onCancel(item) },
                        isDarkTheme = isDarkTheme,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }

                if (failedItems.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CON ERROR (${failedItems.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    items(failedItems, key = { it.id }) { item ->
                        ActiveDownloadingCard(
                            item = item,
                            onPause = {},
                            onResume = { onForceStart(item) },
                            onCancel = { onCancel(item) },
                            isDarkTheme = isDarkTheme,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedTab(
    downloadedList: List<DownloadItem>,
    onPlayOffline: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
    onDeleteMultiple: (List<DownloadItem>) -> Unit,
    onExploreClick: () -> Unit,
    isDarkTheme: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    if (downloadedList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = if (isDarkTheme) 0.15f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Biblioteca sin contenido",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Los títulos que descargues se conservarán aquí para su reproducción sin conexión en cualquier momento.",
                    fontSize = 13.5.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onExploreClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Movie, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explorar catálogo", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Sticky multi-selection toolbar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF131D31) else Color(0xFFF1F5F9)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = selectedIds.size == downloadedList.size && downloadedList.isNotEmpty()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedIds = if (allSelected) emptySet() else downloadedList.map { it.id }.toSet()
                                }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (allSelected) "Deseleccionar todo" else "Seleccionar todo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (allSelected) "Deseleccionar todo" else "Seleccionar todo",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { showBatchDeleteDialog = true },
                                enabled = selectedIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    disabledContainerColor = Color(0xFFEF4444).copy(alpha = 0.35f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eliminar", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            TextButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedIds = emptySet()
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Listo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("downloaded_movies_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row when not in selection mode
                if (!isSelectionMode) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${downloadedList.size} ${if (downloadedList.size == 1) "descarga" else "descargas"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                            Surface(
                                onClick = { isSelectionMode = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = "Seleccionar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Seleccionar",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

            items(downloadedList, key = { it.id }) { item ->
                val isSelected = selectedIds.contains(item.id)
                DownloadedMovieCard(
                    item = item,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onToggleSelect = {
                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                    },
                    onLongPress = {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedIds = setOf(item.id)
                        } else {
                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                        }
                    },
                    onPlay = {
                        if (isSelectionMode) {
                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                        } else {
                            onPlayOffline(item)
                        }
                    },
                    onDelete = { onDelete(item) },
                    isDarkTheme = isDarkTheme,
                    cardBg = cardBg,
                    cardBorder = if (isSelectionMode && isSelected) MaterialTheme.colorScheme.primary else cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
        }
    }
    }

    // Batch deletion confirmation dialog
    if (showBatchDeleteDialog && selectedIds.isNotEmpty()) {
        val itemsToDelete = downloadedList.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(16.dp),
            titleContentColor = textPrimary,
            textContentColor = textSecondary,
            title = { Text("Eliminar elementos seleccionados", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Deseas eliminar ${itemsToDelete.size} ${if (itemsToDelete.size == 1) "archivo" else "archivos"} de la biblioteca? Los datos se borrarán del almacenamiento del dispositivo."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMultiple(itemsToDelete)
                        selectedIds = emptySet()
                        isSelectionMode = false
                        showBatchDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Eliminar (${itemsToDelete.size})", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancelar", color = textSecondary)
                }
            }
        )
    }
}

/**
 * Card for an actively downloading or paused item.
 */
@Composable
fun ActiveDownloadingCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    isDarkTheme: Boolean = true,
    cardBg: Color = Color(0xFF10192C),
    cardBorder: Color = Color(0xFF1E293B),
    textPrimary: Color = Color.White,
    textSecondary: Color = Color(0xFF94A3B8),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPaused = item.status == DownloadStatus.PAUSED
    val isFailed = item.status == DownloadStatus.FAILED
    val animatedProgress by animateFloatAsState(
        targetValue = item.progress / 100f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "download_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        border = BorderStroke(
            1.dp,
            when {
                isFailed -> Color(0xFFEF4444).copy(alpha = 0.5f)
                isPaused -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                else -> cardBorder
            }
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 96.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDarkTheme) Color(0xFF161F33) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.coverUrl)
                            .crossfade(150)
                            .size(240, 320)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info & Controls
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isFailed -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                isPaused -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = when {
                                    isFailed -> "Error"
                                    isPaused -> "En Pausa"
                                    else -> "Descargando"
                                },
                                color = when {
                                    isFailed -> Color(0xFFEF4444)
                                    isPaused -> Color(0xFFF59E0B)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val displayTitle = remember(item.title, item.year) {
                        val y = item.year.trim()
                        if (y.isNotEmpty() && !item.title.contains("($y)")) {
                            "${item.title} ($y)"
                        } else {
                            item.title
                        }
                    }

                    Text(
                        text = displayTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Speed and progress percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isFailed) "Error de conexión" else item.formattedSpeed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isFailed -> Color(0xFFEF4444)
                                isPaused -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            text = "${item.progress}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    SleekLinearProgressBar(
                        progress = animatedProgress,
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp),
                        color = when {
                            isFailed -> Color(0xFFEF4444)
                            isPaused -> Color(0xFFF59E0B)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // ETA & size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.formattedEta,
                            fontSize = 10.sp,
                            color = textSecondary
                        )
                        Text(
                            text = "${item.formattedDownloadedSize} / ${item.formattedTotalSize}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Pause / Resume and Cancel Action Buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        isFailed -> {
                            // Retry Button
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reintentar descarga",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        isPaused -> {
                            // Resume Button
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reanudar descarga",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        else -> {
                            // Pause Button
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pausar descarga",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Cancel Button
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancelar descarga",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendingQueueCard(
    item: DownloadItem,
    queueIndex: Int,
    onForceStart: () -> Unit = {},
    onCancel: () -> Unit,
    isDarkTheme: Boolean = true,
    cardBg: Color = Color(0xFF10192C),
    cardBorder: Color = Color(0xFF1E293B),
    textPrimary: Color = Color.White,
    textSecondary: Color = Color(0xFF94A3B8),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDarkTheme) Color(0xFF161F33) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(150)
                        .size(200, 260)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shimmerEffect(RoundedCornerShape(8.dp), isDark = isDarkTheme)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "En cola #$queueIndex",
                        color = Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val displayTitle = remember(item.title, item.year) {
                    val y = item.year.trim()
                    if (y.isNotEmpty() && !item.title.contains("($y)")) {
                        "${item.title} ($y)"
                    } else {
                        item.title
                    }
                }
                Text(
                    text = displayTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Esperando turno automático...",
                    fontSize = 11.sp,
                    color = textSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Cancelar",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadedMovieCard(
    item: DownloadItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    isDarkTheme: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
    cardBg: Color = Color(0xFF10192C),
    cardBorder: Color = Color(0xFF1E293B),
    textPrimary: Color = Color.White,
    textSecondary: Color = Color(0xFF94A3B8),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayTitle = remember(item.title, item.year) {
        val y = item.year.trim()
        if (y.isNotEmpty() && !item.title.contains("($y)")) {
            "${item.title} ($y)"
        } else {
            item.title
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onPlay()
                },
                onLongClick = {
                    onLongPress()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelectionMode && isSelected) 1.5.dp else 1.dp,
            color = if (isSelectionMode && isSelected) MaterialTheme.colorScheme.primary else cardBorder
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectionMode && isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.12f else 0.06f)
            } else cardBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Multi-selection indicator
            if (isSelectionMode) {
                Box(
                    modifier = Modifier.padding(end = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Seleccionada" else "No seleccionada",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Thumbnail poster (without play overlay)
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 92.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDarkTheme) Color(0xFF161F33) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(150)
                        .size(240, 320)
                        .build(),
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shimmerEffect(RoundedCornerShape(10.dp), isDark = isDarkTheme)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                // Size info only (Completada badge removed as requested)
                Text(
                    text = item.formattedTotalSize,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary
                )
            }

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
