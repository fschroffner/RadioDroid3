package net.programmierecke.radiodroid2.players.mpd.tasks

import net.programmierecke.radiodroid2.players.mpd.MPDAsyncTask

class MPDPlayTask(url: String, failureCallback: MPDAsyncTask.FailureCallback?) : MPDAsyncTask() {
    private var songId = -1

    init {
        setStages(
            arrayOf(
                okReadStage(),
                MPDAsyncTask.ReadStage { _, result ->
                    if (result.startsWith("Id:")) {
                        songId = result.substring(3, result.indexOf("\n")).trim().toInt()
                    }
                    true
                },
                statusReadStage(false)
            ),
            arrayOf(
                MPDAsyncTask.WriteStage { _, writer ->
                    writer.write("addid $url\n")
                    true
                },
                MPDAsyncTask.WriteStage { _, writer ->
                    writer.write("command_list_begin\nplayid $songId\nstatus\ncommand_list_end\n")
                    true
                }
            ),
            failureCallback
        )
    }
}
