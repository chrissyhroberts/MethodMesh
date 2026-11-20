# Shared Downloads / Signals / Weather / Network — 24 October 2026

Active checkout: /Users/icrucrob/AndroidStudioProjects/MethodMesh; branch feature/esp-mesh-transport; canonical Master Book v1.31 FINAL. The user requested multiple audit/remediation steps in one round. This is a bounded engineering continuation, not all-module or clinical certification.

## 1. Shared legacy Downloads access

The shared Save to Downloads path wrote public storage on supported API 27/28 without obtaining the runtime permission. A permission preflight now runs before any result file is created. Already-granted access proceeds; API 29+ bypasses the legacy permission check entirely.

When access is missing, shared transport opens a private, non-exported permission-only Activity and returns an explicit no-files-saved/retry status to the original screen. That Activity explains access, offers the system permission request or app settings after prior non-rationale denial, and always offers Back to result. Denial history is recorded after a denial response and cleared after access is granted. Permission denial/back/recreation cannot automatically save or confirm a result: no execution, file URI, save callback or pending export is carried into the Activity. After granting access the user returns and explicitly retries Save. This avoids replaying a stale or different committed record after a delayed permission response.

The approach was cross-checked against Android's [runtime permission guidance](https://developer.android.com/training/permissions/requesting) and [shared-storage guidance](https://developer.android.com/training/data-storage/shared/media). No new permission is declared; the existing WRITE_EXTERNAL_STORAGE declaration remains limited to API 28. No broad all-files access is introduced.

The API 27/28 behavior is policy/source/build checked, NOT run on a legacy OS: only API 37 ARM and API 34 x86 system images are installed here. No legacy image was downloaded and no physical phone was used. Native allow/deny/permanent-denial/settings/rotation/revocation acceptance on Android 8.1/9 remains required. Modern Android's no-permission/no-save bypass is exercised on the emulator.

## 2. Weather and Network committed actions

Weather's module-owned ACTION_SEND text-only chooser is replaced by canonical ResultShare. Useful text is equivalent in EXTRA_TEXT and a readable, coherently named text/plain sidecar. Optional canonical FULL audit is appended after the metadata.json marker; no separate debug JSON stream is added. Copy uses the same text construction. Native Save retains separate optional metadata-file semantics.

Weather and Network previously applied preset result actions whenever the native-preset flag was present, including intermediate steps. Their Done handlers AND labels now apply Share/Save-and-finish only on the last step. Intermediate completion delegates the original result to its owner without Share, Save or Home navigation. Actual-host Home/logging order and failed Share/Save remain verified. No Weather provider, network diagnostic, location acquisition or scientific calculation changes are made.

## 3. Signals committed export consistency

All 15 committed-card call sites covering 17 declared Signals capabilities, including sensor scope, now delegate export policy to one Signals-local action bay. The exact retained execution and existing primary value/file selection are passed in; no replacement execution is constructed for native actions. Individual value tap-to-copy remains scalar.

The bay has an explicit execution-keyed Include full JSON / audit toggle, off by default, a generic Copy action, Share, Save to Downloads and origin-aware Done/Share-and-finish/Save-and-finish. Canonical FULL audit is optional rather than unconditionally persisted. Generic Copy/Share use equivalent useful text plus optional audit; native Share uses the shared readable sidecar, ClipData and read-only grants. Former raw field-map dumps on several transmit/optical surfaces are replaced by the already displayed primary result; technical telemetry remains visible and available in FULL audit.

QR/QR Burst received-file exports retain actual file URIs/bytes and their useful verification text. Changing audit changes only text/metadata, not the media set. Open received file remains a distinct existing action. The generic Downloads exporter gains an optional MIME-hint map so a declared received-file type can supply a useful extension/type when resolver metadata is generic; specific resolver metadata stays authoritative. Other callers retain the default empty hint map and existing signature arguments.

