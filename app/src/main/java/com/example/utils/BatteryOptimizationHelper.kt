package com.example.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Gestor centralizado para la exención de optimizaciones de batería
 * y configuración de segundo plano en Android y fabricantes con capas agresivas
 * (Xiaomi/MIUI/HyperOS, OPPO/ColorOS, Realme, Vivo, Huawei, etc.).
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    /**
     * Comprueba si la aplicación está exenta de las optimizaciones de batería
     * (retorna true si está en modo "Sin restricciones").
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization status", e)
            true
        }
    }

    /**
     * Solicita la exención de optimización de batería.
     * Intenta primero el diálogo directo ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS con el paquete de la app.
     * Si no está disponible o falla, abre la pantalla general ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, trying settings screen fallback", e)
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS also failed, opening app details", fallbackEx)
                    openAppDetailsSettings(context)
                }
            }
        } else {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Abre los ajustes generales de la aplicación (ACTION_APPLICATION_DETAILS_SETTINGS).
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open application details settings", e)
        }
    }

    /**
     * Para fabricantes con gestión agresiva de segundo plano (Xiaomi, Oppo, Realme, Vivo, Huawei, etc.),
     * intenta abrir primero la pantalla propia del fabricante para 'Inicio automático' y 'Gestión de energía'.
     * Si no está disponible o el dispositivo no cuenta con esa pantalla específica, usa los ajustes de la app.
     */
    fun openAutoStartOrAppDetails(context: Context) {
        val autoStartIntents = listOf(
            // Xiaomi / Redmi / POCO (MIUI / HyperOS)
            Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            },
            Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
            },
            // OPPO / Realme (ColorOS / Realme UI)
            Intent().apply {
                component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            },
            Intent().apply {
                component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            },
            Intent().apply {
                component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            },
            Intent().apply {
                component = ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
            },
            // Vivo / iQOO (FuntouchOS / OriginOS)
            Intent().apply {
                component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            },
            Intent().apply {
                component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
            },
            Intent().apply {
                component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            },
            // Huawei / Honor (EMUI / MagicOS)
            Intent().apply {
                component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            },
            Intent().apply {
                component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            },
            Intent().apply {
                component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            },
            // Samsung Device Care / Battery
            Intent().apply {
                component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            },
            // Transsion (Infinix, Tecno, Itel)
            Intent().apply {
                component = ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoRunListActivity")
            },
            // Asus
            Intent().apply {
                component = ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings")
            }
        )

        for (intent in autoStartIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val resolved = context.packageManager.resolveActivity(intent, 0)
                if (resolved != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {
                // Continuar con la siguiente opción
            }
        }

        // Si ninguna actividad específica de inicio automático se pudo resolver, abrir los ajustes de la app
        openAppDetailsSettings(context)
    }
}
