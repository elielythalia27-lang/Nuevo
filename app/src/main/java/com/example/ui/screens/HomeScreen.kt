package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.ElielFont
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.ui.components.ContinueWatchingBanner
import com.example.ui.components.FilterBar
import com.example.ui.components.PeliculaCard
import com.example.ui.components.PeliculaGridSkeleton
import com.example.ui.components.PeliculaListSkeleton
import com.example.ui.components.SleekDeterminateCircularProgress
import com.example.ui.components.shimmerEffect
import com.example.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    isDarkTheme: Boolean = true,
    onSearchChange: (String) -> Unit,
    onTypeSelect: (String) -> Unit,
    onClearFilters: () -> Unit,
    onPlayPelicula: (Pelicula, Long) -> Unit,
    onDownloadPelicula: (Pelicula) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissContinueWatching: () -> Unit,
    onLayoutModeChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPeliculaForSheet by remember { mutableStateOf<Pelicula?>(null) }
    var showTelegramDialog by remember { mutableStateOf(false) }

    val isDark = isDarkTheme
    val screenBg = MaterialTheme.colorScheme.background
    val titleTextColor = MaterialTheme.colorScheme.onBackground
    val subtitleTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val badgeBg = MaterialTheme.colorScheme.surfaceVariant

    val gridStateAll = rememberLazyGridState()
    val gridStateMovies = rememberLazyGridState()
    val gridStateVideos = rememberLazyGridState()

    val activeGridState = when (uiState.selectedType) {
        "MOVIE" -> gridStateMovies
        "VIDEO" -> gridStateVideos
        else -> gridStateAll
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = screenBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header: Clean title without any side icon, badge count and Telegram button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title (no side icon as requested)
                Text(
                    text = "Download Free",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = ElielFont,
                    color = titleTextColor,
                    letterSpacing = 0.4.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isListVisible = !uiState.isLoading && uiState.allPeliculas.isNotEmpty()

                    if (isListVisible) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = uiState.allPeliculas.size.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Telegram Channel Button with official Telegram logo
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { showTelegramDialog = true }
                            .testTag("telegram_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.telegram),
                            contentDescription = "Canal de Telegram",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            // Search Bar and Category Chips
            FilterBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = onSearchChange,
                selectedType = uiState.selectedType,
                onTypeSelected = onTypeSelect,
                totalCount = uiState.allPeliculas.size,
                filteredCount = uiState.filteredPeliculas.size,
                onClearFilters = onClearFilters,
                isDarkTheme = isDark
            )

            // Continue Watching Banner (if user has an in-progress video)
            AnimatedVisibility(
                visible = uiState.continueWatching != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.continueWatching?.let { watching ->
                    ContinueWatchingBanner(
                        item = watching,
                        isDarkTheme = isDark,
                        onResumeClick = {
                            val targetPelicula = uiState.allPeliculas.find { it.safeVideoUrl == watching.videoUrl }
                                ?: Pelicula(
                                    nombre = watching.title,
                                    url = watching.coverUrl,
                                    anio = watching.year,
                                    peli = watching.videoUrl,
                                    tp = watching.type
                                )
                            onPlayPelicula(targetPelicula, watching.positionMs)
                        },
                        onDismissClick = onDismissContinueWatching
                    )
                }
            }

            // Clean 2-column Movie & Video Grid (reverted from list as requested)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoading && uiState.allPeliculas.isEmpty() -> {
                        if (uiState.catalogLayoutMode == "LIST") {
                            PeliculaListSkeleton(
                                isDark = isDark,
                                itemCount = 6,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        } else {
                            val cols = if (uiState.catalogLayoutMode == "GRID_3") 3 else 2
                            PeliculaGridSkeleton(
                                columnsCount = cols,
                                isDark = isDark,
                                itemCount = if (cols == 3) 9 else 6,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // No connection or failure to load JSON catalog and no local items
                    uiState.allPeliculas.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                Text(
                                    text = "Sin conexión con el catálogo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = titleTextColor,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "No fue posible actualizar el catálogo. Comprueba tu conexión a la red e inténtalo de nuevo.",
                                    fontSize = 13.5.sp,
                                    color = subtitleTextColor,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                 Button(
                                    onClick = onRefresh,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reintentar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    uiState.filteredPeliculas.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = subtitleTextColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Sin coincidencias",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = titleTextColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No se encontraron títulos que coincidan con los filtros aplicados.",
                                    fontSize = 13.sp,
                                    color = subtitleTextColor,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onClearFilters,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Restablecer filtros", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    else -> {
                        val downloadsMap = remember(uiState.downloads) {
                            uiState.downloads.associateBy { it.id }
                        }

                        val isListMode = uiState.catalogLayoutMode == "LIST"
                        val columns = when (uiState.catalogLayoutMode) {
                            "GRID_3" -> GridCells.Fixed(3)
                            "LIST" -> GridCells.Fixed(1)
                            else -> GridCells.Fixed(2)
                        }

                        // Responsive catalog grid supporting 2-col, 3-col, and detailed list with independent scroll per tab
                        LazyVerticalGrid(
                            columns = columns,
                            state = activeGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("movies_grid"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (isListMode) 0.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isListMode) 12.dp else 16.dp)
                        ) {
                            items(
                                items = uiState.filteredPeliculas,
                                key = { it.id },
                                contentType = { "pelicula_card" }
                            ) { pelicula ->
                                val downloadItem = downloadsMap[pelicula.id]
                                PeliculaCard(
                                    pelicula = pelicula,
                                    downloadItem = downloadItem,
                                    isDarkTheme = isDark,
                                    isListMode = isListMode,
                                    onCardClick = {
                                        selectedPeliculaForSheet = pelicula
                                    },
                                    onDownloadClick = {
                                        onDownloadPelicula(pelicula)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Movie Details
    selectedPeliculaForSheet?.let { pelicula ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val downloadItem = uiState.downloads.find { it.id == pelicula.id }
        val sheetBg = MaterialTheme.colorScheme.surface
        val sheetTitleColor = MaterialTheme.colorScheme.onSurface
        val sheetBadgeBg = MaterialTheme.colorScheme.surfaceVariant
        val sheetBadgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant

        ModalBottomSheet(
            onDismissRequest = { selectedPeliculaForSheet = null },
            sheetState = sheetState,
            containerColor = sheetBg,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Header with Poster and Metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Elevated Poster Card
                    Surface(
                        modifier = Modifier
                            .size(width = 105.dp, height = 148.dp),
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = if (isDark) 4.dp else 6.dp,
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)),
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(pelicula.safeCoverUrl)
                                .crossfade(150)
                                .size(320, 440)
                                .build(),
                            contentDescription = pelicula.safeTitle,
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .shimmerEffect(RoundedCornerShape(14.dp), isDark = isDark)
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pelicula.safeTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = sheetTitleColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (pelicula.isVideo) Color(0xFFE50914) else MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (pelicula.isVideo) "YouTube" else "Película",
                                    fontSize = 11.sp,
                                    color = if (pelicula.isVideo) Color.White else MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            if (pelicula.isVideo) {
                                val creator = pelicula.youtuberName
                                if (creator.isNotEmpty() && !creator.equals("YouTube", ignoreCase = true)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = sheetBadgeBg
                                    ) {
                                        Text(
                                            text = creator,
                                            fontSize = 11.sp,
                                            color = sheetBadgeTextColor,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            } else if (pelicula.safeYear.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = sheetBadgeBg
                                ) {
                                    Text(
                                        text = pelicula.safeYear,
                                        fontSize = 11.sp,
                                        color = sheetBadgeTextColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (downloadItem != null && downloadItem.status == DownloadStatus.COMPLETED) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    val downloadedBadgeLabel = if (pelicula.isVideo) "Descargado" else "Descargada"
                                    Text(
                                        text = downloadedBadgeLabel,
                                        fontSize = 11.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                val isDownloaded = downloadItem?.status == DownloadStatus.COMPLETED
                val isDownloading = downloadItem?.status == DownloadStatus.DOWNLOADING
                val isPending = downloadItem?.status == DownloadStatus.PENDING
                val isPaused = downloadItem?.status == DownloadStatus.PAUSED

                // Primary Play Button: Only shown if NOT already downloaded
                if (!isDownloaded) {
                    Button(
                        onClick = {
                            val toPlay = selectedPeliculaForSheet
                            selectedPeliculaForSheet = null
                            if (toPlay != null) {
                                onPlayPelicula(toPlay, 0L)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reproducir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Download Status or Action Component
                when {
                    isDownloading -> {
                        // Sleek Linear / Circular Progress Card (Silent tap, no unwanted toast)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val progressFloat = ((downloadItem?.progress ?: 0) / 100f).coerceIn(0f, 1f)
                                // Smooth visual gradient progress fill
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressFloat)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                                )
                                            )
                                        )
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        SleekDeterminateCircularProgress(
                                            progress = progressFloat,
                                            size = 26.dp,
                                            strokeWidth = 3.dp,
                                            isDarkTheme = isDark,
                                            gradientColors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                Color(0xFF38BDF8)
                                            )
                                        )
                                        Column(verticalArrangement = Arrangement.Center) {
                                            Text(
                                                text = "Descargando (${downloadItem?.progress ?: 0}%)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val speedText = if (!downloadItem?.formattedSpeed.isNullOrBlank() && downloadItem?.formattedSpeed != "0 B") {
                                                "${downloadItem?.formattedSpeed} • ${downloadItem?.formattedEta}"
                                            } else {
                                                "Iniciando descarga..."
                                            }
                                            Text(
                                                text = speedText,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    isPending -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "En cola de descarga",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Iniciará automáticamente al liberarse un turno",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    isDownloaded -> {
                        OutlinedButton(
                            onClick = {
                                selectedPeliculaForSheet = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF10B981)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Disponible en tu biblioteca de Descargas",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    isPaused -> {
                        OutlinedButton(
                            onClick = {
                                val toDown = selectedPeliculaForSheet
                                selectedPeliculaForSheet = null
                                if (toDown != null) {
                                    onDownloadPelicula(toDown)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, Color(0xFFF59E0B)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF59E0B)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reanudar descarga pausada (${downloadItem?.progress ?: 0}%)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    else -> {
                        OutlinedButton(
                            onClick = {
                                val toDown = selectedPeliculaForSheet
                                selectedPeliculaForSheet = null
                                if (toDown != null) {
                                    onDownloadPelicula(toDown)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Descargar",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Telegram Community Dialog
    if (showTelegramDialog) {
        AlertDialog(
            onDismissRequest = { showTelegramDialog = false },
            containerColor = if (isDark) Color(0xFF0D1424) else Color.White,
            titleContentColor = titleTextColor,
            textContentColor = subtitleTextColor,
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.telegram),
                    contentDescription = "Telegram",
                    modifier = Modifier.size(52.dp)
                )
            },
            title = {
                Text(
                    text = "Canal oficial",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Únete a nuestro canal para consultar nuevos estrenos, enlaces actualizados y comunicados de la plataforma.",
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTelegramDialog = false
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+cOVZ_V9JPTdjN2Ux"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            AppToastManager.show("No fue posible abrir el enlace en el navegador", ToastType.ERROR)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Abrir canal", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTelegramDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cerrar", color = subtitleTextColor)
                }
            }
        )
    }
}
