package com.example.methodmesh.modules.datatools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.*

object DataToolsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DataToolsMethod.ID
    override val title = "Data tools"
    override val description = "Offline encoders, validators and format conversions."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var operation by rememberSaveable { mutableStateOf(context.action.settings["operation"] ?: context.action.settings["input_operation"] ?: "json_pretty") }
        var data by rememberSaveable { mutableStateOf(context.action.settings["data"] ?: context.action.settings["input_data"] ?: "") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        fun run() {
            val settings = mapOf("operation" to operation, "data" to data)
            val request = As100DataToolsMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + context.action.settings + settings, emptyList(), emptyList())
            result = As100DataToolsMethod.result(request, As100DataToolsMethod.transform(settings), context.request.invocationContext)
            if (context.submitsImmediately) result?.let(onConfirmed)
        }
        LaunchedEffect(operation, data) { context.onSettingsChanged(mapOf("operation" to operation, "data" to data)) }
        LaunchedEffect(context.presentationMode) { if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) { launched = true; run() } }
        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { run() }, { result?.let(onConfirmed) }, onCancel) {
            if (context.settingShouldBeShown("operation")) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf("json_pretty", "json_minify", "json_validate", "base64_encode", "base64_decode", "url_encode", "url_decode", "hex_encode", "hex_decode", "unix_to_iso", "iso_to_unix", "csv_to_json", "json_to_csv").forEach { op -> FilterChip(operation == op, { operation = op }, { Text(op.replace('_', ' ')) }, modifier = Modifier.padding(2.dp)) }
                }
            } else {
                Text("Operation: ${operation.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
            }
            if (context.settingShouldBeShown("data")) {
                OutlinedTextField(data, { data = it }, label = { Text("Input") }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp), minLines = 8)
            }
            Spacer(Modifier.height(8.dp)); Button(onClick = ::run, modifier = Modifier.fillMaxWidth()) { Text("Run") }
        }
    }
}
