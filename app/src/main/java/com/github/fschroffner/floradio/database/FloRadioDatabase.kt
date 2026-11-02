package com.github.fschroffner.floradio.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.fschroffner.floradio.data.local.entity.RadioStationEntity
import com.github.fschroffner.floradio.history.TrackHistoryDao
import com.github.fschroffner.floradio.history.TrackHistoryEntry
import com.github.fschroffner.floradio.history.TrackHistoryEntry.Companion.MAX_UNKNOWN_TRACK_DURATION
import com.github.fschroffner.floradio.station.RadioStationDao
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Database(entities = [TrackHistoryEntry::class, RadioStationEntity::class], version = 2)
@TypeConverters(Converters::class)
abstract class FloRadioDatabase : RoomDatabase() {
    abstract fun songHistoryDao(): TrackHistoryDao
    abstract fun radioStationDao(): RadioStationDao

    val historyQueryExecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "FloRadioDatabase Executor") }

    companion object {
        @Volatile private var INSTANCE: FloRadioDatabase? = null

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
        fun getDatabase(context: Context): FloRadioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, FloRadioDatabase::class.java, "flo_radio_database")
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
