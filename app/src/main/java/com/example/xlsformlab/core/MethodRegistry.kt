package com.example.xlsformlab.core

import com.example.xlsformlab.core.researchos.runtime.As100MethodRegistry

/**
 * Legacy compatibility registry.
 *
 * The canonical registry is now As100MethodRegistry. This object remains so the
 * existing UI and transport code can migrate gradually without breaking imports.
 */
object MethodRegistry {

    fun all(): List<Method> =
        As100MethodRegistry.legacyMethods()

    fun byCategory(category: MethodCategory): List<Method> =
        As100MethodRegistry.legacyByCategory(category)

    fun find(id: String): Method? =
        As100MethodRegistry.legacyFind(id)

    fun categoriesInUse(): List<MethodCategory> =
        As100MethodRegistry.legacyCategoriesInUse()

    fun manifests(): List<MethodManifest> =
        As100MethodRegistry.legacyManifests()

    fun require(id: String): Method =
        As100MethodRegistry.legacyRequire(id)
}
