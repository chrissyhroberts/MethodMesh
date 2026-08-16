package com.example.researchos.modules.qrcode

import com.example.researchos.modules.ModuleExample
import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object QrCodeModule : ResearchOSModule {
    override val moduleId: String = "qrcode"
    override val displayName: String = "Automatic code scanner"
    override val summary: String = "Automatically capture QR, Data Matrix, and common 1D barcode evidence."

    override fun as100Methods() = listOf(As100QrScanMethod)

    override fun rilBindings() = listOf(
        RilBinding("scan qr", As100QrScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("read qr", As100QrScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("capture qr", As100QrScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("scan qr token", As100QrScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("scan code", As100QrScanMethod.ID, "Automatically capture a supported 1D or 2D code"),
        RilBinding("scan barcode", As100QrScanMethod.ID, "Automatically capture a supported 1D or 2D code"),
        RilBinding("scan data matrix", As100QrScanMethod.ID, "Capture a Data Matrix code")
    )

    override fun capabilityScreens() = listOf(QrScanCapabilityScreen)

    override fun capabilitySettings() = mapOf(As100QrScanMethod.ID to listOf(
        MethodSetting.ChoiceSetting(
            "barcode_formats",
            "Accepted code formats",
            "Choose a supported scanner profile; automatic detection is usually best.",
            "Scanner",
            "",
            listOf(
                "",
                "QR_CODE",
                "DATA_MATRIX",
                "QR_CODE|DATA_MATRIX",
                "CODE_128|CODE_39|EAN_13|EAN_8|UPC_A|UPC_E",
                "DATA_MATRIX|CODE_128"
            )
        )
    ))

    override fun examples() = listOf(
        ModuleExample(
            title = "Capture a QR, Data Matrix, or barcode token",
            ril = "WHAT; scan qr; WHERE; participant/P001; RESULT; return qr_payload_hash, qr_payload; format json",
            notes = "This is a standalone code-scanning capability so other modules, including attestation, can depend on captured code evidence rather than reimplementing scanner behaviour."
        )
    )
}
