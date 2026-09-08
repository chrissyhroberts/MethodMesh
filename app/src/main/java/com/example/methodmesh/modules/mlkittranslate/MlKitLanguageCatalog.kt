package com.example.methodmesh.modules.mlkittranslate

import com.google.mlkit.nl.translate.TranslateLanguage

data class MlKitLanguageInfo(
    val code: String,
    val name: String
) {
    val label: String get() = "$name (${code.replaceFirstChar { it.uppercase() }})"
}

object MlKitLanguageCatalog {
    private val names = linkedMapOf(
        "af" to "Afrikaans",
        "am" to "Amharic",
        "ar" to "Arabic",
        "az" to "Azerbaijani",
        "be" to "Belarusian",
        "bg" to "Bulgarian",
        "bn" to "Bengali",
        "bs" to "Bosnian",
        "ca" to "Catalan",
        "ceb" to "Cebuano",
        "co" to "Corsican",
        "cs" to "Czech",
        "cy" to "Welsh",
        "da" to "Danish",
        "de" to "German",
        "el" to "Greek",
        "en" to "English",
        "eo" to "Esperanto",
        "es" to "Spanish",
        "et" to "Estonian",
        "eu" to "Basque",
        "fa" to "Persian",
        "fi" to "Finnish",
        "fr" to "French",
        "fy" to "Frisian",
        "ga" to "Irish",
        "gd" to "Scots Gaelic",
        "gl" to "Galician",
        "gu" to "Gujarati",
        "ha" to "Hausa",
        "haw" to "Hawaiian",
        "he" to "Hebrew",
        "hi" to "Hindi",
        "hmn" to "Hmong",
        "hr" to "Croatian",
        "ht" to "Haitian Creole",
        "hu" to "Hungarian",
        "hy" to "Armenian",
        "id" to "Indonesian",
        "ig" to "Igbo",
        "is" to "Icelandic",
        "it" to "Italian",
        "ja" to "Japanese",
        "jv" to "Javanese",
        "ka" to "Georgian",
        "kk" to "Kazakh",
        "km" to "Khmer",
        "kn" to "Kannada",
        "ko" to "Korean",
        "kri" to "Krio",
        "ku" to "Kurdish",
        "ky" to "Kyrgyz",
        "la" to "Latin",
        "lb" to "Luxembourgish",
        "lo" to "Lao",
        "lt" to "Lithuanian",
        "lv" to "Latvian",
        "mg" to "Malagasy",
        "mi" to "Māori",
        "mk" to "Macedonian",
        "ml" to "Malayalam",
        "mn" to "Mongolian",
        "mr" to "Marathi",
        "ms" to "Malay",
        "mt" to "Maltese",
        "my" to "Myanmar",
        "ne" to "Nepali",
        "nl" to "Dutch",
        "no" to "Norwegian",
        "ny" to "Chichewa",
        "pa" to "Punjabi",
        "pl" to "Polish",
        "ps" to "Pashto",
        "pt" to "Portuguese",
        "ro" to "Romanian",
        "ru" to "Russian",
        "sd" to "Sindhi",
        "si" to "Sinhala",
        "sk" to "Slovak",
        "sl" to "Slovenian",
        "sm" to "Samoan",
        "sn" to "Shona",
        "so" to "Somali",
        "sq" to "Albanian",
        "sr" to "Serbian",
        "st" to "Sesotho",
        "su" to "Sundanese",
        "sv" to "Swedish",
        "sw" to "Swahili",
        "ta" to "Tamil",
        "te" to "Telugu",
        "tg" to "Tajik",
        "th" to "Thai",
        "tl" to "Tagalog",
        "tr" to "Turkish",
        "uk" to "Ukrainian",
        "ur" to "Urdu",
        "uz" to "Uzbek",
        "vi" to "Vietnamese",
        "xh" to "Xhosa",
        "yi" to "Yiddish",
        "yo" to "Yoruba",
        "zh" to "Chinese",
        "zu" to "Zulu"
    )

    private val aliases = mapOf(
        "chinese" to "zh",
        "mandarin" to "zh",
        "simplified chinese" to "zh",
        "traditional chinese" to "zh",
        "zh-cn" to "zh",
        "zh-hans" to "zh",
        "zh-hans-cn" to "zh",
        "cmn" to "zh",
        "cmn-cn" to "zh",
        "cmn-hans" to "zh",
        "cmn-hans-cn" to "zh",
        "cn" to "zh",
        "japanese" to "ja",
        "jp" to "ja",
        "ja-jp" to "ja",
        "korean" to "ko",
        "kr" to "ko",
        "ko-kr" to "ko",
        "english" to "en",
        "spanish" to "es",
        "castilian" to "es",
        "french" to "fr",
        "portuguese" to "pt",
        "brazilian portuguese" to "pt",
        "german" to "de",
        "swahili" to "sw"
    )

    private val fallbackCanonicalCodes = listOf(
        "af", "sq", "ar", "be", "bg", "bn", "ca", "zh", "hr", "cs", "da", "nl", "en", "eo",
        "et", "fi", "fr", "gl", "ka", "de", "el", "gu", "ht", "he", "hi", "hu", "is", "id",
        "ga", "it", "ja", "kn", "ko", "lt", "lv", "mk", "mr", "ms", "mt", "no", "fa", "pl",
        "pt", "ro", "ru", "sk", "sl", "es", "sv", "sw", "tl", "ta", "te", "th", "tr", "uk",
        "ur", "vi", "cy"
    )

    fun canonicalCode(input: String?, fallback: String = ""): String {
        val raw = input.orEmpty().trim()
        if (raw.isBlank()) return fallback
        val compact = raw
            .substringBefore("(")
            .trim()
            .lowercase()
            .replace('_', '-')
        aliases[compact]?.let { return it }
        names.entries.firstOrNull { it.value.lowercase() == compact }?.let { return it.key }
        val fromTag = runCatching { TranslateLanguage.fromLanguageTag(compact) }.getOrNull()
        if (!fromTag.isNullOrBlank()) return fromTag
        if (compact in fallbackCanonicalCodes) return compact
        return fallback.ifBlank { compact }
    }

    fun canonicalCodes(): List<String> =
        supportedCodes()
            .map { canonicalCode(it, it) }
            .distinct()
            .sortedBy { info(it).name.lowercase() }

    fun supportedCodes(): Set<String> =
        runCatching { TranslateLanguage.getAllLanguages().map { canonicalCode(it, it) }.toSet() }
            .getOrElse { fallbackCanonicalCodes.toSet() }

    fun supportedLanguages(): List<MlKitLanguageInfo> =
        canonicalCodes().map { code -> info(code) }

    fun allKnownLanguages(): List<MlKitLanguageInfo> =
        supportedLanguages()

    fun info(code: String): MlKitLanguageInfo =
        MlKitLanguageInfo(code = code, name = names[code] ?: code.uppercase())

    fun label(code: String): String = info(code).label
}
