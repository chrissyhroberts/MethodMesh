package com.example.methodmesh.modules.signals

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

object SignalMorseTransmitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalMorseTransmitMethod.id
    override val title = "Morse transmitter"
    override val description = "Loop a self-framed Morse beacon over screen, torch or sound so repeated cycles can refine reception."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val activity = remember(androidContext) { androidContext.findActivity() }
        val scope = rememberCoroutineScope()
        val torch = remember(androidContext) { SignalTorchController(androidContext) }
        val settings = context.action.settings

        var payload by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("payload", "hello, world")) }
        var route by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("route", "screen")) }
        val initialWpmRaw = settings.signalSetting("wpm", "5").toIntOrNull()?.coerceIn(3, 30) ?: 5
        val initialWpmOptions = if (route == "sound") SignalPresetCatalog.morseAudioWpm else SignalPresetCatalog.morseOpticalWpm
        var wpm by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialWpmOptions.minByOrNull { kotlin.math.abs(it - initialWpmRaw) } ?: 5) }
        val initialTone = SignalPresetCatalog.closestMorseTone(settings.signalSetting("tone_frequency_hz", "700").toDoubleOrNull() ?: 700.0)
        var toneHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialTone.hz.toFloat()) }
        var loopMode by rememberSaveable(context.action.canonicalId) {
            val stored = settings.signalSetting("loop_mode", "continuous")
            mutableStateOf(if (stored == "once") "count" else stored)
        }
        var repeatCount by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.repeatCounts.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("repeat_count", "3").toIntOrNull() ?: 3)) } ?: 3) }
        var elementGapUnits by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.elementGapUnits.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("element_gap_units", "1.25").toDoubleOrNull() ?: 1.25)) } ?: 1.25) }
        var letterGapUnits by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.letterGapUnits.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("letter_gap_units", "4").toDoubleOrNull() ?: 4.0)) } ?: 4.0) }
        var wordGapUnits by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.wordGapUnits.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("word_gap_units", "9").toDoubleOrNull() ?: 9.0)) } ?: 9.0) }
        var loopGap by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.cycleGapUnits.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("loop_gap_units", "15").toIntOrNull() ?: 15)) } ?: 15) }
        var colourAssist by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("colour_assist", "true").toBooleanStrictOrNull() ?: true) }

        var sending by remember { mutableStateOf(false) }
        var screenOn by remember { mutableStateOf(false) }
        var currentMark by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var completedCycles by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var cameraGranted by remember { mutableStateOf(hasPermission(androidContext, Manifest.permission.CAMERA)) }
        var transmitJob by remember { mutableStateOf<Job?>(null) }
        SignalActiveSessionOrientationGuard(sending)

        val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted || hasPermission(androidContext, Manifest.permission.CAMERA)
            if (!cameraGranted) error = "Camera permission is required for the rear torch route."
        }

        val notation = remember(payload) { MorseCodec.encode(payload) }
        val dotMs = MorseCodec.dotDurationMs(wpm)
        val usesTorch = route in setOf("torch", "torch_sound", "all")
        val usesSound = route in setOf("sound", "screen_sound", "torch_sound", "all")
        val usesScreen = route in setOf("screen", "screen_sound", "all")

        LaunchedEffect(route, wpm) {
            val maxForRoute = if (route == "sound") 30 else 10
            if (wpm > maxForRoute) wpm = maxForRoute
        }

        LaunchedEffect(payload, route, wpm, toneHz, loopMode, repeatCount, elementGapUnits, letterGapUnits, wordGapUnits, loopGap, colourAssist) {
            context.onSettingsChanged(
                mapOf(
                    "payload" to payload,
                    "route" to route,
                    "wpm" to wpm.toString(),
                    "tone_frequency_hz" to toneHz.roundToInt().toString(),
                    "loop_mode" to loopMode,
                    "repeat_count" to repeatCount.toString(),
                    "element_gap_units" to elementGapUnits.toString(),
                    "letter_gap_units" to letterGapUnits.toString(),
                    "word_gap_units" to wordGapUnits.toString(),
                    "loop_gap_units" to loopGap.toString(),
                    "colour_assist" to colourAssist.toString()
                )
            )
        }

        LaunchedEffect(Unit) {
            // Physical transmission does not survive Activity recreation. Persist the
            // working configuration/cycle count, but never pretend the emitter is still on.
            screenOn = false
            if (!sending && status.startsWith("Transmitting", ignoreCase = true)) {
                status = if (completedCycles > 0)
                    "Transmission paused after screen recreation; $completedCycles complete cycle(s) retained."
                else
                    "Transmission paused after screen recreation. Start again when ready."
            }
        }

        fun stopTransmission(userRequested: Boolean = true) {
            transmitJob?.cancel()
            transmitJob = null
            torch.off()
            screenOn = false
            currentMark = ""
            sending = false
            if (userRequested) status = if (completedCycles > 0) "Stopped after $completedCycles complete cycle(s)." else "Stopped."
        }

        fun startTransmission() {
            if (payload.isBlank() || notation.isBlank()) {
                error = "Enter at least one Morse-supported character."
                return
            }
            if (usesTorch && !torch.available) {
                error = "No rear-camera flash unit is available on this device."
                return
            }
            if (usesTorch && !cameraGranted) {
                cameraPermission.launch(Manifest.permission.CAMERA)
                return
            }
            stopTransmission(userRequested = false)
            error = ""
            exportStatus = ""
            completedCycles = 0
            sending = true
            status = "Transmitting…"
            val timeline = MorseCodec.timeline(payload, loopGap)
            val maxCycles = if (loopMode == "count") repeatCount.coerceAtLeast(2) else Int.MAX_VALUE
            transmitJob = scope.launch {
                val audioSession = if (usesSound) withContext(Dispatchers.IO) {
                    SignalAudioOutput.openToneSession(toneHz.toDouble())
                } else null
                try {
                    var cycle = 0
                    while (isActive && cycle < maxCycles) {
                        timeline.forEach { element ->
                            if (!isActive) return@forEach
                            when (element) {
                                is MorseCodec.Element.Mark -> {
                                    val duration = dotMs * element.units
                                    currentMark = when (element.kind) {
                                        MorseCodec.MarkKind.PREAMBLE -> "ACQUIRE"
                                        MorseCodec.MarkKind.START -> "START"
                                        MorseCodec.MarkKind.END -> "END"
                                        MorseCodec.MarkKind.DATA -> element.symbol.toString()
                                    }
                                    if (usesScreen) screenOn = true
                                    if (usesTorch) runCatching { torch.set(true) }.onFailure { error = it.message ?: "Torch failed." }
                                    if (audioSession != null) withContext(Dispatchers.IO) { audioSession.mark(duration) } else delay(duration)
                                    if (usesTorch) runCatching { torch.set(false) }
                                    screenOn = false
                                }
                                is MorseCodec.Element.Gap -> {
                                    currentMark = ""
                                    if (usesTorch) runCatching { torch.set(false) }
                                    screenOn = false
                                    val configuredUnits = when (element.kind) {
                                        MorseCodec.GapKind.ELEMENT -> elementGapUnits
                                        MorseCodec.GapKind.LETTER -> letterGapUnits
                                        MorseCodec.GapKind.WORD -> wordGapUnits
                                        MorseCodec.GapKind.LOOP -> loopGap.toDouble()
                                    }
                                    val duration = (dotMs * configuredUnits).roundToInt().toLong().coerceAtLeast(1L)
                                    if (audioSession != null) withContext(Dispatchers.IO) { audioSession.gap(duration) } else delay(duration)
                                }
                            }
                        }
                        cycle++
                        completedCycles = cycle
                        status = if (maxCycles == Int.MAX_VALUE) "Looping — $cycle cycle(s) sent" else "$cycle / $maxCycles cycle(s) sent"
                    }
                } catch (errorValue: Throwable) {
                    if (isActive) error = errorValue.message ?: "Transmission failed."
                } finally {
                    withContext(Dispatchers.IO) { runCatching { audioSession?.close() } }
                    torch.off()
                    screenOn = false
                    currentMark = ""
                    sending = false
                    if (completedCycles > 0 && loopMode != "continuous") status = "Transmission complete."
                }
            }
        }

        fun commit() {
            if (completedCycles <= 0) {
                error = "Complete at least one transmission cycle before Commit."
                return
            }
            stopTransmission(userRequested = false)
            val values = mapOf(
                SignalMorseTransmitFields.RESULT to payload,
                SignalMorseTransmitFields.PAYLOAD to payload,
                SignalMorseTransmitFields.NOTATION to notation,
                SignalMorseTransmitFields.ROUTE to route,
                SignalMorseTransmitFields.WPM to wpm.toString(),
                SignalMorseTransmitFields.REPETITIONS to completedCycles.toString(),
                SignalMorseTransmitFields.STATUS to "sent",
                SignalMorseTransmitFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed. Live settings remain editable; recommit after another transmission to replace the frozen result."
            val result = signalResult(As100SignalMorseTransmitMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        DisposableEffect(activity, sending, usesScreen) {
            val window = activity?.window
            val previous = window?.attributes?.screenBrightness
            if (window != null && sending && usesScreen) {
                val attributes = window.attributes
                attributes.screenBrightness = 1f
                window.attributes = attributes
            }
            onDispose {
                if (window != null && previous != null) {
                    val attributes = window.attributes
                    attributes.screenBrightness = previous
                    window.attributes = attributes
                }
            }
        }

        DisposableEffect(Unit) { onDispose { stopTransmission(userRequested = false) } }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) {
            committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalMorseTransmitMethod, context, it) }
        }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (sending) stopTransmission() else startTransmission() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (sending) "Stop beacon" else "Start looping beacon") }

            SignalInstrumentPanel(
                kicker = "OPTICAL / AUDIO TELEGRAPH",
                title = "Morse beacon",
                accent = SignalAmber,
                badge = if (sending) "On air" else "Standby"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.55f)
                        .background(
                            if (!screenOn) SignalBlack
                            else if (colourAssist && (currentMark == "-" || currentMark == "START" || currentMark == "END")) Color.Red
                            else Color(0xFFFFFDF5),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (sending) currentMark.ifBlank { "·" } else "READY",
                        color = if (!screenOn) SignalAmber
                        else if (colourAssist && (currentMark == "-" || currentMark == "START" || currentMark == "END")) Color.White
                        else SignalBlack,
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    notation.ifBlank { "—" },
                    color = SignalText,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = notation.isNotBlank()) {
                        copySignalValue(androidContext, "Morse", notation)
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("DOT", "$dotMs ms", SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("SPEED", "$wpm WPM", SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("CYCLES", completedCycles.toString(), SignalAmber, Modifier.weight(1f))
                }
                Text(
                    if (usesScreen && colourAssist)
                        "Colour assist: acquisition/dots are WHITE; START/END/dashes are RED. Timing remains canonical Morse, so monochrome reception still works."
                    else
                        "Every cycle is self-framing: three acquisition flashes → START → message → END → repeat. Partial pre-START text is quarantined until a bounded cycle proves its alignment.",
                    color = SignalMuted, style = MaterialTheme.typography.labelSmall
                )
                Text(
                    if (sending) "${route.replace('_', '+')} • ${if (loopMode == "continuous") "continuous beacon" else "transmitting"}" else status,
                    color = SignalMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(onClick = ::commit, enabled = completedCycles > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (committedResult == null) "Commit" else "Recommit")
            }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed transmission",
                    primaryLabel = "message",
                    primaryValue = committedFields[SignalMorseTransmitFields.RESULT].orEmpty(),
                    fields = listOf(
                        "Morse" to committedFields[SignalMorseTransmitFields.NOTATION].orEmpty(),
                        "Route" to committedFields[SignalMorseTransmitFields.ROUTE].orEmpty(),
                        "WPM" to committedFields[SignalMorseTransmitFields.WPM].orEmpty(),
                        "Cycles" to committedFields[SignalMorseTransmitFields.REPETITIONS].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { exportStatus = shareSignalText(androidContext, "Share Morse message", committedFields[SignalMorseTransmitFields.RESULT].orEmpty()) ?: "" },
                    onSave = { exportStatus = saveSignalText(androidContext, "morse_transmission", committedFields[SignalMorseTransmitFields.RESULT].orEmpty(), committedFullJson) },
                    onDone = { finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "morse_transmission", committedFields[SignalMorseTransmitFields.RESULT].orEmpty(), committedFullJson) } }
                )
            }

            MorseTransmitSettings(
                context = context,
                sending = sending,
                payload = payload,
                onPayload = { next -> if (next != payload) { payload = next; completedCycles = 0 } },
                route = route,
                onRoute = { next ->
                    if (next != route) {
                        route = next
                        val maxForRoute = if (next == "sound") 30 else 10
                        if (wpm > maxForRoute) wpm = maxForRoute
                        completedCycles = 0
                    }
                },
                wpm = wpm,
                onWpm = { next -> if (next != wpm) { wpm = next; completedCycles = 0 } },
                toneHz = toneHz,
                onToneHz = { next -> if (next != toneHz) { toneHz = next; completedCycles = 0 } },
                loopMode = loopMode,
                onLoopMode = { next -> if (next != loopMode) { loopMode = next; completedCycles = 0 } },
                repeatCount = repeatCount,
                onRepeatCount = { next -> if (next != repeatCount) { repeatCount = next; completedCycles = 0 } },
                elementGapUnits = elementGapUnits,
                onElementGapUnits = { next -> if (next != elementGapUnits) { elementGapUnits = next; completedCycles = 0 } },
                letterGapUnits = letterGapUnits,
                onLetterGapUnits = { next -> if (next != letterGapUnits) { letterGapUnits = next; completedCycles = 0 } },
                wordGapUnits = wordGapUnits,
                onWordGapUnits = { next -> if (next != wordGapUnits) { wordGapUnits = next; completedCycles = 0 } },
                loopGap = loopGap,
                onLoopGap = { next -> if (next != loopGap) { loopGap = next; completedCycles = 0 } },
                colourAssist = colourAssist,
                onColourAssist = { next -> if (next != colourAssist) { colourAssist = next; completedCycles = 0 } }
            )

            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (committedResult == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (context.stepNumber > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                }
            }
        }

        if (usesScreen && sending) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        if (!screenOn) Color.Black
                        else if (colourAssist && (currentMark == "-" || currentMark == "START" || currentMark == "END")) Color.Red
                        else Color.White
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        currentMark.ifBlank { " " },
                        color = if (!screenOn) Color.White
                        else if (colourAssist && (currentMark == "-" || currentMark == "START" || currentMark == "END")) Color.White
                        else Color.Black,
                        style = MaterialTheme.typography.displayLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                    Button(
                        onClick = { stopTransmission() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                    ) { Text("Stop") }
                }
            }
        }
    }
}

