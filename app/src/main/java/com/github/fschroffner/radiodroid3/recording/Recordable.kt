package com.github.fschroffner.radiodroid3.recording

interface Recordable {
    fun canRecord(): Boolean
    fun startRecording(recordableListener: RecordableListener)
    fun stopRecording()
    fun isRecording(): Boolean
    fun getRecordNameFormattingArgs(): Map<String, String>
    fun getExtension(): String
}
