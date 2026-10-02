package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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
 * BottomBarScrollBehavior:
 * Tracks vertical scroll movements on the container wrapping the HorizontalPager.
 * Completely ignores horizontal movements (e.g. horizontal swipes in pager).
 * Keeps nav bar visible when at the end of the list (atEnd = true).
 */
class BottomBarScrollBehavior(private val thresholdPx: Float = 0f) : NestedScrollConnection {
    var isVisible by mutableStateOf(true)
        private set
    var atEnd by mutableStateOf(false)
    var isChangingPage by mutableStateOf(false)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // BottomNavigation never disappears as requested ("quita eso de que el bottomnavigation desaparezca")
        if (!isVisible) isVisible = true
        return Offset.Zero
    }

    fun show() {
        isVisible = true
    }
}

/**
 * NestedScrollConnection that tracks vertical scroll movements:
 * - Hides nav bar on vertical scroll down (> thresholdPx, e.g. 10px).
 * - Reveals nav bar on vertical scroll up (> thresholdPx).
 * - Does NOT reveal nav bar when reaching the bottom of the list.
 * - Always keeps nav bar visible when at the top (position 0) or if content cannot scroll.
 */
@Composable
fun rememberHideOnScrollConnection(
    onVisibilityChange: (Boolean) -> Unit,
    canScrollBackward: () -> Boolean = { true },
    canScrollForward: () -> Boolean = { true },
    thresholdPx: Float = 10f
): NestedScrollConnection {
    val currentOnVisibilityChange by rememberUpdatedState(onVisibilityChange)
    val currentCanScrollBackward by rememberUpdatedState(canScrollBackward)
    val currentCanScrollForward by rememberUpdatedState(canScrollForward)

    return remember {
        object : NestedScrollConnection {
            private var accumulatedDelta = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Rule 1: Depende ÚNICAMENTE de la dirección del scroll VERTICAL
                val deltaY = available.y

                // Rule 5(d): Si la pantalla no tiene suficiente contenido para desplazarse
                if (!currentCanScrollBackward() && !currentCanScrollForward()) {
                    currentOnVisibilityChange(true)
                    accumulatedDelta = 0f
                    return Offset.Zero
                }

                // Rule 5(a): Si está en la parte superior (posición 0), forzar visible
                if (!currentCanScrollBackward()) {
                    currentOnVisibilityChange(true)
                    accumulatedDelta = 0f
                    if (deltaY < 0) {
                        accumulatedDelta += deltaY
                        if (accumulatedDelta < -thresholdPx) {
                            currentOnVisibilityChange(false)
                            accumulatedDelta = 0f
                        }
                    }
                    return Offset.Zero
                }

                // Reset accumulation when reversing scroll direction
                if ((deltaY > 0 && accumulatedDelta < 0) || (deltaY < 0 && accumulatedDelta > 0)) {
                    accumulatedDelta = 0f
                }
                accumulatedDelta += deltaY

                // Rule 3: Scroll hacia abajo (más allá del umbral) -> ocultar la barra
                // Rule 4: Al llegar al final de la lista, la barra NO debe reaparecer (si estaba oculta se queda oculta)
                if (accumulatedDelta < -thresholdPx) {
                    currentOnVisibilityChange(false)
                    accumulatedDelta = 0f
                } else if (accumulatedDelta > thresholdPx) {
                    // Rule 3: Scroll hacia arriba (más allá del umbral) -> mostrarla
                    currentOnVisibilityChange(true)
                    accumulatedDelta = 0f
                }

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Rule 4 & 5(a): Solo reaparece forzadamente si el scroll llegó a la parte superior (posición 0).
                // Al llegar al final de la lista (canScrollForward == false), NO debe reaparecer.
                if (!currentCanScrollBackward()) {
                    currentOnVisibilityChange(true)
                    accumulatedDelta = 0f
                }
                return Offset.Zero
            }
        }
    }
}

