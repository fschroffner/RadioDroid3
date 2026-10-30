package com.github.fschroffner.radiodroid3.players

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.CastHandler
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import dagger.hilt.EntryPoints
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class PlayStationTask(
    private val stationToPlay: DataRadioStation,
    ctx: Context,
    private val playFunc: PlayFunc,
    private val postExecuteTask: PostExecuteTask?
) {

    fun interface PlayFunc {
        fun play(url: String)
    }

    enum class ExecutionResult { FAILURE, SUCCESS }

    fun interface PostExecuteTask {
        fun onPostExecute(executionResult: ExecutionResult)
    }

    private val contextWeakReference = WeakReference(ctx)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        @JvmStatic
        fun playExternal(stationToPlay: DataRadioStation, ctx: Context) =
            PlayStationTask(stationToPlay, ctx, { url ->
                val share = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url), "audio/*") }
                ctx.startActivity(share)
            }, null)

        @JvmStatic
        fun playCAST(stationToPlay: DataRadioStation, ctx: Context): PlayStationTask {
            val castHandler = EntryPoints.get(ctx.applicationContext, AppEntryPoint::class.java).castHandler()
            return PlayStationTask(stationToPlay, ctx, { url ->
                castHandler.playRemote(stationToPlay.Name, url, stationToPlay.IconUrl)
            }, null)
        }
    }

    fun execute(): PlayStationTask {
        val ctx = contextWeakReference.get() ?: return this
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))

        val entryPoint = EntryPoints.get(ctx.applicationContext, AppEntryPoint::class.java)
        entryPoint.historyManager().add(stationToPlay)

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (sharedPref.getBoolean("auto_favorite", false)) {
            val favouriteManager = entryPoint.favouriteManager()
            if (!favouriteManager.has(stationToPlay.StationUuid)) {
                favouriteManager.add(stationToPlay)
                Toast.makeText(ctx, ctx.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }

        job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                val currentCtx = contextWeakReference.get() ?: return@withContext null
                val ep = EntryPoints.get(currentCtx.applicationContext, AppEntryPoint::class.java)
                val httpClient = ep.okHttpClient()

                var streamUrl: String? = null
                if (stationToPlay.hasValidUuid()) {
                    try {
                        streamUrl = Utils.getRealStationLink(httpClient, currentCtx.applicationContext, stationToPlay.StationUuid)
                    } catch (e: Exception) {
                        Log.w("PlayStationTask", "getRealStationLink failed: $e")
                    }
                }

                if (streamUrl.isNullOrBlank()) {
                    streamUrl = stationToPlay.playableUrl?.takeIf { it.isNotBlank() }
                        ?: stationToPlay.StreamUrl.takeIf { it.isNotBlank() }
                }

                streamUrl?.let { Utils.resolvePlaylistIfNeeded(httpClient, it) }
            }

            val currentCtx = contextWeakReference.get() ?: return@launch
            LocalBroadcastManager.getInstance(currentCtx).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))

            val resolvedUrl = result?.takeIf { it.isNotBlank() }
                ?: stationToPlay.playableUrl?.takeIf { it.isNotBlank() }
                ?: stationToPlay.StreamUrl.takeIf { it.isNotBlank() }

            if (resolvedUrl != null) {
                stationToPlay.playableUrl = resolvedUrl
                playFunc.play(resolvedUrl)
            } else {
                Toast.makeText(currentCtx.applicationContext, currentCtx.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
            }

            postExecuteTask?.onPostExecute(if (resolvedUrl != null) ExecutionResult.SUCCESS else ExecutionResult.FAILURE)
        }
        return this
    }

    fun executeOnExecutor(executor: Any?): PlayStationTask = execute()

    fun cancel(mayInterruptIfRunning: Boolean = true) {
        job?.cancel()
    }
}
