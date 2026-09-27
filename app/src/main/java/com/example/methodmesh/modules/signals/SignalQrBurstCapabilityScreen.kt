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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SIGNAL_QR_BURST_EXAMPLE = "MethodMesh QR Burst: capture first, decode afterwards."

object SignalQrBurstTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalQrBurstTransmitMethod.id
    override val title = "QR Burst transmitter"
    override val description = "Flash MMS/1 QR frames at 10–30 QR/s for capture-first offline decoding on the receiving phone."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val activity = remember(androidContext) { androidContext.signalActivity() }
        val settings = context.action.settings
        var contentMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("content_mode", "text").let { if (it == "file") "file" else "text" }) }
        var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("payload", SIGNAL_QR_BURST_EXAMPLE)) }
        var fileUri by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("file_uri", "")) }
        var fileName by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("file_name", "")) }
        var fileMime by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("file_mime", "")) }
        var profileId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("profile", "balanced")) }
        var loop by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("loop", "true").toBooleanStrictOrNull() ?: true) }
        var transferId by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPacketCodec.newMessageId().take(12)) }
        var active by remember { mutableStateOf(false) }
        var frameIndex by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var cycles by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Prepare content, then start the recorded QR burst.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        SignalActiveSessionOrientationGuard(active)

        val profile = SignalQrBurstProfiles.byId(profileId)

        fun invalidate(reason: String = "Content or burst profile changed; transmit a fresh complete cycle before Commit.") {
            active = false
            frameIndex = 0
            cycles = 0
            transferId = SignalPacketCodec.newMessageId().take(12)
            status = reason
            error = ""
        }

        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentMode = "file"
                fileUri = uri.toString()
                fileName = androidContext.qrBurstDisplayName(uri).ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') }
                fileMime = androidContext.contentResolver.getType(uri).orEmpty()
                invalidate("File selected. Preparing high-rate QR frames.")
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
        val encodedAttempt = remember(contentBytes, profileId, transferId) {
            runCatching {
                SignalQrTransferCodec.encode(
                    contentEnvelope = contentBytes ?: error(contentError.ifBlank { "No content is ready." }),
                    robustness = profile.robustness,
                    shardBytes = profile.shardBytes,
                    transferId = transferId
                )
            }
        }
        val encoded = encodedAttempt.getOrNull()
        val encodeError = encodedAttempt.exceptionOrNull()?.message.orEmpty()
        val frames = encoded?.frames.orEmpty()
        val cycleMs = qrBurstCycleDurationMs(frames.size, profile.qrPerSecond)
        val currentFrame = frames.getOrNull(frameIndex).orEmpty()
        val qrBitmap = remember(currentFrame) { currentFrame.takeIf { it.isNotBlank() }?.let { SignalQrRenderer.bitmap(it, 1000) } }

        LaunchedEffect(contentMode, payload, fileUri, fileName, fileMime, profileId, loop) {
            context.onSettingsChanged(
                mapOf(
                    "content_mode" to contentMode,
                    "payload" to payload,
                    "file_uri" to fileUri,
                    "file_name" to fileName,
                    "file_mime" to fileMime,
                    "profile" to profileId,
                    "loop" to loop.toString()
                )
            )
        }

        LaunchedEffect(active, frames, profileId, loop) {
            if (!active || frames.isEmpty()) return@LaunchedEffect
            while (active) {
                delay(profile.dwellMs.toLong())
                if (!active) break
                if (frameIndex >= frames.lastIndex) {
                    cycles++
                    frameIndex = 0
                    status = "QR Burst cycle $cycles complete${if (loop) " — continuing" else ""}."
                    if (!loop) { active = false; break }
                } else frameIndex++
            }
        }

        fun start() {
            error = contentError.ifBlank { encodeError }
            if (frames.isEmpty() || envelope == null || encoded == null) {
                if (error.isBlank()) error = "Content could not be encoded."
                return
            }
            active = true
            frameIndex = 0
            cycles = 0
            status = "Broadcasting ${profile.qrPerSecond} QR/s • ${frames.size} frames/cycle • ${cycleMs} ms/cycle."
            error = ""
        }

        fun stop() {
            active = false
            status = if (cycles > 0) "Stopped after $cycles complete cycle(s)." else "Stopped before one complete cycle."
        }

        fun commit() {
            val packet = encoded
            val metadata = envelope
            if (packet == null || metadata == null || cycles <= 0) {
                error = "Complete at least one QR Burst cycle before Commit."
                return
            }
            active = false
            val values = mapOf(
                SignalQrBurstTransmitFields.RESULT to if (metadata.type == "file") metadata.fileName.orEmpty() else metadata.text.orEmpty(),
                SignalQrBurstTransmitFields.PAYLOAD to if (metadata.type == "text") metadata.text.orEmpty() else "",
                SignalQrBurstTransmitFields.CONTENT_TYPE to metadata.type,
                SignalQrBurstTransmitFields.FILE_NAME to metadata.fileName.orEmpty(),
                SignalQrBurstTransmitFields.FILE_MIME to metadata.mimeType.orEmpty(),
                SignalQrBurstTransmitFields.FILE_BYTES to metadata.payload.size.toString(),
                SignalQrBurstTransmitFields.CHECKSUM_SHA256 to metadata.expectedSha256,
                SignalQrBurstTransmitFields.MESSAGE_ID to packet.transferId,
                SignalQrBurstTransmitFields.FRAME_COUNT to packet.frames.size.toString(),
                SignalQrBurstTransmitFields.QR_PER_SECOND to profile.qrPerSecond.toString(),
                SignalQrBurstTransmitFields.SHARD_BYTES to profile.shardBytes.toString(),
                SignalQrBurstTransmitFields.CYCLE_DURATION_MS to cycleMs.toString(),
                SignalQrBurstTransmitFields.PROFILE to profile.id,
                SignalQrBurstTransmitFields.CYCLES to cycles.toString(),
                SignalQrBurstTransmitFields.STATUS to "sent",
                SignalQrBurstTransmitFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed QR Burst transmission."
            val result = signalResult(As100SignalQrBurstTransmitMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        DisposableEffect(activity, active) {
            val window = activity?.window
            val previous = window?.attributes?.screenBrightness
            if (window != null && active) window.attributes = window.attributes.apply { screenBrightness = 1f }
            onDispose { if (window != null && previous != null) window.attributes = window.attributes.apply { screenBrightness = previous } }
        }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalQrBurstTransmitMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (active) stop() else start() }, enabled = frames.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(if (active) "Stop QR Burst" else "Start QR Burst")
            }

            SignalInstrumentPanel(kicker = "RECORDED OPTICAL MODEM", title = "QR Burst transmitter", accent = SignalCyan, badge = if (active) "On air" else "Ready") {
                Box(Modifier.fillMaxWidth().height(260.dp).background(Color.White, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    if (qrBitmap != null) Image(qrBitmap.asImageBitmap(), "Current high-rate QR frame", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                    else Text("BUILDING QR BURST", color = SignalBlack, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("RATE", "${profile.qrPerSecond}/s", SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("FRAME", if (frames.isEmpty()) "—" else "${frameIndex + 1}/${frames.size}", SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("CYCLE", if (cycleMs > 0) "${cycleMs}ms" else "—", SignalCyan, Modifier.weight(1f))
                }
                Text("Receiver records first and performs QR decoding only after capture stops. MMS/1 still supplies frame CRC, Reed–Solomon recovery and final SHA-256 verification.", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::commit, enabled = cycles > 0 && !active, modifier = Modifier.weight(1f)) { Text(if (committedResult == null) "Commit" else "Recommit") }
                OutlinedButton(onClick = { invalidate("Transmission reset.") }, modifier = Modifier.weight(1f)) { Text("Reset") }
            }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed QR Burst",
                    primaryLabel = if (committedFields[SignalQrBurstTransmitFields.CONTENT_TYPE] == "file") "file" else "message",
                    primaryValue = committedFields[SignalQrBurstTransmitFields.RESULT].orEmpty(),
                    fields = listOf(
                        "SHA-256" to committedFields[SignalQrBurstTransmitFields.CHECKSUM_SHA256].orEmpty(),
                        "Message ID" to committedFields[SignalQrBurstTransmitFields.MESSAGE_ID].orEmpty(),
                        "Rate" to "${committedFields[SignalQrBurstTransmitFields.QR_PER_SECOND].orEmpty()} QR/s",
                        "Frames" to committedFields[SignalQrBurstTransmitFields.FRAME_COUNT].orEmpty(),
                        "Profile" to committedFields[SignalQrBurstTransmitFields.PROFILE].orEmpty()
                    ),
                    status = null,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { shareSignalText(androidContext, "Share QR Burst record", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }) },
                    onSave = { saveSignalText(androidContext, "qr_burst_transmit", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, committedFullJson) },
                    onDone = { finishSignalResult(context, androidContext, committedResult, onConfirmed) }
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (context.settingShouldBeShown("content_mode")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = contentMode == "text", enabled = !active, onClick = { contentMode = "text"; invalidate() }, label = { Text("Text") })
                            FilterChip(selected = contentMode == "file", enabled = !active, onClick = { contentMode = "file"; invalidate() }, label = { Text("File") })
                        }
                    }
                    if (contentMode == "text") {
                        if (context.settingShouldBeShown("payload")) OutlinedTextField(payload, { payload = it; invalidate() }, enabled = !active, modifier = Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 3)
                    } else {
                        Text(fileName.ifBlank { "No file selected" }, fontWeight = FontWeight.SemiBold)
                        Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !active, modifier = Modifier.fillMaxWidth()) { Text(if (fileUri.isBlank()) "Choose file" else "Choose another file") }
                    }
                    if (context.settingShouldBeShown("profile")) {
                        Text("Burst profile")
                        SignalQrBurstProfiles.all.forEach { item ->
                            FilterChip(selected = profileId == item.id, enabled = !active, onClick = { profileId = item.id; invalidate() }, label = { Text("${item.label} · ${item.shardBytes} B/shard") })
                        }
                    }
                    if (context.settingShouldBeShown("loop")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = loop, enabled = !active, onClick = { loop = true; cycles = 0 }, label = { Text("Loop") })
                            FilterChip(selected = !loop, enabled = !active, onClick = { loop = false; cycles = 0 }, label = { Text("One cycle") })
                        }
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
                Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("QR BURST · ${profile.qrPerSecond} QR/s", color = SignalCyan, fontWeight = FontWeight.Bold)
                        Text("${frameIndex + 1}/${frames.size}", color = SignalText, fontFamily = FontFamily.Monospace)
                    }
                    Button(onClick = ::stop, modifier = Modifier.fillMaxWidth()) { Text("Stop transmission") }
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Image(qrBitmap.asImageBitmap(), "Fullscreen QR Burst frame", Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
    }
}

object SignalQrBurstReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalQrBurstReceiveMethod.id
    override val title = "QR Burst recorded receiver"
    override val description = "Record a short grayscale camera burst first, then decode its QR frames offline and accumulate MMS/1 shards across captures."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val settings = context.action.settings
        val scope = rememberCoroutineScope()
        var captureSeconds by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("capture_seconds", "5").toIntOrNull()?.coerceIn(2, 12) ?: 5) }
        var messageFilter by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("message_id_filter", "")) }
        var cameraZoom by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("zoom_ratio", "1").toFloatOrNull()?.coerceAtLeast(1f) ?: 1f) }
        var cameraActualZoom by remember { mutableStateOf(1f) }
        var cameraMaxZoom by remember { mutableStateOf(4f) }
        var cameraGranted by remember { mutableStateOf(qrBurstHasPermission(androidContext, Manifest.permission.CAMERA)) }
        var capturing by remember { mutableStateOf(false) }
        var analyzing by remember { mutableStateOf(false) }
        var capturedThisBurst by remember { mutableStateOf(0) }
        var totalCaptured by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var totalAnalyzed by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var captureMsTotal by rememberSaveable(context.action.canonicalId) { mutableStateOf(0L) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Capture a short burst while the transmitting QR fills the target area. Decoding happens afterwards.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val buffer = remember { SignalQrBurstFrameBuffer() }
        val collector = remember { SignalQrBurstOfflineCollector(messageFilter) }
        var snapshot by remember { mutableStateOf(collector.snapshot()) }
        SignalActiveSessionOrientationGuard(capturing)

        val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted || qrBurstHasPermission(androidContext, Manifest.permission.CAMERA)
            if (!cameraGranted) error = "Camera permission is required for QR Burst receive."
        }

        LaunchedEffect(captureSeconds, messageFilter, cameraZoom) {
            context.onSettingsChanged(mapOf("capture_seconds" to captureSeconds.toString(), "message_id_filter" to messageFilter, "zoom_ratio" to cameraZoom.toString()))
        }

        fun resetWorking() {
            capturing = false
            analyzing = false
            buffer.clear()
            capturedThisBurst = 0
            totalCaptured = 0
            totalAnalyzed = 0
            captureMsTotal = 0L
            collector.reset(messageFilter)
            snapshot = collector.snapshot()
            status = "Working QR Burst evidence cleared."
            error = ""
        }

        fun analyzeCurrentBurst() {
            if (analyzing) return
            capturing = false
            val frames = buffer.snapshot()
            if (frames.isEmpty()) {
                error = "No camera frames were captured."
                return
            }
            analyzing = true
            status = "Analyzing ${frames.size} captured frames offline…"
            val captureDurationMs = if (frames.size >= 2) ((frames.last().timestampNs - frames.first().timestampNs) / 1_000_000L).coerceAtLeast(0L) else 0L
            scope.launch {
                val report = withContext(Dispatchers.Default) { decodeQrBurstFrames(frames) }
                report.decodedPayloads.forEach { snapshot = collector.offerDecoded(it) }
                totalCaptured += frames.size
                totalAnalyzed += report.framesAnalyzed
                captureMsTotal += captureDurationMs
                analyzing = false
                buffer.clear()
                capturedThisBurst = 0
                val transfer = snapshot.transfer
                val content = transfer.contentEnvelope?.let(SignalContentEnvelope::decode)
                status = when {
                    transfer.complete && content?.checksumVerified == true -> "VERIFIED — offline burst decoding recovered the complete SHA-256-verified object."
                    report.qrDecodes == 0 -> "No QR codes decoded from this burst. Reframe/zoom and capture again."
                    transfer.expectedSegments > 0 -> "Offline decode found ${report.qrDecodes} QR observations; ${transfer.completeSegments}/${transfer.expectedSegments} segments complete. Capture another burst if needed."
                    else -> "Offline decode found ${report.qrDecodes} QR observations and ${snapshot.uniqueQrFrames} unique MMS/1 frames. Capture another burst to continue recovery."
                }
            }
        }

        fun startCapture() {
            error = ""
            if (!cameraGranted) {
                cameraPermission.launch(Manifest.permission.CAMERA)
                return
            }
            if (analyzing) return
            buffer.clear()
            capturedThisBurst = 0
            capturing = true
            status = "Capturing raw grayscale frames for $captureSeconds s — no QR decoding yet."
        }

        LaunchedEffect(capturing, captureSeconds) {
            if (capturing) {
                delay(captureSeconds * 1000L)
                if (capturing) analyzeCurrentBurst()
            }
        }

        fun writeReceivedFile(content: SignalContentEnvelope.Decoded): String {
            val safe = content.fileName.orEmpty().replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "received_file.bin" }
            val target = File(androidContext.cacheDir, "methodmesh-qr-burst-${System.currentTimeMillis()}-$safe")
            target.writeBytes(content.payload)
            return FileProvider.getUriForFile(androidContext, "${androidContext.packageName}.fileprovider", target).toString()
        }

        fun commit() {
            val transfer = snapshot.transfer
            val content = transfer.contentEnvelope?.let(SignalContentEnvelope::decode)
            if (!transfer.complete || content == null || !content.checksumVerified) {
                error = "QR Burst is not yet recoverable as a complete SHA-256-verified object."
                return
            }
            val fileUri = if (content.type == "file") runCatching { writeReceivedFile(content) }.getOrElse { error = it.message ?: "Could not expose the reconstructed file."; return } else ""
            val values = mapOf(
                SignalQrBurstReceiveFields.RESULT to content.text.orEmpty(),
                SignalQrBurstReceiveFields.CONTENT_TYPE to content.type,
                SignalQrBurstReceiveFields.RECEIVED_FILE_URI to fileUri,
                SignalQrBurstReceiveFields.FILE_NAME to content.fileName.orEmpty(),
                SignalQrBurstReceiveFields.FILE_MIME to content.mimeType.orEmpty(),
                SignalQrBurstReceiveFields.FILE_BYTES to content.payload.size.toString(),
                SignalQrBurstReceiveFields.EXPECTED_SHA256 to content.expectedSha256,
                SignalQrBurstReceiveFields.RECONSTRUCTED_SHA256 to content.reconstructedSha256,
                SignalQrBurstReceiveFields.CHECKSUM_VERIFIED to content.checksumVerified.toString(),
                SignalQrBurstReceiveFields.MESSAGE_ID to transfer.transferId.orEmpty(),
                SignalQrBurstReceiveFields.CAPTURED_FRAMES to totalCaptured.toString(),
                SignalQrBurstReceiveFields.FRAMES_ANALYZED to totalAnalyzed.toString(),
                SignalQrBurstReceiveFields.QR_DECODES to snapshot.qrDecodes.toString(),
                SignalQrBurstReceiveFields.UNIQUE_QR_FRAMES to snapshot.uniqueQrFrames.toString(),
                SignalQrBurstReceiveFields.DUPLICATES to snapshot.duplicates.toString(),
                SignalQrBurstReceiveFields.REJECTED to (snapshot.rejectedQrPayloads + snapshot.unrelatedQrPayloads).toString(),
                SignalQrBurstReceiveFields.RECOVERED_MISSING to transfer.recoveredMissingSources.toString(),
                SignalQrBurstReceiveFields.CAPTURE_MS to captureMsTotal.toString(),
                SignalQrBurstReceiveFields.STATUS to "received",
                SignalQrBurstReceiveFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed verified QR Burst reception."
            val result = signalResult(As100SignalQrBurstReceiveMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        val decodedContent = snapshot.transfer.contentEnvelope?.let(SignalContentEnvelope::decode)
        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalQrBurstReceiveMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }
        val committedIsFile = committedFields[SignalQrBurstReceiveFields.CONTENT_TYPE] == "file"
        val committedFileUri = committedFields[SignalQrBurstReceiveFields.RECEIVED_FILE_URI].orEmpty()
        val committedMime = committedFields[SignalQrBurstReceiveFields.FILE_MIME].orEmpty()

        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (capturing) analyzeCurrentBurst() else startCapture() },
                enabled = !analyzing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (capturing) "Stop capture & analyze" else if (analyzing) "Analyzing…" else "Capture burst") }

            SignalInstrumentPanel(kicker = "CAPTURE FIRST · DECODE AFTER", title = "QR Burst recorded receiver", accent = SignalCyan, badge = when {
                decodedContent?.checksumVerified == true -> "Verified"
                analyzing -> "Analyzing"
                capturing -> "Recording"
                else -> "Ready"
            }) {
                Text(
                    when {
                        decodedContent?.type == "file" -> decodedContent.fileName.orEmpty()
                        decodedContent?.type == "text" -> decodedContent.text.orEmpty()
                        snapshot.transfer.expectedSegments > 0 -> "${snapshot.transfer.completeSegments}/${snapshot.transfer.expectedSegments} SEGMENTS"
                        else -> "WAITING FOR CAPTURE"
                    }.ifBlank { "WAITING FOR CAPTURE" },
                    color = if (decodedContent == null) SignalMuted else SignalText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("CAPTURED", (totalCaptured + capturedThisBurst).toString(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("DECODED", snapshot.qrDecodes.toString(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("UNIQUE", snapshot.uniqueQrFrames.toString(), SignalCyan, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("DUPES", snapshot.duplicates.toString(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("REPAIRED", snapshot.transfer.recoveredMissingSources.toString(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("ANALYZED", totalAnalyzed.toString(), SignalCyan, Modifier.weight(1f))
                }
                decodedContent?.let { content ->
                    SignalStatusPill(if (content.checksumVerified) "SHA-256 MATCH" else "SHA-256 FAIL", if (content.checksumVerified) SignalGreen else SignalRed)
                    Text("BROADCAST  ${content.expectedSha256}", color = SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "broadcast SHA-256", content.expectedSha256) })
                    Text("REBUILT    ${content.reconstructedSha256}", color = SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "reconstructed SHA-256", content.reconstructedSha256) })
                }
            }

            if (cameraGranted) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SignalBlack)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalQrBurstCapturePreview(
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            active = capturing,
                            buffer = buffer,
                            zoomRatio = cameraZoom,
                            onFrameCount = { capturedThisBurst = it },
                            onCameraInfo = { cameraActualZoom = it.actualZoomRatio; cameraMaxZoom = it.maxZoomRatio.coerceAtLeast(1f) },
                            onError = { error = it; capturing = false }
                        )
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (capturing) "RAW FRAME CAPTURE" else "CAMERA READY", color = SignalCyan, fontWeight = FontWeight.Bold)
                                Text("${"%.1f".format(cameraActualZoom)}×", color = SignalText, fontFamily = FontFamily.Monospace)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1f to "1×", 2f to "2×", 4f to "4×", cameraMaxZoom to "Max").forEach { (target, label) ->
                                    val effective = target.coerceAtMost(cameraMaxZoom).coerceAtLeast(1f)
                                    FilterChip(selected = kotlin.math.abs(cameraZoom - effective) < 0.15f, onClick = { cameraZoom = effective }, enabled = !capturing && !analyzing && cameraMaxZoom > 1.01f, label = { Text(label) })
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("Grant camera permission") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::commit, enabled = decodedContent?.checksumVerified == true && !capturing && !analyzing, modifier = Modifier.weight(1f)) { Text(if (committedResult == null) "Commit verified" else "Recommit") }
                OutlinedButton(onClick = ::resetWorking, enabled = !analyzing, modifier = Modifier.weight(1f)) { Text("Reset") }
            }

            if (committedResult != null && !context.submitsImmediately) {
                val verification = buildString {
                    appendLine("MethodMesh Signals QR Burst reception")
                    appendLine("checksum_algorithm=SHA-256")
                    appendLine("broadcast_checksum=${committedFields[SignalQrBurstReceiveFields.EXPECTED_SHA256].orEmpty()}")
                    appendLine("reconstructed_checksum=${committedFields[SignalQrBurstReceiveFields.RECONSTRUCTED_SHA256].orEmpty()}")
                    appendLine("checksum_verified=${committedFields[SignalQrBurstReceiveFields.CHECKSUM_VERIFIED].orEmpty()}")
                    appendLine("captured_frames=${committedFields[SignalQrBurstReceiveFields.CAPTURED_FRAMES].orEmpty()}")
                    appendLine("unique_qr_frames=${committedFields[SignalQrBurstReceiveFields.UNIQUE_QR_FRAMES].orEmpty()}")
                }
                SignalCommittedCard(
                    title = if (committedIsFile) "Verified QR Burst file" else "Verified QR Burst message",
                    primaryLabel = if (committedIsFile) "file name" else "received text",
                    primaryValue = if (committedIsFile) committedFields[SignalQrBurstReceiveFields.FILE_NAME].orEmpty() else committedFields[SignalQrBurstReceiveFields.RESULT].orEmpty(),
                    fields = listOf(
                        "SHA-256" to committedFields[SignalQrBurstReceiveFields.EXPECTED_SHA256].orEmpty(),
                        "Captured" to committedFields[SignalQrBurstReceiveFields.CAPTURED_FRAMES].orEmpty(),
                        "Decoded" to committedFields[SignalQrBurstReceiveFields.QR_DECODES].orEmpty(),
                        "Unique" to committedFields[SignalQrBurstReceiveFields.UNIQUE_QR_FRAMES].orEmpty(),
                        "Recovered" to committedFields[SignalQrBurstReceiveFields.RECOVERED_MISSING].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { exportStatus = if (committedIsFile) shareSignalMedia(androidContext, "Share QR Burst file", committedFileUri, committedMime) ?: "" else shareSignalText(androidContext, "Share QR Burst message", committedFields[SignalQrBurstReceiveFields.RESULT].orEmpty()) ?: "" },
                    onSave = { exportStatus = if (committedIsFile) saveSignalMedia(androidContext, "qr_burst_verified_file", committedFileUri, verification, committedFullJson) else saveSignalText(androidContext, "qr_burst_verified_text", verification + "\ntext=${committedFields[SignalQrBurstReceiveFields.RESULT].orEmpty()}", committedFullJson) },
                    onDone = { finishSignalResult(context, androidContext, committedResult, onConfirmed) },
                    onOpen = if (committedIsFile) ({ exportStatus = openSignalMedia(androidContext, committedFileUri, committedMime) ?: "" }) else null
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (context.settingShouldBeShown("capture_seconds")) {
                        Text("Capture length")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 8).forEach { seconds -> FilterChip(selected = captureSeconds == seconds, enabled = !capturing && !analyzing, onClick = { captureSeconds = seconds }, label = { Text("${seconds}s") }) }
                        }
                    }
                    if (context.settingShouldBeShown("message_id_filter")) OutlinedTextField(messageFilter, { next ->
                        if (next != messageFilter) {
                            messageFilter = next
                            collector.updateFilter(next)
                            snapshot = collector.snapshot()
                            totalCaptured = 0; totalAnalyzed = 0; captureMsTotal = 0L
                        }
                    }, enabled = !capturing && !analyzing, modifier = Modifier.fillMaxWidth(), label = { Text("Optional transfer ID filter") })
                    Text("Capture is intentionally decode-free. The frozen grayscale frames are scanned by ZXing only after recording stops; repeated QR observations are deduplicated before MMS/1 recovery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

private fun Context.qrBurstDisplayName(uri: Uri): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else ""
    }.orEmpty()
}.getOrDefault("")

private fun qrBurstHasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
