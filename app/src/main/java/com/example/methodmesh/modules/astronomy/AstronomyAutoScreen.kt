package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapturedExecutionRecovery
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

internal data class AstronomyAutoCapture(val inputs: Map<String, String>, val values: Map<String, String>)

/** Only returned captures can become executions. Cancellation never produces a fallback record. */
internal suspend fun acquireAstronomyAuto(
    kind: AstronomyAutoKind,
    plan: AstronomyAutoPlan,
    app: Context,
    loadCurrent: (Context, Double, Double) -> AstronomyLivePayload,
    loadHourly: (Context, Double, Double) -> String,
    deviceLocation: suspend (Context) -> AstroLocation
): AstronomyAutoCapture {
    var inputs = plan.inputs
    var source = plan.source
    if (plan.needsCurrent || plan.needsHourly) {
        val location = plan.location ?: deviceLocation(app)
        require(location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
            location.longitude.isFinite() && location.longitude in -180.0..180.0) { "The location is invalid." }
        inputs = inputs + mapOf("latitude" to location.latitude.toString(), "longitude" to location.longitude.toString(),
            // Resolve once. The calculator must not decode a stale Plus Code over the chosen coordinates.
            "plus_code" to "", "resolved_location_source" to location.source)
        if (plan.needsCurrent) {
            val payload = withContext(Dispatchers.IO) { loadCurrent(app, location.latitude, location.longitude) }
            inputs = inputs + mapOf("weather_json" to payload.weatherJson, "data_source" to "api")
            if (kind == AstronomyAutoKind.Conditions) inputs = inputs + ("air_quality_json" to payload.airQualityJson)
            source = location.source + " + Open-Meteo"
        } else if (!inputs["forecast_json"].isNullOrBlank()) {
            inputs = inputs + ("data_source" to "supplied_forecast")
            source = location.source + " + supplied forecast"
        } else {
            try {
                val forecast = withContext(Dispatchers.IO) { loadHourly(app, location.latitude, location.longitude) }
                inputs = inputs + mapOf("forecast_json" to forecast, "data_source" to "api")
                source = location.source + " + Open-Meteo hourly"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                inputs = inputs + mapOf("forecast_json" to "", "data_source" to "astronomy_only",
                    "forecast_warning" to ("Hourly forecast unavailable: " + (failure.message ?: "provider error") +
                        ". Astronomy-only plan; weather is not included."))
                source = location.source + " + astronomy only"
            }
        }
    }
    currentCoroutineContext().ensureActive()
    val capturedInputs = inputs + ("resolved_data_source_label" to source)
    val values = withContext(Dispatchers.IO) { kind.method.calculate(capturedInputs) }
    return AstronomyAutoCapture(capturedInputs, values)
}

