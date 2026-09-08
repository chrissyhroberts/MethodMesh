package com.example.methodmesh.modules.hamradio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Ham-radio screens are deliberately task-oriented. The earlier implementation
 * exposed almost every setting as a text box, including enums such as "sent",
 * "callsign" and "live_noaa". That was technically functional but made the
 * capability feel like an API debug form rather than an operator tool.
 *
 * Choice fields are now rendered as labelled buttons, advanced controls are
 * collapsed by default, each tool says what the operator should do, and a small
 * result card surfaces the useful answer before the scaffold's audit output.
 */
private data class HamScreenField(
    val key: String,
    val label: String,
    val defaultValue: String,
    val help: String = "",
    val numeric: Boolean = false,
    val choices: List<Pair<String, String>> = emptyList(),
    val advanced: Boolean = false,
    val settingBacked: Boolean = true,
    val visibleWhen: (Map<String, String>) -> Boolean = { true }
)

private class HamRadioFormScreen(
    override val capabilityId: String,
    override val title: String,
    override val description: String,
    private val method: As100Method,
    private val fields: List<HamScreenField>,
    private val actionLabel: String,
    private val network: Boolean,
    private val steps: List<String>,
    private val supportingText: String = "",
    private val resultKey: String,
    private val statusKey: String,
    private val errorKey: String,
    private val warningKey: String? = null,
    private val resultDetails: List<Pair<String, String>> = emptyList(),
    private val previewKeys: List<String> = emptyList(),
    private val resultBuilder: (ExecutionRequest, Map<String, String>, InvocationContext?) -> ExecutionResult
) : CapabilityScreenSpec {
    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        fun initial(key: String, default: String): String =
            context.action.settings[key] ?: context.action.settings["input_$key"] ?: default

        fun encode(values: Map<String, String>): String = JSONObject(values).toString()
        fun decode(json: String): Map<String, String> {
            if (json.isBlank()) return emptyMap()
            val obj = JSONObject(json)
            return obj.keys().asSequence().associateWith { key -> obj.optString(key, "") }
        }

        val initialSettings = remember(context.action.canonicalId) {
            fields.associate { it.key to initial(it.key, it.defaultValue) }.toMutableMap().apply {
                // UI-only target selector should reflect a supplied target grid in
                // native presets rather than defaulting to the distance view.
                if ("target_mode" in this && initial("target_locator", "").isNotBlank()) {
                    this["target_mode"] = "grid"
                }
            }
        }
        var settingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(encode(initialSettings)) }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var showAdvanced by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        // Running is deliberately not saveable. If Android recreates the screen while a
        // provider call is in flight, the provider boundary remains authoritative.
        var running by remember { mutableStateOf(false) }
        var localStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        fun currentSettings(): Map<String, String> = decode(settingsJson)
        fun update(key: String, value: String) {
            val next = currentSettings().toMutableMap().apply { this[key] = value }
            settingsJson = encode(next)
            // UI-only controls are intentionally not exported as method settings.
            fields.firstOrNull { it.key == key }?.takeIf { it.settingBacked }?.let {
                context.onSettingsChanged(mapOf(key to value))
            }
        }
        fun requestFor(settings: Map<String, String>) = method.request(
            action = capabilityId,
            context = context.request.invocationContext.asMap(capabilityId) + context.action.settings +
                settings.filterKeys { key -> fields.firstOrNull { it.key == key }?.settingBacked != false },
            signals = emptyList(),
            inputs = emptyList()
        )

        val reconstructedResult: ExecutionResult? = remember(valuesJson, settingsJson, context.action.canonicalId) {
            if (valuesJson.isBlank()) null else resultBuilder(
                requestFor(currentSettings()),
                decode(valuesJson),
                context.request.invocationContext
            )
        }

        fun runCapability() {
            if (running) return
            val settings = currentSettings()
            val request = requestFor(settings)
            running = true
            localStatus = if (network) "Retrieving…" else "Calculating…"
            scope.launch {
                val execution = runCatching {
                    withContext(if (network) Dispatchers.IO else Dispatchers.Default) {
                        method.execute(request, settingsState = null, transport = null)
                    }
                }
                execution.onSuccess { result ->
                    val values = OutputFormatter.fields(result, includeProvenance = false)
                        .mapValues { (_, value) -> value?.toString().orEmpty() }
                    valuesJson = encode(values)
                    localStatus = values[statusKey].orEmpty().ifBlank { "Done." }
                }.onFailure { error ->
                    localStatus = "Error: ${error.message ?: "execution failed"}"
                }
                running = false
            }
        }

        LaunchedEffect(context.presentationMode, context.completionMode, context.action.settings, valuesJson) {
            if (
                context.submitsImmediately &&
                context.presentationMode == CapabilityPresentationMode.IntentLaunch &&
                valuesJson.isBlank() &&
                !running
            ) {
                runCapability()
            }
        }

        val resultValues = remember(valuesJson) { decode(valuesJson) }
        val preview = if (resultValues.isEmpty()) emptyMap() else {
            val keys = (
                listOf(resultKey) +
                    resultDetails.map { it.second } +
                    listOfNotNull(warningKey) +
                    previewKeys +
                    listOf(statusKey, errorKey)
                ).distinct()
            keys.associateWith { resultValues[it].orEmpty() }.filterValues { it.isNotBlank() }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = reconstructedResult,
            resultPreview = preview,
            onBack = onBack,
            onRetry = { valuesJson = ""; localStatus = "" },
            onConfirm = { reconstructedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))

            if (steps.isNotEmpty()) {
                Text("How to use it", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
                }
                Spacer(Modifier.height(10.dp))
            }

            val settings = currentSettings()
            fields.filter { field ->
                !field.advanced && field.visibleWhen(settings) && shouldShowField(context, field)
            }.forEach { field ->
                HamField(field, settings[field.key].orEmpty(), running) { value -> update(field.key, value) }
            }

            val advancedFields = fields.filter { field -> field.advanced && field.visibleWhen(settings) && shouldShowField(context, field) }
            if (advancedFields.isNotEmpty()) {
                val customised = advancedFields.count { field -> settings[field.key].orEmpty() != field.defaultValue }
                OutlinedButton(
                    onClick = { showAdvanced = !showAdvanced },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    val suffix = if (customised > 0) " · $customised customised" else ""
                    Text(if (showAdvanced) "Hide advanced settings$suffix" else "Advanced settings$suffix")
                }
                if (showAdvanced) {
                    Spacer(Modifier.height(4.dp))
                    advancedFields.forEach { field ->
                        HamField(field, settings[field.key].orEmpty(), running) { value -> update(field.key, value) }
                    }
                }
            }

            if (supportingText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(supportingText, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(10.dp))
            Button(onClick = { runCapability() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) "Working…" else if (reconstructedResult == null) actionLabel else "$actionLabel again")
            }
            if (localStatus.isNotBlank()) {
                Text(localStatus, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HamField(
    field: HamScreenField,
    value: String,
    running: Boolean,
    onValueChange: (String) -> Unit
) {
    if (field.choices.isNotEmpty()) {
        Text(field.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
        field.choices.chunked(2).forEach { rowChoices ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                rowChoices.forEach { (choice, label) ->
                    if (choice == value) {
                        Button(
                            onClick = { onValueChange(choice) },
                            enabled = !running,
                            modifier = Modifier.weight(1f)
                        ) { Text("✓ $label") }
                    } else {
                        OutlinedButton(
                            onClick = { onValueChange(choice) },
                            enabled = !running,
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    }
                }
                if (rowChoices.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (field.help.isNotBlank()) {
            Text(field.help, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = { raw -> onValueChange(if (field.numeric) raw.numericText() else raw) },
            label = { Text(field.label) },
            supportingText = if (field.help.isBlank()) null else ({ Text(field.help) }),
            enabled = !running,
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            singleLine = true
        )
    }
}

private fun shouldShowField(context: CapabilityScreenContext, field: HamScreenField): Boolean =
    !field.settingBacked || context.settingShouldBeShown(field.key)

private fun String.numericText(): String = filter { it.isDigit() || it == '-' || it == '.' }.let { filtered ->
    val minus = if (filtered.startsWith("-")) "-" else ""
    val body = filtered.removePrefix("-")
    minus + body.split('.').let { parts ->
        parts.firstOrNull().orEmpty() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
    }
}

private fun choices(vararg values: Pair<String, String>): List<Pair<String, String>> = values.toList()

val HamSpaceWeatherCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamSpaceWeatherMethod.ID,
    title = "Space weather",
    description = "A quick radio-oriented view of current solar and geomagnetic conditions.",
    method = As100HamSpaceWeatherMethod,
    fields = listOf(
        HamScreenField(
            "refresh_mode", "Data refresh", "cache_preferred",
            choices = choices("cache_preferred" to "Use recent data", "fresh_required" to "Force refresh"),
            advanced = true
        )
    ),
    actionLabel = "Check space weather",
    network = true,
    steps = listOf(
        "Usually you can just tap Check space weather.",
        "Open Advanced settings only when you specifically need to bypass the normal NOAA cache."
    ),
    supportingText = "Source: NOAA Space Weather Prediction Center. No QTH or callsign is sent to NOAA.",
    resultKey = HamSpaceWeatherFields.RESULT,
    statusKey = HamSpaceWeatherFields.STATUS,
    errorKey = HamSpaceWeatherFields.ERROR,
    resultDetails = listOf(
        "Kp" to HamSpaceWeatherFields.KP,
        "F10.7" to HamSpaceWeatherFields.F107,
        "Solar wind km/s" to HamSpaceWeatherFields.WIND
    ),
    previewKeys = listOf(HamSpaceWeatherFields.RESULT, HamSpaceWeatherFields.KP, HamSpaceWeatherFields.F107, HamSpaceWeatherFields.STATUS, HamSpaceWeatherFields.ERROR),
    resultBuilder = As100HamSpaceWeatherMethod::result
)

val HamPskReporterCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamPskReporterMethod.ID,
    title = "PSK Reporter activity",
    description = "See where a station has recently been heard, or what it has recently heard.",
    method = As100HamPskReporterMethod,
    fields = listOf(
        HamScreenField("subject_type", "Search for", "callsign", choices = choices("callsign" to "Callsign", "grid" to "Grid square")),
        HamScreenField("subject", "Callsign or grid", "", "Example: M0ABC or IO91wm"),
        HamScreenField("direction", "What do you want to see?", "sent", choices = choices(
            "sent" to "Who heard this station",
            "received" to "What this station heard",
            "either" to "Either direction"
        )),
        HamScreenField("lookback_minutes", "Time window", "30", choices = choices(
            "15" to "15 min", "30" to "30 min", "60" to "1 hour", "360" to "6 hours", "1440" to "24 hours"
        )),
        HamScreenField("mode", "Mode", "", choices = choices(
            "" to "Any", "FT8" to "FT8", "FT4" to "FT4", "CW" to "CW", "SSB" to "SSB", "digital" to "Other digital"
        )),
        HamScreenField("min_frequency_mhz", "Minimum frequency (MHz)", "0", "0 = no lower limit", numeric = true, advanced = true),
        HamScreenField("max_frequency_mhz", "Maximum frequency (MHz)", "0", "0 = no upper limit", numeric = true, advanced = true),
        HamScreenField("record_limit", "Maximum reports", "100", "1–100", numeric = true, advanced = true),
        HamScreenField("include_no_locator", "Reports without grid locators", "no", choices = choices("no" to "Exclude", "yes" to "Include"), advanced = true)
    ),
    actionLabel = "Check activity",
    network = true,
    steps = listOf(
        "Enter the callsign or grid you are interested in.",
        "Choose whether you want stations that heard it, stations it heard, or both.",
        "Pick a time window and mode, then tap Check activity."
    ),
    supportingText = "PSK Reporter is queried no more than once every 7 minutes. Repeating the same query can use cached data during that interval.",
    resultKey = HamPskReporterFields.RESULT,
    statusKey = HamPskReporterFields.STATUS,
    errorKey = HamPskReporterFields.ERROR,
    warningKey = HamPskReporterFields.WARNING,
    resultDetails = listOf(
        "Reports" to HamPskReporterFields.REPORT_COUNT,
        "Stations" to HamPskReporterFields.UNIQUE_CALLSIGNS,
        "Grids" to HamPskReporterFields.UNIQUE_GRIDS,
        "Latest report" to HamPskReporterFields.LATEST_TIME
    ),
    previewKeys = listOf(HamPskReporterFields.RESULT, HamPskReporterFields.REPORT_COUNT, HamPskReporterFields.UNIQUE_CALLSIGNS, HamPskReporterFields.UNIQUE_GRIDS, HamPskReporterFields.STATUS, HamPskReporterFields.ERROR),
    resultBuilder = As100HamPskReporterMethod::result
)

val HamBandAdviceCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamBandAdviceMethod.ID,
    title = "Best HF bands now",
    description = "Get a simple ranked suggestion for which HF bands are worth trying from your QTH.",
    method = As100HamBandAdviceMethod,
    fields = listOf(
        HamScreenField("location_mode", "My QTH", "locator", choices = choices("locator" to "Maidenhead locator", "coordinates" to "Latitude / longitude")),
        HamScreenField("origin_locator", "My Maidenhead locator", "", "Example: IO91wm", visibleWhen = { it["location_mode"] != "coordinates" }),
        HamScreenField("latitude", "My latitude", "0", numeric = true, visibleWhen = { it["location_mode"] == "coordinates" }),
        HamScreenField("longitude", "My longitude", "0", numeric = true, visibleWhen = { it["location_mode"] == "coordinates" }),
        HamScreenField("target_mode", "Destination", "distance", choices = choices("distance" to "Approx. distance", "grid" to "Target grid"), settingBacked = false),
        HamScreenField("target_locator", "Target Maidenhead locator", "", "Example: FN30as", visibleWhen = { it["target_mode"] == "grid" }),
        HamScreenField("target_distance_km", "Approx. target distance (km)", "2000", numeric = true, visibleWhen = { it["target_mode"] != "grid" }),
        HamScreenField("conditions_source", "Space weather", "live_noaa", choices = choices("live_noaa" to "Use live NOAA", "manual" to "Enter manually")),
        HamScreenField("kp", "Kp", "2", numeric = true, visibleWhen = { it["conditions_source"] == "manual" }),
        HamScreenField("f107", "F10.7 solar flux", "100", numeric = true, visibleWhen = { it["conditions_source"] == "manual" }),
        HamScreenField("r_scale", "NOAA R scale", "0", numeric = true, visibleWhen = { it["conditions_source"] == "manual" }),
        HamScreenField("mode", "Operating mode", "mixed", choices = choices(
            "mixed" to "General", "ssb" to "SSB", "cw" to "CW", "ft8" to "FT8", "ft4" to "FT4", "digital" to "Other digital"
        )),
        HamScreenField("when_iso", "UTC time", "", "Leave blank for now. ISO-8601 is accepted for planning/checking another time.", advanced = true)
    ),
    actionLabel = "Recommend bands",
    network = true,
    steps = listOf(
        "Enter your QTH as a Maidenhead locator (usually the easiest option).",
        "Enter a target grid, or just an approximate target distance.",
        "Choose your mode and tap Recommend bands. Live NOAA conditions are used automatically."
    ),
    supportingText = "This is a lightweight operating heuristic, not a MUF/VOACAP reliability prediction and not a band-plan check.",
    resultKey = HamBandFields.RESULT,
    statusKey = HamBandFields.STATUS,
    errorKey = HamBandFields.ERROR,
    warningKey = HamBandFields.ADVISORY,
    resultDetails = listOf(
        "Best band" to HamBandFields.PRIMARY,
        "Representative MHz" to HamBandFields.PRIMARY_MHZ,
        "Score" to HamBandFields.PRIMARY_SCORE,
        "Path distance km" to HamBandFields.DISTANCE
    ),
    previewKeys = listOf(HamBandFields.RESULT, HamBandFields.PRIMARY, HamBandFields.PRIMARY_MHZ, HamBandFields.PRIMARY_SCORE, HamBandFields.STATUS, HamBandFields.ERROR),
    resultBuilder = As100HamBandAdviceMethod::result
)

val HamMaidenheadCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamMaidenheadMethod.ID,
    title = "Maidenhead converter",
    description = "Convert between latitude/longitude and amateur-radio grid locators.",
    method = As100HamMaidenheadMethod,
    fields = listOf(
        HamScreenField("operation", "What do you have?", "encode", choices = choices("encode" to "Coordinates", "decode" to "Grid locator")),
        HamScreenField("latitude", "Latitude", "0", numeric = true, visibleWhen = { it["operation"] != "decode" }),
        HamScreenField("longitude", "Longitude", "0", numeric = true, visibleWhen = { it["operation"] != "decode" }),
        HamScreenField("precision", "Grid precision", "6", choices = choices("4" to "4 characters", "6" to "6 characters", "8" to "8 characters"), visibleWhen = { it["operation"] != "decode" }),
        HamScreenField("locator", "Maidenhead locator", "", "Example: IO91wm", visibleWhen = { it["operation"] == "decode" })
    ),
    actionLabel = "Convert",
    network = false,
    steps = listOf(
        "Choose whether you are starting with coordinates or a grid locator.",
        "Enter the value and tap Convert."
    ),
    resultKey = HamMaidenheadFields.RESULT,
    statusKey = HamMaidenheadFields.STATUS,
    errorKey = HamMaidenheadFields.ERROR,
    resultDetails = listOf(
        "Locator" to HamMaidenheadFields.LOCATOR,
        "Latitude" to HamMaidenheadFields.LATITUDE,
        "Longitude" to HamMaidenheadFields.LONGITUDE
    ),
    previewKeys = listOf(HamMaidenheadFields.RESULT, HamMaidenheadFields.LOCATOR, HamMaidenheadFields.LATITUDE, HamMaidenheadFields.LONGITUDE, HamMaidenheadFields.ERROR),
    resultBuilder = As100HamMaidenheadMethod::result
)

val HamPathCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamPathMethod.ID,
    title = "Radio path",
    description = "Find the distance and beam heading between two Maidenhead locators.",
    method = As100HamPathMethod,
    fields = listOf(
        HamScreenField("origin_locator", "From grid", "", "Example: IO91wm"),
        HamScreenField("destination_locator", "To grid", "", "Example: FN30as")
    ),
    actionLabel = "Calculate path",
    network = false,
    steps = listOf("Enter your grid and the other station's grid, then tap Calculate path."),
    resultKey = HamPathFields.RESULT,
    statusKey = HamPathFields.STATUS,
    errorKey = HamPathFields.ERROR,
    resultDetails = listOf(
        "Distance km" to HamPathFields.DISTANCE_KM,
        "Initial bearing" to HamPathFields.BEARING,
        "Reverse bearing" to HamPathFields.REVERSE
    ),
    previewKeys = listOf(HamPathFields.RESULT, HamPathFields.DISTANCE_KM, HamPathFields.BEARING, HamPathFields.STATUS, HamPathFields.ERROR),
    resultBuilder = As100HamPathMethod::result
)

val HamAntennaCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamAntennaMethod.ID,
    title = "Antenna length",
    description = "Get a practical starting length for a simple wavelength-derived antenna.",
    method = As100HamAntennaMethod,
    fields = listOf(
        HamScreenField("frequency_mhz", "Frequency (MHz)", "14.2", "Use the part of the band you intend to centre the antenna on.", numeric = true),
        HamScreenField("design", "Antenna", "dipole", choices = choices(
            "dipole" to "Centre-fed dipole", "quarter_wave" to "Quarter-wave", "half_wave" to "Half-wave",
            "five_eighths" to "5/8-wave", "full_wave" to "Full-wave"
        )),
        HamScreenField("velocity_factor", "Velocity / end-effect factor", "0.95", "0.95 is a useful starting assumption; construction changes the final resonant length.", numeric = true, advanced = true)
    ),
    actionLabel = "Calculate length",
    network = false,
    steps = listOf(
        "Enter the frequency you want to design around.",
        "Choose the antenna type and tap Calculate length. Cut long and tune after construction."
    ),
    resultKey = HamAntennaFields.RESULT,
    statusKey = HamAntennaFields.STATUS,
    errorKey = HamAntennaFields.ERROR,
    resultDetails = listOf(
        "Total length m" to HamAntennaFields.TOTAL_M,
        "Element / leg m" to HamAntennaFields.ELEMENT_M,
        "Total length ft" to HamAntennaFields.TOTAL_FT
    ),
    previewKeys = listOf(HamAntennaFields.RESULT, HamAntennaFields.TOTAL_M, HamAntennaFields.ELEMENT_M, HamAntennaFields.STATUS, HamAntennaFields.ERROR),
    resultBuilder = As100HamAntennaMethod::result
)

val HamSwrCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamSwrMethod.ID,
    title = "SWR calculator",
    description = "Turn forward and reflected power readings into SWR and mismatch figures.",
    method = As100HamSwrMethod,
    fields = listOf(
        HamScreenField("forward_power_w", "Forward power (W)", "100", numeric = true),
        HamScreenField("reflected_power_w", "Reflected power (W)", "4", numeric = true)
    ),
    actionLabel = "Calculate SWR",
    network = false,
    steps = listOf("Enter the forward and reflected power shown by your meter, then tap Calculate SWR."),
    resultKey = HamSwrFields.RESULT,
    statusKey = HamSwrFields.STATUS,
    errorKey = HamSwrFields.ERROR,
    resultDetails = listOf(
        "SWR" to HamSwrFields.VALUE,
        "Return loss dB" to HamSwrFields.RETURN_LOSS,
        "Mismatch loss dB" to HamSwrFields.MISMATCH_LOSS
    ),
    previewKeys = listOf(HamSwrFields.RESULT, HamSwrFields.VALUE, HamSwrFields.RETURN_LOSS, HamSwrFields.STATUS, HamSwrFields.ERROR),
    resultBuilder = As100HamSwrMethod::result
)

