package com.example.methodmesh.modules.qrcode

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TemporalContext
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.io.File
import java.time.Instant
import kotlinx.coroutines.delay

private val CLONE_ORDER = listOf(
    BarcodeFormat.QR_CODE,
    BarcodeFormat.CODE_128,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.AZTEC,
    BarcodeFormat.PDF_417,
    BarcodeFormat.CODE_39,
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E
)

private data class CloneRenderedCode(val bitmap: Bitmap, val format: BarcodeFormat)

/**
 * Clone is deliberately a normal MethodMesh capability surface, not an immersive
 * capture activity. The embedded scanner is the first instrument on the screen so
 * the host can retain its normal preamble/preset affordances.
 *
 * There is no visible Commit gate. Share / Copy / Save / Return each snapshots the
 * current scan + selected symbology atomically. That snapshot is cached only while
 * the live state is unchanged; rescan or format change makes the next action create
 * a new canonical execution.
 */
private class CodeCloneCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100BarcodeCloneMethod.ID
    override val title: String = "Clone code"
    override val description: String = "Scan a code and re-present its exact payload in the same or another compatible symbology."

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
        val presetPayloadMode = OutputFormatter.PayloadMode.normalize(
            context.request.settings["payload_mode"]
                ?: context.request.settings["input_payload_mode"]
                ?: context.action.settings["payload_mode"]
                ?: context.action.settings["input_payload_mode"]
                ?: OutputFormatter.PayloadMode.CORE
        )
        val formatEditable = context.settingShouldBeShown("barcode_clone_format")
        val cycleEditable = context.settingShouldBeShown("barcode_auto_cycle")
        val returnTextEditable = context.settingShouldBeShown("barcode_return_text_payload")

        val configuredFormat = context.action.settings["barcode_clone_format"]
            ?: context.action.settings["input_barcode_clone_format"]
            ?: "SOURCE"
        val configuredCycle = (context.action.settings["barcode_auto_cycle"]
            ?: context.action.settings["input_barcode_auto_cycle"]
            ?: "false").toBoolean()
        val configuredReturnText = (context.action.settings["barcode_return_text_payload"]
            ?: context.action.settings["input_barcode_return_text_payload"]
            ?: "true").toBoolean()

        var sourcePayload by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var sourceFormat by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var sourceScanTime by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var scannerActive by rememberSaveable(context.action.canonicalId) { mutableStateOf(true) }
        var formatName by rememberSaveable(context.action.canonicalId) { mutableStateOf(configuredFormat) }
        var cycling by rememberSaveable(context.action.canonicalId) { mutableStateOf(configuredCycle) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Point the camera at a code.") }
        var presentationMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var includeJsonSidecar by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.isNativePresetRun && presetPayloadMode != OutputFormatter.PayloadMode.CORE)
        }
        var returnTextPayload by rememberSaveable(context.action.canonicalId) { mutableStateOf(configuredReturnText) }
        var includePayloadInShare by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var showTechnicalDetails by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        // Invisible action snapshot cache. It provides stable execution/image IDs
        // for repeated actions on unchanged live state without creating a staged UI.
        var snapshotKey by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotPayload by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotSourceFormat by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotCloneFormat by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotScanTime by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotCloneTime by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotImageUri by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotReturnTextPayload by rememberSaveable(context.action.canonicalId) { mutableStateOf<Boolean?>(null) }
        var snapshotExecutionId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotObservationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotTransformationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotRelationshipIds by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var snapshotSystemTimeMs by rememberSaveable(context.action.canonicalId) { mutableStateOf<Long?>(null) }

        val displayPayload = sourcePayload.orEmpty()
        val compatible = remember(displayPayload) { CLONE_ORDER.filter { cloneCanEncode(displayPayload, it) } }
        val sourceAsFormat = sourceFormat?.let(::cloneParseFormatOrNull)
        val selected = cloneParseFormatOrNull(formatName)
        val effectiveFormat = when {
            formatName == "SOURCE" && sourceAsFormat != null && sourceAsFormat in compatible -> sourceAsFormat
            formatName == "SOURCE" && compatible.isNotEmpty() -> compatible.first()
            !formatEditable && selected != null -> selected
            selected != null && selected in compatible -> selected
            sourceAsFormat != null && sourceAsFormat in compatible -> sourceAsFormat
            compatible.isNotEmpty() -> compatible.first()
            else -> selected ?: BarcodeFormat.QR_CODE
        }
        val rendered = remember(displayPayload, effectiveFormat.name) {
            if (displayPayload.isEmpty()) null
            else runCatching { cloneRenderCode(displayPayload, effectiveFormat) }.getOrNull()
        }
        val liveKey = sourceScanTime?.let { "$it|${sourceFormat.orEmpty()}|${effectiveFormat.name}|$returnTextPayload" }

        val restoredSnapshot = remember(
            snapshotKey, snapshotPayload, snapshotSourceFormat, snapshotCloneFormat, snapshotScanTime,
            snapshotCloneTime, snapshotImageUri, snapshotReturnTextPayload,
            snapshotExecutionId, snapshotObservationId, snapshotTransformationId,
            snapshotRelationshipIds, snapshotSystemTimeMs, context.request.invocationContext
        ) {
            val payload = snapshotPayload
            val sf = snapshotSourceFormat
            val cf = snapshotCloneFormat
            val st = snapshotScanTime
            val ct = snapshotCloneTime
            val imageUri = snapshotImageUri
            val returnText = snapshotReturnTextPayload
            if (payload == null || sf == null || cf == null || st == null || ct == null || imageUri == null || returnText == null) null
            else buildCloneResult(
                context = context,
                payload = payload,
                sourceFormat = sf,
                cloneFormat = cf,
                scanTime = st,
                cloneTime = ct,
                imageUri = imageUri,
                returnTextPayload = returnText,
                executionId = snapshotExecutionId,
                observationId = snapshotObservationId,
                transformationId = snapshotTransformationId,
                relationshipIds = snapshotRelationshipIds,
                systemTimeEpochMs = snapshotSystemTimeMs
            )
        }
        val currentSnapshot = restoredSnapshot.takeIf { snapshotKey != null && snapshotKey == liveKey }

        LaunchedEffect(formatName, cycling, returnTextPayload) {
            context.onSettingsChanged(
                mapOf(
                    "barcode_clone_format" to formatName,
                    "barcode_auto_cycle" to cycling.toString(),
                    "barcode_return_text_payload" to returnTextPayload.toString()
                )
            )
        }

        val cyclingState by rememberUpdatedState(cycling)
        LaunchedEffect(cycling, compatible, formatEditable, displayPayload) {
            if (!formatEditable || !cycling || compatible.size < 2 || displayPayload.isEmpty()) return@LaunchedEffect
            while (cyclingState) {
                delay(1700)
                val current = compatible.indexOf(effectiveFormat).coerceAtLeast(0)
                formatName = compatible[(current + 1) % compatible.size].name
            }
        }

        fun clearSnapshotCache() {
            snapshotKey = null
            snapshotPayload = null
            snapshotSourceFormat = null
            snapshotCloneFormat = null
            snapshotScanTime = null
            snapshotCloneTime = null
            snapshotImageUri = null
            snapshotReturnTextPayload = null
            snapshotExecutionId = null
            snapshotObservationId = null
            snapshotTransformationId = null
            snapshotRelationshipIds = null
            snapshotSystemTimeMs = null
        }

        fun acceptDecodedPayload(payload: String, format: String) {
            if (payload.isBlank()) return
            sourcePayload = payload
            sourceFormat = format.ifBlank { "UNKNOWN" }
            sourceScanTime = Instant.now().toString()
            scannerActive = false
            formatName = configuredFormat
            cycling = configuredCycle
            clearSnapshotCache()
            exportStatus = ""
            status = "Captured ${clonePrettySource(sourceFormat.orEmpty())}. Swipe to change the clone format."
        }

        fun startRescan() {
            scannerActive = true
            sourcePayload = null
            sourceFormat = null
            sourceScanTime = null
            formatName = configuredFormat
            cycling = configuredCycle
            clearSnapshotCache()
            exportStatus = ""
            status = "Point the camera at a code."
        }

        fun moveFormat(delta: Int) {
            if (!formatEditable || compatible.isEmpty()) return
            if (cycleEditable) cycling = false
            val current = compatible.indexOf(effectiveFormat).coerceAtLeast(0)
            formatName = compatible[(current + delta + compatible.size) % compatible.size].name
            clearSnapshotCache()
        }

        fun ensureSnapshot(): ExecutionResult? {
            val payload = sourcePayload ?: return null
            val sf = sourceFormat ?: "UNKNOWN"
            val st = sourceScanTime ?: return null
            val code = rendered ?: return null
            val key = liveKey ?: return null
            if (snapshotKey == key && restoredSnapshot != null) return restoredSnapshot

            cycling = false
            val ct = Instant.now().toString()
            val imageUri = cloneMaterializeCommittedImage(appContext, code.bitmap, effectiveFormat).toString()
            val result = buildCloneResult(
                context = context,
                payload = payload,
                sourceFormat = sf,
                cloneFormat = effectiveFormat.name,
                scanTime = st,
                cloneTime = ct,
                imageUri = imageUri,
                returnTextPayload = returnTextPayload
            )
            snapshotKey = key
            snapshotPayload = payload
            snapshotSourceFormat = sf
            snapshotCloneFormat = effectiveFormat.name
            snapshotScanTime = st
            snapshotCloneTime = ct
            snapshotImageUri = imageUri
            snapshotReturnTextPayload = returnTextPayload
            snapshotExecutionId = result.request.id.value
            snapshotObservationId = result.observations.firstOrNull()?.id?.value
            snapshotTransformationId = result.transformations.firstOrNull()?.id?.value
            snapshotRelationshipIds = result.relationships.joinToString("|") { it.id.value }.ifBlank { null }
            snapshotSystemTimeMs = result.request.temporalContext.systemTimeEpochMs
            return result
        }

        fun metadataFor(result: ExecutionResult): String {
            val mode = when {
                context.isNativePresetRun && presetPayloadMode != OutputFormatter.PayloadMode.CORE -> presetPayloadMode
                includeJsonSidecar -> OutputFormatter.PayloadMode.FULL
                else -> OutputFormatter.PayloadMode.CORE
            }
            return if (mode == OutputFormatter.PayloadMode.CORE) "" else OutputFormatter.format(
                result = result,
                returnMode = ReturnMode.Json,
                includeProvenance = true,
                payloadMode = mode
            )
        }

        fun shareResult(result: ExecutionResult): Boolean {
            val imageUri = result.observations.firstOrNull()?.values?.get("barcode_clone_image_uri").orEmpty()
            return runCatching {
                cloneShareImage(
                    context = appContext,
                    imageUri = imageUri,
                    format = cloneParseFormat(
                        result.observations.firstOrNull()?.values?.get("barcode_clone_format").orEmpty()
                    ),
                    payloadText = if (includePayloadInShare) sourcePayload.orEmpty() else "",
                    metadataJson = metadataFor(result)
                )
                exportStatus = "Sharing barcode image…"
            }.onFailure {
                exportStatus = "Share failed: ${it.message ?: "no sharing app available"}"
            }.isSuccess
        }

        fun shareCurrent(): Boolean {
            val result = ensureSnapshot() ?: return false
            return shareResult(result)
        }

        fun copyCurrent(): Boolean {
            val result = ensureSnapshot() ?: return false
            val imageUri = result.observations.firstOrNull()?.values?.get("barcode_clone_image_uri").orEmpty()
            return runCatching {
                cloneCopyImage(
                    context = appContext,
                    imageUri = imageUri,
                    payloadText = if (includePayloadInShare) sourcePayload.orEmpty() else ""
                )
                exportStatus = "Barcode image copied."
            }.onFailure {
                exportStatus = "Copy failed: ${it.message ?: "clipboard error"}"
            }.isSuccess
        }

        fun saveResult(result: ExecutionResult): Boolean {
            val imageUri = result.observations.firstOrNull()?.values?.get("barcode_clone_image_uri").orEmpty()
            return runCatching {
                val saved = cloneSaveImage(
                    context = appContext,
                    imageUri = imageUri,
                    metadataJson = metadataFor(result)
                )
                exportStatus = "Saved ${saved.summary}"
            }.onFailure {
                exportStatus = "Save failed: ${it.message ?: "storage error"}"
            }.isSuccess
        }

        fun saveCurrent(): Boolean {
            val result = ensureSnapshot() ?: return false
            return saveResult(result)
        }

        fun returnCurrent() {
            val result = ensureSnapshot() ?: return
            onConfirmed(result)
        }

        fun finishCurrent() {
            val result = ensureSnapshot() ?: return
            if (!context.isNativePresetRun || !context.isLastStep) {
                onConfirmed(result)
                return
            }
            when (presetResultAction) {
                PresetResultAction.SHARE -> {
                    if (!shareResult(result)) return
                    onConfirmed(result)
                }
                PresetResultAction.SAVE -> {
                    if (!saveResult(result)) return
                    onConfirmed(result)
                }
                else -> {
                    if (finishToLauncher) {
                        onConfirmed(result)
                    } else {
                        appContext.startActivity(
                            Intent(appContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            }
        }

        DisposableEffect(presentationMode) {
            val activity = appContext.findCloneActivity()
            val oldBrightness = activity?.window?.attributes?.screenBrightness
            if (presentationMode && activity != null) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val attrs = activity.window.attributes
                attrs.screenBrightness = 1f
                activity.window.attributes = attrs
            }
            onDispose {
                if (activity != null) {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (oldBrightness != null) {
                        val attrs = activity.window.attributes
                        attrs.screenBrightness = oldBrightness
                        activity.window.attributes = attrs
                    }
                }
            }
        }

        if (presentationMode) {
            Dialog(
                onDismissRequest = { presentationMode = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().clickable { presentationMode = false },
                    color = Color.White
                ) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        rendered?.let {
                            Image(
                                bitmap = it.bitmap.asImageBitmap(),
                                contentDescription = "Cloned ${clonePrettyFormat(it.format)} code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.10f))
                        ) {
                            Text(
                                "Tap anywhere to return",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = Color.Black,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CloneHeader(
                sourceFormat = sourceFormat,
                cloneFormat = if (displayPayload.isEmpty()) null else effectiveFormat
            )

            BarcodePresetAuthoring(
                context = context,
                methodId = As100BarcodeCloneMethod.ID,
                methodName = "Code clone",
                currentSettings = mapOf(
                    "barcode_clone_format" to formatName,
                    "barcode_auto_cycle" to cycling.toString(),
                    "barcode_return_text_payload" to returnTextPayload.toString()
                )
            )

            CloneEmbeddedScannerWindow(
                active = scannerActive,
                status = status,
                capturedFormat = sourceFormat,
                onDecoded = ::acceptDecodedPayload
            )

            if (displayPayload.isEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        status,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = ::startRescan) { Text("Scan another") }
                }

                CloneCodeHero(
                    rendered = rendered,
                    swipeEnabled = formatEditable && compatible.size > 1,
                    onSwipe = ::moveFormat,
                    onPresent = { if (rendered != null) presentationMode = true }
                )

                if (formatEditable) {
                    CloneTransport(
                        cycling = cycling,
                        enabled = compatible.size > 1,
                        cycleEditable = cycleEditable,
                        onPrevious = { moveFormat(-1) },
                        onCycle = { if (cycleEditable) cycling = !cycling },
                        onNext = { moveFormat(+1) }
                    )
                    CloneFormatRail(
                        compatible = compatible,
                        selected = effectiveFormat,
                        onSelect = {
                            if (cycleEditable) cycling = false
                            formatName = it.name
                            clearSnapshotCache()
                        }
                    )
                } else if (rendered == null) {
                    Text(
                        "The fixed ${clonePrettyFormat(effectiveFormat)} format cannot represent this exact payload. The payload will not be altered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                ClonePayloadCard(displayPayload) { cloneCopyPayload(appContext, displayPayload) }

                if (returnTextEditable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Return text payload", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "The cloned PNG is always returned. Enable this when the caller also needs the exact decoded text.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = returnTextPayload,
                            onCheckedChange = {
                                returnTextPayload = it
                                clearSnapshotCache()
                            }
                        )
                    }
                }

                Text(
                    "${clonePrettySource(sourceFormat.orEmpty())} → ${clonePrettyFormat(effectiveFormat)} · exact payload preserved",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (automaticReturn) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = ::returnCurrent,
                        enabled = rendered != null
                    ) { Text("Return clone") }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { shareCurrent() },
                            enabled = rendered != null
                        ) { Text("Share image") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { copyCurrent() },
                            enabled = rendered != null
                        ) { Text("Copy image") }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { saveCurrent() },
                        enabled = rendered != null
                    ) { Text("Save image") }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Include payload text in Share / Copy", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Off by default: the barcode PNG is the primary human-facing artefact.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = includePayloadInShare, onCheckedChange = { includePayloadInShare = it })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("JSON sidecar", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (context.isNativePresetRun && presetPayloadMode != OutputFormatter.PayloadMode.CORE)
                                            "Preset return scope requests ${presetPayloadMode.lowercase()} metadata."
                                        else
                                            "Optional for native Share/Save. ODK receives methodmesh_full_json through the roundtrip contract.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = includeJsonSidecar,
                                    onCheckedChange = { includeJsonSidecar = it },
                                    enabled = !(context.isNativePresetRun && presetPayloadMode != OutputFormatter.PayloadMode.CORE)
                                )
                            }
                            TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                                Text(if (showTechnicalDetails) "Hide technical details" else "Technical details")
                            }
                            if (showTechnicalDetails) {
                                val technicalFields = currentSnapshot?.let {
                                    OutputFormatter.fields(it, includeProvenance = false)
                                }.orEmpty()
                                if (technicalFields.isEmpty()) {
                                    CloneTechnicalValue("Source format", sourceFormat.orEmpty()) {
                                        cloneCopyPayload(appContext, sourceFormat.orEmpty())
                                    }
                                    CloneTechnicalValue("Clone format", effectiveFormat.name) {
                                        cloneCopyPayload(appContext, effectiveFormat.name)
                                    }
                                } else {
                                    cloneTechnicalRows(technicalFields).forEach { (label, value) ->
                                        CloneTechnicalValue(label, value) { cloneCopyPayload(appContext, value) }
                                    }
                                }
                            }
                        }
                    }

                    if (exportStatus.isNotBlank()) {
                        Text(exportStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = ::finishCurrent,
                        enabled = rendered != null
                    ) {
                        Text(
                            when {
                                context.isNativePresetRun && context.isLastStep && presetResultAction == PresetResultAction.SHARE -> "Share and finish"
                                context.isNativePresetRun && context.isLastStep && presetResultAction == PresetResultAction.SAVE -> "Save and finish"
                                finishToLauncher -> "Done"
                                else -> "Home"
                            }
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.stepNumber > 1) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onBack) { Text("Back") }
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun CloneHeader(sourceFormat: String?, cloneFormat: BarcodeFormat?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Clone code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Scan. Swipe. Share.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                if (sourceFormat != null && cloneFormat != null) {
                    "${clonePrettySource(sourceFormat)} → ${clonePrettyFormat(cloneFormat)}"
                } else {
                    "LIVE"
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CloneEmbeddedScannerWindow(
    active: Boolean,
    status: String,
    capturedFormat: String?,
    onDecoded: (String, String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = remember(context) { context.findCloneLifecycleOwner() }
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
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!cameraGranted) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera access is needed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "MethodMesh scans locally on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
            } else {
                val onDecodedState = rememberUpdatedState(onDecoded)
                val activeState = rememberUpdatedState(active)
                val callback = remember {
                    object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult) {
                            if (!activeState.value) return
                            val decoded = result.text.orEmpty()
                            if (decoded.isNotBlank()) {
                                onDecodedState.value(decoded, result.barcodeFormat?.name.orEmpty().ifBlank { "UNKNOWN" })
                            }
                        }
                    }
                }
                var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
                val decoderFactory = remember { DefaultDecoderFactory(CLONE_ORDER) }

                AndroidView(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                    factory = { viewContext ->
                        DecoratedBarcodeView(viewContext).apply {
                            setStatusText("")
                            statusView?.visibility = View.GONE
                            setDecoderFactory(decoderFactory)
                            decodeContinuous(callback)
                            scannerView = this
                            if (active) resume()
                        }
                    },
                    update = { view ->
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

                // Screen-relative horizon/aim line. It is horizontal in both
                // portrait and landscape because the whole capability follows the
                // device orientation rather than pinning a portrait capture UI.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.72f)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.82f))
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
            ) {
                Text(
                    when {
                        !cameraGranted -> "CAMERA"
                        active -> "SCANNING"
                        capturedFormat != null -> "CAPTURED"
                        else -> "PAUSED"
                    },
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!active && capturedFormat != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                ) {
                    Text(
                        clonePrettySource(capturedFormat),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (active && cameraGranted) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                ) {
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun Context.findCloneLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return current as? LifecycleOwner
}

@Composable
private fun CloneCodeHero(
    rendered: CloneRenderedCode?,
    swipeEnabled: Boolean,
    onSwipe: (Int) -> Unit,
    onPresent: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(swipeEnabled) {
                if (swipeEnabled) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            when {
                                drag < -70f -> onSwipe(+1)
                                drag > 70f -> onSwipe(-1)
                            }
                        }
                    )
                }
            },
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f)),
        shadowElevation = 3.dp
    ) {
        Box(Modifier.fillMaxWidth().height(340.dp).padding(22.dp), contentAlignment = Alignment.Center) {
            if (rendered != null) {
                Image(
                    bitmap = rendered.bitmap.asImageBitmap(),
                    contentDescription = "Cloned ${clonePrettyFormat(rendered.format)}",
                    modifier = Modifier.fillMaxSize().clickable(onClick = onPresent),
                    contentScale = ContentScale.Fit
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f))
                ) {
                    Text(
                        "FULL SCREEN",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text("No compatible rendering", color = Color.Black, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun CloneTransport(
    cycling: Boolean,
    enabled: Boolean,
    cycleEditable: Boolean,
    onPrevious: () -> Unit,
    onCycle: () -> Unit,
    onNext: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onPrevious, enabled = enabled) { Text("‹") }
        if (cycleEditable) {
            Button(modifier = Modifier.weight(2f), onClick = onCycle, enabled = enabled) {
                Text(if (cycling) "■  Stop" else "▶  Cycle")
            }
        }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onNext, enabled = enabled) { Text("›") }
    }
}

@Composable
private fun CloneFormatRail(
    compatible: List<BarcodeFormat>,
    selected: BarcodeFormat,
    onSelect: (BarcodeFormat) -> Unit
) {
    if (compatible.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        compatible.forEach { format ->
            Surface(
                modifier = Modifier.clickable { onSelect(format) },
                shape = RoundedCornerShape(999.dp),
                color = if (format == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = if (format == selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null
            ) {
                Text(
                    clonePrettyFormat(format),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (format == selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ClonePayloadCard(payload: String, onCopy: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = payload.isNotEmpty(), onClick = onCopy),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("PAYLOAD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    payload.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (payload.isNotEmpty()) Text("COPY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun buildCloneResult(
    context: CapabilityScreenContext,
    payload: String,
    sourceFormat: String,
    cloneFormat: String,
    scanTime: String,
    cloneTime: String,
    imageUri: String,
    returnTextPayload: Boolean,
    executionId: String? = null,
    observationId: String? = null,
    transformationId: String? = null,
    relationshipIds: String? = null,
    systemTimeEpochMs: Long? = null
): ExecutionResult {
    val method: As100Method = As100BarcodeCloneMethod
    val methodContext = context.request.invocationContext.asMap(method.id) +
        context.action.settings + mapOf(
            "barcode_payload" to payload,
            "barcode_source_format" to sourceFormat,
            "barcode_clone_format" to cloneFormat,
            "barcode_scan_time_iso" to scanTime,
            "barcode_clone_time_iso" to cloneTime,
            "barcode_clone_image_uri" to imageUri,
            "barcode_return_text_payload" to returnTextPayload.toString(),
            "barcode_source" to "camera_zxing_clone"
        )
    val canRestoreIdentity = !executionId.isNullOrBlank() && !observationId.isNullOrBlank() &&
        !transformationId.isNullOrBlank() && systemTimeEpochMs != null
    val result = if (canRestoreIdentity) {
        val request = As100ExecutionEngine.request(
            action = method.id, method = method.ref, id = ArchitectureId(executionId!!),
            context = methodContext, temporalContext = TemporalContext(systemTimeEpochMs = systemTimeEpochMs!!)
        )
        As100BarcodeCloneMethod.executeWithIdentity(
            request, ArchitectureId(observationId!!), ArchitectureId(transformationId!!)
        )
    } else {
        method.execute(
            request = method.request(action = method.id, context = methodContext),
            settingsState = null, transport = context.request.source
        )
    }
    val contextual = result.withInvocationContext(context.request.invocationContext)
    val savedRelationshipIds = relationshipIds.orEmpty().split('|').filter(String::isNotBlank)
    return if (savedRelationshipIds.size == contextual.relationships.size) {
        contextual.copy(relationships = contextual.relationships.mapIndexed { index, relationship ->
            relationship.copy(id = ArchitectureId(savedRelationshipIds[index]))
        })
    } else contextual
}

private fun cloneParseFormat(raw: String): BarcodeFormat =
    cloneParseFormatOrNull(raw) ?: BarcodeFormat.QR_CODE

private fun cloneParseFormatOrNull(raw: String?): BarcodeFormat? =
    raw?.takeUnless { it.equals("SOURCE", ignoreCase = true) || it.isBlank() }
        ?.let { runCatching { BarcodeFormat.valueOf(it.trim().uppercase()) }.getOrNull() }

private fun clonePrettySource(raw: String): String =
    cloneParseFormatOrNull(raw)?.let(::clonePrettyFormat) ?: raw.ifBlank { "Unknown" }

private fun clonePrettyFormat(format: BarcodeFormat): String = when (format) {
    BarcodeFormat.QR_CODE -> "QR"
    BarcodeFormat.CODE_128 -> "Code 128"
    BarcodeFormat.DATA_MATRIX -> "Data Matrix"
    BarcodeFormat.AZTEC -> "Aztec"
    BarcodeFormat.PDF_417 -> "PDF417"
    BarcodeFormat.CODE_39 -> "Code 39"
    BarcodeFormat.EAN_13 -> "EAN-13"
    BarcodeFormat.EAN_8 -> "EAN-8"
    BarcodeFormat.UPC_A -> "UPC-A"
    BarcodeFormat.UPC_E -> "UPC-E"
    else -> format.name
}

private fun cloneCanEncode(payload: String, format: BarcodeFormat): Boolean {
    if (payload.isEmpty()) return false
    val (width, height) = cloneWriterDimensions(format)
    return runCatching {
        MultiFormatWriter().encode(payload, format, width, height, cloneHints(format))
    }.isSuccess
}

private fun cloneDimensions(format: BarcodeFormat): Pair<Int, Int> = when (format) {
    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E -> 960 to 320
    BarcodeFormat.PDF_417 -> 960 to 480
    else -> 720 to 720
}

private fun cloneWriterDimensions(format: BarcodeFormat): Pair<Int, Int> = when (format) {
    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E -> 360 to 120
    BarcodeFormat.PDF_417 -> 360 to 180
    else -> 320 to 320
}

private fun cloneHints(format: BarcodeFormat): Map<EncodeHintType, Any> {
    val hints = mutableMapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to if (format == BarcodeFormat.QR_CODE) 4 else 2
    )
    if (format == BarcodeFormat.QR_CODE) hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
    return hints
}

private fun cloneRenderCode(payload: String, format: BarcodeFormat): CloneRenderedCode {
    val (writerWidth, writerHeight) = cloneWriterDimensions(format)
    val matrix = MultiFormatWriter().encode(payload, format, writerWidth, writerHeight, cloneHints(format))
    val raw = cloneMatrixToBitmap(matrix)
    val (targetWidth, targetHeight) = cloneDimensions(format)
    val bitmap = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, false)
    if (raw !== bitmap) raw.recycle()
    // Clone intentionally leaves the symbol pristine. Branding belongs to
    // barcode.generate; barcode.clone prioritises scanner/print fidelity.
    return CloneRenderedCode(bitmap, format)
}

private fun cloneMatrixToBitmap(matrix: BitMatrix): Bitmap {
    val pixels = IntArray(matrix.width * matrix.height)
    val black = android.graphics.Color.BLACK
    val white = android.graphics.Color.WHITE
    for (y in 0 until matrix.height) {
        val offset = y * matrix.width
        for (x in 0 until matrix.width) pixels[offset + x] = if (matrix[x, y]) black else white
    }
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}

private fun cloneCopyPayload(context: Context, payload: String) {
    if (payload.isEmpty()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("Barcode payload", payload))
    Toast.makeText(context, "Payload copied", Toast.LENGTH_SHORT).show()
}

private fun cloneMaterializeCommittedImage(
    context: Context,
    bitmap: Bitmap,
    format: BarcodeFormat
): Uri {
    val folder = File(context.cacheDir, "barcode_clone_result").apply { mkdirs() }
    val file = File(folder, "methodmesh_barcode_clone_${format.name.lowercase()}_${System.currentTimeMillis()}.png")
    file.outputStream().use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Could not encode barcode PNG." }
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun cloneMetadataAttachment(context: Context, metadataJson: String): ResultShare.Attachment? {
    if (metadataJson.isBlank()) return null
    val folder = File(context.cacheDir, "barcode_clone_share").apply { mkdirs() }
    val file = File(folder, "methodmesh_barcode_clone_${System.currentTimeMillis()}_metadata.json")
    file.writeText(metadataJson, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return ResultShare.Attachment(file.name, uri)
}

private fun cloneShareImage(
    context: Context,
    imageUri: String,
    format: BarcodeFormat,
    payloadText: String,
    metadataJson: String
) {
    val image = Uri.parse(imageUri)
    val metadata = cloneMetadataAttachment(context, metadataJson)?.uri
    val streams = arrayListOf(image).apply { metadata?.let { add(it) } }
    val intent = if (streams.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, image)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "application/json"))
        }
    }.apply {
        if (payloadText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, payloadText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "MethodMesh cloned barcode", streams.first()).apply {
            streams.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
    }

    context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { info ->
        streams.forEach { uri ->
            context.grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(
        Intent.createChooser(intent, "Share cloned ${clonePrettyFormat(format)} code").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = intent.clipData
        }
    )
}

private fun cloneCopyImage(context: Context, imageUri: String, payloadText: String) {
    val uri = Uri.parse(imageUri)
    val clip = ClipData.newUri(context.contentResolver, "MethodMesh barcode clone", uri)
    if (payloadText.isNotBlank()) clip.addItem(ClipData.Item(payloadText))
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    Toast.makeText(context, if (payloadText.isBlank()) "Barcode image copied" else "Barcode image + payload copied", Toast.LENGTH_SHORT).show()
}

private fun cloneSaveImage(
    context: Context,
    imageUri: String,
    metadataJson: String
): OutputExportRepository.DownloadsExport = OutputExportRepository.saveToDownloads(
    context = context,
    label = "barcode_clone",
    text = "",
    mediaUris = listOf(imageUri),
    jsonText = metadataJson
)

private fun cloneTechnicalRows(fields: Map<String, Any?>): List<Pair<String, String>> = listOf(
    "Clone image" to fields["barcode_clone_image_uri"]?.toString().orEmpty(),
    "Payload" to fields["barcode_payload"]?.toString().orEmpty(),
    "Payload kind" to fields["barcode_payload_kind"]?.toString().orEmpty(),
    "URL" to fields["barcode_payload_url"]?.toString().orEmpty(),
    "Source format" to fields["barcode_source_format"]?.toString().orEmpty(),
    "Clone format" to fields["barcode_clone_format"]?.toString().orEmpty(),
    "SHA-256" to fields["barcode_payload_sha256"]?.toString().orEmpty(),
    "Evidence format" to fields[BarcodeEvidenceFields.FORMAT_FIELD]?.toString().orEmpty(),
    "Evidence hash" to fields[BarcodeEvidenceFields.HASH_FIELD]?.toString().orEmpty(),
    "Scan time" to fields["barcode_scan_time_iso"]?.toString().orEmpty(),
    "Clone time" to fields["barcode_clone_time_iso"]?.toString().orEmpty(),
    "Source" to fields["barcode_source"]?.toString().orEmpty()
).filter { it.second.isNotBlank() }

@Composable
private fun CloneTechnicalValue(label: String, value: String, onCopy: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = value.isNotBlank(), onClick = onCopy),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("TAP TO COPY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Context.findCloneActivity(): android.app.Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return current as? android.app.Activity
}

val BarcodeCloneCapabilityScreen: CapabilityScreenSpec = CodeCloneCapabilityScreen()
