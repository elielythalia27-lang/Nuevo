package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.contrastingTextColor

enum class ScreenRoute(
    val route: String,
    val title: String,
    val pageIndex: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Catálogo", 0, Icons.Filled.Movie, Icons.Outlined.Movie),
    DOWNLOADS("downloads", "Descargas", 1, Icons.Filled.CloudDownload, Icons.Outlined.CloudDownload),
    SETTINGS("settings", "Ajustes", 2, Icons.Filled.Tune, Icons.Outlined.Tune)
}

/**
 * Architectural Modern Floating Navigation Dock.
 * - Non-oval, refined geometric silhouette (16.dp corner radius)
 * - Eye-catching magnetic fluid sliding indicator with kinetic top beam
 * - Tactile spring micro-bouncing interactions on active tabs
 * - Dynamic pulsating beacon for background transfers
 */
@Composable
fun AppBottomNav(
    currentPage: Int,
    onNavigate: (Int) -> Unit,
    downloadsCount: Int,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Architectural dock colors: refined glass look without excessive roundness
    val dockShape = RoundedCornerShape(16.dp)
    val indicatorShape = RoundedCornerShape(12.dp)

    val containerBg = if (isDarkTheme) {
        Color(0xF2090F1D) // Deep obsidian frosted glass
    } else {
        Color(0xFAFFFFFF) // Crisp bright crystalline frost
    }

    val dockBorderGradient = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                activeColor.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.10f),
                activeColor.copy(alpha = 0.20f)
            )
        } else {
            listOf(
                Color.White,
                activeColor.copy(alpha = 0.35f),
                Color(0xFFCBD5E1)
            )
        }
    )

    // Infinite breathing beacon for active downloads
    val infiniteTransition = rememberInfiniteTransition(label = "bottom_nav_beacon")
    val beaconPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_scale"
    )
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDarkTheme) 22.dp else 16.dp,
                    shape = dockShape,
                    spotColor = if (isDarkTheme) activeColor.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.18f),
                    ambientColor = if (isDarkTheme) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.10f)
                )
                .border(
                    width = 1.2.dp,
                    brush = dockBorderGradient,
                    shape = dockShape
                )
                .testTag("floating_bottom_nav"),
            shape = dockShape,
            color = containerBg,
            tonalElevation = 6.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(6.dp)
            ) {
                val totalWidth = maxWidth
                val tabCount = ScreenRoute.entries.size
                val itemWidth = totalWidth / tabCount

                // Animated magnetic sliding offset
                val targetOffsetX = itemWidth * currentPage
                val animatedOffsetX by animateDpAsState(
                    targetValue = targetOffsetX,
                    animationSpec = spring(
                        dampingRatio = 0.68f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "magnetic_indicator_offset"
                )

                // 1. UNIQUE SLIDING MAGNETIC INDICATOR CAPSULE
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffsetX)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(indicatorShape)
                        .background(
                            Brush.verticalGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        activeColor.copy(alpha = 0.22f),
                                        activeColor.copy(alpha = 0.12f)
                                    )
                                } else {
                                    listOf(
                                        activeColor.copy(alpha = 0.16f),
                                        activeColor.copy(alpha = 0.08f)
                                    )
                                }
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = activeColor.copy(alpha = if (isDarkTheme) 0.55f else 0.40f),
                            shape = indicatorShape
                        )
                        .drawBehind {
                            // Ambient neon glow pool
                            drawCircle(
                                color = activeColor.copy(alpha = if (isDarkTheme) 0.20f else 0.12f),
                                radius = size.maxDimension * 0.45f
                            )
                        }
                ) {
                    // Kinetic Top Runner Light Beam on active tab
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(36.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        activeColor,
                                        Color.White,
                                        activeColor,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // 2. INTERACTIVE TABS ROW
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScreenRoute.entries.forEach { screen ->
                        val isSelected = currentPage == screen.pageIndex
                        val interactionSource = remember { MutableInteractionSource() }

                        // Tactile spring physics for selected tab
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.14f else 0.96f,
                            animationSpec = spring(
                                dampingRatio = 0.60f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "icon_scale"
                        )
                        val iconBounceY by animateDpAsState(
                            targetValue = if (isSelected) (-2).dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = 0.55f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "icon_bounce"
                        )

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) activeColor else inactiveColor,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "content_color"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    onNavigate(screen.pageIndex)
                                }
                                .testTag("nav_item_${screen.route}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.graphicsLayer(translationY = iconBounceY.value)
                            ) {
                                // Icon with Badge or pulsating Beacon
                                Box(
                                    modifier = Modifier.scale(iconScale),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (screen == ScreenRoute.DOWNLOADS && downloadsCount > 0) {
                                        // Beacon pulse effect behind badge
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .offset(x = 10.dp, y = (-8).dp)
                                                .scale(beaconPulseScale)
                                                .clip(CircleShape)
                                                .background(activeColor.copy(alpha = beaconAlpha))
                                        )

                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = activeColor,
                                                    contentColor = activeColor.contrastingTextColor(),
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .offset(x = 6.dp, y = (-4).dp)
                                                ) {
                                                    Text(
                                                        text = "$downloadsCount",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                contentDescription = screen.title,
                                                tint = contentColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title,
                                            tint = contentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                // Clear, legible, crisp title label
                                Text(
                                    text = screen.title,
                                    color = contentColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    letterSpacing = 0.2.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
