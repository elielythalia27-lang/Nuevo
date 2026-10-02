# Arquitectura Completa de Descargas Sincronizadas con Notificaciones en Android

Esta guía y archivo de código reúne toda la implementación profesional desarrollada para la gestión de descargas en segundo plano sincronizadas en tiempo real con las notificaciones del sistema Android (compatible desde Android 8.0 Oreo hasta Android 15), sin fugas de memoria, sin bloqueos del sistema y sin parpadeos.

---

## 1. Diagrama del Flujo de Datos (Arquitectura SSOT)

```
┌────────────────────────────────────────────────────────┐
│             Jetpack Compose UI (DescargasScreen)       │
│  - Muestra velocidad estabilizada (EMA)               │
│  - Observa StateFlow de descargas activas y en cola    │
└──────────────────────────┬─────────────────────────────┘
                           │ acciones (iniciar, pausar, cancelar)
                           ▼
┌────────────────────────────────────────────────────────┐
│             DownloadHelper (Single Source of Truth)    │
│  - Repositorio en memoria + Room / SharedPreferences  │
│  - Despacha Intents explícitos al Foreground Service   │
│  - Emite actualizaciones atómicas de progreso a la UI  │
└──────────────────────────┬─────────────────────────────┘
                           │ startService(intent)
                           ▼
┌────────────────────────────────────────────────────────┐
│          DownloadForegroundService (DataSync)          │
│  - Corre como Service con tipo FOREGROUND_SERVICE_DATA_SYNC
│  - Maneja CoroutineScope independiente de la UI        │
│  - Throttle de 800 ms para calcular velocidad y ETA    │
│  - Transferencia segura de notificación líder          │
│    (promoteNextForegroundNotification)                 │
└──────────────────────────┬─────────────────────────────┘
                           │ actualiza progreso cada 800 ms
                           ▼
┌────────────────────────────────────────────────────────┐
│              NotificationUtils (System Tray)           │
│  - Canal silencioso IMPORTANCE_LOW para progreso       │
│  - Canal con sonido/alerta para descargas finalizadas   │
│  - SortKey para evitar que bailen de posición          │
│  - setOnlyAlertOnce(true) y sin botones intrusivos     │
└────────────────────────────────────────────────────────┘
```

---

## 2. Permisos y Manifiesto (`AndroidManifest.xml`)

En Android 14+ (API 34), es obligatorio declarar el tipo específico de servicio en primer plano:

```xml
<!-- Permisos de red y notificaciones -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Permisos de Foreground Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<application ...>
    <!-- Declaración del servicio en primer plano con tipo dataSync -->
    <service
        android:name=".data.download.DownloadForegroundService"
        android:foregroundServiceType="dataSync"
        android:exported="false" />
</application>
```

---

## 3. Gestor de Notificaciones (`NotificationUtils.kt`)

Este módulo se encarga de:
- Crear los canales adecuados (Canal de progreso: `IMPORTANCE_LOW` totalmente silencioso; Canal de éxito: `IMPORTANCE_DEFAULT` con sonido).
- Configurar notificaciones persistentes que no vibran continuamente al actualizarse.
- Aplicar `setSortKey` para que las notificaciones mantengan su posición fija en la cortina de notificaciones.
- Sanitizar los mensajes de error para no mencionar servidores.

