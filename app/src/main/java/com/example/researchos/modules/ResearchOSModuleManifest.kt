package com.example.researchos.modules

/** Explicit, auditable module manifest. */
object ResearchOSModuleManifest {
    val modules: List<ResearchOSModule> = listOf(
        com.example.researchos.modules.nfc.NfcModule,
        com.example.researchos.modules.adminfingerprint.AdminFingerprintModule,
        com.example.researchos.modules.gpstargetnavigator.GpsTargetNavigatorModule,
        com.example.researchos.modules.calibratedscale.CalibratedScaleModule,
        com.example.researchos.modules.choiceexperiment.ChoiceExperimentModule,
        com.example.researchos.modules.qrcode.QrCodeModule,
        com.example.researchos.modules.attestation.AttestationModule
    ).also { registered ->
        require(registered.map { it.moduleId }.distinct().size == registered.size) {
            "ResearchOS module IDs must be unique."
        }
    }.sortedBy { it.moduleId }
}
