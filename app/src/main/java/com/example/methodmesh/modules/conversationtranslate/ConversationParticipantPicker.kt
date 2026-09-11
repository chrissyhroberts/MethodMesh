package com.example.methodmesh.modules.conversationtranslate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.modules.mlkittranslate.MlKitLanguageCatalog
import java.util.Locale

internal const val ARABIC_VARIANT_LEVANTINE = "levantine"
internal const val ARABIC_VARIANT_GULF = "gulf"
internal const val ARABIC_VARIANT_NORTH_AFRICAN = "north_african"

internal data class ParticipantLanguageChoice(
    val language: String,
    val speechLocaleOverride: String = "",
    val arabicVariant: String = "",
    val flag: String = ""
)

private data class CountryLanguage(
    val language: String,
    val speechLocale: String = "",
    val arabicVariant: String = ""
)

private data class CountryOption(
    val flag: String,
    val country: String,
    val languages: List<CountryLanguage>
)

/**
 * Visual country -> language navigation for the participant-facing picker.
 * Country is deliberately not treated as synonymous with language: countries
 * with several useful ML Kit languages expose a second, native-language choice.
 */
private val countryOptions = listOf(
    CountryOption("🇿🇦", "South Africa", listOf(CountryLanguage("af", "af-ZA"), CountryLanguage("en", "en-ZA"))),
    CountryOption("🇦🇱", "Albania", listOf(CountryLanguage("sq", "sq-AL"))),
    CountryOption("🇱🇧", "Lebanon", listOf(CountryLanguage("ar", "ar-LB", ARABIC_VARIANT_LEVANTINE))),
    CountryOption("🇯🇴", "Jordan", listOf(CountryLanguage("ar", "ar-JO", ARABIC_VARIANT_LEVANTINE))),
    CountryOption("🇸🇾", "Syria", listOf(CountryLanguage("ar", "ar-SY", ARABIC_VARIANT_LEVANTINE))),
    CountryOption("🇵🇸", "Palestine", listOf(CountryLanguage("ar", "ar-PS", ARABIC_VARIANT_LEVANTINE))),
    CountryOption("🇸🇦", "Saudi Arabia", listOf(CountryLanguage("ar", "ar-SA", ARABIC_VARIANT_GULF))),
    CountryOption("🇦🇪", "United Arab Emirates", listOf(CountryLanguage("ar", "ar-AE", ARABIC_VARIANT_GULF), CountryLanguage("en", "en-GB"))),
    CountryOption("🇰🇼", "Kuwait", listOf(CountryLanguage("ar", "ar-KW", ARABIC_VARIANT_GULF))),
    CountryOption("🇶🇦", "Qatar", listOf(CountryLanguage("ar", "ar-QA", ARABIC_VARIANT_GULF))),
    CountryOption("🇧🇭", "Bahrain", listOf(CountryLanguage("ar", "ar-BH", ARABIC_VARIANT_GULF))),
    CountryOption("🇴🇲", "Oman", listOf(CountryLanguage("ar", "ar-OM", ARABIC_VARIANT_GULF))),
    CountryOption("🇲🇦", "Morocco", listOf(CountryLanguage("ar", "ar-MA", ARABIC_VARIANT_NORTH_AFRICAN), CountryLanguage("fr", "fr-FR"))),
    CountryOption("🇩🇿", "Algeria", listOf(CountryLanguage("ar", "ar-DZ", ARABIC_VARIANT_NORTH_AFRICAN), CountryLanguage("fr", "fr-FR"))),
    CountryOption("🇹🇳", "Tunisia", listOf(CountryLanguage("ar", "ar-TN", ARABIC_VARIANT_NORTH_AFRICAN), CountryLanguage("fr", "fr-FR"))),
    CountryOption("🇱🇾", "Libya", listOf(CountryLanguage("ar", "ar-LY", ARABIC_VARIANT_NORTH_AFRICAN))),
    CountryOption("🇪🇬", "Egypt", listOf(CountryLanguage("ar", "ar-EG", ARABIC_VARIANT_NORTH_AFRICAN))),
    CountryOption("🇧🇾", "Belarus", listOf(CountryLanguage("be", "be-BY"), CountryLanguage("ru", "ru-RU"))),
    CountryOption("🇧🇩", "Bangladesh", listOf(CountryLanguage("bn", "bn-BD"))),
    CountryOption("🇧🇬", "Bulgaria", listOf(CountryLanguage("bg", "bg-BG"))),
    CountryOption("🇪🇸", "Spain", listOf(CountryLanguage("es", "es-ES"), CountryLanguage("ca", "ca-ES"), CountryLanguage("gl", "gl-ES"))),
    CountryOption("🇨🇳", "China", listOf(CountryLanguage("zh", "zh-CN"))),
    CountryOption("🇹🇼", "Taiwan", listOf(CountryLanguage("zh", "zh-TW"))),
    CountryOption("🇭🇷", "Croatia", listOf(CountryLanguage("hr", "hr-HR"))),
    CountryOption("🇨🇿", "Czechia", listOf(CountryLanguage("cs", "cs-CZ"))),
    CountryOption("🇩🇰", "Denmark", listOf(CountryLanguage("da", "da-DK"))),
    CountryOption("🇳🇱", "Netherlands", listOf(CountryLanguage("nl", "nl-NL"))),
    CountryOption("🇧🇪", "Belgium", listOf(CountryLanguage("nl", "nl-BE"), CountryLanguage("fr", "fr-BE"), CountryLanguage("de", "de-DE"))),
    CountryOption("🇬🇧", "United Kingdom", listOf(CountryLanguage("en", "en-GB"), CountryLanguage("cy", "cy-GB"))),
    CountryOption("🇺🇸", "United States", listOf(CountryLanguage("en", "en-US"), CountryLanguage("es", "es-US"))),
    CountryOption("🇨🇦", "Canada", listOf(CountryLanguage("en", "en-CA"), CountryLanguage("fr", "fr-CA"))),
    CountryOption("🇦🇺", "Australia", listOf(CountryLanguage("en", "en-AU"))),
    CountryOption("🇮🇪", "Ireland", listOf(CountryLanguage("en", "en-IE"), CountryLanguage("ga", "ga-IE"))),
    CountryOption("🇳🇿", "New Zealand", listOf(CountryLanguage("en", "en-NZ"))),
    CountryOption("🌐", "Esperanto", listOf(CountryLanguage("eo", "eo"))),
    CountryOption("🇪🇪", "Estonia", listOf(CountryLanguage("et", "et-EE"))),
    CountryOption("🇫🇮", "Finland", listOf(CountryLanguage("fi", "fi-FI"))),
    CountryOption("🇫🇷", "France", listOf(CountryLanguage("fr", "fr-FR"))),
    CountryOption("🇬🇪", "Georgia", listOf(CountryLanguage("ka", "ka-GE"))),
    CountryOption("🇩🇪", "Germany", listOf(CountryLanguage("de", "de-DE"))),
    CountryOption("🇦🇹", "Austria", listOf(CountryLanguage("de", "de-AT"))),
    CountryOption("🇨🇭", "Switzerland", listOf(CountryLanguage("de", "de-CH"), CountryLanguage("fr", "fr-CH"), CountryLanguage("it", "it-CH"))),
    CountryOption("🇬🇷", "Greece", listOf(CountryLanguage("el", "el-GR"))),
    CountryOption("🇭🇹", "Haiti", listOf(CountryLanguage("ht", "ht-HT"), CountryLanguage("fr", "fr-FR"))),
    CountryOption("🇮🇱", "Israel", listOf(CountryLanguage("he", "he-IL"), CountryLanguage("ar", "ar-IL", ARABIC_VARIANT_LEVANTINE))),
    CountryOption("🇭🇺", "Hungary", listOf(CountryLanguage("hu", "hu-HU"))),
    CountryOption("🇮🇸", "Iceland", listOf(CountryLanguage("is", "is-IS"))),
    CountryOption("🇮🇩", "Indonesia", listOf(CountryLanguage("id", "id-ID"))),
    CountryOption("🇮🇹", "Italy", listOf(CountryLanguage("it", "it-IT"))),
    CountryOption("🇯🇵", "Japan", listOf(CountryLanguage("ja", "ja-JP"))),
    CountryOption("🇰🇷", "South Korea", listOf(CountryLanguage("ko", "ko-KR"))),
    CountryOption("🇱🇻", "Latvia", listOf(CountryLanguage("lv", "lv-LV"))),
    CountryOption("🇱🇹", "Lithuania", listOf(CountryLanguage("lt", "lt-LT"))),
    CountryOption("🇲🇰", "North Macedonia", listOf(CountryLanguage("mk", "mk-MK"))),
    CountryOption("🇲🇾", "Malaysia", listOf(CountryLanguage("ms", "ms-MY"))),
    CountryOption("🇲🇹", "Malta", listOf(CountryLanguage("mt", "mt-MT"))),
    CountryOption("🇳🇴", "Norway", listOf(CountryLanguage("no", "nb-NO"))),
    CountryOption("🇮🇷", "Iran", listOf(CountryLanguage("fa", "fa-IR"))),
    CountryOption("🇵🇱", "Poland", listOf(CountryLanguage("pl", "pl-PL"))),
    CountryOption("🇵🇹", "Portugal", listOf(CountryLanguage("pt", "pt-PT"))),
    CountryOption("🇧🇷", "Brazil", listOf(CountryLanguage("pt", "pt-BR"))),
    CountryOption("🇷🇴", "Romania", listOf(CountryLanguage("ro", "ro-RO"))),
    CountryOption("🇷🇺", "Russia", listOf(CountryLanguage("ru", "ru-RU"))),
    CountryOption("🇸🇰", "Slovakia", listOf(CountryLanguage("sk", "sk-SK"))),
    CountryOption("🇸🇮", "Slovenia", listOf(CountryLanguage("sl", "sl-SI"))),
    CountryOption("🇲🇽", "Mexico", listOf(CountryLanguage("es", "es-MX"))),
    CountryOption("🇦🇷", "Argentina", listOf(CountryLanguage("es", "es-AR"))),
    CountryOption("🇨🇴", "Colombia", listOf(CountryLanguage("es", "es-CO"))),
    CountryOption("🇨🇱", "Chile", listOf(CountryLanguage("es", "es-CL"))),
    CountryOption("🇵🇪", "Peru", listOf(CountryLanguage("es", "es-PE"))),
    CountryOption("🇰🇪", "Kenya", listOf(CountryLanguage("sw", "sw-KE"), CountryLanguage("en", "en-GB"))),
    CountryOption("🇹🇿", "Tanzania", listOf(CountryLanguage("sw", "sw-TZ"), CountryLanguage("en", "en-GB"))),
    CountryOption("🇸🇪", "Sweden", listOf(CountryLanguage("sv", "sv-SE"))),
    CountryOption("🇵🇭", "Philippines", listOf(CountryLanguage("tl", "fil-PH"), CountryLanguage("en", "en-US"))),
    CountryOption("🇱🇰", "Sri Lanka", listOf(CountryLanguage("ta", "ta-LK"))),
    CountryOption("🇹🇭", "Thailand", listOf(CountryLanguage("th", "th-TH"))),
    CountryOption("🇹🇷", "Türkiye", listOf(CountryLanguage("tr", "tr-TR"))),
    CountryOption("🇺🇦", "Ukraine", listOf(CountryLanguage("uk", "uk-UA"), CountryLanguage("ru", "ru-RU"))),
    CountryOption("🇵🇰", "Pakistan", listOf(CountryLanguage("ur", "ur-PK"), CountryLanguage("en", "en-GB"))),
    CountryOption("🇻🇳", "Vietnam", listOf(CountryLanguage("vi", "vi-VN"))),
    CountryOption(
        "🇮🇳",
        "India",
        listOf(
            CountryLanguage("en", "en-IN"),
            CountryLanguage("hi", "hi-IN"),
            CountryLanguage("bn", "bn-IN"),
            CountryLanguage("gu", "gu-IN"),
            CountryLanguage("kn", "kn-IN"),
            CountryLanguage("mr", "mr-IN"),
            CountryLanguage("ta", "ta-IN"),
            CountryLanguage("te", "te-IN"),
            CountryLanguage("ur", "ur-IN")
        )
    ),
    CountryOption("🇸🇬", "Singapore", listOf(CountryLanguage("en", "en-GB"), CountryLanguage("ms", "ms-SG"), CountryLanguage("ta", "ta-SG"), CountryLanguage("zh", "zh-CN")))
)