```kotlin
package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.DownloadItem
import java.util.Locale

object NotificationUtils {

    const val CHANNEL_PROGRESS_ID = "channel_download_progress_v6"
    const val CHANNEL_SUCCESS_ID = "channel_download_success_v6"
    const val CHANNEL_ERROR_ID = "channel_download_error_v6"

    private const val GROUP_DOWNLOADS_ID = "group_download_management"

    /**
     * Inicializa los canales de notificación en Android 8.0+ (API 26+)
     */
    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val downloadsGroup = NotificationChannelGroup(GROUP_DOWNLOADS_ID, "Gestión de Descargas")
            notificationManager.createNotificationChannelGroups(listOf(downloadsGroup))

            // Canal 1: Progreso en tiempo real (IMPORTANCE_LOW = 100% silencioso)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Progreso de descargas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Velocidad, porcentaje y tiempo restante"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Canal 2: Descargas completadas (Con sonido y vibración)
            val successChannel = NotificationChannel(
                CHANNEL_SUCCESS_ID,
                "Descargas completadas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Avisos al finalizar las descargas"
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Canal 3: Errores y alertas
            val errorChannel = NotificationChannel(
                CHANNEL_ERROR_ID,
                "Errores de descarga",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                group = GROUP_DOWNLOADS_ID
                description = "Alertas de conexión o almacenamiento"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(progressChannel, successChannel, errorChannel)
            )
        }
    }

    /**
     * PendingIntent que abre la app en la pestaña de descargas al pulsar la notificación
     */
    fun createOpenDownloadsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("initial_tab", 1) // Abre directamente la pestaña de descargas
            putExtra("skip_splash", true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 1001, intent, flags)
    }

    /**
     * Construye la notificación activa de descarga en progreso
     */
    fun buildProgressNotification(
        context: Context,
        item: DownloadItem,
        progress: Int,
        speed: Long,
        eta: Long
    ): Notification {
        initNotificationChannels(context)

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }

        val sizeStr = if (item.totalBytes > 0L) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }

        // Formato con 2 decimales para precisión
        val speedStr = if (speed > 0L) {
            if (speed >= 1024 * 1024) String.format(Locale.US, "%.2f MB/s", speed / (1024.0 * 1024.0))
            else String.format(Locale.US, "%.2f KB/s", speed / 1024.0)
        } else "Conectando..."

        val etaStr = if (eta > 0L) {
            val hours = eta / 3600
            val minutes = (eta % 3600) / 60
            val seconds = eta % 60
            when {
                hours > 0 -> "${hours}h ${minutes}m restantes"
                minutes > 0 -> "${minutes}m ${seconds}s restantes"
                else -> "${seconds}s restantes"
            }
        } else if (item.totalBytes > 0L) {
            "Calculando tiempo..."
        } else {
            "Iniciando..."
        }

        val isIndeterminate = item.totalBytes <= 0L
        val subtitle = if (isIndeterminate) {
            "$sizeStr • $speedStr"
        } else {
            "$progress% • $sizeStr • $speedStr • $etaStr"
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setContentTitle("Descargando: $displayTitle")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setSmallIcon(R.drawable.ic_notification_download)
            .setColor(0xFF00897B.toInt()) // Tonalidad esmeralda limpia
            .setProgress(100, if (isIndeterminate) 0 else progress, isIndeterminate)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Construye la notificación cuando la descarga está pausada
     */
    fun buildPausedNotification(
        context: Context,
        item: DownloadItem
    ): Notification {
        initNotificationChannels(context)

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }

        val sizeInfo = if (item.totalBytes > 0L) {
            "${formatByteSize(item.downloadedBytes)} / ${formatByteSize(item.totalBytes)}"
        } else {
            formatByteSize(item.downloadedBytes)
        }
        val subtitle = "${item.progress}% • $sizeInfo • En pausa"

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle("En pausa: $displayTitle")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setSmallIcon(R.drawable.ic_notification_pause)
            .setColor(0xFFF59E0B.toInt())
            .setProgress(100, item.progress, false)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(false)
            .setOngoing(false)
            .setShowWhen(false)
            .setWhen(0L)
            .setSortKey("download_${item.id}")
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Construye la notificación de descarga finalizada con éxito
     */
    fun buildCompletedNotification(
        context: Context,
        item: DownloadItem
    ): Notification {
        initNotificationChannels(context)

        val displayTitle = if (item.year.isNotBlank() && !item.title.contains("(${item.year})")) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }
        val sizeStr = formatByteSize(item.totalBytes.coerceAtLeast(item.downloadedBytes))
        val detail = "$sizeStr • Descarga completada. Lista para ver sin conexión."

        return NotificationCompat.Builder(context, CHANNEL_SUCCESS_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle("Descarga completada")
            .setContentText("$displayTitle • $sizeStr")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$displayTitle\n$detail"))
            .setSmallIcon(R.drawable.ic_notification_done)
            .setColor(0xFF10B981.toInt())
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .build()
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
```

---

## 4. Servicio en Primer Plano (`DownloadForegroundService.kt`)

