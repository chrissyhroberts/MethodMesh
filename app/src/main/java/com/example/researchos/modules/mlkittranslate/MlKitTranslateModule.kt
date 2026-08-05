package com.example.researchos.modules.mlkittranslate

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object MlKitTranslateModule : ResearchOSModule {
    override val moduleId = "mlkittranslate"
    override val displayName = "ML Kit translation"
    override val summary = "Download, remove, list and use ML Kit on-device translation language models."

    override fun as100Methods() = listOf(As100MlKitTranslateMethod)

    override fun rilBindings() = listOf(
        RilBinding("translate text with ML Kit", As100MlKitTranslateMethod.ID, "Translate text using downloaded ML Kit language models"),
        RilBinding("manage ML Kit translation languages", As100MlKitTranslateMethod.ID, "List, download and remove on-device translation language models")
    )

    override fun capabilityScreens() = listOf(MlKitTranslateCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100MlKitTranslateMethod.ID to listOf(
            MethodSetting.ChoiceSetting("source_language", "Source language", defaultValue = "en", choices = commonMlKitLanguageCodes),
            MethodSetting.ChoiceSetting("target_language", "Target language", defaultValue = "fr", choices = commonMlKitLanguageCodes),
            MethodSetting.TextSetting("input_text", "Text to translate", defaultValue = "hello world"),
            MethodSetting.ChoiceSetting("model_action", "Model action", defaultValue = "translate", choices = listOf("translate", "download", "delete", "list"))
        )
    )
}

val commonMlKitLanguageCodes = listOf(
    "af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de", "el", "en", "eo", "es", "et", "fa",
    "fi", "fr", "ga", "gl", "gu", "he", "hi", "hr", "ht", "hu", "id", "is", "it", "ja", "ka", "kn",
    "ko", "lt", "lv", "mk", "mr", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq",
    "sv", "sw", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh"
)
