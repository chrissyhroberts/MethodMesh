package com.example.researchos.modules.adminfingerprint

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object AdminFingerprintModule : ResearchOSModule {
    override val moduleId: String = "adminfingerprint"
    override val displayName: String = "Local device authentication"


    override fun as100Methods() = listOf(As100VerifyFingerprintMethod)

    override fun rilBindings() = listOf(
        RilBinding("authorize local access", As100VerifyFingerprintMethod.ID, "Authorise access using a device biometric or credential"),
        RilBinding("authenticate on device", As100VerifyFingerprintMethod.ID, "Authenticate locally without making a person-identity claim"),
        RilBinding("require biometric", As100VerifyFingerprintMethod.ID, "Require an enrolled Android biometric"),
        RilBinding("require device credential", As100VerifyFingerprintMethod.ID, "Require the configured PIN, pattern or password")
    )

    override fun capabilityScreens() = listOf(AdminFingerprintCapabilityScreen)
}
