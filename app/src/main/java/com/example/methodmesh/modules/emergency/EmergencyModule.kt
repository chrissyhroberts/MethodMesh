package com.example.methodmesh.modules.emergency

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object EmergencyModule : MethodMeshModule {
    override val moduleId = "emergency"
    override val displayName = "Emergency"
    override val summary = "Offline-first emergency reference, local risk status, emergency location and strategic exit planning."
    override val iconKey = "location"

    override fun as100Methods() = listOf(
        As100EmergencyStatusMethod,
        As100EmergencyLocationMethod,
        As100EmergencyExitFindMethod,
        As100EmergencyReferenceOpenMethod,
        As100EmergencyPackPrepareMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("check emergency status", As100EmergencyStatusMethod.ID, "Check explainable local risk and preparedness"),
        RilBinding("share emergency location", As100EmergencyLocationMethod.ID, "Capture GPS and Plus Code for emergency sharing"),
        RilBinding("find emergency exit", As100EmergencyExitFindMethod.ID, "Rank offline strategic exit options"),
        RilBinding("open emergency guide", As100EmergencyReferenceOpenMethod.ID, "Open an offline emergency reference card"),
        RilBinding("prepare emergency location", As100EmergencyPackPrepareMethod.ID, "Inspect or prepare a regional emergency pack")
    )

    override fun capabilityScreens() = listOf(
        EmergencyStatusCapabilityScreen,
        EmergencyLocationCapabilityScreen,
        EmergencyExitCapabilityScreen,
        EmergencyReferenceCapabilityScreen,
        EmergencyPackCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100EmergencyStatusMethod.ID to listOf(
            MethodSetting.MultiChoiceSetting(
                id = "critical_domains",
                label = "Critical hazard domains",
                description = "Domains required before a GREEN status is possible. Missing/stale enabled domains make status GREY.",
                group = "Risk",
                defaultValue = EmergencyHazardDomain.entries.joinToString("|") { it.name },
                choices = EmergencyHazardDomain.entries.map { it.name },
                emptyMeansAll = true
            ),
            MethodSetting.ChoiceSetting(
                id = "monitoring_mode",
                label = "Monitoring session",
                description = "Stores session intent only in this version. Persistent notification shade is deliberately deferred.",
                group = "Monitoring",
                defaultValue = EmergencyMonitoringMode.OFF.name,
                choices = EmergencyMonitoringMode.entries.map { it.name }
            )
        ),
        As100EmergencyLocationMethod.ID to listOf(
            MethodSetting.IntSetting(
                id = "plus_code_length",
                label = "Plus Code length",
                description = "Emergency sharing defaults to a full 10-character Open Location Code.",
                group = "Location",
                defaultValue = 10,
                minimum = 8,
                maximum = 10,
                step = 2
            )
        ),
        As100EmergencyExitFindMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "nationality_iso2",
                label = "Nationality",
                description = "Optional country of nationality. Used only to prioritise relevant embassies/consulates in exit results.",
                group = "Exit strategy",
                defaultValue = "",
                choices = listOf("") + EmergencyCountries.all.map { it.label }
            )
        ),
        As100EmergencyReferenceOpenMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "guide_id",
                label = "Emergency guide",
                group = "Reference",
                defaultValue = "adult_cpr_aed",
                choices = EmergencyCoreData.referenceItems.map { it.id }
            )
        ),
        As100EmergencyPackPrepareMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "country_iso2",
                label = "Country to prepare",
                description = "Country whose emergency pack should be inspected or prepared for offline use.",
                group = "Offline pack",
                defaultValue = "",
                choices = listOf("") + EmergencyCountries.all.map { it.label }
            )
        )
    )

    override fun dependencies() = listOf(
        ModuleDependency(
            moduleId = "pluscodecapture",
            reason = "Use MethodMesh's local Open Location Code implementation for emergency coordinates."
        )
    )
}
