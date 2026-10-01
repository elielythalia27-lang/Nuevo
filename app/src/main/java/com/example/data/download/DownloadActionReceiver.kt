package com.example.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver for handling interactive notification actions (Pause, Resume, Cancel).
 * Routes intents directly to DownloadForegroundService for instant processing.
 */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(DownloadHelper.EXTRA_DOWNLOAD_ID) ?: return
        when (intent.action) {
            DownloadHelper.ACTION_PAUSE_DOWNLOAD -> {
                DownloadForegroundService.pauseDownload(context, itemId)
            }
            DownloadHelper.ACTION_RESUME_DOWNLOAD -> {
                DownloadForegroundService.resumeDownload(context, itemId)
            }
            DownloadHelper.ACTION_CANCEL_DOWNLOAD -> {
                DownloadForegroundService.cancelDownload(context, itemId)
            }
        }
    }
}
