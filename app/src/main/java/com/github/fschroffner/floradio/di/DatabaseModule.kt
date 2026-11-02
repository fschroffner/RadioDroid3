package com.github.fschroffner.floradio.di

import android.content.Context
import com.github.fschroffner.floradio.database.FloRadioDatabase
import com.github.fschroffner.floradio.history.TrackHistoryDao
import com.github.fschroffner.floradio.station.RadioStationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFloRadioDatabase(
        @ApplicationContext context: Context
    ): FloRadioDatabase = FloRadioDatabase.getDatabase(context)

    @Provides
    fun provideTrackHistoryDao(
        database: FloRadioDatabase
    ): TrackHistoryDao = database.songHistoryDao()

    @Provides
    fun provideRadioStationDao(
        database: FloRadioDatabase
    ): RadioStationDao = database.radioStationDao()
}
