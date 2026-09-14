package com.example.methodmesh.transport

import com.example.methodmesh.core.ResearchGraph
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus

/**
 * Formats MethodMesh results for caller-facing transport.
 *
 * The default contract is deliberately compact: one canonical key per value.
 * The complete graph remains in MethodMesh and can be queried explicitly with
 * return selectors.
 */
object OutputFormatter {
    object PayloadMode {
        const val CORE = "CORE"
        const val AUDIT = "AUDIT"
        const val FULL = "FULL"

        fun normalize(value: String): String = when (value.trim().uppercase()) {
            AUDIT -> AUDIT
            FULL -> FULL
            else -> CORE
        }
    }

    /** Compact, caller-facing fields. */
    fun fields(result: ExecutionResult, includeProvenance: Boolean = true): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>()
        fields["methodmesh_execution_id"] = result.request.id.value
        fields["methodmesh_method_id"] = result.request.method.id.value
        fields["methodmesh_status"] = result.status.name

        copyContext(result, fields)
        appendObservationsCompact(result, fields, includeProvenance)
        appendStatesCompact(result, fields)
        appendEntitiesCompact(result, fields)

        // Diagnostics are useful on failure, but should not inflate every normal ODK return.
        if (result.status != TransformationStatus.Succeeded) {
            result.diagnostics.forEach { (key, value) -> fields["diagnostic_$key"] = value }
        }
        return fields
    }

    fun fields(
        result: ExecutionResult,
        includeProvenance: Boolean = true,
        payloadMode: String
    ): Map<String, Any?> = projectFields(fields(result, includeProvenance), payloadMode, result.status)

    fun selectedFields(
        result: ExecutionResult,
        selectors: List<GraphSelector>,
        graph: ResearchGraph? = null,
        includeProvenance: Boolean = true
    ): Map<String, Any?> {
        if (selectors.isEmpty()) return fields(result, includeProvenance)
        val selected = linkedMapOf<String, Any?>()
        selected["methodmesh_execution_id"] = result.request.id.value
        selected["methodmesh_status"] = result.status.name
        selected.putAll(GraphSelectorResolver.resolve(selectors, result, graph))
        return selected
    }

    fun format(
        result: ExecutionResult,
        returnMode: ReturnMode,
        includeProvenance: Boolean = true,
        selectors: List<GraphSelector> = emptyList(),
        graph: ResearchGraph? = null,
        payloadMode: String = PayloadMode.FULL
    ): String = formatFields(
        projectFields(selectedFields(result, selectors, graph, includeProvenance), payloadMode, result.status),
        returnMode
    )

    fun projectFields(
        fields: Map<String, Any?>,
        payloadMode: String,
        status: TransformationStatus? = null
    ): Map<String, Any?> {
        return when (PayloadMode.normalize(payloadMode)) {
            PayloadMode.FULL -> {
                val projected = linkedMapOf<String, Any?>()
                projected.putAll(fields.filter { (key, _) -> isCoreField(key) || isFailureHelpField(key) })
                fields["methodmesh_execution_id"]?.let { projected["methodmesh_execution_id"] = it }
                fields["methodmesh_status"]?.let { projected["methodmesh_status"] = it }
                projected["methodmesh_full_json"] = formatFields(fields, ReturnMode.Json)
                projected
            }
            PayloadMode.AUDIT -> fields.filterKeys(::isAuditOrCoreField)
            else -> fields.filter { (key, _) ->
                isCoreField(key) || (status != TransformationStatus.Succeeded && isFailureHelpField(key))
            }
        }
    }

    private fun isCoreField(key: String): Boolean {
        if (key in headlineCoreFields) return true
        if (key.startsWith("methodmesh_")) return false
        if (key.startsWith("diagnostic_")) return false
        if (key in setOf("subject_id", "context_entity_id", "visit_id", "form_id", "operator_id")) return false
        if (key in calibratedScaleAuditFields) return false
        if (key in documentScanAuditFields) return false
        if (key in plusCodeAuditFields) return false
        if (key in conversationTranslateAuditFields) return false
        if (key in imageRedactionAuditFields) return false
        if (key.endsWith("_json") || key.endsWith("_payload")) return false
        if (key.endsWith("_input_text") || key.endsWith("_source_language") || key.endsWith("_target_language")) return false
        if (key.endsWith("_available_languages") || key.endsWith("_downloaded_models") || key.endsWith("_model_action")) return false
        if (key.endsWith("_error")) return false
        if (key.contains("manifest", ignoreCase = true)) return false
        if (key.contains("trace", ignoreCase = true)) return false
        if (key.contains("summary", ignoreCase = true)) return false
        if (key.contains("sha", ignoreCase = true) || key.contains("hash", ignoreCase = true)) return false
        if (key.contains("uuid", ignoreCase = true) || key.endsWith("_id")) return false
        if (key.contains("device", ignoreCase = true) || key.contains("address", ignoreCase = true)) return false
        if (key.contains("requested", ignoreCase = true) || key.contains("actual", ignoreCase = true)) return false
        if (key.contains("selection", ignoreCase = true) || key.contains("substitution", ignoreCase = true)) return false
        if (key.contains("duration", ignoreCase = true) || key.contains("interval", ignoreCase = true)) return false
        if (key.contains("sample_count", ignoreCase = true) || key.contains("mode", ignoreCase = true)) return false
        if (key.contains("status", ignoreCase = true)) return false
        if (key.endsWith("_time_iso") || key.endsWith("_at_iso")) return false
        if (key.startsWith("entity_") || key.startsWith("observation_") || key.startsWith("state_")) return false
        return true
    }

    private val headlineCoreFields = setOf(
        "barcode_payload",
        "api_value",
        "api_values_json",
        "plus_code",
        "redacted_image_uri",
        "redacted_image_sha256",
        "conversation_transcript",
        "random_first_number",
        "random_numbers_csv",
        "sampling_value",
        "sampling_result_uri",
        "sound_summary"
    )

    private val apiAuditFields = setOf(
        "api_status",
        "api_values_json",
        "api_label",
        "api_definition_id",
        "api_definition_name",
        "api_result_path",
        "api_result_paths",
        "api_provider",
        "api_http_status",
        "api_from_cache",
        "api_stale",
        "api_source_url",
        "api_response_json",
        "api_error",
        "api_retrieved_time_iso",
        "api_data_age_hours",
        "api_exchange_rate",
        "api_exchange_amount",
        "api_exchange_converted"
    )

    private fun isAuditOrCoreField(key: String): Boolean =
        isCoreField(key) ||
            key.endsWith("_audit_json") ||
            key in apiAuditFields ||
            key in calibratedScaleAuditFields ||
            key in documentScanAuditFields ||
            key in plusCodeAuditFields ||
            key in conversationTranslateAuditFields ||
            key in imageRedactionAuditFields ||
            key.startsWith("methodmesh_") ||
            key in setOf("subject_id", "context_entity_id", "visit_id", "form_id", "operator_id") ||
            key.contains("time", ignoreCase = true) ||
            key.contains("date", ignoreCase = true) ||
            key.contains("hash", ignoreCase = true) ||
            key.contains("sha", ignoreCase = true) ||
            key.contains("uuid", ignoreCase = true) ||
            key.contains("device", ignoreCase = true) ||
            key.contains("address", ignoreCase = true) ||
            key.startsWith("diagnostic_") ||
            key.contains("warning", ignoreCase = true) ||
            key.contains("error", ignoreCase = true)

    private fun isFailureHelpField(key: String): Boolean =
        key.startsWith("methodmesh_") ||
            key.startsWith("diagnostic_") ||
            key.contains("status", ignoreCase = true) ||
            key.contains("warning", ignoreCase = true) ||
            key.contains("error", ignoreCase = true)

    private val calibratedScaleAuditFields = setOf(
        "minimum",
        "maximum",
        "use_range",
        "scale_length_mm",
        "scale_length_dp",
        "dp_per_mm",
        "vertical_mode"
    )

    private val documentScanAuditFields = setOf(
        "document_scan_status",
        "document_scan_page_count",
        "document_scan_page_image_uris_json",
        "document_scan_pdf_uri",
        "document_scan_ocr_text_file_uri",
        "document_scan_ocr_page_count",
        "document_scan_mode",
        "document_scan_gallery_import_allowed",
        "document_scan_page_limit",
        "document_scan_time_iso",
        "document_scan_error"
    )

    private val plusCodeAuditFields = setOf(
        "plus_code_status",
        "plus_code_length",
        "plus_code_centroid_latitude",
        "plus_code_centroid_longitude",
        "plus_code_gps_latitude",
        "plus_code_gps_longitude",
        "plus_code_gps_accuracy_m",
        "plus_code_gps_fix_count",
        "plus_code_basemap_mode",
        "plus_code_basemap_actual_source",
        "plus_code_selected_time_iso",
        "plus_code_audit_json",
        "plus_code_error"
    )

    private val conversationTranslateAuditFields = setOf(
        "conversation_turns_json",
        "conversation_language_a",
        "conversation_language_b",
        "conversation_label_a",
        "conversation_label_b",
        "conversation_spoken_output",
        "conversation_prefer_offline",
        "conversation_turn_count",
        "conversation_started_time_iso",
        "conversation_finished_time_iso",
        "conversation_status",
        "conversation_error"
    )

    private val imageRedactionAuditFields = setOf(
        "image_redaction_status",
        "redacted_image_name",
        "redaction_mask_json",
        "redacted_cells",
        "redaction_grid_rows",
        "redaction_grid_columns",
        "redaction_style",
        "redaction_input_source",
        "redaction_created_time_iso",
        "image_redaction_error"
    )

    private fun copyContext(result: ExecutionResult, fields: LinkedHashMap<String, Any?>) {
        val subjectId = result.request.context["subject_id"]
        val contextEntityId = result.request.context["context_entity_id"]
        subjectId
            ?.takeUnless { result.isDemoPlaceholderSubject(it) }
            ?.let { fields["subject_id"] = it }
        if (contextEntityId != null && contextEntityId != subjectId && !result.isDemoPlaceholderSubject(contextEntityId)) {
            fields["context_entity_id"] = contextEntityId
        }
        listOf("visit_id", "form_id", "operator_id").forEach { key ->
            result.request.context[key]?.let { fields[key] = it }
        }
    }

    private fun appendObservationsCompact(
        result: ExecutionResult,
        fields: LinkedHashMap<String, Any?>,
        includeProvenance: Boolean
    ) {
        if (result.observations.size == 1) {
            val observation = result.observations.first()
            observation.subject?.id?.value
                ?.takeUnless { result.isDemoPlaceholderSubject(it) }
                ?.let { fields.putIfAbsent("subject_id", it) }
            observation.values.forEach { (key, value) -> fields[key] = value }
            return
        }

        // Multiple observations must remain distinguishable, but transport metadata
        // is kept to the minimum needed to interpret them. Stable graph identifiers,
        // provenance and other implementation details remain available through
        // explicit graph selectors.
        if (result.observations.isNotEmpty()) {
            fields["observation_count"] = result.observations.size
        }
        result.observations.forEachIndexed { index, observation ->
            val prefix = "observation_${index + 1}"
            fields["${prefix}_type"] = observation.phenomenon
            observation.subject?.id?.value
                ?.takeIf { it != fields["subject_id"]?.toString() }
                ?.takeUnless { result.isDemoPlaceholderSubject(it) }
                ?.let { fields["${prefix}_subject_id"] = it }
            observation.values.forEach { (key, value) -> fields["${prefix}_${key}"] = value }
        }
    }

    private fun appendStatesCompact(result: ExecutionResult, fields: LinkedHashMap<String, Any?>) {
        if (result.states.isEmpty()) return
        val unprefixed = result.states.size == 1 && result.observations.isEmpty()
        if (!unprefixed) fields["state_count"] = result.states.size
        result.states.forEachIndexed { index, state ->
            val prefix = if (unprefixed) "state" else "state_${index + 1}"
            if (!unprefixed) fields["${prefix}_type"] = state.stateType
            state.subject.id.value
                .takeIf { it != fields["subject_id"]?.toString() }
                ?.takeUnless { result.isDemoPlaceholderSubject(it) }
                ?.let { fields["${prefix}_subject_id"] = it }
            state.values.forEach { (key, value) ->
                fields[if (unprefixed) key else "${prefix}_${key}"] = value
            }
        }
    }

    private fun appendEntitiesCompact(result: ExecutionResult, fields: LinkedHashMap<String, Any?>) {
        if (result.entities.isEmpty()) return
        val representedIds = buildSet {
            fields["context_entity_id"]?.toString()?.let(::add)
            fields["subject_id"]?.toString()?.let(::add)
            result.observations.mapNotNullTo(this) { it.subject?.id?.value }
            result.states.mapTo(this) { it.subject.id.value }
        }
        val novel = result.entities.filterNot { it.id.value in representedIds }
        novel.forEachIndexed { index, entity ->
            val prefix = if (novel.size == 1) "entity" else "entity_${index + 1}"
            fields["${prefix}_id"] = entity.id.value
            fields["${prefix}_type"] = entity.entityType
        }
    }

    private fun ExecutionResult.isDemoPlaceholderSubject(subject: String): Boolean {
        if (subject != "participant/P001") return false
        val caller = request.context["caller"].orEmpty().lowercase()
        val source = request.context["source"].orEmpty().lowercase()
        return caller in setOf("dashboard", "intent_test") ||
            source in setOf("dashboard", "intent_test")
    }

    private fun formatFields(fields: Map<String, Any?>, returnMode: ReturnMode): String = when (returnMode) {
        ReturnMode.Single -> fields.values.firstOrNull()?.toString() ?: ""
        ReturnMode.Fields -> fields.entries.joinToString("\n") { (key, value) -> "$key=$value" }
        ReturnMode.Json -> fields.entries.joinToString(
            prefix = "{\n", separator = ",\n", postfix = "\n}"
        ) { (key, value) -> "  ${quote(key)}: ${formatJsonValue(value)}" }
        ReturnMode.Datapoints -> fields.entries.mapIndexed { index, entry ->
            "${index + 1},${escapeCsv(entry.key)},${escapeCsv(entry.value?.toString() ?: "") }"
        }.joinToString("\n")
    }

    private fun formatJsonValue(value: Any?): String = when (value) {
        null -> "null"
        is Number -> value.toString()
        is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", separator = ",", postfix = "}") { (key, nestedValue) ->
            "${quote(key.toString())}:${formatJsonValue(nestedValue)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", separator = ",", postfix = "]") { formatJsonValue(it) }
        else -> quote(value.toString())
    }

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

    private fun escapeCsv(value: String): String {
        val mustQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (mustQuote) "\"$escaped\"" else escaped
    }
}
