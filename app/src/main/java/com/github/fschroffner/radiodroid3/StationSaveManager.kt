package com.github.fschroffner.radiodroid3

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import info.debatty.java.stringsimilarity.Cosine
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.Reader
import java.io.Writer
import java.util.Collections

open class StationSaveManager(
    protected val context: Context,
    protected val stationDao: RadioStationDao? = null
) {

    fun interface DataChangeListener {
        fun onDataChanged()
    }

    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    var listStations: MutableList<DataRadioStation> = mutableListOf()
    private var stationStatusListener: StationStatusListener? = null
    protected val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataChangeListeners = Collections.synchronizedList(mutableListOf<DataChangeListener>())

    open val stationsFlow: Flow<List<DataRadioStation>>
        get() = emptyFlow()

    init {
        Load()
    }

    protected open fun getSaveId() = "default"

    protected fun setStationStatusListener(listener: StationStatusListener) {
        stationStatusListener = listener
    }

    fun addListener(listener: DataChangeListener) {
        if (!dataChangeListeners.contains(listener)) {
            dataChangeListeners.add(listener)
        }
    }

    fun removeListener(listener: DataChangeListener) {
        dataChangeListeners.remove(listener)
    }

    fun addObserver(listener: DataChangeListener) = addListener(listener)
    fun deleteObserver(listener: DataChangeListener) = removeListener(listener)

    open fun notifyObservers() {
        val listenersCopy = synchronized(dataChangeListeners) { ArrayList(dataChangeListeners) }
        for (listener in listenersCopy) {
            listener.onDataChanged()
        }
    }

    open fun add(station: DataRadioStation) {
        listStations.add(station)
        Save()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, true)
    }

    fun addMultiple(stations: List<DataRadioStation>) {
        listStations.addAll(stations)
        Save()
        notifyObservers()
    }

    fun replaceList(stationsNew: List<DataRadioStation>) {
        for (stationNew in stationsNew) {
            for (i in listStations.indices) {
                if (listStations[i].StationUuid == stationNew.StationUuid) {
                    listStations[i] = stationNew
                    break
                }
            }
        }
        Save()
        notifyObservers()
    }

    fun addFront(station: DataRadioStation) {
        listStations.add(0, station)
        Save()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, true)
    }

    fun addAll(stations: List<DataRadioStation>?) {
        stations ?: return
        listStations.addAll(stations)
    }

    fun getLast() = if (listStations.isNotEmpty()) listStations.last() else null

    fun getFirst() = if (listStations.isNotEmpty()) listStations.first() else null

    fun getById(id: String): DataRadioStation? {
        var i = 0
        while (i < listStations.size) {
            val station = listStations.getOrNull(i) ?: break
            if (station.StationUuid == id) return station
            i++
        }
        return null
    }

    fun getNextById(id: String): DataRadioStation? {
        if (listStations.isEmpty()) return null
        for (i in 0 until listStations.size - 1) {
            if (listStations[i].StationUuid == id) return listStations[i + 1]
        }
        return listStations[0]
    }

    fun getPreviousById(id: String): DataRadioStation? {
        if (listStations.isEmpty()) return null
        for (i in 1 until listStations.size) {
            if (listStations[i].StationUuid == id) return listStations[i - 1]
        }
        return listStations.last()
    }

    fun moveWithoutNotify(fromPos: Int, toPos: Int) {
        Collections.rotate(listStations.subList(minOf(fromPos, toPos), maxOf(fromPos, toPos) + 1), Integer.signum(fromPos - toPos))
    }

    fun move(fromPos: Int, toPos: Int) {
        moveWithoutNotify(fromPos, toPos)
        Save()
        notifyObservers()
    }

    @Nullable
    fun getBestNameMatch(query: String): DataRadioStation? {
        val upperQuery = query.uppercase()
        val distMeasure = Cosine()
        var bestStation: DataRadioStation? = null
        var smallestDistance = Double.MAX_VALUE
        for (station in listStations) {
            val distance = distMeasure.distance(station.Name.uppercase(), upperQuery)
            if (distance < smallestDistance) {
                bestStation = station
                smallestDistance = distance
            }
        }
        return bestStation
    }

    open fun remove(id: String): Int {
        for (i in listStations.indices) {
            val station = listStations[i]
            if (station.StationUuid == id) {
                listStations.removeAt(i)
                Save()
                notifyObservers()
                stationStatusListener?.onStationStatusChanged(station, false)
                return i
            }
        }
        return -1
    }

    open fun restore(station: DataRadioStation, pos: Int) {
        listStations.add(pos, station)
        Save()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, false)
    }

    open fun clear() {
        val oldStations = listStations.toList()
        listStations = mutableListOf()
        Save()
        notifyObservers()
        oldStations.forEach { stationStatusListener?.onStationStatusChanged(it, false) }
    }

    open fun hasChanged() = true

    fun size() = listStations.size

    fun isEmpty() = listStations.isEmpty()

    fun has(id: String) = getById(id) != null

    private fun hasInvalidUuids() = listStations.any { !it.hasValidUuid() }

    fun getList(): List<DataRadioStation> = Collections.unmodifiableList(listStations)

    private fun refreshStationsFromServer() {
        val httpClient = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).okHttpClient()
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))

        coroutineScope.launch {
            val savedStations = ArrayList(listStations)
            val stationsToRemove = withContext(Dispatchers.IO) {
                savedStations.filterTo(ArrayList()) { station ->
                    !Utils.refreshStation(station, httpClient, context) && !station.hasValidUuid() && station.RefreshRetryCount > DataRadioStation.MAX_REFRESH_RETRIES
                }
            }
            withContext(Dispatchers.Main) {
                listStations.removeAll(stationsToRemove.toSet())
                Save()
                notifyObservers()
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
            }
        }
    }

    protected open suspend fun loadInternal() = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val legacyJson = prefs.getString(getSaveId(), null)

        if (stationDao != null) {
            // One-time migration of legacy JSON strings from SharedPreferences into Room
            if (legacyJson != null) {
                val legacyStations = DataRadioStation.DecodeJson(legacyJson)
                if (legacyStations.isNotEmpty()) {
                    legacyStations.forEachIndexed { index, s ->
                        if (getSaveId() == "favourites") {
                            s.isFavorite = true
                            s.favoriteOrder = index
                        } else if (getSaveId() == "history") {
                            s.lastPlayed = System.currentTimeMillis() - (index * 1000L)
                        }
                        stationDao.insertOrUpdate(s)
                    }
                }
                prefs.edit().remove(getSaveId()).apply()
            }

            // Load directly from Room
            val fromDb = if (getSaveId() == "favourites") {
                stationDao.getFavoriteStationsList()
            } else if (getSaveId() == "history") {
                stationDao.getHistoryStationsList()
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                listStations = fromDb.toMutableList()
                notifyObservers()
                if (hasInvalidUuids() && Utils.hasAnyConnection(context)) refreshStationsFromServer()
            }
        } else {
            if (legacyJson != null) {
                val arr = DataRadioStation.DecodeJson(legacyJson)
                withContext(Dispatchers.Main) {
                    listStations.clear()
                    listStations.addAll(arr)
                    notifyObservers()
                }
            }
        }
    }

    open internal fun Load() {
        coroutineScope.launch {
            loadInternal()
        }
    }

    open internal fun Save() {
        if (stationDao != null) {
            val currentList = ArrayList(listStations)
            coroutineScope.launch {
                if (getSaveId() == "favourites") {
                    currentList.forEachIndexed { index, station ->
                        station.isFavorite = true
                        station.favoriteOrder = index
                        stationDao.insertOrUpdate(station)
                    }
                } else if (getSaveId() == "history") {
                    currentList.forEachIndexed { index, station ->
                        if (station.lastPlayed == null) {
                            station.lastPlayed = System.currentTimeMillis() - (index * 1000L)
                        }
                        stationDao.insertOrUpdate(station)
                    }
                }
            }
        }
    }

    fun SaveM3U(filePath: String, fileName: String) {
        Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_now, filePath, fileName), Toast.LENGTH_LONG).show()
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                SaveM3UInternal(filePath, fileName)
            }
            withContext(Dispatchers.Main) {
                if (result) Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_ok, filePath, fileName), Toast.LENGTH_LONG).show()
                else Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_nok, filePath, fileName), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun SaveM3USimple(filePath: String, fileName: String) = SaveM3U(filePath, fileName)

    fun LoadM3U(filePath: String, fileName: String) {
        Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_now, filePath, fileName), Toast.LENGTH_LONG).show()
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                LoadM3UInternal(filePath, fileName)
            }
            withContext(Dispatchers.Main) {
                if (result != null) {
                    Log.i("LOAD", "Loaded ${result.size} stations")
                    addMultiple(result)
                    Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_ok, result.size, filePath, fileName), Toast.LENGTH_LONG).show()
                } else {
                    Log.e("LOAD", "Load failed")
                    Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_nok, filePath, fileName), Toast.LENGTH_LONG).show()
                }
                notifyObservers()
            }
        }
    }

    fun LoadM3USimple(reader: Reader) {
        Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_now, "", ""), Toast.LENGTH_LONG).show()
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                LoadM3UReader(reader)
            }
            withContext(Dispatchers.Main) {
                if (result != null) {
                    Log.i("LOAD", "Loaded ${result.size} stations")
                    addMultiple(result)
                    Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_ok, result.size, "", ""), Toast.LENGTH_LONG).show()
                } else {
                    Log.e("LOAD", "Load failed")
                    Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_nok, "", ""), Toast.LENGTH_LONG).show()
                }
                notifyObservers()
            }
        }
    }

    protected val M3U_PREFIX = "#RADIOBROWSERUUID:"

    internal fun SaveM3UInternal(filePath: String, fileName: String): Boolean {
        return try {
            val f = File(filePath, fileName)
            val bw = BufferedWriter(FileWriter(f, false))
            val r = SaveM3UWriter(bw)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                @Suppress("DEPRECATION")
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)}")))
            } else {
                MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null)
            }
            r
        } catch (e: Exception) {
            Log.e("Exception", "File write failed: $e")
            false
        }
    }

    fun SaveM3UWriter(bw: Writer): Boolean {
        return try {
            bw.write("#EXTM3U\n")
            for (station in listStations) {
                bw.write("$M3U_PREFIX${station.StationUuid}\n")
                bw.write("#EXTINF:-1,${station.Name}\n")
                bw.write("${station.StreamUrl}\n\n")
            }
            bw.flush()
            bw.close()
            true
        } catch (e: Exception) {
            Log.e("Exception", "File write failed: $e")
            false
        }
    }

    internal fun LoadM3UInternal(filePath: String, fileName: String): List<DataRadioStation>? {
        return try {
            LoadM3UReader(FileReader(File(filePath, fileName)))
        } catch (e: Exception) {
            Log.e("LOAD", "File read failed: $e")
            null
        }
    }

    internal fun LoadM3UReader(reader: Reader): List<DataRadioStation>? {
        return try {
            val httpClient = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).okHttpClient()
            val listUuids = mutableListOf<String>()
            BufferedReader(reader).useLines { lines ->
                for (line in lines) {
                    Log.v("LOAD", "line: $line")
                    if (line.startsWith(M3U_PREFIX)) {
                        try {
                            listUuids.add(line.substring(M3U_PREFIX.length).trim())
                        } catch (e: Exception) {
                            Log.e("LOAD", e.toString())
                        }
                    }
                }
            }
            val fetched = Utils.getStationsByUuid(httpClient, context, listUuids)
            listUuids.mapNotNull { uuid -> fetched?.firstOrNull { it.StationUuid == uuid } }
        } catch (e: Exception) {
            Log.e("LOAD", "File read failed: $e")
            null
        }
    }

    companion object {
        @JvmStatic
        @Suppress("DEPRECATION")
        fun getSaveDir(): String {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString()
            val folder = File(path)
            if (!folder.exists() && !folder.mkdirs()) Log.e("SAVE", "could not create dir:$path")
            return path
        }
    }
}