internal fun defaultFlagForLanguage(language: String): String {
    val canonical = MlKitLanguageCatalog.canonicalCode(language, language)
    if (canonical == "ar") return "☾"
    return countryOptions.firstOrNull { option -> option.languages.any { it.language == canonical } }?.flag ?: "🌐"
}

private fun nativeCountryName(option: CountryOption): String {
    if (option.country == "India") return "भारत"
    val primaryTag = option.languages.firstOrNull()?.speechLocale.orEmpty()
    if (primaryTag.isBlank()) return option.country
    val locale = Locale.forLanguageTag(primaryTag)
    val countryCode = locale.country
    if (countryCode.isBlank()) return option.country
    return runCatching {
        Locale("", countryCode).getDisplayCountry(locale).trim()
    }.getOrDefault("").ifBlank { option.country }
}

internal fun nativeConversationLanguageLabel(language: String): String {
    val canonical = MlKitLanguageCatalog.canonicalCode(language, language)
    val tag = ConversationLanguageSupport.defaultSpeechLocaleTag(canonical)
    val locale = Locale.forLanguageTag(tag)
    val native = locale.getDisplayLanguage(locale).trim()
    return native.ifBlank { MlKitLanguageCatalog.info(canonical).name }
}

internal fun arabicVariantDisplayName(value: String): String = when (value) {
    ARABIC_VARIANT_LEVANTINE -> "Levantine · الشامية"
    ARABIC_VARIANT_NORTH_AFRICAN -> "North African · شمال أفريقيا"
    else -> "Gulf · الخليجية"
}

