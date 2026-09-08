package com.example.methodmesh.modules.chance

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object DiceSimulationFields {
    const val STATUS = "dice_status"
    const val RESULT = "dice_result"
    const val TOTAL = "dice_total"
    const val OUTCOME = "dice_outcome"
    const val EXPRESSION = "dice_expression"
    const val CANONICAL_EXPRESSION = "dice_canonical_expression"
    const val ROLL_COUNT = "dice_roll_count"
    const val PLAYER_COUNT = "dice_player_count"
    const val PLAYERS_JSON = "dice_players_json"
    const val LAST_VALUES_CSV = "dice_last_values_csv"
    const val LAST_RETAINED_VALUES_CSV = "dice_last_retained_values_csv"
    const val TOTALS_CSV = "dice_totals_csv"
    const val HISTORY_JSON = "dice_history_json"
    const val LAST_ROLL_DETAILS_JSON = "dice_last_roll_details_json"
    const val PRIMITIVE_DRAW_COUNT = "dice_primitive_draw_count"
    const val RNG_MODE = "dice_rng_mode"
    const val SEED = "dice_seed"
    const val SEED_SHA256 = "dice_seed_sha256"
    const val ALGORITHM = "dice_rng_algorithm"
    const val ALGORITHM_VERSION = "dice_rng_algorithm_version"
    const val ENGINE_VERSION = "dice_engine_version"
    const val GENERATED_TIME_ISO = "dice_generated_time_iso"
    const val AUDIT_JSON = "dice_audit_json"
    const val ERROR = "dice_error"

    val outputs = listOf(
        STATUS, RESULT, TOTAL, OUTCOME, EXPRESSION, CANONICAL_EXPRESSION, ROLL_COUNT,
        PLAYER_COUNT, PLAYERS_JSON, LAST_VALUES_CSV, LAST_RETAINED_VALUES_CSV,
        TOTALS_CSV, HISTORY_JSON, LAST_ROLL_DETAILS_JSON, PRIMITIVE_DRAW_COUNT,
        RNG_MODE, SEED, SEED_SHA256, ALGORITHM, ALGORITHM_VERSION, ENGINE_VERSION,
        GENERATED_TIME_ISO, AUDIT_JSON, ERROR
    )
}

object CoinTossFields {
    const val STATUS = "coin_status"
    const val RESULT = "coin_result"
    const val TOSS_COUNT = "coin_toss_count"
    const val LAST_TOSS = "coin_last_toss"
    const val HEADS_COUNT = "coin_heads_count"
    const val TAILS_COUNT = "coin_tails_count"
    const val TOSSES_CSV = "coin_tosses_csv"
    const val TOSSES_JSON = "coin_tosses_json"
    const val RNG_MODE = "coin_rng_mode"
    const val SEED = "coin_seed"
    const val SEED_SHA256 = "coin_seed_sha256"
    const val ALGORITHM = "coin_rng_algorithm"
    const val ALGORITHM_VERSION = "coin_rng_algorithm_version"
    const val ENGINE_VERSION = "coin_engine_version"
    const val GENERATED_TIME_ISO = "coin_generated_time_iso"
    const val AUDIT_JSON = "coin_audit_json"
    const val ERROR = "coin_error"

    val outputs = listOf(
        STATUS, RESULT, TOSS_COUNT, LAST_TOSS, HEADS_COUNT, TAILS_COUNT,
        TOSSES_CSV, TOSSES_JSON, RNG_MODE, SEED, SEED_SHA256, ALGORITHM,
        ALGORITHM_VERSION, ENGINE_VERSION, GENERATED_TIME_ISO, AUDIT_JSON, ERROR
    )
}

object As100DiceSimulationMethod : As100Method {
    const val ID = "dice.simulate"
    private const val VERSION = "0.2.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Dice simulation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Dice",
        version = VERSION,
        description = "Simulate simple or advanced RPG dice expressions with complete primitive-roll provenance.",
        outputs = DiceSimulationFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Development", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> {
        val generatedAt = Instant.now().toString()
        val expression = settings.value("expression") ?: "d6"
        val rollCount = settings.value("roll_count")?.toIntOrNull() ?: 1
        val playerCount = settings.value("player_count")?.toIntOrNull()?.coerceIn(1, 20) ?: 1
        val historyOutput = settings.boolValue("history_output", true)
        val rngMode = settings.value("rng_mode") ?: "secure_random"
        val seed = settings.value("seed").orEmpty()

