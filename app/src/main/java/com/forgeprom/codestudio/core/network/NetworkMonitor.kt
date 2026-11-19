package com.forgeprom.codestudio.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * مراقب حالة الشبكة ووضع الطيران (NetworkMonitor)
 * يكتشف لحظياً حالة الاتصال بالإنترنت أو تفعيل وضع الطيران (Airplane Mode)
 * مع استخدام distinctUntilChanged لمنع إعادة التركيب غير الضرورية للواجهة.
 */
class NetworkMonitor(private val context: Context) {

    data class NetworkState(
        val isOnline: Boolean,
        val isAirplaneMode: Boolean,
        val connectionType: String
    )

    fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }

    fun getCurrentNetworkState(): NetworkState {
        val airplane = isAirplaneModeOn()
        if (airplane) {
            return NetworkState(
                isOnline = false,
                isAirplaneMode = true,
                connectionType = "وضع الطيران (محلي 100%)"
            )
        }

        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return NetworkState(
                isOnline = false,
                isAirplaneMode = false,
                connectionType = "غير متصل (محلي 100%)"
            )
            val caps = cm.getNetworkCapabilities(network) ?: return NetworkState(
                isOnline = false,
                isAirplaneMode = false,
                connectionType = "غير متصل (محلي 100%)"
            )

            val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "بيانات الهاتف (Cellular)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "متصل"
            }

            NetworkState(
                isOnline = isConnected,
                isAirplaneMode = false,
                connectionType = if (isConnected) type else "غير متصل (محلي 100%)"
            )
        } catch (_: Exception) {
            NetworkState(
                isOnline = false,
                isAirplaneMode = false,
                connectionType = "غير متصل (محلي 100%)"
            )
        }
    }

    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        trySend(getCurrentNetworkState())

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentNetworkState())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentNetworkState())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(getCurrentNetworkState())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var registered = false
        try {
            cm.registerNetworkCallback(request, callback)
            registered = true
        } catch (_: Exception) {
            // في حال عدم توفر الصلاحية يستمر بالعمل المحلي
        }

        awaitClose {
            if (registered) {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {}
            }
        }
    }.distinctUntilChanged()
}
