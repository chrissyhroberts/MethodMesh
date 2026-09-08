package com.example.methodmesh.modules.gpstargetnavigator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.camera.LiveCameraPreview
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.roundToInt

class GpsTargetNavigatorInteraction {

    val settings = listOf(
        MethodSetting.TextSetting(
            id = "target_plus_code",
            label = "Target Plus Code",
            description = "Optional full Plus Code. When supplied it takes precedence over target coordinates.",
            group = "Target",
            defaultValue = ""
        ),
        MethodSetting.TextSetting(
            id = "target_name",
            label = "Target name",
            group = "Target",
            defaultValue = "Target location"
        ),
        MethodSetting.FloatSetting(
            id = "target_latitude",
            label = "Target latitude",
            group = "Target",
            defaultValue = 0f,
            minimum = -90f,
            maximum = 90f,
            step = 0.0001f,
            decimals = 6
        ),
        MethodSetting.FloatSetting(
            id = "target_longitude",
            label = "Target longitude",
            group = "Target",
            defaultValue = 0f,
            minimum = -180f,
            maximum = 180f,
            step = 0.0001f,
            decimals = 6
        ),
        MethodSetting.FloatSetting(
            id = "arrival_radius_m",
            label = "Arrival radius",
            group = "Target",
            defaultValue = 10f,
            minimum = 1f,
            maximum = 500f,
            step = 1f,
            unit = "m",
            decimals = 0
        ),
        MethodSetting.BooleanSetting(
            id = "show_current_location",
            label = "Show current location",
            group = "Display",
            defaultValue = true
        ),
        MethodSetting.BooleanSetting(
            id = "show_bearing",
            label = "Show bearing",
            group = "Display",
            defaultValue = true
        ),
        MethodSetting.BooleanSetting(
            id = "show_distance",
            label = "Show distance",
            group = "Display",
            defaultValue = true
        ),
        MethodSetting.BooleanSetting(
            id = "show_ar_camera",
            label = "Show AR camera",
            description = "Open the separate live-camera HUD after navigation starts.",
            group = "Display",
            defaultValue = true
        )
    )

    @Composable
    fun Render(
        settingsState: SettingsState,
        startsImmediately: Boolean = false,
        onChangeLocation: (() -> Unit)? = null,
        targetEditor: (@Composable () -> Unit)? = null,
        onNavigationCommitted: (ExecutionResult) -> Unit = {}
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val stateStore = remember(context) { GpsTargetNavigatorStateStore(context) }

        val targetLatitude = settingsState.getFloat("target_latitude").toDouble()
        val targetLongitude = settingsState.getFloat("target_longitude").toDouble()
        val arrivalRadius = settingsState.getFloat("arrival_radius_m")
        val storedSnapshot = remember(targetLatitude, targetLongitude, arrivalRadius) {
            stateStore.load()?.takeIf { it.matchesTarget(targetLatitude, targetLongitude, arrivalRadius) }
        }

        var hasLocationPermission by rememberSaveable {
            mutableStateOf(hasLocationPermission(context))
        }
        var hasCameraPermission by rememberSaveable {
            mutableStateOf(hasCameraPermission(context))
        }
        var cameraStatus by rememberSaveable { mutableStateOf("") }
        var arCameraOpen by rememberSaveable { mutableStateOf(false) }
        var statusText by rememberSaveable {
            mutableStateOf(if (storedSnapshot != null) "Navigation session restored." else "Ready to navigate")
        }
        var lifecycleName by rememberSaveable {
            mutableStateOf(storedSnapshot?.lifecycle ?: NavigationLifecycle.Idle.name)
        }
        var startedAtMs by rememberSaveable {
            mutableStateOf(storedSnapshot?.startedAtMs ?: 0L)
        }
        var updateCount by rememberSaveable {
            mutableStateOf(storedSnapshot?.updateCount ?: 0)
        }
        var hasLocationFixState by rememberSaveable {
            mutableStateOf(storedSnapshot?.hasLocationFix ?: settingsState.getString("timestamp_ms").isNotBlank())
        }
        var currentLatitude by rememberSaveable {
            mutableStateOf(storedSnapshot?.currentLatitude?.toFloat() ?: settingsState.getFloat("current_latitude"))
        }
        var currentLongitude by rememberSaveable {
            mutableStateOf(storedSnapshot?.currentLongitude?.toFloat() ?: settingsState.getFloat("current_longitude"))
        }
        var accuracy by rememberSaveable {
            mutableStateOf(storedSnapshot?.accuracyM ?: settingsState.getFloat("accuracy_m"))
        }
        var firstFixLatitude by rememberSaveable { mutableStateOf(storedSnapshot?.firstFixLatitude) }
        var firstFixLongitude by rememberSaveable { mutableStateOf(storedSnapshot?.firstFixLongitude) }
        var lastFixLatitude by rememberSaveable { mutableStateOf(storedSnapshot?.lastFixLatitude) }
        var lastFixLongitude by rememberSaveable { mutableStateOf(storedSnapshot?.lastFixLongitude) }
        var minDistanceM by rememberSaveable { mutableStateOf(storedSnapshot?.minDistanceM) }
        var accuracySum by rememberSaveable { mutableStateOf(storedSnapshot?.accuracySum ?: 0.0) }
        var accuracyCount by rememberSaveable { mutableStateOf(storedSnapshot?.accuracyCount ?: 0) }
        var maxAccuracyM by rememberSaveable { mutableStateOf(storedSnapshot?.maxAccuracyM) }
        var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }

