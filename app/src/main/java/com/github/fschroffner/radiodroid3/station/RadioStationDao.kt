package com.github.fschroffner.radiodroid3.station

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Query("SELECT * FROM radio_stations WHERE isFavorite = 1 ORDER BY favoriteOrder ASC, Name COLLATE NOCASE ASC")
    fun getFavoriteStations(): Flow<List<DataRadioStation>>

    @Query("SELECT * FROM radio_stations WHERE lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT 25")
    fun getHistoryStations(): Flow<List<DataRadioStation>>

    @Query("SELECT * FROM radio_stations WHERE isFavorite = 1 ORDER BY favoriteOrder ASC, Name COLLATE NOCASE ASC")
    suspend fun getFavoriteStationsList(): List<DataRadioStation>

    @Query("SELECT * FROM radio_stations WHERE lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT 25")
    suspend fun getHistoryStationsList(): List<DataRadioStation>

    @Query("SELECT * FROM radio_stations WHERE StationUuid = :uuid LIMIT 1")
    fun getStationByUuidFlow(uuid: String): Flow<DataRadioStation?>

    @Query("SELECT * FROM radio_stations WHERE StationUuid = :uuid LIMIT 1")
    suspend fun getStationByUuid(uuid: String): DataRadioStation?

    @Query("SELECT isFavorite FROM radio_stations WHERE StationUuid = :uuid LIMIT 1")
    fun isFavoriteFlow(uuid: String): Flow<Boolean?>

    @Query("SELECT isFavorite FROM radio_stations WHERE StationUuid = :uuid LIMIT 1")
    suspend fun isFavorite(uuid: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(station: DataRadioStation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<DataRadioStation>)

    @Update
    suspend fun update(station: DataRadioStation)

    @Query("UPDATE radio_stations SET isFavorite = :isFav, favoriteOrder = :order WHERE StationUuid = :uuid")
    suspend fun setFavorite(uuid: String, isFav: Boolean, order: Int = 0)

    @Query("UPDATE radio_stations SET lastPlayed = :timestamp WHERE StationUuid = :uuid")
    suspend fun setLastPlayed(uuid: String, timestamp: Long)

    @Query("UPDATE radio_stations SET favoriteOrder = :order WHERE StationUuid = :uuid")
    suspend fun updateFavoriteOrder(uuid: String, order: Int)

    @Query("UPDATE radio_stations SET isFavorite = 0, favoriteOrder = 0")
    suspend fun clearFavorites()

    @Query("UPDATE radio_stations SET lastPlayed = NULL")
    suspend fun clearHistory()

    @Delete
    suspend fun delete(station: DataRadioStation)

    @Query("DELETE FROM radio_stations WHERE StationUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM radio_stations WHERE isFavorite = 0 AND lastPlayed IS NULL")
    suspend fun deleteUnusedStations()
}
