package com.example.methodmesh.modules.soundgenerator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import java.util.Locale

object SoundGeneratorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SoundPlayMethod.ID
    override val title = "Sound generator"
    override val description = "Generate and play tones, noise and frequency sweeps locally."

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val player = remember { AndroidSoundPlayer(androidContext) }
        val supplied = remember(context.request.settings, context.action.settings, context.request.invocationContext) {
            context.request.invocationContext.asMap(context.action.canonicalId) +
                context.request.settings + context.action.settings
        }

        var stimulusType by rememberSaveable { mutableStateOf(supplied.value("stimulus_type", "tone")) }
        var waveform by rememberSaveable { mutableStateOf(supplied.value("waveform", "sine")) }
        var frequencyHz by rememberSaveable { mutableStateOf(supplied.value("frequency_hz", "1000")) }
        var noiseType by rememberSaveable { mutableStateOf(supplied.value("noise_type", "white")) }
        var sweepStartHz by rememberSaveable { mutableStateOf(supplied.value("sweep_start_hz", "250")) }
        var sweepEndHz by rememberSaveable { mutableStateOf(supplied.value("sweep_end_hz", "8000")) }
        var sweepScale by rememberSaveable { mutableStateOf(supplied.value("sweep_scale", "logarithmic")) }
        var levelDbfs by rememberSaveable { mutableStateOf(supplied.value("level_dbfs", "-30")) }
        var durationMs by rememberSaveable { mutableStateOf(supplied.value("duration_ms", "1000")) }
        var channel by rememberSaveable { mutableStateOf(supplied.value("channel", "both")) }
        var fadeMs by rememberSaveable { mutableStateOf(supplied.value("fade_ms", "10")) }
        var gateMode by rememberSaveable { mutableStateOf(supplied.value("gate_mode", "steady")) }
        var pulseOnMs by rememberSaveable { mutableStateOf(supplied.value("pulse_on_ms", "500")) }
        var pulseOffMs by rememberSaveable { mutableStateOf(supplied.value("pulse_off_ms", "500")) }
        var sampleRateHz by rememberSaveable { mutableStateOf(supplied.value("sample_rate_hz", "48000")) }
        var noiseSeedMode by rememberSaveable { mutableStateOf(supplied.value("noise_seed_mode", "secure_random")) }
        var noiseSeed by rememberSaveable { mutableStateOf(supplied.value("noise_seed", "")) }
        var outputDeviceId by rememberSaveable { mutableStateOf(supplied.value("output_device_id", "")) }
        var systemVolumePolicy by rememberSaveable { mutableStateOf(supplied.value("system_volume_policy", "preserve")) }
        var systemVolumePercent by rememberSaveable { mutableStateOf(supplied.value("system_volume_percent", "50")) }

        var resultValuesJson by rememberSaveable { mutableStateOf("") }
        var status by rememberSaveable { mutableStateOf("Ready to play.") }
        var playing by remember { mutableStateOf(false) }
        var autoAttempted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var deviceRefresh by rememberSaveable { mutableStateOf(0) }
        val outputDevices = remember(deviceRefresh) { player.outputDevices() }
        var volumeSnapshot by remember(deviceRefresh) { mutableStateOf(player.currentMediaVolume()) }

        fun currentSettings(): Map<String, String> = linkedMapOf(
            "stimulus_type" to stimulusType,
            "waveform" to waveform,
            "frequency_hz" to frequencyHz,
            "noise_type" to noiseType,
            "sweep_start_hz" to sweepStartHz,
            "sweep_end_hz" to sweepEndHz,
            "sweep_scale" to sweepScale,
            "level_dbfs" to levelDbfs,
            "duration_ms" to durationMs,
            "channel" to channel,
            "fade_ms" to fadeMs,
            "gate_mode" to gateMode,
            "pulse_on_ms" to pulseOnMs,
            "pulse_off_ms" to pulseOffMs,
            "sample_rate_hz" to sampleRateHz,
            "noise_seed_mode" to noiseSeedMode,
            "noise_seed" to noiseSeed,
            "output_device_id" to outputDeviceId,
            "system_volume_policy" to systemVolumePolicy,
            "system_volume_percent" to systemVolumePercent
        )

        fun requestFor(settings: Map<String, String>) = As100SoundPlayMethod.request(
            action = As100SoundPlayMethod.ID,
            context = context.request.invocationContext.asMap(As100SoundPlayMethod.ID) + supplied + settings,
            signals = emptyList(),
            inputs = emptyList()
        )

        fun executionFor(values: Map<String, String>, settings: Map<String, String>): ExecutionResult =
            As100SoundPlayMethod.result(requestFor(settings), values, context.request.invocationContext)

        val restoredValues = remember(resultValuesJson) { jsonToStringMap(resultValuesJson) }
        val restoredResult = remember(resultValuesJson) {
            if (restoredValues.isEmpty()) null else executionFor(restoredValues, currentSettings())
        }

        fun record(values: Map<String, String>, settings: Map<String, String>) {
            resultValuesJson = stringMapToJson(values)
            status = when (values[SoundGeneratorFields.STATUS]) {
                "played" -> "Playback completed."
                "stopped" -> "Playback stopped before the configured duration."
                "interrupted" -> "Playback was interrupted."
                else -> values[SoundGeneratorFields.ERROR].orEmpty().ifBlank { "Playback failed." }
            }
            volumeSnapshot = player.currentMediaVolume()
            val execution = executionFor(values, settings)
            if (context.submitsImmediately) onConfirmed(execution)
        }

        fun startPlayback() {
            if (playing) return
            resultValuesJson = ""
            val settings = currentSettings()
            val spec = runCatching { settings.toSoundSpec() }.getOrElse { error ->
                val values = failureValues(settings, error.message ?: "Invalid sound configuration.")
                record(values, settings)
                return
            }
            status = "Generating stimulus and starting playback…"
            playing = true
            player.play(
                spec = spec,
                settings = PlaybackSettings(
                    requestedDeviceId = outputDeviceId,
                    systemVolumePolicy = systemVolumePolicy,
                    systemVolumePercent = systemVolumePercent.toIntOrNull() ?: 50
                )
            ) { rendered, outcome ->
                playing = false
                val values = playbackValues(settings, spec, rendered, outcome)
                record(values, settings)
            }
        }

        LaunchedEffect(
            stimulusType, waveform, frequencyHz, noiseType, sweepStartHz, sweepEndHz,
            sweepScale, levelDbfs, durationMs, channel, fadeMs, gateMode, pulseOnMs,
            pulseOffMs, sampleRateHz, noiseSeedMode, noiseSeed, outputDeviceId,
            systemVolumePolicy, systemVolumePercent
        ) {
            context.onSettingsChanged(currentSettings())
        }

        LaunchedEffect(context.presentationMode, context.submitsImmediately, context.action.settings) {
            if (
                context.presentationMode == CapabilityPresentationMode.IntentLaunch &&
                context.submitsImmediately && !autoAttempted
            ) {
                autoAttempted = true
                startPlayback()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                if (player.isPlaying()) player.stop()
            }
        }

        val compactPreview = restoredValues.takeIf { it.isNotEmpty() }?.let { values ->
            linkedMapOf(
                SoundGeneratorFields.STATUS to values[SoundGeneratorFields.STATUS].orEmpty(),
                SoundGeneratorFields.SUMMARY to values[SoundGeneratorFields.SUMMARY].orEmpty(),
                SoundGeneratorFields.PCM_SHA256 to values[SoundGeneratorFields.PCM_SHA256].orEmpty(),
                SoundGeneratorFields.ROUTED_DEVICE to values[SoundGeneratorFields.ROUTED_DEVICE].orEmpty(),
                SoundGeneratorFields.ERROR to values[SoundGeneratorFields.ERROR].orEmpty()
            ).filterValues { it.isNotBlank() }
        }.orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = restoredResult,
            resultPreview = compactPreview,
            onBack = onBack,
            onRetry = { startPlayback() },
            onConfirm = { restoredResult?.let(onConfirmed) },
            onCancel = {
                if (playing) player.stop()
                onCancel()
            }
        ) {
            Text(
                "Digital level is not an acoustic dB SPL or dB HL measurement. Output depends on the phone, audio route, headphones/speaker, gain and fit.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            if (context.settingShouldBeShown("stimulus_type")) {
                ChoiceField(
                    "Sound type", stimulusType,
                    listOf("tone" to "Tone", "noise" to "Noise", "sweep" to "Frequency sweep"),
                    enabled = !playing,
                    onSelected = { stimulusType = it }
                )
            }

            if (stimulusType != "noise" && context.settingShouldBeShown("waveform")) {
                ChoiceField(
                    "Waveform", waveform,
                    listOf("sine" to "Sine", "square" to "Square", "triangle" to "Triangle", "sawtooth" to "Sawtooth"),
                    enabled = !playing,
                    onSelected = { waveform = it }
                )
            }
            if (stimulusType == "tone" && context.settingShouldBeShown("frequency_hz")) {
                NumberField("Frequency (Hz)", frequencyHz, !playing) { frequencyHz = it.numericText(allowNegative = false) }
            }
            if (stimulusType == "noise") {
                if (context.settingShouldBeShown("noise_type")) {
                    ChoiceField(
                        "Noise type", noiseType,
                        listOf("white" to "White", "pink" to "Pink", "brown" to "Brown"),
                        enabled = !playing,
                        onSelected = { noiseType = it }
                    )
                }
                if (context.settingShouldBeShown("noise_seed_mode")) {
                    ChoiceField(
                        "Noise seed", noiseSeedMode,
                        listOf("secure_random" to "New random seed each run", "fixed_seed" to "Fixed reproducible seed"),
                        enabled = !playing,
                        onSelected = { noiseSeedMode = it }
                    )
                }
                if (noiseSeedMode == "fixed_seed" && context.settingShouldBeShown("noise_seed")) {
                    OutlinedTextField(
                        value = noiseSeed,
                        onValueChange = { noiseSeed = it },
                        label = { Text("Fixed noise seed") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true,
                        enabled = !playing
                    )
                }
            }
            if (stimulusType == "sweep") {
                if (context.settingShouldBeShown("sweep_start_hz")) NumberField("Sweep start (Hz)", sweepStartHz, !playing) { sweepStartHz = it.numericText(false) }
                if (context.settingShouldBeShown("sweep_end_hz")) NumberField("Sweep end (Hz)", sweepEndHz, !playing) { sweepEndHz = it.numericText(false) }
                if (context.settingShouldBeShown("sweep_scale")) {
                    ChoiceField(
                        "Sweep scale", sweepScale,
                        listOf("logarithmic" to "Logarithmic", "linear" to "Linear"),
                        enabled = !playing,
                        onSelected = { sweepScale = it }
                    )
                }
            }

            if (context.settingShouldBeShown("level_dbfs")) NumberField("Digital peak level (dBFS)", levelDbfs, !playing) { levelDbfs = it.numericText(true) }
            if (context.settingShouldBeShown("duration_ms")) NumberField("Duration (ms)", durationMs, !playing) { durationMs = it.integerText() }
            if (context.settingShouldBeShown("channel")) {
                ChoiceField(
                    "Channel", channel,
                    listOf("both" to "Both", "left" to "Left only", "right" to "Right only"),
                    enabled = !playing,
                    onSelected = { channel = it }
                )
            }
            if (context.settingShouldBeShown("fade_ms")) NumberField("Fade in/out (ms)", fadeMs, !playing) { fadeMs = it.integerText() }
            if (context.settingShouldBeShown("gate_mode")) {
                ChoiceField(
                    "Pattern", gateMode,
                    listOf("steady" to "Steady", "pulsed" to "Pulsed"),
                    enabled = !playing,
                    onSelected = { gateMode = it }
                )
            }
            if (gateMode == "pulsed") {
                if (context.settingShouldBeShown("pulse_on_ms")) NumberField("Pulse on (ms)", pulseOnMs, !playing) { pulseOnMs = it.integerText() }
                if (context.settingShouldBeShown("pulse_off_ms")) NumberField("Pulse off (ms)", pulseOffMs, !playing) { pulseOffMs = it.integerText() }
            }
            if (context.settingShouldBeShown("sample_rate_hz")) {
                ChoiceField(
                    "Sample rate", sampleRateHz,
                    listOf("48000" to "48 kHz", "44100" to "44.1 kHz"),
                    enabled = !playing,
                    onSelected = { sampleRateHz = it }
                )
            }

            if (context.settingShouldBeShown("output_device_id", alwaysShow = true)) {
                DeviceField(
                    selectedId = outputDeviceId,
                    devices = outputDevices,
                    enabled = !playing,
                    onSelected = { outputDeviceId = it }
                )
                OutlinedButton(
                    onClick = { deviceRefresh += 1; volumeSnapshot = player.currentMediaVolume() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !playing
                ) { Text("Refresh audio outputs") }
            }

            if (context.settingShouldBeShown("system_volume_policy")) {
                ChoiceField(
                    "System media volume", systemVolumePolicy,
                    listOf(
                        "preserve" to "Preserve current volume",
                        "require_percent" to "Require configured volume",
                        "temporary_set_percent" to "Set temporarily, then restore"
                    ),
                    enabled = !playing,
                    onSelected = { systemVolumePolicy = it }
                )
            }
            if (systemVolumePolicy != "preserve" && context.settingShouldBeShown("system_volume_percent")) {
                NumberField("Required / temporary media volume (%)", systemVolumePercent, !playing) { systemVolumePercent = it.integerText() }
            }

            val (currentVolume, maxVolume) = volumeSnapshot
            Text(
                "Current Android media volume: $currentVolume/$maxVolume. This is a system gain index, not an acoustic sound-pressure level.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                configuredSummary(currentSettings()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { player.stop(); status = "Stopping playback…" },
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop") }
                }
            } else {
                Button(onClick = { startPlayback() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (restoredResult == null) "Play sound" else "Play again")
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val display = choices.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (choice, choiceLabel) ->
                DropdownMenuItem(
                    text = { Text(choiceLabel) },
                    onClick = { onSelected(choice); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceField(
    selectedId: String,
    devices: List<AudioOutputDevice>,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember(selectedId, devices.size) { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id.toString() == selectedId }
    val display = selected?.displayLabel ?: if (selectedId.isBlank() || selectedId.equals("default", true)) "System default route" else "Unavailable device: $selectedId"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Audio output") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("System default route") }, onClick = { onSelected(""); expanded = false })
            devices.forEach { device ->
                DropdownMenuItem(text = { Text(device.displayLabel) }, onClick = { onSelected(device.id.toString()); expanded = false })
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        enabled = enabled
    )
}

private fun Map<String, String>.value(key: String, default: String): String =
    (this[key] ?: this["input_$key"])?.trim()?.takeIf { it.isNotBlank() } ?: default

private fun Map<String, String>.toSoundSpec(): SoundSpec = SoundSpec(
    stimulusType = value("stimulus_type", "tone"),
    waveform = value("waveform", "sine"),
    frequencyHz = value("frequency_hz", "1000").toDoubleOrNull() ?: error("Frequency must be numeric."),
    noiseType = value("noise_type", "white"),
    sweepStartHz = value("sweep_start_hz", "250").toDoubleOrNull() ?: error("Sweep start must be numeric."),
    sweepEndHz = value("sweep_end_hz", "8000").toDoubleOrNull() ?: error("Sweep end must be numeric."),
    sweepScale = value("sweep_scale", "logarithmic"),
    levelDbfs = value("level_dbfs", "-30").toDoubleOrNull() ?: error("Digital level must be numeric."),
    durationMs = value("duration_ms", "1000").toIntOrNull() ?: error("Duration must be an integer number of milliseconds."),
    channel = value("channel", "both"),
    fadeMs = value("fade_ms", "10").toIntOrNull() ?: error("Fade must be an integer number of milliseconds."),
    gateMode = value("gate_mode", "steady"),
    pulseOnMs = value("pulse_on_ms", "500").toIntOrNull() ?: error("Pulse on-time must be an integer."),
    pulseOffMs = value("pulse_off_ms", "500").toIntOrNull() ?: error("Pulse off-time must be an integer."),
    sampleRateHz = value("sample_rate_hz", "48000").toIntOrNull() ?: error("Sample rate must be numeric."),
    noiseSeedMode = value("noise_seed_mode", "secure_random"),
    noiseSeed = value("noise_seed", "")
)

private fun playbackValues(
    settings: Map<String, String>,
    spec: SoundSpec,
    rendered: RenderedSound?,
    outcome: PlaybackOutcome
): Map<String, String> {
    val error = when {
        outcome.error.isNotBlank() -> outcome.error
        outcome.status == "interrupted" -> "Playback was interrupted by audio-focus loss."
        outcome.status == "stopped" -> "Playback was stopped before the configured stimulus finished."
        else -> ""
    }
    val summary = when (outcome.status) {
        "played" -> "Played ${configuredSummary(settings)}"
        "stopped" -> "Stopped: ${configuredSummary(settings)}"
        "interrupted" -> "Interrupted: ${configuredSummary(settings)}"
        else -> "Failed: ${configuredSummary(settings)}"
    }
    val values = linkedMapOf(
        SoundGeneratorFields.STATUS to outcome.status,
        SoundGeneratorFields.SUMMARY to summary,
        SoundGeneratorFields.STIMULUS_TYPE to spec.stimulusType,
        SoundGeneratorFields.WAVEFORM to if (spec.stimulusType == "noise") "" else spec.waveform,
        SoundGeneratorFields.FREQUENCY_HZ to if (spec.stimulusType == "tone") formatNumber(spec.frequencyHz) else "",
        SoundGeneratorFields.NOISE_TYPE to if (spec.stimulusType == "noise") spec.noiseType else "",
        SoundGeneratorFields.SWEEP_START_HZ to if (spec.stimulusType == "sweep") formatNumber(spec.sweepStartHz) else "",
        SoundGeneratorFields.SWEEP_END_HZ to if (spec.stimulusType == "sweep") formatNumber(spec.sweepEndHz) else "",
        SoundGeneratorFields.SWEEP_SCALE to if (spec.stimulusType == "sweep") spec.sweepScale else "",
        SoundGeneratorFields.LEVEL_DBFS to formatNumber(spec.levelDbfs),
        SoundGeneratorFields.AMPLITUDE_LINEAR to rendered?.let { formatNumber(it.amplitudeLinear, 8) }.orEmpty(),
        SoundGeneratorFields.DURATION_MS to spec.durationMs.toString(),
        SoundGeneratorFields.CHANNEL to spec.channel,
        SoundGeneratorFields.FADE_MS to spec.fadeMs.toString(),
        SoundGeneratorFields.GATE_MODE to spec.gateMode,
        SoundGeneratorFields.PULSE_ON_MS to if (spec.gateMode == "pulsed") spec.pulseOnMs.toString() else "",
        SoundGeneratorFields.PULSE_OFF_MS to if (spec.gateMode == "pulsed") spec.pulseOffMs.toString() else "",
        SoundGeneratorFields.SAMPLE_RATE_HZ to spec.sampleRateHz.toString(),
        SoundGeneratorFields.ALGORITHM_ID to rendered?.algorithmId.orEmpty(),
        SoundGeneratorFields.ALGORITHM_VERSION to rendered?.algorithmVersion.orEmpty(),
        SoundGeneratorFields.NOISE_SEED to rendered?.noiseSeedUsed.orEmpty(),
        SoundGeneratorFields.PCM_SHA256 to rendered?.pcmSha256.orEmpty(),
        SoundGeneratorFields.WRITTEN_PCM_SHA256 to if (outcome.framesWritten > 0) outcome.writtenPcmSha256 else "",
        SoundGeneratorFields.FRAMES_PLANNED to (rendered?.frameCount ?: 0).toString(),
        SoundGeneratorFields.FRAMES_WRITTEN to outcome.framesWritten.toString(),
        SoundGeneratorFields.FRAMES_PLAYED to outcome.framesPlayed.toString(),
        SoundGeneratorFields.REQUESTED_DEVICE to outcome.requestedDevice,
        SoundGeneratorFields.ROUTED_DEVICE to outcome.routedDevice,
        SoundGeneratorFields.ROUTED_DEVICE_ID to outcome.routedDeviceId,
        SoundGeneratorFields.ROUTED_DEVICE_TYPE to outcome.routedDeviceType,
        SoundGeneratorFields.VOLUME_POLICY to settings.value("system_volume_policy", "preserve"),
        SoundGeneratorFields.VOLUME_PERCENT to settings.value("system_volume_percent", "50"),
        SoundGeneratorFields.VOLUME_BEFORE to outcome.mediaVolumeBefore.toString(),
        SoundGeneratorFields.VOLUME_TARGET to outcome.mediaVolumeTarget.toString(),
        SoundGeneratorFields.VOLUME_DURING to outcome.mediaVolumeDuring.toString(),
        SoundGeneratorFields.VOLUME_AFTER to outcome.mediaVolumeAfter.toString(),
        SoundGeneratorFields.VOLUME_MAX to outcome.mediaVolumeMax.toString(),
        SoundGeneratorFields.AUDIO_FOCUS_GRANTED to outcome.audioFocusGranted.toString(),
        SoundGeneratorFields.AUDIO_FOCUS_INTERRUPTED to outcome.audioFocusInterrupted.toString(),
        SoundGeneratorFields.PREFERRED_ROUTE_ACCEPTED to outcome.preferredRouteAccepted.toString(),
        SoundGeneratorFields.STARTED_TIME_ISO to outcome.startedTimeIso,
        SoundGeneratorFields.FINISHED_TIME_ISO to outcome.finishedTimeIso,
        SoundGeneratorFields.ERROR to error
    )
    values[SoundGeneratorFields.AUDIT_JSON] = buildAuditJson(values)
    return values
}

private fun failureValues(settings: Map<String, String>, error: String): Map<String, String> {
    val values = linkedMapOf(
        SoundGeneratorFields.STATUS to "failed",
        SoundGeneratorFields.SUMMARY to "Sound was not played.",
        SoundGeneratorFields.STIMULUS_TYPE to settings.value("stimulus_type", "tone"),
        SoundGeneratorFields.LEVEL_DBFS to settings.value("level_dbfs", "-30"),
        SoundGeneratorFields.DURATION_MS to settings.value("duration_ms", "1000"),
        SoundGeneratorFields.CHANNEL to settings.value("channel", "both"),
        SoundGeneratorFields.VOLUME_POLICY to settings.value("system_volume_policy", "preserve"),
        SoundGeneratorFields.VOLUME_PERCENT to settings.value("system_volume_percent", "50"),
        SoundGeneratorFields.ERROR to error
    )
    values[SoundGeneratorFields.AUDIT_JSON] = buildAuditJson(values)
    return values
}

private fun buildAuditJson(values: Map<String, String>): String = JSONObject().apply {
    put("schema", "methodmesh.sound.play.audit.v1")
    put("method_id", As100SoundPlayMethod.ID)
    put("method_version", "0.1.0")
    put("calibrated_acoustic_output", false)
    put("level_semantics", "peak digital amplitude; linear amplitude = 10^(dBFS/20); not dB SPL or dB HL")
    val fields = JSONObject()
    values.filterKeys { it != SoundGeneratorFields.AUDIT_JSON }.forEach { (key, value) -> fields.put(key, value) }
    put("fields", fields)
}.toString()

private fun configuredSummary(settings: Map<String, String>): String {
    val type = settings.value("stimulus_type", "tone")
    val stimulus = when (type) {
        "noise" -> "${settings.value("noise_type", "white")} noise"
        "sweep" -> "${settings.value("sweep_start_hz", "250")}→${settings.value("sweep_end_hz", "8000")} Hz ${settings.value("sweep_scale", "logarithmic")} ${settings.value("waveform", "sine")} sweep"
        else -> "${settings.value("frequency_hz", "1000")} Hz ${settings.value("waveform", "sine")} tone"
    }
    val pulse = if (settings.value("gate_mode", "steady") == "pulsed") {
        " · pulse ${settings.value("pulse_on_ms", "500")}/${settings.value("pulse_off_ms", "500")} ms"
    } else ""
    return "$stimulus · ${settings.value("level_dbfs", "-30")} dBFS peak · ${settings.value("duration_ms", "1000")} ms · ${settings.value("channel", "both")}$pulse"
}

private fun stringMapToJson(values: Map<String, String>): String = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value) }
}.toString()

private fun jsonToStringMap(json: String): Map<String, String> {
    if (json.isBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(json)
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.optString(key, ""))
            }
        }
    }.getOrDefault(emptyMap())
}

private fun String.integerText(): String = filter(Char::isDigit)

private fun String.numericText(allowNegative: Boolean): String {
    val allowed = filter { it.isDigit() || it == '.' || (allowNegative && it == '-') }
    val minus = if (allowNegative && allowed.startsWith("-")) "-" else ""
    val body = allowed.removePrefix("-")
    val parts = body.split('.')
    return minus + parts.firstOrNull().orEmpty() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
}

private fun formatNumber(value: Double, decimals: Int = 4): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
