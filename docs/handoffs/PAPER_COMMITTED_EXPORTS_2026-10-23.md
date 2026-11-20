# Paper Bridge committed Share/Downloads — 23 October 2026

Checkout: /Users/icrucrob/AndroidStudioProjects/MethodMesh; branch feature/esp-mesh-transport; Master Book v1.31 FINAL. Bounded strategic audit continuation after PAPER_COMMIT_CONSISTENCY_2026-10-23.md.

## Confirmed defects and corrections

- Committed Save in both Paper screens wrote private MethodMesh Files/ArtifactStore collections rather than canonical Downloads. PaperBridgeResultActions now delegates to shared OutputExportRepository.saveToDownloads. UI explicitly says Save to Downloads and reports the actual folder summary. One run-specific capability/form/timestamp stem identifies result text, typed domain/media files and optional metadata JSON.
- Reusable schema/workspace persistence and generated authoring artefacts remain separate and unchanged. PaperBridgeWorkspace and PaperDesignExportStore are not modified. Existing saved Files collections are not migrated or deleted. Commit itself still does not create a new output/archive copy.
- Native transcription exports now contain the registered page and declared image ROIs, not an additional raw acquisition page. This matches the module's documented beef-first policy. Source URI/hash/evidence remain in the original committed record and unchanged external/ODK contract; no return field is removed or renamed.
- Both native Share actions still use shared ResultShare: equivalent useful text in EXTRA_TEXT and a readable coherently named text/plain sidecar, real domain/media streams, ClipData and read-only grants. Audit defaults off. When enabled, canonical FULL JSON is appended to the text/sidecar, not added as a separate debug JSON stream. Designer template JSON/YAML/ZIP are genuine domain outputs and remain attachments with audit off or on. UI descriptions no longer claim Share emits metadata.json as an attachment.
- Both Paper screens previously treated preset SHARE like Home, without performing the share. Final-step native preset Share now dispatches before confirming; failed Share stays open. SAVE similarly confirms only after the complete Downloads operation succeeds. Intermediate steps and automatic external return preserve their existing no-native-export behavior. Closeout buttons state Share and finish or Save and finish where appropriate; host confirmation/logging ownership is preserved.
- Shared MIME fallback now treats application/octet-stream as uninformative when a known filename supplies a useful type. Shared Downloads fallback recognizes YAML/YML/ZIP; shared Share already had those suffix mappings. This is generic transport policy, not receiver- or Paper-specific branching. No shared transport signatures changed.
- Public capability IDs, fields/settings, versions, canonical execution/audit schemas, OCR/registration algorithms, workspace files, and NFC/attestation/trusted timestamp/signing/clinical/Sentinel sources are unchanged. Unrelated dirty changes are preserved. No physical-phone installation, commit, push, tag or release.

## Verification

Both debug APK builds pass; git diff --check passes. Thirty focused JVM cases pass: four new committed-export wiring cases, eight commit-controller/wiring, four Paper projection, four retained-store, five registry/contract parity and five native closeout policy. This is not a new full-suite run.

Fifteen Android cases were verified across an initial 15-case run (14 pass, one YAML metadata expectation failure) and a successful one-case rerun, without further production-code changes:

- Eight new actual committed-screen cases use synthetic canonical Paper records. They cover designer/transcription Share with audit off/on, Downloads with audit off/on, successful/failed final preset Share and Save on both screens, byte-identical original FULL evidence, read grants/ClipData, coherent text sidecars/names, domain JSON preservation, unchanged attachment sets when audit changes, and no duplicate raw native scan.
- Three existing shared Share contract cases pass, including real domain JSON versus debug JSON and recipient hints. Two existing shared Downloads cases pass, including complete bytes/types/names and rollback of a partial attempt with missing media. Two preceding synthetic Paper committed-screen recreation/Done cases pass.
- Downloads tests use real MediaStore rows and FileProvider source bytes, with cleanup restricted to test-unique files/rows. Choosers are intercepted, not delivered to another app. Domain/media content is synthetic, not a full valid generated form/image/ZIP acceptance run. Tests inject records only after the capture boundary; no production capture bypass is added.
- The YAML test now asserts the exact requested MIME separately from platform storage. Targeted diagnostics confirm application/yaml was requested, but API 37 MediaProvider stored application/octet-stream. The .yaml suffix and complete bytes are preserved; Share advertises application/yaml. This platform label limitation is recorded, not hidden as an all-green initial run. A transient new diagnostic-fixture reference compile error was corrected before the focused rerun.
- No full camera/OCR/designer gate, real ODK/Collect/Kobo/recipient app, physical phone/card, process death or broad hardware acceptance. Successful module callback ordering is tested; this increment does not newly instrument host persistent logging with a real Paper capture. Existing native policy tests remain in the focused JVM selection.
- Dedicated read-only API 37 emulator shut down. App APK: app/build/outputs/apk/debug/app-debug.apk.

## Subsequent multi-module continuation

The missing legacy Downloads permission path and Signals/Weather/Network export issues were subsequently addressed in `NATIVE_EXPORTS_AND_LEGACY_DOWNLOADS_2026-10-24.md`. Actual API 27/28 device acceptance remains open. The list below records the queue at this earlier Paper checkpoint.

## Outstanding risks / next review at this checkpoint

1. API 27/28 public Downloads still uses the existing legacy storage path. The manifest declares WRITE_EXTERNAL_STORAGE through API 28, but source search found no runtime acquisition of that permission. A fresh older-device install may therefore fail Save. No permission request/fallback was introduced or verified here; address this in the shared persistence workflow with older-API acceptance rather than claiming all-version Save parity.
2. Real recipient-app handling and real domain-file/form acceptance, Android versions/providers other than the API 37 emulator, Downloads permission/fault UX, and YAML's platform display/MIME normalization.
3. Continue module-owned Share/Save review outside Paper Bridge, including Signals audit controls and Weather/Network action dispatch; physical sessions/large working payloads, remaining closeout faults, Astronomy/provider runtime input, Workbench policy, and large return/log limits remain open.
4. Full app-wide runtime/UX parity across 80 modules / 381 capabilities and actual clinical/Collect/Sentinel acceptance remain incomplete. Protected credential and signing systems must retain their known-good acceptance baseline.
