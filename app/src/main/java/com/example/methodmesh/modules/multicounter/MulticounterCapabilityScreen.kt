package com.example.methodmesh.modules.multicounter

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.ceil

object MulticounterCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MulticounterMethod.ID
    override val title = "Multi-counter"
    override val description = "Compact counts, score tallies and per-entity timers."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        fun configured(key: String, fallback: String): String =
            context.action.settings[key] ?: context.action.settings["input_$key"] ?: fallback

        var mode by rememberSaveable { mutableStateOf(configured("mode", MulticounterSessionState.MODE_COUNT)) }
        var entityCount by rememberSaveable { mutableStateOf(configured("entity_count", "2")) }
        var entityNames by rememberSaveable { mutableStateOf(configured("entity_names", "")) }
        var counterStart by rememberSaveable { mutableStateOf(configured("counter_start", "0")) }
        var step by rememberSaveable { mutableStateOf(configured("step", "1")) }
        var allowNegative by rememberSaveable { mutableStateOf(configured("allow_negative", "false").toBooleanStrictOrNull() ?: false) }
        var showTotal by rememberSaveable { mutableStateOf(configured("show_total", "false").toBooleanStrictOrNull() ?: false) }
        var showLeader by rememberSaveable { mutableStateOf(configured("show_leader", "false").toBooleanStrictOrNull() ?: false) }
        var timerMode by rememberSaveable { mutableStateOf(configured("timer_mode", MulticounterSessionState.TIMER_STOPWATCH)) }
        var countdownSeconds by rememberSaveable { mutableStateOf(configured("countdown_seconds", "60")) }
        var staggerSeconds by rememberSaveable { mutableStateOf(configured("stagger_seconds", "0")) }
        var timerPrecision by rememberSaveable { mutableStateOf(configured("timer_precision", MulticounterSessionState.PRECISION_TENTHS)) }

        val autoStart = context.startsImmediately && (!context.isNativePresetRun || context.runtimeInputFields.isEmpty())
        var sessionStarted by rememberSaveable(context.action.canonicalId) { mutableStateOf(autoStart) }
        var sessionJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var finalizedFieldsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var nowRealtimeMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

        fun currentSettings(): Map<String, String> = linkedMapOf(
            "mode" to mode,
            "entity_count" to entityCount,
            "entity_names" to entityNames,
            "counter_start" to counterStart,
            "step" to step,
            "allow_negative" to allowNegative.toString(),
            "show_total" to showTotal.toString(),
            "show_leader" to showLeader.toString(),
            "timer_mode" to timerMode,
            "countdown_seconds" to countdownSeconds,
            "stagger_seconds" to staggerSeconds,
            "timer_precision" to timerPrecision
        )

        fun executionFor(values: Map<String, String>): ExecutionResult {
            val request = As100MulticounterMethod.request(
                action = As100MulticounterMethod.ID,
                context = context.request.invocationContext.asMap(As100MulticounterMethod.ID) + context.action.settings + currentSettings(),
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100MulticounterMethod.result(request, values, context.request.invocationContext)
        }

        fun createSession() {
            val now = SystemClock.elapsedRealtime()
            nowRealtimeMs = now
            val state = MulticounterSessionState.create(currentSettings(), now, Instant.now().toString())
            sessionJson = state.toJson()
            finalizedFieldsJson = ""
            result = null
            sessionStarted = true
        }

        fun updateState(transform: (MulticounterSessionState, Long, String) -> MulticounterSessionState) {
            if (sessionJson.isBlank()) return
            val now = SystemClock.elapsedRealtime()
            val wall = Instant.now().toString()
            nowRealtimeMs = now
            val state = runCatching { MulticounterSessionState.fromJson(sessionJson) }.getOrNull() ?: return
            sessionJson = transform(state, now, wall).toJson()
        }

        fun finishSession() {
            if (sessionJson.isBlank()) return
            val now = SystemClock.elapsedRealtime()
            val finishedIso = Instant.now().toString()
            nowRealtimeMs = now
            val state = MulticounterSessionState.fromJson(sessionJson).finish(now, finishedIso)
            sessionJson = state.toJson()
            val fields = As100MulticounterMethod.fieldsFor(state, now, finishedIso)
            finalizedFieldsJson = As100MulticounterMethod.fieldsToJson(fields)
            result = executionFor(fields)
        }

        LaunchedEffect(
            mode, entityCount, entityNames, counterStart, step, allowNegative,
            showTotal, showLeader, timerMode, countdownSeconds, staggerSeconds, timerPrecision
        ) {
            if (!sessionStarted) context.onSettingsChanged(currentSettings())
        }

        LaunchedEffect(sessionStarted, sessionJson) {
            if (sessionStarted && sessionJson.isBlank()) createSession()
        }

        LaunchedEffect(finalizedFieldsJson) {
            if (finalizedFieldsJson.isNotBlank() && result == null) {
                result = executionFor(As100MulticounterMethod.fieldsFromJson(finalizedFieldsJson))
            }
        }

        // Update visible time without serialising the session on every tick. State JSON
        // changes only when a scheduled timer starts or a countdown expires.
        LaunchedEffect(sessionStarted, sessionJson, timerPrecision) {
            if (!sessionStarted || sessionJson.isBlank()) return@LaunchedEffect
            while (true) {
                val state = runCatching { MulticounterSessionState.fromJson(sessionJson) }.getOrNull() ?: break
                if (!state.hasActiveTimers) break
                delay(if (timerPrecision == MulticounterSessionState.PRECISION_TENTHS) 100L else 250L)
                val now = SystemClock.elapsedRealtime()
                nowRealtimeMs = now
                val normalized = state.normalize(now, Instant.now().toString())
                val next = normalized.toJson()
                if (next != sessionJson) {
                    sessionJson = next
                    break
                }
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                finalizedFieldsJson = ""
                sessionJson = ""
                sessionStarted = false
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (!sessionStarted) {
                SetupPanel(
                    context = context,
                    mode = mode,
                    onMode = { mode = it },
                    entityCount = entityCount,
                    onEntityCount = { entityCount = it.filter(Char::isDigit).take(2) },
                    entityNames = entityNames,
                    onEntityNames = { entityNames = it },
                    counterStart = counterStart,
                    onCounterStart = { counterStart = it.integerText() },
                    step = step,
                    onStep = { step = it.filter(Char::isDigit) },
                    allowNegative = allowNegative,
                    onAllowNegative = { allowNegative = it },
                    showTotal = showTotal,
                    onShowTotal = { showTotal = it },
                    showLeader = showLeader,
                    onShowLeader = { showLeader = it },
                    timerMode = timerMode,
                    onTimerMode = { timerMode = it },
                    countdownSeconds = countdownSeconds,
                    onCountdownSeconds = { countdownSeconds = it.filter(Char::isDigit) },
                    staggerSeconds = staggerSeconds,
                    onStaggerSeconds = { staggerSeconds = it.filter(Char::isDigit) },
                    timerPrecision = timerPrecision,
                    onTimerPrecision = { timerPrecision = it },
                    onStart = { createSession() }
                )
            } else if (sessionJson.isNotBlank()) {
                val state = runCatching { MulticounterSessionState.fromJson(sessionJson) }.getOrNull()
                if (state == null) {
                    Text("Counter state could not be restored.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        sessionStarted = false
                        sessionJson = ""
                    }, modifier = Modifier.fillMaxWidth()) { Text("Restart") }
                } else {
                    BoardPanel(
                        state = state,
                        nowRealtimeMs = nowRealtimeMs,
                        onDelta = { index, direction -> updateState { s, now, wall -> s.changeValue(index, direction, now, wall) } },
                        onToggleTimer = { index -> updateState { s, now, wall -> s.toggleTimer(index, now, wall) } },
                        onStartAll = { updateState { s, now, wall -> s.startAll(now, wall) } },
                        onPauseAll = { updateState { s, now, wall -> s.pauseAll(now, wall) } },
                        onResetTimers = { updateState { s, now, wall -> s.resetTimers(now, wall) } },
                        onResetValues = { updateState { s, now, wall -> s.resetValues(now, wall) } },
                        onFinish = { finishSession() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupPanel(
    context: CapabilityScreenContext,
    mode: String,
    onMode: (String) -> Unit,
    entityCount: String,
    onEntityCount: (String) -> Unit,
    entityNames: String,
    onEntityNames: (String) -> Unit,
    counterStart: String,
    onCounterStart: (String) -> Unit,
    step: String,
    onStep: (String) -> Unit,
    allowNegative: Boolean,
    onAllowNegative: (Boolean) -> Unit,
    showTotal: Boolean,
    onShowTotal: (Boolean) -> Unit,
    showLeader: Boolean,
    onShowLeader: (Boolean) -> Unit,
    timerMode: String,
    onTimerMode: (String) -> Unit,
    countdownSeconds: String,
    onCountdownSeconds: (String) -> Unit,
    staggerSeconds: String,
    onStaggerSeconds: (String) -> Unit,
    timerPrecision: String,
    onTimerPrecision: (String) -> Unit,
    onStart: () -> Unit
) {
    Text("Set up the board, then use one compact screen for the whole session.", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(10.dp))

    if (context.settingShouldBeShown("mode")) {
        CompactChoice(
            label = "Board",
            value = mode,
            options = listOf(
                MulticounterSessionState.MODE_COUNT to "Count",
                MulticounterSessionState.MODE_TIME to "Time",
                MulticounterSessionState.MODE_COUNT_AND_TIME to "Both"
            ),
            onSelected = onMode
        )
    }
    if (context.settingShouldBeShown("entity_count")) {
        OutlinedTextField(
            value = entityCount,
            onValueChange = onEntityCount,
            label = { Text("Number of entities") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
    if (context.settingShouldBeShown("entity_names")) {
        OutlinedTextField(
            value = entityNames,
            onValueChange = onEntityNames,
            label = { Text("Names — Alice|Bob|Charlie") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    val usesCounter = mode != MulticounterSessionState.MODE_TIME
    val usesTimer = mode != MulticounterSessionState.MODE_COUNT

    if (usesCounter) {
        if (context.settingShouldBeShown("counter_start")) {
            OutlinedTextField(counterStart, onCounterStart, label = { Text("Starting value") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (context.settingShouldBeShown("step")) {
            OutlinedTextField(step, onStep, label = { Text("Step") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (context.settingShouldBeShown("allow_negative")) ToggleRow("Allow negative values", allowNegative, onAllowNegative)
        if (context.settingShouldBeShown("show_total")) ToggleRow("Show total", showTotal, onShowTotal)
        if (context.settingShouldBeShown("show_leader")) ToggleRow("Show leader", showLeader, onShowLeader)
    }

    if (usesTimer) {
        if (context.settingShouldBeShown("timer_mode")) {
            CompactChoice(
                label = "Timer",
                value = timerMode,
                options = listOf(
                    MulticounterSessionState.TIMER_STOPWATCH to "Stopwatch",
                    MulticounterSessionState.TIMER_COUNTDOWN to "Countdown"
                ),
                onSelected = onTimerMode
            )
        }
        if (timerMode == MulticounterSessionState.TIMER_COUNTDOWN && context.settingShouldBeShown("countdown_seconds")) {
            OutlinedTextField(countdownSeconds, onCountdownSeconds, label = { Text("Countdown seconds") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (context.settingShouldBeShown("stagger_seconds")) {
            OutlinedTextField(staggerSeconds, onStaggerSeconds, label = { Text("Start stagger, seconds") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (context.settingShouldBeShown("timer_precision")) {
            CompactChoice(
                label = "Precision",
                value = timerPrecision,
                options = listOf(
                    MulticounterSessionState.PRECISION_SECONDS to "Seconds",
                    MulticounterSessionState.PRECISION_TENTHS to "Tenths"
                ),
                onSelected = onTimerPrecision
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start session") }
}

@Composable
private fun BoardPanel(
    state: MulticounterSessionState,
    nowRealtimeMs: Long,
    onDelta: (Int, Int) -> Unit,
    onToggleTimer: (Int) -> Unit,
    onStartAll: () -> Unit,
    onPauseAll: () -> Unit,
    onResetTimers: () -> Unit,
    onResetValues: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${state.entities.size} ${if (state.entities.size == 1) "entity" else "entities"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (state.showsTimer && state.staggerMs > 0L) {
            Text("stagger ${state.staggerMs / 1000}s", style = MaterialTheme.typography.labelMedium)
        }
    }

    if (state.showsTimer) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onStartAll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) { Text("Start all") }
            OutlinedButton(onClick = onPauseAll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) { Text("Pause all") }
            OutlinedButton(onClick = onResetTimers, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) { Text("Reset time") }
        }
    }
    if (state.showsCounter) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onResetValues, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) { Text("Reset values") }
    }

    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        state.entities.forEachIndexed { index, entity ->
            EntityBoardRow(
                state = state,
                entity = entity,
                index = index,
                nowRealtimeMs = nowRealtimeMs,
                onDelta = onDelta,
                onToggleTimer = onToggleTimer
            )
        }
    }

    if (state.showsCounter && (state.showTotal || state.showLeader)) {
        Spacer(Modifier.height(8.dp))
        val values = state.entities.map { it.value }
        val status = buildList {
            if (state.showTotal) add("Total ${values.sum()}")
            if (state.showLeader) {
                values.maxOrNull()?.let { best ->
                    val leaders = state.entities.filter { it.value == best }.joinToString(" / ") { it.label }
                    add("Leader $leaders ($best)")
                }
            }
        }.joinToString(" · ")
        Text(status, style = MaterialTheme.typography.labelLarge)
    }

    Spacer(Modifier.height(12.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish session") }
}

@Composable
private fun EntityBoardRow(
    state: MulticounterSessionState,
    entity: MulticounterEntity,
    index: Int,
    nowRealtimeMs: Long,
    onDelta: (Int, Int) -> Unit,
    onToggleTimer: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        if (state.showsCounter && !state.showsTimer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entity.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                OutlinedButton(onClick = { onDelta(index, -1) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("−") }
                Text(entity.value.toString(), modifier = Modifier.width(54.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Button(onClick = { onDelta(index, 1) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("+") }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(entity.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    val pending = entity.pendingStartRealtimeMs
                    val timer = state.displayTimerMs(entity, nowRealtimeMs)
                    val pendingSuffix = if (pending != null && pending > nowRealtimeMs) {
                        " +${ceil((pending - nowRealtimeMs) / 1000.0).toInt()}s"
                    } else ""
                    Text(
                        MulticounterSessionState.formatDuration(timer, state.timerPrecision) + pendingSuffix,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { onToggleTimer(index) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            when {
                                entity.pendingStartRealtimeMs != null -> "Cancel"
                                entity.runningSinceRealtimeMs != null -> "Pause"
                                else -> "Start"
                            }
                        )
                    }
                }
                if (state.showsCounter) {
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onDelta(index, -1) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) { Text("−") }
                        Text(entity.value.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Button(onClick = { onDelta(index, 1) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) { Text("+") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactChoice(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, title) ->
            if (key == value) {
                Button(onClick = { onSelected(key) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 7.dp)) { Text("✓ $title") }
            } else {
                OutlinedButton(onClick = { onSelected(key) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 7.dp)) { Text(title) }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onValue)
    }
}

private fun String.integerText(): String =
    filter { it.isDigit() || it == '-' }.let { filtered ->
        if (filtered.startsWith("-")) "-" + filtered.removePrefix("-").replace("-", "") else filtered.replace("-", "")
    }

