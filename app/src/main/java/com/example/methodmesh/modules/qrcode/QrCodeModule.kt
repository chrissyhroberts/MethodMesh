package com.example.methodmesh.modules.qrcode

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object QrCodeModule : MethodMeshModule {
    // Historical IDs are intentionally retained for saved presets, protocols,
    // XLSForms and external callers.
    override val moduleId: String = "barcode"
    override val displayName: String = "Automatic code scanner"
    override val summary: String = "Scan QR, Data Matrix, Aztec, PDF417, and common 1D codes into canonical evidence."
    override val iconKey: String = "tool"

    // Current Master Book status metadata. The host interface does not yet expose
    // typed status properties, so these module-owned values follow the pattern
    // used by reviewed modules while capability-level descriptor metadata remains
    // authoritative for generic discovery.
    val maturityTag: String = "Production"
    val connectivityTag: String = "Offline"

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
        MethodSetting.MultiChoiceSetting(
            "barcode_formats",
            "Accepted code formats",
            "Leave automatic on to accept every supported format, or choose specific barcode formats.",
            "Scanner",
            "",
            listOf(
                "QR_CODE",
                "DATA_MATRIX",
                "PDF_417",
                "AZTEC",
                "CODE_128",
                "CODE_39",
                "EAN_13",
                "EAN_8",
                "UPC_A",
                "UPC_E"
            ),
            emptyMeansAll = true
        )
    )

    override fun capabilitySettings() = mapOf(
        As100BarcodeScanMethod.ID to scannerSettings
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Capture a QR, Data Matrix, or barcode token",
            ril = "WHAT; scan barcode; WHERE; participant/P001; RESULT; return barcode_payload, barcode_format; format json",
            notes = "The same barcode.scan contract is used by direct native runs, presets, protocols, schedules/widgets and ODK/XLSForm."
        )
    )
}
