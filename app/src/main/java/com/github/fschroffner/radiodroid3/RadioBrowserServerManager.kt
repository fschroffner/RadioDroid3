package com.github.fschroffner.radiodroid3

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Random

object RadioBrowserServerManager {
    private var currentServer: String? = null
    private var serverList: Array<String>? = null

    private fun doDnsServerListing(): Array<String> {
        Log.d("DNS", "doDnsServerListing()")
        val listResult = mutableListOf<String>()
        try {
            val list = InetAddress.getAllByName("all.api.radio-browser.info")
            for (item in list) {
                val currentHostAddress = item.hostAddress
                val newItem = InetAddress.getByName(currentHostAddress)
                Log.i("DNS", "Found: $newItem -> ${newItem.canonicalHostName}")
                val name = item.canonicalHostName
                if (name != "all.api.radio-browser.info" && name != currentHostAddress) {
                    Log.i("DNS", "Added entry: '$name'")
                    listResult.add(name)
                }
            }
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        }
        if (listResult.isEmpty()) {
            Log.w("DNS", "Fallback to de1.api.radio-browser.info because dns call did not work.")
            listResult.add("de1.api.radio-browser.info")
        }
        Log.d("DNS", "doDnsServerListing() Found servers: ${listResult.size}")
        return listResult.toTypedArray()
    }

    @JvmStatic
    fun getServerList(forceRefresh: Boolean): Array<String> {
        if (serverList == null || serverList!!.isEmpty() || forceRefresh) {
            serverList = doDnsServerListing()
        }
        return serverList!!
    }

    @JvmStatic
    fun getCurrentServer(): String? {
        if (currentServer == null) {
            val list = getServerList(false)
            if (list.isNotEmpty()) {
                currentServer = list[Random().nextInt(list.size)]
                Log.d("SRV", "Selected new default server: $currentServer")
            } else {
                Log.e("SRV", "no servers found")
            }
        }
        return currentServer
    }

    @JvmStatic
    fun setCurrentServer(newServer: String) { currentServer = newServer }

    @JvmStatic
    fun constructEndpoint(server: String, path: String) = "https://$server/$path"
}
