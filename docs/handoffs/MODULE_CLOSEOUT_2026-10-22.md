# Module closeout review — 22 October 2026

Focused continuation in `/Users/icrucrob/AndroidStudioProjects/MethodMesh`, branch `feature/esp-mesh-transport`. Master Book v1.31 remains authoritative, especially Commit, explicit persistence and origin-aware closeout. This is not an all-module runtime certification.

## Confirmed defects and changes

- Compass, shared Signals closeout, all three Barcode screens and both Paper Bridge screens could open task-clearing Home themselves, bypassing the workflow host's confirmation-owned log attempt. These branches now honour `hostOwnsNativePresetCloseout`, following the previously corrected scaffold/Conversions/Weather/Network pattern. The host still decides Home versus caller/protocol/schedule/widget return. Legacy non-owning-host fallbacks are preserved, not globally removed.
- Compass and Paper Bridge Save-and-finish swallowed failures and confirmed anyway. Their save functions now return explicit success; failure keeps the committed result open with an action error and without confirmation.
- Signals' shared closeout accepted a Unit save callback and always confirmed. Checked text/media outcomes now gate confirmation, and every caller displays failure beside the committed actions. Existing manual-save string helpers remain compatible wrappers.
- Both QR Burst screens omitted the Save-and-finish callback altogether. It is now required by the helper signature, and both screens supply the text/file save. Transmit actions now display save/share failures instead of discarding them. Received QR text is included in Save-and-finish just as in ordinary Save.
- Barcode scan applied final-preset export/Home logic to intermediate steps, disabled the audit toggle for Save-and-finish, and could open Home after Save rather than confirm. Final closeout is now last-step-only, native Save honours the selected audit option and successful Save confirms through the host.

No automatic persistence on Commit, canonical execution field changes, method/setting/output ID changes, external-return contract changes or version bumps. No signal codec, credential, scanner or signing operation is rewritten.

## Verification and limits

- Both debug APKs build. The final Android rerun rebuilt the app/instrumentation after the QR Burst status-reset change.
- **13/13 focused JVM cases passed:** five closeout routing, five all-module registry/contract parity, one Barcode evidence and two Barcode format cases.
- **14 focused Android cases verified across two runs.** The initial 14-case run passed 13; the new Barcode Home fixture alone timed out waiting for an off-screen control before scrolling. Removing that redundant wait let the existing scrolling click helper handle it; the one-case rerun passed. No repeated full-suite rerun or receiver-specific production workaround.
- Android coverage: five Signals helper cases (host delegation with identical frozen evidence, denied text/media storage, save-before-confirm ordering, intermediate/external return, launcher return); six real-screen/log-before-Home cases including Barcode generator; three module failure cases exercising denied Save and Share for Weather, Network and the actual Barcode generator UI.
- Signals success ordering uses a synthetic successful callback; denied storage uses the real Downloads implementation. Hardware/QR Burst sessions were not exercised on-air. Compass/Paper Bridge failure gates and Barcode scan/clone Home edits are source/build checked, not individually fault-injected on screen. Complete rotation/process-death correctness is not claimed.
- API 37 disposable read-only Pixel 7a emulator only. No connected physical phone, real message receiver, live provider, Collect/Enketo, physical NFC card or Sentinel workflow was exercised.
- `git diff --check` passes. Protected NFC/attestation/trusted-timestamp production trees have no diff. Existing signing production diff remains the prior six-file 102/0, 9/1, 13/2, 11/0, 13/0, 5/1 baseline. No commit, push, tag or release.

## Still open / next focused pass

Later 22 October continuation: Compass and `signal.sensor.scope` now retain exact committed executions and reject fabricated missing-envelope recovery; see `SENSOR_COMMIT_RETENTION_2026-10-22.md` for the 14 JVM/13 Android checks and limits. The remaining Signals transmit/receive, Paper Bridge and Barcode reconstruction paths below are still pending.

1. **Committed evidence on recreation:** Compass, Signals and Paper Bridge still have saved-value-to-new-envelope reconstruction paths; Barcode reconstructs using saved identity/timing fields. Audit exact FULL evidence and lost-envelope recovery rather than assuming Home fixes also solve lifecycle integrity.
2. **Module Share/Save conformance:** Signals and Weather/Network include module-owned text sharing instead of the shared text-sidecar projection. Signals' native audit controls and Paper Bridge's internal Files save destination need reconciliation with the Master Book's default-off audit and canonical Downloads rules. Paper Bridge workspace/template authoring saves are a separate intentional operation. This increment preserves existing persistence destinations and does not claim them compliant.
3. Complete fault-injection/real UI coverage for Compass, Paper Bridge and Barcode scan/clone, then actual origin/provider/receiver and physical-device acceptance. Workbench authoring policy, other Astronomy/provider screens, large state/log limits and broader per-module usability remain open.

The user's NFC/clinical-trial/Sentinel boundary in `APP_WIDE_ENGINEERING_REVIEW_2026-10-20.md` continues to apply. Shared routing changes require real deployment acceptance even when protected module sources are unchanged.
