package com.example.methodmesh.modules.qrcode

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object QrCodeModule : MethodMeshModule {
    // Historical IDs are intentionally retained for saved presets, protocols,
    // XLSForms and external callers.
    override val moduleId: String = "barcode"
    override val displayName: String = "Codes"
    override val summary: String = "Scan codes, create scannable codes from exact text, or clone a scanned payload into another compatible symbology."
    override val iconKey: String = "tool"

    val maturityTag: String = "Development"
    val connectivityTag: String = "Offline"

    override fun as100Methods() = listOf(
        As100BarcodeScanMethod,
        As100BarcodeGenerateMethod,
        As100BarcodeCloneMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("scan qr", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("read qr", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("capture qr", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("scan qr token", As100BarcodeScanMethod.ID, "Capture a QR token as verifiable workflow evidence"),
        RilBinding("scan code", As100BarcodeScanMethod.ID, "Automatically capture a supported 1D or 2D code"),
        RilBinding("scan barcode", As100BarcodeScanMethod.ID, "Automatically capture a supported 1D or 2D code"),
        RilBinding("scan data matrix", As100BarcodeScanMethod.ID, "Capture a Data Matrix code"),
        RilBinding("create qr", As100BarcodeGenerateMethod.ID, "Create a scannable code from exact payload text"),
        RilBinding("create code", As100BarcodeGenerateMethod.ID, "Create a scannable code from exact payload text"),
        RilBinding("create barcode", As100BarcodeGenerateMethod.ID, "Create a scannable code from exact payload text"),
        RilBinding("show barcode", As100BarcodeGenerateMethod.ID, "Present exact payload text as a scannable code"),
        RilBinding("clone barcode", As100BarcodeCloneMethod.ID, "Scan a code and re-present its exact payload"),
        RilBinding("clone code", As100BarcodeCloneMethod.ID, "Scan a code and re-present its exact payload"),
        RilBinding("copy barcode", As100BarcodeCloneMethod.ID, "Scan a code and re-present its exact payload")
    )

    override fun capabilityScreens() = listOf(
        BarcodeScanCapabilityScreen,
        BarcodeGenerateCapabilityScreen,
        BarcodeCloneCapabilityScreen
    )

    private val scannerSettings = listOf(
        MethodSetting.MultiChoiceSetting(
            "barcode_formats",
            "Accepted code formats",
            "Leave automatic on to accept every supported format, or choose specific barcode formats.",
            "Scanner",
            "",
            listOf(
                "QR_CODE", "DATA_MATRIX", "PDF_417", "AZTEC",
                "CODE_128", "CODE_39", "EAN_13", "EAN_8", "UPC_A", "UPC_E"
            ),
            emptyMeansAll = true
        )
    )

    private val generatorSettings = listOf(
        MethodSetting.TextSetting(
            "barcode_payload",
            "Payload",
            "Exact text to encode. Keep this a runtime input for reusable presets unless a fixed card/token is intentional.",
            "Content",
            ""
        ),
        MethodSetting.ChoiceSetting(
            "barcode_format",
            "Starting format",
            "QR is the safest default. Incompatible formats are skipped rather than changing the payload.",
            "Presentation",
            "QR_CODE",
            listOf("QR_CODE", "CODE_128", "DATA_MATRIX", "AZTEC", "PDF_417", "CODE_39", "EAN_13", "EAN_8", "UPC_A", "UPC_E")
        ),
        MethodSetting.BooleanSetting(
            "barcode_auto_cycle",
            "Start format cycling",
            "Cycle through formats that can represent the exact payload until stopped.",
            "Presentation",
            false
        )
    )



    private val cloneSettings = listOf(
        MethodSetting.ChoiceSetting(
            "barcode_clone_format",
            "Starting clone format",
            "SOURCE starts with the detected source symbology when that exact payload can be represented. Swipe to other compatible formats at runtime.",
            "Presentation",
            "SOURCE",
            listOf("SOURCE", "QR_CODE", "CODE_128", "DATA_MATRIX", "AZTEC", "PDF_417", "CODE_39", "EAN_13", "EAN_8", "UPC_A", "UPC_E")
        ),
        MethodSetting.BooleanSetting(
            "barcode_auto_cycle",
            "Start format cycling",
            "Cycle through compatible clone formats until stopped.",
            "Presentation",
            false
        ),
        MethodSetting.BooleanSetting(
            "barcode_return_text_payload",
            "Return text payload",
            "Also return the exact decoded text alongside the cloned PNG. Keep enabled for ODK workflows that need both image and text; disable when the image artefact is sufficient.",
            "Return",
            true
        )
    )

    override fun capabilitySettings() = mapOf(
        As100BarcodeScanMethod.ID to scannerSettings,
        As100BarcodeGenerateMethod.ID to generatorSettings,
        As100BarcodeCloneMethod.ID to cloneSettings
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Capture a QR, Data Matrix, or barcode token",
            ril = "WHAT; scan barcode; WHERE; participant/P001; RESULT; return barcode_payload, barcode_format; format json",
            notes = "The same barcode.scan contract is used by direct native runs, presets, protocols, schedules/widgets and ODK/XLSForm."
        ),
        ModuleExample(
            title = "Create a scannable code",
            ril = "WHAT; create barcode; RESULT; return barcode_payload, barcode_format; format json",
            notes = "barcode.generate preserves the exact payload. QR is the default; swipe or Cycle moves only through compatible formats."
        ),
        ModuleExample(
            title = "Clone a scanned code",
            ril = "WHAT; clone barcode; RESULT; return barcode_clone_image_uri, barcode_payload, barcode_source_format, barcode_clone_format; format json",
            notes = "barcode.clone scans once, preserves the exact decoded payload, returns the cloned PNG as the primary artefact, and can optionally return the text payload alongside it."
        )
    )
}
