package com.example.methodmesh.modules.timertools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

data class TimerItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mode: String,
    val durationMs: Long = 0L,
    val intervalMs: Long = 0L,
    val repeating: Boolean = false,
    val running: Boolean = false,
    val startedAtEpochMs: Long? = null,
    val accumulatedMs: Long = 0L,
    val lapsMs: List<Long> = emptyList()
) {
    fun elapsed(now: Long): Long = accumulatedMs + if (running && startedAtEpochMs != null) max(0L, now - startedAtEpochMs) else 0L
    fun remaining(now: Long): Long = if (mode == "countdown" || mode == "session") {
        max(0L, durationMs - elapsed(now))
    } else if (mode == "interval") {
        val e = elapsed(now)
        when {
            intervalMs <= 0L -> 0L
            !repeating -> max(0L, intervalMs - e)
            e == 0L -> intervalMs
            e % intervalMs == 0L -> intervalMs
            else -> intervalMs - (e % intervalMs)
        }
    } else 0L
}

object TimerToolsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TimerToolsMethod.ID
    override val title = "Timer tools"
    override val description = "Stopwatch, countdown, lap and interval timers."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val defaultMode = context.action.settings["default_mode"] ?: context.action.settings["input_default_mode"] ?: "stopwatch"
        val defaultDuration = ((context.action.settings["default_duration_seconds"] ?: context.action.settings["input_default_duration_seconds"])?.toLongOrNull() ?: 60L) * 1000L
        val defaultInterval = ((context.action.settings["default_interval_seconds"] ?: context.action.settings["input_default_interval_seconds"])?.toLongOrNull() ?: 30L) * 1000L
        val repeat = (context.action.settings["repeat_interval"] ?: context.action.settings["input_repeat_interval"])?.toBooleanStrictOrNull() ?: false
        val timersSaver = remember { Saver<List<TimerItem>, String>(save = ::timerItemsToJson, restore = ::timerItemsFromJson) }
        var timers by rememberSaveable(stateSaver = timersSaver) {
            mutableStateOf(listOf(TimerItem(name = "Timer 1", mode = defaultMode, durationMs = defaultDuration, intervalMs = defaultInterval, repeating = repeat)))
        }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var addOpen by rememberSaveable { mutableStateOf(false) }
        var newName by rememberSaveable { mutableStateOf("") }
        var newMode by rememberSaveable { mutableStateOf(defaultMode) }
        var newSeconds by rememberSaveable { mutableStateOf((defaultDuration / 1000L).toString()) }
        var newRepeat by rememberSaveable { mutableStateOf(repeat) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100L) } }
        LaunchedEffect(now) {
            timers = timers.map { timer ->
                val shouldStop = timer.running && when (timer.mode) {
                    "countdown", "session" -> timer.durationMs > 0L && timer.elapsed(now) >= timer.durationMs
                    "interval" -> !timer.repeating && timer.intervalMs > 0L && timer.elapsed(now) >= timer.intervalMs
                    else -> false
                }
                if (shouldStop) timer.copy(running = false, accumulatedMs = if (timer.mode == "interval") timer.intervalMs else timer.durationMs, startedAtEpochMs = null) else timer
            }
        }

        fun update(id: String, transform: (TimerItem) -> TimerItem) { timers = timers.map { if (it.id == id) transform(it) else it }; result = null }
        fun snapshot(): ExecutionResult {
            val array = JSONArray().apply {
                timers.forEach { t -> put(JSONObject().apply {
                    val elapsed = t.elapsed(now)
                    put("id", t.id); put("name", t.name); put("mode", t.mode); put("elapsed_ms", elapsed); put("remaining_ms", t.remaining(now))
                    put("duration_ms", t.durationMs); put("interval_ms", t.intervalMs); put("repeating", t.repeating); put("running", t.running)
                    put("laps_ms", JSONArray(t.lapsMs))
                }) }
            }
            val request = As100TimerToolsMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + context.action.settings + mapOf("timers_json" to array.toString()), emptyList(), emptyList())
            return As100TimerToolsMethod.result(request, As100TimerToolsMethod.values(mapOf("timers_json" to array.toString())), context.request.invocationContext).also { result = it }
        }

        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (context.stepNumber > 1) TextButton(onClick = onBack) { Text("Back") }
                OutlinedButton(onClick = onCancel) { Text("Close") }
            }
            Text("Timers continue by wall-clock time while this workspace is open or backgrounded. No alarm/notification service is used.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(timers, key = { it.id }) { timer ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(timer.name, style = MaterialTheme.typography.titleMedium); Text(timer.mode.uppercase(), style = MaterialTheme.typography.labelSmall) }
                                Text(formatDuration(if (timer.mode in listOf("countdown", "session", "interval")) timer.remaining(now) else timer.elapsed(now)), style = MaterialTheme.typography.headlineSmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = {
                                    if (timer.running) update(timer.id) { it.copy(running = false, accumulatedMs = it.elapsed(now), startedAtEpochMs = null) }
                                    else update(timer.id) { it.copy(running = true, startedAtEpochMs = now) }
                                }, modifier = Modifier.weight(1f)) { Text(if (timer.running) "Pause" else "Start") }
                                OutlinedButton(onClick = { update(timer.id) { it.copy(running = false, startedAtEpochMs = null, accumulatedMs = 0L, lapsMs = emptyList()) } }, modifier = Modifier.weight(1f)) { Text("Reset") }
                                if (timer.mode == "stopwatch") OutlinedButton(onClick = { update(timer.id) { it.copy(lapsMs = it.lapsMs + it.elapsed(now)) } }, modifier = Modifier.weight(1f)) { Text("Lap") }
                            }
                            if (timer.lapsMs.isNotEmpty()) Text(timer.lapsMs.mapIndexed { i, ms -> "${i + 1}: ${formatDuration(ms)}" }.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { timers = timers.filterNot { it.id == timer.id }; result = null }) { Text("Remove") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { newName = ""; newMode = defaultMode; newRepeat = repeat; addOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Add timer") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onConfirmed(snapshot()) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (context.isNativePresetRun) "Finish with timer snapshot" else "Use this snapshot")
            }
        }

        if (addOpen) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = .55f)) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Add timer", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth()) { listOf("stopwatch", "countdown", "interval", "session").forEach { m -> FilterChip(newMode == m, { newMode = m }, { Text(m) }, modifier = Modifier.padding(2.dp)) } }
                            if (newMode != "stopwatch") OutlinedTextField(newSeconds, { newSeconds = it.filter(Char::isDigit) }, label = { Text(if (newMode == "interval") "Interval seconds" else "Duration seconds") }, modifier = Modifier.fillMaxWidth())
                            if (newMode == "interval") {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Repeat interval", modifier = Modifier.weight(1f))
                                    Switch(checked = newRepeat, onCheckedChange = { newRepeat = it })
                                }
                            }
                            Button(onClick = {
                                val ms = (newSeconds.toLongOrNull() ?: 60L).coerceAtLeast(1L) * 1000L
                                timers = timers + TimerItem(name = newName.ifBlank { "Timer ${timers.size + 1}" }, mode = newMode, durationMs = if (newMode == "countdown" || newMode == "session") ms else 0L, intervalMs = if (newMode == "interval") ms else 0L, repeating = newRepeat)
                                addOpen = false
                            }, modifier = Modifier.fillMaxWidth()) { Text("Add") }
                            OutlinedButton(onClick = { addOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalTenths = ms.coerceAtLeast(0L) / 100L
    val hours = totalTenths / 36000L
    val minutes = (totalTenths / 600L) % 60L
    val seconds = (totalTenths / 10L) % 60L
    val tenth = totalTenths % 10L
    return if (hours > 0) "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenth) else "%02d:%02d.%d".format(minutes, seconds, tenth)
}

private fun timerItemsToJson(timers: List<TimerItem>): String = JSONArray().apply {
    timers.forEach { t ->
        put(JSONObject().apply {
            put("id", t.id)
            put("name", t.name)
            put("mode", t.mode)
            put("duration_ms", t.durationMs)
            put("interval_ms", t.intervalMs)
            put("repeating", t.repeating)
            put("running", t.running)
            if (t.startedAtEpochMs != null) put("started_at_epoch_ms", t.startedAtEpochMs)
            put("accumulated_ms", t.accumulatedMs)
            put("laps_ms", JSONArray(t.lapsMs))
        })
    }
}.toString()

private fun timerItemsFromJson(raw: String): List<TimerItem> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { i -> array.optJSONObject(i) }.map { o ->
        val laps = o.optJSONArray("laps_ms") ?: JSONArray()
        TimerItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Timer"),
            mode = o.optString("mode", "stopwatch"),
            durationMs = o.optLong("duration_ms", 0L),
            intervalMs = o.optLong("interval_ms", 0L),
            repeating = o.optBoolean("repeating", false),
            running = o.optBoolean("running", false),
            startedAtEpochMs = if (o.has("started_at_epoch_ms") && !o.isNull("started_at_epoch_ms")) o.optLong("started_at_epoch_ms") else null,
            accumulatedMs = o.optLong("accumulated_ms", 0L),
            lapsMs = (0 until laps.length()).map { laps.optLong(it) }
        )
    }
}.getOrDefault(emptyList())
