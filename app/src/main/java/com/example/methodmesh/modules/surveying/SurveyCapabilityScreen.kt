package com.example.methodmesh.modules.surveying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

private enum class SurveyInputKind { Text, Number, Choice, Boolean }

private data class SurveyInput(
    val key: String,
    val label: String,
    val defaultValue: String = "",
    val kind: SurveyInputKind = SurveyInputKind.Number,
    val choices: List<String> = emptyList(),
    val multiline: Boolean = false,
    val hint: String = "",
    val showWhen: (Map<String, String>) -> Boolean = { true }
)

private data class AtomicSurveyScreenDefinition(
    val method: SurveyMethod,
    val title: String,
    val description: String,
    val inputs: List<SurveyInput>,
    val actionLabel: String = "Calculate"
)

private class AtomicSurveyCapabilityScreen(
    private val definition: AtomicSurveyScreenDefinition
) : CapabilityScreenSpec {
    override val capabilityId = definition.method.id
    override val title = definition.title
    override val description = definition.description

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val initial = remember(context.action.canonicalId) {
            definition.inputs.map { input ->
                context.action.settings[input.key]
                    ?: context.action.settings["input_${input.key}"]
                    ?: context.request.settings[input.key]
                    ?: context.request.settings["input_${input.key}"]
                    ?: input.defaultValue
            }
        }
        var serializedValues by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(encodeValues(initial))
        }
        var attempted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready.") }

        val values = decodeValues(serializedValues, definition.inputs.size)
        val valueMap = definition.inputs.indices.associate { definition.inputs[it].key to values[it] }

        fun setValue(index: Int, value: String) {
            val updated = values.toMutableList()
            updated[index] = value
            serializedValues = encodeValues(updated)
        }

        fun runCalculation() {
            attempted = true
            status = "Calculating…"
            val local = definition.inputs.indices.associate { definition.inputs[it].key to values[it] }
            context.onSettingsChanged(local)
            val merged = context.request.invocationContext.asMap(definition.method.id) +
                context.request.settings + context.action.settings + local
            val request = definition.method.request(
                action = definition.method.id,
                context = merged,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = definition.method.execute(request, null, context.request.source)
                .withInvocationContext(context.request.invocationContext)
            result = execution
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            status = fields[SurveyFields.MAIN_RESULT]?.toString().orEmpty().ifBlank {
                fields[SurveyFields.ERROR]?.toString().orEmpty().ifBlank { "Calculation complete." }
            }
            if (context.submitsImmediately) onConfirmed(execution)
        }

        // Deterministic calculations can safely reconstruct the ExecutionResult
        // after configuration recreation from saved inputs.
        LaunchedEffect(attempted, result) {
            if (attempted && result == null) runCalculation()
        }

        val shouldAutoRun = context.submitsImmediately ||
            (context.isNativePresetRun && context.runtimeInputFields.isEmpty())
        LaunchedEffect(context.presentationMode, shouldAutoRun) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && shouldAutoRun && !launched) {
                launched = true
                runCalculation()
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
            onRetry = { runCalculation() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(description, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                definition.inputs.forEachIndexed { index, input ->
                    if (!context.settingShouldBeShown(input.key)) return@forEachIndexed
                    if (!input.showWhen(valueMap)) return@forEachIndexed
                    when (input.kind) {
                        SurveyInputKind.Text, SurveyInputKind.Number -> {
                            OutlinedTextField(
                                value = values[index],
                                onValueChange = { setValue(index, it) },
                                label = { Text(input.label) },
                                supportingText = if (input.hint.isNotBlank()) ({ Text(input.hint) }) else null,
                                keyboardOptions = if (input.kind == SurveyInputKind.Number) {
                                    KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                } else KeyboardOptions.Default,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = !input.multiline,
                                minLines = if (input.multiline) 4 else 1
                            )
                        }
                        SurveyInputKind.Choice -> {
                            Text(input.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                input.choices.forEach { choice ->
                                    val selected = values[index] == choice
                                    if (selected) {
                                        Button(onClick = { setValue(index, choice) }, modifier = Modifier.weight(1f)) { Text("✓ $choice") }
                                    } else {
                                        OutlinedButton(onClick = { setValue(index, choice) }, modifier = Modifier.weight(1f)) { Text(choice) }
                                    }
                                }
                            }
                        }
                        SurveyInputKind.Boolean -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(input.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = values[index].equals("true", true),
                                    onCheckedChange = { setValue(index, it.toString()) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { runCalculation() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (attempted) "${definition.actionLabel} again" else definition.actionLabel)
                }
            }
        }
    }
}

private fun numeric(key: String, label: String, default: String = "0", hint: String = "", showWhen: (Map<String, String>) -> Boolean = { true }) =
    SurveyInput(key, label, default, SurveyInputKind.Number, hint = hint, showWhen = showWhen)
private fun text(key: String, label: String, default: String = "", multiline: Boolean = false, hint: String = "", showWhen: (Map<String, String>) -> Boolean = { true }) =
    SurveyInput(key, label, default, SurveyInputKind.Text, multiline = multiline, hint = hint, showWhen = showWhen)
private fun choice(key: String, label: String, default: String, choices: List<String>) =
    SurveyInput(key, label, default, SurveyInputKind.Choice, choices = choices)
private fun boolean(key: String, label: String, default: Boolean) =
    SurveyInput(key, label, default.toString(), SurveyInputKind.Boolean)

val BearingDistanceCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100BearingDistanceMethod,
        "Bearing and distance",
        "Inverse surveying calculation. Local-grid mode returns ΔE/ΔN, grid azimuth, quadrant bearing and distance. GPS mode is for field/navigation use and is not a cadastral geodesy engine.",
        listOf(
            choice("coordinate_mode", "Coordinate mode", "planar", listOf("planar", "gps")),
            numeric("start_easting", "Start easting", showWhen = { it["coordinate_mode"] != "gps" }),
            numeric("start_northing", "Start northing", showWhen = { it["coordinate_mode"] != "gps" }),
            text("start_elevation", "Start elevation (optional)", showWhen = { it["coordinate_mode"] != "gps" }),
            numeric("end_easting", "End easting", showWhen = { it["coordinate_mode"] != "gps" }),
            numeric("end_northing", "End northing", showWhen = { it["coordinate_mode"] != "gps" }),
            text("end_elevation", "End elevation (optional)", showWhen = { it["coordinate_mode"] != "gps" }),
            numeric("start_latitude", "Start latitude", showWhen = { it["coordinate_mode"] == "gps" }),
            numeric("start_longitude", "Start longitude", showWhen = { it["coordinate_mode"] == "gps" }),
            numeric("end_latitude", "End latitude", showWhen = { it["coordinate_mode"] == "gps" }),
            numeric("end_longitude", "End longitude", showWhen = { it["coordinate_mode"] == "gps" })
        )
    )
)

val ForwardCoordinateCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100ForwardCoordinateMethod, "Forward coordinate",
        "Project a local-grid coordinate from a start point, clockwise-from-north azimuth and horizontal distance.",
        listOf(numeric("start_easting", "Start easting"), numeric("start_northing", "Start northing"), numeric("azimuth_deg", "Azimuth (degrees)"), numeric("horizontal_distance_m", "Horizontal distance (m)"))
    )
)

val OffsetPointCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100OffsetPointMethod, "Offset point",
        "Create a coordinate from baseline chainage and signed perpendicular offset. Positive offset is to the right looking from baseline start to end.",
        listOf(
            numeric("start_easting", "Baseline start easting"), numeric("start_northing", "Baseline start northing"),
            numeric("end_easting", "Baseline end easting"), numeric("end_northing", "Baseline end northing", "100"),
            numeric("chainage_m", "Chainage (m)"), numeric("offset_right_m", "Offset right (m)")
        )
    )
)

val ChainageOffsetCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100ChainageOffsetMethod, "Chainage and offset",
        "Project a point onto a baseline. Chainage can fall outside the segment; positive offset is right of the baseline direction.",
        listOf(
            numeric("start_easting", "Baseline start easting"), numeric("start_northing", "Baseline start northing"),
            numeric("end_easting", "Baseline end easting"), numeric("end_northing", "Baseline end northing", "100"),
            numeric("point_easting", "Point easting"), numeric("point_northing", "Point northing")
        )
    )
)

val TraverseCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100TraverseMethod, "Traverse calculation",
        "Enter one leg per line as point ID, azimuth, distance. If a known closing coordinate is supplied, MethodMesh reports misclosure and can apply Bowditch or transit adjustment.",
        listOf(
            text("start_point_id", "Start point ID", "START"), numeric("start_easting", "Start easting"), numeric("start_northing", "Start northing"),
            text("traverse_legs", "Traverse legs", "P1,90,100\nP2,0,100", multiline = true, hint = "point_id,bearing_deg,distance_m — one per line"),
            text("close_easting", "Known close easting (optional)"), text("close_northing", "Known close northing (optional)"),
            choice("adjustment_mode", "Adjustment", "none", listOf("none", "bowditch", "transit")),
            text("minimum_relative_precision", "Minimum relative precision 1:n (optional)", hint = "Example: 5000 means require at least 1:5000")
        )
    )
)

val AreaCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100AreaMethod, "Area from coordinates",
        "Compute polygon area, signed area, perimeter and centroid. Coordinates may be ID,easting,northing or just easting,northing.",
        listOf(text("coordinates", "Polygon coordinates", "P1,0,0\nP2,10,0\nP3,10,10\nP4,0,10", multiline = true, hint = "One vertex per line; polygon closes automatically."))
    )
)

val LevelReduceCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100LevelReduceMethod, "Reduce levelling book",
        "Reduce a differential-levelling field book. A change point is recorded as FS followed by BS using the same station ID. Outputs include height of collimation, rise/fall, RL, ΣBS−ΣFS check and closure correction.",
        listOf(
            numeric("start_reduced_level_m", "Starting reduced level (m)", "100"),
            text("level_observations", "Levelling observations", "BM,BS,1.500,0\nP1,IS,2.000,25\nCP1,FS,2.500,25\nCP1,BS,1.000,0\nEND,FS,1.500,50", multiline = true, hint = "station,BS|IS|FS,reading_m,distance_from_previous_m"),
            text("known_close_reduced_level_m", "Known closing RL (optional)"),
            boolean("distribute_closure", "Distribute closure correction", true)
        )
    )
)

val GradeCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100GradeMethod, "Grade / rise-fall",
        "Convert signed rise/fall and horizontal distance to percent grade, slope angle and 1:n.",
        listOf(numeric("rise_m", "Rise (+) / fall (-) (m)"), numeric("horizontal_distance_m", "Horizontal distance (m)", "1"))
    )
)

val GpsAverageCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100GpsAverageMethod, "Average GPS fixes",
        "Average repeated phone/GNSS fixes and report RMS/max scatter. Accuracy weighting uses 1/accuracy² when reported accuracy is available.",
        listOf(
            text("gps_fixes", "GPS fixes", "", multiline = true, hint = "latitude,longitude,accuracy_m — one fix per line"),
            boolean("accuracy_weighted", "Weight by reported accuracy", true)
        )
    )
)

val IntersectionCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100IntersectionMethod, "Bearing-bearing intersection",
        "Intersect two local-grid bearing lines from known stations. The crossing angle is returned as a geometry-quality diagnostic.",
        listOf(
            numeric("station_a_easting", "Station A easting"), numeric("station_a_northing", "Station A northing"), numeric("bearing_a_deg", "Bearing from A (degrees)", "90"),
            numeric("station_b_easting", "Station B easting", "100"), numeric("station_b_northing", "Station B northing", "-100"), numeric("bearing_b_deg", "Bearing from B (degrees)", "0")
        )
    )
)

val SetoutCapabilityScreen: CapabilityScreenSpec = AtomicSurveyCapabilityScreen(
    AtomicSurveyScreenDefinition(
        As100SetoutMethod, "Local-grid set-out",
        "Calculate grid bearing and distance from the current/control coordinate to a target. For live GPS navigation, use MethodMesh's existing GPS target navigator rather than duplicating it here.",
        listOf(numeric("current_easting", "Current easting"), numeric("current_northing", "Current northing"), numeric("target_easting", "Target easting"), numeric("target_northing", "Target northing"))
    )
)

private const val VALUE_SEPARATOR = '\u001F'
private fun encodeValues(values: List<String>): String = values.joinToString(VALUE_SEPARATOR.toString())
private fun decodeValues(serialized: String, count: Int): List<String> {
    val split = serialized.split(VALUE_SEPARATOR)
    return List(count) { index -> split.getOrNull(index).orEmpty() }
}
