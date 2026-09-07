package com.example.methodmesh.modules.clinicalinstruments

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
import org.json.JSONObject
import java.time.Instant

object ClinicalInstrumentFields {
    const val STATUS = "clinical_status"
    const val RESULT = "clinical_result"
    const val ANSWERS = "clinical_answers"
    // CORE-friendly transport aliases: OutputFormatter intentionally suppresses
    // *_id and hash/sha fields from compact flat payloads. These aliases let ODK
    // store the exact protocol identity without parsing the full JSON envelope.
    const val INSTRUMENT = "clinical_instrument"
    const val VERSION = "clinical_version"
    const val DEFINITION_FINGERPRINT = "clinical_definition_fingerprint"
    const val SESSION = "clinical_session"
    const val INSTRUMENT_ID = "clinical_instrument_id"
    const val INSTRUMENT_NAME = "clinical_instrument_name"
    const val INSTRUMENT_VERSION = "clinical_instrument_version"
    const val DEFINITION_SHA256 = "clinical_definition_sha256"
    const val SUBJECT_ID = "clinical_subject_id"
    const val SESSION_ID = "clinical_session_id"
    const val RESPONSES_JSON = "clinical_responses_json"
    const val DERIVED_JSON = "clinical_derived_json"
    const val SCORE = "clinical_score"
    const val CLASSIFICATION = "clinical_classification"
    const val RESULT_JSON = "clinical_result_json"
    const val COMPLETED_TIME_ISO = "clinical_completed_time_iso"
    const val ERROR = "clinical_error"
    val outputs = listOf(
        STATUS, RESULT, ANSWERS, INSTRUMENT, VERSION, DEFINITION_FINGERPRINT, SESSION,
        INSTRUMENT_ID, INSTRUMENT_NAME, INSTRUMENT_VERSION, DEFINITION_SHA256,
        SUBJECT_ID, SESSION_ID, RESPONSES_JSON, DERIVED_JSON, SCORE,
        CLASSIFICATION, RESULT_JSON, COMPLETED_TIME_ISO, ERROR
    )
}

