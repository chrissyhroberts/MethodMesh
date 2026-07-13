package com.example.researchos.core.researchos.runtime

import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.modules.ResearchOSModuleRegistry

/**
 * Canonical AS-facing registry of executable methods.
 *
 * The registry is assembled from self-contained modules under modules/. New
 * capabilities should provide a module object implementing ResearchOSModule;
 * they should not require edits to this central registry.
 */
object As100MethodRegistry {

    private val nativeMethods: List<As100Method>
        get() = ResearchOSModuleRegistry.as100Methods()

    private val methods: List<As100Method>
        get() = nativeMethods

    fun all(): List<As100Method> = methods

    fun find(id: String): As100Method? =
        methods.firstOrNull { it.id == id }

    fun require(id: String): As100Method =
        find(id) ?: error("No AS method registered with id: $id")
}
