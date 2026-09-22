package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R

/**
 * Modern Pull-to-Refresh container powered by Lottie animation.
 *
 * - When dragging: progress directly drives the Lottie animation frame (0% -> 100%).
 * - When refreshing: plays an infinite, smooth Lottie loop with a sleek indicator pill.
 * - Respects dynamic theme colors and provides responsive tactile feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LottiePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    isDarkTheme: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            LottiePullToRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                isDarkTheme = isDarkTheme,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .pullToRefreshIndicator(
                        state = state,
                        isRefreshing = isRefreshing
                    )
            )
        },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LottiePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    // Load Lottie animation composition
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.pull_to_refresh)
    )

    // When refreshing, loop endlessly; when pulling, progress is bound to pull distance
    val loopProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = isRefreshing,
        speed = 1.25f
    )

    val pullProgress by remember(state.distanceFraction) {
        derivedStateOf { state.distanceFraction.coerceIn(0f, 1f) }
    }

    // Determine current progress to display
    val currentProgress = if (isRefreshing) loopProgress else pullProgress

    // Animate scale in/out for sleek entrance
    val isVisible = isRefreshing || state.distanceFraction > 0.1f
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lottie_indicator_scale"
    )

    val containerBg = if (isDarkTheme) {
        Color(0xEE0F172A) // 93% opacity dark slate
    } else {
        Color(0xFAFFFFFF) // 98% opacity clean pearl white
    }

    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.6f),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.6f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = containerBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = 6.dp,
            modifier = Modifier
                .scale(scale)
                .padding(top = 12.dp)
                .testTag("lottie_pull_to_refresh_indicator")
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Lottie Animation view
                LottieAnimation(
                    composition = composition,
                    progress = { currentProgress },
                    modifier = Modifier.size(32.dp)
                )

                if (isRefreshing) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Actualizando...",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        letterSpacing = 0.2.sp
                    )
                } else if (state.distanceFraction >= 0.95f) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Soltar para recargar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}
