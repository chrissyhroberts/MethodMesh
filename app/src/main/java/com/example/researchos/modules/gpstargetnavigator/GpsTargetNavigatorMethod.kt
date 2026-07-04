package com.example.researchos.modules.gpstargetnavigator

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.researchos.core.Method
import com.example.researchos.core.MethodCategory
import com.example.researchos.core.MethodField
import com.example.researchos.core.MethodFieldType
import com.example.researchos.core.RequiredWhen
import com.example.researchos.core.GraphOutput
import com.example.researchos.core.GraphField
import com.example.researchos.core.MethodManifest
import com.example.researchos.core.MethodOutput
import com.example.researchos.core.MethodOutputSchema
import com.example.researchos.core.MethodRequest
import com.example.researchos.core.MethodResult
import com.example.researchos.core.MethodStatus
import com.example.researchos.core.ResearchActivity
import com.example.researchos.core.ResearchActivityKind
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState
import com.example.researchos.platform.sensors.PhoneSensorRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.roundToInt

class GpsTargetNavigatorMethod : Method {

    override val manifest = MethodManifest(
        id = "gps_target_navigator",
        name = "GPS Target Navigator",
        description = "Guide the user towards a target GPS coordinate and return distance, bearing and arrival status.",
        version = "0.2.0",
        category = MethodCategory.Mapping,
        status = MethodStatus.Experimental,
        capabilities = listOf(
            ResearchActivity(
                id = "gps_target_navigator.localise",
                kind = ResearchActivityKind.Localise,
                label = "Localise relative to a target coordinate",
                producesEvidence = listOf("distance_m", "bearing_degrees", "arrived")
            )
        ),
        requiredDeviceFeatures = listOf("fine_location", "coarse_location", "compass"),
        contractSummary = "Guides fieldworkers to a known coordinate and returns distance, bearing and arrival evidence."
    )

