package com.github.fschroffner.radiodroid3.players

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.CastHandler
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference

/**
 * Legacy task for station URL resolution and playback execution.
 * Kept for backward compatibility only.
 *
 * @deprecated Use [com.github.fschroffner.radiodroid3.player.StationPlaybackUseCase] instead.
 */
@Deprecated("Deprecated AsyncTask clone. Use StationPlaybackUseCase instead.")
class PlayStationTask(
    private val stationToPlay: DataRadioStation,
    ctx: Context,
    private val playFunc: PlayFunc,
    private val postExecuteTask: PostExecuteTask?,
    private val httpClient: OkHttpClient? = null,
    private val historyManager: HistoryManager? = null,
    private val favouriteManager: FavouriteManager? = null,
    private val castHandler: CastHandler? = null
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
        fun playExternal(
            stationToPlay: DataRadioStation,
            ctx: Context,
            httpClient: OkHttpClient? = null,
            historyManager: HistoryManager? = null,
            favouriteManager: FavouriteManager? = null
        ) = PlayStationTask(
            stationToPlay = stationToPlay,
            ctx = ctx,
            playFunc = { url ->
                val share = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "audio/*")
                    if (ctx !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                ctx.startActivity(share)
            },
            postExecuteTask = null,
            httpClient = httpClient,
            historyManager = historyManager,
            favouriteManager = favouriteManager
        )

        @JvmStatic
        fun playCAST(
            stationToPlay: DataRadioStation,
            ctx: Context,
            castHandler: CastHandler? = null,
            httpClient: OkHttpClient? = null,
            historyManager: HistoryManager? = null,
            favouriteManager: FavouriteManager? = null
        ): PlayStationTask {
            val handler = castHandler ?: EntryPoints.get(ctx.applicationContext, AppEntryPoint::class.java).castHandler()
            return PlayStationTask(
                stationToPlay = stationToPlay,
                ctx = ctx,
                playFunc = { url ->
                    handler.playRemote(stationToPlay.Name, url, stationToPlay.IconUrl)
                },
                postExecuteTask = null,
                httpClient = httpClient,
                historyManager = historyManager,
                favouriteManager = favouriteManager,
                castHandler = handler
            )
        }
    }

    fun execute(): PlayStationTask {
        val ctx = contextWeakReference.get() ?: return this
        LoadingStateHolder.show()

        val hm = historyManager ?: EntryPoints.get(ctx.applicationContext, AppEntryPoint::class.java).historyManager()
        hm.add(stationToPlay)

        val fm = favouriteManager ?: EntryPoints.get(ctx.applicationContext, AppEntryPoint::class.java).favouriteManager()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (sharedPref.getBoolean("auto_favorite", false)) {
            if (!fm.has(stationToPlay.StationUuid)) {
                fm.add(stationToPlay)
                Toast.makeText(ctx, ctx.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }

        job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                val currentCtx = contextWeakReference.get() ?: return@withContext null
                val client = httpClient ?: EntryPoints.get(currentCtx.applicationContext, AppEntryPoint::class.java).okHttpClient()

                var streamUrl: String? = null
                if (stationToPlay.hasValidUuid()) {
                    try {
                        streamUrl = Utils.getRealStationLink(client, currentCtx.applicationContext, stationToPlay.StationUuid)
                    } catch (e: Exception) {
                        Log.w("PlayStationTask", "getRealStationLink failed: $e")
                    }
                }

                if (streamUrl.isNullOrBlank()) {
                    streamUrl = stationToPlay.playableUrl?.takeIf { it.isNotBlank() }
                        ?: stationToPlay.StreamUrl.takeIf { it.isNotBlank() }
                }

                streamUrl?.let { Utils.resolvePlaylistIfNeeded(client, it) }
            }

            val currentCtx = contextWeakReference.get() ?: return@launch
            LoadingStateHolder.hide()

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
        LoadingStateHolder.hide()
    }
}
