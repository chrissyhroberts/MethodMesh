package com.example.methodmesh.modules.weather

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
internal fun WeatherToolScreen(
    method: WeatherMethodBase,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val app = LocalContext.current
    val scope = rememberCoroutineScope()
    var latitude by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("latitude").orEmpty())
    }
    var longitude by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("longitude").orEmpty())
    }
    var targetTime by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("target_time_iso").orEmpty())
    }
    var frameTime by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("frame_time_iso").orEmpty())
    }
    var horizonHours by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("horizon_hours") ?: when (method.id) {
            As100WeatherForecastMethod.id -> "168"
            As100WeatherMeteogramMethod.id -> "72"
            else -> "24"
        })
    }
    var threshold by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("threshold_mm_per_hour") ?: "0.2")
    }
    var sourcePolicy by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("source_policy") ?: "best_available")
    }
    var zoom by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("zoom") ?: "5")
    }
    var offlineOnly by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.weatherSetting("offline_only")?.equals("true", ignoreCase = true) == true)
    }
    var resultJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedSettingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var running by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready") }
    var attempted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var showLocation by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(latitude.isBlank() || longitude.isBlank())
    }

    val resultValues = remember(resultJson) { resultJson.toStringMap() }
    val committedValues = remember(committedJson) { committedJson.toStringMap() }
    val committedSettings = remember(committedSettingsJson) { committedSettingsJson.toStringMap() }
    val statusField = method.descriptor.outputs.first { it.endsWith("_status") }
    val errorField = method.descriptor.outputs.first { it.endsWith("_error") }

    fun currentSettings(): Map<String, String> =
        context.action.settings.mapKeys { it.key.removePrefix("input_") } +
            mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "target_time_iso" to targetTime,
                "frame_time_iso" to frameTime,
                "horizon_hours" to horizonHours,
                "threshold_mm_per_hour" to threshold,
                "source_policy" to sourcePolicy,
                "zoom" to zoom,
                "offline_only" to offlineOnly.toString()
            )

    fun buildExecution(values: Map<String, String>, settings: Map<String, String> = currentSettings()) = method.result(
        method.request(
            context.action.canonicalId,
            context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings + settings,
            emptyList(),
            emptyList()
        ),
        values,
        context.request.invocationContext
    )
    val committedExecution = remember(committedJson, committedSettingsJson) {
        committedValues.takeIf { it.isNotEmpty() }?.let { values ->
            buildExecution(values, committedSettings.ifEmpty { currentSettings() })
        }
    }

    fun runNow() {
        if (running || latitude.toDoubleOrNull() == null || longitude.toDoubleOrNull() == null) return
        if (method.id == As100WeatherSnapshotMethod.id && targetTime.isBlank()) {
            status = "Weather snapshot needs an ISO-8601 event timestamp."
            return
        }
        running = true
        status = "Refreshing weather…"
        scope.launch {
            val capture = withContext(Dispatchers.IO) {
                WeatherRuntime.capture(method, currentSettings())
            }
            val captured = capture.values
            resultJson = JSONObject(captured).toString()
            running = false
            status = captured[errorField].orEmpty().ifBlank {
                if (captured[statusField] == "succeeded") "Current working result" else "Weather unavailable"
            }
            context.onSettingsChanged(capture.settings)
            if (context.submitsImmediately) onConfirmed(buildExecution(captured))
        }
    }

    fun useDeviceLocation() {
        if (!hasWeatherLocationPermission(app)) return
        running = true
        status = "Getting current location…"
        val token = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(app)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    running = false
                    status = "Current GPS position is unavailable."
                } else {
                    latitude = loc.latitude.toString()
                    longitude = loc.longitude.toString()
                    showLocation = false
                    running = false
                    runNow()
                }
            }
            .addOnFailureListener {
                running = false
                status = it.message ?: "Location failed."
            }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasWeatherLocationPermission(app)) useDeviceLocation()
        else status = "Location denied. Enter coordinates instead."
    }
    fun requestDeviceLocation() {
        if (hasWeatherLocationPermission(app)) useDeviceLocation()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(latitude, longitude, targetTime, frameTime, horizonHours, threshold, sourcePolicy, zoom, offlineOnly) {
        context.onSettingsChanged(currentSettings())
    }
    val nativePresetNeedsRuntimeInput = context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()
    LaunchedEffect(context.startsImmediately, context.action.canonicalId, nativePresetNeedsRuntimeInput) {
        if (!attempted) {
            attempted = true
            if (nativePresetNeedsRuntimeInput) {
                status = "Complete the runtime settings, then refresh."
                showLocation = context.runtimeInputFields.any { it == "latitude" || it == "longitude" }
            } else if (latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null) {
                runNow()
            } else {
                requestDeviceLocation()
            }
        }
    }

    val rootScrollState = rememberScrollState()
    val rootModifier = if (context.presentationMode == CapabilityPresentationMode.Dashboard) {
        Modifier.fillMaxWidth().verticalScroll(rootScrollState)
    } else {
        // ExternalWorkflowActivity already owns vertical scrolling for preset, ODK,
        // protocol and other intent-launched capability surfaces. Nesting another
        // verticalScroll here can be measured with an unbounded height and crash.
        Modifier.fillMaxWidth()
    }

    Column(rootModifier.padding(20.dp)) {
        Text(screenTitle(method.id), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(screenSubtitle(method.id), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (context.stepNumber > 1) OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(Modifier.height(18.dp))

        val locationEditable = context.settingShouldBeShown("latitude") || context.settingShouldBeShown("longitude")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (locationEditable) {
                OutlinedButton(onClick = { showLocation = !showLocation }) { Text(if (showLocation) "Hide location" else "Location") }
                OutlinedButton(onClick = { requestDeviceLocation() }, enabled = !running) { Text("Use GPS") }
            }
            Button(
                onClick = { runNow() },
                enabled = !running && latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null
            ) { Text("Refresh") }
        }
        if (showLocation && locationEditable) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(latitude, { latitude = it; resultJson = "" }, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(longitude, { longitude = it; resultJson = "" }, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (context.settingShouldBeShown("target_time_iso") && method.id in setOf(
                As100WeatherConditionsMethod.id, As100WeatherForecastMethod.id,
                As100WeatherMeteogramMethod.id, As100WeatherWindMethod.id,
                As100WeatherAtmosphereMethod.id, As100WeatherSunMethod.id,
                As100WeatherModelCompareMethod.id
            )
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                targetTime,
                { targetTime = it; resultJson = "" },
                label = { Text("Selected time (ISO-8601 UTC, optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (context.settingShouldBeShown("horizon_hours") && method.id in setOf(As100WeatherForecastMethod.id, As100WeatherPrecipitationMethod.id, As100WeatherMeteogramMethod.id)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                horizonHours,
                { horizonHours = it.filter(Char::isDigit).take(3); resultJson = "" },
                label = { Text("Horizon (hours)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (context.settingShouldBeShown("threshold_mm_per_hour") && method.id == As100WeatherPrecipitationMethod.id) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                threshold,
                { threshold = it.take(8); resultJson = "" },
                label = { Text("Meaningful rain threshold (mm/h)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (method.id == As100WeatherRadarMethod.id &&
            (context.settingShouldBeShown("frame_time_iso") || context.settingShouldBeShown("zoom"))) {
            if (context.settingShouldBeShown("frame_time_iso")) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    frameTime,
                    { frameTime = it; resultJson = "" },
                    label = { Text("Radar frame time (ISO-8601 UTC, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            if (context.settingShouldBeShown("zoom")) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    zoom,
                    { zoom = it.take(4); resultJson = "" },
                    label = { Text("Radar zoom (1–7)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        if (method.id == As100WeatherSnapshotMethod.id &&
            (context.settingShouldBeShown("target_time_iso") || context.settingShouldBeShown("source_policy"))) {
            if (context.settingShouldBeShown("target_time_iso")) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    targetTime,
                    { targetTime = it; resultJson = "" },
                    label = { Text("Event time (ISO-8601)") },
                    placeholder = { Text("2026-09-10T12:00:00Z") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        showSnapshotDateTimePicker(app, targetTime) { picked ->
                            targetTime = picked
                            resultJson = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pick calendar date & time") }
                Text(
                    "Picker uses the device timezone and stores an unambiguous UTC ISO timestamp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (context.settingShouldBeShown("source_policy")) {
                Spacer(Modifier.height(8.dp))
                Text("Source policy", style = MaterialTheme.typography.labelLarge)
                listOf("best_available", "historical_forecast", "reanalysis", "forecast").forEach { policy ->
                    OutlinedButton(
                        onClick = { sourcePolicy = policy; resultJson = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (sourcePolicy == policy) "✓ ${policy.replace('_',' ')}" else policy.replace('_',' '))
                    }
                }
            }
        }
        if (method.descriptor.parameters["connectivity"] == "ONLINE_OFFLINE" && context.settingShouldBeShown("offline_only")) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { offlineOnly = !offlineOnly; resultJson = "" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (offlineOnly) "Cache only · on" else "Cache only · off")
            }
        }
        if (!context.submitsImmediately && !context.isNativePresetRun) {
            Spacer(Modifier.height(10.dp))
            WeatherPresetAuthoring(
                context = context,
                methodId = method.id,
                methodName = screenTitle(method.id),
                currentSettings = currentSettings()
            )
        }
        Spacer(Modifier.height(14.dp))

        if (running) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        } else if (resultValues.isEmpty()) {
            InstrumentCard {
                WeatherGlyph(null, Modifier.width(72.dp).height(72.dp))
                Spacer(Modifier.height(8.dp))
                Text(if (status == "Ready") "Choose a location to begin." else status, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (method.id == As100WeatherRadarMethod.id) "Observed radar remains distinct from model forecast precipitation."
                    else "Live data use MethodMesh's declared online-data layer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            WeatherInstrumentResult(method.id, resultValues) { selectedFrameTime ->
                frameTime = selectedFrameTime
                resultJson = ""
                runNow()
            }
            Spacer(Modifier.height(12.dp))
            if (!context.submitsImmediately) {
                Button(
                    onClick = {
                        committedJson = JSONObject(resultValues).toString()
                        committedSettingsJson = JSONObject(currentSettings()).toString()
                        status = "Committed. Refreshing or editing now changes only the working result."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = resultValues[statusField] == "succeeded"
                ) { Text(if (committedJson.isBlank()) "Commit result" else "Recommit result") }
            }
            Text(
                "Commit freezes this weather result. Refreshing creates a new working result.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        committedExecution?.let { execution ->
            Spacer(Modifier.height(12.dp))
            WeatherCommittedActions(
                context = context,
                label = screenTitle(method.id),
                result = execution,
                workingChanged = committedJson != resultJson,
                onDone = { onConfirmed(execution) }
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun WeatherInstrumentResult(methodId: String, v: Map<String, String>, onRadarFrameSelected: (String) -> Unit = {}) {
    when (methodId) {
        "weather.conditions" -> ConditionsResult(v)
        "weather.forecast" -> ForecastResult(v)
        "weather.precipitation" -> PrecipitationResult(v)
        "weather.radar" -> RadarResult(v, onRadarFrameSelected)
        "weather.meteogram" -> MeteogramResult(v)
        "weather.wind" -> WindResult(v)
        "weather.atmosphere" -> AtmosphereResult(v)
        "weather.sun" -> SunResult(v)
        "weather.snapshot" -> SnapshotResult(v)
        "weather.model_compare" -> ModelCompareResult(v)
        else -> InstrumentCard { Text(v.values.firstOrNull { it.isNotBlank() }.orEmpty()) }
    }
}

@Composable
private fun ConditionsResult(v: Map<String, String>) {
    InstrumentCard {
        val code = v["weather_conditions_weather_code"]?.toIntOrNull()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            WeatherGlyph(code, Modifier.width(90.dp).height(90.dp))
            Column {
                CopyValue("Temperature", v["weather_conditions_temperature_c"].orEmpty(), " °C", large = true)
                Text(v["weather_conditions_condition_label"].orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text("Feels ${v["weather_conditions_apparent_temperature_c"].orEmpty()} °C", style = MaterialTheme.typography.bodyMedium)
            }
        }
        MetricGrid(listOf(
            Triple("Rain", v["weather_conditions_precipitation_mm"].orEmpty(), "mm"),
            Triple("Humidity", v["weather_conditions_relative_humidity_pct"].orEmpty(), "%"),
            Triple("Dew point", v["weather_conditions_dew_point_c"].orEmpty(), "°C"),
            Triple("Pressure", v["weather_conditions_pressure_msl_hpa"].orEmpty(), "hPa"),
            Triple("Wind", v["weather_conditions_wind_speed_ms"].orEmpty(), "m/s"),
            Triple("Gust", v["weather_conditions_wind_gust_ms"].orEmpty(), "m/s")
        ))
        Freshness(v, "weather_conditions")
    }
}

@Composable
private fun ForecastResult(v: Map<String, String>) {
    InstrumentCard {
        Text(v["weather_forecast_result"].orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("Hourly temperature · °C", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabeledLineChart(v["weather_forecast_series_json"].orEmpty(), "temperature_c", "°C", Modifier.fillMaxWidth().height(165.dp))
        Spacer(Modifier.height(12.dp))
        DailyForecastStrip(v["weather_forecast_daily_series_json"].orEmpty())
        MetricGrid(listOf(
            Triple("Selected", v["weather_forecast_selected_temperature_c"].orEmpty(), "°C"),
            Triple("Rain", v["weather_forecast_selected_precipitation_mm"].orEmpty(), "mm"),
            Triple("Rain chance", v["weather_forecast_selected_precipitation_probability_pct"].orEmpty(), "%"),
            Triple("Wind", v["weather_forecast_selected_wind_speed_ms"].orEmpty(), "m/s"),
            Triple("Today high", v["weather_forecast_daily_high_c"].orEmpty(), "°C"),
            Triple("Today low", v["weather_forecast_daily_low_c"].orEmpty(), "°C")
        ))
        Freshness(v, "weather_forecast")
    }
}

@Composable
private fun PrecipitationResult(v: Map<String, String>) {
    InstrumentCard {
        Text(v["weather_precipitation_result"].orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        LabeledBarChart(v["weather_precipitation_series_json"].orEmpty(), "precipitation_mm", "mm", Modifier.fillMaxWidth().height(170.dp))
        MetricGrid(listOf(
            Triple("Now", v["weather_precipitation_current_mm"].orEmpty(), "mm"),
            Triple("Total", v["weather_precipitation_total_mm"].orEmpty(), "mm"),
            Triple("Max", v["weather_precipitation_max_hourly_mm"].orEmpty(), "mm/h"),
            Triple("Threshold", v["weather_precipitation_threshold_mm_per_hour"].orEmpty(), "mm/h")
        ))
        v["weather_precipitation_next_threshold_time_iso"]?.takeIf { it.isNotBlank() }?.let { CopyValue("Next meaningful rain", it) }
        Freshness(v, "weather_precipitation")
    }
}

@Composable
private fun RadarResult(v: Map<String, String>, onFrameSelected: (String) -> Unit) {
    InstrumentCard {
        Text("Radar timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        RadarTimelinePanel(v, Modifier.fillMaxWidth().height(290.dp), onFrameSelected)
        Spacer(Modifier.height(8.dp))
        Text("Model forecast precipitation is never labelled radar. Radar data: RainViewer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Freshness(v, "weather_radar")
    }
}

@Composable
internal fun RadarTimelinePanel(
    v: Map<String, String>,
    mapModifier: Modifier,
    onFrameSelected: (String) -> Unit = {}
) {
    val frames = remember(v["weather_radar_timeline_json"]) { radarTimelineEntries(v["weather_radar_timeline_json"].orEmpty()) }
    val originalTime = v["weather_radar_frame_time_iso"].orEmpty()
    val initialIndex = frames.indexOfFirst { it.timeIso == originalTime }.let { if (it >= 0) it else (frames.size - 1).coerceAtLeast(0) }
    var frameIndex by rememberSaveable(v["weather_radar_timeline_json"], originalTime) { mutableStateOf(initialIndex) }
    var playing by rememberSaveable(v["weather_radar_timeline_json"]) { mutableStateOf(false) }
    var mapInteraction by rememberSaveable { mutableStateOf(false) }
    val safeIndex = frameIndex.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    val selected = frames.getOrNull(safeIndex)
    val host = v["weather_radar_host"].orEmpty()
    val tile = selected?.path?.takeIf { host.isNotBlank() }?.let { "$host$it/256/{z}/{x}/{y}/2/1_1.png" }
        ?: v["weather_radar_tile_template"].orEmpty()
    val lat = v["weather_radar_requested_latitude"]?.toDoubleOrNull()
    val lon = v["weather_radar_requested_longitude"]?.toDoubleOrNull()

    LaunchedEffect(playing, frames.size) {
        while (playing && frames.size > 1) {
            delay(850)
            frameIndex = if (frameIndex >= frames.lastIndex) 0 else frameIndex + 1
        }
    }

    if (lat != null && lon != null && tile.isNotBlank()) {
        WeatherRadarMap(
            latitude = lat,
            longitude = lon,
            tileTemplate = tile,
            zoom = v["weather_radar_zoom"]?.toDoubleOrNull() ?: 5.0,
            gesturesEnabled = mapInteraction,
            overlayText = buildString {
                append(shortRadarTime(selected?.timeIso ?: originalTime))
                val source = selected?.sourceClass ?: v["weather_radar_frame_class"].orEmpty()
                if (source.isNotBlank()) append(" · ${if (source == "NOWCAST") "NOWCAST" else "OBSERVED"}")
            },
            modifier = mapModifier
        )
    } else {
        Text(v["weather_radar_error"].orEmpty().ifBlank { "Radar unavailable" }, style = MaterialTheme.typography.bodyMedium)
    }

    if (frames.size > 1) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (playing) {
                    playing = false
                    frames.getOrNull(frameIndex.coerceIn(0, frames.lastIndex))?.timeIso?.let(onFrameSelected)
                } else {
                    playing = true
                }
            }) { Text(if (playing) "Stop" else "Play") }
            OutlinedButton(onClick = { mapInteraction = !mapInteraction }) {
                Text(if (mapInteraction) "Scroll page" else "Move map")
            }
        }
        Text(
            if (mapInteraction) "Map gestures active. Tap Scroll page when finished." else "Map locked so vertical swipes scroll the dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = safeIndex.toFloat(),
            onValueChange = { playing = false; frameIndex = it.roundToInt().coerceIn(0, frames.lastIndex) },
            onValueChangeFinished = { frames.getOrNull(frameIndex.coerceIn(0, frames.lastIndex))?.timeIso?.let(onFrameSelected) },
            valueRange = 0f..frames.lastIndex.toFloat(),
            steps = (frames.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortRadarTime(frames.first().timeIso), style = MaterialTheme.typography.labelSmall)
            Text("${safeIndex + 1} / ${frames.size}", style = MaterialTheme.typography.labelSmall)
            Text(shortRadarTime(frames.last().timeIso), style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
    val frameClass = selected?.sourceClass ?: v["weather_radar_frame_class"].orEmpty()
    Text(if (frameClass == "NOWCAST") "Provider nowcast frame" else "Observed radar frame", style = MaterialTheme.typography.labelLarge)
    Text("Observed ${v["weather_radar_observed_frame_count"].orEmpty()} · provider nowcast ${v["weather_radar_nowcast_frame_count"].orEmpty()}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MeteogramResult(v: Map<String, String>) {
    InstrumentCard {
        Text("Meteogram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val series = v["weather_meteogram_series_json"].orEmpty()
        Text("Temperature · °C", style = MaterialTheme.typography.labelMedium)
        LabeledLineChart(series, "temperature_c", "°C", Modifier.fillMaxWidth().height(155.dp))
        Text("Pressure · hPa", style = MaterialTheme.typography.labelMedium)
        LabeledLineChart(series, "pressure_msl_hpa", "hPa", Modifier.fillMaxWidth().height(145.dp))
        Text("Wind · m/s", style = MaterialTheme.typography.labelMedium)
        LabeledLineChart(series, "wind_speed_ms", "m/s", Modifier.fillMaxWidth().height(145.dp))
        CopyValue("Selected time", v["weather_meteogram_selected_time_iso"].orEmpty())
        Freshness(v, "weather_meteogram")
    }
}

@Composable
private fun WindResult(v: Map<String, String>) {
    InstrumentCard {
        WindDial(v["weather_wind_direction_deg"]?.toDoubleOrNull(), Modifier.fillMaxWidth().height(190.dp))
        Text(v["weather_wind_result"].orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        MetricGrid(listOf(
            Triple("Wind", v["weather_wind_speed_ms"].orEmpty(), "m/s"),
            Triple("Gust", v["weather_wind_gust_ms"].orEmpty(), "m/s"),
            Triple("Direction", v["weather_wind_direction_deg"].orEmpty(), "°")
        ))
        Freshness(v, "weather_wind")
    }
}

@Composable
private fun AtmosphereResult(v: Map<String, String>) {
    InstrumentCard {
        Text("Upper atmosphere", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(v["weather_atmosphere_result"].orEmpty(), style = MaterialTheme.typography.titleMedium)
        MetricGrid(listOf(
            Triple("CAPE", v["weather_atmosphere_cape_jkg"].orEmpty(), "J/kg"),
            Triple("850 hPa temp", v["weather_atmosphere_temperature_850hpa_c"].orEmpty(), "°C"),
            Triple("850 hPa RH", v["weather_atmosphere_relative_humidity_850hpa_pct"].orEmpty(), "%"),
            Triple("850 hPa wind", v["weather_atmosphere_wind_speed_850hpa_ms"].orEmpty(), "m/s"),
            Triple("500 hPa temp", v["weather_atmosphere_temperature_500hpa_c"].orEmpty(), "°C"),
            Triple("300 hPa wind", v["weather_atmosphere_wind_speed_300hpa_ms"].orEmpty(), "m/s")
        ))
        Text("Experimental · pressure-level model data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Freshness(v, "weather_atmosphere")
    }
}

@Composable
private fun SunResult(v: Map<String, String>) {
    InstrumentCard {
        WeatherGlyph(0, Modifier.width(76.dp).height(76.dp))
        Text(v["weather_sun_result"].orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        MetricGrid(listOf(
            Triple("Sunrise", v["weather_sun_sunrise_iso"].orEmpty().substringAfter("T"), ""),
            Triple("Sunset", v["weather_sun_sunset_iso"].orEmpty().substringAfter("T"), ""),
            Triple("Daylight", durationHours(v["weather_sun_daylight_seconds"].orEmpty()), ""),
            Triple("UV max", v["weather_sun_uv_index_max"].orEmpty(), "")
        ))
        Freshness(v, "weather_sun")
    }
}

@Composable
private fun SnapshotResult(v: Map<String, String>) {
    InstrumentCard {
        Text("Research snapshot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(v["weather_snapshot_result"].orEmpty(), style = MaterialTheme.typography.titleMedium)
        Text(v["weather_snapshot_data_class"].orEmpty().replace('_', ' '), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        MetricGrid(listOf(
            Triple("Temperature", v["weather_snapshot_temperature_c"].orEmpty(), "°C"),
            Triple("Rain", v["weather_snapshot_precipitation_mm"].orEmpty(), "mm"),
            Triple("Humidity", v["weather_snapshot_relative_humidity_pct"].orEmpty(), "%"),
            Triple("Pressure", v["weather_snapshot_pressure_msl_hpa"].orEmpty(), "hPa"),
            Triple("Wind", v["weather_snapshot_wind_speed_ms"].orEmpty(), "m/s"),
            Triple("Cloud", v["weather_snapshot_cloud_cover_pct"].orEmpty(), "%")
        ))
        CopyValue("Valid time", v["weather_snapshot_valid_time_iso"].orEmpty())
        Text("Reanalysis is not presented as an observation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModelCompareResult(v: Map<String, String>) {
    InstrumentCard {
        Text("Ensemble spread", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(v["weather_model_compare_result"].orEmpty(), style = MaterialTheme.typography.titleMedium)
        MetricGrid(listOf(
            Triple("Mean", v["weather_model_compare_temperature_mean_c"].orEmpty(), "°C"),
            Triple("Minimum", v["weather_model_compare_temperature_min_c"].orEmpty(), "°C"),
            Triple("Maximum", v["weather_model_compare_temperature_max_c"].orEmpty(), "°C"),
            Triple("Members", v["weather_model_compare_member_count"].orEmpty(), "")
        ))
        Text("Ensemble spread is not labelled as a calibrated confidence interval.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InstrumentCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { Column(Modifier.padding(20.dp)) { content() } }
}

@Composable
internal fun MetricGrid(items: List<Triple<String, String, String>>) {
    Spacer(Modifier.height(12.dp))
    items.filter { it.second.isNotBlank() }.forEach { (label, value, unit) ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CopyValue("", value, if (unit.isBlank()) "" else " $unit")
        }
    }
}

@Composable
internal fun CopyValue(label: String, value: String, suffix: String = "", large: Boolean = false) {
    val context = LocalContext.current
    val text = (value + suffix).trim()
    Column(
        Modifier.clickable(enabled = text.isNotBlank()) {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(if (label.isBlank()) "Weather value" else label, text))
        }.padding(vertical = 3.dp)
    ) {
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (text.isBlank()) "—" else text,
            style = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleMedium,
            fontWeight = if (large) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun Freshness(v: Map<String, String>, prefix: String) {
    Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
    val cache = v["${prefix}_from_cache"] == "true"
    val age = v["${prefix}_data_age_hours"]?.toDoubleOrNull()
    Text(
        buildString {
            append(if (cache) "Cached" else "Fresh")
            age?.let { append(" · "); append(if (it < 1) "${(it * 60).toInt()} min old" else "${it.fmt(1)} h old") }
            v["${prefix}_provider"]?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun WeatherGlyph(code: Int?, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurface
    val rain = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val rainy = code in listOf(51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99)
        val cloudy = code != null && code !in listOf(0,1)
        if (!cloudy) {
            val c = Offset(w * 0.5f, h * 0.45f); val r = min(w,h) * 0.18f
            drawCircle(line, r, c, style = Stroke(width = 3f))
            repeat(8) { i ->
                val a = Math.toRadians(i * 45.0)
                val p1 = Offset(c.x + cos(a).toFloat() * r * 1.45f, c.y + sin(a).toFloat() * r * 1.45f)
                val p2 = Offset(c.x + cos(a).toFloat() * r * 1.9f, c.y + sin(a).toFloat() * r * 1.9f)
                drawLine(line, p1, p2, 3f, cap = StrokeCap.Round)
            }
        } else {
            val y = h * 0.48f
            drawCircle(line, min(w,h) * 0.16f, Offset(w * 0.38f,y), style = Stroke(3f))
            drawCircle(line, min(w,h) * 0.21f, Offset(w * 0.55f,y - 8f), style = Stroke(3f))
            drawLine(line, Offset(w * 0.24f,y + min(w,h) * 0.14f), Offset(w * 0.73f,y + min(w,h) * 0.14f), 3f, cap = StrokeCap.Round)
            if (rainy) repeat(3) { i ->
                val x = w * (0.34f + i * 0.14f)
                drawLine(rain, Offset(x,h * 0.70f), Offset(x - 8f,h * 0.86f), 4f, cap = StrokeCap.Round)
            }
        }
    }
}

private data class WeatherChartPoint(val timeIso: String, val value: Double)

private fun chartPoints(raw: String, key: String): List<WeatherChartPoint> = runCatching {
    val a = JSONArray(raw)
    (0 until a.length()).mapNotNull { index ->
        val o = a.optJSONObject(index) ?: return@mapNotNull null
        val value = o.optDouble(key, Double.NaN).takeIf(Double::isFinite) ?: return@mapNotNull null
        WeatherChartPoint(o.optString("time", o.optString("time_iso", "")), value)
    }
}.getOrDefault(emptyList())

private fun axisTickIndices(size: Int, count: Int = 5): List<Int> {
    if (size <= 1) return listOf(0)
    val n = min(count, size)
    return (0 until n).map { i -> ((size - 1) * i.toDouble() / (n - 1).coerceAtLeast(1)).roundToInt() }.distinct()
}

private val weatherAxisDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.UK)

private val weatherDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

private fun axisTime(iso: String): String =
    parseWeatherDateTime(iso)?.let { weatherAxisDateTimeFormatter.format(it) } ?: iso

private fun weatherDayLabel(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(weatherDayFormatter) }.getOrDefault(isoDate)

private fun parseWeatherDateTime(iso: String): java.time.temporal.TemporalAccessor? =
    runCatching { OffsetDateTime.parse(iso) }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(iso) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso) }.getOrNull()

private fun axisValue(value: Double): String = when {
    kotlin.math.abs(value) >= 100 -> value.fmt(0)
    kotlin.math.abs(value) >= 10 -> value.fmt(1)
    else -> value.fmt(2)
}

@Composable
internal fun LabeledLineChart(raw: String, key: String, unit: String, modifier: Modifier = Modifier) {
    val points = remember(raw, key) { chartPoints(raw, key) }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val left = 50.dp.toPx(); val right = 8.dp.toPx(); val top = 10.dp.toPx(); val bottom = 30.dp.toPx()
        val plotW = (size.width - left - right).coerceAtLeast(1f)
        val plotH = (size.height - top - bottom).coerceAtLeast(1f)
        val minV = points.minOf { it.value }; val maxV = points.maxOf { it.value }; val range = max(0.001, maxV - minV)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label.toArgb(); textSize = 10.sp.toPx() }
        listOf(maxV, (minV + maxV) / 2.0, minV).forEachIndexed { i, value ->
            val y = top + plotH * i / 2f
            drawLine(grid, Offset(left, y), Offset(left + plotW, y), 1f)
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(axisValue(value), left - 6.dp.toPx(), y + 3.dp.toPx(), paint)
        }
        drawLine(label, Offset(left, top), Offset(left, top + plotH), 1.2f)
        drawLine(label, Offset(left, top + plotH), Offset(left + plotW, top + plotH), 1.2f)
        var previous: Offset? = null
        points.forEachIndexed { i, point ->
            val x = left + plotW * i / (points.size - 1).toFloat()
            val y = top + plotH - (((point.value - minV) / range).toFloat() * plotH * 0.90f + plotH * 0.05f)
            val current = Offset(x, y)
            previous?.let { drawLine(line, it, current, 3f, cap = StrokeCap.Round) }
            previous = current
        }
        paint.textAlign = Paint.Align.CENTER
        axisTickIndices(points.size).forEach { i ->
            val x = left + plotW * i / (points.size - 1).toFloat()
            drawLine(label, Offset(x, top + plotH), Offset(x, top + plotH + 4.dp.toPx()), 1f)
            drawContext.canvas.nativeCanvas.drawText(axisTime(points[i].timeIso), x, size.height - 5.dp.toPx(), paint)
        }
        paint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(unit, 2.dp.toPx(), top + 9.dp.toPx(), paint)
    }
}

@Composable
internal fun LabeledBarChart(raw: String, key: String, unit: String, modifier: Modifier = Modifier) {
    val points = remember(raw, key) { chartPoints(raw, key) }
    val bars = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val left = 50.dp.toPx(); val right = 8.dp.toPx(); val top = 10.dp.toPx(); val bottom = 30.dp.toPx()
        val plotW = (size.width - left - right).coerceAtLeast(1f)
        val plotH = (size.height - top - bottom).coerceAtLeast(1f)
        val maxV = max(0.1, points.maxOf { it.value })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label.toArgb(); textSize = 10.sp.toPx() }
        listOf(maxV, maxV / 2.0, 0.0).forEachIndexed { i, value ->
            val y = top + plotH * i / 2f
            drawLine(grid, Offset(left, y), Offset(left + plotW, y), 1f)
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(axisValue(value), left - 6.dp.toPx(), y + 3.dp.toPx(), paint)
        }
        drawLine(label, Offset(left, top), Offset(left, top + plotH), 1.2f)
        drawLine(label, Offset(left, top + plotH), Offset(left + plotW, top + plotH), 1.2f)
        val step = plotW / points.size
        points.forEachIndexed { i, point ->
            val height = (point.value / maxV).toFloat() * plotH * 0.92f
            val x = left + step * (i + 0.5f)
            drawLine(bars, Offset(x, top + plotH), Offset(x, top + plotH - height), max(2f, step * 0.58f), cap = StrokeCap.Butt)
        }
        paint.textAlign = Paint.Align.CENTER
        axisTickIndices(points.size).forEach { i ->
            val x = left + plotW * i / (points.size - 1).coerceAtLeast(1).toFloat()
            drawLine(label, Offset(x, top + plotH), Offset(x, top + plotH + 4.dp.toPx()), 1f)
            drawContext.canvas.nativeCanvas.drawText(axisTime(points[i].timeIso), x, size.height - 5.dp.toPx(), paint)
        }
        paint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(unit, 2.dp.toPx(), top + 9.dp.toPx(), paint)
    }
}

private fun durationHours(rawSeconds: String): String {
    val seconds = rawSeconds.toDoubleOrNull() ?: return rawSeconds
    val hours = (seconds / 3600.0).toInt()
    val minutes = ((seconds % 3600.0) / 60.0).roundToInt()
    return if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
}

private fun showSnapshotDateTimePicker(context: android.content.Context, currentIso: String, onPicked: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = WeatherJson.parseInstant(currentIso)?.atZone(zone) ?: ZonedDateTime.now(zone)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val local = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, zone)
                    onPicked(local.toInstant().toString())
                },
                initial.hour,
                initial.minute,
                true
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    ).show()
}


@Composable
internal fun WindDial(degrees: Double?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val c = Offset(size.width / 2f,size.height / 2f); val r = min(size.width,size.height) * 0.40f
        drawCircle(color,r,c,style=Stroke(2f))
        repeat(16) { i ->
            val a = Math.toRadians(i * 22.5 - 90.0)
            val p1 = Offset(c.x + cos(a).toFloat() * r * 0.88f,c.y + sin(a).toFloat() * r * 0.88f)
            val p2 = Offset(c.x + cos(a).toFloat() * r,c.y + sin(a).toFloat() * r)
            drawLine(color,p1,p2,if(i % 4 == 0)3f else 1.5f)
        }
        degrees?.let {
            val a = Math.toRadians(it - 90.0); val p = Offset(c.x + cos(a).toFloat() * r * 0.78f,c.y + sin(a).toFloat() * r * 0.78f)
            drawLine(accent,c,p,7f,cap=StrokeCap.Round); drawCircle(accent,8f,c)
        }
    }
}

private data class WeatherDailyDisplay(
    val date: String,
    val weatherCode: Int?,
    val highC: Double?,
    val lowC: Double?,
    val rainMm: Double?,
    val rainProbabilityPct: Double?
)

private data class WeatherRadarDisplayFrame(val timeIso: String, val path: String, val sourceClass: String)

@Composable
internal fun DailyForecastStrip(raw: String) {
    val days = remember(raw) { dailyDisplays(raw) }
    if (days.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEach { day ->
            Card(
                modifier = Modifier.width(118.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(weatherDayLabel(day.date), style = MaterialTheme.typography.labelLarge)
                    WeatherGlyph(day.weatherCode, Modifier.width(38.dp).height(38.dp))
                    Text("${day.highC?.fmt(0) ?: "—"} / ${day.lowC?.fmt(0) ?: "—"} °C", style = MaterialTheme.typography.titleSmall)
                    Text("${day.rainMm?.fmt(1) ?: "—"} mm", style = MaterialTheme.typography.bodySmall)
                    day.rainProbabilityPct?.let { Text("${it.fmt(0)}% rain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

private fun dailyDisplays(raw: String): List<WeatherDailyDisplay> = runCatching {
    val a = JSONArray(raw)
    (0 until a.length()).mapNotNull { index ->
        val o = a.optJSONObject(index) ?: return@mapNotNull null
        WeatherDailyDisplay(
            date = o.optString("date", ""),
            weatherCode = o.optInt("weather_code", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            highC = o.optDouble("temperature_max_c", Double.NaN).takeIf(Double::isFinite),
            lowC = o.optDouble("temperature_min_c", Double.NaN).takeIf(Double::isFinite),
            rainMm = o.optDouble("precipitation_sum_mm", Double.NaN).takeIf(Double::isFinite),
            rainProbabilityPct = o.optDouble("precipitation_probability_max_pct", Double.NaN).takeIf(Double::isFinite)
        )
    }
}.getOrDefault(emptyList())

private fun radarTimelineEntries(raw: String): List<WeatherRadarDisplayFrame> = runCatching {
    val root = JSONObject(raw)
    buildList {
        listOf("observed", "nowcast").forEach { key ->
            val a = root.optJSONArray(key) ?: return@forEach
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                add(WeatherRadarDisplayFrame(o.optString("time_iso", ""), o.optString("path", ""), o.optString("class", "")))
            }
        }
    }.sortedBy { it.timeIso }
}.getOrDefault(emptyList())

private fun shortRadarTime(iso: String): String = if (iso.length >= 16) iso.substring(11,16) else iso

private fun screenTitle(id: String) = when(id) {
    "weather.conditions" -> "Conditions"; "weather.forecast" -> "Forecast"; "weather.precipitation" -> "Precipitation"
    "weather.radar" -> "Radar"; "weather.meteogram" -> "Meteogram"; "weather.wind" -> "Wind"
    "weather.atmosphere" -> "Atmosphere"; "weather.sun" -> "Sun & UV"; "weather.snapshot" -> "Weather snapshot"
    "weather.model_compare" -> "Model comparison"; else -> "Weather"
}

private fun screenSubtitle(id: String) = when(id) {
    "weather.radar" -> "Observed weather radar and genuine provider nowcast."
    "weather.snapshot" -> "Timestamped meteorological context with explicit source class."
    "weather.model_compare" -> "Ensemble spread and model disagreement."
    else -> "A canonical MethodMesh weather capability."
}

private fun hasWeatherLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun com.example.methodmesh.transport.workflow.ExternalActionRequest.weatherSetting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

private fun String.toStringMap(): Map<String,String> {
    if (isBlank()) return emptyMap()
    return runCatching {
        val o = JSONObject(this)
        buildMap { val keys = o.keys(); while (keys.hasNext()) { val k = keys.next(); put(k,o.optString(k,"")) } }
    }.getOrDefault(emptyMap())
}