object As100ClinicalInstrumentsMethod : As100Method {
    const val ID = "clinical.instrument.run"
    private const val VERSION = "0.4.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Run clinical instrument")
    private val coreFlatOutputs by lazy {
        ClinicalInstrumentCoreDefinitions.definitions.flatMap { definition ->
            definition.questions.map { "clinical_response_${safeOutputId(it.id)}" } +
                definition.derived.map { "clinical_derived_${safeOutputId(it.id)}" } +
                definition.scores.map { "clinical_score_${safeOutputId(it.id)}" }
        }.distinct()
    }

    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Clinical Instruments",
        version = VERSION,
        description = "Run a versioned offline linear clinical checklist or score and return observations, derived values, result and provenance.",
        outputs = ClinicalInstrumentFields.outputs + coreFlatOutputs,
        graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Clinical", "status" to "Development", "network" to "none")
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
        val instrumentId = request.context.value("instrument_id").orEmpty()
        val answers = request.context.value("answers_json").orEmpty()
        if (instrumentId.isBlank() || answers.isBlank()) {
            return failure(request, "Interactive execution requires the Clinical Instruments capability screen; non-interactive execution requires instrument_id and answers_json.")
        }
        val definition = ClinicalInstrumentCoreDefinitions.definitions.firstOrNull { it.id == instrumentId }
            ?: return failure(request, "Instrument '$instrumentId' was not found in the built-in non-interactive registry.")
        val json = runCatching { JSONObject(answers) }.getOrElse { return failure(request, "answers_json is not valid JSON: ${it.message}") }
        val responses = json.keys().asSequence().associateWith { json.get(it).toString() }
        val run = ClinicalInstrumentEngine.run(definition, responses)
        return result(request, run, subjectId = request.context.value("subject_id").orEmpty(), sessionId = "", invocation = InvocationContext.from(request.context))
    }

    fun result(
        request: ExecutionRequest,
        run: ClinicalRunResult,
        subjectId: String,
        sessionId: String,
        invocation: InvocationContext?
    ): ExecutionResult {
        val values = values(run, subjectId, sessionId)
        val ok = run.completed && run.error.isBlank()
        val provenance = ProvenanceContext("methodmesh.clinicalinstruments", ID, VERSION)
        val entity = Entity(ArchitectureId("clinical-instrument:${sessionId.ifBlank { System.currentTimeMillis().toString() }}"), "ClinicalInstrumentRun", temporalContext = request.temporalContext)
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
            diagnostics = if (ok) emptyMap() else mapOf(ClinicalInstrumentFields.ERROR to run.error)
        ).withInvocationContext(invocation)
    }

    fun requestForUi(context: Map<String, String>) = request(ID, context, emptyList(), emptyList())

    private fun values(run: ClinicalRunResult, subjectId: String, sessionId: String): Map<String, String> {
        val score = run.scores.values.firstOrNull()?.plain().orEmpty()
        val classification = run.classification?.value.orEmpty()
        val classificationLabel = run.classification?.label.orEmpty()
        val resultText = when {
            score.isNotBlank() && classificationLabel.isNotBlank() -> "${run.instrument.name}: $score — $classificationLabel"
            classificationLabel.isNotBlank() -> "${run.instrument.name}: $classificationLabel"
            score.isNotBlank() -> "${run.instrument.name}: $score"
            else -> "${run.instrument.name}: completed"
        }
        val answersText = run.instrument.questions.mapIndexed { index, question ->
            val raw = run.responses[question.id].orEmpty()
            "${index + 1}. ${question.label} — ${clinicalAnswerForOutput(question, raw)}"
        }.joinToString("\n")
        val responsesJson = JSONObject(run.responses).toString()
        val derivedJson = JSONObject(run.derived.mapValues { it.value.plain() }).toString()
        val scoresJson = JSONObject(run.scores.mapValues { it.value.plain() })
        val audit = JSONObject()
            .put("schema", "methodmesh.clinical-result.v1")
            .put("run", JSONObject().put("id", sessionId).put("subject_id", subjectId).put("status", if (run.completed) "completed" else "failed"))
            .put("instrument", JSONObject()
                .put("id", run.instrument.id)
                .put("name", run.instrument.name)
                .put("version", run.instrument.version)
                .put("definition_sha256", run.instrument.definitionSha256)
                .put("type", run.instrument.type)
                .put("category", run.instrument.category))
            .put("responses", JSONObject(run.responses))
            .put("derived", JSONObject(run.derived.mapValues { it.value.plain() }))
            .put("scores", scoresJson)
            .put("classification", run.classification?.let { JSONObject().put("value", it.value).put("label", it.label) } ?: JSONObject.NULL)
            .put("provenance", JSONObject()
                .put("source_url", run.instrument.sourceUrl)
                .put("citation", run.instrument.citation)
                .put("rights_status", run.instrument.rightsStatus)
                .put("definition_sha256", run.instrument.definitionSha256))
        val now = Instant.now().toString()
        val values = linkedMapOf(
            ClinicalInstrumentFields.STATUS to if (run.completed && run.error.isBlank()) "succeeded" else "failed",
            ClinicalInstrumentFields.RESULT to resultText,
            ClinicalInstrumentFields.ANSWERS to answersText,
            ClinicalInstrumentFields.INSTRUMENT to run.instrument.id,
            ClinicalInstrumentFields.VERSION to run.instrument.version,
            ClinicalInstrumentFields.DEFINITION_FINGERPRINT to run.instrument.definitionSha256,
            ClinicalInstrumentFields.SESSION to sessionId,
            ClinicalInstrumentFields.INSTRUMENT_ID to run.instrument.id,
            ClinicalInstrumentFields.INSTRUMENT_NAME to run.instrument.name,
            ClinicalInstrumentFields.INSTRUMENT_VERSION to run.instrument.version,
            ClinicalInstrumentFields.DEFINITION_SHA256 to run.instrument.definitionSha256,
            ClinicalInstrumentFields.SUBJECT_ID to subjectId,
            ClinicalInstrumentFields.SESSION_ID to sessionId,
            ClinicalInstrumentFields.RESPONSES_JSON to responsesJson,
            ClinicalInstrumentFields.DERIVED_JSON to derivedJson,
            ClinicalInstrumentFields.SCORE to score,
            ClinicalInstrumentFields.CLASSIFICATION to classification,
            ClinicalInstrumentFields.RESULT_JSON to audit.toString(),
            ClinicalInstrumentFields.COMPLETED_TIME_ISO to now,
            ClinicalInstrumentFields.ERROR to run.error
        )
        // Flat mirrors are useful to ODK/XLSForm callers that know the instrument
        // schema. The JSON fields remain the stable generic contract for arbitrary
        // local instruments. Prefixing avoids collisions with MethodMesh metadata.
        run.responses.forEach { (id, value) -> values["clinical_response_${safeOutputId(id)}"] = value }
        run.derived.forEach { (id, value) -> values["clinical_derived_${safeOutputId(id)}"] = value.plain() }
        run.scores.forEach { (id, value) -> values["clinical_score_${safeOutputId(id)}"] = value.plain() }
        return values
    }


    private fun clinicalAnswerForOutput(question: ClinicalQuestion, raw: String): String {
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

    private fun failure(request: ExecutionRequest, message: String): ExecutionResult {
        val fallback = ClinicalInstrumentDefinition(
            schema = "methodmesh.clinical-instrument.v1", id = "unknown", name = "Clinical instrument", version = "0.0.0",
            status = InstrumentStatus.CORE, type = "unknown", category = "unknown", tags = emptyList(), summary = "",
            sourceUrl = "", citation = "", rightsStatus = "", rightsNote = "", questions = emptyList(), derived = emptyList(), scores = emptyList(),
            classifications = emptyList(), tests = emptyList(), rawYaml = "", definitionSha256 = ""
        )
        return result(request, ClinicalRunResult(fallback, emptyMap(), emptyMap(), emptyMap(), null, false, message), "", "", InvocationContext.from(request.context))
    }

    private fun Map<String, String>.value(key: String): String? = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

    private fun safeOutputId(value: String): String = value.replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
}
