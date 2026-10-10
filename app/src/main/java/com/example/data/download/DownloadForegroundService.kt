package com.example.data.download

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        /** Esperas entre reintentos automáticos ante cortes de red (≈ 1,5 min en total). */
        internal val RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)

        fun startDownload(context: Context, item: DownloadItem) {
            // Android 14+: long user-initiated downloads use UIDT instead of
            // a dataSync foreground service, avoiding Android 15's 6-hour quota.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (!DownloadUidtJobService.schedule(context, item)) {
                    val helper = DownloadHelper.getActiveInstance(context.applicationContext)
                    helper.updateAndPersist(item.copy(status = DownloadStatus.PENDING, speedBytesPerSec = 0L, etaSeconds = 0L))
                    AppToastManager.show("Android no permitió iniciar la transferencia en segundo plano. Se reintentará cuando sea posible.", ToastType.INFO)
                }
                return
            }

            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, item.id)
                putExtra(EXTRA_FILE_PATH, item.localFilePath)
                putExtra("extra_download_url", item.originalVideoUrl)
                putExtra("extra_title", item.title)
                putExtra("extra_year", item.year)
                putExtra("extra_type", item.type)
                putExtra("extra_cover_url", item.coverUrl)
            }
            startForegroundServiceCompat(context, intent)
        }

        fun pauseDownload(context: Context, downloadId: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                pauseUidtDownload(context, downloadId)
                return
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val helper = DownloadHelper.getActiveInstance(context.applicationContext)
                val item = helper.getItem(downloadId) ?: return
                if (!DownloadUidtJobService.schedule(context, item)) {
                    // Keep the item persisted; a later foreground/user action can retry.
                    return
                }
                return
            }
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            startForegroundServiceCompat(context, intent)
        }

        fun cancelDownload(context: Context, downloadId: String, localFilePath: String? = null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                cancelUidtDownload(context, downloadId, localFilePath)
                return
            }
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                // Ruta del archivo, para borrar el .part al cancelar
                if (!localFilePath.isNullOrBlank()) putExtra(EXTRA_FILE_PATH, localFilePath)
            }
            sendServiceCommand(context, intent)
        }

        fun pauseAll(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val helper = DownloadHelper.getActiveInstance(context.applicationContext)
                helper.liveDownloadsState.value.filter { it.status == DownloadStatus.DOWNLOADING }.forEach {
                    pauseUidtDownload(context, it.id)
                }
                return
            }
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            sendServiceCommand(context, intent)
        }

        fun resumeAll(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val helper = DownloadHelper.getActiveInstance(context.applicationContext)
                helper.liveDownloadsState.value.filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.PENDING }.forEach {
                    val running = it.copy(status = DownloadStatus.DOWNLOADING)
                    helper.updateAndPersist(running)
                    if (!DownloadUidtJobService.schedule(context, running)) {
                        helper.updateAndPersist(running.copy(status = DownloadStatus.PENDING))
                    }
                }
                return
            }
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME_ALL
            }
            startForegroundServiceCompat(context, intent)
        }

        fun cancelAll(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val helper = DownloadHelper.getActiveInstance(context.applicationContext)
                helper.liveDownloadsState.value.toList().forEach {
                    cancelUidtDownload(context, it.id, it.localFilePath)
                }
                return
            }
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            sendServiceCommand(context, intent)
        }

        private fun pauseUidtDownload(context: Context, downloadId: String) {
            val app = context.applicationContext
            val helper = DownloadHelper.getActiveInstance(app)
            val item = helper.getItem(downloadId) ?: return
            val part = File(item.localFilePath + ".part")
            val bytes = if (part.exists()) part.length() else item.downloadedBytes
            val progress = if (item.totalBytes > 0L) {
                ((bytes * 100L) / item.totalBytes).toInt().coerceIn(0, 99)
            } else item.progress

            // Mark paused (or keep queued) before cancelling the job. The downloader checks
            // this state and will no longer publish a "downloading" notification after the tap.
            // Un item en cola (PENDING) debe seguir en cola: si se marcara como PAUSED, al
            // cambiar el límite de descargas o el modo "Solo Wi-Fi" nunca se reanudaría solo.
            val queued = item.status == DownloadStatus.PENDING
            val paused = item.copy(
                status = if (queued) DownloadStatus.PENDING else DownloadStatus.PAUSED,
                downloadedBytes = bytes,
                progress = progress,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )
            helper.updateAndPersist(paused)

            DownloadUidtJobService.cancel(app, downloadId)

            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Re-post after the JobScheduler cancellation so the UIDT notification cannot
            // win a race and remove the user's paused notification.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (queued) {
                    manager.cancel(progressNidStatic(downloadId))
                } else {
                    manager.notify(
                        progressNidStatic(downloadId),
                        NotificationUtils.buildPausedNotification(app, paused)
                    )
                }
            }, 180L)
        }

        private fun cancelUidtDownload(context: Context, downloadId: String, localFilePath: String?) {
            val app = context.applicationContext
            val helper = DownloadHelper.getActiveInstance(app)
            // Remove from the SSOT first. If JobScheduler calls onStopJob after this,
            // it sees no item and cannot resurrect it as PAUSED.
            helper.removeItemFromState(downloadId)
            DownloadUidtJobService.cancel(app, downloadId)
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val nid = progressNidStatic(downloadId)
            manager.cancel(nid)
            if (!localFilePath.isNullOrBlank()) {
                try { File(localFilePath + ".part").delete() } catch (_: Exception) {}
            }
        }

        private fun progressNidStatic(id: String): Int = (id.hashCode() and 0x0FFFFFFF) + 1

        private fun startForegroundServiceCompat(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        /**
         * Sends a control command to an already running download service.
         *
         * IMPORTANT: pause/cancel/queue are not foreground-service starts. Using
         * startForegroundService() for these commands can make Android expect
         * startForeground() within the launch window and kill the service because
         * those commands intentionally do not enter foreground mode.
         *
         * Notification actions are user initiated, so startService() is the
         * appropriate API for control commands on Android O+.
         */
        private fun sendServiceCommand(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // The caller has already updated persistent state. A later
                // foreground start/recovery can reconcile the service state.
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
                var item = downloadId?.let { helper.getItem(it) }
                if (item == null && downloadId != null) {
                    val url = intent.getStringExtra("extra_download_url") ?: ""
                    val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
                    val title = intent.getStringExtra("extra_title") ?: ""
                    if (url.isNotBlank() && path.isNotBlank()) {
                        item = DownloadItem(
                            id = downloadId,
                            title = title,
                            originalVideoUrl = url,
                            coverUrl = intent.getStringExtra("extra_cover_url") ?: "",
                            year = intent.getStringExtra("extra_year") ?: "",
                            type = intent.getStringExtra("extra_type") ?: "pl",
                            localFilePath = path,
                            status = DownloadStatus.DOWNLOADING
                        )
                        helper.updateAndPersist(item)
                    }
                }
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

    /**
     * Ejecuta la descarga con reintentos automáticos (como IDM): ante un corte de red o un
     * error 5xx espera un poco y continúa desde el archivo .part con HTTP Range, sin que el
     * usuario tenga que pulsar nada. Solo si se agotan los reintentos pasa a "Fallida".
     */
    private suspend fun executeDownloadLoop(item: DownloadItem) {
        val myJob = currentCoroutineContext()[Job]
        try {
            var attempt = 0
            while (true) {
                val shouldRetry = downloadAttempt(item)
                if (!shouldRetry) break
                if (attempt >= RETRY_DELAYS_MS.size) {
                    markDownloadFailed(item, "Sin conexión estable")
                    break
                }
                delay(RETRY_DELAYS_MS[attempt])
                attempt++
                // Si mientras esperaba el usuario la pausó o canceló, no reintentar.
                val live = helper.getItem(item.id)
                if (live == null || live.status != DownloadStatus.DOWNLOADING) break
            }
        } finally {
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

    /** @return true si el fallo es transitorio y conviene reintentar. */
    private suspend fun downloadAttempt(item: DownloadItem): Boolean {
        val destinationFile = File(item.localFilePath)
        destinationFile.parentFile?.mkdirs()
        val partFile = File(destinationFile.absolutePath + ".part")
        partFile.parentFile?.mkdirs()
        var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var raf: RandomAccessFile? = null

        try {
            var currentUrl = item.downloadUrl
            var redirectCount = 0
            var conn: HttpURLConnection
            var code: Int

            while (true) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept-Encoding", "identity")
                    if (downloadedBytes > 0L) {
                        setRequestProperty("Range", "bytes=$downloadedBytes-")
                    }
                }
                connection = conn
                connections[item.id] = conn
                currentCoroutineContext().ensureActive()

                code = conn.responseCode
                // Manejar redirecciones cruzadas entre HTTP y HTTPS que HttpURLConnection no sigue por defecto
                if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    connections.remove(item.id, conn)
                    if (!location.isNullOrBlank() && redirectCount < 5) {
                        currentUrl = if (location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)) {
                            location
                        } else {
                            URL(URL(currentUrl), location).toString()
                        }
                        redirectCount++
                        continue
                    }
                }
                break
            }

            if (code == 416) {
                // El servidor no acepta ese rango: el .part no sirve, se empieza de cero
                partFile.delete()
                downloadedBytes = 0L
                conn.disconnect()
                connections.remove(item.id, conn)
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                connection = conn
                connections[item.id] = conn
                code = conn.responseCode
            }
            val isPartial = code == HttpURLConnection.HTTP_PARTIAL
            val isOk = code == HttpURLConnection.HTTP_OK
            if (!isPartial && !isOk) {
                // 5xx y 429 son transitorios (se reintenta); 403/404/etc. no tienen arreglo.
                if (code >= 500 || code == 429) throw java.io.IOException("Error de respuesta HTTP: $code")
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
                if (timeDiff >= 700L) {
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
                    throw java.io.IOException("Conexión interrumpida")
                }
                if (destinationFile.exists()) destinationFile.delete()
                partFile.renameTo(destinationFile)
                markDownloadSuccess(item, destinationFile.length())
            }

        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                if (e is java.io.IOException) return true
                markDownloadFailed(item, e.message ?: "Error de descarga")
            }
        } finally {
            connection?.let {
                connections.remove(item.id, it)
                try { it.disconnect() } catch (_: Exception) {}
            }
            try { raf?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
        }
        return false
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

        // Registrar el archivo de video en el MediaStore del sistema para que aparezca
        // inmediatamente en el Explorador de archivos (HyperOS/MIUI), Galería y Reproductores
        try {
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(completedItem.localFilePath),
                arrayOf("video/mp4")
            ) { path, uri ->
                Log.d("DownloadService", "MediaScanner registró archivo descargado: $path -> $uri")
            }
        } catch (e: Exception) {
            Log.e("DownloadService", "Error escaneando archivo con MediaScanner", e)
        }

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

    /**
     * Android 15+ limits dataSync foreground services to six hours in a
     * rolling 24-hour period. Preserve the download state when the OS asks
     * this service to leave the foreground instead of leaving items stuck
     * forever as DOWNLOADING.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        serviceScope.launch {
            try {
                downloadJobs.values.toList().forEach { it.cancel() }
                connections.values.forEach {
                    try { it.disconnect() } catch (_: Exception) {}
                }
                helper.pauseDownloadsForServiceTimeout()
            } finally {
                try { stopForegroundCompat() } catch (_: Exception) {}
                stopSelf(startId)
            }
        }
    }

    override fun onDestroy() {
        connections.values.forEach {
            try { it.disconnect() } catch (_: Exception) {}
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
