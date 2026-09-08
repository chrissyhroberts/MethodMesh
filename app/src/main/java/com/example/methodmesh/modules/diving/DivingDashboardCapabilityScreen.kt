package com.example.methodmesh.modules.diving

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

private enum class DivingDashboardKind { PLANNER, LOG, GAS }

private data class DivingDashboardDefinition(
    val method: DivingCalculationMethod,
    val kind: DivingDashboardKind,
    val title: String,
    val description: String
)

object DivingPlannerDashboardCapabilityScreen : CapabilityScreenSpec by DivingPersistentDashboardScreen(
    DivingDashboardDefinition(
        DivingMethods.dashboard,
        DivingDashboardKind.PLANNER,
        "Dive planner dashboard",
        "Live planning snapshot for gas margin, ppO₂, MOD, END and gas density. It does not calculate decompression obligations."
    )
)

object DivingLogDashboardCapabilityScreen : CapabilityScreenSpec by DivingPersistentDashboardScreen(
    DivingDashboardDefinition(
        DivingRecordMethods.logDashboard,
        DivingDashboardKind.LOG,
        "Dive log dashboard",
        "Summary of dives stored locally by the MethodMesh diving module."
    )
)

object DivingGasDashboardCapabilityScreen : CapabilityScreenSpec by DivingPersistentDashboardScreen(
    DivingDashboardDefinition(
        DivingRecordMethods.gasDashboard,
        DivingDashboardKind.GAS,
        "Cylinder / gas dashboard",
        "Local cylinder inventory with nominal stored gas and the latest recorded gas-analysis context."
    )
)

private class DivingPersistentDashboardScreen(
    private val definition: DivingDashboardDefinition
) : CapabilityScreenSpec {
    override val capabilityId: String = definition.method.id
    override val title: String = definition.title
    override val description: String = definition.description

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val specs = when (definition.kind) {
            DivingDashboardKind.PLANNER -> DivingSettings.forMethod(definition.method.id)
            else -> DivingRecordSettings.forMethod(definition.method.id)
        }
        var settingsJson by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initialSettingsJson(specs, context.action.settings))
        }
        var snapshotSettingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var attempted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var loading by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var showInputs by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.isNativePresetRun && specs.any { context.settingShouldBeShown(it.id) })
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val settings = remember(settingsJson) { jsonToMap(settingsJson) }
        val values = remember(valuesJson) { jsonToMap(valuesJson) }

        LaunchedEffect(settingsJson) { context.onSettingsChanged(settings) }

        fun effectiveSettings(base: Map<String, String>): Map<String, String> = when (definition.kind) {
            DivingDashboardKind.PLANNER -> base
            DivingDashboardKind.LOG -> base + mapOf(
                "dive_entries_json" to DivingRepository.divesJson(DivingRepository.dives(appContext))
            )
            DivingDashboardKind.GAS -> base + mapOf(
                "cylinders_json" to DivingRepository.cylindersJson(DivingRepository.cylinders(appContext)),
                "analyses_json" to DivingRepository.analysesJson(DivingRepository.analyses(appContext))
            )
        }

        fun makeExecution(baseSettings: Map<String, String>, calculated: Map<String, String>): ExecutionResult {
            val request = definition.method.request(
                action = definition.method.id,
                context = context.request.invocationContext.asMap(definition.method.id) + context.action.settings + baseSettings,
                signals = emptyList(),
                inputs = emptyList()
            )
            return definition.method.result(request, calculated, context.request.invocationContext)
        }

        fun refresh(baseSettings: Map<String, String> = settings) {
            if (loading) return
            loading = true
            val mergedBase = context.action.settings + baseSettings
            val effective = effectiveSettings(mergedBase)
            val calculated = definition.method.calculate(effective)
            valuesJson = mapToJson(calculated)
            snapshotSettingsJson = mapToJson(baseSettings)
            result = makeExecution(baseSettings, calculated)
            loading = false
        }

        // Reconstruct a real ExecutionResult after configuration recreation from
        // the saved successful values/settings snapshot instead of committing a new run.
        LaunchedEffect(valuesJson, snapshotSettingsJson) {
            if (result == null && valuesJson.isNotBlank() && snapshotSettingsJson.isNotBlank()) {
                val restoredValues = jsonToMap(valuesJson)
                val restoredSettings = jsonToMap(snapshotSettingsJson)
                result = makeExecution(restoredSettings, restoredValues)
            }
        }

        LaunchedEffect(Unit) {
            if (!attempted) {
                attempted = true
                refresh()
            }
        }

        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard ||
                context.isNativePresetRun ||
                context.request.source.equals("intent_test", ignoreCase = true)
        val scaffoldResult = if (keepLiveDashboard) null else result

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { refresh() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (definition.kind == DivingDashboardKind.PLANNER) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Planning support only — no NDL/decompression schedule, repetitive tissue model, omitted-decompression procedure or equipment-specific life-support rule is generated.",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (specs.isNotEmpty()) {
                OutlinedButton(onClick = { showInputs = !showInputs }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showInputs) "Hide inputs" else "Show inputs")
                }
                if (showInputs) {
                    Spacer(Modifier.height(6.dp))
                    DivingSettingsEditor(
                        context = context,
                        specs = specs,
                        values = settings,
                        onValue = { key, value -> settingsJson = mapToJson(settings + (key to value)) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(onClick = { refresh() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Refreshing…" else "Refresh")
            }
            Spacer(Modifier.height(10.dp))

            when (definition.kind) {
                DivingDashboardKind.PLANNER -> PlannerCards(values)
                DivingDashboardKind.LOG -> LogCards(values)
                DivingDashboardKind.GAS -> GasCards(values)
            }

            values[DivingCommonFields.WARNING]?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                WarningCard(it)
            }
            values[DivingCommonFields.ERROR]?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                WarningCard(it)
            }

            if (keepLiveDashboard && result != null && values[DivingCommonFields.STATUS] == "succeeded") {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { result?.let(onConfirmed) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (context.isNativePresetRun) "Finish" else "Use this snapshot")
                }
            }
        }
    }
}

