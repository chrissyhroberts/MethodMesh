# File-bearing Share regression — 28 October 2026

Checkout: /Users/icrucrob/AndroidStudioProjects/MethodMesh. The user reported that scanner/trusted-timestamp shares to WhatsApp delivered only filenames, and correctly flagged the risk that recent shared work had undone prior receiver compatibility. After this correction the user reported: "seems fixed now". Record this as user-reported retest feedback, not certification of every receiver, both acquisition workflows, or clinical/Sentinel acceptance.

## Confirmed transport differences and correction

The recent shared ResultShare change added EXTRA_TEXT to file-bearing shares. A filename/summary message could compete with the actual PDF/ZIP streams. In addition, the generated text sidecar was first in the stream list, and ClipData.newUri advertised only that first text/plain type even when PDF/ZIP/image items followed. Earlier tests inspected producer-side intents/streams, not a real separate-UID file receiver. WhatsApp's internal selection algorithm was not inspected; the exact receiver decision remains an inference from the report and the changed envelope.

The shared transport now:

- puts real media/domain files before generated summary text in EXTRA_STREAM and ClipData;
- omits competing EXTRA_TEXT for file-bearing shares, restoring the earlier file-oriented message behavior;
- advertises every included MIME type in ClipData, not just text/plain;
- keeps useful summary and opt-in FULL audit in the readable text sidecar; does not manufacture a separate generic JSON attachment;
- retains equivalent EXTRA_TEXT/text-file behavior for text-only results;
- retains selected-recipient read-only grants, no package-specific branches and no broad pre-selection grants.

Source/proof/PDF/image bytes, the selected real-file set, Downloads behavior, public IDs and external/ODK returns are unchanged. The timestamp screen has only a one-line caption correction; proof generation, verification, TSA/Clock Assurance and NFC/signing/attestation implementations are untouched. Scanner processing is untouched. Existing unrelated dirty-checkout changes are preserved.

## Preventing reintroduction

Master Book v1.31 now contains a dated native Share compatibility erratum in section 9, quick rule 18 and MM-OUT-005: simultaneous message plus text-file transport applies to text-only results; file-bearing results must remain file-first without a competing message. This deliberately corrects the older blanket EXTRA_TEXT wording that contributed to the regression. The edition remains v1.31 and no runtime/app/capability version changes are made. Shared/scanner and timestamp audit captions reflect the corrected distinction.

Updated shared, Paper and photo tests assert the file-bearing exception rather than preserving the faulty competing message. The new NativeFileShareReceiverActivity exists only in the test APK under a separate Android UID.

## Verification

Both debug app and Android-test APK builds pass on final sources. Twelve focused JVM tests pass: five sharing policy/projection cases, the existing trusted-timestamp verification case, scanner ODK form contract case and five registry parity cases. This is not a full-suite or comprehensive timestamp-verification result.

Android verification used a disposable read-only API 37 ARM emulator:

1. Initial six-case run: four passes and two chooser-harness timeouts. The scanner cross-app case and three existing Share contracts passed. The harness could click a stale previous chooser window; diagnostics showed a reused chooser, not a PDF/ZIP reading failure.
2. The harness now waits for a fresh chooser window and isolates chooser tasks. The final bounded 32-case run passes cleanly: three real cross-app file-delivery cases, twenty shared committed-action cases, eight Paper export cases and the previous redacted-photo sharing regression. Together with the three earlier passing shared Share contracts, 35 distinct Android cases were verified across the two runs. This is not a blanket 35/35 single-run result.

The three new cross-app cases send a real generated PDF, OCR text and a ZIP through Android's actual chooser to an explicitly selected test receiver in another UID. That receiver reads/hashes all bytes, checks all advertised types, observes no competing message, cannot write the files, and cannot read an unselected original. Plain and audit-enabled ZIP delivery are both covered. The ZIP is a synthetic transport fixture, NOT a validated RFC 3161 proof; no TSA/network request or clinical assertion is made.

The emulator has no WhatsApp installed. No real receiver message was sent and no physical phone was installed/controlled by this work. The user's subsequent "seems fixed now" is the available real-world retest evidence, with no inference that every route was exercised. The last source changes after the clean Android run are caption text only; final sources were built and the JVM selection rerun successfully. Only test-created synthetic source files, sidecars and Downloads rows were cleaned. The emulator was shut down after testing.

Build: app/build/outputs/apk/debug/app-debug.apk. No commit, push, tag or release. The wider strategic review remains unfinished; this increment fixes only the reported sharing regression.

Android reference checks used the official [sharing guidance](https://developer.android.com/develop/ui/compose/sharing/send) and [ClipData reference](https://developer.android.com/reference/android/content/ClipData). These support typed streams/grants and complete clip metadata, not a claim about WhatsApp's internal implementation.
