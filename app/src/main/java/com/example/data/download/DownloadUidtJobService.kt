package com.example.data.download

import android.app.Notification
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.ui.components.AppToastManager
import com.example.ui.components.ToastType
import com.example.utils.NotificationUtils
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Android 14+ user-initiated data transfer implementation.
 * This replaces dataSync foreground services for long user-started downloads,
 * avoiding the Android 15 six-hour dataSync foreground-service quota.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DownloadUidtJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()

    override fun onStartJob(params: JobParameters): Boolean {
        val itemId = params.extras.getString(EXTRA_ITEM_ID) ?: return false
        val helper = DownloadHelper.getActiveInstance(applicationContext)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        NotificationUtils.initNotificationChannels(this)

        val job = scope.launch {
            try {
                withTimeoutOrNull(2500L) { helper.awaitLoaded() }
                val item = helper.getItem(itemId)
                if (item == null) {
                    jobFinished(params, false)
                    return@launch
                }

                val notification = NotificationUtils.buildProgressNotification(
                    applicationContext, item, item.progress, item.speedBytesPerSec, item.etaSeconds
                )
                // UIDT requires an ongoing user-visible notification while running.
                setNotification(
                    params,
                    notificationId(item.id),
                    notification,
                    JobService.JOB_END_NOTIFICATION_POLICY_DETACH
                )

                executeDownload(item, helper, notificationManager)
                jobFinished(params, false)
            } catch (_: CancellationException) {
                jobFinished(params, true)
            } catch (_: Exception) {
                jobFinished(params, true)
            } finally {
                jobs.remove(params.jobId)
            }
        }
        jobs[params.jobId] = job
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        val itemId = params.extras.getString(EXTRA_ITEM_ID)
        if (itemId != null) {
            try { connections.remove(itemId)?.disconnect() } catch (_: Exception) {}
            // State is persisted continuously, so a system stop can safely resume later.
            val helper = DownloadHelper.getActiveInstance(applicationContext)
            helper.markStoppedForUidt(itemId)
            val paused = helper.getItem(itemId)
            if (paused != null) {
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(
                    notificationId(itemId),
                    NotificationUtils.buildPausedNotification(applicationContext, paused)
                )
            }
        }
        return true
    }

    private suspend fun executeDownload(
        item: DownloadItem,
        helper: DownloadHelper,
        notificationManager: android.app.NotificationManager
    ) {
        val destination = File(item.localFilePath)
        destination.parentFile?.mkdirs()
        val part = File(destination.absolutePath + ".part")
        part.parentFile?.mkdirs()
        var downloaded = if (part.exists()) part.length() else item.downloadedBytes
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var raf: RandomAccessFile? = null

        try {
            var currentUrl = item.originalVideoUrl
            var redirects = 0
            var code: Int
            var conn: HttpURLConnection

            while (true) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    setRequestProperty("Accept-Encoding", "identity")
                    if (downloaded > 0L) setRequestProperty("Range", "bytes=$downloaded-")
                }
                connection = conn
                connections[item.id] = conn
                currentCoroutineContext().ensureActive()
                code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    connections.remove(item.id, conn)
                    if (!location.isNullOrBlank() && redirects < 5) {
                        currentUrl = if (location.startsWith("http://", true) || location.startsWith("https://", true)) {
                            location
                        } else URL(URL(currentUrl), location).toString()
                        redirects++
                        continue
                    }
                }
                break
            }

            if (code == 416) {
                part.delete()
                downloaded = 0L
                conn.disconnect()
                connections.remove(item.id, conn)
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                connection = conn
                connections[item.id] = conn
                code = conn.responseCode
            }

            val partial = code == HttpURLConnection.HTTP_PARTIAL
            val ok = code == HttpURLConnection.HTTP_OK
            if (!partial && !ok) error("Error de respuesta HTTP: $code")

            val contentLength = conn.contentLengthLong
            val rangeTotal = if (partial) parseContentRangeTotal(conn.getHeaderField("Content-Range")) else null
            val knownTotal = rangeTotal ?: if (partial && contentLength > 0) contentLength + downloaded else if (ok && contentLength > 0) contentLength else item.totalBytes
            var total = knownTotal.coerceAtLeast(0L)
            if (ok) downloaded = 0L
            if (total in 1L..downloaded) total = 0L

            input = conn.inputStream
            raf = RandomAccessFile(part, "rw")
            if (downloaded > 0) raf.seek(downloaded) else raf.setLength(0L)
            publish(item, helper, notificationManager, downloaded, total, 0L, 0L)

            val buffer = ByteArray(32 * 1024)
            var windowBytes = 0L
            var last = System.currentTimeMillis()
            var speed = 0L

            while (currentCoroutineContext().isActive) {
                val read = input.read(buffer)
                if (read == -1) break
                currentCoroutineContext().ensureActive()
                raf.write(buffer, 0, read)
                downloaded += read
                windowBytes += read
                val now = System.currentTimeMillis()
                val dt = now - last
                if (dt >= 700L) {
                    val instant = (windowBytes * 1000L) / dt.coerceAtLeast(1L)
                    speed = if (speed == 0L) instant else ((speed * 0.70) + (instant * 0.30)).toLong()
                    val remaining = (total - downloaded).coerceAtLeast(0L)
                    val eta = if (speed > 2048L && remaining > 0) remaining / speed else 0L
                    publish(item, helper, notificationManager, downloaded, total, speed, eta)
                    windowBytes = 0L
                    last = now
                }
            }

            currentCoroutineContext().ensureActive()
            raf.close()
            input.close()
            if (total > 0 && downloaded < total) error("Conexión interrumpida")
            if (destination.exists()) destination.delete()
            if (!part.renameTo(destination)) error("No se pudo finalizar el archivo")

            val completed = (helper.getItem(item.id) ?: item).copy(
                status = DownloadStatus.COMPLETED,
                progress = 100,
                downloadedBytes = destination.length(),
                totalBytes = destination.length(),
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )
            helper.markCompleted(completed)
            notificationManager.notify(
                notificationId(item.id),
                NotificationUtils.buildCompletedNotification(applicationContext, completed)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val live = helper.getItem(item.id) ?: item
            if (live.status != DownloadStatus.PAUSED) {
                val failed = live.copy(status = DownloadStatus.FAILED, speedBytesPerSec = 0L, etaSeconds = 0L)
                helper.updateAndPersist(failed)
                notificationManager.notify(
                    notificationId(item.id),
                    NotificationUtils.buildFailedNotification(applicationContext, failed)
                )
            }
        } finally {
            connections.remove(item.id)?.let { try { it.disconnect() } catch (_: Exception) {} }
            try { input?.close() } catch (_: Exception) {}
            try { raf?.close() } catch (_: Exception) {}
        }
    }

    private fun publish(
        item: DownloadItem,
        helper: DownloadHelper,
        manager: android.app.NotificationManager,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long
    ) {
        val progress = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 99) else 0
        helper.reportProgress(item.id, downloaded, total, progress, speed, eta)
        val shown = (helper.getItem(item.id) ?: item).copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = downloaded,
            totalBytes = total,
            progress = progress,
            speedBytesPerSec = speed,
            etaSeconds = eta
        )
        manager.notify(
            notificationId(item.id),
            NotificationUtils.buildProgressNotification(applicationContext, shown, progress, speed, eta)
        )
    }

    private fun parseContentRangeTotal(header: String?): Long? =
        header?.substringAfterLast('/')?.trim()?.toLongOrNull()?.takeIf { it > 0L }

    private fun notificationId(id: String): Int = (id.hashCode() and 0x0FFFFFFF) + 1

    companion object {
        const val EXTRA_ITEM_ID = "uidt_item_id"
        private const val JOB_PREFIX = 0x4A000000

        fun jobId(itemId: String): Int = JOB_PREFIX or (itemId.hashCode() and 0x00FFFFFF)

        fun schedule(context: android.content.Context, item: DownloadItem): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
            val scheduler = context.getSystemService(android.app.job.JobScheduler::class.java) ?: return false
            val component = android.content.ComponentName(context, DownloadUidtJobService::class.java)
            val jobId = jobId(item.id)
            val jobInfo = android.app.job.JobInfo.Builder(jobId, component)
                .setUserInitiated(true)
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                .setEstimatedNetworkBytes(
                    if (item.totalBytes > 0) item.totalBytes else 1L,
                    if (item.totalBytes > 0) item.totalBytes else 1L
                )
                .setExtras(android.os.PersistableBundle().apply { putString(EXTRA_ITEM_ID, item.id) })
                .build()
            return try { scheduler.schedule(jobInfo) == android.app.job.JobScheduler.RESULT_SUCCESS } catch (_: Exception) { false }
        }

        fun cancel(context: android.content.Context, itemId: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            try { context.getSystemService(android.app.job.JobScheduler::class.java)?.cancel(jobId(itemId)) } catch (_: Exception) {}
        }
    }
}
