package com.example.researchos.core.researchos

/** AS1.00 domains: useful for diagnostics, documentation and future storage. */
enum class ArchitectureDomain {
    Knowledge,
    Method,
    Time,
    Space,
    Transformation
}

enum class KnowledgeObjectType {
    Entity,
    Attribute,
    Observation,
    Relationship,
    Classification,
    State
}

/** Marker for persistent or persistable knowledge. Signals deliberately do not
 * implement this interface because a Signal is transient until interpreted. */
interface KnowledgeObject : ArchitectureObject {
    val knowledgeType: KnowledgeObjectType
    val temporalContext: TemporalContext
    val spatialContext: SpatialContext?
    override val objectType: String
        get() = knowledgeType.name
}

data class Entity(
    override val id: ArchitectureId = ArchitectureId(),
    val entityType: String,
    val attributes: Map<String, String> = emptyMap(),
    override val temporalContext: TemporalContext = TemporalContext(),
    override val spatialContext: SpatialContext? = null
) : KnowledgeObject {
    override val knowledgeType: KnowledgeObjectType = KnowledgeObjectType.Entity
}

data class Attribute(
    override val id: ArchitectureId = ArchitectureId(),
    val subject: ArchitectureRef,
    val name: String,
    val values: Map<String, String> = emptyMap(),
    override val temporalContext: TemporalContext = TemporalContext(),
    override val spatialContext: SpatialContext? = null,
    val provenance: ProvenanceContext? = null
) : KnowledgeObject {
    override val knowledgeType: KnowledgeObjectType = KnowledgeObjectType.Attribute
}

data class Observation(
    override val id: ArchitectureId = ArchitectureId(),
    val phenomenon: String,
    val subject: ArchitectureRef? = null,
    val values: Map<String, String>,
    val sourceSignal: ArchitectureRef? = null,
    override val temporalContext: TemporalContext = TemporalContext(),
    override val spatialContext: SpatialContext? = null,
    val provenance: ProvenanceContext
) : KnowledgeObject {
    override val knowledgeType: KnowledgeObjectType = KnowledgeObjectType.Observation
}

data class Relationship(
    override val id: ArchitectureId = ArchitectureId(),
    val relationshipType: String,
    val from: ArchitectureRef,
    val to: ArchitectureRef,
    val attributes: Map<String, String> = emptyMap(),
    override val temporalContext: TemporalContext = TemporalContext(),
    override val spatialContext: SpatialContext? = null
) : KnowledgeObject {
    override val knowledgeType: KnowledgeObjectType = KnowledgeObjectType.Relationship
}

data class Classification(
    override val id: ArchitectureId = ArchitectureId(),
    val subject: ArchitectureRef,
    val scheme: String,
    val classId: String,
    val label: String? = null,
    val confidence: Double? = null,
    override val temporalContext: TemporalContext = TemporalContext(),
    override val spatialContext: SpatialContext? = null,
    val provenance: ProvenanceContext? = null
) : KnowledgeObject {
    override val knowledgeType: KnowledgeObjectType = KnowledgeObjectType.Classification
}

data class State(
    override val id: ArchitectureId = ArchitectureId(),
    val subject: ArchitectureRef,
    val stateType: String,
    val values: Map<String, String> = emptyMap(),
    override val temporalContext: TemporalContext = TemporalContext(),
    override val spatialContext: SpatialContext? = null
) : KnowledgeObject {
    override val knowledgeType: KnowledgeObjectType = KnowledgeObjectType.State
}
