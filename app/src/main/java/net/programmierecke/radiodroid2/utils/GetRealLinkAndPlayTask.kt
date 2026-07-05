package net.programmierecke.radiodroid2.utils

import android.content.Context
import android.os.AsyncTask
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.station.DataRadioStation
import java.lang.ref.WeakReference

@Suppress("DEPRECATION")
class GetRealLinkAndPlayTask(
    context: Context,
    private val station: DataRadioStation,
    private val stationReadyListener: StationReadyListener
) : AsyncTask<Void, Void, String?>() {

    /**
     * Invoked once the real (resolved) stream link is known so the caller can start playback. This
     * decouples the task from any specific playback client (the removed AIDL binder or a Media3
     * MediaController).
     */
    fun interface StationReadyListener {
        fun onStationReady(station: DataRadioStation)
    }

    private val contextRef = WeakReference(context)
    private val httpClient = (context.applicationContext as RadioDroidApp).httpClient

    override fun doInBackground(vararg params: Void?): String? {
        val context = contextRef.get() ?: return null
        return Utils.getRealStationLink(httpClient, context.applicationContext, station.StationUuid)
    }

    override fun onPostExecute(result: String?) {
        if (result != null && !isCancelled) {
            station.playableUrl = result
            stationReadyListener.onStationReady(station)
        }
    }
}
