package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    totalCount: Int,
    filteredCount: Int,
    onClearFilters: () -> Unit,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasActiveFilters = searchQuery.isNotEmpty() || selectedType != "ALL"
    val searchBg = if (isDarkTheme) Color(0xFF131C30) else Color.White
    val searchBorder = if (searchQuery.isNotEmpty()) {
        MaterialTheme.colorScheme.primary
    } else if (isDarkTheme) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color(0xFFA0AEC0)
    }
    val textColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val hintColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val chipBg = if (isDarkTheme) Color(0xFF131C30) else Color(0xFFE2E8F0)
    val chipText = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF1E293B)
    val counterBg = if (isDarkTheme) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFFCBD5E1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize()
    ) {
        // Modern Search Pill with integrated icons and clear button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(
                    width = 1.dp,
                    color = searchBorder,
                    shape = CircleShape
                ),
            shape = CircleShape,
            color = searchBg,
            shadowElevation = if (isDarkTheme) 2.dp else 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.18f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_bar"),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = textColor
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Buscar por título...",
                                    fontSize = 14.sp,
                                    color = hintColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = hintColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Clean category filter chips (Todos, Películas, Videos / YouTube)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onPrimary = MaterialTheme.colorScheme.onPrimary

            // Chip: Todos
            FilterChip(
                selected = selectedType == "ALL",
                onClick = { onTypeSelected("ALL") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selectedType == "ALL") onPrimary else chipText
                    )
                },
                label = { Text("Todos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (selectedType == "ALL") Color.Transparent else if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = onPrimary,
                    containerColor = chipBg,
                    labelColor = chipText
                )
            )

            // Chip: Películas
            FilterChip(
                selected = selectedType == "MOVIE",
                onClick = { onTypeSelected("MOVIE") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selectedType == "MOVIE") onPrimary else chipText
                    )
                },
                label = { Text("Películas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (selectedType == "MOVIE") Color.Transparent else if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = onPrimary,
                    containerColor = chipBg,
                    labelColor = chipText
                )
            )

            val youtubeRed = Color(0xFFEF4444)
            val isYouTubeSelected = selectedType == "VIDEO"

            // Chip: YouTube - Dynamic styling matching YouTube branding (Red icon when unselected, full Red pill when selected)
            FilterChip(
                selected = isYouTubeSelected,
                onClick = { onTypeSelected("VIDEO") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isYouTubeSelected) Color.White else youtubeRed
                    )
                },
                label = {
                    Text(
                        text = "YouTube",
                        fontSize = 12.sp,
                        fontWeight = if (isYouTubeSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isYouTubeSelected) Color.Transparent else if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = youtubeRed,
                    selectedLabelColor = Color.White,
                    containerColor = chipBg,
                    labelColor = chipText
                )
            )
        }
    }
}