        return try {
            val playerSimulations = (1..playerCount).map { player ->
                val playerSeed = if (rngMode == "fixed_seed" && playerCount > 1) {
                    "${seed.ifBlank { "methodmesh-dice" }}|player:$player"
                } else seed
                DiceSimulatorEngine.simulate(expression, rollCount, rngMode, playerSeed)
            }

            val playersJson = JSONArray()
            val playerRoundJsons = mutableListOf<JSONArray>()
            val playerLastJsons = mutableListOf<JSONObject>()
            val playerMains = mutableListOf<String>()
            var primitiveDrawCount = 0

            playerSimulations.forEachIndexed { index, simulation ->
                val roundsJson = JSONArray()
                simulation.rounds.forEach { roundsJson.put(roundJson(it)) }
                val last = simulation.rounds.last()
                val lastJson = roundJson(last)
                val main = formatMainResult(last)
                val playerPrimitiveCount = simulation.rounds.sumOf { it.primitiveDraws.size }
                primitiveDrawCount += playerPrimitiveCount
                playerRoundJsons += roundsJson
                playerLastJsons += lastJson
                playerMains += main
                playersJson.put(
                    JSONObject()
                        .put("player", index + 1)
                        .put("result", main)
                        .put("total", last.total)
                        .put("outcome", last.comparisonPassed?.let { if (it) "success" else "failure" } ?: "")
                        .put("last_roll_details", lastJson)
                        .put("history", if (historyOutput) roundsJson else JSONArray())
                        .put("primitive_draw_count", playerPrimitiveCount)
                        .put("rng_mode", simulation.rngMode)
                        .put("seed", simulation.seed ?: JSONObject.NULL)
                )
            }

            val first = playerSimulations.first()
            val firstLast = first.rounds.last()
            val firstRoundsJson = playerRoundJsons.first()
            val firstLastJson = playerLastJsons.first()
            val main = if (playerCount == 1) playerMains.first() else playerMains.mapIndexed { index, value -> "P${index + 1} $value" }.joinToString(" · ")
            val outcome = if (playerCount == 1) firstLast.comparisonPassed?.let { if (it) "success" else "failure" }.orEmpty() else ""
            val originalSeed = if (playerCount == 1) first.seed.orEmpty() else if (rngMode == "fixed_seed") seed.ifBlank { "methodmesh-dice" } else ""
            val seedHash = originalSeed.takeIf { it.isNotBlank() }?.let(DiceSimulatorEngine::sha256Hex).orEmpty()
            val audit = JSONObject()
                .put("method_id", ID)
                .put("method_version", VERSION)
                .put("engine_version", DiceSimulatorEngine.ENGINE_VERSION)
                .put("generated_time_iso", generatedAt)
                .put("expression", expression)
                .put("canonical_expression", first.canonicalExpression)
                .put("roll_count", first.rounds.size)
                .put("player_count", playerCount)
                .put("rng_mode", first.rngMode)
                .put("rng_algorithm", first.algorithm)
                .put("rng_algorithm_version", first.algorithmVersion)
                .put("seed", if (originalSeed.isBlank()) JSONObject.NULL else originalSeed)
                .put("seed_sha256", seedHash)
                .put("primitive_draw_count", primitiveDrawCount)
                .put("players", playersJson)

            linkedMapOf(
                DiceSimulationFields.STATUS to "succeeded",
                DiceSimulationFields.RESULT to main,
                DiceSimulationFields.TOTAL to firstLast.total.toString(),
                DiceSimulationFields.OUTCOME to outcome,
                DiceSimulationFields.EXPRESSION to expression,
                DiceSimulationFields.CANONICAL_EXPRESSION to first.canonicalExpression,
                DiceSimulationFields.ROLL_COUNT to first.rounds.size.toString(),
                DiceSimulationFields.PLAYER_COUNT to playerCount.toString(),
                DiceSimulationFields.PLAYERS_JSON to playersJson.toString(),
                DiceSimulationFields.LAST_VALUES_CSV to firstLast.allDice.joinToString(",") { it.acceptedBaseValue.toString() },
                DiceSimulationFields.LAST_RETAINED_VALUES_CSV to firstLast.allDice.filter { it.retained }.joinToString(",") { it.total.toString() },
                DiceSimulationFields.TOTALS_CSV to first.rounds.joinToString(",") { it.total.toString() },
                DiceSimulationFields.HISTORY_JSON to if (historyOutput) { if (playerCount == 1) firstRoundsJson.toString() else playersJson.toString() } else "",
                DiceSimulationFields.LAST_ROLL_DETAILS_JSON to firstLastJson.toString(),
                DiceSimulationFields.PRIMITIVE_DRAW_COUNT to primitiveDrawCount.toString(),
                DiceSimulationFields.RNG_MODE to first.rngMode,
                DiceSimulationFields.SEED to originalSeed,
                DiceSimulationFields.SEED_SHA256 to seedHash,
                DiceSimulationFields.ALGORITHM to first.algorithm,
                DiceSimulationFields.ALGORITHM_VERSION to first.algorithmVersion,
                DiceSimulationFields.ENGINE_VERSION to DiceSimulatorEngine.ENGINE_VERSION,
                DiceSimulationFields.GENERATED_TIME_ISO to generatedAt,
                DiceSimulationFields.AUDIT_JSON to audit.toString(),
                DiceSimulationFields.ERROR to ""
            )
        } catch (e: IllegalArgumentException) {
            failure(expression, rollCount, playerCount, rngMode, seed, generatedAt, e.message ?: "Invalid dice expression.")
        }
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        wrapResult(
            request = request,
            methodRef = ref,
            methodId = ID,
            methodVersion = VERSION,
            phenomenon = ID,
            entityType = "DiceSimulation",
            values = values,
            statusField = DiceSimulationFields.STATUS,
            errorField = DiceSimulationFields.ERROR,
            invocation = invocation
        )

