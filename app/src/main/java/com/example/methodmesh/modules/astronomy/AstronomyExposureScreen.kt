package com.example.methodmesh.modules.astronomy

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.*
import org.json.JSONObject

/** Saved UI state contains declared inputs; opaque caller/audit metadata stays in the request. */
internal fun astronomyEditableInputs(method: AstronomyMethodBase, raw: Map<String, String>): Map<String, String> {
    val defaults = AstronomyModule.capabilitySettings().getValue(method.id).associate { it.id to it.autoDefault() }
    return defaults + raw.mapKeys { it.key.removePrefix("input_") }.filterKeys { it in defaults }
}

internal fun validateAstronomyCalculation(method: AstronomyMethodBase, settings: Map<String, String>) {
    AstronomyModule.capabilitySettings().getValue(method.id).forEach { field ->
        if (field is MethodSetting.FloatSetting) {
            val value = settings[field.id]?.toDoubleOrNull()
            require(value != null && value.isFinite() &&
                (field.minimum == null || value >= field.minimum) &&
                (field.maximum == null || value <= field.maximum)) { "Enter a valid " + field.label.lowercase() + " within its allowed range." }
        }
    }
}

@Composable
internal fun AstronomyExposureScreen(context: CapabilityScreenContext, onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val fields = remember { AstronomyModule.capabilitySettings().getValue(As100ExposureLimitMethod.id) }
    val initial = remember {
        astronomyEditableInputs(As100ExposureLimitMethod, context.action.settings)
    }
    var settingsJson by rememberSaveable { mutableStateOf(JSONObject(initial).toString()) }
    var result by rememberRetainedExecutionResult(As100ExposureLimitMethod.id)
    var hadResult by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    val settings = settingsMap(settingsJson)
    fun run() {
        attempted = true
        try {
            val frozen = settingsMap(settingsJson).toMap()
            validateAstronomyCalculation(As100ExposureLimitMethod, frozen)
            result = methodExecution(As100ExposureLimitMethod, context, As100ExposureLimitMethod.calculate(frozen), frozen)
            hadResult = true; status = ""
        } catch (failure: Exception) { status = failure.message ?: "Check the lens and pixel inputs." }
    }
    LaunchedEffect(Unit) {
        if (!attempted && !hadResult && result == null && !context.awaitingRuntimeInputs &&
            context.presentationMode == CapabilityPresentationMode.IntentLaunch) run()
    }
    if (hadResult && result == null) {
        CapturedExecutionRecovery({ hadResult = false; status = "Calculate a new result." }, onCancel)
        return
    }
    CapabilityScreenScaffold("Untracked exposure limit", As100ExposureLimitMethod.id, context, context.stepNumber > 1,
        result, result?.let { mapOf(ExposureFields.RESULT to OutputFormatter.fields(it, false)[ExposureFields.RESULT]) }.orEmpty(),
        onBack, { result = null; hadResult = false }, { result?.let(onConfirmed) }, onCancel) {
        Text("For an untracked camera/lens on a fixed tripod: pixel-aware trail limit with a 500-rule comparator.")
        fields.filter { context.settingShouldBeShown(it.id) }.forEach { field ->
            OutlinedTextField(settings[field.id].orEmpty(), { entered ->
                settingsJson = JSONObject(settingsMap(settingsJson) + (field.id to entered)).toString()
                context.onSettingsChanged(settingsMap(settingsJson))
            }, label = { Text(field.label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Button(onClick = ::run, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
        if (status.isNotBlank()) Text(status)
    }
}
