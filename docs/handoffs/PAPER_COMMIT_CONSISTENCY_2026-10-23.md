# Paper transcription Commit consistency — 23 October 2026

Checkout: /Users/icrucrob/AndroidStudioProjects/MethodMesh; branch feature/esp-mesh-transport; Master Book v1.31 FINAL. Bounded continuation after PAPER_BARCODE_RETENTION_2026-10-23.md, not a full-module or clinical readiness claim.

## Defect and correction

Paper transcription captured flat dynamic answers before awaiting file hashes, then read current review results/manual audit to construct structured answers, counts, logic checks, attachment descriptions and the extraction audit. Changes during hashing could combine different working states in one execution. Duplicate taps could launch overlapping finalizations, and cancellation was caught as an ordinary failure.

- PaperCommitSnapshot serializes all answers, review/NA/logic state, manual ledger, counts, registration diagnostics, media identities/labels/dimensions and attachment policy synchronously before any suspend point. Later source, rectified and ROI hashes are added only to that frozen metadata. Native recent-activity counts use the same snapshot, not later working values.
- PaperCommitController allows one active screen-owned preparation. Duplicate starts do not prepare or return a second record. Explicit Cancel commit, review invalidation, new acquisition/extraction, and screen disposal cancel it. Cancellation is not a failure and does not publish, log or return evidence. An old cancelled/non-cooperative operation cannot return late or release a newer operation's gate. There is no automatic retry.
- The working surface is replaced by a clear progress/cancel surface during preparation. Before publication, the controller checks that the session, reviewed results, manual ledger, schema, source identity and workspace mode still match. Changed working state rejects prepared evidence and requires an explicit review/Commit.
- Commit rejects unresolved/empty review, active extraction/scanner and a schema different from the extracted schema. Parsed canonical schemas are compared, so harmless JSON formatting differences are not rejected. Starting a new scan clears the prior working session immediately, preventing a commit of that old session while acquisition opens.
- The execution request starts before hashing, so its execution interval includes finalization work. The resulting original execution is retained exactly as in the preceding increment. Automatic return, native post-Commit actions and explicit workspace persistence retain their established boundaries.
- Failure to update optional recent activity warns without hiding or replacing an already committed record. Source/media hash failure produces no committed record; an explicit retry remains available.
- Public method IDs, inputs/outputs, JSON/audit schemas, capability versions, app version, codecs, registration/OCR algorithms, NA policy, and caller attachment semantics are unchanged. Shared framework/store and protected NFC/attestation/trusted timestamp/signing/clinical/Sentinel sources were not edited. Existing unrelated dirty changes remain preserved.

## Verification

Both app and instrumentation debug APK builds pass. git diff --check passes.

**26 focused JVM cases pass:** eight new controller/source-wiring cases (single-flight, explicit cancel, uninterruptible old work versus newer commit, failure/retry, owning-scope cancellation, already-cancelled scope, changed-working-state rejection, screen wiring); four Paper projection/retention, four retained-store, five app-wide registry/contract parity and five native closeout policy cases. These are a bounded selection, not a new full-suite result.

**Seven Android cases pass in one focused run** on a dedicated read-only API 37 emulator:

- Five new snapshot cases use synthetic already-registered/reviewed sessions. A deliberately suspended hash allows later answer/image edits, score/anchor mutations and manual-ledger changes; the prepared structured/flat answers, audit, NA/counts and ROI hash identity remain from the original snapshot. Real FileProvider content-URI bytes verify source/rectified/ROI SHA-256 consistency. Other cases cover cancellation during an uninterruptible hash, empty/unresolved rejection, canonical field set and unchanged caller-supplied-source policy.
- Two preceding synthetic post-Commit Paper screen cases still preserve the identical execution object/FULL JSON through recreation and Done.

No real camera/OCR, designer safety-gate run, actual busy-screen interaction with a captured page, in-flight rotation of a real capture, physical phone/card, actual Collect/Kobo/recipient app, or OS process death was exercised. Controller cancellation/staleness is tested deterministically; UI wiring is source/build checked. Hash cancellation prevents publication after a blocked read returns; it does not promise to interrupt that read immediately. Frozen metadata does not lock media files against outside modification or guarantee durable cache lifetime.

Only three test-created media files were deleted by the test cleanup. The dedicated emulator was shut down. No physical-phone installation, commit, push, tag or release. APK: app/build/outputs/apk/debug/app-debug.apk.

## Subsequent continuation

Paper committed exports were subsequently aligned with the shared Downloads/Share contract in `PAPER_COMMITTED_EXPORTS_2026-10-23.md`. The following list records the queue at this earlier Commit checkpoint.

## Remaining strategic queue at this checkpoint

1. Module-owned Share/Save conformity, starting with Paper Bridge committed exports versus explicit schema/workspace saves. Paper exports still target MethodMesh Files, not the canonical committed Downloads workflow.
2. Real full Paper designer/transcription and barcode scan/clone camera workflows, busy/cancel/rotation acceptance with a real captured page, large working-state/record limits and media lifetime/provider faults.
3. Signals physical sessions/large frame state, Compass physical camera-sight, other module closeout failures, Astronomy/provider runtime inputs, Workbench authoring policy and large log/payload limits.
4. Actual external caller/Collect/Sentinel acceptance remains separate from these engineering fixtures. All 80 modules / 381 capabilities remain in the strategic review scope; no blanket all-module runtime certification.
