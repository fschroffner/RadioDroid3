package com.github.fschroffner.radiodroid3

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import info.debatty.java.stringsimilarity.Cosine
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import org.json.JSONArray
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.Reader
import java.io.Writer
import java.util.Collections
import java.util.Observable

open class StationSaveManager(protected val context: Context) : Observable() {
    interface StationStatusListener {
        fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean)
    }

    var listStations: MutableList<DataRadioStation> = mutableListOf()
    private var stationStatusListener: StationStatusListener? = null

    init {
        Load()
    }

    protected open fun getSaveId() = "default"

    protected fun setStationStatusListener(listener: StationStatusListener) {
        stationStatusListener = listener
    }

    open fun add(station: DataRadioStation) {
        if (station.queue == null) station.queue = this
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
        if (station.queue == null) station.queue = this
        listStations.add(0, station)
        Save()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, true)
    }

    fun addAll(stations: List<DataRadioStation>?) {
        stations ?: return
        stations.forEach { it.queue = this }
        listStations.addAll(stations)
    }

    fun getLast() = if (listStations.isNotEmpty()) listStations.last() else null

    fun getFirst() = if (listStations.isNotEmpty()) listStations.first() else null

    fun getById(id: String) = listStations.firstOrNull { it.StationUuid == id }

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

    fun remove(id: String): Int {
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
        station.queue = this
        listStations.add(pos, station)
        Save()
        notifyObservers()
        stationStatusListener?.onStationStatusChanged(station, false)
    }

    fun clear() {
        val oldStations = listStations.toList()
        listStations = mutableListOf()
        Save()
        notifyObservers()
        oldStations.forEach { stationStatusListener?.onStationStatusChanged(it, false) }
    }

    override fun hasChanged() = true

    fun size() = listStations.size

    fun isEmpty() = listStations.isEmpty()

    fun has(id: String) = getById(id) != null

    private fun hasInvalidUuids() = listStations.any { !it.hasValidUuid() }

    fun getList(): List<DataRadioStation> = Collections.unmodifiableList(listStations)

    @Suppress("DEPRECATION")
    private fun refreshStationsFromServer() {
        val radioDroidApp = context.applicationContext as RadioDroidApp
        val httpClient = radioDroidApp.httpClient
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))

        object : AsyncTask<Void, Void, ArrayList<DataRadioStation>>() {
            private lateinit var savedStations: ArrayList<DataRadioStation>

            override fun onPreExecute() {
                savedStations = ArrayList(listStations)
            }

            override fun doInBackground(vararg params: Void?): ArrayList<DataRadioStation> {
                return savedStations.filterTo(ArrayList()) { station ->
                    !station.refresh(httpClient, context) && !station.hasValidUuid() && station.RefreshRetryCount > DataRadioStation.MAX_REFRESH_RETRIES
                }
            }

            override fun onPostExecute(stationsToRemove: ArrayList<DataRadioStation>) {
                listStations.removeAll(stationsToRemove.toSet())
                Save()
                notifyObservers()
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
            }
        }.execute()
    }

    open internal fun Load() {
        listStations.clear()
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val str = prefs.getString(getSaveId(), null)
        if (str != null) {
            val arr = DataRadioStation.DecodeJson(str)
            arr.forEach { it.queue = this }
            listStations.addAll(arr)
            if (hasInvalidUuids() && Utils.hasAnyConnection(context)) refreshStationsFromServer()
        } else {
            Log.w("SAVE", "Load() no stations to load")
        }
    }

    open internal fun Save() {
        val arr = JSONArray()
        listStations.forEach { arr.put(it.toJson()) }
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val str = arr.toString()
        if (BuildConfig.DEBUG) Log.d("SAVE", "wrote: $str")
        prefs.edit().putString(getSaveId(), str).commit()
    }

    @Suppress("DEPRECATION")
    fun SaveM3U(filePath: String, fileName: String) {
        Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_now, filePath, fileName), Toast.LENGTH_LONG).show()
        object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg p: Void?) = SaveM3UInternal(filePath, fileName)
            override fun onPostExecute(result: Boolean) {
                if (result) Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_ok, filePath, fileName), Toast.LENGTH_LONG).show()
                else Toast.makeText(context, context.resources.getString(R.string.notify_save_playlist_nok, filePath, fileName), Toast.LENGTH_LONG).show()
            }
        }.execute()
    }

    @Suppress("DEPRECATION")
    fun SaveM3USimple(filePath: String, fileName: String) = SaveM3U(filePath, fileName)

    @Suppress("DEPRECATION")
    fun LoadM3U(filePath: String, fileName: String) {
        Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_now, filePath, fileName), Toast.LENGTH_LONG).show()
        object : AsyncTask<Void, Void, List<DataRadioStation>?>() {
            override fun doInBackground(vararg p: Void?) = LoadM3UInternal(filePath, fileName)
            override fun onPostExecute(result: List<DataRadioStation>?) {
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
        }.execute()
    }

    @Suppress("DEPRECATION")
    fun LoadM3USimple(reader: Reader) {
        Toast.makeText(context, context.resources.getString(R.string.notify_load_playlist_now, "", ""), Toast.LENGTH_LONG).show()
        object : AsyncTask<Void, Void, List<DataRadioStation>?>() {
            override fun doInBackground(vararg p: Void?) = LoadM3UReader(reader)
            override fun onPostExecute(result: List<DataRadioStation>?) {
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
        }.execute()
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
            val httpClient = (context.applicationContext as RadioDroidApp).httpClient
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
