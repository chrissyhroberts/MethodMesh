package com.example.methodmesh.modules.imageredaction

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.settings.MethodSetting

object ImageRedactionModule : MethodMeshModule {
    override val moduleId = "imageredaction"
    override val displayName = "Image annotation and redaction"
    override val summary = "Redact a new or saved photo, a scanned document or a PDF. Share only the redacted copy."
    override fun dependencies() = listOf(ModuleDependency("documentscanner", "Scan paper pages through document.scan before PDF redaction."))

    override fun as100Methods() = listOf(As100ImageRedactionMethod, As100PdfRedactionMethod)
    override fun capabilityScreens() = listOf(ImageRedactionCapabilityScreen, PdfRedactionCapabilityScreen)
    override fun rilBindings() = listOf(
        RilBinding("redact image", As100ImageRedactionMethod.ID, "Mask selected image regions and return a redacted image"),
        RilBinding("redact pdf", As100PdfRedactionMethod.ID, "Open a PDF, remove selected regions and share the redacted copy")
    )

    override fun capabilitySettings() = mapOf(
        As100ImageRedactionMethod.ID to listOf(
            MethodSetting.ChoiceSetting("input_source", "Input source", defaultValue = "camera", choices = listOf("camera", "file_picker")),
            MethodSetting.IntSetting("grid_rows", "Grid rows", defaultValue = 10, minimum = 1, maximum = 50),
            MethodSetting.IntSetting("grid_columns", "Grid columns", defaultValue = 10, minimum = 1, maximum = 50),
            MethodSetting.ChoiceSetting("redaction_style", "Redaction style", defaultValue = "black", choices = listOf("black", "white"))
        ),
        As100PdfRedactionMethod.ID to listOf(
            MethodSetting.ChoiceSetting("document_source", "Document source", defaultValue = "file_picker",
                choices = listOf("file_picker", "document_scanner")),
            MethodSetting.TextSetting("pdf_uri", "PDF from device", defaultValue = "",
                description = "Optional readable content URI supplied by a caller/preset. Otherwise choose a PDF when the tool opens.")
        )
    )
}
