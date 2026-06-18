package net.programmierecke.radiodroid2.recording

import java.io.FileOutputStream

class RunningRecordingInfo {
    var recordable: Recordable? = null
        protected set
    var title: String? = null
        protected set
    var fileName: String? = null
        protected set
    var outputStream: FileOutputStream? = null
        protected set
    var bytesWritten: Long = 0
        protected set
}