@Composable
internal fun ArabicVariantButton(
    language: String,
    variant: String,
    rotationDegrees: Float = 0f,
    onVariantSelected: (String) -> Unit
) {
    if (MlKitLanguageCatalog.canonicalCode(language, language) != "ar") return
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(arabicVariantDisplayName(variant), textAlign = TextAlign.Center)
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(18.dp).rotate(rotationDegrees),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("اختر اللهجة · Choose Arabic variant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    listOf(
                        ARABIC_VARIANT_LEVANTINE,
                        ARABIC_VARIANT_GULF,
                        ARABIC_VARIANT_NORTH_AFRICAN
                    ).forEach { option ->
                        Button(
                            onClick = { onVariantSelected(option); open = false },
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            Text(arabicVariantDisplayName(option), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ParticipantLanguageButton(
    selectedLanguage: String,
    selectedFlag: String,
    rotationDegrees: Float = 0f,
    modifier: Modifier = Modifier.fillMaxWidth(),
    prominent: Boolean = false,
    onSelected: (ParticipantLanguageChoice) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Keep the flag grid state outside the dialog so closing/reopening the
    // picker, drilling into a multilingual country, or changing mode does not
    // reset the participant to the A countries.
    val flagGridState = rememberLazyGridState()
    if (prominent) {
        Button(
            onClick = { open = true },
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                "${selectedFlag.ifBlank { defaultFlagForLanguage(selectedLanguage) }}  ${nativeConversationLanguageLabel(selectedLanguage)}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Text("▼")
        }
    } else {
        OutlinedButton(onClick = { open = true }, modifier = modifier) {
            Text(
                "${selectedFlag.ifBlank { defaultFlagForLanguage(selectedLanguage) }}  ${nativeConversationLanguageLabel(selectedLanguage)}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Text("▼")
        }
    }
    if (open) {
        ParticipantLanguageDialog(
            currentLanguage = selectedLanguage,
            rotationDegrees = rotationDegrees,
            flagGridState = flagGridState,
            onDismiss = { open = false },
            onSelected = { choice -> onSelected(choice); open = false }
        )
    }
}

@Composable
private fun ParticipantLanguageDialog(
    currentLanguage: String,
    rotationDegrees: Float,
    flagGridState: LazyGridState,
    onDismiss: () -> Unit,
    onSelected: (ParticipantLanguageChoice) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("flags") }
    var countryChoice by remember { mutableStateOf<CountryOption?>(null) }
    var arabicShortcutOpen by rememberSaveable { mutableStateOf(false) }
    val supported = remember { MlKitLanguageCatalog.supportedCodes() }
    val countries = remember(supported) {
        countryOptions.mapNotNull { option ->
            val languages = option.languages.filter { it.language in supported }
            if (languages.isEmpty()) null else option.copy(languages = languages)
        }.sortedBy { it.country.lowercase() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.94f).heightIn(max = 680.dp).rotate(rotationDegrees),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 10.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Choose language", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { mode = "flags"; countryChoice = null; arabicShortcutOpen = false }, modifier = Modifier.weight(1f)) { Text("🏳 Flags") }
                    OutlinedButton(onClick = { mode = "list"; countryChoice = null; arabicShortcutOpen = false }, modifier = Modifier.weight(1f)) { Text("A–Z") }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    arabicShortcutOpen -> ArabicShortcutChoice(
                        onBack = { arabicShortcutOpen = false },
                        onSelected = onSelected
                    )
                    countryChoice != null -> CountryLanguageChoice(
                        country = countryChoice!!,
                        onBack = { countryChoice = null },
                        onSelected = onSelected
                    )
                    mode == "flags" -> CountryFlagGrid(
                        countries = countries,
                        gridState = flagGridState,
                        modifier = Modifier.weight(1f),
                        onArabic = { arabicShortcutOpen = true },
                        onCountry = { country ->
                            if (country.languages.size == 1) {
                                val language = country.languages.first()
                                onSelected(
                                    ParticipantLanguageChoice(
                                        language = language.language,
                                        speechLocaleOverride = language.speechLocale,
                                        arabicVariant = language.arabicVariant,
                                        flag = country.flag
                                    )
                                )
                            } else {
                                countryChoice = country
                            }
                        }
                    )
                    else -> AlphabeticalLanguageList(currentLanguage, onSelected)
                }
            }
        }
    }
}

@Composable
private fun CountryFlagGrid(
    countries: List<CountryOption>,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    onArabic: () -> Unit,
    onCountry: (CountryOption) -> Unit
) {
    data class GridItem(val key: String, val country: CountryOption? = null)
    val gridItems = remember(countries) {
        listOf(GridItem(key = "language:ar")) +
            countries.map { country -> GridItem(key = "country:${country.country}", country = country) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = gridItems,
            key = { it.key }
        ) { item ->
            val country = item.country
            if (country == null) {
                FlagPickerTile(
                    flag = "☾",
                    englishName = "Arabic",
                    nativeName = "العربية",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onArabic
                )
            } else {
                FlagPickerTile(
                    flag = country.flag,
                    englishName = country.country,
                    nativeName = nativeCountryName(country),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCountry(country) }
                )
            }
        }
    }
}

