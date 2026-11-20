# Shared result-screen preset completion — 28 October 2026

This continuation fixes the common CapabilityScreenScaffold result controls in /Users/icrucrob/AndroidStudioProjects/MethodMesh, against Master Book v1.31 and its current file-bearing Share erratum. It follows ASTRONOMY_AUTO_WORKFLOWS_2026-10-28.md and resolves that checkpoint's shared-scaffold closeout caveat. The prior dirty checkout and module work are preserved.

## Confirmed defects and remediation

1. A final native preset configured to Share could close/return Home without dispatching a chooser. The shared result screen now delegates Share and finish to the existing CanonicalCommittedResultActions handler; confirmation follows successful chooser dispatch. Dispatch failure stays on the original result and requires an explicit retry.
2. Shared-screen manual/preset Downloads saves ran synchronously. They now use the existing single-flight IO worker, application-context storage access and legacy permission preflight. Final Save confirms only after the complete save succeeds. Manual Save/Share keep the result open.
3. Back/Retry/Cancel could race completion or storage work. The shared screen now receives the action handler's synchronous busy signal and disables/guards those controls while an operation or completion is pending. Existing action state survives Activity recreation; completion is consumed once by the current Activity, never an old callback retained in an IO worker.
4. The screen retained a direct Home fallback when hostOwnsNativePresetCloseout was false. It now always delegates completion to its owner, preserving confirmation/logging/return order. No shared-screen path starts MainActivity itself.
5. An empty result preview used an alternate Done path that could bypass the selected preset action. All completed manual results now use the same gated action controls, including empty previews; an empty Share fails visibly instead of confirming success.
6. Final preset labels now match the action: Share and finish, Save and finish, or the existing Done/Home destination. Intermediate steps retain Continue without an implicit final export. Automatic external/dependency returns bypass manual exports and the shared automatic callback is guarded by original execution ID across recreation.
7. Canonical result actions now forward the existing request recipient hint to ResultShare. Two optional presentation labels let this scaffold retain its task-specific Share result/document/image/file wording and its final/Continue labels; existing module callers retain their defaults.

The shared screen keeps its existing user-facing result projection, details, tap-to-copy values, selected media, and compiler delivery-ZIP precedence. It passes these presentation overrides to the existing action handler; canonical FULL still comes from the unchanged original ExecutionResult. No method is re-executed and no result envelope is synthesized for export/completion. The audit option remains off by default per new result and survives rotation; native Share appends optional audit to text, while Downloads uses optional metadata.json.

Obsolete, unreachable export-package UI and the duplicate synchronous result-action implementation were removed from the scaffold. There are no new module-specific branches in the framework.

## Verification

Both debug app and Android-test APK builds pass. All 69 focused JVM checks pass, zero failures/errors/skips:

- 10 committed-action state cases.
- 4 retained-original store cases.
- 5 native closeout-policy cases.
- 5 existing Barcode/Compass integration-wiring cases.
- 5 registry-parity cases (80 modules / 381 capabilities).
- 5 ResultShare cases.
- 8 legacy Downloads permission/access policy cases.
- 27 NFC contract/evidence/credential/overwrite/one-shot/protocol/compiler cases.

Final Android run: all 69 cases passed in one clean run on the disposable read-only API 37 ARM emulator, zero failed/skipped:

- 10 new shared-scaffold UI/action cases.
- 7 actual native Home/confirmation/logging cases.
- 14 Astronomy automatic-screen lifecycle/input cases.
- 9 Barcode/Compass result-action cases.
- 20 existing committed-action contract cases spanning controlled canonical/module boundaries and actual module screens.
- 3 actual separate-UID PDF/OCR/ZIP delivery cases.
- 3 native Share contract cases.
- 3 module export-failure cases.

An initial new-tests-only run passed eight of ten. The remaining two tests hit the same fixture helper mistake: it tried to find a clickable parent for the non-clickable audit label. The scrolling helper was corrected without changing production behavior; the complete final 69-case run passed. This is a focused regression run, not a new full-suite certification or replacement of recorded baseline failures.

The new screen tests use the production scaffold/action handler with synthetic original records and controlled chooser/Downloads contexts. Storage assertions verify worker-thread execution, publication before confirmation, exact original identity, current-Activity confirmation after recreation, no automatic failed-action replay and no duplicate finish. The delivery-bundle test uses synthetic ZIP/PDF bytes and the real registered NFC compiler ID, not a real compiled form or live timestamp service.

## Sharing and protected boundaries

ResultShare.kt was not edited. Its SHA-256 remains:

6fbfc7a405ead303a0b1cba27fbc3012416ab10384b015350bc62eedd610015e

The working file-first scanner/timestamp/WhatsApp transport correction remains intact. Useful files stay real streams; audit does not introduce a competing JSON stream or EXTRA_TEXT on file-bearing shares. Compiler delivery ZIP remains the selected domain artifact, without fanning out constituent files.

NFC credential/PIN/provisioning/write verification, Sentinel/clinical-trial source contracts, digital-signing and timestamp implementations/source forms were not edited. NFC screens do use this shared result surface, so the change deliberately includes their contract regression suite and a controlled compiler delivery-bundle projection check. These checks do not replace real-card/Collect/Sentinel acceptance.

## Remaining limits / next review

- Successful Share means the OS chooser was dispatched, not that a recipient accepted, sent or saved the payload. Cancelling the chooser is not a delivery acknowledgement.
- Activity recreation and controlled missing/failing operations are tested; real OS process death during export is not a resumable transactional save guarantee.
- Actual API 27/28 permission/Settings/storage-provider acceptance remains open; policy tests and modern-emulator behavior do not certify the legacy OS flow.
- Real native preset/protocol/ODK/Collect recipients, WhatsApp, live TSA, physical NFC/cards, other module-owned closeout implementations and storage-provider faults still need acceptance. No blanket all-module/runtime certification is claimed.
- Remaining Astronomy camera/sensor/polar/NASA/SAF/session work, provider/permission acceptance, large active-session/return/log payload limits and the broader module scorecard remain active.
- Disposable emulator only; no physical phone installation, commit, push, tag or release.
