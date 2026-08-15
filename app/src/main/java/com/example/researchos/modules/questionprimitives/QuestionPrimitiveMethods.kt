package com.example.researchos.modules.questionprimitives

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.SettingsState
import org.json.JSONArray
import java.time.Instant

object QuestionPrimitiveFields {
    const val STATUS = "question_status"
    const val TYPE = "question_type"
    const val ID = "question_id"
    const val PROMPT = "question_prompt"
    const val HINT = "question_hint"
    const val ANSWER = "question_answer"
    const val ANSWER_JSON = "question_answer_json"
    const val VALID = "question_valid"
    const val REQUIRED = "question_required"
    const val REGEX = "question_regex"
    const val CONSTRAINT_MESSAGE = "question_constraint_message"
    const val ERROR = "question_error"
    const val OPTIONS_JSON = "question_options_json"
    const val SELECTED_COUNT = "question_selected_count"
    const val MIN = "question_min"
    const val MAX = "question_max"
    const val CAPTURED_TIME_ISO = "question_captured_time_iso"

    val outputs = listOf(
        STATUS,
        TYPE,
        ID,
        PROMPT,
        HINT,
        ANSWER,
        ANSWER_JSON,
        VALID,
        REQUIRED,
        REGEX,
        CONSTRAINT_MESSAGE,
        ERROR,
        OPTIONS_JSON,
        SELECTED_COUNT,
        MIN,
        MAX,
        CAPTURED_TIME_ISO
    )
}

