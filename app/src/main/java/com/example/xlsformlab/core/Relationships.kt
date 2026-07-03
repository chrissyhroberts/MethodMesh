package org.researchos.core

/**
 * Reference to another ResearchOS object.
 *
 * ObjectRef is the lightweight glue that allows ResearchOS objects to form
 * a graph without embedding one object directly inside another.
 */
data class ObjectRef(
    val id: String,
    val type: String? = null,
    val label: String? = null
)

/**
 * A typed relationship between two ResearchOS objects.
 *
 * Relationships are runtime graph edges. They connect canonical objects such
 * as Entities, Observations, Assertions, Intents and Methods.
 */
data class Relationship(
    val source: ObjectRef,
    val type: RelationshipType,
    val target: ObjectRef,
    val metadata: Map<String, Any?> = emptyMap()
)

/**
 * Canonical relationship types used by the initial ResearchOS runtime.
 */
enum class RelationshipType {
    Observes,
    Describes,
    Supports,
    ProducedBy,
    RequestedBy,
    DerivedFrom,
    Supersedes,
    RelatedTo
}