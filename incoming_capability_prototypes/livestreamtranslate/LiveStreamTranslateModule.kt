package com.example.methodmesh.modules.livestreamtranslate

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.modules.mlkittranslate.commonMlKitLanguageCodes
import com.example.methodmesh.settings.MethodSetting

object LiveStreamTranslateModule : MethodMeshModule {
    override val moduleId = "livestreamtranslate"
    override val displayName = "Live stream translation"
    override val summary = "Listen to a meeting and provide a continuously updating translated text feed, with fixed-language and automatic-language modes."

    override fun as100Methods() = listOf(
        As100LiveStreamTranslateFixedMethod,
        As100LiveStreamTranslateAutoMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("translate meeting", As100LiveStreamTranslateFixedMethod.ID, "Listen continuously and translate a meeting from a selected source language"),
        RilBinding("live translation", As100LiveStreamTranslateFixedMethod.ID, "Run robust fixed-language live translation"),
        RilBinding("detect and translate meeting", As100LiveStreamTranslateAutoMethod.ID, "Detect the current spoken language and translate a meeting live"),
        RilBinding("multilingual meeting", As100LiveStreamTranslateAutoMethod.ID, "Run automatic-language live meeting translation")
    )

    override fun capabilityScreens() = listOf(
        LiveStreamTranslateFixedCapabilityScreen,
        LiveStreamTranslateAutoCapabilityScreen
    )

    private fun commonSettings() = listOf(
        MethodSetting.ChoiceSetting(
            id = "target_language",
            label = "Target language",
            description = "Language shown in the translated live feed.",
            group = "Languages",
            defaultValue = "en",
            choices = commonMlKitLanguageCodes
        ),
        MethodSetting.BooleanSetting(
            id = "prefer_offline",
            label = "Prefer offline Android recognition",
            description = "When the Android recognizer is active, prefer an installed on-device speech model. ML Kit Basic/GenAI recognition is already on-device after model preparation.",
            group = "Audio",
            defaultValue = false
        ),
        MethodSetting.BooleanSetting(
            id = "transcript_on_start",
            label = "Record transcript at start",
            description = "The live feed always works; this controls whether utterances are persisted into the committed transcript. It can be changed during the session.",
            group = "Transcript",
            defaultValue = true
        ),
        MethodSetting.BooleanSetting(
            id = "speaker_tagging",
            label = "Speaker tags",
            description = "Show one-tap Speaker 1/2/3… tags. The bundled speech-recognition providers do not expose reliable speaker diarisation, so speaker identity is never guessed.",
            group = "Speakers",
            defaultValue = true
        ),
        MethodSetting.ChoiceSetting(
            id = "speaker_slots",
            label = "Speaker buttons",
            description = "Number of quick speaker labels shown during the meeting.",
            group = "Speakers",
            defaultValue = "4",
            choices = listOf("2", "3", "4", "5", "6", "8")
        )
    )

    override fun capabilitySettings() = mapOf(
        As100LiveStreamTranslateFixedMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "source_language",
                label = "Source language",
                description = "Speech-recognition language. This mode deliberately does not auto-detect, making it the robust option when the meeting language is known.",
                group = "Languages",
                defaultValue = "fr",
                choices = commonMlKitLanguageCodes
            ),
            MethodSetting.ChoiceSetting(
                id = "speech_engine",
                label = "Speech recognition",
                description = "Automatic prefers ML Kit GenAI, then ML Kit Basic, then Android, with safe fallback. The active provider can be hot-switched during a meeting at utterance boundaries.",
                group = "Audio",
                defaultValue = "auto",
                choices = listOf("auto", "android", "mlkit_basic", "mlkit_genai")
            )
        ) + commonSettings(),
        As100LiveStreamTranslateAutoMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "speech_engine",
                label = "Speech recognition",
                description = "Detect-language mode currently uses Android language detection/switching. Automatic and Android are equivalent here; ML Kit speech modes require a fixed locale.",
                group = "Audio",
                defaultValue = "auto",
                choices = listOf("auto", "android")
            ),
            MethodSetting.TextSetting(
                id = "allowed_languages",
                label = "Likely spoken languages",
                description = "Optional comma-separated ML Kit/BCP-47 language codes, for example en,fr,es. Restricting the set can improve Android language-switching accuracy. Leave blank to let the recognizer use its available models.",
                group = "Languages",
                defaultValue = ""
            ),
            MethodSetting.ChoiceSetting(
                id = "switch_sensitivity",
                label = "Language switch sensitivity",
                description = "High precision waits for stronger evidence; balanced is the default; quick response switches earlier and may be less stable.",
                group = "Languages",
                defaultValue = "balanced",
                choices = listOf("high_precision", "balanced", "quick_response")
            )
        ) + commonSettings()
    )
}