@Composable
private fun PlannerCards(v: Map<String, String>) {
    DashboardMetricRow(
        "Depth", v[DivingFields.DASHBOARD_DEPTH_M]?.plus(" m").orEmpty(),
        "ppO₂", v[DivingFields.DASHBOARD_PPO2_BAR]?.plus(" bar").orEmpty()
    )
    DashboardMetricRow(
        "MOD boundary", v[DivingFields.DASHBOARD_MOD_M]?.plus(" m").orEmpty(),
        "END", v[DivingFields.DASHBOARD_END_M]?.plus(" m").orEmpty()
    )
    DashboardMetricRow(
        "Gas density", v[DivingFields.DASHBOARD_GAS_DENSITY_G_L]?.plus(" g/L").orEmpty(),
        "End pressure", v[DivingFields.DASHBOARD_END_PRESSURE_BAR]?.plus(" bar").orEmpty()
    )
    DashboardMetricRow(
        "Gas margin", v[DivingFields.DASHBOARD_GAS_MARGIN_BAR]?.plus(" bar").orEmpty(),
        "Reserve", if (v[DivingFields.DASHBOARD_RESERVE_MET] == "true") "met" else "NOT met"
    )
}

@Composable
private fun LogCards(v: Map<String, String>) {
    DashboardMetricRow("Dives", v[DivingFields.LOG_COUNT].orEmpty(), "Total time", v[DivingFields.LOG_TOTAL_TIME_MIN]?.plus(" min").orEmpty())
    DashboardMetricRow("Deepest", v[DivingFields.LOG_DEEPEST_M]?.plus(" m").orEmpty(), "Mean RMV", v[DivingFields.LOG_MEAN_RMV_L_MIN]?.takeIf { it.isNotBlank() }?.plus(" L/min") ?: "—")
    val recent = dashboardRecentLines(v[DivingFields.LOG_RECENT_JSON].orEmpty())
    if (recent.isNotEmpty()) {
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Recent dives", fontWeight = FontWeight.SemiBold)
                recent.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

@Composable
private fun GasCards(v: Map<String, String>) {
    DashboardMetricRow("Cylinders", v[DivingFields.GAS_CYLINDER_COUNT].orEmpty(), "Analyses", v[DivingFields.GAS_ANALYSIS_COUNT].orEmpty())
    DashboardMetricRow("Nominal stored gas", v[DivingFields.GAS_TOTAL_NOMINAL_L]?.plus(" L").orEmpty(), "", "")
    val raw = v[DivingFields.GAS_INVENTORY_JSON].orEmpty()
    val lines = runCatching {
        val a = org.json.JSONArray(raw.ifBlank { "[]" })
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { item ->
                val c = item.optJSONObject("cylinder") ?: return@let null
                val analysis = item.optJSONObject("latest_analysis")
                val label = c.optString("label").ifBlank { c.optString("id") }
                val pressure = c.optDouble("current_pressure_bar", 0.0)
                val volume = c.optDouble("water_volume_l", 0.0)
                val mix = analysis?.let { " · ${f(it.optDouble("fo2_percent", 0.0), 1)}% O₂${if (it.optDouble("fhe_percent", 0.0) > 0) "/${f(it.optDouble("fhe_percent"), 1)}% He" else ""}" }.orEmpty()
                "$label · ${f(volume, 1)} L @ ${f(pressure, 0)} bar$mix"
            }
        }
    }.getOrDefault(emptyList())
    if (lines.isNotEmpty()) {
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Inventory", fontWeight = FontWeight.SemiBold)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

@Composable
private fun DashboardMetricRow(labelA: String, valueA: String, labelB: String, valueB: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(labelA, valueA, Modifier.weight(1f))
        if (labelB.isNotBlank()) MetricCard(labelB, valueB, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Check assumptions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
