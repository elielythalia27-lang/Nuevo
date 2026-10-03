package com.example.data.download

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.utils.NotificationUtils
import com.example.utils.VpnProxyDetector
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

// Adaptadores para nombres y tipos del modelo en el proyecto
private val DownloadItem.downloadUrl: String get() = this.originalVideoUrl
private val DownloadHelper.downloadsFlow get() = this.liveDownloadsState
private fun DownloadHelper.removeItem(id: String) = this.removeItemFromState(id)
private fun DownloadHelper.removeItem(id: Long) = this.removeItemFromState(id.toString())
private fun DownloadHelper.getItem(id: Long): DownloadItem? = this.getItem(id.toString())

/** Se lanza dentro del bucle de descarga cuando se detecta VPN o Proxy: pausa, no es un fallo. */
private class VpnBlockedException : Exception("VPN o Proxy detectado")

class DownloadForegroundService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.downloadfree.ACTION_START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.downloadfree.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.downloadfree.ACTION_RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.downloadfree.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_QUEUE_DOWNLOAD = "com.downloadfree.ACTION_QUEUE_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.downloadfree.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.downloadfree.ACTION_RESUME_ALL"
        const val ACTION_CANCEL_ALL = "com.downloadfree.ACTION_CANCEL_ALL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_FILE_PATH = "extra_file_path"
        private const val SUMMARY_NID = 0x7FFF0000

        fun startDownload(context: Context, item: DownloadItem) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, item.id)
            }
            startForegroundServiceCompat(context, intent)
        }

        fun pauseDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            sendServiceCommand(context, intent)
        }

        fun queueDownload(context: Context, downloadId: String) {
            pauseDownload(context, downloadId)
        }

        fun resumeDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            startForegroundServiceCompat(context, intent)
        }

        fun cancelDownload(context: Context, downloadId: String, localFilePath: String? = null) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                // Ruta del archivo, para borrar el .part al cancelar
                if (!localFilePath.isNullOrBlank()) putExtra(EXTRA_FILE_PATH, localFilePath)
            }
            sendServiceCommand(context, intent)
        }

        fun pauseAll(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            sendServiceCommand(context, intent)
        }

        fun resumeAll(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME_ALL
            }
            startForegroundServiceCompat(context, intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            sendServiceCommand(context, intent)
        }

        private fun startForegroundServiceCompat(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        private fun sendServiceCommand(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()
    private val userStopping: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val startJobLock = Any()

    private val notifLock = Any()
    private var foregroundStarted = false
    private var lastSummaryTime = 0L

    private lateinit var notificationManager: NotificationManager
    private lateinit var helper: DownloadHelper

    // IDs de notificación por descarga. Progreso y pausa comparten ID: al pausar,
    // Android actualiza la notificación en su mismo lugar (no desaparece ni se reordena).
    private fun progressNid(id: String): Int = (id.hashCode() and 0x0FFFFFFF) + 1
    private fun progressNid(id: Long): Int = ((id xor (id ushr 32)).toInt() and 0x0FFFFFFF) + 1
    private fun pausedNid(id: String): Int = progressNid(id)
    private fun pausedNid(id: Long): Int = progressNid(id)
    private fun doneNid(id: String): Int = progressNid(id) + 0x20000000
    private fun doneNid(id: Long): Int = progressNid(id) + 0x20000000
    private fun failedNid(id: String): Int = progressNid(id) + 0x30000000
    private fun failedNid(id: Long): Int = progressNid(id) + 0x30000000

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        helper = DownloadHelper.getActiveInstance(applicationContext)
        NotificationUtils.initNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val downloadId = intent.getStringExtra(DownloadHelper.EXTRA_DOWNLOAD_ID)
            ?: intent.getLongExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, -1L).takeIf { it != -1L }?.toString()

        // Si nos arrancaron con startForegroundService, hay que llamar a startForeground
        // SIEMPRE y rápido, aunque después no haya nada que descargar. Si no, Android
        // cierra la app a los pocos segundos (ForegroundServiceDidNotStartInTimeException).
        val startedAsForeground = action == DownloadHelper.ACTION_START_DOWNLOAD ||
            action == DownloadHelper.ACTION_RESUME_DOWNLOAD ||
            action == ACTION_RESUME_ALL
        if (startedAsForeground) ensureForeground()

        // Si el proceso acaba de arrancar (por ejemplo, desde un botón de la notificación),
        // esperar a que la lista de descargas se cargue antes de buscar la descarga.
        waitUntilHelperLoaded()

        when (action) {
            DownloadHelper.ACTION_START_DOWNLOAD,
            "com.downloadfree.ACTION_RESUME_DOWNLOAD" -> {
                val item = downloadId?.let { helper.getItem(it) }
                if (item == null) {
                    if (downloadId != null) {
                        notificationManager.cancel(pausedNid(downloadId))
                        notificationManager.cancel(failedNid(downloadId))
                    }
                    stopIfIdle()
                } else {
                    startDownloadJob(item)
                }
            }
            DownloadHelper.ACTION_PAUSE_DOWNLOAD -> downloadId?.let { pauseDownload(it) }
            DownloadHelper.ACTION_CANCEL_DOWNLOAD -> downloadId?.let {
                // La ruta viene en el intent (desde la app) o se toma de la lista (desde la notificación)
                val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: helper.getItem(it)?.localFilePath
                cancelDownload(it, path)
            }
            ACTION_PAUSE_ALL, "com.downloadfree.ACTION_PAUSE_ALL" -> {
                val ids = downloadJobs.keys.toList()
                // Al pausar todo, la cola NO debe arrancar sola (startNext = false)
                if (ids.isEmpty()) stopIfIdle() else ids.forEach { pauseDownload(it, startNext = false) }
            }
            ACTION_RESUME_ALL -> {
                helper.resumeAllDownloads()
            }
            ACTION_CANCEL_ALL -> {
                helper.cancelAllDownloads()
            }
        }
        return START_NOT_STICKY
    }

    /** Arranca el primer plano con la notificación resumen si todavía no está arrancado. */
    private fun ensureForeground() {
        synchronized(notifLock) {
            if (!foregroundStarted) {
                val notif = NotificationUtils.buildSummaryNotification(this, activeItems())
                startForegroundCompat(SUMMARY_NID, notif)
                foregroundStarted = true
                lastSummaryTime = System.currentTimeMillis()
            }
        }
    }

    private fun waitUntilHelperLoaded() {
        if (helper.isLoaded()) return
        try {
            runBlocking { withTimeoutOrNull(2500L) { helper.awaitLoaded() } }
        } catch (_: Exception) {}
    }

    private fun activeItems(): List<DownloadItem> =
        helper.downloadsFlow.value.filter { it.status == DownloadStatus.DOWNLOADING }

    /** Actualiza la notificación resumen (que es la del primer plano). Máximo 1 vez cada 1,5 segundos. */
    private fun refreshSummary(force: Boolean = false) {
        synchronized(notifLock) {
            if (!foregroundStarted) return
            val now = System.currentTimeMillis()
            if (!force && now - lastSummaryTime < 1500L) return
            lastSummaryTime = now
            val active = activeItems()
            if (active.isEmpty()) {
                if (downloadJobs.isEmpty() && userStopping.isEmpty()) {
                    foregroundStarted = false
                    stopForegroundCompat()
                    try { notificationManager.cancel(SUMMARY_NID) } catch (_: Exception) {}
                }
            } else {
                val notif = NotificationUtils.buildSummaryNotification(applicationContext, active)
                notificationManager.notify(SUMMARY_NID, notif)
            }
        }
    }

    private fun startDownloadJob(item: DownloadItem) = synchronized(startJobLock) {
        // Primero lo más barato: si ya está corriendo o se está pausando/cancelando, no hacer nada
        if (userStopping.contains(item.id)) return@synchronized
        if (downloadJobs[item.id]?.isActive == true) return@synchronized

        if (VpnProxyDetector.isVpnOrProxyActive(applicationContext)) {
            val paused = item.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L, etaSeconds = 0L)
            helper.updateAndPersist(paused)
            notificationManager.notify(
                pausedNid(item.id),
                NotificationUtils.buildPausedNotification(applicationContext, paused)
            )
            AppToastManager.show("Desactiva la VPN o Proxy para descargar", ToastType.WARNING)
            stopIfIdle()
            return@synchronized
        }

        // No contar las descargas que se están pausando/cancelando: ya están liberando su lugar
        val activeRunningJobs = downloadJobs.entries.count {
            it.value.isActive && !userStopping.contains(it.key)
        }
        val maxLimit = helper.getMaxConcurrentLimit().coerceIn(1, 5)
        if (activeRunningJobs >= maxLimit) {
            val pending = item.copy(status = DownloadStatus.PENDING, speedBytesPerSec = 0L, etaSeconds = 0L)
            helper.updateAndPersist(pending)
            // Una descarga en cola no debe seguir mostrando "En pausa" en la bandeja
            try { notificationManager.cancel(progressNid(item.id)) } catch (_: Exception) {}
            AppToastManager.show("En cola (máximo $maxLimit descargas activas)", ToastType.INFO)
            stopIfIdle()
            return@synchronized
        }

        // Solo se quita la notificación "Fallida". La de pausa comparte ID con la de progreso.
        notificationManager.cancel(failedNid(item.id))

        val running = item.copy(
            status = DownloadStatus.DOWNLOADING,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        helper.updateAndPersist(running)

        // La misma notificación pasa de "En pausa" a "Descargando" de inmediato, en su mismo lugar
        notificationManager.notify(
            progressNid(item.id),
            NotificationUtils.buildProgressNotification(applicationContext, running, running.progress, 0L, 0L)
        )

        ensureForeground()

        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            executeDownloadLoop(running)
        }
        downloadJobs[item.id] = job
        job.start()
        refreshSummary(force = true)
    }

    private suspend fun executeDownloadLoop(item: DownloadItem) {
        val myJob = currentCoroutineContext()[Job]
        val destinationFile = File(item.localFilePath)
        val partFile = File(destinationFile.absolutePath + ".part")
        var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var raf: RandomAccessFile? = null

        try {
            val conn = (URL(item.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
                if (downloadedBytes > 0L) {
                    setRequestProperty("Range", "bytes=$downloadedBytes-")
                }
            }
            connection = conn
            connections[item.id] = conn
            currentCoroutineContext().ensureActive()

            val code = conn.responseCode
            if (code == 416) {
                // El servidor no acepta ese rango: el .part no sirve, se empieza de cero
                partFile.delete()
                throw Exception("No se pudo reanudar. Pulsa reanudar otra vez.")
            }
            val isPartial = code == HttpURLConnection.HTTP_PARTIAL
            val isOk = code == HttpURLConnection.HTTP_OK
            if (!isPartial && !isOk) {
                throw Exception("Error de respuesta HTTP: $code")
            }

            // TOTAL REAL: Content-Range en 206, Content-Length en 200
            val lengthHeader = conn.contentLengthLong
            val rangeTotal = if (isPartial) {
                parseContentRangeTotal(conn.getHeaderField("Content-Range"))
            } else null

            var totalFromServer = true
            var totalBytes = when {
                rangeTotal != null -> rangeTotal
                isPartial && lengthHeader > 0L -> lengthHeader + downloadedBytes
                !isPartial && lengthHeader > 0L -> lengthHeader
                else -> {
                    totalFromServer = false
                    item.totalBytes.coerceAtLeast(0L)
                }
            }

            // Si el servidor ignoró Range (200), se empieza desde cero
            if (isOk) downloadedBytes = 0L

            // Un total menor o igual a lo descargado es falso: se trata como desconocido
            if (!totalFromServer && totalBytes in 1L..downloadedBytes) totalBytes = 0L

            val stream = conn.inputStream
            input = stream
            val file = RandomAccessFile(partFile, "rw")
            raf = file
            if (downloadedBytes > 0L) file.seek(downloadedBytes) else file.setLength(0L)

            // Mostrar de inmediato el total correcto
            publishProgress(item, downloadedBytes, totalBytes, 0L, 0L)

            val buffer = ByteArray(32 * 1024)
            var bytesSinceLastUpdate = 0L
            var lastTime = System.currentTimeMillis()
            var smoothedSpeed = 0L

            while (currentCoroutineContext().isActive) {
                val bytesRead = stream.read(buffer)
                if (bytesRead == -1) break
                // Si se pausó mientras leía, no escribir ese último bloque
                if (!currentCoroutineContext().isActive) break

                file.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                val timeDiff = now - lastTime
                if (timeDiff >= 1000L) {
                    if (VpnProxyDetector.isVpnOrProxyActive(applicationContext)) {
                        throw VpnBlockedException()
                    }
                    // Velocidad medida en esta ventana de ~1 segundo
                    val instantSpeed = (bytesSinceLastUpdate * 1000L) / timeDiff.coerceAtLeast(1L)
                    // Suavizado EMA: 70% valor anterior + 30% medición nueva
                    smoothedSpeed = if (smoothedSpeed <= 0L) {
                        instantSpeed
                    } else {
                        ((smoothedSpeed * 0.70) + (instantSpeed * 0.30)).toLong()
                    }
                    val speed = smoothedSpeed

                    if (totalBytes in 1L until downloadedBytes) totalBytes = 0L
                    val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                    val eta = if (speed > 2048 && remaining > 0L) remaining / speed else 0L

                    publishProgress(item, downloadedBytes, totalBytes, speed, eta)

                    bytesSinceLastUpdate = 0L
                    lastTime = now
                }
            }

            if (currentCoroutineContext().isActive) {
                file.close()
                stream.close()
                // Conexión cortada antes de tiempo: no marcar como completa
                if (totalFromServer && totalBytes > 0L && downloadedBytes < totalBytes) {
                    throw Exception("Conexión interrumpida")
                }
                if (destinationFile.exists()) destinationFile.delete()
                partFile.renameTo(destinationFile)
                markDownloadSuccess(item, destinationFile.length())
            }

        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                if (e is VpnBlockedException) {
                    // VPN/Proxy detectado a mitad de descarga: se pausa (no es un fallo)
                    pauseBecauseOfVpn(item)
                } else {
                    markDownloadFailed(item, e.message ?: "Error de descarga")
                }
            }
        } finally {
            connection?.let {
                connections.remove(item.id, it)
                try { it.disconnect() } catch (_: Exception) {}
            }
            try { raf?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}

            myJob?.let { downloadJobs.remove(item.id, it) }
            // Si la descarga quedó en pausa (por el usuario o por la VPN), NO se cancela la
            // notificación: se queda como "En pausa" en su mismo lugar.
            val keepNotification = userStopping.contains(item.id) ||
                helper.getItem(item.id)?.status == DownloadStatus.PAUSED
            if (!keepNotification) {
                try { notificationManager.cancel(progressNid(item.id)) } catch (_: Exception) {}
            }
            refreshSummary(force = true)
            stopIfIdle()
        }
    }

    private fun publishProgress(
        item: DownloadItem,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long
    ) {
        val progress = if (total > 0L) {
            ((downloaded * 100L) / total).toInt().coerceIn(0, 99)
        } else 0

        helper.reportProgress(
            downloadId = item.id,
            downloadedBytes = downloaded,
            totalBytes = total,
            progress = progress,
            speedBytesPerSec = speed,
            etaSeconds = eta
        )

        val shown = item.copy(
            downloadedBytes = downloaded,
            totalBytes = total,
            progress = progress,
            speedBytesPerSec = speed,
            etaSeconds = eta,
            status = DownloadStatus.DOWNLOADING
        )
        notificationManager.notify(
            progressNid(item.id),
            NotificationUtils.buildProgressNotification(applicationContext, shown, progress, speed, eta)
        )
        refreshSummary()
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        // Formato: "bytes 5000-999999/1000000" (o "/*" si es desconocido)
        val total = header?.substringAfterLast('/')?.trim() ?: return null
        return total.toLongOrNull()?.takeIf { it > 0L }
    }

    /** Cuando no queda nada activo ni en proceso de pausa/cancelación, suelta el primer plano y se detiene. */
    private fun stopIfIdle() {
        synchronized(notifLock) {
            val active = activeItems()
            if (active.isEmpty() && downloadJobs.isEmpty() && userStopping.isEmpty()) {
                if (foregroundStarted) {
                    foregroundStarted = false
                    stopForegroundCompat()
                }
                try { notificationManager.cancel(SUMMARY_NID) } catch (_: Exception) {}
                stopSelf()
            }
        }
    }

    private fun startForegroundCompat(id: Int, notif: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notif)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
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

        val successNotif = NotificationUtils.buildCompletedNotification(applicationContext, completedItem)
        notificationManager.notify(doneNid(item.id), successNotif)
    }

    /** VPN o Proxy detectado a mitad de descarga: queda en pausa, con su notificación "En pausa". */
    private fun pauseBecauseOfVpn(item: DownloadItem) {
        val live = helper.getItem(item.id) ?: item
        val part = File(live.localFilePath + ".part")
        val realBytes = if (part.exists()) part.length() else live.downloadedBytes
        val paused = live.copy(
            status = DownloadStatus.PAUSED,
            downloadedBytes = realBytes,
            speedBytesPerSec = 0L,
            etaSeconds = 0L
        )
        helper.updateAndPersist(paused)
        notificationManager.notify(
            pausedNid(item.id),
            NotificationUtils.buildPausedNotification(applicationContext, paused)
        )
        AppToastManager.show("Descarga en pausa: desactiva la VPN o Proxy", ToastType.WARNING)
    }

    private fun markDownloadFailed(item: DownloadItem, errorReason: String) {
        val live = helper.getItem(item.id) ?: item
        val failed = live.copy(status = DownloadStatus.FAILED, speedBytesPerSec = 0L, etaSeconds = 0L)
        helper.updateAndPersist(failed)
        notificationManager.notify(
            failedNid(item.id),
            NotificationUtils.buildFailedNotification(applicationContext, failed)
        )
    }

    /** Cancela el hilo, corta el socket para que el read() falle al instante y espera a que termine. */
    private suspend fun stopJob(id: String) {
        val job = downloadJobs[id]
        job?.cancel()
        try { connections[id]?.disconnect() } catch (_: Exception) {}
        job?.join()
    }

    /**
     * Detiene la descarga. Si el helper la había puesto en cola (PENDING), se queda en cola;
     * en cualquier otro caso queda en pausa. startNext = false evita que la cola arranque sola
     * (se usa en "Pausar todo").
     */
    private fun pauseDownload(id: String, startNext: Boolean = true) {
        if (!userStopping.add(id)) return
        serviceScope.launch {
            try {
                stopJob(id)
                val live = helper.getItem(id)
                if (live != null && live.status != DownloadStatus.COMPLETED) {
                    // Datos reales del archivo, no la copia vieja
                    val part = File(live.localFilePath + ".part")
                    val realBytes = if (part.exists()) part.length() else live.downloadedBytes
                    val progress = if (live.totalBytes > 0L) {
                        ((realBytes * 100L) / live.totalBytes).toInt().coerceIn(0, 99)
                    } else 0

                    val queued = live.status == DownloadStatus.PENDING
                    val stopped = live.copy(
                        status = if (queued) DownloadStatus.PENDING else DownloadStatus.PAUSED,
                        downloadedBytes = realBytes,
                        progress = progress,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    helper.updateAndPersist(stopped)
                    if (queued) {
                        notificationManager.cancel(progressNid(id))
                    } else {
                        notificationManager.notify(
                            pausedNid(id),
                            NotificationUtils.buildPausedNotification(applicationContext, stopped)
                        )
                    }
                }
            } finally {
                userStopping.remove(id)
                refreshSummary(force = true)
                if (startNext) helper.checkAndStartNextPending()
                stopIfIdle()
            }
        }
    }

    private fun cancelDownload(id: String, localFilePath: String? = null) {
        if (!userStopping.add(id)) return
        serviceScope.launch {
            try {
                stopJob(id)
                helper.removeItem(id)
                notificationManager.cancel(progressNid(id))
                notificationManager.cancel(pausedNid(id))
                notificationManager.cancel(failedNid(id))
                // El hilo ya terminó (join), así que nadie sigue escribiendo: se borra el archivo parcial
                if (!localFilePath.isNullOrBlank()) {
                    try { File("$localFilePath.part").delete() } catch (_: Exception) {}
                }
            } finally {
                userStopping.remove(id)
                // Asegurar que las notificaciones de las descargas restantes no desaparezcan
                val remainingActive = activeItems()
                remainingActive.forEach { rem ->
                    notificationManager.notify(
                        progressNid(rem.id),
                        NotificationUtils.buildProgressNotification(
                            applicationContext,
                            rem,
                            rem.progress,
                            rem.speedBytesPerSec,
                            rem.etaSeconds
                        )
                    )
                }
                refreshSummary(force = true)
                helper.checkAndStartNextPending()
                stopIfIdle()
            }
        }
    }

    private fun pauseDownload(id: Long) = pauseDownload(id.toString())
    private fun cancelDownload(id: Long) = cancelDownload(id.toString())

    override fun onDestroy() {
        connections.values.forEach {
            try { it.disconnect() } catch (_: Exception) {}
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
