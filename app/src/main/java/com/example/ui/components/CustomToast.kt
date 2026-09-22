package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ToastType {
    SUCCESS,
    INFO,
    WARNING,
    ERROR,
    DOWNLOAD
}

data class ToastData(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val durationMillis: Long = 2800L,
    val id: Long = System.currentTimeMillis()
)

object AppToastManager {
    private val _currentToast = MutableStateFlow<ToastData?>(null)
    val currentToast = _currentToast.asStateFlow()

    private var dismissJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    fun show(message: String, type: ToastType = ToastType.INFO, durationMillis: Long = 2800L) {
        scope.launch {
            dismissJob?.cancel()
            _currentToast.value = ToastData(message, type, durationMillis, System.currentTimeMillis())
            dismissJob = launch {
                delay(durationMillis)
                _currentToast.value = null
            }
        }
    }

    fun dismiss() {
        scope.launch {
            dismissJob?.cancel()
            _currentToast.value = null
        }
    }
}

/**
 * Highly polished, executive animated toast host with countdown indicator,
 * spring-physics overshoot animations, swipe-to-dismiss, and modern glass styling.
 */
@Composable
fun CustomToastHost(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val toastData by AppToastManager.currentToast.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .zIndex(9999f),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toastData != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(180)) + scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(180)) + scaleOut(
                targetScale = 0.92f,
                animationSpec = tween(220)
            )
        ) {
            toastData?.let { data ->
                val primaryTheme = MaterialTheme.colorScheme.primary
                val (icon: ImageVector, accentColor: Color) = when (data.type) {
                    ToastType.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF10B981)
                    ToastType.DOWNLOAD -> Icons.Default.Download to primaryTheme
                    ToastType.WARNING -> Icons.Default.WarningAmber to Color(0xFFF59E0B)
                    ToastType.ERROR -> Icons.Default.ErrorOutline to Color(0xFFEF4444)
                    ToastType.INFO -> Icons.Default.Info to primaryTheme
                }

                // Architectural glass colors with subtle gradient
                val bgColor = if (isDarkTheme) Color(0xF2090F1D) else Color(0xFAF8FAFC)
                val borderColor = if (isDarkTheme) {
                    accentColor.copy(alpha = 0.35f)
                } else {
                    accentColor.copy(alpha = 0.28f)
                }
                val textColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)

                // Countdown animation for the bottom progress indicator
                val progress = remember(data.id) { Animatable(1f) }
                LaunchedEffect(data.id) {
                    progress.snapTo(1f)
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = data.durationMillis.toInt(),
                            easing = LinearEasing
                        )
                    )
                }

                // Icon micro-pulse animation
                val pulseTransition = rememberInfiniteTransition(label = "toast_icon_pulse")
                val iconScale by pulseTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor,
                    border = BorderStroke(1.2.dp, borderColor),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = accentColor.copy(alpha = if (isDarkTheme) 0.35f else 0.20f),
                            ambientColor = Color.Black.copy(alpha = 0.15f)
                        )
                        .pointerInput(data.id) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -15f) {
                                    AppToastManager.dismiss()
                                }
                            }
                        }
                        .clickable { AppToastManager.dismiss() }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Status icon with soft glowing badge
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .scale(iconScale)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.copy(alpha = if (isDarkTheme) 0.18f else 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Message text
                            Text(
                                text = data.message,
                                color = textColor,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )

                            // Quick dismiss icon
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = if (isDarkTheme) Color(0xFF64748B) else Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { AppToastManager.dismiss() }
                            )
                        }

                        // Smooth countdown progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(accentColor.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = progress.value)
                                    .height(2.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                accentColor.copy(alpha = 0.6f),
                                                accentColor
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
