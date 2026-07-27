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
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.OutputFormatter
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
                                "qr_source" to currentSourceLabel,
                                "barcode_format" to scan.formatName.orEmpty().ifBlank { "UNKNOWN" }
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
                val requestedFormats = barcodeFormats(currentContext.action.settings["barcode_formats"])
                // ZXing represents "all supported formats" by leaving the
                // desired-formats extra unset. ScanOptions.ALL_CODE_TYPES is
                // itself null in 4.3.0, so it must not pass through a Kotlin
                // non-null return boundary.
                if (requestedFormats != null) {
                    setDesiredBarcodeFormats(requestedFormats)
                }
                setPrompt("Point the camera at a QR, Data Matrix, or barcode")
                setBeepEnabled(false)
                setOrientationLocked(false)
                setBarcodeImageEnabled(false)
            }
        )
    }
}

internal fun barcodeFormats(raw: String?): Collection<String>? = raw
    ?.split('|', ',', ';')
    ?.map { it.trim().uppercase() }
    ?.filter(String::isNotBlank)
    ?.distinct()
    ?.takeIf(List<String>::isNotEmpty)

/**
 * Operational QR capability.
 *
 * External invocation enters capture immediately. The scanner result is converted
 * into the canonical AS100 result and returned without a second confirmation gate.
 */
object QrScanCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100QrScanMethod.ID
    override val title: String = "Scan code"
    override val description: String = "Automatically detect QR, Data Matrix, and common 1D barcode formats."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var launched by remember(context.action.canonicalId) { mutableStateOf(false) }
        var status by remember { mutableStateOf("Opening camera…") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        val launchScanner = rememberQrCapabilityInvocation(
            context = context,
            sourceLabel = "camera_zxing",
            onResult = {
                status = "Code captured."
                result = it
            },
            onCancel = {
                status = "Scan cancelled."
                if (context.startsImmediately) onCancel()
            },
            onError = { status = it }
        )

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched) {
                launched = true
                status = "Point the camera at a QR, Data Matrix, or barcode."
                launchScanner()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                status = "Point the camera at a QR, Data Matrix, or barcode."
                launchScanner()
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (result == null && context.startsImmediately) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                }
                Text(status, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    result = null
                    status = "Point the camera at a QR, Data Matrix, or barcode."
                    launchScanner()
                }) { Text(if (result == null) "Open scanner" else "Scan again") }

                Spacer(Modifier.height(24.dp))
                IntentExampleDropdown(
                    capabilityId = As100QrScanMethod.ID,
                    examples = listOf(
                        IntentExample(
                            label = "Automatic code detection",
                            description = "Detect QR, Data Matrix, and common 1D barcodes",
                            intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100QrScanMethod.ID}')"
                        ),
                        IntentExample(
                            label = "Restricted formats",
                            description = "Accept only Data Matrix and Code 128",
                            intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100QrScanMethod.ID}',barcode_formats='DATA_MATRIX|CODE_128')"
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
}
