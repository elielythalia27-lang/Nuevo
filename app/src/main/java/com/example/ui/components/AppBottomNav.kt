package com.example.ui.components

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DownloadFreeTeal = Color(0xFF14B8A6)
private val DownloadFreeLightTeal = Color(0xFF00695C)
private val NavigationInk = Color(0xFF0B1020)

enum class ScreenRoute(
    val route: String,
    val title: String,
    val pageIndex: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Catálogo", 0, Icons.Rounded.Movie, Icons.Outlined.Movie),
    DOWNLOADS("downloads", "Descargas", 1, Icons.Rounded.CloudDownload, Icons.Outlined.CloudDownload),
    SETTINGS("settings", "Ajustes", 2, Icons.Rounded.Tune, Icons.Outlined.Tune)
}

/**
 * Shared preference used by Compose animations in the navigation. Android's "Remove animations"
 * setting writes zero to the global animator/transition scales; observing those settings lets the
 * dock switch to snap transitions immediately, even if the preference changes while the app is open.
 */
@Composable
fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    var reduceMotion by remember(context) {
        mutableStateOf(readReduceMotionPreference(context))
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                reduceMotion = readReduceMotionPreference(context)
            }
        }
        val resolver = context.contentResolver
        val preferenceUris = listOf(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE)
        ).distinct()
        preferenceUris.forEach { resolver.registerContentObserver(it, false, observer) }

        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return reduceMotion
}

private fun readReduceMotionPreference(context: Context): Boolean = runCatching {
    val resolver = context.contentResolver
    val animatorScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    val transitionScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    )
    val windowScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.WINDOW_ANIMATION_SCALE,
        1f
    )
    animatorScale <= 0f || transitionScale <= 0f || windowScale <= 0f
}.getOrDefault(false)

/** Keeps the bottom navigation visible; scroll callbacks are retained for existing screen APIs. */
class BottomBarScrollBehavior(private val thresholdPx: Float = 0f) : NestedScrollConnection {
    var isVisible by mutableStateOf(true)
        private set
    var atEnd by mutableStateOf(false)
    var isChangingPage by mutableStateOf(false)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!isVisible) isVisible = true
        return Offset.Zero
    }

    fun show() {
        isVisible = true
    }
}

