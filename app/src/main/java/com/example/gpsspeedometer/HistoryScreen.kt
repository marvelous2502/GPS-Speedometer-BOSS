package com.example.gpsspeedometer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpsspeedometer.data.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    trips: List<TripEntity>,
    onDelete: (TripEntity) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Trip History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (trips.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text("Clear All") }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (trips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved trips yet. Tap \"Save Trip\" after a ride to log it here.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(trips) { trip ->
                    TripCard(trip = trip, onDelete = { onDelete(trip) })
                }
            }
        }
    }
}

@Composable
fun TripCard(trip: TripEntity, onDelete: () -> Unit) {
    val dateFormat = remember(trip.startTimeMillis) {
        SimpleDateFormat("MMM d, yyyy \u2022 h:mm a", Locale.getDefault())
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(dateFormat.format(Date(trip.startTimeMillis)), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(String.format(Locale.getDefault(), "%.2f km  \u2022  Max %.0f km/h  \u2022  Avg %.0f km/h",
                    trip.distanceKm, trip.maxSpeedKmh, trip.avgSpeedKmh))
                Text(formatElapsed(trip.durationSeconds), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete trip")
            }
        }
    }
}
