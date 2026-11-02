package com.github.fschroffner.floradio.di

import android.content.SharedPreferences
import com.github.fschroffner.floradio.CastHandler
import com.github.fschroffner.floradio.FallbackStationsManager
import com.github.fschroffner.floradio.FavouriteManager
import com.github.fschroffner.floradio.HistoryManager
import com.github.fschroffner.floradio.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.floradio.data.loading.LoadingStateHolder
import com.github.fschroffner.floradio.database.FloRadioDatabase
import com.github.fschroffner.floradio.history.TrackHistoryDao
import com.github.fschroffner.floradio.history.TrackHistoryRepository
import com.github.fschroffner.floradio.player.MediaControllerRepository
import com.github.fschroffner.floradio.player.RadioPlayerController
import com.github.fschroffner.floradio.player.StationPlaybackUseCase
import com.github.fschroffner.floradio.station.RadioStationDao
import com.github.fschroffner.floradio.station.live.metadata.TrackMetadataSearcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun radioDroidDatabase(): FloRadioDatabase
    fun trackHistoryDao(): TrackHistoryDao
    fun radioStationDao(): RadioStationDao
    fun sharedPreferences(): SharedPreferences
    fun appPreferencesDataStore(): AppPreferencesDataStore
    fun historyManager(): HistoryManager
    fun favouriteManager(): FavouriteManager
    fun fallbackStationsManager(): FallbackStationsManager
    fun trackHistoryRepository(): TrackHistoryRepository
    fun castHandler(): CastHandler
    fun trackMetadataSearcher(): TrackMetadataSearcher
    fun radioPlayerController(): RadioPlayerController
    fun mediaControllerRepository(): MediaControllerRepository
    fun loadingStateHolder(): LoadingStateHolder
    fun stationPlaybackUseCase(): StationPlaybackUseCase
}
