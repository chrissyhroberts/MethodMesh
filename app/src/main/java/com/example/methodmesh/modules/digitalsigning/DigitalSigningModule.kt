package com.example.methodmesh.modules.digitalsigning

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object DigitalSigningModule : MethodMeshModule {
    override val moduleId = "digitalsigning"
    override val displayName = "Digital signing"
    override val summary = "Open a PDF full-screen, annotate with freehand ink, commit a new version, and optionally obtain a trusted RFC 3161 timestamp."
    override val iconKey = "document"

    override fun as100Methods() = listOf(As100DigitalSigningMethod)

    override fun rilBindings() = listOf(
        RilBinding("sign pdf", As100DigitalSigningMethod.ID, "Ink-sign or mark up a PDF and commit a new document"),
        RilBinding("digitally sign document", As100DigitalSigningMethod.ID, "Add visible handwritten ink to a PDF"),
        RilBinding("markup pdf", As100DigitalSigningMethod.ID, "Annotate a PDF with freehand coloured ink"),
        RilBinding("timestamp signed pdf", As100DigitalSigningMethod.ID, "Sign a PDF and optionally request an RFC 3161 timestamp")
    )

    override fun capabilityScreens() = listOf(DigitalSigningCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100DigitalSigningMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = "pdf_uri",
                label = "PDF input URI (advanced)",
                description = "Optional stable content/file URI for protocols or other Android callers. ODK launches deliberately ignore pushed PDF paths and select the document inside the signing workspace to avoid transient attachment-path failures.",
                group = "Document",
                defaultValue = ""
            ),
            MethodSetting.BooleanSetting(
                id = "finalise_pdf",
                label = "Finalise PDF",
                description = "Raster-flatten the committed appearance to remove editable form/annotation structure. ODK calls always use this mode.",
                group = "Commit",
                defaultValue = false
            ),
            MethodSetting.BooleanSetting(
                id = "request_tsa",
                label = "Trusted timestamp",
                description = "After Commit, request an RFC 3161 timestamp over the SHA-256 of the exact committed PDF.",
                group = "Attestation",
                defaultValue = false
            ),
            MethodSetting.TextSetting(
                id = "tsa_url",
                label = "Trusted timestamp authority URL",
                description = "RFC 3161 endpoint. Defaults to the same FreeTSA endpoint used by MethodMesh attestation capabilities.",
                group = "Attestation",
                defaultValue = DigitalSigningTsaClient.DEFAULT_TSA_URL
            ),
            MethodSetting.FloatSetting(
                id = "pen_width_pt",
                label = "Ink width",
                group = "Markup",
                defaultValue = 2.8f,
                minimum = 0.8f,
                maximum = 12f,
                step = 0.4f,
                unit = "pt",
                decimals = 1
            ),
            MethodSetting.ChoiceSetting(
                id = "pen_color",
                label = "Ink colour",
                group = "Markup",
                defaultValue = "black",
                choices = listOf("black", "blue", "red", "green", "purple", "orange", "teal", "magenta")
            )
        )
    )
}
