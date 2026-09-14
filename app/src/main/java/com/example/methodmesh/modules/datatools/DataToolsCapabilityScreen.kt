package com.example.methodmesh.modules.datatools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object DataToolsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DataToolsMethod.ID
    override val title = "Data tools"
    override val description = "Offline encoders, validators and format conversions."

    private data class OperationGroup(val label: String, val operations: List<String>)

    private val groups = listOf(
        OperationGroup("JSON", listOf("json_pretty", "json_minify", "json_validate")),
        OperationGroup("Encode", listOf("base64_encode", "base64_decode", "url_encode", "url_decode", "hex_encode", "hex_decode")),
        OperationGroup("Time", listOf("unix_to_iso", "iso_to_unix")),
        OperationGroup("Tabular", listOf("csv_to_json", "json_to_csv"))
    )

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var operation by rememberSaveable {
            mutableStateOf(context.action.settings["operation"] ?: context.action.settings["input_operation"] ?: "json_pretty")
        }
        var data by rememberSaveable {
            mutableStateOf(context.action.settings["data"] ?: context.action.settings["input_data"] ?: "")
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun run() {
            val settings = mapOf("operation" to operation, "data" to data)
            val request = As100DataToolsMethod.request(
                capabilityId,
                context.request.invocationContext.asMap(capabilityId) + context.action.settings + settings,
                emptyList(),
                emptyList()
            )
            result = As100DataToolsMethod.result(
                request,
                As100DataToolsMethod.transform(settings),
                context.request.invocationContext
            )
            if (context.submitsImmediately) result?.let(onConfirmed)
        }

        LaunchedEffect(operation, data) {
            context.onSettingsChanged(mapOf("operation" to operation, "data" to data))
        }
        LaunchedEffect(context.presentationMode, context.awaitingRuntimeInputs) {
            if (
                context.presentationMode == CapabilityPresentationMode.IntentLaunch &&
                !context.awaitingRuntimeInputs &&
                !launched
            ) {
                launched = true
                run()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack,
            onRetry = ::run,
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "Transform text locally. The operation belongs to the preset; the data can be supplied when the preset runs.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))

            if (context.settingShouldBeShown("operation")) {
                Text("Operation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                groups.forEach { group ->
                    Text(
                        group.label,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        group.operations.forEach { op ->
                            FilterChip(
                                selected = operation == op,
                                onClick = { operation = op; result = null },
                                label = { Text(opLabel(op)) }
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Operation", style = MaterialTheme.typography.labelMedium)
                        Text(opLabel(operation), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (context.settingShouldBeShown("data")) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = data,
                    onValueChange = { data = it; result = null },
                    label = { Text(inputLabel(operation)) },
                    supportingText = { Text(inputHint(operation)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = ::run,
                enabled = data.isNotBlank() || operation == "json_validate",
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (result == null) "Run ${opLabel(operation)}" else "Run again")
            }
        }
    }
}

private fun opLabel(operation: String): String = when (operation) {
    "json_pretty" -> "Pretty JSON"
    "json_minify" -> "Minify JSON"
    "json_validate" -> "Validate JSON"
    "base64_encode" -> "Base64 encode"
    "base64_decode" -> "Base64 decode"
    "url_encode" -> "URL encode"
    "url_decode" -> "URL decode"
    "hex_encode" -> "Hex encode"
    "hex_decode" -> "Hex decode"
    "unix_to_iso" -> "Unix → ISO"
    "iso_to_unix" -> "ISO → Unix"
    "csv_to_json" -> "CSV → JSON"
    "json_to_csv" -> "JSON → CSV"
    else -> operation.replace('_', ' ')
}

private fun inputLabel(operation: String): String = when (operation) {
    "csv_to_json" -> "CSV data"
    "json_to_csv", "json_pretty", "json_minify", "json_validate" -> "JSON data"
    "unix_to_iso" -> "Unix timestamp"
    "iso_to_unix" -> "ISO-8601 timestamp"
    else -> "Input"
}

private fun inputHint(operation: String): String = when (operation) {
    "unix_to_iso" -> "Seconds or milliseconds since the Unix epoch, according to the capability contract."
    "iso_to_unix" -> "Example: 2026-09-13T22:00:00Z"
    "json_validate" -> "Validation does not modify the supplied JSON."
    else -> "Processing is local; this capability does not send the input to a network service."
}
