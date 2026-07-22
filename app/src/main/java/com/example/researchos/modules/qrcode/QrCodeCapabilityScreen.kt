package com.example.researchos.modules.qrcode

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown

/**
 * Reusable invocation boundary for the QR capability.
 *
 * Parent capabilities call the returned function instead of embedding QR scanning.
 * The dependency performs camera capture and returns its canonical AS100 result.
 */
@Composable
fun rememberQrCapabilityInvocation(
    context: CapabilityScreenContext,
    sourceLabel: String = "camera_zxing",
    onResult: (ExecutionResult) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit = {}
): () -> Unit {
    val currentContext by rememberUpdatedState(context)
    val currentSourceLabel by rememberUpdatedState(sourceLabel)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val currentOnError by rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(ScanContract()) { scan ->
        val payload = scan.contents
        if (payload.isNullOrBlank()) {
            currentOnCancel()
        } else {
            val invocationContext = currentContext.request.invocationContext
            val execution = runCatching {
                As100QrScanMethod.execute(
                    request = As100QrScanMethod.request(
                        action = As100QrScanMethod.ID,
                        context = invocationContext.asMap(As100QrScanMethod.ID) +
                            currentContext.action.settings + mapOf(
                                "qr_payload" to payload,
                                "qr_source" to currentSourceLabel
                            )
                    ),
                    settingsState = null,
                    transport = currentContext.request.source
                ).withInvocationContext(invocationContext)
            }.getOrElse { error ->
                currentOnError(error.message ?: "QR capture failed.")
                return@rememberLauncherForActivityResult
            }
            currentOnResult(execution)
        }
    }

    return {
        launcher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Point the camera at a QR code")
                setBeepEnabled(false)
                setOrientationLocked(false)
                setBarcodeImageEnabled(false)
            }
        )
    }
}

/**
 * Operational QR capability.
 *
 * External invocation enters capture immediately. The scanner result is converted
 * into the canonical AS100 result and returned without a second confirmation gate.
 */
object QrScanCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100QrScanMethod.ID
    override val title: String = "Scan QR code"
    override val description: String = "Scan and return QR evidence."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var launched by remember(context.action.canonicalId) { mutableStateOf(false) }
        var status by remember { mutableStateOf("Opening camera…") }

        val launchScanner = rememberQrCapabilityInvocation(
            context = context,
            sourceLabel = "camera_zxing",
            onResult = {
                status = "QR code captured."
                onConfirmed(it)
            },
            onCancel = {
                status = "Scan cancelled."
                onCancel()
            },
            onError = { status = it }
        )

        LaunchedEffect(context.isExternalInvocation) {
            if (context.isExternalInvocation && !launched) {
                launched = true
                status = "Point the camera at a QR code."
                launchScanner()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                status = "Point the camera at a QR code."
                launchScanner()
            }) { Text("Open scanner") }

            Spacer(Modifier.height(24.dp))
            IntentExampleDropdown(
                capabilityId = As100QrScanMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Basic QR scan",
                        description = "Simple intent to capture a QR code",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100QrScanMethod.ID}')"
                    ),
                    IntentExample(
                        label = "With study context",
                        description = "Include study and operator information",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100QrScanMethod.ID}',study_id='study_01',operator_id='operator_001')"
                    )
                )
            )
        }
    }
}
