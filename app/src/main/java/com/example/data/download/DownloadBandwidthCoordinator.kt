package com.example.data.download

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * DownloadBandwidthCoordinator
 *
 * Implements strict Fair-Share Bandwidth Allocation across all active downloads.
 * Whenever N > 1 downloads are running, each download is dynamically paced so that
 * all downloads receive an EQUAL slice (1/N) of total available bandwidth.
 */
object DownloadBandwidthCoordinator {

    private class ActiveStream(
        val id: String,
        @Volatile var lastReadTime: Long = System.currentTimeMillis(),
        @Volatile var speedBytesPerSec: Long = 0L
    ) {
        val windowBytes = AtomicLong(0L)
        @Volatile var windowStartTime = System.currentTimeMillis()

        fun recordBytes(bytes: Int) {
            windowBytes.addAndGet(bytes.toLong())
        }

        fun computeSpeed(now: Long): Long {
            val elapsed = max(1L, now - windowStartTime)
            if (elapsed >= 500L) {
                val currentBytes = windowBytes.getAndSet(0L)
                val instantSpeed = (currentBytes * 1000L) / elapsed
                speedBytesPerSec = if (speedBytesPerSec == 0L) {
                    instantSpeed
                } else {
                    // Smooth exponential moving average
                    (speedBytesPerSec * 5 + instantSpeed * 5) / 10
                }
                windowStartTime = now
            }
            return speedBytesPerSec
        }
    }

    private val activeStreams = ConcurrentHashMap<String, ActiveStream>()
    private val totalBandwidthEstimate = AtomicLong(150 * 1024L) // 150 KB/s initial baseline estimate
    @Volatile private var lastGlobalCalcTime = 0L

    fun registerStream(downloadId: String) {
        activeStreams[downloadId] = ActiveStream(downloadId)
        updateGlobalBandwidth()
    }

    fun unregisterStream(downloadId: String) {
        activeStreams.remove(downloadId)
        updateGlobalBandwidth()
    }

    fun getActiveStreamCount(): Int = activeStreams.size

    private fun updateGlobalBandwidth() {
        val now = System.currentTimeMillis()
        var sum = 0L
        for (stream in activeStreams.values) {
            val s = stream.computeSpeed(now)
            sum += s
        }
        if (sum > 10 * 1024L) {
            // Smooth aggregate throughput estimate
            val prev = totalBandwidthEstimate.get()
            val smoothed = ((prev * 4 + sum * 6) / 10).coerceAtLeast(32 * 1024L)
            totalBandwidthEstimate.set(smoothed)
        }
        lastGlobalCalcTime = now
    }

    /**
     * Paces the download stream to enforce strict equal division (1/N share) across all active downloads.
     */
    suspend fun paceTransfer(downloadId: String, bytesRead: Int) {
        if (bytesRead <= 0) return

        val stream = activeStreams.computeIfAbsent(downloadId) { ActiveStream(downloadId) }
        val now = System.currentTimeMillis()
        stream.recordBytes(bytesRead)

        val activeCount = activeStreams.size
        // If only 1 download is active: full speed, zero delay
        if (activeCount <= 1) {
            stream.lastReadTime = now
            return
        }

        if (now - lastGlobalCalcTime >= 400L) {
            updateGlobalBandwidth()
        }

        val totalEstimate = totalBandwidthEstimate.get().coerceAtLeast(32 * 1024L)
        // Fair share target for each stream = Total / N (with 10% discovery headroom)
        val fairShareTargetBytesPerSec = max((totalEstimate / activeCount) * 11L / 10L, 16 * 1024L)

        // Time required to download `bytesRead` at the target fair speed
        val requiredTimeMs = (bytesRead.toLong() * 1000L) / fairShareTargetBytesPerSec
        val actualElapsedMs = (now - stream.lastReadTime).coerceAtLeast(0L)

        if (requiredTimeMs > actualElapsedMs) {
            val delayMs = (requiredTimeMs - actualElapsedMs).coerceIn(2L, 250L)
            delay(delayMs)
            stream.lastReadTime = System.currentTimeMillis()
        } else {
            stream.lastReadTime = now
        }
    }
}
