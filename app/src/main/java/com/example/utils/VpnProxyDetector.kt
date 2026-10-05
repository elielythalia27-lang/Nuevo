package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.NetworkInterface

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

            // 1. VPN detection via NetworkCapabilities
            var hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            if (!hasVpn && cm != null) {
                try {
                    hasVpn = cm.allNetworks.any { net ->
                        cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    }
                } catch (_: Exception) {}
            }

            // 2. VPN detection via NetworkInterface names (tun, ppp, tap, utun, wg, wireguard, ipsec)
            if (!hasVpn) {
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    if (interfaces != null) {
                        for (intf in interfaces) {
                            if (intf.isUp && (intf.name.startsWith("tun", ignoreCase = true) ||
                                    intf.name.startsWith("ppp", ignoreCase = true) ||
                                    intf.name.startsWith("tap", ignoreCase = true) ||
                                    intf.name.startsWith("utun", ignoreCase = true) ||
                                    intf.name.startsWith("wg", ignoreCase = true) ||
                                    intf.name.startsWith("wireguard", ignoreCase = true) ||
                                    intf.name.startsWith("ipsec", ignoreCase = true))) {
                                hasVpn = true
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Proxy detection: Java system properties
            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")
            val hasSystemPropertyProxy = !proxyHost.isNullOrEmpty() &&
                    proxyHost != "127.0.0.1" &&
                    proxyHost != "localhost" &&
                    !proxyPort.isNullOrEmpty()

            // 4. Proxy detection: Android LinkProperties & ConnectivityManager defaultProxy
            var hasAndroidSystemProxy = false
            if (cm != null) {
                try {
                    val linkProxy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activeNetwork != null) {
                        cm.getLinkProperties(activeNetwork)?.httpProxy
                    } else null
                    val defaultProxy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cm.defaultProxy
                    } else null

                    val p = linkProxy ?: defaultProxy
                    if (p != null && !p.host.isNullOrBlank() && p.port > 0) {
                        val host = p.host ?: ""
                        if (host != "127.0.0.1" && host != "localhost") {
                            hasAndroidSystemProxy = true
                        }
                    }
                } catch (_: Exception) {}
            }

            val hasProxy = hasSystemPropertyProxy || hasAndroidSystemProxy

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
