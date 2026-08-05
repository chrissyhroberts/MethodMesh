package com.example.researchos.modules.speechtranscription

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object SpeechTranscriptionModule : ResearchOSModule {
    override val moduleId = "speechtranscription"
    override val displayName = "Speech transcription"
    override val summary = "Capture a voice note through Android speech recognition and return text."

    override fun as100Methods() = listOf(As100SpeechTranscriptionMethod)

    override fun rilBindings() = listOf(
        RilBinding("transcribe speech", As100SpeechTranscriptionMethod.ID, "Open Android speech recognition and return a transcript"),
        RilBinding("speech to text", As100SpeechTranscriptionMethod.ID, "Capture speech and convert it to text")
    )

    override fun capabilityScreens() = listOf(SpeechTranscriptionCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100SpeechTranscriptionMethod.ID to listOf(
            MethodSetting.TextSetting("speech_language", "Language", defaultValue = "en-GB"),
            MethodSetting.TextSetting("speech_prompt", "Prompt", defaultValue = "Speak now"),
            MethodSetting.BooleanSetting("speech_prefer_offline", "Prefer offline recognizer", defaultValue = true)
        )
    )
}
