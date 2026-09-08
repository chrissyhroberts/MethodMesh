package com.example.methodmesh.modules.clinicalinstruments

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ClinicalInstrumentsModule : MethodMeshModule {
    override val moduleId = "clinicalinstruments"
    override val displayName = "Clinical Instruments"
    override val summary = "Run curated or local versioned clinical checklists and scores entirely offline and return structured observations plus results."

    override fun as100Methods() = listOf(As100ClinicalInstrumentsMethod)

    override fun rilBindings() = listOf(
        RilBinding("run clinical instrument", As100ClinicalInstrumentsMethod.ID, "Run a versioned clinical instrument"),
        RilBinding("run clinical score", As100ClinicalInstrumentsMethod.ID, "Run a clinical score or bedside assessment")
    )

    override fun capabilityScreens() = listOf(ClinicalInstrumentsCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100ClinicalInstrumentsMethod.ID to listOf(
            MethodSetting.TextSetting("instrument_id", "Instrument ID", defaultValue = ""),
            MethodSetting.TextSetting("subject_id", "Patient / participant / case ID", defaultValue = ""),
            MethodSetting.TextSetting("session_label", "Session label", defaultValue = ""),
            MethodSetting.TextSetting("answers_json", "Pre-supplied answers JSON", defaultValue = "")
        )
    )
}
