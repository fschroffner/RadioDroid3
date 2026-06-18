package net.programmierecke.radiodroid2.players.mpd

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * MPD servers repository serialized into preferences.
 * NOT thread safe. Should be backed by database in future.
 */
class MPDServersRepository(private val context: Context) {
    private val servers: MutableList<MPDServerData>
    private val serversLiveData = MutableLiveData<List<MPDServerData>>()
    private var lastServerId = -1

    init {
        servers = getMPDServers(context).toMutableList()
        servers.forEach { if (it.id > lastServerId) lastServerId = it.id }
        serversLiveData.value = servers
    }

    fun getAllServers(): LiveData<List<MPDServerData>> = serversLiveData

    fun addServer(mpdServerData: MPDServerData) {
        mpdServerData.id = ++lastServerId
        servers.add(mpdServerData)
        saveMPDServers(servers, context)
        serversLiveData.postValue(servers)
    }

    fun isEmpty(): Boolean = serversLiveData.value?.isEmpty() ?: true

    fun removeServer(mpdServerData: MPDServerData) {
        val index = servers.indexOfFirst { it.id == mpdServerData.id }
        if (index >= 0) {
            servers.removeAt(index)
            saveMPDServers(servers, context)
            serversLiveData.postValue(servers)
        }
    }

    fun resetAllConnectionStatus() {
        servers.forEach { it.connected = false }
        serversLiveData.postValue(serversLiveData.value)
    }

    fun updatePersistentData(mpdServerData: MPDServerData) {
        val index = servers.indexOfFirst { it.id == mpdServerData.id && !it.contentEquals(mpdServerData) }
        if (index >= 0) {
            servers[index] = mpdServerData
            saveMPDServers(servers, context)
            serversLiveData.postValue(serversLiveData.value)
        }
    }

    fun updateRuntimeData(mpdServerData: MPDServerData) {
        val index = servers.indexOfFirst { it.id == mpdServerData.id && !it.contentEquals(mpdServerData) }
        if (index >= 0) {
            servers[index] = mpdServerData
            serversLiveData.postValue(serversLiveData.value)
        }
    }

    companion object {
        private fun getMPDServers(context: Context): List<MPDServerData> {
            val json = PreferenceManager.getDefaultSharedPreferences(context).getString("mpd_servers", "")
            val type = object : TypeToken<ArrayList<MPDServerData>>() {}.type
            return Gson().fromJson(json, type) ?: emptyList()
        }

        private fun saveMPDServers(servers: List<MPDServerData>, context: Context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("mpd_servers", Gson().toJson(servers))
                .apply()
        }
    }
}
