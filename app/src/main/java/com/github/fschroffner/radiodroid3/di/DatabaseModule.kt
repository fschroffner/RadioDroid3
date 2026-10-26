package com.github.fschroffner.radiodroid3.di

import android.content.Context
import com.github.fschroffner.radiodroid3.database.RadioDroidDatabase
import com.github.fschroffner.radiodroid3.history.TrackHistoryDao
import com.github.fschroffner.radiodroid3.station.RadioStationDao
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
    fun provideRadioDroidDatabase(
        @ApplicationContext context: Context
    ): RadioDroidDatabase = RadioDroidDatabase.getDatabase(context)

    @Provides
    fun provideTrackHistoryDao(
        database: RadioDroidDatabase
    ): TrackHistoryDao = database.songHistoryDao()

    @Provides
    fun provideRadioStationDao(
        database: RadioDroidDatabase
    ): RadioStationDao = database.radioStationDao()
}
