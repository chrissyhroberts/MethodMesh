# Barcode scan / Compass result-action audit — 28 October 2026

Checkout: /Users/icrucrob/AndroidStudioProjects/MethodMesh; branch feature/esp-mesh-transport. Canonical authority: Master Book v1.31 including the file-first native Share erratum. This is the next bounded strategic-review increment after FILE_SHARE_REGRESSION_2026-10-28.md, not a whole-app/clinical sign-off.

## Confirmed findings and corrections

1. Barcode scan and Compass final native presets configured to Share could confirm/navigate without dispatching Share. Both committed surfaces now use the existing success-gated CanonicalCommittedResultActions: chooser dispatch or complete Save precedes confirmation of the exact original execution; failed operations remain visible, unconfirmed and explicitly retryable through rotation. Completion is delegated to the launch owner rather than private module Home navigation.
2. Barcode's manual Share callback lacked a visible failure handler. It now uses the same shared failure status rather than letting the launch exception escape the control.
3. Barcode's intermediate-step finish label could advertise Save and finish although its handler correctly skipped that export. Final preset actions/labels now come from the same final-step-only policy as the action. Intermediate and direct Done confirm the original without implicit Share/Save/Home.
4. Module-owned saves previously performed Downloads work synchronously. These two surfaces now use the existing single-flight IO worker/permission preflight, retaining error/audit/action state across recreation. Barcode New scan and Compass Recommit/Sight acquisition are fenced while export or completion is pending.
5. The blocked-save test caught a real timing gap in the new parent busy observer: reporting only from a deferred effect left a queued-input window. Busy state is now reported synchronously at action start and via SideEffect when restoring/completing, with a handler-level New scan guard as well as disabled controls. This changes no file transport behavior.

The instrument/scanner and task-specific primary/technical displays remain. Their module-owned committed components delegate only the action controls. Barcode keeps decoded payload as its text; Compass keeps compass_result as its human reading. Generic action controls gain optional primary-text/attachment overrides and an optional busy observer; existing callers keep their default projection. Both capabilities explicitly pass no media attachments. A literal content:// barcode therefore remains decoded text rather than being interpreted as a phone-file attachment. Canonical FULL always comes from the unchanged original ExecutionResult; no ad-hoc audit map or replacement execution is built for Share/Save/Copy/Done. Audit is keyed to execution and off by default for a new record.

No public method, input, output, version, format, evidence hash, compass math/north reference, physical sensor/camera acquisition or automatic external-return contract is changed. Barcode generation/cloning algorithms and their committed UIs are not migrated in this increment. Automatic Commit still returns the retained original; no extra native export is injected into that route. Tap-to-copy and safe explicit HTTP/HTTPS opening remain available.

## Sharing regression safeguard

ResultShare.kt was not edited. Its SHA-256 before and after this increment is:

6fbfc7a405ead303a0b1cba27fbc3012416ab10384b015350bc62eedd610015e

The user-verified file-first sharing correction and its text-only exception remain intact. The current Master Book's sharing erratum is not altered by this pass. Three actual separate-UID chooser/file tests still pass: scanner PDF/OCR/ZIP, plain timestamp ZIP and audit-enabled ZIP. These use synthetic transport fixtures, not a live TSA or a real WhatsApp recipient.

## Verification

Both debug app and Android-test APK builds pass on final sources. git diff --check passes. Thirty-seven focused JVM tests pass: five new Barcode/Compass wiring/primary-value/reacquisition/automatic-return cases; ten committed-action state cases; four retained-store; five native closeout policy; five registry parity; three existing barcode format/evidence cases; and five sharing cases. This is not a full-suite run and does not supersede recorded baseline failures. Registry metadata still covers 80 modules / 381 capabilities, not end-to-end acceptance of all of them.

Forty-four distinct Android cases were verified across two runs on the disposable read-only API 37 ARM emulator:

- Initial 44-case run: 42 passed; the new slow-save case exposed the busy-report timing gap. Its failed fixture released a late write which contaminated the following barcode audit test's broad before/after folder-count assumption. This was not a second audit-default production failure.
- The busy gate was corrected, and the new fixture now tracks exact provider-created URIs, waits for published writes and releases/waits for blocked writes before cleanup. No user/pre-existing rows are collected by a broad prefix deletion.
- Only 13 affected cases were rerun: all nine new module action cases, Compass actual-screen retention and Recommit, actual-host Compass confirmation/logging order, and the existing generic slow-save recreation case. All 13 pass. There is no claim of one clean final 44/44 run or a whole-suite rerun.

The nine new cases exercise real Barcode/Compass committed components with canonical synthetic captures: audit off/on, exact useful message/text-sidecar/clipboard values, real Downloads bytes and optional metadata, original FULL through recreation, six intermediate action variants, final Share/Save ordering, denied actions and explicit retries, blocked IO with responsive Activity/recreation/no duplicate write and disabled New scan, plus a URI-looking literal barcode. Choosers are intercepted; no real message or browser launch occurs.

Eight existing Compass/sensor-scope lifecycle cases passed in the initial run; four exercise the real Compass screen with emulator sensors (rotation/Done, Recommit/new ID, simulated missing original and automatic return without replay), and four cover sensor-scope regression. Normal Compass retention/Recommit also pass after the lock correction. The new actual ExternalWorkflowActivity Compass Home/log test passes in both runs and checks confirmed original evidence and opt-in log contents before intercepted Home navigation. Twenty existing multi-module committed-action cases and six shared/native file transport cases passed in the initial run. Physical compass accuracy, camera sight, real barcode capture/interruption/permission recovery, OS process death, actual Collect/receiver/Sentinel/clinical acceptance and API 27/28 permission UI remain unqualified. The Compass busy-toolbar guard is source/build checked; the blocked-IO fixture uses the module committed component, not live camera/sensor acquisition.

Only fixture-owned Downloads rows and sidecars are removed. No physical phone is installed/controlled. The dedicated emulator is shut down after verification. Debug build: app/build/outputs/apk/debug/app-debug.apk.

## Protected boundaries and next queue

NFC/attestation, trusted timestamp proof generation/verification and digital signing sources are untouched by this increment; unrelated existing signing changes and the dirty checkout are preserved. Public/external/ODK, FULL, log policy and app/capability versions are unchanged. No commit, push, tag or release.

Next: the remaining Astronomy/provider request cancellation, runtime-input and lifecycle cases; further module-owned export/closeout paths and large-file responsiveness; Workbench capability-specific authoring eligibility; large saved-state/log bounds and legacy/device/Collect/hardware acceptance. Camera/GPS/physical correctness acceptance for Barcode and Compass remains separate from this result-action increment.
