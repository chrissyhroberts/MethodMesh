package com.example.methodmesh.modules.qrcode

import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object QrCodeModule : MethodMeshModule {
    override val moduleId: String = "barcode"
    override val displayName: String = "Automatic code scanner"
    override val summary: String = "Automatically capture QR, Data Matrix, and common 1D barcode evidence."

    override fun as100Methods() = listOf(As100BarcodeScanMethod)

    override fun rilBindings() = listOf(
        RilBinding("scan qr", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("read qr", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("capture qr", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("scan qr token", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("scan code", As100BarcodeScanMethod.ID, "Automatically capture a supported 1D or 2D code"),
        RilBinding("scan barcode", As100BarcodeScanMethod.ID, "Automatically capture a supported 1D or 2D code"),
        RilBinding("scan data matrix", As100BarcodeScanMethod.ID, "Capture a Data Matrix code")
    )

    override fun capabilityScreens() = listOf(BarcodeScanCapabilityScreen)

    private val scannerSettings = listOf(
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
    )

    override fun capabilitySettings() = mapOf(
        As100BarcodeScanMethod.ID to scannerSettings
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Capture a QR, Data Matrix, or barcode token",
            ril = "WHAT; scan barcode; WHERE; participant/P001; RESULT; return barcode_payload, barcode_format; format json",
            notes = "This is a standalone code-scanning capability so other modules, including attestation, can depend on captured code evidence rather than reimplementing scanner behaviour."
        )
    )
}
