@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.methodmesh.modules.tamagotchi

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import java.time.LocalTime
import kotlin.math.roundToInt

data class TamagotchiScreenResult(val execution: ExecutionResult, val values: Map<String, String>)

private fun CapabilityScreenContext.setting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

private fun parseClock(raw: String, fallback: Int): Int = runCatching {
    val t = LocalTime.parse(raw)
    t.hour * 60 + t.minute
}.getOrDefault(fallback)

private fun sessionId(context: CapabilityScreenContext, store: TamagotchiStore): String =
    context.setting("session_id").ifBlank { store.activeSessionId().orEmpty() }

private fun commonValues(session: TamagotchiSession?, operation: String): LinkedHashMap<String, String> = linkedMapOf(
    TamagotchiFields.STATUS to if (session == null) "failed" else "succeeded",
    TamagotchiFields.OPERATION to operation,
    TamagotchiFields.SESSION_ID to session?.id.orEmpty(),
    TamagotchiFields.CREATURE_ID to session?.creatureId.orEmpty(),
    TamagotchiFields.CREATURE_NAME to session?.creatureName.orEmpty(),
    TamagotchiFields.SCENARIO_ID to session?.scenarioId.orEmpty(),
    TamagotchiFields.SIMULATION_MINUTE to (session?.simulationMinute ?: 0.0).toString(),
    TamagotchiFields.HISTORY_ARTIFACT_REF to session?.historyArtifactRef.orEmpty(),
    TamagotchiFields.ANALYSIS_UNLOCKED to (session?.ended == true).toString(),
    TamagotchiFields.ERROR to if (session == null) "No Tamagotchi session is available." else ""
)

private fun makeResult(
    method: TamagotchiMethod,
    context: CapabilityScreenContext,
    values: Map<String, String>
): TamagotchiScreenResult {
    val request = method.request(
        action = method.id,
        context = context.request.invocationContext.asMap(method.id) + context.action.settings + values,
        signals = emptyList(),
        inputs = emptyList()
    )
    return TamagotchiScreenResult(
        method.result(request, values, context.request.invocationContext),
        values
    )
}

object TamagotchiSessionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiSessionMethod.ID
    override val title = "Tamagotchi Lab"
    override val description = "Care for a kawaii longitudinal teaching creature whose complete history becomes data."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current.applicationContext
        val store = remember(appContext) { TamagotchiStore(appContext) }
        var session by remember { mutableStateOf<TamagotchiSession?>(null) }
        var selectedCreature by rememberSaveable { mutableStateOf(context.setting("creature", "mossbit")) }
        var selectedScenario by rememberSaveable { mutableStateOf(context.setting("scenario_id", "foundation_care")) }
        var creatureName by rememberSaveable { mutableStateOf(context.setting("creature_name")) }
        var simulationMode by rememberSaveable { mutableStateOf(context.setting("simulation_mode", "classroom")) }
        var offSessionMode by rememberSaveable { mutableStateOf(context.setting("off_session_mode", "sleep")) }
        var startError by rememberSaveable { mutableStateOf("") }
        var latestEvent by remember { mutableStateOf<String?>(null) }
        var startedReturnSent by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(context.setting("session_id")) {
            val explicit = context.setting("session_id")
            session = when {
                explicit.isNotBlank() -> store.loadAndAdvance(explicit)
                context.presentationMode == CapabilityPresentationMode.Dashboard -> store.active()?.let { store.loadAndAdvance(it.id) }
                else -> null
            }
        }

        fun resultFor(s: TamagotchiSession): TamagotchiScreenResult {
            val scenario = TamagotchiCatalog.scenario(s.scenarioId)
            val creature = TamagotchiCatalog.creature(s.creatureId)
            val phenotype = TamagotchiEngine.phenotype(s, scenario, creature)
            val values = commonValues(s, capabilityId).apply {
                this[TamagotchiFields.PHENOTYPE] = phenotype.headline
                this[TamagotchiFields.VISIBLE_STATE_JSON] = TamagotchiEngine.visibleState(s, scenario).toJsonObject().toString()
                this[TamagotchiFields.ANALYSIS_UNLOCKED] = s.ended.toString()
            }
            return makeResult(As100TamagotchiSessionMethod, context, values)
        }

        LaunchedEffect(session?.id, context.submitsImmediately) {
            val s = session ?: return@LaunchedEffect
            if (context.submitsImmediately && context.setting("session_id").isNotBlank() && !startedReturnSent) {
                startedReturnSent = true
                onConfirmed(resultFor(s).execution)
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (session == null) {
                StartSessionSurface(
                    selectedCreature = selectedCreature,
                    onCreature = { selectedCreature = it },
                    selectedScenario = selectedScenario,
                    onScenario = { selectedScenario = it },
                    creatureName = creatureName,
                    onCreatureName = { creatureName = it },
                    simulationMode = simulationMode,
                    onSimulationMode = { simulationMode = it },
                    offSessionMode = offSessionMode,
                    onOffSessionMode = { offSessionMode = it },
                    errorMessage = startError,
                    onBack = onBack,
                    onStart = {
                        val policy = AttentionPolicy(
                            enabled = context.setting("notifications_enabled", "true").toBoolean(),
                            profile = context.setting("notification_profile", "young_learner"),
                            maxPerDay = context.setting("max_notifications_per_day", "2").toIntOrNull()?.coerceIn(0, 12) ?: 2,
                            minimumSpacingMinutes = context.setting("minimum_notification_spacing_minutes", "180").toIntOrNull()?.coerceAtLeast(0) ?: 180,
                            schoolStartMinutes = parseClock(context.setting("school_start", "08:30"), 8*60+30),
                            schoolEndMinutes = parseClock(context.setting("school_end", "15:30"), 15*60+30),
                            quietStartMinutes = parseClock(context.setting("quiet_start", "20:30"), 20*60+30),
                            quietEndMinutes = parseClock(context.setting("quiet_end", "07:00"), 7*60),
                            allowDuringSchool = context.setting("notification_profile") == "continuous_simulation"
                        )
                        runCatching {
                            store.create(
                                creatureId = selectedCreature,
                                creatureName = creatureName,
                                scenarioId = selectedScenario,
                                seed = context.setting("seed").toLongOrNull() ?: System.currentTimeMillis(),
                                simulationMode = SimulationMode.from(simulationMode),
                                acceleration = context.setting("acceleration", "12").toDoubleOrNull() ?: 12.0,
                                offSessionMode = OffSessionMode.from(offSessionMode),
                                attentionPolicy = policy
                            )
                        }.onSuccess { created ->
                            startError = ""
                            session = created
                            if (context.submitsImmediately) {
                                startedReturnSent = true
                                onConfirmed(resultFor(created).execution)
                            }
                        }.onFailure { failure ->
                            startError = failure.message ?: "The Tamagotchi care session could not be started."
                        }
                    }
                )
            } else {
                session?.let { currentSession ->
                    CareDashboard(
                        session = currentSession,
                        store = store,
                        latestEvent = latestEvent,
                        onAction = { actionId ->
                            runCatching { store.applyAction(currentSession.id, actionId) }
                                .onSuccess { (updated, event) ->
                                    session = updated
                                    latestEvent = event.payload.optString("label", actionId)
                                }
                                .onFailure { latestEvent = it.message ?: "That care action could not be applied." }
                        },
                        onObserve = {
                            runCatching { store.observe(currentSession.id) }
                                .onSuccess { (updated, event) ->
                                    session = updated
                                    latestEvent = event.payload.optString("headline")
                                }
                                .onFailure { latestEvent = it.message ?: "The observation could not be recorded." }
                        },
                        onMeasure = { variable ->
                            runCatching { store.measure(currentSession.id, variable) }
                                .onSuccess { (updated, event) ->
                                    session = updated
                                    latestEvent = "${event.payload.optString("label")}: ${event.payload.optDouble("value")} ${event.payload.optString("unit")}"
                                }
                                .onFailure { latestEvent = it.message ?: "The measurement could not be recorded." }
                        },
                        onAdvance = { minutes ->
                            runCatching { store.advance(currentSession.id, minutes) }
                                .onSuccess { updated ->
                                    session = updated
                                    latestEvent = "Time advanced by ${minutes.roundToInt()} minutes."
                                }
                                .onFailure { latestEvent = it.message ?: "Simulation time could not be advanced." }
                        },
                        onEnd = {
                            runCatching { store.end(currentSession.id) }
                                .onSuccess { ended ->
                                    session = ended
                                    latestEvent = "Care period ended. Latent analysis is now unlocked."
                                }
                                .onFailure { latestEvent = it.message ?: "The care period could not be ended." }
                        },
                        onBack = onBack,
                        onDone = {
                            session?.let { latest -> onConfirmed(resultFor(latest).execution) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartSessionSurface(
    selectedCreature: String,
    onCreature: (String) -> Unit,
    selectedScenario: String,
    onScenario: (String) -> Unit,
    creatureName: String,
    onCreatureName: (String) -> Unit,
    simulationMode: String,
    onSimulationMode: (String) -> Unit,
    offSessionMode: String,
    onOffSessionMode: (String) -> Unit,
    errorMessage: String,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Tamagotchi Lab", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text("Choose someone to care about.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Text("Starter creatures", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TamagotchiCatalog.creatures.forEach { creature ->
            val phenotype = Phenotype("", "", 80.0, 80.0, 80.0, 10.0, false, false)
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onCreature(creature.id) },
                colors = CardDefaults.cardColors(containerColor = if (selectedCreature == creature.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    KawaiiCreature(creature, phenotype, Modifier.size(92.dp))
                    Column(Modifier.weight(1f).padding(start=10.dp)) {
                        Text(creature.displayName, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                        Text(creature.tagline, style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
                        Text(creature.personality, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        OutlinedTextField(
            value = creatureName,
            onValueChange = onCreatureName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Give them a name (optional)") },
            singleLine = true
        )
        Text("Teaching scenario", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
        TamagotchiCatalog.scenarios.forEach { scenario ->
            Card(
                modifier=Modifier.fillMaxWidth().clickable { onScenario(scenario.id) },
                colors=CardDefaults.cardColors(containerColor=if(selectedScenario==scenario.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.38f)),
                shape=RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(scenario.title, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
                    Text(scenario.subtitle, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Clock", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            SimulationMode.entries.forEach { mode ->
                FilterChip(selected=simulationMode==mode.wire, onClick={onSimulationMode(mode.wire)}, label={Text(mode.wire.replace('_',' '))})
            }
        }
        Text("Between sessions", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OffSessionMode.entries.forEach { mode ->
                FilterChip(selected=offSessionMode==mode.wire, onClick={onOffSessionMode(mode.wire)}, label={Text(mode.wire)})
            }
        }
        if (errorMessage.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(errorMessage, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Button(onClick=onStart, modifier=Modifier.fillMaxWidth().height(56.dp)) { Text("Start care period") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CareDashboard(
    session: TamagotchiSession,
    store: TamagotchiStore,
    latestEvent: String?,
    onAction: (String) -> Unit,
    onObserve: () -> Unit,
    onMeasure: (String) -> Unit,
    onAdvance: (Double) -> Unit,
    onEnd: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val scenario = TamagotchiCatalog.scenario(session.scenarioId)
    val creature = TamagotchiCatalog.creature(session.creatureId)
    val phenotype = TamagotchiEngine.phenotype(session, scenario, creature)
    val visible = TamagotchiEngine.visibleState(session, scenario)
    val history = remember(session.simulationMinute, latestEvent, session.ended) { store.historyLines(session) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton(onClick=onBack) { Text("Back") }
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(session.creatureName, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Black)
                Text(scenario.title, style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick=onDone) { Text("Done") }
        }

        Card(shape=RoundedCornerShape(30.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                KawaiiCreature(creature, phenotype, Modifier.size(230.dp))
                Text(phenotype.headline, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                Text(phenotype.detail, modifier=Modifier.padding(top=4.dp), color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (latestEvent != null) {
                    Surface(Modifier.padding(top=12.dp), shape=RoundedCornerShape(14.dp), color=MaterialTheme.colorScheme.surface.copy(alpha=.72f)) {
                        Text(latestEvent, Modifier.padding(horizontal=14.dp, vertical=9.dp), style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Text("Care", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Black)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(9.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            (scenario.actions + session.generatedOptions.map { it.toAction() }).forEach { action ->
                Card(
                    modifier=Modifier.clickable(enabled=!session.ended) { onAction(action.id) },
                    shape=RoundedCornerShape(18.dp),
                    colors=CardDefaults.cardColors(containerColor=if(action.generated) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(horizontal=13.dp,vertical=9.dp), verticalAlignment=Alignment.CenterVertically) {
                        CareGlyph(action.glyph)
                        Column(Modifier.padding(start=7.dp)) {
                            Text(action.label, fontWeight=FontWeight.Bold)
                            if(action.generated) Text("mystery option", style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Text("Observe — purposive actions become data", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=onObserve, enabled=!session.ended, modifier=Modifier.weight(1f)) { CareGlyph(CareGlyphKind.Observe); Text("  Observe") }
            scenario.measurementVariableIds.take(2).forEach { variable ->
                OutlinedButton(onClick={onMeasure(variable)}, enabled=!session.ended, modifier=Modifier.weight(1f)) {
                    Text(scenario.variable(variable)?.label ?: variable)
                }
            }
        }

        if (session.simulationMode == SimulationMode.Classroom || session.simulationMode == SimulationMode.TurnBased) {
            Text("Classroom time", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(15.0 to "+15m", 60.0 to "+1h", 240.0 to "+4h", 720.0 to "+12h", 1440.0 to "+1d").forEach { (m,label) ->
                    FilterChip(selected=false, enabled=!session.ended, onClick={onAdvance(m)}, label={Text(label)})
                }
            }
        }

        Text("Visible state", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Black)
        Text("These are care-visible variables only. Hidden state is not leaked before analysis unlock.", color=MaterialTheme.colorScheme.onSurfaceVariant, style=MaterialTheme.typography.bodySmall)
        visible.forEach { (id, value) ->
            val variable = scenario.variable(id) ?: return@forEach
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(variable.label, fontWeight=FontWeight.SemiBold)
                    Text(if(variable.unit.isBlank()) "${value.roundToInt()}" else "${(value*10).roundToInt()/10.0} ${variable.unit}", color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val fraction = ((value-variable.minimum)/(variable.maximum-variable.minimum)).toFloat().coerceIn(0f,1f)
                LinearProgressIndicator(progress={fraction}, modifier=Modifier.fillMaxWidth().height(7.dp))
            }
        }

        Text("History", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Black)
        TrajectoryChart(history, scenario, Modifier.fillMaxWidth().height(190.dp))
        history.asReversed().filter { it.optString("visibility") != "latent" }.take(8).forEach { e ->
            val p = e.optJSONObject("payload") ?: JSONObject()
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.40f)), shape=RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(e.optString("type").replace('_',' '), fontWeight=FontWeight.Bold)
                        Text("t=${e.optDouble("simulation_minute").roundToInt()}m", style=MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        p.optString("label").ifBlank { p.optString("headline").ifBlank { p.optString("reason") } },
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        HorizontalDivider()
        if (!session.ended) {
            OutlinedButton(onClick=onEnd, modifier=Modifier.fillMaxWidth()) { Text("End care period and unlock analysis") }
        } else {
            Surface(shape=RoundedCornerShape(18.dp), color=MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text("Care period complete", fontWeight=FontWeight.Black)
                    Text("Latent state and generated option parameters may now be analysed/exported.")
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TrajectoryChart(events: List<JSONObject>, scenario: ScenarioDefinition, modifier: Modifier = Modifier) {
    val variableIds = scenario.variables.filter { it.careVisible }.take(4).map { it.id }
    val points = variableIds.associateWith { mutableListOf<Pair<Double,Double>>() }
    events.filter { it.optString("type") == "care_state_transition" }.forEach { e ->
        val state = e.optJSONObject("payload")?.optJSONObject("visible_state") ?: return@forEach
        val t = e.optDouble("simulation_minute")
        variableIds.forEach { id -> if(state.has(id)) points.getValue(id) += t to state.optDouble(id) }
    }
    Card(shape=RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.38f))) {
        Canvas(modifier.padding(14.dp)) {
            val maxT = points.values.flatten().maxOfOrNull { it.first }?.coerceAtLeast(1.0) ?: 1.0
            drawLine(Color(0x335A4857), Offset(0f,size.height), Offset(size.width,size.height), 2f)
            drawLine(Color(0x335A4857), Offset(0f,0f), Offset(0f,size.height), 2f)
            val palette = listOf(Color(0xFFCC6F98),Color(0xFF6B9FB8),Color(0xFF7AAE79),Color(0xFFD3A64F))
            variableIds.forEachIndexed { index,id ->
                val v = scenario.variable(id) ?: return@forEachIndexed
                val series = points[id].orEmpty()
                if(series.size<2) return@forEachIndexed
                val path=Path()
                series.forEachIndexed { i,(t,value) ->
                    val x=(t/maxT*size.width).toFloat()
                    val y=(size.height-((value-v.minimum)/(v.maximum-v.minimum)*size.height)).toFloat().coerceIn(0f,size.height)
                    if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                drawPath(path,palette[index%palette.size],style=androidx.compose.ui.graphics.drawscope.Stroke(4f))
            }
        }
    }
}

private fun valuesForSession(session: TamagotchiSession, methodId: String): LinkedHashMap<String,String> {
    val scenario = TamagotchiCatalog.scenario(session.scenarioId)
    val creature = TamagotchiCatalog.creature(session.creatureId)
    return commonValues(session, methodId).apply {
        this[TamagotchiFields.PHENOTYPE] = TamagotchiEngine.phenotype(session, scenario, creature).headline
        this[TamagotchiFields.VISIBLE_STATE_JSON] = TamagotchiEngine.visibleState(session, scenario).toJsonObject().toString()
    }
}

private fun shouldReturnImmediately(context: CapabilityScreenContext): Boolean =
    context.submitsImmediately || context.completionMode == CapabilityCompletionMode.AutomaticReturn

private fun safeSessionLoad(store: TamagotchiStore, id: String): Pair<TamagotchiSession?, String?> {
    if (id.isBlank()) return null to "No active Tamagotchi session is available. Start or resume a care session first."
    return runCatching { store.loadAndAdvance(id) }
        .fold(
            onSuccess = { session ->
                if (session == null) null to "Tamagotchi session '$id' was not found."
                else session to null
            },
            onFailure = { null to (it.message ?: "The Tamagotchi session could not be opened.") }
        )
}

@Composable
private fun CopyableTamagotchiValue(label: String, value: String, prominent: Boolean = false) {
    if (value.isBlank()) return
    val clipboard = LocalClipboardManager.current
    val androidContext = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(value))
                Toast.makeText(androidContext, "Copied $label", Toast.LENGTH_SHORT).show()
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .44f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (prominent) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TamagotchiWorkingResult(result: TamagotchiScreenResult) {
    val values = result.values
    val succeeded = values[TamagotchiFields.STATUS] == "succeeded"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (succeeded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .62f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (succeeded) "Working result" else "Could not complete", fontWeight = FontWeight.Black)
            values[TamagotchiFields.PHENOTYPE]?.takeIf(String::isNotBlank)?.let {
                CopyableTamagotchiValue("Phenotype", it, prominent = true)
            }
            values[TamagotchiFields.SESSION_ID]?.takeIf(String::isNotBlank)?.let {
                CopyableTamagotchiValue("Session ID", it)
            }
            values[TamagotchiFields.SIMULATION_MINUTE]?.takeIf(String::isNotBlank)?.let {
                CopyableTamagotchiValue("Simulation minute", it)
            }
            values[TamagotchiFields.EVENT_JSON]?.takeIf(String::isNotBlank)?.let {
                CopyableTamagotchiValue("Event JSON", it)
            }
            values[TamagotchiFields.EXPORT_URI]?.takeIf(String::isNotBlank)?.let {
                CopyableTamagotchiValue("Export URI", it)
            }
            values[TamagotchiFields.ERROR]?.takeIf(String::isNotBlank)?.let {
                CopyableTamagotchiValue("Error", it)
            }
            Text("Tap any value to copy.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TamagotchiCapabilityScaffold(
    title: String,
    method: TamagotchiMethod,
    context: CapabilityScreenContext,
    result: TamagotchiScreenResult?,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    commitLabel: String = "Commit result",
    content: @Composable () -> Unit
) {
    CapabilityScreenScaffold(
        title = title,
        capabilityId = method.id,
        context = context,
        canGoBack = context.stepNumber > 1,
        // v1.05 rule: never feed a live working result into the legacy captured-result
        // slot. Doing so replaces the capability UI with the generic result screen.
        capturedResult = null,
        resultPreview = emptyMap<String, String>(),
        onBack = onBack,
        onRetry = onRetry,
        onConfirm = onCommit,
        onCancel = onCancel
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            error?.takeIf(String::isNotBlank)?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            result?.let { working ->
                TamagotchiWorkingResult(working)
                if (!shouldReturnImmediately(context)) {
                    Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text(commitLabel) }
                }
            }
        }
    }
}

object TamagotchiInterveneCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiInterveneMethod.ID
    override val title = "Tamagotchi intervention"
    override val description = "Apply one care action to the persistent longitudinal session."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val store = remember(app) { TamagotchiStore(app) }
        val id = sessionId(context, store)
        val initialLoad = remember(id) { safeSessionLoad(store, id) }
        var current by remember(id) { mutableStateOf(initialLoad.first) }
        var error by rememberSaveable(id) { mutableStateOf(initialLoad.second) }
        var result by remember { mutableStateOf<TamagotchiScreenResult?>(null) }
        var selected by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.setting("action_id")) }
        var ran by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        val scenario = current?.let { TamagotchiCatalog.scenario(it.scenarioId) }

        fun publish(working: TamagotchiScreenResult) {
            result = working
            error = null
            if (shouldReturnImmediately(context)) onConfirmed(working.execution)
        }

        fun runAction() {
            val s = current ?: run {
                error = "No valid Tamagotchi session is available for an intervention."
                return
            }
            val chosen = selected.trim()
            if (chosen.isBlank()) {
                error = "Choose an intervention first."
                return
            }
            runCatching { store.applyAction(s.id, chosen) }
                .onSuccess { (updated, event) ->
                    current = updated
                    val values = valuesForSession(updated, capabilityId).apply {
                        this[TamagotchiFields.EVENT_JSON] = event.toJson(updated.id).toString()
                    }
                    publish(makeResult(As100TamagotchiInterveneMethod, context, values))
                }
                .onFailure { error = it.message ?: "The intervention could not be applied." }
        }

        LaunchedEffect(id, selected, context.startsImmediately) {
            if (!ran && context.startsImmediately && current != null && selected.isNotBlank()) {
                ran = true
                runAction()
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiInterveneMethod, context, result, error, onBack,
            onRetry = { result = null; error = null; ran = false },
            onCommit = { result?.let { onConfirmed(it.execution) } },
            onCancel = onCancel,
            commitLabel = "Commit intervention"
        ) {
            if (current == null) {
                Text("No active session. Start or resume a Tamagotchi care period first.")
            } else {
                current?.let { loadedSession ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(loadedSession.creatureName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        (scenario?.actions.orEmpty() + loadedSession.generatedOptions.map { it.toAction() }).forEach { action ->
                            FilterChip(selected = selected == action.id, onClick = { selected = action.id; result = null }, label = { Text(action.label) })
                        }
                        Button(onClick = ::runAction, enabled = selected.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Apply intervention") }
                    }
                }
            }
        }
    }
}

object TamagotchiObserveCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiObserveMethod.ID
    override val title = "Observe Tamagotchi"
    override val description = "Record a purposive visible-phenotype observation."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val store = remember(app) { TamagotchiStore(app) }
        val id = sessionId(context, store)
        val initialLoad = remember(id) { safeSessionLoad(store, id) }
        var current by remember(id) { mutableStateOf(initialLoad.first) }
        var error by rememberSaveable(id) { mutableStateOf(initialLoad.second) }
        var result by remember { mutableStateOf<TamagotchiScreenResult?>(null) }
        var ran by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun publish(working: TamagotchiScreenResult) {
            result = working
            error = null
            if (shouldReturnImmediately(context)) onConfirmed(working.execution)
        }

        fun runObservation() {
            val s = current ?: run {
                error = "No valid Tamagotchi session is available to observe."
                return
            }
            runCatching { store.observe(s.id) }
                .onSuccess { (updated, event) ->
                    current = updated
                    val values = valuesForSession(updated, capabilityId).apply {
                        this[TamagotchiFields.EVENT_JSON] = event.toJson(updated.id).toString()
                    }
                    publish(makeResult(As100TamagotchiObserveMethod, context, values))
                }
                .onFailure { error = it.message ?: "The observation could not be recorded." }
        }

        LaunchedEffect(id, context.startsImmediately) {
            if (!ran && context.startsImmediately && current != null) {
                ran = true
                runObservation()
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiObserveMethod, context, result, error, onBack,
            onRetry = { result = null; error = null; ran = false },
            onCommit = { result?.let { onConfirmed(it.execution) } },
            onCancel = onCancel,
            commitLabel = "Commit observation"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("An observation is a purposive act. Merely rendering the companion sprite never counts as having observed it.")
                Button(onClick = ::runObservation, enabled = current != null, modifier = Modifier.fillMaxWidth()) { Text("Record observation") }
            }
        }
    }
}

object TamagotchiMeasureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiMeasureMethod.ID
    override val title = "Measure Tamagotchi"
    override val description = "Take a scenario-authorised measurement."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val store = remember(app) { TamagotchiStore(app) }
        val id = sessionId(context, store)
        val initialLoad = remember(id) { safeSessionLoad(store, id) }
        var current by remember(id) { mutableStateOf(initialLoad.first) }
        var error by rememberSaveable(id) { mutableStateOf(initialLoad.second) }
        var result by remember { mutableStateOf<TamagotchiScreenResult?>(null) }
        var selected by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.setting("variable_id")) }
        var ran by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        val scenario = current?.let { TamagotchiCatalog.scenario(it.scenarioId) }

        fun publish(working: TamagotchiScreenResult) {
            result = working
            error = null
            if (shouldReturnImmediately(context)) onConfirmed(working.execution)
        }

        fun runMeasurement() {
            val s = current ?: run {
                error = "No valid Tamagotchi session is available to measure."
                return
            }
            val variable = selected.trim()
            if (variable.isBlank()) {
                error = "Choose a measurement first."
                return
            }
            if (scenario?.measurementVariableIds?.contains(variable) != true) {
                error = "'$variable' is not an authorised measurement in this scenario."
                return
            }
            runCatching { store.measure(s.id, variable) }
                .onSuccess { (updated, event) ->
                    current = updated
                    val values = valuesForSession(updated, capabilityId).apply {
                        this[TamagotchiFields.EVENT_JSON] = event.toJson(updated.id).toString()
                    }
                    publish(makeResult(As100TamagotchiMeasureMethod, context, values))
                }
                .onFailure { error = it.message ?: "The measurement could not be recorded." }
        }

        LaunchedEffect(id, selected, context.startsImmediately) {
            if (!ran && context.startsImmediately && current != null && selected.isNotBlank()) {
                ran = true
                runMeasurement()
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiMeasureMethod, context, result, error, onBack,
            onRetry = { result = null; error = null; ran = false },
            onCommit = { result?.let { onConfirmed(it.execution) } },
            onCancel = onCancel,
            commitLabel = "Commit measurement"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                scenario?.measurementVariableIds?.forEach { variable ->
                    FilterChip(
                        selected = selected == variable,
                        onClick = { selected = variable; result = null },
                        label = { Text(scenario.variable(variable)?.label ?: variable) }
                    )
                }
                Button(onClick = ::runMeasurement, enabled = current != null && selected.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Take measurement") }
            }
        }
    }
}

object TamagotchiAdvanceCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiAdvanceMethod.ID
    override val title = "Advance simulation"
    override val description = "Advance classroom simulation time."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val store = remember(app) { TamagotchiStore(app) }
        val id = sessionId(context, store)
        val initialLoad = remember(id) { safeSessionLoad(store, id) }
        var current by remember(id) { mutableStateOf(initialLoad.first) }
        var error by rememberSaveable(id) { mutableStateOf(initialLoad.second) }
        var result by remember { mutableStateOf<TamagotchiScreenResult?>(null) }
        var minutes by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.setting("minutes", "60").toIntOrNull()?.coerceIn(1, 10080) ?: 60) }
        var ran by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun publish(working: TamagotchiScreenResult) {
            result = working
            error = null
            if (shouldReturnImmediately(context)) onConfirmed(working.execution)
        }

        fun runAdvance() {
            val s = current ?: run {
                error = "No valid Tamagotchi session is available to advance."
                return
            }
            runCatching { store.advance(s.id, minutes.toDouble()) }
                .onSuccess { updated ->
                    current = updated
                    publish(makeResult(As100TamagotchiAdvanceMethod, context, valuesForSession(updated, capabilityId)))
                }
                .onFailure { error = it.message ?: "Simulation time could not be advanced." }
        }

        LaunchedEffect(id, context.startsImmediately) {
            if (!ran && context.startsImmediately && current != null) {
                ran = true
                runAdvance()
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiAdvanceMethod, context, result, error, onBack,
            onRetry = { result = null; error = null; ran = false },
            onCommit = { result?.let { onConfirmed(it.execution) } },
            onCancel = onCancel,
            commitLabel = "Commit time advance"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 60, 240, 720, 1440).forEach { value ->
                        FilterChip(
                            selected = minutes == value,
                            onClick = { minutes = value; result = null },
                            label = { Text(if (value < 60) "${value}m" else if (value < 1440) "${value / 60}h" else "1d") }
                        )
                    }
                }
                Button(onClick = ::runAdvance, enabled = current != null, modifier = Modifier.fillMaxWidth()) { Text("Advance") }
            }
        }
    }
}

object TamagotchiHistoryCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiHistoryMethod.ID
    override val title = "Tamagotchi history"
    override val description = "Inspect care-visible longitudinal history."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val store = remember(app) { TamagotchiStore(app) }
        val id = sessionId(context, store)
        var refresh by rememberSaveable { mutableStateOf(0) }
        val loaded = remember(id, refresh) { safeSessionLoad(store, id) }
        val s = loaded.first
        val error = loaded.second
        val history = remember(s?.id, s?.simulationMinute, refresh) { s?.let(store::historyLines).orEmpty() }
        val scenario = s?.let { TamagotchiCatalog.scenario(it.scenarioId) }
        val result = s?.let { session ->
            val values = valuesForSession(session, capabilityId).apply {
                this[TamagotchiFields.HISTORY_ARTIFACT_REF] = session.historyArtifactRef
            }
            makeResult(As100TamagotchiHistoryMethod, context, values)
        }
        var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        LaunchedEffect(id, result?.execution?.request?.id?.value) {
            if (shouldReturnImmediately(context) && !autoReturned && result != null) {
                autoReturned = true
                onConfirmed(result.execution)
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiHistoryMethod, context, result, error, onBack,
            onRetry = { refresh++ },
            onCommit = { result?.let { onConfirmed(it.execution) } },
            onCancel = onCancel,
            commitLabel = "Commit history snapshot"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s == null) {
                    Text("No active session.")
                } else {
                    Text("${s.creatureName} · ${history.size} ledger events", fontWeight = FontWeight.Bold)
                    scenario?.let { TrajectoryChart(history, it, Modifier.fillMaxWidth().height(190.dp)) }
                    history.asReversed().filter { it.optString("visibility") != "latent" }.take(12).forEach { event ->
                        Text(
                            "${event.optString("type").replace('_', ' ')} · t=${event.optDouble("simulation_minute").roundToInt()}m",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh history") }
                }
            }
        }
    }
}

object TamagotchiAnalyseCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiAnalyseMethod.ID
    override val title = "Analyse care period"
    override val description = "Reveal latent state only after care ends."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val store = remember(app) { TamagotchiStore(app) }
        val id = sessionId(context, store)
        var refresh by rememberSaveable { mutableStateOf(0) }
        val loaded = remember(id, refresh) { safeSessionLoad(store, id) }
        val s = loaded.first
        val error = loaded.second
        val result = s?.let { session ->
            val values = valuesForSession(session, capabilityId).apply {
                this[TamagotchiFields.ANALYSIS_UNLOCKED] = session.ended.toString()
                this[TamagotchiFields.LATENT_STATE_JSON] = if (session.ended) session.state.toJsonObject().toString() else ""
            }
            makeResult(As100TamagotchiAnalyseMethod, context, values)
        }
        var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        LaunchedEffect(id, result?.execution?.request?.id?.value) {
            if (shouldReturnImmediately(context) && !autoReturned && result != null) {
                autoReturned = true
                onConfirmed(result.execution)
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiAnalyseMethod, context, result, error, onBack,
            onRetry = { refresh++ },
            onCommit = { result?.let { onConfirmed(it.execution) } },
            onCancel = onCancel,
            commitLabel = "Commit analysis snapshot"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    s == null -> Text("No session.")
                    !s.ended -> Text("Analysis locked. End the care period first; hidden variables and generated-option properties remain sealed during intervention.")
                    else -> {
                        Text("Analysis unlocked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        val scenario = TamagotchiCatalog.scenario(s.scenarioId)
                        scenario.variables.forEach { variable ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(variable.label)
                                val numeric = ((s.state[variable.id] ?: variable.initial) * 10.0).roundToInt() / 10.0
                                Text("$numeric${if (variable.unit.isBlank()) "" else " ${variable.unit}"}")
                            }
                        }
                        CopyableTamagotchiValue("Seed", s.seed.toString())
                        CopyableTamagotchiValue("History artifact", "artifact://${s.historyArtifactRef}")
                    }
                }
                OutlinedButton(onClick = { refresh++ }, enabled = s != null, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
            }
        }
    }
}

object TamagotchiExportCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiExportMethod.ID
    override val title = "Export Tamagotchi dataset"
    override val description = "Export history.jsonl plus derived analysis tables."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val store = remember(app) { TamagotchiStore(app) }
        val id = sessionId(context, store)
        val initialLoad = remember(id) { safeSessionLoad(store, id) }
        var current by remember(id) { mutableStateOf(initialLoad.first) }
        var error by rememberSaveable(id) { mutableStateOf(initialLoad.second) }
        var result by remember { mutableStateOf<TamagotchiScreenResult?>(null) }
        var include by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.setting("include_analysis", "true").toBoolean()) }
        var ran by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun publish(working: TamagotchiScreenResult) {
            result = working
            error = null
            if (shouldReturnImmediately(context)) onConfirmed(working.execution)
        }

        fun runExport() {
            val s = current ?: run {
                error = "No valid Tamagotchi session is available to export."
                return
            }
            runCatching { TamagotchiExporter.export(app, s.id, include) }
                .onSuccess { exported ->
                    val refreshed = safeSessionLoad(store, s.id).first ?: s
                    current = refreshed
                    val values = valuesForSession(refreshed, capabilityId).apply {
                        this[TamagotchiFields.EXPORT_URI] = exported.uri
                        this[TamagotchiFields.EXPORT_SHA256] = exported.sha256
                        this[TamagotchiFields.EXPORT_ARTIFACT_REF] = exported.artifactRef
                        this[TamagotchiFields.ANALYSIS_UNLOCKED] = refreshed.ended.toString()
                    }
                    publish(makeResult(As100TamagotchiExportMethod, context, values))
                }
                .onFailure { error = it.message ?: "The teaching dataset could not be exported." }
        }

        LaunchedEffect(id, context.startsImmediately) {
            if (!ran && context.startsImmediately && current != null) {
                ran = true
                runExport()
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiExportMethod, context, result, error, onBack,
            onRetry = { result = null; error = null; ran = false },
            onCommit = { result?.let { onConfirmed(it.execution) } },
            onCancel = onCancel,
            commitLabel = "Commit export"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include latent analysis", fontWeight = FontWeight.Bold)
                        Text("Only honoured after the care period ends.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = include, onCheckedChange = { include = it; result = null })
                }
                Button(onClick = ::runExport, enabled = current != null, modifier = Modifier.fillMaxWidth()) { Text("Build teaching dataset ZIP") }
            }
        }
    }
}

object TamagotchiConfigureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TamagotchiConfigureMethod.ID
    override val title = "Tamagotchi scenario"
    override val description = "Inspect/validate a scenario definition."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var selected by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.setting("scenario_id", "foundation_care")) }
        val scenario = TamagotchiCatalog.scenario(selected)
        val reveal = context.setting("reveal_latent", "false").toBoolean()
        val values = linkedMapOf(
            TamagotchiFields.STATUS to "succeeded",
            TamagotchiFields.OPERATION to capabilityId,
            TamagotchiFields.SCENARIO_ID to scenario.id,
            TamagotchiFields.CONFIG_JSON to ScenarioJson.encode(scenario, reveal).toString(),
            TamagotchiFields.ERROR to ""
        )
        val result = makeResult(As100TamagotchiConfigureMethod, context, values)
        var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        LaunchedEffect(selected, context.completionMode) {
            if (shouldReturnImmediately(context) && !autoReturned) {
                autoReturned = true
                onConfirmed(result.execution)
            }
        }

        TamagotchiCapabilityScaffold(
            title, As100TamagotchiConfigureMethod, context, result, null, onBack,
            onRetry = { },
            onCommit = { onConfirmed(result.execution) },
            onCancel = onCancel,
            commitLabel = "Commit configuration"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TamagotchiCatalog.scenarios.forEach { item ->
                    FilterChip(selected = selected == item.id, onClick = { selected = item.id }, label = { Text(item.title) })
                }
                Text(scenario.subtitle)
                Text("${scenario.variables.size} state variables · ${scenario.actions.size} fixed actions · ${scenario.generators.size} procedural generators", fontWeight = FontWeight.Bold)
                Text(scenario.teachingNote, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
