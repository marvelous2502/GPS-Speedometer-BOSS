package com.example.gpsspeedometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMillis: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val durationSeconds: Long
)
