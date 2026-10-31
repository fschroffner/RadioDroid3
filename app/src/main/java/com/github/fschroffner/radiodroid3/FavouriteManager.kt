package com.github.fschroffner.radiodroid3

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import com.github.fschroffner.radiodroid3.data.mapper.toDataRadioStation
import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import com.github.fschroffner.radiodroid3.domain.model.RadioStation
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import com.github.fschroffner.radiodroid3.station.StationShortcutHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FavouriteManager @Inject constructor(
    @ApplicationContext ctx: Context,
    stationDao: RadioStationDao
) : StationSaveManager(ctx, stationDao) {

    override fun getSaveId() = "favourites"

    override val stationsFlow: Flow<List<DataRadioStation>>
        get() = stationDao?.getFavoriteStations()?.map { list -> list.map { it.toDataRadioStation() } } ?: emptyFlow()

    val domainStationsFlow: Flow<List<RadioStation>>
        get() = stationDao?.getFavoriteStations()?.map { list -> list.map { it.toDomain() } } ?: emptyFlow()

    private val _stationChangedFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val stationChangedFlow: SharedFlow<String> = _stationChangedFlow.asSharedFlow()

    init {
        observeDatabaseFlow()

        setStationStatusListener(object : StationStatusListener {
            override fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean) {
                _stationChangedFlow.tryEmit(station.StationUuid)
            }
        })

        coroutineScope.launch {
            stationsFlow.collect { favorites ->
                withContext(Dispatchers.Main) {
                    updateShortcuts(favorites)
                }
            }
        }
    }

    fun add(station: RadioStation) {
        add(station.toDataRadioStation())
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



    fun updateShortcuts(favorites: List<DataRadioStation> = listStations) {
        if (Build.VERSION.SDK_INT >= 25 && !BuildConfig.IS_TESTING.get()) {
            val number = minOf(favorites.size, ActivityMain.MAX_DYNAMIC_LAUNCHER_SHORTCUTS)
            val setter = SetDynamicAppLauncherShortcuts(number)
            for (i in 0 until number) {
                StationShortcutHelper.prepareShortcut(favorites[i], context, setter)
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
                shortcutManager?.removeAllDynamicShortcuts()
                shortcutManager?.setDynamicShortcuts(shortcuts)
            }
        }
    }
}
