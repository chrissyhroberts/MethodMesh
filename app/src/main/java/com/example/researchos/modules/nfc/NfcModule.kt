package com.example.researchos.modules.nfc

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

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

    override fun capabilitySettings() = mapOf(
        As100NfcWriteMethod.ID to listOf(
            MethodSetting.TextSetting("value", "Value to write", defaultValue = ""),
            MethodSetting.TextSetting("record_type", "Record type", defaultValue = "text/plain"),
            MethodSetting.ChoiceSetting("overwrite_policy", "Overwrite policy", defaultValue = "replace", choices = listOf("replace", "empty_only"))
        ),
        As100NfcCredentialProvisioningMethod.ID to listOf(
            MethodSetting.TextSetting("credential_subject_id", "Credential subject", defaultValue = ""),
            MethodSetting.IntSetting("pin_length", "PIN length", defaultValue = 6, minimum = 4, maximum = 6),
            MethodSetting.ChoiceSetting("overwrite_policy", "Overwrite policy", defaultValue = "empty_only", choices = listOf("empty_only", "replace"))
        ),
        As100NfcCredentialVerificationMethod.ID to listOf(
            MethodSetting.TextSetting("trusted_issuer_key_ids", "Trusted issuer key IDs", "Comma-separated; leave blank to report issuer trust as not checked.", defaultValue = "")
        )
    )
}
