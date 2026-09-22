package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.ui.components.shimmerEffect

/**
 * Standard Uniform Movie Card.
 * Adheres strictly to dark and light mode theming, ensuring no mismatched white blocks in dark mode
 * and crisp borders with comfortable elevation in light mode.
 */
@Composable
fun PeliculaCard(
    pelicula: Pelicula,
    downloadItem: DownloadItem?,
    onCardClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDarkTheme: Boolean = true,
    isListMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardBg = if (isDarkTheme) Color(0xFF0F172A) else Color.White
    val cardBorder = if (isDarkTheme) Color(0xFF26354D) else Color(0xFFA0AEC0)
    val titleColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val subtitleColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF334155)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pelicula_card_${pelicula.id}")
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCardClick() },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isDarkTheme) 1.dp else 1.2.dp, cardBorder),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 1.5.dp else 2.5.dp)
    ) {
        if (isListMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Poster
                Box(
                    modifier = Modifier
                        .size(width = 74.dp, height = 108.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isDarkTheme) Color(0xFF161F33) else Color(0xFFE2E8F0))
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pelicula.safeCoverUrl)
                            .crossfade(150)
                            .size(240, 350)
                            .build(),
                        contentDescription = pelicula.safeTitle,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmerEffect(RoundedCornerShape(9.dp), isDark = isDarkTheme)
                            )
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDarkTheme) Color(0xFF161F33) else Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Metadata Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = pelicula.safeTitle,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 2,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Badges row
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
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (pelicula.isVideo && pelicula.youtuberName.isNotEmpty() && !pelicula.youtuberName.equals("YouTube", ignoreCase = true)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = pelicula.youtuberName,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (pelicula.safeYear.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = pelicula.safeYear,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Download status chip
                    if (downloadItem != null) {
                        when (downloadItem.status) {
                            DownloadStatus.COMPLETED -> {
                                val downloadedLabel = if (pelicula.isVideo) "Descargado" else "Descargada"
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x2210B981)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = downloadedLabel,
                                            color = Color(0xFF10B981),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            DownloadStatus.DOWNLOADING -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Descargando (${downloadItem.progress}%)",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            DownloadStatus.PENDING -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF3B82F6).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "En cola",
                                        color = Color(0xFF3B82F6),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            DownloadStatus.PAUSED -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Pausada (${downloadItem.progress}%)",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Vertical movie poster area (compact refined ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.78f)
                        .background(if (isDarkTheme) Color(0xFF161F33) else Color(0xFFE2E8F0))
                ) {
                    // Fast SubcomposeAsyncImage with smooth shimmer placeholder
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pelicula.safeCoverUrl)
                            .crossfade(150)
                            .size(360, 500)
                            .build(),
                        contentDescription = pelicula.safeTitle,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmerEffect(
                                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                        isDark = isDarkTheme
                                    )
                            )
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDarkTheme) Color(0xFF161F33) else Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Type or Creator/Year Badge on top right
                    if (pelicula.isVideo) {
                        Surface(
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.TopEnd),
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE50914)
                        ) {
                            Text(
                                text = "YouTube",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        val yearBadgeText = if (pelicula.safeYear.isNotEmpty()) pelicula.safeYear else "Película"
                        Surface(
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.TopEnd),
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xDD0F172A)
                        ) {
                            Text(
                                text = yearBadgeText,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Download status indicator overlay on bottom-start of poster (prevents overlap with year/badge at TopEnd)
                    if (downloadItem != null && downloadItem.status == DownloadStatus.COMPLETED) {
                        val downloadedLabel = if (pelicula.isVideo) "Descargado" else "Descargada"
                        Surface(
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.BottomStart),
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xEE10B981)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = downloadedLabel,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = downloadedLabel,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Title Container: clear legible title with creator info for YouTube with generous breathing room
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .background(cardBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = pelicula.safeTitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                            textAlign = TextAlign.Center,
                            maxLines = if (pelicula.isVideo && pelicula.safeYear.isNotBlank()) 1 else 2,
                            lineHeight = 14.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pelicula.isVideo && pelicula.safeYear.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pelicula.safeYear,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE50914),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
