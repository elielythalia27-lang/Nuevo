package com.example.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Notification control entry point.
 *
 * Using a BroadcastReceiver for Pause/Cancel avoids starting a foreground service
 * merely to process a button tap. On Android 14+ the command is routed directly
 * to the UIDT job; older Android versions forward it to the compatibility service.
 */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadHelper.ACTION_PAUSE_ALL -> {
                DownloadForegroundService.pauseAll(context)
                return
            }
            DownloadHelper.ACTION_CANCEL_ALL -> {
                DownloadForegroundService.cancelAll(context)
                return
            }
        }

        val itemId = intent.getStringExtra(DownloadHelper.EXTRA_DOWNLOAD_ID)
            ?: intent.getLongExtra(DownloadHelper.EXTRA_DOWNLOAD_ID, -1L)
                .takeIf { it != -1L }?.toString()
            ?: return

        when (intent.action) {
            DownloadHelper.ACTION_PAUSE_DOWNLOAD ->
                DownloadForegroundService.pauseDownload(context, itemId)

            DownloadHelper.ACTION_RESUME_DOWNLOAD ->
                DownloadForegroundService.resumeDownload(context, itemId)

            DownloadHelper.ACTION_CANCEL_DOWNLOAD ->
                DownloadForegroundService.cancelDownload(context, itemId)
        }
    }
}
