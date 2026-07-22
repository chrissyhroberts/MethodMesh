package com.example.researchos.modules

import android.content.Context
import dalvik.system.DexFile

/** Discovers self-contained Kotlin module objects without a central capability list. */
object ResearchOSModuleDiscovery {
    private const val MODULE_PACKAGE = "com.example.researchos.modules."

    @Suppress("DEPRECATION")
    fun discover(context: Context): List<ResearchOSModule> {
        val paths = listOfNotNull(context.applicationInfo.sourceDir) + context.applicationInfo.splitSourceDirs.orEmpty()
        val classNames = mutableListOf<String>()
        paths.forEach { path ->
            val dex = DexFile(path)
            try {
                val entries = dex.entries()
                while (entries.hasMoreElements()) classNames += entries.nextElement()
            } finally {
                dex.close()
            }
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
    }.getOrNull()
}
