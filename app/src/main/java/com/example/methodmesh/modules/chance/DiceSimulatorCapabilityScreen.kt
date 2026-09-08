package com.example.methodmesh.modules.chance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class InteractiveStage { Configure, Animating, Settled, Committed }

object DiceSimulatorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DiceSimulationMethod.ID
    override val title = "Dice"
    override val description = "Roll ordinary dice or advanced RPG dice expressions."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var expression by rememberSaveable {
            mutableStateOf(context.action.settings["expression"] ?: context.action.settings["input_expression"] ?: "d6")
        }
        var rollCount by rememberSaveable {
            mutableStateOf(context.action.settings["roll_count"] ?: context.action.settings["input_roll_count"] ?: "1")
        }
        var playerCountText by rememberSaveable {
            mutableStateOf(context.action.settings["player_count"] ?: context.action.settings["input_player_count"] ?: "1")
        }
        var playerMode by rememberSaveable {
            mutableStateOf(context.action.settings["player_mode"] ?: context.action.settings["input_player_mode"] ?: "take_turns")
        }
        var activePlayer by rememberSaveable { mutableStateOf(0) }
        var historyOutput by rememberSaveable { mutableStateOf(readBoolean(context.action.settings, "history_output", true)) }
        var rngMode by rememberSaveable {
            mutableStateOf(context.action.settings["rng_mode"] ?: context.action.settings["input_rng_mode"] ?: "secure_random")
        }
        var seed by rememberSaveable { mutableStateOf(context.action.settings["seed"] ?: context.action.settings["input_seed"] ?: "") }
        var animationMode by rememberSaveable {
            mutableStateOf(context.action.settings["animation_mode"] ?: context.action.settings["input_animation_mode"] ?: "full")
        }
        var rollOptionsOpen by rememberSaveable { mutableStateOf(false) }
        var showAdvanced by rememberSaveable { mutableStateOf(!isPlainDiceExpression(expression)) }
        var savedValuesJson by rememberSaveable { mutableStateOf("") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var stage by rememberSaveable { mutableStateOf(InteractiveStage.Configure) }
        var animationFrame by rememberSaveable { mutableStateOf(0) }

        val parseError = remember(expression) { runCatching { DiceSimulatorEngine.parse(expression) }.exceptionOrNull()?.message }
        val simpleGroups = remember(expression) { simpleGroups(expression) }
        val visualSpecs = remember(expression) { runCatching { DiceSimulatorEngine.visualDice(expression) }.getOrDefault(emptyList()) }
        val savedValues = remember(savedValuesJson) { decodeStringMap(savedValuesJson) }
        val players = playerCountText.toIntOrNull()?.coerceIn(1, 20) ?: 1
        val effectivePlayerMode = if (players > 4) "take_turns" else playerMode
        val playerRuns = remember(savedValuesJson) {
            parseDicePlayerRuns(
                savedValues[DiceSimulationFields.PLAYERS_JSON].orEmpty(),
                savedValues[DiceSimulationFields.LAST_ROLL_DETAILS_JSON].orEmpty(),
                savedValues[DiceSimulationFields.RESULT].orEmpty()
            )
        }
        val execution = remember(savedValuesJson, context.request.invocationContext, context.action.settings) {
            if (savedValuesJson.isBlank()) null else {
                val request = As100DiceSimulationMethod.request(
                    action = As100DiceSimulationMethod.ID,
                    context = context.request.invocationContext.asMap(As100DiceSimulationMethod.ID) + context.action.settings +
                        mapOf(
                            "expression" to savedValues[DiceSimulationFields.EXPRESSION].orEmpty(),
                            "roll_count" to savedValues[DiceSimulationFields.ROLL_COUNT].orEmpty(),
                            "player_count" to savedValues[DiceSimulationFields.PLAYER_COUNT].orEmpty(),
                            "player_mode" to effectivePlayerMode,
                            "rng_mode" to savedValues[DiceSimulationFields.RNG_MODE].orEmpty(),
                            "seed" to savedValues[DiceSimulationFields.SEED].orEmpty()
                        ),
                    signals = emptyList(), inputs = emptyList()
                )
                As100DiceSimulationMethod.result(request, savedValues, context.request.invocationContext)
            }
        }
        val finalVisual = remember(playerRuns, activePlayer, savedValuesJson) {
            playerRuns.getOrNull(activePlayer)?.outcomes
                ?: parseLastVisualOutcomes(savedValues[DiceSimulationFields.LAST_ROLL_DETAILS_JSON].orEmpty())
        }

        LaunchedEffect(expression, rollCount, playerCountText, playerMode, historyOutput, rngMode, seed, animationMode) {
            context.onSettingsChanged(
                mapOf(
                    "expression" to expression,
                    "roll_count" to rollCount,
                    "player_count" to playerCountText,
                    "player_mode" to effectivePlayerMode,
                    "history_output" to historyOutput.toString(),
                    "rng_mode" to rngMode,
                    "seed" to seed,
                    "animation_mode" to animationMode
                )
            )
        }

        fun animateDice(outcomes: List<VisualOutcome>) {
            if (animationMode == "off") {
                stage = InteractiveStage.Settled
                animationFrame = 100
                return
            }
            // The outcome already exists; animation only reveals it.  Keeping the
            // frame in saveable state allows a rotation to resume the reveal.
            stage = InteractiveStage.Animating
            animationFrame = 0
        }

        LaunchedEffect(stage, activePlayer, savedValuesJson, animationMode) {
            if (stage != InteractiveStage.Animating) return@LaunchedEffect
            val outcomes = if (effectivePlayerMode == "around_table" && playerRuns.size > 1) {
                playerRuns.flatMap { it.outcomes }
            } else {
                playerRuns.getOrNull(activePlayer)?.outcomes.orEmpty()
            }
            val timing = diceAnimationTiming(animationMode)
            val maxEvents = outcomes.maxOfOrNull { (it.draws.size - 1).coerceAtLeast(0) } ?: 0
            val needsDropResolution = outcomes.any { !it.retained }
            val frames = timing.baseEnd + maxEvents * timing.eventFrames + if (needsDropResolution) timing.dropFrames else 0
            var frame = animationFrame.coerceIn(0, frames)
            while (frame < frames && stage == InteractiveStage.Animating) {
                frame += 1
                animationFrame = frame
                delay(timing.frameMs)
            }
            if (stage == InteractiveStage.Animating) {
                animationFrame = frames
                stage = InteractiveStage.Settled
            }
        }

        fun runSimulation() {
            val settings = mapOf(
                "expression" to expression,
                "roll_count" to rollCount,
                "player_count" to players.toString(),
                "player_mode" to effectivePlayerMode,
                "history_output" to historyOutput.toString(),
                "rng_mode" to rngMode,
                "seed" to seed,
                "animation_mode" to animationMode
            )
            val request = As100DiceSimulationMethod.request(
                action = As100DiceSimulationMethod.ID,
                context = context.request.invocationContext.asMap(As100DiceSimulationMethod.ID) + context.action.settings + settings,
                signals = emptyList(), inputs = emptyList()
            )
            val values = As100DiceSimulationMethod.generate(settings)
            savedValuesJson = encodeStringMap(values)
            val result = As100DiceSimulationMethod.result(request, values, context.request.invocationContext)
            if (context.submitsImmediately) {
                onConfirmed(result)
                return
            }
            if (values[DiceSimulationFields.STATUS] != "succeeded") {
                stage = InteractiveStage.Configure
                return
            }
            activePlayer = 0
            val generatedRuns = parseDicePlayerRuns(
                values[DiceSimulationFields.PLAYERS_JSON].orEmpty(),
                values[DiceSimulationFields.LAST_ROLL_DETAILS_JSON].orEmpty(),
                values[DiceSimulationFields.RESULT].orEmpty()
            )
            val outcomesForAnimation = if (effectivePlayerMode == "around_table" && generatedRuns.size > 1) {
                generatedRuns.flatMap { it.outcomes }
            } else generatedRuns.firstOrNull()?.outcomes.orEmpty()
            animateDice(outcomesForAnimation)
        }

        fun advanceOrCommitDice() {
            if (effectivePlayerMode == "take_turns" && playerRuns.size > 1 && activePlayer < playerRuns.lastIndex) {
                activePlayer += 1
                animateDice(playerRuns[activePlayer].outcomes)
            } else {
                val result = execution ?: return
                if (context.submitsImmediately) onConfirmed(result) else stage = InteractiveStage.Committed
            }
        }

        val dicePresetSettings = listOf(
            "expression", "roll_count", "player_count", "player_mode", "history_output", "rng_mode", "seed", "animation_mode"
        )
        val autoRunFixedPreset = context.chanceCanAutoRunFixedPreset(dicePresetSettings)
        LaunchedEffect(context.submitsImmediately, autoRunFixedPreset, context.action.settings) {
            if ((context.submitsImmediately || autoRunFixedPreset) && !launched) {
                launched = true
                runSimulation()
            }
        }

        if (stage == InteractiveStage.Animating || stage == InteractiveStage.Settled || stage == InteractiveStage.Committed) {
            if (effectivePlayerMode == "around_table" && playerRuns.size in 2..4) {
                DiceAroundTableExperience(
                    specs = visualSpecs,
                    runs = playerRuns,
                    frame = animationFrame,
                    settled = stage != InteractiveStage.Animating,
                    committed = stage == InteractiveStage.Committed,
                    expression = savedValues[DiceSimulationFields.CANONICAL_EXPRESSION].orEmpty(),
                    animationMode = animationMode,
                    primaryResult = savedValues[DiceSimulationFields.RESULT].orEmpty(),
                    auditJson = savedValues[DiceSimulationFields.AUDIT_JSON].orEmpty(),
                    onCommit = { advanceOrCommitDice() },
                    onRollAgain = { runSimulation() },
                    onDone = { execution?.let(onConfirmed) },
                    onNewRun = { stage = InteractiveStage.Configure; savedValuesJson = ""; activePlayer = 0 },
                    onCancel = onCancel
                )
            } else {
                val run = playerRuns.getOrNull(activePlayer)
                DiceRollExperience(
                    specs = visualSpecs,
                    outcomes = run?.outcomes ?: finalVisual,
                    frame = animationFrame,
                    settled = stage != InteractiveStage.Animating,
                    committed = stage == InteractiveStage.Committed,
                    result = run?.result ?: savedValues[DiceSimulationFields.RESULT].orEmpty(),
                    primaryResult = savedValues[DiceSimulationFields.RESULT].orEmpty(),
                    expression = savedValues[DiceSimulationFields.CANONICAL_EXPRESSION].orEmpty(),
                    animationMode = animationMode,
                    playerLabel = if (playerRuns.size > 1) "Player ${activePlayer + 1} of ${playerRuns.size}" else null,
                    primaryActionLabel = if (effectivePlayerMode == "take_turns" && activePlayer < playerRuns.lastIndex) "Next player" else "Commit",
                    auditJson = savedValues[DiceSimulationFields.AUDIT_JSON].orEmpty(),
                    onCommit = { advanceOrCommitDice() },
                    onRollAgain = { runSimulation() },
                    onDone = { execution?.let(onConfirmed) },
                    onNewRun = { stage = InteractiveStage.Configure; savedValuesJson = ""; activePlayer = 0 },
                    onCancel = onCancel
                )
            }
            return
        }

        ChanceConfigSurface(
            title = title,
            context = context,
            onBack = onBack,
            onCancel = onCancel
        ) {
            if (context.settingShouldBeShown("expression")) {
                Text("Quick roll", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickRollButton("D6", "d6") { expression = it; showAdvanced = false }
                    QuickRollButton("D20", "d20") { expression = it; showAdvanced = false }
                    QuickRollButton("2D6", "2d6") { expression = it; showAdvanced = false }
                    QuickRollButton("Percentile", "d%") { expression = it; showAdvanced = true }
                    QuickRollButton("More…", "2d20kh1") { expression = it; showAdvanced = true }
                }

                Spacer(Modifier.height(14.dp))
                Text("Dice", style = MaterialTheme.typography.titleSmall)
                if (simpleGroups != null) {
                    simpleGroups.forEachIndexed { index, group ->
                        DiceGroupEditor(
                            group = group,
                            onChange = { updated ->
                                val groups = simpleGroups.toMutableList(); groups[index] = updated
                                expression = groupsToExpressionPreservingRules(expression, groups)
                            },
                            onRemove = {
                                if (simpleGroups.size > 1) {
                                    val groups = simpleGroups.toMutableList(); groups.removeAt(index)
                                    expression = groupsToExpressionPreservingRules(expression, groups)
                                }
                            },
                            canRemove = simpleGroups.size > 1
                        )
                    }
                    OutlinedButton(
                        onClick = { expression = groupsToExpressionPreservingRules(expression, simpleGroups + DiceGroup(1, "6")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ Add die") }
                } else {
                    Text("This expression is easiest to edit in Advanced notation.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { expression = "d6"; showAdvanced = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset to D6")
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { rollOptionsOpen = !rollOptionsOpen }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (rollOptionsOpen) "Hide roll options" else "Roll options  ▾")
                }
                if (rollOptionsOpen) {
                    RollOptionsPanel(expression = expression, onExpressionChange = { expression = it })
                }

                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAdvanced) "Hide advanced notation" else "Advanced notation  ▾")
                }
                if (showAdvanced) {
                    OutlinedTextField(
                        expression,
                        { expression = it.take(160) },
                        label = { Text("Dice notation") },
                        supportingText = { Text("Examples: 4d6kh3 · d20+5>=15 · d6! · d6rr<3 · 8d6cs>=5 · dF") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                parseError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            if (context.settingShouldBeShown("player_count")) {
                Spacer(Modifier.height(10.dp))
                ChanceCountStepper(
                    label = "Players",
                    value = players,
                    minimum = 1,
                    maximum = 20,
                    onValueChange = { playerCountText = it.toString() },
                    supportingText = "Everyone uses the same dice setup."
                )
            }
            if (players > 1 && context.settingShouldBeShown("player_mode")) {
                if (players <= 4) {
                    TwoChoiceToggle(
                        "Player view", effectivePlayerMode,
                        "take_turns" to "Take turns", "around_table" to "Around table"
                    ) { playerMode = it }
                } else {
                    Text("Take turns is used for more than four players.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (context.settingShouldBeShown("roll_count")) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    rollCount,
                    { rollCount = it.filter(Char::isDigit).take(4) },
                    label = { Text(if (players == 1) "Number of simulations" else "Rolls per player") },
                    supportingText = { Text(if (players == 1) "Use more than one for a documented roll series." else "Each player gets the same number of recorded rolls.") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            if (context.settingShouldBeShown("history_output")) {
                ChanceSwitchRow("Return full series", historyOutput, { historyOutput = it })
            }
            if (context.settingShouldBeShown("rng_mode")) RandomSourceChooser(rngMode) { rngMode = it }
            if (rngMode == "fixed_seed" && context.settingShouldBeShown("seed")) {
                OutlinedTextField(seed, { seed = it }, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (context.settingShouldBeShown("animation_mode")) AnimationChooser(animationMode) { animationMode = it }

            Spacer(Modifier.height(14.dp))
            Button(onClick = { runSimulation() }, modifier = Modifier.fillMaxWidth(), enabled = parseError == null) { Text("ROLL") }
            savedValues[DiceSimulationFields.ERROR]?.takeIf { it.isNotBlank() }?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

object CoinTossCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100CoinTossMethod.ID
    override val title = "Coin toss"
    override val description = "Toss one or more fair coins."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var tossCount by rememberSaveable { mutableStateOf(context.action.settings["toss_count"] ?: context.action.settings["input_toss_count"] ?: "1") }
        var historyOutput by rememberSaveable { mutableStateOf(readBoolean(context.action.settings, "history_output", true)) }
        var rngMode by rememberSaveable { mutableStateOf(context.action.settings["rng_mode"] ?: context.action.settings["input_rng_mode"] ?: "secure_random") }
        var seed by rememberSaveable { mutableStateOf(context.action.settings["seed"] ?: context.action.settings["input_seed"] ?: "") }
        var animationMode by rememberSaveable { mutableStateOf(context.action.settings["animation_mode"] ?: context.action.settings["input_animation_mode"] ?: "full") }
        var interactionMode by rememberSaveable {
            mutableStateOf(context.action.settings["interaction_mode"] ?: context.action.settings["input_interaction_mode"] ?: "record")
        }
        var savedValuesJson by rememberSaveable { mutableStateOf("") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var stage by rememberSaveable { mutableStateOf(InteractiveStage.Configure) }
        var animationFrame by rememberSaveable { mutableStateOf(0) }

        val savedValues = remember(savedValuesJson) { decodeStringMap(savedValuesJson) }
        val execution = remember(savedValuesJson, context.request.invocationContext, context.action.settings) {
            if (savedValuesJson.isBlank()) null else {
                val request = As100CoinTossMethod.request(
                    action = As100CoinTossMethod.ID,
                    context = context.request.invocationContext.asMap(As100CoinTossMethod.ID) + context.action.settings +
                        mapOf(
                            "toss_count" to savedValues[CoinTossFields.TOSS_COUNT].orEmpty(),
                            "rng_mode" to savedValues[CoinTossFields.RNG_MODE].orEmpty(),
                            "seed" to savedValues[CoinTossFields.SEED].orEmpty()
                        ),
                    signals = emptyList(), inputs = emptyList()
                )
                As100CoinTossMethod.result(request, savedValues, context.request.invocationContext)
            }
        }
        val outcomes = remember(savedValuesJson) { coinOutcomes(savedValues[CoinTossFields.TOSSES_JSON].orEmpty(), savedValues[CoinTossFields.AUDIT_JSON].orEmpty(), savedValues[CoinTossFields.LAST_TOSS].orEmpty()) }
        val count = tossCount.toIntOrNull()?.coerceIn(1, 10000) ?: 1
        val casual = !context.submitsImmediately && count == 1 && interactionMode == "just_toss"

        LaunchedEffect(tossCount, historyOutput, rngMode, seed, animationMode, interactionMode) {
            context.onSettingsChanged(
                mapOf(
                    "toss_count" to tossCount,
                    "history_output" to historyOutput.toString(),
                    "rng_mode" to rngMode,
                    "seed" to seed,
                    "animation_mode" to animationMode,
                    "interaction_mode" to interactionMode
                )
            )
        }

        fun runToss() {
            val oneShot = casual
            val settings = mapOf(
                "toss_count" to if (oneShot) "1" else tossCount,
                "history_output" to if (oneShot) "false" else historyOutput.toString(),
                "rng_mode" to rngMode,
                "seed" to seed,
                "animation_mode" to animationMode
            )
            val request = As100CoinTossMethod.request(
                action = As100CoinTossMethod.ID,
                context = context.request.invocationContext.asMap(As100CoinTossMethod.ID) + context.action.settings + settings,
                signals = emptyList(), inputs = emptyList()
            )
            val values = As100CoinTossMethod.generate(settings)
            savedValuesJson = encodeStringMap(values)
            val result = As100CoinTossMethod.result(request, values, context.request.invocationContext)
            if (context.submitsImmediately) {
                onConfirmed(result)
                return
            }
            if (values[CoinTossFields.STATUS] != "succeeded") {
                stage = InteractiveStage.Configure
                return
            }
            if (animationMode == "off" || count > 10) {
                animationFrame = 100
                stage = InteractiveStage.Settled
                return
            }
            stage = InteractiveStage.Animating
            animationFrame = 0
        }

        LaunchedEffect(stage, savedValuesJson, animationMode) {
            if (stage != InteractiveStage.Animating) return@LaunchedEffect
            val frameMs = if (animationMode == "full") 65L else 40L
            val frames = if (animationMode == "full") 24 else 12
            var frame = animationFrame.coerceIn(0, frames)
            while (frame < frames && stage == InteractiveStage.Animating) {
                frame += 1
                animationFrame = frame
                delay(frameMs)
            }
            if (stage == InteractiveStage.Animating) {
                animationFrame = 100
                stage = InteractiveStage.Settled
            }
        }

        fun commitToss() {
            val result = execution ?: return
            if (context.submitsImmediately) onConfirmed(result) else stage = InteractiveStage.Committed
        }

        val coinPresetSettings = listOf("toss_count", "interaction_mode", "history_output", "rng_mode", "seed", "animation_mode")
        val autoRunFixedPreset = context.chanceCanAutoRunFixedPreset(coinPresetSettings)
        LaunchedEffect(context.submitsImmediately, autoRunFixedPreset, context.action.settings) {
            if ((context.submitsImmediately || autoRunFixedPreset) && !launched) {
                launched = true
                runToss()
            }
        }

        if (stage == InteractiveStage.Animating || stage == InteractiveStage.Settled || stage == InteractiveStage.Committed) {
            CoinTossExperience(
                outcomes = outcomes,
                count = count,
                frame = animationFrame,
                settled = stage != InteractiveStage.Animating,
                committed = stage == InteractiveStage.Committed,
                casual = casual,
                result = savedValues[CoinTossFields.RESULT].orEmpty(),
                auditJson = savedValues[CoinTossFields.AUDIT_JSON].orEmpty(),
                onCommit = { commitToss() },
                onTossAgain = { runToss() },
                onDone = { if (casual) onCancel() else execution?.let(onConfirmed) },
                onNewRun = { stage = InteractiveStage.Configure; savedValuesJson = "" },
                onCancel = onCancel
            )
            return
        }

        ChanceConfigSurface(
            title = title,
            context = context,
            onBack = onBack,
            onCancel = onCancel
        ) {
            if (context.settingShouldBeShown("toss_count")) {
                OutlinedTextField(
                    tossCount, { tossCount = it.filter(Char::isDigit).take(5) },
                    label = { Text("Number of coins") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                if (count > 10) Text("More than 10 coins: individual coin animation is skipped.", style = MaterialTheme.typography.bodySmall)
            }
            if (count == 1 && !context.submitsImmediately && context.settingShouldBeShown("interaction_mode")) {
                TwoChoiceToggle(
                    "Single-coin mode", interactionMode,
                    "just_toss" to "Just toss", "record" to "Record toss"
                ) { interactionMode = it }
                if (interactionMode == "just_toss") {
                    Text("Just toss is not accumulated into a session history. Toss again as often as you like, then tap Done.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!casual && context.settingShouldBeShown("history_output")) {
                ChanceSwitchRow("Return full series", historyOutput, { historyOutput = it })
            }
            if (context.settingShouldBeShown("rng_mode")) RandomSourceChooser(rngMode) { rngMode = it }
            if (rngMode == "fixed_seed" && context.settingShouldBeShown("seed")) {
                OutlinedTextField(seed, { seed = it }, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (context.settingShouldBeShown("animation_mode")) AnimationChooser(animationMode) { animationMode = it }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { runToss() }, modifier = Modifier.fillMaxWidth()) { Text("TOSS") }
            savedValues[CoinTossFields.ERROR]?.takeIf { it.isNotBlank() }?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private data class DiceGroup(val count: Int, val sides: String)
private data class VisualDraw(val kind: String, val value: Int)
private data class VisualOutcome(
    val termIndex: Int,
    val dieIndex: Int,
    val sides: String,
    val initialValue: Int,
    val acceptedBaseValue: Int,
    val retained: Boolean,
    val explosionValues: List<Int>,
    val rerolledValues: List<Int>,
    val draws: List<VisualDraw>
)
private data class DicePlayerRun(val player: Int, val result: String, val outcomes: List<VisualOutcome>)

private fun parseDicePlayerRuns(playersJson: String, fallbackDetails: String, fallbackResult: String): List<DicePlayerRun> = runCatching {
    if (playersJson.isBlank()) return@runCatching emptyList()
    val array = JSONArray(playersJson)
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        val details = obj.optJSONObject("last_roll_details")?.toString()
            ?: obj.optString("last_roll_details")
        DicePlayerRun(
            player = obj.optInt("player", index + 1),
            result = obj.optString("result"),
            outcomes = parseLastVisualOutcomes(details)
        )
    }
}.getOrDefault(emptyList()).ifEmpty {
    listOf(DicePlayerRun(1, fallbackResult, parseLastVisualOutcomes(fallbackDetails)))
}

private enum class DieEffect { Neutral, RerollFlash, ExplosionFlash, DropFlash, Dropped }
private data class DieComponentVisual(val value: Int, val rolling: Boolean = false, val effect: DieEffect = DieEffect.Neutral)
private data class DieTimelineVisual(val base: DieComponentVisual, val insets: List<DieComponentVisual>, val dropped: Boolean)

@Composable
private fun DiceRollExperience(
    specs: List<Pair<String, Int>>,
    outcomes: List<VisualOutcome>,
    frame: Int,
    settled: Boolean,
    committed: Boolean,
    result: String,
    primaryResult: String,
    expression: String,
    animationMode: String,
    playerLabel: String? = null,
    primaryActionLabel: String = "Commit",
    auditJson: String,
    onCommit: () -> Unit,
    onRollAgain: () -> Unit,
    onDone: () -> Unit,
    onNewRun: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (committed) "Dice" else if (settled) "Current roll" else "Rolling…", style = MaterialTheme.typography.headlineMedium)
            playerLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (expression.isNotBlank()) Text(expression, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        DiceTray(specs, outcomes, settled = settled, frame = frame, animationMode = animationMode, large = true)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (settled) {
                ChanceCopyableText(
                    value = result,
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
            if (settled && committed) {
                ChancePostCommitActions(
                    title = "Dice",
                    primaryText = primaryResult.ifBlank { result },
                    auditJson = auditJson,
                    onDone = onDone,
                    onNewRun = onNewRun
                )
            } else if (settled) {
                Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text(primaryActionLabel) }
                OutlinedButton(onClick = onRollAgain, modifier = Modifier.fillMaxWidth()) { Text("Roll again") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            } else {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun DiceAroundTableExperience(
    specs: List<Pair<String, Int>>,
    runs: List<DicePlayerRun>,
    frame: Int,
    settled: Boolean,
    committed: Boolean,
    expression: String,
    animationMode: String,
    primaryResult: String,
    auditJson: String,
    onCommit: () -> Unit,
    onRollAgain: () -> Unit,
    onDone: () -> Unit,
    onNewRun: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (committed) "Dice" else if (settled) "Current rolls" else "Rolling…", style = MaterialTheme.typography.headlineMedium)
        if (expression.isNotBlank()) Text(expression, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        when (runs.size) {
            2 -> {
                AroundTableDicePanel(specs, runs[0], frame, settled, animationMode, 180f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                AroundTableDicePanel(specs, runs[1], frame, settled, animationMode, 0f, Modifier.fillMaxWidth())
            }
            3 -> {
                AroundTableDicePanel(specs, runs[0], frame, settled, animationMode, 180f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AroundTableDicePanel(specs, runs[1], frame, settled, animationMode, 90f, Modifier.weight(1f))
                    AroundTableDicePanel(specs, runs[2], frame, settled, animationMode, -90f, Modifier.weight(1f))
                }
            }
            else -> {
                AroundTableDicePanel(specs, runs[0], frame, settled, animationMode, 180f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AroundTableDicePanel(specs, runs[1], frame, settled, animationMode, 90f, Modifier.weight(1f))
                    AroundTableDicePanel(specs, runs[2], frame, settled, animationMode, -90f, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                AroundTableDicePanel(specs, runs[3], frame, settled, animationMode, 0f, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(10.dp))
        if (settled && committed) {
            ChancePostCommitActions(
                title = "Dice",
                primaryText = primaryResult,
                auditJson = auditJson,
                onDone = onDone,
                onNewRun = onNewRun
            )
        } else if (settled) {
            Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text("Commit") }
            OutlinedButton(onClick = onRollAgain, modifier = Modifier.fillMaxWidth()) { Text("Roll again") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        } else {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun AroundTableDicePanel(
    specs: List<Pair<String, Int>>,
    run: DicePlayerRun,
    frame: Int,
    settled: Boolean,
    animationMode: String,
    rotation: Float,
    modifier: Modifier
) {
    Column(
        modifier.padding(4.dp).graphicsLayer { rotationZ = rotation },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Player ${run.player}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        DiceTray(specs, run.outcomes, settled = settled, frame = frame, animationMode = animationMode, large = false)
        if (settled) ChanceCopyableText(run.result, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CoinTossExperience(
    outcomes: List<Boolean>,
    count: Int,
    frame: Int,
    settled: Boolean,
    committed: Boolean,
    casual: Boolean,
    result: String,
    auditJson: String,
    onCommit: () -> Unit,
    onTossAgain: () -> Unit,
    onDone: () -> Unit,
    onNewRun: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(if (committed) "Coin toss" else if (settled) "Current toss" else "Tossing…", style = MaterialTheme.typography.headlineMedium)
        if (count <= 10) {
            CoinTray(outcomes, count, frame, settled)
        } else {
            Box(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (settled) ChanceCopyableText(result, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                else Text("Tossing $count coins…", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (settled) ChanceCopyableText(result, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            when {
                settled && casual -> {
                    Button(onClick = onTossAgain, modifier = Modifier.fillMaxWidth()) { Text("Toss again") }
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                settled && committed -> {
                    ChancePostCommitActions(
                        title = "Coin toss",
                        primaryText = result,
                        auditJson = auditJson,
                        onDone = onDone,
                        onNewRun = onNewRun
                    )
                }
                settled -> {
                    Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text("Commit") }
                    OutlinedButton(onClick = onTossAgain, modifier = Modifier.fillMaxWidth()) { Text("Toss again") }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                else -> OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun CoinTray(outcomes: List<Boolean>, count: Int, frame: Int, settled: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        (0 until count).toList().chunked(if (count <= 4) count.coerceAtLeast(1) else 4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { i ->
                    val settleFrame = 13 + ((i * 5 + 3) % 10)
                    val coinSettled = settled || frame >= settleFrame
                    val shownHeads = if (coinSettled) outcomes.getOrNull(i) ?: true else (frame + i) % 2 == 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Pseudo3dCoin(
                            heads = shownHeads,
                            tick = if (coinSettled) 0 else frame + i * 2,
                            modifier = Modifier.size(if (count == 1) 190.dp else 86.dp)
                        )
                        if (coinSettled) ChanceCopyableText(if (shownHeads) "Heads" else "Tails", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRollButton(label: String, expression: String, onSelect: (String) -> Unit) {
    OutlinedButton(onClick = { onSelect(expression) }) { Text(label) }
}

@Composable
private fun DiceGroupEditor(group: DiceGroup, onChange: (DiceGroup) -> Unit, onRemove: () -> Unit, canRemove: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { if (group.count > 1) onChange(group.copy(count = group.count - 1)) },
                modifier = Modifier.size(44.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("−") }
            Text("${group.count}×", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            OutlinedButton(
                onClick = { if (group.count < DiceSimulatorEngine.MAX_DICE) onChange(group.copy(count = group.count + 1)) },
                modifier = Modifier.size(44.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("+") }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dieLabel(group.sides), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▼")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf("4", "6", "8", "10", "12", "20", "100").forEach { sides ->
                        DropdownMenuItem(text = { Text("D$sides") }, onClick = {
                            onChange(group.copy(sides = sides)); menuOpen = false; customOpen = false
                        })
                    }
                    DropdownMenuItem(text = { Text("Other…") }, onClick = { menuOpen = false; customOpen = true })
                }
            }
            if (canRemove) {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("×") }
            }
        }
        if (customOpen) {
            OutlinedTextField(
                group.sides,
                { value -> onChange(group.copy(sides = value.filter(Char::isDigit).take(4).ifBlank { "6" })) },
                label = { Text("Number of sides") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
    }
}

private data class DiceAnimationTiming(
    val baseEnd: Int,
    val eventFrames: Int,
    val flashFrames: Int,
    val dropFrames: Int,
    val frameMs: Long
)

private fun diceAnimationTiming(mode: String): DiceAnimationTiming =
    if (mode == "full") DiceAnimationTiming(baseEnd = 26, eventFrames = 7, flashFrames = 2, dropFrames = 6, frameMs = 70L)
    else DiceAnimationTiming(baseEnd = 13, eventFrames = 4, flashFrames = 1, dropFrames = 3, frameMs = 42L)

private fun timelineForDie(
    outcome: VisualOutcome?,
    spec: Pair<String, Int>,
    frame: Int,
    settled: Boolean,
    timing: DiceAnimationTiming,
    dieIndex: Int,
    globalEventCount: Int
): DieTimelineVisual {
    if (outcome == null) {
        return DieTimelineVisual(DieComponentVisual(defaultFace(spec.first)), emptyList(), false)
    }
    if (settled) {
        return DieTimelineVisual(
            base = DieComponentVisual(outcome.acceptedBaseValue, effect = if (outcome.retained) DieEffect.Neutral else DieEffect.Dropped),
            insets = outcome.explosionValues.map { DieComponentVisual(it, effect = if (outcome.retained) DieEffect.Neutral else DieEffect.Dropped) },
            dropped = !outcome.retained
        )
    }

    // Base phase: every die is neutral. No keep/drop/explosion result is leaked before the roll settles.
    val settleFrame = (timing.baseEnd - 11) + ((dieIndex * 7 + 2) % 10)
    if (frame <= timing.baseEnd) {
        val stillRolling = frame < settleFrame
        return DieTimelineVisual(
            base = DieComponentVisual(
                value = if (stillRolling) animatedFace(spec.first, spec.second, dieIndex, frame) else outcome.initialValue,
                rolling = stillRolling
            ),
            insets = emptyList(),
            dropped = false
        )
    }

    val components = mutableListOf(outcome.initialValue)
    var activeEffect = DieEffect.Neutral
    var activeComponent = 0
    var temporaryInset: DieComponentVisual? = null
    val events = outcome.draws.drop(1)
    val eventElapsed = frame - timing.baseEnd - 1
    val completed = (eventElapsed / timing.eventFrames).coerceIn(0, events.size)

    for (i in 0 until completed) {
        val event = events[i]
        if (event.kind == "explosion") components += event.value
        else if (event.kind == "reroll") components[components.lastIndex] = event.value
    }

    if (completed < events.size) {
        val event = events[completed]
        val within = eventElapsed % timing.eventFrames
        activeComponent = components.lastIndex
        if (within < timing.flashFrames) {
            activeEffect = if (event.kind == "explosion") DieEffect.ExplosionFlash else DieEffect.RerollFlash
        } else if (event.kind == "explosion") {
            temporaryInset = DieComponentVisual(
                value = animatedFace(spec.first, spec.second, dieIndex + completed + 17, frame),
                rolling = true
            )
        } else if (event.kind == "reroll") {
            components[components.lastIndex] = animatedFace(spec.first, spec.second, dieIndex + completed + 29, frame)
        }
    }

    val allEventsDone = completed >= events.size
    // Keep/drop is a group-level resolution and is only revealed after every die has
    // finished its reroll/explosion chain. This prevents a discarded die being grey
    // while another die is still resolving.
    val dropStart = timing.baseEnd + globalEventCount * timing.eventFrames
    val inDropPhase = allEventsDone && !outcome.retained && frame > dropStart
    val dropElapsed = (frame - dropStart).coerceAtLeast(0)
    val dropEffect = when {
        !inDropPhase -> null
        dropElapsed <= timing.dropFrames - 2 -> DieEffect.DropFlash
        else -> DieEffect.Dropped
    }

    fun effectFor(componentIndex: Int): DieEffect = when {
        dropEffect != null -> dropEffect
        componentIndex == activeComponent -> activeEffect
        else -> DieEffect.Neutral
    }

    val base = DieComponentVisual(components.first(), effect = effectFor(0))
    val insetValues = components.drop(1).mapIndexed { i, value -> DieComponentVisual(value, effect = effectFor(i + 1)) }.toMutableList()
    temporaryInset?.let { insetValues += it }
    return DieTimelineVisual(base, insetValues, dropEffect == DieEffect.Dropped)
}

@Composable
private fun DiceTray(
    specs: List<Pair<String, Int>>,
    outcomes: List<VisualOutcome>,
    settled: Boolean,
    frame: Int,
    animationMode: String,
    large: Boolean = false
) {
    if (specs.isEmpty()) return
    val timing = diceAnimationTiming(animationMode)
    val globalEventCount = outcomes.maxOfOrNull { (it.draws.size - 1).coerceAtLeast(0) } ?: 0
    // Never use more than four base-die slots per row. Explosion/reroll descendants
    // are nested inside the parent slot, so pathological chains grow downward, not sideways.
    val perRow = when {
        specs.size <= 3 -> specs.size
        else -> 4
    }
    val dieSize = when {
        !large -> 78.dp
        specs.size <= 2 -> 142.dp
        specs.size <= 4 -> 112.dp
        specs.size <= 8 -> 94.dp
        specs.size <= 16 -> 82.dp
        specs.size <= 25 -> 68.dp
        else -> 56.dp
    }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        specs.chunked(perRow.coerceAtLeast(1)).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEachIndexed { colIndex, spec ->
                    val index = rowIndex * perRow + colIndex
                    val outcome = outcomes.getOrNull(index)
                    val visual = timelineForDie(outcome, spec, frame, settled, timing, index, globalEventCount)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Pseudo3dDie(
                            sidesLabel = spec.first,
                            numericSides = spec.second,
                            effect = visual.base.effect,
                            tick = if (visual.base.rolling) frame + index * 3 else index,
                            modifier = Modifier.size(dieSize)
                        )
                        val baseValueText = if (spec.first.equals("F", true)) fateLabel(visual.base.value) else visual.base.value.toString()
                        if (settled) {
                            ChanceCopyableText(
                                baseValueText,
                                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                                modifier = Modifier.alpha(if (visual.dropped) 0.35f else 1f)
                            )
                        } else {
                            Text(
                                baseValueText,
                                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                                modifier = Modifier.alpha(if (visual.dropped) 0.35f else 1f)
                            )
                        }
                        if (visual.insets.isNotEmpty()) {
                            val visibleInsets = visual.insets.take(8)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                visibleInsets.chunked(2).forEachIndexed { insetRowIndex, insetRow ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top) {
                                        insetRow.forEachIndexed { insetColIndex, inset ->
                                            val insetIndex = insetRowIndex * 2 + insetColIndex
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Pseudo3dDie(
                                                    sidesLabel = spec.first,
                                                    numericSides = spec.second,
                                                    effect = inset.effect,
                                                    tick = if (inset.rolling) frame + insetIndex * 5 + 41 else insetIndex + 3,
                                                    modifier = Modifier.size((dieSize.value * 0.42f).coerceAtLeast(26f).dp)
                                                )
                                                if (!inset.rolling) {
                                                    val insetValueText = if (spec.first.equals("F", true)) fateLabel(inset.value) else inset.value.toString()
                                                    if (settled) {
                                                        ChanceCopyableText(insetValueText, style = MaterialTheme.typography.labelSmall)
                                                    } else {
                                                        Text(insetValueText, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (visual.insets.size > 8) Text("+${visual.insets.size - 8} more", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pseudo3dDie(
    sidesLabel: String,
    numericSides: Int,
    effect: DieEffect,
    tick: Int,
    modifier: Modifier = Modifier
) {
    val normalLine = MaterialTheme.colorScheme.onSurface
    val line = when (effect) {
        DieEffect.DropFlash -> MaterialTheme.colorScheme.error
        DieEffect.ExplosionFlash -> androidx.compose.ui.graphics.Color(0xFF2E8B57)
        DieEffect.RerollFlash -> androidx.compose.ui.graphics.Color(0xFFD18B22)
        else -> normalLine
    }
    val mesh = remember(numericSides) { meshForSides(numericSides) }
    val opacity = if (effect == DieEffect.Dropped) 0.30f else 1f
    Box(modifier.alpha(opacity), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawOval(
                color = normalLine.copy(alpha = 0.12f),
                topLeft = Offset(size.width * 0.18f, size.height * 0.76f),
                size = Size(size.width * 0.64f, size.height * 0.13f)
            )
            val phase = tick * 0.24
            val projected = projectMesh(normalizeVertices(mesh.vertices), phase + numericSides * 0.017, phase * 0.73 + 0.6, phase * 0.41, centre, size.minDimension * 0.39f)
            mesh.edges.forEach { edge ->
                val a = projected[edge.first]; val b = projected[edge.second]
                val depth = (a.depth + b.depth) / 2f
                drawLine(
                    color = line.copy(alpha = (0.35f + (depth + 1f) * 0.27f).coerceIn(0.28f, 1f)),
                    start = a.point, end = b.point,
                    strokeWidth = if (depth > 0f) 2.2.dp.toPx() else 1.2.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun Pseudo3dCoin(heads: Boolean, tick: Int, modifier: Modifier = Modifier) {
    val accent = androidx.compose.ui.graphics.Color(0xFF477C78)
    val neutral = androidx.compose.ui.graphics.Color(0xFFB8AFA8)
    val dark = androidx.compose.ui.graphics.Color(0xFF302A28)
    val warmFace = androidx.compose.ui.graphics.Color(0xFFF4EEE9)
    val squash = (0.08f + 0.92f * kotlin.math.abs(cos(tick * 0.48))).toFloat()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // The unsquashed coin is a true circle. scaleX alone creates the edge-on ellipse.
            val diameter = size.minDimension * 0.84f
            val w = diameter * squash
            val left = (size.width - w) / 2f
            val top = (size.height - diameter) / 2f
            drawOval(warmFace, topLeft = Offset(left, top), size = Size(w, diameter))
            drawOval(accent, topLeft = Offset(left, top), size = Size(w, diameter), style = Stroke(3.2.dp.toPx()))
            if (squash > 0.36f && !heads) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val faceScale = squash.coerceAtMost(1f)
                val barH = diameter * 0.072f
                val radius = androidx.compose.ui.geometry.CornerRadius(barH / 2f, barH / 2f)
                val baseW = diameter * 0.48f * faceScale
                val x0 = cx - baseW / 2f
                drawRoundRect(dark, topLeft = Offset(x0, cy - barH * 2.0f), size = Size(baseW, barH), cornerRadius = radius)
                drawRoundRect(accent, topLeft = Offset(cx - baseW * 0.39f, cy - barH * 0.55f), size = Size(baseW * 0.89f, barH), cornerRadius = radius)
                drawRoundRect(neutral, topLeft = Offset(cx - baseW * 0.13f, cy + barH * 0.90f), size = Size(baseW * 0.63f, barH), cornerRadius = radius)
            }
        }
        if (heads && squash > 0.36f) {
            Text(
                ":3",
                color = dark,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.graphicsLayer { scaleX = squash }
            )
        }
    }
}

@Composable
private fun RollOptionsPanel(expression: String, onExpressionChange: (String) -> Unit) {
    val parsed = remember(expression) { runCatching { DiceSimulatorEngine.parse(expression) }.getOrNull() }
    val diceTerms = parsed?.terms?.filterIsInstance<DiceSimulatorEngine.DiceTerm>().orEmpty()
    var targetGroup by rememberSaveable { mutableStateOf(0) }
    if (parsed == null || diceTerms.isEmpty()) {
        Text("Enter a valid dice expression before applying roll options.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val selectedGroup = targetGroup.coerceIn(0, diceTerms.lastIndex)
    val target = diceTerms[selectedGroup]
    if (diceTerms.size > 1) {
        Text("Dice group", style = MaterialTheme.typography.labelMedium)
        ChoiceDropdown(
            value = selectedGroup.toString(),
            choices = diceTerms.mapIndexed { index, term ->
                index.toString() to "${term.count} × ${if (term.fate) "DF" else "D${term.sides}"}"
            }
        ) { selected -> targetGroup = selected.toIntOrNull()?.coerceIn(0, diceTerms.lastIndex) ?: 0 }
        Text("Keep/drop is resolved inside this dice group only; unlike dice are never compared with one another.", style = MaterialTheme.typography.bodySmall)
    }

    Spacer(Modifier.height(6.dp))
    Text("Keep / drop", style = MaterialTheme.typography.labelMedium)
    ChoiceDropdown(
        value = target.keepDrop?.kind?.code ?: "none",
        choices = listOf("none" to "None", "kh" to "Keep highest", "kl" to "Keep lowest", "dh" to "Drop highest", "dl" to "Drop lowest")
    ) { code ->
        val replacement = if (code == "none") null else {
            val kind = when (code) {
                "kh" -> DiceSimulatorEngine.KeepDropKind.KEEP_HIGHEST
                "kl" -> DiceSimulatorEngine.KeepDropKind.KEEP_LOWEST
                "dh" -> DiceSimulatorEngine.KeepDropKind.DROP_HIGHEST
                else -> DiceSimulatorEngine.KeepDropKind.DROP_LOWEST
            }
            DiceSimulatorEngine.KeepDropRule(kind, target.keepDrop?.count?.coerceAtMost(target.count) ?: 1)
        }
        onExpressionChange(updateDiceTermAt(expression, selectedGroup) { it.copy(keepDrop = replacement) })
    }
    if (target.keepDrop != null) {
        OutlinedTextField(
            target.keepDrop.count.toString(),
            { raw ->
                val n = raw.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, target.count) ?: 1
                onExpressionChange(updateDiceTermAt(expression, selectedGroup) { term -> term.copy(keepDrop = term.keepDrop?.copy(count = n)) })
            },
            label = { Text("How many") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }

    Spacer(Modifier.height(6.dp))
    Text("Reroll", style = MaterialTheme.typography.labelMedium)
    val rerollCode = when {
        target.reroll == null -> "none"
        !target.reroll.repeat && target.reroll.condition.op == DiceSimulatorEngine.CompareOp.EQ && target.reroll.condition.target == 1 -> "ones_once"
        target.reroll.repeat && target.reroll.condition.op in listOf(DiceSimulatorEngine.CompareOp.LT, DiceSimulatorEngine.CompareOp.LTE) -> "below"
        target.reroll.repeat && target.reroll.condition.op in listOf(DiceSimulatorEngine.CompareOp.GT, DiceSimulatorEngine.CompareOp.GTE) -> "above"
        else -> "custom"
    }
    ChoiceDropdown(
        value = rerollCode,
        choices = listOf("none" to "None", "ones_once" to "Reroll ones once", "below" to "Repeat below threshold", "above" to "Repeat at/above threshold", "custom" to "Custom (notation)")
    ) { code ->
        val replacement = when (code) {
            "none" -> null
            "ones_once" -> DiceSimulatorEngine.RerollRule(false, DiceSimulatorEngine.Condition(DiceSimulatorEngine.CompareOp.EQ, 1))
            "below" -> DiceSimulatorEngine.RerollRule(true, DiceSimulatorEngine.Condition(DiceSimulatorEngine.CompareOp.LT, 3))
            "above" -> DiceSimulatorEngine.RerollRule(true, DiceSimulatorEngine.Condition(DiceSimulatorEngine.CompareOp.GTE, target.maximumFace))
            else -> target.reroll
        }
        onExpressionChange(updateDiceTermAt(expression, selectedGroup) { it.copy(reroll = replacement) })
    }
    if (target.reroll != null && rerollCode in setOf("below", "above")) {
        OutlinedTextField(
            target.reroll.condition.target.toString(),
            { raw ->
                val target = raw.filter(Char::isDigit).toIntOrNull()?.coerceIn(target.minimumFace, target.maximumFace) ?: target.reroll.condition.target
                val op = if (rerollCode == "below") DiceSimulatorEngine.CompareOp.LT else DiceSimulatorEngine.CompareOp.GTE
                onExpressionChange(updateDiceTermAt(expression, selectedGroup) { it.copy(reroll = DiceSimulatorEngine.RerollRule(true, DiceSimulatorEngine.Condition(op, target))) })
            },
            label = { Text("Reroll threshold") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }

    Spacer(Modifier.height(6.dp))
    TwoChoiceToggle("Exploding dice", if (target.explode == null) "off" else "on", "off" to "Off", "on" to "On") { value ->
        val replacement = if (value == "on") DiceSimulatorEngine.ExplodeRule(null, true) else null
        onExpressionChange(updateDiceTermAt(expression, selectedGroup) { it.copy(explode = replacement) })
    }
    if (target.explode != null) {
        val explodeCode = if (target.explode.onMaximum) "max" else "threshold"
        ChoiceDropdown(explodeCode, listOf("max" to "Explode on maximum", "threshold" to "Explode at/above threshold")) { code ->
            val replacement = if (code == "max") DiceSimulatorEngine.ExplodeRule(null, true)
            else DiceSimulatorEngine.ExplodeRule(DiceSimulatorEngine.Condition(DiceSimulatorEngine.CompareOp.GTE, target.maximumFace), false)
            onExpressionChange(updateDiceTermAt(expression, selectedGroup) { it.copy(explode = replacement) })
        }
        if (!target.explode.onMaximum) {
            val currentThreshold = target.explode.condition?.target?.coerceIn(target.minimumFace + 1, target.maximumFace)
                ?: target.maximumFace
            ChoiceDropdown(
                value = currentThreshold.toString(),
                choices = ((target.minimumFace + 1)..target.maximumFace).map { threshold ->
                    threshold.toString() to "${threshold}+"
                }
            ) { selected ->
                val threshold = selected.toIntOrNull()?.coerceIn(target.minimumFace + 1, target.maximumFace)
                    ?: target.maximumFace
                onExpressionChange(
                    updateDiceTermAt(expression, selectedGroup) {
                        it.copy(
                            explode = DiceSimulatorEngine.ExplodeRule(
                                DiceSimulatorEngine.Condition(DiceSimulatorEngine.CompareOp.GTE, threshold),
                                false
                            )
                        )
                    }
                )
            }
            Text(
                "Explodes on $currentThreshold or higher. Values that would make every face explode are not offered.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    TwoChoiceToggle("Count successes", if (target.success == null) "off" else "on", "off" to "Off", "on" to "On") { value ->
        val replacement = if (value == "on") DiceSimulatorEngine.SuccessRule(DiceSimulatorEngine.Condition(DiceSimulatorEngine.CompareOp.GTE, minOf(5, target.maximumFace))) else null
        onExpressionChange(updateDiceTermAt(expression, selectedGroup) { it.copy(success = replacement) })
    }
    if (target.success != null) {
        OutlinedTextField(
            target.success.condition.target.toString(),
            { raw ->
                val target = raw.filter(Char::isDigit).toIntOrNull()?.coerceIn(target.minimumFace, target.maximumFace) ?: target.success.condition.target
                onExpressionChange(updateDiceTermAt(expression, selectedGroup) { it.copy(success = DiceSimulatorEngine.SuccessRule(DiceSimulatorEngine.Condition(DiceSimulatorEngine.CompareOp.GTE, target))) })
            },
            label = { Text("Success threshold (≥)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }

    Spacer(Modifier.height(6.dp))
    val modifierValue = parsed.terms.filterIsInstance<DiceSimulatorEngine.ConstantTerm>().sumOf { it.sign * it.value }
    OutlinedTextField(
        modifierValue.toString(),
        { raw ->
            val clean = raw.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }
            val value = clean.toIntOrNull()?.coerceIn(-9999, 9999) ?: 0
            onExpressionChange(updateModifier(expression, value))
        },
        label = { Text("Modifier") }, modifier = Modifier.fillMaxWidth(), singleLine = true
    )
    Text("Current notation: ${parsed.canonical}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChoiceDropdown(value: String, choices: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shown = choices.firstOrNull { it.first == value }?.second ?: value
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(shown, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Text("▼")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (code, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onSelected(code) })
            }
        }
    }
}

@Composable
private fun RandomSourceChooser(value: String, onSelected: (String) -> Unit) {
    Text("Random source", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth()) {
        if (value == "secure_random") Button(onClick = { onSelected("secure_random") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ Secure") }
        else OutlinedButton(onClick = { onSelected("secure_random") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("Secure") }
        if (value == "fixed_seed") Button(onClick = { onSelected("fixed_seed") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ Fixed seed") }
        else OutlinedButton(onClick = { onSelected("fixed_seed") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("Fixed seed") }
    }
}

@Composable
private fun AnimationChooser(value: String, onSelected: (String) -> Unit) {
    Text("Animation", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth()) {
        listOf("full" to "Full", "fast" to "Fast", "off" to "Off").forEach { (code, label) ->
            if (value == code) Button(onClick = { onSelected(code) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ $label") }
            else OutlinedButton(onClick = { onSelected(code) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text(label) }
        }
    }
}

@Composable
private fun TwoChoiceToggle(
    label: String, value: String, left: Pair<String, String>, right: Pair<String, String>, onSelected: (String) -> Unit
) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth()) {
        listOf(left, right).forEach { option ->
            if (value == option.first) Button(onClick = { onSelected(option.first) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ ${option.second}") }
            else OutlinedButton(onClick = { onSelected(option.first) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text(option.second) }
        }
    }
}

private fun simpleGroups(expression: String): List<DiceGroup>? = runCatching {
    val parsed = DiceSimulatorEngine.parse(expression)
    val diceTerms = parsed.terms.filterIsInstance<DiceSimulatorEngine.DiceTerm>()
    if (diceTerms.isEmpty() || diceTerms.any { it.sign < 0 || it.fate }) return@runCatching null
    if (parsed.terms.any { it !is DiceSimulatorEngine.DiceTerm && it !is DiceSimulatorEngine.ConstantTerm }) return@runCatching null
    diceTerms.map { DiceGroup(it.count, it.sides.toString()) }
}.getOrNull()

private fun isPlainDiceExpression(expression: String): Boolean = runCatching {
    val parsed = DiceSimulatorEngine.parse(expression)
    parsed.comparison == null && parsed.terms.all { term ->
        when (term) {
            is DiceSimulatorEngine.ConstantTerm -> false
            is DiceSimulatorEngine.DiceTerm -> term.sign > 0 && !term.fate && term.keepDrop == null && term.reroll == null && term.explode == null && term.success == null
        }
    }
}.getOrDefault(false)

private fun dieLabel(sides: String): String = "D${sides.toIntOrNull() ?: 6}"

private fun groupsToExpressionPreservingRules(expression: String, groups: List<DiceGroup>): String = runCatching {
    val parsed = DiceSimulatorEngine.parse(expression)
    val oldDice = parsed.terms.filterIsInstance<DiceSimulatorEngine.DiceTerm>()
    val rebuiltDice = groups.mapIndexed { index, group ->
        val count = group.count.coerceIn(1, DiceSimulatorEngine.MAX_DICE)
        val sides = group.sides.toIntOrNull()?.coerceIn(2, DiceSimulatorEngine.MAX_SIDES) ?: 6
        val old = oldDice.getOrNull(index)
        if (old == null) {
            DiceSimulatorEngine.DiceTerm(1, count, sides, false, null, null, null, null, "${count}d$sides")
        } else {
            old.copy(
                count = count,
                sides = sides,
                fate = false,
                keepDrop = old.keepDrop?.takeIf { it.count <= count },
                source = "${count}d$sides"
            )
        }
    }
    val constants = parsed.terms.filterIsInstance<DiceSimulatorEngine.ConstantTerm>()
    formatExpression(rebuiltDice + constants, parsed.comparison)
}.getOrElse {
    groups.joinToString("+") { group ->
        val count = group.count.coerceIn(1, DiceSimulatorEngine.MAX_DICE)
        val sides = group.sides.toIntOrNull()?.coerceIn(2, DiceSimulatorEngine.MAX_SIDES) ?: 6
        "${count}d$sides"
    }
}

private fun updateDiceTermAt(
    expression: String,
    diceGroupIndex: Int,
    transform: (DiceSimulatorEngine.DiceTerm) -> DiceSimulatorEngine.DiceTerm
): String = runCatching {
    val parsed = DiceSimulatorEngine.parse(expression)
    var seen = 0
    val terms = parsed.terms.map { term ->
        if (term is DiceSimulatorEngine.DiceTerm) {
            val current = seen++
            if (current == diceGroupIndex) transform(term) else term
        } else term
    }
    formatExpression(terms, parsed.comparison)
}.getOrDefault(expression)

private fun updateModifier(expression: String, modifier: Int): String = runCatching {
    val parsed = DiceSimulatorEngine.parse(expression)
    val diceTerms = parsed.terms.filterIsInstance<DiceSimulatorEngine.DiceTerm>()
    val terms = buildList<DiceSimulatorEngine.TermSpec> {
        addAll(diceTerms)
        if (modifier != 0) add(DiceSimulatorEngine.ConstantTerm(if (modifier < 0) -1 else 1, kotlin.math.abs(modifier), kotlin.math.abs(modifier).toString()))
    }
    formatExpression(terms, parsed.comparison)
}.getOrDefault(expression)

private fun formatExpression(terms: List<DiceSimulatorEngine.TermSpec>, comparison: DiceSimulatorEngine.Condition?): String {
    val body = terms.mapIndexed { index, term ->
        val signText = when {
            index == 0 && term.sign < 0 -> "-"
            index == 0 -> ""
            term.sign < 0 -> "-"
            else -> "+"
        }
        signText + when (term) {
            is DiceSimulatorEngine.ConstantTerm -> term.value.toString()
            is DiceSimulatorEngine.DiceTerm -> buildString {
                append(term.count).append('d').append(if (term.fate) "F" else term.sides)
                term.keepDrop?.let { append(it.kind.code).append(it.count) }
                term.reroll?.let {
                    append(if (it.repeat) "rr" else "r")
                    if (it.condition.op != DiceSimulatorEngine.CompareOp.EQ) append(it.condition.op.symbol)
                    append(it.condition.target)
                }
                term.explode?.let {
                    append('!')
                    if (!it.onMaximum) append(requireNotNull(it.condition))
                }
                term.success?.let { append("cs").append(it.condition) }
            }
        }
    }.joinToString("")
    return body + (comparison?.toString() ?: "")
}

private fun normalizeVertices(vertices: List<V3>): List<V3> {
    val maxRadius = vertices.maxOfOrNull { kotlin.math.sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }?.takeIf { it > 0.0 } ?: 1.0
    return vertices.map { V3(it.x / maxRadius, it.y / maxRadius, it.z / maxRadius) }
}

private fun parseLastVisualOutcomes(json: String): List<VisualOutcome> {
    if (json.isBlank()) return emptyList()
    return try {
        val round = JSONObject(json)
        val drawsByDie = mutableMapOf<Pair<Int, Int>, MutableList<VisualDraw>>()
        val primitive = round.optJSONArray("primitive_draws") ?: JSONArray()
        for (i in 0 until primitive.length()) {
            val draw = primitive.getJSONObject(i)
            val key = draw.optInt("term_index", 1) to draw.optInt("die_index", 1)
            drawsByDie.getOrPut(key) { mutableListOf() } += VisualDraw(
                kind = draw.optString("kind", "initial"),
                value = draw.optInt("value", 1)
            )
        }
        val result = mutableListOf<VisualOutcome>()
        val terms = round.getJSONArray("terms")
        for (i in 0 until terms.length()) {
            val dice = terms.getJSONObject(i).getJSONArray("dice")
            for (j in 0 until dice.length()) {
                val die = dice.getJSONObject(j)
                val termIndex = die.optInt("term_index", i + 1)
                val dieIndex = die.optInt("die_index", j + 1)
                result += VisualOutcome(
                    termIndex = termIndex,
                    dieIndex = dieIndex,
                    sides = die.optString("sides", "6"),
                    initialValue = die.optInt("initial_value", 1),
                    acceptedBaseValue = die.optInt("accepted_base_value", 1),
                    retained = die.optBoolean("retained", true),
                    explosionValues = jsonInts(die.optJSONArray("explosion_values")),
                    rerolledValues = jsonInts(die.optJSONArray("rerolled_values")),
                    draws = drawsByDie[termIndex to dieIndex].orEmpty()
                )
            }
        }
        result
    } catch (_: Exception) { emptyList() }
}

private fun coinOutcomes(historyJson: String, auditJson: String, lastToss: String): List<Boolean> {
    fun parseArray(array: JSONArray): List<Boolean> =
        (0 until array.length()).map { array.optString(it).equals("Heads", ignoreCase = true) }
    if (historyJson.isNotBlank()) {
        runCatching { return parseArray(JSONArray(historyJson)) }
    }
    if (auditJson.isNotBlank()) {
        runCatching { return parseArray(JSONObject(auditJson).getJSONArray("tosses")) }
    }
    return if (lastToss.isBlank()) emptyList() else listOf(lastToss.equals("Heads", ignoreCase = true))
}

private fun jsonInts(array: JSONArray?): List<Int> = if (array == null) emptyList() else (0 until array.length()).map { array.optInt(it) }
private fun readBoolean(settings: Map<String, String>, key: String, default: Boolean): Boolean = when ((settings[key] ?: settings["input_$key"])?.trim()?.lowercase()) {
    "true", "1", "yes", "y" -> true
    "false", "0", "no", "n" -> false
    else -> default
}
private fun defaultFace(sidesLabel: String): Int = if (sidesLabel.equals("F", true)) 0 else 1
private fun animatedFace(sidesLabel: String, numericSides: Int, index: Int, tick: Int): Int {
    if (sidesLabel.equals("F", true)) return ((tick * 5 + index * 7) % 3) - 1
    return ((tick * 37 + index * 19 + 11) % numericSides.coerceAtLeast(2)) + 1
}
private fun fateLabel(value: Int): String = when { value > 0 -> "+"; value < 0 -> "−"; else -> "0" }
private data class V3(val x: Double, val y: Double, val z: Double)
private data class Mesh(val vertices: List<V3>, val edges: List<Pair<Int, Int>>)
private data class Projected(val point: Offset, val depth: Float)

private fun meshForSides(sides: Int): Mesh = when (sides) {
    4 -> tetrahedron()
    6 -> cube()
    8 -> octahedron()
    10 -> d10Mesh()
    12 -> dodecahedron()
    20 -> icosahedron()
    else -> genericDie(sides)
}

private fun tetrahedron(): Mesh {
    val v = listOf(V3(1.0, 1.0, 1.0), V3(-1.0, -1.0, 1.0), V3(-1.0, 1.0, -1.0), V3(1.0, -1.0, -1.0))
    return Mesh(v, allNearestEdges(v))
}

private fun cube(): Mesh {
    val v = mutableListOf<V3>()
    listOf(-1.0, 1.0).forEach { x -> listOf(-1.0, 1.0).forEach { y -> listOf(-1.0, 1.0).forEach { z -> v += V3(x, y, z) } } }
    return Mesh(v, allNearestEdges(v))
}

private fun octahedron(): Mesh {
    val v = listOf(V3(1.0, 0.0, 0.0), V3(-1.0, 0.0, 0.0), V3(0.0, 1.0, 0.0), V3(0.0, -1.0, 0.0), V3(0.0, 0.0, 1.0), V3(0.0, 0.0, -1.0))
    return Mesh(v, allNearestEdges(v))
}

private fun d10Mesh(): Mesh {
    val v = mutableListOf(V3(0.0, 0.0, 1.25), V3(0.0, 0.0, -1.25))
    repeat(10) { i ->
        val a = i * 2.0 * PI / 10.0
        val z = if (i % 2 == 0) 0.24 else -0.24
        v += V3(cos(a), sin(a), z)
    }
    val edges = mutableListOf<Pair<Int, Int>>()
    repeat(10) { i ->
        edges += (2 + i) to (2 + (i + 1) % 10)
        if (i % 2 == 0) edges += 0 to (2 + i) else edges += 1 to (2 + i)
    }
    return Mesh(v, edges)
}

private fun dodecahedron(): Mesh {
    val phi = (1.0 + kotlin.math.sqrt(5.0)) / 2.0
    val inv = 1.0 / phi
    val v = mutableListOf<V3>()
    listOf(-1.0, 1.0).forEach { x -> listOf(-1.0, 1.0).forEach { y -> listOf(-1.0, 1.0).forEach { z -> v += V3(x, y, z) } } }
    listOf(-1.0, 1.0).forEach { a -> listOf(-1.0, 1.0).forEach { b ->
        v += V3(0.0, a * inv, b * phi)
        v += V3(a * inv, b * phi, 0.0)
        v += V3(b * phi, 0.0, a * inv)
    } }
    return Mesh(v, allNearestEdges(v))
}

private fun icosahedron(): Mesh {
    val phi = (1.0 + kotlin.math.sqrt(5.0)) / 2.0
    val v = mutableListOf<V3>()
    listOf(-1.0, 1.0).forEach { a -> listOf(-phi, phi).forEach { b ->
        v += V3(0.0, a, b)
        v += V3(a, b, 0.0)
        v += V3(b, 0.0, a)
    } }
    return Mesh(v, allNearestEdges(v))
}

private fun genericDie(sides: Int): Mesh {
    val n = sides.coerceIn(3, 12)
    val v = mutableListOf<V3>(V3(0.0, 0.0, 1.0), V3(0.0, 0.0, -1.0))
    repeat(n) { i ->
        val a = i * 2.0 * PI / n
        v += V3(cos(a), sin(a), 0.0)
    }
    val edges = mutableListOf<Pair<Int, Int>>()
    repeat(n) { i ->
        val p = 2 + i
        val next = 2 + (i + 1) % n
        edges += p to next
        edges += 0 to p
        edges += 1 to p
    }
    return Mesh(v, edges)
}

private fun allNearestEdges(vertices: List<V3>): List<Pair<Int, Int>> {
    if (vertices.size < 2) return emptyList()
    var min = Double.MAX_VALUE
    for (i in vertices.indices) for (j in i + 1 until vertices.size) {
        val d = squaredDistance(vertices[i], vertices[j])
        if (d > 1e-8 && d < min) min = d
    }
    val tolerance = min * 1.08
    val edges = mutableListOf<Pair<Int, Int>>()
    for (i in vertices.indices) for (j in i + 1 until vertices.size) {
        if (squaredDistance(vertices[i], vertices[j]) <= tolerance) edges += i to j
    }
    return edges
}

private fun squaredDistance(a: V3, b: V3): Double =
    (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)

private fun projectMesh(
    vertices: List<V3>,
    rx: Double,
    ry: Double,
    rz: Double,
    centre: Offset,
    scale: Float
): List<Projected> = vertices.map { original ->
    var x = original.x
    var y = original.y
    var z = original.z

    val y1 = y * cos(rx) - z * sin(rx)
    val z1 = y * sin(rx) + z * cos(rx)
    y = y1; z = z1

    val x2 = x * cos(ry) + z * sin(ry)
    val z2 = -x * sin(ry) + z * cos(ry)
    x = x2; z = z2

    val x3 = x * cos(rz) - y * sin(rz)
    val y3 = x * sin(rz) + y * cos(rz)
    x = x3; y = y3

    val perspective = 1.0 / (1.0 + (z + 2.8) * 0.10)
    Projected(
        Offset(centre.x + (x * perspective * scale).toFloat(), centre.y + (y * perspective * scale).toFloat()),
        z.toFloat().coerceIn(-1f, 1f)
    )
}
