package com.github.fschroffner.radiodroid3.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.CastHandler
import com.github.fschroffner.radiodroid3.FallbackStationsManager
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.alarm.RadioAlarmManager
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.data.datastore.DataStorePreferenceBridge
import com.github.fschroffner.radiodroid3.database.RadioDroidDatabase
import com.github.fschroffner.radiodroid3.history.TrackHistoryDao
import com.github.fschroffner.radiodroid3.history.TrackHistoryRepository
import com.github.fschroffner.radiodroid3.players.mpd.MPDClient
import com.github.fschroffner.radiodroid3.recording.RecordingsManager
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import com.github.fschroffner.radiodroid3.station.live.metadata.TrackMetadataSearcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @Singleton
    fun provideAppPreferencesDataStore(
        @ApplicationContext context: Context
    ): AppPreferencesDataStore = AppPreferencesDataStore(context)

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        appPreferencesDataStore: AppPreferencesDataStore
    ): DataStore<Preferences> = appPreferencesDataStore.dataStore

    @Provides
    @Singleton
    fun provideDataStorePreferenceBridge(
        appPreferencesDataStore: AppPreferencesDataStore
    ): DataStorePreferenceBridge = DataStorePreferenceBridge(appPreferencesDataStore)

    @Provides
    @Singleton
    fun provideHistoryManager(
        @ApplicationContext context: Context,
        stationDao: RadioStationDao
    ): HistoryManager = HistoryManager(context, stationDao)

    @Provides
    @Singleton
    fun provideFavouriteManager(
        @ApplicationContext context: Context,
        stationDao: RadioStationDao
    ): FavouriteManager = FavouriteManager(context, stationDao)

    @Provides
    @Singleton
    fun provideFallbackStationsManager(
        @ApplicationContext context: Context
    ): FallbackStationsManager = FallbackStationsManager(context)

    @Provides
    @Singleton
    fun provideRecordingsManager(): RecordingsManager =
        RecordingsManager().apply { updateRecordingsList() }

    @Provides
    @Singleton
    fun provideRadioAlarmManager(
        @ApplicationContext context: Context,
        appPreferencesDataStore: AppPreferencesDataStore
    ): RadioAlarmManager = RadioAlarmManager(context, appPreferencesDataStore)

    @Provides
    @Singleton
    fun provideTrackHistoryRepository(
        database: RadioDroidDatabase,
        dao: TrackHistoryDao
    ): TrackHistoryRepository = TrackHistoryRepository(database, dao)

    @Provides
    @Singleton
    fun provideMPDClient(
        @ApplicationContext context: Context
    ): MPDClient = MPDClient(context)

    @Provides
    @Singleton
    fun provideCastHandler(): CastHandler = CastHandler()

    @Provides
    @Singleton
    fun provideTrackMetadataSearcher(
        okHttpClient: OkHttpClient
    ): TrackMetadataSearcher = TrackMetadataSearcher(okHttpClient)
}
