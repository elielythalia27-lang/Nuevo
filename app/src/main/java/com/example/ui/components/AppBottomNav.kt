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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Modern Translucent Floating Navigation Bar.
 * - Elegant rounded capsule with subtle frosted-glass transparency.
 * - Soft pill active indicator around the icon (no harsh rectangular boxes).
 * - Smooth spring physics on tab changes.
 * - Animated breathing badge for active downloads.
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
    val navAnimSpec = tween<Color>(durationMillis = 350, easing = FastOutSlowInEasing)

    val inactiveColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF334155),
        animationSpec = navAnimSpec,
        label = "nav_inactive_color"
    )

    // Sleek rounded capsule silhouette
    val dockShape = RoundedCornerShape(30.dp)

    // In dark theme: frosted obsidian with 93% opacity
    // In light theme: frosted clean white with 93% opacity matching the dark theme translucency
    val targetContainerBg = if (isDarkTheme) {
        Color(0xEE0B1220) // 93% opacity dark obsidian
    } else {
        Color(0xEEFFFFFF) // 93% opacity frosted clean white
    }

    val containerBg by animateColorAsState(
        targetValue = targetContainerBg,
        animationSpec = navAnimSpec,
        label = "nav_container_bg"
    )

    val targetDockBorderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color(0xFFCBD5E1).copy(alpha = 0.85f)
    }

    val dockBorderColor by animateColorAsState(
        targetValue = targetDockBorderColor,
        animationSpec = navAnimSpec,
        label = "nav_border_color"
    )

    // Infinite gentle breathing pulse for active download beacon
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDarkTheme) 12.dp else 16.dp,
                    shape = dockShape,
                    spotColor = if (isDarkTheme) Color.Black.copy(alpha = 0.6f) else Color(0x380F172A),
                    ambientColor = if (isDarkTheme) Color.Black.copy(alpha = 0.2f) else Color(0x1F000000)
                )
                .testTag("floating_bottom_nav"),
            shape = dockShape,
            color = containerBg,
            border = BorderStroke(1.2.dp, dockBorderColor),
            tonalElevation = if (isDarkTheme) 4.dp else 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenRoute.entries.forEach { screen ->
                    val isSelected = currentPage == screen.pageIndex
                    val interactionSource = remember { MutableInteractionSource() }

                    // Smooth spring animations for scale and position
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.10f else 0.94f,
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "icon_scale"
                    )

                    val pillWidth by animateDpAsState(
                        targetValue = if (isSelected) 56.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = 0.70f,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "pill_width"
                    )

                    val pillAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "pill_alpha"
                    )

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) activeColor else inactiveColor,
                        animationSpec = tween(durationMillis = 200),
                        label = "content_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
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
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Soft pill indicator around the icon only
                            Box(
                                modifier = Modifier
                                    .width(58.dp)
                                    .height(30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background pill container (visible when selected)
                                if (isSelected || pillAlpha > 0.05f) {
                                    Box(
                                        modifier = Modifier
                                            .width(pillWidth)
                                            .height(30.dp)
                                            .clip(CircleShape)
                                            .background(
                                                activeColor.copy(
                                                    alpha = (if (isDarkTheme) 0.22f else 0.18f) * pillAlpha
                                                )
                                            )
                                    )
                                }

                                // Icon with Badge or pulsating Beacon
                                Box(
                                    modifier = Modifier.scale(iconScale),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (screen == ScreenRoute.DOWNLOADS && downloadsCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = activeColor,
                                                    contentColor = activeColor.contrastingTextColor(),
                                                    modifier = Modifier.offset(x = 2.dp, y = (-2).dp)
                                                ) {
                                                    val displayCount = if (downloadsCount > 99) "99+" else "$downloadsCount"
                                                    Text(
                                                        text = displayCount,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 11.sp,
                                                        textAlign = TextAlign.Center
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
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Clean and crisp title label
                            Text(
                                text = screen.title,
                                color = contentColor,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                letterSpacing = 0.1.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
