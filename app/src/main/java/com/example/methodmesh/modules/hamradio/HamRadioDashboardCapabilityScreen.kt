package com.example.methodmesh.modules.hamradio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class HamDashboardLocation(
    val latitude: Double,
    val longitude: Double,
    val locator: String,
    val source: String
)

object HamRadioDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100HamDashboardMethod.ID
    override val title = "Ham radio dashboard"
    override val description = "A refreshable operating dashboard combining QTH, current space weather, HF band guidance and optional PSK Reporter activity."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val app = LocalContext.current
        val scope = rememberCoroutineScope()

        val initial = remember(context.action.settings) {
            context.action.settings.mapKeys { it.key.removePrefix("input_") }
        }
        var settingsJson by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(JSONObject(initial).toString())
        }
        var spaceValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var pskValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var dashboardValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Locating QTH…") }
        var loading by remember { mutableStateOf(false) }
        var attempted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        var showQthEditor by rememberSaveable { mutableStateOf(false) }
        var showCallsignEditor by rememberSaveable { mutableStateOf(false) }
        var qthDraft by rememberSaveable { mutableStateOf(initial["qth_locator"].orEmpty()) }
        var callsignDraft by rememberSaveable { mutableStateOf(initial["callsign"].orEmpty()) }

        fun settings(): Map<String, String> = dashboardMap(settingsJson)

        fun merge(extra: Map<String, String>) {
            val merged = settings().toMutableMap().apply { putAll(extra) }
            settingsJson = JSONObject(merged).toString()
            context.onSettingsChanged(merged)
        }

        fun dashboardRequest(s: Map<String, String>) = As100HamDashboardMethod.request(
            action = capabilityId,
            context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + s,
            signals = emptyList(),
            inputs = emptyList()
        )

        val dashboardValues = remember(dashboardValuesJson) { dashboardMap(dashboardValuesJson) }
        val result: ExecutionResult? = remember(dashboardValuesJson, settingsJson) {
            if (dashboardValues.isEmpty()) null else As100HamDashboardMethod.result(
                request = dashboardRequest(settings()),
                values = dashboardValues,
                invocation = context.request.invocationContext
            )
        }

        fun composeDashboard(
            location: HamDashboardLocation,
            spaceValues: Map<String, String>,
            pskValues: Map<String, String>,
            targetDistance: String = normalizedDashboardDistance(settings()["target_distance_km"]),
            mode: String = settings()["mode"].orEmpty().ifBlank { "mixed" }
        ) {
            val bandValues = As100HamBandAdviceMethod.calculate(
                mapOf(
                    "location_mode" to "coordinates",
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "target_distance_km" to targetDistance,
                    "conditions_source" to "manual",
                    "kp" to spaceValues[HamSpaceWeatherFields.KP].orEmpty().ifBlank { "2" },
                    "f107" to spaceValues[HamSpaceWeatherFields.F107].orEmpty().ifBlank { "100" },
                    "r_scale" to spaceValues[HamSpaceWeatherFields.R].orEmpty().ifBlank { "0" },
                    "mode" to mode
                )
            )

            val extra = mapOf(
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString(),
                "qth_locator" to location.locator,
                "location_source" to location.source,
                "target_distance_km" to targetDistance,
                "mode" to mode,
                "space_weather_values_json" to JSONObject(spaceValues).toString(),
                "band_advice_values_json" to JSONObject(bandValues).toString(),
                "psk_values_json" to if (pskValues.isEmpty()) "" else JSONObject(pskValues).toString(),
                "retrieved_time_iso" to Instant.now().toString()
            )
            merge(extra)
            val calculated = As100HamDashboardMethod.calculate(settings() + extra)
            dashboardValuesJson = JSONObject(calculated).toString()
            status = if (calculated[HamDashboardFields.STATUS] == "succeeded") {
                "Updated"
            } else {
                calculated[HamDashboardFields.ERROR].orEmpty().ifBlank { "Dashboard failed." }
            }
        }

        fun currentLocationFromState(): HamDashboardLocation? {
            val s = settings()
            val lat = dashboardValues[HamDashboardFields.LATITUDE]?.toDoubleOrNull()
                ?: s["latitude"]?.toDoubleOrNull()
            val lon = dashboardValues[HamDashboardFields.LONGITUDE]?.toDoubleOrNull()
                ?: s["longitude"]?.toDoubleOrNull()
            val locator = dashboardValues[HamDashboardFields.QTH_LOCATOR].orEmpty()
                .ifBlank { s["qth_locator"].orEmpty() }
            val source = dashboardValues[HamDashboardFields.LOCATION_SOURCE].orEmpty()
                .ifBlank { s["location_source"].orEmpty().ifBlank { "saved QTH" } }
            return if (lat != null && lon != null && locator.isNotBlank()) {
                HamDashboardLocation(lat, lon, locator, source)
            } else null
        }

        fun recalculate(
            targetDistance: String = normalizedDashboardDistance(settings()["target_distance_km"]),
            mode: String = settings()["mode"].orEmpty().ifBlank { "mixed" }
        ) {
            val location = currentLocationFromState()
            val space = dashboardMap(spaceValuesJson)
            if (location == null || space.isEmpty()) {
                status = "Refresh once to establish QTH and live conditions."
                return
            }
            merge(mapOf("target_distance_km" to targetDistance, "mode" to mode))
            composeDashboard(location, space, dashboardMap(pskValuesJson), targetDistance, mode)
        }

        fun fetchAt(location: HamDashboardLocation) {
            if (loading) return
            loading = true
            status = "Refreshing radio conditions…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val space = As100HamSpaceWeatherMethod.read(
                            mapOf("refresh_mode" to "cache_preferred"),
                            HamRadioRepository.shared
                        )
                        check(space[HamSpaceWeatherFields.STATUS] == "succeeded") {
                            space[HamSpaceWeatherFields.ERROR].orEmpty().ifBlank { "NOAA space weather unavailable." }
                        }

                        val s = settings()
                        val callsign = s["callsign"].orEmpty().trim()
                        val psk = if (callsign.isBlank()) {
                            emptyMap()
                        } else {
                            As100HamPskReporterMethod.read(
                                mapOf(
                                    "subject_type" to "callsign",
                                    "subject" to callsign,
                                    "direction" to s["psk_direction"].orEmpty().ifBlank { "sent" },
                                    "lookback_minutes" to s["psk_lookback_minutes"].orEmpty().ifBlank { "30" },
                                    "mode" to "",
                                    "record_limit" to "100",
                                    "include_no_locator" to "no"
                                ),
                                HamRadioRepository.shared
                            )
                        }
                        space to psk
                    }
                }.onSuccess { (space, psk) ->
                    spaceValuesJson = JSONObject(space).toString()
                    pskValuesJson = if (psk.isEmpty()) "" else JSONObject(psk).toString()
                    composeDashboard(location, space, psk)
                    loading = false
                }.onFailure { error ->
                    loading = false
                    status = "Refresh failed: ${error.message ?: "live data unavailable"}"
                }
            }
        }

        fun resolveAndFetch() {
            explicitDashboardLocation(settings())?.let { fetchAt(it); return }
            currentDashboardLocation(
                context = app,
                ok = ::fetchAt,
                fail = { error ->
                    loading = false
                    status = error
                    showQthEditor = true
                }
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _: Map<String, Boolean> ->
            loading = false
            if (hasDashboardLocationPermission(app)) {
                resolveAndFetch()
            } else {
                status = "Location permission denied. Enter your Maidenhead locator below."
                showQthEditor = true
            }
        }

        fun startRefresh() {
            if (loading) return
            val explicit = explicitDashboardLocation(settings())
            when {
                explicit != null -> fetchAt(explicit)
                hasDashboardLocationPermission(app) -> resolveAndFetch()
                else -> {
                    status = "Requesting location…"
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!attempted) {
                attempted = true
                startRefresh()
            }
        }

        val keepLiveDashboard = context.presentationMode == CapabilityPresentationMode.Dashboard
        val scaffoldResult = if (keepLiveDashboard) null else result

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let {
                mapOf(
                    HamDashboardFields.RESULT to
                        OutputFormatter.fields(it, includeProvenance = false)[HamDashboardFields.RESULT]?.toString().orEmpty()
                )
            }.orEmpty(),
            onBack = onBack,
            onRetry = {
                dashboardValuesJson = ""
                startRefresh()
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            DashboardBody(
                values = dashboardValues,
                settings = settings(),
                status = status,
                loading = loading,
                qthDraft = qthDraft,
                callsignDraft = callsignDraft,
                showQthEditor = showQthEditor,
                showCallsignEditor = showCallsignEditor,
                onRefresh = ::startRefresh,
                onDistance = { recalculate(targetDistance = it) },
                onMode = { recalculate(mode = it) },
                onToggleQthEditor = { showQthEditor = !showQthEditor },
                onQthDraft = { qthDraft = it },
                onUseQth = {
                    runCatching {
                        val cell = HamRadioCalculations.decodeMaidenhead(qthDraft)
                        qthDraft = cell.locator
                        merge(mapOf("location_source" to "locator", "qth_locator" to cell.locator))
                        showQthEditor = false
                        fetchAt(
                            HamDashboardLocation(
                                cell.centreLatitude,
                                cell.centreLongitude,
                                cell.locator,
                                "supplied Maidenhead locator"
                            )
                        )
                    }.onFailure {
                        status = it.message ?: "Invalid Maidenhead locator."
                    }
                },
                onToggleCallsignEditor = { showCallsignEditor = !showCallsignEditor },
                onCallsignDraft = {
                    callsignDraft = it.uppercase(Locale.US).replace(" ", "")
                },
                onUseCallsign = {
                    merge(mapOf("callsign" to callsignDraft.trim().uppercase(Locale.US)))
                    showCallsignEditor = false
                    startRefresh()
                },
                onUseSnapshot = if (keepLiveDashboard) {
                    result?.let { captured -> { onConfirmed(captured) } }
                } else null
            )
        }
    }
}

