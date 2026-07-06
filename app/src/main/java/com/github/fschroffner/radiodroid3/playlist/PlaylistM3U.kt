package com.github.fschroffner.radiodroid3.playlist

import java.io.BufferedReader
import java.io.StringReader
import java.net.MalformedURLException
import java.net.URL

class PlaylistM3U(private val path: URL, private val fullText: String) {
    private var extended = false
    private val entries = mutableListOf<PlaylistM3UEntry>()
    private var header: String? = null

    init { decode() }

    private fun decode() {
        getLines().forEach { line ->
            try { decodeLine(line) } catch (_: MalformedURLException) {}
        }
    }

    private fun resolveToBase(file: String): URL {
        val filePath = getBasePath(path.path) + "/" + file
        return URL(path.protocol, path.host, path.port, filePath)
    }

    private fun decodeLine(line: String) {
        when {
            line.startsWith(EXTENDED) -> extended = true
            line.startsWith(COMMENTMARKER) -> if (extended) header = line
            else -> {
                val lineLower = line.lowercase()
                val content = if (lineLower.startsWith("http://") || lineLower.startsWith("https://")) {
                    line
                } else {
                    resolveToBase(line).toString()
                }
                entries.add(PlaylistM3UEntry(header, content))
                header = null
            }
        }
    }

    private fun getBasePath(fullPath: String): String = fullPath.substring(0, fullPath.lastIndexOf('/'))

    private fun getLines(): List<String> {
        return BufferedReader(StringReader(fullText)).readLines()
    }

    fun getEntries(): Array<PlaylistM3UEntry> = entries.toTypedArray()

    companion object {
        const val COMMENTMARKER = "#"
        const val EXTENDED = "#EXTM3U"
    }
}
