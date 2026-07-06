package com.github.fschroffner.radiodroid3.players.exoplayer

import android.net.Uri
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import java.util.Locale

class IcyDataSource(
    private val httpClient: OkHttpClient,
    private val transferListener: TransferListener,
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
        const val DEFAULT_TIME_UNTIL_STOP_RECONNECTING = 2 * 60 * 1000L
        const val DEFAULT_DELAY_BETWEEN_RECONNECTIONS = 0L
    }

    private lateinit var dataSpec: DataSpec
    private var request: Request? = null
    private var responseBody: ResponseBody? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    var metadataBytesToSkip = 0
    var remainingUntilMetadata = Int.MAX_VALUE
    private var opened = false

    var shoutcastInfo: ShoutcastInfo? = null
    private var streamLiveInfo: StreamLiveInfo? = null

    override fun open(dataSpec: DataSpec): Long {
        close()
        this.dataSpec = dataSpec
        val allowGzip = (dataSpec.flags and DataSpec.FLAG_ALLOW_GZIP) != 0
        val url = dataSpec.uri.toString().toHttpUrl()
        val builder = Request.Builder().url(url).addHeader("Icy-MetaData", "1")
        if (!allowGzip) builder.addHeader("Accept-Encoding", "identity")
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

        opened = true
        dataSourceListener.onDataSourceConnected()
        transferListener.onTransferStart(this, dataSpec, true)

        if (type == "application/vnd.apple.mpegurl" || type == "application/x-mpegurl") {
            return responseBody!!.contentLength()
        } else {
            shoutcastInfo = ShoutcastInfo.Decode(response)
            dataSourceListener.onDataSourceShoutcastInfo(shoutcastInfo)
            metadataBytesToSkip = 0
            remainingUntilMetadata = shoutcastInfo?.metadataOffset ?: Int.MAX_VALUE
            return responseBody!!.contentLength()
        }
    }

    private fun isAcceptableContentType(contentType: String) =
        !contentType.lowercase(Locale.US).startsWith("text/html")

    override fun close() {
        if (opened) {
            opened = false
            transferListener.onTransferEnd(this, dataSpec, true)
        }
        try { responseBody?.close() } catch (_: Exception) {}
        responseBody = null
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return try {
            val bytesTransferred = readInternal(buffer, offset, readLength)
            transferListener.onBytesTransferred(this, dataSpec, true, bytesTransferred)
            bytesTransferred
        } catch (e: HttpDataSourceException) {
            dataSourceListener.onDataSourceConnectionLost()
            throw e
        }
    }

    fun sendToDataSourceListenersWithoutMetadata(buffer: ByteArray, offset: Int, bytesAvailable: Int) {
        var off = offset
        var avail = bytesAvailable
        val canSkip = minOf(metadataBytesToSkip, avail)
        off += canSkip
        avail -= canSkip
        remainingUntilMetadata -= canSkip
        while (avail > 0) {
            if (avail > remainingUntilMetadata) {
                if (remainingUntilMetadata > 0) {
                    dataSourceListener.onDataSourceBytesRead(buffer, off, remainingUntilMetadata)
                    off += remainingUntilMetadata
                    avail -= remainingUntilMetadata
                }
                metadataBytesToSkip = buffer[off] * 16 + 1
                remainingUntilMetadata = shoutcastInfo!!.metadataOffset + metadataBytesToSkip
            }
            val bytesLeft = minOf(avail, remainingUntilMetadata)
            if (bytesLeft > metadataBytesToSkip) {
                dataSourceListener.onDataSourceBytesRead(buffer, off + metadataBytesToSkip, bytesLeft - metadataBytesToSkip)
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
        sendToDataSourceListenersWithoutMetadata(buffer, offset, bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri = dataSpec.uri
    override fun setRequestProperty(name: String, value: String) {}
    override fun clearRequestProperty(name: String) {}
    override fun clearAllRequestProperties() {}
    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders
    override fun getResponseCode(): Int = 0
    override fun addTransferListener(transferListener: TransferListener) {}
}
