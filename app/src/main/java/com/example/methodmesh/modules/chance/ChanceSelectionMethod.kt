package com.example.methodmesh.modules.chance

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private const val CHANCE_METHOD_VERSION = "0.2.0"

object CardDrawFields {
    const val STATUS = "card_status"
    const val RESULT = "card_result"
    const val CARDS_CSV = "card_cards_csv"
    const val CARDS_JSON = "card_cards_json"
    const val DRAW_COUNT = "card_draw_count"
    const val PLAYER_COUNT = "card_player_count"
    const val CARDS_PER_PLAYER = "card_cards_per_player"
    const val PLAYER_HANDS_JSON = "card_player_hands_json"
    const val INCLUDE_JOKERS = "card_include_jokers"
    const val RNG_MODE = "card_rng_mode"
    const val SEED = "card_seed"
    const val SEED_SHA256 = "card_seed_sha256"
    const val ALGORITHM = "card_rng_algorithm"
    const val ALGORITHM_VERSION = "card_rng_algorithm_version"
    const val GENERATED_TIME_ISO = "card_generated_time_iso"
    const val AUDIT_JSON = "card_audit_json"
    const val ERROR = "card_error"
    val outputs = listOf(
        STATUS, RESULT, CARDS_CSV, CARDS_JSON, DRAW_COUNT, PLAYER_COUNT,
        CARDS_PER_PLAYER, PLAYER_HANDS_JSON, INCLUDE_JOKERS, RNG_MODE, SEED,
        SEED_SHA256, ALGORITHM, ALGORITHM_VERSION, GENERATED_TIME_ISO, AUDIT_JSON, ERROR
    )
}

object PickOneFields {
    const val STATUS = "pick_status"
    const val RESULT = "pick_result"
    const val SET_TYPE = "pick_set_type"
    const val ITEM_COUNT = "pick_item_count"
    const val SELECTED_POSITION = "pick_selected_position"
    const val SELECTED_VALUE = "pick_selected_value"
    const val ARRANGEMENT_JSON = "pick_arrangement_json"
    const val REVEAL_REMAINING = "pick_reveal_remaining"
    const val PREPARED_TIME_ISO = "pick_prepared_time_iso"
    const val RNG_MODE = "pick_rng_mode"
    const val SEED = "pick_seed"
    const val SEED_SHA256 = "pick_seed_sha256"
    const val ALGORITHM = "pick_rng_algorithm"
    const val ALGORITHM_VERSION = "pick_rng_algorithm_version"
    const val AUDIT_JSON = "pick_audit_json"
    const val ERROR = "pick_error"
    val outputs = listOf(STATUS, RESULT, SET_TYPE, ITEM_COUNT, SELECTED_POSITION, SELECTED_VALUE, ARRANGEMENT_JSON, REVEAL_REMAINING, PREPARED_TIME_ISO, RNG_MODE, SEED, SEED_SHA256, ALGORITHM, ALGORITHM_VERSION, AUDIT_JSON, ERROR)
}

object SpinnerFields {
    const val STATUS = "spinner_status"
    const val RESULT = "spinner_result"
    const val ITEMS_JSON = "spinner_items_json"
    const val SELECTED_INDEX = "spinner_selected_index"
    const val SELECTED_VALUE = "spinner_selected_value"
    const val RNG_MODE = "spinner_rng_mode"
    const val SEED = "spinner_seed"
    const val SEED_SHA256 = "spinner_seed_sha256"
    const val ALGORITHM = "spinner_rng_algorithm"
    const val ALGORITHM_VERSION = "spinner_rng_algorithm_version"
    const val GENERATED_TIME_ISO = "spinner_generated_time_iso"
    const val AUDIT_JSON = "spinner_audit_json"
    const val ERROR = "spinner_error"
    val outputs = listOf(STATUS, RESULT, ITEMS_JSON, SELECTED_INDEX, SELECTED_VALUE, RNG_MODE, SEED, SEED_SHA256, ALGORITHM, ALGORITHM_VERSION, GENERATED_TIME_ISO, AUDIT_JSON, ERROR)
}

