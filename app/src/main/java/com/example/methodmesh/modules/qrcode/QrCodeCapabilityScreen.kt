package com.example.methodmesh.modules.qrcode

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TemporalContext
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.time.Instant

/**
 * Reusable invocation boundary for the barcode capability.
 *
 * Parent capabilities call the returned function instead of embedding QR scanning.
 * The dependency performs camera capture and returns the canonical AS100 result.
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
            val execution = runCatching {
                buildBarcodeScanResult(
                    context = currentContext,
                    payload = payload,
                    sourceLabel = currentSourceLabel,
                    formatName = scan.formatName.orEmpty().ifBlank { "UNKNOWN" },
                    scanTimeIso = Instant.now().toString()
                )
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
                // ZXing represents all supported formats by leaving this unset.
                if (requestedFormats != null) setDesiredBarcodeFormats(requestedFormats)
                setPrompt("Point the camera at a QR, Data Matrix, or barcode")
                setBeepEnabled(false)
                // Do not pin the ZXing capture activity to portrait. Dependency
                // launches should follow the device so the viewfinder line stays
                // horizontal in both portrait and landscape.
                setOrientationLocked(false)
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
 * v1.05 barcode instrument.
 *
 * Capture produces a mutable working result. Commit freezes that result. Native
 * runs then expose beef-first share/save/copy actions on the same screen; ODK,
 * protocol and schedule origins return the frozen canonical payload immediately
 * to their caller when Commit is pressed.
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
        val appContext = LocalContext.current
        val automaticReturn = context.completionMode == CapabilityCompletionMode.AutomaticReturn
        val finishToLauncher = context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
            context.request.settings["input_methodmesh_finish_to_launcher"] == "true"
        val presetResultAction = PresetResultAction.normalize(
            context.request.settings["methodmesh_preset_result_action"]
                ?: context.request.settings["input_methodmesh_preset_result_action"]
                ?: PresetResultAction.HOME
        )
        val formatSettingVisible = context.settingShouldBeShown("barcode_formats")

        var barcodeFormatsValue by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["barcode_formats"] ?: context.action.settings["input_barcode_formats"] ?: "")
        }
        var status by rememberSaveable(context.action.canonicalId) {
            mutableStateOf("Scanning…")
        }

        // Working result state. The embedded camera stays live while this result
        // changes; only a deliberate Commit freezes the execution payload.
        var workingPayload by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var workingFormat by rememberSaveable(context.action.canonicalId) { mutableStateOf("UNKNOWN") }
        var workingSource by rememberSaveable(context.action.canonicalId) { mutableStateOf("camera_zxing") }
        var workingScanTime by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var workingExecutionId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var workingObservationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var workingTransformationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var workingRelationshipIds by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var workingSystemTimeMs by rememberSaveable(context.action.canonicalId) { mutableStateOf<Long?>(null) }

        // Frozen result state. These fields deliberately do not mutate while a
        // later scan is visible; Edit / new scan explicitly clears commitment.
        var committedPayload by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedFormat by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedSource by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedScanTime by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedExecutionId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedObservationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedTransformationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedRelationshipIds by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedSystemTimeMs by rememberSaveable(context.action.canonicalId) { mutableStateOf<Long?>(null) }

        var includeFullJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var showTechnicalDetails by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }

        val scannerContext = context.copy(
            action = context.action.copy(
                settings = context.action.settings + mapOf("barcode_formats" to barcodeFormatsValue)
            )
        )

        val workingResult = remember(
            workingPayload,
            workingFormat,
            workingSource,
            workingScanTime,
            workingExecutionId,
            workingObservationId,
            workingTransformationId,
            workingRelationshipIds,
            workingSystemTimeMs,
            scannerContext.action.settings
        ) {
            resultFromSavedState(
                context = scannerContext,
                payload = workingPayload,
                formatName = workingFormat,
                sourceLabel = workingSource,
                scanTimeIso = workingScanTime,
                executionId = workingExecutionId,
                observationId = workingObservationId,
                transformationId = workingTransformationId,
                relationshipIds = workingRelationshipIds,
                systemTimeEpochMs = workingSystemTimeMs
            )
        }

        val committedResult = remember(
            committedPayload,
            committedFormat,
            committedSource,
            committedScanTime,
            committedExecutionId,
            committedObservationId,
            committedTransformationId,
            committedRelationshipIds,
            committedSystemTimeMs,
            scannerContext.action.settings
        ) {
            resultFromSavedState(
                context = scannerContext,
                payload = committedPayload,
                formatName = committedFormat.orEmpty().ifBlank { "UNKNOWN" },
                sourceLabel = committedSource.orEmpty().ifBlank { "camera_zxing" },
                scanTimeIso = committedScanTime,
                executionId = committedExecutionId,
                observationId = committedObservationId,
                transformationId = committedTransformationId,
                relationshipIds = committedRelationshipIds,
                systemTimeEpochMs = committedSystemTimeMs
            )
        }

        val committedFields = remember(committedResult?.request?.id?.value) {
            committedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        }
        val fullJsonText = remember(committedResult?.request?.id?.value) {
            committedResult?.let {
                OutputFormatter.format(
                    result = it,
                    returnMode = ReturnMode.Json,
                    includeProvenance = true,
                    payloadMode = OutputFormatter.PayloadMode.FULL
                )
            }.orEmpty()
        }

        LaunchedEffect(barcodeFormatsValue) {
            context.onSettingsChanged(mapOf("barcode_formats" to barcodeFormatsValue))
        }

        fun clearWorkingForNewScan() {
            workingPayload = null
            workingFormat = "UNKNOWN"
            workingSource = "camera_zxing"
            workingScanTime = null
            workingExecutionId = null
            workingObservationId = null
            workingTransformationId = null
            workingRelationshipIds = null
            workingSystemTimeMs = null
        }

        fun clearCommitForEdit() {
            committedPayload = null
            committedFormat = null
            committedSource = null
            committedScanTime = null
            committedExecutionId = null
            committedObservationId = null
            committedTransformationId = null
            committedRelationshipIds = null
            committedSystemTimeMs = null
            includeFullJson = false
            exportStatus = null
        }

        fun acceptDecodedPayload(payload: String, formatName: String) {
            if (payload.isBlank()) return
            val normalizedFormat = formatName.ifBlank { "UNKNOWN" }
            // Continuous decoding reports the same stationary code repeatedly.
            // Keep the current result stable until a genuinely different code
            // or format is observed.
            if (payload == workingPayload && normalizedFormat == workingFormat) return
            val captured = runCatching {
                buildBarcodeScanResult(
                    context = scannerContext,
                    payload = payload,
                    sourceLabel = "camera_zxing",
                    formatName = normalizedFormat,
                    scanTimeIso = Instant.now().toString()
                )
            }.getOrElse { error ->
                status = error.message ?: "Barcode capture failed."
                return
            }
            val fields = captured.observations.firstOrNull()?.values.orEmpty()
            workingPayload = fields["barcode_payload"]
            workingFormat = fields["barcode_format"].orEmpty().ifBlank { "UNKNOWN" }
            workingSource = fields["barcode_source"].orEmpty().ifBlank { "camera_zxing" }
            workingScanTime = fields["barcode_scan_time_iso"].orEmpty().ifBlank { Instant.now().toString() }
            workingExecutionId = captured.request.id.value
            workingObservationId = captured.observations.firstOrNull()?.id?.value
            workingTransformationId = captured.transformations.firstOrNull()?.id?.value
            workingRelationshipIds = captured.relationships.joinToString("|") { it.id.value }.ifBlank { null }
            workingSystemTimeMs = captured.request.temporalContext.systemTimeEpochMs
            status = "Latest code detected."
            exportStatus = null
        }

        fun commitWorkingResult() {
            val result = workingResult ?: return
            committedPayload = workingPayload
            committedFormat = workingFormat
            committedSource = workingSource
            committedScanTime = workingScanTime
            committedExecutionId = workingExecutionId
            committedObservationId = workingObservationId
            committedTransformationId = workingTransformationId
            committedRelationshipIds = workingRelationshipIds
            committedSystemTimeMs = workingSystemTimeMs
            status = "Committed."
            if (automaticReturn) onConfirmed(result)
        }

        fun saveCommitted(includeJson: Boolean): Boolean {
            val payload = committedPayload.orEmpty()
            if (payload.isBlank()) return false
            return runCatching {
                val exported = OutputExportRepository.saveToDownloads(
                    context = appContext,
                    label = "barcode_scan",
                    text = payload,
                    mediaUris = emptyList(),
                    jsonText = if (includeJson) fullJsonText else ""
                )
                exportStatus = "Saved ${exported.summary}"
            }.onFailure {
                exportStatus = "Save failed: ${it.message ?: "storage error"}"
            }.isSuccess
        }

        fun finishManualRun() {
            val result = committedResult ?: return
            if (context.isNativePresetRun && presetResultAction == PresetResultAction.SAVE) {
                if (!saveCommitted(includeJson = false)) return
            }
            if (context.isNativePresetRun && !finishToLauncher) {
                appContext.startActivity(
                    Intent(appContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } else {
                onConfirmed(result)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Scan code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "QR, Data Matrix, Aztec, PDF417 and common barcodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))

                if (committedResult != null && !automaticReturn) {
                    CommittedBarcodePanel(
                        fields = committedFields,
                        showTechnicalDetails = showTechnicalDetails,
                        onToggleTechnicalDetails = { showTechnicalDetails = !showTechnicalDetails },
                        onCopy = { value, label -> copyValue(appContext, value, label) }
                    )
                    committedFields["barcode_payload_url"]?.toString()?.takeIf(String::isNotBlank)?.let { url ->
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openHttpLink(appContext, url) }
                        ) { Text("Open link") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Include full JSON", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Share/copy append debug JSON text; Save adds metadata.json.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            shareText(
                                appContext,
                                committedPayload.orEmpty(),
                                if (includeFullJson) fullJsonText else ""
                            )
                        }
                    ) { Text("Share") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val text = ResultShare.buildShareText(
                                committedPayload.orEmpty(),
                                if (includeFullJson) fullJsonText else ""
                            )
                            copyValue(appContext, text, "Barcode result")
                        }
                    ) { Text("Copy result") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { saveCommitted(includeFullJson) }
                    ) { Text("Save to Downloads") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { finishManualRun() }
                    ) {
                        Text(
                            when {
                                context.isNativePresetRun && presetResultAction == PresetResultAction.SAVE -> "Save and finish"
                                finishToLauncher -> "Done"
                                else -> "Home"
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            clearCommitForEdit()
                            clearWorkingForNewScan()
                            status = "Scanning…"
                        }
                    ) { Text("Edit / new scan") }
                    exportStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    // Working-screen hierarchy: scanner first, latest result second,
                    // configuration third, and action buttons last.
                    EmbeddedBarcodeScannerWindow(
                        formatsRaw = barcodeFormatsValue,
                        active = committedResult == null,
                        onDecoded = ::acceptDecodedPayload
                    )

                    Spacer(Modifier.height(12.dp))
                    if (workingResult != null) {
                        WorkingBarcodePanel(
                            result = workingResult,
                            onCopy = { value, label -> copyValue(appContext, value, label) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The scanner stays live. A different code replaces the current working result until you Commit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Current result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text("No code detected yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Hold a code inside the scanner window. The latest payload will appear here automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (formatSettingVisible) {
                        Spacer(Modifier.height(16.dp))
                        CodeFormatChooser(
                            selectedValue = barcodeFormatsValue,
                            onSelected = { selected ->
                                if (selected != barcodeFormatsValue) {
                                    barcodeFormatsValue = selected
                                    clearWorkingForNewScan()
                                    status = "Format filter changed. Scanning…"
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    workingResult?.observations?.firstOrNull()?.values?.get("barcode_payload_url")
                        ?.takeIf(String::isNotBlank)
                        ?.let { url ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { openHttpLink(appContext, url) }
                            ) { Text("Open link") }
                            Spacer(Modifier.height(8.dp))
                        }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = workingResult != null,
                        onClick = { commitWorkingResult() }
                    ) { Text("Commit") }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (context.stepNumber > 1) {
                            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                        }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddedBarcodeScannerWindow(
    formatsRaw: String,
    active: Boolean,
    onDecoded: (String, String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    var cameraGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 16f / 7f else 4f / 3f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (!cameraGranted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera access is needed to scan codes.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
            }
        } else {
            val onDecodedState = rememberUpdatedState(onDecoded)
            val callback = remember {
                object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        val payload = result.text.orEmpty()
                        if (payload.isNotBlank()) {
                            onDecodedState.value(payload, result.barcodeFormat?.name.orEmpty().ifBlank { "UNKNOWN" })
                        }
                    }
                }
            }
            var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
            val decoderFactory = remember(formatsRaw) { decoderFactoryFor(formatsRaw) }

            AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                factory = { viewContext ->
                    DecoratedBarcodeView(viewContext).apply {
                        setStatusText("")
                        statusView?.visibility = View.GONE
                        setDecoderFactory(decoderFactory)
                        decodeContinuous(callback)
                        tag = formatsRaw
                        scannerView = this
                        if (active) resume()
                    }
                },
                update = { view ->
                    if (view.tag != formatsRaw) {
                        view.pause()
                        view.setDecoderFactory(decoderFactory)
                        view.decodeContinuous(callback)
                        view.tag = formatsRaw
                    }
                    if (active) view.resume() else view.pause()
                }
            )

            DisposableEffect(lifecycleOwner, scannerView, active) {
                val view = scannerView
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> if (active) view?.resume()
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> view?.pause()
                        else -> Unit
                    }
                }
                lifecycleOwner?.lifecycle?.addObserver(observer)
                onDispose {
                    lifecycleOwner?.lifecycle?.removeObserver(observer)
                    view?.pause()
                }
            }
        }
    }
}

private fun decoderFactoryFor(raw: String): DefaultDecoderFactory {
    val requested = barcodeFormats(raw)
        ?.mapNotNull { name -> runCatching { BarcodeFormat.valueOf(name) }.getOrNull() }
        ?.takeIf { it.isNotEmpty() }
    return if (requested == null) DefaultDecoderFactory() else DefaultDecoderFactory(requested)
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return current as? LifecycleOwner
}

@Composable
private fun WorkingBarcodePanel(
    result: ExecutionResult,
    onCopy: (String, String) -> Unit
) {
    val fields = result.observations.firstOrNull()?.values.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Current result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            TappableResultValue("Payload", fields["barcode_payload"].orEmpty(), prominent = true, onCopy = onCopy)
            Spacer(Modifier.height(10.dp))
            TappableResultValue("Format", fields["barcode_format"].orEmpty(), onCopy = onCopy)
            fields["barcode_payload_url"]?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(10.dp))
                TappableResultValue("URL", it, onCopy = onCopy)
            }
        }
    }
}

@Composable
private fun CommittedBarcodePanel(
    fields: Map<String, Any?>,
    showTechnicalDetails: Boolean,
    onToggleTechnicalDetails: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Committed result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            TappableResultValue("Payload", fields["barcode_payload"]?.toString().orEmpty(), prominent = true, onCopy = onCopy)
            Spacer(Modifier.height(10.dp))
            TappableResultValue("Format", fields["barcode_format"]?.toString().orEmpty(), onCopy = onCopy)
            fields["barcode_payload_url"]?.toString()?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(10.dp))
                TappableResultValue("URL", it, onCopy = onCopy)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                if (showTechnicalDetails) "Hide technical details" else "Technical details",
                modifier = Modifier.clickable(onClick = onToggleTechnicalDetails),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (showTechnicalDetails) {
                listOf(
                    "Payload kind" to "barcode_payload_kind",
                    "SHA-256" to "barcode_payload_sha256",
                    "Evidence format" to BarcodeEvidenceFields.FORMAT_FIELD,
                    "Evidence hash" to BarcodeEvidenceFields.HASH_FIELD,
                    "Scan time" to "barcode_scan_time_iso",
                    "Source" to "barcode_source"
                ).forEach { (label, key) ->
                    fields[key]?.toString()?.takeIf(String::isNotBlank)?.let { value ->
                        Spacer(Modifier.height(10.dp))
                        TappableResultValue(label, value, onCopy = onCopy)
                    }
                }
            }
        }
    }
}

@Composable
private fun TappableResultValue(
    label: String,
    value: String,
    prominent: Boolean = false,
    onCopy: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = value.isNotBlank()) { onCopy(value, label) }
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "—" },
            style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Normal
        )
        if (value.isNotBlank()) {
            Text("Tap to copy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun copyValue(context: Context, value: String, label: String) {
    if (value.isBlank()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun openHttpLink(context: Context, url: String) {
    val safe = BarcodePayloadSemantics.safeHttpUrl(url) ?: return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)))
    }.onFailure {
        Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareText(context: Context, payload: String, jsonText: String) {
    if (payload.isBlank() && jsonText.isBlank()) return
    ResultShare.share(
        context = context,
        chooserTitle = "Share code result",
        text = payload,
        attachments = emptyList(),
        jsonText = jsonText
    )
}

private fun resultFromSavedState(
    context: CapabilityScreenContext,
    payload: String?,
    formatName: String,
    sourceLabel: String,
    scanTimeIso: String?,
    executionId: String?,
    observationId: String?,
    transformationId: String?,
    relationshipIds: String?,
    systemTimeEpochMs: Long?
): ExecutionResult? {
    if (payload.isNullOrEmpty() || scanTimeIso.isNullOrEmpty()) return null
    return buildBarcodeScanResult(
        context = context,
        payload = payload,
        sourceLabel = sourceLabel,
        formatName = formatName,
        scanTimeIso = scanTimeIso,
        executionId = executionId,
        observationId = observationId,
        transformationId = transformationId,
        relationshipIds = relationshipIds,
        systemTimeEpochMs = systemTimeEpochMs
    )
}

private fun buildBarcodeScanResult(
    context: CapabilityScreenContext,
    payload: String,
    sourceLabel: String,
    formatName: String,
    scanTimeIso: String,
    executionId: String? = null,
    observationId: String? = null,
    transformationId: String? = null,
    relationshipIds: String? = null,
    systemTimeEpochMs: Long? = null
): ExecutionResult {
    val invocationContext = context.request.invocationContext
    val method: As100Method = As100BarcodeScanMethod
    val methodContext = invocationContext.asMap(method.id) +
        context.action.settings + mapOf(
            "barcode_payload" to payload,
            "barcode_source" to sourceLabel,
            "barcode_format" to formatName.ifBlank { "UNKNOWN" },
            "barcode_scan_time_iso" to scanTimeIso
        )

    val canRestoreIdentity = !executionId.isNullOrBlank() &&
        !observationId.isNullOrBlank() &&
        !transformationId.isNullOrBlank() &&
        systemTimeEpochMs != null

    val result = if (canRestoreIdentity) {
        val request = As100ExecutionEngine.request(
            action = method.id,
            method = method.ref,
            id = ArchitectureId(executionId!!),
            context = methodContext,
            temporalContext = TemporalContext(systemTimeEpochMs = systemTimeEpochMs!!)
        )
        As100BarcodeScanMethod.executeWithIdentity(
            request = request,
            observationId = ArchitectureId(observationId!!),
            transformationId = ArchitectureId(transformationId!!)
        )
    } else {
        method.execute(
            request = method.request(action = method.id, context = methodContext),
            settingsState = null,
            transport = context.request.source
        )
    }
    val contextual = result.withInvocationContext(invocationContext)
    val savedRelationshipIds = relationshipIds.orEmpty()
        .split('|')
        .filter { it.isNotBlank() }
    return if (savedRelationshipIds.size == contextual.relationships.size) {
        contextual.copy(
            relationships = contextual.relationships.mapIndexed { index, relationship ->
                relationship.copy(id = ArchitectureId(savedRelationshipIds[index]))
            }
        )
    } else {
        contextual
    }
}

val BarcodeScanCapabilityScreen: CapabilityScreenSpec = CodeScanCapabilityScreen(
    capabilityId = As100BarcodeScanMethod.ID,
    title = "Scan code",
    description = "Automatically detect QR, Data Matrix, Aztec, PDF417, and common 1D barcode formats."
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
                ?: "Custom format set supplied by the caller.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CodeFormatPreset.options.forEach { preset ->
            val selected = preset.value == selectedValue
            if (selected) {
                Button(
                    onClick = { onSelected(preset.value) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors()
                ) { Text("✓ ${preset.label}") }
            } else {
                OutlinedButton(
                    onClick = { onSelected(preset.value) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(preset.label) }
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
            CodeFormatPreset("Automatic detection", "", "Accept every code format supported by the scanner."),
            CodeFormatPreset("QR codes only", "QR_CODE", "Use when the expected token is definitely a QR code."),
            CodeFormatPreset("Data Matrix only", "DATA_MATRIX", "Useful for medication packs, laboratory labels and compact 2D identifiers."),
            CodeFormatPreset("QR + Data Matrix", "QR_CODE|DATA_MATRIX", "Accept either common square 2D code format."),
            CodeFormatPreset("All 2D codes", "QR_CODE|DATA_MATRIX|PDF_417|AZTEC", "Accept QR, Data Matrix, PDF417 and Aztec."),
            CodeFormatPreset("1D barcodes", "CODE_128|CODE_39|EAN_13|EAN_8|UPC_A|UPC_E", "Accept common linear barcode formats."),
            CodeFormatPreset("Data Matrix + Code 128", "DATA_MATRIX|CODE_128", "Useful for mixed clinical and laboratory label workflows.")
        )
    }
}
