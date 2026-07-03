package com.example.researchos.core.researchos

enum class TransformationStatus {
    Requested,
    Succeeded,
    Failed,
    Cancelled,
    Unsupported
}

data class Diagnostic(
    val code: String,
    val message: String,
    val severity: String = "info",
    val attributes: Map<String, String> = emptyMap()
)

data class ValidationFinding(
    val passed: Boolean,
    val message: String,
    val field: String? = null,
    val code: String? = null
)

data class QualityAssessment(
    val usable: Boolean,
    val grade: String = if (usable) "usable" else "not_usable",
    val metrics: Map<String, String> = emptyMap(),
    val messages: List<String> = emptyList()
)

/** An explicit AS1.00 transformation/state-transition record. */
data class Transformation(
    override val id: ArchitectureId = ArchitectureId(),
    val action: String,
    val method: ArchitectureRef,
    val inputs: List<ArchitectureRef> = emptyList(),
    val outputs: List<ArchitectureRef> = emptyList(),
    val previousState: State? = null,
    val resultingState: State? = null,
    val status: TransformationStatus,
    val diagnostics: Map<String, String> = emptyMap(),
    val temporalContext: TemporalContext = TemporalContext(),
    val provenance: ProvenanceContext
) : ArchitectureObject {
    override val objectType: String = "Transformation"
}

data class ExecutionRequest(
    override val id: ArchitectureId = ArchitectureId(),
    val action: String,
    val method: ArchitectureRef,
    val context: Map<String, String> = emptyMap(),
    val signals: List<Signal> = emptyList(),
    val inputs: List<ArchitectureRef> = emptyList(),
    val temporalContext: TemporalContext = TemporalContext()
) : ArchitectureObject {
    override val objectType: String = "ExecutionRequest"
}

data class ExecutionResult(
    val request: ExecutionRequest,
    val status: TransformationStatus,
    val entities: List<Entity> = emptyList(),
    val attributes: List<Attribute> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val relationships: List<Relationship> = emptyList(),
    val classifications: List<Classification> = emptyList(),
    val transformations: List<Transformation> = emptyList(),
    val states: List<State> = emptyList(),
    val validation: List<ValidationFinding> = emptyList(),
    val quality: QualityAssessment? = null,
    val diagnostics: Map<String, String> = emptyMap()
) {
    val knowledgeObjects: List<KnowledgeObject>
        get() {
            val objects = mutableListOf<KnowledgeObject>()
            objects.addAll(entities)
            objects.addAll(attributes)
            objects.addAll(observations)
            objects.addAll(relationships)
            objects.addAll(classifications)
            objects.addAll(states)
            return objects
        }
}
