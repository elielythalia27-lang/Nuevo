package com.example.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * Devuelve la lista de permisos requeridos por el dispositivo actual
     * según su versión de Android.
     */
    fun getRequiredAppPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // Notificaciones (Android 13+ / API 33+) para el servicio en primer plano
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // Android 10 o inferior
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions
    }

    /**
     * Verifica si se tienen todos los permisos necesarios
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        for (permission in getRequiredAppPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * Comprueba si las notificaciones están activas
     */
    fun hasNotificationPermission(context: Context): Boolean {
        val areEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!areEnabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
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
     * Devuelve los permisos de almacenamiento/multimedia según la versión de Android.
     * En Android 11+ el almacenamiento de descargas es específico de la aplicación (Scoped Storage),
     * por lo que no se requieren permisos amplios.
     */
    fun getStoragePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            emptyList()
        }
    }

    /**
     * Comprueba si los permisos de almacenamiento están concedidos.
     * En Android 11+ (API 30+) como en Redmi Note 14 Pro / Xiaomi HyperOS,
     * comprueba explícitamente si se tiene acceso a todos los archivos
     * (MANAGE_EXTERNAL_STORAGE) para escribir en la carpeta seleccionada.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            val writeGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            readGranted && writeGranted
        } else {
            true
        }
    }

    /**
     * Solicita los permisos de almacenamiento según la versión de Android.
     * En Android 11+ abre la pantalla de Acceso a todos los archivos.
     */
    fun requestStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (fallbackEx: Exception) {
                    openAppSettings(context)
                }
            }
        } else {
            openAppSettings(context)
        }
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
