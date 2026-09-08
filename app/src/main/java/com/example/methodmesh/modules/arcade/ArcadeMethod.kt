package com.example.methodmesh.modules.arcade

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
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.Instant

object ArcadeFields {
    const val STATUS = "arcade_status"
    const val GAME = "arcade_game"
    const val STATE = "arcade_state_json"
    const val SCORE = "arcade_score"
    const val TICK = "arcade_tick"
    const val FINISHED = "arcade_finished"
    const val WINNER = "arcade_winner"
    const val SESSION_ID = "arcade_session_id"
    const val RNG_MODE = "arcade_rng_mode"
    const val RESULT = "arcade_result"
    const val PLAYER_STATS_JSON = "arcade_player_stats_json"
    const val PLAY_DATA_JSON = "arcade_play_data_json"
    const val GENERATED = "arcade_generated_time_iso"
    const val ERROR = "arcade_error"

    val outputs = listOf(
        STATUS,
        GAME,
        STATE,
        SCORE,
        TICK,
        FINISHED,
        WINNER,
        SESSION_ID,
        RNG_MODE,
        RESULT,
        PLAYER_STATS_JSON,
        PLAY_DATA_JSON,
        GENERATED,
        ERROR
    )
}

object As100ArcadeMethod : As100Method {
    const val ID = "arcade.snapshot"
    private const val VERSION = "0.0.9"

    override val id = ID
    override val ref = ArchitectureRef(
        ArchitectureId(ID),
        "Method",
        "Arcade snapshot"
    )

    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Arcade",
        version = VERSION,
        description = "Return the current lightweight fixed-step arcade state snapshot.",
        outputs = ArcadeFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Development",
            "status" to "Development"
        )
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
        signals: List<com.example.methodmesh.core.methodmesh.Signal>,
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
        val game =
            request.context["game"]
                ?: request.context["input_game"]
                ?: ArcadeEngine.LAUNCHER
        val rngMode =
            request.context["rng_mode"]
                ?: request.context["input_rng_mode"]
                ?: "secure_random"
        val seed =
            request.context["seed"]
                ?: request.context["input_seed"]
                ?: ""
        val sessionId =
            request.context["session_id"]
                ?: request.context["input_session_id"]
                ?: ""
        val snakeSpeedCps =
            (request.context["snake_speed_cps"]
                ?: request.context["input_snake_speed_cps"])
                ?.toIntOrNull()
                ?.let { ArcadeSnakeSpeed.normalizeCps(it) }
                ?: ArcadeSnakeSpeed.fromLegacy(
                    request.context["snake_speed"]
                        ?: request.context["input_snake_speed"]
                )
        val pongCpuDifficulty =
            request.context["pong_cpu_difficulty"]
                ?: request.context["input_pong_cpu_difficulty"]
                ?: "standard"
        val breakoutStartLevel =
            (request.context["breakout_start_level"]
                ?: request.context["input_breakout_start_level"])
                ?.toIntOrNull()
                ?.coerceIn(1, 5)
                ?: 1
        val dodgeDifficulty =
            request.context["dodge_difficulty"]
                ?: request.context["input_dodge_difficulty"]
                ?: "normal"
        val state =
            request.context["state_json"]
                ?: request.context["input_state_json"]
                ?: ArcadeEngine.newState(
                    game = game,
                    rngMode = rngMode,
                    seed = seed,
                    snakeSpeedCps = snakeSpeedCps,
                    pongCpuDifficulty = pongCpuDifficulty,
                    breakoutStartLevel = breakoutStartLevel,
                    dodgeDifficulty = dodgeDifficulty
                )

        return result(
            request = request,
            values = snapshot(
                game = game,
                stateJson = state,
                sessionId = sessionId,
                rngMode = rngMode,
                playerStatsJson = request.context["player_stats_json"].orEmpty()
            ),
            invocation = InvocationContext.from(request.context)
        )
    }

    fun snapshot(
        game: String,
        stateJson: String,
        sessionId: String,
        rngMode: String,
        playerStatsJson: String = ""
    ): Map<String, String> {
        val summary = ArcadeEngine.summary(stateJson)
        val generatedAt = Instant.now().toString()
        val resultText = when (game) {
            ArcadeEngine.SNAKE -> {
                val s = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
                val length = s.optJSONArray("body")?.length() ?: 0
                "Snake Sprint — score ${summary.score} — length $length"
            }
            ArcadeEngine.PONG -> summary.detail
            ArcadeEngine.BREAKOUT -> "Wall Break — ${summary.detail}"
            ArcadeEngine.DODGE -> "Lane Dodge — ${summary.detail}"
            else -> "Arcade"
        }

        val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
        val playData = JSONObject()
            .put("game", game)
            .put("score", summary.score)
            .put("tick", summary.tick)
            .put("finished", summary.finished)
            .put("winner", summary.winner)
            .apply {
                when (game) {
                    ArcadeEngine.SNAKE -> {
                        put("length", state.optJSONArray("body")?.length() ?: 0)
                        put("speed_cps", state.optInt("speed_cps", 0))
                        put("reason", state.optString("reason"))
                    }
                    ArcadeEngine.PONG -> {
                        put("p1_score", state.optInt("p1_score", 0))
                        put("p2_score", state.optInt("p2_score", 0))
                        put("cpu", state.optBoolean("cpu", true))
                        put("cpu_difficulty", state.optString("cpu_difficulty", "standard"))
                        put("rally", state.optInt("rally", 0))
                    }
                    ArcadeEngine.BREAKOUT -> {
                        put("level", state.optInt("level", 1))
                        put("start_level", state.optInt("start_level", state.optInt("level", 1)))
                        put("total_levels", state.optInt("total_levels", 1))
                        put("lives", state.optInt("lives", 0))
                    }
                    ArcadeEngine.DODGE -> {
                        put("difficulty", state.optString("difficulty", "normal"))
                        put("level", state.optInt("level", 1))
                        put("lives", state.optInt("lives", 0))
                    }
                }
            }
            .toString()

        return linkedMapOf(
            ArcadeFields.STATUS to "succeeded",
            ArcadeFields.GAME to game,
            ArcadeFields.STATE to stateJson,
            ArcadeFields.SCORE to summary.score.toString(),
            ArcadeFields.TICK to summary.tick.toString(),
            ArcadeFields.FINISHED to summary.finished.toString(),
            ArcadeFields.WINNER to summary.winner,
            ArcadeFields.SESSION_ID to sessionId,
            ArcadeFields.RNG_MODE to rngMode,
            ArcadeFields.RESULT to resultText,
            ArcadeFields.PLAYER_STATS_JSON to playerStatsJson,
            ArcadeFields.PLAY_DATA_JSON to playData,
            ArcadeFields.GENERATED to generatedAt,
            ArcadeFields.ERROR to ""
        )
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[ArcadeFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("ArcadeSnapshot:${System.currentTimeMillis()}"),
            "ArcadeSnapshot",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext(
            "methodmesh.arcade",
            ID,
            VERSION
        )
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
            outputs = listOf(
                ArchitectureRef(
                    observation.id,
                    observation.objectType,
                    observation.phenomenon
                )
            ),
            status = if (ok) {
                TransformationStatus.Succeeded
            } else {
                TransformationStatus.Failed
            },
            temporalContext = request.temporalContext,
            provenance = provenance
        )

        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) {
                emptyMap()
            } else {
                mapOf(
                    ArcadeFields.ERROR to
                        values[ArcadeFields.ERROR].orEmpty()
                )
            }
        ).withInvocationContext(invocation)
    }
}
