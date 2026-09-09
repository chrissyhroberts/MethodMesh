package com.example.methodmesh.modules.paperbridge

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object PaperBridgeModule : MethodMeshModule {
    override val moduleId = "paperbridge"
    override val displayName = "Paper Bridge"
    override val summary = "Visually design ODK-mapped paper forms, scan anchored questionnaires, review uncertainty, and return validated structured data to the calling workflow."
    override val iconKey = "document"

    val maturityTag = PaperBridgeContractMetadata.MATURITY
    val connectivityTag = PaperBridgeContractMetadata.CONNECTIVITY

    override fun as100Methods() = listOf(As100PaperFormTranscribeMethod, As100PaperFormDesignMethod)

    override fun rilBindings() = listOf(
        RilBinding("transcribe paper form", As100PaperFormTranscribeMethod.ID, "Scan an anchored paper questionnaire and return validated field values"),
        RilBinding("scan paper questionnaire", As100PaperFormTranscribeMethod.ID, "Capture a paper form, review uncertain fields and Commit the result"),
        RilBinding("design paper form", As100PaperFormDesignMethod.ID, "Visually map a blank paper questionnaire to ODK variables and recognition regions"),
        RilBinding("mark up paper questionnaire", As100PaperFormDesignMethod.ID, "Place and edit persistent regions, link return values and validate a Paper Bridge form")
    )

    override fun capabilityScreens() = listOf(PaperFormTranscribeCapabilityScreen, PaperFormDesignCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100PaperFormDesignMethod.ID to emptyList(),
        As100PaperFormTranscribeMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = PaperBridgeInputs.TEMPLATE_JSON,
                label = "Paper template manifest",
                description = "Versioned MethodMesh paper manifest. Native UI keeps the JSON out of the working surface.",
                group = "Template",
                defaultValue = PaperTemplateSamples.DEMO_MANIFEST_JSON
            ),
            MethodSetting.ChoiceSetting(
                id = PaperBridgeInputs.INPUT_SOURCE,
                label = "Input source",
                description = "Capture a new photograph or choose an existing image.",
                group = "Acquisition",
                defaultValue = "camera",
                choices = listOf("camera", "file_picker")
            ),
            MethodSetting.BooleanSetting(
                id = PaperBridgeInputs.AUTO_ACCEPT_OMR,
                label = "Auto-accept unambiguous marks",
                description = "Only accepts marks that pass threshold and separation checks; ambiguity always goes to review.",
                group = "Review",
                defaultValue = true
            ),
            MethodSetting.BooleanSetting(
                id = PaperBridgeInputs.AUTO_ACCEPT_OCR,
                label = "Auto-accept validated OCR",
                description = "Off by default. OCR candidates normally require human confirmation even when constraints pass.",
                group = "Review",
                defaultValue = false
            ),
            MethodSetting.BooleanSetting(
                id = PaperBridgeInputs.RETURN_SOURCE_IMAGE,
                label = "Return source image",
                description = "Expose the captured page as a caller-owned attachment after Commit.",
                group = "Returns",
                defaultValue = true
            ),
            MethodSetting.BooleanSetting(
                id = PaperBridgeInputs.RETURN_RECTIFIED_IMAGE,
                label = "Return rectified image",
                description = "Expose the anchor-rectified page as a caller-owned attachment after Commit.",
                group = "Returns",
                defaultValue = true
            )
        )
    )
}
