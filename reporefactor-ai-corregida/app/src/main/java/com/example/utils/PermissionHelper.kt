package com.example.utils

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * Devuelve la lista de permisos requeridos por el dispositivo actual
     * según su versión de Android.
     */
    fun getRequiredAppPermissions(): List<String> {
        // Android 13+ requiere permiso runtime para publicar notificaciones.
        // Las versiones anteriores no tienen este permiso runtime.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    }

    /**
     * Verifica si se tienen todos los permisos necesarios
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getRequiredAppPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Comprueba si las notificaciones están activas
     */
    fun hasNotificationPermission(context: Context): Boolean {
        val areEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!areEnabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            val channelIds = listOf(
                NotificationUtils.CHANNEL_PROGRESS_ID,
                NotificationUtils.CHANNEL_SUCCESS_ID,
                NotificationUtils.CHANNEL_ERROR_ID
            )
            if (channelIds.any { manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }) {
                return false
            }
        }
        return true
    }

    /**
     * Abre los ajustes específicos de notificaciones de la aplicación
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Comprueba si los permisos de almacenamiento están concedidos
     */
    fun hasStoragePermission(context: Context): Boolean {
        // El directorio externo app-specific no requiere permisos de lectura multimedia.
        // El selector SAF otorga acceso URI puntual a carpetas elegidas por el usuario.
        return true
    }

    /**
     * Abre los ajustes de la aplicación para conceder permisos manualmente
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
