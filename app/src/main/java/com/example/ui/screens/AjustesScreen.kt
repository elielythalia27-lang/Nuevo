package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import com.example.utils.StoragePathUtils
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SortOption
import com.example.data.model.ThemeMode
import com.example.ui.theme.AppThemeColor
import com.example.ui.theme.clampColorForTheme
import com.example.ui.theme.contrastingTextColor
import com.example.ui.theme.ensureReadableAccent
import com.example.ui.theme.toAdaptivePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    themeColor: AppThemeColor,
    onThemeColorChange: (AppThemeColor) -> Unit,
    defaultFilterType: String,
    onDefaultFilterTypeChange: (String) -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    onClearCache: () -> Unit,
    maxConcurrentDownloads: Int = 3,
    onMaxConcurrentDownloadsChange: (Int) -> Unit = {},
    catalogLayoutMode: String = "GRID_2",
    onCatalogLayoutModeChange: (String) -> Unit = {},
    downloadFolderName: String = "Download Free",
    downloadFolderPath: String = "",
    onDownloadFolderChange: (name: String, path: String) -> Unit = { _, _ -> },
    wifiOnly: Boolean = false,
    onWifiOnlyChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}

            val result = StoragePathUtils.parseTreeUri(context, uri)
            onDownloadFolderChange(result.name, result.path)
            AppToastManager.show("Carpeta configurada: ${result.name}", ToastType.SUCCESS)
        }
    }

    var showColorPickerDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }

    var hardwareAcceleration by remember { mutableStateOf(true) }
    var screenGestures by remember { mutableStateOf(true) }
    var autoResume by remember { mutableStateOf(true) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    val screenBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val itemBg = MaterialTheme.colorScheme.surfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val cardElevation by animateDpAsState(
        targetValue = if (isDarkTheme) 0.dp else 1.5.dp,
        animationSpec = tween<androidx.compose.ui.unit.Dp>(220),
        label = "card_elevation"
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.2f else 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Ajustes",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Apariencia y Personalización
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "APARIENCIA Y TEMA", icon = Icons.Default.Palette)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Selector de Tema Segmentado
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Modo de visualización",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )

                                val modes = listOf(
                                    Triple(ThemeMode.SYSTEM, "Sistema", Icons.Default.BrightnessAuto),
                                    Triple(ThemeMode.DARK, "Oscuro", Icons.Default.DarkMode),
                                    Triple(ThemeMode.LIGHT, "Claro", Icons.Default.LightMode)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(itemBg)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    modes.forEach { (mode, label, icon) ->
                                        val isSelected = themeMode == mode
                                        val activeBg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        val activeText = if (isSelected) MaterialTheme.colorScheme.onPrimary else textSecondary

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(activeBg)
                                                .clickable {
                                                    onThemeModeChange(mode)
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = activeText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = activeText
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = dividerColor)

                            // Color de Énfasis
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showColorPickerDialog = true }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val activePrimary = remember(themeColor, isDarkTheme) {
                                        themeColor.primaryForTheme(isDarkTheme)
                                    }
                                    val activeVariant = remember(themeColor, isDarkTheme) {
                                        themeColor.primaryVariantForTheme(isDarkTheme)
                                    }
                                    val onPrimaryColor = remember(activePrimary) {
                                        activePrimary.contrastingTextColor()
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        activePrimary,
                                                        activeVariant
                                                    )
                                                )
                                            )
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = onPrimaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        val currentHex = remember(activePrimary) {
                                            val r = (activePrimary.red * 255).toInt().coerceIn(0, 255)
                                            val g = (activePrimary.green * 255).toInt().coerceIn(0, 255)
                                            val b = (activePrimary.blue * 255).toInt().coerceIn(0, 255)
                                            String.format("#%02X%02X%02X", r, g, b)
                                        }
                                        Text(
                                            text = "Color de énfasis",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = currentHex,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            color = textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = { showColorPickerDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Elegir",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HorizontalDivider(color = dividerColor)

                            // Disposición del Catálogo
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Vista del catálogo principal",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )

                                val layoutOptions = listOf(
                                    Triple("GRID_2", "Cuadrícula (2)", Icons.Default.GridView),
                                    Triple("GRID_3", "Compacta (3)", Icons.Default.ViewModule),
                                    Triple("LIST", "Lista", Icons.Default.ViewAgenda)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    layoutOptions.forEach { (mode, label, icon) ->
                                        val isSelected = catalogLayoutMode == mode
                                        val activeBorder = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        val activeBg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.12f) else itemBg

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(activeBg)
                                                .border(1.dp, activeBorder, RoundedCornerShape(12.dp))
                                                .clickable { onCatalogLayoutModeChange(mode) }
                                                .padding(vertical = 12.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else textSecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = label,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) textPrimary else textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Criterio de Ordenación
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "ORDENAR CATÁLOGO", icon = Icons.Default.Sort)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Elige el criterio para listar títulos en pantalla",
                                fontSize = 12.5.sp,
                                color = textSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            SortOption.entries.forEach { option ->
                                val isSelected = sortOption == option
                                val activeBorder = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                val activeBg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.16f else 0.10f) else itemBg

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(activeBg)
                                        .border(1.dp, activeBorder, RoundedCornerShape(12.dp))
                                        .clickable { onSortOptionChange(option) }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = option.displayName,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) textPrimary else textSecondary
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Gestor de Descargas y Almacenamiento
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "DESCARGAS Y ALMACENAMIENTO", icon = Icons.Default.Download)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Ubicación de descargas
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showFolderDialog = true }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.10f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = "Ubicación de descargas",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = downloadFolderName.ifBlank { "Download Free" },
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Button(
                                    onClick = { showFolderDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.25f else 0.15f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Cambiar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            HorizontalDivider(color = dividerColor)

                            // Límite de descargas simultáneas
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.10f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Descargas simultáneas",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = textPrimary
                                            )
                                            Text(
                                                text = "$maxConcurrentDownloads activas al mismo tiempo",
                                                fontSize = 12.sp,
                                                color = textSecondary
                                            )
                                        }
                                    }
                                }

                                Slider(
                                    value = maxConcurrentDownloads.toFloat(),
                                    onValueChange = { onMaxConcurrentDownloadsChange(it.toInt().coerceIn(1, 5)) },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.2f else 0.15f),
                                        activeTickColor = MaterialTheme.colorScheme.onPrimary,
                                        inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    (1..5).forEach { num ->
                                        val isSelected = num == maxConcurrentDownloads
                                        Text(
                                            text = "$num",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else textSecondary,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable { onMaxConcurrentDownloadsChange(num) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = dividerColor)

                            // Wi-Fi Only Switch
                            SettingsSwitchRow(
                                icon = Icons.Default.Wifi,
                                title = "Transferencias únicamente por Wi-Fi",
                                subtitle = "Restringe descargas a redes Wi-Fi para no consumir tus datos móviles.",
                                checked = wifiOnly,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onCheckedChange = {
                                    onWifiOnlyChange(it)
                                    AppToastManager.show(
                                        if (it) "Descargas restringidas a redes Wi-Fi" else "Descargas permitidas en cualquier conexión",
                                        ToastType.INFO
                                    )
                                }
                            )

                            HorizontalDivider(color = dividerColor)

                            // Limpiar Caché
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.10f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CleaningServices,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = "Memoria caché",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "Elimina archivos temporales y miniaturas",
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = { showClearCacheDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                                        contentColor = textPrimary
                                    )
                                ) {
                                    Text("Limpiar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Reproductor de Video
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "REPRODUCTOR DE VIDEO", icon = Icons.Default.PlayCircleOutline)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SettingsSwitchRow(
                                icon = Icons.Default.Memory,
                                title = "Aceleración por hardware",
                                subtitle = "Mejora fluidez y reduce consumo de batería en reproducción.",
                                checked = hardwareAcceleration,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onCheckedChange = { hardwareAcceleration = it }
                            )
                            HorizontalDivider(color = dividerColor)
                            SettingsSwitchRow(
                                icon = Icons.Default.TouchApp,
                                title = "Gestos en pantalla",
                                subtitle = "Control táctil de brillo, volumen y avance con doble toque.",
                                checked = screenGestures,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onCheckedChange = { screenGestures = it }
                            )
                            HorizontalDivider(color = dividerColor)
                            SettingsSwitchRow(
                                icon = Icons.Default.PlayCircleOutline,
                                title = "Recordar posición",
                                subtitle = "Reanudar videos automáticamente desde donde los dejaste.",
                                checked = autoResume,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onCheckedChange = { autoResume = it }
                            )
                        }
                    }
                }
            }

            // Section 5: Comunidad y Soporte
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "COMUNIDAD Y SOPORTE", icon = Icons.Default.Info)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(itemBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+cOVZ_V9JPTdjN2Ux"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            AppToastManager.show("No fue posible abrir el enlace en el navegador", ToastType.ERROR)
                                        }
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.telegram),
                                    contentDescription = "Telegram Oficial",
                                    modifier = Modifier.size(38.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Canal Oficial de Telegram",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Novedades, estrenos y enlaces actualizados",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Clear Cache Confirmation
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                containerColor = cardBg,
                shape = RoundedCornerShape(16.dp),
                title = { Text("¿Deseas vaciar la memoria caché?", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Se liberará espacio eliminando archivos temporales y miniaturas del sistema. Tu biblioteca de descargas se conservará intacta.",
                        color = textSecondary,
                        lineHeight = 19.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearCache()
                            showClearCacheDialog = false
                            AppToastManager.show("Memoria caché liberada con éxito", ToastType.SUCCESS)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Vaciar caché", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearCacheDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar", color = textSecondary)
                    }
                }
            )
        }

        if (showColorPickerDialog) {
            ThemeColorPickerDialog(
                currentColor = themeColor,
                isDarkTheme = isDarkTheme,
                onDismiss = { showColorPickerDialog = false },
                onColorSelected = { selectedPreset ->
                    onThemeColorChange(selectedPreset)
                }
            )
        }

        if (showFolderDialog) {
            DownloadFolderDialog(
                currentName = downloadFolderName,
                currentPath = downloadFolderPath,
                isDarkTheme = isDarkTheme,
                onDismiss = { showFolderDialog = false },
                onSelectFolder = { name, path ->
                    onDownloadFolderChange(name, path)
                },
                onOpenSystemPicker = {
                    openFolderLauncher.launch(null)
                }
            )
        }
    }
}

