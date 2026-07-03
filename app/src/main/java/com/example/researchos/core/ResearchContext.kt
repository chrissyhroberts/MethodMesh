package com.example.researchos.core

/**
 * Execution context supplied by ODK, another app, a protocol runner, or demo mode.
 */
data class ResearchContext(
    val values: Map<String, String> = emptyMap()
) {
    operator fun get(key: String): String? = values[key]

    fun withValues(extra: Map<String, String>): ResearchContext =
        copy(values = values + extra)

    fun missing(requiredKeys: List<String>): List<String> =
        requiredKeys.filter { key -> values[key].isNullOrBlank() }
}