        val lifecycleState = runCatching { NavigationLifecycle.valueOf(lifecycleName) }
            .getOrDefault(NavigationLifecycle.Idle)

        fun resetTraceSummary() {
            updateCount = 0
            firstFixLatitude = null
            firstFixLongitude = null
            lastFixLatitude = null
            lastFixLongitude = null
            minDistanceM = null
            accuracySum = 0.0
            accuracyCount = 0
            maxAccuracyM = null
        }

        fun persistActiveSession(state: NavigationLifecycle) {
            if (state != NavigationLifecycle.Navigating && state != NavigationLifecycle.ArrivedPendingCommit) return
            stateStore.save(
                NavigationSessionSnapshot(
                    targetName = settingsState.getString("target_name"),
                    targetPlusCode = settingsState.getString("target_plus_code"),
                    targetLatitude = targetLatitude,
                    targetLongitude = targetLongitude,
                    arrivalRadiusM = arrivalRadius,
                    lifecycle = state.name,
                    startedAtMs = startedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    hasLocationFix = hasLocationFixState,
                    currentLatitude = currentLatitude.toDouble(),
                    currentLongitude = currentLongitude.toDouble(),
                    accuracyM = accuracy,
                    distanceM = settingsState.getFloat("distance_m"),
                    bearingDeg = settingsState.getFloat("bearing_deg"),
                    headingDeg = settingsState.getFloat("heading_deg"),
                    relativeBearingDeg = settingsState.getFloat("relative_bearing_deg"),
                    updateCount = updateCount,
                    firstFixLatitude = firstFixLatitude,
                    firstFixLongitude = firstFixLongitude,
                    lastFixLatitude = lastFixLatitude,
                    lastFixLongitude = lastFixLongitude,
                    minDistanceM = minDistanceM,
                    accuracySum = accuracySum,
                    accuracyCount = accuracyCount,
                    maxAccuracyM = maxAccuracyM
                )
            )
        }

