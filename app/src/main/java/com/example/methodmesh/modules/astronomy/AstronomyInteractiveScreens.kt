package com.example.methodmesh.modules.astronomy

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapturedExecutionRecovery
import com.example.methodmesh.transport.workflow.ui.CanonicalCommittedResultActions
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToLong

object ImageScaleCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ImageScaleMethod.id
    override val title = "Field of view & sampling"
    override val description = "Use sliders for quick exploration and the text boxes for exact optical values."

    private data class Field(
        val key: String,
        val label: String,
        val unit: String,
        val sliderMin: Float,
        val sliderMax: Float,
        val fallback: String
    )

    private val fields = listOf(
        Field("focal_length_mm", "Focal length", "mm", 10f, 2000f, "400"),
        Field("aperture_mm", "Aperture", "mm", 10f, 500f, "80"),
        Field("pixel_size_micron", "Pixel size", "µm", 0.5f, 10f, "3.76"),
        Field("sensor_width_mm", "Sensor width", "mm", 1f, 50f, "17.7"),
        Field("sensor_height_mm", "Sensor height", "mm", 1f, 50f, "13.4"),
        Field("multiplier", "Barlow / reducer", "×", 0.2f, 5f, "1.0")
    )

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val initial = remember(context.action.settings) {
            fields.associate { field ->
                field.key to (context.action.settings[field.key]
                    ?: context.action.settings["input_${field.key}"]
                    ?: field.fallback)
            }
        }
        var settingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(JSONObject(initial).toString()) }
        val settings = remember(settingsJson) { jsonToInteractiveMap(settingsJson) }
        var result by rememberRetainedExecutionResult(context.action.canonicalId)
        var hadResult by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("") }
        var actionsBusy by remember { mutableStateOf(false) }
        val values = result?.observations?.singleOrNull()?.values.orEmpty()
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun update(key: String, value: String) {
            settingsJson = JSONObject(settingsMap(settingsJson) + (key to value)).toString()
        }

        fun calculate() {
            if (actionsBusy) return
            try {
                val frozen = settingsMap(settingsJson).toMap()
                validateAstronomyCalculation(As100ImageScaleMethod, frozen)
                val calculated = As100ImageScaleMethod.calculate(frozen)
                result = methodExecution(As100ImageScaleMethod, context, calculated, frozen)
                hadResult = true
                status = ""
            } catch (failure: Exception) { status = failure.message ?: "Check optical inputs." }
        }

        LaunchedEffect(settingsJson) { context.onSettingsChanged(settings) }
        LaunchedEffect(context.presentationMode, launched) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched && !hadResult && result == null && !context.awaitingRuntimeInputs) {
                launched = true
                calculate()
            }
        }

        if (hadResult && result == null) {
            CapturedExecutionRecovery({ hadResult = false; status = "Review inputs and calculate a new result." }, onCancel)
            return
        }
        val keepInteractive = !context.submitsImmediately && (context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun)
        val scaffoldResult = if (keepInteractive) null else result
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { mapOf(ImageScaleFields.RESULT to OutputFormatter.fields(it, false)[ImageScaleFields.RESULT]?.toString().orEmpty()) }.orEmpty(),
            onBack = { if (!actionsBusy) onBack() },
            onRetry = { if (!actionsBusy) { result = null; hadResult = false } },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = { if (!actionsBusy) onCancel() }
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (result == null) fields.forEach { field ->
                if (context.settingShouldBeShown(field.key)) {
                    NumericSliderField(field, settings[field.key].orEmpty(), onValueChange = { update(field.key, it) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (result == null) Button(onClick = ::calculate, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
            if (status.isNotBlank()) Text(status)
            if (actionsBusy) Text("Finishing result action…")
            if (values[ImageScaleFields.STATUS] == "succeeded") {
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("IMAGE SCALE", style = MaterialTheme.typography.labelLarge)
                        Text("${values[ImageScaleFields.SCALE].orEmpty()} arcsec / pixel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Field of view ${values[ImageScaleFields.WIDTH].orEmpty()}° × ${values[ImageScaleFields.HEIGHT].orEmpty()}°")
                        Text("Effective focal length ${values[ImageScaleFields.EFL].orEmpty()} mm · f/${values[ImageScaleFields.FRATIO].orEmpty()}")
                    }
                }
                if (keepInteractive && result != null) {
                    Spacer(Modifier.height(8.dp))
                    CanonicalCommittedResultActions(
                        result = result!!, label = title, screenContext = context,
                        primaryText = values[ImageScaleFields.RESULT].orEmpty(), attachments = emptyList(),
                        onDone = { result?.let(onConfirmed) },
                        onEdit = { if (!actionsBusy) { result = null; hadResult = false } },
                        completionLabel = if (!context.isLastStep) "Continue" else null,
                        onBusyChanged = { actionsBusy = it }
                    )
                }
            } else if (values[ImageScaleFields.ERROR].orEmpty().isNotBlank()) {
                Text(values[ImageScaleFields.ERROR].orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun NumericSliderField(field: Field, text: String, onValueChange: (String) -> Unit) {
        val parsed = text.toFloatOrNull()?.takeIf { it.isFinite() }
        val slider = (parsed ?: field.fallback.toFloat()).coerceIn(field.sliderMin, field.sliderMax)
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(field.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = text,
                    onValueChange = onValueChange,
                    label = { Text(field.unit) },
                    singleLine = true,
                    modifier = Modifier.weight(0.72f)
                )
            }
            Slider(
                value = slider,
                onValueChange = { value ->
                    val digits = when (field.key) {
                        "pixel_size_micron", "multiplier" -> 2
                        "sensor_width_mm", "sensor_height_mm" -> 1
                        else -> 0
                    }
                    onValueChange(String.format(Locale.US, "%.${digits}f", value))
                },
                valueRange = field.sliderMin..field.sliderMax,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Slider ${field.sliderMin}–${field.sliderMax} ${field.unit}; type any valid exact value in the box.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

object SessionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SessionMethod.id
    override val title = "Astronomy session"
    override val description = "A resumable observing timer with explicit start, pause/resume and stop controls."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomySessionScreen(context, onBack, onConfirmed, onCancel)
    }
}

private fun jsonToInteractiveMap(json: String): Map<String, String> = runCatching {
    val o = JSONObject(json.ifBlank { "{}" })
    buildMap { o.keys().forEach { key -> put(key, o.optString(key, "")) } }
}.getOrDefault(emptyMap())
