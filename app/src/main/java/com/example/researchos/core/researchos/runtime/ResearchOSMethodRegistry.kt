package com.example.researchos.core.researchos.runtime

import com.example.researchos.core.Method
import com.example.researchos.core.MethodCategory
import com.example.researchos.core.MethodManifest
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

    private val legacyMethods: List<Method>
        get() = ResearchOSModuleRegistry.legacyMethods()

    private val nativeMethods: List<As100Method>
        get() = ResearchOSModuleRegistry.as100Methods()

    private val methods: List<As100Method>
        get() {
            val nativeIds = nativeMethods.map { it.id }.toSet()
            return nativeMethods + legacyMethods
                .filterNot { method -> method.manifest.id in nativeIds }
                .map { method -> As100LegacyMethodAdapter(method) }
        }

    fun all(): List<As100Method> = methods

    fun find(id: String): As100Method? =
        methods.firstOrNull { it.id == id }

    fun require(id: String): As100Method =
        find(id) ?: error("No AS method registered with id: $id")

    fun descriptors(): List<MethodDescriptor> =
        methods.map { it.descriptor }

    fun contracts(): List<MethodContract> =
        methods.map { it.contract }

    /** Legacy compatibility surface for UI/transport still being migrated. */
    fun legacyMethods(): List<Method> = legacyMethods

    fun legacyFind(id: String): Method? =
        legacyMethods.firstOrNull { it.manifest.id == id }

    fun legacyRequire(id: String): Method =
        legacyFind(id) ?: error("No legacy method registered with id: $id")

    fun legacyByCategory(category: MethodCategory): List<Method> =
        legacyMethods.filter { it.manifest.category == category }

    fun legacyCategoriesInUse(): List<MethodCategory> =
        legacyMethods.map { it.manifest.category }.distinct()

    fun legacyManifests(): List<MethodManifest> =
        legacyMethods.map { it.manifest }
}
