package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object NetworkUtils {
    /**
     * Checks if the device is currently connected to an active Wi-Fi or Ethernet network.
     */
    fun isWifiOrEthernet(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if the device has an active internet-capable network connection (Wi-Fi, Ethernet, or Cellular).
     */
    fun isConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if the current connection is exclusively over cellular (mobile data).
     */
    fun isCellular(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Actively verifies that the remote video server or host is reachable via HTTP HEAD/GET request.
     * Prevents false positives where WiFi/Mobile data is connected but there's no actual route to server.
     */
    suspend fun isServerReachable(urlStr: String, timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        if (!urlStr.startsWith("http", ignoreCase = true)) {
            // Local file path or non-http uri -> always accessible offline
            return@withContext true
        }
        try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399 || code == 405 || code == 403 // 405 Method Not Allowed or 403 still indicates server responds
        } catch (_: Exception) {
            // Fallback quick attempt with Range header or simple socket if HEAD is rejected
            try {
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Range", "bytes=0-0")
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.instanceFollowRedirects = true
                val code = connection.responseCode
                connection.disconnect()
                code in 200..399 || code == 206
            } catch (_: Exception) {
                false
            }
        }
    }
}
