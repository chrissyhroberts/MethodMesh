package com.example.methodmesh.modules.documentscanner

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object DocumentScannerModule : MethodMeshModule {
    override val moduleId = "documentscanner"
    override val displayName = "Document scanner"
    override val summary = "Scan paper pages, crop/align them, OCR them, and return PDF/text attachments."

    override fun as100Methods() = listOf(As100DocumentScannerMethod)

    override fun rilBindings() = listOf(
        RilBinding("scan paper document", As100DocumentScannerMethod.ID, "Capture paper pages and return searchable PDF/OCR outputs"),
        RilBinding("scan document to PDF", As100DocumentScannerMethod.ID, "Scan pages into PDF and OCR text attachments")
    )

    override fun capabilityScreens() = listOf(DocumentScannerCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100DocumentScannerMethod.ID to listOf(
            MethodSetting.IntSetting("page_limit", "Maximum pages", defaultValue = 10, minimum = 1, maximum = 50),
            MethodSetting.ChoiceSetting("scanner_mode", "Scanner mode", defaultValue = "full", choices = listOf("full", "base_with_filter", "base")),
            MethodSetting.BooleanSetting("allow_gallery_import", "Allow gallery import", defaultValue = true),
            MethodSetting.BooleanSetting("run_ocr", "Run OCR", defaultValue = true),
            MethodSetting.BooleanSetting("return_searchable_pdf", "Return searchable PDF", defaultValue = true),
            MethodSetting.BooleanSetting("return_text_file", "Return OCR text file", defaultValue = true)
        )
    )
}
