package com.example.gpsspeedometer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY startTimeMillis DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Delete
    suspend fun delete(trip: TripEntity)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}
