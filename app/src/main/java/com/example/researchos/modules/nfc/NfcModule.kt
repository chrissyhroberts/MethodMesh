package com.example.researchos.modules.nfc

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object NfcModule : ResearchOSModule {
    override val moduleId: String = "nfc"
    override val displayName: String = "NFC"

    override fun legacyMethods() = listOf(NfcReadMethod(), NfcWriteMethod())

    override fun as100Methods() = listOf(As100NfcReadMethod, As100NfcWriteMethod)

    override fun rilBindings() = listOf(
        RilBinding("scan nfc", "nfc.read", "Read an NFC tag"),
        RilBinding("read nfc", "nfc.read", "Read an NFC tag"),
        RilBinding("capture nfc", "nfc.read", "Read an NFC tag"),
        RilBinding("scan tag", "nfc.read", "Read an NFC tag"),
        RilBinding("read tag", "nfc.read", "Read an NFC tag"),
        RilBinding("write nfc", "nfc.write", "Write an NFC tag"),
        RilBinding("write tag", "nfc.write", "Write an NFC tag")
    )

    override fun capabilityScreens() = listOf(NfcReadCapabilityScreen)
}
