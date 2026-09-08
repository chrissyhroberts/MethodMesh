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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import org.json.JSONArray
import org.json.JSONObject

private data class DivingScreenInfo(val title: String, val description: String, val actionLabel: String)

private val screenInfo = mapOf(
    DivingIds.SAC_RMV to DivingScreenInfo("SAC / RMV", "Calculate gas consumption from pressure drop, cylinder volume, time and average depth.", "Calculate"),
    DivingIds.CYLINDER_CAPACITY to DivingScreenInfo("Cylinder gas", "Estimate nominal cylinder gas, reserve and usable volume.", "Calculate"),
    DivingIds.PPO2 to DivingScreenInfo("Oxygen partial pressure", "Calculate ppO₂ at depth from the analysed gas mix.", "Calculate"),
    DivingIds.MOD to DivingScreenInfo("Maximum operating depth", "Calculate the ppO₂ boundary depth from analysed oxygen and your configured limit.", "Calculate"),
    DivingIds.BEST_MIX to DivingScreenInfo("Nitrox best mix", "Calculate the maximum oxygen percentage meeting the configured ppO₂ boundary at depth.", "Calculate"),
    DivingIds.EAD to DivingScreenInfo("Equivalent air depth", "Calculate equivalent air depth for nitrox.", "Calculate"),
    DivingIds.END to DivingScreenInfo("Equivalent narcotic depth", "Calculate END for helium mixes using an explicit narcotic-gas assumption.", "Calculate"),
    DivingIds.GAS_DENSITY to DivingScreenInfo("Breathing gas density", "Estimate ideal-gas density of an O₂/N₂/He mix at depth.", "Calculate"),
    DivingIds.GAS_PLAN to DivingScreenInfo("Gas plan", "Calculate gas demand across entered depth/time segments and compare with reserve.", "Calculate gas plan"),
    DivingIds.BAILOUT_PLAN to DivingScreenInfo("Bailout gas", "Calculate simplified bailout gas from entered problem, ascent and stop assumptions.", "Calculate bailout"),
    DivingIds.NAVIGATION to DivingScreenInfo("Dive navigation", "Calculate reciprocal heading and simple time/distance navigation.", "Calculate"),
    DivingIds.LIFT to DivingScreenInfo("Lift bag physics", "Estimate the minimum displacement and surface-equivalent inflation gas.", "Calculate"),
    DivingIds.LOG_RECORD to DivingScreenInfo("Dive log", "Record a dive locally, including optional cylinder-consumption data and calculated RMV.", "Record dive"),
    DivingIds.GAS_ANALYSIS_RECORD to DivingScreenInfo("Gas analysis record", "Record the measured O₂/He composition for a cylinder.", "Record analysis"),
    DivingIds.CYLINDER_RECORD to DivingScreenInfo("Cylinder record", "Create or update a local cylinder inventory record.", "Save cylinder")
)

private fun methodFor(id: String): DivingCalculationMethod? = DivingMethods.byId(id) ?: DivingRecordMethods.byId(id)
private fun settingsFor(id: String): List<MethodSetting> = DivingSettings.forMethod(id) + DivingRecordSettings.forMethod(id)