@Composable
internal fun AstronomyAutoScreen(
    kind: AstronomyAutoKind,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit,
    loadCurrent: (Context, Double, Double) -> AstronomyLivePayload = AstronomyLiveData::current,
    loadHourly: (Context, Double, Double) -> String = AstronomyLiveData::hourly,
    deviceLocation: suspend (Context) -> AstroLocation = ::astronomyDeviceLocation
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val controller = remember(kind) { AstronomyAutoRequestController() }
    val alive = remember { AtomicBoolean(true) }
    val initial = remember(context.action.settings) { context.action.settings.mapKeys { it.key.removePrefix("input_") } }
    var settingsJson by rememberSaveable(kind) { mutableStateOf(JSONObject(initial).toString()) }
    val settings = settingsMap(settingsJson)
    var result by rememberRetainedExecutionResult(kind.method.id + ":automatic")
    var hadResult by rememberSaveable(kind) { mutableStateOf(false) }
    var attempted by rememberSaveable(kind) { mutableStateOf(false) }
    var wasAcquiring by rememberSaveable(kind) { mutableStateOf(false) }
    var status by rememberSaveable(kind) { mutableStateOf("Ready.") }
    var showDetails by rememberSaveable(kind) { mutableStateOf(false) }
    var confirmedId by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var pendingPermission by remember { mutableStateOf<AstronomyAutoPlan?>(null) }
    val busy = controller.busy || pendingPermission != null

    fun merge(key: String, value: String) {
        settingsJson = JSONObject(settingsMap(settingsJson) + (key to value)).toString()
        context.onSettingsChanged(settingsMap(settingsJson))
    }
    fun failed(failure: Exception) {
        wasAcquiring = false
        status = "Run failed: " + (failure.message ?: "data unavailable") + " Review inputs and Run to retry."
        showDetails = true
    }
    fun acquire(plan: AstronomyAutoPlan) {
        wasAcquiring = true
        status = "Acquiring data…"
        controller.start(scope, {
            acquireAstronomyAuto(kind, plan, app, loadCurrent, loadHourly, deviceLocation)
        }, { capture ->
            // Provider payloads belong to this original execution, never to editable/preset settings.
            result = kind.method.result(kind.method.request(
                context.action.canonicalId,
                context.request.invocationContext.asMap(context.action.canonicalId) + capture.inputs,
                emptyList(), emptyList()), capture.values, context.request.invocationContext)
            hadResult = true
            wasAcquiring = false
            status = "Ready."
        }, ::failed, onCancelled = {
            if (alive.get()) {
                wasAcquiring = false
                status = "Request cancelled. Run to retry."
            }
        })
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val plan = pendingPermission
        pendingPermission = null
        if (alive.get() && plan != null) {
            if (hasLocationPermission(app)) acquire(plan)
            else failed(IllegalStateException("Location denied. Supply a site or choose manual/upstream data."))
        }
    }
    fun run() {
        if (controller.busy || pendingPermission != null || !alive.get()) return
        attempted = true
        val plan = try { astronomyAutoPlan(kind, settingsMap(settingsJson)) }
        catch (failure: Exception) { failed(failure); return }
        result = null
        hadResult = false
        wasAcquiring = true
        if (plan.needsDeviceLocation && !hasLocationPermission(app)) {
            pendingPermission = plan
            status = "Waiting for location permission…"
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else acquire(plan)
    }
    fun cancelWork() {
        pendingPermission = null
        controller.cancel()
        wasAcquiring = false
    }
    fun confirmOriginal() {
        val original = result ?: return
        val id = original.request.id.value
        if (confirmedId != id) {
            confirmedId = id
            onConfirmed(original)
        }
    }
    DisposableEffect(controller) {
        onDispose {
            alive.set(false)
            controller.cancel()
        }
    }
    LaunchedEffect(Unit) {
        when {
            hadResult -> Unit
            wasAcquiring -> {
                wasAcquiring = false
                status = "Previous request was interrupted. Run to retry."
            }
            context.awaitingRuntimeInputs -> status = "Enter runtime inputs, then Run."
            !attempted -> run()
        }
    }
    if (hadResult && result == null) {
        CapturedExecutionRecovery(onRestart = { hadResult = false; run() }, onClose = onCancel)
        return
    }
    result?.request?.context?.get("resolved_data_source_label")?.let { Text("Source: " + it) }
    val preview = result?.let { original ->
        val fields = OutputFormatter.fields(original, false)
        val primary = fields[kind.method.mainField]?.toString().orEmpty().ifBlank {
            fields[kind.errorField]?.toString().orEmpty()
        }
        val warning = original.request.context["forecast_warning"].orEmpty()
        mapOf(kind.method.mainField to (primary + if (warning.isBlank()) "" else "\n" + warning))
    }.orEmpty()
    CapabilityScreenScaffold(
        kind.title, kind.method.id, context, context.stepNumber > 1, result,
        preview,
        { cancelWork(); onBack() },
        { cancelWork(); result = null; hadResult = false; status = "Review inputs, then Run." },
        ::confirmOriginal, { cancelWork(); onCancel() }
    ) {
        Text(status, style = MaterialTheme.typography.bodySmall)
        Button(onClick = ::run, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Working…" else "Run")
        }
        if (busy) OutlinedButton(onClick = { cancelWork(); status = "Request cancelled. Run to retry." },
            modifier = Modifier.fillMaxWidth()) { Text("Cancel request") }
        val fields = kind.fields.filter { context.settingShouldBeShown(it.id) }
        val primary = setOf("source_mode", "location_source", "plus_code", "latitude", "longitude",
            "target_name", "use_target", "target_ra_hours", "target_dec_deg")
        val visible = if (context.isNativePresetRun || showDetails || settings["source_mode"] == "manual") fields
            else fields.filter { it.id in primary }
        if (kind != AstronomyAutoKind.Window && !context.isNativePresetRun)
            Text("Choose manual data source to calculate offline.", style = MaterialTheme.typography.bodySmall)
        visible.forEach { field -> AstronomyAutoInput(field, settings[field.id] ?: field.autoDefault()) { merge(field.id, it) } }
        if (!context.isNativePresetRun && fields.any { it !in visible }) {
            OutlinedButton(onClick = { showDetails = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Manual values / planning options")
            }
        } else if (!context.isNativePresetRun && showDetails && settings["source_mode"] != "manual") {
            OutlinedButton(onClick = { showDetails = false }, modifier = Modifier.fillMaxWidth()) { Text("Hide extra inputs") }
        }
    }
}

@Composable
private fun AstronomyAutoInput(field: MethodSetting, value: String, onChange: (String) -> Unit) {
    when (field) {
        is MethodSetting.BooleanSetting -> Row(Modifier.fillMaxWidth()) {
            Text(field.label, Modifier.weight(1f))
            Switch(checked = value.toBoolean(), onCheckedChange = { onChange(it.toString()) })
        }
        is MethodSetting.ChoiceSetting -> {
            var expanded by remember(field.id) { mutableStateOf(false) }
            Column {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(field.label + ": " + value)
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    field.choices.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice) }, onClick = { onChange(choice); expanded = false })
                    }
                }
            }
        }
        else -> {
            val numeric = field is MethodSetting.FloatSetting || field is MethodSetting.IntSetting
            val unit = when (field) {
                is MethodSetting.FloatSetting -> field.unit
                is MethodSetting.IntSetting -> field.unit
                else -> null
            }
            // A full keyboard keeps a minus sign available for signed coordinates/temperatures.
            val signed = field is MethodSetting.FloatSetting && (field.minimum ?: 0f) < 0f
            OutlinedTextField(value, onChange, label = { Text(field.label) }, modifier = Modifier.fillMaxWidth(),
                suffix = if (unit != null) { { Text(unit) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric && !signed) KeyboardType.Decimal else KeyboardType.Text),
                singleLine = numeric || !field.id.endsWith("_json"))
        }
    }
}
