package com.example.researchos.modules

/**
 * Explicit module manifest.
 *
 * Module code remains owned by modules/<capability>/; this is the single central
 * index that makes capabilities visible to registries, dashboards, RIL and
 * external workflows. It replaces fragile Dex scanning and hidden fallback
 * behaviour with one auditable registration point.
 */
object ResearchOSModuleManifest {
    val modules: List<ResearchOSModule> = listOf(
        com.example.researchos.modules.nfc.NfcModule,
        com.example.researchos.modules.adminfingerprint.AdminFingerprintModule,
        com.example.researchos.modules.gpstargetnavigator.GpsTargetNavigatorModule,
        com.example.researchos.modules.calibratedscale.CalibratedScaleModule,
        com.example.researchos.modules.choiceexperiment.ChoiceExperimentModule,
        com.example.researchos.modules.qrcode.QrCodeModule,
        com.example.researchos.modules.attestation.AttestationModule
    ).distinctBy { it.moduleId }.sortedBy { it.moduleId }
}
