package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val startFractionX: Float,   // 0..1 posición horizontal inicial
    val launchAngle: Float,      // radianes del disparo inicial
    val launchPower: Float,      // 0..1 fuerza del disparo
    val fallDelayMs: Int,        // escalonado para que caigan en cascada
    val fallDurationMs: Int,
    val sizePx: Float,
    val color: Color,
    val swayFrequency: Float,
    val swayAmplitudePx: Float,
    val rotationSpeed: Float,    // vueltas durante la caída
    val isCircle: Boolean
)

/**
 * Explosión de confeti premium: las partículas salen disparadas desde un punto,
 * describe un arco con la gravedad, balanceo lateral tipo papel y rotación continua,
 * y se desvanecen al final. Se dispara cada vez que [trigger] cambia a un valor mayor.
 *
 * No bloquea la entrada táctil: solo dibuja.
 */
@Composable
fun ConfettiOverlay(
    trigger: Int,
    originFraction: Offset = Offset(0.5f, 0.35f),
    modifier: Modifier = Modifier
) {
    if (trigger <= 0) return
    val progress = remember(trigger) { Animatable(0f) }

    // Paleta festiva coherente con la app: teal, ámbar, esmeralda, cielo, rosa, violeta
    val palette = remember {
        listOf(
            Color(0xFF14B8A6), Color(0xFF2DD4BF), Color(0xFFF59E0B),
            Color(0xFFFBBF24), Color(0xFF10B981), Color(0xFF34D399),
            Color(0xFF38BDF8), Color(0xFF60A5FA), Color(0xFFF472B6),
            Color(0xFFA78BFA), Color(0xFFF8FAFC)
        )
    }

    val density = LocalDensity.current
    val particles = remember(trigger) {
        val rnd = Random(trigger)
        List(64) {
            ConfettiParticle(
                startFractionX = originFraction.x + (rnd.nextFloat() - 0.5f) * 0.06f,
                launchAngle = (-PI.toFloat() / 2f) + (rnd.nextFloat() - 0.5f) * 2.2f,
                launchPower = 0.55f + rnd.nextFloat() * 0.45f,
                fallDelayMs = (rnd.nextFloat() * 260f).toInt(),
                fallDurationMs = 1300 + (rnd.nextFloat() * 900f).toInt(),
                sizePx = with(density) { (4.dp.toPx() + rnd.nextFloat() * 4.dp.toPx()) },
                color = palette[rnd.nextInt(palette.size)],
                swayFrequency = 1.5f + rnd.nextFloat() * 3f,
                swayAmplitudePx = with(density) { (6.dp.toPx() + rnd.nextFloat() * 14.dp.toPx()) },
                rotationSpeed = (rnd.nextFloat() - 0.5f) * 900f,
                isCircle = rnd.nextBoolean()
            )
        }
    }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2400,
                easing = CubicBezierEasing(0.3f, 0f, 0.6f, 1f)
            )
        )
    }

    val p = progress.value
    if (p >= 1f) return

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val originY = h * originFraction.y

            particles.forEach { particle ->
                // Fase 1 — disparo balístico (primeros ~28% del tiempo), Fase 2 — caída con balanceo
                val totalMs = particle.fallDelayMs + particle.fallDurationMs
                val elapsed = p * totalMs
                if (elapsed < particle.fallDelayMs) return@forEach

                val t = ((elapsed - particle.fallDelayMs) / particle.fallDurationMs).coerceIn(0f, 1f)

                val launchPx = particle.launchPower * h * 0.22f
                val ballisticY = originY - sin((PI / 2f).toFloat() + particle.launchAngle).let { abs(it) } * launchPx * (1f - t * 2.2f).coerceAtLeast(0f)
                val gravityY = t * t * h * 0.85f
                val y = ballisticY + gravityY

                val sway = sin(t * particle.swayFrequency * 2f * PI.toFloat()) * particle.swayAmplitudePx * t
                val x = w * particle.startFractionX + cos(particle.launchAngle) * launchPx * (1f - t * 2.2f).coerceAtLeast(0f) * 0.6f + sway

                // Desvanecido elegante al final de la caída
                val alpha = when {
                    t > 0.82f -> ((1f - t) / 0.18f).coerceIn(0f, 1f)
                    else -> 1f
                }
                if (alpha <= 0.01f) return@forEach

                val rotation = particle.rotationSpeed * t
                rotate(degrees = rotation, pivot = Offset(x, y)) {
                    val half = particle.sizePx / 2f
                    if (particle.isCircle) {
                        drawCircle(
                            color = particle.color.copy(alpha = alpha),
                            radius = half,
                            center = Offset(x, y),
                            style = Fill
                        )
                    } else {
                        // Rectángulo tipo trozo de papel, un poco más alto que ancho
                        drawRect(
                            color = particle.color.copy(alpha = alpha),
                            topLeft = Offset(x - half * 0.7f, y - half),
                            size = androidx.compose.ui.geometry.Size(half * 1.4f, half * 2f),
                            style = Fill
                        )
                    }
                }
            }
        }
    }
}
