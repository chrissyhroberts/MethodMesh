package com.example.researchos.modules

/**
 * Explicit capability manifest.
 *
 * This is the only central file that must change when a built-in capability is
 * added or removed. All method definitions, RIL bindings, capability screens,
 * examples and output helpers remain owned by the module folder itself.
 */
object ResearchOSModuleManifest {
    val modules: List<ResearchOSModule> = listOf(
        com.example.researchos.modules.nfc.NfcModule,
        com.example.researchos.modules.adminfingerprint.AdminFingerprintModule,
        com.example.researchos.modules.gpstargetnavigator.GpsTargetNavigatorModule,
        com.example.researchos.modules.calibratedscale.CalibratedScaleModule,
        com.example.researchos.modules.choiceexperiment.ChoiceExperimentModule
    ).distinctBy { it.moduleId }.sortedBy { it.moduleId }
}
