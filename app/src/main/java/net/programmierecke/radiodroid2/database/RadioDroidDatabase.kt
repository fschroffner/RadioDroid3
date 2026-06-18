package net.programmierecke.radiodroid2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.programmierecke.radiodroid2.history.TrackHistoryDao
import net.programmierecke.radiodroid2.history.TrackHistoryEntry
import net.programmierecke.radiodroid2.history.TrackHistoryEntry.Companion.MAX_UNKNOWN_TRACK_DURATION
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Database(entities = [TrackHistoryEntry::class], version = 1)
@TypeConverters(Converters::class)
abstract class RadioDroidDatabase : RoomDatabase() {
    abstract fun songHistoryDao(): TrackHistoryDao

    val queryExecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "RadioDroidDatabase Executor") }

    companion object {
        @Volatile private var INSTANCE: RadioDroidDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): RadioDroidDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, RadioDroidDatabase::class.java, "radio_droid_database")
                    .addCallback(CALLBACK)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }

        private val CALLBACK = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE!!.queryExecutor.execute {
                    INSTANCE!!.songHistoryDao().setLastHistoryItemEndTimeRelative(MAX_UNKNOWN_TRACK_DURATION)
                }
            }
        }
    }
}
