package com.example.methodmesh.modules.scoring

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

object ScoringFields {
    const val STATUS = "score_status"
    const val SESSION_ID = "score_session_id"
    const val SESSION_STATUS = "score_session_status"
    const val RESULT = "score_result"
    const val WINNER = "score_winner"
    const val RULESET = "score_ruleset"
    const val PARTICIPANTS_JSON = "score_participants_json"
    const val SCORES_JSON = "score_scores_json"
    const val EVENT_COUNT = "score_event_count"
    const val STARTED_AT = "score_started_at"
    const val UPDATED_AT = "score_updated_at"
    const val FINISHED_AT = "score_finished_at"
    const val CURRENT_STATE_JSON = "score_current_state_json"
    const val FULL_JSON = "score_full_json"
    const val ERROR = "score_error"

    val outputs = listOf(
        STATUS, SESSION_ID, SESSION_STATUS, RESULT, WINNER, RULESET,
        PARTICIPANTS_JSON, SCORES_JSON, EVENT_COUNT, STARTED_AT, UPDATED_AT,
        FINISHED_AT, CURRENT_STATE_JSON, FULL_JSON, ERROR
    )
}

class ScoringAs100Method(
    override val id: String,
    name: String,
    description: String,
    private val graphOutput: String
) : As100Method {
    private val version = "0.2.0"
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = name,
        version = version,
        description = description,
        outputs = ScoringFields.outputs,
        graphOutputs = listOf(graphOutput),
        parameters = mapOf("category" to "Scoring", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        return failure(request, "Scoring is an interactive/session capability and must be executed through its capability screen.", InvocationContext.from(request.context))
    }

    fun result(request: ExecutionRequest, session: ScoreSession, invocation: InvocationContext?): ExecutionResult {
        val values = sessionValues(session)
        val entity = Entity(ArchitectureId("score-session:${session.id}"), "ScoreSession", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("methodmesh.scoring", id, version)
        val observation = Observation(
            phenomenon = graphOutput,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request, TransformationStatus.Succeeded,
            entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation)
        ).withInvocationContext(invocation)
    }

    fun failure(request: ExecutionRequest, message: String, invocation: InvocationContext?): ExecutionResult {
        val values = linkedMapOf(
            ScoringFields.STATUS to "failed", ScoringFields.SESSION_ID to "",
            ScoringFields.SESSION_STATUS to "failed", ScoringFields.RESULT to "",
            ScoringFields.WINNER to "", ScoringFields.RULESET to "",
            ScoringFields.PARTICIPANTS_JSON to "[]", ScoringFields.SCORES_JSON to "[]",
            ScoringFields.EVENT_COUNT to "0", ScoringFields.STARTED_AT to "",
            ScoringFields.UPDATED_AT to "", ScoringFields.FINISHED_AT to "",
            ScoringFields.CURRENT_STATE_JSON to "{}", ScoringFields.FULL_JSON to "{}",
            ScoringFields.ERROR to message
        )
        val provenance = ProvenanceContext("methodmesh.scoring", id, version)
        val observation = Observation(
            phenomenon = "score.failed",
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id, method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Failed, temporalContext = request.temporalContext, provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request, TransformationStatus.Failed,
            observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = mapOf(ScoringFields.ERROR to message)
        ).withInvocationContext(invocation)
    }

    private fun sessionValues(session: ScoreSession): Map<String, String> = linkedMapOf(
        ScoringFields.STATUS to "succeeded",
        ScoringFields.SESSION_ID to session.id,
        ScoringFields.SESSION_STATUS to session.status.name.lowercase(),
        ScoringFields.RESULT to ScoringEngine.resultText(session),
        ScoringFields.WINNER to ScoringEngine.winner(session),
        ScoringFields.RULESET to session.ruleset,
        ScoringFields.PARTICIPANTS_JSON to JSONArray().apply { session.participants.forEach { put(it.label) } }.toString(),
        ScoringFields.SCORES_JSON to JSONArray().apply { session.participants.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("score", it.score)) } }.toString(),
        ScoringFields.EVENT_COUNT to session.events.size.toString(),
        ScoringFields.STARTED_AT to session.startedAtIso,
        ScoringFields.UPDATED_AT to session.updatedAtIso,
        ScoringFields.FINISHED_AT to session.finishedAtIso.orEmpty(),
        ScoringFields.CURRENT_STATE_JSON to session.stateJson,
        ScoringFields.FULL_JSON to session.toJson().toString(),
        ScoringFields.ERROR to ""
    )
}

object ScoringMethods {
    val Counter = ScoringAs100Method("score.counter", "Counter", "Persistent one- or multi-entity numeric counter.", "score.counter")
    val Tally = ScoringAs100Method("score.tally", "Tally", "Fast persistent categorical tallying.", "score.tally")
    val Match = ScoringAs100Method("score.match", "Match score", "Generic head-to-head or team match scoring.", "score.match")
    val Rounds = ScoringAs100Method("score.rounds", "Round scoring", "Accumulate scores across rounds.", "score.rounds")
    val RaceTo = ScoringAs100Method("score.race_to", "Race to target", "First participant to a configured target score.", "score.race_to")
    val SetMatch = ScoringAs100Method("score.set_match", "Set match", "Hierarchical point/game/set match scoring.", "score.set_match")
    val Sports = ScoringAs100Method("score.sports", "Sports scorer", "Rule-driven sports scoring with persistent sessions.", "score.sports")
    val HighScore = ScoringAs100Method("score.high_score", "High scores", "Explicitly save and rank scores or personal bests.", "score.high_score")
    val SessionRead = ScoringAs100Method("score.session.read", "Read score session", "Read a persistent score session without altering it.", "score.session.read")
    val SessionResume = ScoringAs100Method("score.session.resume", "Resume score session", "Resume a persistent active or paused score session.", "score.session.resume")
    val SessionFinish = ScoringAs100Method("score.session.finish", "Finish score session", "Mark a persistent score session complete and return its result.", "score.session.finish")

    val all = listOf(Counter, Tally, Match, Rounds, RaceTo, SetMatch, Sports, HighScore, SessionRead, SessionResume, SessionFinish)
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