@Composable
private fun DashboardBody(
    values: Map<String, String>,
    settings: Map<String, String>,
    status: String,
    loading: Boolean,
    qthDraft: String,
    callsignDraft: String,
    showQthEditor: Boolean,
    showCallsignEditor: Boolean,
    onRefresh: () -> Unit,
    onDistance: (String) -> Unit,
    onMode: (String) -> Unit,
    onToggleQthEditor: () -> Unit,
    onQthDraft: (String) -> Unit,
    onUseQth: () -> Unit,
    onToggleCallsignEditor: () -> Unit,
    onCallsignDraft: (String) -> Unit,
    onUseCallsign: () -> Unit,
    onUseSnapshot: (() -> Unit)?
) {
    val band = values[HamDashboardFields.PRIMARY_BAND].orEmpty()
        .ifBlank { if (loading) "UPDATING" else "—" }
    val mhz = values[HamDashboardFields.PRIMARY_MHZ].orEmpty()
    val score = values[HamDashboardFields.PRIMARY_SCORE]?.toIntOrNull()
    val updated = displayDashboardTime(values[HamDashboardFields.RETRIEVED_TIME].orEmpty())

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = dashboardHeroColor(score)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("BEST HF BAND NOW", style = MaterialTheme.typography.labelLarge)
                    Text(band, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            score != null && mhz.isNotBlank() -> "$mhz MHz · $score / 100"
                            score != null -> "$score / 100"
                            else -> status
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    values[HamDashboardFields.SECONDARY_BANDS]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Text("Then $it", style = MaterialTheme.typography.bodyMedium) }
                }
                Button(onClick = onRefresh, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("↻  Refresh")
                    }
                }
            }
            val qth = values[HamDashboardFields.QTH_LOCATOR].orEmpty()
            if (qth.isNotBlank() || updated.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        qth.takeIf { it.isNotBlank() }?.let { "QTH $it" },
                        updated.takeIf { it.isNotBlank() }?.let { "updated $it" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    DashboardSection("Operating target") {
        Text("Range", style = MaterialTheme.typography.labelLarge)
        ChoiceGrid(
            value = normalizedDashboardDistance(settings["target_distance_km"]),
            choices = listOf(
                "500" to "Regional",
                "1500" to "Continental",
                "2500" to "DX",
                "7000" to "Long DX"
            ),
            onSelected = onDistance
        )
        Spacer(Modifier.height(6.dp))
        Text("Mode", style = MaterialTheme.typography.labelLarge)
        ChoiceGrid(
            value = settings["mode"].orEmpty().ifBlank { "mixed" },
            choices = listOf(
                "mixed" to "General",
                "ssb" to "SSB",
                "cw" to "CW",
                "ft8" to "FT8"
            ),
            onSelected = onMode
        )
        val distance = values[HamDashboardFields.TARGET_DISTANCE_KM]?.toDoubleOrNull()
        val solar = values[HamDashboardFields.SOLAR_ELEVATION]?.toDoubleOrNull()
        MetricPair(
            "Target range",
            distance?.let { "${it.toInt()} km" } ?: "—",
            "Local ionosphere",
            solar?.let {
                when {
                    it > 3 -> "Daylight"
                    it >= -9 -> "Twilight"
                    else -> "Night"
                }
            } ?: "—"
        )
    }

    DashboardSection("Bands to try") {
        BandRankList(values[HamDashboardFields.RANKED_BANDS_JSON].orEmpty())
        Text(
            "Scores rank bands against each other for this QTH, range and time. They are not QSO probabilities or MUF predictions.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    DashboardSection("Space weather") {
        MetricPair(
            "Kp", values[HamDashboardFields.KP].dash(),
            "Solar flux", values[HamDashboardFields.F107].dash()
        )
        MetricPair(
            "Solar wind", withSuffix(values[HamDashboardFields.WIND], " km/s"),
            "Bz", withSuffix(values[HamDashboardFields.BZ], " nT")
        )
        MetricPair(
            "NOAA storm",
            "G${values[HamDashboardFields.G_SCALE].orEmpty().ifBlank { "0" }}",
            "Blackout / radiation",
            "R${values[HamDashboardFields.R_SCALE].orEmpty().ifBlank { "0" }} · S${values[HamDashboardFields.S_SCALE].orEmpty().ifBlank { "0" }}"
        )
        Text(
            values[HamDashboardFields.ALERT].orEmpty().ifBlank { status },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }

    DashboardSection("QTH") {
        VerdictLine("Locator", values[HamDashboardFields.QTH_LOCATOR].dash())
        VerdictLine("Source", values[HamDashboardFields.LOCATION_SOURCE].dash())
        OutlinedButton(
            onClick = onToggleQthEditor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showQthEditor) "Hide QTH entry" else "Change / enter QTH")
        }
        if (showQthEditor) {
            OutlinedTextField(
                value = qthDraft,
                onValueChange = onQthDraft,
                label = { Text("Maidenhead locator") },
                supportingText = { Text("Example: IO91wm") },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true
            )
            Button(
                onClick = onUseQth,
                enabled = qthDraft.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use this QTH")
            }
        }
    }

    DashboardSection("Observed digital activity") {
        val callsign = settings["callsign"].orEmpty()
        if (callsign.isBlank()) {
            Text(
                "Optional: add your callsign to include recent PSK Reporter observations in this dashboard.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = callsignDraft,
                onValueChange = onCallsignDraft,
                label = { Text("My callsign") },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true
            )
            Button(
                onClick = onUseCallsign,
                enabled = callsignDraft.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add callsign & refresh")
            }
        } else {
            VerdictLine("Callsign", callsign)
            VerdictLine(
                "PSK Reporter",
                values[HamDashboardFields.PSK_RESULT].orEmpty()
                    .ifBlank { values[HamDashboardFields.PSK_STATUS].dash() }
            )
            MetricPair(
                "Reports",
                values[HamDashboardFields.PSK_REPORT_COUNT].dash(),
                "Stations / grids",
                "${values[HamDashboardFields.PSK_UNIQUE_CALLSIGNS].dash()} / ${values[HamDashboardFields.PSK_UNIQUE_GRIDS].dash()}"
            )
            val retry = values[HamDashboardFields.PSK_RETRY_AFTER_SECONDS]?.toLongOrNull()
            if (retry != null && retry > 0) {
                Text(
                    "Upstream PSK Reporter refresh available in about ${((retry + 59) / 60)} min; Refresh can reuse the matching cache meanwhile.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "PSK Reporter has a hard MethodMesh upstream floor of 7 minutes.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(
                onClick = onToggleCallsignEditor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showCallsignEditor) "Cancel callsign change" else "Change callsign")
            }
            if (showCallsignEditor) {
                OutlinedTextField(
                    value = callsignDraft,
                    onValueChange = onCallsignDraft,
                    label = { Text("My callsign") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    singleLine = true
                )
                Button(
                    onClick = onUseCallsign,
                    enabled = callsignDraft.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use callsign & refresh")
                }
            }
        }
    }

    if (onUseSnapshot != null) {
        Button(
            onClick = onUseSnapshot,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use this snapshot")
        }
        Spacer(Modifier.height(8.dp))
    }

    Text(
        listOfNotNull(
            values[HamDashboardFields.SPACE_WEATHER_FROM_CACHE]
                ?.takeIf { it == "true" }?.let { "NOAA cache" },
            values[HamDashboardFields.PSK_FROM_CACHE]
                ?.takeIf { it == "true" }?.let { "PSK cache" },
            values[HamDashboardFields.SPACE_WEATHER_STALE]
                ?.takeIf { it == "true" }?.let { "space-weather data stale" }
        ).joinToString(" · ").ifBlank { status },
        style = MaterialTheme.typography.bodySmall
    )
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
private fun ChoiceGrid(
    value: String,
    choices: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    choices.chunked(2).forEach { rowChoices ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowChoices.forEach { (choice, label) ->
                if (choice == value) {
                    Button(
                        onClick = { onSelected(choice) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✓ $label")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(choice) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label)
                    }
                }
            }
            if (rowChoices.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        MetricTile(leftLabel, leftValue, Modifier.weight(1f))
        Spacer(Modifier.padding(3.dp))
        MetricTile(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(
            MaterialTheme.colorScheme.surface,
            RoundedCornerShape(12.dp)
        ).padding(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VerdictLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BandRankList(json: String) {
    val rows = remember(json) {
        runCatching {
            val arr = JSONArray(json.ifBlank { "[]" })
            (0 until minOf(arr.length(), 5))
                .mapNotNull { index ->
                    arr.optJSONObject(index)?.let { obj ->
                        Triple(
                            obj.optString("band"),
                            obj.optDouble("representative_mhz", Double.NaN),
                            obj.optInt("score", -1)
                        )
                    }
                }
                .filter { it.first.isNotBlank() && it.second.isFinite() && it.third >= 0 }
        }.getOrDefault(emptyList())
    }

    if (rows.isEmpty()) {
        Text(
            "Refresh to calculate the current band ranking.",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    rows.forEach { (band, mhz, score) ->
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(band, fontWeight = FontWeight.SemiBold)
            Text("${"%.2f".format(Locale.US, mhz)} MHz · $score")
        }
        LinearProgressIndicator(
            progress = { score.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)
        )
    }
}

@Composable
private fun dashboardHeroColor(score: Int?) = when {
    score == null -> MaterialTheme.colorScheme.surfaceVariant
    score >= 80 -> MaterialTheme.colorScheme.primaryContainer
    score >= 60 -> MaterialTheme.colorScheme.secondaryContainer
    score < 35 -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun String?.dash(): String = this?.takeIf { it.isNotBlank() } ?: "—"

private fun withSuffix(value: String?, suffix: String): String =
    value?.takeIf { it.isNotBlank() }?.let { "$it$suffix" } ?: "—"

private fun displayDashboardTime(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(iso))
}.getOrDefault("")

private fun normalizedDashboardDistance(value: String?): String =
    value?.toDoubleOrNull()?.let { distance ->
        if (distance % 1.0 == 0.0) distance.toLong().toString() else "%.1f".format(Locale.US, distance).trimEnd('0').trimEnd('.')
    } ?: "2500"

private fun dashboardMap(json: String): Map<String, String> = runCatching {
    if (json.isBlank()) return emptyMap()
    val obj = JSONObject(json)
    buildMap {
        obj.keys().forEach { key -> put(key, obj.optString(key, "")) }
    }
}.getOrDefault(emptyMap())

private fun explicitDashboardLocation(settings: Map<String, String>): HamDashboardLocation? {
    val source = settings["location_source"].orEmpty().ifBlank { "auto" }
    val locatorRaw = settings["qth_locator"].orEmpty().trim()
    if ((source == "locator" || locatorRaw.isNotBlank()) && locatorRaw.isNotBlank()) {
        return runCatching {
            val cell = HamRadioCalculations.decodeMaidenhead(locatorRaw)
            HamDashboardLocation(
                cell.centreLatitude,
                cell.centreLongitude,
                cell.locator,
                "supplied Maidenhead locator"
            )
        }.getOrNull()
    }

    val lat = settings["latitude"]?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val lon = settings["longitude"]?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    return if (
        source == "manual" &&
        lat != null &&
        lon != null &&
        lat in -90.0..90.0 &&
        lon in -180.0..180.0
    ) {
        HamDashboardLocation(
            lat,
            lon,
            HamRadioCalculations.encodeMaidenhead(lat, lon, 6),
            "supplied coordinates"
        )
    } else {
        null
    }
}

private fun hasDashboardLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

private fun currentDashboardLocation(
    context: Context,
    ok: (HamDashboardLocation) -> Unit,
    fail: (String) -> Unit
) {
    if (!hasDashboardLocationPermission(context)) {
        fail("Location permission is required for automatic QTH. Enter a Maidenhead locator instead.")
        return
    }
    val token = CancellationTokenSource()
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
        .addOnSuccessListener { location ->
            if (location == null) {
                fail("Current GPS position is unavailable. Enter a Maidenhead locator instead.")
            } else {
                val locator = HamRadioCalculations.encodeMaidenhead(
                    location.latitude,
                    location.longitude,
                    6
                )
                ok(
                    HamDashboardLocation(
                        location.latitude,
                        location.longitude,
                        locator,
                        "live GPS"
                    )
                )
            }
        }
        .addOnFailureListener {
            fail(it.message ?: "GPS location failed. Enter a Maidenhead locator instead.")
        }
}
