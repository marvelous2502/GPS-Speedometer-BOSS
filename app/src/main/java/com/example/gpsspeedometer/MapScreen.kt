package com.example.gpsspeedometer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MarkerState

@Composable
fun MapScreen(state: SpeedUiState) {
    val defaultPosition = state.currentPosition ?: LatLng(6.5244, 3.3792) // Lagos fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 16f)
    }

    LaunchedEffect(state.currentPosition) {
        state.currentPosition?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(it))
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false)
    ) {
        if (state.routePoints.size > 1) {
            Polyline(points = state.routePoints, color = Color(0xFF2196F3), width = 8f)
        }
        state.currentPosition?.let {
            Marker(state = MarkerState(position = it), title = "You")
        }
    }
}
