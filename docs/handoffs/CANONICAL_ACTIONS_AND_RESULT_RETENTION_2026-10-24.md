# Canonical result actions and retained evidence — 24 October 2026

Checkout: /Users/icrucrob/AndroidStudioProjects/MethodMesh; feature/esp-mesh-transport. Authority: docs/METHODMESH_MASTER_BOOK.md v1.31 FINAL. Continuation after NATIVE_EXPORTS_AND_LEGACY_DOWNLOADS_2026-10-24.md, not a whole-app or clinical certification.

## Confirmed corrections

### 1. Completion policy across four more modules

The shared CanonicalCommittedResultActions component previously accepted no launch context. Its Done callback could confirm a native preset without its configured Share or Save. All ten call sites now pass CapabilityScreenContext: six Media sites, Expenses, two SVG surfaces and Geocaching's common MethodSurface (13 capabilities). These four modules declare 21 capabilities; this is shared-boundary coverage, not 21 independently device-accepted workflows.

Only a final, manually confirmed native preset applies Share-and-finish or Save-and-finish. Direct/intermediate steps delegate Done without an implicit export, and automatic external returns retain their existing module-owned path. A successful chooser dispatch or complete save precedes confirmation; failures stay visible and unconfirmed. Origin navigation and opt-in logging remain owned by existing callbacks/hosts. No capability-specific navigation or private export logic is added to shared code. SVG's extra full-screen header Done button was removed because it could bypass the common policy; Cancel remains before Commit and the committed action controls remain below the frozen instrument.

Canonical Share/Copy text, readable sidecars, typed media and optional FULL audit semantics are preserved. The audit toggle remains off by default. Domain exports (expense CSV/JSON/ZIP, catalogue files and Geocaching GPX/ledgers) remain separate and unchanged.

### 2. Responsive, single-flight Downloads actions

The common action component now runs Downloads file work on Dispatchers.IO. A small activity-owned, memory-only CommittedActionStore retains busy/error/pending-completion state across configuration changes. Permission preflight runs before starting the worker; the long-lived storage operation uses application context rather than retaining a departing Activity. Controls/audit/Edit are fenced during an action, pending completion is consumed once by the current composition, and no old UI callback is retained. There is no automatic retry on permission grant, recreation or failure.

This fixes UI-thread save work on these common-control surfaces only, not every Save in the app. There is no background-service/durable job promise, process-death recovery, user-facing cancellation or hard interruption of blocking provider IO. An explicitly started write can finish if its screen is abandoned, but a departed composition cannot confirm a newer result. Large text projection/Share preparation and other module-owned Save paths remain follow-up work.

### 3. Preserve original records during those actions

Six Media result states and the Expenses committed result use the existing RetainedExecutionStore instead of plain remember. This preserves the original ExecutionResult/FULL through ordinary recreation without changing media catalogue/ledger persistence or acquisition algorithms. It does not qualify interrupted microphone/provider/import work or durable recovery after process death.

SVG uses the same retained original and no longer calls execute to reconstruct an execution from saved selection/event/completion-time display values. If saved Commit state exists without its original record, the existing explicit recovery panel prevents export/confirmation until a new run is chosen. Selection geometry and public output fields are unchanged.

Geocaching's common surface now retains one original execution per frozen snapshot in a module-local ViewModel store. An equal restored snapshot does not rerun its result factory. A changed explicit snapshot may produce a new record. If a previously committed record is unavailable, the saved snapshot stays reviewable and there is no hidden reconstruction or fallback Done; an explicit Commit snapshot as new record is required. This is not fresh GPS acquisition or restoration of the old execution identity. Persistent field ledgers, account/provider state, GPS/navigation/maps and remote sync are untouched.

### 4. Workbench contract visibility without expanded execution authority

Home's CapabilityCard previously hid its read-only ODK / return contract and Copy ODK intent controls solely on Workbench classification. Those inspection controls now remain available. The Workbench classification includes destructive provisioning/install tools and protected issuer inspection, so this pass deliberately does NOT blanket-enable Save preset, Run + save, scheduling or unattended execution. Existing authoring/host restrictions remain. Capability-specific eligibility metadata/policy is still an open review item, not reported fixed.

## Verification and exact limits

Both debug app and Android-test APK builds pass on the final implementation. No dependency/version/manifest/form/public ID/output-contract changes are made in this increment. An initial newly written test-source escaping error was corrected before the successful focused runs; it was not a production compile failure.

27 focused JVM tests pass: ten action single-flight/permission/retry/cancellation/pending-finish/wiring/Workbench policy cases; three Geocaching frozen-record cases; four retained-store, five registry parity and five native closeout policy regressions. The registry tests still enumerate all 80 modules / 381 capabilities. This is not a full-suite result and does not supersede historical baseline failures.

23 final Android cases have been verified across three targeted invocations on a temporary read-only API 37 ARM emulator:

- Initial 21-case selection: 19 passed, two actual-screen fixture cases timed out. Shared audit off/on, real Downloads bytes/stems/optional metadata, intermediate/final/failure gating, controlled slow storage, existing Weather/Network/Signals actions and three shared Share cases passed.
- Two-case diagnostic rerun: still two timeouts. Diagnostics identified missing normal host scrolling and Compose Dialog replacing LocalContext. In the latter case the fixture's context-only share-denial interceptor was bypassed and Android opened its sharesheet; no recipient was chosen and no message was sent.
- Final four-case selection: four passed after fixing host scrolling, limiting actual-screen failure injection to Media/Expenses, separately monitoring SVG dialog chooser dispatch, and adding Geocaching boundary retention. There was no whole-selection rerun or blanket clean 23/23 claim.

The shared slow-save test blocks the actual provider insert off the UI thread, completes a main-thread Activity callback and recreation while it is blocked, checks disabled duplicate/finish controls, releases storage, verifies one write and original-result confirmation, then recreates again without replay. The shared component tests retain audit state/error status and verify canonical text/clipboard/sidecar and saved metadata.

Actual Media library-state, Expenses and SVG screens use injected synthetic frozen records, then verify exact original/FULL through rotation and Done. They do not perform real ledger edits, catalogue imports, audio recording or polygon acquisition. Actual Media/Expenses final-share failures remain unconfirmed through recreation. SVG's actual native preset dialog has no bypass Done and confirms the original only after intercepted chooser dispatch. The shared component/source checks cover failure gating; failed chooser launch is not separately injected through that Dialog context. The Geocaching test exercises the real common MethodSurface with a synthetic factory, proving one factory call through recreation/share/confirmation, not GPS/maps/provider/hardware acceptance.

Fixtures delete only their own generated Downloads rows, sidecars and synthetic source files. No phone install, network provider call, real receiver consumption, OS process-kill or legacy API 27/28 permission acceptance is claimed. The dedicated emulator was shut down after verification. APK: app/build/outputs/apk/debug/app-debug.apk.

## Protected boundaries / outstanding queue

NFC, attestation, trusted timestamp, signing and clinical/Sentinel implementation sources are not edited. Existing unrelated signing edits and the dirty checkout are preserved. No commit, push, tag or release. Shared external-return envelopes, FULL, attachment/grant contracts, preset log policy and persistent domain stores are unchanged.

Continue with remaining module-owned exports/closeout and asynchronous large-file work, actual interrupted acquisition/provider/runtime-input cases, capability-specific Workbench authoring eligibility, large saved-state/log limits and actual legacy storage/receiver/Collect/hardware acceptance. In particular, Barcode scan/Compass custom result actions and other Astronomy surfaces still need their dedicated parity/fault review. All modules remain in scope; this handoff closes only the bounded increment above.