/**
 * Modern Translucent Floating Navigation Bar.
 * - Elegant rounded capsule with subtle frosted-glass transparency.
 * - Soft pill active indicator around the icon (no harsh rectangular boxes).
 * - Smooth spring physics on tab changes.
 * - Animated breathing badge for active downloads matching empty list animations.
 * - Fluid 250ms hide/reveal animation with smooth easing (FastOutSlowInEasing).
 */
@Composable
fun AppBottomNav(
    currentPage: Int,
    onNavigate: (Int) -> Unit,
    downloadsCount: Int,
    isDarkTheme: Boolean = true,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val navAnimSpec = tween<Color>(durationMillis = 350, easing = FastOutSlowInEasing)
    val motionAnimSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    val navOffsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 140f,
        animationSpec = motionAnimSpec,
        label = "nav_offset_y"
    )

    val navAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = motionAnimSpec,
        label = "nav_alpha"
    )

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

    // Infinite transitions for active downloads pulse & circular ripple wave (identical to empty list icons)
    val infiniteTransition = rememberInfiniteTransition(label = "bottom_nav_downloads_anim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "downloads_icon_pulse"
    )

    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "downloads_ripple_scale"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "downloads_ripple_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .graphicsLayer {
                translationY = navOffsetY.dp.toPx()
                alpha = navAlpha
            }
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
                    .height(68.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
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
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onNavigate(screen.pageIndex)
                            }
                            .testTag("nav_item_${screen.route}"),
                        contentAlignment = Alignment.Center
                    ) {
                        val isDownloadsTab = screen == ScreenRoute.DOWNLOADS
                        val hasActiveDownloads = isDownloadsTab && downloadsCount > 0 && !isSelected

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Soft pill indicator around the icon only
                            Box(
                                modifier = Modifier
                                    .width(58.dp)
                                    .height(32.dp),
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

                                // Icon (Animated downward arrow like Android system status bar when active downloads exist)
                                Box(
                                    modifier = Modifier.scale(iconScale),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDownloadsTab && downloadsCount > 0) {
                                        SystemDownloadAnimatedIcon(
                                            tint = contentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title,
                                            tint = contentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Number badge of active downloads count placed at top-end with expanding ripple wave and breathing pulse
                                if (hasActiveDownloads) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-3).dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Radiant animated expanding ripple ring around the number badge
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .scale(rippleScale)
                                                .clip(CircleShape)
                                                .background(activeColor.copy(alpha = rippleAlpha))
                                        )

                                        // Number badge with breathing pulse, perfectly centered
                                        Box(
                                            modifier = Modifier
                                                .scale(pulseScale)
                                                .size(19.dp)
                                                .clip(CircleShape)
                                                .background(activeColor)
                                                .border(1.5.dp, containerBg, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (downloadsCount > 99) "99+" else downloadsCount.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                                style = androidx.compose.ui.text.TextStyle(
                                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                        includeFontPadding = false
                                                    ),
                                                    lineHeight = 10.sp
                                                )
                                            )
                                        }
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

/**
 * Animated downward download arrow icon modeled after the Android system download indicator.
 * Displays a stationary bottom tray and a downward moving arrow with continuous looping animation.
 */
@Composable
private fun SystemDownloadAnimatedIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "system_download_arrow")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arrow_y_offset"
    )
    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arrow_alpha"
    )

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Bottom tray line representing device storage / destination
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val startX = size.width * 0.20f
            val endX = size.width * 0.80f
            val bottomY = size.height * 0.82f
            val lipHeight = 3.5.dp.toPx()

            // Horizontal tray base
            drawLine(
                color = tint,
                start = Offset(startX, bottomY),
                end = Offset(endX, bottomY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            // Left lip
            drawLine(
                color = tint,
                start = Offset(startX, bottomY),
                end = Offset(startX, bottomY - lipHeight),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            // Right lip
            drawLine(
                color = tint,
                start = Offset(endX, bottomY),
                end = Offset(endX, bottomY - lipHeight),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Downward animated arrow moving down into the tray
        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = tint.copy(alpha = arrowAlpha),
            modifier = Modifier
                .size(16.dp)
                .offset(y = arrowOffset.dp)
        )
    }
}
