package com.example.researchos.core

/**
 * A canonical ResearchOS Entity.
 *
 * An Entity represents a distinct thing that exists within the ResearchOS
 * knowledge graph and may become the subject of Observation, Assertion,
 * Intent or Method execution.
 *
 * An Entity deliberately contains only identity and lightweight descriptive
 * properties. Scientific knowledge about an Entity is represented elsewhere
 * through Observation and Assertion objects.
 *
 * Entity is part of the ResearchOS knowledge layer and should not be confused
 * with ResearchContext, which represents transient runtime execution state.
 */
data class Entity(

    /**
     * Globally unique identifier.
     *
     * Examples:
     *   participant:001
     *   household:17
     *   sample:blood-42
     */
    val id: String,

    /**
     * Canonical entity type.
     *
     * Examples:
     *   Participant
     *   Household
     *   Sample
     *   Visit
     *   Device
     *   Location
     */
    val type: String,

    /**
     * Optional human-readable label.
     */
    val label: String? = null,

    /**
     * Lightweight implementation properties.
     *
     * These are not scientific Assertions. They exist to support
     * identification, display and execution.
     */
    val properties: MutableMap<String, Any?> = mutableMapOf()

) {

    /**
     * Convenience accessor.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> property(name: String): T? =
        properties[name] as? T

    /**
     * Convenience mutator.
     */
    fun setProperty(name: String, value: Any?) {
        properties[name] = value
    }

    /**
     * Returns true if a property exists.
     */
    fun hasProperty(name: String): Boolean =
        properties.containsKey(name)
}