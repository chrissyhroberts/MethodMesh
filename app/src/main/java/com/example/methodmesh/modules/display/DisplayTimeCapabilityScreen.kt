@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.methodmesh.modules.display

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.view.WindowManager
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

class DisplayTimerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = DisplayTimerMethod.ID
    override val title = DisplayTimerMethod.title
    override val description = DisplayTimerMethod.help

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        DisplayTimerUi(context, onBack, onConfirmed, onCancel)
    }
}

class DisplayClockCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = DisplayClockMethod.ID
    override val title = DisplayClockMethod.title
    override val description = DisplayClockMethod.help

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        DisplayClockUi(context, onBack, onConfirmed, onCancel)
    }
}

@Composable
private fun DisplayTimerUi(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    fun initial(key: String, fallback: String) =
        context.action.settings[key] ?: context.action.settings["input_$key"] ?: fallback

    val initialDuration = (initial("duration_seconds", DisplayTimerMethod.DEFAULT_DURATION_SECONDS.toString())
        .toIntOrNull() ?: DisplayTimerMethod.DEFAULT_DURATION_SECONDS).coerceIn(1, 359999)
    var hours by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialDuration / 3600) }
    var minutes by rememberSaveable(context.action.canonicalId) { mutableStateOf((initialDuration % 3600) / 60) }
    var seconds by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialDuration % 60) }
    var beep by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(initial("beep", "true").toBooleanStrictOrNull() ?: true)
    }
    var theme by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("theme", "white_on_black")) }
    var highBrightness by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(initial("high_brightness", "true").toBooleanStrictOrNull() ?: true)
    }
    var showing by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var startedIso by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var startedElapsed by rememberSaveable(context.action.canonicalId) { mutableStateOf(0L) }
    var autoStarted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var committed by remember { mutableStateOf<ExecutionResult?>(null) }
    var committedPreview by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun durationSeconds(): Int = (
        hours.coerceIn(0, 99) * 3600 +
            minutes.coerceIn(0, 59) * 60 +
            seconds.coerceIn(0, 59)
        ).coerceIn(0, 359999)

    fun settings() = context.action.settings + mapOf(
        "duration_seconds" to durationSeconds().toString(),
        "beep" to beep.toString(),
        "theme" to theme,
        "high_brightness" to highBrightness.toString()
    )

    fun startTimer() {
        if (durationSeconds() <= 0) return
        startedIso = Instant.now().toString()
        startedElapsed = SystemClock.elapsedRealtime()
        showing = true
    }

    fun finishTimer(remainingSeconds: Int, completionReason: String) {
        showing = false
        if (startedIso.isBlank()) return
        val stopped = Instant.now().toString()
        val elapsed = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
        val values = DisplayTimerMethod.values(
            settings = settings(),
            startedIso = startedIso,
            stoppedIso = stopped,
            elapsedMs = elapsed,
            remainingSeconds = remainingSeconds,
            completionReason = completionReason
        )
        val request = DisplayTimerMethod.request(
            DisplayTimerMethod.ID,
            context.request.invocationContext.asMap(DisplayTimerMethod.ID) + settings() + values,
            emptyList(),
            emptyList()
        )
        committedPreview = values
        committed = DisplayTimerMethod.result(request, values, context.request.invocationContext)
    }

    LaunchedEffect(context.startsImmediately, autoStarted, committed, initialDuration) {
        if (context.startsImmediately && !autoStarted && committed == null && initialDuration > 0) {
            autoStarted = true
            startTimer()
        }
    }

    CapabilityScreenScaffold(
        title = DisplayTimerMethod.title,
        capabilityId = DisplayTimerMethod.ID,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = committed,
        resultPreview = committedPreview,
        onBack = onBack,
        onRetry = {
            committed = null
            committedPreview = emptyMap()
            autoStarted = false
        },
        onConfirm = { committed?.let(onConfirmed) },
        onCancel = {
            showing = false
            onCancel()
        }
    ) {
        if (committed == null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (context.settingShouldBeShown("duration_seconds")) {
                    Text("Duration", style = MaterialTheme.typography.labelMedium)
                    DurationWheelPicker(
                        hours = hours,
                        minutes = minutes,
                        seconds = seconds,
                        onHoursChange = { hours = it },
                        onMinutesChange = { minutes = it },
                        onSecondsChange = { seconds = it }
                    )
                }

                Card(Modifier.fillMaxWidth()) {
                    TimerFace(
                        remainingSeconds = durationSeconds(),
                        theme = theme,
                        modifier = Modifier.fillMaxWidth().height(132.dp)
                    )
                }

                if (context.settingShouldBeShown("beep")) {
                    CompactSwitchRow("Alarm beeper at zero", beep) { beep = it }
                }
                if (context.settingShouldBeShown("theme")) {
                    TimeChoiceRow("Appearance", theme, listOf("white_on_black", "black_on_white", "black_on_yellow")) {
                        theme = it
                    }
                }
                if (context.settingShouldBeShown("high_brightness")) {
                    CompactSwitchRow("High brightness", highBrightness) { highBrightness = it }
                }
                Button(
                    enabled = durationSeconds() > 0,
                    onClick = { startTimer() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("START") }
            }
        }
    }

    if (showing) {
        FullScreenCountdown(
            durationSeconds = durationSeconds(),
            beep = beep,
            theme = theme,
            highBrightness = highBrightness,
            onStop = ::finishTimer,
            onCancel = { showing = false }
        )
    }
}

