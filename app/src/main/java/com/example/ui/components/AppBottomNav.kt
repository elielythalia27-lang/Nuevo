package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
    HOME("home", "Catálogo", 0, Icons.Rounded.Movie, Icons.Rounded.Movie),
    DOWNLOADS("downloads", "Descargas", 1, Icons.Rounded.CloudDownload, Icons.Rounded.CloudDownload),
    SETTINGS("settings", "Ajustes", 2, Icons.Rounded.Tune, Icons.Rounded.Tune)
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
 * Dock de navegación flotante con diseño propio de Download Free:
 * - Cápsula de vidrio despegada de los bordes, con brillo superior y glow del color activo.
 * - Pestaña activa: píldora morfeante con degradado del color primario + etiqueta que se expande.
 * - Icono con rebote elástico al seleccionar y muesca luminosa que viaja por el borde superior.
 * - Badge de descargas activas con onda expansiva y flecha animada estilo sistema.
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
    val haptics = rememberHaptics()
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val dockBg = if (isDarkTheme) Color(0xFF0F172A).copy(alpha = 0.93f) else Color(0xFFFFFFFF).copy(alpha = 0.96f)
    val dockBorderColor = if (isDarkTheme) Color(0xFF334155).copy(alpha = 0.55f) else Color(0xFFCBD5E1).copy(alpha = 0.70f)
    val dockShape = RoundedCornerShape(24.dp)

    val motionAnimSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)
    val navOffsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 150f,
        animationSpec = motionAnimSpec,
        label = "nav_offset_y"
    )
    val navAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = motionAnimSpec,
        label = "nav_alpha"
    )

    if (navAlpha > 0.02f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                .graphicsLayer {
                    translationY = navOffsetY
                    alpha = navAlpha
                }
        ) {
            // Halo de color bajo el dock (glow de la pestaña activa)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.55f }
                    .shadow(
                        elevation = 26.dp,
                        shape = dockShape,
                        ambientColor = activeColor.copy(alpha = 0.55f),
                        spotColor = activeColor.copy(alpha = 0.55f)
                    )
                    .clip(dockShape)
                    .background(Color.Transparent)
            )

            // Cuerpo del dock: vidrio redondeado con borde luminoso
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = dockShape,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = activeColor.copy(alpha = 0.35f)
                    )
                    .clip(dockShape)
                    .background(dockBg)
                    .border(1.dp, dockBorderColor, dockShape)
                    .testTag("bottom_nav")
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val cellWidth = maxWidth / 3
                    val notchWidth = 26.dp

                    // Muesca luminosa que viaja por el borde superior hasta la pestaña activa
                    val notchX by animateDpAsState(
                        targetValue = cellWidth * currentPage + (cellWidth - notchWidth) / 2,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 380f
                        ),
                        label = "notch_x"
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = notchX)
                            .padding(top = 0.dp)
                            .width(notchWidth)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        activeColor.copy(alpha = 0.25f),
                                        activeColor,
                                        activeColor.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        ScreenRoute.entries.forEach { screen ->
                            val isSelected = currentPage == screen.pageIndex
                            DockNavItem(
                                screen = screen,
                                isSelected = isSelected,
                                isDarkTheme = isDarkTheme,
                                activeColor = activeColor,
                                inactiveColor = inactiveColor,
                                downloadsCount = downloadsCount,
                                onNavigate = onNavigate,
                                haptics = haptics,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockNavItem(
    screen: ScreenRoute,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    downloadsCount: Int,
    onNavigate: (Int) -> Unit,
    haptics: AppHaptics,
    modifier: Modifier = Modifier
) {
    val isDownloadsTab = screen == ScreenRoute.DOWNLOADS
    val hasActiveDownloads = isDownloadsTab && downloadsCount > 0
    val pillShape = RoundedCornerShape(18.dp)

    // Píldora morfeante: círculo compacto cuando está inactiva, ancha con etiqueta al activarse
    val pillWidth by animateDpAsState(
        targetValue = if (isSelected) 96.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
        label = "pill_width"
    )
    val pillColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Color.Transparent,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "pill_color"
    )

    // Rebote elástico del icono al ganar la selección
    val iconBounce = remember { Animatable(1f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            iconBounce.snapTo(0.62f)
            iconBounce.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Respiración sutil del icono activo
    val breath = rememberInfiniteTransition(label = "dock_item_breath")
    val breathScale by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dock_breath"
    )

    // Onda expansiva del badge de descargas
    val ripple = rememberInfiniteTransition(label = "dock_badge_ripple")
    val rippleScale by ripple.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dock_ripple_scale"
    )
    val rippleAlpha by ripple.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dock_ripple_alpha"
    )
    val pulseScale by ripple.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dock_pulse"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) activeColor.contrastingTextColor() else inactiveColor,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "dock_item_content"
    )
    val dockBg = if (isDarkTheme) Color(0xFF0F172A) else Color.White

    Box(
        modifier = modifier
            .testTag("bottom_nav_item_${screen.pageIndex}")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.tick()
                onNavigate(screen.pageIndex)
            },
        contentAlignment = Alignment.Center
    ) {
        // Píldora con degradado + glow propio
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(46.dp)
                .shadow(
                    elevation = if (isSelected) 10.dp else 0.dp,
                    shape = pillShape,
                    ambientColor = Color.Transparent,
                    spotColor = activeColor.copy(alpha = 0.5f)
                )
                .clip(pillShape)
                .background(
                    if (isSelected) {
                        Brush.horizontalGradient(
                            listOf(
                                activeColor,
                                activeColor.copy(alpha = 0.72f)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (hasActiveDownloads) {
                        // Flecha de descarga animada estilo sistema mientras hay descargas activas
                        SystemDownloadAnimatedIcon(
                            tint = contentColor,
                            modifier = Modifier.size(23.dp)
                        )
                    } else {
                        Icon(
                            imageVector = screen.selectedIcon,
                            contentDescription = screen.title,
                            tint = contentColor,
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    scaleX = iconBounce.value * (if (isSelected) breathScale else 1f)
                                    scaleY = iconBounce.value * (if (isSelected) breathScale else 1f)
                                }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isSelected,
                    enter = expandHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkHorizontally(
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(140))
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = screen.title,
                            color = contentColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                }
            }

            // Badge de descargas activas sobre el extremo del icono
            if (hasActiveDownloads) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .scale(rippleScale)
                            .clip(CircleShape)
                            .background(activeColor.copy(alpha = rippleAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                            .border(1.5.dp, dockBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (downloadsCount > 99) "99+" else downloadsCount.toString(),
                            color = activeColor.contrastingTextColor(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both
                                ),
                                lineHeight = 10.sp
                            )
                        )
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
fun SystemDownloadAnimatedIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "system_download_arrow")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "system_download_anim"
    )

    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        // 1. System Tray at bottom: |_|
        val trayLeft = w * 0.20f
        val trayRight = w * 0.80f
        val trayBottom = h * 0.82f
        val lipHeight = 3.5.dp.toPx()

        // Tray base line
        drawLine(
            color = tint,
            start = Offset(trayLeft, trayBottom),
            end = Offset(trayRight, trayBottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Left lip
        drawLine(
            color = tint,
            start = Offset(trayLeft, trayBottom),
            end = Offset(trayLeft, trayBottom - lipHeight),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Right lip
        drawLine(
            color = tint,
            start = Offset(trayRight, trayBottom),
            end = Offset(trayRight, trayBottom - lipHeight),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // 2. Animated Downward Arrow: moves from top towards tray, fading in and then fading out
        val startY = h * 0.20f
        val endY = h * 0.65f
        val arrowTipY = startY + (endY - startY) * animProgress

        val alpha = when {
            animProgress < 0.20f -> (animProgress / 0.20f).coerceIn(0f, 1f)
            animProgress > 0.80f -> ((1f - animProgress) / 0.20f).coerceIn(0f, 1f)
            else -> 1f
        }
        val arrowColor = tint.copy(alpha = alpha)

        val centerX = w * 0.5f
        val stemLength = 8.dp.toPx()
        val arrowHeadWidth = 4.5.dp.toPx()
        val arrowHeadHeight = 4.5.dp.toPx()

        // Vertical arrow shaft
        drawLine(
            color = arrowColor,
            start = Offset(centerX, arrowTipY - stemLength),
            end = Offset(centerX, arrowTipY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Left arrowhead wing
        drawLine(
            color = arrowColor,
            start = Offset(centerX - arrowHeadWidth, arrowTipY - arrowHeadHeight),
            end = Offset(centerX, arrowTipY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Right arrowhead wing
        drawLine(
            color = arrowColor,
            start = Offset(centerX + arrowHeadWidth, arrowTipY - arrowHeadHeight),
            end = Offset(centerX, arrowTipY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
