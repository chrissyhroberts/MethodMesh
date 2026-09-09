package com.example.methodmesh.modules.signals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SignalSurfaceTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalSurfaceTransmitMethod.id
    override val title = "Tabletop transmitter"
    override val description = "Experimental contact-coupled modem. Couples a slow error-corrected packet into a shared table, box or rail using the phone speaker."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val settings = context.action.settings
        val scope = rememberCoroutineScope()
        var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("payload", "HELLO TABLE")) }
        var robustness by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("robustness", "fast")) }
        val initialSurfaceProfile = remember(context.action.canonicalId) {
            SignalPresetCatalog.surfaceById(settings.signalSetting("surface_profile", ""))
                ?: SignalPresetCatalog.closestSurface(
                    settings.signalSetting("carrier_hz", "180").toDoubleOrNull() ?: 180.0,
                    settings.signalSetting("bit_ms", "60").toIntOrNull() ?: 60
                )
        }
        var carrierHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialSurfaceProfile.carrierHz) }
        var bitMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialSurfaceProfile.bitMs) }
        var repeatCount by rememberSaveable(context.action.canonicalId) {
            val raw = settings.signalSetting("repeat_count", "2").toIntOrNull() ?: 2
            mutableStateOf(listOf(1, 2, 3, 5).minByOrNull { kotlin.math.abs(it - raw) } ?: 2)
        }
        var messageId by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPacketCodec.newMessageId()) }
        var sending by remember { mutableStateOf(false) }
        var completedCycles by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var frameIndex by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var currentBit by remember { mutableStateOf(0) }
        var totalBits by remember { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Place both phones firmly on the same surface. Speaker contact matters.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var job by remember { mutableStateOf<Job?>(null) }
        val continueFlag = remember { AtomicBoolean(false) }

        val envelopeBytes = remember(payload) { SignalContentEnvelope.encodeText(payload) }
        val envelope = remember(envelopeBytes) { SignalContentEnvelope.decode(envelopeBytes)!! }
        val encodedAttempt = remember(envelopeBytes, robustness, messageId) {
            runCatching {
                val shardBytes = when {
                    envelopeBytes.size <= 48 -> 24
                    envelopeBytes.size <= 144 -> 48
                    else -> 64
                }
                SignalPacketCodec.encode(envelopeBytes, SignalPacketCodec.Robustness.from(robustness), shardBytes, messageId)
            }
        }
        val encoded = encodedAttempt.getOrNull()
        val estimatedFirstFrameSeconds = remember(encoded, bitMs) {
            encoded?.frames?.firstOrNull()?.let { FskPhysicalCodec.encodeFrame(it).size * bitMs / 1000.0 } ?: 0.0
        }

        fun invalidate() {
            continueFlag.set(false); job?.cancel(); job = null; sending = false; completedCycles = 0; frameIndex = 0; currentBit = 0; totalBits = 0
            messageId = SignalPacketCodec.newMessageId()
            status = "Settings changed. Send a new complete surface cycle before Commit."
        }

        fun stop() { continueFlag.set(false); job?.cancel(); job = null; sending = false; status = if (completedCycles > 0) "Stopped after $completedCycles complete cycle(s)." else "Stopped." }

        fun start() {
            val packet = encoded
            if (payload.isBlank() || packet == null) { error = encodedAttempt.exceptionOrNull()?.message ?: "Enter a message."; return }
            completedCycles = 0; frameIndex = 0; currentBit = 0; totalBits = 0; sending = true; error = ""; continueFlag.set(true)
            status = "Surface carrier active. Keep both phones still and in firm contact."
            job = scope.launch(Dispatchers.IO) {
                runCatching {
                    SignalAudioOutput.playOokFramesBlocking(
                        packet.frames, carrierHz, bitMs, repeatCount,
                        onProgress = { cycle, frame, _ -> scope.launch { completedCycles = cycle - 1; frameIndex = frame; currentBit = 0 } },
                        onBitProgress = { _, _, bit, bits -> scope.launch { currentBit = bit; totalBits = bits } },
                        shouldContinue = { continueFlag.get() }
                    )
                }.onSuccess {
                    scope.launch {
                        if (continueFlag.get()) {
                            completedCycles = repeatCount
                            status = "Surface transmission complete."
                        }
                        sending = false
                        continueFlag.set(false)
                    }
                }.onFailure { failure -> scope.launch { sending = false; continueFlag.set(false); error = failure.message ?: "Surface transmission failed." } }
            }
        }

        fun commit() {
            val packet = encoded
            if (packet == null || completedCycles <= 0) { error = "Complete at least one full surface cycle before Commit."; return }
            stop()
            val values = mapOf(
                SignalSurfaceTransmitFields.RESULT to payload,
                SignalSurfaceTransmitFields.PAYLOAD to payload,
                SignalSurfaceTransmitFields.MESSAGE_ID to packet.messageId,
                SignalSurfaceTransmitFields.FRAME_COUNT to packet.frames.size.toString(),
                SignalSurfaceTransmitFields.CARRIER_HZ to carrierHz.roundToInt().toString(),
                SignalSurfaceTransmitFields.BIT_MS to bitMs.toString(),
                SignalSurfaceTransmitFields.ROBUSTNESS to robustness,
                SignalSurfaceTransmitFields.CYCLES to completedCycles.toString(),
                SignalSurfaceTransmitFields.CHECKSUM_SHA256 to envelope.expectedSha256,
                SignalSurfaceTransmitFields.STATUS to "sent",
                SignalSurfaceTransmitFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed. SHA-256 and physical settings are frozen with the transmission record."
            val result = signalResult(As100SignalSurfaceTransmitMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        LaunchedEffect(payload, robustness, carrierHz, bitMs, repeatCount) {
            context.onSettingsChanged(mapOf("payload" to payload, "robustness" to robustness, "surface_profile" to SignalPresetCatalog.closestSurface(carrierHz, bitMs).id, "carrier_hz" to carrierHz.roundToInt().toString(), "bit_ms" to bitMs.toString(), "repeat_count" to repeatCount.toString()))
        }
        SignalActiveSessionOrientationGuard(sending)
        DisposableEffect(Unit) { onDispose { continueFlag.set(false); job?.cancel() } }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalSurfaceTransmitMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalInstrumentPanel("EXPERIMENTAL CONTACT-COUPLED OOK", "Tabletop transmitter", SignalAmber, badge = if (sending) "Vibrating" else "Experimental") {
                Text(payload.ifBlank { "NO MESSAGE" }, color = SignalText, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("CARRIER", "${carrierHz.roundToInt()} Hz", SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("FRAME", if (encoded == null) "—" else "$frameIndex/${encoded.frames.size}", SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("BIT", if (totalBits > 0) "$currentBit/$totalBits" else "$bitMs ms", SignalAmber, Modifier.weight(1f))
                }
                if (estimatedFirstFrameSeconds > 0) Text("First physical frame ~${"%.0f".format(estimatedFirstFrameSeconds)} s. Bit progress updates continuously so a long contact frame should never look frozen.", color = SignalMuted, style = MaterialTheme.typography.labelSmall)
                Text("SHA-256 ${envelope.expectedSha256}", color = SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { copySignalValue(androidContext, "SHA-256", envelope.expectedSha256) })
                Text("Experimental: current phone-pair testing has not produced reliable surface reception. Retained as a research channel. Best coupling: phone speaker edge firmly touching a rigid table, case, box or rail.", color = SignalMuted, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (sending) stop() else start() }, modifier = Modifier.weight(1f)) { Text(if (sending) "Stop" else "Transmit") }
                Button(onClick = ::commit, enabled = completedCycles > 0, modifier = Modifier.weight(1f)) { Text(if (committedResult == null) "Commit" else "Recommit") }
            }
            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard("Committed tabletop transmission", "message", committedFields[SignalSurfaceTransmitFields.RESULT].orEmpty(), listOf(
                    "SHA-256" to committedFields[SignalSurfaceTransmitFields.CHECKSUM_SHA256].orEmpty(),
                    "Carrier" to committedFields[SignalSurfaceTransmitFields.CARRIER_HZ].orEmpty(),
                    "Bit ms" to committedFields[SignalSurfaceTransmitFields.BIT_MS].orEmpty(),
                    "Cycles" to committedFields[SignalSurfaceTransmitFields.CYCLES].orEmpty()
                ), exportStatus, { l,v -> copySignalValue(androidContext,l,v) },
                    { exportStatus = shareSignalText(androidContext,"Share tabletop transmission",committedFields[SignalSurfaceTransmitFields.RESULT].orEmpty()) ?: "" },
                    { exportStatus = saveSignalText(androidContext,"surface_transmission",committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" },committedFullJson) },
                    { finishSignalResult(context,androidContext,committedResult,onConfirmed) { saveSignalText(androidContext,"surface_transmission",committedFields.entries.joinToString("\n") { "${it.key}=${it.value}" },committedFullJson) } }
                )
            }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (context.settingShouldBeShown("payload")) OutlinedTextField(payload, { payload = it; invalidate() }, enabled = !sending, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
                if (context.settingShouldBeShown("robustness")) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("fast","robust","extreme").forEach { r -> FilterChip(robustness==r,{ robustness=r; invalidate() },enabled=!sending,label={Text(r)}) } }
                if (context.settingShouldBeShown("surface_profile") || context.settingShouldBeShown("carrier_hz") || context.settingShouldBeShown("bit_ms")) {
                    Text("Matched surface profile", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SignalPresetCatalog.surfaceProfiles.forEach { profile ->
                            FilterChip(
                                selected = kotlin.math.abs(carrierHz - profile.carrierHz) < 0.5 && bitMs == profile.bitMs,
                                onClick = { carrierHz = profile.carrierHz; bitMs = profile.bitMs; invalidate() },
                                enabled = !sending,
                                label = { Text(profile.id) }
                            )
                        }
                    }
                    val selectedProfile = SignalPresetCatalog.closestSurface(carrierHz, bitMs)
                    Text("${selectedProfile.label}. Select the same letter on TX and RX.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (context.settingShouldBeShown("repeat_count")) {
                    Text("Cycles", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3, 5).forEach { count ->
                            FilterChip(selected = repeatCount == count, onClick = { repeatCount = count; invalidate() }, enabled = !sending, label = { Text(count.toString()) })
                        }
                    }
                }
            } }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (committedResult == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (context.stepNumber > 1) OutlinedButton(onClick=onBack,modifier=Modifier.weight(1f)){Text("Back")}; OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Cancel")} }
        }
    }
}

object SignalSurfaceReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalSurfaceReceiveMethod.id
    override val title = "Tabletop receiver"
    override val description = "Experimental accelerometer contact modem. Calibrate a shared surface, watch its vibration trace and attempt MMS/1 recovery."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val settings = context.action.settings
        val initialSurfaceProfile = remember(context.action.canonicalId) {
            SignalPresetCatalog.surfaceById(settings.signalSetting("surface_profile", ""))
                ?: SignalPresetCatalog.closestSurface(
                    settings.signalSetting("carrier_hz", "180").toDoubleOrNull() ?: 180.0,
                    settings.signalSetting("bit_ms", "60").toIntOrNull() ?: 60
                )
        }
        var carrierHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialSurfaceProfile.carrierHz) }
        var bitMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialSurfaceProfile.bitMs) }
        var calibrationMs by rememberSaveable(context.action.canonicalId) {
            val raw = settings.signalSetting("calibration_ms", "1500").toIntOrNull() ?: 1500
            mutableStateOf(SignalPresetCatalog.surfaceCalibrationMs.minByOrNull { kotlin.math.abs(it - raw) } ?: 1500)
        }
        var sensitivity by rememberSaveable(context.action.canonicalId) {
            val raw = settings.signalSetting("sensitivity", "2.5").toDoubleOrNull() ?: 2.5
            mutableStateOf(SignalPresetCatalog.surfaceSensitivity.minByOrNull { kotlin.math.abs(it - raw) } ?: 2.5)
        }
        var listening by remember { mutableStateOf(false) }
        var calibrating by remember { mutableStateOf(false) }
        var calibrationStarted by remember { mutableStateOf(0L) }
        val calibrationValues = remember { mutableListOf<Double>() }
        var baseline by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.0) }
        var threshold by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.0) }
        var currentVibration by remember { mutableStateOf(0.0) }
        var sensorWindows by remember { mutableStateOf(0) }
        var frameStoreJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var physicalRejected by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Place both phones on the same surface, then calibrate while the transmitter is quiet. Sensor sampling uses 100 Hz and needs no high-rate sensor permission.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val trace = remember { mutableStateListOf<Float>() }
        val engine = remember(androidContext) { SignalSurfaceReceiverEngine(androidContext) }
        val clock = remember { FskClockRecoveryDecoder(bitMs) }

        fun storedFrames(raw: String = frameStoreJson): MutableList<String> = runCatching { val a=JSONArray(raw); MutableList(a.length()){a.optString(it)} }.getOrDefault(mutableListOf())
        fun collectorFrom(raw: String = frameStoreJson): SignalFrameCollector = SignalFrameCollector().also { c -> storedFrames(raw).forEach(c::offer) }
        val collectorState = remember(frameStoreJson) { collectorFrom().state() }
        val content = remember(frameStoreJson) { collectorState.payload?.let(SignalContentEnvelope::decode) }
        val telemetry = remember(currentVibration, frameStoreJson, physicalRejected) { clock.telemetry() }

        fun resetWorking(message: String = "Receiver reset.") {
            frameStoreJson="[]"; physicalRejected=0; clock.reset(); trace.clear(); currentVibration=0.0; sensorWindows=0; status=message; error=""
        }
        fun stop() { engine.stop(); listening=false; calibrating=false }
        fun acceptFrame(raw: String) {
            if (!SignalPacketCodec.parse(raw).valid) { physicalRejected++; return }
            val frames=storedFrames(); if (raw !in frames) frames += raw
            frameStoreJson=JSONArray().also { a -> frames.forEach { a.put(it) } }.toString()
            val state=collectorFrom().state(); val decoded=state.payload?.let(SignalContentEnvelope::decode)
            status=when { state.complete && decoded?.checksumVerified==true -> "Packet recovered • CRC verified • SHA-256 MATCH."; state.complete -> "Packet reconstructed but SHA-256 verification failed."; state.requiredRank>0 -> "Collecting ${state.rank}/${state.requiredRank} shards."; else -> "Clock acquired; waiting for MMS/1 geometry." }
            if (state.complete) stop()
        }
        fun startCalibration() {
            stop()
            resetWorking("Calibrating resting vibration… keep both phones still and transmitter OFF.")
            calibrationValues.clear()
            calibrationStarted = System.currentTimeMillis()
            calibrating = true
            error = ""
            val started = engine.start(
                onWindow = { w ->
                    runCatching {
                        if (!w.vibrationRms.isFinite()) return@runCatching
                        currentVibration = w.vibrationRms
                        sensorWindows++
                        trace += w.vibrationRms.toFloat()
                        if (trace.size > 180) trace.removeAt(0)
                        if (calibrating) {
                            calibrationValues += w.vibrationRms
                            val elapsed = System.currentTimeMillis() - calibrationStarted
                            status = "Calibrating resting vibration… ${elapsed.coerceAtMost(calibrationMs.toLong())}/${calibrationMs} ms • ${calibrationValues.size} windows"
                            if (elapsed >= calibrationMs && calibrationValues.size >= 5) {
                                val values = calibrationValues.filter { it.isFinite() }
                                if (values.size < 5) {
                                    status = "Waiting for stable accelerometer samples…"
                                } else {
                                    val mean = values.average()
                                    val variance = values.sumOf { sample -> val d = sample - mean; d * d } / values.size.toDouble()
                                    val sd = sqrt(variance.coerceAtLeast(0.0))
                                    baseline = mean
                                    threshold = mean + sensitivity * sd.coerceAtLeast(mean * 0.08 + 1e-6)
                                    calibrating = false
                                    clock.reset()
                                    status = "Calibrated from ${values.size} windows. Start the transmitter now; packet acquisition is live."
                                }
                            }
                        } else if (threshold > 0.0) {
                            val tone = w.vibrationRms > threshold
                            clock.feed(tone).forEach(::acceptFrame)
                        }
                    }.onFailure { failure ->
                        error = failure.message ?: "Tabletop calibration failed."
                        listening = false
                        calibrating = false
                        engine.stop()
                    }
                },
                onError = { message -> error = message; listening = false; calibrating = false }
            )
            listening = started
            if (!started) calibrating = false
        }
        fun commit() {
            val state=collectorFrom().state(); val decoded=state.payload?.let(SignalContentEnvelope::decode)
            if (!state.complete || decoded?.text.isNullOrBlank()) { error="A complete text packet has not been recovered."; return }
            if (decoded?.checksumVerified != true) { error="INTEGRITY FAILURE: SHA-256 does not match."; return }
            stop()
            val values=mapOf(
                SignalSurfaceReceiveFields.RESULT to decoded.text.orEmpty(), SignalSurfaceReceiveFields.MESSAGE_ID to state.messageId.orEmpty(),
                SignalSurfaceReceiveFields.FRAMES_ACCEPTED to state.acceptedFrames.toString(), SignalSurfaceReceiveFields.FRAMES_REJECTED to (state.rejectedFrames+physicalRejected).toString(),
                SignalSurfaceReceiveFields.RECOVERED_MISSING to state.recoveredMissingSources.toString(), SignalSurfaceReceiveFields.CRC_VERIFIED to state.messageCrcVerified.toString(),
                SignalSurfaceReceiveFields.CHECKSUM_SHA256 to decoded.expectedSha256, SignalSurfaceReceiveFields.CHECKSUM_VERIFIED to decoded.checksumVerified.toString(),
                SignalSurfaceReceiveFields.CARRIER_HZ to carrierHz.roundToInt().toString(), SignalSurfaceReceiveFields.BIT_MS to bitMs.toString(),
                SignalSurfaceReceiveFields.BASELINE to baseline.toString(), SignalSurfaceReceiveFields.THRESHOLD to threshold.toString(), SignalSurfaceReceiveFields.STATUS to "received", SignalSurfaceReceiveFields.ERROR to ""
            )
            committedJson=fieldsJson(values); status="Committed verified tabletop reception."
            val result=signalResult(As100SignalSurfaceReceiveMethod,context,values); if(context.submitsImmediately) onConfirmed(result)
        }
        fun invalidate() { stop(); baseline=0.0; threshold=0.0; resetWorking("Receiver settings changed. Recalibrate before receiving; committed result remains frozen.") }

        LaunchedEffect(carrierHz,bitMs,calibrationMs,sensitivity) { context.onSettingsChanged(mapOf("surface_profile" to SignalPresetCatalog.closestSurface(carrierHz, bitMs).id, "carrier_hz" to carrierHz.roundToInt().toString(),"bit_ms" to bitMs.toString(),"calibration_ms" to calibrationMs.toString(),"sensitivity" to sensitivity.toString())) }
        SignalActiveSessionOrientationGuard(listening || calibrating)
        DisposableEffect(Unit) { onDispose { engine.stop() } }
        val committedFields=fieldsFromJson(committedJson); val committedResult=remember(committedJson){committedFields.takeIf{it.isNotEmpty()}?.let{signalResult(As100SignalSurfaceReceiveMethod,context,it)}}; val committedFullJson=remember(committedJson){fullJson(committedResult)}

        Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SignalInstrumentPanel("EXPERIMENTAL ACCELEROMETER CONTACT MODEM","Tabletop receiver",SignalAmber,badge=when{content?.checksumVerified==true->"Verified"; calibrating->"Calibrating"; listening->"Listening"; else->"Experimental"}) {
                Text(content?.text?.ifBlank{null} ?: if(collectorState.requiredRank>0) "${collectorState.rank}/${collectorState.requiredRank} SHARDS" else "RESTING SURFACE", color=if(content==null) SignalMuted else SignalText, style=MaterialTheme.typography.headlineSmall,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.SemiBold)
                SignalScopeTrace(trace,SignalAmber)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("VIB", "%.4f".format(currentVibration), SignalAmber, Modifier.weight(1f)); SignalTelemetryTile("SAMPLES", sensorWindows.toString(), SignalAmber, Modifier.weight(1f)); SignalTelemetryTile("THRESH", if(threshold>0) "%.4f".format(threshold) else "—", SignalAmber, Modifier.weight(1f))
                }
                val t=clock.telemetry(); Text("SIGNAL ${if(currentVibration>threshold && threshold>0) "ON" else "rest"}  →  CLOCK ${if(t.clockLocked) "LOCK" else "search"}  →  SYNC ${t.syncDetections}  →  FRAME ${t.physicalFrames}  →  MMS ${collectorState.acceptedFrames}", color=SignalMuted,style=MaterialTheme.typography.labelSmall,fontFamily=FontFamily.Monospace)
                content?.let { SignalStatusPill(if(it.checksumVerified) "SHA-256 MATCH" else "SHA-256 FAIL",if(it.checksumVerified) SignalGreen else SignalRed) }
                Text("Experimental: current phone-pair testing has not produced reliable surface reception. Retained as a research channel.", color=SignalMuted, style=MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick=::startCalibration,modifier=Modifier.weight(1f)){Text(if(calibrating) "Restart calibration" else if(listening) "Recalibrate" else "Calibrate & listen")}; Button(onClick=::commit,enabled=content?.checksumVerified==true,modifier=Modifier.weight(1f)){Text(if(committedResult==null)"Commit verified" else "Recommit")}; OutlinedButton(onClick={stop();resetWorking()}){Text("Reset")} }
            if(committedResult!=null && !context.submitsImmediately) SignalCommittedCard("Committed tabletop reception","received text",committedFields[SignalSurfaceReceiveFields.RESULT].orEmpty(),listOf("SHA-256" to committedFields[SignalSurfaceReceiveFields.CHECKSUM_SHA256].orEmpty(),"Verified" to committedFields[SignalSurfaceReceiveFields.CHECKSUM_VERIFIED].orEmpty(),"Recovered" to committedFields[SignalSurfaceReceiveFields.RECOVERED_MISSING].orEmpty(),"Baseline" to committedFields[SignalSurfaceReceiveFields.BASELINE].orEmpty()),exportStatus,{l,v->copySignalValue(androidContext,l,v)},{exportStatus=shareSignalText(androidContext,"Share tabletop message",committedFields[SignalSurfaceReceiveFields.RESULT].orEmpty())?:""},{exportStatus=saveSignalText(androidContext,"surface_reception",committedFields.entries.joinToString("\n"){"${it.key}=${it.value}"},committedFullJson)},{finishSignalResult(context,androidContext,committedResult,onConfirmed){saveSignalText(androidContext,"surface_reception",committedFields.entries.joinToString("\n"){"${it.key}=${it.value}"},committedFullJson)}})
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Calibration & clock",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                if(context.settingShouldBeShown("surface_profile") || context.settingShouldBeShown("carrier_hz") || context.settingShouldBeShown("bit_ms")){
                    Text("Matched surface profile", style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        SignalPresetCatalog.surfaceProfiles.forEach { profile ->
                            FilterChip(
                                selected=kotlin.math.abs(carrierHz-profile.carrierHz)<0.5 && bitMs==profile.bitMs,
                                onClick={ carrierHz=profile.carrierHz; bitMs=profile.bitMs; clock.setBitMs(bitMs); invalidate() },
                                enabled=!listening && !calibrating,
                                label={Text(profile.id)}
                            )
                        }
                    }
                    val selectedProfile = SignalPresetCatalog.closestSurface(carrierHz, bitMs)
                    Text("${selectedProfile.label}. Match the same letter on the transmitter.", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(context.settingShouldBeShown("calibration_ms")){
                    Text("Rest calibration", style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        SignalPresetCatalog.surfaceCalibrationMs.forEach { ms -> FilterChip(selected=calibrationMs==ms,onClick={calibrationMs=ms;invalidate()},enabled=!listening && !calibrating,label={Text("${ms/1000.0} s")}) }
                    }
                }
                if(context.settingShouldBeShown("sensitivity")){
                    Text("Sensitivity", style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        SignalPresetCatalog.surfaceSensitivity.forEach { value -> FilterChip(selected=kotlin.math.abs(sensitivity-value)<0.01,onClick={sensitivity=value;invalidate()},enabled=!listening && !calibrating,label={Text("${value}×")}) }
                    }
                }
            } }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error); Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(committedResult==null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){if(context.stepNumber>1)OutlinedButton(onClick=onBack,modifier=Modifier.weight(1f)){Text("Back")};OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Cancel")}}
        }
    }
}
