package net.programmierecke.radiodroid2.recording

interface RecordableListener {
    fun onBytesAvailable(buffer: ByteArray, offset: Int, length: Int)
    fun onRecordingEnded()
}