    override val settings = listOf(
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
        )
    )

    override val outputSchema = MethodOutputSchema(
        graphOutputs = listOf(
            GraphOutput(
                id = "target_entity",
                objectType = KnowledgeObjectType.Entity,
                entityType = "SpatialTarget",
                subjectRole = "target",
                description = "The configured destination or point to be reached.",
                fields = listOf(
                    GraphField("target_name", "Entity.attributes.target_name", MethodFieldType.Text, RequiredWhen.Always),
                    GraphField("target_latitude", "Entity.spatialContext.location.latitude", MethodFieldType.Float, RequiredWhen.Always),
                    GraphField("target_longitude", "Entity.spatialContext.location.longitude", MethodFieldType.Float, RequiredWhen.Always)
                )
            ),
            GraphOutput(
                id = "target_navigation_observation",
                objectType = KnowledgeObjectType.Observation,
                phenomenon = "location.target_navigation",
                subjectRole = "target",
                description = "A location fix interpreted against the target coordinate.",
                fields = listOf(
                    GraphField("current_latitude", "Observation.spatialContext.location.latitude", MethodFieldType.Float, RequiredWhen.OnSuccessfulCapture),
                    GraphField("current_longitude", "Observation.spatialContext.location.longitude", MethodFieldType.Float, RequiredWhen.OnSuccessfulCapture),
                    GraphField("accuracy_m", "Observation.spatialContext.location.accuracy_m", MethodFieldType.Float, RequiredWhen.IfAvailable),
                    GraphField("distance_m", "Observation.values.distance_m", MethodFieldType.Float, RequiredWhen.OnSuccessfulCapture),
                    GraphField("bearing_deg", "Observation.values.bearing_deg", MethodFieldType.Float, RequiredWhen.OnSuccessfulCapture),
                    GraphField("heading_deg", "Observation.values.heading_deg", MethodFieldType.Float, RequiredWhen.IfAvailable),
                    GraphField("relative_bearing_deg", "Observation.values.relative_bearing_deg", MethodFieldType.Float, RequiredWhen.IfAvailable),
                    GraphField("timestamp_ms", "Observation.temporalContext", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
                    GraphField("update_count", "Observation.values.update_count", MethodFieldType.Integer, RequiredWhen.IfAvailable)
                )
            ),
            GraphOutput(
                id = "arrival_state",
                objectType = KnowledgeObjectType.State,
                stateType = "navigation.arrival_state",
                subjectRole = "target",
                fields = listOf(
                    GraphField("arrived", "State.values.arrived", MethodFieldType.Boolean, RequiredWhen.OnSuccessfulCapture),
                    GraphField("status", "State.values.status", MethodFieldType.Text, RequiredWhen.IfAvailable)
                )
            ),
            GraphOutput(
                id = "navigation_outcome",
                objectType = KnowledgeObjectType.Observation,
                phenomenon = "location.navigation_outcome",
                subjectRole = "target",
                description = "Summary observation recorded when a navigation session is saved or aborted.",
                fields = listOf(
                    GraphField("navigation_completed", "Observation.values.navigation_completed", MethodFieldType.Boolean, RequiredWhen.OnSuccessfulCapture),
                    GraphField("duration_seconds", "Observation.values.duration_seconds", MethodFieldType.Float, RequiredWhen.IfAvailable),
                    GraphField("sample_count", "Observation.values.sample_count", MethodFieldType.Integer, RequiredWhen.IfAvailable),
                    GraphField("min_distance_m", "Observation.values.min_distance_m", MethodFieldType.Float, RequiredWhen.IfAvailable),
                    GraphField("mean_accuracy_m", "Observation.values.mean_accuracy_m", MethodFieldType.Float, RequiredWhen.IfAvailable),
                    GraphField("max_accuracy_m", "Observation.values.max_accuracy_m", MethodFieldType.Float, RequiredWhen.IfAvailable)
                )
            )
        ),
        fields = listOf(
            MethodField("target_name", "Target name", MethodFieldType.Text, required = true, graphPath = "Entity.attributes.target_name"),
            MethodField("target_latitude", "Target latitude", MethodFieldType.Float, required = true, graphPath = "Entity.spatialContext.location.latitude"),
            MethodField("target_longitude", "Target longitude", MethodFieldType.Float, required = true, graphPath = "Entity.spatialContext.location.longitude"),
            MethodField("current_latitude", "Current latitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.spatialContext.location.latitude"),
            MethodField("current_longitude", "Current longitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.spatialContext.location.longitude"),
            MethodField("accuracy_m", "Accuracy metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.spatialContext.location.accuracy_m"),
            MethodField("distance_m", "Distance metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.distance_m"),
            MethodField("bearing_deg", "Bearing degrees", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.bearing_deg"),
            MethodField("heading_deg", "Heading degrees", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.heading_deg"),
            MethodField("relative_bearing_deg", "Relative bearing degrees", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.relative_bearing_deg"),
            MethodField("arrived", "Arrived", MethodFieldType.Boolean, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "State.values.arrived"),
            MethodField("timestamp_ms", "Timestamp milliseconds", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.temporalContext"),
            MethodField("update_count", "Update count", MethodFieldType.Integer, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.update_count"),
            MethodField("status", "Status", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "State.values.status"),
            MethodField("event_type", "Event type", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.TransportOnly, graphPath = "Observation.phenomenon"),
            MethodField("navigation_completed", "Navigation completed", MethodFieldType.Boolean, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.navigation_completed"),
            MethodField("arrival_radius_m", "Arrival radius metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "State.values.arrival_radius_m"),
            MethodField("arrival_latitude", "Arrival latitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.arrival_latitude"),
            MethodField("arrival_longitude", "Arrival longitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.arrival_longitude"),
            MethodField("arrival_accuracy_m", "Arrival accuracy metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.arrival_accuracy_m"),
            MethodField("final_distance_m", "Final distance metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.final_distance_m"),
            MethodField("started_at_ms", "Started at milliseconds", MethodFieldType.Integer, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.temporalContext.interval_start"),
            MethodField("ended_at_ms", "Ended at milliseconds", MethodFieldType.Integer, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.temporalContext.interval_end"),
            MethodField("duration_seconds", "Duration seconds", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.duration_seconds"),
            MethodField("sample_count", "Sample count", MethodFieldType.Integer, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.sample_count"),
            MethodField("first_fix_latitude", "First fix latitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.first_fix_latitude"),
            MethodField("first_fix_longitude", "First fix longitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.first_fix_longitude"),
            MethodField("last_fix_latitude", "Last fix latitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.last_fix_latitude"),
            MethodField("last_fix_longitude", "Last fix longitude", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.last_fix_longitude"),
            MethodField("min_distance_m", "Minimum distance metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.min_distance_m"),
            MethodField("mean_accuracy_m", "Mean accuracy metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.mean_accuracy_m"),
            MethodField("max_accuracy_m", "Maximum accuracy metres", MethodFieldType.Float, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.max_accuracy_m")
        )
    )
    @Composable
    override fun Demo(settingsState: SettingsState) {
        val context = LocalContext.current
        var hasLocationPermission by remember {
            mutableStateOf(hasLocationPermission(context))
        }
        var statusText by remember {
            mutableStateOf("Ready to navigate")
        }
        var updateCount by remember {
            mutableIntStateOf(0)
        }
        var lifecycleState by remember {
            mutableStateOf(NavigationLifecycle.Idle)
        }
        var startedAtMs by remember {
            mutableStateOf<Long?>(null)
        }
        var endedAtMs by remember {
            mutableStateOf<Long?>(null)
        }
        val trace = remember {
            mutableStateListOf<GpsTracePoint>()
        }

        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner, context) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasLocationPermission = hasLocationPermission(context)
                    if (hasLocationPermission && lifecycleState == NavigationLifecycle.Idle) {
                        statusText = "Location permission granted. Press Start navigation."
                    }
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        DisposableEffect(context) {
            PhoneSensorRepository.start(context)
            onDispose {
                PhoneSensorRepository.stop()
            }
        }

        val targetLatitude = settingsState.getFloat("target_latitude").toDouble()
        val targetLongitude = settingsState.getFloat("target_longitude").toDouble()
        val arrivalRadius = settingsState.getFloat("arrival_radius_m")

        FusedLocationUpdates(
            enabled = hasLocationPermission && lifecycleState == NavigationLifecycle.Navigating,
            settingsState = settingsState,
            targetLatitude = targetLatitude,
            targetLongitude = targetLongitude,
            arrivalRadius = arrivalRadius,
            onStatus = { statusText = it },
            onUpdateCount = { updateCount = it },
            onLocationFix = { location, count ->
                val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
                val distance = settingsState.getFloat("distance_m")
                trace.add(
                    GpsTracePoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyM = accuracy,
                        distanceM = distance,
                        timestampMs = System.currentTimeMillis()
                    )
                )

                if (settingsState.getBoolean("arrived")) {
                    lifecycleState = NavigationLifecycle.ArrivedPendingSave
                    endedAtMs = System.currentTimeMillis()
                    settingsState.setString("status", "arrived_pending_save")
                    statusText = "Arrived at target. Save navigation result or continue tracking."
                } else {
                    statusText = "Live location update #$count"
                }
            }
        )

        val currentLatitude = settingsState.getFloat("current_latitude")
        val currentLongitude = settingsState.getFloat("current_longitude")
        val accuracy = settingsState.getFloat("accuracy_m")
        val distance = settingsState.getFloat("distance_m")
        val bearing = settingsState.getFloat("bearing_deg")
        val heading = PhoneSensorRepository.headingDegrees
        val relativeBearing = if (heading != null) {
            relativeBearingDegrees(bearing, heading)
        } else {
            bearing
        }
        settingsState.setFloat("heading_deg", heading ?: 0f)
        settingsState.setFloat("relative_bearing_deg", relativeBearing)
        val arrived = settingsState.getBoolean("arrived")

        LaunchedEffect(targetLatitude, targetLongitude, arrivalRadius, currentLatitude, currentLongitude) {
            if (currentLatitude != 0f || currentLongitude != 0f) {
                updateNavigationState(
                    settingsState = settingsState,
                    currentLatitude = currentLatitude.toDouble(),
                    currentLongitude = currentLongitude.toDouble(),
                    accuracy = accuracy,
                    targetLatitude = targetLatitude,
                    targetLongitude = targetLongitude,
                    arrivalRadius = arrivalRadius
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = settingsState.getString("target_name"),
                fontWeight = FontWeight.Bold
            )

            Text(statusText)
            Text("Navigation status: ${lifecycleState.label}", fontWeight = FontWeight.SemiBold)

            if (!hasLocationPermission) {
                Button(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            ActivityCompat.requestPermissions(
                                activity,
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ),
                                LOCATION_PERMISSION_REQUEST_CODE
                            )
                            statusText = "Location permission requested."
                        } else {
                            statusText = "Could not request permission: no Activity context available."
                        }
                    }
                ) {
                    Text("Grant location permission")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (lifecycleState) {
                NavigationLifecycle.Idle,
                NavigationLifecycle.Completed,
                NavigationLifecycle.Aborted -> {
                    Button(
                        enabled = hasLocationPermission,
                        onClick = {
                            trace.clear()
                            updateCount = 0
                            startedAtMs = System.currentTimeMillis()
                            endedAtMs = null
                            lifecycleState = NavigationLifecycle.Navigating
                            settingsState.setString("status", "navigating")
                            statusText = "Navigation started."
                        }
                    ) {
                        Text("Start navigation")
                    }
                }

                NavigationLifecycle.Navigating -> {
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            endedAtMs = now
                            lifecycleState = NavigationLifecycle.Aborted
                            settingsState.setString("status", "aborted")
                            GpsResearchSessionRecorder.recordNavigationOutcome(
                                buildNavigationOutcomeFields(
                                    settingsState = settingsState,
                                    status = "aborted",
                                    startedAtMs = startedAtMs,
                                    endedAtMs = now,
                                    trace = trace
                                )
                            )
                            statusText = "Navigation aborted and recorded."
                        }
                    ) {
                        Text("Abort navigation")
                    }
                }

                NavigationLifecycle.ArrivedPendingSave -> {
                    Row {
                        Button(
                            onClick = {
                                val now = endedAtMs ?: System.currentTimeMillis()
                                endedAtMs = now
                                lifecycleState = NavigationLifecycle.Completed
                                settingsState.setString("status", "arrived")
                                GpsResearchSessionRecorder.recordNavigationOutcome(
                                    buildNavigationOutcomeFields(
                                        settingsState = settingsState,
                                        status = "arrived",
                                        startedAtMs = startedAtMs,
                                        endedAtMs = now,
                                        trace = trace
                                    )
                                )
                                statusText = "Navigation result saved."
                            }
                        ) {
                            Text("Save navigation result")
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        Button(
                            onClick = {
                                lifecycleState = NavigationLifecycle.Navigating
                                endedAtMs = null
                                settingsState.setString("status", "navigating")
                                statusText = "Continuing navigation."
                            }
                        ) {
                            Text("Continue tracking")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            endedAtMs = now
                            lifecycleState = NavigationLifecycle.Aborted
                            settingsState.setString("status", "aborted_after_arrival")
                            GpsResearchSessionRecorder.recordNavigationOutcome(
                                buildNavigationOutcomeFields(
                                    settingsState = settingsState,
                                    status = "aborted_after_arrival",
                                    startedAtMs = startedAtMs,
                                    endedAtMs = now,
                                    trace = trace
                                )
                            )
                            statusText = "Navigation aborted after arrival and recorded."
                        }
                    ) {
                        Text("Abort")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CompassPreview(
                bearingDegrees = bearing,
                headingDegrees = heading,
                relativeBearingDegrees = relativeBearing,
                arrived = arrived
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (settingsState.getBoolean("show_distance")) {
                Text(
                    text = if (distance > 0f) {
                        "Distance: ${formatDistance(distance)}"
                    } else {
                        "Distance: waiting for GPS"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (settingsState.getBoolean("show_bearing")) {
                Text("Bearing: ${bearing.roundToInt()}°")
                Text("Heading: ${heading?.roundToInt()?.toString() ?: "waiting"}°")
                Text("Turn: ${relativeBearing.roundToInt()}°")
            }

            Text(
                text = if (arrived) {
                    "Arrived: within ${arrivalRadius.roundToInt()} m"
                } else {
                    "Not arrived"
                },
                fontWeight = FontWeight.SemiBold
            )

            if (settingsState.getBoolean("show_current_location")) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Current latitude: ${formatCoordinate(currentLatitude)}", fontFamily = FontFamily.Monospace)
                Text("Current longitude: ${formatCoordinate(currentLongitude)}", fontFamily = FontFamily.Monospace)
                Text("Accuracy: ${accuracy.roundToInt()} m", fontFamily = FontFamily.Monospace)
                Text("Updates: $updateCount", fontFamily = FontFamily.Monospace)
                Text("Trace points: ${trace.size}", fontFamily = FontFamily.Monospace)
                Text("Timestamp: ${settingsState.getString("timestamp_ms")}", fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(16.dp))

            GpsResearchSessionPreview()
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

                var updateCount = 0

                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1000L
                )
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
                            onStatus("Loaded last known location. Waiting for live updates.")
                        } else {
                            onStatus("Waiting for first live location update.")
                        }
                    }
                    .addOnFailureListener { exception ->
                        onStatus("Last location unavailable: ${exception.message ?: "unknown error"}")
                    }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    context.mainLooper
                ).addOnSuccessListener {
                    onStatus("Live high-accuracy location updates started.")
                }.addOnFailureListener { exception ->
                    onStatus("Could not start live updates: ${exception.message ?: "unknown error"}")
                }

                onDispose {
                    fusedLocationClient.removeLocationUpdates(callback)
                }
            }
        }
    }

    override fun buildOutput(
        settingsState: SettingsState
    ): MethodOutput = As100LocateTargetMethod.buildOutput(settingsState)

    @Composable
    override fun Help() {
        Text(
            "GPS Target Navigator guides the user towards a configured latitude and longitude. " +
                "It uses high-accuracy fused location updates while the method is visible. " +
                "Compass heading, AR overlay and map view can be added in later patches."
        )
    }

    override fun execute(
        request: MethodRequest
    ): MethodResult {
        return MethodResult(success = true)
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
        Location.distanceBetween(
            currentLatitude,
            currentLongitude,
            targetLatitude,
            targetLongitude,
            result
        )

        val bearing = ((result[1] % 360f) + 360f) % 360f
        return NavigationResult(
            distanceMeters = result[0],
            initialBearingDegrees = bearing
        )
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @Composable
    private fun CompassPreview(
        bearingDegrees: Float,
        headingDegrees: Float?,
        relativeBearingDegrees: Float,
        arrived: Boolean
    ) {
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .padding(8.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 10.dp.toPx()

            drawCircle(
                color = Color.Black,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx()
                )
            )

            val northLength = radius * 0.85f
            drawLine(
                color = Color.Gray,
                start = center,
                end = Offset(center.x, center.y - northLength),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            rotate(degrees = relativeBearingDegrees, pivot = center) {
                drawLine(
                    color = if (arrived) Color(0xFF2E7D32) else Color.Black,
                    start = center,
                    end = Offset(center.x, center.y - northLength),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text("Target ${bearingDegrees.roundToInt()}° • Heading ${headingDegrees?.roundToInt()?.toString() ?: "waiting"}°")
            Spacer(modifier = Modifier.weight(1f))
        }
    }



    @Composable
    private fun GpsResearchSessionPreview() {
        val observations = ResearchRuntime.session.observations
            .filter { it.provenance.methodId == As100LocateTargetMethod.ID }

        val latest = observations.lastOrNull()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Research Session", fontWeight = FontWeight.Bold)
            Text("GPS observations: ${observations.size}")

            if (latest == null) {
                Text("No GPS evidence recorded yet.")
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Latest navigation outcome", fontWeight = FontWeight.SemiBold)

                val preferredKeys = listOf(
                    "event_type",
                    "status",
                    "navigation_completed",
                    "target_name",
                    "target_latitude",
                    "target_longitude",
                    "arrival_latitude",
                    "arrival_longitude",
                    "arrival_accuracy_m",
                    "final_distance_m",
                    "duration_seconds",
                    "sample_count",
                    "min_distance_m",
                    "mean_accuracy_m",
                    "max_accuracy_m",
                    "timestamp_ms"
                )

                preferredKeys.forEach { key ->
                    val value = latest.output.fields[key]
                    if (value != null) {
                        Text("$key: $value", fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }


    private fun buildNavigationOutcomeFields(
        settingsState: SettingsState,
        status: String,
        startedAtMs: Long?,
        endedAtMs: Long?,
        trace: List<GpsTracePoint>
    ): Map<String, Any?> {
        val start = startedAtMs ?: System.currentTimeMillis()
        val end = endedAtMs ?: System.currentTimeMillis()
        val durationSeconds = ((end - start).coerceAtLeast(0L) / 1000.0)

        val first = trace.firstOrNull()
        val last = trace.lastOrNull()
        val minDistance = trace.map { it.distanceM }.filter { it > 0f }.minOrNull()
        val meanAccuracy = trace.map { it.accuracyM }.filter { it > 0f }.averageOrNull()
        val maxAccuracy = trace.map { it.accuracyM }.filter { it > 0f }.maxOrNull()

        return mapOf(
            "capability" to "gps_target_navigator",
            "event_type" to "navigation_outcome",
            "status" to status,
            "navigation_completed" to (status == "arrived"),
            "target_name" to settingsState.getString("target_name"),
            "target_latitude" to settingsState.getFloat("target_latitude"),
            "target_longitude" to settingsState.getFloat("target_longitude"),
            "arrival_radius_m" to settingsState.getFloat("arrival_radius_m"),
            "arrival_latitude" to settingsState.getFloat("current_latitude"),
            "arrival_longitude" to settingsState.getFloat("current_longitude"),
            "arrival_accuracy_m" to settingsState.getFloat("accuracy_m"),
            "final_distance_m" to settingsState.getFloat("distance_m"),
            "bearing_deg" to settingsState.getFloat("bearing_deg"),
            "heading_deg" to settingsState.getFloat("heading_deg"),
            "relative_bearing_deg" to settingsState.getFloat("relative_bearing_deg"),
            "started_at_ms" to start,
            "ended_at_ms" to end,
            "duration_seconds" to durationSeconds,
            "sample_count" to trace.size,
            "first_fix_latitude" to first?.latitude,
            "first_fix_longitude" to first?.longitude,
            "last_fix_latitude" to last?.latitude,
            "last_fix_longitude" to last?.longitude,
            "min_distance_m" to minDistance,
            "mean_accuracy_m" to meanAccuracy,
            "max_accuracy_m" to maxAccuracy,
            "timestamp_ms" to end
        )
    }

    private fun Iterable<Float>.averageOrNull(): Double? {
        val values = this.toList()
        return if (values.isEmpty()) null else values.average()
    }

    private enum class NavigationLifecycle(
        val label: String
    ) {
        Idle("Idle"),
        Navigating("Navigating"),
        ArrivedPendingSave("Arrived - pending save"),
        Completed("Completed"),
        Aborted("Aborted")
    }

    private data class GpsTracePoint(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Float,
        val distanceM: Float,
        val timestampMs: Long
    )

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            "%.2f km".format(distanceMeters / 1000f)
        } else {
            "${distanceMeters.roundToInt()} m"
        }
    }

    private fun formatCoordinate(value: Float): String {
        return if (value == 0f) {
            "waiting"
        } else {
            "%.6f".format(value)
        }
    }

    private fun relativeBearingDegrees(
        bearingDegrees: Float,
        headingDegrees: Float
    ): Float {
        var relative = bearingDegrees - headingDegrees
        while (relative > 180f) relative -= 360f
        while (relative < -180f) relative += 360f
        return relative
    }

    private data class NavigationResult(
        val distanceMeters: Float,
        val initialBearingDegrees: Float
    )

    private fun Context.findActivity(): Activity? {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}