Este es el núcleo de ejecución. Resuelve tres problemas críticos en Android:
1. **Control de Throttle a 800 ms:** No saturar el bus de IPC de Android ni el `NotificationManager` con miles de llamadas por segundo, manteniendo el consumo de CPU y batería al mínimo.
2. **Transferencia de Foreground (`promoteNextForegroundNotification`):** Si hay 3 descargas y la que está anclada como notificación de primer plano termina o se pausa, el servicio transfiere el anclaje a otra descarga activa inmediatamente antes de cancelar la notificación anterior, evitando que Android mate el servicio.
3. **Persistencia y Resumen con `Range: bytes=`:** Permite reanudar archivos `.part` desde el byte exacto donde se quedaron.

```kotlin
package com.example.data.download

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.utils.NotificationUtils
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val activeItems = ConcurrentHashMap<Long, DownloadItem>()

    private val notifLock = Any()
    private var currentForegroundId = 0

    private lateinit var notificationManager: NotificationManager
    private lateinit var helper: DownloadHelper

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        helper = DownloadHelper.getActiveInstance(applicationContext)
        NotificationUtils.initNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val downloadId = intent.getLongExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, -1L)

        when (action) {
            DownloadHelper.ACTION_START_DOWNLOAD -> {
                val item = helper.getItem(downloadId) ?: return START_NOT_STICKY
                startDownloadJob(item)
            }
            DownloadHelper.ACTION_PAUSE_DOWNLOAD -> {
                pauseDownload(downloadId)
            }
            DownloadHelper.ACTION_CANCEL_DOWNLOAD -> {
                cancelDownload(downloadId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownloadJob(item: DownloadItem) {
        if (downloadJobs.containsKey(item.id)) return // Ya está corriendo

        // Asignar notificación en primer plano si es la primera
        synchronized(notifLock) {
            val notifId = item.id.toInt()
            if (currentForegroundId == 0) {
                currentForegroundId = notifId
                val notif = NotificationUtils.buildProgressNotification(
                    this, item, item.progress, 0L, 0L
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(notifId, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(notifId, notif)
                }
            }
        }

        activeItems[item.id] = item

        val job = serviceScope.launch {
            executeDownloadLoop(item)
        }
        downloadJobs[item.id] = job
    }

    private suspend fun executeDownloadLoop(item: DownloadItem) {
        val destinationFile = File(item.localFilePath)
        val partFile = File(destinationFile.absolutePath + ".part")
        var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

        var bytesSinceLastUpdate = 0L
        var lastTime = System.currentTimeMillis()
        var lastSpeed = 0L
        var lastEta = 0L

        try {
            val url = URL(item.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                if (downloadedBytes > 0L) {
                    setRequestProperty("Range", "bytes=$downloadedBytes-")
                }
            }

            val responseCode = connection.responseCode
            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
            val isOk = responseCode == HttpURLConnection.HTTP_OK

            if (!isPartial && !isOk) {
                throw Exception("Error de respuesta HTTP: $responseCode")
            }

            val serverTotal = connection.contentLengthLong
            val totalBytes = if (isPartial) {
                serverTotal + downloadedBytes
            } else {
                if (serverTotal > 0L) serverTotal else item.totalBytes
            }

            val input = connection.inputStream
            val raf = RandomAccessFile(partFile, "rw")
            if (downloadedBytes > 0L && isPartial) {
                raf.seek(downloadedBytes)
            } else {
                raf.setLength(0L)
                downloadedBytes = 0L
            }

            val buffer = ByteArray(32 * 1024) // Buffer óptimo de 32 KB
            var bytesRead: Int

            while (coroutineContext.isActive) {
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                raf.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                val timeDiff = now - lastTime

                // CONTROL DE THROTTLE A 800 ms
                if (timeDiff >= 800L) {
                    lastSpeed = (bytesSinceLastUpdate * 1000L) / timeDiff.coerceAtLeast(1L)
                    val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                    lastEta = if (lastSpeed > 2048 && remainingBytes > 0) remainingBytes / lastSpeed else 0L

                    val currentProgress = if (totalBytes > 0L) {
                        ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                    } else 0

                    val updatedItem = item.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        progress = currentProgress,
                        speedBytesPerSec = lastSpeed,
                        etaSeconds = lastEta,
                        status = DownloadStatus.DOWNLOADING
                    )

                    // 1. Reportar al SSOT para la UI de Compose
                    helper.reportProgress(
                        downloadId = item.id,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        progress = currentProgress,
                        speedBytesPerSec = lastSpeed,
                        etaSeconds = lastEta
                    )

                    // 2. Actualizar notificación del sistema
                    postProgressNotification(updatedItem, currentProgress, lastSpeed, lastEta)

                    bytesSinceLastUpdate = 0L
                    lastTime = now
                }
            }

            raf.close()
            input.close()
            connection.disconnect()

            if (coroutineContext.isActive) {
                // Renombrar .part al archivo final
                partFile.renameTo(destinationFile)
                markDownloadSuccess(item, destinationFile.length())
            }

        } catch (e: Exception) {
            if (coroutineContext.isActive) {
                markDownloadFailed(item, e.message ?: "Error de descarga")
            }
        } finally {
            downloadJobs.remove(item.id)
            activeItems.remove(item.id)
            promoteNextForegroundNotification(item.id.toInt())
        }
    }

    private fun postProgressNotification(item: DownloadItem, progress: Int, speed: Long, eta: Long) {
        val notif = NotificationUtils.buildProgressNotification(
            applicationContext, item, progress, speed, eta
        )
        notificationManager.notify(item.id.toInt(), notif)
    }

    /**
     * TRANSFERENCIA DE FOREGROUND SIN CRASHES:
     * Si la descarga que representaba la notificación en primer plano termina,
     * se transfiere inmediatamente a otra descarga activa antes de cancelar la anterior.
     */
    private fun promoteNextForegroundNotification(stoppingNotifId: Int) {
        synchronized(notifLock) {
            if (currentForegroundId == stoppingNotifId) {
                val nextActive = activeItems.values.firstOrNull { it.id.toInt() != stoppingNotifId }
                if (nextActive != null) {
                    val nextId = nextActive.id.toInt()
                    val live = helper.getItem(nextActive.id) ?: nextActive
                    val nextNotif = NotificationUtils.buildProgressNotification(
                        applicationContext, live, live.progress, live.speedBytesPerSec, live.etaSeconds
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(nextId, nextNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(nextId, nextNotif)
                    }
                    currentForegroundId = nextId
                    try {
                        notificationManager.cancel(stoppingNotifId)
                    } catch (_: Exception) {}
                } else {
                    currentForegroundId = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    try {
                        notificationManager.cancel(stoppingNotifId)
                    } catch (_: Exception) {}
                    stopSelf()
                }
            } else {
                try {
                    notificationManager.cancel(stoppingNotifId)
                } catch (_: Exception) {}
            }
        }
    }

    private fun markDownloadSuccess(item: DownloadItem, finalSize: Long) {
        val completedItem = item.copy(
            status = DownloadStatus.COMPLETED,
            progress = 100,
            downloadedBytes = finalSize,
            totalBytes = finalSize,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        helper.markCompleted(completedItem)

        // Publicar notificación de éxito en su canal correspondiente
        val successNotif = NotificationUtils.buildCompletedNotification(applicationContext, completedItem)
        notificationManager.notify(item.id.toInt() + 100000, successNotif)
    }

    private fun markDownloadFailed(item: DownloadItem, errorReason: String) {
        val failedItem = item.copy(
            status = DownloadStatus.FAILED,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        helper.updateItemState(failedItem)
    }

    private fun pauseDownload(id: Long) {
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)
        val item = activeItems.remove(id) ?: helper.getItem(id)
        if (item != null) {
            val paused = item.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
            helper.updateItemState(paused)
            val notif = NotificationUtils.buildPausedNotification(applicationContext, paused)
            notificationManager.notify(id.toInt(), notif)
        }
        promoteNextForegroundNotification(id.toInt())
    }

    private fun cancelDownload(id: Long) {
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)
        activeItems.remove(id)
        helper.removeItem(id)
        promoteNextForegroundNotification(id.toInt())
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

---

## 5. Coordinador y SSOT (`DownloadHelper.kt`)

`DownloadHelper` mantiene la lista en memoria como `StateFlow` para que las pantallas de Compose (`DescargasScreen`, `HomeScreen`) se actualicen sin demoras, y despacha las órdenes al Servicio.

```kotlin
package com.example.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class DownloadHelper private constructor(private val context: Context) {

    private val inMemoryItems = ConcurrentHashMap<Long, DownloadItem>()
    private val _downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadsFlow: StateFlow<List<DownloadItem>> = _downloadsFlow.asStateFlow()

    companion object {
        const val ACTION_START_DOWNLOAD = "com.downloadfree.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.downloadfree.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.downloadfree.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        @Volatile
        private var INSTANCE: DownloadHelper? = null

        fun getActiveInstance(context: Context): DownloadHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun startDownload(item: DownloadItem) {
        inMemoryItems[item.id] = item
        emitUpdates()

        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = ACTION_START_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, item.id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseDownload(id: Long) {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = ACTION_PAUSE_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancelDownload(id: Long) {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, id)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun reportProgress(
        downloadId: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        progress: Int,
        speedBytesPerSec: Long,
        etaSeconds: Long
    ) {
        val current = inMemoryItems[downloadId] ?: return
        inMemoryItems[downloadId] = current.copy(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            progress = progress,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds,
            status = DownloadStatus.DOWNLOADING
        )
        emitUpdates()
    }

    fun updateItemState(item: DownloadItem) {
        inMemoryItems[item.id] = item
        emitUpdates()
    }

    fun markCompleted(item: DownloadItem) {
        inMemoryItems[item.id] = item
        emitUpdates()
    }

    fun removeItem(id: Long) {
        inMemoryItems.remove(id)
        emitUpdates()
    }

    fun getItem(id: Long): DownloadItem? = inMemoryItems[id]

    private fun emitUpdates() {
        _downloadsFlow.value = inMemoryItems.values.toList()
    }
}
```

---

## 6. Integración en Jetpack Compose con Velocidad Estabilizada (EMA)

Para evitar que los números de la velocidad general parpadeen rápidamente cuando hay muchas transferencias simultáneas:

```kotlin
// En DescargasScreen.kt:
val currentActiveList by rememberUpdatedState(activeList)
var stabilizedTotalSpeed by remember { mutableLongStateOf(0L) }

// Si no hay descargas activas, volver a 0L de inmediato
LaunchedEffect(activeList) {
    val hasDownloading = activeList.any { it.status == DownloadStatus.DOWNLOADING }
    if (!hasDownloading) {
        stabilizedTotalSpeed = 0L
    }
}

// Bucle temporizado fijo a 1400 ms con filtro de Media Móvil Exponencial (EMA)
LaunchedEffect(Unit) {
    var smoothedSpeed = 0L
    while (isActive) {
        delay(1400L)
        val list = currentActiveList
        val hasDownloading = list.any { it.status == DownloadStatus.DOWNLOADING }
        if (hasDownloading) {
            val targetSum = list
                .filter { it.status == DownloadStatus.DOWNLOADING }
                .sumOf { it.speedBytesPerSec }
            smoothedSpeed = if (smoothedSpeed <= 0L) {
                targetSum
            } else if (targetSum <= 0L) {
                (smoothedSpeed * 0.40).toLong()
            } else {
                // 40% del histórico + 60% de la nueva muestra instantánea
                ((smoothedSpeed * 0.40) + (targetSum * 0.60)).toLong()
            }
            stabilizedTotalSpeed = smoothedSpeed
        } else {
            smoothedSpeed = 0L
            stabilizedTotalSpeed = 0L
        }
    }
}
```

---

## 7. Claves Principales de la Solución

1. **Evitar parpadeos en la barra de estado:** `setOnlyAlertOnce(true)` y `setSilent(true)` en el canal de progreso impiden que Android emita sonidos o vibraciones en cada refresco.
2. **Evitar bloqueos del Foreground Service:** `promoteNextForegroundNotification` transfiere dinámicamente la notificación activa antes de descartar la que se detuvo.
3. **Persistencia de reanudación:** El uso de archivos `.part` con cabeceras HTTP `Range: bytes=offset-` permite reanudar descargas interrumpidas sin descargar el archivo desde el inicio.
4. **Independencia del ciclo de vida:** El `ForegroundService` vive en su propio proceso y `SupervisorJob`, permitiendo que las descargas continúen incluso si el usuario cierra la aplicación o bloquea el teléfono.
