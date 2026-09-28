package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Creates a modern glowing shimmer brush for skeleton loading states.
 * Uses an angled linear light sweep wave for a fluid, polished effect.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp),
    isDark: Boolean? = null
): Modifier = composed {
    val systemDark = isSystemInDarkTheme()
    val darkMode = isDark ?: systemDark
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = -700f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_float"
    )

    val shimmerColors = if (darkMode) {
        listOf(
            Color(0xFF131C31),
            Color(0xFF1D283E),
            Color(0xFF2D3C59),
            Color(0xFF1D283E),
            Color(0xFF131C31)
        )
    } else {
        listOf(
            Color(0xFFE2E8F0),
            Color(0xFFEDF2F7),
            Color(0xFFF8FAFC),
            Color(0xFFEDF2F7),
            Color(0xFFE2E8F0)
        )
    }

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim.value - 380f, y = translateAnim.value - 380f),
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    this
        .clip(shape)
        .background(brush)
}

/**
 * Shimmer placeholder for movie cards in grid mode.
 */
@Composable
fun PeliculaGridItemSkeleton(isDark: Boolean = isSystemInDarkTheme()) {
    val cardBg = if (isDark) Color(0xFF0F172A) else Color.White
    val cardBorder = if (isDark) Color(0xFF26354D) else Color(0xFFA0AEC0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (isDark) 1.dp else 1.2.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 1.5.dp else 2.5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster skeleton matching vertical 0.78f aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .shimmerEffect(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp), isDark = isDark)
            )
            // Title and badge skeleton
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .height(11.dp)
                        .shimmerEffect(RoundedCornerShape(4.dp), isDark = isDark)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.50f)
                        .height(10.dp)
                        .shimmerEffect(RoundedCornerShape(4.dp), isDark = isDark)
                )
            }
        }
    }
}

/**
 * Shimmer placeholder for movie cards in list mode.
 * Matches PeliculaCard list mode exactly: poster 74x108dp, corner 9dp, card 10dp, 2-line title and badge pills.
 */
@Composable
fun PeliculaListItemSkeleton(isDark: Boolean = isSystemInDarkTheme()) {
    val cardBg = if (isDark) Color(0xFF0F172A) else Color.White
    val cardBorder = if (isDark) Color(0xFF26354D) else Color(0xFFA0AEC0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (isDark) 1.dp else 1.2.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 1.5.dp else 2.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster (exact 74x108 dp with 9dp corner)
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 108.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .shimmerEffect(RoundedCornerShape(9.dp), isDark = isDark)
            )

            // Metadata Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title Line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(14.dp)
                        .shimmerEffect(RoundedCornerShape(4.dp), isDark = isDark)
                )
                // Title Line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(14.dp)
                        .shimmerEffect(RoundedCornerShape(4.dp), isDark = isDark)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Badges row skeleton (simulating category and year pills)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 18.dp)
                            .shimmerEffect(RoundedCornerShape(6.dp), isDark = isDark)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 18.dp)
                            .shimmerEffect(RoundedCornerShape(6.dp), isDark = isDark)
                    )
                }
            }
        }
    }
}

/**
 * Full skeleton loading grid for the catalog.
 */
@Composable
fun PeliculaGridSkeleton(
    modifier: Modifier = Modifier,
    columnsCount: Int = 2,
    itemCount: Int = 6,
    isDark: Boolean = isSystemInDarkTheme()
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnsCount),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) {
            PeliculaGridItemSkeleton(isDark = isDark)
        }
    }
}

/**
 * Full skeleton loading list for the catalog.
 */
@Composable
fun PeliculaListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    isDark: Boolean = isSystemInDarkTheme()
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) {
            PeliculaListItemSkeleton(isDark = isDark)
        }
    }
}

/**
 * Shimmer image placeholder for SubcomposeAsyncImage loading slots.
 */
@Composable
fun ShimmerImagePlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    isDark: Boolean = isSystemInDarkTheme()
) {
    Box(
        modifier = modifier.shimmerEffect(shape = shape, isDark = isDark)
    )
}
