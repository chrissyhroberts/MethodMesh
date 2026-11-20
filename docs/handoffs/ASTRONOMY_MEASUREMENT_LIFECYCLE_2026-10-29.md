# Astronomy measurement lifecycle — 29 October 2026

This bounded continuation covers astronomy.sky_test, astronomy.focus and astronomy.mount_stability in /Users/icrucrob/AndroidStudioProjects/MethodMesh on feature/esp-mesh-transport, against canonical Master Book v1.31. The overall app-wide audit remains active. NASA/SAF, polar alignment's location/result workflow, the local calculator screens and session persistence remain separate follow-ups.

## Confirmed defects and fixes

1. All three screens rebuilt completed executions from saved field JSON, creating replacement IDs/times after recreation. They now use the existing retained-original store. The exact original/FULL envelope is used for display/actions/Done/automatic return; unavailable originals require explicit recovery. No saved-values reconstruction remains in these three screens.
2. Sky/mount timed windows saved running=true across recreation, potentially restarting a full measurement over lost/partial acquisition. Timed runs now belong to a composition/lifecycle-owned single-flight request. Backgrounding, recreation or explicit cancellation stops the sample; resume requires an explicit new run and no interrupted record is returned.
3. Mount cancellation could release or stop a newer run if both shared one sampler. Every run now owns its own source. A late cancelled run closes only its source and cannot publish or interfere with its replacement. MountStabilitySampler also ignores callbacks while stopped and clears the previous gyroscope observation at start. Vibration math/thresholds are unchanged.
4. Focus queued main-thread updates could repopulate reset/camera-switched state, and saved best/median readings could outlive the physical analyzer that produced them. Each frame generation now has a bounded owned collector. Reset, camera/averaging changes and interruption retire the old owner; posted updates check that owner before affecting UI. Capture freezes one coherent summary/readout and rejects later frames. Live working readings start fresh; completed records remain original.
5. CameraX provider callbacks could bind after disposal, and unbindAll() could unbind another screen's camera. The new module-local camera owner fences late provider callbacks, binds the requested owned preview/analysis, and releases only those use cases and its executor. Binding failures become visible retryable screen errors. The polar AR preview uses this same owned-camera helper; its remaining GPS/result issues are not fixed by that helper.
6. Sky test's Save this run as CLEAR-NIGHT reference control sat inside working content hidden by the completed-result scaffold. It is now available beside the completed result. Its exact completed StarTestSummary is retained separately in Activity memory for calibration, including across rotation. The note comes from the frozen original inputs; saving a new reference never rewrites the captured result or historical scores. Missing original evidence cannot be used as a new reference.
7. Relevant duration/frame/note inputs and focus camera choice obey native runtime/fixed-field visibility. A run captures immutable input values; edits made while sampling remain available for a later run. Automatic return goes through the shared original-ID guard rather than an extra module callback.

AstronomyMeasurementScreens.kt owns the repaired three UIs; the existing screen objects/IDs delegate to it. AstronomyFrameSession.kt handles owned frame windows and the small camera lease; AstronomyOwnedCamera.kt handles CameraX resources; AstronomySkyReferenceStore.kt retains the exact calibration sample. These are module-local mechanisms, not a module-specific branch in shared transport/framework code.

The existing scientific engines, StarFrameAnalyzer image analysis/summarize formulas, AstronomyMath, AstronomyRepository, public method IDs/settings/output fields/versions and Development maturity are unchanged. Focus uses the existing odd/even rolling-median and best-value rules. No new claim of calibrated seeing, sky magnitude or absolute physical accuracy is introduced.

## Verification

Both debug app and Android-test APK builds pass on final sources. git diff --check passes.

Final JVM run: 75 passed, no failed/errors/skipped:

- 10 new frame-window, freeze, invalid/late-frame, bounded-buffer and camera-lease ownership cases.
- 13 existing Astronomy automatic-routing/site cases.
- 6 existing single-flight/cancellation controller cases, reused by timed measurements.
- 4 retained-original store cases.
- 5 native closeout-policy cases.
- 5 registry-parity cases (80 modules / 381 capabilities).
- 5 ResultShare cases.
- 27 protected NFC credential/evidence/form/overwrite/one-shot/protocol/compiler contract cases.

Final clean Android run: 53 passed in one run on the disposable read-only API 37 ARM emulator:

- 11 new actual measurement-screen cases, several iterating all three screens/routes.
- 14 automatic Astronomy screen cases.
- 9 Astronomy dashboard cases.
- 3 GPS task cases.
- 10 shared-scaffold result-action/closeout cases.
- 3 actual separate-UID scanner PDF/OCR/ZIP and timestamp-proof ZIP delivery cases.
- 3 native Share contract cases.

The initial ten measurement cases also passed; the final run adds an adversarial non-cooperative old mount run and verifies it cannot stop a newer source. Coverage includes exact original/FULL/Done retention, interrupted rotation/background recovery, unavailable-original recovery without camera replay, fixed/runtime controls, frozen acquisition inputs, focus reset/camera switch, exact sky reference saving after rotation, camera-error cancellation and automatic return once.

Measurement UI tests inject synthetic StarFrameMetric values, controllable timer completion and synthetic mount summaries through the production screen bodies. Camera lease tests use controlled resource callbacks; they do not exercise actual CameraX hardware binding. Missing-original tests simulate lost retained memory rather than real OS process death. The sky-reference test restores only its prior calibration preference; no broad user-data clearing is performed.

## Sharing and protected boundaries

ResultShare.kt was not edited. Its SHA-256 remains:

6fbfc7a405ead303a0b1cba27fbc3012416ab10384b015350bc62eedd610015e

The working file-first scanner/timestamp/WhatsApp compatibility correction and shared preset completion controls remain intact. Actual separate-UID readable file delivery passes, using transport fixtures rather than an installed WhatsApp recipient or live TSA.

No NFC credential/PIN/provisioning/Sentinel/clinical-trial, signing or timestamp implementation/source form/public contract was changed. Their 27 existing NFC checks pass, but real card/Collect/Sentinel acceptance is not replaced by emulator evidence. No physical phone installation, commit, push, tag or release.

## Outstanding review / acceptance

- Physical cameras, optical adapters, sensor accuracy, missing-sensor behavior, exposure/focus/OIS/permission dialogs and genuine CameraX provider/device failures still need device acceptance. The new resource owner has controlled tests rather than real camera switching/disposal acceptance.
- Polar alignment still has its earlier callback GPS and saved-value execution reconstruction; only camera-preview ownership was corrected here.
- NASA/light-pollution download/import/map workflows still need cancellation, saved busy-state, frozen site/input, retained-result and real provider/SAF review.
- Exposure-limit/image-scale and completed sessions still need original-record/runtime-input/closeout review. Active-session persistence/time accounting remains separate.
- Other modules' camera provider callbacks/unbindAll patterns, real preset/protocol/ODK/Collect recipients, old Android storage acceptance, OS process loss and large session/caller/log payload limits remain open.
- This is a focused checkpoint, not app-wide completion or replacement of historical full-suite baseline failures.
