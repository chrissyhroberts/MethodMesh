package com.example.researchos.modules

import android.content.Context
import android.util.Log

/** Discovers self-contained Kotlin module objects without a central capability list. */
object ResearchOSModuleDiscovery {
    private const val MODULE_PACKAGE = "com.example.researchos.modules."

    @Suppress("DEPRECATION")
    fun discover(context: Context): List<ResearchOSModule> {
        val resourceId = context.resources.getIdentifier("researchos_module_index", "raw", context.packageName)
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

    private fun loadModuleObject(className: String): ResearchOSModule? = runCatching {
        val type = Class.forName(className)
        if (!ResearchOSModule::class.java.isAssignableFrom(type)) return@runCatching null
        type.getField("INSTANCE").get(null) as ResearchOSModule
    }.onFailure { error ->
        // A class can be in the module package without being a module object;
        // only report failures for classes that look like module candidates.
        if (className.endsWith("Module")) {
            Log.w("ResearchOSModules", "Unable to load module candidate $className", error)
        }
    }.getOrNull()
}
