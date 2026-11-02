package com.github.fschroffner.floradio.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.preference.PreferenceManager
import com.github.fschroffner.floradio.CastHandler
import com.github.fschroffner.floradio.FallbackStationsManager
import com.github.fschroffner.floradio.FavouriteManager
import com.github.fschroffner.floradio.HistoryManager
import com.github.fschroffner.floradio.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.floradio.data.datastore.DataStorePreferenceBridge
import com.github.fschroffner.floradio.database.FloRadioDatabase
import com.github.fschroffner.floradio.history.TrackHistoryDao
import com.github.fschroffner.floradio.history.TrackHistoryRepository
import com.github.fschroffner.floradio.station.RadioStationDao
import com.github.fschroffner.floradio.station.live.metadata.TrackMetadataSearcher
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
        appPreferencesDataStore: AppPreferencesDataStore,
        sharedPreferences: SharedPreferences
    ): DataStorePreferenceBridge = DataStorePreferenceBridge(appPreferencesDataStore, sharedPreferences)

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
    fun provideTrackHistoryRepository(
        database: FloRadioDatabase,
        dao: TrackHistoryDao
    ): TrackHistoryRepository = TrackHistoryRepository(database, dao)

    @Provides
    @Singleton
    fun provideCastHandler(): CastHandler = CastHandler()

    @Provides
    @Singleton
    fun provideTrackMetadataSearcher(
        okHttpClient: OkHttpClient
    ): TrackMetadataSearcher = TrackMetadataSearcher(okHttpClient)
}
