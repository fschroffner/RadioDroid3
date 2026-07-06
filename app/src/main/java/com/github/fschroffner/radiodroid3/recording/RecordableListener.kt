package com.github.fschroffner.radiodroid3.recording

interface RecordableListener {
    fun onBytesAvailable(buffer: ByteArray, offset: Int, length: Int)
    fun onRecordingEnded()
}
