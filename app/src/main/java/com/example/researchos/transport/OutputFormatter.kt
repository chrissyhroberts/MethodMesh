package com.example.researchos.transport

import com.example.researchos.core.MethodOutput
import com.example.researchos.core.Observation
import com.example.researchos.core.ResearchGraph
import com.example.researchos.core.researchos.ExecutionResult

object OutputFormatter {

    fun format(output: MethodOutput, returnMode: ReturnMode): String =
        formatFields(output.fields, returnMode)

    fun format(artifact: Observation, returnMode: ReturnMode, includeProvenance: Boolean = true): String =
        formatFields(artifact.toRecord(includeProvenance), returnMode)

    fun fields(result: ExecutionResult, includeProvenance: Boolean = true): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>()
        fields["researchos_execution_id"] = result.request.id.value
        fields["researchos_method_id"] = result.request.method.id.value
        fields["researchos_action"] = result.request.action
        fields["researchos_status"] = result.status.name

        result.request.context["context_entity_id"]?.let { fields["context_entity_id"] = it }
        result.request.context["subject_id"]?.let { fields["subject_id"] = it }
        result.request.context["visit_id"]?.let { fields["visit_id"] = it }
        result.request.context["form_id"]?.let { fields["form_id"] = it }
        result.request.context["operator_id"]?.let { fields["operator_id"] = it }

        result.observations.forEachIndexed { index, observation ->
            val prefix = if (result.observations.size == 1) "observation" else "observation_${index + 1}"
            fields["${prefix}_id"] = observation.id.value
            fields["${prefix}_type"] = observation.phenomenon
            observation.subject?.id?.value?.let { fields["${prefix}_subject_id"] = it }
            observation.values.forEach { (key, value) ->
                fields[key] = value
                fields["${prefix}_${key}"] = value
            }
            if (includeProvenance) {
                fields["${prefix}_provider"] = observation.provenance.provider
                observation.provenance.methodId?.let { fields["${prefix}_method_id"] = it }
            }
        }

        result.states.forEachIndexed { index, state ->
            val prefix = if (result.states.size == 1) "state" else "state_${index + 1}"
            fields["${prefix}_id"] = state.id.value
            fields["${prefix}_type"] = state.stateType
            fields["${prefix}_subject_id"] = state.subject.id.value
            state.values.forEach { (key, value) ->
                fields["${prefix}_${key}"] = value
            }
        }

        result.entities.forEachIndexed { index, entity ->
            val prefix = if (result.entities.size == 1) "entity" else "entity_${index + 1}"
            fields["${prefix}_id"] = entity.id.value
            fields["${prefix}_type"] = entity.entityType
        }

        result.diagnostics.forEach { (key, value) ->
            fields["diagnostic_$key"] = value
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
    ): String =
        formatFields(selectedFields(result, selectors, graph, includeProvenance), returnMode)

    private fun formatFields(fields: Map<String, Any?>, returnMode: ReturnMode): String {
        return when (returnMode) {
            ReturnMode.Single -> fields.values.firstOrNull()?.toString() ?: ""

            ReturnMode.Fields -> fields.entries.joinToString("\n") { (key, value) ->
                "$key=$value"
            }

            ReturnMode.Json -> fields.entries.joinToString(
                prefix = "{\n",
                separator = ",\n",
                postfix = "\n}"
            ) { (key, value) ->
                "  ${quote(key)}: ${formatJsonValue(value)}"
            }

            ReturnMode.Datapoints -> fields.entries.mapIndexed { index, entry ->
                "${index + 1},${escapeCsv(entry.key)},${escapeCsv(entry.value?.toString() ?: "") }"
            }.joinToString("\n")
        }
    }

    private fun formatJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(
                prefix = "{",
                separator = ",",
                postfix = "}"
            ) { (key, nestedValue) ->
                "${quote(key.toString())}:${formatJsonValue(nestedValue)}"
            }
            is Iterable<*> -> value.joinToString(
                prefix = "[",
                separator = ",",
                postfix = "]"
            ) { item -> formatJsonValue(item) }
            else -> quote(value.toString())
        }
    }

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

    private fun escapeCsv(value: String): String {
        val mustQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (mustQuote) "\"$escaped\"" else escaped
    }
}
