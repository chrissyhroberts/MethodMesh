package com.example.methodmesh.modules.chance

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class ChanceStage { Configure, Animating, Choosing, Settled, Committed }

object CardDrawCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100CardDrawMethod.ID
    override val title = "Draw cards"
    override val description = "Deal one or more cards without replacement from a shared standard deck."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var drawCount by rememberSaveable { mutableStateOf(context.chanceSetting("draw_count", "1")) }
        var playerCountText by rememberSaveable { mutableStateOf(context.chanceSetting("player_count", "1")) }
        var playerMode by rememberSaveable { mutableStateOf(context.chanceSetting("player_mode", "take_turns")) }
        var includeJokers by rememberSaveable { mutableStateOf(context.chanceBoolSetting("include_jokers", false)) }
        var rngMode by rememberSaveable { mutableStateOf(context.chanceSetting("rng_mode", "secure_random")) }
        var seed by rememberSaveable { mutableStateOf(context.chanceSetting("seed", "")) }
        var animationMode by rememberSaveable { mutableStateOf(context.chanceSetting("animation_mode", "full")) }
        var savedValuesJson by rememberSaveable { mutableStateOf("") }
        var stage by rememberSaveable { mutableStateOf(ChanceStage.Configure) }
        var animationFrame by rememberSaveable { mutableStateOf(0) }
        var activePlayer by rememberSaveable { mutableStateOf(0) }
        var waitingPass by rememberSaveable { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        val saved = remember(savedValuesJson) { decodeStringMap(savedValuesJson) }
        val hands = remember(savedValuesJson) { jsonPlayerHands(saved[CardDrawFields.PLAYER_HANDS_JSON].orEmpty()) }
        val execution = remember(savedValuesJson) { if (savedValuesJson.isBlank()) null else cardExecution(context, saved) }
        val maxDeck = if (includeJokers) 54 else 52
        val players = playerCountText.toIntOrNull()?.coerceIn(1, 20) ?: 1
        val effectiveMode = if (players > 4) "take_turns" else playerMode
        val maxPerPlayer = (maxDeck / players).coerceAtLeast(1)
        val cardsPerPlayer = drawCount.toIntOrNull()?.coerceIn(1, maxPerPlayer) ?: 1

        LaunchedEffect(drawCount, playerCountText, playerMode, includeJokers, rngMode, seed, animationMode) {
            context.onSettingsChanged(
                mapOf(
                    "draw_count" to drawCount,
                    "player_count" to playerCountText,
                    "player_mode" to effectiveMode,
                    "include_jokers" to includeJokers.toString(),
                    "rng_mode" to rngMode,
                    "seed" to seed,
                    "animation_mode" to animationMode
                )
            )
        }

        fun animateDeal() {
            if (animationMode == "off") {
                animationFrame = 100
                stage = ChanceStage.Settled
            } else {
                animationFrame = 0
                stage = ChanceStage.Animating
            }
        }

        LaunchedEffect(stage, activePlayer, savedValuesJson, animationMode) {
            if (stage != ChanceStage.Animating) return@LaunchedEffect
            val cardCount = if (effectiveMode == "around_table") cardsPerPlayer else hands.getOrNull(activePlayer)?.size ?: cardsPerPlayer
            val perCard = if (animationMode == "full") 7 else 4
            val frameMs = if (animationMode == "full") 55L else 35L
            val frames = cardCount * perCard + 8
            var frame = animationFrame.coerceIn(0, frames)
            while (frame < frames && stage == ChanceStage.Animating) {
                frame += 1
                animationFrame = frame
                delay(frameMs)
            }
            if (stage == ChanceStage.Animating) {
                animationFrame = 100
                stage = ChanceStage.Settled
            }
        }

        fun draw() {
            val settings = mapOf(
                "draw_count" to cardsPerPlayer.toString(),
                "player_count" to players.toString(),
                "player_mode" to effectiveMode,
                "include_jokers" to includeJokers.toString(),
                "rng_mode" to rngMode,
                "seed" to seed,
                "animation_mode" to animationMode
            )
            val request = As100CardDrawMethod.request(
                As100CardDrawMethod.ID,
                context.request.invocationContext.asMap(As100CardDrawMethod.ID) + context.action.settings + settings,
                emptyList(), emptyList()
            )
            val values = As100CardDrawMethod.generate(settings)
            savedValuesJson = encodeStringMap(values)
            val result = As100CardDrawMethod.result(request, values, context.request.invocationContext)
            if (values[CardDrawFields.STATUS] != "succeeded") {
                stage = ChanceStage.Configure
                return
            }
            if (context.submitsImmediately) {
                onConfirmed(result)
                return
            }
            activePlayer = 0
            waitingPass = false
            animateDeal()
        }

        fun revealNextPlayer() {
            waitingPass = false
            activePlayer = (activePlayer + 1).coerceAtMost((hands.size - 1).coerceAtLeast(0))
            animateDeal()
        }

        fun commitDeal() {
            val result = execution ?: return
            if (context.submitsImmediately) onConfirmed(result) else stage = ChanceStage.Committed
        }

        val presetSettingIds = listOf("draw_count", "player_count", "player_mode", "include_jokers", "rng_mode", "seed", "animation_mode")
        val autoRunFixedPreset = context.chanceCanAutoRunFixedPreset(presetSettingIds)
        LaunchedEffect(context.submitsImmediately, autoRunFixedPreset, context.action.settings) {
            if ((context.submitsImmediately || autoRunFixedPreset) && !launched) {
                launched = true
                draw()
            }
        }

        if (stage == ChanceStage.Animating || stage == ChanceStage.Settled || stage == ChanceStage.Committed) {
            CardDrawExperience(
                hands = hands,
                frame = animationFrame,
                settled = stage != ChanceStage.Animating,
                committed = stage == ChanceStage.Committed,
                playerMode = effectiveMode,
                activePlayer = activePlayer,
                waitingPass = waitingPass,
                primaryResult = saved[CardDrawFields.RESULT].orEmpty(),
                auditJson = chanceFullJson(execution),
                onCommit = { commitDeal() },
                onNextPlayer = { waitingPass = true },
                onRevealNext = { revealNextPlayer() },
                onDrawAgain = { draw() },
                onDone = { execution?.let(onConfirmed) },
                onNewRun = { stage = ChanceStage.Configure; savedValuesJson = ""; activePlayer = 0; waitingPass = false },
                onCancel = onCancel
            )
            return
        }

        ChanceConfigSurface(title, context, onBack, onCancel) {
            if (context.settingShouldBeShown("player_count")) {
                Text("Players", style = MaterialTheme.typography.bodySmall)
                CompactCountControl(players, 1, 20) { playerCountText = it.toString() }
            }
            if (players > 1 && context.settingShouldBeShown("player_mode")) {
                Text("How to show the deal", style = MaterialTheme.typography.bodySmall)
                if (players <= 4) {
                    ChanceChoiceDropdown(
                        effectiveMode,
                        listOf("take_turns" to "Take turns — private hands", "around_table" to "Around the table — visible hands")
                    ) { playerMode = it }
                } else {
                    Text("Take turns is used for more than four players.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (context.settingShouldBeShown("draw_count")) {
                Text(if (players == 1) "Cards to draw" else "Cards per player", style = MaterialTheme.typography.bodySmall)
                CompactCountControl(cardsPerPlayer, 1, maxPerPlayer) { drawCount = it.toString() }
                if (players > 1) Text("${players * cardsPerPlayer} cards dealt from one shared deck.", style = MaterialTheme.typography.bodySmall)
            }
            if (context.settingShouldBeShown("include_jokers")) ChanceBoolToggle("Include jokers", includeJokers) { includeJokers = it }
            if (context.settingShouldBeShown("rng_mode")) ChanceRngChooser(rngMode) { rngMode = it }
            if (rngMode == "fixed_seed" && context.settingShouldBeShown("seed")) {
                OutlinedTextField(seed, { seed = it }, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (context.settingShouldBeShown("animation_mode")) ChanceAnimationChooser(animationMode) { animationMode = it }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { draw() }, modifier = Modifier.fillMaxWidth()) { Text(if (players == 1 && cardsPerPlayer == 1) "DRAW CARD" else "DEAL") }
            saved[CardDrawFields.ERROR]?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

object PickOneCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PickOneMethod.ID
    override val title = "Pick one"
    override val description = "Shuffle concealed values, then choose a face-down card yourself."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var setType by rememberSaveable { mutableStateOf(context.chanceSetting("set_type", "numbers")) }
        var itemCount by rememberSaveable { mutableStateOf(context.chanceSetting("item_count", "6")) }
        var customItems by rememberSaveable { mutableStateOf(context.chanceSetting("custom_items", "")) }
        var revealRemaining by rememberSaveable { mutableStateOf(context.chanceBoolSetting("reveal_remaining", true)) }
        var rngMode by rememberSaveable { mutableStateOf(context.chanceSetting("rng_mode", "secure_random")) }
        var seed by rememberSaveable { mutableStateOf(context.chanceSetting("seed", "")) }
        var animationMode by rememberSaveable { mutableStateOf(context.chanceSetting("animation_mode", "full")) }
        var preparedJson by rememberSaveable { mutableStateOf("") }
        var savedValuesJson by rememberSaveable { mutableStateOf("") }
        var selectedPosition by rememberSaveable { mutableStateOf(0) }
        var revealAll by rememberSaveable { mutableStateOf(false) }
        var flipFrame by rememberSaveable { mutableStateOf(0) }
        var stage by rememberSaveable { mutableStateOf(ChanceStage.Configure) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        val saved = remember(savedValuesJson) { decodeStringMap(savedValuesJson) }
        val prepared = remember(preparedJson) {
            runCatching { if (preparedJson.isBlank()) null else ChanceSelectionEngine.parsePickPreparation(preparedJson) }.getOrNull()
        }
        val execution = remember(savedValuesJson) { if (savedValuesJson.isBlank()) null else pickExecution(context, saved) }
        val requestedCount = itemCount.toIntOrNull()?.coerceIn(2, 12) ?: 6
        val maxForSet = when (setType) { "suits" -> 4; "custom" -> 12; else -> 12 }
        val count = requestedCount.coerceAtMost(maxForSet)

        LaunchedEffect(setType, itemCount, customItems, revealRemaining, rngMode, seed, animationMode) {
            context.onSettingsChanged(
                mapOf(
                    "set_type" to setType,
                    "item_count" to itemCount,
                    "custom_items" to customItems,
                    "reveal_remaining" to revealRemaining.toString(),
                    "rng_mode" to rngMode,
                    "seed" to seed,
                    "animation_mode" to animationMode
                )
            )
        }

        fun prepareNative() {
            try {
                val prep = ChanceSelectionEngine.preparePick(setType, count, customItems, rngMode, seed)
                preparedJson = prep.toJson()
                selectedPosition = 0
                savedValuesJson = ""
                revealAll = false
                flipFrame = 0
                stage = ChanceStage.Choosing
            } catch (e: Exception) {
                savedValuesJson = encodeStringMap(mapOf(PickOneFields.STATUS to "failed", PickOneFields.ERROR to (e.message ?: "Invalid choices.")))
                stage = ChanceStage.Configure
            }
        }

        fun resolvePick(position: Int, preparedPick: String?, nonInteractiveExternal: Boolean = false) {
            try {
                val settings = mapOf(
                    "set_type" to setType,
                    "item_count" to count.toString(),
                    "custom_items" to customItems,
                    "selected_position" to position.toString(),
                    "reveal_remaining" to revealRemaining.toString(),
                    "rng_mode" to rngMode,
                    "seed" to seed,
                    "animation_mode" to animationMode
                ) + (preparedPick?.let { mapOf("prepared_pick_json" to it) } ?: emptyMap())
                val request = As100PickOneMethod.request(
                    As100PickOneMethod.ID,
                    context.request.invocationContext.asMap(As100PickOneMethod.ID) + context.action.settings + settings,
                    emptyList(), emptyList()
                )
                val values = As100PickOneMethod.generate(settings)
                savedValuesJson = encodeStringMap(values)
                val result = As100PickOneMethod.result(request, values, context.request.invocationContext)
                if (values[PickOneFields.STATUS] != "succeeded") {
                    if (context.submitsImmediately) onConfirmed(result) else stage = ChanceStage.Configure
                    return
                }
                if (context.submitsImmediately && nonInteractiveExternal) {
                    onConfirmed(result)
                    return
                }
                selectedPosition = position
                flipFrame = 0
                stage = if (animationMode == "off") ChanceStage.Settled else ChanceStage.Animating
                if (animationMode == "off") flipFrame = 100
            } catch (e: Exception) {
                savedValuesJson = encodeStringMap(mapOf(PickOneFields.STATUS to "failed", PickOneFields.ERROR to (e.message ?: "Pick one failed.")))
                stage = ChanceStage.Configure
            }
        }

        LaunchedEffect(stage, selectedPosition, savedValuesJson, animationMode) {
            if (stage != ChanceStage.Animating) return@LaunchedEffect
            val frameMs = if (animationMode == "full") 35L else 22L
            val frames = if (animationMode == "full") 22 else 12
            var frame = flipFrame.coerceIn(0, frames)
            while (frame < frames && stage == ChanceStage.Animating) {
                frame += 1
                flipFrame = frame
                delay(frameMs)
            }
            if (stage == ChanceStage.Animating) {
                flipFrame = 100
                stage = ChanceStage.Settled
            }
        }

        fun commitPick() {
            val result = execution ?: return
            if (context.submitsImmediately) onConfirmed(result) else stage = ChanceStage.Committed
        }

        val presetSettingIds = listOf("set_type", "item_count", "custom_items", "reveal_remaining", "rng_mode", "seed", "animation_mode")
        val autoPrepareFixedPreset = context.chanceCanAutoRunFixedPreset(presetSettingIds)
        LaunchedEffect(context.submitsImmediately, autoPrepareFixedPreset, context.action.settings) {
            if (!launched && (context.submitsImmediately || autoPrepareFixedPreset)) {
                launched = true
                val suppliedPosition = context.action.settings["selected_position"]?.toIntOrNull()
                    ?: context.action.settings["input_selected_position"]?.toIntOrNull()
                    ?: context.request.settings["selected_position"]?.toIntOrNull()
                    ?: context.request.settings["input_selected_position"]?.toIntOrNull()
                    ?: 0
                if (context.submitsImmediately && suppliedPosition > 0) {
                    resolvePick(suppliedPosition, null, nonInteractiveExternal = true)
                } else {
                    // Interactive external/protocol launches keep Pick one's human-choice
                    // semantics: MethodMesh shuffles the concealed arrangement; the user taps.
                    prepareNative()
                }
            }
        }

        if (stage == ChanceStage.Choosing || stage == ChanceStage.Animating || stage == ChanceStage.Settled || stage == ChanceStage.Committed) {
            val arrangement = prepared?.arrangement ?: jsonStringList(saved[PickOneFields.ARRANGEMENT_JSON].orEmpty())
            PickOneExperience(
                setType = prepared?.setType ?: saved[PickOneFields.SET_TYPE].orEmpty().ifBlank { setType },
                arrangement = arrangement,
                selectedPosition = selectedPosition,
                flipFrame = flipFrame,
                settled = stage == ChanceStage.Settled || stage == ChanceStage.Committed,
                committed = stage == ChanceStage.Committed,
                revealAll = revealAll,
                allowRevealAll = revealRemaining,
                result = saved[PickOneFields.RESULT].orEmpty(),
                auditJson = chanceFullJson(execution),
                onPick = { if (stage == ChanceStage.Choosing) resolvePick(it, preparedJson) },
                onRevealAll = { revealAll = true },
                onPickAgain = { prepareNative() },
                onCommit = { commitPick() },
                onDone = { execution?.let(onConfirmed) },
                onNewRun = { stage = ChanceStage.Configure; preparedJson = ""; savedValuesJson = ""; selectedPosition = 0; revealAll = false },
                onCancel = onCancel
            )
            return
        }

        ChanceConfigSurface(title, context, onBack, onCancel) {
            if (context.settingShouldBeShown("set_type")) {
                ChanceChoiceDropdown(
                    setType,
                    listOf(
                        "numbers" to "Numbers",
                        "colours" to "Colours",
                        "suits" to "Card suits",
                        "abstract" to "Wiggly / abstract",
                        "classic_cards" to "Classic playing cards",
                        "custom" to "Custom text"
                    )
                ) { setType = it; if (it == "suits" && count > 4) itemCount = "4" }
            }
            if (setType == "custom" && context.settingShouldBeShown("custom_items")) {
                OutlinedTextField(
                    customItems,
                    { customItems = it },
                    label = { Text("Choices") },
                    supportingText = { Text("One choice per line, 2–12 choices.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10
                )
            } else if (context.settingShouldBeShown("item_count")) {
                Text("Cards", style = MaterialTheme.typography.bodySmall)
                CompactCountControl(count, 2, maxForSet) { itemCount = it.toString() }
            }
            if (context.settingShouldBeShown("reveal_remaining")) ChanceBoolToggle("Allow reveal remaining", revealRemaining) { revealRemaining = it }
            if (context.settingShouldBeShown("rng_mode")) ChanceRngChooser(rngMode) { rngMode = it }
            if (rngMode == "fixed_seed" && context.settingShouldBeShown("seed")) {
                OutlinedTextField(seed, { seed = it }, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (context.settingShouldBeShown("animation_mode")) ChanceAnimationChooser(animationMode) { animationMode = it }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { prepareNative() }, modifier = Modifier.fillMaxWidth()) { Text("LAY OUT CARDS") }
            saved[PickOneFields.ERROR]?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

object SpinnerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SpinnerMethod.ID
    override val title = "Spinner"
    override val description = "Spin an equal-segment wheel and reveal a random choice."
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var items by rememberSaveable { mutableStateOf(context.chanceSetting("items", "Yes\nNo")) }
        var rngMode by rememberSaveable { mutableStateOf(context.chanceSetting("rng_mode", "secure_random")) }
        var seed by rememberSaveable { mutableStateOf(context.chanceSetting("seed", "")) }
        var animationMode by rememberSaveable { mutableStateOf(context.chanceSetting("animation_mode", "full")) }
        WheelCapabilityScreen(
            title, capabilityId, context, onBack, onConfirmed, onCancel,
            items, { items = it }, rngMode, { rngMode = it }, seed, { seed = it }, animationMode, { animationMode = it }, weighted = false
        )
    }
}

object WeightedChoiceCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100WeightedChoiceMethod.ID
    override val title = "Weighted choice"
    override val description = "Choose from labelled options in proportion to positive numeric weights."
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var items by rememberSaveable { mutableStateOf(context.chanceSetting("weighted_items", "Option A:1\nOption B:1")) }
        var rngMode by rememberSaveable { mutableStateOf(context.chanceSetting("rng_mode", "secure_random")) }
        var seed by rememberSaveable { mutableStateOf(context.chanceSetting("seed", "")) }
        var animationMode by rememberSaveable { mutableStateOf(context.chanceSetting("animation_mode", "full")) }
        WheelCapabilityScreen(
            title, capabilityId, context, onBack, onConfirmed, onCancel,
            items, { items = it }, rngMode, { rngMode = it }, seed, { seed = it }, animationMode, { animationMode = it }, weighted = true
        )
    }
}

@Composable
private fun WheelCapabilityScreen(
    title: String,
    capabilityId: String,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit,
    itemsText: String,
    onItemsText: (String) -> Unit,
    rngMode: String,
    onRngMode: (String) -> Unit,
    seed: String,
    onSeed: (String) -> Unit,
    animationMode: String,
    onAnimationMode: (String) -> Unit,
    weighted: Boolean
) {
    var savedValuesJson by rememberSaveable { mutableStateOf("") }
    var stage by rememberSaveable { mutableStateOf(ChanceStage.Configure) }
    var frame by rememberSaveable { mutableStateOf(0) }
    var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    val saved = remember(savedValuesJson) { decodeStringMap(savedValuesJson) }
    val execution = remember(savedValuesJson) {
        if (savedValuesJson.isBlank()) null else if (weighted) weightedExecution(context, saved) else spinnerExecution(context, saved)
    }
    val labels = remember(savedValuesJson, itemsText) {
        if (weighted) weightedLabels(saved[WeightedChoiceFields.OPTIONS_JSON].orEmpty(), itemsText)
        else jsonStringList(saved[SpinnerFields.ITEMS_JSON].orEmpty()).ifEmpty { ChanceSelectionEngine.parseItems(itemsText) }
    }
    val weights = remember(savedValuesJson, itemsText) {
        if (weighted) weightedWeights(saved[WeightedChoiceFields.OPTIONS_JSON].orEmpty(), itemsText) else List(labels.size) { 1.0 }
    }
    val selectedIndex = if (weighted) saved[WeightedChoiceFields.SELECTED_INDEX]?.toIntOrNull()?.minus(1) ?: 0
        else saved[SpinnerFields.SELECTED_INDEX]?.toIntOrNull()?.minus(1) ?: 0
    val resultText = if (weighted) saved[WeightedChoiceFields.RESULT].orEmpty() else saved[SpinnerFields.RESULT].orEmpty()
    val auditJson = chanceFullJson(execution)

    LaunchedEffect(itemsText, rngMode, seed, animationMode) {
        context.onSettingsChanged(
            mapOf(
                (if (weighted) "weighted_items" else "items") to itemsText,
                "rng_mode" to rngMode,
                "seed" to seed,
                "animation_mode" to animationMode
            )
        )
    }

    fun spin() {
        try {
            val settings = mapOf(
                (if (weighted) "weighted_items" else "items") to itemsText,
                "rng_mode" to rngMode,
                "seed" to seed,
                "animation_mode" to animationMode
            )
            val values: Map<String, String>
            val result: ExecutionResult
            if (weighted) {
                val request = As100WeightedChoiceMethod.request(
                    As100WeightedChoiceMethod.ID,
                    context.request.invocationContext.asMap(As100WeightedChoiceMethod.ID) + context.action.settings + settings,
                    emptyList(), emptyList()
                )
                values = As100WeightedChoiceMethod.generate(settings)
                result = As100WeightedChoiceMethod.result(request, values, context.request.invocationContext)
            } else {
                val request = As100SpinnerMethod.request(
                    As100SpinnerMethod.ID,
                    context.request.invocationContext.asMap(As100SpinnerMethod.ID) + context.action.settings + settings,
                    emptyList(), emptyList()
                )
                values = As100SpinnerMethod.generate(settings)
                result = As100SpinnerMethod.result(request, values, context.request.invocationContext)
            }
            savedValuesJson = encodeStringMap(values)
            val status = values[if (weighted) WeightedChoiceFields.STATUS else SpinnerFields.STATUS]
            if (status != "succeeded") {
                if (context.submitsImmediately) onConfirmed(result) else stage = ChanceStage.Configure
                return
            }
            if (context.submitsImmediately) {
                onConfirmed(result)
                return
            }
            frame = if (animationMode == "off") 100 else 0
            stage = if (animationMode == "off") ChanceStage.Settled else ChanceStage.Animating
        } catch (e: Exception) {
            val statusField = if (weighted) WeightedChoiceFields.STATUS else SpinnerFields.STATUS
            val errorField = if (weighted) WeightedChoiceFields.ERROR else SpinnerFields.ERROR
            savedValuesJson = encodeStringMap(mapOf(statusField to "failed", errorField to (e.message ?: "Chance wheel failed.")))
            stage = ChanceStage.Configure
        }
    }

    LaunchedEffect(stage, savedValuesJson, animationMode) {
        if (stage != ChanceStage.Animating) return@LaunchedEffect
        val frames = if (animationMode == "full") 62 else 32
        val frameMs = if (animationMode == "full") 32L else 25L
        var current = frame.coerceIn(0, frames)
        while (current < frames && stage == ChanceStage.Animating) {
            current += 1
            frame = current
            delay(frameMs)
        }
        if (stage == ChanceStage.Animating) {
            frame = 100
            stage = ChanceStage.Settled
        }
    }

    fun commitWheel() {
        val result = execution ?: return
        if (context.submitsImmediately) onConfirmed(result) else stage = ChanceStage.Committed
    }

    val presetSettingIds = listOf(if (weighted) "weighted_items" else "items", "rng_mode", "seed", "animation_mode")
    val autoRunFixedPreset = context.chanceCanAutoRunFixedPreset(presetSettingIds)
    LaunchedEffect(context.submitsImmediately, autoRunFixedPreset, context.action.settings) {
        if ((context.submitsImmediately || autoRunFixedPreset) && !launched) {
            launched = true
            spin()
        }
    }

    if (stage == ChanceStage.Animating || stage == ChanceStage.Settled || stage == ChanceStage.Committed) {
        ChanceWheelExperience(
            labels = labels,
            weights = weights,
            selectedIndex = selectedIndex,
            frame = frame,
            settled = stage != ChanceStage.Animating,
            committed = stage == ChanceStage.Committed,
            weighted = weighted,
            result = resultText,
            auditJson = auditJson,
            onCommit = { commitWheel() },
            onSpinAgain = { spin() },
            onDone = { execution?.let(onConfirmed) },
            onNewRun = { stage = ChanceStage.Configure; savedValuesJson = "" },
            onCancel = onCancel
        )
        return
    }

    val errorField = if (weighted) WeightedChoiceFields.ERROR else SpinnerFields.ERROR
    ChanceConfigSurface(title, context, onBack, onCancel) {
        if (context.settingShouldBeShown(if (weighted) "weighted_items" else "items")) {
            OutlinedTextField(
                itemsText,
                onItemsText,
                label = { Text(if (weighted) "Choices and weights" else "Spinner labels") },
                supportingText = {
                    Text(if (weighted) "One per line as Label:weight. Weights can be any positive numbers." else "One label per line. 2–60 segments.")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10
            )
        }
        if (context.settingShouldBeShown("rng_mode")) ChanceRngChooser(rngMode, onRngMode)
        if (rngMode == "fixed_seed" && context.settingShouldBeShown("seed")) {
            OutlinedTextField(seed, onSeed, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (context.settingShouldBeShown("animation_mode")) ChanceAnimationChooser(animationMode, onAnimationMode)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { spin() }, modifier = Modifier.fillMaxWidth()) { Text(if (weighted) "CHOOSE" else "SPIN") }
        saved[errorField]?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CardDrawExperience(
    hands: List<List<String>>,
    frame: Int,
    settled: Boolean,
    committed: Boolean,
    playerMode: String,
    activePlayer: Int,
    waitingPass: Boolean,
    primaryResult: String,
    auditJson: String,
    onCommit: () -> Unit,
    onNextPlayer: () -> Unit,
    onRevealNext: () -> Unit,
    onDrawAgain: () -> Unit,
    onDone: () -> Unit,
    onNewRun: () -> Unit,
    onCancel: () -> Unit
) {
    val safeHands = hands.ifEmpty { listOf(emptyList()) }
    val aroundTable = playerMode == "around_table" && safeHands.size in 2..4
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (waitingPass && !aroundTable && !committed) {
            Text("Pass to Player ${activePlayer + 2}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(width = 150.dp, height = 210.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) { MethodMeshBackMark() }
            Spacer(Modifier.height(18.dp))
            Text("Hand hidden. Pass the device before revealing.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRevealNext, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("REVEAL PLAYER ${activePlayer + 2}") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
            return@Column
        }

        Text(
            when {
                committed -> "Draw cards"
                !settled -> "Dealing…"
                safeHands.size == 1 -> "Current draw"
                aroundTable -> "Current deal"
                else -> "Player ${activePlayer + 1}"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (!aroundTable && safeHands.size > 1) Text("Your hand", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))

        if (aroundTable) {
            AroundTableCardHands(safeHands, frame, settled)
        } else {
            val hand = safeHands.getOrElse(activePlayer) { emptyList() }
            val visible = if (settled || frame >= 100) hand.size else ((frame - 4).coerceAtLeast(0) / 7).coerceIn(0, hand.size)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = (maxWidth.value / 90f).toInt().coerceAtLeast(1).coerceAtMost(6)
                val slots: List<Pair<Int?, String?>> = buildList {
                    add(null to null)
                    hand.forEachIndexed { index, card -> add(index to card) }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    slots.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            row.forEach { (index, card) ->
                                Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                    if (index == null) DeckBackStack()
                                    else PlayingCard(card.orEmpty(), faceUp = index < visible, modifier = Modifier.alpha(if (index < visible) 1f else 0.45f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        if (!settled) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            return@Column
        }

        if (!aroundTable) {
            val hand = safeHands.getOrElse(activePlayer) { emptyList() }
            ChanceCopyableText(
                hand.joinToString(" · "),
                modifier = Modifier.padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (committed) {
            ChancePostCommitActions(
                title = "Draw cards",
                primaryText = primaryResult,
                auditJson = auditJson,
                onDone = onDone,
                onNewRun = onNewRun
            )
            return@Column
        }

        if (!aroundTable && activePlayer < safeHands.lastIndex) {
            Button(onClick = onNextPlayer, modifier = Modifier.fillMaxWidth()) { Text("HIDE & PASS") }
        } else {
            Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text("Commit") }
            OutlinedButton(onClick = onDrawAgain, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Deal again") }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
    }
}

@Composable
private fun AroundTableCardHands(hands: List<List<String>>, frame: Int, settled: Boolean) {
    val visibleCount = if (settled || frame >= 100) Int.MAX_VALUE else ((frame - 4).coerceAtLeast(0) / 7)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (hands.size) {
            2 -> {
                AroundTableCardPanel(0, hands[0], visibleCount, 180f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                AroundTableCardPanel(1, hands[1], visibleCount, 0f, Modifier.fillMaxWidth())
            }
            3 -> {
                AroundTableCardPanel(0, hands[0], visibleCount, 180f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AroundTableCardPanel(1, hands[1], visibleCount, 90f, Modifier.weight(1f))
                    AroundTableCardPanel(2, hands[2], visibleCount, -90f, Modifier.weight(1f))
                }
            }
            else -> {
                AroundTableCardPanel(0, hands[0], visibleCount, 180f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AroundTableCardPanel(1, hands[1], visibleCount, 90f, Modifier.weight(1f))
                    AroundTableCardPanel(2, hands[2], visibleCount, -90f, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                AroundTableCardPanel(3, hands[3], visibleCount, 0f, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AroundTableCardPanel(playerIndex: Int, hand: List<String>, visibleCount: Int, rotation: Float, modifier: Modifier) {
    Column(
        modifier.padding(4.dp).graphicsLayer { rotationZ = rotation },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Player ${playerIndex + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            hand.take(4).forEachIndexed { index, card -> PlayingCard(card, faceUp = index < visibleCount, compact = true) }
        }
        if (hand.size > 4) Text("+${hand.size - 4} cards", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PickOneExperience(
    setType: String,
    arrangement: List<String>,
    selectedPosition: Int,
    flipFrame: Int,
    settled: Boolean,
    committed: Boolean,
    revealAll: Boolean,
    allowRevealAll: Boolean,
    result: String,
    auditJson: String,
    onPick: (Int) -> Unit,
    onRevealAll: () -> Unit,
    onPickAgain: () -> Unit,
    onCommit: () -> Unit,
    onDone: () -> Unit,
    onNewRun: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                committed -> "Pick one"
                selectedPosition == 0 -> "Pick one"
                settled -> "Current pick"
                else -> "Turning over…"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(if (selectedPosition == 0) "Choose any face-down card." else "Position $selectedPosition", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth.value / 82f).toInt().coerceAtLeast(1).coerceAtMost(4).coerceAtMost(arrangement.size.coerceAtLeast(1))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                arrangement.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        row.forEachIndexed { colIndex, value ->
                            val position = rowIndex * columns + colIndex + 1
                            val selected = position == selectedPosition
                            val reveal = revealAll || (selected && (settled || flipFrame > 10))
                            val flipProgress = if (selected && !settled && flipFrame in 1..99) (flipFrame.coerceAtMost(22) / 22f) else if (reveal) 1f else 0f
                            val flipScale = abs(cos(flipProgress * PI)).toFloat().coerceAtLeast(0.06f)
                            ChoiceCard(
                                value = value,
                                setType = setType,
                                faceUp = reveal,
                                selected = selected,
                                modifier = Modifier.padding(5.dp).graphicsLayer { scaleX = flipScale }
                                    .clickable(enabled = selectedPosition == 0) { onPick(position) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (selectedPosition == 0) {
            Text("The values have been shuffled. You choose the position.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
            return@Column
        }
        if (!settled) {
            Text("Turning over…", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
            return@Column
        }

        val selectedValue = arrangement.getOrNull(selectedPosition - 1).orEmpty()
        ChanceCopyableText(
            selectedValue,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (allowRevealAll && !revealAll) {
            OutlinedButton(onClick = onRevealAll, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Reveal remaining") }
        }

        if (committed) {
            ChancePostCommitActions(
                title = "Pick one",
                primaryText = result.ifBlank { selectedValue },
                auditJson = auditJson,
                onDone = onDone,
                onNewRun = onNewRun
            )
        } else {
            Button(onClick = onCommit, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Commit") }
            OutlinedButton(onClick = onPickAgain, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Shuffle and pick again") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun ChanceWheelExperience(
    labels: List<String>,
    weights: List<Double>,
    selectedIndex: Int,
    frame: Int,
    settled: Boolean,
    committed: Boolean,
    weighted: Boolean,
    result: String,
    auditJson: String,
    onCommit: () -> Unit,
    onSpinAgain: () -> Unit,
    onDone: () -> Unit,
    onNewRun: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                committed -> if (weighted) "Weighted choice" else "Spinner"
                !settled -> if (weighted) "Choosing…" else "Spinning…"
                else -> if (weighted) "Current choice" else "Current spin"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        ChanceWheel(labels, weights, selectedIndex, frame, settled)
        Spacer(Modifier.height(12.dp))
        if (settled) {
            ChanceCopyableText(
                result,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (committed) {
                ChancePostCommitActions(
                    title = if (weighted) "Weighted choice" else "Spinner",
                    primaryText = result,
                    auditJson = auditJson,
                    onDone = onDone,
                    onNewRun = onNewRun
                )
            } else {
                Button(onClick = onCommit, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Commit") }
                OutlinedButton(onClick = onSpinAgain, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (weighted) "Choose again" else "Spin again") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
            }
        } else {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun ChanceWheel(labels: List<String>, weights: List<Double>, selectedIndex: Int, frame: Int, settled: Boolean) {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    )
    val textColor = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline
    val safeLabels = labels.ifEmpty { listOf("?") }
    val safeWeights = if (weights.size == safeLabels.size && weights.all { it > 0 }) weights else List(safeLabels.size) { 1.0 }
    val total = safeWeights.sum()
    val selectedStart = safeWeights.take(selectedIndex.coerceIn(0, safeWeights.lastIndex)).sum() / total * 360.0
    val selectedSweep = safeWeights[selectedIndex.coerceIn(0, safeWeights.lastIndex)] / total * 360.0
    val target = 360f * 6f - (selectedStart + selectedSweep / 2.0).toFloat()
    val t = if (settled || frame >= 100) 1f else (frame.coerceAtLeast(0) / 62f).coerceIn(0f, 1f)
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    val rotation = target * eased

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val wheelSize = if (maxWidth > 300.dp) 300.dp else maxWidth
        Box(Modifier.size(wheelSize), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val diameter = minOf(size.width, size.height) * 0.88f
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f + 6f)
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            var start = -90f + rotation
            safeWeights.forEachIndexed { i, weight ->
                val sweep = (weight / total * 360.0).toFloat()
                drawArc(palette[i % palette.size], start, sweep, useCenter = true, topLeft = topLeft, size = arcSize)
                drawArc(outline, start, sweep, useCenter = true, topLeft = topLeft, size = arcSize, style = Stroke(width = 1.2f))
                if (safeLabels.size <= 12 && sweep >= 20f) {
                    val mid = Math.toRadians((start + sweep / 2f).toDouble())
                    val radius = diameter * 0.31f
                    val cx = size.width / 2f + cos(mid).toFloat() * radius
                    val cy = size.height / 2f + 6f + sin(mid).toFloat() * radius
                    drawContext.canvas.nativeCanvas.drawText(
                        safeLabels[i].take(10), cx, cy,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor.toArgb(); textSize = 11.dp.toPx(); textAlign = Paint.Align.CENTER }
                    )
                }
                start += sweep
            }
            val pointer = Path().apply {
                moveTo(size.width / 2f, 5f)
                lineTo(size.width / 2f - 11f, 31f)
                lineTo(size.width / 2f + 11f, 31f)
                close()
            }
            drawPath(pointer, color = textColor)
        }
        }
    }
    if (labels.size > 12) Text("${labels.size} segments · labels shown in the result/details", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DeckBackStack() {
    Box(Modifier.size(width = 82.dp, height = 116.dp)) {
        repeat(3) { offset ->
            Box(
                Modifier.size(width = 70.dp, height = 100.dp).graphicsLayer { translationX = offset * 4f; translationY = offset * -3f }
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { MethodMeshBackMark() }
        }
    }
}

@Composable
private fun PlayingCard(card: String, faceUp: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val appContext = LocalContext.current
    val cardWidth = if (compact) 52.dp else 76.dp
    val cardHeight = if (compact) 74.dp else 108.dp
    val cardModifier = modifier.size(width = cardWidth, height = cardHeight)
        .background(if (faceUp) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
        .let { base -> if (faceUp && card.isNotBlank()) base.clickable { copyChanceValue(appContext, card) } else base }
    Box(cardModifier, contentAlignment = Alignment.Center) {
        if (!faceUp) MethodMeshBackMark()
        else {
            val red = card.contains('♥') || card.contains('♦')
            Text(card, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (red) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ChoiceCard(value: String, setType: String, faceUp: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier.size(width = 72.dp, height = 104.dp)
            .background(if (faceUp) { if (setType == "colours") choiceFaceColor(value) else MaterialTheme.colorScheme.surface } else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!faceUp) MethodMeshBackMark()
        else when (setType) {
            "abstract" -> AbstractMark(value)
            else -> Text(choiceDisplay(value, setType), style = if (setType == "numbers") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun MethodMeshBackMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(34.dp).height(7.dp).background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))) {}
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(27.dp).height(7.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))) {}
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(19.dp).height(7.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) {}
    }
}

@Composable
private fun AbstractMark(value: String) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(52.dp)) {
        val variant = (value.hashCode() and Int.MAX_VALUE) % 6
        when (variant) {
            0 -> repeat(3) { row ->
                val path = Path(); for (i in 0..24) { val x = size.width * i / 24f; val y = size.height * (0.25f + row * 0.22f) + sin(i / 24f * PI * 4).toFloat() * 4f; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; drawPath(path, lineColor, style = Stroke(2f))
            }
            1 -> { drawLine(lineColor, androidx.compose.ui.geometry.Offset(4f, 6f), androidx.compose.ui.geometry.Offset(size.width - 4f, size.height - 6f), 3f); drawLine(lineColor, androidx.compose.ui.geometry.Offset(4f, size.height - 6f), androidx.compose.ui.geometry.Offset(size.width - 4f, 6f), 3f) }
            2 -> repeat(4) { i -> drawCircle(lineColor, radius = 3.5f, center = androidx.compose.ui.geometry.Offset(size.width * (i + 1) / 5f, size.height / 2f)) }
            3 -> { val p = Path().apply { moveTo(5f, size.height * .7f); cubicTo(size.width*.25f, 0f, size.width*.75f, size.height, size.width-5f, size.height*.3f) }; drawPath(p, lineColor, style=Stroke(3f)) }
            4 -> repeat(4) { i -> drawLine(lineColor, androidx.compose.ui.geometry.Offset(8f + i * 10f, 6f), androidx.compose.ui.geometry.Offset(8f + i * 10f, size.height - 6f), 2.5f) }
            else -> { val p = Path(); p.moveTo(4f,size.height/2f); p.lineTo(size.width*.25f,8f); p.lineTo(size.width*.5f,size.height-8f); p.lineTo(size.width*.75f,8f); p.lineTo(size.width-4f,size.height/2f); drawPath(p,lineColor,style=Stroke(3f)) }
        }
    }
}

@Composable
private fun CompactCountControl(value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        OutlinedButton(onClick = { onValue((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }
        Text(value.toString(), modifier = Modifier.width(64.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onValue((value + 1).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
}

@Composable
private fun ChanceChoiceDropdown(value: String, choices: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shown = choices.firstOrNull { it.first == value }?.second ?: value
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(shown, modifier = Modifier.weight(1f), textAlign = TextAlign.Start); Text("▼") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (code, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onSelected(code) }) }
        }
    }
}

@Composable
private fun ChanceRngChooser(value: String, onSelected: (String) -> Unit) {
    Text("Random source", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth()) {
        if (value == "secure_random") Button(onClick = { onSelected("secure_random") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ Secure") }
        else OutlinedButton(onClick = { onSelected("secure_random") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("Secure") }
        if (value == "fixed_seed") Button(onClick = { onSelected("fixed_seed") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ Fixed seed") }
        else OutlinedButton(onClick = { onSelected("fixed_seed") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("Fixed seed") }
    }
}

@Composable
private fun ChanceAnimationChooser(value: String, onSelected: (String) -> Unit) {
    Text("Animation", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth()) {
        listOf("full" to "Full", "fast" to "Fast", "off" to "Off").forEach { (code, label) ->
            if (value == code) Button(onClick = { onSelected(code) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ $label") }
            else OutlinedButton(onClick = { onSelected(code) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text(label) }
        }
    }
}

@Composable
private fun ChanceBoolToggle(label: String, value: Boolean, onSelected: (Boolean) -> Unit) {
    ChanceSwitchRow(label = label, checked = value, onCheckedChange = onSelected)
}

private fun CapabilityScreenContext.chanceSetting(key: String, default: String): String = action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default
private fun CapabilityScreenContext.chanceBoolSetting(key: String, default: Boolean): Boolean = when ((action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"])?.lowercase()) { "true", "1", "yes" -> true; "false", "0", "no" -> false; else -> default }

private fun cardExecution(context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = As100CardDrawMethod.request(As100CardDrawMethod.ID, context.request.invocationContext.asMap(As100CardDrawMethod.ID) + context.action.settings, emptyList(), emptyList())
    return As100CardDrawMethod.result(request, values, context.request.invocationContext)
}
private fun pickExecution(context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = As100PickOneMethod.request(As100PickOneMethod.ID, context.request.invocationContext.asMap(As100PickOneMethod.ID) + context.action.settings, emptyList(), emptyList())
    return As100PickOneMethod.result(request, values, context.request.invocationContext)
}
private fun spinnerExecution(context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = As100SpinnerMethod.request(As100SpinnerMethod.ID, context.request.invocationContext.asMap(As100SpinnerMethod.ID) + context.action.settings, emptyList(), emptyList())
    return As100SpinnerMethod.result(request, values, context.request.invocationContext)
}
private fun weightedExecution(context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = As100WeightedChoiceMethod.request(As100WeightedChoiceMethod.ID, context.request.invocationContext.asMap(As100WeightedChoiceMethod.ID) + context.action.settings, emptyList(), emptyList())
    return As100WeightedChoiceMethod.result(request, values, context.request.invocationContext)
}

private fun jsonPlayerHands(json: String): List<List<String>> = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).map { index ->
        val cards = array.getJSONObject(index).optJSONArray("cards") ?: JSONArray()
        (0 until cards.length()).map { cards.getString(it) }
    }
}.getOrDefault(emptyList())

private fun jsonStringList(json: String): List<String> = runCatching {
    val arr = JSONArray(json); (0 until arr.length()).map { arr.getString(it) }
}.getOrDefault(emptyList())
private fun weightedLabels(json: String, fallback: String): List<String> = runCatching {
    val arr = JSONArray(json); (0 until arr.length()).map { arr.getJSONObject(it).getString("label") }
}.getOrElse { runCatching { ChanceSelectionEngine.parseWeightedItems(fallback).map { it.label } }.getOrDefault(emptyList()) }
private fun weightedWeights(json: String, fallback: String): List<Double> = runCatching {
    val arr = JSONArray(json); (0 until arr.length()).map { arr.getJSONObject(it).getDouble("weight") }
}.getOrElse { runCatching { ChanceSelectionEngine.parseWeightedItems(fallback).map { it.weight } }.getOrDefault(emptyList()) }

private fun choiceDisplay(value: String, setType: String): String = when (setType) {
    "suits" -> value.substringBefore(' ')
    else -> value
}
private fun choiceFaceColor(value: String): Color = when (value.lowercase()) {
    "red" -> Color(0xFFE57373); "orange" -> Color(0xFFFFB74D); "yellow" -> Color(0xFFFFF176); "green" -> Color(0xFF81C784)
    "teal" -> Color(0xFF4DB6AC); "blue" -> Color(0xFF64B5F6); "indigo" -> Color(0xFF7986CB); "purple" -> Color(0xFFBA68C8)
    "pink" -> Color(0xFFF06292); "brown" -> Color(0xFFA1887F); "grey" -> Color(0xFFB0BEC5); "black" -> Color(0xFF424242)
    else -> Color(0xFFE0E0E0)
}
