package com.example.methodmesh.modules.gamedeck

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

object GameDeckFields {
    const val STATUS = "gamedeck_status"
    const val RESULT = "gamedeck_result"
    const val GAME = "gamedeck_game"
    const val STATE_JSON = "gamedeck_state_json"
    const val TURN = "gamedeck_turn"
    const val WINNER = "gamedeck_winner"
    const val MOVE_COUNT = "gamedeck_move_count"
    const val SCORE = "gamedeck_score"
    const val PLAYER_STATS_JSON = "gamedeck_player_stats_json"
    const val MOVE_DATA_JSON = "gamedeck_move_data_json"
    const val RNG_MODE = "gamedeck_rng_mode"
    const val SEED = "gamedeck_seed"
    const val GENERATED_TIME_ISO = "gamedeck_generated_time_iso"
    const val AUDIT_JSON = "gamedeck_audit_json"
    const val ERROR = "gamedeck_error"

    val outputs = listOf(
        STATUS, RESULT, GAME, STATE_JSON, TURN, WINNER, MOVE_COUNT,
        SCORE, PLAYER_STATS_JSON, MOVE_DATA_JSON,
        RNG_MODE, SEED, GENERATED_TIME_ISO, AUDIT_JSON, ERROR
    )
}

object As100GameDeckMethod : As100Method {
    const val ID = "gamedeck.snapshot"
    private const val VERSION = "0.0.12"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "GameDeck snapshot")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "GameDeck",
        version = VERSION,
        description = "Return the current GameDeck game snapshot. Native use remains on the live dashboard until explicitly finished.",
        outputs = GameDeckFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Development", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val values = snapshotFromSettings(request.context)
        return result(request, values, InvocationContext.from(request.context))
    }

    fun snapshot(
        game: String,
        stateJson: String,
        turn: String,
        winner: String,
        moveCount: Int,
        rngMode: String,
        seed: String,
        session: GameDeckSession? = null,
        playerStatsJson: String = ""
    ): Map<String, String> {
        val generatedAt = Instant.now().toString()
        val publicStateJson = GameDeckSessionCodec.publicStateJson(stateJson)
        val publicSeed = GameDeckSessionCodec.publicSeed(game, stateJson, seed)
        val rngCommitment = GameDeckSessionCodec.rngCommitment(game, rngMode, seed)
        val publicSessionJson = session?.let {
            GameDeckSessionCodec.publicSessionJson(it, stateJson)
        }.orEmpty()
        val publicState = runCatching { JSONObject(publicStateJson) }.getOrElse { JSONObject() }
        val score = scoreFor(game, publicState)
        val moveDataJson = moveDataFor(game, publicState)

        val resultText = when {
            winner.isNotBlank() -> "$game — winner: $winner"
            turn.isNotBlank() -> "$game — $moveCount moves — turn: $turn"
            else -> "$game — $moveCount moves"
        }
        val audit = JSONObject()
            .put("method_id", ID)
            .put("method_version", VERSION)
            .put("generated_time_iso", generatedAt)
            .put("game", game)
            .put("move_count", moveCount)
            .put("turn", turn)
            .put("winner", winner)
            .put("rng_mode", rngMode)
            .put("seed", publicSeed.ifBlank { JSONObject.NULL })
            .put("rng_commitment", rngCommitment.ifBlank { JSONObject.NULL })
            .put("session", publicSessionJson.takeIf { it.isNotBlank() }?.let { JSONObject(it) } ?: JSONObject.NULL)
            .put("state", runCatching { JSONObject(publicStateJson) }.getOrElse { publicStateJson })

        return linkedMapOf(
            GameDeckFields.STATUS to "succeeded",
            GameDeckFields.RESULT to resultText,
            GameDeckFields.GAME to game,
            GameDeckFields.STATE_JSON to publicStateJson,
            GameDeckFields.TURN to turn,
            GameDeckFields.WINNER to winner,
            GameDeckFields.MOVE_COUNT to moveCount.toString(),
            GameDeckFields.SCORE to score,
            GameDeckFields.PLAYER_STATS_JSON to playerStatsJson,
            GameDeckFields.MOVE_DATA_JSON to moveDataJson,
            GameDeckFields.RNG_MODE to rngMode,
            GameDeckFields.SEED to publicSeed,
            GameDeckFields.GENERATED_TIME_ISO to generatedAt,
            GameDeckFields.AUDIT_JSON to audit.toString(),
            GameDeckFields.ERROR to ""
        )
    }

    private fun scoreFor(game: String, state: JSONObject): String = when (game) {
        GameDeckExtraEngine.GO_9X9 -> {
            val black = state.opt("black_score")
            val white = state.opt("white_score")
            if (black == null || black == JSONObject.NULL || white == null || white == JSONObject.NULL) {
                ""
            } else {
                "black=${state.optDouble("black_score")};white=${state.optDouble("white_score")}"
            }
        }
        GameDeckExtraEngine.REVERSI -> {
            val board = state.optJSONArray("board") ?: JSONArray()
            var p1 = 0
            var p2 = 0
            for (i in 0 until board.length()) {
                when (board.optInt(i)) {
                    1 -> p1 += 1
                    2 -> p2 += 1
                }
            }
            "p1=$p1;p2=$p2"
        }
        GameDeckExtraEngine.MEMORY -> {
            val scores = state.optJSONArray("scores") ?: JSONArray()
            "p1=${scores.optInt(0)};p2=${scores.optInt(1)}"
        }
        else -> if (state.has("score")) state.opt("score")?.toString().orEmpty() else ""
    }

    private fun moveDataFor(game: String, state: JSONObject): String {
        val out = JSONObject()
            .put("game", game)
            .put("move_count", state.optInt("moves", 0))

        when {
            state.has("move_log") -> {
                out.put("moves", state.optJSONArray("move_log") ?: JSONArray())
            }
            game == GameDeckExtraEngine.CODEBREAKER && state.has("history") -> {
                out.put("moves", state.optJSONArray("history") ?: JSONArray())
            }
            else -> {
                // Every game still returns useful terminal play data even when
                // that engine predates the explicit event-log contract.
                out.put("final_state", state)
            }
        }
        return out.toString()
    }

    private fun snapshotFromSettings(settings: Map<String, String>): Map<String, String> {
        val game = settings["game"] ?: settings["input_game"] ?: GameDeckEngine.LAUNCHER
        val rngMode = settings["rng_mode"] ?: settings["input_rng_mode"] ?: "secure_random"
        val seed = settings["seed"] ?: settings["input_seed"] ?: ""
        val defaultState = if (GameDeckExtraEngine.isSupported(game)) {
            GameDeckExtraEngine.newStateJson(game, rngMode, seed)
        } else {
            GameDeckEngine.newStateJson(game)
        }
        return snapshot(
            game = game,
            stateJson = settings["state_json"] ?: settings["input_state_json"] ?: defaultState,
            turn = settings["turn"].orEmpty(),
            winner = settings["winner"].orEmpty(),
            moveCount = settings["move_count"]?.toIntOrNull() ?: 0,
            rngMode = rngMode,
            seed = seed
        )
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[GameDeckFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("GameDeckSnapshot:${System.currentTimeMillis()}"),
            "GameDeckSnapshot",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.gamedeck", ID, VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
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
            diagnostics = if (ok) emptyMap() else mapOf(GameDeckFields.ERROR to values[GameDeckFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
