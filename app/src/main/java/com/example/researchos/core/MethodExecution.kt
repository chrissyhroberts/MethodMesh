package com.example.researchos.core

import com.example.researchos.settings.SettingsState

/**
 * Canonical runtime request. Existing methods can still use MethodRequest directly;
 * this wrapper is the stable ResearchOS-aligned execution envelope.
 */
data class MethodExecutionRequest(
    val methodId: String,
    val context: ResearchContext = ResearchContext(),
    val parameters: Map<String, String> = emptyMap(),
    val transport: String? = null
)

data class MethodExecutionResult(
    val success: Boolean,
    val artifact: Observation? = null,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList()
)

object MethodRuntime {

    fun execute(
        method: Method,
        request: MethodExecutionRequest,
        settingsState: SettingsState? = null
    ): MethodExecutionResult {
        val missingContext = request.context.missing(method.manifest.requiredInputs)
        val contextWarnings = missingContext.map { key -> "Missing context: $key" }

        val legacyResult = method.execute(
            MethodRequest(parameters = request.parameters + request.context.values)
        )

        if (!legacyResult.success) {
            return MethodExecutionResult(
                success = false,
                errorMessage = legacyResult.errorMessage ?: "Method execution failed.",
                warnings = contextWarnings
            )
        }

        val output = when {
            legacyResult.fields.isNotEmpty() -> MethodOutput(legacyResult.fields)
            settingsState != null -> method.buildOutput(settingsState)
            else -> MethodOutput(
                legacyResult.json?.let { mapOf("json" to it) } ?: emptyMap()
            )
        }

        val validation = MethodOutputValidator.validate(
            schema = method.outputSchema,
            output = output
        )

        val provenance = Provenance(
            methodId = method.manifest.id,
            methodVersion = method.manifest.version,
            activityIds = method.manifest.capabilities.map { it.id },
            transport = request.transport,
            warnings = contextWarnings + validation.messages
        )

        return MethodExecutionResult(
            success = validation.valid,
            artifact = Observation(
                output = output,
                schema = method.outputSchema,
                context = request.context,
                provenance = provenance,
                validation = validation
            ),
            errorMessage = if (validation.valid) null else validation.messages.joinToString("; "),
            warnings = contextWarnings
        )
    }
}
