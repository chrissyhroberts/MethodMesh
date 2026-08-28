package com.example.methodmesh.core.methodmesh.runtime

import com.example.methodmesh.settings.MethodSetting

/** Generic configuration descriptions supplied by capability modules. */
object CapabilityConfigurationRegistry {
    @Volatile private var configurations: Map<String, List<MethodSetting>> = emptyMap()

    fun install(values: Map<String, List<MethodSetting>>) {
        configurations = values.toMap()
    }

    fun settingsFor(methodId: String): List<MethodSetting> = configurations[methodId].orEmpty()
}
