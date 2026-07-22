package com.example.researchos.transport

import com.example.researchos.core.ResearchGraph
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.State

/**
 * A small transport-safe selector language for callers such as ODK.
 *
 * Intent/appearance examples:
 *   returns=execution.id:execution_id, observation.nfc.uid:tag_uid
 *   graph_return=graph://latest/observation/nfc.tag.uid as tag_uid
 *   select=state.navigation.arrived as arrived
 *
 * Selectors intentionally resolve against graph objects, not module-specific
 * Android fields. OutputFormatter can still fall back to the old flattened
 * payload when no selectors are supplied.
 */
data class GraphSelector(
    val path: String,
    val alias: String = defaultAliasFor(path)
) {
    companion object {
        fun defaultAliasFor(path: String): String = path
            .removePrefix("graph://")
            .removePrefix("latest/")
            .replace('/', '.')
            .substringAfterLast('.')
            .ifBlank { "value" }
            .replace(Regex("[^A-Za-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "value" }
    }
}

object GraphSelectorParser {
    fun parse(raw: String?): List<GraphSelector> {
        if (raw.isNullOrBlank()) return emptyList()

        return raw
            .split(',', ';', '\n')
            .mapNotNull { parseOne(it.trim()) }
    }

    private fun parseOne(raw: String): GraphSelector? {
        if (raw.isBlank()) return null

        val asMatch = Regex("\\s+as\\s+", RegexOption.IGNORE_CASE).split(raw, limit = 2)
        if (asMatch.size == 2) {
            return GraphSelector(path = asMatch[0].trim(), alias = asMatch[1].trim())
        }

        val colonIndex = raw.lastIndexOf(':')
        if (colonIndex > 0 && colonIndex < raw.length - 1 && !raw.startsWith("graph://")) {
            return GraphSelector(
                path = raw.substring(0, colonIndex).trim(),
                alias = raw.substring(colonIndex + 1).trim()
            )
        }

        return GraphSelector(path = raw.trim())
    }

    fun looksLikeSelector(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val value = raw.trim()
        return value.startsWith("graph://") ||
            value.contains(" as ", ignoreCase = true) ||
            value.contains("observation.") ||
            value.contains("state.") ||
            value.contains("entity.") ||
            value.contains("execution.") ||
            value.contains("context.")
    }
}

object GraphSelectorResolver {
    fun resolve(
        selectors: List<GraphSelector>,
        result: ExecutionResult,
        graph: ResearchGraph? = null
    ): Map<String, Any?> {
        if (selectors.isEmpty()) return emptyMap()
        return selectors.associate { selector ->
            selector.alias to resolve(selector.path, result, graph)
        }
    }

    fun resolve(path: String, result: ExecutionResult, graph: ResearchGraph? = null): Any? {
        val normalised = normalise(path)
        val tokens = normalised.split('.').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        return when (tokens.first()) {
            "execution" -> resolveExecution(tokens.drop(1), result)
            "context" -> resolveContext(tokens.drop(1), result)
            "observation" -> resolveObservation(tokens.drop(1), result.observations, graph)
            "state" -> resolveState(tokens.drop(1), result.states, graph)
            "entity" -> resolveEntity(tokens.drop(1), result, graph)
            "diagnostic", "diagnostics" -> result.diagnostics[tokens.drop(1).joinToString("_")]
            else -> null
        }
    }

    private fun normalise(path: String): String = path
        .trim()
        .removePrefix("graph://")
        .removePrefix("latest/")
        .replace('/', '.')
        .replace(Regex("\\.+"), ".")
        .trim('.')

    private fun resolveExecution(tokens: List<String>, result: ExecutionResult): Any? {
        val key = tokens.joinToString(".")
        return when (key) {
            "id" -> result.request.id.value
            "status" -> result.status.name
            "method", "method.id" -> result.request.method.id.value
            "action" -> result.request.action
            else -> null
        }
    }

    private fun resolveContext(tokens: List<String>, result: ExecutionResult): Any? {
        val key = tokens.joinToString("_")
        return result.request.context[key]
            ?: result.request.context[tokens.joinToString(".")]
            ?: result.request.context["context_$key"]
    }

    private fun resolveObservation(
        tokens: List<String>,
        current: List<Observation>,
        graph: ResearchGraph?
    ): Any? {
        val observations = current.ifEmpty { graph?.asObservations?.values?.toList().orEmpty() }
        if (observations.isEmpty()) return null

        val valueKey = tokens.lastOrNull() ?: return observations.lastOrNull()?.id?.value
        val typeTokens = tokens.dropLast(1)
        val candidates = observations.filterByTypeTokens(typeTokens) { it.phenomenon }
        val observation = candidates.lastOrNull() ?: observations.lastOrNull()

        return when (valueKey) {
            "id" -> observation?.id?.value
            "type", "phenomenon" -> observation?.phenomenon
            "subject", "subject_id" -> observation?.subject?.id?.value
            "provider" -> observation?.provenance?.provider
            else -> observation?.values?.get(valueKey)
                ?: observation?.values?.get(valueKey.replace('_', '.'))
                ?: observation?.values?.entries?.firstOrNull { (k, _) -> k.equals(valueKey, ignoreCase = true) }?.value
        }
    }

    private fun resolveState(
        tokens: List<String>,
        current: List<State>,
        graph: ResearchGraph?
    ): Any? {
        val states = current.ifEmpty { graph?.asStates?.values?.toList().orEmpty() }
        if (states.isEmpty()) return null

        val valueKey = tokens.lastOrNull() ?: return states.lastOrNull()?.id?.value
        val typeTokens = tokens.dropLast(1)
        val candidates = states.filterByTypeTokens(typeTokens) { it.stateType }
        val state = candidates.lastOrNull() ?: states.lastOrNull()

        return when (valueKey) {
            "id" -> state?.id?.value
            "type", "state_type" -> state?.stateType
            "subject", "subject_id" -> state?.subject?.id?.value
            else -> state?.values?.get(valueKey)
                ?: state?.values?.get(valueKey.replace('_', '.'))
                ?: state?.values?.entries?.firstOrNull { (k, _) -> k.equals(valueKey, ignoreCase = true) }?.value
        }
    }

    private fun resolveEntity(tokens: List<String>, result: ExecutionResult, graph: ResearchGraph?): Any? {
        val entities = result.entities.ifEmpty { graph?.asEntities?.values?.toList().orEmpty() }
        val entity = entities.lastOrNull() ?: return null
        val key = tokens.joinToString(".")
        return when (key) {
            "id" -> entity.id.value
            "type", "entity_type" -> entity.entityType
            else -> entity.attributes[key] ?: entity.attributes[key.replace('.', '_')]
        }
    }

    private inline fun <T> List<T>.filterByTypeTokens(
        typeTokens: List<String>,
        crossinline typeOf: (T) -> String
    ): List<T> {
        if (typeTokens.isEmpty()) return this
        return filter { item ->
            val type = typeOf(item).lowercase()
            typeTokens.all { token -> type.contains(token.lowercase()) }
        }
    }
}
