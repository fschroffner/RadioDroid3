package com.github.fschroffner.radiodroid3.players

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.CastHandler
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.players.mpd.MPDClient
import com.github.fschroffner.radiodroid3.players.mpd.MPDServerData
import com.github.fschroffner.radiodroid3.players.mpd.tasks.MPDPlayTask
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import java.lang.ref.WeakReference

@Suppress("DEPRECATION")
class PlayStationTask(
    private val stationToPlay: DataRadioStation,
    ctx: Context,
    private val playFunc: PlayFunc,
    private val postExecuteTask: PostExecuteTask?
) : AsyncTask<Void, Void, String?>() {

    fun interface PlayFunc {
        fun play(url: String)
    }

    enum class ExecutionResult { FAILURE, SUCCESS }

    fun interface PostExecuteTask {
        fun onPostExecute(executionResult: ExecutionResult)
    }

    private val contextWeakReference = WeakReference(ctx)

    companion object {
        @JvmStatic
        fun playMPD(mpdClient: MPDClient, mpdServerData: MPDServerData, stationToPlay: DataRadioStation, ctx: Context) =
            PlayStationTask(stationToPlay, ctx, { url -> mpdClient.enqueueTask(mpdServerData, MPDPlayTask(url, null)) }, null)

        @JvmStatic
        fun playExternal(stationToPlay: DataRadioStation, ctx: Context) =
            PlayStationTask(stationToPlay, ctx, { url ->
                val share = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), "audio/*") }
                ctx.startActivity(share)
            }, null)

        @JvmStatic
        fun playCAST(stationToPlay: DataRadioStation, ctx: Context): PlayStationTask {
            val castHandler = (ctx.applicationContext as RadioDroidApp).castHandler
            return PlayStationTask(stationToPlay, ctx, { url ->
                castHandler.playRemote(stationToPlay.Name, url, stationToPlay.IconUrl)
            }, null)
        }
    }

    override fun onPreExecute() {
        val ctx = contextWeakReference.get() ?: return
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))

        val radioDroidApp = ctx.applicationContext as RadioDroidApp
        radioDroidApp.historyManager.add(stationToPlay)

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (sharedPref.getBoolean("auto_favorite", false)) {
            val favouriteManager = radioDroidApp.favouriteManager
            if (!favouriteManager.has(stationToPlay.StationUuid)) {
                favouriteManager.add(stationToPlay)
                Toast.makeText(ctx, ctx.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun doInBackground(vararg params: Void?): String? {
        val ctx = contextWeakReference.get() ?: return null
        val radioDroidApp = ctx.applicationContext as RadioDroidApp

        if (!stationToPlay.hasValidUuid()) {
            if (!stationToPlay.refresh(radioDroidApp.httpClient, ctx)) return null
        }

        if (isCancelled) return null

        return Utils.getRealStationLink(radioDroidApp.httpClient, ctx.applicationContext, stationToPlay.StationUuid)
    }

    override fun onPostExecute(result: String?) {
        val ctx = contextWeakReference.get() ?: return
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))

        if (result != null) {
            stationToPlay.playableUrl = result
            playFunc.play(result)
        } else {
            Toast.makeText(ctx.applicationContext, ctx.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
        }

        postExecuteTask?.onPostExecute(if (result != null) ExecutionResult.SUCCESS else ExecutionResult.FAILURE)
    }
}
