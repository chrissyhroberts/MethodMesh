package com.example.methodmesh.modules.paperbridge

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object PaperBridgeModule : MethodMeshModule {
    override val moduleId = "paperbridge"
    override val displayName = "Paper Bridge"
    override val summary = "Design portable paper-form mappings from standard survey/choices workbooks, scan registered questionnaires, review uncertainty, and return validated data with source and registered-page evidence."
    override val iconKey = "document"

    val maturityTag = PaperBridgeContractMetadata.MATURITY
    val connectivityTag = PaperBridgeContractMetadata.CONNECTIVITY

    override fun as100Methods() = listOf(As100PaperFormTranscribeMethod, As100PaperFormDesignMethod)

    override fun rilBindings() = listOf(
        RilBinding("transcribe paper form", As100PaperFormTranscribeMethod.ID, "Scan a registered paper questionnaire and return validated field values"),
        RilBinding("scan paper questionnaire", As100PaperFormTranscribeMethod.ID, "Capture a paper form, review uncertain fields and Commit the result"),
        RilBinding("design paper form", As100PaperFormDesignMethod.ID, "Match a registered paper questionnaire to survey/choices workbook fields and recognition regions"),
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
                label = "Auto-accept constrained numeric OCR",
                description = "Off by default. Integer/decimal candidates may auto-accept when constraints pass; free-text OCR always requires human confirmation.",
                group = "Review",
                defaultValue = false
            ),
        )
    )
}