val HamLinkCapabilityScreen: CapabilityScreenSpec = HamRadioFormScreen(
    capabilityId = As100HamLinkMethod.ID,
    title = "RF link / Fresnel",
    description = "Check path loss, line-of-sight horizon and Fresnel clearance for a terrestrial radio link.",
    method = As100HamLinkMethod,
    fields = listOf(
        HamScreenField("frequency_mhz", "Frequency (MHz)", "145", numeric = true),
        HamScreenField("distance_km", "Path distance (km)", "25", numeric = true),
        HamScreenField("tx_height_m", "TX antenna height (m)", "10", numeric = true),
        HamScreenField("rx_height_m", "RX antenna height (m)", "10", numeric = true),
        HamScreenField("tx_power_w", "TX power (W)", "0", "Leave at 0 if you only want geometry/path-loss calculations.", numeric = true, advanced = true),
        HamScreenField("antenna_gain_dbi", "TX antenna gain (dBi)", "0", numeric = true, advanced = true),
        HamScreenField("feedline_loss_db", "TX feedline loss (dB)", "0", numeric = true, advanced = true)
    ),
    actionLabel = "Calculate link",
    network = false,
    steps = listOf(
        "Enter frequency, path distance and both antenna heights.",
        "Tap Calculate link for path loss, horizon and Fresnel radius. Open Advanced settings only if you also want a simple TX-side link budget."
    ),
    resultKey = HamLinkFields.RESULT,
    statusKey = HamLinkFields.STATUS,
    errorKey = HamLinkFields.ERROR,
    resultDetails = listOf(
        "FSPL dB" to HamLinkFields.FSPL,
        "Radio horizon km" to HamLinkFields.RADIO_HORIZON,
        "Midpoint Fresnel radius m" to HamLinkFields.FRESNEL,
        "EIRP dBm" to HamLinkFields.EIRP
    ),
    previewKeys = listOf(HamLinkFields.RESULT, HamLinkFields.FSPL, HamLinkFields.RADIO_HORIZON, HamLinkFields.FRESNEL, HamLinkFields.STATUS, HamLinkFields.ERROR),
    resultBuilder = As100HamLinkMethod::result
)
