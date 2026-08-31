package com.example.methodmesh.modules.qrcode

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.OutputFormatter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown

/**
 * Reusable invocation boundary for the barcode capability.
 *
 * Parent capabilities call the returned function instead of embedding QR scanning.
 * The dependency performs camera capture and returns its canonical AS100 result.
 */
@Composable
fun rememberBarcodeCapabilityInvocation(
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
        if (payload.isNullOrEmpty()) {
            currentOnCancel()
        } else {
            val invocationContext = currentContext.request.invocationContext
            val execution = runCatching {
                val method: As100Method = As100BarcodeScanMethod
                method.execute(
                    request = method.request(
                        action = method.id,
                        context = invocationContext.asMap(method.id) +
                            currentContext.action.settings + mapOf(
                                "barcode_payload" to payload,
                                "barcode_source" to currentSourceLabel,
                                "barcode_format" to scan.formatName.orEmpty().ifBlank { "UNKNOWN" }
                            )
                    ),
                    settingsState = null,
                    transport = currentContext.request.source
                ).withInvocationContext(invocationContext)
            }.getOrElse { error ->
                currentOnError(error.message ?: "Barcode capture failed.")
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
                setOrientationLocked(true)
                setBarcodeImageEnabled(false)
            }
        )
    }
}

internal fun barcodeFormats(raw: String?): Collection<String>? = raw
    ?.split(Regex("[|,;\\s]+"))
    ?.map { it.trim().uppercase() }
    ?.filter(String::isNotBlank)
    ?.distinct()
    ?.takeIf(List<String>::isNotEmpty)

/**
 * Operational barcode capability.
 *
 * External invocation enters capture immediately. The scanner result is converted
 * into the canonical AS100 result and returned without a second confirmation gate.
 */
private class CodeScanCapabilityScreen(
    override val capabilityId: String,
    override val title: String,
    override val description: String
) : CapabilityScreenSpec {

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        // Activity-result transitions can briefly recreate the Compose surface.
        // Persist the launch/finish guard so returning from ZXing cannot open a
        // second scanner before the result has been delivered upstream.
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var scanFinished by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var barcodeFormatsValue by rememberSaveable {
            mutableStateOf(context.action.settings["barcode_formats"] ?: context.action.settings["input_barcode_formats"] ?: "")
        }
        var status by remember { mutableStateOf("Opening camera…") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val scannerContext = context.copy(
            action = context.action.copy(settings = context.action.settings + mapOf("barcode_formats" to barcodeFormatsValue))
        )

        LaunchedEffect(barcodeFormatsValue) {
            context.onSettingsChanged(mapOf("barcode_formats" to barcodeFormatsValue))
        }

        val launchScanner = rememberBarcodeCapabilityInvocation(
            context = scannerContext,
            sourceLabel = "camera_zxing",
            onResult = {
                scanFinished = true
                launched = true
                status = "Code captured."
                result = it
            },
            onCancel = {
                scanFinished = true
                launched = true
                status = "Scan cancelled."
                if (context.startsImmediately) onCancel()
            },
            onError = { status = it }
        )

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched && !scanFinished) {
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
                launched = true
                scanFinished = false
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
                CodeFormatChooser(
                    selectedValue = barcodeFormatsValue,
                    onSelected = { barcodeFormatsValue = it }
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    launched = true
                    scanFinished = false
                    result = null
                    status = "Point the camera at a QR, Data Matrix, or barcode."
                    launchScanner()
                }) { Text(if (result == null) "Open scanner" else "Scan again") }

                Spacer(Modifier.height(24.dp))
                IntentExampleDropdown(
                    capabilityId = capabilityId,
                    examples = listOf(
                        IntentExample(
                            label = "Automatic code detection",
                            description = "Detect QR, Data Matrix, and common 1D barcodes",
                            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId')"
                        ),
                        IntentExample(
                            label = "Restricted formats",
                            description = "Accept only Data Matrix and Code 128",
                            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',input_barcode_formats='DATA_MATRIX|CODE_128')"
                        ),
                        IntentExample(
                            label = "With study context",
                            description = "Include study and operator information",
                            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',input_study_id='study_01',operator_id='operator_001')"
                        )
                    )
                )
            }
        }
    }
}

val BarcodeScanCapabilityScreen: CapabilityScreenSpec = CodeScanCapabilityScreen(
    capabilityId = As100BarcodeScanMethod.ID,
    title = "Scan barcode",
    description = "Automatically detect QR, Data Matrix, and common 1D barcode formats."
)

@Composable
private fun CodeFormatChooser(
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Accepted code formats", style = MaterialTheme.typography.labelLarge)
        Text(
            CodeFormatPreset.options.firstOrNull { it.value == selectedValue }?.description
                ?: "Custom format set from an external intent.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        CodeFormatPreset.options.forEach { preset ->
            val selected = preset.value == selectedValue
            val colors = if (selected) {
                ButtonDefaults.buttonColors()
            } else {
                ButtonDefaults.outlinedButtonColors()
            }
            val buttonModifier = Modifier.fillMaxWidth()
            if (selected) {
                Button(
                    onClick = { onSelected(preset.value) },
                    modifier = buttonModifier,
                    colors = colors
                ) {
                    Text("✓ ${preset.label}")
                }
            } else {
                OutlinedButton(
                    onClick = { onSelected(preset.value) },
                    modifier = buttonModifier,
                    colors = colors
                ) {
                    Text(preset.label)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private data class CodeFormatPreset(
    val label: String,
    val value: String,
    val description: String
) {
    companion object {
        val options = listOf(
            CodeFormatPreset("Automatic detection", "", "Accept QR, Data Matrix, and common one-dimensional barcodes."),
            CodeFormatPreset("QR codes only", "QR_CODE", "Use when the expected token is definitely a QR code."),
            CodeFormatPreset("Data Matrix only", "DATA_MATRIX", "Use for medication packs, laboratory labels, and compact 2D identifiers."),
            CodeFormatPreset("QR + Data Matrix", "QR_CODE|DATA_MATRIX", "Accept either common 2D code format."),
            CodeFormatPreset("1D barcodes", "CODE_128|CODE_39|EAN_13|EAN_8|UPC_A|UPC_E", "Accept common linear barcode formats."),
            CodeFormatPreset("Data Matrix + Code 128", "DATA_MATRIX|CODE_128", "Useful for mixed clinical/lab label workflows.")
        )
    }
}
