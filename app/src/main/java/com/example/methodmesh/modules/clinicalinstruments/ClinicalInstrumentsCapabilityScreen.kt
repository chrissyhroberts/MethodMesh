package com.example.methodmesh.modules.clinicalinstruments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

object ClinicalInstrumentsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ClinicalInstrumentsMethod.ID
    override val title = "Clinical Instruments"
    override val description = "Run curated or local versioned clinical checklists and scores offline."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val repository = remember(androidContext) { ClinicalInstrumentRepository(androidContext) }
        var refresh by remember { mutableIntStateOf(0) }
        val catalogue = remember(refresh) { repository.catalogue() }
        val activeSessions = remember(refresh) { repository.listSessions() }

        val configuredInstrumentId = context.action.settings["instrument_id"] ?: context.action.settings["input_instrument_id"] ?: ""
        var subjectId by rememberSaveable { mutableStateOf(context.action.settings["subject_id"] ?: context.action.settings["input_subject_id"] ?: "") }
        var sessionLabel by rememberSaveable { mutableStateOf(context.action.settings["session_label"] ?: context.action.settings["input_session_label"] ?: "") }
        var mode by rememberSaveable {
            mutableStateOf(if (configuredInstrumentId.isNotBlank() && context.submitsImmediately) "runner_pending" else "library")
        }
        var selectedDefinitionKey by rememberSaveable { mutableStateOf("") }
        var activeRunId by rememberSaveable { mutableStateOf("") }
        var completedRunId by rememberSaveable { mutableStateOf("") }
        var editorYaml by rememberSaveable { mutableStateOf("") }
        var editorMessage by rememberSaveable { mutableStateOf("") }
        var runnerMessage by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var externalLaunchHandled by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun definitionKey(definition: ClinicalInstrumentDefinition) = "${definition.id}@@${definition.version}@@${definition.definitionSha256}"
        fun selectedDefinition(): ClinicalInstrumentDefinition? = catalogue.firstOrNull { definitionKey(it) == selectedDefinitionKey }

        fun begin(definition: ClinicalInstrumentDefinition) {
            val storedInvocation = context.request.invocationContext.asMap(As100ClinicalInstrumentsMethod.ID) +
                mapOf("subject_id" to subjectId.trim()).filterValues { it.isNotBlank() }
            val session = repository.newSession(
                definition = definition,
                subjectId = subjectId.trim(),
                sessionLabel = sessionLabel.trim(),
                callerMethodId = As100ClinicalInstrumentsMethod.ID,
                invocationContext = storedInvocation
            )
            activeRunId = session.runId
            completedRunId = ""
            selectedDefinitionKey = definitionKey(definition)
            result = null
            runnerMessage = ""
            mode = "runner"
            refresh++
        }

        fun resume(runId: String) {
            val session = repository.loadSession(runId) ?: return
            val definition = runCatching { ClinicalInstrumentYaml.parse(session.definitionYaml, InstrumentStatus.LOCAL) }.getOrNull() ?: return
            activeRunId = runId
            selectedDefinitionKey = definitionKey(definition)
            subjectId = session.subjectId
            sessionLabel = session.sessionLabel
            result = null
            completedRunId = ""
            runnerMessage = ""
            mode = "runner"
        }

        fun buildExecution(session: ClinicalInstrumentSession): ExecutionResult {
            val definition = ClinicalInstrumentYaml.parse(session.definitionYaml, if (catalogue.any { it.status == InstrumentStatus.CORE && it.definitionSha256 == session.definitionSha256 }) InstrumentStatus.CORE else InstrumentStatus.LOCAL)
            val run = ClinicalInstrumentEngine.run(definition, session.responses)
            val invocationMap = session.invocationContext.ifEmpty {
                context.request.invocationContext.asMap(As100ClinicalInstrumentsMethod.ID)
            }
            val request = As100ClinicalInstrumentsMethod.requestForUi(
                invocationMap + context.action.settings + mapOf(
                    "instrument_id" to definition.id,
                    "instrument_version" to definition.version,
                    "definition_sha256" to definition.definitionSha256,
                    "subject_id" to session.subjectId,
                    "session_id" to session.runId
                )
            )
            val invocation = InvocationContext.from(invocationMap) ?: context.request.invocationContext
            return As100ClinicalInstrumentsMethod.result(request, run, session.subjectId, session.runId, invocation)
        }

        LaunchedEffect(subjectId, sessionLabel) {
            context.onSettingsChanged(mapOf("subject_id" to subjectId, "session_label" to sessionLabel))
        }

        LaunchedEffect(configuredInstrumentId, externalLaunchHandled, context.submitsImmediately) {
            if (context.submitsImmediately && configuredInstrumentId.isNotBlank() && !externalLaunchHandled) {
                externalLaunchHandled = true
                val definition = catalogue.firstOrNull { it.id == configuredInstrumentId }
                if (definition == null) {
                    mode = "library"
                    runnerMessage = "Instrument '$configuredInstrumentId' was not found."
                } else {
                    begin(definition)
                }
            }
        }

        LaunchedEffect(completedRunId) {
            if (completedRunId.isNotBlank() && result == null) {
                repository.loadSession(completedRunId)?.let { result = buildExecution(it) }
            }
        }

        // Instrument execution uses a deliberately stripped-down focus surface.
        // Do not wrap questions in CapabilityScreenScaffold: the scaffold is useful
        // for catalogue/settings/results, but adds MethodMesh chrome that is noise
        // during bedside data collection.
        if (mode == "runner") {
            val session = repository.loadSession(activeRunId)
            if (session == null) {
                mode = "library"
            } else {
                val definition = ClinicalInstrumentYaml.parse(
                    session.definitionYaml,
                    if (catalogue.any { it.definitionSha256 == session.definitionSha256 && it.status == InstrumentStatus.CORE }) InstrumentStatus.CORE else InstrumentStatus.LOCAL
                )
                InstrumentRunnerView(
                    definition = definition,
                    session = session,
                    message = runnerMessage,
                    onUpdate = { responses, currentQuestionId ->
                        repository.updateSession(session, responses, currentQuestionId)
                        refresh++
                    },
                    onMessage = { runnerMessage = it },
                    onFinish = { responses ->
                        val updated = repository.updateSession(session, responses, session.currentQuestionId)
                        val run = ClinicalInstrumentEngine.run(definition, updated.responses)
                        if (!run.completed) {
                            runnerMessage = run.error
                        } else {
                            val execution = buildExecution(updated)
                            result = execution
                            completedRunId = updated.runId
                            activeRunId = ""
                            mode = "library"
                        }
                    },
                    onLeaveOpen = {
                        activeRunId = ""
                        refresh++
                        mode = "active"
                    }
                )
                return
            }
        }

        val resultPreview = result?.let { execution ->
            // Human-facing completion output is deliberately clinical rather than
            // technical: headline result first, then every recorded answer in
            // instrument order. Version/hash/provenance remain in the structured
            // ExecutionResult and FULL/AUDIT transports.
            val session = completedRunId.takeIf { it.isNotBlank() }?.let(repository::loadSession)
            val run = session?.let { saved ->
                runCatching {
                    val definition = ClinicalInstrumentYaml.parse(saved.definitionYaml)
                    ClinicalInstrumentEngine.run(definition, saved.responses)
                }.getOrNull()
            }
            if (run != null) {
                linkedMapOf<String, Any?>(
                    "Headline score" to clinicalHeadline(run),
                    "Individual answers" to clinicalAnswers(run)
                )
            } else {
                // Defensive fallback for an old/restored result whose temporary
                // session is no longer available. Never make result rendering a
                // crash path.
                val fields = OutputFormatter.fields(execution, includeProvenance = false)
                linkedMapOf<String, Any?>(
                    "Headline score" to fields[ClinicalInstrumentFields.RESULT],
                    "Individual answers" to fields[ClinicalInstrumentFields.RESPONSES_JSON]
                ).filterValues { it?.toString()?.isNotBlank() == true }
            }
        }.orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = resultPreview,
            onBack = onBack,
            onRetry = {
                result = null
                if (completedRunId.isNotBlank()) {
                    activeRunId = completedRunId
                    completedRunId = ""
                    mode = "runner"
                }
            },
            onConfirm = {
                result?.let { execution ->
                    if (completedRunId.isNotBlank()) repository.deleteSession(completedRunId)
                    refresh++
                    onConfirmed(execution)
                }
            },
            onCancel = onCancel
        ) {
            Column(Modifier.fillMaxWidth()) {
                when (mode) {
                    "library", "detail", "editor", "active", "runner_pending" -> {
                        NavigationTabs(
                            mode = mode,
                            activeCount = activeSessions.size,
                            onLibrary = { mode = "library" },
                            onActive = { mode = "active" },
                            onNew = {
                                editorYaml = repository.newLocalTemplate()
                                editorMessage = ""
                                mode = "editor"
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                when (mode) {
                    "library", "runner_pending" -> LibraryView(
                        catalogue = catalogue,
                        subjectId = subjectId,
                        sessionLabel = sessionLabel,
                        configuredInstrumentId = configuredInstrumentId,
                        restrictInstrumentId = if (context.isNativePresetRun && !context.settingShouldBeShown("instrument_id")) configuredInstrumentId else "",
                        showSubjectId = context.settingShouldBeShown("subject_id"),
                        showSessionLabel = context.settingShouldBeShown("session_label"),
                        runnerMessage = runnerMessage,
                        onSubjectId = { subjectId = it },
                        onSessionLabel = { sessionLabel = it },
                        onStart = ::begin,
                        onDetails = { definition -> selectedDefinitionKey = definitionKey(definition); mode = "detail" },
                        onDuplicate = { definition ->
                            editorYaml = repository.duplicateYaml(definition)
                            editorMessage = "Forked from ${definition.id} ${definition.version}; choose a local id/version before saving."
                            mode = "editor"
                        }
                    )
                    "active" -> ActiveSessionsView(
                        sessions = activeSessions,
                        onResume = ::resume,
                        onDelete = { repository.deleteSession(it.runId); refresh++ }
                    )
                    "detail" -> selectedDefinition()?.let { definition ->
                        InstrumentDetailView(
                            definition = definition,
                            message = runnerMessage,
                            onStart = { begin(definition) },
                            onDuplicate = {
                                editorYaml = repository.duplicateYaml(definition)
                                editorMessage = "Forked from ${definition.id} ${definition.version}."
                                mode = "editor"
                            },
                            onEditLocal = if (definition.status == InstrumentStatus.LOCAL) ({
                                editorYaml = definition.rawYaml
                                editorMessage = "Editing ${definition.id} ${definition.version}. Change version if you alter content."
                                mode = "editor"
                            }) else null,
                            onDeleteLocal = if (definition.status == InstrumentStatus.LOCAL) ({
                                repository.deleteLocal(definition)
                                selectedDefinitionKey = ""
                                refresh++
                                mode = "library"
                            }) else null
                        )
                    } ?: run { Text("Instrument not found."); mode = "library" }
                    "editor" -> InstrumentEditorView(
                        yaml = editorYaml,
                        message = editorMessage,
                        onYaml = { editorYaml = it },
                        onValidate = {
                            val validation = ClinicalInstrumentYaml.validate(editorYaml, InstrumentStatus.LOCAL)
                            editorMessage = buildString {
                                append(if (validation.valid) "Definition is structurally valid." else "Definition has errors.")
                                validation.errors.forEach { append("\nERROR: $it") }
                                validation.warnings.forEach { append("\nWARNING: $it") }
                                if (validation.valid) {
                                    val definition = ClinicalInstrumentYaml.parse(editorYaml, InstrumentStatus.LOCAL)
                                    val testFailures = ClinicalInstrumentEngine.runDefinitionTests(definition)
                                    if (definition.tests.isNotEmpty()) append("\nTests: ${definition.tests.size - testFailures.size}/${definition.tests.size} passed.")
                                    testFailures.forEach { append("\nTEST: $it") }
                                }
                            }
                        },
                        onSave = {
                            runCatching { repository.saveLocalDefinition(editorYaml) }
                                .onSuccess { definition ->
                                    refresh++
                                    selectedDefinitionKey = definitionKey(definition)
                                    editorMessage = "Saved ${definition.id} ${definition.version}."
                                    mode = "library"
                                }
                                .onFailure { editorMessage = it.message ?: "Could not save definition." }
                        },
                        onCancel = { mode = "library" }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationTabs(mode: String, activeCount: Int, onLibrary: () -> Unit, onActive: () -> Unit, onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mode == "library" || mode == "detail" || mode == "runner_pending") Button(onClick = onLibrary, modifier = Modifier.weight(1f)) { Text("Library") }
        else OutlinedButton(onClick = onLibrary, modifier = Modifier.weight(1f)) { Text("Library") }
        if (mode == "active") Button(onClick = onActive, modifier = Modifier.weight(1f)) { Text("Active $activeCount") }
        else OutlinedButton(onClick = onActive, modifier = Modifier.weight(1f)) { Text("Active $activeCount") }
        OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f)) { Text("+ New") }
    }
}

@Composable
private fun LibraryView(
    catalogue: List<ClinicalInstrumentDefinition>, subjectId: String, sessionLabel: String,
    configuredInstrumentId: String, restrictInstrumentId: String, showSubjectId: Boolean, showSessionLabel: Boolean, runnerMessage: String,
    onSubjectId: (String) -> Unit, onSessionLabel: (String) -> Unit,
    onStart: (ClinicalInstrumentDefinition) -> Unit,
    onDetails: (ClinicalInstrumentDefinition) -> Unit,
    onDuplicate: (ClinicalInstrumentDefinition) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    Text("Versioned offline clinical checklists and scores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("All instrument execution, scoring and temporary session state are local to this device.", style = MaterialTheme.typography.bodySmall)
    if (runnerMessage.isNotBlank()) Text(runnerMessage, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
    if (showSubjectId) OutlinedTextField(subjectId, onSubjectId, label = { Text("Patient / participant / case ID (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    if (showSessionLabel) OutlinedTextField(sessionLabel, onSessionLabel, label = { Text("Session label (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    if (restrictInstrumentId.isBlank()) {
        OutlinedTextField(search, { search = it }, label = { Text("Search instruments") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    } else {
        Text("Preset instrument: $restrictInstrumentId", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))

    val filtered = catalogue.filter { definition ->
        val permittedByPreset = restrictInstrumentId.isBlank() || definition.id == restrictInstrumentId
        permittedByPreset && (search.isBlank() || listOf(definition.name, definition.id, definition.category, definition.type, definition.tags.joinToString(" "))
            .any { it.contains(search.trim(), ignoreCase = true) })
    }
    if (filtered.isEmpty()) Text("No instruments match this search.", style = MaterialTheme.typography.bodySmall)
    filtered.groupBy { it.category }.toSortedMap().forEach { (category, items) ->
        Text(category.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        items.forEach { definition ->
            InstrumentCard(definition, configuredInstrumentId == definition.id, onStart, onDetails, onDuplicate)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InstrumentCard(
    definition: ClinicalInstrumentDefinition,
    configured: Boolean,
    onStart: (ClinicalInstrumentDefinition) -> Unit,
    onDetails: (ClinicalInstrumentDefinition) -> Unit,
    onDuplicate: (ClinicalInstrumentDefinition) -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(definition.name, fontWeight = FontWeight.Bold)
            Text("${if (definition.status == InstrumentStatus.CORE) "Core · immutable" else "Local · editable"} · ${definition.type} · v${definition.version}", style = MaterialTheme.typography.bodySmall)
            if (configured) Text("Requested by caller", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            if (definition.summary.isNotBlank()) Text(definition.summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onStart(definition) }, modifier = Modifier.weight(1f)) { Text("Start") }
                OutlinedButton(onClick = { onDetails(definition) }, modifier = Modifier.weight(1f)) { Text("Details") }
                OutlinedButton(onClick = { onDuplicate(definition) }, modifier = Modifier.weight(1f)) { Text("Duplicate") }
            }
        }
    }
}

@Composable
private fun ActiveSessionsView(sessions: List<ClinicalInstrumentSession>, onResume: (String) -> Unit, onDelete: (ClinicalInstrumentSession) -> Unit) {
    Text("Open instruments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Responses are autosaved to app-private local storage. Completed sessions are returned through the normal MethodMesh result contract.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    if (sessions.isEmpty()) Text("No open instruments.")
    sessions.forEach { session ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(session.instrumentName, fontWeight = FontWeight.Bold)
                val identifier = listOf(session.subjectId, session.sessionLabel).filter { it.isNotBlank() }.joinToString(" · ")
                if (identifier.isNotBlank()) Text(identifier)
                Text("v${session.instrumentVersion} · ${session.responses.size} responses · updated ${session.updatedAt}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onResume(session.runId) }, modifier = Modifier.weight(1f)) { Text("Resume") }
                    OutlinedButton(onClick = { onDelete(session) }, modifier = Modifier.weight(1f)) { Text("Discard") }
                }
            }
        }
    }
}

@Composable
private fun InstrumentDetailView(
    definition: ClinicalInstrumentDefinition,
    message: String,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onEditLocal: (() -> Unit)?,
    onDeleteLocal: (() -> Unit)?
) {
    Text(definition.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("${if (definition.status == InstrumentStatus.CORE) "Core / immutable" else "Local / editable"} · ${definition.id} · v${definition.version}")
    Text("Definition SHA-256: ${definition.definitionSha256}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))
    Text(definition.summary)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(8.dp))
    Text("Source", fontWeight = FontWeight.Bold)
    Text(definition.citation.ifBlank { "No citation supplied." }, style = MaterialTheme.typography.bodySmall)
    if (definition.sourceUrl.isNotBlank()) Text(definition.sourceUrl, style = MaterialTheme.typography.bodySmall)
    Text("Rights: ${definition.rightsStatus}", style = MaterialTheme.typography.bodySmall)
    if (definition.rightsNote.isNotBlank()) Text(definition.rightsNote, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Text("${definition.questions.size} questions · ${definition.derived.size} derived fields · ${definition.scores.size} scores · ${definition.tests.size} tests", style = MaterialTheme.typography.bodySmall)
    val testFailures = remember(definition.definitionSha256) { ClinicalInstrumentEngine.runDefinitionTests(definition) }
    if (definition.tests.isNotEmpty()) Text("Definition tests: ${definition.tests.size - testFailures.size}/${definition.tests.size} passed", style = MaterialTheme.typography.bodySmall)
    testFailures.forEach { Text("Test failure: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(10.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start instrument") }
    OutlinedButton(onClick = onDuplicate, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Duplicate to local definition") }
    onEditLocal?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Edit YAML") } }
    onDeleteLocal?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Delete local definition") } }
    Spacer(Modifier.height(8.dp))
    Text("Definition YAML", fontWeight = FontWeight.Bold)
    Text(definition.rawYaml, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun InstrumentEditorView(yaml: String, message: String, onYaml: (String) -> Unit, onValidate: () -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Text("Local instrument definition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("The YAML is the executable specification. Built-in definitions cannot be edited; duplicating creates a local fork.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = yaml,
        onValueChange = onYaml,
        label = { Text("Instrument YAML") },
        modifier = Modifier.fillMaxWidth().height(420.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    )
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onValidate, modifier = Modifier.weight(1f)) { Text("Validate") }
        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save local") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
    }
}

@Composable
private fun InstrumentRunnerView(
    definition: ClinicalInstrumentDefinition,
    session: ClinicalInstrumentSession,
    message: String,
    onUpdate: (Map<String, String>, String) -> Unit,
    onMessage: (String) -> Unit,
    onFinish: (Map<String, String>) -> Unit,
    onLeaveOpen: () -> Unit
) {
    val questions = definition.questions
    var currentId by rememberSaveable(session.runId) { mutableStateOf(session.currentQuestionId.ifBlank { questions.firstOrNull()?.id.orEmpty() }) }
    val currentIndex = questions.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
    val question = questions.getOrNull(currentIndex)
    val progress = if (questions.isEmpty()) 1f else ((currentIndex + 1).toFloat() / questions.size.toFloat()).coerceIn(0f, 1f)

    // Focus mode: only protocol content and navigation are visible.
    // Instrument metadata remains available before/after the run and in outputs.
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(definition.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("${currentIndex + 1} / ${questions.size}", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp))

        if (question == null) {
            Text("No questions", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { onFinish(session.responses) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Complete") }
            return@Column
        }

        Text(
            question.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (question.hint.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(question.hint, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))

    QuestionInput(question, session.responses[question.id].orEmpty()) { newValue ->
        val updated = session.responses.toMutableMap().apply {
            if (newValue.isBlank()) remove(question.id) else put(question.id, newValue)
        }
        onUpdate(updated, question.id)
        onMessage("")
    }

    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(10.dp))
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            enabled = currentIndex > 0,
            onClick = {
                val previous = questions.getOrNull(currentIndex - 1) ?: return@OutlinedButton
                currentId = previous.id
                onUpdate(session.responses, previous.id)
                onMessage("")
            },
            modifier = Modifier.weight(1f)
        ) { Text("Back") }

        val isLast = currentIndex >= questions.lastIndex
        Button(
            onClick = {
                val value = session.responses[question.id].orEmpty()
                val error = ClinicalInstrumentEngine.validateResponse(question, value)
                if (error != null) {
                    onMessage(error)
                    return@Button
                }
                val next = questions.getOrNull(currentIndex + 1)
                if (next == null) onFinish(session.responses)
                else {
                    currentId = next.id
                    onUpdate(session.responses, next.id)
                    onMessage("")
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text(if (isLast) "Complete" else "Next") }
    }
        OutlinedButton(
            onClick = onLeaveOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Save and exit") }
    }
}

@Composable
private fun QuestionInput(question: ClinicalQuestion, value: String, onValue: (String) -> Unit) {
    when (question.type) {
        QuestionType.INTEGER -> OutlinedTextField(
            value = value,
            onValueChange = { onValue(it.filter { c -> c.isDigit() || c == '-' }) },
            label = { Text(question.unit.ifBlank { "Response" }) },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        QuestionType.DECIMAL -> OutlinedTextField(
            value = value,
            onValueChange = { onValue(decimalText(it)) },
            label = { Text(question.unit.ifBlank { "Response" }) },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        QuestionType.TEXT -> OutlinedTextField(value, onValue, label = { Text("Response") }, modifier = Modifier.fillMaxWidth())
        QuestionType.BOOLEAN -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (value == "true") Button(onClick = { onValue("true") }, modifier = Modifier.weight(1f)) { Text("✓ Yes") }
            else OutlinedButton(onClick = { onValue("true") }, modifier = Modifier.weight(1f)) { Text("Yes") }
            if (value == "false") Button(onClick = { onValue("false") }, modifier = Modifier.weight(1f)) { Text("✓ No") }
            else OutlinedButton(onClick = { onValue("false") }, modifier = Modifier.weight(1f)) { Text("No") }
        }
        QuestionType.SELECT_ONE -> Column(Modifier.fillMaxWidth()) {
            question.choices.forEach { choice ->
                if (value == choice.value) Button(onClick = { onValue(choice.value) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text("✓ ${choice.label}") }
                else OutlinedButton(onClick = { onValue(choice.value) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(choice.label) }
            }
        }
    }
}

private fun clinicalHeadline(run: ClinicalRunResult): String {
    val score = run.scores.values.firstOrNull()?.plain().orEmpty()
    val classification = run.classification?.label.orEmpty()
    return buildString {
        append(run.instrument.name)
        when {
            score.isNotBlank() && classification.isNotBlank() -> append(" — $score · $classification")
            score.isNotBlank() -> append(" — $score")
            classification.isNotBlank() -> append(" — $classification")
            else -> append(" — Completed")
        }
    }
}

private fun clinicalAnswers(run: ClinicalRunResult): String =
    run.instrument.questions.mapIndexed { index, question ->
        val raw = run.responses[question.id].orEmpty()
        "${index + 1}. ${question.label} — ${clinicalAnswer(question, raw)}"
    }.joinToString("\n")

private fun clinicalAnswer(question: ClinicalQuestion, raw: String): String {
    if (raw.isBlank()) return "Not answered"
    val display = when (question.type) {
        QuestionType.BOOLEAN -> when (raw.lowercase()) {
            "true" -> "Yes"
            "false" -> "No"
            else -> raw
        }
        QuestionType.SELECT_ONE -> question.choices.firstOrNull { it.value == raw }?.label ?: raw
        else -> raw
    }
    return if (question.unit.isNotBlank() && question.type in setOf(QuestionType.INTEGER, QuestionType.DECIMAL)) {
        "$display ${question.unit}"
    } else {
        display
    }
}

private fun decimalText(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '-' || it == '.' }
    val minus = if (filtered.startsWith('-')) "-" else ""
    val body = filtered.removePrefix("-")
    val parts = body.split('.')
    return minus + parts.firstOrNull().orEmpty() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
}
