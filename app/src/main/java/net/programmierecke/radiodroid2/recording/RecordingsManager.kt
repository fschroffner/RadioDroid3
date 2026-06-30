package net.programmierecke.radiodroid2.recording

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.Utils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.Observable

class RecordingsManager {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormatter = SimpleDateFormat("HH-mm", Locale.US)

    private inner class RecordingsObservable : Observable() {
        override fun hasChanged() = true
    }

    val savedRecordingsObservable: Observable = RecordingsObservable()

    private inner class RunningRecordableListener(
        private val runningRecordingInfo: RunningRecordingInfo
    ) : RecordableListener {
        private var ended = false

        override fun onBytesAvailable(buffer: ByteArray, offset: Int, length: Int) {
            try {
                runningRecordingInfo.outputStream!!.write(buffer, offset, length)
                runningRecordingInfo.bytesWritten += length
            } catch (e: IOException) {
                e.printStackTrace()
                runningRecordingInfo.recordable?.stopRecording()
            }
        }

        override fun onRecordingEnded() {
            if (ended) return
            ended = true
            try { runningRecordingInfo.outputStream?.close() } catch (_: IOException) {}
            runningRecordingInfo.recordable?.let { this@RecordingsManager.stopRecording(it) }
        }
    }

    private val runningRecordings = mutableMapOf<Recordable, RunningRecordingInfo>()
    private val savedRecordings = mutableListOf<DataRecording>()

    fun record(context: Context, recordable: Recordable) {
        if (!recordable.canRecord() || runningRecordings.containsKey(recordable)) return

        val info = RunningRecordingInfo()
        info.recordable = recordable

        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val fileNameFormat = prefs.getString("record_name_formatting", context.getString(R.string.settings_record_name_formatting_default)) ?: ""

        val formattingArgs = recordable.getRecordNameFormattingArgs().toMutableMap()
        val currentTime = Calendar.getInstance().time
        formattingArgs["date"] = dateFormatter.format(currentTime)
        formattingArgs["time"] = timeFormatter.format(currentTime)
        val recordNum = prefs.getInt("record_num", 1)
        formattingArgs["index"] = recordNum.toString()

        val recordTitle = Utils.formatStringWithNamedArgs(fileNameFormat, formattingArgs)
        info.title = recordTitle
        info.fileName = "$recordTitle.${recordable.getExtension()}"

        val filePath = getRecordDir() + "/" + info.fileName
        try {
            info.outputStream = FileOutputStream(filePath)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return
        }

        recordable.startRecording(RunningRecordableListener(info))
        runningRecordings[recordable] = info
        prefs.edit().putInt("record_num", recordNum + 1).apply()
    }

    fun stopRecording(recordable: Recordable) {
        recordable.stopRecording()
        runningRecordings.remove(recordable)
        updateRecordingsList()
    }

    fun getRecordingInfo(recordable: Recordable): RunningRecordingInfo? = runningRecordings[recordable]

    fun getRunningRecordings(): Map<Recordable, RunningRecordingInfo> = Collections.unmodifiableMap(runningRecordings)

    fun getSavedRecordings(): List<DataRecording> = ArrayList(savedRecordings)

    fun updateRecordingsList() {
        val path = getRecordDir()
        if (BuildConfig.DEBUG) Log.d(TAG, "Updating recordings from $path")

        savedRecordings.clear()
        val files = File(path).listFiles()
        if (files != null) {
            files.mapTo(savedRecordings) { f ->
                DataRecording().apply { Name = f.name; Time = Date(f.lastModified()) }
            }
            savedRecordings.sortByDescending { it.Time?.time ?: 0L }
        } else {
            Log.e(TAG, "Could not enumerate files in recordings directory")
        }
        savedRecordingsObservable.notifyObservers()
    }

    companion object {
        private const val TAG = "Recordings"

        @JvmStatic
        fun getRecordDir(): String {
            @Suppress("DEPRECATION")
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString() + "/Recordings"
            val folder = File(path)
            if (!folder.exists() && !folder.mkdirs()) Log.e(TAG, "could not create dir:$path")
            return path
        }
    }
}