    private fun failure(
        expression: String,
        rollCount: Int,
        playerCount: Int,
        rngMode: String,
        seed: String,
        generatedAt: String,
        error: String
    ) = linkedMapOf(
        DiceSimulationFields.STATUS to "failed",
        DiceSimulationFields.RESULT to "",
        DiceSimulationFields.TOTAL to "",
        DiceSimulationFields.OUTCOME to "",
        DiceSimulationFields.EXPRESSION to expression,
        DiceSimulationFields.CANONICAL_EXPRESSION to "",
        DiceSimulationFields.ROLL_COUNT to rollCount.toString(),
        DiceSimulationFields.PLAYER_COUNT to playerCount.toString(),
        DiceSimulationFields.PLAYERS_JSON to "",
        DiceSimulationFields.LAST_VALUES_CSV to "",
        DiceSimulationFields.LAST_RETAINED_VALUES_CSV to "",
        DiceSimulationFields.TOTALS_CSV to "",
        DiceSimulationFields.HISTORY_JSON to "",
        DiceSimulationFields.LAST_ROLL_DETAILS_JSON to "",
        DiceSimulationFields.PRIMITIVE_DRAW_COUNT to "",
        DiceSimulationFields.RNG_MODE to rngMode,
        DiceSimulationFields.SEED to seed,
        DiceSimulationFields.SEED_SHA256 to if (seed.isBlank()) "" else DiceSimulatorEngine.sha256Hex(seed),
        DiceSimulationFields.ALGORITHM to "",
        DiceSimulationFields.ALGORITHM_VERSION to "",
        DiceSimulationFields.ENGINE_VERSION to DiceSimulatorEngine.ENGINE_VERSION,
        DiceSimulationFields.GENERATED_TIME_ISO to generatedAt,
        DiceSimulationFields.AUDIT_JSON to JSONObject()
            .put("method_id", ID)
            .put("method_version", VERSION)
            .put("engine_version", DiceSimulatorEngine.ENGINE_VERSION)
            .put("generated_time_iso", generatedAt)
            .put("status", "failed")
            .put("expression", expression)
            .put("player_count", playerCount)
            .put("error", error)
            .toString(),
        DiceSimulationFields.ERROR to error
    )

    private fun formatMainResult(round: DiceSimulatorEngine.SimulationRound): String =
        round.comparisonPassed?.let { passed -> "${round.total} · ${if (passed) "Success" else "Failure"}" }
            ?: round.total.toString()

