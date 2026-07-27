package com.example.researchos.modules.nfc

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object NfcModule : ResearchOSModule {
    override val moduleId: String = "nfc"
    override val displayName: String = "NFC"


    override fun as100Methods() = listOf(
        As100NfcReadMethod,
        As100NfcWriteMethod,
        As100NfcWipeMethod,
        As100NfcCredentialProvisioningMethod,
        As100NfcCredentialVerificationMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("scan nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("read nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("capture nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("scan tag", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("read tag", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("write nfc", As100NfcWriteMethod.ID, "Write an NFC tag"),
        RilBinding("write tag", As100NfcWriteMethod.ID, "Write an NFC tag"),
        RilBinding("wipe nfc", As100NfcWipeMethod.ID, "Remove NDEF user content from an NFC tag"),
        RilBinding("wipe tag", As100NfcWipeMethod.ID, "Remove NDEF user content from an NFC tag"),
        RilBinding(
            "provision nfc credential",
            As100NfcCredentialProvisioningMethod.ID,
            "Create a PIN-protected portable NFC credential"
        ),
        RilBinding(
            "verify nfc credential",
            As100NfcCredentialVerificationMethod.ID,
            "Verify a portable NFC credential and PIN"
        )
    )

    override fun capabilityScreens() = listOf(
        NfcReadCapabilityScreen,
        NfcWriteCapabilityScreen,
        NfcWipeCapabilityScreen,
        NfcCredentialProvisioningCapabilityScreen,
        NfcCredentialVerificationCapabilityScreen
    )
}
