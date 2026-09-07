package com.example.methodmesh.modules.aviation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val EmergencyPanelBackground = Color(0xFF080B0E)
private val EmergencyPanelSurface = Color(0xFF11171C)
private val EmergencyPanelSurfaceHigh = Color(0xFF172027)
private val EmergencyPanelText = Color(0xFFF4F7F8)
private val EmergencyPanelMuted = Color(0xFF9BAAB4)
private val EmergencyPanelCyan = Color(0xFF62D7F5)
private val EmergencyPanelGreen = Color(0xFF81E6A7)
private val EmergencyPanelAmber = Color(0xFFFFC857)
private val EmergencyPanelRed = Color(0xFFFF6B6B)
private val EmergencySky = Color(0xFF276B91)
private val EmergencyGround = Color(0xFF735236)

object AviationEmergencyInstrumentsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AviationEmergencyInstrumentsMethod.ID
    override val title = "Emergency instrument panel"
    override val description = "Persistent supplementary phone-sensor flight reference with device attitude, GNSS metrics and nearby-airfield context."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val initial = context.action.settings
        val repository = remember { AviationAirfieldRepository(File(androidContext.filesDir, "aviation")) }
        val locationClient = remember { LocationServices.getFusedLocationProviderClient(androidContext) }

        var mountOrientation by rememberSaveable {
            mutableStateOf(initial.aviationValue("mount_orientation") ?: "portrait_up")
        }
        var pitchZero by rememberSaveable {
            mutableStateOf(initial.aviationValue("pitch_zero_deg")?.toDoubleOrNull() ?: 0.0)
        }
        var rollZero by rememberSaveable {
            mutableStateOf(initial.aviationValue("roll_zero_deg")?.toDoubleOrNull() ?: 0.0)
        }
        var attitudeCalibrated by rememberSaveable {
            mutableStateOf(initial.aviationValue("attitude_calibrated")?.toBooleanStrictOrNull() ?: false)
        }
        var airfieldRadiusNm by rememberSaveable {
            mutableStateOf(initial.aviationValue("airfield_radius_nm") ?: "25")
        }
        var airfieldRefreshPolicy by rememberSaveable {
            mutableStateOf(initial.aviationValue("airfield_refresh_policy") ?: "refresh_if_stale")
        }
        var showSetup by rememberSaveable { mutableStateOf(false) }

        var latitude by remember { mutableStateOf<Double?>(initial.aviationValue("latitude")?.toDoubleOrNull()) }
        var longitude by remember { mutableStateOf<Double?>(initial.aviationValue("longitude")?.toDoubleOrNull()) }
        var gpsGroundSpeedKt by remember { mutableStateOf(initial.aviationValue("gps_ground_speed_kt", "ground_speed_kt")?.toDoubleOrNull()) }
        var gpsTrackDeg by remember { mutableStateOf(initial.aviationValue("gps_track_deg", "track_deg")?.toDoubleOrNull()) }
        var gpsAltitudeFt by remember { mutableStateOf(initial.aviationValue("gps_altitude_ft")?.toDoubleOrNull()) }
        var gpsVerticalSpeedFpm by remember { mutableStateOf(initial.aviationValue("gps_vertical_speed_fpm")?.toDoubleOrNull()) }
        var gpsAccuracyM by remember { mutableStateOf(initial.aviationValue("gps_accuracy_m", "location_accuracy_m")?.toDoubleOrNull()) }
        var fixTimeMillis by remember { mutableStateOf<Long?>(null) }
        var previousAltitudeFt by remember { mutableStateOf<Double?>(null) }
        var previousAltitudeTimeMillis by remember { mutableStateOf<Long?>(null) }
        var nearestAirfields by remember { mutableStateOf<List<AviationAirfieldRepository.Airfield>>(emptyList()) }
        var airfieldStatus by remember { mutableStateOf("Airfield reference pending") }
        var lastAirfieldQueryLat by remember { mutableStateOf<Double?>(null) }
        var lastAirfieldQueryLon by remember { mutableStateOf<Double?>(null) }

        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var externalCaptured by rememberSaveable { mutableStateOf(false) }
        var permissionGranted by remember {
            mutableStateOf(hasEmergencyLocationPermission(androidContext))
        }

        fun currentPitchRaw(): Double? = PhoneSensorRepository.pitchDegrees?.toDouble()
        fun currentRollRaw(): Double? = PhoneSensorRepository.rollDegrees?.toDouble()

        fun calibration(): AviationEmergencyCalibration = AviationEmergencyCalibration(
            mountOrientation = mountOrientation,
            pitchZeroDeg = pitchZero,
            rollZeroDeg = rollZero,
            calibrated = attitudeCalibrated
        )

        fun liveSample(): AviationEmergencySample {
            val accelerometer = PhoneSensorRepository.readings["accelerometer"]?.values
            val accelerationMagnitudeG = accelerometer?.takeIf { it.size >= 3 }?.let { sensorValues ->
                sqrt((sensorValues[0] * sensorValues[0] + sensorValues[1] * sensorValues[1] + sensorValues[2] * sensorValues[2]).toDouble()) / 9.80665
            }
            return AviationEmergencySample(
                rawPitchDeg = currentPitchRaw(),
                rawRollDeg = currentRollRaw(),
                deviceHeadingDeg = PhoneSensorRepository.headingDegrees?.toDouble(),
                pressureHpa = PhoneSensorRepository.readings["pressure"]?.values?.firstOrNull()?.toDouble(),
                gpsGroundSpeedKt = gpsGroundSpeedKt,
                gpsTrackDeg = gpsTrackDeg,
                gpsAltitudeFt = gpsAltitudeFt,
                gpsVerticalSpeedFpm = gpsVerticalSpeedFpm,
                gpsAccuracyM = gpsAccuracyM,
                gpsFixAgeSeconds = fixTimeMillis?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 1000.0) },
                magneticAccuracy = PhoneSensorRepository.readings["magnetometer"]?.accuracy,
                accelerationMagnitudeG = accelerationMagnitudeG,
                rotationVectorAvailable = PhoneSensorRepository.readings["rotation_vector"]?.available == true
            )
        }

        fun requestFor(): com.example.methodmesh.core.methodmesh.ExecutionRequest {
            val settings = mapOf(
                "mount_orientation" to mountOrientation,
                "pitch_zero_deg" to pitchZero.toString(),
                "roll_zero_deg" to rollZero.toString(),
                "attitude_calibrated" to attitudeCalibrated.toString(),
                "airfield_radius_nm" to airfieldRadiusNm,
                "airfield_refresh_policy" to airfieldRefreshPolicy
            )
            return As100AviationEmergencyInstrumentsMethod.request(
                action = As100AviationEmergencyInstrumentsMethod.ID,
                context = context.request.invocationContext.asMap(As100AviationEmergencyInstrumentsMethod.ID) + context.action.settings + settings,
                signals = emptyList(),
                inputs = emptyList()
            )
        }

        fun rebuildLocalSnapshot(): ExecutionResult {
            val calculated = AviationEmergencyInstrumentEngine.values(
                sample = liveSample(),
                calibration = calibration(),
                nearestAirfield = nearestAirfields.firstOrNull()
            )
            values = calculated
            val execution = As100AviationEmergencyInstrumentsMethod.result(
                requestFor(),
                calculated,
                context.request.invocationContext
            )
            result = execution
            return execution
        }

        fun shouldReloadAirfields(lat: Double, lon: Double): Boolean {
            val previousLat = lastAirfieldQueryLat ?: return true
            val previousLon = lastAirfieldQueryLon ?: return true
            return AviationCalculations.distanceAndBearing(previousLat, previousLon, lat, lon).distanceNm >= 1.0
        }

        fun refreshAirfields(lat: Double, lon: Double) {
            if (!shouldReloadAirfields(lat, lon)) return
            lastAirfieldQueryLat = lat
            lastAirfieldQueryLon = lon
            airfieldStatus = "Updating nearby airfield reference…"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        repository.searchNearest(
                            latitude = lat,
                            longitude = lon,
                            radiusNm = airfieldRadiusNm.toDoubleOrNull() ?: 25.0,
                            allowedTypes = setOf("large_airport", "medium_airport", "small_airport"),
                            scheduledOnly = false,
                            maximumResults = 3,
                            refreshPolicy = airfieldRefreshPolicy
                        )
                    }
                }
                outcome.onSuccess { search ->
                    nearestAirfields = search.airfields
                    airfieldStatus = when {
                        search.airfields.isEmpty() -> "No matching airfields inside ${airfieldRadiusNm} NM"
                        search.warning.isNotBlank() -> search.warning
                        else -> "Reference catalogue ready"
                    }
                    rebuildLocalSnapshot()
                }.onFailure { error ->
                    airfieldStatus = "Airfield reference unavailable: ${error.message ?: "no cached data"}"
                    nearestAirfields = emptyList()
                    rebuildLocalSnapshot()
                }
            }
        }

        LaunchedEffect(mountOrientation, pitchZero, rollZero, attitudeCalibrated, airfieldRadiusNm, airfieldRefreshPolicy) {
            context.onSettingsChanged(
                mapOf(
                    "mount_orientation" to mountOrientation,
                    "pitch_zero_deg" to pitchZero.toString(),
                    "roll_zero_deg" to rollZero.toString(),
                    "attitude_calibrated" to attitudeCalibrated.toString(),
                    "airfield_radius_nm" to airfieldRadiusNm,
                    "airfield_refresh_policy" to airfieldRefreshPolicy
                )
            )
        }

        DisposableEffect(Unit) {
            val sensorWasAlreadyRunning = PhoneSensorRepository.status == "Running"
            PhoneSensorRepository.start(androidContext)
            val activity = androidContext as? Activity
            val keepScreenFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            val screenWasAlreadyKeptOn = activity?.window?.attributes?.flags?.and(keepScreenFlag) != 0
            activity?.window?.addFlags(keepScreenFlag)
            onDispose {
                if (!sensorWasAlreadyRunning) PhoneSensorRepository.stop()
                if (!screenWasAlreadyKeptOn) activity?.window?.clearFlags(keepScreenFlag)
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            permissionGranted = map.values.any { it } || hasEmergencyLocationPermission(androidContext)
        }

        LaunchedEffect(Unit) {
            if (!permissionGranted && latitude == null && longitude == null) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        val locationCallback = remember {
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    val newAltitudeFt = if (location.hasAltitude()) location.altitude * 3.280839895 else null
                    if (newAltitudeFt != null) {
                        val previousAlt = previousAltitudeFt
                        val previousTime = previousAltitudeTimeMillis
                        if (previousAlt != null && previousTime != null) {
                            val dtSeconds = (location.time - previousTime) / 1000.0
                            if (dtSeconds in 0.5..10.0) {
                                val rawFpm = (newAltitudeFt - previousAlt) / dtSeconds * 60.0
                                gpsVerticalSpeedFpm = gpsVerticalSpeedFpm?.let { prior -> prior * 0.72 + rawFpm * 0.28 } ?: rawFpm
                            }
                        }
                        previousAltitudeFt = newAltitudeFt
                        previousAltitudeTimeMillis = location.time
                    }
                    latitude = location.latitude
                    longitude = location.longitude
                    gpsGroundSpeedKt = if (location.hasSpeed()) location.speed * 1.943844492 else null
                    gpsTrackDeg = if (location.hasBearing()) location.bearing.toDouble() else null
                    gpsAltitudeFt = newAltitudeFt
                    gpsAccuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null
                    fixTimeMillis = location.time
                    refreshAirfields(location.latitude, location.longitude)
                }
            }
        }

        DisposableEffect(permissionGranted, latitude, longitude) {
            if (permissionGranted && initial.aviationValue("latitude") == null && initial.aviationValue("longitude") == null) {
                startEmergencyLocationUpdates(locationClient, locationCallback)
            } else if (latitude != null && longitude != null) {
                refreshAirfields(latitude!!, longitude!!)
            }
            onDispose { locationClient.removeLocationUpdates(locationCallback) }
        }

        val internalIntentTest = context.request.source.equals("intent_test", ignoreCase = true)
        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun || internalIntentTest

        // Keep a recent real ExecutionResult in capability-owned state without committing it.
        // External callers use the separate one-shot path below instead of this live loop.
        LaunchedEffect(keepLiveDashboard) {
            if (keepLiveDashboard) {
                while (true) {
                    delay(1000L)
                    rebuildLocalSnapshot()
                }
            }
        }

        val canUseSnapshot = result?.status == TransformationStatus.Succeeded && values[AviationEmergencyFields.STATUS] == "succeeded"

        // External callers get a bounded sensor-stabilisation window, then one structured snapshot.
        // Do not return an empty GNSS panel merely because the first location callback has not fired yet.
        LaunchedEffect(keepLiveDashboard) {
            if (!keepLiveDashboard && !externalCaptured) {
                var tries = 0
                while ((latitude == null || longitude == null) && tries < 12) {
                    delay(250L)
                    tries += 1
                }
                externalCaptured = true
                rebuildLocalSnapshot()
            }
        }

        LaunchedEffect(context.completionMode, result?.request?.id?.value, keepLiveDashboard) {
            val captured = result
            if (!keepLiveDashboard && captured != null && context.completionMode == CapabilityCompletionMode.AutomaticReturn) {
                onConfirmed(captured)
            }
        }

        val sample = liveSample()
        val attitude = AviationEmergencyInstrumentEngine.transformAttitude(sample.rawPitchDeg, sample.rawRollDeg, calibration())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EmergencyPanelBackground)
        ) {
            AviationImmersiveTopBar(
                title = "Emergency instruments",
                subtitle = qualityTopBarSubtitle(sample, attitude),
                canGoBack = context.stepNumber > 1,
                onBack = onBack,
                onExit = onCancel,
                containerColor = EmergencyPanelSurface,
                contentColor = EmergencyPanelText,
                accentColor = EmergencyPanelCyan
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                EmergencyInstrumentPanel(
                    sample = sample,
                    attitude = attitude,
                    nearestAirfields = nearestAirfields,
                    airfieldStatus = airfieldStatus,
                    showSetup = showSetup,
                    mountOrientation = mountOrientation,
                    airfieldRadiusNm = airfieldRadiusNm,
                    airfieldRefreshPolicy = airfieldRefreshPolicy,
                    onToggleSetup = { showSetup = !showSetup },
                    onMountOrientation = { orientation ->
                        if (orientation != mountOrientation) {
                            mountOrientation = orientation
                            attitudeCalibrated = false
                            pitchZero = 0.0
                            rollZero = 0.0
                        }
                    },
                    onCalibrate = {
                        AviationEmergencyInstrumentEngine.calibrationOffsets(currentPitchRaw(), currentRollRaw(), mountOrientation)?.let { offsets ->
                            pitchZero = offsets.first
                            rollZero = offsets.second
                            attitudeCalibrated = true
                        }
                    },
                    onResetCalibration = {
                        pitchZero = 0.0
                        rollZero = 0.0
                        attitudeCalibrated = false
                    },
                    onAirfieldRadius = { airfieldRadiusNm = it },
                    onAirfieldRefreshPolicy = { airfieldRefreshPolicy = it },
                    onRefreshAirfields = {
                        val lat = latitude
                        val lon = longitude
                        if (lat != null && lon != null) {
                            lastAirfieldQueryLat = null
                            lastAirfieldQueryLon = null
                            refreshAirfields(lat, lon)
                        }
                    },
                    onUseSnapshot = null,
                    snapshotButtonLabel = ""
                )
            }

            if (keepLiveDashboard && canUseSnapshot) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = EmergencyPanelSurface,
                    contentColor = EmergencyPanelText,
                    tonalElevation = 4.dp
                ) {
                    Button(
                        onClick = { onConfirmed(rebuildLocalSnapshot()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .heightIn(min = 52.dp)
                    ) {
                        Text(
                            if (context.isNativePresetRun) "Finish" else "Use this snapshot",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun qualityTopBarSubtitle(
    sample: AviationEmergencySample,
    attitude: AviationEmergencyAttitude
): String = AviationEmergencyInstrumentEngine.quality(sample, attitude)

@SuppressLint("MissingPermission")
private fun startEmergencyLocationUpdates(
    client: com.google.android.gms.location.FusedLocationProviderClient,
    callback: LocationCallback
) {
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
        .setMinUpdateIntervalMillis(500L)
        .setMaxUpdateDelayMillis(1500L)
        .build()
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
}

private fun hasEmergencyLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun EmergencyInstrumentPanel(
    sample: AviationEmergencySample,
    attitude: AviationEmergencyAttitude,
    nearestAirfields: List<AviationAirfieldRepository.Airfield>,
    airfieldStatus: String,
    showSetup: Boolean,
    mountOrientation: String,
    airfieldRadiusNm: String,
    airfieldRefreshPolicy: String,
    onToggleSetup: () -> Unit,
    onMountOrientation: (String) -> Unit,
    onCalibrate: () -> Unit,
    onResetCalibration: () -> Unit,
    onAirfieldRadius: (String) -> Unit,
    onAirfieldRefreshPolicy: (String) -> Unit,
    onRefreshAirfields: () -> Unit,
    onUseSnapshot: (() -> Unit)?,
    snapshotButtonLabel: String
) {
    val quality = AviationEmergencyInstrumentEngine.quality(sample, attitude)
    val pressureAltitude = AviationEmergencyInstrumentEngine.pressureAltitudeFt(sample.pressureHpa)
    val qualityColor = when {
        quality == "REFERENCE DATA AVAILABLE" -> EmergencyPanelGreen
        "NO GPS" in quality || "GPS POOR" in quality || "NO ATT SENSOR" in quality -> EmergencyPanelRed
        else -> EmergencyPanelAmber
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = EmergencyPanelBackground,
        contentColor = EmergencyPanelText
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EmergencyPanelHeader(quality, qualityColor, onToggleSetup)

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 620.dp
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            EmergencyRoundGauge(
                                label = "GS",
                                value = sample.gpsGroundSpeedKt,
                                display = sample.gpsGroundSpeedKt?.let { "${it.roundToInt()}" } ?: "—",
                                unit = "kt · GROUND SPEED",
                                rangeMin = 0.0,
                                rangeMax = 200.0,
                                accent = EmergencyPanelCyan,
                                modifier = Modifier.fillMaxWidth()
                            )
                            EmergencyRoundGauge(
                                label = "GNSS ALT",
                                value = sample.gpsAltitudeFt,
                                display = sample.gpsAltitudeFt?.let { "${it.roundToInt()}" } ?: "—",
                                unit = "ft · NOT PRESSURE ALT",
                                rangeMin = 0.0,
                                rangeMax = 12000.0,
                                accent = EmergencyPanelGreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        EmergencyAttitudeIndicator(attitude, sample.accelerationMagnitudeG, Modifier.weight(1.7f).aspectRatio(1.25f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            EmergencyRoundGauge(
                                label = "TRK",
                                value = sample.gpsTrackDeg,
                                display = sample.gpsTrackDeg?.let { "%03d".format(it.roundToInt() % 360) } ?: "—",
                                unit = "° · GNSS TRACK",
                                rangeMin = 0.0,
                                rangeMax = 360.0,
                                accent = EmergencyPanelCyan,
                                modifier = Modifier.fillMaxWidth()
                            )
                            EmergencyRoundGauge(
                                label = "GNSS VS",
                                value = sample.gpsVerticalSpeedFpm?.let { abs(it) },
                                display = sample.gpsVerticalSpeedFpm?.let { "%+d".format(it.roundToInt()) } ?: "—",
                                unit = "ft/min · ESTIMATE",
                                rangeMin = 0.0,
                                rangeMax = 3000.0,
                                accent = EmergencyPanelAmber,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    EmergencyAttitudeIndicator(attitude, sample.accelerationMagnitudeG, Modifier.fillMaxWidth().aspectRatio(1.35f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmergencyRoundGauge("GS", sample.gpsGroundSpeedKt, sample.gpsGroundSpeedKt?.let { "${it.roundToInt()}" } ?: "—", "kt · GROUND", 0.0, 200.0, EmergencyPanelCyan, Modifier.weight(1f))
                        EmergencyRoundGauge("TRK", sample.gpsTrackDeg, sample.gpsTrackDeg?.let { "%03d".format(it.roundToInt() % 360) } ?: "—", "° · GNSS", 0.0, 360.0, EmergencyPanelCyan, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmergencyRoundGauge("GNSS ALT", sample.gpsAltitudeFt, sample.gpsAltitudeFt?.let { "${it.roundToInt()}" } ?: "—", "ft", 0.0, 12000.0, EmergencyPanelGreen, Modifier.weight(1f))
                        EmergencyRoundGauge("GNSS VS", sample.gpsVerticalSpeedFpm?.let { abs(it) }, sample.gpsVerticalSpeedFpm?.let { "%+d".format(it.roundToInt()) } ?: "—", "ft/min", 0.0, 3000.0, EmergencyPanelAmber, Modifier.weight(1f))
                    }
                }
            }

            EmergencySourceStrip(sample, pressureAltitude)
            EmergencyAirfieldReferenceCard(nearestAirfields, airfieldStatus)

            AnimatedVisibility(showSetup) {
                EmergencySetupCard(
                    mountOrientation = mountOrientation,
                    calibrated = attitude.valid,
                    airfieldRadiusNm = airfieldRadiusNm,
                    airfieldRefreshPolicy = airfieldRefreshPolicy,
                    onMountOrientation = onMountOrientation,
                    onCalibrate = onCalibrate,
                    onResetCalibration = onResetCalibration,
                    onAirfieldRadius = onAirfieldRadius,
                    onAirfieldRefreshPolicy = onAirfieldRefreshPolicy,
                    onRefreshAirfields = onRefreshAirfields
                )
            }

            EmergencySafetyBand(attitude.valid)

            if (onUseSnapshot != null) {
                Button(
                    onClick = onUseSnapshot,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(snapshotButtonLabel, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun EmergencyPanelHeader(quality: String, qualityColor: Color, onToggleSetup: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("EMERGENCY REFERENCE", color = EmergencyPanelRed, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text("SUPPLEMENTARY PHONE INSTRUMENTS", color = EmergencyPanelText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onToggleSetup) { Text("SETUP", color = EmergencyPanelCyan, fontWeight = FontWeight.Bold) }
    }
    Surface(shape = RoundedCornerShape(6.dp), color = qualityColor.copy(alpha = 0.16f), contentColor = qualityColor) {
        Text(quality, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmergencyAttitudeIndicator(attitude: AviationEmergencyAttitude, accelerationMagnitudeG: Double?, modifier: Modifier = Modifier) {
    val measuredPitch = attitude.pitchDeg ?: 0.0
    val measuredRoll = attitude.rollDeg ?: 0.0
    val dynamicCaution = accelerationMagnitudeG?.let { abs(it - 1.0) > 0.18 } == true
    // Do not animate an uncalibrated horizon. A moving raw-phone horizon looks
    // authoritative even when its relationship to the aircraft is unknown.
    val pitch = if (attitude.valid) measuredPitch else 0.0
    val roll = if (attitude.valid) measuredRoll else 0.0
    val borderColor = when {
        !attitude.valid -> EmergencyPanelAmber
        dynamicCaution -> EmergencyPanelAmber
        else -> EmergencyPanelMuted.copy(alpha = 0.25f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(EmergencyPanelSurface)
            .border(if (attitude.valid && !dynamicCaution) 1.dp else 2.dp, borderColor, RoundedCornerShape(18.dp))
            .semantics { contentDescription = "Device attitude reference. Pitch ${attitude.pitchDeg ?: Double.NaN} degrees, roll ${attitude.rollDeg ?: Double.NaN} degrees." },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            clipRect {
                rotate((-roll).toFloat(), pivot = center) {
                    val pitchOffset = (pitch / 45.0 * size.height * 0.45).toFloat()
                    val horizonY = center.y + pitchOffset
                    drawRect(EmergencySky, topLeft = Offset(0f, -size.height), size = androidx.compose.ui.geometry.Size(size.width, horizonY + size.height))
                    drawRect(EmergencyGround, topLeft = Offset(0f, horizonY), size = androidx.compose.ui.geometry.Size(size.width, size.height * 2f))
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, horizonY), Offset(size.width, horizonY), strokeWidth = 3f)
                    for (mark in -30..30 step 10) {
                        if (mark == 0) continue
                        val y = horizonY - (mark / 45f * size.height * 0.45f)
                        val half = if (mark % 20 == 0) size.width * 0.13f else size.width * 0.08f
                        drawLine(Color.White.copy(alpha = 0.8f), Offset(center.x - half, y), Offset(center.x + half, y), strokeWidth = 2f)
                    }
                }
            }
            val wingY = center.y
            drawLine(EmergencyPanelAmber, Offset(center.x - size.width * 0.22f, wingY), Offset(center.x - size.width * 0.06f, wingY), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(EmergencyPanelAmber, Offset(center.x + size.width * 0.06f, wingY), Offset(center.x + size.width * 0.22f, wingY), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(EmergencyPanelAmber, Offset(center.x, wingY - 6f), Offset(center.x, wingY + 10f), strokeWidth = 5f, cap = StrokeCap.Round)
            for (degrees in -60..60 step 15) {
                val radians = (degrees - 90) * PI / 180.0
                val outer = size.minDimension * 0.46f
                val inner = if (degrees % 30 == 0) outer - 18f else outer - 10f
                val a = Offset(center.x + cos(radians).toFloat() * inner, center.y + sin(radians).toFloat() * inner)
                val b = Offset(center.x + cos(radians).toFloat() * outer, center.y + sin(radians).toFloat() * outer)
                drawLine(Color.White.copy(alpha = 0.75f), a, b, strokeWidth = 2f)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    !attitude.valid -> "DEVICE ATT · CALIBRATION REQUIRED"
                    dynamicCaution -> "DEVICE ATT · DYNAMIC CAUTION · P ${measuredPitch.roundToInt()}° · R ${measuredRoll.roundToInt()}°"
                    else -> "DEVICE ATT · P ${measuredPitch.roundToInt()}° · R ${measuredRoll.roundToInt()}°"
                },
                color = if (attitude.valid && !dynamicCaution) EmergencyPanelText else EmergencyPanelAmber,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(EmergencyPanelBackground.copy(alpha = 0.72f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmergencyRoundGauge(
    label: String,
    value: Double?,
    display: String,
    unit: String,
    rangeMin: Double,
    rangeMax: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val fraction = value?.let { ((it - rangeMin) / (rangeMax - rangeMin)).coerceIn(0.0, 1.0) } ?: 0.0
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(EmergencyPanelSurface, CircleShape)
            .border(1.dp, EmergencyPanelMuted.copy(alpha = 0.22f), CircleShape)
            .semantics { contentDescription = "$label $display $unit" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            drawArc(
                color = EmergencyPanelMuted.copy(alpha = 0.18f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.055f, cap = StrokeCap.Round)
            )
            if (value != null) {
                drawArc(
                    color = accent,
                    startAngle = 135f,
                    sweepAngle = (270f * fraction).toFloat(),
                    useCenter = false,
                    style = Stroke(width = size.minDimension * 0.055f, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Text(display, color = EmergencyPanelText, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(unit, color = EmergencyPanelMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun EmergencySourceStrip(sample: AviationEmergencySample, pressureAltitude: Double?) {
    Card(colors = CardDefaults.cardColors(containerColor = EmergencyPanelSurfaceHigh), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmergencyDigitalReadout(
                    "DEV MAG",
                    sample.deviceHeadingDeg?.let { "%03d°".format(it.roundToInt() % 360) } ?: "—",
                    if (sample.magneticAccuracy != null && sample.magneticAccuracy <= 1) "LOW ACCURACY" else "MAGNETOMETER",
                    Modifier.weight(1f)
                )
                EmergencyDigitalReadout(
                    "BARO PA",
                    pressureAltitude?.let { "${it.roundToInt()} ft" } ?: "—",
                    sample.pressureHpa?.let { "${AviationCalculations.format(it, 1)} hPa" } ?: "NO BAROMETER",
                    Modifier.weight(1f)
                )
                EmergencyDigitalReadout(
                    "GPS FIX",
                    sample.gpsAccuracyM?.let { "±${it.roundToInt()} m" } ?: "—",
                    sample.gpsFixAgeSeconds?.let { "${AviationCalculations.format(it, 1)} s old" } ?: "WAITING",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmergencyDigitalReadout(label: String, primary: String, secondary: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = EmergencyPanelCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(primary, color = EmergencyPanelText, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(secondary, color = EmergencyPanelMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun EmergencyAirfieldReferenceCard(airfields: List<AviationAirfieldRepository.Airfield>, status: String) {
    Card(colors = CardDefaults.cardColors(containerColor = EmergencyPanelSurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("NEAREST AIRFIELD REFERENCE", color = EmergencyPanelAmber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            val nearest = airfields.firstOrNull()
            if (nearest == null) {
                Text(status, color = EmergencyPanelMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nearest.ident.ifBlank { "AIRFIELD" }, color = EmergencyPanelText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(nearest.name, color = EmergencyPanelText, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${AviationCalculations.format(nearest.distanceNm, 1)} NM", color = EmergencyPanelAmber, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("BRG ${AviationCalculations.format(nearest.bearingDeg, 0)}°", color = EmergencyPanelText, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                    }
                }
                val details = buildList {
                    nearest.elevationFt?.let { add("ELEV ${it.roundToInt()} ft") }
                    if (nearest.municipality.isNotBlank()) add(nearest.municipality)
                }.joinToString(" · ")
                if (details.isNotBlank()) Text(details, color = EmergencyPanelMuted, style = MaterialTheme.typography.labelMedium)
                if (airfields.size > 1) {
                    HorizontalDivider(color = EmergencyPanelMuted.copy(alpha = 0.18f))
                    Text(
                        airfields.drop(1).joinToString("   ") { field -> "${field.ident} ${AviationCalculations.format(field.distanceNm, 1)} NM/${AviationCalculations.format(field.bearingDeg, 0)}°" },
                        color = EmergencyPanelMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(status, color = EmergencyPanelMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmergencySetupCard(
    mountOrientation: String,
    calibrated: Boolean,
    airfieldRadiusNm: String,
    airfieldRefreshPolicy: String,
    onMountOrientation: (String) -> Unit,
    onCalibrate: () -> Unit,
    onResetCalibration: () -> Unit,
    onAirfieldRadius: (String) -> Unit,
    onAirfieldRefreshPolicy: (String) -> Unit,
    onRefreshAirfields: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = EmergencyPanelSurfaceHigh), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SETUP / CALIBRATION", color = EmergencyPanelCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text("For attitude reference, the phone must be rigidly mounted. Select the physical mount orientation, then set the current device position as level only when you have a trustworthy level reference.", color = EmergencyPanelMuted, style = MaterialTheme.typography.bodySmall)
            EmergencyChoiceField(
                "Mount orientation",
                mountOrientation,
                listOf(
                    "portrait_up" to "Portrait · top edge up",
                    "landscape_left" to "Landscape · top edge left",
                    "landscape_right" to "Landscape · top edge right",
                    "portrait_down" to "Portrait · top edge down"
                ),
                onMountOrientation
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCalibrate, modifier = Modifier.weight(1f)) { Text(if (calibrated) "Re-set level" else "Set current as level") }
                OutlinedButton(onClick = onResetCalibration, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
            HorizontalDivider(color = EmergencyPanelMuted.copy(alpha = 0.18f))
            EmergencyChoiceField("Airfield radius", airfieldRadiusNm, listOf("10" to "10 NM", "25" to "25 NM", "50" to "50 NM", "100" to "100 NM"), onAirfieldRadius)
            EmergencyChoiceField(
                "Reference catalogue",
                airfieldRefreshPolicy,
                listOf("refresh_if_stale" to "Refresh if stale", "cache_only" to "Offline cache only", "force_refresh" to "Force refresh"),
                onAirfieldRefreshPolicy
            )
            OutlinedButton(onClick = onRefreshAirfields, modifier = Modifier.fillMaxWidth()) { Text("Refresh nearby-airfield reference") }
        }
    }
}

@Composable
private fun EmergencySafetyBand(attitudeValid: Boolean) {
    val text = if (attitudeValid) {
        "PHONE REFERENCE ONLY · GS IS NOT AIRSPEED · GNSS ALT IS NOT PRESSURE ALTITUDE · TRK IS NOT HEADING · DEVICE ATTITUDE CAN BE WRONG UNDER ACCELERATION"
    } else {
        "ATTITUDE NOT CALIBRATED · PHONE REFERENCE ONLY · GS IS NOT AIRSPEED · GNSS ALT IS NOT PRESSURE ALTITUDE · TRK IS NOT HEADING"
    }
    Surface(shape = RoundedCornerShape(8.dp), color = EmergencyPanelRed.copy(alpha = 0.13f), contentColor = EmergencyPanelRed) {
        Text(text, modifier = Modifier.padding(9.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyChoiceField(label: String, value: String, choices: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember(value, choices.size) { mutableStateOf(false) }
    val display = choices.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = EmergencyPanelText,
                unfocusedTextColor = EmergencyPanelText,
                focusedBorderColor = EmergencyPanelCyan,
                unfocusedBorderColor = EmergencyPanelMuted.copy(alpha = 0.5f),
                focusedLabelColor = EmergencyPanelCyan,
                unfocusedLabelColor = EmergencyPanelMuted
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (choice, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelected(choice); expanded = false })
            }
        }
    }
}
