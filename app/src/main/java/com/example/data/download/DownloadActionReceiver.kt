package com.example.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(DownloadHelper.EXTRA_DOWNLOAD_ID) ?: return
        val helper = DownloadHelper.getActiveInstance(context)
        when (intent.action) {
            DownloadHelper.ACTION_PAUSE_DOWNLOAD -> {
                helper.pauseDownloadById(itemId)
            }
            DownloadHelper.ACTION_RESUME_DOWNLOAD -> {
                helper.resumeDownloadById(itemId)
            }
            DownloadHelper.ACTION_CANCEL_DOWNLOAD -> {
                helper.cancelDownloadById(itemId)
            }
        }
    }
}
