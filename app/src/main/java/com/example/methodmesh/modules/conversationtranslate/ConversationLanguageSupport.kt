package com.example.methodmesh.modules.conversationtranslate

import com.example.methodmesh.modules.mlkittranslate.MlKitLanguageCatalog
import java.util.Locale

/**
 * Speech/UI bridge for the languages currently exposed by ML Kit Translation.
 *
 * ML Kit translation models use language-only BCP-47 tags (for example `ar`).
 * Android speech recognition and TTS normally work better with a concrete locale
 * (for example `ar-SA`) and some services use legacy/alternate language tags.
 * Keep that translation-code -> speech-locale distinction explicit here.
 */
internal object ConversationLanguageSupport {
    data class Profile(
        val speechLocaleCandidates: List<String>,
        val pressToSpeak: String,
        val initialInstruction: String,
        val replay: String
    )

    private val profiles = mapOf(
        "af" to Profile(listOf("af-ZA"), "Druk om te praat", "Druk die knoppie om te praat. Jou stem sal vir die ander persoon vertaal word.", "Speel weer"),
        "ar" to Profile(listOf("ar-SA", "ar-EG", "ar-AE", "ar-JO", "ar-LB", "ar-MA", "ar-DZ", "ar-IQ", "ar-KW", "ar-QA", "ar-BH", "ar-OM", "ar-YE", "ar-TN"), "اضغط للتحدث", "اضغط على الزر للتحدث، وستتم ترجمة صوتك للشخص الآخر.", "إعادة التشغيل"),
        "be" to Profile(listOf("be-BY"), "Націсніце, каб гаварыць", "Націсніце кнопку, каб гаварыць. Ваша гаворка будзе перакладзена для іншага чалавека.", "Паўтарыць"),
        "bg" to Profile(listOf("bg-BG"), "Натиснете, за да говорите", "Натиснете бутона, за да говорите. Гласът ви ще бъде преведен за другия човек.", "Повтори"),
        "bn" to Profile(listOf("bn-BD", "bn-IN"), "কথা বলতে চাপুন", "কথা বলতে বোতামটি চাপুন। আপনার কথা অন্য ব্যক্তির জন্য অনুবাদ করা হবে।", "আবার শুনুন"),
        "ca" to Profile(listOf("ca-ES"), "Prem per parlar", "Prem el botó per parlar. La teva veu es traduirà per a l’altra persona.", "Torna a reproduir"),
        "cs" to Profile(listOf("cs-CZ"), "Stiskněte a mluvte", "Stiskněte tlačítko a mluvte. Váš hlas bude přeložen pro druhou osobu.", "Přehrát znovu"),
        "cy" to Profile(listOf("cy-GB"), "Pwyswch i siarad", "Pwyswch y botwm i siarad. Bydd eich llais yn cael ei gyfieithu ar gyfer y person arall.", "Ailchwarae"),
        "da" to Profile(listOf("da-DK"), "Tryk for at tale", "Tryk på knappen for at tale. Din stemme bliver oversat for den anden person.", "Afspil igen"),
        "de" to Profile(listOf("de-DE", "de-AT", "de-CH"), "Zum Sprechen drücken", "Drücken Sie die Taste, um zu sprechen. Ihre Stimme wird für die andere Person übersetzt.", "Wiederholen"),
        "el" to Profile(listOf("el-GR"), "Πατήστε για να μιλήσετε", "Πατήστε το κουμπί για να μιλήσετε. Η φωνή σας θα μεταφραστεί για το άλλο άτομο.", "Επανάληψη"),
        "en" to Profile(listOf("en-GB", "en-US", "en-AU", "en-CA", "en-IN", "en-IE", "en-NZ", "en-ZA"), "Press to speak", "Press the button to speak and your voice will be translated for the other person.", "Replay"),
        "eo" to Profile(listOf("eo"), "Premu por paroli", "Premu la butonon por paroli. Via voĉo estos tradukita por la alia persono.", "Reludi"),
        "es" to Profile(listOf("es-ES", "es-MX", "es-US", "es-AR", "es-CO", "es-CL", "es-PE"), "Pulsa para hablar", "Pulsa el botón para hablar y tu voz se traducirá para la otra persona.", "Repetir"),
        "et" to Profile(listOf("et-EE"), "Vajutage rääkimiseks", "Rääkimiseks vajutage nuppu. Teie hääl tõlgitakse teisele inimesele.", "Esita uuesti"),
        "fa" to Profile(listOf("fa-IR"), "برای صحبت فشار دهید", "برای صحبت دکمه را فشار دهید. صدای شما برای شخص دیگر ترجمه می‌شود.", "پخش دوباره"),
        "fi" to Profile(listOf("fi-FI"), "Paina puhuaksesi", "Paina painiketta ja puhu. Puheesi käännetään toiselle henkilölle.", "Toista uudelleen"),
        "fr" to Profile(listOf("fr-FR", "fr-CA", "fr-BE", "fr-CH"), "Appuyez pour parler", "Appuyez sur le bouton pour parler. Votre voix sera traduite pour l’autre personne.", "Réécouter"),
        "ga" to Profile(listOf("ga-IE"), "Brúigh chun labhairt", "Brúigh an cnaipe chun labhairt. Aistreofar do ghuth don duine eile.", "Seinn arís"),
        "gl" to Profile(listOf("gl-ES"), "Preme para falar", "Preme o botón para falar. A túa voz traducirase para a outra persoa.", "Reproducir de novo"),
        "gu" to Profile(listOf("gu-IN"), "બોલવા માટે દબાવો", "બોલવા માટે બટન દબાવો. તમારો અવાજ બીજી વ્યક્તિ માટે અનુવાદિત કરવામાં આવશે.", "ફરી સાંભળો"),
        "he" to Profile(listOf("he-IL", "iw-IL"), "לחצו כדי לדבר", "לחצו על הכפתור כדי לדבר. הקול שלכם יתורגם עבור האדם השני.", "השמעה חוזרת"),
        "hi" to Profile(listOf("hi-IN"), "बोलने के लिए दबाएँ", "बोलने के लिए बटन दबाएँ। आपकी आवाज़ दूसरे व्यक्ति के लिए अनुवादित की जाएगी।", "फिर से चलाएँ"),
        "hr" to Profile(listOf("hr-HR"), "Pritisnite za govor", "Pritisnite gumb i govorite. Vaš će se glas prevesti za drugu osobu.", "Ponovi"),
        "ht" to Profile(listOf("ht-HT"), "Peze pou pale", "Peze bouton an pou pale. Vwa ou ap tradui pou lòt moun nan.", "Jwe ankò"),
        "hu" to Profile(listOf("hu-HU"), "Nyomja meg a beszédhez", "Nyomja meg a gombot, és beszéljen. A hangja le lesz fordítva a másik személy számára.", "Lejátszás újra"),
        "id" to Profile(listOf("id-ID", "in-ID"), "Tekan untuk berbicara", "Tekan tombol untuk berbicara. Suara Anda akan diterjemahkan untuk orang lain.", "Putar ulang"),
        "is" to Profile(listOf("is-IS"), "Ýttu til að tala", "Ýttu á hnappinn til að tala. Röddin þín verður þýdd fyrir hinn aðilann.", "Spila aftur"),
        "it" to Profile(listOf("it-IT", "it-CH"), "Premi per parlare", "Premi il pulsante per parlare. La tua voce verrà tradotta per l’altra persona.", "Riascolta"),
        "ja" to Profile(listOf("ja-JP"), "押して話す", "ボタンを押して話してください。あなたの声は相手の言語に翻訳されます。", "もう一度聞く"),
        "ka" to Profile(listOf("ka-GE"), "დააჭირეთ სალაპარაკოდ", "სალაპარაკოდ დააჭირეთ ღილაკს. თქვენი ხმა ითარგმნება მეორე ადამიანისთვის.", "ხელახლა მოსმენა"),
        "kn" to Profile(listOf("kn-IN"), "ಮಾತನಾಡಲು ಒತ್ತಿ", "ಮಾತನಾಡಲು ಬಟನ್ ಒತ್ತಿ. ನಿಮ್ಮ ಧ್ವನಿಯನ್ನು ಇನ್ನೊಬ್ಬ ವ್ಯಕ್ತಿಗಾಗಿ ಅನುವಾದಿಸಲಾಗುತ್ತದೆ.", "ಮತ್ತೆ ಕೇಳಿ"),
        "ko" to Profile(listOf("ko-KR"), "눌러서 말하기", "버튼을 누르고 말하세요. 음성이 상대방을 위해 번역됩니다.", "다시 듣기"),
        "lt" to Profile(listOf("lt-LT"), "Paspauskite ir kalbėkite", "Paspauskite mygtuką ir kalbėkite. Jūsų balsas bus išverstas kitam asmeniui.", "Pakartoti"),
        "lv" to Profile(listOf("lv-LV"), "Nospiediet, lai runātu", "Nospiediet pogu un runājiet. Jūsu balss tiks iztulkota otrai personai.", "Atskaņot vēlreiz"),
        "mk" to Profile(listOf("mk-MK"), "Притиснете за да зборувате", "Притиснете го копчето и зборувајте. Вашиот глас ќе биде преведен за другото лице.", "Пушти повторно"),
        "mr" to Profile(listOf("mr-IN"), "बोलण्यासाठी दाबा", "बोलण्यासाठी बटण दाबा. तुमचा आवाज दुसऱ्या व्यक्तीसाठी भाषांतरित केला जाईल.", "पुन्हा ऐका"),
        "ms" to Profile(listOf("ms-MY", "ms-SG"), "Tekan untuk bercakap", "Tekan butang untuk bercakap. Suara anda akan diterjemahkan untuk orang lain.", "Main semula"),
        "mt" to Profile(listOf("mt-MT"), "Agħfas biex titkellem", "Agħfas il-buttuna biex titkellem. Il-vuċi tiegħek se tiġi tradotta għall-persuna l-oħra.", "Erġa’ semma’"),
        "nl" to Profile(listOf("nl-NL", "nl-BE"), "Druk om te spreken", "Druk op de knop om te spreken. Uw stem wordt voor de andere persoon vertaald.", "Opnieuw afspelen"),
        "no" to Profile(listOf("nb-NO", "no-NO", "nn-NO"), "Trykk for å snakke", "Trykk på knappen for å snakke. Stemmen din blir oversatt for den andre personen.", "Spill av igjen"),
        "pl" to Profile(listOf("pl-PL"), "Naciśnij, aby mówić", "Naciśnij przycisk i mów. Twój głos zostanie przetłumaczony dla drugiej osoby.", "Odtwórz ponownie"),
        "pt" to Profile(listOf("pt-PT", "pt-BR"), "Prima para falar", "Prima o botão para falar. A sua voz será traduzida para a outra pessoa.", "Reproduzir novamente"),
        "ro" to Profile(listOf("ro-RO"), "Apăsați pentru a vorbi", "Apăsați butonul și vorbiți. Vocea dvs. va fi tradusă pentru cealaltă persoană.", "Redă din nou"),
        "ru" to Profile(listOf("ru-RU"), "Нажмите, чтобы говорить", "Нажмите кнопку и говорите. Ваша речь будет переведена для другого человека.", "Повторить"),
        "sk" to Profile(listOf("sk-SK"), "Stlačte a hovorte", "Stlačte tlačidlo a hovorte. Váš hlas bude preložený pre druhú osobu.", "Prehrať znova"),
        "sl" to Profile(listOf("sl-SI"), "Pritisnite za govor", "Pritisnite gumb in govorite. Vaš glas bo preveden za drugo osebo.", "Predvajaj znova"),
        "sq" to Profile(listOf("sq-AL"), "Shtyp për të folur", "Shtyp butonin dhe fol. Zëri yt do të përkthehet për personin tjetër.", "Luaj përsëri"),
        "sv" to Profile(listOf("sv-SE"), "Tryck för att tala", "Tryck på knappen och tala. Din röst översätts för den andra personen.", "Spela igen"),
        "sw" to Profile(listOf("sw-KE", "sw-TZ"), "Bonyeza kuzungumza", "Bonyeza kitufe ili kuzungumza. Sauti yako itatafsiriwa kwa mtu mwingine.", "Cheza tena"),
        "ta" to Profile(listOf("ta-IN", "ta-SG", "ta-LK"), "பேச அழுத்தவும்", "பேச பொத்தானை அழுத்தவும். உங்கள் குரல் மற்ற நபருக்காக மொழிபெயர்க்கப்படும்.", "மீண்டும் கேட்கவும்"),
        "te" to Profile(listOf("te-IN"), "మాట్లాడటానికి నొక్కండి", "మాట్లాడటానికి బటన్ నొక్కండి. మీ స్వరం మరొక వ్యక్తి కోసం అనువదించబడుతుంది.", "మళ్లీ వినండి"),
        "th" to Profile(listOf("th-TH"), "กดเพื่อพูด", "กดปุ่มเพื่อพูด เสียงของคุณจะถูกแปลให้อีกฝ่าย", "เล่นอีกครั้ง"),
        "tl" to Profile(listOf("fil-PH", "tl-PH"), "Pindutin para magsalita", "Pindutin ang button para magsalita. Isasalin ang boses mo para sa kausap.", "I-play muli"),
        "tr" to Profile(listOf("tr-TR"), "Konuşmak için basın", "Konuşmak için düğmeye basın. Sesiniz diğer kişi için çevrilecektir.", "Tekrar oynat"),
        "uk" to Profile(listOf("uk-UA"), "Натисніть, щоб говорити", "Натисніть кнопку й говоріть. Ваш голос буде перекладено для іншої людини.", "Повторити"),
        "ur" to Profile(listOf("ur-PK", "ur-IN"), "بولنے کے لیے دبائیں", "بولنے کے لیے بٹن دبائیں۔ آپ کی آواز دوسرے شخص کے لیے ترجمہ کی جائے گی۔", "دوبارہ سنیں"),
        "vi" to Profile(listOf("vi-VN"), "Nhấn để nói", "Nhấn nút để nói. Giọng nói của bạn sẽ được dịch cho người kia.", "Phát lại"),
        "zh" to Profile(listOf("zh-CN", "cmn-Hans-CN", "zh-Hans-CN", "zh-TW", "cmn-Hant-TW", "zh-Hant-TW"), "按下说话", "按下按钮开始说话。您的语音会翻译给对方。", "重新播放")
    )

