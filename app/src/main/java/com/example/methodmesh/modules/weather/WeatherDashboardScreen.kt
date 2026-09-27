package com.example.methodmesh.modules.weather

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun WeatherDashboardScreen(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val app = LocalContext.current
    val scope = rememberCoroutineScope()

    var latitude by rememberSaveable { mutableStateOf((context.action.settings["latitude"] ?: context.action.settings["input_latitude"]).orEmpty()) }
    var longitude by rememberSaveable { mutableStateOf((context.action.settings["longitude"] ?: context.action.settings["input_longitude"]).orEmpty()) }
    var rainThreshold by rememberSaveable { mutableStateOf(context.action.settings["threshold_mm_per_hour"] ?: context.action.settings["input_threshold_mm_per_hour"] ?: "0.2") }
    var offlineOnly by rememberSaveable { mutableStateOf((context.action.settings["offline_only"] ?: context.action.settings["input_offline_only"]).equals("true", true)) }
    var payloadJson by rememberSaveable { mutableStateOf("") }
    var committedDashboardJson by rememberSaveable { mutableStateOf("") }
    var committedSettingsJson by rememberSaveable { mutableStateOf("") }
    var running by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("Locating…") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var showLocation by rememberSaveable { mutableStateOf(context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()) }
    var showRainDetail by rememberSaveable { mutableStateOf(false) }
    var showDeepWeather by rememberSaveable { mutableStateOf(false) }

    val payload = remember(payloadJson) { runCatching { JSONObject(payloadJson) }.getOrNull() }
    fun section(name: String): Map<String, String> = payload?.optJSONObject(name)?.let { o ->
        buildMap {
            val keys = o.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, o.optString(key, ""))
            }
        }
    }.orEmpty()

    fun currentSettings(): Map<String, String> =
        context.action.settings.mapKeys { it.key.removePrefix("input_") } + mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "threshold_mm_per_hour" to rainThreshold,
            "offline_only" to offlineOnly.toString()
        )

    fun buildDashboardExecution(values: Map<String, String>, settings: Map<String, String> = currentSettings()) =
        As100WeatherDashboardMethod.result(
            As100WeatherDashboardMethod.request(
                context.action.canonicalId,
                context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings + settings,
                emptyList(),
                emptyList()
            ),
            values,
            context.request.invocationContext
        )

    val committedExecution = remember(committedDashboardJson, committedSettingsJson) {
        val values = committedDashboardJson.toStringMap()
        val settings = committedSettingsJson.toStringMap()
        values.takeIf { it.isNotEmpty() }?.let { buildDashboardExecution(it, settings.ifEmpty { currentSettings() }) }
    }

    fun runDashboard() {
        if (running || latitude.toDoubleOrNull() == null || longitude.toDoubleOrNull() == null) return
        running = true
        message = "Refreshing weather…"
        scope.launch {
            val settings = currentSettings()
            val capture = withContext(Dispatchers.IO) {
                coroutineScope {
                    val atmosphereDeferred = async { WeatherRuntime.capture(As100WeatherAtmosphereMethod, settings).values }
                    val modelDeferred = async { WeatherRuntime.capture(As100WeatherModelCompareMethod, settings).values }
                    val dashboardCapture = WeatherRuntime.capture(
                        As100WeatherDashboardMethod,
                        settings + ("horizon_hours" to "240")
                    )
                    val shared = dashboardCapture.settings
                    val radarSettings = dashboardCapture.radarPayload?.let { p ->
                        shared + mapOf(
                            "provider" to p.provider,
                            "retrieved_time_iso" to p.retrievedTimeIso,
                            "from_cache" to p.fromCache.toString(),
                            "data_age_hours" to p.dataAgeHours?.toString().orEmpty(),
                            "api_error" to p.error
                        )
                    } ?: shared
                    val snapshotTime = dashboardCapture.values["weather_dashboard_valid_time_iso"].orEmpty()
                    mapOf(
                        "dashboard" to dashboardCapture.values,
                        "conditions" to As100WeatherConditionsMethod.calculate(shared),
                        "forecast" to As100WeatherForecastMethod.calculate(shared + ("horizon_hours" to "168")),
                        "precipitation" to As100WeatherPrecipitationMethod.calculate(shared + ("horizon_hours" to "24")),
                        "radar" to As100WeatherRadarMethod.calculate(radarSettings + ("zoom" to "5")),
                        "meteogram" to As100WeatherMeteogramMethod.calculate(shared + ("horizon_hours" to "72")),
                        "wind" to As100WeatherWindMethod.calculate(shared),
                        "sun" to As100WeatherSunMethod.calculate(shared),
                        "snapshot" to As100WeatherSnapshotMethod.calculate(shared + mapOf("target_time_iso" to snapshotTime, "source_policy" to "forecast")),
                        "atmosphere" to atmosphereDeferred.await(),
                        "model_compare" to modelDeferred.await()
                    )
                }
            }
            payloadJson = JSONObject().apply { capture.forEach { (k, v) -> put(k, JSONObject(v)) } }.toString()
            running = false
            message = "Live dashboard"
            context.onSettingsChanged(settings)
            if (context.submitsImmediately) onConfirmed(buildDashboardExecution(capture["dashboard"].orEmpty(), settings))
        }
    }

    fun useGps() {
        if (!hasDashboardLocationPermission(app)) return
        running = true
        message = "Getting current location…"
        val token = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(app)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    running = false
                    message = "Current GPS position unavailable."
                } else {
                    latitude = loc.latitude.toString()
                    longitude = loc.longitude.toString()
                    running = false
                    showLocation = false
                    runDashboard()
                }
            }
            .addOnFailureListener {
                running = false
                message = it.message ?: "Location failed."
            }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasDashboardLocationPermission(app)) useGps() else {
            running = false
            showLocation = true
            message = "Location denied. Enter coordinates below."
        }
    }
    fun locate() {
        if (hasDashboardLocationPermission(app)) useGps()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val nativePresetNeedsRuntimeInput = context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()
    LaunchedEffect(context.startsImmediately, nativePresetNeedsRuntimeInput) {
        if (!attempted) {
            attempted = true
            if (nativePresetNeedsRuntimeInput) {
                message = "Complete the runtime settings, then refresh."
                showLocation = true
            } else if (latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null) {
                runDashboard()
            } else {
                locate()
            }
        }
    }

    val dashboard = section("dashboard")
    val conditions = section("conditions")
    val forecast = section("forecast")
    val precip = section("precipitation")
    val radar = section("radar")
    val meteogram = section("meteogram")
    val wind = section("wind")
    val sun = section("sun")
    val atmosphere = section("atmosphere")
    val modelCompare = section("model_compare")
    val snapshot = section("snapshot")

    val rootScrollState = rememberScrollState()
    val rootModifier = if (context.presentationMode == CapabilityPresentationMode.Dashboard) {
        Modifier.fillMaxWidth().verticalScroll(rootScrollState)
    } else Modifier.fillMaxWidth()

    Column(rootModifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        DashboardHeader(
            latitude = latitude,
            longitude = longitude,
            retrievedIso = conditions["weather_conditions_retrieved_time_iso"].orEmpty().ifBlank { dashboard["weather_dashboard_retrieved_time_iso"].orEmpty() },
            running = running,
            locationVisible = showLocation,
            canEditLocation = context.settingShouldBeShown("latitude") || context.settingShouldBeShown("longitude"),
            onToggleLocation = { showLocation = !showLocation },
            onGps = { locate() },
            onRefresh = { runDashboard() }
        )

        if (showLocation) {
            Spacer(Modifier.height(8.dp))
            DashboardOptionsCard(
                context = context,
                latitude = latitude,
                longitude = longitude,
                rainThreshold = rainThreshold,
                offlineOnly = offlineOnly,
                running = running,
                onLatitude = { latitude = it; payloadJson = "" },
                onLongitude = { longitude = it; payloadJson = "" },
                onThreshold = { rainThreshold = it.take(8); payloadJson = "" },
                onOffline = { offlineOnly = !offlineOnly; payloadJson = "" },
                onRefresh = { runDashboard() },
                currentSettings = { currentSettings() }
            )
        }

        Spacer(Modifier.height(12.dp))
        when {
            running && dashboard.isEmpty() -> LoadingDashboard(message)
            dashboard.isEmpty() -> DashboardEmpty(message)
            else -> {
                WeatherNowHero(dashboard, conditions, forecast, wind, sun)
                Spacer(Modifier.height(10.dp))
                WeatherQuickFacts(dashboard, conditions, precip, wind, sun)
                Spacer(Modifier.height(10.dp))

                WeatherPanel("Next 24 hours", "Temperature and rain at a glance") {
                    HourlyWeatherStrip(forecast["weather_forecast_series_json"].orEmpty())
                }
                Spacer(Modifier.height(10.dp))

                WeatherPanel("This week", "Daily high, low and rain outlook") {
                    DailyForecastStrip(forecast["weather_forecast_daily_series_json"].orEmpty())
                }
                Spacer(Modifier.height(10.dp))

                WeatherPanel("Radar", "Observed history and provider nowcast") {
                    RadarTimelinePanel(radar, Modifier.fillMaxWidth().height(250.dp))
                }
                Spacer(Modifier.height(10.dp))

                OutlinedButton(onClick = { showRainDetail = !showRainDetail }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showRainDetail) "Hide rain detail" else "Rain detail")
                }
                if (showRainDetail) {
                    Spacer(Modifier.height(8.dp))
                    WeatherPanel("Rain detail", "Timing, accumulation and threshold") {
                        Text(precip["weather_precipitation_result"].orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        LabeledBarChart(precip["weather_precipitation_series_json"].orEmpty(), "precipitation_mm", "mm", Modifier.fillMaxWidth().height(165.dp))
                        MetricGrid(listOf(
                            Triple("Now", precip["weather_precipitation_current_mm"].orEmpty(), "mm"),
                            Triple("24 h total", precip["weather_precipitation_total_mm"].orEmpty(), "mm"),
                            Triple("Max hour", precip["weather_precipitation_max_hourly_mm"].orEmpty(), "mm/h"),
                            Triple("Threshold", precip["weather_precipitation_threshold_mm_per_hour"].orEmpty(), "mm/h")
                        ))
                        precip["weather_precipitation_next_threshold_time_iso"]?.takeIf { it.isNotBlank() }?.let {
                            CopyValue("Next meaningful rain", dashboardFriendlyTime(it))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDeepWeather = !showDeepWeather }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showDeepWeather) "Hide meteorology & research" else "Meteorology & research")
                }
                if (showDeepWeather) {
                    Spacer(Modifier.height(8.dp))
                    WeatherPanel("Today in detail", "Useful secondary readings") {
                        MetricGrid(listOf(
                            Triple("Feels like", conditions["weather_conditions_apparent_temperature_c"].orEmpty(), "°C"),
                            Triple("Humidity", conditions["weather_conditions_relative_humidity_pct"].orEmpty(), "%"),
                            Triple("Dew point", conditions["weather_conditions_dew_point_c"].orEmpty(), "°C"),
                            Triple("Pressure", conditions["weather_conditions_pressure_msl_hpa"].orEmpty(), "hPa"),
                            Triple("Sunrise", dashboardFriendlyClock(sun["weather_sun_sunrise_iso"].orEmpty()), ""),
                            Triple("Sunset", dashboardFriendlyClock(sun["weather_sun_sunset_iso"].orEmpty()), ""),
                            Triple("Daylight", dashboardDuration(sun["weather_sun_daylight_seconds"].orEmpty()), ""),
                            Triple("UV max", sun["weather_sun_uv_index_max"].orEmpty(), "")
                        ))
                    }
                    Spacer(Modifier.height(8.dp))
                    WeatherPanel("Meteogram", "Temperature, pressure and wind across the forecast") {
                        val series = meteogram["weather_meteogram_series_json"].orEmpty()
                        Text("Temperature · °C", style = MaterialTheme.typography.labelMedium)
                        LabeledLineChart(series, "temperature_c", "°C", Modifier.fillMaxWidth().height(145.dp))
                        Text("Pressure · hPa", style = MaterialTheme.typography.labelMedium)
                        LabeledLineChart(series, "pressure_msl_hpa", "hPa", Modifier.fillMaxWidth().height(135.dp))
                        Text("Wind · m/s", style = MaterialTheme.typography.labelMedium)
                        LabeledLineChart(series, "wind_speed_ms", "m/s", Modifier.fillMaxWidth().height(135.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    WeatherPanel("Upper atmosphere · experimental", "Pressure-level and convective model data") {
                        MetricGrid(listOf(
                            Triple("CAPE", atmosphere["weather_atmosphere_cape_jkg"].orEmpty(), "J/kg"),
                            Triple("850 hPa temp", atmosphere["weather_atmosphere_temperature_850hpa_c"].orEmpty(), "°C"),
                            Triple("850 hPa RH", atmosphere["weather_atmosphere_relative_humidity_850hpa_pct"].orEmpty(), "%"),
                            Triple("850 hPa wind", atmosphere["weather_atmosphere_wind_speed_850hpa_ms"].orEmpty(), "m/s"),
                            Triple("500 hPa temp", atmosphere["weather_atmosphere_temperature_500hpa_c"].orEmpty(), "°C"),
                            Triple("300 hPa wind", atmosphere["weather_atmosphere_wind_speed_300hpa_ms"].orEmpty(), "m/s")
                        ))
                    }
                    Spacer(Modifier.height(8.dp))
                    WeatherPanel("Model comparison · experimental", "Ensemble spread is not a calibrated confidence interval") {
                        Text(modelCompare["weather_model_compare_result"].orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        MetricGrid(listOf(
                            Triple("Mean", modelCompare["weather_model_compare_temperature_mean_c"].orEmpty(), "°C"),
                            Triple("Minimum", modelCompare["weather_model_compare_temperature_min_c"].orEmpty(), "°C"),
                            Triple("Maximum", modelCompare["weather_model_compare_temperature_max_c"].orEmpty(), "°C"),
                            Triple("Spread", modelCompare["weather_model_compare_temperature_spread_c"].orEmpty(), "°C"),
                            Triple("Members", modelCompare["weather_model_compare_member_count"].orEmpty(), ""),
                            Triple("Rain mean", modelCompare["weather_model_compare_precipitation_mean_mm"].orEmpty(), "mm")
                        ))
                    }
                    Spacer(Modifier.height(8.dp))
                    WeatherPanel("Research weather snapshot", "Current timestamp through the research snapshot contract") {
                        Text(snapshot["weather_snapshot_result"].orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(snapshot["weather_snapshot_data_class"].orEmpty().replace('_', ' '), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        MetricGrid(listOf(
                            Triple("Temperature", snapshot["weather_snapshot_temperature_c"].orEmpty(), "°C"),
                            Triple("Humidity", snapshot["weather_snapshot_relative_humidity_pct"].orEmpty(), "%"),
                            Triple("Pressure", snapshot["weather_snapshot_pressure_msl_hpa"].orEmpty(), "hPa"),
                            Triple("Cloud", snapshot["weather_snapshot_cloud_cover_pct"].orEmpty(), "%")
                        ))
                        CopyValue("Valid time", dashboardFriendlyTime(snapshot["weather_snapshot_valid_time_iso"].orEmpty()))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Exact requested coordinates and rounded third-party query coordinates remain distinct in provenance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!context.submitsImmediately) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            committedDashboardJson = JSONObject(dashboard).toString()
                            committedSettingsJson = JSONObject(currentSettings()).toString()
                            message = "Committed dashboard snapshot. Live refreshes no longer alter the frozen payload."
                        }
                    ) { Text(if (committedDashboardJson.isBlank()) "Commit dashboard snapshot" else "Recommit dashboard snapshot") }
                }
            }
        }

        committedExecution?.let { execution ->
            Spacer(Modifier.height(10.dp))
            WeatherCommittedActions(
                context = context,
                label = "Weather dashboard",
                result = execution,
                workingChanged = dashboard.isNotEmpty() && committedDashboardJson != JSONObject(dashboard).toString(),
                onDone = { onConfirmed(execution) }
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (context.stepNumber > 1) OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DashboardHeader(
    latitude: String,
    longitude: String,
    retrievedIso: String,
    running: Boolean,
    locationVisible: Boolean,
    canEditLocation: Boolean,
    onToggleLocation: () -> Unit,
    onGps: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.width(230.dp)) {
            Text("WEATHER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                if (latitude.toDoubleOrNull() == null || longitude.toDoubleOrNull() == null) "Location required"
                else "${latitude.toDoubleOrNull()?.fmt(4)}, ${longitude.toDoubleOrNull()?.fmt(4)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (retrievedIso.isNotBlank()) Text("Updated ${dashboardFriendlyTime(retrievedIso)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (canEditLocation) {
                OutlinedButton(onClick = onToggleLocation, enabled = !running) { Text(if (locationVisible) "Hide" else "Location") }
                OutlinedButton(onClick = onGps, enabled = !running) { Text("GPS") }
            }
            Button(onClick = onRefresh, enabled = !running && latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null) { Text("Refresh") }
        }
    }
}

@Composable
private fun DashboardOptionsCard(
    context: CapabilityScreenContext,
    latitude: String,
    longitude: String,
    rainThreshold: String,
    offlineOnly: Boolean,
    running: Boolean,
    onLatitude: (String) -> Unit,
    onLongitude: (String) -> Unit,
    onThreshold: (String) -> Unit,
    onOffline: () -> Unit,
    onRefresh: () -> Unit,
    currentSettings: () -> Map<String, String>
) {
    WeatherPanel("Location & options", "Deeper configuration stays out of the forecast") {
        if (context.settingShouldBeShown("latitude")) {
            OutlinedTextField(latitude, onLatitude, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
        }
        if (context.settingShouldBeShown("longitude")) {
            OutlinedTextField(longitude, onLongitude, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
        }
        if (context.settingShouldBeShown("threshold_mm_per_hour")) {
            OutlinedTextField(rainThreshold, onThreshold, label = { Text("Meaningful rain threshold (mm/h)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
        }
        if (context.settingShouldBeShown("offline_only")) {
            OutlinedButton(onClick = onOffline, modifier = Modifier.fillMaxWidth()) { Text(if (offlineOnly) "Cache only · on" else "Cache only · off") }
            Spacer(Modifier.height(6.dp))
        }
        Button(onClick = onRefresh, enabled = !running && latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("Refresh weather") }
        if (!context.submitsImmediately && !context.isNativePresetRun) {
            Spacer(Modifier.height(8.dp))
            WeatherPresetAuthoring(context, As100WeatherDashboardMethod.id, "Weather dashboard", currentSettings())
        }
    }
}

@Composable
private fun LoadingDashboard(message: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Column {
                Text("Weather", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(160.dp))
    }
}

@Composable
private fun DashboardEmpty(message: String) {
    WeatherPanel("Weather", "Choose a location to begin") {
        WeatherGlyph(null, Modifier.width(80.dp).height(80.dp))
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WeatherNowHero(
    dashboard: Map<String, String>,
    conditions: Map<String, String>,
    forecast: Map<String, String>,
    wind: Map<String, String>,
    sun: Map<String, String>
) {
    val code = conditions["weather_conditions_weather_code"]?.toIntOrNull()
    val condition = conditions["weather_conditions_condition_label"].orEmpty().ifBlank { dashboard["weather_dashboard_condition"].orEmpty() }
    val temp = conditions["weather_conditions_temperature_c"].orEmpty().ifBlank { dashboard["weather_dashboard_temperature_c"].orEmpty() }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(210.dp)) {
                    Text("NOW", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    CopyValue("Temperature", temp, " °C", large = true)
                    Text(condition.ifBlank { "Weather" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Feels like ${conditions["weather_conditions_apparent_temperature_c"].orEmpty().ifBlank { "—" }} °C", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WeatherGlyph(code, Modifier.width(102.dp).height(102.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroMiniValue("TODAY", "${forecast["weather_forecast_daily_high_c"].orEmpty().ifBlank { "—" }} / ${forecast["weather_forecast_daily_low_c"].orEmpty().ifBlank { "—" }} °C")
                HeroMiniValue("WIND", "${wind["weather_wind_speed_ms"].orEmpty().ifBlank { "—" }} m/s ${wind["weather_wind_direction_compass"].orEmpty()}")
                HeroMiniValue("SUNSET", dashboardFriendlyClock(sun["weather_sun_sunset_iso"].orEmpty()).ifBlank { "—" })
            }
        }
    }
}

@Composable
private fun HeroMiniValue(label: String, value: String) {
    Column(Modifier.width(100.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WeatherQuickFacts(
    dashboard: Map<String, String>,
    conditions: Map<String, String>,
    precip: Map<String, String>,
    wind: Map<String, String>,
    sun: Map<String, String>
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickFact("Rain next", dashboardFriendlyClock(precip["weather_precipitation_next_time_iso"].orEmpty()).ifBlank { "None soon" }, "${precip["weather_precipitation_total_mm"].orEmpty().ifBlank { "0" }} mm / 24 h")
        QuickFact("Humidity", "${conditions["weather_conditions_relative_humidity_pct"].orEmpty().ifBlank { "—" }}%", "Dew ${conditions["weather_conditions_dew_point_c"].orEmpty().ifBlank { "—" }} °C")
        QuickFact("Wind", "${wind["weather_wind_speed_ms"].orEmpty().ifBlank { "—" }} m/s", "Gust ${wind["weather_wind_gust_ms"].orEmpty().ifBlank { "—" }} m/s")
        QuickFact("Pressure", "${conditions["weather_conditions_pressure_msl_hpa"].orEmpty().ifBlank { "—" }} hPa", "MSL")
        QuickFact("Daylight", dashboardDuration(sun["weather_sun_daylight_seconds"].orEmpty()).ifBlank { "—" }, "UV ${sun["weather_sun_uv_index_max"].orEmpty().ifBlank { "—" }}")
        dashboard["weather_dashboard_next_rain_time_iso"]?.takeIf { it.isNotBlank() }?.let { QuickFact("Next rain", dashboardFriendlyClock(it), "${dashboard["weather_dashboard_next_rain_mm"].orEmpty()} mm") }
    }
}

@Composable
private fun QuickFact(label: String, value: String, detail: String) {
    Card(modifier = Modifier.width(145.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WeatherPanel(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}

private data class HourlyWeatherPoint(
    val timeIso: String,
    val temperatureC: Double?,
    val weatherCode: Int?,
    val precipitationMm: Double?,
    val precipitationProbabilityPct: Double?
)

@Composable
private fun HourlyWeatherStrip(raw: String) {
    val points = remember(raw) { dashboardHourlyPoints(raw).take(24) }
    if (points.isEmpty()) {
        Text("Hourly forecast unavailable.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        points.forEach { point ->
            Column(modifier = Modifier.width(78.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), RoundedCornerShape(16.dp)).padding(horizontal = 9.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dashboardFriendlyClock(point.timeIso), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(5.dp))
                WeatherGlyph(point.weatherCode, Modifier.width(34.dp).height(34.dp))
                Spacer(Modifier.height(5.dp))
                Text("${point.temperatureC?.fmt(0) ?: "—"}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${point.precipitationProbabilityPct?.fmt(0) ?: "—"}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("${point.precipitationMm?.fmt(1) ?: "—"} mm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dashboardHourlyPoints(raw: String): List<HourlyWeatherPoint> = runCatching {
    val a = JSONArray(raw)
    (0 until a.length()).mapNotNull { index ->
        val o = a.optJSONObject(index) ?: return@mapNotNull null
        HourlyWeatherPoint(
            timeIso = o.optString("time", ""),
            temperatureC = o.optDouble("temperature_c", Double.NaN).takeIf(Double::isFinite),
            weatherCode = o.optInt("weather_code", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            precipitationMm = o.optDouble("precipitation_mm", Double.NaN).takeIf(Double::isFinite),
            precipitationProbabilityPct = o.optDouble("precipitation_probability_pct", Double.NaN).takeIf(Double::isFinite)
        )
    }
}.getOrDefault(emptyList())

private fun hasDashboardLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private val dashboardDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.UK)
private val dashboardClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

private fun dashboardFriendlyTime(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching { dashboardDateTimeFormatter.withZone(ZoneId.systemDefault()).format(Instant.parse(iso)) }
        .recoverCatching { dashboardDateTimeFormatter.format(OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())) }
        .getOrElse { iso }
}

private fun dashboardFriendlyClock(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching { dashboardClockFormatter.withZone(ZoneId.systemDefault()).format(Instant.parse(iso)) }
        .recoverCatching { dashboardClockFormatter.format(OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())) }
        .getOrElse { iso.takeLast(5) }
}

private fun dashboardDuration(rawSeconds: String): String {
    val seconds = rawSeconds.toDoubleOrNull() ?: return rawSeconds
    val hours = (seconds / 3600.0).toInt()
    val minutes = ((seconds % 3600.0) / 60.0).toInt()
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
