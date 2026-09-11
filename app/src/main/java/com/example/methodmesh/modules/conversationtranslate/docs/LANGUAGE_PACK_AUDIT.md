# Conversation translator language-pack audit

Audit date: 2026-09-11.

Source of translation-pack truth: Google ML Kit Translation supported-language list and the runtime `TranslateLanguage.getAllLanguages()` catalogue. Android speech recognition and TTS are separate services, so their actual installed/online support remains device-specific.

Official ML Kit reference: https://developers.google.com/ml-kit/language/translation/translation-language-support

| Language | ML Kit code | Primary Android speech/TTS locale | Additional accepted speech locale candidates | Localised participant UI |
|---|---|---|---|---|
| Afrikaans | `af` | `af-ZA` | — | Yes |
| Albanian | `sq` | `sq-AL` | — | Yes |
| Arabic | `ar` | `ar-SA` | ar-EG, ar-AE, ar-JO, ar-LB, ar-MA, ar-DZ, ar-IQ, ar-KW, ar-QA, ar-BH, ar-OM, ar-YE, ar-TN | Yes |
| Belarusian | `be` | `be-BY` | — | Yes |
| Bengali | `bn` | `bn-BD` | bn-IN | Yes |
| Bulgarian | `bg` | `bg-BG` | — | Yes |
| Catalan | `ca` | `ca-ES` | — | Yes |
| Chinese | `zh` | `zh-CN` | cmn-Hans-CN, zh-Hans-CN, zh-TW, cmn-Hant-TW, zh-Hant-TW | Yes |
| Croatian | `hr` | `hr-HR` | — | Yes |
| Czech | `cs` | `cs-CZ` | — | Yes |
| Danish | `da` | `da-DK` | — | Yes |
| Dutch | `nl` | `nl-NL` | nl-BE | Yes |
| English | `en` | `en-GB` | en-US, en-AU, en-CA, en-IN, en-IE, en-NZ, en-ZA | Yes |
| Esperanto | `eo` | `eo` | — | Yes |
| Estonian | `et` | `et-EE` | — | Yes |
| Finnish | `fi` | `fi-FI` | — | Yes |
| French | `fr` | `fr-FR` | fr-CA, fr-BE, fr-CH | Yes |
| Galician | `gl` | `gl-ES` | — | Yes |
| Georgian | `ka` | `ka-GE` | — | Yes |
| German | `de` | `de-DE` | de-AT, de-CH | Yes |
| Greek | `el` | `el-GR` | — | Yes |
| Gujarati | `gu` | `gu-IN` | — | Yes |
| Haitian Creole | `ht` | `ht-HT` | — | Yes |
| Hebrew | `he` | `he-IL` | iw-IL | Yes |
| Hindi | `hi` | `hi-IN` | — | Yes |
| Hungarian | `hu` | `hu-HU` | — | Yes |
| Icelandic | `is` | `is-IS` | — | Yes |
| Indonesian | `id` | `id-ID` | in-ID | Yes |
| Irish | `ga` | `ga-IE` | — | Yes |
| Italian | `it` | `it-IT` | it-CH | Yes |
| Japanese | `ja` | `ja-JP` | — | Yes |
| Kannada | `kn` | `kn-IN` | — | Yes |
| Korean | `ko` | `ko-KR` | — | Yes |
| Latvian | `lv` | `lv-LV` | — | Yes |
| Lithuanian | `lt` | `lt-LT` | — | Yes |
| Macedonian | `mk` | `mk-MK` | — | Yes |
| Malay | `ms` | `ms-MY` | ms-SG | Yes |
| Maltese | `mt` | `mt-MT` | — | Yes |
| Marathi | `mr` | `mr-IN` | — | Yes |
| Norwegian | `no` | `nb-NO` | no-NO, nn-NO | Yes |
| Persian | `fa` | `fa-IR` | — | Yes |
| Polish | `pl` | `pl-PL` | — | Yes |
| Portuguese | `pt` | `pt-PT` | pt-BR | Yes |
| Romanian | `ro` | `ro-RO` | — | Yes |
| Russian | `ru` | `ru-RU` | — | Yes |
| Slovak | `sk` | `sk-SK` | — | Yes |
| Slovenian | `sl` | `sl-SI` | — | Yes |
| Spanish | `es` | `es-ES` | es-MX, es-US, es-AR, es-CO, es-CL, es-PE | Yes |
| Swahili | `sw` | `sw-KE` | sw-TZ | Yes |
| Swedish | `sv` | `sv-SE` | — | Yes |
| Tagalog | `tl` | `fil-PH` | tl-PH | Yes |
| Tamil | `ta` | `ta-IN` | ta-SG, ta-LK | Yes |
| Telugu | `te` | `te-IN` | — | Yes |
| Thai | `th` | `th-TH` | — | Yes |
| Turkish | `tr` | `tr-TR` | — | Yes |
| Ukrainian | `uk` | `uk-UA` | — | Yes |
| Urdu | `ur` | `ur-PK` | ur-IN | Yes |
| Vietnamese | `vi` | `vi-VN` | — | Yes |
| Welsh | `cy` | `cy-GB` | — | Yes |

## Audit result

- ML Kit translation languages covered: **59 / 59**.
- Explicit participant-facing UI localisation profiles: **59 / 59**.
- Explicit translation-code → speech-locale mappings: **59 / 59**.
- Arabic: `ar` translation model → `ar-SA` primary speech locale, with common Arabic regional variants accepted and device-advertised Arabic preferred.
- Krio (`kri`): **removed from ODK choices** because it is not in the current ML Kit Translation language list.
- Device speech availability: checked opportunistically from Android recogniser language details; a translation model being present does not imply a speech model is present.

## Regional speech routing added in v0.2

Translation language codes remain ML Kit codes. Country/region choice and speech-region choice are separate participant-facing metadata.

Arabic keeps translation code `ar` and adds three selectable speech families:

| Participant choice | Preferred Android speech locale candidates |
|---|---|
| Levantine · الشامية | `ar-LB`, `ar-JO`, `ar-SY`, `ar-PS`, `ar-IL` |
| Gulf · الخليجية | `ar-AE`, `ar-SA`, `ar-KW`, `ar-QA`, `ar-BH`, `ar-OM` |
| North African · شمال أفريقيا | `ar-MA`, `ar-DZ`, `ar-TN`, `ar-LY`, `ar-EG` |

The preference is best-effort. If the selected regional locale is not advertised by the installed Android recogniser, the module falls back to another advertised Arabic locale rather than failing the Talk action.

The flag-first country catalogue was checked against the 59-language ML Kit list: **59/59 translation languages have at least one flag/visual route**. Multilingual countries expose a second language choice rather than equating country with language.
