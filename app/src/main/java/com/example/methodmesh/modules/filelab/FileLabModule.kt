package com.example.methodmesh.modules.filelab

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object FileLabModule : MethodMeshModule {
    override val moduleId = "filelab"
    override val displayName = "File Lab"
    override val summary = "Identify and explain unfamiliar files safely, then run explicit offline conversions."
    override val iconKey = "document"

    // Current host exposes reviewed-module status as module-owned strings; capability
    // descriptor metadata below remains authoritative for each method.
    val maturityTag = "Development"
    val connectivityTag = "Offline"

    override fun as100Methods() = listOf(As100FileInspectMethod, As100FileConvertMethod)

    override fun rilBindings() = listOf(
        RilBinding("inspect file", As100FileInspectMethod.ID, "Identify and inspect a file without modifying it"),
        RilBinding("identify file", As100FileInspectMethod.ID, "Identify and inspect a file without modifying it"),
        RilBinding("convert file", As100FileConvertMethod.ID, "Convert a supported file using a declared local route")
    )

    override fun capabilityScreens() = listOf(FileInspectCapabilityScreen, FileConvertCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100FileInspectMethod.ID to emptyList(),
        As100FileConvertMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "target_format",
                label = "Target format",
                description = "Targets are source-dependent. Current executable routes produce PDF or CBZ.",
                group = "Conversion",
                defaultValue = "pdf",
                choices = listOf("pdf", "cbz")
            ),
            MethodSetting.IntSetting(
                id = "max_dimension",
                label = "Maximum raster dimension",
                description = "Used for PDF → CBZ rasterisation and image → PDF scaling.",
                group = "Conversion",
                defaultValue = 2400,
                minimum = 256,
                maximum = 12000,
                step = 100,
                unit = "px"
            ),
            MethodSetting.IntSetting(
                id = "jpeg_quality",
                label = "JPEG quality",
                description = "Used only for PDF → CBZ rasterisation.",
                group = "PDF to CBZ",
                defaultValue = 92,
                minimum = 1,
                maximum = 100,
                step = 1,
                unit = "%"
            )
        )
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Inspect a field file",
            ril = "WHAT; inspect file; RESULT; return filelab_inspection_summary, filelab_sha256; format json",
            notes = "Content-first detection; the source is never modified."
        ),
        ModuleExample(
            title = "Convert a comic archive",
            ril = "WHAT; convert file; RESULT; return filelab_output_uri, filelab_output_sha256; format json",
            notes = "Executable routes include CBZ→PDF, PDF→CBZ, common text→PDF and common image→PDF."
        )
    )
}
