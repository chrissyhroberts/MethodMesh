package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AstronomyDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DashboardMethod.id
    override val title = "Astronomy dashboard"
    override val description = "A refreshable observing dashboard. Refresh updates the preview; only confirm/return records the snapshot."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val app = LocalContext.current
        val scope = rememberCoroutineScope()
        val lightRepo = remember { LightPollutionRepository(app) }
        var lightCacheCount by remember { mutableStateOf(lightRepo.cacheCount()) }
        var activeLightRaster by remember { mutableStateOf<LightPollutionRepository.RasterRegion?>(null) }
        var lightHeatmap by remember { mutableStateOf<Bitmap?>(null) }
        var currentSite by remember { mutableStateOf<AstroLocation?>(null) }
        var lightBusy by rememberSaveable { mutableStateOf(false) }
        val initial = remember(context.action.settings) { context.action.settings.mapKeys { it.key.removePrefix("input_") } }
        var settingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(JSONObject(initial).toString()) }
        val settings = settingsMap(settingsJson)
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var values by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
        var status by rememberSaveable { mutableStateOf("Locating…") }
        var loading by rememberSaveable { mutableStateOf(false) }
        var attempted by rememberSaveable { mutableStateOf(false) }

        fun merge(extra: Map<String,String>) {
            val base = settingsMap(settingsJson)
            settingsJson = JSONObject(base.toMutableMap().apply { putAll(extra) }).toString()
            context.onSettingsChanged(settingsMap(settingsJson))
        }

        fun fetchAt(location: AstroLocation) {
            currentSite = location
            loading = true
            status = "Refreshing sky data…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val live = AstronomyLiveData.dashboard(app, location.latitude, location.longitude)
                        val lookup = lightRepo.query(location.latitude, location.longitude)
                        val raster = lookup
                            ?.takeIf { it.cacheKind == "nasa_gibs_raster" }
                            ?.let { lightRepo.rasterById(it.regionId) }
                            ?: lightRepo.rasterCovering(location.latitude, location.longitude)
                        val heat = raster?.let(lightRepo::heatmapBitmap)
                        DashboardRefreshBundle(live, lookup, raster, heat)
                    }
                }.onSuccess { bundle ->
                    activeLightRaster = bundle.raster
                    lightHeatmap = bundle.heatmap
                    lightCacheCount = lightRepo.cacheCount()
                    val lightValues = bundle.lookup?.let(As100LightPollutionMethod::fromLookup).orEmpty()
                    val extra = mapOf(
                        "latitude" to location.latitude.toString(),
                        "longitude" to location.longitude.toString(),
                        "resolved_location_source" to location.source,
                        "light_pollution_radiance_nw_cm2_sr" to lightValues[LightPollutionFields.RADIANCE].orEmpty(),
                        "light_pollution_unit" to lightValues[LightPollutionFields.UNIT].orEmpty(),
                        "light_pollution_label" to lightValues[LightPollutionFields.LABEL].orEmpty(),
                        "light_pollution_dataset" to lightValues[LightPollutionFields.DATASET].orEmpty(),
                        "light_pollution_dataset_date" to lightValues[LightPollutionFields.DATASET_DATE].orEmpty(),
                        "light_pollution_cache_kind" to lightValues[LightPollutionFields.CACHE_KIND].orEmpty(),
                        "light_pollution_value_mcd_m2" to lightValues[LightPollutionFields.LEGACY_VALUE].orEmpty(),
                        "light_pollution_ratio" to lightValues[LightPollutionFields.LEGACY_RATIO].orEmpty(),
                        "weather_json" to bundle.live.weatherJson,
                        "air_quality_json" to bundle.live.airQualityJson,
                        "hourly_json" to bundle.live.hourlyJson,
                        "jet_json" to bundle.live.jetJson,
                        "retrieved_time_iso" to bundle.live.retrievedTimeIso.ifBlank { Instant.now().toString() },
                        "data_age_hours" to bundle.live.dataAgeHours?.toString().orEmpty(),
                        "from_cache" to bundle.live.fromCache.toString()
                    )
                    merge(extra)
                    val merged = settings + extra
                    val calculated = As100DashboardMethod.calculate(merged)
                    val execution = methodExecution(As100DashboardMethod, context, calculated, merged)
                    values = calculated
                    result = execution
                    status = if (calculated[DashboardFields.STATUS] == "succeeded") "Updated" else calculated[DashboardFields.ERROR].orEmpty()
                    loading = false
                }.onFailure { error ->
                    loading = false
                    status = "Refresh failed: ${error.message ?: "live data unavailable"}"
                }
            }
        }

        fun selectedLocation(): AstroLocation? {
            return when (settings["location_source"].orEmpty().ifBlank { "auto" }) {
                "plus_code" -> explicitLocation(settings + mapOf("latitude" to "", "longitude" to ""))
                "manual" -> explicitLocation(settings + mapOf("plus_code" to "", "location_source" to "manual"))
                else -> null
            }
        }

        fun refresh() {
            val mode = settings["location_source"].orEmpty().ifBlank { "auto" }
            if (mode == "auto") {
                currentLocation(app, ::fetchAt) { status = it; loading = false }
                return
            }
            val selected = selectedLocation()
            if (selected == null) {
                status = if (mode == "plus_code") "Enter a valid Plus Code." else "Enter valid latitude and longitude."
                loading = false
            } else fetchAt(selected)
        }

        fun centreOnGps() {
            if (!hasLocationPermission(app)) return
            currentLocation(app, { location ->
                merge(mapOf(
                    "location_source" to "auto",
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "resolved_location_source" to location.source
                ))
                fetchAt(location)
            }) { status = it; loading = false }
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _: Map<String, Boolean> ->
            if (hasLocationPermission(app)) centreOnGps()
            else status = "Location denied. Select Plus Code or latitude/longitude instead."
        }

        fun startRefresh() {
            if (loading) return
            val mode = settings["location_source"].orEmpty().ifBlank { "auto" }
            if (mode != "auto") refresh()
            else if (hasLocationPermission(app)) refresh()
            else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        fun requestGpsCentre() {
            if (loading) return
            if (hasLocationPermission(app)) centreOnGps()
            else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        val lightImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not read selected file.")
                    }
                }.mapCatching(lightRepo::importRegion)
                    .onSuccess { region ->
                        lightCacheCount = lightRepo.cacheCount()
                        status = "Imported ${region.name}. Refreshing dashboard…"
                        startRefresh()
                    }
                    .onFailure { error -> status = "Light-pollution import failed: ${error.message.orEmpty()}" }
            }
        }

        fun downloadLightHeatmap() {
            val location = currentSite
            if (location == null) {
                status = "Refresh the dashboard location before downloading a light-pollution heatmap."
                return
            }
            if (lightBusy) return
            lightBusy = true
            status = "Downloading 50 km NASA nighttime-radiance heatmap…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        LightPollutionNetwork.downloadAndCache(lightRepo, location.latitude, location.longitude, 50.0)
                    }
                }.onSuccess { region ->
                    lightBusy = false
                    lightCacheCount = lightRepo.cacheCount()
                    activeLightRaster = region
                    lightHeatmap = lightRepo.heatmapBitmap(region)
                    status = "NASA ${region.datasetDate} heatmap cached. Refreshing dashboard…"
                    fetchAt(location)
                }.onFailure { error ->
                    lightBusy = false
                    status = "Light-pollution download failed: ${error.message.orEmpty()}"
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!attempted) {
                attempted = true
                startRefresh()
            }
        }

        // Dashboard-style capabilities stay in their live presentation for native
        // dashboard use and for native preset runs. External/ODK invocation still
        // receives the normal automatic structured return.
        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun
        val scaffoldResult = if (keepLiveDashboard) null else result
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { mapOf(DashboardFields.RESULT to OutputFormatter.fields(it, false)[DashboardFields.RESULT]?.toString().orEmpty()) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; values = emptyMap(); startRefresh() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            DashboardBody(
                values = values,
                status = status,
                loading = loading,
                locationMode = settings["location_source"].orEmpty().ifBlank { "auto" },
                plusCode = settings["plus_code"].orEmpty(),
                latitude = settings["latitude"].orEmpty(),
                longitude = settings["longitude"].orEmpty(),
                onLocationModeChange = { mode -> merge(mapOf("location_source" to mode)) },
                onPlusCodeChange = { value -> merge(mapOf("plus_code" to value)) },
                onLatitudeChange = { value -> merge(mapOf("latitude" to value)) },
                onLongitudeChange = { value -> merge(mapOf("longitude" to value)) },
                onCentreGps = ::requestGpsCentre,
                onRefresh = ::startRefresh,
                lightRegionCount = lightCacheCount,
                lightRaster = activeLightRaster,
                lightHeatmap = lightHeatmap,
                lightBusy = lightBusy,
                onDownloadLightRegion = ::downloadLightHeatmap,
                onImportLightRegion = { lightImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onUseSnapshot = if (keepLiveDashboard && result != null) ({ onConfirmed(result!!) }) else null,
                snapshotButtonLabel = if (context.isNativePresetRun) "Finish" else "Use this snapshot"
            )
        }
    }
}

