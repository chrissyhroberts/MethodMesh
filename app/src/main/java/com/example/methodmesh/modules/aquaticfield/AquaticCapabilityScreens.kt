package com.example.methodmesh.modules.aquaticfield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.ui.components.SettingsRenderer
import org.json.JSONArray
import org.json.JSONObject

private class AquaticGenericCapabilityScreen(
    override val capabilityId: String,
    override val title: String,
    override val description: String,
    private val method: AquaticMethodBase,
    private val persistNativeRecord: Boolean = true
) : CapabilityScreenSpec {

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val schema = remember(capabilityId) { AquaticFieldModule.settingsFor(capabilityId) }
        var savedSettingsJson by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(settingsJsonFromAction(schema, context.action.settings))
        }
        var stateRef: SettingsState? = null
        val settingsState = remember(capabilityId) {
            SettingsState(schema) { _, _ ->
                stateRef?.let { state ->
                    savedSettingsJson = anySettingsToJson(state.asMap())
                    context.onSettingsChanged(state.asStringMap())
                }
            }
        }
        stateRef = settingsState
        var restored by remember(context.action.canonicalId) { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready.") }

        LaunchedEffect(context.action.canonicalId, context.action.settings) {
            if (!restored) {
                settingsState.restore(typedSettings(schema, savedSettingsJson, context.action.settings))
                restored = true
            }
        }

        fun runCapability(autoSubmit: Boolean = context.submitsImmediately) {
            val runtime = settingsState.asStringMap()
            context.onSettingsChanged(runtime)
            val request = method.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + runtime,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = method.execute(request, settingsState, null)
            result = execution
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            val stringFields = fields.mapValues { it.value?.toString().orEmpty() }
            status = stringFields[AquaticCommonFields.RESULT]
                .orEmpty()
                .ifBlank { stringFields[AquaticCommonFields.ERROR].orEmpty() }
                .ifBlank { stringFields[AquaticCommonFields.STATUS].orEmpty() }

            val keepLocal = persistNativeRecord &&
                (context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun)
            if (keepLocal) AquaticFieldRepository.record(appContext, capabilityId, stringFields)
            if (autoSubmit) onConfirmed(execution)
        }

        LaunchedEffect(restored, context.completionMode, context.action.canonicalId) {
            if (restored && context.submitsImmediately && !launched) {
                launched = true
                runCapability(autoSubmit = true)
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { runCapability(autoSubmit = false) },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            SettingsRenderer(
                settings = schema,
                settingsState = settingsState,
                capabilityId = capabilityId,
                visibleWhen = { setting -> context.settingShouldBeShown(setting.id) }
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { runCapability(autoSubmit = false) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (result == null) "Run" else "Run again")
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun SettingsState.asStringMap(): Map<String, String> =
    asMap().mapValues { (_, value) -> value.toString() }

private fun settingsJsonFromAction(schema: List<MethodSetting>, action: Map<String, String>): String {
    val map = linkedMapOf<String, String>()
    schema.forEach { setting ->
        val supplied = action[setting.id] ?: action["input_${setting.id}"]
        if (supplied != null) map[setting.id] = supplied
    }
    return JSONObject(map as Map<*, *>).toString()
}

private fun anySettingsToJson(values: Map<String, Any>): String =
    JSONObject(values as Map<*, *>).toString()

private fun typedSettings(
    schema: List<MethodSetting>,
    savedJson: String,
    action: Map<String, String>
): Map<String, Any> {
    val saved = runCatching { JSONObject(savedJson) }.getOrElse { JSONObject() }
    return buildMap {
        schema.forEach { setting ->
            val raw = (if (saved.has(setting.id)) saved.opt(setting.id)?.toString() else null)
                ?: action[setting.id]
                ?: action["input_${setting.id}"]
                ?: return@forEach
            when (setting) {
                is MethodSetting.BooleanSetting -> put(setting.id, raw.toBooleanStrictOrNull() ?: setting.defaultValue)
                is MethodSetting.IntSetting -> put(setting.id, raw.toIntOrNull() ?: setting.defaultValue)
                is MethodSetting.FloatSetting -> put(setting.id, raw.toFloatOrNull() ?: setting.defaultValue)
                is MethodSetting.TextSetting -> put(setting.id, raw)
                is MethodSetting.ChoiceSetting -> put(setting.id, raw)
                is MethodSetting.MultiChoiceSetting -> put(setting.id, raw)
            }
        }
    }
}

object AquaticSalinityCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticSalinityMethod.ID, "Salinity and conductivity",
    "Convert conductivity, calculate Practical Salinity, normalise specific conductance, or make an explicitly empirical TDS estimate.",
    As100AquaticSalinityMethod
)

object AquaticPressureDepthCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticPressureDepthMethod.ID, "Pressure / depth",
    "Convert pressure and depth or apply an explicit simple wire/sensor correction.",
    As100AquaticPressureDepthMethod
)

object AquaticSecchiCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticSecchiMethod.ID, "Secchi depth",
    "Record disappearance and reappearance depth with field-condition metadata.",
    As100AquaticSecchiMethod
)

object AquaticStationVisitCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticStationVisitMethod.ID, "Station visit",
    "Create or update one occupation of a persistent aquatic sampling station.",
    As100AquaticStationVisitMethod
)

object AquaticCtdCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticCtdCastMethod.ID, "CTD / sonde cast",
    "Record cast provenance and file identifiers without pretending to replace instrument-specific raw processing.",
    As100AquaticCtdCastMethod
)

object AquaticSampleIdCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticSampleIdMethod.ID, "Sample ID",
    "Generate a configurable human-readable sample identifier.",
    As100AquaticSampleIdMethod,
    persistNativeRecord = false
)

object AquaticSampleCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticSampleRecordMethod.ID, "Water sample",
    "Record one discrete water sample and its field provenance.",
    As100AquaticSampleRecordMethod
)

object AquaticRosetteCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticRosetteFireMethod.ID, "Rosette firing",
    "Record one bottle firing with pressure/depth and linked sample IDs.",
    As100AquaticRosetteFireMethod
)

object AquaticProfileCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticProfileSummaryMethod.ID, "Vertical profile",
    "Summarise a processed CSV/TSV profile using explicit gradient and mixed-layer criteria.",
    As100AquaticProfileSummaryMethod
)

object AquaticDepthPlanCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticDepthPlanMethod.ID, "Sampling depth plan",
    "Generate a planned set of depths with water-depth and bottom-clearance checks.",
    As100AquaticDepthPlanMethod
)

object AquaticInstrumentCheckCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticInstrumentCheckMethod.ID, "Instrument check",
    "Record calibration, verification, cleaning, service or other operational checks.",
    As100AquaticInstrumentCheckMethod
)

object AquaticFieldQcCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticFieldQcMethod.ID, "Field QC",
    "Run non-destructive completeness and plausibility checks.",
    As100AquaticFieldQcMethod
)

object AquaticTransectCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticTransectMethod.ID, "Transect",
    "Record a transect and derive start/end geodesic distance where coordinates are supplied.",
    As100AquaticTransectMethod
)

object AquaticMooringCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticMooringMethod.ID, "Mooring",
    "Record mooring deployment, service, inspection or recovery.",
    As100AquaticMooringMethod
)

object AquaticSedimentCapabilityScreen : CapabilityScreenSpec by AquaticGenericCapabilityScreen(
    As100AquaticSedimentMethod.ID, "Sediment core / grab",
    "Record a sediment sampling operation and resulting subsample count.",
    As100AquaticSedimentMethod
)

object AquaticStationDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AquaticStationDashboardMethod.ID
    override val title = "Aquatic station"
    override val description = "Persistent current-station view built from the aquatic field repository."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        var snapshot by remember { mutableStateOf(AquaticFieldRepository.snapshot(appContext)) }
        var latestResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var quickPanel by rememberSaveable { mutableStateOf("") }
        var quickStatus by rememberSaveable { mutableStateOf("") }

        var sampleId by rememberSaveable { mutableStateOf("") }
        var sampleDepth by rememberSaveable { mutableStateOf("") }
        var sampleType by rememberSaveable { mutableStateOf("chemistry") }
        var ctdCastId by rememberSaveable { mutableStateOf("") }
        var ctdInstrumentId by rememberSaveable { mutableStateOf("") }
        var ctdDepth by rememberSaveable { mutableStateOf("") }
        var secchiDisappear by rememberSaveable { mutableStateOf("") }
        var secchiReappear by rememberSaveable { mutableStateOf("") }

        val isIntentTest = context.request.source.equals("intent_test", ignoreCase = true)
        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard ||
                context.isNativePresetRun ||
                isIntentTest

        fun refresh(completeVisit: Boolean = false, submit: Boolean = false) {
            if (completeVisit) AquaticFieldRepository.finishVisit(appContext)
            snapshot = AquaticFieldRepository.snapshot(appContext)
            val request = As100AquaticStationDashboardMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + snapshot,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100AquaticStationDashboardMethod.result(
                request = request,
                values = As100AquaticStationDashboardMethod.calculateValues(snapshot),
                invocation = context.request.invocationContext
            )
            latestResult = execution
            if (submit) onConfirmed(execution)
        }

        fun persistQuick(method: AquaticMethodBase, values: Map<String, String>) {
            val request = method.request(
                action = method.id,
                context = context.request.invocationContext.asMap(method.id) + values,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = method.execute(request, null, null)
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
                .mapValues { it.value?.toString().orEmpty() }
            quickStatus = fields[AquaticCommonFields.RESULT]
                .orEmpty()
                .ifBlank { fields[AquaticCommonFields.ERROR].orEmpty() }
                .ifBlank { fields[AquaticCommonFields.STATUS].orEmpty() }
            AquaticFieldRepository.record(appContext, method.id, fields)
            refresh()
        }

        LaunchedEffect(context.action.canonicalId, context.submitsImmediately) {
            if (!launched) {
                launched = true
                refresh(submit = context.submitsImmediately && !isIntentTest)
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = if (keepLiveDashboard) null else latestResult,
            resultPreview = if (keepLiveDashboard) emptyMap() else latestResult?.let {
                OutputFormatter.fields(it, includeProvenance = false)
            }.orEmpty(),
            onBack = onBack,
            onRetry = { refresh() },
            onConfirm = { latestResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            val station = snapshot["station_id"].orEmpty()
            val visit = snapshot["visit_id"].orEmpty()
            val status = snapshot["visit_status"].orEmpty()
            val planned = snapshot["planned_sample_count"].orEmpty()
            val completed = snapshot["completed_sample_count"].orEmpty()
            val casts = snapshot["cast_count"].orEmpty()
            val secchi = snapshot["secchi_depth_m"].orEmpty()
            val qc = snapshot["qc_issue_count"].orEmpty()
            val waterDepth = snapshot["water_depth_m"].orEmpty()
            val plannedDepths = jsonArrayText(snapshot["planned_depths_json"].orEmpty())
            val completedDepths = jsonArrayText(snapshot["completed_depths_json"].orEmpty())

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (station.isBlank()) "No active station" else "$station${if (visit.isBlank()) "" else " · $visit"}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (station.isNotBlank()) {
                        Text("Status: ${status.ifBlank { "open" }}")
                        if (waterDepth.isNotBlank()) Text("Water depth: $waterDepth m")
                        Text("Samples: $completed / $planned")
                        if (plannedDepths.isNotBlank()) Text("Planned depths: $plannedDepths m", style = MaterialTheme.typography.bodySmall)
                        if (completedDepths.isNotBlank()) Text("Completed depths: $completedDepths m", style = MaterialTheme.typography.bodySmall)
                        Text("CTD/sonde casts: $casts")
                        Text("Secchi: ${if (secchi.isBlank()) "not recorded" else "$secchi m"}")
                        Text("QC findings: $qc")
                    }
                }
            }

            if (station.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { quickPanel = if (quickPanel == "sample") "" else "sample" }, modifier = Modifier.weight(1f)) { Text("Add sample") }
                    OutlinedButton(onClick = { quickPanel = if (quickPanel == "ctd") "" else "ctd" }, modifier = Modifier.weight(1f)) { Text("CTD cast") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { quickPanel = if (quickPanel == "secchi") "" else "secchi" }, modifier = Modifier.weight(1f)) { Text("Secchi") }
                    OutlinedButton(
                        onClick = {
                            persistQuick(
                                As100AquaticFieldQcMethod,
                                mapOf(
                                    "station_water_depth_m" to waterDepth,
                                    "secchi_depth_m" to secchi,
                                    "planned_sample_count" to planned,
                                    "completed_sample_count" to completed
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Run QC") }
                }
            }

            when (quickPanel) {
                "sample" -> Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Quick sample", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(sampleId, { sampleId = it }, label = { Text("Sample ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(sampleDepth, { sampleDepth = it.aquaticNumericText() }, label = { Text("Depth (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(sampleType, { sampleType = it }, label = { Text("Sample type") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(
                            onClick = {
                                persistQuick(
                                    As100AquaticSampleRecordMethod,
                                    mapOf(
                                        "sample_id" to sampleId,
                                        "station_id" to station,
                                        "visit_id" to visit,
                                        "target_depth_m" to sampleDepth,
                                        "actual_depth_m" to sampleDepth,
                                        "sample_type" to sampleType
                                    )
                                )
                            },
                            enabled = sampleId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Record sample") }
                    }
                }

                "ctd" -> Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Quick CTD / sonde cast", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(ctdCastId, { ctdCastId = it }, label = { Text("Cast ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(ctdInstrumentId, { ctdInstrumentId = it }, label = { Text("Instrument ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(ctdDepth, { ctdDepth = it.aquaticNumericText() }, label = { Text("Maximum depth (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(
                            onClick = {
                                persistQuick(
                                    As100AquaticCtdCastMethod,
                                    mapOf(
                                        "cast_id" to ctdCastId,
                                        "instrument_id" to ctdInstrumentId,
                                        "station_id" to station,
                                        "visit_id" to visit,
                                        "max_depth_m" to ctdDepth
                                    )
                                )
                            },
                            enabled = ctdCastId.isNotBlank() && ctdInstrumentId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Record cast") }
                    }
                }

                "secchi" -> Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Quick Secchi", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(secchiDisappear, { secchiDisappear = it.aquaticNumericText() }, label = { Text("Disappearance depth (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(secchiReappear, { secchiReappear = it.aquaticNumericText() }, label = { Text("Reappearance depth (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(
                            onClick = {
                                persistQuick(
                                    As100AquaticSecchiMethod,
                                    mapOf(
                                        "disappearance_depth_m" to secchiDisappear,
                                        "reappearance_depth_m" to secchiReappear,
                                        "water_depth_m" to waterDepth,
                                        "calculate_carlson_tsi" to "false"
                                    )
                                )
                            },
                            enabled = secchiDisappear.isNotBlank() || secchiReappear.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Record Secchi") }
                    }
                }
            }

            if (quickStatus.isNotBlank()) {
                Text(quickStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("Refresh") }
                OutlinedButton(
                    onClick = {
                        AquaticFieldRepository.clearActiveVisit(appContext)
                        quickPanel = ""
                        refresh()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { refresh(completeVisit = true, submit = true) },
                enabled = station.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Finish station")
            }
            Text(
                "Quick actions write durable local field state through the same aquatic method engines. Dashboard Refresh reads that state without closing the MethodMesh execution; Finish returns the selected station snapshot.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun jsonArrayText(raw: String): String = runCatching {
    val arr = JSONArray(raw.ifBlank { "[]" })
    buildList {
        for (i in 0 until arr.length()) add(arr.optString(i))
    }.filter { it.isNotBlank() }.joinToString(", ")
}.getOrDefault("")

private fun String.aquaticNumericText(): String =
    filter { it.isDigit() || it == '-' || it == '.' }.let { filtered ->
        val minus = if (filtered.startsWith("-")) "-" else ""
        val body = filtered.removePrefix("-")
        minus + body.split('.').let { parts ->
            parts.first() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
        }
    }
