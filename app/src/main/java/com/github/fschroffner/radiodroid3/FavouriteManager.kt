package com.github.fschroffner.radiodroid3

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import com.github.fschroffner.radiodroid3.station.StationShortcutHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class FavouriteManager(
    ctx: Context,
    stationDao: RadioStationDao? = null
) : StationSaveManager(ctx, stationDao) {

    override fun getSaveId() = "favourites"

    override val stationsFlow: Flow<List<DataRadioStation>>
        get() = stationDao?.getFavoriteStations() ?: emptyFlow()

    @Inject
    constructor(@ApplicationContext ctx: Context) : this(
        ctx,
        try {
            dagger.hilt.EntryPoints.get(ctx.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).radioStationDao()
        } catch (e: Exception) {
            null
        }
    )

    init {
        setStationStatusListener(object : StationStatusListener {
            override fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean) {
                val local = Intent(DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED).apply {
                    putExtra(DataRadioStation.RADIO_STATION_UUID, station.StationUuid)
                }
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(local)
            }
        })
    }

    override fun add(station: DataRadioStation) {
        if (!has(station.StationUuid)) {
            station.isFavorite = true
            station.favoriteOrder = listStations.size
            super.add(station)
        }
    }

    override fun restore(station: DataRadioStation, pos: Int) {
        if (!has(station.StationUuid)) {
            station.isFavorite = true
            station.favoriteOrder = pos
            super.restore(station, pos)
        }
    }

    override fun remove(id: String): Int {
        stationDao?.let { dao ->
            coroutineScope.launch {
                dao.setFavorite(id, false, 0)
            }
        }
        return super.remove(id)
    }

    override fun clear() {
        stationDao?.let { dao ->
            coroutineScope.launch {
                dao.clearFavorites()
            }
        }
        super.clear()
    }

    override fun Load() {
        super.Load()
        updateShortcuts()
    }

    override fun Save() {
        super.Save()
        updateShortcuts()
    }

    fun updateShortcuts() {
        if (Build.VERSION.SDK_INT >= 25 && !BuildConfig.IS_TESTING.get()) {
            val number = minOf(listStations.size, ActivityMain.MAX_DYNAMIC_LAUNCHER_SHORTCUTS)
            val setter = SetDynamicAppLauncherShortcuts(number)
            for (i in 0 until number) {
                StationShortcutHelper.prepareShortcut(listStations[i], context, setter)
            }
        }
    }

    @TargetApi(25)
    inner class SetDynamicAppLauncherShortcuts(private val expectedNumber: Int) : StationShortcutHelper.ShortcutReadyListener {
        private val shortcuts = ArrayList<ShortcutInfo>(expectedNumber)

        override fun onShortcutReadyListener(shortcutInfo: ShortcutInfo) {
            shortcuts.add(shortcutInfo)
            if (shortcuts.size >= expectedNumber) {
                val shortcutManager = context.applicationContext.getSystemService(ShortcutManager::class.java)
                shortcutManager.removeAllDynamicShortcuts()
                shortcutManager.setDynamicShortcuts(shortcuts)
            }
        }
    }
}
