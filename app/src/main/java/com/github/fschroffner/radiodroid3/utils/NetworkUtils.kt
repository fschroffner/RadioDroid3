package com.github.fschroffner.radiodroid3.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {

    @JvmStatic
    fun hasWifiConnection(context: Context): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connManager.activeNetwork ?: return false
            val capabilities = connManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            mWifi?.isConnected == true
        }
    }

    @JvmStatic
    fun hasAnyConnection(context: Context): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connManager.activeNetwork ?: return false
            val capabilities = connManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val netInfo = connManager.activeNetworkInfo
            netInfo != null && netInfo.isConnected
        }
    }
}
