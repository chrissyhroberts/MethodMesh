package com.example.xlsformlab.core

/**
 * A ResearchOS Observation: recorded evidence produced by a Method.
 *
 * Observation is the runtime representation of evidence. It combines the
 * Method output with the schema, research context, provenance and validation
 * metadata required for audit, transport and later interpretation.
 *
 * Conceptually:
 *
 * Intent -> MethodExecutionRequest -> Method -> Observation
 *
 * The Observation is evidence. Scientific interpretation of that evidence
 * belongs in Assertion objects rather than being embedded here.
 */
data class Observation(
    val output: MethodOutput,
    val schema: MethodOutputSchema = MethodOutputSchema(),
    val context: ResearchContext = ResearchContext(),
    val provenance: Provenance,
    val validation: MethodOutputValidation = MethodOutputValidation(valid = true)
) {
    /**
     * Convert this Observation into a flat record suitable for export,
     * storage, or hand-off to systems that expect field/value maps.
     */
    fun toRecord(includeProvenance: Boolean = true): Map<String, Any?> {
        if (!includeProvenance) return output.fields

        return output.fields + mapOf(
            "_researchos_run_id" to provenance.runId,
            "_researchos_generated_at" to provenance.generatedAt,
            "_researchos_method_id" to provenance.methodId,
            "_researchos_method_version" to provenance.methodVersion
        )
    }

    /**
     * Backwards-compatible alias for older runtime code.
     *
     * Prefer [toRecord] in new code.
     */
    @Deprecated(
        message = "Use toRecord() instead.",
        replaceWith = ReplaceWith("toRecord(includeProvenance)")
    )
    fun asFlatFields(includeProvenance: Boolean = true): Map<String, Any?> =
        toRecord(includeProvenance)
}
