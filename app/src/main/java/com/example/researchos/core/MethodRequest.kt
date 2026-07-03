package com.example.researchos.core

/**
 * Compatibility request used by existing method implementations.
 * Prefer MethodExecutionRequest for new runtime code.
 */
data class MethodRequest(
    val parameters: Map<String, String> = emptyMap()
) {
    fun get(key: String): String? = parameters[key]
}