    fun pressToSpeak(language: String): String = profile(language).pressToSpeak

    fun initialInstruction(language: String): String = profile(language).initialInstruction

    fun replay(language: String): String = profile(language).replay

    fun speechLocaleTag(
        language: String,
        advertisedLocales: Set<String> = emptySet(),
        deviceLocale: Locale = Locale.getDefault(),
        localeOverride: String = "",
        arabicVariant: String = ""
    ): String {
        val canonical = MlKitLanguageCatalog.canonicalCode(language, language)
        val variantCandidates = if (canonical == "ar") arabicVariantCandidates(arabicVariant) else emptyList()
        val candidates = buildList {
            if (localeOverride.isNotBlank()) add(localeOverride)
            addAll(variantCandidates)
            if (canonicalSpeechLanguage(deviceLocale.toLanguageTag()) == canonical) add(deviceLocale.toLanguageTag())
            addAll(profile(canonical).speechLocaleCandidates)
            add(canonical)
        }.filter { it.isNotBlank() }.distinctBy { normalizeTag(it) }

        if (advertisedLocales.isNotEmpty()) {
            val byNormalizedTag = advertisedLocales.associateBy(::normalizeTag)
            candidates.firstNotNullOfOrNull { candidate -> byNormalizedTag[normalizeTag(candidate)] }?.let { return it }
            // Regional preference is best-effort. If the requested locale is absent,
            // keep the conversation working by using any recogniser locale for the
            // same translation language rather than failing the Speak button.
            advertisedLocales.firstOrNull { canonicalSpeechLanguage(it) == canonical }?.let { return it }
        }
        return candidates.first()
    }

