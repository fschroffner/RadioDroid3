package com.github.fschroffner.radiodroid3.playlist

import android.util.Log
import com.github.fschroffner.radiodroid3.BuildConfig

class PlaylistM3UEntry {
    var header: String? = null
    var content: String = ""
    var length: Int = -1
    var title: String? = null
    var bitrate: Int = -1
    var programid: Int = -1
    var isStreamInfo: Boolean = false

    constructor(header: String?, content: String) {
        this.header = header
        this.content = content
        decode()
    }

    constructor(content: String) {
        this.header = null
        this.content = content
    }

    private fun decode() {
        val h = header ?: return
        when {
            h.startsWith(EXTINF) -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "found EXTINF:$h")
                val attributes = h.substring(EXTINF.length)
                val sep = attributes.indexOf(",")
                length = attributes.substring(0, sep).toIntOrNull() ?: -1
                title = attributes.substring(sep + 1)
            }
            h.startsWith(STREAMINF) -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "found STREAMINFO:$h")
                isStreamInfo = true
                val attributes = h.substring(STREAMINF.length)
                attributes.split(",").forEach { attr ->
                    when {
                        attr.startsWith(STREAMINF_BANDWIDTH) -> bitrate = attr.substring(STREAMINF_BANDWIDTH.length).toIntOrNull() ?: -1
                        attr.startsWith(STREAMINF_PROGRAM) -> programid = attr.substring(STREAMINF_PROGRAM.length).toIntOrNull() ?: -1
                    }
                }
            }
        }
    }

    companion object {
        const val EXTINF = "#EXTINF:"
        const val STREAMINF = "#EXT-X-STREAM-INF:"
        const val STREAMINF_PROGRAM = "PROGRAM-ID="
        const val STREAMINF_BANDWIDTH = "BANDWIDTH="
        const val STREAMINF_CODECS = "CODECS="
        const val TAG = "M3U"
    }
}
