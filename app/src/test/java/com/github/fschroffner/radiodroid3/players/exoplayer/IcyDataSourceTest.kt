package com.github.fschroffner.radiodroid3.players.exoplayer

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IcyDataSourceTest {

    private val transferredBytesWithoutMetadata = StringBuilder()

    private lateinit var icyDataSource: IcyDataSource

    private val dataSourceListener = object : IcyDataSource.IcyDataSourceListener {
        override fun onDataSourceConnected() {}

        override fun onDataSourceConnectionLost() {}

        override fun onDataSourceConnectionLostIrrecoverably() {}

        override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?) {}

        override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {}

        override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {
            transferredBytesWithoutMetadata.append(String(buffer, offset, length, Charsets.UTF_8))
        }
    }

    private val transferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {}

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    }

    @BeforeAll
    fun setup() {
        icyDataSource = IcyDataSource(OkHttpClient(), transferListener, dataSourceListener)
        icyDataSource.shoutcastInfo = ShoutcastInfo()
        icyDataSource.shoutcastInfo!!.metadataOffset = 16000
    }

    @BeforeEach
    fun init() {
        transferredBytesWithoutMetadata.setLength(0)
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleMultipleMetadataFrames() {
        val buffer = "OFFSETaudio1audio2METADATAMETADATAaudio3audio4audio5METADATAMETADATAMETADATAMETADATAaudio6".toByteArray()
        val offset = 6
        icyDataSource.remainingUntilMetadata = "audioN".length * 2
        icyDataSource.shoutcastInfo!!.metadataOffset = "audioN".length * 3
        icyDataSource.metadataBytesToSkip = 0
        icyDataSource.sendToDataSourceListenersWithoutMetadata(buffer, offset, buffer.size - offset)
        assertEquals("audio1audio2audio3audio4audio5audio6", transferredBytesWithoutMetadata.toString())
        assertEquals(icyDataSource.shoutcastInfo!!.metadataOffset - "audioN".length, icyDataSource.remainingUntilMetadata)
        assertEquals(0, icyDataSource.metadataBytesToSkip)
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleIncompleteMetaDataFrames() {
        val buffer = "OFFSETaudio7audio8METADATAMETADATAaudio9audioAaudioBMETA".toByteArray()
        val offset = 6
        icyDataSource.remainingUntilMetadata = "audioN".length * 2
        icyDataSource.shoutcastInfo!!.metadataOffset = "audioN".length * 3
        icyDataSource.metadataBytesToSkip = 0
        icyDataSource.sendToDataSourceListenersWithoutMetadata(buffer, offset, buffer.size - offset)
        assertEquals("audio7audio8audio9audioAaudioB", transferredBytesWithoutMetadata.toString())
        assertEquals(16 - "META".length, icyDataSource.metadataBytesToSkip)
        assertEquals(icyDataSource.shoutcastInfo!!.metadataOffset + 16 - "META".length, icyDataSource.remainingUntilMetadata)
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleInterruptedMetadata() {
        sendToDataSourceListenersWithoutMetadata_canHandleIncompleteMetaDataFrames()
        val buffer = "DATAMETADATAaudioCaudioDaudioEMETADATAMETADATAaudioF".toByteArray()
        icyDataSource.sendToDataSourceListenersWithoutMetadata(buffer, 0, buffer.size)
        assertEquals("audio7audio8audio9audioAaudioBaudioCaudioDaudioEaudioF", transferredBytesWithoutMetadata.toString())
        assertEquals(0, icyDataSource.metadataBytesToSkip)
        assertEquals("audioN".length * 2, icyDataSource.remainingUntilMetadata)
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleInterruptedAudioData() {
        sendToDataSourceListenersWithoutMetadata_canHandleMultipleMetadataFrames()
        val buffer = "audio7audio8".toByteArray()
        icyDataSource.sendToDataSourceListenersWithoutMetadata(buffer, 0, buffer.size)
        assertEquals("audio1audio2audio3audio4audio5audio6audio7audio8", transferredBytesWithoutMetadata.toString())
        assertEquals(0, icyDataSource.metadataBytesToSkip)
    }
}
