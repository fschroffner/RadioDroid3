package net.programmierecke.radiodroid2.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import net.programmierecke.radiodroid2.IPlayerService
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.utils.GetRealLinkAndPlayTask

class MediaSessionCallback(
    private val context: Context,
    private val playerService: IPlayerService
) : MediaSessionCompat.Callback() {

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        val event = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)!!
        if (event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isLongPress) {
                try {
                    if (playerService.isPlaying) playerService.Pause(PauseReason.USER)
                    else playerService.Resume()
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
            return true
        }
        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    override fun onPause() {
        try { playerService.Pause(PauseReason.USER) } catch (e: RemoteException) { e.printStackTrace() }
    }

    override fun onPlay() {
        try { playerService.Resume() } catch (e: RemoteException) { e.printStackTrace() }
    }

    override fun onSkipToNext() {
        try { playerService.SkipToNext() } catch (e: RemoteException) { e.printStackTrace() }
    }

    override fun onSkipToPrevious() {
        try { playerService.SkipToPrevious() } catch (e: RemoteException) { e.printStackTrace() }
    }

    override fun onStop() {
        try { playerService.Stop() } catch (e: RemoteException) { e.printStackTrace() }
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        val stationId = RadioDroidBrowser.stationIdFromMediaId(mediaId)
        if (stationId.isNotEmpty()) {
            val intent = Intent(BROADCAST_PLAY_STATION_BY_ID).putExtra(EXTRA_STATION_ID, stationId)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }

    override fun onPlayFromSearch(query: String, extras: Bundle?) {
        val cleanQuery = query.replace(Regex("(?i) \\w+ radio\\s*droid.*"), "")
        val app = context.applicationContext as RadioDroidApp
        val station = app.favouriteManager.getBestNameMatch(cleanQuery)
            ?: app.historyManager.getBestNameMatch(cleanQuery)
            ?: app.fallbackStationsManager.getBestNameMatch(cleanQuery)
        GetRealLinkAndPlayTask(context, station, playerService).execute()
    }

    companion object {
        const val BROADCAST_PLAY_STATION_BY_ID = "PLAY_STATION_BY_ID"
        const val EXTRA_STATION_ID = "STATION_ID"
        const val ACTION_PLAY_STATION_BY_UUID = "PLAY_STATION_BY_UUID"
        const val EXTRA_STATION_UUID = "STATION_UUID"
    }
}