object WeightedChoiceFields {
    const val STATUS = "weighted_status"
    const val RESULT = "weighted_result"
    const val OPTIONS_JSON = "weighted_options_json"
    const val SELECTED_INDEX = "weighted_selected_index"
    const val SELECTED_VALUE = "weighted_selected_value"
    const val SELECTED_WEIGHT = "weighted_selected_weight"
    const val SELECTED_PROBABILITY = "weighted_selected_probability"
    const val TOTAL_WEIGHT = "weighted_total_weight"
    const val RNG_MODE = "weighted_rng_mode"
    const val SEED = "weighted_seed"
    const val SEED_SHA256 = "weighted_seed_sha256"
    const val ALGORITHM = "weighted_rng_algorithm"
    const val ALGORITHM_VERSION = "weighted_rng_algorithm_version"
    const val GENERATED_TIME_ISO = "weighted_generated_time_iso"
    const val AUDIT_JSON = "weighted_audit_json"
    const val ERROR = "weighted_error"
    val outputs = listOf(STATUS, RESULT, OPTIONS_JSON, SELECTED_INDEX, SELECTED_VALUE, SELECTED_WEIGHT, SELECTED_PROBABILITY, TOTAL_WEIGHT, RNG_MODE, SEED, SEED_SHA256, ALGORITHM, ALGORITHM_VERSION, GENERATED_TIME_ISO, AUDIT_JSON, ERROR)
}

