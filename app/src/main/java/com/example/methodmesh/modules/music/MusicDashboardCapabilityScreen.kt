package com.example.methodmesh.modules.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object PracticeDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PracticeDashboardMethod.ID
    override val title = "Practice dashboard"
    override val description = "Live tempo, exercise and tuning-reference dashboard."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = PracticeDashboardUi(context, onBack, onConfirmed, onCancel)
}

internal object PerformanceDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PerformanceDashboardMethod.ID
    override val title = "Performance / set list"
    override val description = "Persistent running order with planned and actual timing."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = PerformanceDashboardUi(context, onBack, onConfirmed, onCancel)
}

internal object ReferenceDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ReferenceDashboardMethod.ID
    override val title = "Music reference dashboard"
    override val description = "Live key, scale and diatonic-chord reference."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = ReferenceDashboardUi(context, onBack, onConfirmed, onCancel)
}

@Composable
private fun PracticeDashboardUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val androidContext = LocalContext.current
    val repository = remember(androidContext) { SetListRepository(androidContext.applicationContext) }
    val persisted = remember { repository.loadPracticeState() }
    val initialStart = persisted.startedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis().also {
        repository.savePracticeState(SetListRepository.PracticeState(startedAtEpochMs = it, accumulatedElapsedSeconds = 0L, active = true))
    }

    var bpm by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bpm"] ?: context.action.settings["input_bpm"] ?: "100") }
    var beats by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["beats_per_bar"] ?: context.action.settings["input_beats_per_bar"] ?: "4") }
    var subdivision by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["subdivision"] ?: context.action.settings["input_subdivision"] ?: "quarter") }
    var a4 by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["reference_a4_hz"] ?: context.action.settings["input_reference_a4_hz"] ?: "440") }
    var exercise by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["exercise"] ?: context.action.settings["input_exercise"] ?: "") }
    var target by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["target_bpm"] ?: context.action.settings["input_target_bpm"] ?: "120") }
    var startedAtEpochMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialStart) }
    var elapsed by rememberSaveable(context.action.canonicalId) { mutableStateOf(persisted.accumulatedElapsedSeconds) }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    LaunchedEffect(startedAtEpochMs) {
        while (true) {
            elapsed = persisted.accumulatedElapsedSeconds + ((System.currentTimeMillis() - startedAtEpochMs).coerceAtLeast(0L) / 1000L)
            delay(1000)
        }
    }

    val workingValues = As100PracticeDashboardMethod.calculate(
        mapOf(
            "bpm" to bpm,
            "beats_per_bar" to beats,
            "subdivision" to subdivision,
            "reference_a4_hz" to a4,
            "exercise" to exercise,
            "target_bpm" to target,
            "elapsed_seconds" to elapsed.toString(),
        )
    )
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100PracticeDashboardMethod.fields, it) }
    val committedExecution = committedValues?.let { dashboardExecution(As100PracticeDashboardMethod, context, it) }

    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned, bpm, beats, subdivision, a4, exercise, target, elapsed) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[As100PracticeDashboardMethod.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(dashboardExecution(As100PracticeDashboardMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Practice dashboard", capabilityId = As100PracticeDashboardMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = onBack, onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (committedValues == null || committedExecution == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CopyableMusicText(
                            value = elapsed.toString(),
                            label = "elapsed seconds",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        CopyableMusicText(value = bpm, label = "BPM")
                        CopyableMusicText(value = beats, label = "beats per bar")
                        CopyableMusicText(value = subdivision, label = "subdivision")
                        if (exercise.isNotBlank()) CopyableMusicText(value = exercise, label = "exercise")
                    }
                }
                MusicWorkingResult(As100PracticeDashboardMethod, workingValues, heading = "Practice snapshot", secondaryLimit = 4)
                if (context.settingShouldBeShown("bpm")) OutlinedTextField(bpm, { bpm = it }, label = { Text("Tempo (BPM)") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { bpm = ((bpm.toDoubleOrNull() ?: 100.0) - 1.0).coerceAtLeast(20.0).toString() }, modifier = Modifier.fillMaxWidth()) { Text("−1 BPM") }
                Button(onClick = { bpm = ((bpm.toDoubleOrNull() ?: 100.0) + 1.0).coerceAtMost(400.0).toString() }, modifier = Modifier.fillMaxWidth()) { Text("+1 BPM") }
                if (context.settingShouldBeShown("exercise")) OutlinedTextField(exercise, { exercise = it }, label = { Text("Current exercise") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("target_bpm")) OutlinedTextField(target, { target = it }, label = { Text("Target BPM") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("reference_a4_hz")) OutlinedTextField(a4, { a4 = it }, label = { Text("Reference A4 (Hz)") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        startedAtEpochMs = now
                        elapsed = 0L
                        repository.savePracticeState(SetListRepository.PracticeState(startedAtEpochMs = now, accumulatedElapsedSeconds = 0L, active = true))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset session timer") }
                Button(
                    enabled = workingValues[As100PracticeDashboardMethod.fields.status] == "succeeded",
                    onClick = {
                        val execution = dashboardExecution(As100PracticeDashboardMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit snapshot") }
            } else {
                MusicCommittedPanel(As100PracticeDashboardMethod, committedValues, committedExecution, onDone = onConfirmed, onEdit = { committedJson = null })
            }
        }
    }
}

@Composable
private fun PerformanceDashboardUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val androidContext = LocalContext.current
    val repository = remember(androidContext) { SetListRepository(androidContext.applicationContext) }
    val persisted = remember { repository.loadRunState() }
    val songs = remember { mutableStateListOf<SetListRepository.Song>().apply { addAll(repository.load()) } }

    var setName by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["set_name"] ?: context.action.settings["input_set_name"] ?: persisted.setName) }
    var plannedStart by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["planned_start"] ?: context.action.settings["input_planned_start"] ?: persisted.plannedStart) }
    var curfew by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["curfew"] ?: context.action.settings["input_curfew"] ?: persisted.curfew) }
    var newTitle by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var newDuration by rememberSaveable(context.action.canonicalId) { mutableStateOf("3:30") }
    var newBpm by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var newKey by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var currentIndex by rememberSaveable(context.action.canonicalId) { mutableStateOf(persisted.currentIndex.coerceIn(0, songs.size)) }
    var running by rememberSaveable(context.action.canonicalId) { mutableStateOf(persisted.running) }
    var startedAtEpochMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(persisted.startedAtEpochMs) }
    var accumulatedElapsed by rememberSaveable(context.action.canonicalId) { mutableStateOf(persisted.accumulatedElapsedSeconds) }
    var elapsed by rememberSaveable(context.action.canonicalId) { mutableStateOf(persisted.accumulatedElapsedSeconds) }
    var songStartedAtEpochMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(persisted.songStartedAtEpochMs) }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    fun persistRun() {
        repository.saveRunState(
            SetListRepository.RunState(
                setName = setName,
                plannedStart = plannedStart,
                curfew = curfew,
                currentIndex = currentIndex,
                running = running,
                startedAtEpochMs = startedAtEpochMs,
                accumulatedElapsedSeconds = accumulatedElapsed,
                songStartedAtEpochMs = songStartedAtEpochMs,
            )
        )
    }

    LaunchedEffect(running, startedAtEpochMs, accumulatedElapsed) {
        while (running) {
            elapsed = accumulatedElapsed + ((System.currentTimeMillis() - startedAtEpochMs).coerceAtLeast(0L) / 1000L)
            delay(1000)
        }
        if (!running) elapsed = accumulatedElapsed
    }

    val planned = songs.sumOf { it.durationSeconds + it.gapAfterSeconds }
    val remaining = (planned - elapsed.toInt()).coerceAtLeast(0)
    val plannedElapsed = songs.take(currentIndex.coerceAtMost(songs.size)).sumOf { it.durationSeconds + it.gapAfterSeconds }
    val variance = elapsed.toInt() - plannedElapsed
    val currentSong = songs.getOrNull(currentIndex)?.title.orEmpty()
    val songsJson = JSONArray().apply {
        songs.forEachIndexed { i, song ->
            put(
                JSONObject()
                    .put("index", i + 1)
                    .put("title", song.title)
                    .put("duration_seconds", song.durationSeconds)
                    .put("bpm", song.bpm ?: JSONObject.NULL)
                    .put("key", song.key)
                    .put("gap_after_seconds", song.gapAfterSeconds)
                    .put("actual_duration_seconds", song.actualDurationSeconds ?: JSONObject.NULL)
            )
        }
    }.toString()
    val workingValues = As100PerformanceDashboardMethod.calculate(
        mapOf(
            "set_name" to setName,
            "planned_start" to plannedStart,
            "curfew" to curfew,
            "song_count" to songs.size.toString(),
            "planned_seconds" to planned.toString(),
            "completed_count" to currentIndex.coerceAtMost(songs.size).toString(),
            "current_index" to currentIndex.toString(),
            "current_song" to currentSong,
            "running" to running.toString(),
            "elapsed_seconds" to elapsed.toString(),
            "remaining_seconds" to remaining.toString(),
            "variance_seconds" to variance.toString(),
            "songs_json" to songsJson,
        )
    )
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100PerformanceDashboardMethod.fields, it) }
    val committedExecution = committedValues?.let { dashboardExecution(As100PerformanceDashboardMethod, context, it) }

    LaunchedEffect(setName, plannedStart, curfew, currentIndex, running, startedAtEpochMs, accumulatedElapsed, songStartedAtEpochMs) { persistRun() }
    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[As100PerformanceDashboardMethod.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(dashboardExecution(As100PerformanceDashboardMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Performance / set list", capabilityId = As100PerformanceDashboardMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = onBack, onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (committedValues == null || committedExecution == null) {
                if (context.settingShouldBeShown("set_name")) OutlinedTextField(setName, { setName = it }, label = { Text("Set name") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("planned_start")) OutlinedTextField(plannedStart, { plannedStart = it }, label = { Text("Planned start") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("curfew")) OutlinedTextField(curfew, { curfew = it }, label = { Text("Curfew / end time") }, modifier = Modifier.fillMaxWidth())

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CopyableMusicText(
                            value = elapsed.toString(),
                            label = "elapsed seconds",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        CopyableMusicText(value = remaining.toString(), label = "remaining seconds")
                        CopyableMusicText(value = planned.toString(), label = "planned seconds")
                        if (currentSong.isNotBlank()) CopyableMusicText(value = currentSong, label = "current song") else Text("Set complete")
                    }
                }
                CopyableMusicValue("Elapsed", elapsed.toString())
                CopyableMusicValue("Remaining", remaining.toString())
                if (currentSong.isNotBlank()) CopyableMusicValue("Current song", currentSong)

                Button(
                    onClick = {
                        if (!running) {
                            running = true
                            startedAtEpochMs = System.currentTimeMillis()
                            if (songStartedAtEpochMs == 0L) songStartedAtEpochMs = startedAtEpochMs
                        } else {
                            accumulatedElapsed = elapsed
                            running = false
                            startedAtEpochMs = 0L
                        }
                        persistRun()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (running) "PAUSE" else "START") }
                Button(
                    enabled = songs.isNotEmpty() && currentIndex < songs.size,
                    onClick = {
                        val now = System.currentTimeMillis()
                        val actual = if (songStartedAtEpochMs > 0L) ((now - songStartedAtEpochMs).coerceAtLeast(0L) / 1000L).toInt() else null
                        if (actual != null) songs[currentIndex] = songs[currentIndex].copy(actualDurationSeconds = actual)
                        currentIndex = (currentIndex + 1).coerceAtMost(songs.size)
                        songStartedAtEpochMs = now
                        repository.save(songs.toList())
                        persistRun()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("NEXT SONG") }

                LazyColumn(modifier = Modifier.height(240.dp)) {
                    itemsIndexed(songs) { index, song ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                CopyableMusicText(
                                    value = song.title,
                                    label = "song title",
                                    fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                )
                                CopyableMusicValue("Planned duration", MusicAlgorithms.formatDuration(song.durationSeconds))
                                song.bpm?.let { CopyableMusicValue("BPM", "${"%.0f".format(it)}") }
                                if (song.key.isNotBlank()) CopyableMusicValue("Key", song.key)
                                OutlinedButton(
                                    onClick = {
                                        songs.removeAt(index)
                                        currentIndex = currentIndex.coerceAtMost(songs.size)
                                        repository.save(songs.toList())
                                        persistRun()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Remove") }
                            }
                        }
                    }
                }

                Text("Add song", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(newTitle, { newTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(newDuration, { newDuration = it }, label = { Text("Duration mm:ss") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(newBpm, { newBpm = it }, label = { Text("BPM") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(newKey, { newKey = it }, label = { Text("Key") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        val seconds = MusicAlgorithms.parseDuration(newDuration)
                        if (newTitle.isNotBlank() && seconds != null) {
                            songs.add(SetListRepository.Song(UUID.randomUUID().toString(), newTitle, seconds, newBpm.toDoubleOrNull(), newKey, "", 20))
                            newTitle = ""
                            repository.save(songs.toList())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add to set") }
                OutlinedButton(
                    onClick = {
                        currentIndex = 0
                        running = false
                        startedAtEpochMs = 0L
                        accumulatedElapsed = 0L
                        elapsed = 0L
                        songStartedAtEpochMs = 0L
                        persistRun()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset run") }
                OutlinedButton(
                    onClick = {
                        songs.clear()
                        repository.clear()
                        currentIndex = 0
                        running = false
                        startedAtEpochMs = 0L
                        accumulatedElapsed = 0L
                        elapsed = 0L
                        songStartedAtEpochMs = 0L
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear set") }

                Button(
                    enabled = workingValues[As100PerformanceDashboardMethod.fields.status] == "succeeded",
                    onClick = {
                        val execution = dashboardExecution(As100PerformanceDashboardMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit snapshot") }
            } else {
                MusicCommittedPanel(As100PerformanceDashboardMethod, committedValues, committedExecution, onDone = onConfirmed, onEdit = { committedJson = null })
            }
        }
    }
}

@Composable
private fun ReferenceDashboardUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    var root by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["root"] ?: context.action.settings["input_root"] ?: "C") }
    var scale by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["scale"] ?: context.action.settings["input_scale"] ?: "major") }
    var flats by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["prefer_flats"] ?: context.action.settings["input_prefer_flats"] ?: "false") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    val workingValues = As100ReferenceDashboardMethod.calculate(mapOf("root" to root, "scale" to scale, "prefer_flats" to flats))
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100ReferenceDashboardMethod.fields, it) }
    val committedExecution = committedValues?.let { dashboardExecution(As100ReferenceDashboardMethod, context, it) }

    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned, root, scale, flats) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[As100ReferenceDashboardMethod.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(dashboardExecution(As100ReferenceDashboardMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Music reference dashboard", capabilityId = As100ReferenceDashboardMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = onBack, onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (committedValues == null || committedExecution == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CopyableMusicText(value = root, label = "root", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        CopyableMusicText(value = scale, label = "scale")
                        CopyableMusicValue("Scale notes", workingValues[As100ReferenceDashboardMethod.fields.field("notes")].orEmpty())
                        val triads = workingValues[As100ReferenceDashboardMethod.fields.field("triads")].orEmpty()
                        if (triads.isNotBlank()) CopyableMusicValue("Diatonic triads", triads)
                        val relative = workingValues[As100ReferenceDashboardMethod.fields.field("relative_note")].orEmpty()
                        if (relative.isNotBlank()) CopyableMusicValue("Relative note", relative)
                    }
                }
                OutlinedButton(onClick = { val pc = MusicAlgorithms.parsePitchClass(root) ?: 0; root = MusicAlgorithms.pitchClassName(pc - 1, flats.toBoolean()) }, modifier = Modifier.fillMaxWidth()) { Text("← semitone") }
                Button(onClick = { val pc = MusicAlgorithms.parsePitchClass(root) ?: 0; root = MusicAlgorithms.pitchClassName(pc + 1, flats.toBoolean()) }, modifier = Modifier.fillMaxWidth()) { Text("semitone →") }
                if (context.settingShouldBeShown("root")) OutlinedTextField(root, { root = it }, label = { Text("Root") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("scale")) OutlinedTextField(scale, { scale = it }, label = { Text("Scale") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("prefer_flats")) {
                    OutlinedButton(onClick = { flats = "false" }, modifier = Modifier.fillMaxWidth()) { Text("Use sharps") }
                    OutlinedButton(onClick = { flats = "true" }, modifier = Modifier.fillMaxWidth()) { Text("Use flats") }
                }
                Button(
                    enabled = workingValues[As100ReferenceDashboardMethod.fields.status] == "succeeded",
                    onClick = {
                        val execution = dashboardExecution(As100ReferenceDashboardMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit snapshot") }
            } else {
                MusicCommittedPanel(As100ReferenceDashboardMethod, committedValues, committedExecution, onDone = onConfirmed, onEdit = { committedJson = null })
            }
        }
    }
}

private fun dashboardExecution(method: MusicPureMethod, context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = method.request(action = method.id, context = context.request.invocationContext.asMap(method.id) + context.action.settings + values, signals = emptyList(), inputs = emptyList())
    return method.result(request, values, context.request.invocationContext)
}
