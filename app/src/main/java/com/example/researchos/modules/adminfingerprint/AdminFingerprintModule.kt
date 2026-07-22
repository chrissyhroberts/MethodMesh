package com.example.researchos.modules.adminfingerprint

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object AdminFingerprintModule : ResearchOSModule {
    override val moduleId: String = "adminfingerprint"
    override val displayName: String = "Admin fingerprint"


    override fun as100Methods() = listOf(As100VerifyFingerprintMethod)

    override fun rilBindings() = listOf(
        RilBinding("verify identity fingerprint", As100VerifyFingerprintMethod.ID, "Verify identity using the device biometric/credential prompt"),
        RilBinding("verify identity", As100VerifyFingerprintMethod.ID, "Verify identity using the device biometric/credential prompt"),
        RilBinding("verify fingerprint", As100VerifyFingerprintMethod.ID, "Verify identity using the device biometric/credential prompt"),
        RilBinding("identify fingerprint", As100VerifyFingerprintMethod.ID, "Verify identity using the device biometric/credential prompt"),
        RilBinding("verify admin fingerprint", As100VerifyFingerprintMethod.ID, "Verify identity using the device biometric/credential prompt")
    )

    override fun capabilityScreens() = listOf(AdminFingerprintCapabilityScreen)
}
