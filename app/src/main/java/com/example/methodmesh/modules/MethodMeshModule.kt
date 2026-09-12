package com.example.methodmesh.modules

import android.content.Context
import com.example.methodmesh.core.transport.MethodMeshTransportProvider
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.core.scheduling.As100SchedulerMethod
import com.example.methodmesh.core.scheduling.As100ScheduleRunMethod
import com.example.methodmesh.core.scheduling.SchedulePlanCapabilityScreen
import com.example.methodmesh.core.scheduling.CronScheduleCapabilityScreen
import com.example.methodmesh.core.scheduling.ScheduleTriggerCapabilityScreen
import com.example.methodmesh.core.methodmesh.runtime.CapabilityConfigurationRegistry
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsSectionSpec

/**
 * Self-contained module contract.
 *
 * A capability module exposes one object implementing this interface from
 * inside its own modules/<module-name>/ folder. Modules are discovered at
 * application startup, so adding a capability requires no central registration.
 *
 * Each module also owns its implementation documentation:
 * - docs/README_<CapabilityModule>.md describes its capabilities, Android
 *   intents, inputs, outputs, and ODK usage.
 * - docs/example_odk_<Capability>.xlsx contains importable XLSForms exercising
 *   every public capability exposed by the module.
 *
 * Architecture tests enforce this convention without teaching the runtime
 * anything about individual capability modules.
 */
interface MethodMeshModule {
    val moduleId: String
    val displayName: String

    /**
     * One-line human readable description used by the debug module browser.
     * Module authors can override this inside modules/<module>/ without touching
     * dashboard or registry code.
     */
    val summary: String
        get() = "Self-contained MethodMesh module."

    /**
     * Optional MethodMesh-styled icon hint for widgets and other generic launch
     * surfaces. The UI may map this to a broad visual family; modules can set a
     * value such as "location", "language", "document", "hardware", "random",
     * or their own method/category identifier without the UI learning capability
     * internals.
     */
    val iconKey: String
        get() = moduleId

    /** Declare tool placement rather than adding module IDs to the shell. */
    val workbenchTool: Boolean get() = false

    /** Persistent in-app controls; no system overlay permission is involved. */
    fun overlays(): List<ModuleOverlaySpec> = emptyList()

    fun as100Methods(): List<As100Method> = emptyList()
    fun rilBindings(): List<RilBinding> = emptyList()
    fun capabilityScreens(): List<CapabilityScreenSpec> = emptyList()
    fun capabilitySettings(): Map<String, List<MethodSetting>> = emptyMap()

    /**
     * Optional Settings sections owned by this module.
     *
     * The shared Settings UI controls layout and collapsible presentation; a
     * module only contributes the section metadata and its content. This means
     * future modules can add shared services (for example offline resource packs)
     * without editing HomeScreen.kt.
     */
    fun settingsSections(): List<SettingsSectionSpec> = emptyList()

    /** Optional generic transport adapters owned by this module. */
    fun transportProviders(context: Context): List<MethodMeshTransportProvider> = emptyList()

    /**
     * Module-level dependencies. Dependency modules remain independently owned;
     * callers should consume their public methods/screens rather than copying
     * implementation details into the dependent module.
     */
    fun dependencies(): List<ModuleDependency> = emptyList()

    /**
     * Optional examples shown in debug panels. These are module-owned so adding
     * a new module can also add its own documentation/examples without central
     * UI edits.
     */
    fun examples(): List<ModuleExample> = rilBindings().take(3).map { binding ->
        ModuleExample(
            title = binding.description.ifBlank { binding.phrase },
            ril = "WHAT; ${binding.phrase}; RESULT; return execution.id as execution_id; format json"
        )
    }

}

data class RilBinding(
    val phrase: String,
    val actionId: String,
    val description: String = ""
)

data class ModuleDependency(
    val moduleId: String,
    val reason: String
)

data class ModuleExample(
    val title: String,
    val ril: String,
    val notes: String = ""
)

data class ModuleCapabilitySummary(
    val id: String,
    val title: String,
    val description: String = "",
    val graphOutputs: List<String> = emptyList(),
    val outputFields: List<String> = emptyList(),
    val screenAvailable: Boolean = false,
    val rilPhrases: List<String> = emptyList()
)

object MethodMeshModuleRegistry {
    @Volatile private var installed: List<MethodMeshModule>? = null

    fun install(modules: List<MethodMeshModule>) {
        require(modules.isNotEmpty()) { "MethodMesh discovered no capability modules." }
        require(modules.map { it.moduleId }.distinct().size == modules.size) { "MethodMesh module IDs must be unique." }
        val methods = modules.flatMap { it.as100Methods() } + coreMethods()
        require(methods.map { it.id }.distinct().size == methods.size) { "MethodMesh method IDs must be unique." }
        val screens = modules.flatMap { it.capabilityScreens() } + coreScreens()
        require(screens.map { it.capabilityId }.distinct().size == screens.size) { "MethodMesh capability screen IDs must be unique." }
        val settingsSections = modules.flatMap { it.settingsSections() }
        require(settingsSections.map { it.id }.distinct().size == settingsSections.size) { "MethodMesh Settings section IDs must be unique." }
        val overlays = modules.flatMap { it.overlays() }
        require(overlays.all { it.id.isNotBlank() } && overlays.map { it.id }.distinct().size == overlays.size) { "Module overlay IDs must be nonblank and unique." }
        installed = modules.sortedBy { it.moduleId }
        As100MethodRegistry.install(methods)
        CapabilityConfigurationRegistry.install(modules.flatMap { it.capabilitySettings().entries }.associate { it.key to it.value })
    }

    fun all(): List<MethodMeshModule> = installed
        ?: error("MethodMesh modules have not been discovered. MethodMeshApplication must initialise the registry.")

    fun as100Methods(): List<As100Method> = all().flatMap { it.as100Methods() } + coreMethods()
    fun rilBindings(): List<RilBinding> = all().flatMap { it.rilBindings() } + coreBindings()
    fun capabilityScreens(): List<CapabilityScreenSpec> = all().flatMap { it.capabilityScreens() } + coreScreens()
    fun settingsSections(): List<SettingsSectionSpec> = all()
        .flatMap { it.settingsSections() }
        .sortedWith(compareBy<SettingsSectionSpec> { it.order }.thenBy { it.title.lowercase() })

    fun overlays(): List<ModuleOverlaySpec> = all().flatMap { it.overlays() }
        .sortedWith(compareBy<ModuleOverlaySpec> { it.order }.thenBy { it.id })

    private fun coreMethods() = listOf(As100SchedulerMethod, As100ScheduleRunMethod)
    private fun coreScreens() = listOf(CronScheduleCapabilityScreen, ScheduleTriggerCapabilityScreen)
    private fun coreBindings() = listOf(
        RilBinding("create schedule", As100SchedulerMethod.ID, "Create a local MethodMesh schedule"),
        RilBinding("run schedule", As100ScheduleRunMethod.ID, "Start a saved MethodMesh schedule"),
    )

    fun canonicalAction(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null
        val lower = value.lowercase()
        return rilBindings().firstOrNull { it.phrase.lowercase() == lower }?.actionId
            ?: rilBindings().firstOrNull { it.actionId.lowercase() == lower }?.actionId
            ?: as100Methods().firstOrNull { it.id.lowercase() == lower }?.id
    }

    fun screenFor(actionId: String): CapabilityScreenSpec? =
        capabilityScreens().firstOrNull { it.capabilityId == actionId }
}