@Composable
private fun SettingsCategoryHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.1.sp
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = textSecondary
            )
        }
        val activePrimary = MaterialTheme.colorScheme.primary
        val isBrightAccent = (0.299f * activePrimary.red + 0.587f * activePrimary.green + 0.114f * activePrimary.blue) > 0.65f

        val checkedThumb = if (isBrightAccent) Color(0xFF0F172A) else Color.White
        val checkedTrack = activePrimary
        val checkedBorder = if (isBrightAccent) Color(0xFF64748B) else Color.Transparent

        val uncheckedThumbColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
        val uncheckedTrackColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
        val uncheckedBorderColor = if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                        tint = if (isBrightAccent) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            } else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = checkedThumb,
                checkedTrackColor = checkedTrack,
                checkedBorderColor = checkedBorder,
                uncheckedThumbColor = uncheckedThumbColor,
                uncheckedTrackColor = uncheckedTrackColor,
                uncheckedBorderColor = uncheckedBorderColor
            )
        )
    }
}

@Composable
private fun DownloadFolderDialog(
    currentName: String,
    currentPath: String,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onSelectFolder: (name: String, path: String) -> Unit,
    onOpenSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    val cardBg = if (isDarkTheme) Color(0xFF1E293B) else Color.White
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    val defaultPath = remember {
        StoragePathUtils.getDefaultDownloadFolder().path
    }

    var selectedOptionName by remember {
        val cleanName = if (currentName.isBlank() || currentName.contains("Por defecto", ignoreCase = true) || currentName.contains("Películas", ignoreCase = true)) "Download Free" else currentName
        mutableStateOf(cleanName)
    }
    var selectedOptionPath by remember {
        mutableStateOf(if (currentPath.isBlank()) defaultPath else currentPath)
    }

    val isDefaultSelected = selectedOptionName == "Download Free"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Carpeta de Descargas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Selecciona la carpeta donde se guardarán los archivos descargados:",
                    fontSize = 12.5.sp,
                    color = textSecondary
                )

                // 1. Opción de la aplicación: Download Free
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDefaultSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.2f else 0.12f) else (if (isDarkTheme) Color(0xFF131D33) else Color(0xFFF8FAFC)),
                    border = BorderStroke(
                        1.2.dp,
                        if (isDefaultSelected) MaterialTheme.colorScheme.primary else (if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedOptionName = "Download Free"
                            selectedOptionPath = defaultPath
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isDefaultSelected,
                            onClick = {
                                selectedOptionName = "Download Free"
                                selectedOptionPath = defaultPath
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Free",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Opción personalizada: Almacenamiento del teléfono
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isDefaultSelected && selectedOptionName.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.2f else 0.12f) else (if (isDarkTheme) Color(0xFF131D33) else Color(0xFFF8FAFC)),
                    border = BorderStroke(
                        1.2.dp,
                        if (!isDefaultSelected && selectedOptionName.isNotBlank()) MaterialTheme.colorScheme.primary else (if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenSystemPicker()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.25f else 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (!isDefaultSelected && selectedOptionName.isNotBlank()) selectedOptionName else "Elegir carpeta personalizada",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isDefaultSelected && selectedOptionName.isNotBlank()) textPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectFolder(selectedOptionName, selectedOptionPath)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("Guardar ubicación", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Cancelar", color = textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        },
        containerColor = cardBg
    )
}

@Composable
private fun ThemeColorPickerDialog(
    currentColor: AppThemeColor,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onColorSelected: (AppThemeColor) -> Unit
) {
    val dialogBg = if (isDarkTheme) Color(0xFF1E293B) else Color.White
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val dialogBorder = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
    val subCardBg = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)

    val availableColors = remember(isDarkTheme) {
        AppThemeColor.getPresetsForTheme(isDarkTheme)
    }

    val chunkedColors = remember(availableColors) {
        availableColors.chunked(5)
    }

    // Determine the exact index and row of the currently selected color
    val selectedColorIndex = remember(availableColors, currentColor) {
        val idx = availableColors.indexOfFirst {
            it.id.equals(currentColor.id, ignoreCase = true) ||
                    it.primary.value == currentColor.primary.value
        }
        if (idx >= 0) idx else 0
    }

    val selectedRowIndex = remember(selectedColorIndex) {
        selectedColorIndex / 5
    }

    val initialScrollIndex = remember(selectedRowIndex) {
        (selectedRowIndex - 1).coerceAtLeast(0)
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex
    )

    // Ensure the dialog immediately displays the selected color upon opening
    LaunchedEffect(selectedRowIndex) {
        listState.scrollToItem((selectedRowIndex - 1).coerceAtLeast(0))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = dialogBg,
            border = BorderStroke(1.dp, dialogBorder),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with icon, title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Color de Énfasis",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = textPrimary
                            )
                            Text(
                                text = "Toca un color para aplicarlo",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Active color indicator chip (HEX code only, no color names)
                val activePrimary = remember(currentColor, isDarkTheme) {
                    currentColor.primaryForTheme(isDarkTheme)
                }
                val currentHex = remember(activePrimary) {
                    val r = (activePrimary.red * 255).toInt().coerceIn(0, 255)
                    val g = (activePrimary.green * 255).toInt().coerceIn(0, 255)
                    val b = (activePrimary.blue * 255).toInt().coerceIn(0, 255)
                    String.format("#%02X%02X%02X", r, g, b)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = subCardBg,
                    border = BorderStroke(1.dp, dialogBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(activePrimary)
                                    .border(
                                        1.5.dp,
                                        if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "Color seleccionado",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = textSecondary
                            )
                        }

                        Text(
                            text = currentHex,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Color Palette grid with tight bounds and instant scroll to selection
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp)
                ) {
                    items(chunkedColors) { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowColors.forEach { preset ->
                                val presetActivePrimary = remember(preset, isDarkTheme) {
                                    preset.primaryForTheme(isDarkTheme)
                                }
                                val isSelected = currentColor.id.equals(preset.id, ignoreCase = true) ||
                                        (activePrimary.value == presetActivePrimary.value)
                                val presetOnColor = remember(presetActivePrimary) { presetActivePrimary.contrastingTextColor() }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(presetActivePrimary)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) (if (isDarkTheme) Color.White else Color(0xFF0F172A)) else dialogBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onColorSelected(preset)
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = presetOnColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
