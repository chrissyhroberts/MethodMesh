package com.example.methodmesh.modules.livestreamtranslate

import com.example.methodmesh.modules.mlkittranslate.MlKitLanguageCatalog

/**
 * Translation-code -> concrete speech-locale bridge, derived from the audited
 * Conversation Translator routing table but kept module-local so this feature
 * does not depend on another feature module implementation.
 */
internal object LiveStreamLanguageSupport {
    private val defaultSpeechLocales = mapOf(
        "af" to "af-ZA",
        "ar" to "ar-SA",
        "be" to "be-BY",
        "bg" to "bg-BG",
        "bn" to "bn-BD",
        "ca" to "ca-ES",
        "cs" to "cs-CZ",
        "cy" to "cy-GB",
        "da" to "da-DK",
        "de" to "de-DE",
        "el" to "el-GR",
        "en" to "en-GB",
        "eo" to "eo",
        "es" to "es-ES",
        "et" to "et-EE",
        "fa" to "fa-IR",
        "fi" to "fi-FI",
        "fr" to "fr-FR",
        "ga" to "ga-IE",
        "gl" to "gl-ES",
        "gu" to "gu-IN",
        "he" to "he-IL",
        "hi" to "hi-IN",
        "hr" to "hr-HR",
        "ht" to "ht-HT",
        "hu" to "hu-HU",
        "id" to "id-ID",
        "is" to "is-IS",
        "it" to "it-IT",
        "ja" to "ja-JP",
        "ka" to "ka-GE",
        "kn" to "kn-IN",
        "ko" to "ko-KR",
        "lt" to "lt-LT",
        "lv" to "lv-LV",
        "mk" to "mk-MK",
        "mr" to "mr-IN",
        "ms" to "ms-MY",
        "mt" to "mt-MT",
        "nl" to "nl-NL",
        "no" to "nb-NO",
        "pl" to "pl-PL",
        "pt" to "pt-PT",
        "ro" to "ro-RO",
        "ru" to "ru-RU",
        "sk" to "sk-SK",
        "sl" to "sl-SI",
        "sq" to "sq-AL",
        "sv" to "sv-SE",
        "sw" to "sw-KE",
        "ta" to "ta-IN",
        "te" to "te-IN",
        "th" to "th-TH",
        "tl" to "fil-PH",
        "tr" to "tr-TR",
        "uk" to "uk-UA",
        "ur" to "ur-PK",
        "vi" to "vi-VN",
        "zh" to "zh-CN"
    )

    fun defaultSpeechLocaleTag(language: String): String {
        val canonical = MlKitLanguageCatalog.canonicalCode(language, language)
        return defaultSpeechLocales[canonical] ?: canonical
    }
}
