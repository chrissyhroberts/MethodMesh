package com.example.methodmesh.modules.tabletoputilities

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import java.time.Instant

object TabletopUtilitiesCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TabletopUtilitiesMethod.ID
    override val title = "Tabletop utilities"
    override val description = "Persistent game workspaces with counters, characters, initiative, effects, scoring and an audit trail."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val supplied = remember(context.action.settings, context.request.settings) {
            context.request.settings + context.action.settings
        }

        var workspaces by remember { mutableStateOf(emptyList<GameWorkspace>()) }
        var selectedWorkspaceId by rememberSaveable {
            mutableStateOf(supplied.value("workspace_id").orEmpty())
        }
        var resultValuesJson by rememberSaveable { mutableStateOf("") }
        var statusMessage by rememberSaveable { mutableStateOf("") }
        var showCreateWorkspace by rememberSaveable { mutableStateOf(false) }
        var showAddCharacter by rememberSaveable { mutableStateOf(false) }
        var showAddPlayer by rememberSaveable { mutableStateOf(false) }
        var showAddCounter by rememberSaveable { mutableStateOf(false) }
        var showAddEffect by rememberSaveable { mutableStateOf(false) }
        var showAddInitiative by rememberSaveable { mutableStateOf(false) }
        var showSessionNote by rememberSaveable { mutableStateOf(false) }
        var showHistory by rememberSaveable { mutableStateOf(false) }
        var showArchiveConfirm by rememberSaveable { mutableStateOf(false) }
        var externalRunDone by rememberSaveable { mutableStateOf(false) }

        val result = remember(resultValuesJson) {
            resultValuesJson.takeIf { it.isNotBlank() }?.let { encoded ->
                val values = JSONObject(encoded).toStringMap()
                As100TabletopUtilitiesMethod.result(
                    request = As100TabletopUtilitiesMethod.request(As100TabletopUtilitiesMethod.ID, values),
                    values = values,
                    invocation = context.request.invocationContext
                )
            }
        }

        fun refresh(selectId: String? = null) {
            workspaces = TabletopUtilitiesRepository.listWorkspaces()
            if (selectId != null) selectedWorkspaceId = selectId
            if (selectedWorkspaceId.isNotBlank() && workspaces.none { it.id == selectedWorkspaceId }) {
                selectedWorkspaceId = ""
            }
        }

        fun mutate(mutation: TabletopMutation) {
            if (selectedWorkspaceId.isBlank()) return
            runCatching {
                TabletopUtilitiesRepository.mutate(selectedWorkspaceId, mutation).also { outcome ->
                    statusMessage = outcome.summary
                }
            }.onFailure { statusMessage = it.message ?: "State change failed." }
            refresh(selectedWorkspaceId)
        }

        fun captureSnapshot(workspace: GameWorkspace) {
            resultValuesJson = JSONObject(As100TabletopUtilitiesMethod.snapshotValues(workspace)).toString()
        }

        val diceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned ->
            val data = returned.data
            val diceResult = data?.getStringExtra("dice_result").orEmpty()
            val diceAudit = data?.getStringExtra("dice_audit_json")
            if (selectedWorkspaceId.isNotBlank() && diceResult.isNotBlank()) {
                runCatching { TabletopUtilitiesRepository.recordDiceRoll(selectedWorkspaceId, diceResult, diceAudit) }
                statusMessage = "Dice: $diceResult"
                refresh(selectedWorkspaceId)
            } else if (diceResult.isBlank()) {
                statusMessage = "Dice Simulator returned no dice_result."
            }
        }

        fun launchDice() {
            val intent = Intent("com.example.methodmesh.EXECUTE_METHOD")
                .setPackage(androidContext.packageName)
                .putExtra("method_id", "dice.simulate")
                .putExtra("input_expression", "d20")
                .putExtra("input_roll_count", "1")
                .putExtra("input_history_output", "true")
                .putExtra("input_rng_mode", "secure_random")
                .putExtra("input_animation_mode", "fast")
                .putExtra("input_payload_mode", "FULL")
                .putExtra("return_mode", "flat")
            runCatching { diceLauncher.launch(intent) }
                .onFailure { statusMessage = "Dice Simulator is unavailable: ${it.message.orEmpty()}" }
        }

        LaunchedEffect(Unit) {
            TabletopUtilitiesRepository.initialise(androidContext)
            refresh()
        }

        val requestedOperation = supplied.value("operation") ?: "dashboard"
        val automaticOperation = context.presentationMode == CapabilityPresentationMode.IntentLaunch &&
            context.completionMode == CapabilityCompletionMode.AutomaticReturn &&
            requestedOperation != "dashboard"

        LaunchedEffect(automaticOperation, requestedOperation, externalRunDone) {
            if (automaticOperation && !externalRunDone) {
                TabletopUtilitiesRepository.initialise(androidContext)
                val values = As100TabletopUtilitiesMethod.runOperation(supplied, source = "external_intent")
                resultValuesJson = JSONObject(values).toString()
                externalRunDone = true
            }
        }

        LaunchedEffect(selectedWorkspaceId) {
            if (selectedWorkspaceId.isNotBlank()) {
                context.onSettingsChanged(mapOf("workspace_id" to selectedWorkspaceId))
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { execution ->
                val fields = OutputFormatter.fields(execution, false)
                mapOf(TabletopUtilitiesFields.RESULT to fields[TabletopUtilitiesFields.RESULT])
            }.orEmpty(),
            onBack = onBack,
            onRetry = { resultValuesJson = "" },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            when {
                automaticOperation -> Text("Applying tabletop state operation…")
                context.isNativePresetRun && requestedOperation != "dashboard" -> PresetOperationPanel(
                    context = context,
                    supplied = supplied,
                    onRun = { settings ->
                        val values = As100TabletopUtilitiesMethod.runOperation(settings, source = "native_preset")
                        resultValuesJson = JSONObject(values).toString()
                    }
                )
                selectedWorkspaceId.isBlank() -> WorkspaceLibrary(
                    workspaces = workspaces,
                    onOpen = { selectedWorkspaceId = it.id },
                    onCreate = { showCreateWorkspace = true }
                )
                else -> {
                    val workspace = workspaces.firstOrNull { it.id == selectedWorkspaceId }
                    if (workspace == null) {
                        Text("Workspace not found.")
                        OutlinedButton(onClick = { selectedWorkspaceId = "" }) { Text("Back to games") }
                    } else {
                        WorkspaceDashboard(
                            workspace = workspace,
                            statusMessage = statusMessage,
                            auditEvents = if (showHistory) TabletopUtilitiesRepository.readAudit(workspace.id, 80) else emptyList(),
                            showHistory = showHistory,
                            onBackToLibrary = { selectedWorkspaceId = ""; showHistory = false },
                            onDice = ::launchDice,
                            onToggleHistory = { showHistory = !showHistory },
                            onUndo = {
                                val outcome = TabletopUtilitiesRepository.undoLast(workspace.id)
                                statusMessage = outcome?.summary?.let { "Undo · $it" } ?: "Nothing available to undo."
                                refresh(workspace.id)
                            },
                            onSnapshot = { captureSnapshot(workspace) },
                            onAddCharacter = { showAddCharacter = true },
                            onAddPlayer = { showAddPlayer = true },
                            onAddCounter = { showAddCounter = true },
                            onAddEffect = { showAddEffect = true },
                            onAddInitiative = { showAddInitiative = true },
                            onMutation = ::mutate,
                            onSessionNote = { showSessionNote = true },
                            onArchive = { showArchiveConfirm = true }
                        )
                    }
                }
            }
        }

        if (showCreateWorkspace) {
            CreateWorkspaceDialog(
                onDismiss = { showCreateWorkspace = false },
                onCreate = { name, ruleset, features ->
                    val created = TabletopUtilitiesRepository.createWorkspace(name, ruleset, features)
                    showCreateWorkspace = false
                    refresh(created.id)
                }
            )
        }

        val currentWorkspace = workspaces.firstOrNull { it.id == selectedWorkspaceId }
        if (showAddCharacter && currentWorkspace != null) {
            AddCharacterDialog(currentWorkspace, onDismiss = { showAddCharacter = false }) { character, hpMax ->
                mutate(TabletopMutation.AddCharacter(character, hpMax))
                showAddCharacter = false
            }
        }
        if (showAddPlayer && currentWorkspace != null) {
            TextEntryDialog("Add player", "Player name", onDismiss = { showAddPlayer = false }) { value ->
                mutate(TabletopMutation.AddPlayer(GamePlayer(name = value)))
                showAddPlayer = false
            }
        }
        if (showAddCounter && currentWorkspace != null) {
            AddCounterDialog(currentWorkspace, onDismiss = { showAddCounter = false }) { counter ->
                mutate(TabletopMutation.AddCounter(counter))
                showAddCounter = false
            }
        }
        if (showAddEffect && currentWorkspace != null) {
            AddEffectDialog(currentWorkspace, onDismiss = { showAddEffect = false }) { effect ->
                mutate(TabletopMutation.AddEffect(effect))
                showAddEffect = false
            }
        }
        if (showAddInitiative && currentWorkspace != null) {
            AddInitiativeDialog(currentWorkspace, onDismiss = { showAddInitiative = false }) { entry ->
                mutate(TabletopMutation.AddInitiativeEntry(entry))
                showAddInitiative = false
            }
        }
        if (showSessionNote && currentWorkspace != null) {
            TextEntryDialog("Session note", "Note", multiline = true, onDismiss = { showSessionNote = false }) { value ->
                mutate(TabletopMutation.AddSessionNote(value))
                showSessionNote = false
            }
        }
        if (showArchiveConfirm && currentWorkspace != null) {
            AlertDialog(
                onDismissRequest = { showArchiveConfirm = false },
                title = { Text("Archive ${currentWorkspace.name}?") },
                text = { Text("The workspace disappears from the normal library but its local folder and audit history remain on the device.") },
                confirmButton = {
                    Button(onClick = {
                        TabletopUtilitiesRepository.archiveWorkspace(currentWorkspace.id)
                        showArchiveConfirm = false
                        selectedWorkspaceId = ""
                        refresh()
                    }) { Text("Archive") }
                },
                dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun WorkspaceLibrary(
    workspaces: List<GameWorkspace>,
    onOpen: (GameWorkspace) -> Unit,
    onCreate: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Games", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Each game keeps its own configuration, live state, sessions and audit history.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (workspaces.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                Text("No game workspaces yet.", Modifier.padding(16.dp))
            }
        }
        workspaces.forEach { workspace ->
            Card(onClick = { onOpen(workspace) }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(workspace.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (workspace.ruleset.isNotBlank()) Text(workspace.ruleset, style = MaterialTheme.typography.bodyMedium)
                    val session = workspace.activeSession?.name ?: workspace.sessions.lastOrNull()?.name ?: "No sessions yet"
                    Text("$session · ${workspace.characters.size} characters · ${workspace.players.size} players", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("+ New game") }
    }
}

@Composable
private fun WorkspaceDashboard(
    workspace: GameWorkspace,
    statusMessage: String,
    auditEvents: List<TabletopAuditEvent>,
    showHistory: Boolean,
    onBackToLibrary: () -> Unit,
    onDice: () -> Unit,
    onToggleHistory: () -> Unit,
    onUndo: () -> Unit,
    onSnapshot: () -> Unit,
    onAddCharacter: () -> Unit,
    onAddPlayer: () -> Unit,
    onAddCounter: () -> Unit,
    onAddEffect: () -> Unit,
    onAddInitiative: () -> Unit,
    onMutation: (TabletopMutation) -> Unit,
    onSessionNote: () -> Unit,
    onArchive: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(workspace.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (workspace.ruleset.isNotBlank()) Text(workspace.ruleset, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onDice) { Text("🎲 Dice") }
        }
        Text(
            buildString {
                append(workspace.activeSession?.name ?: "No active session")
                if (TabletopFeature.INITIATIVE in workspace.features || TabletopFeature.EFFECTS in workspace.features) append(" · Round ${workspace.initiative.round}")
                workspace.initiative.current?.let { append(" · ${it.name}") }
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (statusMessage.isNotBlank()) Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onBackToLibrary, modifier = Modifier.weight(1f)) { Text("Games") }
                OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) { Text("↶ Undo") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onToggleHistory, modifier = Modifier.weight(1f)) { Text(if (showHistory) "Hide history" else "History") }
                OutlinedButton(onClick = onSnapshot, modifier = Modifier.weight(1f)) { Text("Use current state") }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (showHistory) {
            SectionCard("Audit history") {
                if (auditEvents.isEmpty()) Text("No events recorded.")
                auditEvents.asReversed().forEach { event ->
                    Text("${event.timestampIso.substringAfter('T').take(8)}  ${event.summary}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (TabletopFeature.SESSIONS in workspace.features) {
            SectionCard("Session") {
                val active = workspace.activeSession
                if (active == null) {
                    Button(onClick = { onMutation(TabletopMutation.StartSession("Session ${workspace.sessions.size + 1}", Instant.now().toString())) }) { Text("Start session") }
                } else {
                    Text(active.name, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onSessionNote, modifier = Modifier.weight(1f)) { Text("Add note") }
                        OutlinedButton(onClick = { onMutation(TabletopMutation.FinishSession(Instant.now().toString())) }, modifier = Modifier.weight(1f)) { Text("Finish session") }
                    }
                }
                workspace.sessions.takeLast(3).asReversed().forEach { session ->
                    Text("${session.name}${if (session.endedAtIso == null) " · active" else ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (workspace.workspaceCounters().isNotEmpty() || TabletopFeature.COUNTERS in workspace.features || TabletopFeature.RESOURCES in workspace.features) {
            SectionCard("Game counters") {
                workspace.workspaceCounters().forEach { CounterRow(it, onMutation) }
                OutlinedButton(onClick = onAddCounter, modifier = Modifier.fillMaxWidth()) { Text("+ Counter / resource") }
            }
        }

        if (TabletopFeature.CHARACTERS in workspace.features || workspace.characters.isNotEmpty()) {
            Text("Characters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            workspace.characters.forEach { character ->
                CharacterCard(workspace, character, onMutation, onAddEffect)
            }
            OutlinedButton(onClick = onAddCharacter, modifier = Modifier.fillMaxWidth()) { Text("+ Character") }
            Spacer(Modifier.height(8.dp))
        }

        if (TabletopFeature.SCORES in workspace.features || TabletopFeature.CHARACTERS in workspace.features || workspace.players.isNotEmpty()) {
            SectionCard(if (TabletopFeature.SCORES in workspace.features) "Players / scores" else "Players") {
                workspace.players.forEach { player ->
                    val score = workspace.countersForPlayer(player.id).firstOrNull { it.kind == CounterKind.SCORE }
                    if (score != null) {
                        Text(player.name, fontWeight = FontWeight.Bold)
                        CounterRow(score, onMutation)
                        val personalBest = workspace.scores.filter { it.playerId == player.id }.maxOfOrNull { it.score }
                        if (personalBest != null) Text("Personal best: $personalBest", style = MaterialTheme.typography.bodySmall)
                    } else Text(player.name)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onAddPlayer, modifier = Modifier.weight(1f)) { Text("+ Player") }
                    if (workspace.players.isNotEmpty() && TabletopFeature.SCORES in workspace.features) {
                        Button(onClick = { onMutation(TabletopMutation.RecordScores(Instant.now().toString())) }, modifier = Modifier.weight(1f)) { Text("Record scores") }
                    }
                }
                workspace.scores.sortedByDescending { it.score }.take(5).forEachIndexed { index, score ->
                    Text("${index + 1}. ${score.playerName} · ${score.score}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (TabletopFeature.INITIATIVE in workspace.features) {
            SectionCard("Initiative") {
                Text("Round ${workspace.initiative.round}${workspace.initiative.current?.let { " · ${it.name}" }.orEmpty()}", fontWeight = FontWeight.Bold)
                workspace.initiative.entries.forEachIndexed { index, entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${if (workspace.initiative.active && index == workspace.initiative.currentIndex) "▶ " else ""}${entry.name}", modifier = Modifier.weight(1f))
                        SmallStepButton("−") { onMutation(TabletopMutation.AdjustInitiativeScore(entry.id, -1)) }
                        Text(entry.score.toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                        SmallStepButton("+") { onMutation(TabletopMutation.AdjustInitiativeScore(entry.id, 1)) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onAddInitiative, modifier = Modifier.weight(1f)) { Text("+ Entry") }
                        if (!workspace.initiative.active && workspace.initiative.entries.isNotEmpty()) {
                            Button(onClick = { onMutation(TabletopMutation.StartInitiative) }, modifier = Modifier.weight(1f)) { Text("Start") }
                        } else if (workspace.initiative.active) {
                            Button(onClick = { onMutation(TabletopMutation.NextTurn) }, modifier = Modifier.weight(1f)) { Text("Next turn") }
                        }
                    }
                    OutlinedButton(onClick = { onMutation(TabletopMutation.NextRound) }, modifier = Modifier.fillMaxWidth()) { Text("Next round") }
                }
                Text("Round changes automatically decrement active round-based effects.", style = MaterialTheme.typography.bodySmall)
            }
        } else if (TabletopFeature.EFFECTS in workspace.features) {
            SectionCard("Round ticker") {
                Text("Round ${workspace.initiative.round}", fontWeight = FontWeight.Bold)
                Button(onClick = { onMutation(TabletopMutation.NextRound) }) { Text("Next round") }
            }
        }

        if (TabletopFeature.EFFECTS in workspace.features && workspace.characters.isEmpty()) {
            SectionCard("Effects") {
                workspace.effects.filter { it.characterId == null }.forEach { EffectRow(it, onMutation) }
                OutlinedButton(onClick = onAddEffect) { Text("+ Effect") }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text("Archive game") }
    }
}

@Composable
private fun CharacterCard(
    workspace: GameWorkspace,
    character: GameCharacter,
    onMutation: (TabletopMutation) -> Unit,
    onAddEffect: () -> Unit
) {
    SectionCard(character.name) {
        if (character.notes.isNotBlank()) Text(character.notes, style = MaterialTheme.typography.bodySmall)
        workspace.countersForCharacter(character.id).forEach { CounterRow(it, onMutation) }
        if (TabletopFeature.DEATH_SAVES in workspace.features) {
            val saves = workspace.deathSaves.firstOrNull { it.characterId == character.id } ?: DeathSaveState(character.id)
            Text("Death saves", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Success ${"●".repeat(saves.successes)}${"○".repeat(3 - saves.successes)}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallStepButton("−") { onMutation(TabletopMutation.SetDeathSaves(character.id, saves.successes - 1, saves.failures)) }
                    SmallStepButton("+") { onMutation(TabletopMutation.SetDeathSaves(character.id, saves.successes + 1, saves.failures)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Failure ${"●".repeat(saves.failures)}${"○".repeat(3 - saves.failures)}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallStepButton("−") { onMutation(TabletopMutation.SetDeathSaves(character.id, saves.successes, saves.failures - 1)) }
                    SmallStepButton("+") { onMutation(TabletopMutation.SetDeathSaves(character.id, saves.successes, saves.failures + 1)) }
                }
            }
        }
        if (TabletopFeature.EFFECTS in workspace.features) {
            Text("Effects", fontWeight = FontWeight.SemiBold)
            workspace.effects.filter { it.characterId == character.id }.forEach { EffectRow(it, onMutation) }
            OutlinedButton(onClick = onAddEffect) { Text("+ Effect") }
        }
    }
}

@Composable
private fun CounterRow(counter: GameCounter, onMutation: (TabletopMutation) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(counter.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (counter.maximum != null) "${counter.current} / ${counter.maximum}" else counter.current.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            counter.quickSteps.distinct().sorted().chunked(4).forEach { steps ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    steps.forEach { step ->
                        OutlinedButton(
                            onClick = { onMutation(TabletopMutation.AdjustCounter(counter.id, step)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (step > 0) "+$step" else step.toString())
                        }
                    }
                    repeat(4 - steps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EffectRow(effect: GameEffect, onMutation: (TabletopMutation) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.weight(1f)) {
            Checkbox(checked = effect.active, onCheckedChange = { onMutation(TabletopMutation.ToggleEffect(effect.id)) })
            Text(effect.name, modifier = Modifier.padding(top = 12.dp))
        }
        if (effect.remainingRounds != null) {
            SmallStepButton("−") { onMutation(TabletopMutation.SetEffectDuration(effect.id, effect.remainingRounds - 1)) }
            Text("${effect.remainingRounds} rnd", modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp))
            SmallStepButton("+") { onMutation(TabletopMutation.SetEffectDuration(effect.id, effect.remainingRounds + 1)) }
        } else {
            Text("∞", modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SmallStepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text(label) }
}

@Composable
private fun CreateWorkspaceDialog(onDismiss: () -> Unit, onCreate: (String, String, Set<TabletopFeature>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var ruleset by rememberSaveable { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(
            setOf(
                TabletopFeature.HP, TabletopFeature.TEMP_HP, TabletopFeature.EXP, TabletopFeature.EFFECTS,
                TabletopFeature.DEATH_SAVES, TabletopFeature.INITIATIVE, TabletopFeature.CHARACTERS, TabletopFeature.SESSIONS
            )
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New game") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Game / campaign name") }, singleLine = true)
                OutlinedTextField(ruleset, { ruleset = it }, label = { Text("Ruleset label (optional)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Utilities", fontWeight = FontWeight.Bold)
                TabletopFeature.entries.forEach { feature ->
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = feature in selected,
                            onCheckedChange = { checked -> selected = if (checked) selected + feature else selected - feature }
                        )
                        Text(feature.label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onCreate(name, ruleset, selected) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddCharacterDialog(workspace: GameWorkspace, onDismiss: () -> Unit, onCreate: (GameCharacter, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var hpMax by rememberSaveable { mutableStateOf("10") }
    var playerId by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add character") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Character name") }, singleLine = true)
                if (TabletopFeature.HP in workspace.features) OutlinedTextField(hpMax, { hpMax = it.filter(Char::isDigit) }, label = { Text("Starting / maximum HP") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
                if (workspace.players.isNotEmpty()) {
                    Text("Player (optional)", modifier = Modifier.padding(top = 6.dp))
                    workspace.players.forEach { player ->
                        Row {
                            RadioButton(selected = playerId == player.id, onClick = { playerId = if (playerId == player.id) "" else player.id })
                            Text(player.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onCreate(GameCharacter(name = name.trim(), playerId = playerId.takeIf(String::isNotBlank), notes = notes.trim()), hpMax.toIntOrNull()?.coerceAtLeast(1) ?: 10)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddCounterDialog(workspace: GameWorkspace, onDismiss: () -> Unit, onCreate: (GameCounter) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var initial by rememberSaveable { mutableStateOf("0") }
    var maximum by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(if (TabletopFeature.RESOURCES in workspace.features) "resource" else "generic") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add counter") },
        text = {
            Column {
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(initial, { initial = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Initial value") }, singleLine = true)
                OutlinedTextField(maximum, { maximum = it.filter(Char::isDigit) }, label = { Text("Maximum (optional)") }, singleLine = true)
                Row {
                    RadioButton(selected = kind == "resource", onClick = { kind = "resource" }); Text("Resource", Modifier.padding(top = 12.dp))
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = kind == "generic", onClick = { kind = "generic" }); Text("Counter", Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            Button(enabled = label.isNotBlank(), onClick = {
                onCreate(
                    GameCounter(
                        label = label.trim(),
                        kind = if (kind == "resource") CounterKind.RESOURCE else CounterKind.GENERIC,
                        scope = CounterScope.WORKSPACE,
                        current = initial.toIntOrNull() ?: 0,
                        maximum = maximum.toIntOrNull(),
                        quickSteps = listOf(-10, -1, 1, 10)
                    )
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddEffectDialog(workspace: GameWorkspace, onDismiss: () -> Unit, onCreate: (GameEffect) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var rounds by rememberSaveable { mutableStateOf("0") }
    var characterId by rememberSaveable { mutableStateOf(workspace.characters.firstOrNull()?.id.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add effect") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Effect / buff") }, singleLine = true)
                OutlinedTextField(rounds, { rounds = it.filter(Char::isDigit) }, label = { Text("Rounds; 0 = indefinite") }, singleLine = true)
                if (workspace.characters.isNotEmpty()) {
                    Text("Applies to")
                    workspace.characters.forEach { character ->
                        Row {
                            RadioButton(selected = characterId == character.id, onClick = { characterId = character.id })
                            Text(character.name, Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                val duration = rounds.toIntOrNull()?.takeIf { it > 0 }
                onCreate(GameEffect(name = name.trim(), characterId = characterId.takeIf(String::isNotBlank), remainingRounds = duration, active = true, autoTick = true))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddInitiativeDialog(workspace: GameWorkspace, onDismiss: () -> Unit, onCreate: (InitiativeEntry) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var score by rememberSaveable { mutableStateOf("0") }
    var characterId by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Initiative entry") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name / NPC") }, singleLine = true)
                OutlinedTextField(score, { score = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Initiative") }, singleLine = true)
                workspace.characters.forEach { character ->
                    Row {
                        RadioButton(selected = characterId == character.id, onClick = { characterId = character.id; name = character.name })
                        Text(character.name, Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onCreate(InitiativeEntry(name = name.trim(), characterId = characterId.takeIf(String::isNotBlank), score = score.toIntOrNull() ?: 0))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TextEntryDialog(title: String, label: String, multiline: Boolean = false, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = !multiline, minLines = if (multiline) 3 else 1) },
        confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onSubmit(value.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PresetOperationPanel(
    context: CapabilityScreenContext,
    supplied: Map<String, String>,
    onRun: (Map<String, String>) -> Unit
) {
    var operation by rememberSaveable { mutableStateOf(supplied.value("operation") ?: "snapshot") }
    var workspaceId by rememberSaveable { mutableStateOf(supplied.value("workspace_id").orEmpty()) }
    var workspaceName by rememberSaveable { mutableStateOf(supplied.value("workspace_name").orEmpty()) }
    var targetId by rememberSaveable { mutableStateOf(supplied.value("target_id").orEmpty()) }
    var value by rememberSaveable { mutableStateOf(supplied.value("value") ?: "0") }
    var note by rememberSaveable { mutableStateOf(supplied.value("note").orEmpty()) }

    Column {
        Text("Preset tabletop operation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (context.settingShouldBeShown("operation")) {
            Text("Operation: $operation")
            Text("For reusable presets, normally fix the operation when saving the preset.", style = MaterialTheme.typography.bodySmall)
        }
        if (context.settingShouldBeShown("workspace_id")) OutlinedTextField(workspaceId, { workspaceId = it }, label = { Text("Workspace ID") }, singleLine = true)
        if (context.settingShouldBeShown("workspace_name")) OutlinedTextField(workspaceName, { workspaceName = it }, label = { Text("Workspace name") }, singleLine = true)
        if (context.settingShouldBeShown("target_id")) OutlinedTextField(targetId, { targetId = it }, label = { Text("Target ID") }, singleLine = true)
        if (context.settingShouldBeShown("value")) OutlinedTextField(value, { value = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Value / delta") }, singleLine = true)
        if (context.settingShouldBeShown("note")) OutlinedTextField(note, { note = it }, label = { Text("Session name / note") })
        Button(
            onClick = {
                onRun(
                    supplied + mapOf(
                        "operation" to operation,
                        "workspace_id" to workspaceId,
                        "workspace_name" to workspaceName,
                        "target_id" to targetId,
                        "value" to value,
                        "note" to note
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Run") }
    }
}

private fun Map<String, String>.value(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

private fun JSONObject.toStringMap(): Map<String, String> = keys().asSequence().associateWith { key -> optString(key) }