/**
 * Legacy scroll connection retained for compatibility with screens that still expose this API.
 * The app's dock itself remains visible, as requested.
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
                val deltaY = available.y
                if (!currentCanScrollBackward() && !currentCanScrollForward()) {
                    currentOnVisibilityChange(true)
                    accumulatedDelta = 0f
                    return Offset.Zero
                }
                if (!currentCanScrollBackward()) {
                    currentOnVisibilityChange(true)
                    accumulatedDelta = 0f
                    return Offset.Zero
                }
                if ((deltaY > 0f && accumulatedDelta < 0f) ||
                    (deltaY < 0f && accumulatedDelta > 0f)
                ) {
                    accumulatedDelta = 0f
                }
                accumulatedDelta += deltaY
                if (accumulatedDelta < -thresholdPx) {
                    currentOnVisibilityChange(false)
                    accumulatedDelta = 0f
                } else if (accumulatedDelta > thresholdPx) {
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
 * Three-item floating glass dock. The lateral tabs keep their labels visible, while the center
 * downloads action rises above the rail and carries the live queue badge and aggregate progress.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AppBottomNav(
    currentPage: Int,
    onNavigate: (Int) -> Unit,
    downloadsCount: Int,
    isDarkTheme: Boolean = true,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier,
    downloadProgress: Float? = null,
    systemReduceMotion: Boolean? = null
) {
    val reducedMotion = if (systemReduceMotion != null) {
        systemReduceMotion
    } else {
        rememberReduceMotionEnabled()
    }
    val haptics = rememberHaptics()
    val accentColor = if (isDarkTheme) DownloadFreeTeal else DownloadFreeLightTeal
    val inactiveColor = if (isDarkTheme) Color(0xFF9BAABD) else Color(0xFF526174)
    val dockColor = if (isDarkTheme) {
        NavigationInk.copy(alpha = 0.66f)
    } else {
        Color.White.copy(alpha = 0.88f)
    }
    val dockBorderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color(0xFF0F172A).copy(alpha = 0.10f)
    }
    val dockShape = RoundedCornerShape(32.dp)
    val safePage = currentPage.coerceIn(0, ScreenRoute.entries.lastIndex)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .height(76.dp)
    ) {
        val cellWidth = maxWidth / 3

        // One persistent indicator glides across all three slots; on Downloads its exposed edges
        // frame the circular action instead of replacing it with a second, competing button.
        val targetPillWidth = if (safePage == ScreenRoute.DOWNLOADS.pageIndex) {
            66.dp
        } else {
            (cellWidth - 8.dp).coerceAtMost(104.dp).coerceAtLeast(64.dp)
        }
        val targetPillX = cellWidth * safePage + (cellWidth - targetPillWidth) / 2
        val pillWidth by animateDpAsState(
            targetValue = targetPillWidth,
            animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.78f, stiffness = 430f),
            label = "nav_selection_width"
        )
        val pillX by animateDpAsState(
            targetValue = targetPillX,
            animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.78f, stiffness = 430f),
            label = "nav_selection_x"
        )

        // The rail is 64dp high; the 52dp center button starts 8dp above it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = dockShape,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(dockShape)
                .background(dockColor)
                .border(1.dp, dockBorderColor, dockShape)
                .testTag("bottom_nav")
        ) {
            // Subtle top sheen gives the translucent surface a glass finish without a heavy rim.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDarkTheme) 0.045f else 0.24f),
                                Color.Transparent
                            )
                        )
                    )
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val railCellWidth = maxWidth / 3
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = pillX, y = 8.dp)
                        .width(pillWidth)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockSideNavItem(
                        screen = ScreenRoute.HOME,
                        isSelected = safePage == ScreenRoute.HOME.pageIndex,
                        accentColor = accentColor,
                        inactiveColor = inactiveColor,
                        reducedMotion = reducedMotion,
                        onNavigate = onNavigate,
                        haptics = haptics,
                        modifier = Modifier
                            .width(railCellWidth)
                            .fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .width(railCellWidth)
                            .fillMaxHeight()
                    )
                    DockSideNavItem(
                        screen = ScreenRoute.SETTINGS,
                        isSelected = safePage == ScreenRoute.SETTINGS.pageIndex,
                        accentColor = accentColor,
                        inactiveColor = inactiveColor,
                        reducedMotion = reducedMotion,
                        onNavigate = onNavigate,
                        haptics = haptics,
                        modifier = Modifier
                            .width(railCellWidth)
                            .fillMaxHeight()
                    )
                }
            }
        }

        DockDownloadsButton(
            isSelected = safePage == ScreenRoute.DOWNLOADS.pageIndex,
            downloadsCount = downloadsCount.coerceAtLeast(0),
            downloadProgress = downloadProgress?.coerceIn(0f, 1f),
            accentColor = accentColor,
            inactiveColor = inactiveColor,
            isDarkTheme = isDarkTheme,
            reducedMotion = reducedMotion,
            onNavigate = onNavigate,
            haptics = haptics,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(cellWidth)
                .height(76.dp)
        )
    }
}

@Composable
private fun DockSideNavItem(
    screen: ScreenRoute,
    isSelected: Boolean,
    accentColor: Color,
    inactiveColor: Color,
    reducedMotion: Boolean,
    onNavigate: (Int) -> Unit,
    haptics: AppHaptics,
    modifier: Modifier = Modifier
) {
    val iconScale = rememberSelectionBounce(isSelected, reducedMotion)
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) accentColor else inactiveColor,
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "${screen.route}_icon_tint"
    )
    val labelTint by animateColorAsState(
        targetValue = if (isSelected) accentColor else inactiveColor,
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "${screen.route}_label_tint"
    )

    Box(
        modifier = modifier
            .testTag("bottom_nav_item_${screen.pageIndex}")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Abrir ${screen.title}",
                role = Role.Tab
            ) {
                haptics.tick()
                onNavigate(screen.pageIndex)
            }
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                contentDescription = screen.title,
                tint = iconTint,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = screen.title,
                modifier = Modifier.clearAndSetSemantics { },
                color = labelTint,
                fontSize = if (isSelected) 11.sp else 10.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun DockDownloadsButton(
    isSelected: Boolean,
    downloadsCount: Int,
    downloadProgress: Float?,
    accentColor: Color,
    inactiveColor: Color,
    isDarkTheme: Boolean,
    reducedMotion: Boolean,
    onNavigate: (Int) -> Unit,
    haptics: AppHaptics,
    modifier: Modifier = Modifier
) {
    val iconScale = rememberSelectionBounce(isSelected, reducedMotion)
    val contentOnAccent = if (isDarkTheme) Color(0xFF062522) else Color.White
    val circleOutline = if (isDarkTheme) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color(0xFF0F172A).copy(alpha = 0.12f)
    }
    val stateDescription = when (downloadsCount) {
        0 -> "Sin descargas en curso o en cola"
        1 -> "1 descarga en curso o en cola"
        else -> "$downloadsCount descargas en curso o en cola"
    }

    Column(
        modifier = modifier
            .testTag("bottom_nav_item_${ScreenRoute.DOWNLOADS.pageIndex}")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Abrir Descargas",
                role = Role.Tab
            ) {
                haptics.tick()
                onNavigate(ScreenRoute.DOWNLOADS.pageIndex)
            }
            .semantics {
                selected = isSelected
                this.stateDescription = stateDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            if (downloadProgress != null) {
                val ringProgress by animateFloatAsState(
                    targetValue = downloadProgress,
                    animationSpec = if (reducedMotion) snap() else tween(240),
                    label = "active_download_progress_ring"
                )
                DownloadProgressRing(
                    progress = ringProgress,
                    accentColor = accentColor,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .then(
                        if (isSelected) {
                            Modifier.shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = accentColor.copy(alpha = 0.52f),
                                spotColor = accentColor.copy(alpha = 0.62f)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .then(
                        if (!isSelected) Modifier.border(1.dp, circleOutline, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) {
                        ScreenRoute.DOWNLOADS.selectedIcon
                    } else {
                        ScreenRoute.DOWNLOADS.unselectedIcon
                    },
                    contentDescription = ScreenRoute.DOWNLOADS.title,
                    tint = if (isSelected) contentOnAccent else inactiveColor,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }

            DownloadCountBadge(
                count = downloadsCount,
                accentColor = accentColor,
                isDarkTheme = isDarkTheme,
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = 1.dp)
            )
        }

        Text(
            text = ScreenRoute.DOWNLOADS.title,
            modifier = Modifier
                .height(16.dp)
                .padding(top = 1.dp)
                .clearAndSetSemantics { },
            color = if (isSelected) accentColor else inactiveColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DownloadCountBadge(
    count: Int,
    accentColor: Color,
    isDarkTheme: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeText = if (count > 99) "99+" else count.toString()
    val badgeTextColor = if (isDarkTheme) Color(0xFF062522) else Color.White
    val badgeBorder = if (isDarkTheme) NavigationInk else Color.White

    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            scaleIn(
                initialScale = 0.55f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(130))
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            scaleOut(targetScale = 0.70f, animationSpec = tween(100)) +
                fadeOut(animationSpec = tween(90))
        }
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(accentColor)
                .border(1.5.dp, badgeBorder, CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = badgeText,
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (
                            scaleIn(
                                initialScale = 0.68f,
                                animationSpec = spring(
                                    dampingRatio = 0.72f,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeIn(animationSpec = tween(110))
                        ) togetherWith (
                            scaleOut(targetScale = 0.72f, animationSpec = tween(90)) +
                                fadeOut(animationSpec = tween(80))
                        )
                    }
                },
                label = "download_count_pop"
            ) { displayedCount ->
                Text(
                    text = displayedCount,
                    color = badgeTextColor,
                    fontSize = if (displayedCount.length > 2) 7.5.sp else 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DownloadProgressRing(
    progress: Float,
    accentColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.17f)
    } else {
        Color(0xFF0F172A).copy(alpha = 0.16f)
    }

    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val arcSize = Size(diameter, diameter)
        val arcTopLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = accentColor,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun rememberSelectionBounce(
    isSelected: Boolean,
    reducedMotion: Boolean
): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isSelected, reducedMotion) {
        if (!isSelected || reducedMotion) {
            scale.snapTo(1f)
        } else {
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = 1.15f,
                animationSpec = spring(
                    dampingRatio = 0.62f,
                    stiffness = 720f
                )
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.70f,
                    stiffness = 760f
                )
            )
        }
    }
    return scale.value
}
