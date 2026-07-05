package com.example.researchos.modules.adminfingerprint

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object AdminFingerprintModule : ResearchOSModule {
    override val moduleId: String = "adminfingerprint"
    override val displayName: String = "Admin fingerprint"

    override fun legacyMethods() = listOf(AdminFingerprintMethod())

    override fun as100Methods() = listOf(As100VerifyFingerprintMethod)

    override fun rilBindings() = listOf(
        RilBinding("verify identity fingerprint", "identity.verify", "Verify identity using the device biometric/credential prompt"),
        RilBinding("verify identity", "identity.verify", "Verify identity using the device biometric/credential prompt"),
        RilBinding("verify fingerprint", "identity.verify", "Verify identity using the device biometric/credential prompt"),
        RilBinding("identify fingerprint", "identity.verify", "Verify identity using the device biometric/credential prompt"),
        RilBinding("verify admin fingerprint", "identity.verify", "Verify identity using the device biometric/credential prompt")
    )

    override fun capabilityScreens() = listOf(AdminFingerprintCapabilityScreen)
}
