package com.github.fschroffner.radiodroid3.players.mpd.tasks

import com.github.fschroffner.radiodroid3.players.mpd.MPDAsyncTask

class MPDResumeTask(failureCallback: MPDAsyncTask.FailureCallback?) : MPDAsyncTask() {
    init {
        setStages(
            arrayOf(okReadStage(), statusReadStage(false)),
            arrayOf(MPDAsyncTask.WriteStage { _, writer ->
                writer.write("command_list_begin\npause 0\nstatus\ncommand_list_end\n")
                true
            }),
            failureCallback
        )
    }
}
