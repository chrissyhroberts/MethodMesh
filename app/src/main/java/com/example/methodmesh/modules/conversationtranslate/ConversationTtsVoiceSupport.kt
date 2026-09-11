package com.example.methodmesh.modules.conversationtranslate

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

internal const val CONVERSATION_VOICE_FEMALE = "female"
internal const val CONVERSATION_VOICE_MALE = "male"

internal fun normalizeConversationVoicePreference(value: String?): String =
    if (value.equals(CONVERSATION_VOICE_MALE, ignoreCase = true)) {
        CONVERSATION_VOICE_MALE
    } else {
        CONVERSATION_VOICE_FEMALE
    }

internal fun toggleConversationVoicePreference(value: String): String =
    if (normalizeConversationVoicePreference(value) == CONVERSATION_VOICE_FEMALE) {
        CONVERSATION_VOICE_MALE
    } else {
        CONVERSATION_VOICE_FEMALE
    }

internal fun conversationVoiceSymbol(value: String): String =
    if (normalizeConversationVoicePreference(value) == CONVERSATION_VOICE_FEMALE) "♀" else "♂"

/**
 * Select a TTS voice for one conversation speaker.
 *
 * Android does not standardise voice gender metadata. When the installed engine
 * exposes a male/female hint in the voice name or features we honour it. When it
 * does not, we still make the participant toggle audible by selecting a stable,
 * different installed voice profile where possible and applying a small pitch
 * offset as the final fallback.
 *
 * [speakerKey] keeps speakers distinct. If an engine exposes several suitable
 * voices in a locale, seats a/b/c/d are deliberately distributed across them so
 * four participants who choose the same profile do not all sound identical.
 */
internal fun TextToSpeech.applyConversationVoicePreference(
    locale: Locale,
    preference: String,
    speakerKey: String = ""
): Boolean {
    val requested = normalizeConversationVoicePreference(preference)
    val availableVoices: Set<Voice> = voices ?: emptySet()
    val sameLanguage = availableVoices
        .filter { voice -> voice.locale.language.equals(locale.language, ignoreCase = true) }
    val localeVoices = if (locale.country.isBlank()) {
        sameLanguage
    } else {
        sameLanguage
            .filter { voice -> voice.locale.country.equals(locale.country, ignoreCase = true) }
            .ifEmpty { sameLanguage }
    }
        .sortedWith(
            compareBy<Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.latency }
                .thenBy { it.name }
        )

    if (localeVoices.isEmpty()) {
        // setLanguage() has already selected the engine default. A modest pitch
        // difference still makes the participant toggle perceptible.
        setPitch(conversationFallbackPitch(requested))
        return false
    }

    val tagged = localeVoices.filter { voiceGenderHint(it) == requested }
    val unknown = localeVoices.filter { voiceGenderHint(it) == null }
    val explicitlyMatched = tagged.isNotEmpty()
    val pool = when {
        explicitlyMatched -> tagged
        unknown.isNotEmpty() -> unknown
        else -> localeVoices
    }

    val speakerOrdinal = conversationSpeakerOrdinal(speakerKey)
    // With untagged engines, keep the whole pool available to each profile so
    // four speakers can receive four distinct variants when four are installed.
    // The one-position profile offset makes ♀/♂ switch to a different voice for
    // the same speaker whenever the pool contains more than one voice.
    val profileOffset = if (!explicitlyMatched && requested == CONVERSATION_VOICE_MALE && pool.size > 1) 1 else 0
    val selected = pool[(speakerOrdinal + profileOffset) % pool.size]
    val success = setVoice(selected) == TextToSpeech.SUCCESS

    // If the engine positively identified the requested gender, leave pitch
    // untouched. Otherwise use a restrained fallback so toggling always changes
    // the rendered voice even on engines that publish no gender metadata.
    setPitch(
        if (voiceGenderHint(selected) == requested) 1.0f
        else conversationFallbackPitch(requested)
    )
    return success
}

private fun conversationSpeakerOrdinal(speakerKey: String): Int = when (speakerKey.lowercase(Locale.ROOT)) {
    "a" -> 0
    "b" -> 1
    "c" -> 2
    "d" -> 3
    else -> speakerKey.hashCode() and Int.MAX_VALUE
}

private fun conversationFallbackPitch(preference: String): Float =
    if (normalizeConversationVoicePreference(preference) == CONVERSATION_VOICE_FEMALE) 1.08f else 0.92f

private fun voiceGenderHint(voice: Voice): String? {
    val searchable = buildString {
        append(voice.name.lowercase(Locale.ROOT))
        voice.features?.forEach { feature -> append(' ').append(feature.lowercase(Locale.ROOT)) }
    }
    return when {
        listOf("female", "woman", "feminine", "girl").any(searchable::contains) -> CONVERSATION_VOICE_FEMALE
        listOf("male", " man", "masculine", "boy").any(searchable::contains) -> CONVERSATION_VOICE_MALE
        else -> null
    }
}
