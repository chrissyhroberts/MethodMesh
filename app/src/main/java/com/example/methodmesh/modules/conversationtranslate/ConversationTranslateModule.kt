package com.example.methodmesh.modules.conversationtranslate

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.modules.mlkittranslate.commonMlKitLanguageCodes
import com.example.methodmesh.settings.MethodSetting

object ConversationTranslateModule : MethodMeshModule {
    override val moduleId = "conversationtranslate"
    override val displayName = "Conversation translator"
    override val summary = "Run two-person or four-person live translated conversations with regional speech routing and optional transcripts."

    override fun as100Methods() = listOf(
        As100ConversationTranslateMethod,
        As100ConversationTranslateTableMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("translate conversation", As100ConversationTranslateMethod.ID, "Run a two-person speech translation session"),
        RilBinding("conversation translator", As100ConversationTranslateMethod.ID, "Capture a translated two-person conversation"),
        RilBinding("translate table conversation", As100ConversationTranslateTableMethod.ID, "Run a four-seat table translation session"),
        RilBinding("four person translator", As100ConversationTranslateTableMethod.ID, "Translate a four-person conversation around one device")
    )

    override fun capabilityScreens() = listOf(
        ConversationTranslateCapabilityScreen,
        ConversationTranslateTableCapabilityScreen
    )

    private fun commonAudioSettings() = listOf(
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
            description = "Ask Android speech recognition to prefer an installed offline model. Leave off for the broadest language compatibility; translation models remain on-device.",
            group = "Audio",
            defaultValue = false
        ),
        MethodSetting.BooleanSetting(
            id = "transcript_on_start",
            label = "Transcript on at start",
            description = "Transcript capture can still be paused and resumed during the conversation without stopping translation.",
            group = "Transcript",
            defaultValue = true
        )
    )

    private fun arabicVariantSetting(id: String, label: String) = MethodSetting.ChoiceSetting(
        id = id,
        label = label,
        description = "Used only when this participant speaks Arabic. Controls speech recognition/TTS routing; ML Kit translation remains Arabic (ar).",
        group = "Languages",
        defaultValue = ARABIC_VARIANT_GULF,
        choices = listOf(ARABIC_VARIANT_LEVANTINE, ARABIC_VARIANT_GULF, ARABIC_VARIANT_NORTH_AFRICAN)
    )

    private fun voiceSetting(id: String, label: String) = MethodSetting.ChoiceSetting(
        id = id,
        label = label,
        description = "Preferred TTS voice presentation. Android voice engines do not expose standard gender metadata; MethodMesh honours male/female tagged voices when the installed engine provides them and otherwise keeps the locale default.",
        group = "Audio",
        defaultValue = CONVERSATION_VOICE_FEMALE,
        choices = listOf(CONVERSATION_VOICE_FEMALE, CONVERSATION_VOICE_MALE)
    )

    override fun capabilitySettings() = mapOf(
        As100ConversationTranslateMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "language_a",
                label = "Language A",
                description = "First conversation language. Participants can change it by flag during the live conversation.",
                group = "Languages",
                defaultValue = "en",
                choices = commonMlKitLanguageCodes
            ),
            MethodSetting.ChoiceSetting(
                id = "language_b",
                label = "Language B",
                description = "Second conversation language. Participants can change it by flag during the live conversation.",
                group = "Languages",
                defaultValue = "es",
                choices = commonMlKitLanguageCodes
            ),
            arabicVariantSetting("arabic_variant_a", "Arabic variant A"),
            arabicVariantSetting("arabic_variant_b", "Arabic variant B"),
            voiceSetting("voice_a", "Speaker A voice preference"),
            voiceSetting("voice_b", "Speaker B voice preference"),
            MethodSetting.TextSetting(
                id = "label_a",
                label = "Custom button label A",
                description = "Optional override. Leave blank to use the first language's localised Press to speak label.",
                group = "Display",
                defaultValue = ""
            ),
            MethodSetting.TextSetting(
                id = "label_b",
                label = "Custom button label B",
                description = "Optional override. Leave blank to use the second language's localised Press to speak label.",
                group = "Display",
                defaultValue = ""
            )
        ) + commonAudioSettings(),
        As100ConversationTranslateTableMethod.ID to listOf(
            MethodSetting.ChoiceSetting("language_a", "Seat A language", "Bottom seat initial language.", "Languages", "en", commonMlKitLanguageCodes),
            MethodSetting.ChoiceSetting("language_b", "Seat B language", "Right seat initial language.", "Languages", "fr", commonMlKitLanguageCodes),
            MethodSetting.ChoiceSetting("language_c", "Seat C language", "Top seat initial language.", "Languages", "de", commonMlKitLanguageCodes),
            MethodSetting.ChoiceSetting("language_d", "Seat D language", "Left seat initial language.", "Languages", "ko", commonMlKitLanguageCodes),
            arabicVariantSetting("arabic_variant_a", "Seat A Arabic variant"),
            arabicVariantSetting("arabic_variant_b", "Seat B Arabic variant"),
            arabicVariantSetting("arabic_variant_c", "Seat C Arabic variant"),
            arabicVariantSetting("arabic_variant_d", "Seat D Arabic variant"),
            voiceSetting("voice_a", "Seat A voice preference"),
            voiceSetting("voice_b", "Seat B voice preference"),
            voiceSetting("voice_c", "Seat C voice preference"),
            voiceSetting("voice_d", "Seat D voice preference")
        ) + commonAudioSettings()
    )
}
