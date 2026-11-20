# Paper Bridge / Barcode execution retention — 23 October 2026

Checkout: /Users/icrucrob/AndroidStudioProjects/MethodMesh; branch feature/esp-mesh-transport; Master Book v1.31 FINAL. Strategic audit continuation; discarded enrichment suggestions were not implemented.

## Confirmed fixes

Five screen bodies reconstructed captured executions from saved values or incomplete identity/timing subsets: Paper Form Designer, Paper Form Transcribe, Barcode generate, Barcode scan and Barcode clone. Reconstruction could change FULL evidence even where visible values and some Barcode IDs appeared unchanged.

- All five reuse the existing activity-scoped retained execution store, unchanged. Committed values, metadata and caller closeout use the original record. Only a small marker/opaque key is saved at the record boundary; existing editable working state is not comprehensively redesigned.
- Paper design retains the execution produced after its existing SAVE SCHEMA → TEST EMPTY → TEST DATA → COMMIT gates. These gates and durable schema saves are unchanged.
- Paper transcription projects stable/dynamic values from the original observation. Its startup effect exits before OCR/acquisition when a committed marker exists, even if the record itself is missing. Rotation must not replay extraction and clear an existing commit.
- Generator captures once at Commit, including automatic return. Explicit Edit/Commit creates a new record. Scan retains its original decoded working execution and freezes that same object at Commit, rather than reconstructing either from saved IDs.
- Clone retains its original action snapshot and canonical PNG URI. Repeated unchanged actions reuse it. Its no-visible-Commit workflow and explicit working-state invalidation remain intact.
- Marker plus missing original displays recovery, without stale committed Share/Save/Done controls. New run/design/scan is explicit. Camera screens return before rendering acquisition in that state. This is honest loss reporting, not durable process-death recovery.
- Public IDs, contracts, fields/settings, app/capability versions, barcode decoding, registration/OCR algorithms and shared return contracts are unchanged. No NFC, attestation, trusted-timestamp, signing, clinical or Sentinel implementation was edited; pre-existing signing changes are preserved.

## Verification and limits

Both debug APK builds and git diff --check pass. Initial new JVM-fixture compilation needed named-argument correction; the subsequent focused build/test run passes. No broad-suite test/fix/rerun loop.

**21 focused JVM cases pass:** four new Paper projection/source-wiring, four retained-store, three Barcode evidence/format, five app-wide registry/contract parity and five native closeout policy. The source check covers all five screens and the transcription startup guard; it is not camera/OCR acceptance.

**Eight Android cases pass in one focused run** on a read-only API 37 emulator. Four real generator workflows verify exact object and byte-identical FULL evidence through recreation/Done, explicit Edit/recommit creating a new identity, simulated lost-store recovery requiring a new run, and automatic return once without replay. Two existing generator cases verify host Home/logging and failed Share/Save closeout.

The other two Android cases inject synthetic canonical Paper executions into the post-Commit boundary, then use the actual committed screens, recreation and Done. They verify identical original object/FULL JSON. They do NOT pass real designer gates, transcribe a form, exercise the saved Paper marker's missing-record recovery path, or prove camera/OCR correctness. No production test bypass was added.

Barcode scan/clone are source/wiring/contract/build checked, not individually camera-instrumented. No real Collect/Kobo, external sharing receiver, physical phone/card, scanner UI or hardware was used. Missing-store recovery is simulated, not OS process death. Large working state, live-session continuity and media-file lifetime remain open.

The dedicated emulator was shut down. Rebuilt APK: app/build/outputs/apk/debug/app-debug.apk. No phone installation, commit, push, tag or release.

## Subsequent continuation

The asynchronous Commit consistency issue below was subsequently fixed and verified in `PAPER_COMMIT_CONSISTENCY_2026-10-23.md`. The following list records the queue at this retention checkpoint.

## Next priorities at this checkpoint

1. Paper transcription asynchronous Commit consistency: flat dynamic values are captured before suspending for hashes, while review results/manual audit are read later. With no in-flight fence, edits or reacquisition during hashing can mix evidence/race completion. Snapshot and fence this transaction with focused regression coverage. This pass retains completed evidence, but does not repair its assembly.
2. Paper committed Share/Save conformity: current module exports use MethodMesh Files rather than canonical committed Downloads. Preserve the explicit durable schema/workspace-save distinction when aligning exports.
3. Real full designer/transcription and barcode scan/clone camera workflows, missing-record recovery, OS process death and actual caller/media acceptance.
4. Existing app-wide queue: Signals physical-session/large-frame state; Compass camera-sight; remaining closeout faults; Astronomy/provider runtime inputs; Workbench policy; large log/payload limits; real storage/Collect/Sentinel acceptance.

This is a bounded five-screen evidence-retention fix, not app-wide or clinical-trial certification.
