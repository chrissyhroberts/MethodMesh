package com.example.methodmesh.modules.signals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.roundToInt

object SignalFskTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalFskTransmitMethod.id
    override val title = "Audio FSK transmitter"
    override val description = "Send an error-corrected packet through air or a walkie-talkie audio path."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AudioFskTransmitUi(context, onBack, onConfirmed, onCancel, ultrasonic = false)
}

object SignalUltrasonicTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalUltrasonicTransmitMethod.id
    override val title = "Near-ultrasonic transmitter"
    override val description = "Experimentally send an error-corrected packet near the upper end of phone audio bandwidth."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AudioFskTransmitUi(context, onBack, onConfirmed, onCancel, ultrasonic = true)
}

@Composable
private fun AudioFskTransmitUi(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit,
    ultrasonic: Boolean
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = context.action.settings
    val defaultMark = if (ultrasonic) 12000.0 else 1200.0
    val defaultSpace = if (ultrasonic) 13000.0 else 2200.0
    val defaultBit = if (ultrasonic) 80 else 60

    var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("payload", "Hello from MethodMesh")) }
    var robustness by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("robustness", "fast")) }
    val initialTxProfile = SignalFskProfiles.byId(ultrasonic, settings.signalSetting("channel_profile", ""))
        ?: SignalFskProfiles.nearest(
            ultrasonic,
            settings.signalSetting("mark_hz", defaultMark.toString()).toDoubleOrNull() ?: defaultMark,
            settings.signalSetting("space_hz", defaultSpace.toString()).toDoubleOrNull() ?: defaultSpace,
            settings.signalSetting("bit_ms", defaultBit.toString()).toIntOrNull() ?: defaultBit
        )
    var markHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialTxProfile.markHz) }
    var spaceHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialTxProfile.spaceHz) }
    var bitMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialTxProfile.bitMs) }
    var pttLeadMs by rememberSaveable(context.action.canonicalId) {
        val raw = if (ultrasonic) 0 else settings.signalSetting("ptt_lead_ms", "120").toIntOrNull() ?: 120
        mutableStateOf(if (ultrasonic) 0 else SignalPresetCatalog.pttLeadMs.minByOrNull { kotlin.math.abs(it - raw) } ?: 120)
    }
    var repeatCount by rememberSaveable(context.action.canonicalId) {
        val raw = settings.signalSetting("repeat_count", "2").toIntOrNull() ?: 2
        mutableStateOf(listOf(1, 2, 3, 5).minByOrNull { kotlin.math.abs(it - raw) } ?: 2)
    }
    var messageId by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPacketCodec.newMessageId()) }
    var sending by remember { mutableStateOf(false) }
    var cyclesCompleted by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    var currentProgress by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready") }
    var status by rememberSaveable(context.action.canonicalId) { mutableStateOf(if (ultrasonic) "Experimental: test the channel on the actual phone pair before relying on it." else "Place the phone speaker near the radio microphone, or use direct acoustic phone-to-phone transfer.") }
    var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var txJob by remember { mutableStateOf<Job?>(null) }
    val continueFlag = remember { AtomicBoolean(false) }
    SignalActiveSessionOrientationGuard(sending)

    // Slow acoustic links transparently DEFLATE text when that genuinely saves bytes,
    // then choose a shard width that balances padding against per-frame overhead.
    val acousticPayload = remember(payload) { SignalAcousticPayloadCodec.encodeText(payload) }
    val encodedAttempt = remember(acousticPayload.bytes.contentHashCode(), robustness, messageId) {
        runCatching {
            SignalPacketCodec.encode(
                acousticPayload.bytes,
                SignalPacketCodec.Robustness.from(robustness),
                shardBytes = SignalAcousticPayloadCodec.recommendedShardBytes(acousticPayload.wireBytes),
                messageId = messageId
            )
        }
    }
    val encoded = encodedAttempt.getOrNull()
    val estimatedFirstFrameSeconds = remember(encoded, bitMs) {
        encoded?.frames?.firstOrNull()?.let { FskPhysicalCodec.encodeFrame(it).size * bitMs / 1000.0 } ?: 0.0
    }
    val estimatedCycleSeconds = remember(encoded, bitMs) {
        encoded?.frames?.sumOf { FskPhysicalCodec.encodeFrame(it).size.toLong() * bitMs.toLong() }?.div(1000.0) ?: 0.0
    }

    LaunchedEffect(payload, robustness, markHz, spaceHz, bitMs, pttLeadMs, repeatCount) {
        val values = mutableMapOf(
            "payload" to payload,
            "robustness" to robustness,
            "channel_profile" to (SignalFskProfiles.matching(ultrasonic, markHz, spaceHz, bitMs)?.id ?: initialTxProfile.id),
            "mark_hz" to markHz.roundToInt().toString(),
            "space_hz" to spaceHz.roundToInt().toString(),
            "bit_ms" to bitMs.toString(),
            "repeat_count" to repeatCount.toString()
        )
        if (!ultrasonic) values["ptt_lead_ms"] = pttLeadMs.toString()
        context.onSettingsChanged(values)
    }

    LaunchedEffect(Unit) {
        if (!sending && (status.startsWith("Transmitting", ignoreCase = true) || currentProgress.startsWith("Cycle", ignoreCase = true))) {
            status = if (cyclesCompleted > 0)
                "Transmission paused after screen recreation; $cyclesCompleted complete cycle(s) retained."
            else
                "Transmission paused after screen recreation. Start again when ready."
            currentProgress = "Ready"
        }
    }

    fun stop(userRequested: Boolean = true) {
        continueFlag.set(false)
        txJob?.cancel()
        txJob = null
        sending = false
        if (userRequested) status = if (cyclesCompleted > 0) "Stopped after $cyclesCompleted complete cycle(s)." else "Stopped."
    }

    fun start() {
        val packet = encoded
        if (packet == null || packet.frames.isEmpty()) {
            error = encodedAttempt.exceptionOrNull()?.message ?: "Message could not be encoded."
            return
        }
        if (kotlin.math.abs(markHz - spaceHz) < 80.0) {
            error = "Choose carrier frequencies at least 80 Hz apart."
            return
        }
        stop(userRequested = false)
        error = ""
        cyclesCompleted = 0
        sending = true
        continueFlag.set(true)
        status = if (ultrasonic) "Transmitting high-frequency FSK…" else "Transmitting. Keep PTT held if using a walkie-talkie."
        txJob = scope.launch(Dispatchers.IO) {
            runCatching {
                SignalAudioOutput.playFskFramesBlocking(
                    frames = packet.frames,
                    markHz = markHz,
                    spaceHz = spaceHz,
                    bitMs = bitMs,
                    pttLeadMs = pttLeadMs,
                    cycles = repeatCount,
                    onProgress = { cycle, frame, total ->
                        scope.launch {
                            currentProgress = "Cycle $cycle / $repeatCount • frame $frame / $total"
                            cyclesCompleted = if (frame == total) cycle else (cycle - 1).coerceAtLeast(0)
                        }
                    },
                    shouldContinue = { continueFlag.get() }
                )
            }.onSuccess {
                scope.launch {
                    if (continueFlag.get()) {
                        cyclesCompleted = repeatCount
                        status = "Transmission complete."
                    }
                    sending = false
                    continueFlag.set(false)
                }
            }.onFailure { failure ->
                scope.launch {
                    error = failure.message ?: "Audio transmission failed."
                    sending = false
                    continueFlag.set(false)
                }
            }
        }
    }

    fun commit() {
        val packet = encoded
        if (packet == null || cyclesCompleted <= 0) {
            error = "Complete at least one full transmission cycle before Commit."
            return
        }
        stop(userRequested = false)
        val values = if (ultrasonic) {
            mapOf(
                SignalUltrasonicTransmitFields.RESULT to payload,
                SignalUltrasonicTransmitFields.PAYLOAD to payload,
                SignalUltrasonicTransmitFields.MESSAGE_ID to packet.messageId,
                SignalUltrasonicTransmitFields.FRAME_COUNT to packet.frames.size.toString(),
                SignalUltrasonicTransmitFields.MARK_HZ to markHz.roundToInt().toString(),
                SignalUltrasonicTransmitFields.SPACE_HZ to spaceHz.roundToInt().toString(),
                SignalUltrasonicTransmitFields.BIT_MS to bitMs.toString(),
                SignalUltrasonicTransmitFields.ROBUSTNESS to robustness,
                SignalUltrasonicTransmitFields.CYCLES to cyclesCompleted.toString(),
                SignalUltrasonicTransmitFields.STATUS to "sent",
                SignalUltrasonicTransmitFields.ERROR to ""
            )
        } else {
            mapOf(
                SignalFskTransmitFields.RESULT to payload,
                SignalFskTransmitFields.PAYLOAD to payload,
                SignalFskTransmitFields.MESSAGE_ID to packet.messageId,
                SignalFskTransmitFields.FRAME_COUNT to packet.frames.size.toString(),
                SignalFskTransmitFields.MARK_HZ to markHz.roundToInt().toString(),
                SignalFskTransmitFields.SPACE_HZ to spaceHz.roundToInt().toString(),
                SignalFskTransmitFields.BIT_MS to bitMs.toString(),
                SignalFskTransmitFields.PTT_LEAD_MS to pttLeadMs.toString(),
                SignalFskTransmitFields.ROBUSTNESS to robustness,
                SignalFskTransmitFields.CYCLES to cyclesCompleted.toString(),
                SignalFskTransmitFields.STATUS to "sent",
                SignalFskTransmitFields.ERROR to ""
            )
        }
        committedJson = fieldsJson(values)
        status = "Committed."
        val method = if (ultrasonic) As100SignalUltrasonicTransmitMethod else As100SignalFskTransmitMethod
        val result = signalResult(method, context, values)
        if (context.submitsImmediately) onConfirmed(result)
    }

    DisposableEffect(Unit) { onDispose { stop(userRequested = false) } }

    val committedFields = fieldsFromJson(committedJson)
    val method = if (ultrasonic) As100SignalUltrasonicTransmitMethod else As100SignalFskTransmitMethod
    val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(method, context, it) } }
    val committedFullJson = remember(committedJson) { fullJson(committedResult) }
    val resultKey = if (ultrasonic) SignalUltrasonicTransmitFields.RESULT else SignalFskTransmitFields.RESULT
    val messageIdKey = if (ultrasonic) SignalUltrasonicTransmitFields.MESSAGE_ID else SignalFskTransmitFields.MESSAGE_ID
    val frameCountKey = if (ultrasonic) SignalUltrasonicTransmitFields.FRAME_COUNT else SignalFskTransmitFields.FRAME_COUNT
    val cyclesKey = if (ultrasonic) SignalUltrasonicTransmitFields.CYCLES else SignalFskTransmitFields.CYCLES
    val markKey = if (ultrasonic) SignalUltrasonicTransmitFields.MARK_HZ else SignalFskTransmitFields.MARK_HZ
    val spaceKey = if (ultrasonic) SignalUltrasonicTransmitFields.SPACE_HZ else SignalFskTransmitFields.SPACE_HZ
    val bitKey = if (ultrasonic) SignalUltrasonicTransmitFields.BIT_MS else SignalFskTransmitFields.BIT_MS

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val accent = if (ultrasonic) SignalViolet else SignalCyan
        val railMin = if (ultrasonic) 11000.0 else 200.0
        val railMax = if (ultrasonic) 17000.0 else 8000.0
        Button(
            onClick = { if (sending) stop() else start() },
            enabled = encoded != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (sending) "Stop transmission" else "Start transmission") }

        SignalInstrumentPanel(
            kicker = if (ultrasonic) "EXPERIMENTAL HIGH-BAND MODEM" else "MMS/1 ACOUSTIC / RADIO MODEM",
            title = if (ultrasonic) "Near-ultrasonic transmitter" else "Audio FSK transmitter",
            accent = accent,
            badge = if (sending) "On air" else "Standby"
        ) {
            Text(
                payload.ifBlank { "—" },
                color = SignalText,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable(enabled = payload.isNotBlank()) { copySignalValue(androidContext, "message", payload) }
            )
            SignalCarrierRail(markHz, spaceHz, railMin, railMax, accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalTelemetryTile("BIT", "$bitMs ms", accent, Modifier.weight(1f))
                SignalTelemetryTile("FRAMES", (encoded?.frames?.size ?: 0).toString(), accent, Modifier.weight(1f))
                SignalTelemetryTile("CYCLES", cyclesCompleted.toString(), accent, Modifier.weight(1f))
            }
            if (estimatedFirstFrameSeconds > 0.0) {
                Text(
                    "Physical timing: first complete frame ~${"%.0f".format(Locale.US, estimatedFirstFrameSeconds)} s • full cycle ~${"%.0f".format(Locale.US, estimatedCycleSeconds)} s. SYNC appears near the start; FRAME only increments after the whole frame arrives.",
                    color = SignalMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    if (acousticPayload.compressed)
                        "Wire compression: ${acousticPayload.originalBytes} → ${acousticPayload.wireBytes} bytes (${(acousticPayload.ratio * 100).roundToInt()}%)."
                    else
                        "Wire compression: not beneficial for this payload (${acousticPayload.originalBytes} bytes), so raw UTF-8 is sent without compression overhead.",
                    color = SignalMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(if (sending) currentProgress else status, color = SignalMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                if (ultrasonic)
                    "High-frequency response is device-specific. This mode is not guaranteed inaudible."
                else
                    "Radio path: hold PTT before Transmit; the lead carrier gives squelch/VOX time to open.",
                color = SignalMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Button(onClick = ::commit, enabled = cyclesCompleted > 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (committedResult == null) "Commit" else "Recommit")
        }

        if (committedResult != null && !context.submitsImmediately) {
            SignalCommittedCard(
                title = "Committed transmission",
                primaryLabel = "message",
                primaryValue = committedFields[resultKey].orEmpty(),
                fields = listOf(
                    "Message ID" to committedFields[messageIdKey].orEmpty(),
                    "Frames" to committedFields[frameCountKey].orEmpty(),
                    "Cycles" to committedFields[cyclesKey].orEmpty(),
                    "Mark Hz" to committedFields[markKey].orEmpty(),
                    "Space Hz" to committedFields[spaceKey].orEmpty(),
                    "Bit ms" to committedFields[bitKey].orEmpty()
                ),
                status = exportStatus,
                onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                onShare = { includeFullJson -> exportStatus = shareSignalText(androidContext, "Share signal payload", committedFields[resultKey].orEmpty(), if (includeFullJson) committedFullJson else "") ?: "" },
                onSave = { includeFullJson -> exportStatus = saveSignalText(androidContext, if (ultrasonic) "near_ultrasonic_transmission" else "audio_fsk_transmission", committedFields[resultKey].orEmpty(), if (includeFullJson) committedFullJson else "") },
                onDone = { includeFullJson -> finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, if (ultrasonic) "near_ultrasonic_transmission" else "audio_fsk_transmission", committedFields[resultKey].orEmpty(), if (includeFullJson) committedFullJson else "") } }
            )
        }

        AudioFskTransmitSettings(
            context = context,
            ultrasonic = ultrasonic,
            sending = sending,
            payload = payload,
            onPayload = { payload = it; cyclesCompleted = 0; messageId = SignalPacketCodec.newMessageId() },
            robustness = robustness,
            onRobustness = { robustness = it; cyclesCompleted = 0; messageId = SignalPacketCodec.newMessageId() },
            markHz = markHz,
            onMarkHz = { markHz = it; cyclesCompleted = 0 },
            spaceHz = spaceHz,
            onSpaceHz = { spaceHz = it; cyclesCompleted = 0 },
            bitMs = bitMs,
            onBitMs = { bitMs = it; cyclesCompleted = 0 },
            pttLeadMs = pttLeadMs,
            onPttLeadMs = { pttLeadMs = it; cyclesCompleted = 0 },
            repeatCount = repeatCount,
            onRepeatCount = { repeatCount = it; cyclesCompleted = 0 }
        )

        encodedAttempt.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (committedResult == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.stepNumber > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun AudioFskTransmitSettings(
    context: CapabilityScreenContext,
    ultrasonic: Boolean,
    sending: Boolean,
    payload: String,
    onPayload: (String) -> Unit,
    robustness: String,
    onRobustness: (String) -> Unit,
    markHz: Double,
    onMarkHz: (Double) -> Unit,
    spaceHz: Double,
    onSpaceHz: (Double) -> Unit,
    bitMs: Int,
    onBitMs: (Int) -> Unit,
    pttLeadMs: Int,
    onPttLeadMs: (Int) -> Unit,
    repeatCount: Int,
    onRepeatCount: (Int) -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Packet & channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Matched channel profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalFskProfiles.list(ultrasonic).forEach { profile ->
                    val selected = SignalFskProfiles.matching(ultrasonic, markHz, spaceHz, bitMs)?.id == profile.id
                    FilterChip(
                        selected = selected,
                        enabled = !sending,
                        onClick = { onMarkHz(profile.markHz); onSpaceHz(profile.spaceHz); onBitMs(profile.bitMs) },
                        label = { Text(profile.id) }
                    )
                }
            }
            Text(SignalFskProfiles.matching(ultrasonic, markHz, spaceHz, bitMs)?.label ?: "Custom tuning", style = MaterialTheme.typography.bodySmall)
            if (context.settingShouldBeShown("payload")) OutlinedTextField(payload, onPayload, enabled = !sending, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
            if (context.settingShouldBeShown("robustness")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("fast", "robust", "extreme").forEach { item -> FilterChip(selected = robustness == item, enabled = !sending, onClick = { onRobustness(item) }, label = { Text(item.replaceFirstChar { it.uppercase() }) }) }
                }
            }
            Text("Profile fixes MARK, SPACE and bit duration together; TX and RX use this exact same catalogue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ultrasonic && context.settingShouldBeShown("ptt_lead_ms")) {
                Text("PTT / squelch lead")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SignalPresetCatalog.pttLeadMs.forEach { value ->
                        FilterChip(selected = pttLeadMs == value, enabled = !sending, onClick = { onPttLeadMs(value) }, label = { Text(if (value == 0) "None" else "$value ms") })
                    }
                }
            }
            if (context.settingShouldBeShown("repeat_count")) {
                Text("Complete cycles")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 5).forEach { value ->
                        FilterChip(selected = repeatCount == value, enabled = !sending, onClick = { onRepeatCount(value) }, label = { Text(value.toString()) })
                    }
                }
            }
        }
    }
}

object SignalFskReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalFskReceiveMethod.id
    override val title = "Audio FSK receiver"
    override val description = "Recover MMS/1 packets from audible two-tone FSK, including walkie-talkie audio."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AudioFskReceiveUi(context, onBack, onConfirmed, onCancel, ultrasonic = false)
}

object SignalUltrasonicReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalUltrasonicReceiveMethod.id
    override val title = "Near-ultrasonic receiver"
    override val description = "Experimentally recover MMS/1 packets from high-frequency two-tone FSK."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AudioFskReceiveUi(context, onBack, onConfirmed, onCancel, ultrasonic = true)
}

@Composable
private fun AudioFskReceiveUi(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit,
    ultrasonic: Boolean
) {
    val androidContext = LocalContext.current
    val settings = context.action.settings
    val defaultMark = if (ultrasonic) 12000.0 else 1200.0
    val defaultSpace = if (ultrasonic) 13000.0 else 2200.0
    val defaultBit = if (ultrasonic) 80 else 60
    val defaultMin = if (ultrasonic) -60.0 else -50.0

    val initialRxProfile = SignalFskProfiles.byId(ultrasonic, settings.signalSetting("channel_profile", ""))
        ?: SignalFskProfiles.nearest(
            ultrasonic,
            settings.signalSetting("mark_hz", defaultMark.toString()).toDoubleOrNull() ?: defaultMark,
            settings.signalSetting("space_hz", defaultSpace.toString()).toDoubleOrNull() ?: defaultSpace,
            settings.signalSetting("bit_ms", defaultBit.toString()).toIntOrNull() ?: defaultBit
        )
    var markHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialRxProfile.markHz) }
    var spaceHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialRxProfile.spaceHz) }
    var bitMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialRxProfile.bitMs) }
    var minimumDbfs by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(SignalPresetCatalog.closestLevel(settings.signalSetting("minimum_dbfs", defaultMin.toString()).toDoubleOrNull() ?: defaultMin).dbfs)
    }
    var listening by remember { mutableStateOf(false) }
    var frameStoreJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
    var physicalRejected by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    var status by rememberSaveable(context.action.canonicalId) { mutableStateOf(if (ultrasonic) "Experimental receiver. Calibrate frequency response on the actual phone pair first." else "Start listening; audible packets may arrive directly or from a radio speaker.") }
    var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var levelText by rememberSaveable(context.action.canonicalId) { mutableStateOf("—") }
    var dominantTone by rememberSaveable(context.action.canonicalId) { mutableStateOf("—") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var audioGranted by remember { mutableStateOf(fskHasPermission(androidContext, Manifest.permission.RECORD_AUDIO)) }

    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioGranted = granted || fskHasPermission(androidContext, Manifest.permission.RECORD_AUDIO)
        if (!audioGranted) error = "Microphone permission is required for FSK reception."
    }
    val engine = remember(androidContext) { SignalFskReceiverEngine(androidContext) }
    val clockDecoder = remember { FskClockRecoveryDecoder(bitMs) }
    var clockTelemetry by remember { mutableStateOf(clockDecoder.telemetry()) }
    var spectrumBins by remember { mutableStateOf(emptyList<Float>()) }
    var spectrumMinHz by remember { mutableStateOf(if (ultrasonic) 11000.0 else 200.0) }
    var spectrumMaxHz by remember { mutableStateOf(if (ultrasonic) 17000.0 else 8000.0) }
    var spectrumPeakHz by remember { mutableStateOf(0.0) }
    var confidenceDb by remember { mutableStateOf(0.0) }
    var lastFrameOutcome by rememberSaveable(context.action.canonicalId) { mutableStateOf("No complete physical frame yet") }
    SignalActiveSessionOrientationGuard(listening)

    fun storedFrames(raw: String = frameStoreJson): MutableList<String> = runCatching {
        val array = JSONArray(raw)
        MutableList(array.length()) { index -> array.optString(index) }
    }.getOrDefault(mutableListOf())

    fun collectorFrom(raw: String = frameStoreJson): SignalFrameCollector = SignalFrameCollector().also { collector -> storedFrames(raw).forEach(collector::offer) }
    val collectorState = remember(frameStoreJson) { collectorFrom().state() }
    val receivedText = remember(frameStoreJson) {
        collectorState.payload?.let(SignalAcousticPayloadCodec::decodeText)
            ?: collectorState.text
    }

    LaunchedEffect(Unit) {
        if (!listening && status.startsWith("Listening", ignoreCase = true)) {
            status = if (collectorState.requiredRank > 0)
                "Reception paused after screen recreation; ${collectorState.rank} / ${collectorState.requiredRank} retained shards."
            else
                "Reception paused after screen recreation. Start listening again."
        }
    }

    fun stop(userRequested: Boolean = true) {
        engine.stop()
        listening = false
        dominantTone = "—"
        if (userRequested) status = if (collectorState.requiredRank > 0) "Paused at ${collectorState.rank} / ${collectorState.requiredRank} shards. Complete frames are retained." else "Paused."
    }

    fun acceptFrame(raw: String) {
        val parsed = SignalPacketCodec.parse(raw)
        if (!parsed.valid) {
            physicalRejected++
            return
        }
        val frames = storedFrames()
        if (raw !in frames) frames += raw
        val json = JSONArray().also { a -> frames.forEach(a::put) }.toString()
        frameStoreJson = json
        val state = collectorFrom(json).state()
        val decodedText = state.payload?.let(SignalAcousticPayloadCodec::decodeText)
        status = when {
            state.complete && decodedText != null -> "Message recovered. CRC verified${if (state.recoveredMissingSources > 0) "; ${state.recoveredMissingSources} missing shard(s) reconstructed" else ""}."
            state.complete -> "MMS/1 reconstructed, but acoustic decompression/text decoding failed."
            state.requiredRank > 0 -> "Collecting ${state.rank} / ${state.requiredRank} independent shards."
            else -> "Acquiring MMS/1…"
        }
        if (state.complete && decodedText != null) stop(userRequested = false)
    }

    fun start() {
        if (!audioGranted) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (kotlin.math.abs(markHz - spaceHz) < 80.0) {
            error = "Choose carrier frequencies at least 80 Hz apart."
            return
        }
        error = ""
        clockDecoder.setBitMs(bitMs)
        clockDecoder.reset()
        clockTelemetry = clockDecoder.telemetry()
        spectrumBins = emptyList()
        spectrumPeakHz = 0.0
        confidenceDb = 0.0
        lastFrameOutcome = "Waiting for a complete physical frame"
        listening = true
        status = if (collectorState.requiredRank > 0) "Listening — retained ${collectorState.rank} / ${collectorState.requiredRank} shards." else "Listening for FSK sync…"
        engine.start(
            markHz = markHz,
            spaceHz = spaceHz,
            minimumDbfs = minimumDbfs,
            onWindow = { window ->
                levelText = "${"%.1f".format(Locale.US, window.dbfs)} dBFS • ${window.audioSource}"
                dominantTone = when (window.tone) { true -> "MARK"; false -> "SPACE"; null -> "—" }
                confidenceDb = window.decisionConfidenceDb
                window.spectrum?.let { values ->
                    spectrumBins = values.toList()
                    spectrumMinHz = window.spectrumMinHz
                    spectrumMaxHz = window.spectrumMaxHz
                    spectrumPeakHz = window.peakHz
                }
                val before = clockTelemetry
                clockDecoder.feed(window.tone).forEach(::acceptFrame)
                val after = clockDecoder.telemetry()
                if (after.physicalFrames > before.physicalFrames) {
                    lastFrameOutcome = if (after.payloadDecodeFailures > before.payloadDecodeFailures)
                        "FRAME 100% • PHY retry: ${after.lastPayloadRejectReason ?: "compact payload could not be reconstructed"}"
                    else
                        "FRAME 100% • MMS/1 frame decoded"
                }
                clockTelemetry = after
                physicalRejected = maxOf(physicalRejected, after.invalidLengths + after.payloadDecodeFailures)
            },
            onError = { error = it; listening = false }
        )
    }

    fun reset() {
        stop(userRequested = false)
        frameStoreJson = "[]"
        physicalRejected = 0
        committedJson = null
        exportStatus = ""
        status = "Receiver reset."
        lastFrameOutcome = "No complete physical frame yet"
        error = ""
    }

    fun invalidateWorkingReception() {
        stop(userRequested = false)
        frameStoreJson = "[]"
        physicalRejected = 0
        clockDecoder.setBitMs(bitMs)
        clockTelemetry = clockDecoder.telemetry()
        spectrumBins = emptyList()
        spectrumPeakHz = 0.0
        confidenceDb = 0.0
        dominantTone = "—"
        levelText = "—"
        lastFrameOutcome = "No complete physical frame yet"
        status = "Receiver tuning changed; working evidence was cleared. Use the same A/B/C/D profile on both phones, then listen again. Any committed result remains frozen."
        error = ""
    }

    fun commit() {
        val state = collectorFrom().state()
        val text = state.payload?.let(SignalAcousticPayloadCodec::decodeText).orEmpty()
        if (!state.complete || text.isBlank()) {
            error = "The message is not yet recoverable. Keep listening for more frames."
            return
        }
        stop(userRequested = false)
        val values = if (ultrasonic) {
            mapOf(
                SignalUltrasonicReceiveFields.RESULT to text,
                SignalUltrasonicReceiveFields.MESSAGE_ID to state.messageId.orEmpty(),
                SignalUltrasonicReceiveFields.FRAMES_ACCEPTED to state.acceptedFrames.toString(),
                SignalUltrasonicReceiveFields.FRAMES_REJECTED to (state.rejectedFrames + physicalRejected).toString(),
                SignalUltrasonicReceiveFields.RECOVERED_MISSING to state.recoveredMissingSources.toString(),
                SignalUltrasonicReceiveFields.CRC_VERIFIED to state.messageCrcVerified.toString(),
                SignalUltrasonicReceiveFields.MARK_HZ to markHz.roundToInt().toString(),
                SignalUltrasonicReceiveFields.SPACE_HZ to spaceHz.roundToInt().toString(),
                SignalUltrasonicReceiveFields.BIT_MS to bitMs.toString(),
                SignalUltrasonicReceiveFields.STATUS to "received",
                SignalUltrasonicReceiveFields.ERROR to ""
            )
        } else {
            mapOf(
                SignalFskReceiveFields.RESULT to text,
                SignalFskReceiveFields.MESSAGE_ID to state.messageId.orEmpty(),
                SignalFskReceiveFields.FRAMES_ACCEPTED to state.acceptedFrames.toString(),
                SignalFskReceiveFields.FRAMES_REJECTED to (state.rejectedFrames + physicalRejected).toString(),
                SignalFskReceiveFields.RECOVERED_MISSING to state.recoveredMissingSources.toString(),
                SignalFskReceiveFields.CRC_VERIFIED to state.messageCrcVerified.toString(),
                SignalFskReceiveFields.MARK_HZ to markHz.roundToInt().toString(),
                SignalFskReceiveFields.SPACE_HZ to spaceHz.roundToInt().toString(),
                SignalFskReceiveFields.BIT_MS to bitMs.toString(),
                SignalFskReceiveFields.STATUS to "received",
                SignalFskReceiveFields.ERROR to ""
            )
        }
        committedJson = fieldsJson(values)
        status = "Committed."
        val method = if (ultrasonic) As100SignalUltrasonicReceiveMethod else As100SignalFskReceiveMethod
        val result = signalResult(method, context, values)
        if (context.submitsImmediately) onConfirmed(result)
    }

    LaunchedEffect(markHz, spaceHz, bitMs, minimumDbfs) {
        context.onSettingsChanged(
            mapOf(
                "channel_profile" to (SignalFskProfiles.matching(ultrasonic, markHz, spaceHz, bitMs)?.id ?: initialRxProfile.id),
                "mark_hz" to markHz.roundToInt().toString(),
                "space_hz" to spaceHz.roundToInt().toString(),
                "bit_ms" to bitMs.toString(),
                "minimum_dbfs" to minimumDbfs.toString()
            )
        )
    }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    val committedFields = fieldsFromJson(committedJson)
    val method = if (ultrasonic) As100SignalUltrasonicReceiveMethod else As100SignalFskReceiveMethod
    val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(method, context, it) } }
    val committedFullJson = remember(committedJson) { fullJson(committedResult) }
    val resultKey = if (ultrasonic) SignalUltrasonicReceiveFields.RESULT else SignalFskReceiveFields.RESULT
    val messageIdKey = if (ultrasonic) SignalUltrasonicReceiveFields.MESSAGE_ID else SignalFskReceiveFields.MESSAGE_ID
    val recoveredKey = if (ultrasonic) SignalUltrasonicReceiveFields.RECOVERED_MISSING else SignalFskReceiveFields.RECOVERED_MISSING
    val crcKey = if (ultrasonic) SignalUltrasonicReceiveFields.CRC_VERIFIED else SignalFskReceiveFields.CRC_VERIFIED
    val markKeyRx = if (ultrasonic) SignalUltrasonicReceiveFields.MARK_HZ else SignalFskReceiveFields.MARK_HZ
    val spaceKeyRx = if (ultrasonic) SignalUltrasonicReceiveFields.SPACE_HZ else SignalFskReceiveFields.SPACE_HZ
    val bitKeyRx = if (ultrasonic) SignalUltrasonicReceiveFields.BIT_MS else SignalFskReceiveFields.BIT_MS

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val accent = if (ultrasonic) SignalViolet else SignalCyan
        val railMin = if (ultrasonic) 11000.0 else 200.0
        val railMax = if (ultrasonic) 17000.0 else 8000.0
        Button(
            onClick = { if (listening) stop() else start() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (listening) "Pause listening" else "Start listening") }

        SignalInstrumentPanel(
            kicker = if (ultrasonic) "EXPERIMENTAL HIGH-BAND RECEIVER" else "MMS/1 ACOUSTIC / RADIO RECEIVER",
            title = if (ultrasonic) "Near-ultrasonic receiver" else "Audio FSK receiver",
            accent = accent,
            badge = when { collectorState.complete -> "Recovered"; listening -> dominantTone.takeUnless { it == "—" } ?: "Listening"; else -> "Paused" }
        ) {
            Text(
                receivedText?.ifBlank { null } ?: if (collectorState.requiredRank > 0) "${collectorState.rank} / ${collectorState.requiredRank} SHARDS" else "WAITING FOR PACKET",
                color = if (receivedText.isNullOrBlank()) SignalMuted else SignalText,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable(enabled = receivedText?.isNotBlank() == true) { copySignalValue(androidContext, "received text", receivedText.orEmpty()) }
            )
            if (collectorState.requiredRank > 0) SignalProgressTrack(collectorState.rank.toFloat() / collectorState.requiredRank.toFloat(), accent)
            SignalCarrierRail(markHz, spaceHz, railMin, railMax, accent)
            SignalSpectrumView(spectrumBins, spectrumMinHz, spectrumMaxHz, markHz, spaceHz, accent)
            Text("Spectrum confirms what the microphone is actually hearing. Amber = MARK target, cyan = SPACE target.", color = SignalMuted, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalTelemetryTile("PEAK", if (spectrumPeakHz > 0) "${spectrumPeakHz.roundToInt()} Hz" else "—", accent, Modifier.weight(1f))
                SignalTelemetryTile("TONE CONF", "${"%.1f".format(Locale.US, confidenceDb)} dB", accent, Modifier.weight(1f))
                SignalTelemetryTile("BIT", "$bitMs ms", accent, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalTelemetryTile("CLOCK", if (clockTelemetry.clockLocked) "LOCK" else "search", accent, Modifier.weight(1f))
                SignalTelemetryTile("SYNC", clockTelemetry.syncDetections.toString(), accent, Modifier.weight(1f))
                SignalTelemetryTile(
                    "FRAME",
                    if (clockTelemetry.pendingPayloadBytes > 0)
                        "${(clockTelemetry.pendingFrameProgress * 100).roundToInt()}%"
                    else clockTelemetry.physicalFrames.toString(),
                    accent,
                    Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalTelemetryTile("ACCEPTED", collectorState.acceptedFrames.toString(), accent, Modifier.weight(1f))
                SignalTelemetryTile("REJECTED", (collectorState.rejectedFrames + physicalRejected).toString(), accent, Modifier.weight(1f))
                SignalTelemetryTile("MMS", if (collectorState.requiredRank > 0) "${collectorState.rank}/${collectorState.requiredRank}" else "—", accent, Modifier.weight(1f))
            }
            Text(levelText, color = SignalMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            if (clockTelemetry.pendingPayloadBytes > 0) {
                Text(
                    "SYNC locked. Receiving ${clockTelemetry.pendingPayloadBytes}-byte physical payload: ${(clockTelemetry.pendingFrameProgress * 100).roundToInt()}% complete.",
                    color = SignalMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(lastFrameOutcome, color = if (lastFrameOutcome.contains("PHY retry")) SignalAmber else SignalMuted, style = MaterialTheme.typography.labelSmall)
            if (ultrasonic) Text("A–C trade speed against high-band reach; D keeps the slower 15/16 kHz profile that survived a noisy music test. A completed but rejected frame now remains visible instead of silently returning to 0%.", color = SignalMuted, style = MaterialTheme.typography.labelSmall)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::commit, enabled = collectorState.complete, modifier = Modifier.weight(1f)) { Text(if (committedResult == null) "Commit" else "Recommit") }
            OutlinedButton(onClick = ::reset, modifier = Modifier.weight(1f)) { Text("Reset") }
        }

        if (committedResult != null && !context.submitsImmediately) {
            SignalCommittedCard(
                title = "Committed reception",
                primaryLabel = "received text",
                primaryValue = committedFields[resultKey].orEmpty(),
                fields = listOf(
                    "Message ID" to committedFields[messageIdKey].orEmpty(),
                    "Recovered missing" to committedFields[recoveredKey].orEmpty(),
                    "CRC verified" to committedFields[crcKey].orEmpty(),
                    "Mark Hz" to committedFields[markKeyRx].orEmpty(),
                    "Space Hz" to committedFields[spaceKeyRx].orEmpty(),
                    "Bit ms" to committedFields[bitKeyRx].orEmpty()
                ),
                status = exportStatus,
                onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                onShare = { includeFullJson -> exportStatus = shareSignalText(androidContext, "Share recovered signal", committedFields[resultKey].orEmpty(), if (includeFullJson) committedFullJson else "") ?: "" },
                onSave = { includeFullJson -> exportStatus = saveSignalText(androidContext, if (ultrasonic) "near_ultrasonic_reception" else "audio_fsk_reception", committedFields[resultKey].orEmpty(), if (includeFullJson) committedFullJson else "") },
                onDone = { includeFullJson -> finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, if (ultrasonic) "near_ultrasonic_reception" else "audio_fsk_reception", committedFields[resultKey].orEmpty(), if (includeFullJson) committedFullJson else "") } }
            )
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Receiver tuning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Use the same profile on both phones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SignalFskProfiles.list(ultrasonic).forEach { profile ->
                        val selected = SignalFskProfiles.matching(ultrasonic, markHz, spaceHz, bitMs)?.id == profile.id
                        FilterChip(selected = selected, enabled = !listening, onClick = {
                            if (!selected) { markHz = profile.markHz; spaceHz = profile.spaceHz; bitMs = profile.bitMs; invalidateWorkingReception() }
                        }, label = { Text(profile.id) })
                    }
                }
                Text(SignalFskProfiles.matching(ultrasonic, markHz, spaceHz, bitMs)?.label ?: "Custom tuning", style = MaterialTheme.typography.bodySmall)
                Text("Profile fixes MARK, SPACE and bit duration together; choose the same letter on sender and receiver.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (context.settingShouldBeShown("minimum_dbfs")) {
                    Text("Minimum microphone level")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SignalPresetCatalog.levelThresholds.forEach { preset ->
                            FilterChip(
                                selected = kotlin.math.abs(minimumDbfs - preset.dbfs) < 0.5,
                                enabled = !listening,
                                onClick = { if (minimumDbfs != preset.dbfs) { minimumDbfs = preset.dbfs; invalidateWorkingReception() } },
                                label = { Text(preset.id) }
                            )
                        }
                    }
                    Text("Current threshold: ${"%.0f".format(Locale.US, minimumDbfs)} dBFS", style = MaterialTheme.typography.bodySmall)
                    SignalDbfsHelp()
                }
            }
        }

        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (committedResult == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.stepNumber > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}

private fun fskHasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
