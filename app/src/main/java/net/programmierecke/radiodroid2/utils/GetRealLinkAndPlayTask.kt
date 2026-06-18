package net.programmierecke.radiodroid2.utils

import android.content.Context
import android.os.AsyncTask
import android.os.RemoteException
import net.programmierecke.radiodroid2.IPlayerService
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.station.DataRadioStation
import java.lang.ref.WeakReference

@Suppress("DEPRECATION")
class GetRealLinkAndPlayTask(
    context: Context,
    private val station: DataRadioStation,
    playerService: IPlayerService
) : AsyncTask<Void, Void, String?>() {

    private val contextRef = WeakReference(context)
    private val playerServiceRef = WeakReference(playerService)
    private val httpClient = (context.applicationContext as RadioDroidApp).httpClient

    override fun doInBackground(vararg params: Void?): String? {
        val context = contextRef.get() ?: return null
        return Utils.getRealStationLink(httpClient, context.applicationContext, station.StationUuid)
    }

    override fun onPostExecute(result: String?) {
        val playerService = playerServiceRef.get()
        if (result != null && playerService != null && !isCancelled()) {
            try {
                station.playableUrl = result
                playerService.SetStation(station)
                playerService.Play(false)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }
}
