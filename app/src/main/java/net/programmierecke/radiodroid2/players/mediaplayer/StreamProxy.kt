package net.programmierecke.radiodroid2.players.mediaplayer

import android.util.Log
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.recording.Recordable
import net.programmierecke.radiodroid2.recording.RecordableListener
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Arrays
import java.util.Locale

class StreamProxy(
    private val httpClient: OkHttpClient,
    private val uri: String,
    private val callback: StreamProxyListener
) : Recordable {

    companion object {
        private const val TAG = "PROXY"
        private const val MAX_RETRIES = 100
    }

    private var recordableListener: RecordableListener? = null
    private val readBuffer = ByteArray(256 * 16)
    @Volatile private var localAddress: String? = null
    private var isStopped = false

    init {
        createProxy()
    }

    private fun createProxy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "thread started")
        Thread({
            try {
                connectToStream()
                if (BuildConfig.DEBUG) Log.d(TAG, "createProxy() ended")
            } catch (e: Exception) {
                Log.e(TAG, "", e)
            }
        }, "StreamProxy").start()
    }

    private fun proxyDefaultStream(info: ShoutcastInfo?, responseBody: okhttp3.ResponseBody, outStream: OutputStream) {
        var bytesUntilMetaData = 0
        val streamHasMetaData = info != null

        if (info != null) {
            callback.onFoundShoutcastStream(info, false)
            bytesUntilMetaData = info.metadataOffset
        }

        val inputStream: InputStream = responseBody.byteStream()

        while (!isStopped) {
            if (!streamHasMetaData || bytesUntilMetaData > 0) {
                var bytesToRead = minOf(readBuffer.size, inputStream.available())
                if (streamHasMetaData) bytesToRead = minOf(bytesUntilMetaData, bytesToRead)

                val readBytes = inputStream.read(readBuffer, 0, bytesToRead)
                if (readBytes == 0) continue
                if (readBytes < 0) break

                if (streamHasMetaData) bytesUntilMetaData -= readBytes

                outStream.write(readBuffer, 0, readBytes)
                recordableListener?.onBytesAvailable(readBuffer, 0, readBytes)
                callback.onBytesRead(readBuffer, 0, readBytes)
            } else {
                readMetaData(inputStream)
                bytesUntilMetaData = info!!.metadataOffset
            }
        }

        stopRecording()
    }

    private fun readMetaData(inputStream: InputStream): Int {
        val metadataBytes = inputStream.read() * 16
        var metadataBytesToRead = metadataBytes
        var readBytesBufferMetadata = 0

        if (BuildConfig.DEBUG) Log.d(TAG, "metadata size:$metadataBytes")
        if (metadataBytes > 0) {
            Arrays.fill(readBuffer, 0.toByte())
            while (true) {
                val readBytes = inputStream.read(readBuffer, readBytesBufferMetadata, metadataBytesToRead)
                if (readBytes == 0) continue
                if (readBytes < 0) break
                metadataBytesToRead -= readBytes
                readBytesBufferMetadata += readBytes
                if (metadataBytesToRead <= 0) {
                    val s = String(readBuffer, 0, metadataBytes, Charsets.UTF_8)
                    if (BuildConfig.DEBUG) Log.d(TAG, "METADATA:$s")
                    val rawMetadata = decodeShoutcastMetadata(s)
                    val streamLiveInfo = StreamLiveInfo(rawMetadata)
                    if (BuildConfig.DEBUG) Log.d(TAG, "META:${streamLiveInfo.title}")
                    callback.onFoundLiveStreamInfo(streamLiveInfo)
                    break
                }
            }
        }
        return readBytesBufferMetadata + 1
    }

    private fun connectToStream() {
        isStopped = false
        var retry = MAX_RETRIES
        var socketProxy: java.net.Socket? = null
        var outputStream: OutputStream? = null
        var proxyServer: ServerSocket? = null

        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "creating local proxy")

            try {
                proxyServer = ServerSocket(0, 1, InetAddress.getLocalHost())
            } catch (e: IOException) {
                e.printStackTrace()
                return
            }

            val port = proxyServer.localPort
            localAddress = String.format(Locale.US, "http://localhost:%d", port)

            val request = Request.Builder().url(uri).addHeader("Icy-MetaData", "1").build()

            while (!isStopped && retry > 0) {
                var responseBody: okhttp3.ResponseBody? = null
                try {
                    if (BuildConfig.DEBUG) Log.d(TAG, "connecting to stream (try=$retry):$uri")

                    val response = httpClient.newCall(request).execute()
                    responseBody = response.body!!
                    val contentType = responseBody.contentType()

                    if (BuildConfig.DEBUG) Log.d(TAG, "waiting...")

                    if (isStopped) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "stopped from the outside")
                        break
                    }

                    socketProxy?.close()
                    socketProxy = null
                    outputStream?.close()
                    outputStream = null

                    callback.onStreamCreated(localAddress!!)
                    proxyServer.soTimeout = 2000
                    socketProxy = proxyServer.accept()

                    if (BuildConfig.DEBUG) Log.d(TAG, "sending OK to the local media player")
                    outputStream = socketProxy.getOutputStream()
                    outputStream.write(("HTTP/1.0 200 OK\r\nPragma: no-cache\r\nContent-Type: $contentType\r\n\r\n").toByteArray(Charsets.UTF_8))

                    val type = contentType.toString().lowercase()
                    if (BuildConfig.DEBUG) Log.d(TAG, "Content Type: $type")

                    if (type == "application/vnd.apple.mpegurl" || type == "application/x-mpegurl") {
                        Log.e(TAG, "Cannot play HLS streams through proxy!")
                    } else {
                        val info = ShoutcastInfo.Decode(response)
                        proxyDefaultStream(info, responseBody, outputStream)
                    }
                    retry = MAX_RETRIES
                } catch (e: ProtocolException) {
                    Log.e(TAG, "connecting to stream failed due to protocol exception, will NOT retry.", e)
                    break
                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    Log.e(TAG, "exception occurred inside the connection loop, retry.", e)
                } finally {
                    responseBody?.close()
                }

                if (isStopped) break
                retry--
                Thread.sleep(1000)
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted ex Proxy() ", e)
        } finally {
            try {
                proxyServer?.close()
                socketProxy?.close()
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "exception occurred while closing resources.", e)
            }
        }

        if (!isStopped) callback.onStreamStopped()
        stop()
    }

    private fun decodeShoutcastMetadata(metadataStr: String): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        for (kv in metadataStr.split(";")) {
            val n = kv.indexOf('=')
            if (n < 1) continue
            val isString = n + 1 < kv.length && kv.last() == '\'' && kv[n + 1] == '\''
            val key = kv.substring(0, n)
            val value = when {
                isString -> kv.substring(n + 2, kv.length - 1)
                n + 1 < kv.length -> kv.substring(n + 1)
                else -> ""
            }
            metadata[key] = value
        }
        return metadata
    }

    fun getLocalAddress(): String? = localAddress

    fun stop() {
        if (BuildConfig.DEBUG) Log.d(TAG, "stopping proxy.")
        isStopped = true
        stopRecording()
    }

    override fun canRecord() = true

    override fun startRecording(recordableListener: RecordableListener) {
        this.recordableListener = recordableListener
    }

    override fun stopRecording() {
        recordableListener?.onRecordingEnded()
        recordableListener = null
    }

    override fun isRecording() = recordableListener != null

    override fun getRecordNameFormattingArgs(): Map<String, String> = emptyMap()

    override fun getExtension() = "mp3"
}
