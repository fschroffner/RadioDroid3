package com.github.fschroffner.radiodroid3.di

import android.content.SharedPreferences
import com.github.fschroffner.radiodroid3.CastHandler
import com.github.fschroffner.radiodroid3.FallbackStationsManager
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.alarm.RadioAlarmManager
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.database.RadioDroidDatabase
import com.github.fschroffner.radiodroid3.history.TrackHistoryDao
import com.github.fschroffner.radiodroid3.history.TrackHistoryRepository
import com.github.fschroffner.radiodroid3.player.MediaControllerRepository
import com.github.fschroffner.radiodroid3.player.RadioPlayerController
import com.github.fschroffner.radiodroid3.players.mpd.MPDClient
import com.github.fschroffner.radiodroid3.recording.RecordingsManager
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import com.github.fschroffner.radiodroid3.station.live.metadata.TrackMetadataSearcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun radioDroidDatabase(): RadioDroidDatabase
    fun trackHistoryDao(): TrackHistoryDao
    fun radioStationDao(): RadioStationDao
    fun sharedPreferences(): SharedPreferences
    fun appPreferencesDataStore(): AppPreferencesDataStore
    fun historyManager(): HistoryManager
    fun favouriteManager(): FavouriteManager
    fun fallbackStationsManager(): FallbackStationsManager
    fun recordingsManager(): RecordingsManager
    fun radioAlarmManager(): RadioAlarmManager
    fun trackHistoryRepository(): TrackHistoryRepository
    fun mpdClient(): MPDClient
    fun castHandler(): CastHandler
    fun trackMetadataSearcher(): TrackMetadataSearcher
    fun radioPlayerController(): RadioPlayerController
    fun mediaControllerRepository(): MediaControllerRepository
}
