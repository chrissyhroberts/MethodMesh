package com.example.methodmesh.modules.earthscience

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import kotlin.math.roundToInt

private data class TimeSegment(val label: String, val youngerMa: Double, val olderMa: Double)

private val EON_SEGMENTS = listOf(
    TimeSegment("Phanerozoic", 0.0, 538.8),
    TimeSegment("Proterozoic", 538.8, 2500.0),
    TimeSegment("Archean", 2500.0, 4031.0),
    TimeSegment("Hadean", 4031.0, 4600.0)
)

private val PHANEROZOIC_ERA_SEGMENTS = listOf(
    TimeSegment("Cenozoic", 0.0, 66.0),
    TimeSegment("Mesozoic", 66.0, 251.902),
    TimeSegment("Paleozoic", 251.902, 538.8)
)

private val PHANEROZOIC_PERIOD_SEGMENTS = listOf(
    TimeSegment("Quaternary", 0.0, 2.58),
    TimeSegment("Neogene", 2.58, 23.04),
    TimeSegment("Paleogene", 23.04, 66.0),
    TimeSegment("Cretaceous", 66.0, 143.1),
    TimeSegment("Jurassic", 143.1, 201.4),
    TimeSegment("Triassic", 201.4, 251.902),
    TimeSegment("Permian", 251.902, 298.9),
    TimeSegment("Carboniferous", 298.9, 358.86),
    TimeSegment("Devonian", 358.86, 419.62),
    TimeSegment("Silurian", 419.62, 443.1),
    TimeSegment("Ordovician", 443.1, 486.85),
    TimeSegment("Cambrian", 486.85, 538.8)
)

object GeoTimeLookupScreen : CapabilityScreenSpec {
    override val capabilityId = As100GeoTimeLookupMethod.ID
    override val title = "Geological time lookup"
    override val description = "Move through Earth history or enter an age in millions of years before present (Ma)."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var ageText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["age_ma"] ?: context.action.settings["input_age_ma"] ?: "66")
        }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        val age = ageText.toDoubleOrNull()?.coerceIn(0.0, 4600.0) ?: 0.0
        val liveValues = remember(ageText) { As100GeoTimeLookupMethod.calculate(mapOf("age_ma" to ageText)) }
        val liveResult = liveValues[GeoTimeFields.RESULT].orEmpty()
        val liveError = liveValues[GeoTimeFields.ERROR].orEmpty()

        fun capture(autoSubmit: Boolean) {
            val values = As100GeoTimeLookupMethod.calculate(mapOf("age_ma" to ageText))
            resultValuesJson = JSONObject(values).toString()
            if (autoSubmit) onConfirmed(As100GeoTimeLookupMethod.resultFromValues(values, context.request.invocationContext))
        }

        val result = remember(resultValuesJson) {
            if (resultValuesJson.isBlank()) null else {
                val json = JSONObject(resultValuesJson)
                val values = buildMap<String, String> {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, json.optString(key, ""))
                    }
                }
                As100GeoTimeLookupMethod.resultFromValues(values, context.request.invocationContext)
            }
        }
        val preview = result?.let { execution ->
            OutputFormatter.fields(execution, includeProvenance = false)[GeoTimeFields.RESULT]
                ?.let { mapOf(GeoTimeFields.RESULT to it) }
                .orEmpty()
        }.orEmpty()

        LaunchedEffect(ageText) { context.onSettingsChanged(mapOf("age_ma" to ageText)) }
        LaunchedEffect(context.presentationMode, context.submitsImmediately) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && context.submitsImmediately && !launched) {
                launched = true
                capture(autoSubmit = true)
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
            onRetry = { resultValuesJson = "" },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = ageText,
                onValueChange = { raw ->
                    ageText = raw.filter { it.isDigit() || it == '.' }.let { filtered ->
                        val parts = filtered.split('.')
                        if (parts.size <= 1) filtered else parts.first() + "." + parts.drop(1).joinToString("")
                    }
                    resultValuesJson = ""
                },
                label = { Text("Age (Ma)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("0 Ma = present · 4600 Ma = formation of Earth", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = age.toFloat(),
                onValueChange = {
                    ageText = if (it < 10f) "%.3f".format(java.util.Locale.US, it).trimEnd('0').trimEnd('.')
                    else it.roundToInt().toString()
                    resultValuesJson = ""
                },
                valueRange = 0f..4600f,
                modifier = Modifier.fillMaxWidth()
            )

            if (liveResult.isNotBlank()) {
                Text(liveResult, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            } else if (liveError.isNotBlank()) {
                Text("Lookup failed: $liveError", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(10.dp))
            Text("Earth history — present → 4600 Ma", style = MaterialTheme.typography.labelLarge)
            TimelineBand("Eons", EON_SEGMENTS, age)
            if (age < 538.8) {
                Spacer(Modifier.height(5.dp))
                TimelineBand("Phanerozoic eras", PHANEROZOIC_ERA_SEGMENTS, age)
                Spacer(Modifier.height(5.dp))
                TimelineBand("Periods", PHANEROZOIC_PERIOD_SEGMENTS, age)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Highlighted phase contains the selected age. The period view expands only within the Phanerozoic so recent Earth history remains readable.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { capture(autoSubmit = false) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (result == null) "Use this age" else "Update result")
            }
        }
    }
}

@Composable
private fun TimelineBand(title: String, segments: List<TimeSegment>, ageMa: Double) {
    Text(title, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().height(34.dp)) {
        segments.forEachIndexed { index, segment ->
            val selected = ageMa >= segment.youngerMa && (ageMa < segment.olderMa || (segment.olderMa == 4600.0 && ageMa <= 4600.0))
            val duration = (segment.olderMa - segment.youngerMa).toFloat().coerceAtLeast(0.001f)
            val base = if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            Surface(
                modifier = Modifier.weight(duration).height(34.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else base
            ) {
                Text(
                    text = if (selected) segment.label else "",
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
