package com.example.methodmesh.modules.signals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private const val SIGNAL_QR_EXAMPLE_TEXT = "MethodMesh QR Blast demonstration. This deliberately longer sample shows that the optical link can move more than a token message. It contains enough text to require several error-corrected QR frames, so you can start the receiving phone part-way through a cycle, lose some frames, and still watch Reed-Solomon recovery complete. The reconstructed text is then checked against the SHA-256 digest broadcast by the sender before MethodMesh marks the result VERIFIED."

object SignalQrTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalQrTransmitMethod.id
    override val title = "QR burst transmitter"
    override val description = "Cycle checksum-protected text or a file up to 1 MiB through segmented error-corrected MMS/1 QR frames."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val activity = remember(androidContext) { androidContext.signalActivity() }
        val settings = context.action.settings
        var contentMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("content_mode", "text").let { if (it == "file") "file" else "text" }) }
        var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("payload", SIGNAL_QR_EXAMPLE_TEXT)) }
        var fileUri by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("file_uri", "")) }
        var fileName by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("file_name", "")) }
        var fileMime by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("file_mime", "")) }
        var robustness by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("robustness", "robust")) }
        var shardBytes by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("shard_bytes", "96").toIntOrNull()?.coerceIn(24, 384) ?: 96) }
        var dwellMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("frame_duration_ms", "650").toIntOrNull()?.coerceIn(150, 3000) ?: 650) }
        var loop by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("loop", "true").toBooleanStrictOrNull() ?: true) }
        var messageId by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPacketCodec.newMessageId()) }
        var active by remember { mutableStateOf(false) }
        var frameIndex by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var cycles by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Prepare text or choose a file, then start the burst.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        SignalActiveSessionOrientationGuard(active)

        fun invalidateTransmission(reason: String = "Payload changed; complete a new burst before Commit.") {
            active = false
            cycles = 0
            frameIndex = 0
            messageId = SignalPacketCodec.newMessageId()
            status = reason
            error = ""
        }

        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentMode = "file"
                fileUri = uri.toString()
                fileName = androidContext.signalDisplayName(uri).ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') }
                fileMime = androidContext.contentResolver.getType(uri).orEmpty()
                invalidateTransmission("File selected. SHA-256 will be calculated before transmission.")
            }
        }

        val contentAttempt = remember(contentMode, payload, fileUri, fileName, fileMime) {
            runCatching {
                if (contentMode == "file") {
                    require(fileUri.isNotBlank()) { "Choose a file to transmit." }
                    val bytes = androidContext.contentResolver.openInputStream(Uri.parse(fileUri))?.use { it.readBytes() }
                        ?: error("The selected file could not be read.")
                    SignalContentEnvelope.encodeFile(bytes, fileName.ifBlank { "transferred_file.bin" }, fileMime)
                } else {
                    require(payload.isNotBlank()) { "Enter text to transmit." }
                    SignalContentEnvelope.encodeText(payload)
                }
            }
        }
        val contentBytes = contentAttempt.getOrNull()
        val contentError = contentAttempt.exceptionOrNull()?.message.orEmpty()
        val envelope = remember(contentBytes) { contentBytes?.let(SignalContentEnvelope::decode) }

        val encodedAttempt = remember(contentBytes, robustness, shardBytes, messageId) {
            runCatching {
                SignalQrTransferCodec.encode(
                    contentEnvelope = contentBytes ?: error(contentError.ifBlank { "No content is ready." }),
                    robustness = SignalPacketCodec.Robustness.from(robustness),
                    shardBytes = shardBytes,
                    transferId = messageId.take(12)
                )
            }
        }
        val encoded = encodedAttempt.getOrNull()
        val encodeError = encodedAttempt.exceptionOrNull()?.message.orEmpty()
        val frames = encoded?.frames.orEmpty()
        LaunchedEffect(frames) { if (frameIndex !in frames.indices && frames.isNotEmpty()) frameIndex = 0 }
        val currentFrame = frames.getOrNull(frameIndex).orEmpty()
        val qrBitmap = remember(currentFrame) { currentFrame.takeIf(String::isNotBlank)?.let { SignalQrRenderer.bitmap(it) } }

        LaunchedEffect(contentMode, payload, fileUri, fileName, fileMime, robustness, shardBytes, dwellMs, loop) {
            context.onSettingsChanged(
                mapOf(
                    "content_mode" to contentMode,
                    "payload" to payload,
                    "file_uri" to fileUri,
                    "file_name" to fileName,
                    "file_mime" to fileMime,
                    "robustness" to robustness,
                    "shard_bytes" to shardBytes.toString(),
                    "frame_duration_ms" to dwellMs.toString(),
                    "loop" to loop.toString()
                )
            )
        }

        LaunchedEffect(active, frames, dwellMs, loop) {
            if (!active || frames.isEmpty()) return@LaunchedEffect
            while (active) {
                delay(dwellMs.toLong())
                if (!active) break
                if (frameIndex >= frames.lastIndex) {
                    cycles++
                    frameIndex = 0
                    status = "QR cycle $cycles complete${if (loop) " — looping" else ""}."
                    if (!loop) { active = false; break }
                } else frameIndex++
            }
        }

        fun start() {
            error = contentError.ifBlank { encodeError }
            if (encoded == null || frames.isEmpty()) {
                if (error.isBlank()) error = "Content could not be encoded."
                return
            }
            active = true
            frameIndex = 0
            cycles = 0
            status = "QR blast running • SHA-256 ${envelope?.expectedSha256?.take(12) ?: "—"}…"
            error = ""
        }

        fun stop() {
            active = false
            status = if (cycles > 0) "Stopped after $cycles complete cycle(s)." else "Stopped before a complete cycle."
        }

        fun commit() {
            val packet = encoded
            val metadata = envelope
            if (packet == null || metadata == null || cycles <= 0) {
                error = "Complete at least one QR cycle before Commit."
                return
            }
            active = false
            val values = mapOf(
                SignalQrTransmitFields.RESULT to if (metadata.type == "file") metadata.fileName.orEmpty() else metadata.text.orEmpty(),
                SignalQrTransmitFields.PAYLOAD to if (metadata.type == "text") metadata.text.orEmpty() else "",
                SignalQrTransmitFields.CONTENT_TYPE to metadata.type,
                SignalQrTransmitFields.FILE_NAME to metadata.fileName.orEmpty(),
                SignalQrTransmitFields.FILE_MIME to metadata.mimeType.orEmpty(),
                SignalQrTransmitFields.FILE_BYTES to metadata.payload.size.toString(),
                SignalQrTransmitFields.CHECKSUM_SHA256 to metadata.expectedSha256,
                SignalQrTransmitFields.MESSAGE_ID to packet.transferId,
                SignalQrTransmitFields.DATA_SHARDS to packet.dataShardCount.toString(),
                SignalQrTransmitFields.PARITY_FRAMES to packet.parityFrameCount.toString(),
                SignalQrTransmitFields.FRAME_COUNT to packet.frames.size.toString(),
                SignalQrTransmitFields.MESSAGE_CRC32 to packet.messageCrc32Hex,
                SignalQrTransmitFields.ROBUSTNESS to robustness,
                SignalQrTransmitFields.CYCLES to cycles.toString(),
                SignalQrTransmitFields.STATUS to "sent",
                SignalQrTransmitFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed. Original SHA-256 is frozen with the transmission record."
            val result = signalResult(As100SignalQrTransmitMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        DisposableEffect(activity, active) {
            val window = activity?.window
            val previous = window?.attributes?.screenBrightness
            if (window != null && active) window.attributes = window.attributes.apply { screenBrightness = 1f }
            onDispose { if (window != null && previous != null) window.attributes = window.attributes.apply { screenBrightness = previous } }
        }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalQrTransmitMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (active) stop() else start() },
                enabled = frames.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (active) "Stop blast" else "Start blast") }

            SignalInstrumentPanel(
                kicker = "MMS/1 OPTICAL TRANSMITTER",
                title = if (contentMode == "file") "QR file blast" else "QR burst transmitter",
                accent = SignalCyan,
                badge = if (active) "On air" else if (cycles > 0) "Cycle complete" else "Ready"
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.28f).background(Color.White, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    if (qrBitmap != null) Image(qrBitmap.asImageBitmap(), "Current MMS/1 QR frame", Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
                    else Text("BUILDING OPTICAL FRAME", color = SignalBlack, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("FRAME", if (frames.isEmpty()) "—" else "${frameIndex + 1}/${frames.size}", SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("CYCLE", (cycles + if (active) 1 else 0).toString(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("BYTES", envelope?.payload?.size?.toString() ?: "—", SignalCyan, Modifier.weight(1f))
                }
                envelope?.let { meta ->
                    SignalStatusPill("SHA-256 EMBEDDED", SignalGreen)
                    Text("ORIGINAL  ${meta.expectedSha256}", color = SignalMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "original SHA-256", meta.expectedSha256) })
                }
                encoded?.let { packet ->
                    Text("${packet.segmentCount} MMS segment${if (packet.segmentCount == 1) "" else "s"} • ${packet.dataShardCount} source + ${packet.parityFrameCount} repair frames • ${robustness.uppercase()}", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
                    SignalProgressTrack((frameIndex + 1f) / packet.frames.size.coerceAtLeast(1).toFloat(), SignalCyan)
                }
            }

            Button(onClick = ::commit, enabled = cycles > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (committedResult == null) "Commit" else "Recommit")
            }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed QR blast",
                    primaryLabel = "content",
                    primaryValue = committedFields[SignalQrTransmitFields.RESULT].orEmpty(),
                    fields = listOf(
                        "Type" to committedFields[SignalQrTransmitFields.CONTENT_TYPE].orEmpty(),
                        "Bytes" to committedFields[SignalQrTransmitFields.FILE_BYTES].orEmpty(),
                        "SHA-256" to committedFields[SignalQrTransmitFields.CHECKSUM_SHA256].orEmpty(),
                        "Message ID" to committedFields[SignalQrTransmitFields.MESSAGE_ID].orEmpty(),
                        "Frames" to committedFields[SignalQrTransmitFields.FRAME_COUNT].orEmpty(),
                        "Cycles" to committedFields[SignalQrTransmitFields.CYCLES].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { exportStatus = shareSignalText(androidContext, "Share QR blast record", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }) ?: "" },
                    onSave = { exportStatus = saveSignalText(androidContext, "qr_blast_transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, committedFullJson) },
                    onDone = { finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "qr_blast_transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, committedFullJson) } }
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Payload & reliability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (context.settingShouldBeShown("content_mode")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("text" to "Text", "file" to "File").forEach { (id, label) ->
                                FilterChip(selected = contentMode == id, enabled = !active, onClick = { if (contentMode != id) { contentMode = id; invalidateTransmission() } }, label = { Text(label) })
                            }
                        }
                    }
                    if (contentMode == "text") {
                        if (context.settingShouldBeShown("payload")) SignalSelectAllTextField(
                            value = payload,
                            onValueChange = { payload = it; invalidateTransmission() },
                            enabled = !active,
                            label = "Message — tap to replace all"
                        )
                    } else {
                        Text(fileName.ifBlank { "No file selected" }, fontWeight = FontWeight.SemiBold)
                        if (fileUri.isNotBlank()) Text("${fileMime.ifBlank { "application/octet-stream" }} • ${envelope?.payload?.size ?: 0} bytes", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !active, modifier = Modifier.fillMaxWidth()) { Text(if (fileUri.isBlank()) "Choose file" else "Choose another file") }
                        Text("File limit: ${SignalContentEnvelope.MAX_FILE_BYTES / (1024 * 1024)} MiB. Larger objects are split into independently recoverable MMS/1 segments; SHA-256 verifies the final reconstructed object.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Large files can require thousands of QR frames. For speed, use Fast reliability, a large shard size and short dwell; text and small documents remain the practical sweet spot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (context.settingShouldBeShown("robustness")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("fast", "robust", "extreme").forEach { item -> FilterChip(selected = robustness == item, enabled = !active, onClick = { robustness = item; invalidateTransmission() }, label = { Text(item.replaceFirstChar { it.uppercase() }) }) }
                        }
                    }
                    if (context.settingShouldBeShown("shard_bytes")) {
                        Text("QR density")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SignalPresetCatalog.qrShardBytes.forEach { value ->
                                FilterChip(selected = shardBytes == value, enabled = !active, onClick = { if (shardBytes != value) { shardBytes = value; invalidateTransmission() } }, label = { Text("$value B") })
                            }
                        }
                        Text("Larger shards reduce frame count but make each QR denser.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (context.settingShouldBeShown("frame_duration_ms")) {
                        Text("Frame dwell")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SignalPresetCatalog.qrDwellMs.forEach { value ->
                                FilterChip(selected = dwellMs == value, enabled = !active, onClick = { if (dwellMs != value) { dwellMs = value; cycles = 0 } }, label = { Text("$value ms") })
                            }
                        }
                    }
                    if (context.settingShouldBeShown("loop")) {
                        Text("Burst mode")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = loop, enabled = !active, onClick = { loop = true; cycles = 0 }, label = { Text("Loop") })
                            FilterChip(selected = !loop, enabled = !active, onClick = { loop = false; cycles = 0 }, label = { Text("One cycle") })
                        }
                        Text("Loop lets a receiver join mid-stream.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            listOf(contentError, encodeError, error).firstOrNull { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (committedResult == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.stepNumber > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }

        if (active && qrBitmap != null) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(if (contentMode == "file") "QR FILE BLAST" else "MMS/1 OPTICAL TRANSMITTER", color = SignalCyan, fontWeight = FontWeight.Bold); Text("Frame ${frameIndex + 1}/${frames.size} • cycle ${cycles + 1}", color = SignalMuted, fontFamily = FontFamily.Monospace) }
                        SignalStatusPill("On air", SignalCyan)
                    }
                    Button(onClick = ::stop, modifier = Modifier.fillMaxWidth()) { Text("Stop transmission") }
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Image(qrBitmap.asImageBitmap(), "Full-screen MMS/1 QR frame", Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
    }
}

object SignalQrReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalQrReceiveMethod.id
    override val title = "QR burst receiver"
    override val description = "Collect QR shards, reconstruct text or a file up to 1 MiB, then verify SHA-256 against the sender's embedded digest."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val settings = context.action.settings
        var messageFilter by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("message_id_filter", "")) }
        var listening by remember { mutableStateOf(false) }
        var frameStoreJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var framesSeen by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var unrelatedFrames by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Start the camera receiver and point it at a MethodMesh QR blast.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedTransferAuditJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var cameraGranted by remember { mutableStateOf(qrHasPermission(androidContext, Manifest.permission.CAMERA)) }
        var cameraZoom by rememberSaveable(context.action.canonicalId) { mutableStateOf(1f) }
        var cameraActualZoom by remember { mutableStateOf(1f) }
        var cameraMaxZoom by remember { mutableStateOf(4f) }
        SignalActiveSessionOrientationGuard(listening)

        val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted || qrHasPermission(androidContext, Manifest.permission.CAMERA)
            if (cameraGranted) listening = true else error = "Camera permission is required for the QR receiver."
        }

        fun storedFrames(raw: String = frameStoreJson): MutableList<String> = runCatching {
            val array = JSONArray(raw); MutableList(array.length()) { index -> array.optString(index) }
        }.getOrDefault(mutableListOf())
        val transferAccumulator = remember { SignalQrTransferCodec.Accumulator() }
        var transferState by remember { mutableStateOf(transferAccumulator.restore(storedFrames())) }
        val decodedContent = remember(transferState.contentEnvelope) { transferState.contentEnvelope?.let(SignalContentEnvelope::decode) }

        fun clearReception(preserveCommitted: Boolean = false) {
            listening = false; frameStoreJson = "[]"; framesSeen = 0; unrelatedFrames = 0
            transferAccumulator.reset(); transferState = transferAccumulator.state()
            if (!preserveCommitted) { committedJson = null; committedTransferAuditJson = null; exportStatus = "" }
            status = if (preserveCommitted) "Receiver filter changed; working shards were cleared. Any committed result remains frozen." else "Receiver reset."
            error = ""
        }

        fun acceptQr(text: String) {
            if (!listening) return
            framesSeen++
            val frame = SignalPacketCodec.parse(text).frame
            if (frame == null) { unrelatedFrames++; status = "QR seen, but it is not a valid MMS/1 frame."; return }
            if (messageFilter.isNotBlank()) { val filter = messageFilter.trim(); if (frame.messageId != filter && !frame.messageId.startsWith("$filter-")) { unrelatedFrames++; return } }
            val frames = storedFrames()
            if (text !in frames) frames += text
            frameStoreJson = JSONArray().also { array -> frames.forEach { array.put(it) } }.toString()
            val state = transferAccumulator.offer(text)
            transferState = state
            val content = state.contentEnvelope?.let(SignalContentEnvelope::decode)
            status = when {
                state.complete && content?.checksumVerified == true -> "Reconstructed • segment CRC/FEC verified • transfer SHA-256 verified • object SHA-256 MATCH."
                state.contentEnvelope != null -> "Transfer reconstructed, but end-to-end content SHA-256 verification failed."
                state.expectedSegments > 0 -> "Collecting ${state.completeSegments} / ${state.expectedSegments} complete MMS segments."
                state.packetGroupsSeen > 0 -> "Receiving MMS segment frames…"
                else -> "Acquiring transfer geometry…"
            }
            if (state.complete && content?.checksumVerified == true) listening = false
        }

        fun start() {
            error = ""
            if (!cameraGranted) { cameraPermission.launch(Manifest.permission.CAMERA); return }
            listening = true
            status = if (transferState.expectedSegments > 0) "Resuming ${transferState.completeSegments} / ${transferState.expectedSegments} complete segments." else "Scanning continuously…"
        }

        fun writeReceivedFile(content: SignalContentEnvelope.Decoded): String {
            val safe = content.fileName.orEmpty().replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "received_file.bin" }
            val target = File(androidContext.cacheDir, "methodmesh-signals-${System.currentTimeMillis()}-$safe")
            target.writeBytes(content.payload)
            return FileProvider.getUriForFile(androidContext, "${androidContext.packageName}.fileprovider", target).toString()
        }

        fun commit() {
            val state = transferAccumulator.state()
            transferState = state
            val content = state.contentEnvelope?.let(SignalContentEnvelope::decode)
            if (!state.complete || content == null) { error = "The object is not yet recoverable as a complete checksum-verified QR transfer."; return }
            if (!content.checksumVerified) { error = "INTEGRITY FAILURE: reconstructed SHA-256 does not match the checksum broadcast by the sender."; return }
            listening = false
            val fileResultUri = if (content.type == "file") runCatching { writeReceivedFile(content) }.getOrElse { error = it.message ?: "Could not expose the reconstructed file."; return } else ""
            val values = mapOf(
                SignalQrReceiveFields.RESULT to content.text.orEmpty(),
                SignalQrReceiveFields.CONTENT_TYPE to content.type,
                SignalQrReceiveFields.RECEIVED_FILE_URI to fileResultUri,
                SignalQrReceiveFields.FILE_NAME to content.fileName.orEmpty(),
                SignalQrReceiveFields.FILE_MIME to content.mimeType.orEmpty(),
                SignalQrReceiveFields.FILE_BYTES to content.payload.size.toString(),
                SignalQrReceiveFields.EXPECTED_SHA256 to content.expectedSha256,
                SignalQrReceiveFields.RECONSTRUCTED_SHA256 to content.reconstructedSha256,
                SignalQrReceiveFields.CHECKSUM_VERIFIED to content.checksumVerified.toString(),
                SignalQrReceiveFields.MESSAGE_ID to state.transferId.orEmpty(),
                SignalQrReceiveFields.FRAMES_SEEN to framesSeen.toString(),
                SignalQrReceiveFields.FRAMES_ACCEPTED to state.acceptedFrames.toString(),
                SignalQrReceiveFields.FRAMES_REJECTED to (state.rejectedFrames + unrelatedFrames).toString(),
                SignalQrReceiveFields.RECOVERED_MISSING to state.recoveredMissingSources.toString(),
                SignalQrReceiveFields.CRC_VERIFIED to state.complete.toString(),
                SignalQrReceiveFields.STATUS to "received",
                SignalQrReceiveFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            committedTransferAuditJson = fieldsJson(mapOf(
                "transfer_sha256_expected" to state.transferSha256Expected,
                "transfer_sha256_reconstructed" to state.transferSha256Reconstructed,
                "transfer_sha256_verified" to state.transferSha256Verified.toString(),
                "segments_expected" to state.expectedSegments.toString(),
                "segments_complete" to state.completeSegments.toString()
            ))
            status = "VERIFIED — SHA-256 MATCH. Commit froze the reconstructed object and provenance."
            val result = signalResult(As100SignalQrReceiveMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        LaunchedEffect(messageFilter) { context.onSettingsChanged(mapOf("message_id_filter" to messageFilter)) }
        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalQrReceiveMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }
        val committedIsFile = committedFields[SignalQrReceiveFields.CONTENT_TYPE] == "file"
        val committedFileUri = committedFields[SignalQrReceiveFields.RECEIVED_FILE_URI].orEmpty()
        val committedMime = committedFields[SignalQrReceiveFields.FILE_MIME].orEmpty()
        val committedTransferAudit = fieldsFromJson(committedTransferAuditJson)
        val verificationText = if (committedFields.isEmpty()) "" else buildString {
            appendLine("MethodMesh Signals QR reception")
            appendLine("checksum_algorithm=SHA-256")
            appendLine("broadcast_checksum=${committedFields[SignalQrReceiveFields.EXPECTED_SHA256].orEmpty()}")
            appendLine("reconstructed_checksum=${committedFields[SignalQrReceiveFields.RECONSTRUCTED_SHA256].orEmpty()}")
            appendLine("checksum_verified=${committedFields[SignalQrReceiveFields.CHECKSUM_VERIFIED].orEmpty()}")
            if (committedTransferAudit["transfer_sha256_expected"].orEmpty().isNotBlank()) {
                appendLine("transfer_envelope_sha256=${committedTransferAudit["transfer_sha256_expected"].orEmpty()}")
                appendLine("transfer_envelope_rebuilt_sha256=${committedTransferAudit["transfer_sha256_reconstructed"].orEmpty()}")
                appendLine("transfer_envelope_verified=${committedTransferAudit["transfer_sha256_verified"].orEmpty()}")
                appendLine("segments=${committedTransferAudit["segments_complete"].orEmpty()}/${committedTransferAudit["segments_expected"].orEmpty()}")
            }
            appendLine("message_id=${committedFields[SignalQrReceiveFields.MESSAGE_ID].orEmpty()}")
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (listening) { listening = false; status = "Paused. Collected shards are retained." } else start() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (listening) "Pause camera" else "Start camera") }

            SignalInstrumentPanel(kicker = "MMS/1 OPTICAL COLLECTOR", title = "QR blast receiver", accent = SignalCyan, badge = when { decodedContent?.checksumVerified == true -> "Verified"; transferState.contentEnvelope != null -> "Integrity fail"; listening -> "Scanning"; else -> "Paused" }) {
                val headline = when {
                    decodedContent?.type == "file" -> decodedContent.fileName.orEmpty()
                    decodedContent?.type == "text" -> decodedContent.text.orEmpty()
                    transferState.expectedSegments > 0 -> "${transferState.completeSegments} / ${transferState.expectedSegments} SEGMENTS"
                    else -> "WAITING FOR MMS/1"
                }
                Text(headline.ifBlank { "WAITING FOR MMS/1" }, color = if (decodedContent == null) SignalMuted else SignalText, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                if (transferState.expectedSegments > 0) SignalProgressTrack(transferState.completeSegments.toFloat() / transferState.expectedSegments.toFloat(), SignalCyan)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("SEEN", framesSeen.toString(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("ACCEPTED", transferState.acceptedFrames.toString(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("RECOVERED", transferState.recoveredMissingSources.toString(), SignalCyan, Modifier.weight(1f))
                }
                decodedContent?.let { content ->
                    SignalStatusPill(if (content.checksumVerified) "SHA-256 MATCH" else "SHA-256 FAIL", if (content.checksumVerified) SignalGreen else SignalRed)
                    Text("BROADCAST  ${content.expectedSha256}", color = SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "broadcast SHA-256", content.expectedSha256) })
                    Text("REBUILT    ${content.reconstructedSha256}", color = SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "reconstructed SHA-256", content.reconstructedSha256) })
                    if (transferState.expectedSegments > 1) Text("TRANSFER   ${if (transferState.transferSha256Verified) "SHA-256 MATCH" else "assembling"} • ${transferState.completeSegments}/${transferState.expectedSegments} segments", color = if (transferState.transferSha256Verified) SignalGreen else SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    Text("The BROADCAST hash travelled with the original object. VERIFIED means the reconstructed bytes produced the same SHA-256.", color = SignalMuted, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (listening && cameraGranted) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SignalBlack)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalQrScanner(Modifier.fillMaxWidth().height(320.dp), cameraZoom, { info -> cameraActualZoom = info.actualZoomRatio; cameraMaxZoom = info.maxZoomRatio.coerceAtLeast(1f) }, ::acceptQr, { error = it; listening = false })
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("LONG-RANGE OPTICS", color = SignalCyan, fontWeight = FontWeight.Bold); Text("${"%.1f".format(cameraActualZoom)}×", color = SignalText, fontFamily = FontFamily.Monospace) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1f to "1×", 2f to "2×", 4f to "4×", cameraMaxZoom to "Max").forEach { (target, label) ->
                                    val effective = target.coerceAtMost(cameraMaxZoom).coerceAtLeast(1f)
                                    FilterChip(selected = kotlin.math.abs(cameraZoom - effective) < 0.15f, onClick = { cameraZoom = effective }, enabled = cameraMaxZoom > 1.01f, label = { Text(label) })
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::commit, enabled = transferState.complete && decodedContent?.checksumVerified == true, modifier = Modifier.weight(1f)) { Text(if (committedResult == null) "Commit verified" else "Recommit") }
                OutlinedButton(onClick = { clearReception() }, modifier = Modifier.weight(1f)) { Text("Reset") }
            }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = if (committedIsFile) "Verified received file" else "Verified QR reception",
                    primaryLabel = if (committedIsFile) "file name" else "received text",
                    primaryValue = if (committedIsFile) committedFields[SignalQrReceiveFields.FILE_NAME].orEmpty() else committedFields[SignalQrReceiveFields.RESULT].orEmpty(),
                    fields = listOf(
                        "SHA-256" to committedFields[SignalQrReceiveFields.EXPECTED_SHA256].orEmpty(),
                        "Verified" to committedFields[SignalQrReceiveFields.CHECKSUM_VERIFIED].orEmpty(),
                        "Bytes" to committedFields[SignalQrReceiveFields.FILE_BYTES].orEmpty(),
                        "Message ID" to committedFields[SignalQrReceiveFields.MESSAGE_ID].orEmpty(),
                        "Recovered" to committedFields[SignalQrReceiveFields.RECOVERED_MISSING].orEmpty(),
                        "MMS CRC" to committedFields[SignalQrReceiveFields.CRC_VERIFIED].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = {
                        exportStatus = if (committedIsFile) shareSignalMedia(androidContext, "Share verified received file", committedFileUri, committedMime) ?: "" else shareSignalText(androidContext, "Share verified message", committedFields[SignalQrReceiveFields.RESULT].orEmpty()) ?: ""
                    },
                    onSave = {
                        exportStatus = if (committedIsFile) saveSignalMedia(androidContext, "qr_verified_file", committedFileUri, verificationText, committedFullJson) else saveSignalText(androidContext, "qr_verified_text", verificationText + "\ntext=${committedFields[SignalQrReceiveFields.RESULT].orEmpty()}", committedFullJson)
                    },
                    onDone = {
                        finishSignalResult(context, androidContext, committedResult, onConfirmed) {
                            if (committedIsFile) saveSignalMedia(androidContext, "qr_verified_file", committedFileUri, verificationText, committedFullJson) else saveSignalText(androidContext, "qr_verified_text", verificationText, committedFullJson)
                        }
                    },
                    onOpen = if (committedIsFile) ({ exportStatus = openSignalMedia(androidContext, committedFileUri, committedMime) ?: "" }) else null
                )
            }

            if (context.settingShouldBeShown("message_id_filter")) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) { OutlinedTextField(messageFilter, { next -> if (next != messageFilter) { messageFilter = next; clearReception(preserveCommitted = true) } }, enabled = !listening, modifier = Modifier.fillMaxWidth(), label = { Text("Optional message ID filter") }) }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (committedResult == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.stepNumber > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SignalSelectAllTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) { if (fieldValue.text != value) fieldValue = TextFieldValue(value) }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { next -> fieldValue = next; onValueChange(next.text) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
            if (focus.isFocused && fieldValue.text.isNotEmpty()) fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
        },
        label = { Text(label) },
        minLines = 5
    )
}

private fun Context.signalDisplayName(uri: Uri): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else ""
    }.orEmpty()
}.getOrDefault("")

private fun qrHasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