    private fun roundJson(round: DiceSimulatorEngine.SimulationRound): JSONObject {
        val terms = JSONArray()
        round.terms.forEach { term ->
            val dice = JSONArray()
            term.dice.forEach { die ->
                dice.put(
                    JSONObject()
                        .put("term_index", die.termIndex + 1)
                        .put("die_index", die.dieIndex)
                        .put("sides", die.sidesLabel)
                        .put("initial_value", die.initialValue)
                        .put("rerolled_values", JSONArray(die.rerolledValues))
                        .put("accepted_base_value", die.acceptedBaseValue)
                        .put("explosion_values", JSONArray(die.explosionValues))
                        .put("die_total", die.total)
                        .put("retained", die.retained)
                )
            }
            terms.put(
                JSONObject()
                    .put("term_index", term.termIndex + 1)
                    .put("source", term.source)
                    .put("sign", term.sign)
                    .put("dice", dice)
                    .put("success_count", term.successCount ?: JSONObject.NULL)
                    .put("unsigned_value", term.unsignedValue)
                    .put("signed_value", term.signedValue)
            )
        }

        val primitive = JSONArray()
        round.primitiveDraws.forEach { draw ->
            primitive.put(
                JSONObject()
                    .put("sequence", draw.sequence)
                    .put("term_index", draw.termIndex + 1)
                    .put("die_index", draw.dieIndex)
                    .put("kind", draw.kind.name.lowercase())
                    .put("value", draw.value)
            )
        }

        return JSONObject()
            .put("round", round.index)
            .put("terms", terms)
            .put("total", round.total)
            .put("comparison", round.comparison?.toString() ?: JSONObject.NULL)
            .put("comparison_passed", round.comparisonPassed ?: JSONObject.NULL)
            .put("primitive_draws", primitive)
    }
}

