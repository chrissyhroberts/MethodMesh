package com.example.methodmesh.modules.aviation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

object AviationDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AviationDashboardMethod.ID
    override val title = "Aviation dashboard"
    override val description = "GPS-aware aviation snapshot with nearby airfields, runway context and optional flight-condition calculations."
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

        var radiusNm by rememberSaveable { mutableStateOf(initial.aviationValue("radius_nm") ?: "25") }
        var airfieldTypes by rememberSaveable {
            mutableStateOf(initial.aviationValue("airfield_types") ?: "large_airport|medium_airport|small_airport")
        }
        var scheduledOnly by rememberSaveable {
            mutableStateOf(initial.aviationValue("scheduled_only")?.toBooleanStrictOrNull() ?: false)
        }
        var maxResults by rememberSaveable { mutableStateOf(initial.aviationValue("max_results") ?: "10") }
        var refreshPolicy by rememberSaveable { mutableStateOf(initial.aviationValue("refresh_policy") ?: "refresh_if_stale") }
        var conditionsEnabled by rememberSaveable {
            mutableStateOf(initial.aviationValue("conditions_enabled")?.toBooleanStrictOrNull() ?: false)
        }
        var windDirection by rememberSaveable { mutableStateOf(initial.aviationValue("wind_direction_deg", "wind_direction") ?: "") }
        var windSpeed by rememberSaveable { mutableStateOf(initial.aviationValue("wind_speed_kt", "wind_speed") ?: "") }
        var includeGust by rememberSaveable {
            mutableStateOf(initial.aviationValue("include_gust")?.toBooleanStrictOrNull() ?: false)
        }
        var gustSpeed by rememberSaveable { mutableStateOf(initial.aviationValue("gust_speed_kt", "wind_gust_kt") ?: "") }
        var qnhUnit by rememberSaveable { mutableStateOf(initial.aviationValue("qnh_unit") ?: "hpa") }
        var qnh by rememberSaveable { mutableStateOf(initial.aviationValue("qnh_value", "qnh_hpa", "qnh_inhg") ?: "1013.25") }
        var oat by rememberSaveable { mutableStateOf(initial.aviationValue("oat_c", "temperature_c") ?: "15") }

        var selectedAirfieldIdent by rememberSaveable {
            mutableStateOf(initial.aviationValue("airfield_ident", "selected_airfield_ident") ?: "")
        }
        var selectedRunwayIdent by rememberSaveable {
            mutableStateOf(initial.aviationValue("runway_ident", "selected_runway_ident") ?: "")
        }

        var searchResult by remember { mutableStateOf<AviationAirfieldRepository.SearchResult?>(null) }
        var runwayResult by remember { mutableStateOf<AviationAirfieldRepository.RunwayResult?>(null) }
        var telemetry by remember { mutableStateOf(AviationDashboardTelemetry()) }
        var queryLatitude by remember { mutableStateOf<Double?>(null) }
        var queryLongitude by remember { mutableStateOf<Double?>(null) }
        var locationSource by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var loading by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready to refresh local aviation context.") }
        var attempted by remember { mutableStateOf(false) }

        fun currentSettings(): Map<String, String> = linkedMapOf(
            "radius_nm" to radiusNm,
            "airfield_types" to airfieldTypes,
            "scheduled_only" to scheduledOnly.toString(),
            "max_results" to maxResults,
            "refresh_policy" to refreshPolicy,
            "conditions_enabled" to conditionsEnabled.toString(),
            "wind_direction_deg" to windDirection,
            "wind_speed_kt" to windSpeed,
            "include_gust" to includeGust.toString(),
            "gust_speed_kt" to if (includeGust) gustSpeed else "",
            "qnh_unit" to qnhUnit,
            "qnh_value" to qnh,
            "oat_c" to oat
        )

        LaunchedEffect(
            radiusNm, airfieldTypes, scheduledOnly, maxResults, refreshPolicy,
            conditionsEnabled, windDirection, windSpeed, includeGust, gustSpeed, qnhUnit, qnh, oat
        ) {
            context.onSettingsChanged(currentSettings())
        }

        fun selectedAirfield(): AviationAirfieldRepository.Airfield? {
            val search = searchResult ?: return null
            return search.airfields.firstOrNull { it.ident == selectedAirfieldIdent } ?: search.airfields.firstOrNull()
        }

        fun selectedRunway(): AviationAirfieldRepository.RunwayEnd? {
            val runway = runwayResult ?: return null
            return runway.runwayEnds.firstOrNull { it.ident == selectedRunwayIdent } ?: runway.runwayEnds.firstOrNull()
        }

        fun requestFor(settings: Map<String, String>) = As100AviationDashboardMethod.request(
            action = As100AviationDashboardMethod.ID,
            context = context.request.invocationContext.asMap(As100AviationDashboardMethod.ID) + context.action.settings + settings,
            signals = emptyList(),
            inputs = emptyList()
        )

        fun rebuildSnapshot() {
            val search = searchResult ?: return
            val latitude = queryLatitude ?: return
            val longitude = queryLongitude ?: return
            val selectedField = selectedAirfield()
            val selectedRunway = selectedRunway()
            val settings = currentSettings() + mapOf(
                "airfield_ident" to selectedField?.ident.orEmpty(),
                "runway_ident" to selectedRunway?.ident.orEmpty(),
                "runway_heading_deg" to selectedRunway?.headingDeg?.toString().orEmpty()
            )
            val calculated = As100AviationDashboardMethod.values(
                search = search,
                runwayResult = runwayResult,
                selectedAirfield = selectedField,
                selectedRunway = selectedRunway,
                latitude = latitude,
                longitude = longitude,
                locationSource = locationSource,
                telemetry = telemetry,
                settings = settings
            )
            values = calculated
            result = As100AviationDashboardMethod.result(requestFor(settings), calculated, context.request.invocationContext)
            status = calculated[AviationDashboardFields.WARNING].orEmpty().ifBlank {
                "Updated ${calculated[AviationDashboardFields.SNAPSHOT_TIME_ISO].orEmpty()}"
            }
        }

        fun loadRunwaysForSelected() {
            val field = selectedAirfield() ?: run {
                runwayResult = null
                selectedRunwayIdent = ""
                rebuildSnapshot()
                return
            }
            loading = true
            scope.launch {
                val repository = AviationAirfieldRepository(File(androidContext.filesDir, "aviation"))
                val resultForRunways = withContext(Dispatchers.IO) {
                    runCatching {
                        val policy = if (repository.hasCachedRunwayCatalog()) "cache_only" else refreshPolicy
                        repository.runwaysFor(field.ident, policy)
                    }.getOrElse { error ->
                        AviationAirfieldRepository.RunwayResult(
                            runwayEnds = emptyList(),
                            sourceUrl = AviationAirfieldRepository.DEFAULT_RUNWAY_SOURCE_URL,
                            catalogUpdatedIso = "",
                            refreshed = false,
                            staleCacheFallback = false,
                            warning = "Runway catalogue unavailable: ${error.message.orEmpty()}".trim()
                        )
                    }
                }
                runwayResult = resultForRunways
                if (resultForRunways.runwayEnds.none { it.ident == selectedRunwayIdent }) {
                    selectedRunwayIdent = resultForRunways.runwayEnds.firstOrNull()?.ident.orEmpty()
                }
                loading = false
                rebuildSnapshot()
            }
        }

        fun executeAt(latitude: Double, longitude: Double, source: String, liveTelemetry: AviationDashboardTelemetry) {
            loading = true
            status = if (source == "gps") "Reading local airfield and runway context…" else "Using supplied position…"
            queryLatitude = latitude
            queryLongitude = longitude
            locationSource = source
            telemetry = liveTelemetry
            val settings = currentSettings()
            scope.launch {
                val repository = AviationAirfieldRepository(File(androidContext.filesDir, "aviation"))
                val searchOutcome = withContext(Dispatchers.IO) {
                    runCatching {
                        repository.searchNearest(
                            latitude = latitude,
                            longitude = longitude,
                            radiusNm = settings["radius_nm"]?.toDoubleOrNull() ?: 25.0,
                            allowedTypes = settings["airfield_types"].orEmpty().split('|').filter(String::isNotBlank).toSet(),
                            scheduledOnly = settings["scheduled_only"].toBoolean(),
                            maximumResults = settings["max_results"]?.toIntOrNull()?.coerceIn(1, 50) ?: 10,
                            refreshPolicy = settings["refresh_policy"] ?: "refresh_if_stale"
                        )
                    }
                }
                val search = searchOutcome.getOrElse { error ->
                    loading = false
                    status = error.message ?: "Could not load nearby airfields."
                    values = As100AviationDashboardMethod.failure(status)
                    result = As100AviationDashboardMethod.result(requestFor(settings), values, context.request.invocationContext)
                    return@launch
                }
                searchResult = search
                if (search.airfields.none { it.ident == selectedAirfieldIdent }) {
                    selectedAirfieldIdent = search.airfields.firstOrNull()?.ident.orEmpty()
                }
                val selected = search.airfields.firstOrNull { it.ident == selectedAirfieldIdent }
                val runway = if (selected != null) {
                    withContext(Dispatchers.IO) {
                        runCatching { repository.runwaysFor(selected.ident, settings["refresh_policy"] ?: "refresh_if_stale") }
                            .getOrElse { error ->
                                AviationAirfieldRepository.RunwayResult(
                                    runwayEnds = emptyList(),
                                    sourceUrl = AviationAirfieldRepository.DEFAULT_RUNWAY_SOURCE_URL,
                                    catalogUpdatedIso = "",
                                    refreshed = false,
                                    staleCacheFallback = false,
                                    warning = "Runway catalogue unavailable: ${error.message.orEmpty()}".trim()
                                )
                            }
                    }
                } else null
                runwayResult = runway
                if (runway?.runwayEnds?.none { it.ident == selectedRunwayIdent } != false) {
                    selectedRunwayIdent = runway?.runwayEnds?.firstOrNull()?.ident.orEmpty()
                }
                loading = false
                rebuildSnapshot()
            }
        }

        fun suppliedCoordinates(): Pair<Double, Double>? {
            val latitude = initial.aviationValue("latitude", "airfield_query_latitude", AviationDashboardFields.QUERY_LATITUDE)?.toDoubleOrNull()
            val longitude = initial.aviationValue("longitude", "airfield_query_longitude", AviationDashboardFields.QUERY_LONGITUDE)?.toDoubleOrNull()
            return if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) latitude to longitude else null
        }

        fun suppliedTelemetry(): AviationDashboardTelemetry = AviationDashboardTelemetry(
            accuracyM = initial.aviationValue("location_accuracy_m", AviationDashboardFields.LOCATION_ACCURACY_M)?.toDoubleOrNull(),
            gpsAltitudeFt = initial.aviationValue("gps_altitude_ft", AviationDashboardFields.GPS_ALTITUDE_FT)?.toDoubleOrNull(),
            groundSpeedKt = initial.aviationValue("ground_speed_kt", "groundspeed_kt", AviationDashboardFields.GROUND_SPEED_KT)?.toDoubleOrNull(),
            trackDeg = initial.aviationValue("track_deg", "course_deg", AviationDashboardFields.TRACK_DEG)?.toDoubleOrNull()
        )

        @SuppressLint("MissingPermission")
        fun locateAndRefresh() {
            val fine = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                loading = false
                status = "Location permission is required when coordinates are not supplied."
                return
            }
            loading = true
            status = "Getting current GPS position…"
            val client = LocationServices.getFusedLocationProviderClient(androidContext)
            val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            client.getCurrentLocation(priority, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        loading = false
                        status = "No current location fix is available."
                    } else {
                        val liveTelemetry = AviationDashboardTelemetry(
                            accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                            gpsAltitudeFt = if (location.hasAltitude()) location.altitude * 3.280839895 else null,
                            groundSpeedKt = if (location.hasSpeed()) location.speed * 1.943844492 else null,
                            trackDeg = if (location.hasBearing()) location.bearing.toDouble() else null
                        )
                        executeAt(location.latitude, location.longitude, "gps", liveTelemetry)
                    }
                }
                .addOnFailureListener { error ->
                    loading = false
                    status = error.message ?: "Could not obtain current location."
                }
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
            if (resultMap.values.any { it }) locateAndRefresh() else {
                loading = false
                status = "Location permission was denied. Supply coordinates from an earlier MethodMesh step or XLSForm instead."
            }
        }

        fun refresh() {
            if (loading) return
            val supplied = suppliedCoordinates()
            if (supplied != null) {
                executeAt(supplied.first, supplied.second, "supplied", suppliedTelemetry())
                return
            }
            val fine = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) locateAndRefresh() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        LaunchedEffect(Unit) {
            if (!attempted) {
                attempted = true
                refresh()
            }
        }

        LaunchedEffect(
            conditionsEnabled, windDirection, windSpeed, includeGust, gustSpeed, qnhUnit, qnh, oat,
            selectedRunwayIdent
        ) {
            if (searchResult != null && !loading) rebuildSnapshot()
        }

        val internalIntentTest = context.request.source.equals("intent_test", ignoreCase = true)
        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard ||
                context.isNativePresetRun ||
                internalIntentTest
        val canUseSnapshot = result?.status == TransformationStatus.Succeeded && values[AviationDashboardFields.STATUS] == "succeeded"

        LaunchedEffect(context.completionMode, result?.request?.id?.value, keepLiveDashboard) {
            val captured = result
            if (!keepLiveDashboard && captured != null && context.completionMode == CapabilityCompletionMode.AutomaticReturn) {
                onConfirmed(captured)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AviationImmersiveTopBar(
                title = "Aviation dashboard",
                subtitle = if (loading) "Refreshing local context…" else "Flight deck",
                canGoBack = context.stepNumber > 1,
                onBack = onBack,
                onExit = onCancel,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                accentColor = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                AviationDashboardBody(
                    values = values,
                    searchResult = searchResult,
                    runwayResult = runwayResult,
                    selectedAirfieldIdent = selectedAirfieldIdent,
                    selectedRunwayIdent = selectedRunwayIdent,
                    loading = loading,
                    status = status,
                    conditionsEnabled = conditionsEnabled,
                    windDirection = windDirection,
                    windSpeed = windSpeed,
                    includeGust = includeGust,
                    gustSpeed = gustSpeed,
                    qnhUnit = qnhUnit,
                    qnh = qnh,
                    oat = oat,
                    onRefresh = ::refresh,
                    onSelectAirfield = { ident ->
                        if (ident != selectedAirfieldIdent) {
                            selectedAirfieldIdent = ident
                            selectedRunwayIdent = ""
                            loadRunwaysForSelected()
                        }
                    },
                    onSelectRunway = { ident -> selectedRunwayIdent = ident },
                    onConditionsEnabled = { conditionsEnabled = it },
                    onWindDirection = { windDirection = it.dashboardIntegerText() },
                    onWindSpeed = { windSpeed = it.dashboardDecimalText(false) },
                    onIncludeGust = { includeGust = it },
                    onGustSpeed = { gustSpeed = it.dashboardDecimalText(false) },
                    onQnhUnit = { selected ->
                        if (selected != qnhUnit) {
                            val numeric = qnh.toDoubleOrNull()
                            qnh = if (numeric == null) "" else if (selected == "inhg" && qnhUnit == "hpa") {
                                AviationCalculations.format(numeric / 33.8638866667, 2)
                            } else if (selected == "hpa" && qnhUnit == "inhg") {
                                AviationCalculations.format(numeric * 33.8638866667, 2)
                            } else qnh
                            qnhUnit = selected
                        }
                    },
                    onQnh = { qnh = it.dashboardDecimalText(false) },
                    onOat = { oat = it.dashboardDecimalText(true) },
                    onUseSnapshot = null,
                    snapshotButtonLabel = ""
                )
            }

            if (keepLiveDashboard && canUseSnapshot) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Button(
                        onClick = { result?.let(onConfirmed) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
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

@Composable
private fun AviationDashboardBody(
    values: Map<String, String>,
    searchResult: AviationAirfieldRepository.SearchResult?,
    runwayResult: AviationAirfieldRepository.RunwayResult?,
    selectedAirfieldIdent: String,
    selectedRunwayIdent: String,
    loading: Boolean,
    status: String,
    conditionsEnabled: Boolean,
    windDirection: String,
    windSpeed: String,
    includeGust: Boolean,
    gustSpeed: String,
    qnhUnit: String,
    qnh: String,
    oat: String,
    onRefresh: () -> Unit,
    onSelectAirfield: (String) -> Unit,
    onSelectRunway: (String) -> Unit,
    onConditionsEnabled: (Boolean) -> Unit,
    onWindDirection: (String) -> Unit,
    onWindSpeed: (String) -> Unit,
    onIncludeGust: (Boolean) -> Unit,
    onGustSpeed: (String) -> Unit,
    onQnhUnit: (String) -> Unit,
    onQnh: (String) -> Unit,
    onOat: (String) -> Unit,
    onUseSnapshot: (() -> Unit)?,
    snapshotButtonLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("FLIGHT DECK", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (loading) "Refreshing local context…" else "Local aviation snapshot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "Refreshing" else "Refresh")
            }
        }

        AviationAirfieldHero(values)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AviationMetricCard(
                title = "GROUND",
                primary = values[AviationDashboardFields.GROUND_SPEED_KT]?.takeIf(String::isNotBlank)?.let { "$it kt" } ?: "—",
                secondary = values[AviationDashboardFields.TRACK_DEG]?.takeIf(String::isNotBlank)?.let { "TRK ${it}°" } ?: "Track unavailable",
                modifier = Modifier.weight(1f)
            )
            AviationMetricCard(
                title = "POSITION",
                primary = values[AviationDashboardFields.LOCATION_ACCURACY_M]?.takeIf(String::isNotBlank)?.let { "±$it m" } ?: "GPS",
                secondary = values[AviationDashboardFields.GPS_ALTITUDE_FT]?.takeIf(String::isNotBlank)?.let { "GNSS ALT $it ft" } ?: values[AviationDashboardFields.LOCATION_SOURCE].orEmpty().uppercase().ifBlank { "Waiting" },
                modifier = Modifier.weight(1f)
            )
        }

        if (!searchResult?.airfields.isNullOrEmpty()) {
            Text("Nearby airfields", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                searchResult?.airfields.orEmpty().forEach { airfield ->
                    val selected = airfield.ident == selectedAirfieldIdent || (selectedAirfieldIdent.isBlank() && airfield == searchResult?.airfields?.firstOrNull())
                    Surface(
                        modifier = Modifier.widthIn(min = 155.dp).clickable { onSelectAirfield(airfield.ident) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = if (selected) 4.dp else 1.dp
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(airfield.ident.ifBlank { "AIRFIELD" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(airfield.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Spacer(Modifier.height(6.dp))
                            Text("${AviationCalculations.format(airfield.distanceNm, 1)} NM · ${AviationCalculations.format(airfield.bearingDeg, 0)}°", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        AviationRunwayCard(values, runwayResult, selectedRunwayIdent, onSelectRunway)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = conditionsEnabled, onCheckedChange = onConditionsEnabled)
                    Column {
                        Text("Current conditions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Enable to calculate runway wind and density altitude.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                AnimatedVisibility(visible = conditionsEnabled) {
                    Column {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Wind", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            DashboardNumberField("From (°)", windDirection, Modifier.weight(1f), onWindDirection)
                            DashboardNumberField("Speed (kt)", windSpeed, Modifier.weight(1f), onWindSpeed)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeGust, onCheckedChange = onIncludeGust)
                            Text("Include gust")
                        }
                        if (includeGust) DashboardNumberField("Gust (kt)", gustSpeed, Modifier.fillMaxWidth(), onGustSpeed)

                        val windValue = values[AviationDashboardFields.WIND_VALUE].orEmpty()
                        if (windValue.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(windValue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                AviationMetricCard("HEAD", values[AviationDashboardFields.HEADWIND_KT]?.takeIf(String::isNotBlank)?.let { "$it kt" } ?: "0 kt", "Headwind", Modifier.weight(1f))
                                AviationMetricCard("CROSS", values[AviationDashboardFields.CROSSWIND_KT]?.takeIf(String::isNotBlank)?.let { "$it kt" } ?: "0 kt", values[AviationDashboardFields.CROSSWIND_SIDE]?.let { "from $it" }.orEmpty(), Modifier.weight(1f))
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Text("Atmosphere", style = MaterialTheme.typography.labelLarge)
                        DashboardChoiceField("QNH unit", qnhUnit, listOf("hpa" to "hPa / mbar", "inhg" to "inHg"), onQnhUnit)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            DashboardNumberField(if (qnhUnit == "inhg") "QNH (inHg)" else "QNH (hPa)", qnh, Modifier.weight(1f), onQnh)
                            DashboardNumberField("OAT (°C)", oat, Modifier.weight(1f), onOat)
                        }
                        val density = values[AviationDashboardFields.DENSITY_ALTITUDE_FT].orEmpty()
                        if (density.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                AviationMetricCard("PRESS ALT", values[AviationDashboardFields.PRESSURE_ALTITUDE_FT]?.let { "$it ft" }.orEmpty(), "Pressure altitude", Modifier.weight(1f))
                                AviationMetricCard("DENS ALT", "$density ft", "Density altitude", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        val warning = values[AviationDashboardFields.WARNING].orEmpty()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Reference data", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (warning.isBlank()) "OurAirports catalogue · local distance/bearing calculations · ${values[AviationDashboardFields.SNAPSHOT_TIME_ISO].orEmpty()}" else warning,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Planning/reference aid only — verify operational information against official AIS/AIP, current weather/NOTAMs and the applicable AFM/POH.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Text(status, style = MaterialTheme.typography.bodySmall)

        if (onUseSnapshot != null) {
            Button(onClick = onUseSnapshot, modifier = Modifier.fillMaxWidth()) { Text(snapshotButtonLabel) }
        }
    }
}

@Composable
private fun AviationAirfieldHero(values: Map<String, String>) {
    val ident = values[AviationDashboardFields.SELECTED_AIRFIELD_IDENT].orEmpty()
    val name = values[AviationDashboardFields.SELECTED_AIRFIELD_NAME].orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(if (ident.isBlank()) "LOCATING" else ident, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (name.isBlank()) "Nearest airfield" else name, style = MaterialTheme.typography.titleMedium)
                    values[AviationDashboardFields.SELECTED_AIRFIELD_MUNICIPALITY]?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(values[AviationDashboardFields.SELECTED_AIRFIELD_DISTANCE_NM]?.takeIf(String::isNotBlank)?.let { "$it NM" } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(values[AviationDashboardFields.SELECTED_AIRFIELD_BEARING_DEG]?.takeIf(String::isNotBlank)?.let { "BRG $it°" } ?: "Bearing —", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(values[AviationDashboardFields.SELECTED_AIRFIELD_ELEVATION_FT]?.takeIf(String::isNotBlank)?.let { "ELEV $it ft" } ?: "Elevation —", style = MaterialTheme.typography.labelLarge)
                Text(values[AviationDashboardFields.SELECTED_RUNWAY_IDENT]?.takeIf(String::isNotBlank)?.let { "RWY $it" } ?: "Runway —", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AviationMetricCard(title: String, primary: String, secondary: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(primary.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun AviationRunwayCard(
    values: Map<String, String>,
    runwayResult: AviationAirfieldRepository.RunwayResult?,
    selectedRunwayIdent: String,
    onSelectRunway: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Runway", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val ends = runwayResult?.runwayEnds.orEmpty()
            if (ends.isEmpty()) {
                Text("No runway-end data available for the selected airfield.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            } else {
                DashboardChoiceField(
                    label = "Runway end",
                    value = selectedRunwayIdent.ifBlank { ends.first().ident },
                    choices = ends.map { runway -> runway.ident to buildString {
                        append("RWY ${runway.ident}")
                        runway.lengthFt?.let { append(" · ${it.roundToInt()} ft") }
                        if (runway.surface.isNotBlank()) append(" · ${runway.surface}")
                    } },
                    onSelected = onSelectRunway
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AviationMetricCard("HEADING", values[AviationDashboardFields.SELECTED_RUNWAY_HEADING_DEG]?.takeIf(String::isNotBlank)?.let { "$it°" } ?: "—", "Approx. runway heading", Modifier.weight(1f))
                    AviationMetricCard("LENGTH", values[AviationDashboardFields.SELECTED_RUNWAY_LENGTH_FT]?.takeIf(String::isNotBlank)?.let { "$it ft" } ?: "—", values[AviationDashboardFields.SELECTED_RUNWAY_SURFACE].orEmpty().ifBlank { "Surface unknown" }, Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember(value, choices.size) { mutableStateOf(false) }
    val display = choices.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (choice, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelected(choice); expanded = false })
            }
        }
    }
}

@Composable
private fun DashboardNumberField(label: String, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.padding(vertical = 3.dp),
        singleLine = true
    )
}

private fun String.dashboardIntegerText(): String = filter(Char::isDigit).take(3)

private fun String.dashboardDecimalText(allowNegative: Boolean): String {
    val filtered = filter { it.isDigit() || it == '.' || (allowNegative && it == '-') }
    val minus = if (allowNegative && filtered.startsWith('-')) "-" else ""
    val body = filtered.removePrefix("-")
    val parts = body.split('.')
    return minus + parts.first() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
}
