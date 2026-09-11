package com.example.methodmesh.modules.signals

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import org.json.JSONArray
import kotlin.math.roundToInt

private const val OPTICAL_EXAMPLE = "HELLO FROM METHODMESH OPTICAL"
private const val TORCH_EXAMPLE = "HELLO FROM METHODMESH TORCH"

private data class PreparedOpticalText(
    val envelope: SignalContentEnvelope.Decoded,
    val wire: SignalAcousticPayloadCodec.Encoded,
    val packet: SignalPacketCodec.EncodedMessage
)

private data class PreparedCompactOpticalText(
    val envelope: SignalContentEnvelope.Decoded,
    val packet: ByteArray,
    val messageId: String
)

private fun prepareCompactOpticalText(payload: String, messageId: String): PreparedCompactOpticalText {
    require(payload.isNotBlank()) { "Enter text to transmit." }
    val packet = OpticalCompactTextCodec.encode(payload, messageId)
    val decoded = requireNotNull(OpticalCompactTextCodec.decode(packet))
    val envelope = requireNotNull(SignalContentEnvelope.decode(SignalContentEnvelope.encodeText(decoded.text)))
    return PreparedCompactOpticalText(envelope, packet, decoded.messageId)
}

private data class OpticalScreenUnit(
    val frameIndex: Int,
    val scalarLevel: Int? = null,
    val aprilTagId: Int? = null
)

private fun prepareOpticalText(
    payload: String,
    robustness: String,
    profile: SignalOpticalProfiles.Profile,
    messageId: String
): PreparedOpticalText {
    require(payload.isNotBlank()) { "Enter text to transmit." }
    val envelopeBytes = SignalContentEnvelope.encodeText(payload)
    val metadata = requireNotNull(SignalContentEnvelope.decode(envelopeBytes)) { "Content envelope could not be verified before transmission." }
    val wire = SignalAcousticPayloadCodec.encodeBytes(envelopeBytes)
    val packet = SignalPacketCodec.encode(
        wire.bytes,
        SignalPacketCodec.Robustness.from(robustness),
        profile.shardBytes,
        messageId
    )
    return PreparedOpticalText(metadata, wire, packet)
}

private fun storedSignalFrames(raw: String): MutableList<String> = runCatching {
    val array = JSONArray(raw)
    MutableList(array.length()) { array.getString(it) }
}.getOrDefault(mutableListOf())

private fun signalFrameStore(frames: List<String>): String = JSONArray().also { array -> frames.forEach(array::put) }.toString()

private fun signalCollectorFrom(raw: String): SignalFrameCollector = SignalFrameCollector().also { collector ->
    storedSignalFrames(raw).forEach(collector::offer)
}

private fun decodedOpticalEnvelope(state: SignalFrameCollector.State): SignalContentEnvelope.Decoded? =
    state.payload
        ?.let(SignalAcousticPayloadCodec::decodeBytes)
        ?.let(SignalContentEnvelope::decode)

private fun roiFromSetting(raw: String, grid: Boolean = false): SignalCameraRoiMode = when (raw.lowercase()) {
    "full" -> SignalCameraRoiMode.FULL
    "pinpoint" -> if (grid) SignalCameraRoiMode.FOCUS else SignalCameraRoiMode.PINPOINT
    else -> SignalCameraRoiMode.FOCUS
}

private fun lumaForLevel(level: Int): Float = when (level.coerceIn(0, 3)) {
    0 -> 0.035f
    1 -> 0.30f
    2 -> 0.66f
    else -> 1.0f
}

@Composable
private fun OpticalAprilTagDisplay(tagId: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White)) {
        val id = tagId ?: return@Canvas
        val modules = AprilTag16h5.modules(id)
        val side = minOf(this.size.width, this.size.height) * 0.96f
        val left = (this.size.width - side) / 2f
        val top = (this.size.height - side) / 2f
        val cell = side / AprilTag16h5.TOTAL_WIDTH.toFloat()
        drawRect(Color.White, Offset(left, top), Size(side, side))
        for (row in 0 until AprilTag16h5.TOTAL_WIDTH) {
            for (column in 0 until AprilTag16h5.TOTAL_WIDTH) {
                val color = if (modules[row][column]) Color.White else Color.Black
                drawRect(
                    color = color,
                    topLeft = Offset(left + column * cell, top + row * cell),
                    size = Size(cell + 0.75f, cell + 0.75f)
                )
            }
        }
    }
}

@Composable
private fun OpticalScreenDisplay(unit: OpticalScreenUnit?, mode: String, modifier: Modifier = Modifier) {
    if (mode == "grid") {
        OpticalAprilTagDisplay(unit?.aprilTagId, modifier)
    } else {
        val luma = lumaForLevel(unit?.scalarLevel ?: 0)
        Box(modifier.background(Color(luma, luma, luma)))
    }
}

private fun screenUnits(packet: SignalPacketCodec.EncodedMessage?, mode: String, profile: SignalOpticalProfiles.Profile): List<OpticalScreenUnit> {
    if (packet == null || mode != "pam") return emptyList()
    return packet.frames.flatMapIndexed { frameIndex, frame ->
        OpticalPamCodec.encodeFrame(frame).map { level -> OpticalScreenUnit(frameIndex = frameIndex, scalarLevel = level) }
    }
}

private fun compactGridUnits(packet: ByteArray?): List<OpticalScreenUnit> {
    if (packet == null) return emptyList()
    return AprilTagBurstCodec.encode(packet).map { state ->
        OpticalScreenUnit(frameIndex = 0, aprilTagId = state.tagId)
    }
}


@Composable
private fun RangeProfileChips(selected: String, enabled: Boolean, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SignalOpticalProfiles.all.forEach { profile ->
            FilterChip(
                selected = selected == profile.id,
                enabled = enabled,
                onClick = { onSelected(profile.id) },
                label = { Text(profile.label.substringBefore(" — ")) }
            )
        }
    }
}

@Composable
private fun RobustnessChips(selected: String, enabled: Boolean, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("fast", "robust", "extreme").forEach { item ->
            FilterChip(selected = selected == item, enabled = enabled, onClick = { onSelected(item) }, label = { Text(item.replaceFirstChar { it.uppercase() }) })
        }
    }
}

object SignalOpticalScreenTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalOpticalScreenTransmitMethod.id
    override val title = "Screen optical modem"
    override val description = "Transmit short text through range-first 4-PAM or full-screen AprilTag16h5 Burst."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val activity = remember(androidContext) { androidContext.signalActivity() }
        val settings = context.action.settings
        var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("payload", OPTICAL_EXAMPLE)) }
        var mode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("mode", "grid").let { if (it == "pam") "pam" else "grid" }) }
        var profileId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("range_profile", "long")) }
        var robustness by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("robustness", "robust")) }
        var loopMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("loop_mode", "continuous")) }
        var repeatCount by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("repeat_count", "3").toIntOrNull()?.coerceIn(1, 10) ?: 3) }
        var messageId by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPacketCodec.newMessageId()) }
        var active by remember { mutableStateOf(false) }
        var unitIndex by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var cycles by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Prepare a message, then start the optical link.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val profile = SignalOpticalProfiles.byId(profileId)
        SignalActiveSessionOrientationGuard(active)

        fun invalidate(reason: String = "Settings changed; complete a new optical cycle before Commit.") {
            active = false
            unitIndex = 0
            cycles = 0
            messageId = SignalPacketCodec.newMessageId()
            status = reason
            error = ""
        }

        val preparedAttempt = remember(payload, robustness, profile.id, messageId) {
            runCatching { prepareOpticalText(payload, robustness, profile, messageId) }
        }
        val prepared = preparedAttempt.getOrNull()
        val preparationError = preparedAttempt.exceptionOrNull()?.message.orEmpty()
        val compactAttempt = remember(payload, messageId) { runCatching { prepareCompactOpticalText(payload, messageId) } }
        val compact = compactAttempt.getOrNull()
        val compactPreparationError = compactAttempt.exceptionOrNull()?.message.orEmpty()
        val usingCompactGrid = mode == "grid" && compact != null
        val units = remember(prepared?.packet, compact?.packet?.contentHashCode(), mode, profile.id) {
            if (usingCompactGrid) compactGridUnits(compact?.packet) else screenUnits(prepared?.packet, mode, profile)
        }
        val dwellMs = if (mode == "pam") profile.pamSymbolMs else profile.gridDwellMs
        val currentUnit = units.getOrNull(unitIndex)
        val currentFrame = currentUnit?.frameIndex?.plus(1) ?: 0

        LaunchedEffect(payload, mode, profileId, robustness, loopMode, repeatCount) {
            context.onSettingsChanged(
                mapOf(
                    "payload" to payload,
                    "mode" to mode,
                    "range_profile" to profileId,
                    "robustness" to robustness,
                    "loop_mode" to loopMode,
                    "repeat_count" to repeatCount.toString()
                )
            )
        }

        LaunchedEffect(active, units, dwellMs, loopMode, repeatCount) {
            if (!active || units.isEmpty()) return@LaunchedEffect
            while (active) {
                delay(dwellMs.toLong())
                if (!active) break
                if (unitIndex >= units.lastIndex) {
                    cycles++
                    unitIndex = 0
                    status = "Optical cycle $cycles complete${if (loopMode == "continuous") " — looping" else ""}."
                    if (loopMode != "continuous" && cycles >= repeatCount) {
                        active = false
                        break
                    }
                } else unitIndex++
            }
        }

        fun start() {
            error = if (mode == "grid") compactPreparationError else preparationError
            if (prepared == null || units.isEmpty()) {
                if (error.isBlank()) error = if (mode == "grid") "AprilTag Burst needs compact field text; use 4-PAM for unsupported characters or very long payloads." else "Optical payload could not be encoded."
                return
            }
            unitIndex = 0
            cycles = 0
            active = true
            status = "${profile.label} • ${if (usingCompactGrid) "AprilTag Burst • ${units.size} states" else if (mode == "grid") "AprilTag Burst" else "4-PAM"} on air."
            error = ""
        }

        fun stop() {
            active = false
            status = if (cycles > 0) "Stopped after $cycles complete optical cycle(s)." else "Stopped before a complete cycle."
        }

        fun commit() {
            val ready = prepared
            if (ready == null || cycles <= 0) {
                error = "Complete at least one full optical cycle before Commit."
                return
            }
            active = false
            val values = mapOf(
                SignalOpticalScreenTransmitFields.RESULT to ready.envelope.text.orEmpty(),
                SignalOpticalScreenTransmitFields.PAYLOAD to ready.envelope.text.orEmpty(),
                SignalOpticalScreenTransmitFields.MESSAGE_ID to (if (usingCompactGrid) compact?.messageId.orEmpty() else ready.packet.messageId),
                SignalOpticalScreenTransmitFields.MODE to mode,
                SignalOpticalScreenTransmitFields.RANGE_PROFILE to profile.id,
                SignalOpticalScreenTransmitFields.DATA_SHARDS to (if (usingCompactGrid) "1" else ready.packet.dataShardCount.toString()),
                SignalOpticalScreenTransmitFields.PARITY_FRAMES to (if (usingCompactGrid) AprilTagBurstCodec.encode(compact!!.packet).count { it.kind == AprilTagBurstCodec.Kind.PARITY }.toString() else ready.packet.parityFrameCount.toString()),
                SignalOpticalScreenTransmitFields.FRAME_COUNT to (if (usingCompactGrid) units.size.toString() else ready.packet.frames.size.toString()),
                SignalOpticalScreenTransmitFields.SHARD_BYTES to (if (usingCompactGrid) (compact?.packet?.size ?: 0).toString() else profile.shardBytes.toString()),
                SignalOpticalScreenTransmitFields.SYMBOL_MS to dwellMs.toString(),
                SignalOpticalScreenTransmitFields.GRID_SIZE to "0",
                SignalOpticalScreenTransmitFields.CYCLES to cycles.toString(),
                SignalOpticalScreenTransmitFields.CHECKSUM_SHA256 to ready.envelope.expectedSha256,
                SignalOpticalScreenTransmitFields.STATUS to "sent",
                SignalOpticalScreenTransmitFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed optical transmission record."
            val result = signalResult(As100SignalOpticalScreenTransmitMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        DisposableEffect(activity, active) {
            val window = activity?.window
            val previous = window?.attributes?.screenBrightness
            if (window != null && active) window.attributes = window.attributes.apply { screenBrightness = 1f }
            onDispose { if (window != null && previous != null) window.attributes = window.attributes.apply { screenBrightness = previous } }
        }

        if (active) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    OpticalScreenDisplay(currentUnit, mode, Modifier.fillMaxSize())
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter).background(SignalBlack.copy(alpha = 0.93f)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = ::stop) { Text("Stop") }
                        Column(Modifier.weight(1f)) {
                            Text(if (mode == "grid") "APRILTAG BURST • ${profile.label.uppercase()}" else "SCREEN 4-PAM • ${profile.label.uppercase()}", color = SignalText, fontWeight = FontWeight.Bold)
                            Text(if (usingCompactGrid) "TAG ${currentUnit?.aprilTagId ?: "—"} • state ${unitIndex + 1}/${units.size} • cycle ${cycles + 1} • ${dwellMs} ms" else "MMS ${currentFrame}/${prepared?.packet?.frames?.size ?: 0} • cycle ${cycles + 1} • ${dwellMs} ms", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalOpticalScreenTransmitMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (active) stop() else start() }, enabled = active || payload.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (active) "Stop optical link" else "Start optical link") }

            SignalInstrumentPanel(
                kicker = "MMS/1 OPTICAL MODEM",
                title = if (mode == "grid") "Screen AprilTag Burst" else "Screen 4-PAM",
                accent = SignalCyan,
                badge = if (active) "On air" else if (cycles > 0) "Cycle complete" else "Ready"
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.7f).background(SignalBlack, RoundedCornerShape(18.dp))) {
                    OpticalScreenDisplay(currentUnit ?: units.firstOrNull(), mode, Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("PROFILE", profile.id.uppercase(), SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile(if (mode == "grid") "STATE" else "FRAME", if (mode == "grid") "${unitIndex + 1}/${units.size.coerceAtLeast(1)}" else if (prepared == null) "—" else "$currentFrame/${prepared.packet.frames.size}", SignalCyan, Modifier.weight(1f))
                    SignalTelemetryTile("DWELL", "$dwellMs ms", SignalCyan, Modifier.weight(1f))
                }
                prepared?.let { ready ->
                    if (usingCompactGrid) {
                        Text("${compact?.packet?.size ?: 0} compact bytes • ${units.size} self-registering tag states • CRC-32 + block parity", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
                        Text("Local SHA-256 fingerprint  ${ready.envelope.expectedSha256}", color = SignalMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "local SHA-256", ready.envelope.expectedSha256) })
                    } else {
                        Text("${ready.packet.dataShardCount} source + ${ready.packet.parityFrameCount} repair frames • ${ready.wire.wireBytes} wire bytes${if (ready.wire.compressed) " • compressed" else ""}", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256  ${ready.envelope.expectedSha256}", color = SignalMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "SHA-256", ready.envelope.expectedSha256) })
                    }
                    SignalProgressTrack((unitIndex + 1f) / units.size.coerceAtLeast(1).toFloat(), SignalCyan)
                }
            }

            Button(onClick = ::commit, enabled = cycles > 0, modifier = Modifier.fillMaxWidth()) { Text(if (committedResult == null) "Commit" else "Recommit") }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed screen optical transmission",
                    primaryLabel = "message",
                    primaryValue = committedFields[SignalOpticalScreenTransmitFields.RESULT].orEmpty(),
                    fields = listOf(
                        "Mode" to committedFields[SignalOpticalScreenTransmitFields.MODE].orEmpty(),
                        "Profile" to committedFields[SignalOpticalScreenTransmitFields.RANGE_PROFILE].orEmpty(),
                        "Message ID" to committedFields[SignalOpticalScreenTransmitFields.MESSAGE_ID].orEmpty(),
                        "SHA-256" to committedFields[SignalOpticalScreenTransmitFields.CHECKSUM_SHA256].orEmpty(),
                        "Cycles" to committedFields[SignalOpticalScreenTransmitFields.CYCLES].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { includeFullJson -> exportStatus = shareSignalText(androidContext, "Share optical transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") ?: "" },
                    onSave = { includeFullJson -> exportStatus = saveSignalText(androidContext, "screen_optical_transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") },
                    onDone = { includeFullJson -> finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "screen_optical_transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") } }
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Message & optical profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (context.settingShouldBeShown("payload")) OutlinedTextField(payload, { payload = it; invalidate() }, enabled = !active, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
                    if (context.settingShouldBeShown("mode")) {
                        Text("Physical mode", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = mode == "pam", enabled = !active, onClick = { mode = "pam"; invalidate() }, label = { Text("4-PAM • range") })
                            FilterChip(selected = mode == "grid", enabled = !active, onClick = { mode = "grid"; invalidate() }, label = { Text("AprilTag Burst") })
                        }
                    }
                    if (context.settingShouldBeShown("range_profile")) {
                        Text("Range / speed", style = MaterialTheme.typography.labelMedium)
                        RangeProfileChips(profile.id, !active) { profileId = it; invalidate() }
                        Text(if (mode == "grid") "Long holds each full-screen tag for 180 ms so a 30 fps camera gets about five observations per state. Balanced uses 120 ms; Fast 90 ms." else "Long deliberately uses slower symbols and smaller MMS shards. It is the default when distance matters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (context.settingShouldBeShown("robustness")) {
                        if (usingCompactGrid) {
                            Text("AprilTag Burst uses fixed CRC-32, one XOR repair state per 12 data symbols and repeated-cycle recovery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Reed–Solomon repair", style = MaterialTheme.typography.labelMedium)
                            RobustnessChips(robustness, !active) { robustness = it; invalidate() }
                        }
                    }
                    if (context.settingShouldBeShown("loop_mode")) {
                        Text("Transmission", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = loopMode == "continuous", enabled = !active, onClick = { loopMode = "continuous"; cycles = 0 }, label = { Text("Continuous") })
                            FilterChip(selected = loopMode == "count", enabled = !active, onClick = { loopMode = "count"; cycles = 0 }, label = { Text("Count") })
                        }
                    }
                    if (loopMode == "count" && context.settingShouldBeShown("repeat_count")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 5, 10).forEach { value -> FilterChip(selected = repeatCount == value, enabled = !active, onClick = { repeatCount = value; cycles = 0 }, label = { Text(value.toString()) }) }
                        }
                    }
                }
            }

            if (status.isNotBlank()) Text(status, color = if (error.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else SignalRed)
            if (error.isNotBlank()) Text(error, color = SignalRed)
        }
    }
}

object SignalOpticalScreenReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalOpticalScreenReceiveMethod.id
    override val title = "Screen optical receiver"
    override val description = "Recover range-oriented 4-PAM or full-screen AprilTag16h5 Burst with block and loop recovery."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        OpticalScreenReceiveUi(context, onConfirmed)
    }
}

@Composable
private fun OpticalScreenReceiveUi(context: CapabilityScreenContext, onConfirmed: (ExecutionResult) -> Unit) {
    val androidContext = LocalContext.current
    val settings = context.action.settings
    var mode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("mode", "grid").let { if (it == "pam") "pam" else "grid" }) }
    var profileId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("range_profile", "long")) }
    var zoomRatio by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("zoom_ratio", "4").toFloatOrNull()?.coerceAtLeast(1f) ?: 4f) }
    var roiModeId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("roi_mode", "focus")) }
    var exposure by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("exposure_reduction", "0.55").toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.55f) }
    var autoLock by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("auto_lock", "true").toBooleanStrictOrNull() ?: true) }
    var listening by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var frameStoreJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
    var physicalRejected by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    var resetGeneration by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Aim the camera at the transmitting screen, then start reception.") }
    var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var roiX by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.5f) }
    var roiY by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.5f) }
    var actualZoom by rememberSaveable(context.action.canonicalId) { mutableStateOf(zoomRatio) }
    var maxZoom by rememberSaveable(context.action.canonicalId) { mutableStateOf(zoomRatio) }
    var lockState by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalCameraLockState.MANUAL.name) }
    var latestLuma by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.0) }
    var latestPam by rememberSaveable(context.action.canonicalId) { mutableStateOf("—") }
    var latestConfidence by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.0) }
    var latestGridState by rememberSaveable(context.action.canonicalId) { mutableStateOf("—") }
    var compactText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var compactMessageId by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var compactStatesHeld by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    var compactStatesNeeded by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    val profile = SignalOpticalProfiles.byId(profileId)
    val roi = roiFromSetting(roiModeId, grid = mode == "grid")
    val pamDetector = remember(profile.id, resetGeneration) { AdaptivePam4Detector(if (profile.id == "long") 14.0 else 18.0) }
    val pamTimed = remember(profile.id, resetGeneration) { OpticalPamTimedDecoder(profile.pamSymbolMs) }
    val aprilTagCollector = remember(resetGeneration) { AprilTagBurstCollector() }
    val currentCollectorState = remember(frameStoreJson, physicalRejected) { signalCollectorFrom(frameStoreJson).state() }
    val currentEnvelope = remember(frameStoreJson) { decodedOpticalEnvelope(signalCollectorFrom(frameStoreJson).state()) }
    val cameraGrantedInitial = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var cameraGranted by remember { mutableStateOf(cameraGrantedInitial) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (granted) {
            listening = true
            status = "Camera ready. Acquiring optical preamble…"
            error = ""
        } else error = "Camera permission is required for optical reception."
    }
    SignalActiveSessionOrientationGuard(listening)

    fun acceptFrame(raw: String) {
        val parsed = SignalPacketCodec.parse(raw)
        if (!parsed.valid) {
            physicalRejected++
            return
        }
        val frames = storedSignalFrames(frameStoreJson)
        if (raw !in frames) frames += raw
        frameStoreJson = signalFrameStore(frames)
        val state = signalCollectorFrom(frameStoreJson).state()
        val envelope = decodedOpticalEnvelope(state)
        status = when {
            state.complete && envelope?.checksumVerified == true -> "Message recovered and SHA-256 verified${if (state.recoveredMissingSources > 0) "; ${state.recoveredMissingSources} missing shard(s) reconstructed" else ""}."
            state.complete -> "MMS/1 reconstructed, but the content envelope did not verify."
            state.requiredRank > 0 -> "Collecting ${state.rank} / ${state.requiredRank} independent MMS shards."
            else -> "Acquiring optical preamble…"
        }
        if (state.complete && envelope?.checksumVerified == true) listening = false
    }

    fun start() {
        if (!cameraGranted) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        listening = true
        error = ""
        status = if (mode == "grid" && compactStatesHeld > 0) "Listening — retained $compactStatesHeld AprilTag data symbol(s) across loops." else if (currentCollectorState.requiredRank > 0) "Listening — retained ${currentCollectorState.rank} / ${currentCollectorState.requiredRank} MMS shards." else "Listening for ${if (mode == "grid") "AprilTag START" else "4-PAM preamble"}…"
    }

    fun stop() {
        listening = false
        status = if (currentCollectorState.requiredRank > 0) "Paused at ${currentCollectorState.rank} / ${currentCollectorState.requiredRank} shards; valid frames are retained." else "Paused."
    }

    fun invalidate() {
        listening = false
        frameStoreJson = "[]"
        physicalRejected = 0
        resetGeneration++
        latestPam = "—"
        latestGridState = "—"
        latestConfidence = 0.0
        compactText = ""
        compactMessageId = ""
        compactStatesHeld = 0
        compactStatesNeeded = 0
        aprilTagCollector.reset()
        status = "Receiver settings changed; working evidence was cleared. Any committed result remains frozen."
        error = ""
    }

    fun commit() {
        val compactEnvelope = compactText.takeIf { it.isNotBlank() }?.let { SignalContentEnvelope.decode(SignalContentEnvelope.encodeText(it)) }
        if (mode == "grid" && compactEnvelope?.text != null) {
            listening = false
            val values = mapOf(
                SignalOpticalScreenReceiveFields.RESULT to compactEnvelope.text,
                SignalOpticalScreenReceiveFields.MESSAGE_ID to compactMessageId,
                SignalOpticalScreenReceiveFields.MODE to mode,
                SignalOpticalScreenReceiveFields.RANGE_PROFILE to profile.id,
                SignalOpticalScreenReceiveFields.FRAMES_ACCEPTED to compactStatesHeld.toString(),
                SignalOpticalScreenReceiveFields.FRAMES_REJECTED to (physicalRejected + aprilTagCollector.rejectedTags).toString(),
                SignalOpticalScreenReceiveFields.RECOVERED_MISSING to aprilTagCollector.snapshot().recovered.toString(),
                SignalOpticalScreenReceiveFields.CRC_VERIFIED to "true",
                SignalOpticalScreenReceiveFields.CHECKSUM_SHA256 to compactEnvelope.expectedSha256,
                SignalOpticalScreenReceiveFields.CHECKSUM_VERIFIED to "false",
                SignalOpticalScreenReceiveFields.ZOOM_RATIO to actualZoom.toString(),
                SignalOpticalScreenReceiveFields.STATUS to "received",
                SignalOpticalScreenReceiveFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed CRC-verified compact optical reception."
            val result = signalResult(As100SignalOpticalScreenReceiveMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
            return
        }
        val state = signalCollectorFrom(frameStoreJson).state()
        val envelope = decodedOpticalEnvelope(state)
        if (!state.complete || envelope?.checksumVerified != true || envelope.text == null) {
            error = "The optical message is not yet completely recoverable and verified."
            return
        }
        listening = false
        val values = mapOf(
            SignalOpticalScreenReceiveFields.RESULT to envelope.text,
            SignalOpticalScreenReceiveFields.MESSAGE_ID to state.messageId.orEmpty(),
            SignalOpticalScreenReceiveFields.MODE to mode,
            SignalOpticalScreenReceiveFields.RANGE_PROFILE to profile.id,
            SignalOpticalScreenReceiveFields.FRAMES_ACCEPTED to state.acceptedFrames.toString(),
            SignalOpticalScreenReceiveFields.FRAMES_REJECTED to (state.rejectedFrames + physicalRejected + pamTimed.telemetry().lockLosses).toString(),
            SignalOpticalScreenReceiveFields.RECOVERED_MISSING to state.recoveredMissingSources.toString(),
            SignalOpticalScreenReceiveFields.CRC_VERIFIED to state.messageCrcVerified.toString(),
            SignalOpticalScreenReceiveFields.CHECKSUM_SHA256 to envelope.expectedSha256,
            SignalOpticalScreenReceiveFields.CHECKSUM_VERIFIED to envelope.checksumVerified.toString(),
            SignalOpticalScreenReceiveFields.ZOOM_RATIO to actualZoom.toString(),
            SignalOpticalScreenReceiveFields.STATUS to "received",
            SignalOpticalScreenReceiveFields.ERROR to ""
        )
        committedJson = fieldsJson(values)
        status = "Committed verified optical reception."
        val result = signalResult(As100SignalOpticalScreenReceiveMethod, context, values)
        if (context.submitsImmediately) onConfirmed(result)
    }

    LaunchedEffect(mode, profileId, zoomRatio, roiModeId, exposure, autoLock) {
        context.onSettingsChanged(
            mapOf(
                "mode" to mode,
                "range_profile" to profileId,
                "zoom_ratio" to zoomRatio.toString(),
                "roi_mode" to roiModeId,
                "exposure_reduction" to exposure.toString(),
                "auto_lock" to autoLock.toString()
            )
        )
    }

    val committedFields = fieldsFromJson(committedJson)
    val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalOpticalScreenReceiveMethod, context, it) } }
    val committedFullJson = remember(committedJson) { fullJson(committedResult) }
    val compactEnvelopeForDisplay = compactText.takeIf { mode == "grid" && it.isNotBlank() }
        ?.let { SignalContentEnvelope.decode(SignalContentEnvelope.encodeText(it)) }
    val envelope = compactEnvelopeForDisplay ?: currentEnvelope
    val compactVerified = mode == "grid" && compactText.isNotBlank()

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { if (listening) stop() else start() }, modifier = Modifier.fillMaxWidth()) { Text(if (listening) "Pause receiver" else "Start receiver") }

        SignalInstrumentPanel(
            kicker = "MMS/1 OPTICAL RECEIVER",
            title = if (mode == "grid") "Screen AprilTag Burst" else "Screen 4-PAM",
            accent = SignalCyan,
            badge = when { compactVerified -> "CRC verified"; envelope?.checksumVerified == true -> "Verified"; listening -> "Live"; else -> "Ready" }
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.25f).background(SignalBlack, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                if (listening && cameraGranted) {
                    if (mode == "grid") {
                        SignalCameraAprilTagPreview(
                            modifier = Modifier.fillMaxSize(),
                            zoomRatio = zoomRatio,
                            roiMode = roi,
                            roiCenterX = roiX,
                            roiCenterY = roiY,
                            searchGeneration = resetGeneration,
                            exposureReduction = exposure,
                            onRoiMoved = { x, y -> roiX = x; roiY = y },
                            onCameraInfo = { info -> actualZoom = info.actualZoomRatio; maxZoom = info.maxZoomRatio },
                            onSample = { sample ->
                                sample.tagId?.let { tagId ->
                                    latestGridState = tagId.toString()
                                    val snapshot = aprilTagCollector.offer(tagId)
                                    compactStatesHeld = snapshot.symbolsHeld
                                    compactStatesNeeded = snapshot.symbolsNeeded ?: 0
                                    snapshot.decoded?.let { decoded ->
                                        compactText = decoded.text
                                        compactMessageId = decoded.messageId
                                        status = "AprilTag Burst message recovered and CRC-32 verified${if (snapshot.recovered > 0) "; ${snapshot.recovered} erasure(s) repaired" else ""}."
                                        listening = false
                                    }
                                }
                            },
                            onError = { error = it; listening = false }
                        )
                    } else {
                        SignalCameraLumaPreview(
                            modifier = Modifier.fillMaxSize(),
                            zoomRatio = zoomRatio,
                            roiMode = roi,
                            roiCenterX = roiX,
                            roiCenterY = roiY,
                            autoLock = autoLock,
                            lockState = SignalCameraLockState.valueOf(lockState),
                            searchGeneration = resetGeneration,
                            exposureReduction = exposure,
                            opticalProfile = "screen",
                            onRoiMoved = { x, y -> roiX = x; roiY = y },
                            onCameraInfo = { info -> actualZoom = info.actualZoomRatio; maxZoom = info.maxZoomRatio },
                            onSample = { sample ->
                                latestLuma = sample.luma
                                lockState = sample.lockState.name
                                val detected = pamDetector.feed(sample.luma)
                                latestPam = detected.level?.toString() ?: "—"
                                latestConfidence = detected.confidence
                                pamTimed.feed(sample.timestampMs, detected.level).forEach(::acceptFrame)
                            },
                            onError = { error = it; listening = false }
                        )
                    }
                } else Text(if (compactVerified) "CRC-32 VERIFIED" else if (envelope?.checksumVerified == true) "SHA-256 VERIFIED" else "CAMERA PAUSED", color = if (compactVerified || envelope?.checksumVerified == true) SignalGreen else SignalMuted, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalTelemetryTile(if (mode == "grid") "SYMBOLS" else "MMS", if (mode == "grid") { if (compactStatesNeeded > 0) "$compactStatesHeld/$compactStatesNeeded" else compactStatesHeld.toString() } else if (currentCollectorState.requiredRank > 0) "${currentCollectorState.rank}/${currentCollectorState.requiredRank}" else "—", SignalCyan, Modifier.weight(1f))
                SignalTelemetryTile(if (mode == "grid") "TAG" else "PAM", if (mode == "grid") latestGridState else latestPam, SignalCyan, Modifier.weight(1f))
                SignalTelemetryTile("ZOOM", "${"%.1f".format(actualZoom)}×", SignalCyan, Modifier.weight(1f))
            }
            if (mode == "pam") Text("Luma ${"%.1f".format(latestLuma)} • classifier confidence ${"%.0f".format(latestConfidence * 100)}% • ${pamTimed.telemetry().emittedSymbols} clocked symbols", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
            else Text("AprilTag16h5 Burst • full-screen self-registering symbols • stable detection ×2 • rejected ${aprilTagCollector.rejectedTags}", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
            if (mode == "grid" && compactStatesNeeded > 0) SignalProgressTrack((compactStatesHeld.toFloat() / compactStatesNeeded.toFloat()).coerceIn(0f, 1f), SignalCyan)
            else if (currentCollectorState.requiredRank > 0) SignalProgressTrack(currentCollectorState.rank.toFloat() / currentCollectorState.requiredRank.toFloat(), SignalCyan)
            envelope?.let { decoded ->
                Text(decoded.text.orEmpty(), color = SignalText, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { copySignalValue(androidContext, "received message", decoded.text.orEmpty()) })
                if (compactVerified) {
                    Text("CRC-32 verified • local SHA-256 fingerprint ${decoded.expectedSha256}", color = SignalGreen, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "local SHA-256", decoded.expectedSha256) })
                } else {
                    Text("SHA-256  ${decoded.expectedSha256}", color = if (decoded.checksumVerified) SignalGreen else SignalRed, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "SHA-256", decoded.expectedSha256) })
                }
            }
        }

        Button(onClick = ::commit, enabled = compactVerified || envelope?.checksumVerified == true, modifier = Modifier.fillMaxWidth()) { Text(if (committedResult == null) "Commit" else "Recommit") }
        OutlinedButton(onClick = { invalidate(); status = "Receiver reset." }, modifier = Modifier.fillMaxWidth()) { Text("Reset working reception") }

        if (committedResult != null && !context.submitsImmediately) {
            SignalCommittedCard(
                title = "Committed screen optical reception",
                primaryLabel = "message",
                primaryValue = committedFields[SignalOpticalScreenReceiveFields.RESULT].orEmpty(),
                fields = listOf(
                    "Mode" to committedFields[SignalOpticalScreenReceiveFields.MODE].orEmpty(),
                    "Profile" to committedFields[SignalOpticalScreenReceiveFields.RANGE_PROFILE].orEmpty(),
                    "SHA-256 verified" to committedFields[SignalOpticalScreenReceiveFields.CHECKSUM_VERIFIED].orEmpty(),
                    "Recovered missing" to committedFields[SignalOpticalScreenReceiveFields.RECOVERED_MISSING].orEmpty(),
                    "Message ID" to committedFields[SignalOpticalScreenReceiveFields.MESSAGE_ID].orEmpty()
                ),
                status = exportStatus,
                onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                onShare = { includeFullJson -> exportStatus = shareSignalText(androidContext, "Share optical reception", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") ?: "" },
                onSave = { includeFullJson -> exportStatus = saveSignalText(androidContext, "screen_optical_reception", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") },
                onDone = { includeFullJson -> finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "screen_optical_reception", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") } }
            )
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Receiver tuning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (context.settingShouldBeShown("mode")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = mode == "pam", enabled = !listening, onClick = { mode = "pam"; invalidate() }, label = { Text("4-PAM • range") })
                        FilterChip(selected = mode == "grid", enabled = !listening, onClick = { mode = "grid"; invalidate() }, label = { Text("AprilTag Burst") })
                    }
                }
                if (context.settingShouldBeShown("range_profile")) RangeProfileChips(profile.id, !listening) { profileId = it; invalidate() }
                if (context.settingShouldBeShown("zoom_ratio")) {
                    Text("Optical zoom • camera max ${"%.1f".format(maxZoom)}×", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1f, 2f, 4f, 8f).forEach { value -> FilterChip(selected = zoomRatio == value, enabled = !listening, onClick = { zoomRatio = value; invalidate() }, label = { Text("${value.roundToInt()}×") }) }
                    }
                }
                if (context.settingShouldBeShown("roi_mode")) {
                    Text(if (mode == "grid") "AprilTag search area" else "Target region", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val choices = if (mode == "grid") listOf("full", "focus") else listOf("full", "focus", "pinpoint")
                        choices.forEach { item -> FilterChip(selected = roiModeId == item, enabled = !listening, onClick = { roiModeId = item; invalidate() }, label = { Text(item.replaceFirstChar { it.uppercase() }) }) }
                    }
                }
                if (mode == "pam" && context.settingShouldBeShown("auto_lock")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = autoLock, enabled = !listening, onClick = { autoLock = true; invalidate() }, label = { Text("Auto target") })
                        FilterChip(selected = !autoLock, enabled = !listening, onClick = { autoLock = false; invalidate() }, label = { Text("Manual") })
                    }
                }
                if (context.settingShouldBeShown("exposure_reduction")) {
                    Text("Underexposure", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.25f, 0.55f, 0.75f, 0.90f).forEach { value -> FilterChip(selected = exposure == value, enabled = !listening, onClick = { exposure = value; invalidate() }, label = { Text("${(value * 100).roundToInt()}%") }) }
                    }
                }
                Text(if (mode == "grid") "AprilTag Burst carries one complete self-registering fiducial per state. The cyan box is only a search area: rotation, perspective and hand motion are resolved from the tag itself rather than from a fixed sampling lattice." else "For long range, zoom until the source is comfortably contained rather than filling the reticle. Underexposure preserves modulation in a small bright target.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error.isNotBlank()) Text(error, color = SignalRed)
    }
}

object SignalOpticalTorchTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalOpticalTorchTransmitMethod.id
    override val title = "Torch PPM transmitter"
    override val description = "Transmit compact short text as single-flash 8-PPM with cadence preamble and parity stripes for long-range point-source reception."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val settings = context.action.settings
        val torch = remember(androidContext) { SignalTorchController(androidContext) }
        var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("payload", TORCH_EXAMPLE)) }
        var profileId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("range_profile", "long")) }
        var robustness by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("robustness", "robust")) }
        var loopMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("loop_mode", "continuous")) }
        var repeatCount by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("repeat_count", "3").toIntOrNull()?.coerceIn(1, 10) ?: 3) }
        var messageId by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPacketCodec.newMessageId()) }
        var sending by remember { mutableStateOf(false) }
        var cycles by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var symbolIndex by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Prepare a short message, then start the torch optical link.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val profile = SignalOpticalProfiles.byId(profileId)
        val parityStripes = when (profile.id) { "long" -> 6; "balanced" -> 4; else -> 2 }
        val cameraGrantedInitial = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        var cameraGranted by remember { mutableStateOf(cameraGrantedInitial) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
            if (!granted) error = "Camera permission is required to control the rear torch."
        }
        SignalActiveSessionOrientationGuard(sending)

        fun invalidate() {
            sending = false
            torch.off()
            cycles = 0
            symbolIndex = 0
            messageId = SignalPacketCodec.newMessageId()
            status = "Settings changed; complete a new torch cycle before Commit."
            error = ""
        }

        val compactAttempt = remember(payload, messageId) { runCatching { prepareCompactOpticalText(payload, messageId) } }
        val compact = compactAttempt.getOrNull()
        val compactPreparationError = compactAttempt.exceptionOrNull()?.message.orEmpty()
        val transmission = remember(compact?.packet?.contentHashCode(), profile.id) { compact?.let { Torch8PpmCodec.encode(it.packet, parityStripes) } }
        val cycleFlashCount = transmission?.flashCount ?: 0
        val cycleDurationMs = cycleFlashCount.toLong() * Torch8PpmCodec.SYMBOL_SLOTS.toLong() * profile.torchSlotMs.toLong()

        fun start() {
            if (!cameraGranted) { permission.launch(Manifest.permission.CAMERA); return }
            if (!torch.available) { error = "No controllable rear torch is available on this device."; return }
            if (compact == null || transmission == null) {
                error = compactAttempt.exceptionOrNull()?.message ?: "Torch payload could not be compactly encoded."
                return
            }
            symbolIndex = 0
            cycles = 0
            sending = true
            status = "Torch 8-PPM • ${profile.label} • $cycleFlashCount flashes/cycle."
            error = ""
        }

        fun stop() {
            sending = false
            torch.off()
            status = if (cycles > 0) "Stopped after $cycles complete torch cycle(s)." else "Stopped before a complete cycle."
        }

        LaunchedEffect(sending, transmission, profile.id, loopMode, repeatCount) {
            if (!sending || transmission == null) return@LaunchedEffect
            val slotMs = profile.torchSlotMs.toLong()
            val periodMs = slotMs * Torch8PpmCodec.SYMBOL_SLOTS.toLong()
            val pulseMs = profile.torchPulseMs.toLong().coerceAtMost(slotMs - 2L).coerceAtLeast(4L)
            suspend fun emitWindow(position: Int) {
                val before = position.coerceIn(0, Torch8PpmCodec.SYMBOL_SLOTS - 1).toLong() * slotMs
                if (before > 0) delay(before)
                runCatching { torch.set(true) }.onFailure { error = it.message ?: "Torch failed."; sending = false }
                if (!sending) return
                delay(pulseMs)
                torch.off()
                val after = periodMs - before - pulseMs
                if (after > 0) delay(after)
            }
            try {
                while (sending) {
                    symbolIndex = 0
                    repeat(Torch8PpmCodec.PREAMBLE_FLASHES) {
                        if (!sending) return@repeat
                        emitWindow(Torch8PpmCodec.SYNC_SLOT)
                    }
                    transmission.symbols.forEachIndexed { index, symbol ->
                        if (!sending) return@forEachIndexed
                        symbolIndex = index + 1
                        emitWindow(symbol)
                    }
                    if (!sending) break
                    cycles++
                    status = "Torch cycle $cycles complete • $cycleFlashCount flashes${if (loopMode == "continuous") " — looping" else ""}."
                    if (loopMode != "continuous" && cycles >= repeatCount) sending = false
                }
            } finally {
                torch.off()
            }
        }

        fun commit() {
            val ready = compact
            val tx = transmission
            if (ready == null || tx == null || cycles <= 0) { error = "Complete at least one full torch cycle before Commit."; return }
            sending = false
            torch.off()
            val values = mapOf(
                SignalOpticalTorchTransmitFields.RESULT to ready.envelope.text.orEmpty(),
                SignalOpticalTorchTransmitFields.PAYLOAD to ready.envelope.text.orEmpty(),
                SignalOpticalTorchTransmitFields.MESSAGE_ID to ready.messageId,
                SignalOpticalTorchTransmitFields.RANGE_PROFILE to profile.id,
                SignalOpticalTorchTransmitFields.DATA_SHARDS to "1",
                SignalOpticalTorchTransmitFields.PARITY_FRAMES to tx.paritySymbols.size.toString(),
                SignalOpticalTorchTransmitFields.FRAME_COUNT to "1",
                SignalOpticalTorchTransmitFields.SHARD_BYTES to ready.packet.size.toString(),
                SignalOpticalTorchTransmitFields.SLOT_MS to profile.torchSlotMs.toString(),
                SignalOpticalTorchTransmitFields.PULSE_MS to profile.torchPulseMs.toString(),
                SignalOpticalTorchTransmitFields.CYCLES to cycles.toString(),
                SignalOpticalTorchTransmitFields.CHECKSUM_SHA256 to ready.envelope.expectedSha256,
                SignalOpticalTorchTransmitFields.STATUS to "sent",
                SignalOpticalTorchTransmitFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed torch optical transmission record."
            val result = signalResult(As100SignalOpticalTorchTransmitMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        LaunchedEffect(payload, profileId, robustness, loopMode, repeatCount) {
            context.onSettingsChanged(mapOf("payload" to payload, "range_profile" to profileId, "robustness" to robustness, "loop_mode" to loopMode, "repeat_count" to repeatCount.toString()))
        }
        DisposableEffect(Unit) { onDispose { torch.off() } }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalOpticalTorchTransmitMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (sending) stop() else start() }, enabled = compact != null, modifier = Modifier.fillMaxWidth()) { Text(if (sending) "Stop torch modem" else "Start torch modem") }
            SignalInstrumentPanel(
                kicker = "COMPACT POINT-SOURCE OPTICAL",
                title = "Torch 8-PPM",
                accent = SignalAmber,
                badge = if (sending) "Flashing" else if (cycles > 0) "Cycle complete" else "Ready"
            ) {
                Box(Modifier.fillMaxWidth().height(170.dp).background(SignalBlack, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (sending) "●" else "○", color = if (sending) SignalAmber else SignalMuted, style = MaterialTheme.typography.displayLarge)
                        Text("ONE FLASH / 3 BITS • CLOCKED BY PREAMBLE", color = SignalMuted, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("PROFILE", profile.id.uppercase(), SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("FLASHES", if (cycleFlashCount > 0) cycleFlashCount.toString() else "—", SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("CYCLE", if (cycleDurationMs > 0) "${"%.1f".format(cycleDurationMs / 1000.0)} s" else "—", SignalAmber, Modifier.weight(1f))
                }
                compact?.let { ready ->
                    Text("${ready.packet.size} compact bytes • ${transmission?.dataSymbols?.size ?: 0} data symbols + ${transmission?.paritySymbols?.size ?: 0} parity • ${Torch8PpmCodec.PREAMBLE_FLASHES} sync flashes", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
                    Text("SHA-256 fingerprint  ${ready.envelope.expectedSha256}", color = SignalMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "SHA-256", ready.envelope.expectedSha256) })
                }
                if (sending) Text("Data symbol $symbolIndex/${transmission?.symbols?.size ?: 0}", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = ::commit, enabled = cycles > 0, modifier = Modifier.fillMaxWidth()) { Text(if (committedResult == null) "Commit" else "Recommit") }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed torch optical transmission",
                    primaryLabel = "message",
                    primaryValue = committedFields[SignalOpticalTorchTransmitFields.RESULT].orEmpty(),
                    fields = listOf(
                        "Profile" to committedFields[SignalOpticalTorchTransmitFields.RANGE_PROFILE].orEmpty(),
                        "Message ID" to committedFields[SignalOpticalTorchTransmitFields.MESSAGE_ID].orEmpty(),
                        "SHA-256" to committedFields[SignalOpticalTorchTransmitFields.CHECKSUM_SHA256].orEmpty(),
                        "Cycles" to committedFields[SignalOpticalTorchTransmitFields.CYCLES].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { includeFullJson -> exportStatus = shareSignalText(androidContext, "Share torch optical transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") ?: "" },
                    onSave = { includeFullJson -> exportStatus = saveSignalText(androidContext, "torch_optical_transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") },
                    onDone = { includeFullJson -> finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "torch_optical_transmission", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") } }
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Message & pulse profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (context.settingShouldBeShown("payload")) OutlinedTextField(payload, { payload = it; invalidate() }, enabled = !sending, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
                    if (context.settingShouldBeShown("range_profile")) RangeProfileChips(profile.id, !sending) { profileId = it; invalidate() }
                    Text("Long prioritises camera acquisition margin. Each data window contains exactly one flash in one of eight timing positions; six parity stripes can repair one missing symbol per stripe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!OpticalCompactTextCodec.canEncode(payload)) Text("Compact torch text supports letters, spaces and . , ? / -. Use the screen/MMS modes for other characters or larger payloads.", color = SignalRed, style = MaterialTheme.typography.bodySmall)
                    if (context.settingShouldBeShown("robustness")) Text("The legacy robustness setting is retained for preset compatibility; compact 8-PPM redundancy is profile-defined.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (context.settingShouldBeShown("loop_mode")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = loopMode == "continuous", enabled = !sending, onClick = { loopMode = "continuous"; cycles = 0 }, label = { Text("Continuous") })
                            FilterChip(selected = loopMode == "count", enabled = !sending, onClick = { loopMode = "count"; cycles = 0 }, label = { Text("Count") })
                        }
                    }
                    if (loopMode == "count" && context.settingShouldBeShown("repeat_count")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 5, 10).forEach { value -> FilterChip(selected = repeatCount == value, enabled = !sending, onClick = { repeatCount = value; cycles = 0 }, label = { Text(value.toString()) }) }
                        }
                    }
                }
            }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error.isNotBlank()) Text(error, color = SignalRed)
        }
    }
}

object SignalOpticalTorchReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalOpticalTorchReceiveMethod.id
    override val title = "Torch PPM receiver"
    override val description = "Recover compact 8-PPM point-source text from pulse timing; amplitude only detects the flash edge."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val settings = context.action.settings
        var profileId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("range_profile", "long")) }
        var zoomRatio by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("zoom_ratio", "4").toFloatOrNull()?.coerceAtLeast(1f) ?: 4f) }
        var roiModeId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("roi_mode", "pinpoint")) }
        var exposure by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("exposure_reduction", "0.90").toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.90f) }
        var autoLock by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("auto_lock", "true").toBooleanStrictOrNull() ?: true) }
        var listening by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var physicalRejected by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var pulses by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var resetGeneration by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Aim at the remote torch, then start reception.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var roiX by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.5f) }
        var roiY by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.5f) }
        var actualZoom by rememberSaveable(context.action.canonicalId) { mutableStateOf(zoomRatio) }
        var maxZoom by rememberSaveable(context.action.canonicalId) { mutableStateOf(zoomRatio) }
        var lockState by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalCameraLockState.MANUAL.name) }
        var luma by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.0) }
        var detectorSpan by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.0) }
        var previousHigh by remember(profileId, resetGeneration) { mutableStateOf<Boolean?>(null) }
        var decodedText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var messageId by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var symbolsHeld by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var symbolsNeeded by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var cyclesSeen by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var recovered by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        val profile = SignalOpticalProfiles.byId(profileId)
        val parityStripes = when (profile.id) { "long" -> 6; "balanced" -> 4; else -> 2 }
        val levelDetector = remember(profile.id, resetGeneration) { AdaptiveLevelDetector(if (profile.id == "long") 8.0 else 12.0) }
        val pulseDecoder = remember(profile.id, resetGeneration) { Torch8PpmPulseDecoder(profile.torchSlotMs) }
        val compactCollector = remember(profile.id, resetGeneration) { Torch8PpmCompactCollector(parityStripes) }
        val cameraGrantedInitial = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        var cameraGranted by remember { mutableStateOf(cameraGrantedInitial) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
            if (granted) { listening = true; status = "Camera ready. Acquiring seven-flash 8-PPM clock…"; error = "" }
            else error = "Camera permission is required for torch optical reception."
        }
        SignalActiveSessionOrientationGuard(listening)

        fun updateSnapshot(snapshot: Torch8PpmCompactCollector.Snapshot) {
            symbolsHeld = snapshot.symbolsHeld
            symbolsNeeded = snapshot.symbolsNeeded ?: 0
            cyclesSeen = snapshot.cycles
            recovered = snapshot.recovered
            snapshot.decoded?.let { decoded ->
                decodedText = decoded.text
                messageId = decoded.messageId
                status = "Compact torch message recovered and CRC-32 verified${if (snapshot.recovered > 0) "; ${snapshot.recovered} missing symbol(s) repaired" else ""}."
                listening = false
            }
        }

        fun start() {
            if (!cameraGranted) { permission.launch(Manifest.permission.CAMERA); return }
            listening = true
            error = ""
            status = if (symbolsHeld > 0) "Listening — retained $symbolsHeld compact symbols across cycles." else "Listening for seven-flash torch clock…"
        }

        fun stop() {
            listening = false
            status = if (symbolsHeld > 0) "Paused with $symbolsHeld observed symbols retained." else "Paused."
        }

        fun invalidate() {
            listening = false
            physicalRejected = 0
            pulses = 0
            resetGeneration++
            previousHigh = null
            decodedText = ""
            messageId = ""
            symbolsHeld = 0
            symbolsNeeded = 0
            cyclesSeen = 0
            recovered = 0
            pulseDecoder.reset()
            compactCollector.reset()
            status = "Receiver settings changed; working evidence was cleared. Any committed result remains frozen."
            error = ""
        }

        fun commit() {
            if (decodedText.isBlank()) { error = "The compact torch message is not yet CRC-verified."; return }
            val envelope = requireNotNull(SignalContentEnvelope.decode(SignalContentEnvelope.encodeText(decodedText)))
            listening = false
            val values = mapOf(
                SignalOpticalTorchReceiveFields.RESULT to decodedText,
                SignalOpticalTorchReceiveFields.MESSAGE_ID to messageId,
                SignalOpticalTorchReceiveFields.RANGE_PROFILE to profile.id,
                SignalOpticalTorchReceiveFields.FRAMES_ACCEPTED to symbolsHeld.toString(),
                SignalOpticalTorchReceiveFields.FRAMES_REJECTED to physicalRejected.toString(),
                SignalOpticalTorchReceiveFields.RECOVERED_MISSING to recovered.toString(),
                SignalOpticalTorchReceiveFields.CRC_VERIFIED to "true",
                SignalOpticalTorchReceiveFields.CHECKSUM_SHA256 to envelope.expectedSha256,
                SignalOpticalTorchReceiveFields.CHECKSUM_VERIFIED to "true",
                SignalOpticalTorchReceiveFields.ZOOM_RATIO to actualZoom.toString(),
                SignalOpticalTorchReceiveFields.PULSES to pulses.toString(),
                SignalOpticalTorchReceiveFields.STATUS to "received",
                SignalOpticalTorchReceiveFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed CRC-verified torch optical reception."
            val result = signalResult(As100SignalOpticalTorchReceiveMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        LaunchedEffect(profileId, zoomRatio, roiModeId, exposure, autoLock) {
            context.onSettingsChanged(mapOf("range_profile" to profileId, "zoom_ratio" to zoomRatio.toString(), "roi_mode" to roiModeId, "exposure_reduction" to exposure.toString(), "auto_lock" to autoLock.toString()))
        }

        val envelope = decodedText.takeIf { it.isNotBlank() }?.let { SignalContentEnvelope.decode(SignalContentEnvelope.encodeText(it)) }
        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalOpticalTorchReceiveMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (listening) stop() else start() }, modifier = Modifier.fillMaxWidth()) { Text(if (listening) "Pause receiver" else "Start receiver") }
            SignalInstrumentPanel(
                kicker = "COMPACT POINT-SOURCE RECEIVER",
                title = "Torch 8-PPM",
                accent = SignalAmber,
                badge = when { decodedText.isNotBlank() -> "Verified"; listening -> "Live"; else -> "Ready" }
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.25f).background(SignalBlack, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    if (listening && cameraGranted) {
                        SignalCameraLumaPreview(
                            modifier = Modifier.fillMaxSize(),
                            zoomRatio = zoomRatio,
                            roiMode = roiFromSetting(roiModeId),
                            roiCenterX = roiX,
                            roiCenterY = roiY,
                            autoLock = autoLock,
                            lockState = SignalCameraLockState.valueOf(lockState),
                            searchGeneration = resetGeneration,
                            exposureReduction = exposure,
                            opticalProfile = "torch",
                            onRoiMoved = { x, y -> roiX = x; roiY = y },
                            onCameraInfo = { info -> actualZoom = info.actualZoomRatio; maxZoom = info.maxZoomRatio },
                            onSample = { sample ->
                                luma = sample.luma
                                lockState = sample.lockState.name
                                val detection = levelDetector.feed(sample.luma)
                                detectorSpan = if (detection.span.isFinite()) detection.span else 0.0
                                val high = detection.state
                                if (high == true && previousHigh != true) {
                                    pulses++
                                    val events = pulseDecoder.feedPulse(sample.timestampMs)
                                    if (events.isEmpty() && cyclesSeen > 0) physicalRejected++
                                    events.forEach { event ->
                                        if (event.cycleStarted) {
                                            compactCollector.startCycle()
                                            cyclesSeen = compactCollector.snapshot().cycles
                                            status = "8-PPM clock locked • cycle $cyclesSeen."
                                        }
                                        if (event.symbolIndex != null && event.slot != null) updateSnapshot(compactCollector.offer(event.symbolIndex, event.slot))
                                    }
                                }
                                previousHigh = high
                            },
                            onError = { error = it; listening = false }
                        )
                    } else Text(if (decodedText.isNotBlank()) "CRC-32 VERIFIED" else "CAMERA PAUSED", color = if (decodedText.isNotBlank()) SignalGreen else SignalMuted, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("SYMBOLS", if (symbolsNeeded > 0) "$symbolsHeld/$symbolsNeeded" else symbolsHeld.toString(), SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("PULSES", pulses.toString(), SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("CYCLES", cyclesSeen.toString(), SignalAmber, Modifier.weight(1f))
                }
                Text("Luma ${"%.1f".format(luma)} • adaptive span ${"%.1f".format(detectorSpan)} • ${profile.torchSlotMs} ms timing slots • parity repaired $recovered", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
                if (symbolsNeeded > 0) SignalProgressTrack((symbolsHeld.toFloat() / symbolsNeeded.toFloat()).coerceIn(0f, 1f), SignalAmber)
                envelope?.let { decoded ->
                    Text(decodedText, color = SignalText, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { copySignalValue(androidContext, "received message", decodedText) })
                    Text("SHA-256 fingerprint  ${decoded.expectedSha256}", color = SignalGreen, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "SHA-256", decoded.expectedSha256) })
                }
            }

            Button(onClick = ::commit, enabled = decodedText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (committedResult == null) "Commit" else "Recommit") }
            OutlinedButton(onClick = { invalidate(); status = "Receiver reset." }, modifier = Modifier.fillMaxWidth()) { Text("Reset working reception") }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed torch optical reception",
                    primaryLabel = "message",
                    primaryValue = committedFields[SignalOpticalTorchReceiveFields.RESULT].orEmpty(),
                    fields = listOf(
                        "Profile" to committedFields[SignalOpticalTorchReceiveFields.RANGE_PROFILE].orEmpty(),
                        "CRC verified" to committedFields[SignalOpticalTorchReceiveFields.CRC_VERIFIED].orEmpty(),
                        "Recovered missing" to committedFields[SignalOpticalTorchReceiveFields.RECOVERED_MISSING].orEmpty(),
                        "Pulses" to committedFields[SignalOpticalTorchReceiveFields.PULSES].orEmpty(),
                        "Message ID" to committedFields[SignalOpticalTorchReceiveFields.MESSAGE_ID].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { includeFullJson -> exportStatus = shareSignalText(androidContext, "Share torch optical reception", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") ?: "" },
                    onSave = { includeFullJson -> exportStatus = saveSignalText(androidContext, "torch_optical_reception", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") },
                    onDone = { includeFullJson -> finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "torch_optical_reception", committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" }, if (includeFullJson) committedFullJson else "") } }
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Long-range camera acquisition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (context.settingShouldBeShown("range_profile")) RangeProfileChips(profile.id, !listening) { profileId = it; invalidate() }
                    if (context.settingShouldBeShown("zoom_ratio")) {
                        Text("Optical zoom • camera max ${"%.1f".format(maxZoom)}×", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1f, 2f, 4f, 8f).forEach { value -> FilterChip(selected = zoomRatio == value, enabled = !listening, onClick = { zoomRatio = value; invalidate() }, label = { Text("${value.roundToInt()}×") }) }
                        }
                    }
                    if (context.settingShouldBeShown("roi_mode")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("full", "focus", "pinpoint").forEach { item -> FilterChip(selected = roiModeId == item, enabled = !listening, onClick = { roiModeId = item; invalidate() }, label = { Text(item.replaceFirstChar { it.uppercase() }) }) }
                        }
                    }
                    if (context.settingShouldBeShown("auto_lock")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = autoLock, enabled = !listening, onClick = { autoLock = true; invalidate() }, label = { Text("Auto target") })
                            FilterChip(selected = !autoLock, enabled = !listening, onClick = { autoLock = false; invalidate() }, label = { Text("Manual") })
                        }
                    }
                    if (context.settingShouldBeShown("exposure_reduction")) {
                        Text("Underexposure", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.55f, 0.75f, 0.90f, 1.0f).forEach { value -> FilterChip(selected = exposure == value, enabled = !listening, onClick = { exposure = value; invalidate() }, label = { Text("${(value * 100).roundToInt()}%") }) }
                        }
                    }
                    Text("8-PPM uses one leading-edge flash per three-bit symbol. A seven-flash cadence preamble anchors the clock; missing symbol windows are erasures and parity stripes can repair them across repeated cycles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error.isNotBlank()) Text(error, color = SignalRed)
        }
    }
}
