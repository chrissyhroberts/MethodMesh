package com.example.researchos.modules.questionprimitives

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object QuestionPrimitivesModule : ResearchOSModule {
    override val moduleId = "questionprimitives"
    override val displayName = "Question primitives"
    override val summary = "Reusable text, number, select-one and select-multiple question blocks for native ResearchOS protocols."

    override fun as100Methods() = listOf(
        QuestionTextMethod,
        QuestionNumberMethod,
        QuestionSelectOneMethod,
        QuestionSelectMultipleMethod
    )

    override fun capabilityScreens() = listOf(
        QuestionTextCapabilityScreen,
        QuestionNumberCapabilityScreen,
        QuestionSelectOneCapabilityScreen,
        QuestionSelectMultipleCapabilityScreen
    )

    override fun rilBindings() = listOf(
        RilBinding("ask text question", QuestionTextMethod.id, "Capture a validated text answer"),
        RilBinding("ask number question", QuestionNumberMethod.id, "Capture a validated numeric answer"),
        RilBinding("ask select one question", QuestionSelectOneMethod.id, "Capture one selected answer"),
        RilBinding("ask select multiple question", QuestionSelectMultipleMethod.id, "Capture multiple selected answers")
    )

    override fun capabilitySettings() = mapOf(
        QuestionTextMethod.id to commonSettings(),
        QuestionNumberMethod.id to commonSettings() + listOf(
            MethodSetting.TextSetting("min", "Minimum", defaultValue = ""),
            MethodSetting.TextSetting("max", "Maximum", defaultValue = "")
        ),
        QuestionSelectOneMethod.id to commonSettings() + optionSettings(),
        QuestionSelectMultipleMethod.id to commonSettings() + optionSettings()
    )

    private fun commonSettings() = listOf(
        MethodSetting.TextSetting("question_id", "Question ID", defaultValue = "question_001"),
        MethodSetting.TextSetting("prompt", "Prompt", defaultValue = "Question"),
        MethodSetting.TextSetting("hint", "Hint", defaultValue = ""),
        MethodSetting.TextSetting("answer", "Default answer", defaultValue = ""),
        MethodSetting.BooleanSetting("required", "Required", defaultValue = false),
        MethodSetting.TextSetting("regex", "Regex constraint", defaultValue = ""),
        MethodSetting.TextSetting("constraint_message", "Constraint message", defaultValue = "Answer does not meet the required constraint.")
    )

    private fun optionSettings() = listOf(
        MethodSetting.TextSetting("options", "Options", defaultValue = "Yes|No|Unknown")
    )
}
