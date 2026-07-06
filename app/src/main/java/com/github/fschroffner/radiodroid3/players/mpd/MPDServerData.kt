package com.github.fschroffner.radiodroid3.players.mpd

class MPDServerData(
    var name: String,
    var hostname: String,
    var port: Int,
    var password: String?
) {
    enum class Status { Idle, Paused, Playing }

    var id: Int = -1

    // Runtime status
    var isReachable: Boolean = false
    var status: Status = Status.Idle
    var volume: Int = 0
    var connected: Boolean = false

    constructor(other: MPDServerData) : this(other.name, other.hostname, other.port, other.password) {
        id = other.id
        isReachable = other.isReachable
        status = other.status
        volume = other.volume
        connected = other.connected
    }

    fun updateStatus(str: String) {
        val statusMap = str.split(Regex("\\R")).mapNotNull { line ->
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

        volume = statusMap["volume"]?.toIntOrNull() ?: 0

        statusMap["state"]?.let {
            status = when (it) {
                "stop" -> Status.Idle
                "pause" -> Status.Paused
                "play" -> Status.Playing
                else -> status
            }
        }

        connected = true
    }

    fun contentEquals(o: MPDServerData?): Boolean {
        if (o == null) return false
        return id == o.id && port == o.port && isReachable == o.isReachable &&
            volume == o.volume && connected == o.connected &&
            password == o.password && name == o.name &&
            hostname == o.hostname && status == o.status
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MPDServerData) return false
        return id == other.id
    }

    override fun hashCode(): Int = id
}
