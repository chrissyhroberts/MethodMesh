package com.example.researchos.modules.providercommands

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object ProviderCommandModule : ResearchOSModule {
    override val moduleId = "providercommands"
    override val displayName = "External command library"
    override val summary = "Save, test, import, export, and run named commands for external Android apps."

    override fun as100Methods() = listOf(As100ProviderCommandRunMethod)

    override fun rilBindings() = listOf(
        RilBinding("run provider command", As100ProviderCommandRunMethod.ID, "Run a saved external app command by command ID"),
        RilBinding("run external command", As100ProviderCommandRunMethod.ID, "Execute a named command from the external command library")
    )

    override fun capabilityScreens() = listOf(ProviderCommandCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100ProviderCommandRunMethod.ID to listOf(
            MethodSetting.TextSetting("provider_command_id", "Command ID", group = "Command", defaultValue = ""),
            MethodSetting.TextSetting("provider_inputs_json", "Inputs JSON", group = "Command", defaultValue = "{}")
        )
    )
}

