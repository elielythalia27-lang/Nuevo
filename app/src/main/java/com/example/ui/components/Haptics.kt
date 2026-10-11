package com.example.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Sistema central de respuestas hápticas de la aplicación.
 *
 * Devuelve un [AppHaptics] ligero ligado a la vista actual. Usar patrones cortos y
 * sutiles: vibraciones fuertes se sienten baratas; los "ticks" casi imperceptibles
 * son los que dan sensación de calidad premium.
 */
class AppHaptics(private val view: View) {

    /** Tick corto y ligero: cambiar de pestaña, seleccionar un chip, play/pausa. */
    fun tick() {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Pulsación firme y agradable: iniciar descarga, confirmar una acción. */
    fun confirm() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    /** Doble tick sutil: alternar favorito, completar una descarga. */
    fun doubleTick() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            view.postDelayed({
                runCatching {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }, 70L)
        }
    }

    /** Aviso suave de error o rechazo: acción bloqueada, fallo. */
    fun reject() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /** Gesto de arrastre/seek: tick mínimo en cada salto de 10 s. */
    fun gestureTick() {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

@Composable
fun rememberHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}
