package com.example.researchos.modules.mlkitvision

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object MlKitVisionModule : ResearchOSModule {
    override val moduleId = "mlkitvision"
    override val displayName = "ML Kit vision"
    override val summary = "Use ML Kit for OCR and barcode/Data Matrix/1D code detection from camera or image files."

    override fun as100Methods() = listOf(As100MlKitVisionMethod)

    override fun rilBindings() = listOf(
        RilBinding("read text with ML Kit", As100MlKitVisionMethod.ID, "Capture or select an image and run ML Kit OCR"),
        RilBinding("scan barcode with ML Kit", As100MlKitVisionMethod.ID, "Capture or select an image and detect barcodes/Data Matrix/QR/1D codes"),
        RilBinding("analyse image with ML Kit", As100MlKitVisionMethod.ID, "Run OCR and barcode detection on an image")
    )

    override fun capabilityScreens() = listOf(MlKitVisionCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100MlKitVisionMethod.ID to listOf(
            MethodSetting.ChoiceSetting("mlkit_mode", "Analysis mode", defaultValue = "ocr_and_barcodes", choices = listOf("ocr", "barcodes", "ocr_and_barcodes")),
            MethodSetting.ChoiceSetting("input_source", "Input source", defaultValue = "camera", choices = listOf("camera", "file_picker")),
            MethodSetting.BooleanSetting("return_pdf", "Return PDF attachment", defaultValue = true),
            MethodSetting.BooleanSetting("return_text_file", "Return OCR text file", defaultValue = true),
            MethodSetting.BooleanSetting("prefer_offline", "Prefer offline/on-device processing", defaultValue = true)
        )
    )
}
