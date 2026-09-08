package com.example.methodmesh.modules.conversationtranslate

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.modules.mlkittranslate.commonMlKitLanguageCodes
import com.example.methodmesh.settings.MethodSetting

object ConversationTranslateModule : MethodMeshModule {
    override val moduleId = "conversationtranslate"
    override val displayName = "Conversation translator"
    override val summary = "Run an operator-controlled bilingual conversation translator and transcript."

    override fun as100Methods() = listOf(As100ConversationTranslateMethod)

    override fun rilBindings() = listOf(
        RilBinding("translate conversation", As100ConversationTranslateMethod.ID, "Run a bilingual speech translation session"),
        RilBinding("conversation translator", As100ConversationTranslateMethod.ID, "Capture a translated conversation transcript")
    )

    override fun capabilityScreens() = listOf(ConversationTranslateCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100ConversationTranslateMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "language_a",
                label = "Language A",
                description = "First conversation language. The ML Kit translation model must be available for offline use.",
                group = "Languages",
                defaultValue = "en",
                choices = commonMlKitLanguageCodes
            ),
            MethodSetting.ChoiceSetting(
                id = "language_b",
                label = "Language B",
                description = "Second conversation language. The ML Kit translation model must be available for offline use.",
                group = "Languages",
                defaultValue = "es",
                choices = commonMlKitLanguageCodes
            ),
            MethodSetting.TextSetting("label_a", "Button label A", group = "Display", defaultValue = "Speak"),
            MethodSetting.TextSetting("label_b", "Button label B", group = "Display", defaultValue = "Habla"),
            MethodSetting.BooleanSetting(
                id = "spoken_output",
                label = "Speak translated text aloud",
                description = "Use Android text-to-speech after each translation.",
                group = "Audio",
                defaultValue = true
            ),
            MethodSetting.BooleanSetting(
                id = "prefer_offline",
                label = "Prefer offline speech recognition",
                description = "Ask Android speech recognition to use offline recognition when available.",
                group = "Audio",
                defaultValue = true
            )
        )
    )
}