@Composable
private fun DisplayClockUi(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    fun initial(key: String, fallback: String) =
        context.action.settings[key] ?: context.action.settings["input_$key"] ?: fallback

    var clockFormat by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("clock_format", "HH:MM")) }
    var showDate by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(initial("show_date", "false").toBooleanStrictOrNull() ?: false)
    }
    var theme by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("theme", "white_on_black")) }
    var highBrightness by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(initial("high_brightness", "true").toBooleanStrictOrNull() ?: true)
    }
    var showing by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var startedIso by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var startedElapsed by rememberSaveable(context.action.canonicalId) { mutableStateOf(0L) }
    var autoStarted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var committed by remember { mutableStateOf<ExecutionResult?>(null) }
    var committedPreview by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun settings() = context.action.settings + mapOf(
        "clock_format" to clockFormat,
        "show_date" to showDate.toString(),
        "theme" to theme,
        "high_brightness" to highBrightness.toString()
    )

    fun startClock() {
        startedIso = Instant.now().toString()
        startedElapsed = SystemClock.elapsedRealtime()
        showing = true
    }

    fun finishClock() {
        showing = false
        if (startedIso.isBlank()) return
        val stopped = Instant.now().toString()
        val elapsed = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
        val values = DisplayClockMethod.values(
            settings = settings(),
            startedIso = startedIso,
            stoppedIso = stopped,
            elapsedMs = elapsed,
            completionReason = "stopped_by_operator",
            now = ZonedDateTime.now()
        )
        val request = DisplayClockMethod.request(
            DisplayClockMethod.ID,
            context.request.invocationContext.asMap(DisplayClockMethod.ID) + settings() + values,
            emptyList(),
            emptyList()
        )
        committedPreview = values
        committed = DisplayClockMethod.result(request, values, context.request.invocationContext)
    }

    LaunchedEffect(context.startsImmediately, autoStarted, committed) {
        if (context.startsImmediately && !autoStarted && committed == null) {
            autoStarted = true
            startClock()
        }
    }

    CapabilityScreenScaffold(
        title = DisplayClockMethod.title,
        capabilityId = DisplayClockMethod.ID,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = committed,
        resultPreview = committedPreview,
        onBack = onBack,
        onRetry = {
            committed = null
            committedPreview = emptyMap()
            autoStarted = false
        },
        onConfirm = { committed?.let(onConfirmed) },
        onCancel = {
            showing = false
            onCancel()
        }
    ) {
        if (committed == null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    ClockFace(
                        format = clockFormat,
                        showDate = showDate,
                        theme = theme,
                        modifier = Modifier.fillMaxWidth().height(132.dp)
                    )
                }
                if (context.settingShouldBeShown("clock_format")) {
                    TimeChoiceRow("Time", clockFormat, listOf("HH:MM", "HH:MM:SS")) { clockFormat = it }
                }
                if (context.settingShouldBeShown("show_date")) {
                    CompactSwitchRow("Date above clock", showDate) { showDate = it }
                }
                if (context.settingShouldBeShown("theme")) {
                    TimeChoiceRow("Appearance", theme, listOf("white_on_black", "black_on_white", "black_on_yellow")) {
                        theme = it
                    }
                }
                if (context.settingShouldBeShown("high_brightness")) {
                    CompactSwitchRow("High brightness", highBrightness) { highBrightness = it }
                }
                Button(onClick = { startClock() }, modifier = Modifier.fillMaxWidth()) { Text("SHOW") }
            }
        }
    }

    if (showing) {
        FullScreenClock(
            format = clockFormat,
            showDate = showDate,
            theme = theme,
            highBrightness = highBrightness,
            onStop = ::finishClock,
            onCancel = { showing = false }
        )
    }
}