abstract class QuestionPrimitiveMethod(
    override val id: String,
    private val primitiveType: String,
    private val displayName: String,
    private val descriptionText: String
) : As100Method {
    private val version = "0.1.0"
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", displayName)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Method,
        name = displayName,
        version = version,
        description = descriptionText,
        outputs = QuestionPrimitiveFields.outputs,
        graphOutputs = listOf(id),
        parameters = mapOf("category" to "Question primitive", "question_type" to primitiveType)
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
        result(request, evaluate(request.context), InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[QuestionPrimitiveFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("question-response:${values[QuestionPrimitiveFields.ID].orEmpty().ifBlank { id }}:${System.currentTimeMillis()}"),
            "QuestionResponse",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("researchos.question", id, version)
        val observation = Observation(
            phenomenon = id,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
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
            diagnostics = if (ok) emptyMap() else mapOf(QuestionPrimitiveFields.ERROR to values[QuestionPrimitiveFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun evaluate(settings: Map<String, String>): Map<String, String> {
        val questionId = settings.value("question_id") ?: id.replace('.', '_')
        val prompt = settings.value("prompt") ?: settings.value("question_prompt") ?: displayName
        val hint = settings.value("hint") ?: settings.value("question_hint") ?: ""
        val answer = settings.value("answer") ?: settings.value("question_answer") ?: ""
        val required = settings.value("required")?.toBooleanStrictOrNull() ?: false
        val regex = settings.value("regex") ?: settings.value("question_regex") ?: ""
        val constraint = settings.value("constraint_message") ?: settings.value("question_constraint_message") ?: "Answer does not meet the required constraint."
        val capturedAt = Instant.now().toString()
        val base = linkedMapOf(
            QuestionPrimitiveFields.TYPE to primitiveType,
            QuestionPrimitiveFields.ID to questionId,
            QuestionPrimitiveFields.PROMPT to prompt,
            QuestionPrimitiveFields.HINT to hint,
            QuestionPrimitiveFields.REQUIRED to required.toString(),
            QuestionPrimitiveFields.REGEX to regex,
            QuestionPrimitiveFields.CONSTRAINT_MESSAGE to constraint,
            QuestionPrimitiveFields.CAPTURED_TIME_ISO to capturedAt
        )
        val validation = validate(settings, answer, required, regex, constraint)
        return base.apply {
            put(QuestionPrimitiveFields.STATUS, if (validation.valid) "succeeded" else "failed")
            put(QuestionPrimitiveFields.ANSWER, validation.answer)
            put(QuestionPrimitiveFields.ANSWER_JSON, validation.answerJson)
            put(QuestionPrimitiveFields.VALID, validation.valid.toString())
            put(QuestionPrimitiveFields.ERROR, validation.error)
            put(QuestionPrimitiveFields.OPTIONS_JSON, validation.optionsJson)
            put(QuestionPrimitiveFields.SELECTED_COUNT, validation.selectedCount.toString())
            put(QuestionPrimitiveFields.MIN, validation.min)
            put(QuestionPrimitiveFields.MAX, validation.max)
        }
    }

    protected abstract fun validate(
        settings: Map<String, String>,
        answer: String,
        required: Boolean,
        regex: String,
        constraint: String
    ): QuestionValidation

    protected fun scalarValidation(answer: String, required: Boolean, regex: String, constraint: String): QuestionValidation {
        if (required && answer.isBlank()) return QuestionValidation(answer = answer, valid = false, error = "Answer is required.")
        val regexError = regexError(answer, regex, constraint)
        if (regexError != null) return QuestionValidation(answer = answer, valid = false, error = regexError)
        return QuestionValidation(answer = answer, valid = true)
    }

    protected fun regexError(answer: String, regex: String, constraint: String): String? {
        if (regex.isBlank() || answer.isBlank()) return null
        val compiled = runCatching { Regex(regex) }.getOrElse { return "Invalid regex: ${it.message.orEmpty()}" }
        return if (compiled.matches(answer)) null else constraint
    }

    protected fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

    protected fun options(settings: Map<String, String>): List<String> =
        (settings.value("options") ?: settings.value("question_options") ?: "")
            .split('\n', '|', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}

data class QuestionValidation(
    val answer: String,
    val answerJson: String = JSONArray(listOf(answer)).toString(),
    val valid: Boolean,
    val error: String = "",
    val optionsJson: String = "[]",
    val selectedCount: Int = if (answer.isBlank()) 0 else 1,
    val min: String = "",
    val max: String = ""
)

object QuestionTextMethod : QuestionPrimitiveMethod(
    id = "question.text",
    primitiveType = "text",
    displayName = "Text question",
    descriptionText = "Capture a free-text answer with optional required and regex validation."
) {
    override fun validate(settings: Map<String, String>, answer: String, required: Boolean, regex: String, constraint: String) =
        scalarValidation(answer, required, regex, constraint)
}

object QuestionNumberMethod : QuestionPrimitiveMethod(
    id = "question.number",
    primitiveType = "number",
    displayName = "Number question",
    descriptionText = "Capture a numeric answer with optional min, max and regex validation."
) {
    override fun validate(settings: Map<String, String>, answer: String, required: Boolean, regex: String, constraint: String): QuestionValidation {
        if (required && answer.isBlank()) return QuestionValidation(answer = answer, valid = false, error = "Answer is required.")
        val min = settings.value("min") ?: settings.value("question_min") ?: ""
        val max = settings.value("max") ?: settings.value("question_max") ?: ""
        val number = answer.toDoubleOrNull()
        if (answer.isNotBlank() && number == null) return QuestionValidation(answer = answer, valid = false, error = "Answer must be numeric.", min = min, max = max)
        min.toDoubleOrNull()?.let { if (number != null && number < it) return QuestionValidation(answer = answer, valid = false, error = "Answer is below minimum.", min = min, max = max) }
        max.toDoubleOrNull()?.let { if (number != null && number > it) return QuestionValidation(answer = answer, valid = false, error = "Answer is above maximum.", min = min, max = max) }
        val regexError = regexError(answer, regex, constraint)
        if (regexError != null) return QuestionValidation(answer = answer, valid = false, error = regexError, min = min, max = max)
        return QuestionValidation(answer = answer, valid = true, min = min, max = max)
    }
}

object QuestionSelectOneMethod : QuestionPrimitiveMethod(
    id = "question.select_one",
    primitiveType = "select_one",
    displayName = "Select one question",
    descriptionText = "Capture one selected answer from a configurable option list."
) {
    override fun validate(settings: Map<String, String>, answer: String, required: Boolean, regex: String, constraint: String): QuestionValidation {
        val opts = options(settings)
        if (required && answer.isBlank()) return QuestionValidation(answer = answer, valid = false, error = "Answer is required.", optionsJson = JSONArray(opts).toString())
        if (answer.isNotBlank() && opts.isNotEmpty() && answer !in opts) {
            return QuestionValidation(answer = answer, valid = false, error = "Answer is not in the option list.", optionsJson = JSONArray(opts).toString())
        }
        val regexError = regexError(answer, regex, constraint)
        if (regexError != null) return QuestionValidation(answer = answer, valid = false, error = regexError, optionsJson = JSONArray(opts).toString())
        return QuestionValidation(answer = answer, valid = true, optionsJson = JSONArray(opts).toString())
    }
}

object QuestionSelectMultipleMethod : QuestionPrimitiveMethod(
    id = "question.select_multiple",
    primitiveType = "select_multiple",
    displayName = "Select multiple question",
    descriptionText = "Capture multiple selected answers from a configurable option list."
) {
    override fun validate(settings: Map<String, String>, answer: String, required: Boolean, regex: String, constraint: String): QuestionValidation {
        val opts = options(settings)
        val selected = answer.split('\n', '|', ',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (required && selected.isEmpty()) {
            return QuestionValidation(answer = "", answerJson = "[]", valid = false, error = "At least one answer is required.", optionsJson = JSONArray(opts).toString(), selectedCount = 0)
        }
        val invalid = selected.filterNot { it in opts }.takeIf { opts.isNotEmpty() && it.isNotEmpty() }.orEmpty()
        if (invalid.isNotEmpty()) {
            return QuestionValidation(
                answer = selected.joinToString("|"),
                answerJson = JSONArray(selected).toString(),
                valid = false,
                error = "Selected answer is not in the option list: ${invalid.joinToString(", ")}.",
                optionsJson = JSONArray(opts).toString(),
                selectedCount = selected.size
            )
        }
        val joined = selected.joinToString("|")
        val regexError = regexError(joined, regex, constraint)
        if (regexError != null) {
            return QuestionValidation(answer = joined, answerJson = JSONArray(selected).toString(), valid = false, error = regexError, optionsJson = JSONArray(opts).toString(), selectedCount = selected.size)
        }
        return QuestionValidation(answer = joined, answerJson = JSONArray(selected).toString(), valid = true, optionsJson = JSONArray(opts).toString(), selectedCount = selected.size)
    }
}
