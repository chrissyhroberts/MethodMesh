package com.example.researchos.transport

import com.example.researchos.core.ResearchGraph
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.TransformationStatus

/**
 * Formats ResearchOS results for caller-facing transport.
 *
 * The default contract is deliberately compact: one canonical key per value.
 * The complete graph remains in ResearchOS and can be queried explicitly with
 * return selectors.
 */
object OutputFormatter {

    /** Compact, caller-facing fields. */
    fun fields(result: ExecutionResult, includeProvenance: Boolean = true): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>()
        fields["researchos_execution_id"] = result.request.id.value
        fields["researchos_method_id"] = result.request.method.id.value
        fields["researchos_status"] = result.status.name

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

    fun selectedFields(
        result: ExecutionResult,
        selectors: List<GraphSelector>,
        graph: ResearchGraph? = null,
        includeProvenance: Boolean = true
    ): Map<String, Any?> {
        if (selectors.isEmpty()) return fields(result, includeProvenance)
        val selected = linkedMapOf<String, Any?>()
        selected["researchos_execution_id"] = result.request.id.value
        selected["researchos_status"] = result.status.name
        selected.putAll(GraphSelectorResolver.resolve(selectors, result, graph))
        return selected
    }

    fun format(
        result: ExecutionResult,
        returnMode: ReturnMode,
        includeProvenance: Boolean = true,
        selectors: List<GraphSelector> = emptyList(),
        graph: ResearchGraph? = null
    ): String = formatFields(selectedFields(result, selectors, graph, includeProvenance), returnMode)

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
