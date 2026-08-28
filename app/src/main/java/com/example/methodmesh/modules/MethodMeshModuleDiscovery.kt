package com.example.methodmesh.modules

import android.content.Context
import android.util.Log

/** Discovers self-contained Kotlin module objects without a central capability list. */
object MethodMeshModuleDiscovery {
    private const val MODULE_PACKAGE = "com.example.methodmesh.modules."

    @Suppress("DEPRECATION")
    fun discover(context: Context): List<MethodMeshModule> {
        val resourceId = context.resources.getIdentifier("methodmesh_module_index", "raw", context.packageName)
        val classNames = if (resourceId != 0) {
            context.resources.openRawResource(resourceId).bufferedReader().useLines { it.filter(String::isNotBlank).toList() }
        } else {
            emptyList()
        }
        return classNames.asSequence()
            .filter { name -> name.startsWith(MODULE_PACKAGE) && name.endsWith("Module") && !name.contains('$') }
            .distinct()
            .mapNotNull(::loadModuleObject)
            .sortedBy { it.moduleId }
            .toList()
    }

    private fun loadModuleObject(className: String): MethodMeshModule? = runCatching {
        val type = Class.forName(className)
        if (!MethodMeshModule::class.java.isAssignableFrom(type)) return@runCatching null
        type.getField("INSTANCE").get(null) as MethodMeshModule
    }.onFailure { error ->
        // A class can be in the module package without being a module object;
        // only report failures for classes that look like module candidates.
        if (className.endsWith("Module")) {
            Log.w("MethodMeshModules", "Unable to load module candidate $className", error)
        }
    }.getOrNull()
}
