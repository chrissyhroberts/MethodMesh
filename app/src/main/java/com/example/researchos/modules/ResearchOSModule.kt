package com.example.researchos.modules

import android.content.Context
import com.example.researchos.core.Method
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec

/**
 * Self-contained module contract.
 *
 * A capability module should expose one object implementing this interface from
 * inside its own modules/<module-name>/ folder. ResearchOSModuleManifest lists
 * the built-in module objects and the registry uses that manifest to build the
 * method registry, RIL bindings and external workflow screens. Adding a new
 * capability should therefore require only module-local files plus one manifest
 * entry.
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

    fun legacyMethods(): List<Method> = emptyList()
    fun as100Methods(): List<As100Method> = emptyList()
    fun rilBindings(): List<RilBinding> = emptyList()
    fun capabilityScreens(): List<CapabilityScreenSpec> = emptyList()

    /**
     * Optional examples shown in debug panels. These are module-owned so adding
     * a new module can also add its own documentation/examples without central
     * UI edits.
     */
    fun examples(): List<ModuleExample> = rilBindings().take(3).map { binding ->
        ModuleExample(
            title = binding.description.ifBlank { binding.phrase },
            ril = "WHAT; ${binding.phrase}; WHERE; participant/P001; RESULT; return execution.id as execution_id; format json"
        )
    }

    /** Module-owned capability descriptions for debug/help screens. */
    fun debugCapabilities(): List<ModuleCapabilitySummary> = as100Methods().map { method ->
        ModuleCapabilitySummary(
            id = method.id,
            title = method.descriptor.name,
            description = method.descriptor.description ?: "",
            graphOutputs = method.descriptor.graphOutputs,
            outputFields = method.descriptor.outputs,
            screenAvailable = capabilityScreens().any { it.capabilityId == method.id },
            rilPhrases = rilBindings().filter { it.actionId == method.id }.map { it.phrase }
        )
    }
}

data class RilBinding(
    val phrase: String,
    val actionId: String,
    val description: String = ""
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

/** Explicit module registry backed by ResearchOSModuleManifest. */
object ResearchOSModuleRegistry {
    /**
     * Kept for existing Android entry points. Module loading is now explicit and
     * deterministic, so there is no context-dependent Dex scan to perform.
     */
    fun initialise(context: Context) = Unit

    fun all(): List<ResearchOSModule> = ResearchOSModuleManifest.modules

    fun as100Methods(): List<As100Method> = all().flatMap { it.as100Methods() }
    fun legacyMethods(): List<Method> = all().flatMap { it.legacyMethods() }
    fun rilBindings(): List<RilBinding> = all().flatMap { it.rilBindings() }
    fun capabilityScreens(): List<CapabilityScreenSpec> = all().flatMap { it.capabilityScreens() }

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
