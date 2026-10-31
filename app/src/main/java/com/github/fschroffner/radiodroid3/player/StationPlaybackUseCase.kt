package com.github.fschroffner.radiodroid3.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.github.fschroffner.radiodroid3.CastHandler
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder
import com.github.fschroffner.radiodroid3.players.selector.PlayerSelectorDialog
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.data.mapper.toDataRadioStation
import com.github.fschroffner.radiodroid3.domain.model.RadioStation
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain Use Case encapsulating station playback, stream URL resolution,
 * playlist detection, cast handling, and external player routing.
 *
 * Eliminates the Service-Locator anti-pattern by injecting all dependencies via constructor,
 * and eliminates deprecated PlayStationTask / LocalBroadcastManager.
 */
@Singleton
class StationPlaybackUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val historyManager: HistoryManager,
    private val favouriteManager: FavouriteManager,
    private val castHandler: CastHandler,
    private val mediaControllerRepository: MediaControllerRepository,
    private val appPreferencesDataStore: AppPreferencesDataStore,
    private val loadingStateHolder: LoadingStateHolder
) {

    private val tag = "StationPlaybackUseCase"
    private val playbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun getCurrentOrLastStation(): DataRadioStation? {
        return mediaControllerRepository.getCurrentStation() ?: historyManager.getFirst()
    }

    fun playStation(station: RadioStation) {
        playStation(station.toDataRadioStation())
    }

    fun playStation(station: DataRadioStation) {
        historyManager.add(station)

        if (appPreferencesDataStore.getBoolean("auto_favorite", false)) {
            if (!favouriteManager.has(station.StationUuid)) {
                favouriteManager.add(station)
                Toast.makeText(context, context.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }

        mediaControllerRepository.playStation(station)
    }

    suspend fun resolveStreamUrl(station: RadioStation): String? =
        resolveStreamUrl(station.toDataRadioStation())

    suspend fun resolveStreamUrl(station: DataRadioStation): String? = withContext(Dispatchers.IO) {
        var streamUrl: String? = null
        if (station.hasValidUuid()) {
            try {
                streamUrl = Utils.getRealStationLink(httpClient, context, station.StationUuid)
            } catch (e: Exception) {
                Log.w(tag, "getRealStationLink failed: $e")
            }
        }

        if (streamUrl.isNullOrBlank()) {
            streamUrl = station.playableUrl?.takeIf { it.isNotBlank() }
                ?: station.StreamUrl.takeIf { it.isNotBlank() }
        }

        val resolved = streamUrl?.let { Utils.resolvePlaylistIfNeeded(httpClient, it) }
        val finalUrl = resolved?.takeIf { it.isNotBlank() }
            ?: station.playableUrl?.takeIf { it.isNotBlank() }
            ?: station.StreamUrl.takeIf { it.isNotBlank() }

        if (finalUrl != null) {
            station.playableUrl = finalUrl
        }
        finalUrl
    }

    fun playExternal(station: DataRadioStation, targetContext: Context = context) {
        historyManager.add(station)
        if (appPreferencesDataStore.getBoolean("auto_favorite", false)) {
            if (!favouriteManager.has(station.StationUuid)) {
                favouriteManager.add(station)
                Toast.makeText(targetContext, targetContext.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }

        loadingStateHolder.showLoading()
        playbackScope.launch {
            try {
                val resolvedUrl = resolveStreamUrl(station)
                withContext(Dispatchers.Main) {
                    loadingStateHolder.hideLoading()
                    if (resolvedUrl != null) {
                        val share = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(resolvedUrl), "audio/*")
                            if (targetContext !is android.app.Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        targetContext.startActivity(share)
                    } else {
                        Toast.makeText(targetContext.applicationContext, targetContext.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingStateHolder.hideLoading()
                    Toast.makeText(targetContext.applicationContext, targetContext.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun playCast(station: DataRadioStation, targetContext: Context = context) {
        historyManager.add(station)
        if (appPreferencesDataStore.getBoolean("auto_favorite", false)) {
            if (!favouriteManager.has(station.StationUuid)) {
                favouriteManager.add(station)
                Toast.makeText(targetContext, targetContext.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }

        loadingStateHolder.showLoading()
        playbackScope.launch {
            try {
                val resolvedUrl = resolveStreamUrl(station)
                withContext(Dispatchers.Main) {
                    loadingStateHolder.hideLoading()
                    if (resolvedUrl != null) {
                        castHandler.playRemote(station.Name, resolvedUrl, station.IconUrl)
                    } else {
                        Toast.makeText(targetContext.applicationContext, targetContext.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingStateHolder.hideLoading()
                    Toast.makeText(targetContext.applicationContext, targetContext.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun showPlaySelection(
        targetContext: Context,
        station: DataRadioStation,
        fragmentManager: FragmentManager
    ) {
        val externalAvailable = appPreferencesDataStore.getBoolean("play_external", false)
        val castAvailable = castHandler.isCastSessionAvailable

        if (castAvailable && !externalAvailable) {
            playCast(station, targetContext)
        } else if (externalAvailable) {
            showPlayerSelectorDialog(fragmentManager, station)
        } else {
            playAndWarnIfMetered(targetContext, station, PlayerType.RADIODROID) {
                playStation(station)
            }
        }
    }

    fun showPlayerSelectorDialog(fragmentManager: FragmentManager, station: DataRadioStation?) {
        val oldFragment = fragmentManager.findFragmentByTag(PlayerSelectorDialog.FRAGMENT_TAG)
        if (oldFragment != null && oldFragment.isVisible) return

        val dialog = PlayerSelectorDialog(station)
        dialog.show(fragmentManager, PlayerSelectorDialog.FRAGMENT_TAG)
    }

    fun playAndWarnIfMetered(
        targetContext: Context,
        station: DataRadioStation,
        playerType: PlayerType,
        playFunc: Runnable
    ) {
        Utils.playAndWarnIfMetered(
            targetContext,
            station,
            playerType,
            playFunc,
            mediaControllerRepository
        )
    }
}
