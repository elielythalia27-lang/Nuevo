package com.example.data.download

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * DownloadBandwidthCoordinator (Option B - Dynamic Fair-Share Bandwidth Allocator)
 *
 * Ensures that whenever there are multiple active downloads (N > 1), available network
 * bandwidth is distributed in equal parts (1/N share) across all active download streams.
 *
 * Key behaviors:
 * 1. When N = 1 (single download): No throttling delay is applied (runs at 100% max speed).
 * 2. When N >= 2 (multiple downloads): The coordinator measures aggregate network throughput
 *    and regulates individual stream pacing so every download receives an equal slice (1/N)
 *    of the total available bandwidth without starvation or unfair dominance.
 * 3. Dynamic adjustment: When a download starts, finishes, pauses, or cancels, N is immediately
 *    recalculated, and bandwidth allocation dynamically expands or contracts in real time.
 */
object DownloadBandwidthCoordinator {

    private class StreamTracker(
        val downloadId: String,
        @Volatile var registeredAt: Long = System.currentTimeMillis()
    ) {
        val windowBytes = AtomicLong(0L)
        @Volatile var windowStartTime = System.currentTimeMillis()
        @Volatile var smoothedSpeedBytesPerSec: Long = 0L
        @Volatile var lastThrottleTime: Long = 0L

        fun recordBytes(bytes: Int): Long {
            return windowBytes.addAndGet(bytes.toLong())
        }

        fun updateSpeed(now: Long) {
            val elapsed = max(1L, now - windowStartTime)
            val currentBytes = windowBytes.get()
            if (elapsed >= 400L) {
                val instantSpeed = (currentBytes * 1000L) / elapsed
                smoothedSpeedBytesPerSec = if (smoothedSpeedBytesPerSec == 0L) {
                    instantSpeed
                } else {
                    // Exponential smoothing (alpha = 0.4)
                    ((smoothedSpeedBytesPerSec * 6 + instantSpeed * 4) / 10).coerceAtLeast(1024L)
                }
                windowBytes.set(0L)
                windowStartTime = now
            }
        }
    }

    private val activeStreams = ConcurrentHashMap<String, StreamTracker>()
    private val totalAggregatedSpeed = AtomicLong(0L)
    @Volatile private var lastAggregateCalcTime = 0L

    /**
     * Registers an active streaming download.
     */
    fun registerStream(downloadId: String) {
        val tracker = StreamTracker(downloadId)
        activeStreams[downloadId] = tracker
        recalculateAggregateSpeed()
    }

    /**
     * Unregisters a download stream when it completes, pauses, cancels, or fails.
     */
    fun unregisterStream(downloadId: String) {
        activeStreams.remove(downloadId)
        recalculateAggregateSpeed()
    }

    /**
     * Returns the number of currently active streaming downloads.
     */
    fun getActiveStreamCount(): Int = activeStreams.size

    /**
     * Returns the aggregate speed across all active streams in bytes/sec.
     */
    fun getAggregateSpeedBytesPerSec(): Long = totalAggregatedSpeed.get()

    /**
     * Recalculates total aggregate speed across all active streams.
     */
    private fun recalculateAggregateSpeed() {
        val now = System.currentTimeMillis()
        var total = 0L
        for (tracker in activeStreams.values) {
            tracker.updateSpeed(now)
            total += tracker.smoothedSpeedBytesPerSec
        }
        totalAggregatedSpeed.set(total)
        lastAggregateCalcTime = now
    }

    /**
     * Paces the download stream according to the Fair-Share (1/N) Bandwidth Allocation.
     *
     * @param downloadId The ID of the downloading item.
     * @param bytesRead The chunk of bytes just read from the network stream.
     * @return Number of milliseconds to delay (0 if no delay is needed).
     */
    suspend fun paceTransfer(downloadId: String, bytesRead: Int): Long {
        if (bytesRead <= 0) return 0L

        val tracker = activeStreams.computeIfAbsent(downloadId) { StreamTracker(downloadId) }
        val now = System.currentTimeMillis()
        tracker.recordBytes(bytesRead)

        val activeCount = activeStreams.size

        // Case 1: Single active download -> 100% full speed, no throttling delay
        if (activeCount <= 1) {
            tracker.updateSpeed(now)
            return 0L
        }

        // Case 2: Multiple downloads (N >= 2) -> Equal distribution (1/N of total speed)
        if (now - lastAggregateCalcTime >= 350L) {
            recalculateAggregateSpeed()
        } else {
            tracker.updateSpeed(now)
        }

        val totalSpeed = max(totalAggregatedSpeed.get(), 100 * 1024L) // at least baseline floor
        val fairShareTargetPerStream = totalSpeed / activeCount

        // Allow an adaptive 10% headroom above fair share so throughput can ramp up dynamically
        val allowableShareWithHeadroom = (fairShareTargetPerStream * 1.10).toLong()
        val streamSpeed = tracker.smoothedSpeedBytesPerSec

        // If this stream is exceeding its fair 1/N portion, apply gentle pacing delay
        if (streamSpeed > allowableShareWithHeadroom && allowableShareWithHeadroom > 0) {
            val excessSpeed = streamSpeed - fairShareTargetPerStream
            // Proportional delay calculation: compute ms needed to balance byte rate
            val delayMs = ((excessSpeed * 1000L) / (fairShareTargetPerStream * 8L)).coerceIn(5L, 80L)

            // Avoid throttling too frequently back-to-back (min 40ms interval between delays)
            if (now - tracker.lastThrottleTime >= 40L) {
                tracker.lastThrottleTime = now
                delay(delayMs)
                return delayMs
            }
        }

        return 0L
    }
}
