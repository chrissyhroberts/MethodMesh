package com.example.researchos.modules

import android.content.Context
import com.example.researchos.core.Method
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import dalvik.system.DexFile

/**
 * Self-contained module contract.
 *
 * A capability module should expose one object implementing this interface from
 * inside its own modules/<module-name>/ folder. The runtime discovers these
 * module objects and uses them to build the method registry, RIL bindings and
 * external workflow screens. Adding a new capability should therefore not
 * require edits to central transport, workflow or registry files.
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

/** Runtime module discovery with a fallback for non-Android/unit-test contexts. */
object ResearchOSModuleRegistry {
    private const val MODULE_PACKAGE_PREFIX = "com.example.researchos.modules."
    private val fallbackModules: List<ResearchOSModule> by lazy {
        listOf(
            com.example.researchos.modules.nfc.NfcModule,
            com.example.researchos.modules.adminfingerprint.AdminFingerprintModule,
            com.example.researchos.modules.gpstargetnavigator.GpsTargetNavigatorModule,
            com.example.researchos.modules.calibratedscale.CalibratedScaleModule,
            com.example.researchos.modules.choiceexperiment.ChoiceExperimentModule
        )
    }

    @Volatile
    private var discoveredModules: List<ResearchOSModule>? = null

    fun initialise(context: Context) {
        if (discoveredModules != null) return
        discoveredModules = mergeWithFallback(discoverFromDex(context))
    }

    fun all(): List<ResearchOSModule> = discoveredModules ?: fallbackModules

    /**
     * Dex discovery is deliberately opportunistic: a module object can fail to
     * instantiate if Compose/runtime classes are not yet loadable, or if a
     * shrinker/packager changes object metadata. Discovery must therefore never
     * replace the known built-in module list with a partial list. In the last
     * DCE capability-panel patch, Android discovery found the non-DCE modules
     * and silently dropped ChoiceExperimentModule, so the DCE methods vanished
     * from the canonical Capabilities panel. Merge discovered modules over the
     * fallback list instead of treating any non-empty discovery result as
     * complete.
     */
    private fun mergeWithFallback(discovered: List<ResearchOSModule>): List<ResearchOSModule> =
        (discovered + fallbackModules)
            .distinctBy { it.moduleId }
            .sortedBy { it.moduleId }

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

    private fun discoverFromDex(context: Context): List<ResearchOSModule> = runCatching {
        val dexFile = DexFile(context.packageCodePath)
        dexFile.entries().asSequence()
            .filter { className ->
                className.startsWith(MODULE_PACKAGE_PREFIX) &&
                    className.endsWith("Module") &&
                    className != ResearchOSModule::class.java.name &&
                    className != ResearchOSModuleRegistry::class.java.name
            }
            .mapNotNull { className -> instantiateModule(className) }
            .distinctBy { it.moduleId }
            .sortedBy { it.moduleId }
            .toList()
    }.getOrElse { emptyList() }

    private fun instantiateModule(className: String): ResearchOSModule? = runCatching {
        val clazz = Class.forName(className)
        val instance = runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull()
            ?: runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
        instance as? ResearchOSModule
    }.getOrNull()
}