class DivingSimpleCapabilityScreen(private val methodId: String) : CapabilityScreenSpec {
    override val capabilityId: String = methodId
    private val info = screenInfo.getValue(methodId)
    override val title: String = info.title
    override val description: String = info.description

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val method = methodFor(methodId) ?: return
        val specs = settingsFor(methodId)
        var settingsJson by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initialSettingsJson(specs, context.action.settings))
        }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var statusText by rememberSaveable { mutableStateOf("Ready.") }
        val settings = remember(settingsJson) { jsonToMap(settingsJson) }

        LaunchedEffect(settingsJson) { context.onSettingsChanged(settings) }

        fun runAction() {
            val merged = context.action.settings + settings
            val values = method.calculate(merged)
            if (values[DivingCommonFields.STATUS] == "succeeded") {
                when (methodId) {
                    DivingIds.LOG_RECORD -> values[DivingFields.LOG_RECORD_JSON]?.let { DivingRepository.saveDiveJson(appContext, it) }
                    DivingIds.GAS_ANALYSIS_RECORD -> values[DivingFields.GAS_ANALYSIS_RECORD_JSON]?.let { DivingRepository.saveAnalysisJson(appContext, it) }
                    DivingIds.CYLINDER_RECORD -> values[DivingFields.CYLINDER_RECORD_JSON]?.let { DivingRepository.saveCylinderJson(appContext, it) }
                }
            }
            val request = method.request(
                action = method.id,
                context = context.request.invocationContext.asMap(method.id) + merged,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = method.result(request, values, context.request.invocationContext)
            result = execution
            statusText = values[DivingCommonFields.ERROR].takeUnless { it.isNullOrBlank() }
                ?: values[DivingCommonFields.WARNING].takeUnless { it.isNullOrBlank() }
                ?: "Completed."
        }

        val genuineExternalAutoSubmit = context.submitsImmediately &&
            !context.request.source.equals("intent_test", ignoreCase = true)
        val hasRuntimeInputs = specs.any { context.settingShouldBeShown(it.id) }

        LaunchedEffect(context.presentationMode, context.action.settings, settingsJson) {
            if (!launched && (genuineExternalAutoSubmit || (context.isNativePresetRun && !hasRuntimeInputs))) {
                launched = true
                runAction()
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
            onRetry = { runAction() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            SafetyNotice(methodId)
            Spacer(Modifier.height(8.dp))
            DivingSettingsEditor(
                context = context,
                specs = specs,
                values = settings,
                onValue = { key, value -> settingsJson = mapToJson(settings + (key to value)) }
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = ::runAction, modifier = Modifier.fillMaxWidth()) { Text(info.actionLabel) }
            if (statusText.isNotBlank()) {
                Text(statusText, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

object DivingCapabilityScreens {
    private val simpleIds = listOf(
        DivingIds.SAC_RMV, DivingIds.CYLINDER_CAPACITY, DivingIds.PPO2, DivingIds.MOD,
        DivingIds.BEST_MIX, DivingIds.EAD, DivingIds.END, DivingIds.GAS_DENSITY,
        DivingIds.GAS_PLAN, DivingIds.BAILOUT_PLAN, DivingIds.NAVIGATION, DivingIds.LIFT,
        DivingIds.LOG_RECORD, DivingIds.GAS_ANALYSIS_RECORD, DivingIds.CYLINDER_RECORD
    )
    val all: List<CapabilityScreenSpec> = simpleIds.map(::DivingSimpleCapabilityScreen) + listOf(
        DivingPlannerDashboardCapabilityScreen,
        DivingLogDashboardCapabilityScreen,
        DivingGasDashboardCapabilityScreen
    )
}

@Composable
internal fun DivingSettingsEditor(
    context: CapabilityScreenContext,
    specs: List<MethodSetting>,
    values: Map<String, String>,
    onValue: (String, String) -> Unit
) {
    var lastGroup: String? = null
    specs.forEach { setting ->
        if (!context.settingShouldBeShown(setting.id)) return@forEach
        if (setting.group != null && setting.group != lastGroup) {
            if (lastGroup != null) Spacer(Modifier.height(8.dp))
            Text(setting.group.orEmpty(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            lastGroup = setting.group
        }
        when (setting) {
            is MethodSetting.BooleanSetting -> {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(setting.label)
                        setting.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Switch(checked = values[setting.id].toBoolean(), onCheckedChange = { onValue(setting.id, it.toString()) })
                }
            }
            is MethodSetting.ChoiceSetting -> ChoiceField(setting, values[setting.id].orEmpty()) { onValue(setting.id, it) }
            is MethodSetting.IntSetting -> TextValueField(setting.label, setting.description, values[setting.id].orEmpty(), true) { onValue(setting.id, it) }
            is MethodSetting.FloatSetting -> TextValueField(setting.label, setting.description, values[setting.id].orEmpty(), true) { onValue(setting.id, it) }
            is MethodSetting.TextSetting -> TextValueField(setting.label, setting.description, values[setting.id].orEmpty(), false) { onValue(setting.id, it) }
            is MethodSetting.MultiChoiceSetting -> TextValueField(setting.label, setting.description, values[setting.id].orEmpty(), false) { onValue(setting.id, it) }
        }
    }
}

@Composable
private fun ChoiceField(setting: MethodSetting.ChoiceSetting, value: String, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(setting.label, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value.ifBlank { setting.defaultValue }) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            setting.choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice) }, onClick = { onValue(choice); expanded = false })
            }
        }
        setting.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TextValueField(label: String, description: String?, value: String, numeric: Boolean, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(if (numeric) it.divingNumericText() else it) },
        label = { Text(label) },
        supportingText = description?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = description == null || description.length < 120
    )
}

@Composable
private fun SafetyNotice(methodId: String) {
    val safetySupporting = methodId in setOf(
        DivingIds.PPO2, DivingIds.MOD, DivingIds.BEST_MIX, DivingIds.EAD, DivingIds.END,
        DivingIds.GAS_DENSITY, DivingIds.GAS_PLAN, DivingIds.BAILOUT_PLAN, DivingIds.LIFT
    )
    if (!safetySupporting) return
    Card(Modifier.fillMaxWidth()) {
        Text(
            "Planning support only. MethodMesh reports calculations against your entered assumptions; it does not certify a dive as safe or replace training, procedures, supervision, dive computers, tables or equipment manuals.",
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

internal fun initialSettingsJson(specs: List<MethodSetting>, supplied: Map<String, String>): String {
    val map = linkedMapOf<String, String>()
    specs.forEach { setting ->
        map[setting.id] = supplied[setting.id] ?: supplied["input_${setting.id}"] ?: setting.defaultString()
    }
    return mapToJson(map)
}

internal fun MethodSetting.defaultString(): String = when (this) {
    is MethodSetting.BooleanSetting -> defaultValue.toString()
    is MethodSetting.IntSetting -> defaultValue.toString()
    is MethodSetting.FloatSetting -> defaultValue.toString()
    is MethodSetting.TextSetting -> defaultValue
    is MethodSetting.ChoiceSetting -> defaultValue
    is MethodSetting.MultiChoiceSetting -> defaultValue
}

internal fun mapToJson(map: Map<String, String>): String = JSONObject(map).toString()
internal fun jsonToMap(raw: String): Map<String, String> = runCatching {
    val o = JSONObject(raw)
    o.keys().asSequence().associateWith { o.optString(it) }
}.getOrDefault(emptyMap())

private fun String.divingNumericText(): String = filter { it.isDigit() || it == '-' || it == '.' }.let { filtered ->
    val minus = if (filtered.startsWith("-")) "-" else ""
    val body = filtered.removePrefix("-")
    minus + body.split('.').let { parts -> parts.first() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else "" }
}

internal fun dashboardRecentLines(raw: String): List<String> = runCatching {
    val a = JSONArray(raw.ifBlank { "[]" })
    (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let { o ->
            val site = o.optString("site").ifBlank { "Dive" }
            val date = o.optString("date_time_iso").take(10)
            val depth = o.optDouble("max_depth_m", 0.0)
            val mins = o.optDouble("duration_min", 0.0)
            "$date · $site · ${f(depth, 1)} m · ${f(mins, 0)} min"
        }
    }
}.getOrDefault(emptyList())
