package net.programmierecke.radiodroid2.players.mpd

import android.text.TextUtils
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.LinkedList

open class MPDAsyncTask : Runnable {
    fun interface ReadStage {
        fun onRead(task: MPDAsyncTask, result: String): Boolean
    }

    fun interface WriteStage {
        @Throws(IOException::class)
        fun onWrite(task: MPDAsyncTask, bufferedWriter: BufferedWriter): Boolean
    }

    fun interface FailureCallback {
        fun onFailure(task: MPDAsyncTask)
    }

    private lateinit var readStages: LinkedList<ReadStage>
    private lateinit var writeStages: LinkedList<WriteStage>
    private var failureCallback: FailureCallback? = null
    private var timeoutMs: Long = 0

    private lateinit var mpdServerData: MPDServerData
    private lateinit var mpdClient: MPDClient

    fun setStages(readStages: Array<ReadStage>, writeStages: Array<WriteStage>, failureCallback: FailureCallback?) {
        this.readStages = LinkedList(readStages.toList())
        this.writeStages = LinkedList(writeStages.toList())
        this.failureCallback = failureCallback
    }

    fun setTimeout(timeoutMs: Long) { this.timeoutMs = timeoutMs }

    fun fail() { failureCallback?.onFailure(this) }

    override fun run() {
        try {
            if (!TextUtils.isEmpty(mpdServerData.password)) {
                readStages.addFirst(okReadStage())
                writeStages.addFirst(loginWriteStage(mpdServerData.password!!))
            }

            val s = Socket()
            s.connect(InetSocketAddress(mpdServerData.hostname, mpdServerData.port), timeoutMs.toInt())
            val reader = BufferedReader(InputStreamReader(s.inputStream, Charset.forName("UTF-8")))
            val writer = BufferedWriter(OutputStreamWriter(s.outputStream, Charset.forName("UTF-8")))

            onConnected(reader, writer)

            reader.close()
            writer.close()
            s.close()
        } catch (ex: IOException) {
            fail()
        }
    }

    private fun onConnected(reader: BufferedReader, writer: BufferedWriter) {
        val readBuffer = CharBuffer.allocate(1024)
        var c = true
        while (c) {
            readBuffer.clear()
            val readStage = readStages.poll()
            if (readStage != null) {
                reader.read(readBuffer)
                readBuffer.position(0)
                Log.d(TAG, readBuffer.toString())
                c = readStage.onRead(this, readBuffer.toString())
            } else {
                c = false
            }

            if (c) {
                val writeStage = writeStages.poll()
                if (writeStage != null) {
                    c = writeStage.onWrite(this, writer)
                    writer.flush()
                } else {
                    c = false
                }
            }
        }
    }

    fun setParams(mpdClient: MPDClient, mpdServerData: MPDServerData) {
        this.mpdClient = mpdClient
        this.mpdServerData = MPDServerData(mpdServerData)
    }

    fun getMpdServerData(): MPDServerData = mpdServerData

    fun notifyServerUpdated() { mpdClient.notifyServerUpdate(mpdServerData) }

    companion object {
        private const val TAG = "MPDAsyncTask"

        @JvmStatic
        fun okReadStage() = ReadStage { task, result ->
            val ok = result.startsWith("OK")
            if (!ok) task.fail()
            ok
        }

        @JvmStatic
        fun statusWriteStage() = WriteStage { _, writer ->
            writer.write("status\n")
            true
        }

        @JvmStatic
        fun loginWriteStage(password: String) = WriteStage { _, writer ->
            writer.write("password $password\n")
            true
        }

        @JvmStatic
        fun statusReadStage(c: Boolean) = ReadStage { task, result ->
            task.getMpdServerData().updateStatus(result)
            task.notifyServerUpdated()
            c
        }
    }
}
