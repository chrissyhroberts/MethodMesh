package com.example.researchos.modules

import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.runtime.As100MethodRegistry
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.core.scheduling.As100SchedulerExportMethod
import com.example.researchos.core.scheduling.As100SchedulerImportMethod
import com.example.researchos.core.scheduling.As100SchedulerMethod
import com.example.researchos.core.scheduling.SchedulerExportCapabilityScreen
import com.example.researchos.core.scheduling.SchedulerTransferCapabilityScreen
import com.example.researchos.core.scheduling.SchedulerCapabilityScreen
import com.example.researchos.core.researchos.runtime.CapabilityConfigurationRegistry
import com.example.researchos.settings.MethodSetting

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
interface ResearchOSModule {
    val moduleId: String
    val displayName: String

    /**
     * One-line human readable description used by the debug module browser.
     * Module authors can override this inside modules/<module>/ without touching
     * dashboard or registry code.
     */
    val summary: String
        get() = "Self-contained ResearchOS module."

    fun as100Methods(): List<As100Method> = emptyList()
    fun rilBindings(): List<RilBinding> = emptyList()
    fun capabilityScreens(): List<CapabilityScreenSpec> = emptyList()
    fun capabilitySettings(): Map<String, List<MethodSetting>> = emptyMap()

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

object ResearchOSModuleRegistry {
    @Volatile private var installed: List<ResearchOSModule>? = null

    fun install(modules: List<ResearchOSModule>) {
        require(modules.isNotEmpty()) { "ResearchOS discovered no capability modules." }
        require(modules.map { it.moduleId }.distinct().size == modules.size) { "ResearchOS module IDs must be unique." }
        val methods = modules.flatMap { it.as100Methods() } + coreMethods()
        require(methods.map { it.id }.distinct().size == methods.size) { "ResearchOS method IDs must be unique." }
        val screens = modules.flatMap { it.capabilityScreens() } + coreScreens()
        require(screens.map { it.capabilityId }.distinct().size == screens.size) { "ResearchOS capability screen IDs must be unique." }
        installed = modules.sortedBy { it.moduleId }
        As100MethodRegistry.install(methods)
        CapabilityConfigurationRegistry.install(modules.flatMap { it.capabilitySettings().entries }.associate { it.key to it.value })
    }

    fun all(): List<ResearchOSModule> = installed
        ?: error("ResearchOS modules have not been discovered. ResearchOSApplication must initialise the registry.")

    fun as100Methods(): List<As100Method> = all().flatMap { it.as100Methods() } + coreMethods()
    fun rilBindings(): List<RilBinding> = all().flatMap { it.rilBindings() } + coreBindings()
    fun capabilityScreens(): List<CapabilityScreenSpec> = all().flatMap { it.capabilityScreens() } + coreScreens()

    private fun coreMethods() = listOf(As100SchedulerMethod, As100SchedulerExportMethod, As100SchedulerImportMethod)
    private fun coreScreens() = listOf(SchedulerCapabilityScreen, SchedulerExportCapabilityScreen, SchedulerTransferCapabilityScreen)
    private fun coreBindings() = listOf(
        RilBinding("create schedule", As100SchedulerMethod.ID, "Create a local ResearchOS schedule"),
        RilBinding("export schedules", As100SchedulerExportMethod.id, "Export schedules as a portable bundle"),
        RilBinding("import schedules", As100SchedulerImportMethod.id, "Import schedules directly or through QR/NFC")
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
