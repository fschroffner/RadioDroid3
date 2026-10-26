package com.github.fschroffner.radiodroid3.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.fschroffner.radiodroid3.history.TrackHistoryDao
import com.github.fschroffner.radiodroid3.history.TrackHistoryEntry
import com.github.fschroffner.radiodroid3.history.TrackHistoryEntry.Companion.MAX_UNKNOWN_TRACK_DURATION
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Database(entities = [TrackHistoryEntry::class, DataRadioStation::class], version = 2)
@TypeConverters(Converters::class)
abstract class RadioDroidDatabase : RoomDatabase() {
    abstract fun songHistoryDao(): TrackHistoryDao
    abstract fun radioStationDao(): RadioStationDao

    val historyQueryExecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "RadioDroidDatabase Executor") }

    companion object {
        @Volatile private var INSTANCE: RadioDroidDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `radio_stations` (
                        `StationUuid` TEXT NOT NULL,
                        `Name` TEXT NOT NULL,
                        `ChangeUuid` TEXT NOT NULL,
                        `StreamUrl` TEXT NOT NULL,
                        `HomePageUrl` TEXT NOT NULL,
                        `IconUrl` TEXT NOT NULL,
                        `Country` TEXT NOT NULL,
                        `CountryCode` TEXT,
                        `State` TEXT NOT NULL,
                        `TagsAll` TEXT NOT NULL,
                        `Language` TEXT NOT NULL,
                        `ClickCount` INTEGER NOT NULL,
                        `ClickTrend` INTEGER NOT NULL,
                        `Votes` INTEGER NOT NULL,
                        `RefreshRetryCount` INTEGER NOT NULL,
                        `Bitrate` INTEGER NOT NULL,
                        `Codec` TEXT,
                        `Working` INTEGER NOT NULL,
                        `Hls` INTEGER NOT NULL,
                        `DeletedOnServer` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `favoriteOrder` INTEGER NOT NULL,
                        `lastPlayed` INTEGER,
                        PRIMARY KEY(`StationUuid`)
                    )
                """.trimIndent())
            }
        }

        @JvmStatic
        fun getDatabase(context: Context): RadioDroidDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, RadioDroidDatabase::class.java, "radio_droid_database")
                    .addCallback(CALLBACK)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }

        private val CALLBACK = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE!!.historyQueryExecutor.execute {
                    INSTANCE!!.songHistoryDao().setLastHistoryItemEndTimeRelative(MAX_UNKNOWN_TRACK_DURATION)
                }
            }
        }
    }
}
