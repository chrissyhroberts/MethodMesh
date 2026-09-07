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
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun update(key: String, value: String) {
            settingsJson = JSONObject(settings.toMutableMap().apply { put(key, value) }).toString()
        }

        fun calculate() {
            val calculated = As100ImageScaleMethod.calculate(settings)
            values = calculated
            val execution = methodExecution(As100ImageScaleMethod, context, calculated, settings)
            result = execution
            if (context.submitsImmediately) onConfirmed(execution)
        }

        LaunchedEffect(settingsJson) { context.onSettingsChanged(settings) }
        LaunchedEffect(context.presentationMode, launched) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) {
                launched = true
                calculate()
            }
        }

        val keepInteractive = context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun
        val scaffoldResult = if (keepInteractive) null else result
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { mapOf(ImageScaleFields.RESULT to OutputFormatter.fields(it, false)[ImageScaleFields.RESULT]?.toString().orEmpty()) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; values = emptyMap() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            fields.forEach { field ->
                if (context.settingShouldBeShown(field.key)) {
                    NumericSliderField(field, settings[field.key].orEmpty(), onValueChange = { update(field.key, it) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::calculate, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
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
                    Button(onClick = { result?.let(onConfirmed) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (context.isNativePresetRun) "Finish" else "Use this result")
                    }
                }
            } else if (values[ImageScaleFields.ERROR].orEmpty().isNotBlank()) {
                Text(values[ImageScaleFields.ERROR].orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun NumericSliderField(field: Field, text: String, onValueChange: (String) -> Unit) {
        val parsed = text.toFloatOrNull()
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

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val repo = remember { AstronomyRepository(androidContext) }
        var active by remember { mutableStateOf(repo.loadActiveSession()) }
        var target by rememberSaveable(context.action.canonicalId) { mutableStateOf(active?.target ?: setting(context, "target")) }
        var equipment by rememberSaveable(context.action.canonicalId) { mutableStateOf(active?.equipment ?: setting(context, "equipment")) }
        var notes by rememberSaveable(context.action.canonicalId) { mutableStateOf(active?.notes ?: setting(context, "notes")) }
        var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var status by rememberSaveable { mutableStateOf(if (active == null) "Ready to start." else "Resumed unfinished session from local storage.") }
        val saveHistory = (setting(context, "save_session").toBooleanStrictOrNull() ?: true)

        fun syncFields(session: AstronomyRepository.ActiveSession): AstronomyRepository.ActiveSession {
            val updated = session.copy(target = target, equipment = equipment, notes = notes)
            repo.updateActiveSession(updated)
            active = updated
            return updated
        }

        fun start() {
            active = repo.startSession(target.ifBlank { "Unspecified target" }, equipment, notes)
            status = "Session started."
            result = null
            values = emptyMap()
        }

        fun stop() {
            val current = active ?: return
            val finished = repo.finishSession(syncFields(current), saveHistory)
            active = null
            val calculationSettings = mapOf(
                "session_id" to finished.optString("session_id"),
                "session_state" to "finished",
                "target" to finished.optString("target"),
                "start_iso" to finished.optString("start_iso"),
                "end_iso" to finished.optString("end_iso"),
                "active_duration_seconds" to finished.optDouble("active_duration_seconds", 0.0).toString(),
                "equipment" to finished.optString("equipment"),
                "conditions_summary" to setting(context, "conditions_summary"),
                "conditions_score" to setting(context, "conditions_score"),
                "sky_test_summary" to setting(context, "sky_test_summary"),
                "notes" to finished.optString("notes"),
                "save_session" to saveHistory.toString()
            )
            val calculated = As100SessionMethod.calculate(calculationSettings)
            values = calculated
            val execution = methodExecution(As100SessionMethod, context, calculated, calculationSettings)
            result = execution
            status = "Session stopped."
            if (context.submitsImmediately) onConfirmed(execution)
        }

        LaunchedEffect(active?.id, active?.state) {
            while (active?.state == "running") {
                tickMs = System.currentTimeMillis()
                delay(1000)
            }
        }

        LaunchedEffect(target, equipment, notes, active?.id) {
            active?.let { syncFields(it) }
            context.onSettingsChanged(
                context.action.settings + mapOf(
                    "target" to target,
                    "equipment" to equipment,
                    "notes" to notes
                )
            )
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { mapOf(SessionFields.RESULT to OutputFormatter.fields(it, false)[SessionFields.RESULT]?.toString().orEmpty()) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; values = emptyMap(); status = "Ready." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (context.settingShouldBeShown("target")) OutlinedTextField(target, { target = it }, label = { Text("Target") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (context.settingShouldBeShown("equipment")) OutlinedTextField(equipment, { equipment = it }, label = { Text("Equipment") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            if (context.settingShouldBeShown("notes")) OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
            Spacer(Modifier.height(10.dp))

            val current = active
            if (current == null) {
                Button(onClick = ::start, enabled = result == null, modifier = Modifier.fillMaxWidth()) { Text("Start session") }
            } else {
                val elapsed = current.activeDurationMs(tickMs)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(current.state.uppercase(), style = MaterialTheme.typography.labelLarge)
                        Text(formatElapsed(elapsed), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Started ${current.startIso}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    if (current.state == "running") {
                        OutlinedButton(onClick = { active = repo.pauseSession(syncFields(current)); status = "Session paused." }, modifier = Modifier.weight(1f)) { Text("Pause") }
                    } else {
                        OutlinedButton(onClick = { active = repo.resumeSession(syncFields(current)); status = "Session resumed." }, modifier = Modifier.weight(1f)) { Text("Resume") }
                    }
                    Spacer(Modifier.padding(4.dp))
                    Button(onClick = ::stop, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
                OutlinedButton(
                    onClick = { repo.discardActiveSession(); active = null; status = "Unfinished session discarded." },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) { Text("Discard session") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            if (values[SessionFields.RESULT].orEmpty().isNotBlank()) Text(values[SessionFields.RESULT].orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
    }

    private fun setting(context: CapabilityScreenContext, key: String): String =
        context.action.settings[key] ?: context.action.settings["input_$key"].orEmpty()

    private fun formatElapsed(ms: Long): String {
        val seconds = (ms / 1000.0).roundToLong().coerceAtLeast(0L)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}

private fun jsonToInteractiveMap(json: String): Map<String, String> = runCatching {
    val o = JSONObject(json.ifBlank { "{}" })
    buildMap { o.keys().forEach { key -> put(key, o.optString(key, "")) } }
}.getOrDefault(emptyMap())
