package com.example.methodmesh.modules.nfc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private enum class ProtocolAdminMode(val method: ProtocolNfcAdministrationMethod, val title: String, val description: String, val fields: List<Pair<String, String>>) {
    Provision(
        As100ProtocolNfcProvisionMethod,
        "Provision protocol card",
        "Create the initial offline protocol receipt on a participant card.",
        listOf(
            "protocol_id" to "Protocol ID",
            "protocol_version" to "Protocol version",
            "flag_bit_count" to "Active flag bit count",
            "completion_bit_count" to "Completion bit count",
            "initial_flag_bits" to "Initial active flag bits (hex)",
            "initial_completion_bits" to "Initial completion bits (hex)",
            "overwrite_policy" to "Provisioning policy",
            "flag_definitions_builder" to "Active flags (one per line: bit | code | label | severity)",
            "step_definitions_builder" to "Protocol steps (one per line: id | bits | label | required expression)"
        )
    ),
    Reconstruct(
        As100ProtocolNfcReconstructMethod,
        "Reconstruct replacement card",
        "Restore a previously exported protocol receipt to a replacement card. A reason is required.",
        listOf(
            ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD to "Encoded protocol state payload",
            ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD_HASH to "State payload SHA-256 (optional)",
            ProtocolNfcTrackingFields.RECONSTRUCTION_REASON to "Replacement/reconstruction reason"
        )
    ),
    Override(
        As100ProtocolNfcOverrideMethod,
        "Override protocol card",
        "Apply a justified manual change to flags or completion bits. This is recorded as a protocol deviation.",
        listOf(
            "protocol_id" to "Protocol ID (optional)",
            "set_flag_bits" to "Set active flag bits (hex)",
            "clear_flag_bits" to "Clear active flag bits (hex)",
            "set_completion_bits" to "Set completion bits (hex)",
            "clear_completion_bits" to "Clear completion bits (hex)",
            ProtocolNfcTrackingFields.OVERRIDE_JUSTIFICATION to "Override justification"
        )
    )
}

@Composable
private fun ProtocolNfcAdministrationContent(
    context: CapabilityScreenContext,
    mode: ProtocolAdminMode,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val supplied = remember(context.action.settings, context.request.settings) {
        context.request.settings + context.action.settings.filterValues(String::isNotBlank)
    }
    val values = remember(supplied) { mutableStateMapOf<String, String>().apply { putAll(supplied) } }
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready.") }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    val definitionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && mode == ProtocolAdminMode.Provision) {
            runCatching {
                androidContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?.also { values[ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_JSON] = it }
                    ?: error("Could not read the selected protocol file.")
            }.onSuccess { status = "Protocol definition loaded from file." }
                .onFailure { status = it.message ?: "Could not load protocol definition." }
        }
    }

    fun start() {
        if (mode == ProtocolAdminMode.Provision && values[ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_JSON].orEmpty().isBlank()) {
            val hasBuilderRows = values["flag_definitions_builder"].orEmpty().isNotBlank() || values["step_definitions_builder"].orEmpty().isNotBlank()
            if (hasBuilderRows) {
                val built = buildDefinitionJson(values)
                if (built == null) {
                    status = "Protocol definition rows are invalid. Use bit | code | label | severity for flags and id | bits | label | required expression for steps."
                    return
                }
                values[ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_JSON] = built
            }
        }
        result = null
        active = true
        status = "Hold the NFC card against the phone…"
    }

    LaunchedEffect(context.startsImmediately) { if (context.startsImmediately) start() }

    NfcDeviceServiceEffect(
        enabled = active,
        onStatus = { status = it },
        onSignal = { signal ->
            active = false
            status = "Updating protocol receipt…"
            scope.launch {
                val executionValues = values.toMutableMap()
                if (mode == ProtocolAdminMode.Provision && executionValues[ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_JSON].orEmpty().isBlank()) {
                    buildDefinitionJson(executionValues)?.let { executionValues[ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_JSON] = it }
                }
                val execution = withContext(Dispatchers.IO) { mode.method.run(signal, executionValues) }
                result = execution
                status = execution.observations.lastOrNull()?.values?.get(ProtocolNfcTrackingFields.PROTOCOL_REASON)
                    ?: execution.status.name
                if (context.startsImmediately) onConfirmed(execution)
            }
        }
    )

    CapabilityScreenScaffold(
        title = mode.title,
        capabilityId = context.action.canonicalId,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = ::start,
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mode.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            mode.fields.forEach { (key, label) ->
                if (mode == ProtocolAdminMode.Provision && key == "flag_definitions_builder") {
                    OutlinedButton(onClick = { definitionPicker.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (values[ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_JSON].orEmpty().isBlank()) "Load protocol definition file" else "Replace loaded protocol definition")
                    }
                    Text("Or build it below. The file/builder definition is not written to the tag; its SHA-256 is returned as provenance.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = values[key].orEmpty(),
                    onValueChange = { values[key] = it },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
            }
            if (active) CircularProgressIndicator()
            else Button(onClick = ::start, modifier = Modifier.fillMaxWidth()) { Text("Tap NFC card") }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Builds the portable JSON definition from readable builder rows; files remain an alternative. */
private fun buildDefinitionJson(values: Map<String, String>): String? = runCatching {
    val protocolId = values["protocol_id"].orEmpty().trim()
    if (protocolId.isBlank()) return null
    val flags = JSONArray()
    values["flag_definitions_builder"].orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).forEach { line ->
        val p = line.split('|').map(String::trim)
        require(p.size >= 3) { "Each flag row must be: bit | code | label | severity" }
        flags.put(JSONObject().apply {
            put("bit", p[0].toInt())
            put("code", p[1])
            put("label", p[2])
            put("severity", p.getOrElse(3) { "WARNING" }.ifBlank { "WARNING" })
        })
    }
    val steps = JSONArray()
    values["step_definitions_builder"].orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).forEach { line ->
        val p = line.split('|').map(String::trim)
        require(p.size >= 3) { "Each step row must be: id | bits | label | required expression" }
        steps.put(JSONObject().apply {
            put("id", p[0]); put("bits", p[1]); put("label", p[2])
            put("required_expression", p.getOrElse(3) { "" })
        })
    }
    JSONObject().apply {
        put("protocol_id", protocolId)
        put("protocol_version", values["protocol_version"].orEmpty().ifBlank { "1" })
        put("flag_bit_count", values["flag_bit_count"].orEmpty().toIntOrNull() ?: 8)
        put("completion_bit_count", values["completion_bit_count"].orEmpty().toIntOrNull() ?: 8)
        put("flags", flags); put("steps", steps)
    }.toString()
}.getOrNull()

object ProtocolNfcProvisionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ProtocolNfcProvisionMethod.id
    override val title = "Provision protocol card"
    override val description = "Create the initial offline protocol-progress receipt."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        ProtocolNfcAdministrationContent(context, ProtocolAdminMode.Provision, onBack, onConfirmed, onCancel)
}

object ProtocolNfcReconstructCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ProtocolNfcReconstructMethod.id
    override val title = "Reconstruct replacement card"
    override val description = "Restore an exported protocol receipt to a replacement NFC card."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        ProtocolNfcAdministrationContent(context, ProtocolAdminMode.Reconstruct, onBack, onConfirmed, onCancel)
}

object ProtocolNfcOverrideCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ProtocolNfcOverrideMethod.id
    override val title = "Override protocol card"
    override val description = "Make a justified manual protocol-state change and record a deviation."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        ProtocolNfcAdministrationContent(context, ProtocolAdminMode.Override, onBack, onConfirmed, onCancel)
}
