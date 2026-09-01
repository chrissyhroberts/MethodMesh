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

    fun supportedCodes(): Set<String> =
        runCatching { TranslateLanguage.getAllLanguages().toSet() }.getOrElse { commonMlKitLanguageCodes.toSet() }

    fun supportedLanguages(): List<MlKitLanguageInfo> =
        supportedCodes()
            .map { code -> info(code) }
            .sortedBy { it.name.lowercase() }

    fun allKnownLanguages(): List<MlKitLanguageInfo> =
        names.keys
            .map(::info)
            .sortedBy { it.name.lowercase() }

    fun info(code: String): MlKitLanguageInfo =
        MlKitLanguageInfo(code = code, name = names[code] ?: code.uppercase())

    fun label(code: String): String = info(code).label
}
