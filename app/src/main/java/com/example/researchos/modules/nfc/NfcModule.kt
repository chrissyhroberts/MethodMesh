package com.example.researchos.modules.nfc

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object NfcModule : ResearchOSModule {
    override val moduleId: String = "nfc"
    override val displayName: String = "NFC"


    override fun as100Methods() = listOf(As100NfcReadMethod, As100NfcWriteMethod)

    override fun rilBindings() = listOf(
        RilBinding("scan nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("read nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("capture nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("scan tag", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("read tag", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("write nfc", As100NfcWriteMethod.ID, "Write an NFC tag"),
        RilBinding("write tag", As100NfcWriteMethod.ID, "Write an NFC tag")
    )

    override fun capabilityScreens() = listOf(NfcReadCapabilityScreen, NfcWriteCapabilityScreen)
}