@Composable
private fun MorseTransmitSettings(
    sending: Boolean,
    context: CapabilityScreenContext,
    payload: String,
    onPayload: (String) -> Unit,
    route: String,
    onRoute: (String) -> Unit,
    wpm: Int,
    onWpm: (Int) -> Unit,
    toneHz: Float,
    onToneHz: (Float) -> Unit,
    loopMode: String,
    onLoopMode: (String) -> Unit,
    repeatCount: Int,
    onRepeatCount: (Int) -> Unit,
    elementGapUnits: Double,
    onElementGapUnits: (Double) -> Unit,
    letterGapUnits: Double,
    onLetterGapUnits: (Double) -> Unit,
    wordGapUnits: Double,
    onWordGapUnits: (Double) -> Unit,
    loopGap: Int,
    onLoopGap: (Int) -> Unit,
    colourAssist: Boolean,
    onColourAssist: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Message & channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (context.settingShouldBeShown("payload")) {
                OutlinedTextField(payload, onPayload, enabled = !sending, modifier = Modifier.fillMaxWidth(), label = { Text("Message") }, supportingText = { Text("Unsupported characters are skipped by the Morse encoder.") })
            }
            if (context.settingShouldBeShown("route")) {
                Text("Output")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("screen", "torch", "sound").forEach { item ->
                        FilterChip(selected = route == item, enabled = !sending, onClick = { onRoute(item) }, label = { Text(item.replaceFirstChar { it.uppercase() }) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("screen_sound", "torch_sound", "all").forEach { item ->
                        FilterChip(selected = route == item, enabled = !sending, onClick = { onRoute(item) }, label = { Text(item.replace('_', '+')) })
                    }
                }
            }
            if (route in setOf("screen", "screen_sound", "all") && context.settingShouldBeShown("colour_assist")) {
                Text("Screen colour assist")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = colourAssist, enabled = !sending, onClick = { onColourAssist(true) }, label = { Text("On") })
                    FilterChip(selected = !colourAssist, enabled = !sending, onClick = { onColourAssist(false) }, label = { Text("Off") })
                }
                Text("On keeps ordinary Morse timing but displays dots/acquisition in white and dashes/START/END in red.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (context.settingShouldBeShown("wpm")) {
                val opticalOutput = route != "sound"
                Text("Speed: $wpm WPM")
                Text(
                    if (opticalOutput) "Optical routes use 5/8/10 WPM; 5 WPM remains the long-range default."
                    else "Audio TX and RX share the same 5/10/20/30 WPM presets; 30 WPM is the module-wide audio ceiling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (opticalOutput) SignalPresetCatalog.morseOpticalWpm else SignalPresetCatalog.morseAudioWpm).forEach { preset ->
                        FilterChip(selected = wpm == preset, enabled = !sending, onClick = { onWpm(preset) }, label = { Text("$preset") })
                    }
                }
            }
            if (context.settingShouldBeShown("tone_frequency_hz") && route.contains("sound")) {
                Text("Audio tone preset")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SignalPresetCatalog.morseTones.forEach { preset ->
                        FilterChip(selected = kotlin.math.abs(toneHz - preset.hz.toFloat()) < 1f, enabled = !sending, onClick = { onToneHz(preset.hz.toFloat()) }, label = { Text(preset.id) })
                    }
                }
                Text(SignalPresetCatalog.closestMorseTone(toneHz.toDouble()).label + " • use the same letter on RX", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (context.settingShouldBeShown("loop_mode")) {
                Text("Repeat")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("count", "continuous").forEach { item ->
                        FilterChip(selected = loopMode == item, enabled = !sending, onClick = { onLoopMode(item) }, label = { Text(item.replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            if (loopMode == "count" && context.settingShouldBeShown("repeat_count")) {
                Text("Cycles")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SignalPresetCatalog.repeatCounts.forEach { value -> FilterChip(selected = repeatCount == value, enabled = !sending, onClick = { onRepeatCount(value) }, label = { Text(value.toString()) }) }
                }
            }
            Text("Spacing presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Between dot/dash elements")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalPresetCatalog.elementGapUnits.forEach { value -> FilterChip(selected = kotlin.math.abs(elementGapUnits - value) < 0.01, enabled = !sending, onClick = { onElementGapUnits(value) }, label = { Text("${value}u") }) }
            }
            Text("Between letters")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalPresetCatalog.letterGapUnits.forEach { value -> FilterChip(selected = kotlin.math.abs(letterGapUnits - value) < 0.01, enabled = !sending, onClick = { onLetterGapUnits(value) }, label = { Text("${value.toInt()}u") }) }
            }
            Text("Between words")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalPresetCatalog.wordGapUnits.forEach { value -> FilterChip(selected = kotlin.math.abs(wordGapUnits - value) < 0.01, enabled = !sending, onClick = { onWordGapUnits(value) }, label = { Text("${value.toInt()}u") }) }
            }
            if (context.settingShouldBeShown("loop_gap_units")) {
                Text("Between complete START→END cycles")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SignalPresetCatalog.cycleGapUnits.forEach { value -> FilterChip(selected = loopGap == value, enabled = !sending, onClick = { onLoopGap(value) }, label = { Text("${value}u") }) }
                }
            }
        }
    }
}

object SignalMorseReceiveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalMorseReceiveMethod.id
    override val title = "Morse receiver"
    override val description = "Decode Morse from camera, microphone, legacy light sensor or a human observer tapping dot/dash with START anchors."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val settings = context.action.settings
        var source by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("source", "camera")) }
        var dotMsSetting by rememberSaveable(context.action.canonicalId) {
            val defaultDot = if (source == "microphone") 60L else 240L
            val raw = settings.signalSetting("dot_ms", defaultDot.toString()).toLongOrNull()?.coerceIn(40, 1000) ?: defaultDot
            val options = if (source == "camera" || source == "light_sensor") SignalPresetCatalog.morseOpticalWpm else SignalPresetCatalog.morseAudioWpm
            val closestWpm = options.minByOrNull { kotlin.math.abs(MorseCodec.dotDurationMs(it) - raw) } ?: 5
            mutableStateOf(MorseCodec.dotDurationMs(closestWpm))
        }
        var autoTiming by rememberSaveable(context.action.canonicalId) {
            val defaultAuto = if (source == "microphone" || source == "manual") "true" else "false"
            mutableStateOf(settings.signalSetting("auto_timing", defaultAuto).toBooleanStrictOrNull() ?: (source == "microphone" || source == "manual"))
        }
        var opticalProfile by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("optical_profile", "screen")) }
        var colourAssistMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.signalSetting("colour_assist", "auto").lowercase().let { if (it == "off") "off" else "auto" }) }
        val initialRxTone = SignalPresetCatalog.closestMorseTone(settings.signalSetting("microphone_tone_hz", "700").toDoubleOrNull() ?: 700.0)
        var toneHz by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialRxTone.hz) }
        var toneTolerance by rememberSaveable(context.action.canonicalId) {
            val raw = settings.signalSetting("microphone_tolerance_hz", initialRxTone.toleranceHz.toString()).toDoubleOrNull() ?: initialRxTone.toleranceHz
            mutableStateOf(SignalPresetCatalog.toneTolerances.minByOrNull { kotlin.math.abs(it.second - raw) }?.second ?: 120.0)
        }
        var minDbfs by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(SignalPresetCatalog.closestLevel(settings.signalSetting("microphone_min_dbfs", "-54").toDoubleOrNull() ?: -54.0).dbfs)
        }
        var elementGapUnits by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.elementGapUnits.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("element_gap_units", "1.25").toDoubleOrNull() ?: 1.25)) } ?: 1.25) }
        var letterGapUnits by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.letterGapUnits.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("letter_gap_units", "4").toDoubleOrNull() ?: 4.0)) } ?: 4.0) }
        var wordGapUnits by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalPresetCatalog.wordGapUnits.minByOrNull { kotlin.math.abs(it - (settings.signalSetting("word_gap_units", "9").toDoubleOrNull() ?: 9.0)) } ?: 9.0) }
        var listening by remember { mutableStateOf(false) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Choose a receiver and start listening.") }
        var error by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var levelText by remember { mutableStateOf("—") }
        var levelOn by remember { mutableStateOf(false) }
        var decodedText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var currentSymbols by remember { mutableStateOf("") }
        var effectiveDotMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(dotMsSetting) }
        var pulsesSeen by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var consensusCopies by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var consensusConfidence by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.0) }
        var framedSignal by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var frameState by remember { mutableStateOf(MorseTimingDecoder.FrameState.SEEKING_START) }
        var orphanObservations by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var unanimousCharacters by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var consensusCharacters by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var cameraGranted by remember { mutableStateOf(hasPermission(androidContext, Manifest.permission.CAMERA)) }
        var audioGranted by remember { mutableStateOf(hasPermission(androidContext, Manifest.permission.RECORD_AUDIO)) }
        var cameraZoom by rememberSaveable(context.action.canonicalId) { mutableStateOf(1f) }
        var cameraActualZoom by remember { mutableStateOf(1f) }
        var cameraMaxZoom by remember { mutableStateOf(4f) }
        var cameraRoiMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(SignalCameraRoiMode.FOCUS.name) }
        var cameraRoiX by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.5f) }
        var cameraRoiY by rememberSaveable(context.action.canonicalId) { mutableStateOf(0.5f) }
        var cameraAutoLock by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var cameraSearchGeneration by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var cameraLockState by remember { mutableStateOf(SignalCameraLockState.MANUAL) }
        var cameraLockConfidence by remember { mutableStateOf(0.0) }
        var colourAssistStatus by remember { mutableStateOf("timing only") }
        SignalActiveSessionOrientationGuard(listening)

        val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it || hasPermission(androidContext, Manifest.permission.CAMERA) }
        val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { audioGranted = it || hasPermission(androidContext, Manifest.permission.RECORD_AUDIO) }

        val decoder = remember(dotMsSetting, autoTiming, source, elementGapUnits, letterGapUnits, wordGapUnits) {
            val optical = source != "microphone"
            MorseTimingDecoder(
                initialDotMs = dotMsSetting,
                autoTiming = autoTiming,
                minimumDotMs = if (optical) 120L else 40L,
                maximumDotMs = 1000L,
                elementGapUnits = elementGapUnits,
                letterGapUnits = letterGapUnits,
                wordGapUnits = wordGapUnits
            )
        }
        val manualDecoder = remember(dotMsSetting, source) { MorseManualTapDecoder(initialDotMs = dotMsSetting) }
        val lightDetector = remember { AdaptiveLevelDetector(minimumSpan = 1.0) }
        val cameraDetector = remember { AdaptiveLevelDetector(minimumSpan = 14.0) }
        val cameraStableGate = remember(opticalProfile) { MorseStableSignalGate(if (opticalProfile == "torch") 3 else 2) }
        val colourCalibrator = remember { MorseColourAssistCalibrator() }
        val colourTracker = remember { MorseColourMarkTracker(colourCalibrator) }
        val microphoneEngine = remember(androidContext) { SignalToneReceiverEngine(androidContext) }

        fun acceptSignal(on: Boolean, timestampMs: Long, markHint: Char? = null, markHintConfidence: Double = 0.0) {
            if (!listening) return
            levelOn = on
            val snapshot = decoder.update(on, timestampMs, markHint, markHintConfidence)
            decodedText = snapshot.decodedText
            currentSymbols = snapshot.currentSymbols
            effectiveDotMs = snapshot.dotMs
            pulsesSeen = snapshot.pulsesSeen
            consensusCopies = snapshot.completedCopies
            consensusConfidence = snapshot.consensusConfidence
            framedSignal = snapshot.framed
            frameState = snapshot.frameState
            orphanObservations = snapshot.orphanObservations
            unanimousCharacters = snapshot.unanimousCharacters
            consensusCharacters = snapshot.characterCount
            status = snapshot.quality
        }

        fun acceptManual(snapshot: MorseManualTapDecoder.Snapshot) {
            decodedText = snapshot.decodedText
            currentSymbols = snapshot.currentSymbols
            effectiveDotMs = snapshot.dotMs
            pulsesSeen = snapshot.marksSeen
            consensusCopies = snapshot.completedCopies
            consensusConfidence = snapshot.consensusConfidence
            framedSignal = snapshot.anchored
            frameState = if (!snapshot.anchored) MorseTimingDecoder.FrameState.SEEKING_START
                else if (snapshot.completedCopies > 0) MorseTimingDecoder.FrameState.HAVE_COMPLETE_FRAME
                else MorseTimingDecoder.FrameState.IN_FRAME
            orphanObservations = 0
            unanimousCharacters = snapshot.unanimousCharacters
            consensusCharacters = snapshot.characterCount
            levelOn = false
            levelText = "human observer"
            status = snapshot.quality
        }

        fun invalidateWorkingReception() {
            decodedText = ""
            currentSymbols = ""
            effectiveDotMs = dotMsSetting
            pulsesSeen = 0
            consensusCopies = 0
            consensusConfidence = 0.0
            framedSignal = false
            frameState = MorseTimingDecoder.FrameState.SEEKING_START
            orphanObservations = 0
            unanimousCharacters = 0
            consensusCharacters = 0
            cameraStableGate.reset()
            colourTracker.reset()
            colourAssistStatus = "timing only"
            manualDecoder.reset()
            levelOn = false
            levelText = "—"
            status = "Receiver settings changed; working decode was cleared. Any committed result remains frozen."
            error = ""
        }

        fun restartCameraWorkingReception(message: String) {
            cameraDetector.reset()
            decoder.reset(System.currentTimeMillis())
            decodedText = ""
            currentSymbols = ""
            effectiveDotMs = dotMsSetting
            pulsesSeen = 0
            consensusCopies = 0
            consensusConfidence = 0.0
            framedSignal = false
            frameState = MorseTimingDecoder.FrameState.SEEKING_START
            orphanObservations = 0
            unanimousCharacters = 0
            consensusCharacters = 0
            cameraStableGate.reset()
            colourTracker.reset()
            colourAssistStatus = "timing only"
            levelOn = false
            levelText = "—"
            if (listening && source == "camera") status = message
            error = ""
        }

        fun startCameraSearch() {
            cameraAutoLock = true
            cameraSearchGeneration += 1
            cameraLockState = SignalCameraLockState.SEARCHING
            cameraLockConfidence = 0.0
            restartCameraWorkingReception("Searching the frame for a flashing source…")
        }

        fun setManualCameraRoi(x: Float, y: Float) {
            cameraAutoLock = false
            cameraLockState = SignalCameraLockState.MANUAL
            cameraRoiX = x.coerceIn(0f, 1f)
            cameraRoiY = y.coerceIn(0f, 1f)
            restartCameraWorkingReception("ROI moved. Listening restarted at the selected point.")
        }

        fun startListening() {
            error = ""
            if (source == "camera" && !cameraGranted) {
                cameraPermission.launch(Manifest.permission.CAMERA)
                return
            }
            if (source == "microphone" && !audioGranted) {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            decoder.reset(System.currentTimeMillis())
            manualDecoder.reset()
            lightDetector.reset()
            cameraDetector.reset()
            decodedText = ""
            currentSymbols = ""
            pulsesSeen = 0
            consensusCopies = 0
            consensusConfidence = 0.0
            framedSignal = false
            frameState = MorseTimingDecoder.FrameState.SEEKING_START
            orphanObservations = 0
            unanimousCharacters = 0
            consensusCharacters = 0
            cameraStableGate.reset()
            colourTracker.reset()
            colourAssistStatus = "timing only"
            effectiveDotMs = dotMsSetting
            listening = true
            status = if (source == "manual") "Manual receiver ready — tap START SIGNAL when you see the start marker." else "Listening…"
            if (source == "microphone") {
                microphoneEngine.start(
                    toneHz = toneHz,
                    toleranceHz = toneTolerance,
                    minimumDbfs = minDbfs,
                    onWindow = { window ->
                        levelText = "${toneHz.roundToInt()} Hz  ${"%.1f".format(Locale.US, window.dbfs)} dBFS  ${window.audioSource}"
                        acceptSignal(window.isOn, window.timestampMs)
                    },
                    onError = { error = it; listening = false }
                )
            }
        }

        fun stopListening() {
            if (source == "microphone") microphoneEngine.stop()
            if (source == "manual") {
                val snapshot = manualDecoder.snapshot("Manual receiver paused. Only START-bounded observations are committable.")
                acceptManual(snapshot)
                listening = false
                return
            }
            val snapshot = decoder.flush(System.currentTimeMillis())
            decodedText = snapshot.decodedText
            currentSymbols = snapshot.currentSymbols
            effectiveDotMs = snapshot.dotMs
            pulsesSeen = snapshot.pulsesSeen
            consensusCopies = snapshot.completedCopies
            consensusConfidence = snapshot.consensusConfidence
            framedSignal = snapshot.framed
            frameState = snapshot.frameState
            orphanObservations = snapshot.orphanObservations
            unanimousCharacters = snapshot.unanimousCharacters
            consensusCharacters = snapshot.characterCount
            listening = false
            levelOn = false
            status = when {
                consensusCopies > 0 -> "Stopped — bounded consensus is ready to Commit."
                decodedText.isNotBlank() -> "Stopped — only an incomplete START-anchored copy was seen; no canonical result yet."
                else -> "Stopped — no complete START→END Morse cycle decoded."
            }
        }

        fun commit() {
            if (decodedText.isBlank() || consensusCopies <= 0) {
                error = if (source == "manual") "Tap START SIGNAL again to close at least one observed copy before Commit." else "Wait for at least one complete START→END cycle before Commit. Partial/orphan text is deliberately not committable."
                return
            }
            if (listening) stopListening()
            val notation = MorseCodec.encode(decodedText)
            val values = mapOf(
                SignalMorseReceiveFields.RESULT to decodedText,
                SignalMorseReceiveFields.NOTATION to notation,
                SignalMorseReceiveFields.SOURCE to source,
                SignalMorseReceiveFields.DOT_MS to effectiveDotMs.toString(),
                SignalMorseReceiveFields.TRANSITIONS to pulsesSeen.toString(),
                SignalMorseReceiveFields.COPIES to consensusCopies.toString(),
                SignalMorseReceiveFields.CONSENSUS_CONFIDENCE to String.format(Locale.US, "%.4f", consensusConfidence),
                SignalMorseReceiveFields.STATUS to "received",
                SignalMorseReceiveFields.ERROR to ""
            )
            committedJson = fieldsJson(values)
            status = "Committed. Continue listening only after starting a new reception."
            val result = signalResult(As100SignalMorseReceiveMethod, context, values)
            if (context.submitsImmediately) onConfirmed(result)
        }

        LaunchedEffect(source, dotMsSetting) {
            if ((source == "camera" || source == "light_sensor") && dotMsSetting < 120L) dotMsSetting = 120L
        }

        LaunchedEffect(source, dotMsSetting, autoTiming, opticalProfile, colourAssistMode, toneHz, toneTolerance, minDbfs, elementGapUnits, letterGapUnits, wordGapUnits) {
            context.onSettingsChanged(
                mapOf(
                    "source" to source,
                    "dot_ms" to dotMsSetting.toString(),
                    "auto_timing" to autoTiming.toString(),
                    "optical_profile" to opticalProfile,
                    "colour_assist" to colourAssistMode,
                    "microphone_tone_hz" to toneHz.roundToInt().toString(),
                    "microphone_tolerance_hz" to toneTolerance.roundToInt().toString(),
                    "microphone_min_dbfs" to minDbfs.toString(),
                    "element_gap_units" to elementGapUnits.toString(),
                    "letter_gap_units" to letterGapUnits.toString(),
                    "word_gap_units" to wordGapUnits.toString()
                )
            )
        }

        LaunchedEffect(Unit) {
            // Camera/microphone/listener registrations are process objects, not saved UI
            // state. Preserve decoded working text but require an explicit restart.
            if (!listening && (status.startsWith("Listening", ignoreCase = true) || status.startsWith("Tracking", ignoreCase = true))) {
                status = if (decodedText.isBlank())
                    "Reception paused after screen recreation. Start listening again."
                else
                    "Reception paused after screen recreation; decoded working text is retained."
            }
        }

        DisposableEffect(androidContext) {
            PhoneSensorRepository.start(androidContext)
            onDispose {
                microphoneEngine.stop()
                PhoneSensorRepository.stop()
            }
        }

        val lux = PhoneSensorRepository.readings["light"]?.values?.firstOrNull()?.toDouble()
        LaunchedEffect(lux, listening, source) {
            if (listening && source == "light_sensor" && lux != null) {
                val detection = lightDetector.feed(lux)
                levelText = "${"%.1f".format(Locale.US, lux)} lx • span ${"%.1f".format(Locale.US, detection.span.takeIf { it.isFinite() } ?: 0.0)}"
                detection.state?.let { acceptSignal(it, System.currentTimeMillis()) }
            }
        }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalMorseReceiveMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (source != "manual") {
                Button(
                    onClick = { if (listening) stopListening() else startListening() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (listening) "Stop listening" else "Start listening") }
            }

            if (source == "manual") {
                Card(
                    Modifier.fillMaxWidth().height(270.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SignalBlack)
                ) {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("HUMAN MORSE", color = SignalAmber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(if (listening) "LIVE" else "READY", color = if (listening) SignalGreen else SignalMuted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            decodedText.ifBlank { "WAITING FOR START SIGNAL" },
                            modifier = Modifier.fillMaxWidth().weight(1f).clickable(enabled = decodedText.isNotBlank()) {
                                copySignalValue(androidContext, "decoded Morse", decodedText)
                            },
                            color = if (decodedText.isBlank()) SignalMuted else SignalText,
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (currentSymbols.isBlank()) "· · ·" else currentSymbols,
                            color = SignalAmber,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SignalTelemetryTile("BEST", if (consensusCopies > 0) "${(consensusConfidence * 100).roundToInt()}%" else "acquiring", SignalGreen, Modifier.weight(1f))
                            SignalTelemetryTile("COPIES", consensusCopies.toString(), SignalAmber, Modifier.weight(1f))
                            SignalTelemetryTile("DOT", "~${effectiveDotMs} ms", SignalAmber, Modifier.weight(1f))
                        }
                    }
                }

                Button(
                    onClick = {
                        if (!listening) listening = true
                        acceptManual(manualDecoder.startSignal(System.currentTimeMillis()))
                    },
                    modifier = Modifier.fillMaxWidth().height(78.dp)
                ) { Text("START SIGNAL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

                Row(Modifier.fillMaxWidth().height(290.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { if (listening) acceptManual(manualDecoder.tapDot(System.currentTimeMillis())) },
                        enabled = listening,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    ) { Text("DOT\n·", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { if (listening) acceptManual(manualDecoder.tapDash(System.currentTimeMillis())) },
                        enabled = listening,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    ) { Text("DASH\n—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { if (listening) stopListening() else startListening() }, modifier = Modifier.weight(1f)) {
                        Text(if (listening) "Pause" else "Resume")
                    }
                    Text(
                        "Eyes stay on the signal: START anchors each copy; the next START closes it. Tap only dot or dash.",
                        modifier = Modifier.weight(2f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (source != "manual") {
            SignalInstrumentPanel(
                kicker = "ADAPTIVE TELEGRAPH DECODER",
                title = "Morse receiver",
                accent = SignalAmber,
                badge = if (listening) "Live" else "Idle"
            ) {
                Text(
                    decodedText.ifBlank { "WAITING FOR MESSAGE" },
                    modifier = Modifier.fillMaxWidth().clickable(enabled = decodedText.isNotBlank()) {
                        copySignalValue(androidContext, "decoded Morse", decodedText)
                    },
                    color = if (decodedText.isBlank()) SignalMuted else SignalText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (currentSymbols.isBlank()) "· · ·" else currentSymbols,
                    color = SignalAmber,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("SOURCE", source.replace('_', ' '), SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("DOT", "~${effectiveDotMs} ms", SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("CYCLES", consensusCopies.toString(), SignalAmber, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalTelemetryTile("BEST GUESS", if (consensusCopies > 0) "${(consensusConfidence * 100).roundToInt()}%" else "acquiring", SignalGreen, Modifier.weight(1f))
                    SignalTelemetryTile("FRAME", when (frameState) {
                        MorseTimingDecoder.FrameState.SEEKING_START -> "seeking"
                        MorseTimingDecoder.FrameState.IN_FRAME -> "START→…"
                        MorseTimingDecoder.FrameState.HAVE_COMPLETE_FRAME -> "bounded"
                    }, SignalAmber, Modifier.weight(1f))
                    SignalTelemetryTile("MARKS", pulsesSeen.toString(), SignalAmber, Modifier.weight(1f))
                }
                Text("LEVEL ${if (levelOn) "ON" else "OFF"}", color = if (levelOn) SignalAmber else SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Text(levelText, color = SignalMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                if (consensusCopies > 0) {
                    Text("$unanimousCharacters/$consensusCharacters characters unanimous • $orphanObservations END-anchored orphan observation(s)", color = SignalMuted, style = MaterialTheme.typography.labelSmall)
                } else if (orphanObservations > 0) {
                    Text("Orphan suffix buffered but excluded from the best guess until a complete START→END cycle is seen.", color = SignalMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            }

            if (source == "camera" && listening && cameraGranted) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SignalBlack)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.fillMaxWidth().height(290.dp)) {
                            SignalCameraLumaPreview(
                                zoomRatio = cameraZoom,
                                roiMode = runCatching { SignalCameraRoiMode.valueOf(cameraRoiMode) }.getOrDefault(SignalCameraRoiMode.FOCUS),
                                roiCenterX = cameraRoiX,
                                roiCenterY = cameraRoiY,
                                autoLock = cameraAutoLock,
                                lockState = cameraLockState,
                                searchGeneration = cameraSearchGeneration,
                                exposureReduction = if (opticalProfile == "torch") 0.55f else 0.25f,
                                opticalProfile = opticalProfile,
                                onRoiMoved = { x, y -> setManualCameraRoi(x, y) },
                                onCameraInfo = { info ->
                                    cameraActualZoom = info.actualZoomRatio
                                    cameraMaxZoom = info.maxZoomRatio.coerceAtLeast(1f)
                                },
                                onSample = sampleLoop@ { sample ->
                                    val previousLock = cameraLockState
                                    cameraLockState = sample.lockState
                                    cameraLockConfidence = sample.lockConfidence
                                    if (cameraAutoLock && sample.lockState == SignalCameraLockState.LOCKED) {
                                        cameraRoiX = sample.roiCenterX
                                        cameraRoiY = sample.roiCenterY
                                    }
                                    if (cameraAutoLock && sample.lockState != SignalCameraLockState.LOCKED) {
                                        levelOn = false
                                        levelText = when (sample.lockState) {
                                            SignalCameraLockState.LOST -> "lock lost • reacquiring"
                                            else -> "searching • modulation ${"%.1f".format(Locale.US, sample.modulationScore)}"
                                        }
                                        return@sampleLoop
                                    }
                                    if (cameraAutoLock && previousLock != SignalCameraLockState.LOCKED && sample.lockState == SignalCameraLockState.LOCKED) {
                                        cameraDetector.reset()
                                        cameraStableGate.reset()
                                        colourTracker.reset()
                                        colourAssistStatus = "timing only"
                                        decoder.reset(sample.timestampMs)
                                        decodedText = ""
                                        currentSymbols = ""
                                        pulsesSeen = 0
                                        effectiveDotMs = dotMsSetting
                                    }
                                    val colourEnabled = colourAssistMode == "auto" && opticalProfile == "screen"
                                    val activity = if (colourEnabled) MorseColourAssistCalibrator.activityLevel(sample.luma, sample.chromaV) else sample.luma
                                    val detection = cameraDetector.feed(activity)
                                    levelText = buildString {
                                        append("luma ${"%.0f".format(Locale.US, sample.luma)}")
                                        if (colourEnabled) append(" • activity ${"%.0f".format(Locale.US, activity)}")
                                        append(" • span ${"%.0f".format(Locale.US, detection.span.takeIf { it.isFinite() } ?: 0.0)}")
                                        if (colourEnabled) append(" • $colourAssistStatus")
                                    }
                                    detection.state?.let { rawState ->
                                        val stableState = cameraStableGate.feed(rawState)
                                        if (colourEnabled) {
                                            if (stableState == true) colourTracker.start(sample.timestampMs)
                                            if (rawState) colourTracker.observe(sample.chromaU, sample.chromaV)
                                        }
                                        stableState?.let { stable ->
                                            if (!stable && colourEnabled) {
                                                val colour = colourTracker.finish(sample.timestampMs, effectiveDotMs, frameState)
                                                colourAssistStatus = when {
                                                    colour?.calibrationEvent != null -> colour.calibrationEvent
                                                    colour?.hint != null -> "${if (colour.hint == '.') "WHITE→dot" else "RED→dash"} ${"%.0f".format(Locale.US, colour.confidence * 100)}%"
                                                    else -> colourCalibrator.calibrationLabel()
                                                }
                                                acceptSignal(false, sample.timestampMs, colour?.hint, colour?.confidence ?: 0.0)
                                            } else {
                                                acceptSignal(stable, sample.timestampMs)
                                            }
                                        }
                                    }
                                },
                                onError = { error = it; listening = false }
                            )
                        }

                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("OPTICAL FRONT END", color = SignalAmber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("${"%.1f".format(Locale.US, cameraActualZoom)}×", color = SignalText, fontFamily = FontFamily.Monospace)
                            }
                            if (context.settingShouldBeShown("optical_profile")) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("screen" to "Screen", "torch" to "Torch / point").forEach { (id, label) ->
                                        FilterChip(
                                            selected = opticalProfile == id,
                                            onClick = {
                                                if (opticalProfile != id) {
                                                    opticalProfile = id
                                                    if (id == "torch") cameraRoiMode = SignalCameraRoiMode.PINPOINT.name
                                                    cameraStableGate.reset()
                                                    restartCameraWorkingReception("$label optical profile selected. Exposure and edge filtering restarted.")
                                                }
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                            Text(
                                if (opticalProfile == "screen") "Screen mode underexposes slightly, requires 2 stable frames, and can fuse WHITE-dot / RED-dash chroma with timing while falling back cleanly to monochrome Morse."
                                else "Torch mode underexposes more, uses a small ROI and requires 3 stable frames so flare decay does not become extra edges.",
                                color = SignalMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1f to "1×", 2f to "2×", 4f to "4×", cameraMaxZoom to "Max").forEach { (target, label) ->
                                    val effective = target.coerceAtMost(cameraMaxZoom).coerceAtLeast(1f)
                                    FilterChip(
                                        selected = abs(cameraZoom - effective) < 0.15f,
                                        onClick = {
                                            cameraZoom = effective
                                            restartCameraWorkingReception("Zoom changed. Camera decode restarted.")
                                        },
                                        enabled = cameraMaxZoom > 1.01f,
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Text("Analysis ROI", color = SignalMuted, style = MaterialTheme.typography.labelMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SignalCameraRoiMode.values().forEach { mode ->
                                    FilterChip(
                                        selected = cameraRoiMode == mode.name,
                                        onClick = {
                                            cameraRoiMode = mode.name
                                            restartCameraWorkingReception("${mode.label} ROI selected. Camera decode restarted.")
                                        },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = ::startCameraSearch, modifier = Modifier.weight(1f)) {
                                    Text(if (cameraAutoLock && cameraLockState == SignalCameraLockState.LOCKED) "Reacquire" else "Find signal")
                                }
                                OutlinedButton(
                                    onClick = {
                                        cameraAutoLock = false
                                        cameraLockState = SignalCameraLockState.MANUAL
                                        restartCameraWorkingReception("Manual ROI active. Tap the preview to move it.")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Manual ROI") }
                            }
                            val lockLabel = when (cameraLockState) {
                                SignalCameraLockState.LOCKED -> "LOCKED  ${"%.2f".format(Locale.US, cameraLockConfidence)}× contrast over next candidate"
                                SignalCameraLockState.SEARCHING -> "SEARCHING FOR TEMPORAL MODULATION"
                                SignalCameraLockState.LOST -> "LOCK LOST • LOCAL REACQUISITION"
                                SignalCameraLockState.MANUAL -> "MANUAL ROI • TAP PREVIEW TO POSITION"
                            }
                            Text(lockLabel, color = when (cameraLockState) {
                                SignalCameraLockState.LOCKED -> SignalGreen
                                SignalCameraLockState.LOST -> SignalRed
                                else -> SignalAmber
                            }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Button(
                onClick = ::commit,
                enabled = decodedText.isNotBlank() && consensusCopies > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (committedResult == null) "Commit" else "Recommit") }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed reception",
                    primaryLabel = "decoded text",
                    primaryValue = committedFields[SignalMorseReceiveFields.RESULT].orEmpty(),
                    fields = listOf(
                        "Morse" to committedFields[SignalMorseReceiveFields.NOTATION].orEmpty(),
                        "Source" to committedFields[SignalMorseReceiveFields.SOURCE].orEmpty(),
                        "Dot ms" to committedFields[SignalMorseReceiveFields.DOT_MS].orEmpty(),
                        "Marks" to committedFields[SignalMorseReceiveFields.TRANSITIONS].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { exportStatus = shareSignalText(androidContext, "Share decoded Morse", committedFields[SignalMorseReceiveFields.RESULT].orEmpty()) ?: "" },
                    onSave = { exportStatus = saveSignalText(androidContext, "morse_reception", committedFields[SignalMorseReceiveFields.RESULT].orEmpty(), committedFullJson) },
                    onDone = { finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "morse_reception", committedFields[SignalMorseReceiveFields.RESULT].orEmpty(), committedFullJson) } }
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Receiver", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (context.settingShouldBeShown("source")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val nativeSources = buildList {
                                if (source == "light_sensor") add("light_sensor" to "Lux · legacy")
                                add("camera" to "Camera")
                                add("microphone" to "Mic")
                                add("manual" to "Manual")
                            }
                            nativeSources.forEach { (id, label) ->
                                FilterChip(selected = source == id, enabled = !listening, onClick = {
                                    if (source != id) {
                                        source = id
                                        if ((id == "camera" || id == "light_sensor") && dotMsSetting < 120L) dotMsSetting = 240L
                                        autoTiming = id == "microphone" || id == "manual"
                                        invalidateWorkingReception()
                                    }
                                }, label = { Text(label) })
                            }
                        }
                    }
                    if (context.settingShouldBeShown("dot_ms")) {
                        val startingWpm = (1200.0 / dotMsSetting.toDouble()).roundToInt().coerceAtLeast(1)
                        Text("Starting speed: ~$startingWpm WPM  ·  $dotMsSetting ms dot")
                        Text("PARIS timing uses dot = 1200 / WPM. TX and RX use the same discrete speed presets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val opticalSource = source == "camera" || source == "light_sensor"
                        Text(
                            if (opticalSource) "Camera/light timing is capped at 10 WPM; 5 WPM is the long-range default."
                            else "Audio TX and RX are capped at 30 WPM (40 ms dot).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (if (opticalSource) SignalPresetCatalog.morseOpticalWpm else SignalPresetCatalog.morseAudioWpm).forEach { wpm ->
                                val ms = MorseCodec.dotDurationMs(wpm)
                                FilterChip(selected = kotlin.math.abs(dotMsSetting - ms) <= 5, enabled = !listening, onClick = { if (dotMsSetting != ms) { dotMsSetting = ms; invalidateWorkingReception() } }, label = { Text("$wpm WPM") })
                            }
                        }
                    }
                    if (source == "manual") {
                        Text("Manual mode treats your dot/dash choice as authoritative and uses tap timing only to infer element, letter and word spacing. Starting speed is a weak prior, not a lock.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (source == "camera") {
                        Text("Optical timing is most reliable locked to the sender speed; 5 WPM is the default test profile. Auto timing is optional because rolling shutter and torch decay bias ON and OFF durations differently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (opticalProfile == "screen" && context.settingShouldBeShown("colour_assist")) {
                            Text("Colour-assisted Morse")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = colourAssistMode == "auto", enabled = !listening, onClick = { if (colourAssistMode != "auto") { colourAssistMode = "auto"; invalidateWorkingReception() } }, label = { Text("Auto") })
                                FilterChip(selected = colourAssistMode == "off", enabled = !listening, onClick = { if (colourAssistMode != "off") { colourAssistMode = "off"; invalidateWorkingReception() } }, label = { Text("Off") })
                            }
                            Text("Auto learns WHITE from acquisition dots and RED from START, then fuses colour with duration. If colour separation is weak it becomes ordinary timing-only Morse automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (context.settingShouldBeShown("auto_timing") && source != "manual") {
                        Text("Timing mode")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = !autoTiming, enabled = !listening, onClick = { if (autoTiming) { autoTiming = false; invalidateWorkingReception() } }, label = { Text("Locked") })
                            FilterChip(selected = autoTiming, enabled = !listening, onClick = { if (!autoTiming) { autoTiming = true; invalidateWorkingReception() } }, label = { Text("Auto") })
                        }
                        Text("Locked is recommended for camera reception; Auto estimates the dot unit from repeated marks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (source == "microphone") {
                        if (context.settingShouldBeShown("microphone_tone_hz")) {
                            Text("Audio tone preset")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SignalPresetCatalog.morseTones.forEach { preset ->
                                    FilterChip(selected = kotlin.math.abs(toneHz - preset.hz) < 1.0, enabled = !listening, onClick = {
                                        if (toneHz != preset.hz) { toneHz = preset.hz; toneTolerance = preset.toleranceHz; invalidateWorkingReception() }
                                    }, label = { Text(preset.id) })
                                }
                            }
                            Text(SignalPresetCatalog.closestMorseTone(toneHz).label + " • same A–D catalogue as TX", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (context.settingShouldBeShown("microphone_tolerance_hz")) {
                            Text("Tone tolerance")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SignalPresetCatalog.toneTolerances.forEach { (label, value) ->
                                    FilterChip(selected = kotlin.math.abs(toneTolerance - value) < 1.0, enabled = !listening, onClick = { if (toneTolerance != value) { toneTolerance = value; invalidateWorkingReception() } }, label = { Text(label) })
                                }
                            }
                            Text("±${toneTolerance.roundToInt()} Hz", style = MaterialTheme.typography.bodySmall)
                        }
                        if (context.settingShouldBeShown("microphone_min_dbfs")) {
                            Text("Minimum microphone level")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SignalPresetCatalog.levelThresholds.forEach { preset ->
                                    FilterChip(selected = kotlin.math.abs(minDbfs - preset.dbfs) < 0.5, enabled = !listening, onClick = { if (minDbfs != preset.dbfs) { minDbfs = preset.dbfs; invalidateWorkingReception() } }, label = { Text(preset.id) })
                                }
                            }
                            Text("Current threshold: ${"%.0f".format(Locale.US, minDbfs)} dBFS", style = MaterialTheme.typography.bodySmall)
                            SignalDbfsHelp()
                        }
                    }
                    Text("Expected spacing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Between dot/dash elements")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SignalPresetCatalog.elementGapUnits.forEach { value -> FilterChip(selected = kotlin.math.abs(elementGapUnits - value) < 0.01, enabled = !listening, onClick = { if (elementGapUnits != value) { elementGapUnits = value; invalidateWorkingReception() } }, label = { Text("${value}u") }) }
                    }
                    Text("Between letters")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SignalPresetCatalog.letterGapUnits.forEach { value -> FilterChip(selected = kotlin.math.abs(letterGapUnits - value) < 0.01, enabled = !listening, onClick = { if (letterGapUnits != value) { letterGapUnits = value; invalidateWorkingReception() } }, label = { Text("${value.toInt()}u") }) }
                    }
                    Text("Between words")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SignalPresetCatalog.wordGapUnits.forEach { value -> FilterChip(selected = kotlin.math.abs(wordGapUnits - value) < 0.01, enabled = !listening, onClick = { if (wordGapUnits != value) { wordGapUnits = value; invalidateWorkingReception() } }, label = { Text("${value.toInt()}u") }) }
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
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
