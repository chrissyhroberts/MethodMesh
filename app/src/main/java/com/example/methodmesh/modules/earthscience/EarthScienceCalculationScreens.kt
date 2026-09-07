package com.example.methodmesh.modules.earthscience

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

internal data class EarthScienceUiInput(
    val id: String,
    val label: String,
    val defaultValue: String,
    val kind: Kind = Kind.NUMBER,
    val choices: List<String> = emptyList(),
    val showWhen: Pair<String, String>? = null
) {
    enum class Kind { NUMBER, TEXT, CHOICE }
}

internal class EarthScienceCalculationScreen(
    override val capabilityId: String,
    override val title: String,
    override val description: String,
    private val method: EarthScienceMethod,
    private val mainResultField: String,
    private val inputs: List<EarthScienceUiInput>
) : CapabilityScreenSpec {

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val initial = inputs.associate { input ->
            input.id to (
                context.action.settings[input.id]
                    ?: context.action.settings["input_${input.id}"]
                    ?: input.defaultValue
                )
        }
        var valuesJson by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(JSONObject(initial).toString())
        }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun currentValues(): Map<String, String> = jsonObjectToStringMap(JSONObject(valuesJson))
        fun update(id: String, value: String) {
            val next = JSONObject(valuesJson)
            next.put(id, value)
            valuesJson = next.toString()
            resultValuesJson = ""
        }
        fun executionFromResultValues(): ExecutionResult? {
            if (resultValuesJson.isBlank()) return null
            return method.resultFromValues(jsonObjectToStringMap(JSONObject(resultValuesJson)), context.request.invocationContext)
        }
        fun runCalculation(autoSubmit: Boolean) {
            val calculated = method.calculate(currentValues())
            resultValuesJson = JSONObject(calculated).toString()
            val execution = method.resultFromValues(calculated, context.request.invocationContext)
            if (autoSubmit) onConfirmed(execution)
        }

        val current = currentValues()
        val result = remember(resultValuesJson) { executionFromResultValues() }
        val preview = result?.let { execution ->
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            val main = fields[mainResultField]?.toString().orEmpty()
            val errorField = method.descriptor.outputs.firstOrNull { it.endsWith("_error") }
            val error = errorField?.let { fields[it]?.toString().orEmpty() }.orEmpty()
            when {
                main.isNotBlank() -> mapOf(mainResultField to main)
                error.isNotBlank() && errorField != null -> mapOf(errorField to error)
                else -> emptyMap()
            }
        }.orEmpty()

        LaunchedEffect(valuesJson) {
            context.onSettingsChanged(currentValues())
        }

        LaunchedEffect(context.presentationMode, context.submitsImmediately) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && context.submitsImmediately && !launched) {
                launched = true
                runCalculation(autoSubmit = true)
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = preview,
            onBack = onBack,
            onRetry = { runCalculation(autoSubmit = false) },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            inputs.forEach { input ->
                val visibleByDependency = input.showWhen?.let { current[it.first] == it.second } ?: true
                if (visibleByDependency && context.settingShouldBeShown(input.id)) {
                    when (input.kind) {
                        EarthScienceUiInput.Kind.CHOICE -> ChoiceRow(
                            label = input.label,
                            selected = current[input.id].orEmpty(),
                            choices = input.choices,
                            onSelected = { update(input.id, it) }
                        )
                        EarthScienceUiInput.Kind.NUMBER -> OutlinedTextField(
                            value = current[input.id].orEmpty(),
                            onValueChange = { update(input.id, it.earthNumericText()) },
                            label = { Text(input.label) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            singleLine = true
                        )
                        EarthScienceUiInput.Kind.TEXT -> OutlinedTextField(
                            value = current[input.id].orEmpty(),
                            onValueChange = { update(input.id, it) },
                            label = { Text(input.label) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            singleLine = true
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { runCalculation(autoSubmit = false) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (result == null) "Calculate" else "Calculate again")
            }
            if (resultValuesJson.isNotBlank()) {
                val resultValues = jsonObjectToStringMap(JSONObject(resultValuesJson))
                val mainText = resultValues[mainResultField].orEmpty()
                val errorField = method.descriptor.outputs.firstOrNull { it.endsWith("_error") }
                val errorText = errorField?.let { resultValues[it].orEmpty() }.orEmpty()
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        mainText.isNotBlank() -> mainText
                        errorText.isNotBlank() -> "Calculation failed: $errorText"
                        else -> "No result was returned."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: String, choices: List<String>, onSelected: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
    choices.chunked(3).forEach { rowChoices ->
        Row(Modifier.fillMaxWidth()) {
            rowChoices.forEach { choice ->
                val display = choice.replace('_', ' ').replaceFirstChar { it.uppercase() }
                if (choice == selected) {
                    Button(onClick = { onSelected(choice) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ $display") }
                } else {
                    OutlinedButton(onClick = { onSelected(choice) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text(display) }
                }
            }
            repeat(3 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun String.earthNumericText(): String =
    filter { it.isDigit() || it == '-' || it == '.' }.let { filtered ->
        val minus = if (filtered.startsWith("-")) "-" else ""
        val body = filtered.removePrefix("-")
        minus + body.split('.').let { parts ->
            parts.first() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
        }
    }

private fun jsonObjectToStringMap(json: JSONObject): Map<String, String> = buildMap {
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        put(key, json.optString(key, ""))
    }
}

object GeodesyDistanceBearingScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100GeodesyDistanceBearingMethod.ID,
    title = "Geodesic distance and bearing",
    description = "Distance and initial/final bearing between two WGS84 positions.",
    method = As100GeodesyDistanceBearingMethod,
    mainResultField = GeodesyDistanceFields.RESULT,
    inputs = listOf(
        EarthScienceUiInput("latitude_1", "Latitude 1", "0"), EarthScienceUiInput("longitude_1", "Longitude 1", "0"),
        EarthScienceUiInput("latitude_2", "Latitude 2", "0"), EarthScienceUiInput("longitude_2", "Longitude 2", "0")
    )
)

object GeodesyDestinationScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100GeodesyDestinationMethod.ID,
    title = "Geodesic destination",
    description = "Project a WGS84 coordinate by initial bearing and distance.",
    method = As100GeodesyDestinationMethod,
    mainResultField = GeodesyDestinationFields.RESULT,
    inputs = listOf(
        EarthScienceUiInput("latitude", "Start latitude", "0"), EarthScienceUiInput("longitude", "Start longitude", "0"),
        EarthScienceUiInput("bearing_deg", "Initial bearing (°)", "0"), EarthScienceUiInput("distance_m", "Distance (m)", "100")
    )
)

object GeodesyWgs84ToUtmScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100GeodesyWgs84ToUtmMethod.ID,
    title = "WGS84 to UTM",
    description = "Convert decimal latitude/longitude to UTM.",
    method = As100GeodesyWgs84ToUtmMethod,
    mainResultField = GeodesyUtmFields.RESULT,
    inputs = listOf(EarthScienceUiInput("latitude", "Latitude", "0"), EarthScienceUiInput("longitude", "Longitude", "0"))
)

object GeodesyUtmToWgs84Screen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100GeodesyUtmToWgs84Method.ID,
    title = "UTM to WGS84",
    description = "Paste the complete UTM line from WGS84 → UTM, or enter its four parts separately. Example: 30N 699316.2 E 5710164.4 N.",
    method = As100GeodesyUtmToWgs84Method,
    mainResultField = GeodesyWgs84Fields.RESULT,
    inputs = listOf(
        EarthScienceUiInput("utm_text", "Paste complete UTM result (optional)", "", EarthScienceUiInput.Kind.TEXT),
        EarthScienceUiInput("zone", "Zone number — e.g. 30 from 30N", "31"),
        EarthScienceUiInput("hemisphere", "Hemisphere — N or S", "N", EarthScienceUiInput.Kind.CHOICE, listOf("N", "S")),
        EarthScienceUiInput("easting_m", "Easting — number before E (m)", "500000"),
        EarthScienceUiInput("northing_m", "Northing — number before N (m)", "0")
    )
)

object StructuralPlaneScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100StructuralPlaneMethod.ID,
    title = "Structural plane",
    description = "Normalise strike/dip and derive dip direction and plane pole.",
    method = As100StructuralPlaneMethod,
    mainResultField = StructuralPlaneFields.RESULT,
    inputs = listOf(
        EarthScienceUiInput("strike_deg", "Strike (°)", "0"), EarthScienceUiInput("dip_deg", "Dip (°)", "30"),
        EarthScienceUiInput("convention", "Convention", "right_hand_rule", EarthScienceUiInput.Kind.CHOICE, listOf("right_hand_rule", "explicit_dip_direction")),
        EarthScienceUiInput("dip_direction_deg", "Dip direction (°)", "90", showWhen = "convention" to "explicit_dip_direction")
    )
)

object StructuralLineScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100StructuralLineMethod.ID,
    title = "Structural line",
    description = "Normalise geological trend and plunge.",
    method = As100StructuralLineMethod,
    mainResultField = StructuralLineFields.RESULT,
    inputs = listOf(EarthScienceUiInput("trend_deg", "Trend (°)", "0"), EarthScienceUiInput("plunge_deg", "Plunge (°)", "0"))
)

object StructuralPlaneIntersectionScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100StructuralPlaneIntersectionMethod.ID,
    title = "Plane intersection",
    description = "Calculate the line where two right-hand-rule planes intersect.",
    method = As100StructuralPlaneIntersectionMethod,
    mainResultField = StructuralIntersectionFields.RESULT,
    inputs = listOf(
        EarthScienceUiInput("strike_1_deg", "Plane 1 strike (°)", "0"), EarthScienceUiInput("dip_1_deg", "Plane 1 dip (°)", "30"),
        EarthScienceUiInput("strike_2_deg", "Plane 2 strike (°)", "90"), EarthScienceUiInput("dip_2_deg", "Plane 2 dip (°)", "30")
    )
)

object SoilTextureScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100SoilTextureMethod.ID,
    title = "USDA soil texture",
    description = "Classify sand, silt and clay percentages. Inputs must total approximately 100%.",
    method = As100SoilTextureMethod,
    mainResultField = SoilTextureFields.RESULT,
    inputs = listOf(
        EarthScienceUiInput("sand_pct", "Sand (%)", "40"), EarthScienceUiInput("silt_pct", "Silt (%)", "40"), EarthScienceUiInput("clay_pct", "Clay (%)", "20"),
        EarthScienceUiInput("sum_tolerance_pct", "Allowed total deviation (%)", "1")
    )
)

object SedimentGrainSizeScreen : CapabilityScreenSpec by EarthScienceCalculationScreen(
    capabilityId = As100SedimentGrainSizeMethod.ID,
    title = "Sediment grain size",
    description = "Convert between diameter and phi and return a Wentworth grain-size class.",
    method = As100SedimentGrainSizeMethod,
    mainResultField = SedimentGrainFields.RESULT,
    inputs = listOf(
        EarthScienceUiInput("input_mode", "Input unit", "diameter_mm", EarthScienceUiInput.Kind.CHOICE, listOf("diameter_mm", "phi")),
        EarthScienceUiInput("diameter_mm", "Diameter (mm)", "1", showWhen = "input_mode" to "diameter_mm"),
        EarthScienceUiInput("phi", "Phi", "0", showWhen = "input_mode" to "phi")
    )
)
