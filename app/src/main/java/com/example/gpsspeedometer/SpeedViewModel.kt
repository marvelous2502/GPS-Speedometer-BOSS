package com.example.gpsspeedometer

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.data.AppDatabase
import com.example.gpsspeedometer.data.TripEntity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class SpeedUiState(
    val currentSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val isTracking: Boolean = false,
    val hasGpsFix: Boolean = false,
    val accuracyMeters: Float = 0f,
    val isSignalWeak: Boolean = true,
    val speedLimitKmh: Int? = null,
    val isOverLimit: Boolean = false,
    val routePoints: List<LatLng> = emptyList(),
    val currentPosition: LatLng? = null
)

class SpeedViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val tripDao = AppDatabase.getInstance(application).tripDao()
    val tripHistory = tripDao.getAllTrips().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _uiState = MutableStateFlow(SpeedUiState())
    val uiState: StateFlow<SpeedUiState> = _uiState

    private var lastLocation: Location? = null
    private var totalDistanceMeters: Double = 0.0
    private var trackingStartTime: Long = 0L
    private var tripStartTimeMillis: Long = 0L
    private var accumulatedElapsedMs: Long = 0L
    private var timerJob: kotlinx.coroutines.Job? = null
    private var lastFixTime: Long = 0L
    private var staleCheckJob: kotlinx.coroutines.Job? = null
    private var alarmPlayer: MediaPlayer? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 1000L
    ).setMinUpdateIntervalMillis(500L).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onNewLocation(location)
        }
    }

    private fun onNewLocation(location: Location) {
        lastFixTime = System.currentTimeMillis()

        val speedMs = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        val speedKmh = speedMs * 3.6

        val prev = lastLocation
        if (prev != null && _uiState.value.isTracking && location.accuracy <= 25f) {
            totalDistanceMeters += prev.distanceTo(location)
        }
        lastLocation = location

        val elapsedS = (accumulatedElapsedMs + if (_uiState.value.isTracking) {
            System.currentTimeMillis() - trackingStartTime
        } else 0L) / 1000.0

        val avg = if (elapsedS > 0) (totalDistanceMeters / elapsedS) * 3.6 else 0.0
        val limit = _uiState.value.speedLimitKmh
        val overLimit = limit != null && speedKmh > limit

        if (overLimit && !_uiState.value.isOverLimit) {
            vibrateAlert()
            startAlarmLoop()
        } else if (!overLimit && _uiState.value.isOverLimit) {
            stopAlarmLoop()
        }

        val newPoint = LatLng(location.latitude, location.longitude)
        val updatedRoute = if (_uiState.value.isTracking) {
            (_uiState.value.routePoints + newPoint).takeLast(2000)
        } else _uiState.value.routePoints

        _uiState.value = _uiState.value.copy(
            currentSpeedKmh = speedKmh,
            maxSpeedKmh = maxOf(_uiState.value.maxSpeedKmh, speedKmh),
            avgSpeedKmh = avg,
            distanceKm = totalDistanceMeters / 1000.0,
            hasGpsFix = true,
            accuracyMeters = location.accuracy,
            isSignalWeak = location.accuracy > 20f,
            isOverLimit = overLimit,
            routePoints = updatedRoute,
            currentPosition = newPoint
        )
    }

    private fun vibrateAlert() {
        val context = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Starts a loud, looping alarm sound. Keeps playing until stopAlarmLoop() is called
     *  (i.e. until speed drops back under the limit) — it will not stop on its own. */
    private fun startAlarmLoop() {
        if (alarmPlayer?.isPlaying == true) return
        stopAlarmLoop()
        try {
            val context = getApplication<Application>()
            val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getValidRingtoneUri(context)
            alarmPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                // Alarm stream volume is set by the phone's alarm volume slider, not app volume.
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            // If no alarm sound is available on the device, fall back to repeated vibration.
            vibrateAlert()
        }
    }

    private fun stopAlarmLoop() {
        alarmPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) { }
        }
        alarmPlayer = null
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, getApplication<Application>().mainLooper
        )
        startStaleSignalWatcher()
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        staleCheckJob?.cancel()
    }

    /** Re-kicks location updates, e.g. after the user taps "Retry" when the signal looks stale. */
    fun retryGpsFix() {
        stopLocationUpdates()
        _uiState.value = _uiState.value.copy(hasGpsFix = false)
        startLocationUpdates()
    }

    private fun startStaleSignalWatcher() {
        staleCheckJob?.cancel()
        staleCheckJob = viewModelScope.launch {
            while (true) {
                delay(5000L)
                val stale = lastFixTime != 0L && System.currentTimeMillis() - lastFixTime > 8000L
                if (stale) {
                    _uiState.value = _uiState.value.copy(isSignalWeak = true)
                }
            }
        }
    }

    fun setSpeedLimit(limitKmh: Int?) {
        if (_uiState.value.isOverLimit) stopAlarmLoop()
        _uiState.value = _uiState.value.copy(speedLimitKmh = limitKmh, isOverLimit = false)
    }

    fun startTrip() {
        if (_uiState.value.isTracking) return
        trackingStartTime = System.currentTimeMillis()
        if (tripStartTimeMillis == 0L) tripStartTimeMillis = trackingStartTime
        _uiState.value = _uiState.value.copy(isTracking = true)
        startTimer()
    }

    fun pauseTrip() {
        if (!_uiState.value.isTracking) return
        accumulatedElapsedMs += System.currentTimeMillis() - trackingStartTime
        _uiState.value = _uiState.value.copy(isTracking = false)
        timerJob?.cancel()
    }

    /** Saves the current trip stats to history, then resets everything for a fresh trip. */
    fun saveAndResetTrip() {
        val state = _uiState.value
        if (state.distanceKm > 0.0 || state.elapsedSeconds > 0) {
            viewModelScope.launch {
                tripDao.insert(
                    TripEntity(
                        startTimeMillis = if (tripStartTimeMillis != 0L) tripStartTimeMillis else System.currentTimeMillis(),
                        distanceKm = state.distanceKm,
                        maxSpeedKmh = state.maxSpeedKmh,
                        avgSpeedKmh = state.avgSpeedKmh,
                        durationSeconds = state.elapsedSeconds
                    )
                )
            }
        }
        resetTrip()
    }

    fun resetTrip() {
        timerJob?.cancel()
        stopAlarmLoop()
        totalDistanceMeters = 0.0
        accumulatedElapsedMs = 0L
        trackingStartTime = System.currentTimeMillis()
        tripStartTimeMillis = 0L
        lastLocation = null
        _uiState.value = _uiState.value.copy(
            maxSpeedKmh = 0.0,
            avgSpeedKmh = 0.0,
            distanceKm = 0.0,
            elapsedSeconds = 0L,
            isTracking = false,
            routePoints = emptyList(),
            isOverLimit = false
        )
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch { tripDao.delete(trip) }
    }

    fun clearHistory() {
        viewModelScope.launch { tripDao.deleteAll() }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.isTracking) {
                val elapsedS = (accumulatedElapsedMs + (System.currentTimeMillis() - trackingStartTime)) / 1000
                _uiState.value = _uiState.value.copy(elapsedSeconds = elapsedS)
                delay(1000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
        stopAlarmLoop()
        timerJob?.cancel()
    }
}
