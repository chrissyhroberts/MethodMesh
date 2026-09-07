package com.example.methodmesh.modules.scoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

class ScoringCapabilityScreenSpec(
    override val capabilityId: String,
    override val title: String,
    override val description: String
) : CapabilityScreenSpec {

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val view = LocalView.current
        val repository = remember { ScoringSessionRepository.get(androidContext) }
        val method = ScoringMethods.byId(capabilityId) ?: return
        val isRead = capabilityId == ScoringMethods.SessionRead.id
        val isResume = capabilityId == ScoringMethods.SessionResume.id
        val isFinish = capabilityId == ScoringMethods.SessionFinish.id
        val isHighScore = capabilityId == ScoringMethods.HighScore.id

        var participantNames by rememberSaveable { mutableStateOf(context.value("participant_names") ?: "Player 1|Player 2") }
        var ruleset by rememberSaveable { mutableStateOf(context.value("ruleset") ?: "football") }
        var target by rememberSaveable { mutableStateOf(context.value("target") ?: "0") }
        var startingValue by rememberSaveable { mutableStateOf(context.value("starting_value") ?: "0") }
        var increment by rememberSaveable { mutableStateOf(context.value("increment") ?: "1") }
        var bestOf by rememberSaveable { mutableStateOf(context.value("best_of") ?: "3") }
        var keepScreenAwake by rememberSaveable { mutableStateOf(context.value("keep_screen_awake")?.toBooleanStrictOrNull() ?: false) }
        var current by remember { mutableStateOf<ScoreSession?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var activeSessions by remember { mutableStateOf(repository.listActive()) }
        var correctionMode by rememberSaveable { mutableStateOf(false) }
        var correctionValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var error by remember { mutableStateOf("") }
        var automaticHandled by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        DisposableEffect(keepScreenAwake) {
            val old = view.keepScreenOn
            view.keepScreenOn = keepScreenAwake
            onDispose { view.keepScreenOn = old }
        }

        LaunchedEffect(participantNames, ruleset, target, startingValue, increment, bestOf, keepScreenAwake) {
            context.onSettingsChanged(
                mapOf(
                    "participant_names" to participantNames,
                    "ruleset" to ruleset,
                    "target" to target,
                    "starting_value" to startingValue,
                    "increment" to increment,
                    "best_of" to bestOf,
                    "keep_screen_awake" to keepScreenAwake.toString()
                )
            )
        }

        fun executionFor(session: ScoreSession): ExecutionResult {
            val request = method.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + mapOf("score_session_id" to session.id),
                signals = emptyList(), inputs = emptyList()
            )
            return method.result(request, session, context.request.invocationContext)
        }

        fun persist(session: ScoreSession) {
            current = repository.save(session)
            activeSessions = repository.listActive()
        }

        fun finishSession(session: ScoreSession, abandoned: Boolean = false) {
            val finalState = when {
                abandoned && session.status != ScoreSessionStatus.ABANDONED -> ScoringEngine.abandon(session)
                !abandoned && session.status != ScoreSessionStatus.COMPLETED -> ScoringEngine.complete(session)
                else -> session
            }
            val final = repository.save(finalState)
            current = final
            val execution = executionFor(final)
            result = execution
            activeSessions = repository.listActive()
            if (context.submitsImmediately) onConfirmed(execution)
        }

        LaunchedEffect(capabilityId, context.action.settings) {
            if (automaticHandled) return@LaunchedEffect
            val sessionId = context.value("score_session_id")
            if (isRead || isFinish) {
                automaticHandled = true
                val session = sessionId?.let(repository::load)
                if (session == null) {
                    error = "Score session not found."
                    val request = method.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + context.action.settings, emptyList(), emptyList())
                    val failed = method.failure(request, error, context.request.invocationContext)
                    result = failed
                    if (context.submitsImmediately) onConfirmed(failed)
                } else {
                    val returned = if (isFinish && session.status !in setOf(ScoreSessionStatus.COMPLETED, ScoreSessionStatus.ABANDONED)) {
                        repository.save(ScoringEngine.complete(session))
                    } else session
                    current = returned
                    val execution = executionFor(returned)
                    result = execution
                    if (context.submitsImmediately) onConfirmed(execution)
                }
            } else if (isResume && !sessionId.isNullOrBlank()) {
                automaticHandled = true
                repository.load(sessionId)?.let { loaded ->
                    persist(if (loaded.status == ScoreSessionStatus.PAUSED) ScoringEngine.resume(loaded) else loaded)
                } ?: run { error = "Score session not found." }
            } else if (!sessionId.isNullOrBlank()) {
                repository.load(sessionId)?.let { current = it }
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { fields ->
                OutputFormatter.fields(fields, includeProvenance = false).filterKeys { it in setOf(ScoringFields.RESULT, ScoringFields.WINNER, ScoringFields.SESSION_ID, ScoringFields.SESSION_STATUS) }
            }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; error = "" },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

            when {
                isRead || isFinish -> current?.let { SessionSummary(it) }
                isHighScore -> HighScoreEditor(context, repository, method, onConfirmed) { result = it }
                current == null -> StartOrResume(
                    context = context,
                    participantNames = participantNames,
                    onParticipantNamesChanged = { participantNames = it },
                    ruleset = ruleset,
                    onRulesetChanged = { ruleset = it },
                    target = target,
                    onTargetChanged = { target = it.filter(Char::isDigit) },
                    startingValue = startingValue,
                    onStartingValueChanged = { startingValue = it.filter { c -> c.isDigit() || c == '-' } },
                    increment = increment,
                    onIncrementChanged = { increment = it.filter(Char::isDigit) },
                    bestOf = bestOf,
                    onBestOfChanged = { bestOf = it.filter(Char::isDigit) },
                    keepScreenAwake = keepScreenAwake,
                    onKeepScreenAwake = { keepScreenAwake = it },
                    activeSessions = activeSessions,
                    onResume = { persist(if (it.status == ScoreSessionStatus.PAUSED) ScoringEngine.resume(it) else it) },
                    onStart = {
                        val settings = context.action.settings + mapOf(
                            "participant_names" to participantNames,
                            "ruleset" to ruleset,
                            "target" to target,
                            "starting_value" to startingValue,
                            "increment" to increment,
                            "best_of" to bestOf,
                            "keep_screen_awake" to keepScreenAwake.toString()
                        )
                        persist(ScoringEngine.create(capabilityId, settings))
                    }
                )
                else -> current?.let { session ->
                    Scoreboard(
                        session = session,
                        correctionMode = correctionMode,
                        correctionValues = correctionValues,
                        onCorrectionValues = { correctionValues = it },
                        onToggleCorrection = {
                            correctionMode = !correctionMode
                            if (correctionMode) correctionValues = session.participants.associate { it.id to it.score.toString() }
                        },
                        onDelta = { participantId, delta -> persist(ScoringEngine.delta(session, participantId, delta)) },
                        onSportAction = { participantId, action -> persist(ScoringEngine.sportAction(session, participantId, action)) },
                        onUndo = { persist(ScoringEngine.undo(session)) },
                        onNextRound = { persist(ScoringEngine.nextRound(session)) },
                        onApplyCorrection = {
                            val values = correctionValues.mapNotNull { (k, v) -> v.toIntOrNull()?.let { k to it } }.toMap()
                            persist(ScoringEngine.correct(session, values)); correctionMode = false
                        },
                        onPauseResume = {
                            persist(if (session.status == ScoreSessionStatus.PAUSED) ScoringEngine.resume(session) else ScoringEngine.pause(session))
                        },
                        onFinish = { finishSession(session) },
                        onAbandon = { finishSession(session, abandoned = true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartOrResume(
    context: CapabilityScreenContext,
    participantNames: String,
    onParticipantNamesChanged: (String) -> Unit,
    ruleset: String,
    onRulesetChanged: (String) -> Unit,
    target: String,
    onTargetChanged: (String) -> Unit,
    startingValue: String,
    onStartingValueChanged: (String) -> Unit,
    increment: String,
    onIncrementChanged: (String) -> Unit,
    bestOf: String,
    onBestOfChanged: (String) -> Unit,
    keepScreenAwake: Boolean,
    onKeepScreenAwake: (Boolean) -> Unit,
    activeSessions: List<ScoreSession>,
    onResume: (ScoreSession) -> Unit,
    onStart: () -> Unit
) {
    if (activeSessions.isNotEmpty()) {
        Text("Active sessions", style = MaterialTheme.typography.titleMedium)
        activeSessions.take(5).forEach { session ->
            OutlinedButton(onClick = { onResume(session) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${session.title} · ${ScoringEngine.resultText(session)}")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Text("New session", style = MaterialTheme.typography.titleMedium)
    if (context.settingShouldBeShown("participant_names")) {
        OutlinedTextField(participantNames, onParticipantNamesChanged, label = { Text("Participants (separate with |)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
    if (context.action.canonicalId !in setOf(ScoringMethods.Sports.id, ScoringMethods.SetMatch.id, ScoringMethods.HighScore.id)) {
        if (context.settingShouldBeShown("starting_value")) OutlinedTextField(startingValue, onStartingValueChanged, label = { Text("Starting value") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (context.settingShouldBeShown("increment")) OutlinedTextField(increment, onIncrementChanged, label = { Text("Standard increment") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
    if (context.action.canonicalId in setOf(ScoringMethods.Sports.id, ScoringMethods.SetMatch.id) && context.settingShouldBeShown("ruleset")) {
        Text("Sport", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        SportsRules.all.chunked(3).forEach { rowRules ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowRules.forEach { rule ->
                    val button: @Composable () -> Unit = { Text(if (ruleset == rule.id) "✓ ${rule.displayName}" else rule.displayName) }
                    if (ruleset == rule.id) Button(onClick = { onRulesetChanged(rule.id) }, modifier = Modifier.weight(1f)) { button() }
                    else OutlinedButton(onClick = { onRulesetChanged(rule.id) }, modifier = Modifier.weight(1f)) { button() }
                }
                repeat(3 - rowRules.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (context.settingShouldBeShown("target")) OutlinedTextField(target, onTargetChanged, label = { Text("Target / points per game (0 = ruleset default)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (context.settingShouldBeShown("best_of")) OutlinedTextField(bestOf, onBestOfChanged, label = { Text("Best of") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onKeepScreenAwake(!keepScreenAwake) }, modifier = Modifier.weight(1f)) {
            Text(if (keepScreenAwake) "✓ Keep screen awake" else "Keep screen awake")
        }
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Start") }
}

@Composable
private fun Scoreboard(
    session: ScoreSession,
    correctionMode: Boolean,
    correctionValues: Map<String, String>,
    onCorrectionValues: (Map<String, String>) -> Unit,
    onToggleCorrection: () -> Unit,
    onDelta: (String, Int) -> Unit,
    onSportAction: (String, String) -> Unit,
    onUndo: () -> Unit,
    onNextRound: () -> Unit,
    onApplyCorrection: () -> Unit,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
    onAbandon: () -> Unit
) {
    val isActive = session.status == ScoreSessionStatus.ACTIVE
    val standardIncrement = runCatching { JSONObject(session.configJson).optInt("increment", 1).coerceAtLeast(1) }.getOrDefault(1)
    Text(session.title.ifBlank { "Score" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("${session.status.name.lowercase()} · ${session.id.take(8)}", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    session.participants.forEachIndexed { index, p ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(p.label, style = MaterialTheme.typography.titleMedium)
                val display = if (SportsRules.byId(session.ruleset).structured && session.participants.size >= 2) ScoringEngine.structuredScoreLabel(session, index) else p.score.toString()
                Text(display, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                if (session.methodId == ScoringMethods.Sports.id || session.methodId == ScoringMethods.SetMatch.id) {
                    val rule = SportsRules.byId(session.ruleset)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rule.actions.forEach { action ->
                            Button(onClick = { onSportAction(p.id, action.id) }, enabled = isActive, modifier = Modifier.weight(1f)) { Text(action.label) }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onDelta(p.id, -standardIncrement) }, enabled = isActive, modifier = Modifier.weight(1f)) { Text("−$standardIncrement") }
                        Button(onClick = { onDelta(p.id, standardIncrement) }, enabled = isActive, modifier = Modifier.weight(1f)) { Text("+$standardIncrement") }
                        OutlinedButton(onClick = { onDelta(p.id, standardIncrement * 5) }, enabled = isActive, modifier = Modifier.weight(1f)) { Text("+${standardIncrement * 5}") }
                    }
                }
            }
        }
    }

    StructuredStateSummary(session)
    if (session.methodId == ScoringMethods.Rounds.id) {
        val round = runCatching { JSONObject(session.stateJson).optInt("current_round", 1) }.getOrDefault(1)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Round $round", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onNextRound, enabled = isActive) { Text("Next round") }
        }
    }

    if (correctionMode) {
        Text("Correct score", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        session.participants.forEach { p ->
            OutlinedTextField(
                value = correctionValues[p.id] ?: p.score.toString(),
                onValueChange = { value -> onCorrectionValues(correctionValues + (p.id to value.filter { it.isDigit() || it == '-' })) },
                label = { Text(p.label) }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
        Button(onClick = onApplyCorrection, modifier = Modifier.fillMaxWidth()) { Text("Apply correction") }
    }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onUndo, enabled = session.events.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Undo") }
        OutlinedButton(onClick = onToggleCorrection, enabled = session.status != ScoreSessionStatus.ABANDONED, modifier = Modifier.weight(1f)) { Text(if (correctionMode) "Cancel correction" else "Correct") }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onPauseResume, enabled = session.status in setOf(ScoreSessionStatus.ACTIVE, ScoreSessionStatus.PAUSED), modifier = Modifier.weight(1f)) { Text(if (session.status == ScoreSessionStatus.PAUSED) "Resume" else "Pause") }
        Button(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("Finish") }
    }
    OutlinedButton(onClick = onAbandon, enabled = session.status !in setOf(ScoreSessionStatus.COMPLETED, ScoreSessionStatus.ABANDONED), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Abandon session") }
}

@Composable
private fun StructuredStateSummary(session: ScoreSession) {
    val s = runCatching { JSONObject(session.stateJson) }.getOrNull() ?: return
    when (session.ruleset) {
        "tennis", "padel" -> Text("Sets ${s.optInt("sets_a")}–${s.optInt("sets_b")} · Games ${s.optInt("games_a")}–${s.optInt("games_b")}${if (s.optBoolean("tiebreak")) " · Tie-break" else ""}", style = MaterialTheme.typography.bodyMedium)
        "badminton", "table_tennis", "volleyball", "squash" -> Text("Games ${s.optInt("games_a")}–${s.optInt("games_b")}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SessionSummary(session: ScoreSession) {
    Text(ScoringEngine.resultText(session), style = MaterialTheme.typography.headlineMedium)
    Text("Status: ${session.status.name.lowercase()}")
    Text("Session: ${session.id}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HighScoreEditor(
    context: CapabilityScreenContext,
    repository: ScoringSessionRepository,
    method: ScoringAs100Method,
    onConfirmed: (ExecutionResult) -> Unit,
    onResult: (ExecutionResult) -> Unit
) {
    var activity by rememberSaveable { mutableStateOf(context.value("activity") ?: "") }
    var participant by rememberSaveable { mutableStateOf(context.value("participant") ?: "") }
    var score by rememberSaveable { mutableStateOf(context.value("score") ?: "0") }
    var note by rememberSaveable { mutableStateOf(context.value("note") ?: "") }
    val records = remember(activity) { repository.listHighScores(activity.ifBlank { null }) }

    OutlinedTextField(activity, { activity = it }, label = { Text("Activity") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(participant, { participant = it }, label = { Text("Participant") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(score, { score = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Score") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        val numeric = score.toIntOrNull() ?: return@Button
        repository.saveHighScore(HighScoreRecord(activity = activity.ifBlank { "Score" }, participant = participant.ifBlank { "Participant" }, score = numeric, note = note))
        val session = ScoreSession(
            id = java.util.UUID.randomUUID().toString(), methodId = ScoringMethods.HighScore.id,
            title = activity.ifBlank { "High score" }, ruleset = "high_score", status = ScoreSessionStatus.COMPLETED,
            participants = listOf(ScoreParticipant("p1", participant.ifBlank { "Participant" }, numeric)), events = emptyList(),
            stateJson = "{}", configJson = "{}", startedAtIso = java.time.Instant.now().toString(), updatedAtIso = java.time.Instant.now().toString(), finishedAtIso = java.time.Instant.now().toString()
        )
        val request = method.request(method.id, context.request.invocationContext.asMap(method.id) + context.action.settings, emptyList(), emptyList())
        val execution = method.result(request, session, context.request.invocationContext)
        onResult(execution)
        if (context.submitsImmediately) onConfirmed(execution)
    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save record") }

    if (records.isNotEmpty()) {
        Text("Saved records", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        records.take(10).forEachIndexed { index, r -> Text("${index + 1}. ${r.participant}: ${r.score}") }
    }
}

private fun CapabilityScreenContext.value(key: String): String? =
    (action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"])
        ?.takeIf { it.isNotBlank() }

object ScoringCapabilityScreens {
    val all = listOf(
        ScoringCapabilityScreenSpec(ScoringMethods.Counter.id, "Counter", "Persistent multi-entity counter."),
        ScoringCapabilityScreenSpec(ScoringMethods.Tally.id, "Tally", "Fast persistent categorical tally."),
        ScoringCapabilityScreenSpec(ScoringMethods.Match.id, "Match score", "Generic head-to-head scoreboard."),
        ScoringCapabilityScreenSpec(ScoringMethods.Rounds.id, "Round scoring", "Accumulate scores across rounds."),
        ScoringCapabilityScreenSpec(ScoringMethods.RaceTo.id, "Race to target", "First participant to a target score."),
        ScoringCapabilityScreenSpec(ScoringMethods.SetMatch.id, "Set match", "Hierarchical set-based scoring."),
        ScoringCapabilityScreenSpec(ScoringMethods.Sports.id, "Sports scorer", "Ruleset-aware persistent sports scoring."),
        ScoringCapabilityScreenSpec(ScoringMethods.HighScore.id, "High scores", "Explicitly save a score or personal best."),
        ScoringCapabilityScreenSpec(ScoringMethods.SessionRead.id, "Read score session", "Read current persistent scoring state."),
        ScoringCapabilityScreenSpec(ScoringMethods.SessionResume.id, "Resume score session", "Resume a persistent score session."),
        ScoringCapabilityScreenSpec(ScoringMethods.SessionFinish.id, "Finish score session", "Finish a persistent score session and return the result.")
    )
}