        fun startNavigation() {
            resetTraceSummary()
            hasLocationFixState = false
            startedAtMs = System.currentTimeMillis()
            lifecycleName = NavigationLifecycle.Navigating.name
            settingsState.setString("status", "navigating")
            stateStore.clear()
            statusText = "Navigation started."
            persistActiveSession(NavigationLifecycle.Navigating)
        }

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            hasLocationPermission = permissions.values.any { it } || hasLocationPermission(context)
            statusText = if (hasLocationPermission) {
                "Location permission granted."
            } else {
                "Location permission is needed for navigation."
            }
        }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted || hasCameraPermission(context)
            cameraStatus = if (hasCameraPermission) {
                settingsState.setBoolean("show_ar_camera", true)
                arCameraOpen = true
                "AR camera ready."
            } else {
                arCameraOpen = false
                "Camera permission was not granted; compass navigation remains available."
            }
        }

        LaunchedEffect(storedSnapshot) {
            if (storedSnapshot != null) {
                settingsState.setFloat("current_latitude", storedSnapshot.currentLatitude.toFloat())
                settingsState.setFloat("current_longitude", storedSnapshot.currentLongitude.toFloat())
                settingsState.setFloat("accuracy_m", storedSnapshot.accuracyM)
                settingsState.setFloat("distance_m", storedSnapshot.distanceM)
                settingsState.setFloat("bearing_deg", storedSnapshot.bearingDeg)
                settingsState.setFloat("heading_deg", storedSnapshot.headingDeg)
                settingsState.setFloat("relative_bearing_deg", storedSnapshot.relativeBearingDeg)
                settingsState.setFloat("update_count", storedSnapshot.updateCount.toFloat())
            } else if (stateStore.load() != null) {
                stateStore.clear()
            }
        }

        DisposableEffect(lifecycleOwner, context) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasLocationPermission = hasLocationPermission(context)
                    hasCameraPermission = hasCameraPermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                val currentState = runCatching { NavigationLifecycle.valueOf(lifecycleName) }
                    .getOrDefault(NavigationLifecycle.Idle)
                persistActiveSession(currentState)
            }
        }

        DisposableEffect(context) {
            PhoneSensorRepository.start(context)
            onDispose { PhoneSensorRepository.stop() }
        }

        LaunchedEffect(startsImmediately, hasLocationPermission, lifecycleName) {
            if (shouldAutoStartNavigation(
                    startsImmediately = startsImmediately,
                    hasLocationPermission = hasLocationPermission,
                    isIdle = lifecycleState == NavigationLifecycle.Idle
                )
            ) {
                startNavigation()
            }
        }

        LaunchedEffect(startsImmediately) {
            if (startsImmediately && settingsState.getBoolean("show_ar_camera")) {
                if (!hasCameraPermission) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    cameraStatus = "Camera permission requested."
                } else {
                    arCameraOpen = true
                }
            }
        }

        FusedLocationUpdates(
            enabled = shouldCollectLocationUpdates(
                hasLocationPermission = hasLocationPermission,
                isNavigating = lifecycleState == NavigationLifecycle.Navigating,
                isAwaitingSave = lifecycleState == NavigationLifecycle.ArrivedPendingCommit
            ),
            settingsState = settingsState,
            targetLatitude = targetLatitude,
            targetLongitude = targetLongitude,
            arrivalRadius = arrivalRadius,
            onStatus = { statusText = it },
            onUpdateCount = { updateCount = it },
            onLocationFix = { location, count ->
                hasLocationFixState = true
                currentLatitude = location.latitude.toFloat()
                currentLongitude = location.longitude.toFloat()
                accuracy = if (location.hasAccuracy()) location.accuracy else 0f

                if (count > 0) {
                    val distance = settingsState.getFloat("distance_m")
                    if (firstFixLatitude == null) {
                        firstFixLatitude = location.latitude
                        firstFixLongitude = location.longitude
                    }
                    lastFixLatitude = location.latitude
                    lastFixLongitude = location.longitude
                    minDistanceM = listOfNotNull(minDistanceM, distance.takeIf { it >= 0f }).minOrNull()
                    if (accuracy > 0f) {
                        accuracySum += accuracy.toDouble()
                        accuracyCount += 1
                        maxAccuracyM = maxOf(maxAccuracyM ?: 0f, accuracy)
                    }
                }

                val arrivedNow = settingsState.getBoolean("arrived")
                val nextState = when {
                    arrivedNow && lifecycleState == NavigationLifecycle.Navigating -> NavigationLifecycle.ArrivedPendingCommit
                    else -> lifecycleState
                }
                if (nextState != lifecycleState) lifecycleName = nextState.name
                statusText = if (arrivedNow) {
                    "Within arrival radius. GPS continues refining until Commit."
                } else {
                    "Live location update #$count"
                }
                persistActiveSession(nextState)
            }
        )

        val hasLocationFix = hasLocationFixState
        val liveNavigation = if (hasLocationFix) {
            distanceAndBearing(
                currentLatitude = currentLatitude.toDouble(),
                currentLongitude = currentLongitude.toDouble(),
                targetLatitude = targetLatitude,
                targetLongitude = targetLongitude
            )
        } else {
            NavigationResult(
                distanceMeters = settingsState.getFloat("distance_m"),
                initialBearingDegrees = settingsState.getFloat("bearing_deg")
            )
        }
        val distance = liveNavigation.distanceMeters
        val bearing = liveNavigation.initialBearingDegrees
        val flatHeading = PhoneSensorRepository.headingDegrees
        val rearCameraHeading = PhoneSensorRepository.rearCameraHeadingDegrees
        val heading = if (arCameraOpen) rearCameraHeading ?: flatHeading else flatHeading
        val headingMode = if (arCameraOpen && rearCameraHeading != null) "camera" else if (flatHeading != null) "flat" else "waiting"
        val relativeBearing = heading?.let { relativeBearingDegrees(bearing, it) } ?: 0f
        val arrived = hasLocationFix && distance <= arrivalRadius

        LaunchedEffect(currentLatitude, currentLongitude, accuracy, distance, bearing, heading, relativeBearing, arrived) {
            settingsState.setFloat("current_latitude", currentLatitude)
            settingsState.setFloat("current_longitude", currentLongitude)
            settingsState.setFloat("accuracy_m", accuracy)
            settingsState.setFloat("distance_m", distance)
            settingsState.setFloat("bearing_deg", bearing)
            settingsState.setFloat("heading_deg", heading ?: 0f)
            settingsState.setFloat("relative_bearing_deg", relativeBearing)
            settingsState.setBoolean("arrived", arrived)
            settingsState.setFloat("update_count", updateCount.toFloat())
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Navigation hierarchy: direction first, then distance/status, then destination.
            NavigationCompassDashboard(
                bearingDegrees = bearing,
                headingDegrees = heading,
                relativeBearingDegrees = relativeBearing,
                hasLocationFix = hasLocationFix,
                arrived = arrived
            )

            NavigationHeroCard(
                distanceMeters = distance,
                bearingDegrees = bearing,
                headingDegrees = heading,
                relativeBearingDegrees = relativeBearing,
                accuracyMeters = accuracy,
                currentLatitude = currentLatitude,
                currentLongitude = currentLongitude,
                hasLocationFix = hasLocationFix,
                hasHeading = heading != null,
                arrived = arrived,
                lifecycleLabel = lifecycleState.label
            )

            NavigationTargetCard(
                targetName = settingsState.getString("target_name"),
                targetPlusCode = settingsState.getString("target_plus_code"),
                targetLatitude = targetLatitude.toFloat(),
                targetLongitude = targetLongitude.toFloat(),
                arrivalRadiusMeters = arrivalRadius,
                onChangeLocation = onChangeLocation
            )

            // Destination editing lives directly beneath the destination card so the
            // navigator remains one continuous control surface rather than a setup flow.
            targetEditor?.invoke()

            if (!hasLocationPermission) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        statusText = "Location permission requested."
                    }
                ) { Text("Grant location permission") }
            }

            when (lifecycleState) {
                NavigationLifecycle.Idle,
                NavigationLifecycle.Aborted -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasLocationPermission,
                        onClick = ::startNavigation
                    ) { Text("Start navigation") }
                }

                NavigationLifecycle.Navigating,
                NavigationLifecycle.ArrivedPendingCommit -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasLocationFix,
                        onClick = {
                            val now = System.currentTimeMillis()
                            val committedStatus = if (arrived) "arrived" else "committed_en_route"
                            lifecycleName = NavigationLifecycle.Completed.name
                            settingsState.setString("status", committedStatus)
                            stateStore.clear()
                            val outcome = As100LocateTargetMethod.navigationOutcomeResult(
                                buildNavigationOutcomeFields(
                                    settingsState = settingsState,
                                    status = committedStatus,
                                    startedAtMs = startedAtMs.takeIf { it > 0L },
                                    endedAtMs = now,
                                    summary = NavigationTraceSummary(
                                        sampleCount = updateCount,
                                        firstFixLatitude = firstFixLatitude,
                                        firstFixLongitude = firstFixLongitude,
                                        lastFixLatitude = lastFixLatitude,
                                        lastFixLongitude = lastFixLongitude,
                                        minDistanceM = minDistanceM,
                                        meanAccuracyM = if (accuracyCount > 0) accuracySum / accuracyCount else null,
                                        maxAccuracyM = maxAccuracyM
                                    )
                                )
                            )
                            statusText = "Result committed. The payload is now frozen."
                            onNavigationCommitted(outcome)
                        }
                    ) {
                        Text(if (arrived) "Commit arrival" else "Commit current result")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            lifecycleName = NavigationLifecycle.Aborted.name
                            settingsState.setString("status", "aborted")
                            stateStore.clear()
                            statusText = "Navigation stopped without committing a result."
                        }
                    ) { Text("Stop without result") }
                }

                NavigationLifecycle.Completed -> {
                    Text(
                        "Committed result frozen. Use the result actions below, or Retry/Edit for a new run.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (lifecycleState == NavigationLifecycle.Navigating || lifecycleState == NavigationLifecycle.ArrivedPendingCommit) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        settingsState.setBoolean("show_ar_camera", true)
                        if (!hasCameraPermission) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            cameraStatus = "Camera permission requested."
                        } else {
                            arCameraOpen = true
                            cameraStatus = "Opening AR navigator."
                        }
                    }
                ) { Text(if (hasCameraPermission) "Open AR navigator" else "Enable AR navigator") }
            }

            if (statusText.isNotBlank()) {
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
            }
            if (cameraStatus.isNotBlank()) {
                Text(cameraStatus, style = MaterialTheme.typography.bodySmall)
            }

            TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                Text(if (showTechnicalDetails) "Hide technical details" else "Technical details")
            }

            if (showTechnicalDetails) {
                NavigationTelemetryGrid(
                    currentLatitude = currentLatitude,
                    currentLongitude = currentLongitude,
                    accuracyMeters = accuracy,
                    bearingDegrees = bearing,
                    headingDegrees = heading,
                    relativeBearingDegrees = relativeBearing,
                    updateCount = updateCount,
                    hasLocationFix = hasLocationFix
                )
                Spacer(Modifier.height(4.dp))
                Text("Heading source: $headingMode", style = MaterialTheme.typography.bodySmall)
                Text("Arrival radius: ${arrivalRadius.roundToInt()} m", style = MaterialTheme.typography.bodySmall)
                Text("Active-session state persists until Commit or Stop.", style = MaterialTheme.typography.bodySmall)
            }

            if (arCameraOpen && hasCameraPermission) {
                FullscreenArNavigationPreview(
                    relativeBearingDegrees = relativeBearing,
                    bearingDegrees = bearing,
                    headingDegrees = heading,
                    headingMode = headingMode,
                    distanceMeters = distance,
                    accuracyMeters = accuracy,
                    hasHeading = heading != null,
                    hasLocationFix = hasLocationFix,
                    arrived = arrived,
                    onCameraError = { cameraStatus = it },
                    onClose = {
                        arCameraOpen = false
                        cameraStatus = "AR navigator closed."
                    }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun FusedLocationUpdates(
        enabled: Boolean,
        settingsState: SettingsState,
        targetLatitude: Double,
        targetLongitude: Double,
        arrivalRadius: Float,
        onStatus: (String) -> Unit,
        onUpdateCount: (Int) -> Unit,
        onLocationFix: (Location, Int) -> Unit
    ) {
        val context = LocalContext.current

        DisposableEffect(enabled, targetLatitude, targetLongitude, arrivalRadius) {
            if (!enabled) {
                onDispose { }
            } else {
                val fusedLocationClient: FusedLocationProviderClient =
                    LocationServices.getFusedLocationProviderClient(context)
                var updateCount = settingsState.getFloat("update_count").toInt().coerceAtLeast(0)
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                    .setMinUpdateIntervalMillis(500L)
                    .setMaxUpdateDelayMillis(1000L)
                    .setWaitForAccurateLocation(false)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val location = locationResult.lastLocation ?: return
                        updateCount += 1
                        updateNavigationState(
                            settingsState = settingsState,
                            currentLatitude = location.latitude,
                            currentLongitude = location.longitude,
                            accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                            targetLatitude = targetLatitude,
                            targetLongitude = targetLongitude,
                            arrivalRadius = arrivalRadius
                        )
                        settingsState.setFloat("update_count", updateCount.toFloat())
                        onUpdateCount(updateCount)
                        onLocationFix(location, updateCount)
                    }
                }

                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            updateNavigationState(
                                settingsState = settingsState,
                                currentLatitude = location.latitude,
                                currentLongitude = location.longitude,
                                accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                                targetLatitude = targetLatitude,
                                targetLongitude = targetLongitude,
                                arrivalRadius = arrivalRadius
                            )
                            onLocationFix(location, 0)
                            onStatus("Last known position loaded; waiting for live GPS updates.")
                        } else {
                            onStatus("Waiting for first GPS fix.")
                        }
                    }
                    .addOnFailureListener { exception ->
                        onStatus("Last position unavailable: ${exception.message ?: "location error"}")
                    }

                fusedLocationClient.requestLocationUpdates(locationRequest, callback, context.mainLooper)
                    .addOnSuccessListener { onStatus("High-accuracy GPS updates active.") }
                    .addOnFailureListener { exception ->
                        onStatus("Could not start GPS updates: ${exception.message ?: "location error"}")
                    }

                onDispose { fusedLocationClient.removeLocationUpdates(callback) }
            }
        }
    }

    private fun updateNavigationState(
        settingsState: SettingsState,
        currentLatitude: Double,
        currentLongitude: Double,
        accuracy: Float,
        targetLatitude: Double,
        targetLongitude: Double,
        arrivalRadius: Float
    ) {
        As100LocateTargetMethod.updateSettingsFromLocation(
            settingsState = settingsState,
            currentLatitude = currentLatitude,
            currentLongitude = currentLongitude,
            accuracy = accuracy,
            targetLatitude = targetLatitude,
            targetLongitude = targetLongitude,
            arrivalRadius = arrivalRadius
        )
    }

    private fun distanceAndBearing(
        currentLatitude: Double,
        currentLongitude: Double,
        targetLatitude: Double,
        targetLongitude: Double
    ): NavigationResult {
        val result = FloatArray(3)
        Location.distanceBetween(currentLatitude, currentLongitude, targetLatitude, targetLongitude, result)
        val bearing = ((result[1] % 360f) + 360f) % 360f
        return NavigationResult(result[0], bearing)
    }

    @Composable
    private fun FullscreenArNavigationPreview(
        relativeBearingDegrees: Float,
        bearingDegrees: Float,
        headingDegrees: Float?,
        headingMode: String,
        distanceMeters: Float,
        accuracyMeters: Float,
        hasHeading: Boolean,
        hasLocationFix: Boolean,
        arrived: Boolean,
        onCameraError: (String) -> Unit,
        onClose: () -> Unit
    ) {
        val markerColor = if (arrived) Color(0xFF00E676) else Color.White
        val clampedTurn = relativeBearingDegrees.coerceIn(-60f, 60f)
        val context = LocalContext.current

        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                LiveCameraPreview(modifier = Modifier.fillMaxSize(), onError = onCameraError)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centreX = size.width / 2f
                    val horizonY = size.height * 0.44f
                    val targetX = centreX + (clampedTurn / 60f) * (size.width * 0.42f)
                    val target = Offset(targetX, horizonY)

                    drawLine(
                        color = Color.White.copy(alpha = 0.38f),
                        start = Offset(0f, horizonY),
                        end = Offset(size.width, horizonY),
                        strokeWidth = 2.dp.toPx()
                    )
                    if (hasHeading || arrived) {
                        drawCircle(markerColor.copy(alpha = 0.28f), 34.dp.toPx(), target)
                        drawCircle(markerColor, 34.dp.toPx(), target, style = Stroke(width = 5.dp.toPx()))
                        drawLine(
                            markerColor,
                            Offset(targetX - 45.dp.toPx(), horizonY),
                            Offset(targetX + 45.dp.toPx(), horizonY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            markerColor,
                            Offset(targetX, horizonY - 45.dp.toPx()),
                            Offset(targetX, horizonY + 45.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
                        .padding(top = 36.dp, start = 18.dp, end = 18.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (hasLocationFix) "${formatNavigationDistance(distanceMeters)} • GPS ±${accuracyMeters.roundToInt()} m" else "Waiting for GPS",
                        modifier = if (hasLocationFix) Modifier.padding(2.dp) else Modifier,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasHeading) arTurnInstruction(relativeBearingDegrees, arrived) else "Waiting for compass heading",
                        color = markerColor
                    )
                    Text(
                        text = "Target ${bearingDegrees.roundToInt()}° • $headingMode heading ${headingDegrees?.roundToInt()?.toString() ?: "waiting"}°",
                        color = Color.White.copy(alpha = 0.82f)
                    )
                    if (hasLocationFix) {
                        TextButton(onClick = { copyNavigationValue(context, formatRawFloat(distanceMeters)) }) {
                            Text("Tap here to copy distance", color = Color.White)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(modifier = Modifier.weight(1f), onClick = onClose) { Text("Back to dashboard") }
                }
            }
        }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun buildNavigationOutcomeFields(
        settingsState: SettingsState,
        status: String,
        startedAtMs: Long?,
        endedAtMs: Long,
        summary: NavigationTraceSummary
    ): Map<String, Any?> {
        val start = startedAtMs ?: endedAtMs
        val durationSeconds = ((endedAtMs - start).coerceAtLeast(0L) / 1000.0)
        return mapOf(
            "capability" to As100LocateTargetMethod.ID,
            "event_type" to "navigation_outcome",
            "status" to status,
            "navigation_completed" to (status == "arrived"),
            "target_name" to settingsState.getString("target_name"),
            "target_plus_code" to settingsState.getString("target_plus_code"),
            "target_latitude" to settingsState.getFloat("target_latitude"),
            "target_longitude" to settingsState.getFloat("target_longitude"),
            "arrival_radius_m" to settingsState.getFloat("arrival_radius_m"),
            "current_latitude" to settingsState.getFloat("current_latitude"),
            "current_longitude" to settingsState.getFloat("current_longitude"),
            "accuracy_m" to settingsState.getFloat("accuracy_m"),
            "distance_m" to settingsState.getFloat("distance_m"),
            "arrived" to settingsState.getBoolean("arrived"),
            "timestamp_ms" to endedAtMs,
            "update_count" to settingsState.getFloat("update_count").toInt(),
            "arrival_latitude" to settingsState.getFloat("current_latitude"),
            "arrival_longitude" to settingsState.getFloat("current_longitude"),
            "arrival_accuracy_m" to settingsState.getFloat("accuracy_m"),
            "final_distance_m" to settingsState.getFloat("distance_m"),
            "bearing_deg" to settingsState.getFloat("bearing_deg"),
            "heading_deg" to settingsState.getFloat("heading_deg"),
            "relative_bearing_deg" to settingsState.getFloat("relative_bearing_deg"),
            "started_at_ms" to start,
            "ended_at_ms" to endedAtMs,
            "duration_seconds" to durationSeconds,
            "sample_count" to summary.sampleCount,
            "first_fix_latitude" to summary.firstFixLatitude,
            "first_fix_longitude" to summary.firstFixLongitude,
            "last_fix_latitude" to summary.lastFixLatitude,
            "last_fix_longitude" to summary.lastFixLongitude,
            "min_distance_m" to summary.minDistanceM,
            "mean_accuracy_m" to summary.meanAccuracyM,
            "max_accuracy_m" to summary.maxAccuracyM
        )
    }

    private enum class NavigationLifecycle(val label: String) {
        Idle("Ready"),
        Navigating("Navigating"),
        ArrivedPendingCommit("Arrival detected"),
        Completed("Committed"),
        Aborted("Stopped")
    }

    private data class NavigationTraceSummary(
        val sampleCount: Int,
        val firstFixLatitude: Double?,
        val firstFixLongitude: Double?,
        val lastFixLatitude: Double?,
        val lastFixLongitude: Double?,
        val minDistanceM: Float?,
        val meanAccuracyM: Double?,
        val maxAccuracyM: Float?
    )

    private fun relativeBearingDegrees(bearingDegrees: Float, headingDegrees: Float): Float {
        var relative = bearingDegrees - headingDegrees
        while (relative > 180f) relative -= 360f
        while (relative < -180f) relative += 360f
        return relative
    }

    private data class NavigationResult(val distanceMeters: Float, val initialBearingDegrees: Float)
}

internal fun shouldAutoStartNavigation(
    startsImmediately: Boolean,
    hasLocationPermission: Boolean,
    isIdle: Boolean
): Boolean = startsImmediately && hasLocationPermission && isIdle

internal fun shouldCollectLocationUpdates(
    hasLocationPermission: Boolean,
    isNavigating: Boolean,
    isAwaitingSave: Boolean
): Boolean = hasLocationPermission && (isNavigating || isAwaitingSave)

internal fun arTurnInstruction(relativeBearingDegrees: Float, arrived: Boolean): String {
    if (arrived) return "Target is within the arrival radius"
    return when {
        relativeBearingDegrees < -12f -> "Turn left ${kotlin.math.abs(relativeBearingDegrees).roundToInt()}°"
        relativeBearingDegrees > 12f -> "Turn right ${relativeBearingDegrees.roundToInt()}°"
        else -> "Target is ahead"
    }
}