@Composable
private fun FullScreenCountdown(
    durationSeconds: Int,
    beep: Boolean,
    theme: String,
    highBrightness: Boolean,
    onStop: (Int, String) -> Unit,
    onCancel: () -> Unit
) {
    var controls by rememberSaveable { mutableStateOf(true) }
    var running by rememberSaveable(durationSeconds) { mutableStateOf(true) }
    var finished by rememberSaveable(durationSeconds) { mutableStateOf(false) }
    var alarmPlayed by rememberSaveable(durationSeconds) { mutableStateOf(false) }
    var deadlineElapsed by rememberSaveable(durationSeconds) {
        mutableStateOf(SystemClock.elapsedRealtime() + durationSeconds.toLong() * 1000L)
    }
    var pausedRemainingMs by rememberSaveable(durationSeconds) {
        mutableStateOf(durationSeconds.toLong() * 1000L)
    }
    var nowElapsed by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    DisplayWindowEffects(highBrightness)
    BackHandler { onCancel() }

    LaunchedEffect(running, deadlineElapsed) {
        while (running) {
            val now = SystemClock.elapsedRealtime()
            nowElapsed = now
            val remaining = (deadlineElapsed - now).coerceAtLeast(0L)
            if (remaining <= 0L) {
                pausedRemainingMs = 0L
                running = false
                finished = true
                controls = true
                break
            }
            delay(80L)
        }
    }

    LaunchedEffect(finished, beep) {
        if (finished && beep && !alarmPlayed) {
            alarmPlayed = true
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
            try {
                // A bounded alarm cadence: three short beeps, a pause, then three more.
                // This is deliberately finite; Display is not a persistent alarm service.
                repeat(2) { burst ->
                    repeat(3) {
                        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 240)
                        delay(360L)
                    }
                    if (burst == 0) delay(480L)
                }
            } finally {
                tone?.stopTone()
                tone?.release()
            }
        }
    }

    LaunchedEffect(controls, finished) {
        if (controls && !finished) {
            delay(4000L)
            controls = false
        }
    }

    val remainingMs = if (running) (deadlineElapsed - nowElapsed).coerceAtLeast(0L) else pausedRemainingMs
    val remainingSeconds = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { controls = !controls } }
        ) {
            TimerFace(
                remainingSeconds = remainingSeconds,
                theme = theme,
                modifier = Modifier.fillMaxSize()
            )
            if (controls) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 18.dp),
                    color = Color.Black.copy(alpha = .82f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!finished) {
                            OutlinedButton(onClick = {
                                if (running) {
                                    pausedRemainingMs = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                                    running = false
                                } else if (pausedRemainingMs > 0L) {
                                    deadlineElapsed = SystemClock.elapsedRealtime() + pausedRemainingMs
                                    running = true
                                }
                            }) { Text(if (running) "Pause" else "Resume") }
                        }
                        OutlinedButton(onClick = {
                            val full = durationSeconds.toLong() * 1000L
                            pausedRemainingMs = full
                            deadlineElapsed = SystemClock.elapsedRealtime() + full
                            nowElapsed = SystemClock.elapsedRealtime()
                            finished = false
                            alarmPlayed = false
                            running = true
                        }) { Text("Restart") }
                        Button(onClick = {
                            val remaining = if (finished) 0 else remainingSeconds
                            onStop(remaining, if (finished) "countdown_finished" else "stopped_by_operator")
                        }) { Text("STOP") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenClock(
    format: String,
    showDate: Boolean,
    theme: String,
    highBrightness: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    var controls by rememberSaveable { mutableStateOf(true) }
    DisplayWindowEffects(highBrightness)
    BackHandler { onCancel() }

    LaunchedEffect(controls) {
        if (controls) {
            delay(4000L)
            controls = false
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { controls = !controls } }
        ) {
            ClockFace(format, showDate, theme, Modifier.fillMaxSize())
            if (controls) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 18.dp),
                    color = Color.Black.copy(alpha = .82f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = onStop) { Text("STOP") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayWindowEffects(highBrightness: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivityForDisplay()
    DisposableEffect(activity, view, highBrightness) {
        val window = activity?.window
        val oldBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        view.keepScreenOn = true
        if (highBrightness) {
            window?.attributes = window?.attributes?.also { it.screenBrightness = 1f }
        }
        window?.decorView?.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        onDispose {
            view.keepScreenOn = false
            window?.attributes = window?.attributes?.also { it.screenBrightness = oldBrightness }
            window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}

@Composable
private fun TimerFace(
    remainingSeconds: Int,
    theme: String,
    modifier: Modifier = Modifier
) {
    val palette = timePalette(theme)
    Box(
        modifier = modifier.background(palette.background).padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        AutoFitSingleLineText(
            text = formatCountdownSeconds(remainingSeconds),
            color = palette.foreground,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ClockFace(
    format: String,
    showDate: Boolean,
    theme: String,
    modifier: Modifier = Modifier
) {
    val palette = timePalette(theme)
    val includeSeconds = format.uppercase() == "HH:MM:SS"
    var now by remember { mutableStateOf(ZonedDateTime.now()) }

    LaunchedEffect(includeSeconds) {
        while (true) {
            val current = System.currentTimeMillis()
            val period = if (includeSeconds) 1000L else 60000L
            val wait = (period - (current % period)).coerceAtLeast(25L)
            delay(wait + 5L)
            now = ZonedDateTime.now()
        }
    }

    val locale = Locale.getDefault()
    val timeText = now.format(DateTimeFormatter.ofPattern(if (includeSeconds) "HH:mm:ss" else "HH:mm", locale))
    val dateText = now.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale))

    Box(
        modifier = modifier.background(palette.background).padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showDate) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AutoFitSingleLineText(
                    text = dateText,
                    color = palette.foreground,
                    modifier = Modifier.fillMaxWidth().weight(.28f),
                    maximumFontSp = 180f
                )
                AutoFitSingleLineText(
                    text = timeText,
                    color = palette.foreground,
                    modifier = Modifier.fillMaxWidth().weight(.72f),
                    maximumFontSp = 700f
                )
            }
        } else {
            AutoFitSingleLineText(
                text = timeText,
                color = palette.foreground,
                modifier = Modifier.fillMaxSize(),
                maximumFontSp = 800f
            )
        }
    }
}

@Composable
private fun AutoFitSingleLineText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    maximumFontSp: Float = 800f
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.padding(4.dp), contentAlignment = Alignment.Center) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val maxHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val fitted = remember(text, maxWidthPx, maxHeightPx, maximumFontSp) {
            var low = 6f
            var high = maximumFontSp.coerceAtLeast(low)
            var best = low
            repeat(13) {
                val candidate = (low + high) / 2f
                val measured = measurer.measure(
                    text = text,
                    style = TextStyle(fontSize = candidate.sp, fontWeight = FontWeight.Bold),
                    softWrap = false,
                    maxLines = 1
                )
                if (measured.size.width <= maxWidthPx * .96f && measured.size.height <= maxHeightPx * .90f) {
                    best = candidate
                    low = candidate
                } else {
                    high = candidate
                }
            }
            best
        }
        Text(
            text = text,
            color = color,
            fontSize = fitted.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            softWrap = false,
            maxLines = 1
        )
    }
}