Signals preset SHARE previously behaved like Home without sharing. The common closeout boundary now requires successful Share before confirmation, just as SAVE requires successful persistence. Failed actions leave the committed record available. Intermediate/external completion does not perform final preset exports. Denial, retry and success do not rewrite the retained execution.

No codecs, packet/FEC/CRC/SHA-256 meanings, timing/power profiles, physical acquisition/transmission, verification strengths or public capability contracts are changed. The unused per-screen FULL renderings were removed after consolidating native actions. A misleading shared action-bay caption claiming debug JSON was a separate Share attachment was also corrected.

## Verification

Both debug app and instrumentation APK builds pass. git diff --check passes.

**49 focused JVM cases pass:** eight new legacy access/retry/revocation/private-Activity wiring cases; four new multi-module export/15-site wiring cases; seven existing Signals state/16-contract cases; sixteen Paper projection/commit/export cases; four retained-store, five app-wide registry/contract parity and five native closeout policy cases. The same bounded selection passes after the final label/permission-history correction. This is not a new broad-suite result and does not supersede historical baseline failures.

**40 distinct Android cases verified across two runs.** The initial 40-case run had 39 passes and one genuine remaining UI-label failure: intermediate Weather/Network SHARE still displayed Share and finish. Its handler had already been guarded, but the test could not find Done. Both labels were corrected, the unit source check strengthened, and only the intermediate, final Share, and modern permission-bypass cases were rerun; all three pass. There was no whole-suite test/fix/rerun loop.

Coverage is:

- Ten new committed-component/transport cases: Weather/Network/Signals text and Signals file audit off/on, exact message/sidecar/clipboard consistency, coherent real Downloads bytes/names and optional metadata, received-file .bin→declared PDF handling, specific provider MIME winning over conflicting hints, audit state through recreation, six Weather/Network intermediate action variants, final preset Share and denied Share/Save across all three modules, plus private permission-Activity modern bypass. These use synthetic frozen records with actual committed components, intercepted choosers and MediaStore/FileProvider bytes—not real capture/provider sessions.
- Eight actual screen-only QR/QR Burst transmitter lifecycle cases still pass: exact original object/FULL through recreation and Done, explicit same-value recommit with a new identity, missing-retained-record recovery and automatic return without replay.
- Five Signals closeout cases pass; the Home/Share case now supplies and checks a successful Share callback rather than expecting SHARE to skip the action. Three shared Share and two shared Downloads success/rollback cases pass.
- Two Weather/Network failed-closeout and two actual-host Home/logging cases pass. Eight existing Paper committed-export cases pass with their already documented synthetic-capture and API 37 YAML-label limits.

Tests clean only test-created source files, text sidecars and Downloads rows; no user files or existing artifact collections are removed. The dedicated read-only API 37 emulator was shut down. Nothing was installed on a physical phone. APK: app/build/outputs/apk/debug/app-debug.apk.

## Protected boundaries and remaining work

NFC, attestation, trusted timestamps, signing and clinical/Sentinel implementation sources are not edited. Existing signing changes and the dirty checkout are preserved. App/capability versions, public method/output/settings IDs, external/ODK attachment/FULL contracts, preset log policy and Paper schema/workspace stores are unchanged. No commit, push, tag or release.

Still required:

1. Actual legacy Android storage permission/Settings/deny/cancel/recreation acceptance and real recipient/storage-provider behavior; API 37 can still normalize saved YAML MIME despite the correct request and intact suffix/bytes.
2. Physical Signals receiver pairing, microphone/camera/torch/contact interruption and large active-frame state. The screen-only transmitter checks are not hardware/channel certification.
3. Real Paper/barcode/redaction capture/receiver/Collect acceptance, other Astronomy/provider cancellation/runtime inputs, remaining per-module closeout/UI review and capability-specific Workbench policy.
4. Large saved-state/return/log limits and main-thread large-file export responsiveness. Shared writes stream bytes but this pass does not make all native saves asynchronous or cancellable.
5. All 80 modules / 381 capabilities remain in scope; actual clinical/Collect/Sentinel acceptance is separate from controlled engineering fixtures.
