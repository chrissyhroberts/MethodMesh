package com.example.researchos.core.researchos.runtime

/**
 * Canonical AS-facing registry of executable methods.
 *
 * The core stores method contracts but knows nothing about capability modules.
 * The application extension layer installs discovered methods at startup.
 */
object As100MethodRegistry {

    @Volatile private var methods: List<As100Method> = emptyList()

    fun install(discovered: List<As100Method>) {
        require(discovered.map { it.id }.distinct().size == discovered.size) { "AS method IDs must be unique." }
        methods = discovered.toList()
    }

    fun all(): List<As100Method> = methods

    fun find(id: String): As100Method? =
        methods.firstOrNull { it.id == id }

    fun require(id: String): As100Method =
        find(id) ?: error("No AS method registered with id: $id")
}