private data class DashboardRefreshBundle(
    val live: AstronomyDashboardLivePayload,
    val lookup: LightPollutionRepository.Lookup?,
    val raster: LightPollutionRepository.RasterRegion?,
    val heatmap: Bitmap?
)

@Composable
private fun DashboardBody(
    values: Map<String,String>,
    status: String,
    loading: Boolean,
    locationMode: String,
    plusCode: String,
    latitude: String,
    longitude: String,
    onLocationModeChange: (String) -> Unit,
    onPlusCodeChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onCentreGps: () -> Unit,
    onRefresh: () -> Unit,
    lightRegionCount: Int,
    lightRaster: LightPollutionRepository.RasterRegion?,
    lightHeatmap: Bitmap?,
    lightBusy: Boolean,
    onDownloadLightRegion: () -> Unit,
    onImportLightRegion: () -> Unit,
    onUseSnapshot: (() -> Unit)?,
    snapshotButtonLabel: String
) {
    val overall = values[DashboardFields.OVERALL_LABEL].orEmpty().ifBlank { if (loading) "UPDATING" else "—" }
    val score = values[DashboardFields.OVERALL_SCORE]?.toIntOrNull()
    val source = values[DashboardFields.LOCATION_SOURCE].orEmpty()
    val updated = displayTime(values[DashboardFields.RETRIEVED_TIME].orEmpty())

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = verdictContainer(overall)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OBSERVING NOW", style = MaterialTheme.typography.labelLarge)
                    Text(overall, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(score?.let { "$it / 100" } ?: status, style = MaterialTheme.typography.titleMedium)
                }
                Button(onClick = onRefresh, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else Text("↻  Refresh")
                }
            }
            if (source.isNotBlank() || updated.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(listOf(source, updated.takeIf { it.isNotBlank() }?.let { "updated $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    DashboardLocationControls(
        mode = locationMode,
        plusCode = plusCode,
        latitude = latitude,
        longitude = longitude,
        loading = loading,
        onModeChange = onLocationModeChange,
        onPlusCodeChange = onPlusCodeChange,
        onLatitudeChange = onLatitudeChange,
        onLongitudeChange = onLongitudeChange,
        onCentreGps = onCentreGps
    )

    DashboardSection("Right now") {
        MetricPair("Cloud", value(values, DashboardFields.CLOUD, "%"), "Visibility", km(values[DashboardFields.VISIBILITY]))
        ProgressMetric("Clear sky", values[DashboardFields.CLOUD]?.toFloatOrNull()?.div(100f), inverse = true)
        MetricPair("Wind", value(values, DashboardFields.WIND, " m/s"), "Gust", value(values, DashboardFields.GUST, " m/s"))
        MetricPair("Temperature", value(values, DashboardFields.TEMPERATURE, " °C"), "Humidity", value(values, DashboardFields.HUMIDITY, "%"))
        MetricPair("AOD", values[DashboardFields.AOD].orDash(), "PM2.5", value(values, DashboardFields.PM25, " µg/m³"))
    }

    DashboardSection("Dew") {
        MetricPair("Dew point", value(values, DashboardFields.DEW_POINT, " °C"), "Margin", value(values, DashboardFields.DEW_MARGIN, " °C"))
        VerdictLine("Risk", values[DashboardFields.DEW_RISK].orDash())
    }

    DashboardSection("Site darkness") {
        VerdictLine("Night-light proxy", values[DashboardFields.LIGHT_LABEL].orDash())
        MetricPair(
            "VIIRS radiance",
            value(values, DashboardFields.LIGHT_RADIANCE, " nW/(cm² sr)"),
            "Dataset date",
            values[DashboardFields.LIGHT_DATASET_DATE].orDash()
        )
        val dataset = values[DashboardFields.LIGHT_DATASET].orEmpty()
        val lat = values[DashboardFields.LATITUDE]?.toDoubleOrNull()
        val lon = values[DashboardFields.LONGITUDE]?.toDoubleOrNull()
        if (lightRaster != null && lightHeatmap != null && lat != null && lon != null) {
            LightPollutionMap(
                latitude = lat,
                longitude = lon,
                radiusKm = lightRaster.radiusKm,
                region = lightRaster,
                heatmap = lightHeatmap,
                basemapMode = "auto",
                opacity = 0.55f,
                height = 180.dp
            )
            Text("NASA VIIRS nighttime at-sensor radiance. This is a light-pollution proxy, not Bortle or observed zenith sky brightness.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                if (lightRegionCount == 0)
                    "No light-pollution heatmaps are cached yet."
                else
                    "$lightRegionCount cached light dataset${if (lightRegionCount == 1) "" else "s"}, but none covers this selected site.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(onClick = onDownloadLightRegion, enabled = !lightBusy && lat != null && lon != null, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (lightBusy) CircularProgressIndicator(strokeWidth = 2.dp) else Text(if (dataset.isBlank()) "Download 50 km heatmap" else "Refresh 50 km heatmap")
        }
        OutlinedButton(onClick = onImportLightRegion, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Import legacy regional data")
        }
    }

    DashboardSection("High atmosphere") {
        VerdictLine("Planetary / high-resolution", values[DashboardFields.PLANETARY_LABEL].orDash())
        MetricPair("300 hPa · 9.2 km", value(values, DashboardFields.JET_300, " m/s"), "250 hPa · 10.4 km", value(values, DashboardFields.JET_250, " m/s"))
        MetricPair("200 hPa · 11.8 km", value(values, DashboardFields.JET_200, " m/s"), "Upper atmosphere", values[DashboardFields.JET_LABEL].orDash())
        Text("Planning proxy from GFS upper-level wind; it is not a direct seeing measurement.", style = MaterialTheme.typography.bodySmall)
    }

    DashboardSection("Moon & tonight") {
        MetricPair("Moon altitude", value(values, DashboardFields.MOON_ALT, "°"), "Illuminated", value(values, DashboardFields.MOON_ILL, "%"))
        VerdictLine("Deep sky", values[DashboardFields.DEEP_SKY_LABEL].orDash())
        val start = shortDateTime(values[DashboardFields.WINDOW_START].orEmpty())
        val end = shortDateTime(values[DashboardFields.WINDOW_END].orEmpty())
        VerdictLine("Best window", if (start.isBlank()) "—" else if (end.isBlank()) start else "$start → $end")
        CloudForecastStrip(values[DashboardFields.FORECAST_CLOUD].orEmpty())
    }

    if (onUseSnapshot != null) {
        Spacer(Modifier.height(2.dp))
        Button(onClick = onUseSnapshot, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(snapshotButtonLabel) }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        listOfNotNull(
            values[DashboardFields.FROM_CACHE]?.takeIf { it == "true" }?.let { "cached source" },
            values[DashboardFields.DATA_AGE_HOURS]?.toDoubleOrNull()?.let { "oldest source %.1f h".format(Locale.US, it) }
        ).joinToString(" · ").ifBlank { status },
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun DashboardLocationControls(
    mode: String,
    plusCode: String,
    latitude: String,
    longitude: String,
    loading: Boolean,
    onModeChange: (String) -> Unit,
    onPlusCodeChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onCentreGps: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = when (mode) { "plus_code" -> "Plus Code"; "manual" -> "Latitude / longitude"; else -> "Current GPS" }
    DashboardSection("Location") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label  ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Current GPS") }, onClick = { expanded = false; onModeChange("auto") })
                    DropdownMenuItem(text = { Text("Plus Code") }, onClick = { expanded = false; onModeChange("plus_code") })
                    DropdownMenuItem(text = { Text("Latitude / longitude") }, onClick = { expanded = false; onModeChange("manual") })
                }
            }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = onCentreGps, enabled = !loading) { Text("◎ GPS") }
        }
        when (mode) {
            "plus_code" -> OutlinedTextField(value = plusCode, onValueChange = onPlusCodeChange, label = { Text("Plus Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            "manual" -> {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = latitude, onValueChange = onLatitudeChange, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.padding(3.dp))
                    OutlinedTextField(value = longitude, onValueChange = onLongitudeChange, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            else -> Text("Refresh uses a fresh GPS fix. The GPS button also switches back to Current GPS and recentres immediately.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DashboardSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricPair(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        MetricTile(leftLabel, leftValue, Modifier.weight(1f))
        Spacer(Modifier.padding(3.dp))
        MetricTile(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VerdictLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressMetric(label: String, progress: Float?, inverse: Boolean = false) {
    if (progress == null) return
    val p = progress.coerceIn(0f, 1f)
    Text(label, style = MaterialTheme.typography.labelSmall)
    LinearProgressIndicator(progress = { if (inverse) 1f - p else p }, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
}

@Composable
private fun CloudForecastStrip(json: String) {
    val values = remember(json) {
        runCatching {
            val a = JSONArray(json.ifBlank { "[]" })
            (0 until a.length()).mapNotNull { a.optDouble(it, Double.NaN).takeIf(Double::isFinite) }
        }.getOrDefault(emptyList())
    }
    if (values.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text("Cloud next ${values.size} h", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { cloud ->
            val h = (5.0 + 27.0 * (cloud.coerceIn(0.0, 100.0) / 100.0)).dp
            Box(Modifier.weight(1f).height(h).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
        }
    }
    Text(values.joinToString("  ") { "${it.toInt()}%" }, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun verdictContainer(label: String) = when (label) {
    "EXCELLENT", "GOOD" -> MaterialTheme.colorScheme.primaryContainer
    "MARGINAL" -> MaterialTheme.colorScheme.secondaryContainer
    "POOR", "VERY POOR" -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun value(values: Map<String,String>, key: String, suffix: String): String = values[key]?.takeIf { it.isNotBlank() }?.let { "$it$suffix" } ?: "—"
private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "—"
private fun km(meters: String?): String = meters?.toDoubleOrNull()?.let { "%.1f km".format(Locale.US, it / 1000.0) } ?: "—"
private fun displayTime(iso: String): String = runCatching { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso)) }.getOrDefault("")
private fun shortDateTime(iso: String): String = runCatching { DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso)) }.getOrDefault(iso.takeIf { it.isNotBlank() }.orEmpty())
