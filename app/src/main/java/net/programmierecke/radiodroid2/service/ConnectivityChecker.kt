package net.programmierecke.radiodroid2.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.net.ConnectivityManagerCompat

class ConnectivityChecker {
    enum class ConnectionType { NOT_METERED, METERED }

    interface ConnectivityCallback {
        fun onConnectivityChanged(connected: Boolean, connectionType: ConnectionType)
    }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkBroadcastReceiver: BroadcastReceiver? = null
    private var connectivityCallback: ConnectivityCallback? = null
    private var lastConnectionType: ConnectionType? = null

    fun startListening(context: Context, connectivityCallback: ConnectivityCallback) {
        this.connectivityCallback = connectivityCallback
        if (networkCallback != null || networkBroadcastReceiver != null) return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm
        lastConnectionType = getCurrentConnectionType(context)

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val connected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                onConnectivityChanged(connected, if (metered) ConnectionType.METERED else ConnectionType.NOT_METERED)
            }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!)
    }

    fun stopListening(context: Context) {
        connectivityCallback = null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { cm.unregisterNetworkCallback(it); networkCallback = null }
        networkBroadcastReceiver?.let { context.unregisterReceiver(it); networkBroadcastReceiver = null }
    }

    private fun onConnectivityChanged(connected: Boolean, connectionType: ConnectionType) {
        if (lastConnectionType == connectionType) return
        lastConnectionType = connectionType
        connectivityCallback?.onConnectivityChanged(connected, connectionType)
    }

    companion object {
        @JvmStatic
        fun getCurrentConnectionType(context: Context): ConnectionType {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return if (ConnectivityManagerCompat.isActiveNetworkMetered(cm)) ConnectionType.METERED else ConnectionType.NOT_METERED
        }
    }
}