@Composable
private fun FlagPickerTile(
    flag: String,
    englishName: String,
    nativeName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(108.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(flag, style = MaterialTheme.typography.headlineMedium)
            Text(
                englishName,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                fontWeight = FontWeight.SemiBold
            )
            if (!nativeName.equals(englishName, ignoreCase = true)) {
                Text(
                    nativeName,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ArabicShortcutChoice(
    onBack: () -> Unit,
    onSelected: (ParticipantLanguageChoice) -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("☾  العربية · Arabic", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("اختر اللهجة · Choose Arabic variant", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        listOf(
            Triple(ARABIC_VARIANT_LEVANTINE, "ar-LB", "Levantine · الشامية"),
            Triple(ARABIC_VARIANT_GULF, "ar-SA", "Gulf · الخليجية"),
            Triple(ARABIC_VARIANT_NORTH_AFRICAN, "ar-MA", "North African · شمال أفريقيا")
        ).forEach { (variant, speechLocale, label) ->
            Button(
                onClick = {
                    onSelected(
                        ParticipantLanguageChoice(
                            language = "ar",
                            speechLocaleOverride = speechLocale,
                            arabicVariant = variant,
                            flag = "☾"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(vertical = 3.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← Flags") }
    }
}

@Composable
private fun CountryLanguageChoice(
    country: CountryOption,
    onBack: () -> Unit,
    onSelected: (ParticipantLanguageChoice) -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("${country.flag}  ${country.country}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Choose the language you speak", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        country.languages.forEach { language ->
            Button(
                onClick = {
                    onSelected(
                        ParticipantLanguageChoice(
                            language = language.language,
                            speechLocaleOverride = language.speechLocale,
                            arabicVariant = language.arabicVariant,
                            flag = country.flag
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(vertical = 3.dp)
            ) {
                Text(nativeConversationLanguageLabel(language.language), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← Flags") }
    }
}

@Composable
private fun AlphabeticalLanguageList(
    currentLanguage: String,
    onSelected: (ParticipantLanguageChoice) -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        MlKitLanguageCatalog.supportedLanguages().forEach { info ->
            OutlinedButton(
                onClick = {
                    val variant = if (info.code == "ar") ARABIC_VARIANT_GULF else ""
                    onSelected(
                        ParticipantLanguageChoice(
                            language = info.code,
                            speechLocaleOverride = "",
                            arabicVariant = variant,
                            flag = defaultFlagForLanguage(info.code)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(
                    "${defaultFlagForLanguage(info.code)}  ${nativeConversationLanguageLabel(info.code)} · ${info.name}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    fontWeight = if (info.code == currentLanguage) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
