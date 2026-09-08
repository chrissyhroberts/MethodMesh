package com.example.methodmesh.modules.texttools

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
import java.time.Instant

object TextCommonFields {
    const val STATUS = "text_status"
    const val OPERATION = "text_operation"
    const val INPUT_CHARACTERS = "text_input_characters"
    const val OUTPUT_CHARACTERS = "text_output_characters"
    const val PROCESSED_TIME_ISO = "text_processed_time_iso"
    const val ERROR = "text_error"

    val outputs = listOf(
        STATUS,
        OPERATION,
        INPUT_CHARACTERS,
        OUTPUT_CHARACTERS,
        PROCESSED_TIME_ISO,
        ERROR
    )
}

abstract class BaseTextToolMethod(
    final override val id: String,
    name: String,
    description: String,
    val coreField: String,
    extraOutputs: List<String>,
    val previewFields: List<String> = listOf(coreField)
) : As100Method {
    private val version = "0.1.0"
    val declaredOutputs: List<String> = (listOf(coreField) + extraOutputs + TextCommonFields.outputs).distinct()

    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)

    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = name,
        version = version,
        description = description,
        outputs = declaredOutputs,
        graphOutputs = listOf(id),
        parameters = mapOf("category" to "Text", "status" to "Development")
    )

    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(
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

    final override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult =
        result(request, process(request.context), InvocationContext.from(request.context))

    fun process(settings: Map<String, String>): Map<String, String> {
        val input = settings["text"] ?: settings["input_text"] ?: ""
        val outcome = try {
            transform(settings)
        } catch (e: Exception) {
            TextProcessOutcome("", error = e.message ?: e.javaClass.simpleName)
        }

        val values = linkedMapOf<String, String>()
        values[coreField] = if (outcome.error == null) outcome.result else ""
        outcome.extras.forEach { (key, value) -> values[key] = value }
        values[TextCommonFields.STATUS] = if (outcome.error == null) "succeeded" else "failed"
        values[TextCommonFields.OPERATION] = id
        values[TextCommonFields.INPUT_CHARACTERS] = input.length.toString()
        values[TextCommonFields.OUTPUT_CHARACTERS] = values[coreField].orEmpty().length.toString()
        values[TextCommonFields.PROCESSED_TIME_ISO] = Instant.now().toString()
        values[TextCommonFields.ERROR] = outcome.error.orEmpty()
        return values
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[TextCommonFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("text-result:${id}:${System.currentTimeMillis()}"),
            "TextResult",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.text", id, version)
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
            diagnostics = if (ok) emptyMap() else mapOf(TextCommonFields.ERROR to values[TextCommonFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    protected abstract fun transform(settings: Map<String, String>): TextProcessOutcome
}

object TextCleanFields {
    const val RESULT = "text_cleaned"
    val extras = listOf(
        "text_clean_operations",
        "text_unicode_normalization",
        "text_characters_removed",
        "text_lines_before",
        "text_lines_after"
    )
}

object As100TextCleanMethod : BaseTextToolMethod(
    id = "text.clean",
    name = "Clean text",
    description = "Trim and normalize text locally using deterministic cleanup operations.",
    coreField = TextCleanFields.RESULT,
    extraOutputs = TextCleanFields.extras,
    previewFields = listOf(TextCleanFields.RESULT, "text_characters_removed", "text_lines_after")
) {
    const val ID = "text.clean"
    override fun transform(settings: Map<String, String>) = TextProcessing.clean(settings)
}

object TextCaseFields {
    const val RESULT = "text_case_result"
    val extras = listOf("text_case_mode", "text_case_locale_policy")
}

object As100TextCaseMethod : BaseTextToolMethod(
    id = "text.case",
    name = "Change text case",
    description = "Convert text to lower, upper, title, sentence, or toggled case.",
    coreField = TextCaseFields.RESULT,
    extraOutputs = TextCaseFields.extras
) {
    const val ID = "text.case"
    override fun transform(settings: Map<String, String>) = TextProcessing.changeCase(settings)
}

object TextReplaceFields {
    const val RESULT = "text_replaced"
    val extras = listOf(
        "text_matches_found",
        "text_replacements_made",
        "text_replace_mode",
        "text_replace_scope"
    )
}

object As100TextReplaceMethod : BaseTextToolMethod(
    id = "text.replace",
    name = "Find and replace",
    description = "Replace literal text or explicit regular-expression matches.",
    coreField = TextReplaceFields.RESULT,
    extraOutputs = TextReplaceFields.extras,
    previewFields = listOf(TextReplaceFields.RESULT, "text_matches_found", "text_replacements_made")
) {
    const val ID = "text.replace"
    override fun transform(settings: Map<String, String>) = TextProcessing.replace(settings)
}

object TextLinesFields {
    const val RESULT = "text_lines_result"
    val extras = listOf(
        "text_line_operation",
        "text_input_line_count",
        "text_output_line_count",
        "text_duplicate_count",
        "text_blank_line_count"
    )
}

object As100TextLinesMethod : BaseTextToolMethod(
    id = "text.lines",
    name = "Process lines",
    description = "Sort, deduplicate, filter, reverse, number, or subset line-oriented text.",
    coreField = TextLinesFields.RESULT,
    extraOutputs = TextLinesFields.extras,
    previewFields = listOf(TextLinesFields.RESULT, "text_output_line_count", "text_duplicate_count")
) {
    const val ID = "text.lines"
    override fun transform(settings: Map<String, String>) = TextProcessing.lines(settings)
}

object TextSplitJoinFields {
    const val RESULT = "text_split_join_result"
    val extras = listOf("text_split_join_operation", "text_item_count", "text_delimiter_mode")
}

object As100TextSplitJoinMethod : BaseTextToolMethod(
    id = "text.split_join",
    name = "Split or join text",
    description = "Convert between delimited text and one-item-per-line text.",
    coreField = TextSplitJoinFields.RESULT,
    extraOutputs = TextSplitJoinFields.extras,
    previewFields = listOf(TextSplitJoinFields.RESULT, "text_item_count")
) {
    const val ID = "text.split_join"
    override fun transform(settings: Map<String, String>) = TextProcessing.splitJoin(settings)
}

object TextCountFields {
    const val RESULT = "text_count_summary"
    val extras = listOf(
        "text_character_count",
        "text_codepoint_count",
        "text_non_whitespace_character_count",
        "text_word_count",
        "text_unique_word_count",
        "text_line_count",
        "text_non_empty_line_count",
        "text_paragraph_count",
        "text_utf8_byte_count"
    )
}

object As100TextCountMethod : BaseTextToolMethod(
    id = "text.count",
    name = "Count text",
    description = "Count characters, code points, words, lines, paragraphs, and UTF-8 bytes.",
    coreField = TextCountFields.RESULT,
    extraOutputs = TextCountFields.extras,
    previewFields = listOf(
        "text_word_count",
        "text_character_count",
        "text_line_count",
        "text_paragraph_count",
        "text_utf8_byte_count"
    )
) {
    const val ID = "text.count"
    override fun transform(settings: Map<String, String>) = TextProcessing.count(settings)
}

object TextExtractFields {
    const val RESULT = "text_extract_result"
    val extras = listOf("text_extract_operation", "text_match_count", "text_matches_json")
}

object As100TextExtractMethod : BaseTextToolMethod(
    id = "text.extract",
    name = "Extract from text",
    description = "Extract structural portions or syntactic patterns such as emails, URLs, numbers, and custom regex matches.",
    coreField = TextExtractFields.RESULT,
    extraOutputs = TextExtractFields.extras,
    previewFields = listOf(TextExtractFields.RESULT, "text_match_count")
) {
    const val ID = "text.extract"
    override fun transform(settings: Map<String, String>) = TextProcessing.extract(settings)
}

object TextTruncateFields {
    const val RESULT = "text_truncated"
    val extras = listOf(
        "text_original_length",
        "text_result_length",
        "text_limit",
        "text_limit_unit",
        "text_was_truncated"
    )
}

object As100TextTruncateMethod : BaseTextToolMethod(
    id = "text.truncate",
    name = "Truncate text",
    description = "Restrict text to a deterministic character, word, line, or UTF-8 byte limit.",
    coreField = TextTruncateFields.RESULT,
    extraOutputs = TextTruncateFields.extras,
    previewFields = listOf(TextTruncateFields.RESULT, "text_result_length", "text_was_truncated")
) {
    const val ID = "text.truncate"
    override fun transform(settings: Map<String, String>) = TextProcessing.truncate(settings)
}

object TextSlugFields {
    const val RESULT = "text_slug"
    val extras = listOf("text_slug_separator", "text_slug_lowercase", "text_slug_ascii_only")
}

object As100TextSlugMethod : BaseTextToolMethod(
    id = "text.slug",
    name = "Make slug",
    description = "Convert text to a filename- or identifier-friendly slug.",
    coreField = TextSlugFields.RESULT,
    extraOutputs = TextSlugFields.extras
) {
    const val ID = "text.slug"
    override fun transform(settings: Map<String, String>) = TextProcessing.slug(settings)
}

object TextEncodeFields {
    const val RESULT = "text_encoded_result"
    val extras = listOf("text_encoding_operation")
}

object As100TextEncodeMethod : BaseTextToolMethod(
    id = "text.encode",
    name = "Encode or decode text",
    description = "Apply reversible Base64, URL, hexadecimal, or basic HTML entity encoding.",
    coreField = TextEncodeFields.RESULT,
    extraOutputs = TextEncodeFields.extras
) {
    const val ID = "text.encode"
    override fun transform(settings: Map<String, String>) = TextProcessing.encode(settings)
}