object As100CardDrawMethod : As100Method {
    const val ID = "chance.cards.draw"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Draw cards")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Draw cards", version = CHANCE_METHOD_VERSION,
        description = "Draw one or more cards without replacement from a standard deck.", outputs = CardDrawFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Development", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = CardDrawFields.outputs, producedGraphOutputs = listOf(ID))
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> {
        val cardsPerPlayer = settings.chanceValue("draw_count")?.toIntOrNull() ?: 1
        val playerCount = settings.chanceValue("player_count")?.toIntOrNull()?.coerceIn(1, 20) ?: 1
        val jokers = settings.chanceBool("include_jokers", false)
        val rngMode = settings.chanceValue("rng_mode") ?: "secure_random"
        val seed = settings.chanceValue("seed").orEmpty()
        return try {
            val deckSize = if (jokers) 54 else 52
            val totalCards = cardsPerPlayer * playerCount
            require(cardsPerPlayer >= 1) { "Cards per player must be at least 1." }
            require(totalCards <= deckSize) { "${playerCount} players × $cardsPerPlayer cards needs $totalCards cards, but this deck has $deckSize." }
            val draw = ChanceSelectionEngine.drawCards(totalCards, jokers, rngMode, seed)
            val hands = List(playerCount) { mutableListOf<String>() }
            draw.cards.forEachIndexed { index, card -> hands[index % playerCount] += card }
            val handsJson = JSONArray()
            hands.forEachIndexed { index, hand ->
                handsJson.put(JSONObject().put("player", index + 1).put("cards", JSONArray(hand)))
            }
            val cardsJson = JSONArray(draw.cards)
            val seedHash = draw.metadata.seed?.let(ChanceSelectionEngine::sha256Hex).orEmpty()
            val resultText = if (playerCount == 1) {
                hands.first().joinToString(", ")
            } else {
                hands.mapIndexed { index, hand -> "P${index + 1}: ${hand.joinToString(", ")}" }.joinToString(" · ")
            }
            val audit = baseAudit(ID, draw.metadata)
                .put("draw_count", draw.cards.size)
                .put("player_count", playerCount)
                .put("cards_per_player", cardsPerPlayer)
                .put("include_jokers", jokers)
                .put("cards", cardsJson)
                .put("player_hands", handsJson)
            linkedMapOf(
                CardDrawFields.STATUS to "succeeded",
                CardDrawFields.RESULT to resultText,
                CardDrawFields.CARDS_CSV to draw.cards.joinToString(","),
                CardDrawFields.CARDS_JSON to cardsJson.toString(),
                CardDrawFields.DRAW_COUNT to draw.cards.size.toString(),
                CardDrawFields.PLAYER_COUNT to playerCount.toString(),
                CardDrawFields.CARDS_PER_PLAYER to cardsPerPlayer.toString(),
                CardDrawFields.PLAYER_HANDS_JSON to handsJson.toString(),
                CardDrawFields.INCLUDE_JOKERS to jokers.toString(),
                CardDrawFields.RNG_MODE to draw.metadata.rngMode,
                CardDrawFields.SEED to draw.metadata.seed.orEmpty(),
                CardDrawFields.SEED_SHA256 to seedHash,
                CardDrawFields.ALGORITHM to draw.metadata.algorithm,
                CardDrawFields.ALGORITHM_VERSION to draw.metadata.algorithmVersion,
                CardDrawFields.GENERATED_TIME_ISO to draw.metadata.generatedTimeIso,
                CardDrawFields.AUDIT_JSON to audit.toString(),
                CardDrawFields.ERROR to ""
            )
        } catch (e: Exception) {
            chanceFailure(CardDrawFields.outputs, CardDrawFields.STATUS, CardDrawFields.ERROR, e.message ?: "Invalid card draw.")
        }
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = wrapResult(request, ref, ID, CHANCE_METHOD_VERSION, ID, "CardDraw", values, CardDrawFields.STATUS, CardDrawFields.ERROR, invocation)
}

object As100PickOneMethod : As100Method {
    const val ID = "chance.pick.one"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Pick one")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Pick one", version = CHANCE_METHOD_VERSION,
        description = "Randomise concealed values, then reveal the position selected by the user.", outputs = PickOneFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Development", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = PickOneFields.outputs, producedGraphOutputs = listOf(ID))
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> {
        val setType = settings.chanceValue("set_type") ?: "numbers"
        val count = settings.chanceValue("item_count")?.toIntOrNull() ?: 6
        val custom = settings.chanceValue("custom_items").orEmpty()
        val selectedPosition = settings.chanceValue("selected_position")?.toIntOrNull() ?: 0
        val revealRemaining = settings.chanceBool("reveal_remaining", true)
        val rngMode = settings.chanceValue("rng_mode") ?: "secure_random"
        val seed = settings.chanceValue("seed").orEmpty()
        val preparedJson = settings.chanceValue("prepared_pick_json")
        return try {
            val prepared = if (!preparedJson.isNullOrBlank()) ChanceSelectionEngine.parsePickPreparation(preparedJson)
            else ChanceSelectionEngine.preparePick(setType, count, custom, rngMode, seed)
            require(selectedPosition in 1..prepared.arrangement.size) {
                "Pick one requires selected_position between 1 and ${prepared.arrangement.size}."
            }
            val value = prepared.arrangement[selectedPosition - 1]
            val arrangementJson = JSONArray(prepared.arrangement)
            val seedHash = prepared.metadata.seed?.let(ChanceSelectionEngine::sha256Hex).orEmpty()
            val audit = baseAudit(ID, prepared.metadata)
                .put("set_type", prepared.setType)
                .put("arrangement", arrangementJson)
                .put("selected_position", selectedPosition)
                .put("selected_value", value)
                .put("reveal_remaining", revealRemaining)
            linkedMapOf(
                PickOneFields.STATUS to "succeeded",
                PickOneFields.RESULT to value,
                PickOneFields.SET_TYPE to prepared.setType,
                PickOneFields.ITEM_COUNT to prepared.arrangement.size.toString(),
                PickOneFields.SELECTED_POSITION to selectedPosition.toString(),
                PickOneFields.SELECTED_VALUE to value,
                PickOneFields.ARRANGEMENT_JSON to arrangementJson.toString(),
                PickOneFields.REVEAL_REMAINING to revealRemaining.toString(),
                PickOneFields.PREPARED_TIME_ISO to prepared.metadata.generatedTimeIso,
                PickOneFields.RNG_MODE to prepared.metadata.rngMode,
                PickOneFields.SEED to prepared.metadata.seed.orEmpty(),
                PickOneFields.SEED_SHA256 to seedHash,
                PickOneFields.ALGORITHM to prepared.metadata.algorithm,
                PickOneFields.ALGORITHM_VERSION to prepared.metadata.algorithmVersion,
                PickOneFields.AUDIT_JSON to audit.toString(),
                PickOneFields.ERROR to ""
            )
        } catch (e: Exception) {
            chanceFailure(PickOneFields.outputs, PickOneFields.STATUS, PickOneFields.ERROR, e.message ?: "Invalid Pick one configuration.")
        }
    }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = wrapResult(request, ref, ID, CHANCE_METHOD_VERSION, ID, "ConcealedPick", values, PickOneFields.STATUS, PickOneFields.ERROR, invocation)
}

object As100SpinnerMethod : As100Method {
    const val ID = "chance.spinner.spin"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Spin spinner")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Spinner", version = CHANCE_METHOD_VERSION,
        description = "Choose uniformly from labelled spinner segments.", outputs = SpinnerFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Development", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = SpinnerFields.outputs, producedGraphOutputs = listOf(ID))
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> {
        val itemsText = settings.chanceValue("items") ?: "Yes\nNo"
        val rngMode = settings.chanceValue("rng_mode") ?: "secure_random"
        val seed = settings.chanceValue("seed").orEmpty()
        return try {
            val items = ChanceSelectionEngine.parseItems(itemsText)
            val choice = ChanceSelectionEngine.spin(itemsText, rngMode, seed)
            val seedHash = choice.metadata.seed?.let(ChanceSelectionEngine::sha256Hex).orEmpty()
            val audit = baseAudit(ID, choice.metadata).put("items", JSONArray(items)).put("selected_index", choice.index + 1).put("selected_value", choice.label)
            linkedMapOf(
                SpinnerFields.STATUS to "succeeded", SpinnerFields.RESULT to choice.label,
                SpinnerFields.ITEMS_JSON to JSONArray(items).toString(), SpinnerFields.SELECTED_INDEX to (choice.index + 1).toString(),
                SpinnerFields.SELECTED_VALUE to choice.label, SpinnerFields.RNG_MODE to choice.metadata.rngMode,
                SpinnerFields.SEED to choice.metadata.seed.orEmpty(), SpinnerFields.SEED_SHA256 to seedHash,
                SpinnerFields.ALGORITHM to choice.metadata.algorithm, SpinnerFields.ALGORITHM_VERSION to choice.metadata.algorithmVersion,
                SpinnerFields.GENERATED_TIME_ISO to choice.metadata.generatedTimeIso, SpinnerFields.AUDIT_JSON to audit.toString(), SpinnerFields.ERROR to ""
            )
        } catch (e: Exception) {
            chanceFailure(SpinnerFields.outputs, SpinnerFields.STATUS, SpinnerFields.ERROR, e.message ?: "Invalid spinner configuration.")
        }
    }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = wrapResult(request, ref, ID, CHANCE_METHOD_VERSION, ID, "SpinnerChoice", values, SpinnerFields.STATUS, SpinnerFields.ERROR, invocation)
}

object As100WeightedChoiceMethod : As100Method {
    const val ID = "chance.weighted.choose"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Weighted choice")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Weighted choice", version = CHANCE_METHOD_VERSION,
        description = "Choose from labelled options in proportion to positive numeric weights.", outputs = WeightedChoiceFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Development", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = WeightedChoiceFields.outputs, producedGraphOutputs = listOf(ID))
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> {
        val weightedText = settings.chanceValue("weighted_items") ?: "Option A:1\nOption B:1"
        val rngMode = settings.chanceValue("rng_mode") ?: "secure_random"
        val seed = settings.chanceValue("seed").orEmpty()
        return try {
            val choice = ChanceSelectionEngine.weightedChoose(weightedText, rngMode, seed)
            val optionsJson = JSONArray().apply { choice.options.forEach { put(JSONObject().put("label", it.label).put("weight", it.weight).put("probability", it.weight / choice.totalWeight)) } }
            val seedHash = choice.metadata.seed?.let(ChanceSelectionEngine::sha256Hex).orEmpty()
            val audit = baseAudit(ID, choice.metadata)
                .put("options", optionsJson).put("total_weight", choice.totalWeight)
                .put("selected_index", choice.index + 1).put("selected_value", choice.label)
                .put("selected_weight", choice.weight).put("selected_probability", choice.probability)
            linkedMapOf(
                WeightedChoiceFields.STATUS to "succeeded", WeightedChoiceFields.RESULT to choice.label,
                WeightedChoiceFields.OPTIONS_JSON to optionsJson.toString(), WeightedChoiceFields.SELECTED_INDEX to (choice.index + 1).toString(),
                WeightedChoiceFields.SELECTED_VALUE to choice.label, WeightedChoiceFields.SELECTED_WEIGHT to formatChanceNumber(choice.weight),
                WeightedChoiceFields.SELECTED_PROBABILITY to formatChanceNumber(choice.probability), WeightedChoiceFields.TOTAL_WEIGHT to formatChanceNumber(choice.totalWeight),
                WeightedChoiceFields.RNG_MODE to choice.metadata.rngMode, WeightedChoiceFields.SEED to choice.metadata.seed.orEmpty(),
                WeightedChoiceFields.SEED_SHA256 to seedHash, WeightedChoiceFields.ALGORITHM to choice.metadata.algorithm,
                WeightedChoiceFields.ALGORITHM_VERSION to choice.metadata.algorithmVersion, WeightedChoiceFields.GENERATED_TIME_ISO to choice.metadata.generatedTimeIso,
                WeightedChoiceFields.AUDIT_JSON to audit.toString(), WeightedChoiceFields.ERROR to ""
            )
        } catch (e: Exception) {
            chanceFailure(WeightedChoiceFields.outputs, WeightedChoiceFields.STATUS, WeightedChoiceFields.ERROR, e.message ?: "Invalid weighted choice configuration.")
        }
    }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = wrapResult(request, ref, ID, CHANCE_METHOD_VERSION, ID, "WeightedChoice", values, WeightedChoiceFields.STATUS, WeightedChoiceFields.ERROR, invocation)
}

private fun baseAudit(methodId: String, metadata: ChanceSelectionEngine.Metadata): JSONObject = JSONObject()
    .put("method_id", methodId)
    .put("method_version", CHANCE_METHOD_VERSION)
    .put("engine_version", ChanceSelectionEngine.ENGINE_VERSION)
    .put("generated_time_iso", metadata.generatedTimeIso)
    .put("rng_mode", metadata.rngMode)
    .put("rng_algorithm", metadata.algorithm)
    .put("rng_algorithm_version", metadata.algorithmVersion)
    .put("seed", metadata.seed ?: JSONObject.NULL)
    .put("seed_sha256", metadata.seed?.let(ChanceSelectionEngine::sha256Hex) ?: "")
    .put("primitive_draws", ChanceSelectionEngine.primitiveDrawsJson(metadata.primitiveDraws))

private fun chanceFailure(outputs: List<String>, statusField: String, errorField: String, error: String): Map<String, String> =
    outputs.associateWith { "" }.toMutableMap().apply { this[statusField] = "failed"; this[errorField] = error }

private fun Map<String, String>.chanceValue(key: String): String? = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
private fun Map<String, String>.chanceBool(key: String, default: Boolean): Boolean = when ((this[key] ?: this["input_$key"])?.trim()?.lowercase()) {
    "true", "1", "yes", "y" -> true
    "false", "0", "no", "n" -> false
    else -> default
}
private fun formatChanceNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.6f".format(value).trimEnd('0').trimEnd('.')