object As100CoinTossMethod : As100Method {
    const val ID = "coin.toss"
    private const val VERSION = "0.2.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Coin toss")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Coin toss",
        version = VERSION,
        description = "Toss a fair coin using the same secure or deterministic random source as Chance.",
        outputs = CoinTossFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Development", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> {
        val generatedAt = Instant.now().toString()
        val count = settings.value("toss_count")?.toIntOrNull() ?: 1
        val historyOutput = settings.boolValue("history_output", true)
        val rngMode = settings.value("rng_mode") ?: "secure_random"
        val seed = settings.value("seed").orEmpty()
        return try {
            val tossed = DiceSimulatorEngine.tossCoins(count, rngMode, seed)
            val labels = tossed.tosses.map { if (it) "Heads" else "Tails" }
            val history = JSONArray(labels)
            val last = labels.last()
            val main = if (labels.size == 1) last else "$last · H=${tossed.heads}, T=${tossed.tails}"
            val seedHash = tossed.seed?.let(DiceSimulatorEngine::sha256Hex).orEmpty()
            val audit = JSONObject()
                .put("method_id", ID)
                .put("method_version", VERSION)
                .put("engine_version", DiceSimulatorEngine.ENGINE_VERSION)
                .put("generated_time_iso", generatedAt)
                .put("toss_count", labels.size)
                .put("rng_mode", tossed.rngMode)
                .put("rng_algorithm", tossed.algorithm)
                .put("rng_algorithm_version", tossed.algorithmVersion)
                .put("seed", tossed.seed ?: JSONObject.NULL)
                .put("seed_sha256", seedHash)
                .put("tosses", history)
                .put("heads_count", tossed.heads)
                .put("tails_count", tossed.tails)
            linkedMapOf(
                CoinTossFields.STATUS to "succeeded",
                CoinTossFields.RESULT to main,
                CoinTossFields.TOSS_COUNT to labels.size.toString(),
                CoinTossFields.LAST_TOSS to last,
                CoinTossFields.HEADS_COUNT to tossed.heads.toString(),
                CoinTossFields.TAILS_COUNT to tossed.tails.toString(),
                CoinTossFields.TOSSES_CSV to if (historyOutput) labels.joinToString(",") else "",
                CoinTossFields.TOSSES_JSON to if (historyOutput) history.toString() else "",
                CoinTossFields.RNG_MODE to tossed.rngMode,
                CoinTossFields.SEED to tossed.seed.orEmpty(),
                CoinTossFields.SEED_SHA256 to seedHash,
                CoinTossFields.ALGORITHM to tossed.algorithm,
                CoinTossFields.ALGORITHM_VERSION to tossed.algorithmVersion,
                CoinTossFields.ENGINE_VERSION to DiceSimulatorEngine.ENGINE_VERSION,
                CoinTossFields.GENERATED_TIME_ISO to generatedAt,
                CoinTossFields.AUDIT_JSON to audit.toString(),
                CoinTossFields.ERROR to ""
            )
        } catch (e: IllegalArgumentException) {
            failure(count, rngMode, seed, generatedAt, e.message ?: "Invalid coin toss configuration.")
        }
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        wrapResult(
            request = request,
            methodRef = ref,
            methodId = ID,
            methodVersion = VERSION,
            phenomenon = ID,
            entityType = "CoinTossSet",
            values = values,
            statusField = CoinTossFields.STATUS,
            errorField = CoinTossFields.ERROR,
            invocation = invocation
        )

    private fun failure(count: Int, rngMode: String, seed: String, generatedAt: String, error: String) = linkedMapOf(
        CoinTossFields.STATUS to "failed",
        CoinTossFields.RESULT to "",
        CoinTossFields.TOSS_COUNT to count.toString(),
        CoinTossFields.LAST_TOSS to "",
        CoinTossFields.HEADS_COUNT to "",
        CoinTossFields.TAILS_COUNT to "",
        CoinTossFields.TOSSES_CSV to "",
        CoinTossFields.TOSSES_JSON to "",
        CoinTossFields.RNG_MODE to rngMode,
        CoinTossFields.SEED to seed,
        CoinTossFields.SEED_SHA256 to if (seed.isBlank()) "" else DiceSimulatorEngine.sha256Hex(seed),
        CoinTossFields.ALGORITHM to "",
        CoinTossFields.ALGORITHM_VERSION to "",
        CoinTossFields.ENGINE_VERSION to DiceSimulatorEngine.ENGINE_VERSION,
        CoinTossFields.GENERATED_TIME_ISO to generatedAt,
        CoinTossFields.AUDIT_JSON to JSONObject()
            .put("method_id", ID)
            .put("method_version", VERSION)
            .put("engine_version", DiceSimulatorEngine.ENGINE_VERSION)
            .put("generated_time_iso", generatedAt)
            .put("status", "failed")
            .put("error", error)
            .toString(),
        CoinTossFields.ERROR to error
    )
}

internal fun wrapResult(
    request: ExecutionRequest,
    methodRef: ArchitectureRef,
    methodId: String,
    methodVersion: String,
    phenomenon: String,
    entityType: String,
    values: Map<String, String>,
    statusField: String,
    errorField: String,
    invocation: InvocationContext?
): ExecutionResult {
    val ok = values[statusField] == "succeeded"
    val entity = Entity(ArchitectureId("$entityType:${System.currentTimeMillis()}"), entityType, temporalContext = request.temporalContext)
    val provenance = ProvenanceContext("methodmesh.chance", methodId, methodVersion)
    val observation = Observation(
        phenomenon = phenomenon,
        subject = null,
        values = values,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    val transformation = Transformation(
        action = methodId,
        method = methodRef,
        outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
        status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    return As100ExecutionEngine.complete(
        request,
        if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        entities = listOf(entity),
        observations = listOf(observation),
        transformations = listOf(transformation),
        diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
    ).withInvocationContext(invocation)
}

internal fun encodeStringMap(values: Map<String, String>): String {
    val json = JSONObject()
    values.forEach { (key, value) -> json.put(key, value) }
    return json.toString()
}

internal fun decodeStringMap(json: String): Map<String, String> {
    if (json.isBlank()) return emptyMap()
    val objectValue = JSONObject(json)
    return objectValue.keys().asSequence().associateWith { key -> objectValue.optString(key, "") }
}

private fun Map<String, String>.value(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

private fun Map<String, String>.boolValue(key: String, default: Boolean): Boolean =
    when ((this[key] ?: this["input_$key"])?.trim()?.lowercase()) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> default
    }
