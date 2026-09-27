package com.example.methodmesh.modules.livestreamtranslate

import com.example.methodmesh.modules.MaturityStatus
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.platform.translation.commonMlKitLanguageCodes
import com.example.methodmesh.settings.MethodSetting

object LiveStreamTranslateModule : MethodMeshModule {
    override val moduleId = "livestreamtranslate"
    override val displayName = "Live stream translation"
    override val version = "0.3.3"
    override val maturity = MaturityStatus.Development
    override val summary = "Meeting translation with fixed-language, automatic-language and experimental continuous-stream translation modes."
    override val iconKey = "language"

    override fun as100Methods() = listOf(
        As100LiveStreamTranslateFixedMethod,
        As100LiveStreamTranslateAutoMethod,
        As100LiveStreamTranslateStreamingMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("translate meeting", As100LiveStreamTranslateFixedMethod.ID, "Listen continuously and translate a meeting from a selected source language"),
        RilBinding("live translation", As100LiveStreamTranslateFixedMethod.ID, "Run robust fixed-language live translation"),
        RilBinding("detect and translate meeting", As100LiveStreamTranslateAutoMethod.ID, "Detect the current spoken language and translate a meeting live"),
        RilBinding("multilingual meeting", As100LiveStreamTranslateAutoMethod.ID, "Run automatic-language live meeting translation"),
        RilBinding("streaming translation", As100LiveStreamTranslateStreamingMethod.ID, "Translate revisable speech-recognition hypotheses while speech is still in progress"),
        RilBinding("translate speech stream", As100LiveStreamTranslateStreamingMethod.ID, "Run experimental low-latency streaming meeting translation")
    )

    override fun capabilityScreens() = listOf(
        LiveStreamTranslateFixedCapabilityScreen,
        LiveStreamTranslateAutoCapabilityScreen,
        LiveStreamTranslateStreamingCapabilityScreen
    )

    private fun sessionSettings() = listOf(
        MethodSetting.ChoiceSetting(
            id = "target_language",
            label = "Target language",
            description = "Language shown in the translated live feed.",
            group = "Languages",
            defaultValue = "en",
            choices = commonMlKitLanguageCodes
        ),
        MethodSetting.BooleanSetting(
            id = "transcript_on_start",
            label = "Record transcript at start",
            description = "The live feed always works; this controls whether final recognition segments are included in the committed transcript. It can be changed during the session.",
            group = "Transcript",
            defaultValue = true
        )
    )

    private fun preferOfflineAndroidSetting() = MethodSetting.BooleanSetting(
        id = "prefer_offline",
        label = "Prefer offline Android recognition",
        description = "When Android recognition is active, prefer an installed on-device speech model. ML Kit Basic/GenAI recognition is on-device after model preparation.",
        group = "Audio",
        defaultValue = false
    )

    private fun sourceLanguageSetting(
        choices: List<String> = commonMlKitLanguageCodes,
        description: String = "Speech-recognition language. Fixed-language recognition is the robust option when the meeting language is known."
    ) = MethodSetting.ChoiceSetting(
        id = "source_language",
        label = "Source language",
        description = description,
        group = "Languages",
        defaultValue = "fr",
        choices = choices
    )

    override fun capabilitySettings() = mapOf(
        As100LiveStreamTranslateFixedMethod.ID to listOf(
            sourceLanguageSetting(),
            MethodSetting.ChoiceSetting(
                id = "speech_engine",
                label = "Speech recognition",
                description = "Automatic prefers ML Kit GenAI, then ML Kit Basic, then Android, with safe fallback. The active provider can be hot-switched during a meeting at final-segment boundaries.",
                group = "Audio",
                defaultValue = "auto",
                choices = listOf("auto", "android", "mlkit_basic", "mlkit_genai")
            ),
            preferOfflineAndroidSetting()
        ) + sessionSettings(),

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
            ),
            preferOfflineAndroidSetting()
        ) + sessionSettings(),

        As100LiveStreamTranslateStreamingMethod.ID to listOf(
            sourceLanguageSetting(
                choices = LiveStreamLanguageSupport.streamingSpeechLanguageCodes,
                description = "Speech language for ML Kit's continuous partial-result stream. The list is conservatively limited to locales supported by ML Kit Basic so Automatic can fall back safely when GenAI is unavailable."
            ),
            MethodSetting.ChoiceSetting(
                id = "speech_engine",
                label = "Streaming recognizer",
                description = "Automatic tries ML Kit GenAI first and falls back to ML Kit Basic. Android SpeechRecognizer is intentionally excluded because this capability depends on ML Kit's continuous partial-result stream.",
                group = "Audio",
                defaultValue = "auto",
                choices = listOf("auto", "mlkit_basic", "mlkit_genai")
            ),
            MethodSetting.ChoiceSetting(
                id = "stream_response",
                label = "Streaming response",
                description = "Controls how long MethodMesh waits for a newer speech hypothesis before translating the current one. Fast is most responsive; Stable reduces translation churn.",
                group = "Streaming",
                defaultValue = "balanced",
                choices = listOf("fast", "balanced", "stable")
            )
        ) + sessionSettings()
    )
}
