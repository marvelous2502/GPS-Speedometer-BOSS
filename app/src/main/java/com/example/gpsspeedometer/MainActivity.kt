package com.example.gpsspeedometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: SpeedViewModel by viewModels()
    private var permissionState by mutableStateOf(PermissionState.UNKNOWN)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionState = PermissionState.GRANTED
            viewModel.startLocationUpdates()
        } else {
            val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionState = if (showRationale) PermissionState.DENIED else PermissionState.PERMANENTLY_DENIED
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionState = if (hasLocationPermission()) {
            viewModel.startLocationUpdates()
            PermissionState.GRANTED
        } else {
            PermissionState.UNKNOWN
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        viewModel = viewModel,
                        permissionState = permissionState,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", packageName, null)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches the case where the user granted permission from system Settings and came back.
        if (permissionState != PermissionState.GRANTED && hasLocationPermission()) {
            permissionState = PermissionState.GRANTED
            viewModel.startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopLocationUpdates()
    }
}

enum class PermissionState { UNKNOWN, GRANTED, DENIED, PERMANENTLY_DENIED }

enum class AppTab(val label: String) { SPEED("Speed"), MAP("Map"), HISTORY("History") }

@Composable
fun AppRoot(
    viewModel: SpeedViewModel,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.SPEED) }
    val state by viewModel.uiState.collectAsState()
    val trips by viewModel.tripHistory.collectAsState()

    // Keep the screen awake only while actively tracking a trip.
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(state.isTracking) {
        val window = (view.context as? ComponentActivity)?.window
        if (state.isTracking) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.SPEED,
                    onClick = { selectedTab = AppTab.SPEED },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    label = { Text("Speed") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.MAP,
                    onClick = { selectedTab = AppTab.MAP },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Map") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.HISTORY,
                    onClick = { selectedTab = AppTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (permissionState != PermissionState.GRANTED) {
                PermissionScreen(
                    permissionState = permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenSettings = onOpenSettings
                )
            } else {
                when (selectedTab) {
                    AppTab.SPEED -> SpeedometerScreen(viewModel = viewModel, state = state)
                    AppTab.MAP -> MapScreen(state = state)
                    AppTab.HISTORY -> HistoryScreen(
                        trips = trips,
                        onDelete = { viewModel.deleteTrip(it) },
                        onClearAll = { viewModel.clearHistory() }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Location permission needed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "This app reads your GPS location to calculate speed, distance, and your route. " +
                "It never leaves your phone.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (permissionState) {
            PermissionState.PERMANENTLY_DENIED -> {
                Text(
                    "Permission was denied. Please enable Location for this app in Settings.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
            else -> {
                Button(onClick = onRequestPermission) { Text("Grant Location Permission") }
            }
        }
    }
}

@Composable
fun SpeedometerScreen(viewModel: SpeedViewModel, state: SpeedUiState) {
    var speedLimitText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GpsStatusBar(state = state, onRetry = { viewModel.retryGpsFix() })

        Spacer(modifier = Modifier.height(8.dp))

        val overLimitModifier = if (state.isOverLimit) {
            Modifier.border(3.dp, Color(0xFFE53935), RoundedCornerShape(16.dp)).padding(8.dp)
        } else Modifier.padding(8.dp)

        Box(modifier = overLimitModifier) {
            SpeedometerGauge(
                speedKmh = state.currentSpeedKmh,
                maxSpeed = 180,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
        }
        Text("km/h", style = MaterialTheme.typography.titleMedium)

        if (state.isOverLimit) {
            Text(
                "OVER SPEED LIMIT",
                color = Color(0xFFE53935),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatColumn("Max", String.format(Locale.getDefault(), "%.0f km/h", state.maxSpeedKmh))
            StatColumn("Avg", String.format(Locale.getDefault(), "%.0f km/h", state.avgSpeedKmh))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatColumn("Distance", String.format(Locale.getDefault(), "%.2f km", state.distanceKm))
            StatColumn("Time", formatElapsed(state.elapsedSeconds))
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = speedLimitText,
            onValueChange = { input ->
                speedLimitText = input.filter { it.isDigit() }
                viewModel.setSpeedLimit(speedLimitText.toIntOrNull())
            },
            label = { Text("Speed limit alert (km/h, optional)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (state.isTracking) viewModel.pauseTrip() else viewModel.startTrip()
            }) {
                Text(if (state.isTracking) "Pause" else "Start Trip")
            }
            OutlinedButton(onClick = { viewModel.saveAndResetTrip() }) {
                Text("Save Trip")
            }
            OutlinedButton(onClick = { viewModel.resetTrip() }) {
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GpsStatusBar(state: SpeedUiState, onRetry: () -> Unit) {
    val (label, color) = when {
        !state.hasGpsFix -> "Waiting for GPS\u2026" to Color(0xFFFFA000)
        state.isSignalWeak -> "Weak signal (\u00b1${state.accuracyMeters.toInt()}m)" to Color(0xFFFFA000)
        else -> "GPS Locked (\u00b1${state.accuracyMeters.toInt()}m)" to Color(0xFF4CAF50)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (!state.hasGpsFix || state.isSignalWeak) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}
