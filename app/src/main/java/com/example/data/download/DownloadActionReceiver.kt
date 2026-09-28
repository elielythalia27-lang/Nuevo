package com.example.data.download

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(DownloadHelper.EXTRA_DOWNLOAD_ID) ?: return
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val helper = DownloadHelper.getActiveInstance(context)
        when (intent.action) {
            DownloadHelper.ACTION_PAUSE_DOWNLOAD -> {
                helper.pauseDownloadById(itemId)
            }
            DownloadHelper.ACTION_RESUME_DOWNLOAD -> {
                helper.resumeDownloadById(itemId)
            }
            DownloadHelper.ACTION_CANCEL_DOWNLOAD -> {
                notifManager?.cancel(itemId.hashCode())
                helper.cancelDownloadById(itemId)
            }
        }
    }
}
