package com.example.methodmesh.modules.qrcode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.Instant
import java.io.File
import kotlinx.coroutines.delay

private val GENERATOR_ORDER = listOf(
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

private data class RenderedCode(val bitmap: Bitmap, val format: BarcodeFormat)

private class CodeGenerateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100BarcodeGenerateMethod.ID
    override val title: String = "Create code"
    override val description: String = "Turn exact text into a scannable code."
    override val hostPresentation: CapabilityHostPresentation = CapabilityHostPresentation.Immersive

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
        val payloadVisible = context.settingShouldBeShown("barcode_payload")
        val formatVisible = context.settingShouldBeShown("barcode_format")
        val cycleVisible = context.settingShouldBeShown("barcode_auto_cycle")

        val suppliedPayload = context.action.settings["barcode_payload"]
            ?: context.action.settings["input_barcode_payload"]
            ?: context.request.settings["barcode_payload"]
            ?: context.request.settings["input_barcode_payload"]
            ?: ""
        val suppliedFormat = parseFormat(
            context.action.settings["barcode_format"]
                ?: context.action.settings["input_barcode_format"]
                ?: "QR_CODE"
        )

        var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedPayload) }
        var formatName by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedFormat.name) }
        var cycling by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(
                (context.action.settings["barcode_auto_cycle"]
                    ?: context.action.settings["input_barcode_auto_cycle"]
                    ?: "false").toBoolean()
            )
        }
        var presentationMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var includeJsonSidecar by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.isNativePresetRun && presetPayloadMode != OutputFormatter.PayloadMode.CORE)
        }
        var showTechnicalDetails by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        // Commit freezes the payload + presentation choice. Editing explicitly
        // clears commitment; automatic-return origins leave immediately.
        var committedPayload by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedFormatName by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedTime by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedExecutionId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedObservationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedTransformationId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedRelationshipIds by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedSystemTimeMs by rememberSaveable(context.action.canonicalId) { mutableStateOf<Long?>(null) }

        val compatible = remember(payload) { GENERATOR_ORDER.filter { canEncode(payload, it) } }
        val selected = parseFormat(formatName)
        val effectiveFormat = when {
            committedFormatName != null -> parseFormat(committedFormatName!!)
            !formatVisible -> selected
            selected in compatible -> selected
            compatible.isNotEmpty() -> compatible.first()
            else -> selected
        }

        LaunchedEffect(effectiveFormat.name, committedFormatName, formatVisible) {
            if (formatVisible && committedFormatName == null && effectiveFormat.name != formatName) {
                formatName = effectiveFormat.name
            }
        }

        val displayPayload = committedPayload ?: payload
        val rendered = remember(displayPayload, effectiveFormat.name) {
            if (displayPayload.isEmpty()) null
            else runCatching { renderCode(displayPayload, effectiveFormat) }.getOrNull()
        }

        val committedResult = remember(
            committedPayload, committedFormatName, committedTime, committedExecutionId,
            committedObservationId, committedTransformationId, committedRelationshipIds,
            committedSystemTimeMs, context.request.invocationContext
        ) {
            val p = committedPayload
            val f = committedFormatName
            val t = committedTime
            if (p == null || f == null || t == null) null
            else buildGenerateResult(
                context = context, payload = p, formatName = f, generatedTime = t,
                executionId = committedExecutionId, observationId = committedObservationId,
                transformationId = committedTransformationId, relationshipIds = committedRelationshipIds,
                systemTimeEpochMs = committedSystemTimeMs
            )
        }

        val committedFields = remember(committedResult?.request?.id?.value) {
            committedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        }
        val metadataJson = remember(
            committedResult?.request?.id?.value, includeJsonSidecar, presetPayloadMode, context.isNativePresetRun
        ) {
            val result = committedResult ?: return@remember ""
            val mode = when {
                context.isNativePresetRun && presetPayloadMode != OutputFormatter.PayloadMode.CORE -> presetPayloadMode
                includeJsonSidecar -> OutputFormatter.PayloadMode.FULL
                else -> OutputFormatter.PayloadMode.CORE
            }
            if (mode == OutputFormatter.PayloadMode.CORE) "" else OutputFormatter.format(
                result = result,
                returnMode = ReturnMode.Json,
                includeProvenance = true,
                payloadMode = mode
            )
        }

        LaunchedEffect(payload, formatName, cycling) {
            if (committedPayload == null) {
                context.onSettingsChanged(
                    mapOf(
                        "barcode_payload" to payload,
                        "barcode_format" to formatName,
                        "barcode_auto_cycle" to cycling.toString()
                    )
                )
            }
        }

        val cyclingState by rememberUpdatedState(cycling)
        LaunchedEffect(cycling, payload, compatible, committedPayload, formatVisible) {
            if (!formatVisible || !cycling || committedPayload != null || compatible.size < 2) return@LaunchedEffect
            while (cyclingState) {
                delay(1700)
                val current = compatible.indexOf(parseFormat(formatName)).coerceAtLeast(0)
                formatName = compatible[(current + 1) % compatible.size].name
            }
        }

        fun editWorkingState() {
            committedPayload = null
            committedFormatName = null
            committedTime = null
            committedExecutionId = null
            committedObservationId = null
            committedTransformationId = null
            committedRelationshipIds = null
            committedSystemTimeMs = null
            exportStatus = ""
            showTechnicalDetails = false
            includeJsonSidecar = context.isNativePresetRun && presetPayloadMode != OutputFormatter.PayloadMode.CORE
        }

        fun moveFormat(delta: Int) {
            if (!formatVisible || committedPayload != null || compatible.isEmpty()) return
            if (cycleVisible) cycling = false
            val current = compatible.indexOf(parseFormat(formatName)).coerceAtLeast(0)
            formatName = compatible[(current + delta + compatible.size) % compatible.size].name
        }

        fun commit() {
            if (payload.isEmpty() || rendered == null) return
            cycling = false
            val t = Instant.now().toString()
            val result = buildGenerateResult(context, payload, effectiveFormat.name, t)
            committedPayload = payload
            committedFormatName = effectiveFormat.name
            committedTime = t
            committedExecutionId = result.request.id.value
            committedObservationId = result.observations.firstOrNull()?.id?.value
            committedTransformationId = result.transformations.firstOrNull()?.id?.value
            committedRelationshipIds = result.relationships.joinToString("|") { it.id.value }.ifBlank { null }
            committedSystemTimeMs = result.request.temporalContext.systemTimeEpochMs
            if (automaticReturn) onConfirmed(result)
        }

        fun shareCommitted(): Boolean {
            val bitmap = rendered?.bitmap ?: return false
            return runCatching {
                shareGenerated(
                    context = appContext,
                    bitmap = bitmap,
                    format = effectiveFormat,
                    metadataJson = metadataJson
                )
                exportStatus = "Sharing committed barcode…"
            }.onFailure {
                exportStatus = "Share failed: ${it.message ?: "no sharing app available"}"
            }.isSuccess
        }

        fun copyCommittedImage(): Boolean {
            val bitmap = rendered?.bitmap ?: return false
            return runCatching {
                copyGeneratedImage(appContext, bitmap, effectiveFormat)
                exportStatus = "Barcode image copied."
            }.onFailure {
                exportStatus = "Copy failed: ${it.message ?: "clipboard error"}"
            }.isSuccess
        }

        fun saveCommitted(): Boolean {
            val bitmap = rendered?.bitmap ?: return false
            return runCatching {
                val saved = saveGeneratedImage(appContext, bitmap, effectiveFormat, metadataJson)
                exportStatus = "Saved ${saved.summary}"
            }.onFailure {
                exportStatus = "Save failed: ${it.message ?: "storage error"}"
            }.isSuccess
        }

        fun finishCommitted() {
            val result = committedResult ?: return
            if (!context.isNativePresetRun || !context.isLastStep) {
                onConfirmed(result)
                return
            }
            when (presetResultAction) {
                PresetResultAction.SHARE -> {
                    if (!shareCommitted()) return
                    onConfirmed(result)
                }
                PresetResultAction.SAVE -> {
                    if (!saveCommitted()) return
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
            val activity = appContext.findActivity()
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
            Surface(
                modifier = Modifier.fillMaxSize().clickable { presentationMode = false },
                color = Color.White
            ) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    rendered?.let {
                        Image(
                            bitmap = it.bitmap.asImageBitmap(),
                            contentDescription = "Generated ${prettyFormat(it.format)}",
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
            return
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GeneratorHeader(committed = committedPayload != null, format = effectiveFormat)

            if (committedPayload == null) {
                BarcodePresetAuthoring(
                    context = context,
                    methodId = As100BarcodeGenerateMethod.ID,
                    methodName = "Code generator",
                    currentSettings = mapOf(
                        "barcode_payload" to payload,
                        "barcode_format" to effectiveFormat.name,
                        "barcode_auto_cycle" to cycling.toString()
                    ),
                    defaultRuntimeFields = setOf("barcode_payload")
                )
            }

            CodeHero(
                rendered = rendered,
                payloadEmpty = displayPayload.isEmpty(),
                committed = committedPayload != null,
                onSwipe = { direction -> moveFormat(direction) },
                onPresent = { if (rendered != null) presentationMode = true }
            )

            if (committedPayload == null) {
                if (formatVisible) {
                    GeneratorTransport(
                        cycling = cycling,
                        enabled = compatible.size > 1,
                        cycleVisible = cycleVisible,
                        onPrevious = { moveFormat(-1) },
                        onCycle = { if (cycleVisible) cycling = !cycling },
                        onNext = { moveFormat(+1) }
                    )
                }

                if (payloadVisible) {
                    TextField(
                        value = payload,
                        onValueChange = { payload = it; if (cycleVisible) cycling = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Payload") },
                        placeholder = { Text("Paste text, URL, loyalty number…") },
                        supportingText = { Text(payloadSummary(payload)) },
                        minLines = 2,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val clip = appContext.getSystemService(ClipboardManager::class.java).primaryClip
                                clip?.getItemAt(0)?.coerceToText(appContext)?.toString()?.let { payload = it; if (cycleVisible) cycling = false }
                            }
                        ) { Text("Paste") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { payload = ""; if (cycleVisible) cycling = false }
                        ) { Text("Clear") }
                    }
                } else {
                    PayloadCard(payload = payload, onCopy = { copyPayload(appContext, payload) })
                }

                if (formatVisible) {
                    FormatRail(
                        compatible = compatible,
                        selected = effectiveFormat,
                        onSelect = { if (cycleVisible) cycling = false; formatName = it.name }
                    )
                } else if (payload.isNotEmpty() && rendered == null) {
                    Text(
                        "The fixed ${prettyFormat(effectiveFormat)} format cannot represent this exact payload. The payload will not be altered; choose a different preset format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                BarcodePayloadSemantics.safeHttpUrl(payload)?.let { url ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { openGeneratedUrl(appContext, url) }) {
                        Text("Open link")
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ::commit,
                    enabled = payload.isNotEmpty() && rendered != null
                ) { Text(if (automaticReturn) "Commit and return" else "Commit") }
            } else {
                PayloadCard(payload = committedPayload.orEmpty(), onCopy = { copyPayload(appContext, committedPayload.orEmpty()) })
                Text(
                    "Committed result · ${prettyFormat(effectiveFormat)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { shareCommitted() }
                    ) { Text("Share image") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { copyCommittedImage() }
                    ) { Text("Copy image") }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { saveCommitted() }
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
                            generatedTechnicalRows(committedFields).forEach { (label, value) ->
                                GeneratedTechnicalValue(label, value) { copyPayload(appContext, value) }
                            }
                        }
                    }
                }
                if (exportStatus.isNotBlank()) {
                    Text(exportStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ::finishCommitted,
                    enabled = committedResult != null
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
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = ::editWorkingState) { Text("Edit") }
            }

            if (committedPayload == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (context.stepNumber > 1) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = onBack) { Text("Back") }
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun GeneratorHeader(committed: Boolean, format: BarcodeFormat) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Create code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Paste. Present. Scan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (committed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                if (committed) "COMMITTED" else prettyFormat(format).uppercase(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CodeHero(
    rendered: RenderedCode?,
    payloadEmpty: Boolean,
    committed: Boolean,
    onSwipe: (Int) -> Unit,
    onPresent: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(committed) {
                if (!committed) {
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
        Box(
            modifier = Modifier.fillMaxWidth().height(340.dp).padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rendered != null) {
                Image(
                    bitmap = rendered.bitmap.asImageBitmap(),
                    contentDescription = "Generated ${prettyFormat(rendered.format)}",
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (payloadEmpty) "Ready for a payload" else "No compatible rendering",
                        color = Color.Black,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (payloadEmpty) "Paste or type below" else "Choose another supported format",
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorTransport(
    cycling: Boolean,
    enabled: Boolean,
    cycleVisible: Boolean,
    onPrevious: () -> Unit,
    onCycle: () -> Unit,
    onNext: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onPrevious, enabled = enabled) { Text("‹") }
        if (cycleVisible) {
            Button(modifier = Modifier.weight(2f), onClick = onCycle, enabled = enabled) {
                Text(if (cycling) "■  Stop" else "▶  Cycle")
            }
        }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onNext, enabled = enabled) { Text("›") }
    }
}

@Composable
private fun FormatRail(
    compatible: List<BarcodeFormat>,
    selected: BarcodeFormat,
    onSelect: (BarcodeFormat) -> Unit
) {
    if (compatible.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Format", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        prettyFormat(format),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (format == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PayloadCard(payload: String, onCopy: () -> Unit) {
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (payload.isNotEmpty()) {
                Text("COPY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun buildGenerateResult(
    context: CapabilityScreenContext,
    payload: String,
    formatName: String,
    generatedTime: String,
    executionId: String? = null,
    observationId: String? = null,
    transformationId: String? = null,
    relationshipIds: String? = null,
    systemTimeEpochMs: Long? = null
): ExecutionResult {
    val method: As100Method = As100BarcodeGenerateMethod
    val methodContext = context.request.invocationContext.asMap(method.id) +
        context.action.settings + mapOf(
            "barcode_payload" to payload,
            "barcode_format" to formatName,
            "barcode_generated_time_iso" to generatedTime,
            "barcode_source" to "methodmesh_generator"
        )
    val canRestoreIdentity = !executionId.isNullOrBlank() && !observationId.isNullOrBlank() &&
        !transformationId.isNullOrBlank() && systemTimeEpochMs != null
    val result = if (canRestoreIdentity) {
        val request = As100ExecutionEngine.request(
            action = method.id, method = method.ref, id = ArchitectureId(executionId!!),
            context = methodContext, temporalContext = TemporalContext(systemTimeEpochMs = systemTimeEpochMs!!)
        )
        As100BarcodeGenerateMethod.executeWithIdentity(
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

private fun parseFormat(raw: String): BarcodeFormat =
    runCatching { BarcodeFormat.valueOf(raw.trim().uppercase()) }.getOrDefault(BarcodeFormat.QR_CODE)

private fun prettyFormat(format: BarcodeFormat): String = when (format) {
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

private fun payloadSummary(payload: String): String {
    if (payload.isEmpty()) return "Exact text is encoded without rewriting."
    val kind = when {
        BarcodePayloadSemantics.safeHttpUrl(payload) != null -> "URL"
        payload.startsWith("mailto:", ignoreCase = true) -> "Email"
        payload.startsWith("tel:", ignoreCase = true) -> "Phone"
        payload.startsWith("WIFI:", ignoreCase = true) -> "Wi-Fi"
        payload.all(Char::isDigit) -> "Number"
        else -> "Text"
    }
    return "$kind · ${payload.length} characters · exact payload preserved"
}

private fun canEncode(payload: String, format: BarcodeFormat): Boolean {
    if (payload.isEmpty()) return false
    val (width, height) = writerDimensions(format)
    return runCatching { MultiFormatWriter().encode(payload, format, width, height, hints(format)) }.isSuccess
}

private fun dimensions(format: BarcodeFormat): Pair<Int, Int> = when (format) {
    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E -> 960 to 320
    BarcodeFormat.PDF_417 -> 960 to 480
    else -> 720 to 720
}

private fun writerDimensions(format: BarcodeFormat): Pair<Int, Int> = when (format) {
    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E -> 360 to 120
    BarcodeFormat.PDF_417 -> 360 to 180
    else -> 320 to 320
}

private fun hints(format: BarcodeFormat): Map<EncodeHintType, Any> {
    val result = mutableMapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to if (format == BarcodeFormat.QR_CODE) 4 else 2
    )
    if (format == BarcodeFormat.QR_CODE) result[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
    return result
}

private fun renderCode(payload: String, format: BarcodeFormat): RenderedCode {
    val (writerWidth, writerHeight) = writerDimensions(format)
    val matrix = MultiFormatWriter().encode(payload, format, writerWidth, writerHeight, hints(format))
    val raw = matrixToBitmap(matrix)
    val (targetWidth, targetHeight) = dimensions(format)
    val bitmap = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, false)
    if (raw !== bitmap) raw.recycle()
    if (format == BarcodeFormat.QR_CODE) overlayMethodMeshMark(bitmap)
    return RenderedCode(bitmap, format)
}

private fun matrixToBitmap(matrix: BitMatrix): Bitmap {
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

private fun overlayMethodMeshMark(code: Bitmap) {
    // Canonical geometry/colours from res/drawable/ic_launcher_foreground.xml.
    // Drawn directly so the generator has no duplicate logo bitmap asset.
    val canvas = Canvas(code)
    val side = (minOf(code.width, code.height) * 0.14f)
    val left = (code.width - side) / 2f
    val top = (code.height - side) / 2f
    val pad = side * 0.12f

    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawRoundRect(
        left - pad, top - pad, left + side + pad, top + side + pad,
        pad * 0.9f, pad * 0.9f, white
    )

    // App icon vector uses a 108 × 108 viewport. Crop to the actual mark bounds
    // (x 27..81, y 38..78) so the mark, not its adaptive-icon whitespace,
    // occupies the conservative QR centre patch.
    val sx = side / 54f
    val sy = side / 40f
    fun roundBar(x0: Float, y0: Float, x1: Float, y1: Float, colour: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        val l = left + (x0 - 27f) * sx
        val t = top + (y0 - 38f) * sy
        val r = left + (x1 - 27f) * sx
        val b = top + (y1 - 38f) * sy
        val radius = minOf((b - t) / 2f, 5f * minOf(sx, sy))
        canvas.drawRoundRect(l, t, r, b, radius, radius, paint)
    }

    roundBar(27f, 38f, 81f, 48f, android.graphics.Color.rgb(0x30, 0x2A, 0x28))
    roundBar(36f, 53f, 81f, 63f, android.graphics.Color.rgb(0x47, 0x7C, 0x78))
    roundBar(49f, 68f, 81f, 78f, android.graphics.Color.rgb(0xB8, 0xAF, 0xA8))
}

private fun copyPayload(context: Context, payload: String) {
    if (payload.isEmpty()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("Barcode payload", payload))
    Toast.makeText(context, "Payload copied", Toast.LENGTH_SHORT).show()
}

private fun generatedTemporaryImage(
    context: Context,
    bitmap: Bitmap,
    format: BarcodeFormat,
    operation: String
): Pair<File, Uri> {
    val folder = File(context.cacheDir, "barcode_share").apply { mkdirs() }
    val file = File(folder, "methodmesh_barcode_generate_${operation}_${format.name.lowercase()}_${System.currentTimeMillis()}.png")
    file.outputStream().use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Could not encode barcode PNG." }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return file to uri
}

private fun generatedMetadataAttachment(context: Context, metadataJson: String): ResultShare.Attachment? {
    if (metadataJson.isBlank()) return null
    val folder = File(context.cacheDir, "barcode_share").apply { mkdirs() }
    val file = File(folder, "methodmesh_barcode_generate_${System.currentTimeMillis()}_metadata.json")
    file.writeText(metadataJson, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return ResultShare.Attachment(file.name, uri)
}

private fun shareGenerated(context: Context, bitmap: Bitmap, format: BarcodeFormat, metadataJson: String) {
    val (file, uri) = generatedTemporaryImage(context, bitmap, format, "share")
    val attachments = mutableListOf(ResultShare.Attachment(file.name, uri))
    generatedMetadataAttachment(context, metadataJson)?.let(attachments::add)
    ResultShare.share(
        context = context,
        chooserTitle = "Share ${prettyFormat(format)} code",
        text = "",
        attachments = attachments,
        jsonText = "",
        fileLabel = "barcode_generate"
    )
}

private fun copyGeneratedImage(context: Context, bitmap: Bitmap, format: BarcodeFormat) {
    val (_, uri) = generatedTemporaryImage(context, bitmap, format, "clipboard")
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newUri(context.contentResolver, "MethodMesh generated barcode", uri))
    Toast.makeText(context, "Barcode image copied", Toast.LENGTH_SHORT).show()
}

private fun saveGeneratedImage(
    context: Context,
    bitmap: Bitmap,
    format: BarcodeFormat,
    metadataJson: String
): OutputExportRepository.DownloadsExport {
    val (_, uri) = generatedTemporaryImage(context, bitmap, format, "save")
    return OutputExportRepository.saveToDownloads(
        context = context,
        label = "barcode_generate",
        text = "",
        mediaUris = listOf(uri.toString()),
        jsonText = metadataJson
    )
}

private fun generatedTechnicalRows(fields: Map<String, Any?>): List<Pair<String, String>> = listOf(
    "Payload" to fields["barcode_payload"]?.toString().orEmpty(),
    "Payload kind" to fields["barcode_payload_kind"]?.toString().orEmpty(),
    "URL" to fields["barcode_payload_url"]?.toString().orEmpty(),
    "Format" to fields["barcode_format"]?.toString().orEmpty(),
    "SHA-256" to fields["barcode_payload_sha256"]?.toString().orEmpty(),
    "Generated time" to fields["barcode_generated_time_iso"]?.toString().orEmpty(),
    "Source" to fields["barcode_source"]?.toString().orEmpty()
).filter { it.second.isNotBlank() }

@Composable
private fun GeneratedTechnicalValue(label: String, value: String, onCopy: () -> Unit) {
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

private fun Context.findActivity(): android.app.Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return current as? android.app.Activity
}

private fun openGeneratedUrl(context: Context, url: String) {
    val safe = BarcodePayloadSemantics.safeHttpUrl(url) ?: return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe))) }
        .onFailure { Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show() }
}

val BarcodeGenerateCapabilityScreen: CapabilityScreenSpec = CodeGenerateCapabilityScreen()
