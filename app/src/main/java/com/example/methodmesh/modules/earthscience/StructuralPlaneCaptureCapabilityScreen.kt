package com.example.methodmesh.modules.earthscience

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import kotlin.math.abs

object StructuralPlaneCaptureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100StructuralPlaneCaptureMethod.ID
    override val title = "Capture structural plane"
    override val description = "Development measurement of a rock surface's direction (strike) and tilt (dip). Place the back of the phone flat on the surface, then rotate it on that surface until the phone's top edge is horizontal; the top edge then points along strike and the sideways tilt gives dip."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var maxLevelErrorText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["max_level_error_deg"] ?: context.action.settings["input_max_level_error_deg"] ?: "5")
        }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Waiting for orientation sensors.") }

        DisposableEffect(androidContext) {
            PhoneSensorRepository.start(androidContext)
            onDispose { PhoneSensorRepository.stop() }
        }

        LaunchedEffect(maxLevelErrorText) {
            context.onSettingsChanged(mapOf("strike_reference" to "magnetic", "max_level_error_deg" to maxLevelErrorText))
        }

        val heading = PhoneSensorRepository.headingDegrees?.toDouble()
        val pitch = PhoneSensorRepository.pitchDegrees?.toDouble()
        val roll = PhoneSensorRepository.rollDegrees?.toDouble()
        val magnetometerAccuracy = PhoneSensorRepository.readings["magnetometer"]?.accuracy
        val levelError = pitch?.let(::abs)
        val maxLevelError = maxLevelErrorText.toDoubleOrNull()?.coerceIn(0.5, 20.0) ?: 5.0
        val ready = heading != null && pitch != null && roll != null && levelError != null && levelError <= maxLevelError

        fun capture(autoSubmit: Boolean) {
            if (!ready || heading == null || pitch == null || roll == null) {
                status = if (levelError != null && levelError > maxLevelError) {
                    "Top edge is not level along strike: ${formatEarth(levelError, 1)}° error; limit ${formatEarth(maxLevelError, 1)}°."
                } else {
                    "Orientation sensors are not ready."
                }
                return
            }
            val values = As100StructuralPlaneCaptureMethod.fromSensor(heading, pitch, roll, magnetometerAccuracy, maxLevelError)
            resultValuesJson = JSONObject(values).toString()
            status = "Plane captured. This remains a Development measurement until field-validated against a geological compass."
            if (autoSubmit) onConfirmed(As100StructuralPlaneCaptureMethod.resultFromValues(values, context.request.invocationContext))
        }

        val result = remember(resultValuesJson) {
            if (resultValuesJson.isBlank()) null else {
                val json = JSONObject(resultValuesJson)
                val values = buildMap<String, String> {
                    val keys = json.keys()
                    while (keys.hasNext()) { val key = keys.next(); put(key, json.optString(key, "")) }
                }
                As100StructuralPlaneCaptureMethod.resultFromValues(values, context.request.invocationContext)
            }
        }
        val preview = result?.let { execution ->
            OutputFormatter.fields(execution, includeProvenance = false)[StructuralPlaneFields.RESULT]?.let { mapOf(StructuralPlaneFields.RESULT to it) }.orEmpty()
        }.orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = preview,
            onBack = onBack,
            onRetry = { resultValuesJson = ""; status = "Ready for another plane." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("How to hold it", style = MaterialTheme.typography.titleSmall)
            Text("1. Put the BACK of the phone flat against the planar rock/soil surface.", style = MaterialTheme.typography.bodySmall)
            Text("2. Without lifting it, rotate the phone until its TOP EDGE (the short edge above the screen) runs horizontally across the surface.", style = MaterialTheme.typography.bodySmall)
            Text("3. That top-edge direction is STRIKE. The surface's sideways tilt across the phone is DIP.", style = MaterialTheme.typography.bodySmall)
            Text("Think:  ← strike — [ PHONE ] — strike →   |   sideways tilt = dip", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("North reference: magnetic", style = MaterialTheme.typography.bodySmall)
            Text("Heading / strike: ${heading?.let { formatEarth(it, 1) } ?: "waiting"}°", style = MaterialTheme.typography.bodySmall)
            Text("Along-strike level error: ${levelError?.let { formatEarth(it, 1) } ?: "waiting"}°", style = MaterialTheme.typography.bodySmall)
            Text("Roll / candidate dip: ${roll?.let { formatEarth(abs(it).coerceIn(0.0, 90.0), 1) } ?: "waiting"}°", style = MaterialTheme.typography.bodySmall)
            Text("Magnetometer accuracy state: ${magnetometerAccuracy?.toString() ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
            if (context.settingShouldBeShown("max_level_error_deg")) {
                OutlinedTextField(
                    value = maxLevelErrorText,
                    onValueChange = { maxLevelErrorText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Maximum along-strike level error (°)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (ready) "Ready: the top edge is level, so its compass direction can be used as strike." else "Not ready: keep the phone flat on the surface and rotate it until the TOP EDGE is horizontal.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (result == null) {
                Button(onClick = { capture(autoSubmit = context.submitsImmediately) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text("Capture plane")
                }
            }
        }
    }
}