@Composable
private fun DurationWheelPicker(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NativeNumberWheel(
            label = "HOURS",
            value = hours,
            maximum = 99,
            modifier = Modifier.weight(1f),
            onValueChange = onHoursChange
        )
        NativeNumberWheel(
            label = "MIN",
            value = minutes,
            maximum = 59,
            modifier = Modifier.weight(1f),
            onValueChange = onMinutesChange
        )
        NativeNumberWheel(
            label = "SEC",
            value = seconds,
            maximum = 59,
            modifier = Modifier.weight(1f),
            onValueChange = onSecondsChange
        )
    }
}

@Composable
private fun NativeNumberWheel(
    label: String,
    value: Int,
    maximum: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(112.dp),
                factory = { androidContext ->
                    NumberPicker(androidContext).apply {
                        minValue = 0
                        maxValue = maximum
                        wrapSelectorWheel = true
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFormatter { number -> String.format(Locale.getDefault(), "%02d", number) }
                        setOnValueChangedListener { _, _, newValue ->
                            currentOnValueChange.value(newValue)
                        }
                        this.value = value.coerceIn(0, maximum)
                    }
                },
                update = { picker ->
                    if (picker.minValue != 0) picker.minValue = 0
                    if (picker.maxValue != maximum) picker.maxValue = maximum
                    val safeValue = value.coerceIn(0, maximum)
                    if (picker.value != safeValue) picker.value = safeValue
                }
            )
        }
    }
}

@Composable
private fun CompactSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimeChoiceRow(
    label: String,
    selected: String,
    choices: List<String>,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            choices.forEach { choice ->
                val chosen = choice == selected
                Surface(
                    onClick = { onSelect(choice) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    border = if (chosen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = choice.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private data class TimePalette(val background: Color, val foreground: Color)

private fun timePalette(theme: String): TimePalette = when (theme) {
    "black_on_white" -> TimePalette(Color.White, Color.Black)
    "black_on_yellow" -> TimePalette(Color(0xFFFFE600), Color.Black)
    else -> TimePalette(Color.Black, Color.White)
}

private tailrec fun Context.findActivityForDisplay(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityForDisplay()
    else -> null
}
