package net.programmierecke.radiodroid2.players.mpd.tasks

import net.programmierecke.radiodroid2.players.mpd.MPDAsyncTask
import net.programmierecke.radiodroid2.players.mpd.MPDServerData

class MPDChangeVolumeTask(
    deltaVolume: Int,
    failureCallback: MPDAsyncTask.FailureCallback?,
    server: MPDServerData
) : MPDAsyncTask() {
    init {
        setStages(
            arrayOf(
                okReadStage(),
                statusReadStage(true),
                MPDAsyncTask.ReadStage { task, result ->
                    task.getMpdServerData().updateStatus(result)
                    task.notifyServerUpdated()
                    false
                }
            ),
            arrayOf(
                statusWriteStage(),
                MPDAsyncTask.WriteStage { task, writer ->
                    val newVolume = (task.getMpdServerData().volume + deltaVolume).coerceIn(0, 100)
                    writer.write("command_list_begin\nsetvol $newVolume\nstatus\ncommand_list_end\n")
                    true
                }
            ),
            failureCallback
        )
    }
}
