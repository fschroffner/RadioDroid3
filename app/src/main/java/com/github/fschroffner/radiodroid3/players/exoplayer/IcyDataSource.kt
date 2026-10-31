package com.github.fschroffner.radiodroid3.players.exoplayer

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidContentTypeException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.TransferListener
import com.github.fschroffner.radiodroid3.Utils.getMimeType
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class IcyDataSource(
    private val httpClient: OkHttpClient,
    transferListener: TransferListener,
    private val dataSourceListener: IcyDataSourceListener
) : HttpDataSource {

    interface IcyDataSourceListener {
        fun onDataSourceConnected()
        fun onDataSourceConnectionLost()
        fun onDataSourceConnectionLostIrrecoverably()
        fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?)
        fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo)
        fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int)
    }

    companion object {
        private const val TAG = "IcyDataSource"
        const val DEFAULT_TIME_UNTIL_STOP_RECONNECTING = 2 * 60 * 1000L
        const val DEFAULT_DELAY_BETWEEN_RECONNECTIONS = 0L
    }

    private val transferListeners = CopyOnWriteArrayList<TransferListener>().apply {
        add(transferListener)
    }
    private val requestProperties = ConcurrentHashMap<String, String>()

    private lateinit var dataSpec: DataSpec
    private var request: Request? = null
    private var responseBody: ResponseBody? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var lastResponseCode: Int = 0

    var metadataBytesToSkip = 0
    var remainingUntilMetadata = Int.MAX_VALUE
    private var opened = false

    var shoutcastInfo: ShoutcastInfo? = null
    private var streamLiveInfo: StreamLiveInfo? = null

    override fun open(dataSpec: DataSpec): Long {
        close()
        this.dataSpec = dataSpec
        val allowGzip = (dataSpec.flags and DataSpec.FLAG_ALLOW_GZIP) != 0
        val url = dataSpec.uri.toString().toHttpUrlOrNull()
            ?: throw HttpDataSourceException("Malformed URL: ${dataSpec.uri}", dataSpec, HttpDataSourceException.TYPE_OPEN)
        val builder = Request.Builder().url(url).addHeader("Icy-MetaData", "1")
        if (!allowGzip) builder.addHeader("Accept-Encoding", "identity")

        for ((name, value) in requestProperties) {
            builder.header(name, value)
        }
        for ((name, value) in dataSpec.httpRequestHeaders) {
            builder.header(name, value)
        }

        if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            var range = "bytes=${dataSpec.position}-"
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                range += "${dataSpec.position + dataSpec.length - 1}"
            }
            builder.addHeader("Range", range)
        }

        request = builder.build()
        return connect()
    }

    private fun connect(): Long {
        val response = try {
            httpClient.newCall(request!!).execute()
        } catch (e: IOException) {
            throw HttpDataSourceException("Unable to connect to ${dataSpec.uri}", e, dataSpec, HttpDataSourceException.TYPE_OPEN)
        }

        val responseCode = response.code
        lastResponseCode = responseCode
        if (!response.isSuccessful) {
            val headers = response.headers.toMultimap()
            throw InvalidResponseCodeException(responseCode, response.message, null, headers, dataSpec, ByteArray(0))
        }

        responseBody = response.body!!
        responseHeaders = response.headers.toMultimap()

        val contentType = responseBody!!.contentType()
        val type = contentType?.toString()?.lowercase() ?: getMimeType(dataSpec.uri.toString(), "audio/mpeg")

        if (!isAcceptableContentType(type)) {
            close()
            throw InvalidContentTypeException(type, dataSpec)
        }

        // If the server answered 200 instead of 206, but a position > 0 was requested, skip position bytes
        var bytesSkipped = 0L
        if (responseCode == 200 && dataSpec.position > 0L) {
            val stream = responseBody!!.byteStream()
            var toSkip = dataSpec.position
            while (toSkip > 0L) {
                val skipped = stream.skip(toSkip)
                if (skipped <= 0L) {
                    val temp = ByteArray(minOf(4096L, toSkip).toInt())
                    val read = stream.read(temp)
                    if (read == -1) {
                        close()
                        throw HttpDataSourceException(dataSpec, HttpDataSourceException.TYPE_OPEN)
                    }
                    toSkip -= read
                    bytesSkipped += read
                } else {
                    toSkip -= skipped
                    bytesSkipped += skipped
                }
            }
        }

        opened = true
        try {
            dataSourceListener.onDataSourceConnected()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onDataSourceConnected: $e")
        }

        for (listener in transferListeners) {
            listener.onTransferStart(this, dataSpec, true)
        }

        val rawLength = responseBody!!.contentLength()
        val bytesToRead: Long = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            rawLength != -1L -> maxOf(0L, rawLength - bytesSkipped)
            else -> C.LENGTH_UNSET.toLong()
        }

        if (type != "application/vnd.apple.mpegurl" && type != "application/x-mpegurl") {
            shoutcastInfo = ShoutcastInfo.Decode(response)
            try {
                dataSourceListener.onDataSourceShoutcastInfo(shoutcastInfo)
            } catch (e: Exception) {
                Log.w(TAG, "Error in onDataSourceShoutcastInfo: $e")
            }
            metadataBytesToSkip = 0
            remainingUntilMetadata = shoutcastInfo?.metadataOffset ?: Int.MAX_VALUE
        }

        return bytesToRead
    }

    private fun isAcceptableContentType(contentType: String) =
        !contentType.lowercase(Locale.US).startsWith("text/html")

    override fun close() {
        if (opened) {
            opened = false
            for (listener in transferListeners) {
                listener.onTransferEnd(this, dataSpec, true)
            }
        }
        lastResponseCode = 0
        try { responseBody?.close() } catch (_: Exception) {}
        responseBody = null
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return try {
            val bytesTransferred = readInternal(buffer, offset, readLength)
            if (bytesTransferred > 0) {
                for (listener in transferListeners) {
                    listener.onBytesTransferred(this, dataSpec, true, bytesTransferred)
                }
            }
            bytesTransferred
        } catch (e: HttpDataSourceException) {
            try {
                dataSourceListener.onDataSourceConnectionLost()
            } catch (le: Exception) {
                Log.w(TAG, "Error in onDataSourceConnectionLost: $le")
            }
            throw e
        }
    }

    fun sendToDataSourceListenersWithoutMetadata(buffer: ByteArray, offset: Int, bytesAvailable: Int) {
        val info = shoutcastInfo
        if (info == null || info.metadataOffset <= 0) {
            try {
                dataSourceListener.onDataSourceBytesRead(buffer, offset, bytesAvailable)
            } catch (e: Exception) {
                Log.w(TAG, "Error in onDataSourceBytesRead: $e")
            }
            return
        }

        var off = offset
        var avail = bytesAvailable
        val canSkip = minOf(metadataBytesToSkip, avail)
        off += canSkip
        avail -= canSkip
        remainingUntilMetadata -= canSkip
        while (avail > 0) {
            if (avail > remainingUntilMetadata) {
                if (remainingUntilMetadata > 0) {
                    try {
                        dataSourceListener.onDataSourceBytesRead(buffer, off, remainingUntilMetadata)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error in onDataSourceBytesRead: $e")
                    }
                    off += remainingUntilMetadata
                    avail -= remainingUntilMetadata
                }
                metadataBytesToSkip = buffer[off] * 16 + 1
                remainingUntilMetadata = info.metadataOffset + metadataBytesToSkip
            }
            val bytesLeft = minOf(avail, remainingUntilMetadata)
            if (bytesLeft > metadataBytesToSkip) {
                try {
                    dataSourceListener.onDataSourceBytesRead(buffer, off + metadataBytesToSkip, bytesLeft - metadataBytesToSkip)
                } catch (e: Exception) {
                    Log.w(TAG, "Error in onDataSourceBytesRead: $e")
                }
                metadataBytesToSkip = 0
            } else {
                metadataBytesToSkip -= bytesLeft
            }
            off += bytesLeft
            avail -= bytesLeft
            remainingUntilMetadata -= bytesLeft
        }
    }

    private fun readInternal(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val body = responseBody ?: throw HttpDataSourceException(dataSpec, HttpDataSourceException.TYPE_READ)
        val bytesRead = try {
            body.byteStream().read(buffer, offset, readLength)
        } catch (e: IOException) {
            throw HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ)
        }
        if (bytesRead > 0) {
            sendToDataSourceListenersWithoutMetadata(buffer, offset, bytesRead)
        }
        return bytesRead
    }

    override fun getUri(): Uri = dataSpec.uri
    override fun setRequestProperty(name: String, value: String) {
        requestProperties[name] = value
    }
    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
    }
    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }
    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders
    override fun getResponseCode(): Int = lastResponseCode
    override fun addTransferListener(transferListener: TransferListener) {
        if (!transferListeners.contains(transferListener)) {
            transferListeners.add(transferListener)
        }
    }
}
