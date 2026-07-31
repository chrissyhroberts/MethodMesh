package com.example.researchos.modules.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun protocolSettings(context: CapabilityScreenContext): Map<String, String> =
    context.request.invocationContext.asMap() + context.request.settings + context.action.settings

@Composable
private fun ProtocolNfcTrackingContent(
    context: CapabilityScreenContext,
    method: ProtocolNfcMethod,
    title: String,
    description: String,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val supplied = remember(context.action.settings, context.request.settings) { protocolSettings(context) }
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to read the participant protocol card.") }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }

    fun start() {
        active = true
        result = null
        status = "Hold the participant card against the phone…"
    }

    LaunchedEffect(context.startsImmediately) { if (context.startsImmediately) start() }

    NfcDeviceServiceEffect(
        enabled = active,
        onStatus = { status = it },
        onSignal = { signal ->
            scope.launch {
                active = false
                status = "Checking protocol progress…"
                result = withContext(Dispatchers.IO) { method.run(signal, supplied) }
                val values = result?.observations?.lastOrNull()?.values.orEmpty()
                status = values[ProtocolNfcTrackingFields.PROTOCOL_REASON].orEmpty()
            }
        }
    )

    CapabilityScreenScaffold(
        title = title,
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
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("Protocol: ${supplied[ProtocolNfcTrackingFields.PROTOCOL_ID].orEmpty().ifBlank { "not supplied" }}")
            Text("Step: ${supplied[ProtocolNfcTrackingFields.STEP_ID].orEmpty().ifBlank { "not supplied" }}")
            Spacer(Modifier.height(12.dp))
            if (active) CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(status)
        }
    }
}

object ProtocolNfcCheckCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ProtocolNfcCheckMethod.id
    override val title = "Check protocol progress"
    override val description = "Read the participant card and check whether this protocol step is allowed."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        ProtocolNfcTrackingContent(context, As100ProtocolNfcCheckMethod, title, description, onBack, onConfirmed, onCancel)
}

object ProtocolNfcCompleteCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ProtocolNfcCompleteMethod.id
    override val title = "Complete protocol step"
    override val description = "Read the participant card, verify the expected progress, and write the completed step back to it."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        ProtocolNfcTrackingContent(context, As100ProtocolNfcCompleteMethod, title, description, onBack, onConfirmed, onCancel)
}
