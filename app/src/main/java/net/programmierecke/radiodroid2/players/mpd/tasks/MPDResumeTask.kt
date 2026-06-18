package net.programmierecke.radiodroid2.players.mpd.tasks

import net.programmierecke.radiodroid2.players.mpd.MPDAsyncTask

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
