# File Lab v0.7 validation record

## Trigger for this iteration

Device testing found that PDF → CBZ could fail after conversion with `requested cbz generated zip`. The generated file was a valid ZIP-container comic, but the semantic verifier required at least two image members before classifying a ZIP as CBZ. A legitimate one-page PDF therefore produced a legitimate one-page CBZ that the verifier mislabeled as generic ZIP.

## v0.7 fix

- ZIP-family classification now receives the selected/output filename as corroborating evidence.
- A `.cbz` containing one image page is accepted as CBZ.
- A generic `.zip` containing only one image remains generic ZIP, preventing the extension hint from globally weakening ZIP classification.
- `ComicInfo.xml`, `Thumbs.db` and `.DS_Store` are treated as ignorable comic metadata when calculating image dominance.
- Multi-page image-dominant ZIPs continue to classify as CBZ by content alone.
- Mixed-content archives still fail CBZ semantic verification even when renamed `.cbz`.
- `file.inspect` and `file.convert` implementation versions are now `0.7.0`; method IDs and field contracts remain unchanged.

## Regression checks executed here

The pure Kotlin engine was compiled and executed against six targeted assertions:

- one-image archive named `.cbz` → `cbz`;
- the same one-image archive named `.zip` → `zip`;
- two-image archive named `.cbz` → `cbz`;
- two-image archive named `.zip` → `cbz` by content;
- one-image `.cbz` plus `ComicInfo.xml` → `cbz`;
- mixed image/text archive named `.cbz` → `zip`.

All passed. This specifically covers the device-reported verification failure. A full Android device rerun of PDF → CBZ remains the final integration check because `PdfRenderer` execution itself is Android-only.

---

# File Lab v0.6 validation record

## Trigger for this iteration

Device feedback showed that identification itself was useful, but a technical label such as **ELF binary** or **CBR comic archive** was not enough. File Lab should explain what a detected format actually is, what people normally use it for and what software can sensibly open it.

## v0.6 changes

- added `FileFormatKnowledgeCatalogue`, an offline catalogue covering **138 detected format identities**;
- every recognised format now resolves to a plain-English description, technical identity, typical uses, software suggestions, related-format hints, cautions and a File Lab support statement;
- useful common producer/toolchain hints are added for formats where producer context is meaningful;
- native inspection now presents **What is this format?** before specialist facts;
- Android package-manager discovery provides **Open with** handlers actually installed on the device;
- bundled **Often opened with** suggestions remain explicitly separate from installed apps;
- unknown/unclassified formats get a neutral unknown-format explanation rather than guessed semantics;
- the canonical `file.inspect` result now includes human-readable knowledge fields plus `filelab_format_knowledge_json`;
- the canonical ODK showcase was updated to capture the new declared outputs.

## Pure Kotlin checks executed here

`FileLabModels.kt`, `FileFormatKnowledge.kt` and `FileLabEngine.kt` compile under the available `kotlinc` environment. Smoke assertions passed for:

- ELF `.so` content → `elf` plus Linux/Android shared-library explanation and producer hints;
- RAR5 + `.cbr` → `cbr` plus comic/RAR explanation and KOReader suggestion;
- plain `.txt` → Text catalogue entry;
- exactly 138 catalogue entries present in this build;
- unknown external format IDs receive the generic `Other` explanation rather than fabricated semantics.

## Android checks still required

A full Android Gradle build/device run is still required before Production promotion. In particular verify Android package-manager handler enumeration across at least PDF, CBR/CBZ, FITS/unknown scientific data and ELF; ensure `Open with…` grants URI read permission correctly; and repeat the v0.5 conversion/device tests.

## ODK/XLSForm

`example_odk_showcase_file_inspect.xlsx` contains one `file.inspect` invocation and captures all current declared capability outputs plus `methodmesh_status` and `methodmesh_full_json`. `example_odk_showcase_file_convert.xlsx` is unchanged from the v0.5 conversion contract.

---

## v0.5 regression record


## Trigger for this iteration

Device feedback from v0.4 identified three concrete failures:

- ELF/shared-object detection worked, demonstrating that content-first inspection was useful;
- a CBR comic was not recognised;
- `file.convert` offered too little for ordinary text files, and PDF→CBZ result export could surface the original PDF instead of only the converted output.

v0.5 treats those as contract/implementation defects rather than cosmetic issues.

## Fixes verified in source

- RAR4/RAR5 magic recognition added; `.cbr` + RAR content resolves to `cbr` with explicit RAR-container warning.
- 7-Zip/CB7 and TAR/CBT recognition added.
- broad signature/extension catalogue added across documents, media, scientific/research, field/geospatial, genomics, ham/SDR/navigation, engineering, executable/network and firmware families.
- plain `.txt` now resolves to `txt`; text-like formats have executable PDF routes.
- PNG/JPEG/WebP/BMP/GIF have executable PDF routes.
- conversion outputs are reopened through `FileLabEngine.inspect` and rejected if their detected format does not equal the requested target.
- `filelab_source_uri` is no longer a declared `file.convert` output; this prevents shared attachment/export machinery from treating the original input as a converted result.
- live conversion output has explicit Open / Save file / Share / Commit controls.

## Pure engine tests executed here

`kotlinc` compilation and executable smoke checks passed for:

- RAR5 bytes + `.cbr` filename → `cbr`;
- RAR content + `.rar` filename → `rar`;
- 7-Zip content + `.cb7` filename → `cb7`;
- plain text + `.txt` → `txt` and `txt→pdf` route exists;
- Markdown + `.md` → `markdown` and `markdown→pdf` route exists;
- ELF bytes + `.so` → `elf`;
- image-dominant ZIP → `cbz` and `cbz→pdf` route exists;
- PCAP magic → `pcap`;
- vCard text → `vcard`;
- Cabrillo log text → `cabrillo` and `cabrillo→pdf` route exists;
- `pdf→cbz` route exists;
- unimplemented `cbr→pdf` remains absent rather than being falsely advertised.

The pure engine compiles under the available `kotlinc` environment. `FileLabAndroid.kt` also passes a compile-oriented check against lightweight Android API stubs covering the APIs used by this module (content URIs, FileProvider, PdfDocument/PdfRenderer, Bitmap/Canvas and intents). This is not a substitute for an Android Gradle build. A full `:app:compileDebugKotlin`/device build is not claimed because the execution environment does not contain the complete Android SDK/repository checkout.

## ODK/XLSForm

Canonical workbooks were regenerated with the v0.5 contract:

- `example_odk_showcase_file_inspect.xlsx`;
- `example_odk_showcase_file_convert.xlsx`.

Each contains exactly one MethodMesh invocation and captures `methodmesh_status` and `methodmesh_full_json`. The conversion workbook no longer declares `filelab_source_uri` as a return `file` field; `filelab_output_uri` is the sole conversion attachment.

## Remaining device checks

Before Production promotion, exercise on a real Android build at minimum: CBR inspection, TXT→PDF, PNG/JPEG→PDF, CBZ→PDF, PDF→CBZ, Save converted file, Share converted file, ODK attachment return, KOReader hand-off and lifecycle restoration during a working conversion.
