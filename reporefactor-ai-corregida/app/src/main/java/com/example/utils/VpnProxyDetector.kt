package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class VpnProxyStatus(
    val isBlocked: Boolean = false,
    val reason: String = "",
    val isVpn: Boolean = false,
    val isProxy: Boolean = false
)

object VpnProxyDetector {

    fun checkStatus(context: Context): VpnProxyStatus {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null

            val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")
            val hasProxy = !proxyHost.isNullOrEmpty() &&
                    proxyHost != "127.0.0.1" &&
                    proxyHost != "localhost" &&
                    !proxyPort.isNullOrEmpty()

            val isBlocked = hasVpn || hasProxy
            val reason = when {
                hasVpn && hasProxy -> "VPN y Servidor Proxy detectados"
                hasVpn -> "Conexión VPN detectada"
                hasProxy -> "Servidor Proxy detectado"
                else -> ""
            }

            VpnProxyStatus(
                isBlocked = isBlocked,
                reason = reason,
                isVpn = hasVpn,
                isProxy = hasProxy
            )
        } catch (e: Exception) {
            VpnProxyStatus(isBlocked = false)
        }
    }

    fun isVpnOrProxyActive(context: Context): Boolean {
        return checkStatus(context).isBlocked
    }

    fun observeVpnAndProxy(context: Context): Flow<VpnProxyStatus> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(VpnProxyStatus(false))
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(checkStatus(context))
            }

            override fun onLost(network: Network) {
                trySend(checkStatus(context))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(checkStatus(context))
            }
        }

        trySend(checkStatus(context))

        val request = NetworkRequest.Builder().build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            trySend(checkStatus(context))
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }.distinctUntilChanged()
}