    fun defaultSpeechLocaleTag(language: String): String =
        profile(MlKitLanguageCatalog.canonicalCode(language, language)).speechLocaleCandidates.firstOrNull()
            ?: MlKitLanguageCatalog.canonicalCode(language, language)

    fun arabicVariantCandidates(variant: String): List<String> = when (variant) {
        ARABIC_VARIANT_LEVANTINE -> listOf("ar-LB", "ar-JO", "ar-SY", "ar-PS", "ar-IL")
        ARABIC_VARIANT_NORTH_AFRICAN -> listOf("ar-MA", "ar-DZ", "ar-TN", "ar-LY", "ar-EG")
        else -> listOf("ar-AE", "ar-SA", "ar-KW", "ar-QA", "ar-BH", "ar-OM")
    }

    fun isAdvertisedByRecognizer(language: String, advertisedLocales: Set<String>): Boolean {
        if (advertisedLocales.isEmpty()) return true
        val canonical = MlKitLanguageCatalog.canonicalCode(language, language)
        return advertisedLocales.any { canonicalSpeechLanguage(it) == canonical }
    }

    /** Returns translation language codes which do not yet have an explicit speech/UI profile. */
    fun missingProfiles(supportedTranslationCodes: Set<String>): List<String> =
        supportedTranslationCodes
            .map { MlKitLanguageCatalog.canonicalCode(it, it) }
            .distinct()
            .filterNot(profiles::containsKey)
            .sorted()

    private fun profile(language: String): Profile {
        val canonical = MlKitLanguageCatalog.canonicalCode(language, language)
        return profiles[canonical] ?: Profile(
            speechLocaleCandidates = listOf(canonical),
            pressToSpeak = "Press to speak",
            initialInstruction = "Press the button to speak and your voice will be translated for the other person.",
            replay = "Replay"
        )
    }

    private fun canonicalSpeechLanguage(tag: String): String {
        val localeLanguage = Locale.forLanguageTag(tag.replace('_', '-')).language.lowercase(Locale.ROOT)
        return when (localeLanguage) {
            "iw" -> "he"
            "in" -> "id"
            "fil" -> "tl"
            "nb", "nn" -> "no"
            "cmn" -> "zh"
            else -> localeLanguage
        }
    }

    private fun normalizeTag(tag: String): String = tag.replace('_', '-').lowercase(Locale.ROOT)
}
