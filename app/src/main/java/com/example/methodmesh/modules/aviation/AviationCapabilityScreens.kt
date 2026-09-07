package com.example.methodmesh.modules.aviation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object NearbyAirfieldsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NearbyAirfieldsMethod.ID
    override val title = "Nearby airfields"
    override val description = "Find nearby aerodromes from GPS or supplied coordinates."

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
        var radiusNm by rememberSaveable { mutableStateOf(initial.aviationValue("radius_nm") ?: "25") }
        var airfieldTypes by rememberSaveable {
            mutableStateOf(initial.aviationValue("airfield_types") ?: "large_airport|medium_airport|small_airport")
        }
        var scheduledOnly by rememberSaveable {
            mutableStateOf(initial.aviationValue("scheduled_only")?.toBooleanStrictOrNull() ?: false)
        }
        var maxResults by rememberSaveable { mutableStateOf(initial.aviationValue("max_results") ?: "10") }
        var refreshPolicy by rememberSaveable { mutableStateOf(initial.aviationValue("refresh_policy") ?: "refresh_if_stale") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var running by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready to find nearby airfields.") }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var liveResult by remember { mutableStateOf<ExecutionResult?>(null) }

        fun currentSettings() = linkedMapOf(
            "radius_nm" to radiusNm,
            "airfield_types" to airfieldTypes,
            "scheduled_only" to scheduledOnly.toString(),
            "max_results" to maxResults,
            "refresh_policy" to refreshPolicy
        )

        LaunchedEffect(radiusNm, airfieldTypes, scheduledOnly, maxResults, refreshPolicy) {
            context.onSettingsChanged(currentSettings())
        }

        fun requestFor(settings: Map<String, String>) = As100NearbyAirfieldsMethod.request(
            action = As100NearbyAirfieldsMethod.ID,
            context = context.request.invocationContext.asMap(As100NearbyAirfieldsMethod.ID) + context.action.settings + settings,
            signals = emptyList(),
            inputs = emptyList()
        )

        val restoredResult = remember(resultValuesJson, context.action.settings) {
            resultValuesJson?.let(::aviationMapFromJson)?.let { values ->
                As100NearbyAirfieldsMethod.result(requestFor(currentSettings()), values, context.request.invocationContext)
            }
        }
        val capturedResult = liveResult ?: restoredResult

        fun store(values: Map<String, String>, execution: ExecutionResult) {
            resultValuesJson = aviationMapToJson(values)
            liveResult = execution
        }

        fun suppliedCoordinates(): Pair<Double, Double>? {
            val latitude = initial.aviationValue("latitude", "airfield_query_latitude")?.toDoubleOrNull()
            val longitude = initial.aviationValue("longitude", "airfield_query_longitude")?.toDoubleOrNull()
            return if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                latitude to longitude
            } else null
        }

        fun executeAt(latitude: Double, longitude: Double, locationSource: String) {
            running = true
            status = if (locationSource == "gps") "Searching around current GPS position…" else "Searching around supplied position…"
            val settings = currentSettings() + mapOf(
                "latitude" to latitude.toString(),
                "longitude" to longitude.toString(),
                "location_source" to locationSource
            )
            scope.launch {
                val values = withContext(Dispatchers.IO) {
                    runCatching {
                        val repository = AviationAirfieldRepository(File(androidContext.filesDir, "aviation"))
                        val search = repository.searchNearest(
                            latitude = latitude,
                            longitude = longitude,
                            radiusNm = radiusNm.toDoubleOrNull() ?: 25.0,
                            allowedTypes = airfieldTypes.split('|').map(String::trim).filter(String::isNotBlank).toSet(),
                            scheduledOnly = scheduledOnly,
                            maximumResults = maxResults.toIntOrNull() ?: 10,
                            refreshPolicy = refreshPolicy
                        )
                        As100NearbyAirfieldsMethod.values(search, latitude, longitude, locationSource)
                    }.getOrElse { error -> As100NearbyAirfieldsMethod.failure(error.message ?: "Airfield search failed.") }
                }
                val execution = As100NearbyAirfieldsMethod.result(requestFor(settings), values, context.request.invocationContext)
                running = false
                status = values[NearbyAirfieldFields.ERROR].orEmpty().ifBlank {
                    val count = values[NearbyAirfieldFields.COUNT] ?: "0"
                    val warning = values[NearbyAirfieldFields.WARNING].orEmpty()
                    "Found $count matching airfield(s).${if (warning.isNotBlank()) " $warning" else ""}"
                }
                if (context.submitsImmediately) onConfirmed(execution) else store(values, execution)
            }
        }

        @SuppressLint("MissingPermission")
        fun locateAndRun() {
            val fine = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                status = "Location permission is required when coordinates are not supplied."
                return
            }
            running = true
            status = "Getting current GPS position…"
            val client = LocationServices.getFusedLocationProviderClient(androidContext)
            val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            client.getCurrentLocation(priority, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        running = false
                        status = "No current location fix is available."
                    } else {
                        executeAt(location.latitude, location.longitude, "gps")
                    }
                }
                .addOnFailureListener { error ->
                    running = false
                    status = error.message ?: "Could not obtain current location."
                }
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) locateAndRun() else {
                running = false
                status = "Location permission was denied. Supply coordinates from an earlier MethodMesh step or XLSForm instead."
            }
        }

        fun runSearch() {
            val supplied = suppliedCoordinates()
            if (supplied != null) {
                executeAt(supplied.first, supplied.second, "supplied")
                return
            }
            val fine = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) locateAndRun() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        LaunchedEffect(context.presentationMode, context.action.settings) {
            val nativePresetNeedsInput = context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()
            if (!launched && context.presentationMode == CapabilityPresentationMode.IntentLaunch && !nativePresetNeedsInput) {
                launched = true
                runSearch()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = capturedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { resultValuesJson = null; liveResult = null; runSearch() },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "Uses supplied/piped coordinates when present; otherwise uses current GPS. Coordinates stay on the device—the remote request only refreshes the public airfield catalogue.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            if (context.settingShouldBeShown("radius_nm")) {
                AviationChoiceField("Radius", radiusNm, listOf("5" to "5 NM", "10" to "10 NM", "25" to "25 NM", "50" to "50 NM", "100" to "100 NM", "200" to "200 NM"), !running) { radiusNm = it }
            }
            if (context.settingShouldBeShown("max_results")) {
                AviationChoiceField("Maximum results", maxResults, listOf("5" to "5", "10" to "10", "20" to "20", "50" to "50"), !running) { maxResults = it }
            }
            if (context.settingShouldBeShown("refresh_policy")) {
                AviationChoiceField(
                    "Airfield catalogue",
                    refreshPolicy,
                    listOf("refresh_if_stale" to "Refresh if older than 24 h", "cache_only" to "Offline / cache only", "force_refresh" to "Refresh now"),
                    !running
                ) { refreshPolicy = it }
            }

            if (context.settingShouldBeShown("airfield_types")) {
                Text("Airfield types", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                val selected = airfieldTypes.split('|').filter(String::isNotBlank).toSet()
                listOf(
                    "large_airport" to "Large airport",
                    "medium_airport" to "Medium airport",
                    "small_airport" to "Small airport",
                    "heliport" to "Heliport",
                    "seaplane_base" to "Seaplane base",
                    "balloonport" to "Balloonport"
                ).forEach { (value, label) ->
                    AviationCheckboxRow(label, value in selected, !running) { checked ->
                        val updated = selected.toMutableSet().apply { if (checked) add(value) else remove(value) }
                        airfieldTypes = updated.joinToString("|")
                    }
                }
            }
            if (context.settingShouldBeShown("scheduled_only")) {
                AviationCheckboxRow("Scheduled-service airfields only", scheduledOnly, !running) { scheduledOnly = it }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { runSearch() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) "Working…" else if (capturedResult == null) "Find nearby airfields" else "Search again")
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

object RunwayWindCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100RunwayWindMethod.ID
    override val title = "Runway wind"
    override val description = "Calculate headwind, tailwind and crosswind components."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val initial = context.action.settings
        var runwayHeading by rememberSaveable { mutableStateOf(initial.aviationValue("runway_heading_deg", "runway_heading") ?: "") }
        var windDirection by rememberSaveable { mutableStateOf(initial.aviationValue("wind_direction_deg", "wind_direction") ?: "") }
        var windSpeed by rememberSaveable { mutableStateOf(initial.aviationValue("wind_speed_kt", "wind_speed") ?: "") }
        var includeGust by rememberSaveable { mutableStateOf(initial.aviationValue("include_gust")?.toBooleanStrictOrNull() ?: (initial.aviationValue("gust_speed_kt")?.toDoubleOrNull()?.let { it > 0 } ?: false)) }
        var gustSpeed by rememberSaveable { mutableStateOf(initial.aviationValue("gust_speed_kt", "wind_gust_kt") ?: "") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var liveResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Ready.") }

        fun settings() = linkedMapOf(
            "runway_heading_deg" to runwayHeading,
            "wind_direction_deg" to windDirection,
            "wind_speed_kt" to windSpeed,
            "include_gust" to includeGust.toString(),
            "gust_speed_kt" to if (includeGust) gustSpeed else ""
        )
        LaunchedEffect(runwayHeading, windDirection, windSpeed, includeGust, gustSpeed) { context.onSettingsChanged(settings()) }
        fun request() = As100RunwayWindMethod.request(As100RunwayWindMethod.ID, context.request.invocationContext.asMap(As100RunwayWindMethod.ID) + context.action.settings + settings(), emptyList(), emptyList())
        val restored = remember(valuesJson, context.action.settings) { valuesJson?.let(::aviationMapFromJson)?.let { As100RunwayWindMethod.result(request(), it, context.request.invocationContext) } }
        val captured = liveResult ?: restored

        fun calculate() {
            val values = As100RunwayWindMethod.generate(settings())
            val execution = As100RunwayWindMethod.result(request(), values, context.request.invocationContext)
            status = values[RunwayWindFields.ERROR].orEmpty().ifBlank { values[RunwayWindFields.VALUE].orEmpty() }
            if (context.submitsImmediately) onConfirmed(execution) else { valuesJson = aviationMapToJson(values); liveResult = execution }
        }
        LaunchedEffect(context.presentationMode, context.action.settings) {
            val nativePresetNeedsInput = context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()
            if (!launched && context.presentationMode == CapabilityPresentationMode.IntentLaunch && !nativePresetNeedsInput) { launched = true; calculate() }
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, captured, captured?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { calculate() }, { captured?.let(onConfirmed) }, onCancel) {
            Text("Wind direction is the direction the wind is FROM. Positive crosswind side is reported from the pilot's perspective looking down the runway.", style = MaterialTheme.typography.bodySmall)
            if (context.settingShouldBeShown("runway_heading_deg")) AviationNumberField("Runway heading (°)", runwayHeading, "0–360") { runwayHeading = it.integerText() }
            if (context.settingShouldBeShown("wind_direction_deg")) AviationNumberField("Wind from (°)", windDirection, "0–360") { windDirection = it.integerText() }
            if (context.settingShouldBeShown("wind_speed_kt")) AviationNumberField("Wind speed (kt)", windSpeed) { windSpeed = it.decimalText(false) }
            if (context.settingShouldBeShown("include_gust")) AviationCheckboxRow("Include gust", includeGust, true) { includeGust = it }
            if (includeGust && context.settingShouldBeShown("gust_speed_kt")) AviationNumberField("Gust speed (kt)", gustSpeed) { gustSpeed = it.decimalText(false) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text(if (captured == null) "Calculate" else "Calculate again") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

object AviationAltitudeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AviationAltitudeMethod.ID
    override val title = "Pressure / density altitude"
    override val description = "Planning calculation from elevation, QNH and outside-air temperature."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val initial = context.action.settings
        var elevation by rememberSaveable { mutableStateOf(initial.aviationValue("field_elevation_ft", "airfield_nearest_elevation_ft", "elevation_ft") ?: "") }
        var qnhUnit by rememberSaveable { mutableStateOf(initial.aviationValue("qnh_unit") ?: if (initial.aviationValue("qnh_inhg") != null) "inhg" else "hpa") }
        var qnh by rememberSaveable {
            mutableStateOf(initial.aviationValue("qnh_value", if (qnhUnit == "inhg") "qnh_inhg" else "qnh_hpa", "pressure_hpa") ?: if (qnhUnit == "inhg") "29.92" else "1013.25")
        }
        var oat by rememberSaveable { mutableStateOf(initial.aviationValue("oat_c", "temperature_c") ?: "15") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var liveResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Ready.") }

        fun settings() = linkedMapOf("field_elevation_ft" to elevation, "qnh_unit" to qnhUnit, "qnh_value" to qnh, "oat_c" to oat)
        LaunchedEffect(elevation, qnhUnit, qnh, oat) { context.onSettingsChanged(settings()) }
        fun request() = As100AviationAltitudeMethod.request(As100AviationAltitudeMethod.ID, context.request.invocationContext.asMap(As100AviationAltitudeMethod.ID) + context.action.settings + settings(), emptyList(), emptyList())
        val restored = remember(valuesJson, context.action.settings) { valuesJson?.let(::aviationMapFromJson)?.let { As100AviationAltitudeMethod.result(request(), it, context.request.invocationContext) } }
        val captured = liveResult ?: restored
        fun calculate() {
            val values = As100AviationAltitudeMethod.generate(settings())
            val execution = As100AviationAltitudeMethod.result(request(), values, context.request.invocationContext)
            status = values[AltitudeFields.ERROR].orEmpty().ifBlank { values[AltitudeFields.VALUE].orEmpty() }
            if (context.submitsImmediately) onConfirmed(execution) else { valuesJson = aviationMapToJson(values); liveResult = execution }
        }
        LaunchedEffect(context.presentationMode, context.action.settings) {
            val nativePresetNeedsInput = context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()
            if (!launched && context.presentationMode == CapabilityPresentationMode.IntentLaunch && !nativePresetNeedsInput) { launched = true; calculate() }
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, captured, captured?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { calculate() }, { captured?.let(onConfirmed) }, onCancel) {
            Text("Planning approximation only. Aircraft take-off/landing performance must come from the applicable AFM/POH.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (context.settingShouldBeShown("field_elevation_ft")) AviationNumberField("Field elevation (ft)", elevation) { elevation = it.decimalText(true) }
            if (context.settingShouldBeShown("qnh_unit")) AviationChoiceField("QNH unit", qnhUnit, listOf("hpa" to "hPa / mbar", "inhg" to "inHg"), true) { selected ->
                if (selected != qnhUnit) {
                    val numeric = qnh.toDoubleOrNull()
                    qnh = if (numeric == null) "" else if (selected == "inhg" && qnhUnit == "hpa") AviationCalculations.format(numeric / 33.8638866667, 2) else if (selected == "hpa" && qnhUnit == "inhg") AviationCalculations.format(numeric * 33.8638866667, 2) else qnh
                    qnhUnit = selected
                }
            }
            if (context.settingShouldBeShown("qnh_value")) AviationNumberField(if (qnhUnit == "inhg") "QNH (inHg)" else "QNH (hPa)", qnh) { qnh = it.decimalText(false) }
            if (context.settingShouldBeShown("oat_c")) AviationNumberField("Outside-air temperature (°C)", oat) { oat = it.decimalText(true) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text(if (captured == null) "Calculate" else "Calculate again") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

object AviationE6bCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AviationE6bMethod.ID
    override val title = "E6B flight calculator"
    override val description = "Time, distance, groundspeed, fuel, endurance and range calculations."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val initial = context.action.settings
        var calculation by rememberSaveable { mutableStateOf(initial.aviationValue("calculation") ?: "time_from_distance_speed") }
        var distance by rememberSaveable { mutableStateOf(initial.aviationValue("distance_nm", "route_distance_nm") ?: "") }
        var speed by rememberSaveable { mutableStateOf(initial.aviationValue("groundspeed_kt", "ground_speed_kt") ?: "") }
        var time by rememberSaveable { mutableStateOf(initial.aviationValue("time_minutes", "ete_minutes") ?: "") }
        var fuelUnit by rememberSaveable { mutableStateOf(initial.aviationValue("fuel_unit") ?: "L") }
        var fuel by rememberSaveable { mutableStateOf(initial.aviationValue("fuel_available") ?: "") }
        var burn by rememberSaveable { mutableStateOf(initial.aviationValue("fuel_burn_per_hour", "fuel_burn_hour") ?: "") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var liveResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Ready.") }

        fun settings() = linkedMapOf("calculation" to calculation, "distance_nm" to distance, "groundspeed_kt" to speed, "time_minutes" to time, "fuel_unit" to fuelUnit, "fuel_available" to fuel, "fuel_burn_per_hour" to burn)
        LaunchedEffect(calculation, distance, speed, time, fuelUnit, fuel, burn) { context.onSettingsChanged(settings()) }
        fun request() = As100AviationE6bMethod.request(As100AviationE6bMethod.ID, context.request.invocationContext.asMap(As100AviationE6bMethod.ID) + context.action.settings + settings(), emptyList(), emptyList())
        val restored = remember(valuesJson, context.action.settings) { valuesJson?.let(::aviationMapFromJson)?.let { As100AviationE6bMethod.result(request(), it, context.request.invocationContext) } }
        val captured = liveResult ?: restored
        fun calculate() {
            val values = As100AviationE6bMethod.generate(settings())
            val execution = As100AviationE6bMethod.result(request(), values, context.request.invocationContext)
            status = values[E6bFields.ERROR].orEmpty().ifBlank { values[E6bFields.VALUE].orEmpty() }
            if (context.submitsImmediately) onConfirmed(execution) else { valuesJson = aviationMapToJson(values); liveResult = execution }
        }
        LaunchedEffect(context.presentationMode, context.action.settings) {
            val nativePresetNeedsInput = context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()
            if (!launched && context.presentationMode == CapabilityPresentationMode.IntentLaunch && !nativePresetNeedsInput) { launched = true; calculate() }
        }

        val needsDistance = calculation in setOf("time_from_distance_speed", "speed_from_distance_time")
        val needsSpeed = calculation in setOf("time_from_distance_speed", "distance_from_speed_time", "range")
        val needsTime = calculation in setOf("distance_from_speed_time", "speed_from_distance_time", "fuel_required")
        val needsFuel = calculation in setOf("endurance", "range")
        val needsBurn = calculation in setOf("fuel_required", "endurance", "range")
        val usesFuel = needsFuel || needsBurn

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, captured, captured?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { calculate() }, { captured?.let(onConfirmed) }, onCancel) {
            if (context.settingShouldBeShown("calculation")) {
                AviationChoiceField(
                    "Calculation", calculation,
                    listOf(
                        "time_from_distance_speed" to "Time from distance + groundspeed",
                        "distance_from_speed_time" to "Distance from groundspeed + time",
                        "speed_from_distance_time" to "Groundspeed from distance + time",
                        "fuel_required" to "Fuel required",
                        "endurance" to "Endurance",
                        "range" to "Range"
                    ), true
                ) { calculation = it }
            }
            if (needsDistance && context.settingShouldBeShown("distance_nm")) AviationNumberField("Distance (NM)", distance) { distance = it.decimalText(false) }
            if (needsSpeed && context.settingShouldBeShown("groundspeed_kt")) AviationNumberField("Groundspeed (kt)", speed) { speed = it.decimalText(false) }
            if (needsTime && context.settingShouldBeShown("time_minutes")) AviationNumberField("Time (minutes)", time) { time = it.decimalText(false) }
            if (usesFuel && context.settingShouldBeShown("fuel_unit")) AviationChoiceField("Fuel unit", fuelUnit, listOf("L" to "Litres", "US gal" to "US gallons", "Imp gal" to "Imperial gallons", "kg" to "Kilograms"), true) { fuelUnit = it }
            if (needsFuel && context.settingShouldBeShown("fuel_available")) AviationNumberField("Fuel available ($fuelUnit)", fuel) { fuel = it.decimalText(false) }
            if (needsBurn && context.settingShouldBeShown("fuel_burn_per_hour")) AviationNumberField("Fuel burn ($fuelUnit/hour)", burn) { burn = it.decimalText(false) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text(if (captured == null) "Calculate" else "Calculate again") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AviationChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val display = choices.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (choice, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelected(choice); expanded = false }) }
        }
    }
}

@Composable
private fun AviationNumberField(label: String, value: String, supporting: String = "", onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = if (supporting.isBlank()) null else ({ Text(supporting) }),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true
    )
}

@Composable
private fun AviationCheckboxRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

private fun String.integerText(): String = filter(Char::isDigit).take(3)

private fun String.decimalText(allowNegative: Boolean): String {
    val filtered = filter { it.isDigit() || it == '.' || (allowNegative && it == '-') }
    val minus = if (allowNegative && filtered.startsWith('-')) "-" else ""
    val body = filtered.removePrefix("-")
    val parts = body.split('.')
    return minus + parts.first() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
}

private fun aviationMapToJson(values: Map<String, String>): String = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value) }
}.toString()

private fun aviationMapFromJson(json: String): Map<String, String> = runCatching {
    val root = JSONObject(json.ifBlank { "{}" })
    buildMap {
        root.keys().forEach { key -> put(key, root.optString(key)) }
    }
}.getOrDefault(emptyMap())
