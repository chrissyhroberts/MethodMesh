package com.example.xlsformlab.core

/**
 * Compatibility result used by existing method implementations.
 * Prefer Observation via MethodRuntime for new runtime code.
 */
data class MethodResult(
    val success: Boolean = true,
    val fields: Map<String, String> = emptyMap(),
    val json: String? = null,
    val errorMessage: String? = null
)
