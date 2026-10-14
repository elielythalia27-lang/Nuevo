package com.example.utils

import android.app.Activity
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
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization status", e)
            false
        }
    }

    /**
     * Devuelve las pantallas del sistema adecuadas para cambiar la exención real.
     * Para activar, intenta primero el diálogo específico; para desactivar, abre la lista
     * del sistema (Android no permite revocar esta exención directamente desde una app).
     */
    fun getBatterySettingsIntents(context: Context, enableExemption: Boolean): List<Intent> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()

        val packageUri = Uri.fromParts("package", context.packageName, null)
        val batteryList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = packageUri
        }
        if (!enableExemption) return listOf(batteryList, appDetails)

        val requestExemption = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = packageUri
        }
        return listOf(requestExemption, batteryList, appDetails)
    }

    /** Compatibilidad para llamadas fuera de Compose; la UI principal usa ActivityResultLauncher. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intents = getBatterySettingsIntents(context, enableExemption = true)
        for (intent in intents) {
            try {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo abrir ${intent.action}; probando la siguiente pantalla", e)
            }
        }
        openAppDetailsSettings(context)
    }

    /**
     * Abre los ajustes generales de la aplicación (ACTION_APPLICATION_DETAILS_SETTINGS).
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
