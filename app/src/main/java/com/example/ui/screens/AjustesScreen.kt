package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
    var wifiOnlyDownloads by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    val screenBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val itemBg = MaterialTheme.colorScheme.surfaceVariant
    val dividerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)

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
            // Section 1: Ordenación del Catálogo
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "ORDENAR POR", icon = Icons.Default.Sort)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Criterio de ordenación para el catálogo de títulos",
                                fontSize = 13.sp,
                                color = textSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SortOption.entries.forEach { option ->
                                val isSelected = sortOption == option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSortOptionChange(option) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSortOptionChange(option) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = textSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = option.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) textPrimary else textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section: Diseño y Vista del Catálogo
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "VISTA DEL CATÁLOGO", icon = Icons.Default.GridView)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Disposición de elementos en la pantalla principal",
                                fontSize = 13.sp,
                                color = textSecondary
                            )

                            val layoutOptions = listOf(
                                Triple("GRID_2", "Cuadrícula (2 columnas)", Icons.Default.GridView),
                                Triple("GRID_3", "Cuadrícula compacta (3 columnas)", Icons.Default.ViewModule),
                                Triple("LIST", "Lista detallada", Icons.Default.ViewAgenda)
                            )

                            layoutOptions.forEach { (mode, label, icon) ->
                                val isSelected = catalogLayoutMode == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.12f)
                                            else itemBg
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onCatalogLayoutModeChange(mode) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = label,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) textPrimary else textSecondary
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onCatalogLayoutModeChange(mode) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = textSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Modo Visual (Sistema / Oscuro / Claro)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "TEMA", icon = Icons.Default.BrightnessAuto)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Elige la apariencia visual de la interfaz",
                                fontSize = 13.sp,
                                color = textSecondary
                            )

                            val modes = listOf(
                                Triple(ThemeMode.SYSTEM, "Predeterminado del sistema", Icons.Default.BrightnessAuto),
                                Triple(ThemeMode.DARK, "Oscuro", Icons.Default.DarkMode),
                                Triple(ThemeMode.LIGHT, "Claro", Icons.Default.LightMode)
                            )

                            modes.forEach { (mode, label, icon) ->
                                val isSelected = themeMode == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.12f)
                                            else itemBg
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            val newIsDark = when (mode) {
                                                ThemeMode.SYSTEM -> isDarkTheme
                                                ThemeMode.DARK -> true
                                                ThemeMode.LIGHT -> false
                                            }
                                            onThemeModeChange(mode)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = label,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) textPrimary else textSecondary
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onThemeModeChange(mode)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = textSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Color de Acento
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "COLOR DE ÉNFASIS", icon = Icons.Default.Palette)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showColorPickerDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val onPrimaryColor = remember(themeColor.primary) {
                                    themeColor.primary.contrastingTextColor()
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    themeColor.primary,
                                                    themeColor.primaryVariantForTheme(isDarkTheme)
                                                )
                                            )
                                        )
                                        .border(
                                            2.dp,
                                            if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = onPrimaryColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    val currentHex = remember(themeColor.primary) {
                                        val r = (themeColor.primary.red * 255).toInt().coerceIn(0, 255)
                                        val g = (themeColor.primary.green * 255).toInt().coerceIn(0, 255)
                                        val b = (themeColor.primary.blue * 255).toInt().coerceIn(0, 255)
                                        String.format("#%02X%02X%02X", r, g, b)
                                    }
                                    Text(
                                        text = "Color de énfasis",
                                        fontSize = 15.sp,
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

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = { showColorPickerDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Elegir",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Gestor de Descargas
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "DESCARGAS", icon = Icons.Default.Download)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Wi-Fi Only
                            SettingsSwitchRow(
                                icon = Icons.Default.Wifi,
                                title = "Transferencias únicamente por Wi-Fi",
                                subtitle = "Restringe las descargas a redes Wi-Fi para optimizar los datos móviles.",
                                checked = wifiOnlyDownloads,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onCheckedChange = {
                                    wifiOnlyDownloads = it
                                    AppToastManager.show(
                                        if (it) "Descargas restringidas a redes Wi-Fi" else "Descargas permitidas en cualquier conexión",
                                        ToastType.INFO
                                    )
                                }
                            )

                            HorizontalDivider(color = dividerColor)

                            // Concurrent downloads limit (1 to 5) with professional Slider/Seekbar
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
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
                                        Column {
                                            Text(
                                                text = "Descargas simultáneas",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = textPrimary
                                            )
                                            Text(
                                                text = "Límite de descargas activas a la vez",
                                                fontSize = 11.5.sp,
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
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else textSecondary,
                                            modifier = Modifier.clickable { onMaxConcurrentDownloadsChange(num) }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = dividerColor)

                            // Carpeta de descargas
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showFolderDialog = true }
                                    .padding(vertical = 4.dp),
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
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.2f else 0.12f)),
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
                        }
                    }
                }
            }

            // Section 5: Reproductor de Video
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "REPRODUCTOR DE VIDEO", icon = Icons.Default.PlayCircleOutline)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SettingsSwitchRow(
                                icon = Icons.Default.Memory,
                                title = "Aceleración por hardware",
                                subtitle = "Mejora el rendimiento y reduce el uso de batería durante la reproducción.",
                                checked = hardwareAcceleration,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onCheckedChange = { hardwareAcceleration = it }
                            )
                            SettingsSwitchRow(
                                icon = Icons.Default.TouchApp,
                                title = "Gestos en pantalla",
                                subtitle = "Control de brillo, volumen y avance rápido con deslizamientos y doble toque.",
                                checked = screenGestures,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onCheckedChange = { screenGestures = it }
                            )
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

            // Section 6: Almacenamiento
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "ALMACENAMIENTO", icon = Icons.Default.CleaningServices)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Borrar caché",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Libera espacio eliminando archivos temporales e imágenes en caché.",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showClearCacheDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Borrar caché", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Section 7: Comunidad y Soporte
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryHeader(title = "COMUNIDAD Y SOPORTE", icon = Icons.Default.Info)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Telegram Canal Oficial
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(itemBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+cOVZ_V9JPTdjN2Ux"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            AppToastManager.show("No fue posible abrir el enlace en el navegador", ToastType.ERROR)
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.telegram),
                                    contentDescription = "Telegram Oficial",
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Canal oficial",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Novedades, estrenos y enlaces actualizados",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
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
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8),
                uncheckedTrackColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
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
                val currentHex = remember(currentColor.primary) {
                    val r = (currentColor.primary.red * 255).toInt().coerceIn(0, 255)
                    val g = (currentColor.primary.green * 255).toInt().coerceIn(0, 255)
                    val b = (currentColor.primary.blue * 255).toInt().coerceIn(0, 255)
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
                                    .background(currentColor.primary)
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
                                val isSelected = currentColor.id.equals(preset.id, ignoreCase = true) ||
                                        (currentColor.primary.value == preset.primary.value)
                                val presetOnColor = remember(preset.primary) { preset.primary.contrastingTextColor() }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(preset.primary)
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
