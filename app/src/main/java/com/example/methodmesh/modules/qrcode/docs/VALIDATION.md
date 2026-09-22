# barcode module validation

Review date: 2026-09-22<br>
Authority: MethodMesh Master Book v1.22 (2026-09-15).

## Pass A — contract correctness

- [x] Historical module ID remains `barcode`.
- [x] Established `barcode.scan` method remains present at version `1.1.2` with its existing inputs/outputs/evidence recipe.
- [x] Existing `BarcodeScanCapabilityScreen` is included in the handoff; the earlier missing-source packaging defect is removed.
- [x] `barcode.generate` is independently registered with its own method, screen, settings, RIL bindings and ODK showcase.
- [x] `barcode.clone` is independently registered with its own method, screen, settings, RIL bindings and ODK showcase.
- [x] Direct/dashboard discovery, presets/protocol discovery and ODK all derive from the same registered method/screen metadata rather than dashboard-only implementations.
- [x] Exactly one module maturity tag (`Development`) and one connectivity tag (`Offline`) are declared. The established scanner method remains Production; new Generate/Clone methods remain Development until receiving-repository build and device gates pass.

## Pass B — interaction quality

- [x] Scan remains scanner-first with live result -> Commit.
- [x] Generate is renderer-first with immediate code updates and no redundant Generate button.
- [x] Clone opens in the standard host with a windowed embedded ZXing scanner, then shows the renderer below it with swipe/rail/cycle format selection.
- [x] Generate and Clone never silently truncate, pad, normalise or rewrite the payload for a symbology.
- [x] Fixed format settings remain fixed; incompatible fixed formats fail visibly and disable Share/Copy/Save/Return rather than silently substituting another format.
- [x] Fixed auto-cycle settings are not exposed as runtime toggle questions.
- [x] Useful displayed payload text is tap-to-copy/copyable.
- [x] Clone has no visible Commit gate: Share/Copy/Save/Return atomically snapshot the current payload/format choice and reuse that snapshot until live state changes.
- [x] Generate preserves committed identity; Clone preserves its invisible action-snapshot execution, observation, transformation, relationship and system-time identity across ordinary recreation.
- [x] Generator QR branding is automatic MethodMesh presentation; arbitrary custom-logo controls are absent.
- [x] Clone renderings stay unbranded for fidelity.

## Pass C — field robustness

- [x] Core scanning/generation/cloning is offline.
- [x] Camera permission remains confined to scan/clone acquisition.
- [x] No private barcode history/wallet store was introduced.
- [x] Full-screen presentation restores prior brightness/keep-awake state on exit.
- [x] Android Share includes a PNG rendering for Generate/Clone; Clone also declares its PNG as the canonical primary/core return.
- [x] A standalone Kotlin parser pass reports no syntax/parser/redeclaration diagnostics across the five module source files.
- [ ] Receiving-repository Android build and physical-device scan tests are still required; this standalone module folder is not a complete Gradle project and therefore cannot resolve Android/Compose/ZXing host dependencies for a truthful app compile here.

## ODK/XLSForm inventory

- [x] `example_odk_barcode_scan.xlsx` retained with stable `form_id=barcode_scan`.
- [x] `example_odk_showcase_barcode_scan.xlsx` retained with stable `form_id=qrcode_barcode_scan`.
- [x] `example_odk_showcase_barcode_generate.xlsx` added with `form_id=barcode_generate`.
- [x] `example_odk_showcase_barcode_clone.xlsx` added with `form_id=barcode_clone`.
- [x] Each workbook contains exactly one MethodMesh invocation.
- [x] Canonical examples use unprefixed canonical return field names and no `methodmesh_return_namespace`.
- [x] All canonical examples request `input_payload_mode='FULL'`, use `return_mode='flat'`, and capture `methodmesh_status` plus `methodmesh_full_json`.
- [x] Generator/clone forms capture only outputs actually declared by their runtime contracts; no speculative image/print return field is invented.
- [x] Workbook node names are unique within each canonical workbook.

The workbooks were generated and re-inspected through the required spreadsheet artifact workflow. Provider-side ODK Central/Kobo upload and pyxform validation were not available in this standalone module workspace and are therefore not claimed.

## Receiving-repository gates

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then exercise:

1. scan: live code A -> code B -> Commit; tap/copy/share/save; rotate before and after Commit;
2. generate: free text, URL, numeric retail code, incompatible fixed format, swipe/Cycle, QR MethodMesh mark, full-screen, Share PNG;
3. clone: open with standard host/preset access, embedded scanner in portrait and landscape, horizontal screen-relative aim line, QR and linear sources, SOURCE mode, cross-format swipe, incompatible fixed target, full-screen presentation, Share/Copy/Save without a Commit step, Return clone;
4. verify every generated/cloned symbol with at least two independent scanners where practical;
5. verify generator branding does not materially reduce QR reliability at representative payload lengths;
6. exercise direct, preset, protocol/schedule/widget origins as applicable;
7. roundtrip all three canonical showcase XLSForms through ODK; confirm every declared output, `methodmesh_status`, `methodmesh_full_json`, Clone **Return clone**/Cancel behaviour and Scan/Generate Commit/Cancel behaviour;
8. upload the canonical workbooks unchanged to ODK Central and Kobo where supported.

## Handoff check

This handoff is one `qrcode/` module root. It contains only module code and module-owned `docs/`; no `app/` wrapper, whole repository tree, build outputs or unrelated modules.


## v1.7 native artefact / preset contract

- [x] `barcode.clone` now owns a complete post-Commit native lifecycle: Share image, Copy image, Save image, optional JSON sidecar, Technical details, origin-aware Home/Done and Clone another.
- [x] `barcode.generate` uses the same image-first Share/Copy/Save rule.
- [x] Share/Copy/Save never create a payload `.txt` file for Generate/Clone. The payload remains available in the canonical result and as a secondary tap-to-copy scalar.
- [x] Native Share uses PNG as the first/primary attachment; optional metadata is a real `.json` sidecar attachment rather than JSON appended to a text share payload.
- [x] Native Save writes PNG plus optional `metadata.json`; no result text file is emitted.
- [x] Native Copy places the PNG URI on the Android clipboard.
- [x] Native presets honour `HOME`, `SHARE`, and `SAVE` result actions.
- [x] Native preset `CORE` uses image only; `AUDIT` / `FULL` request the corresponding JSON metadata sidecar for Share/Save.
- [x] ODK/external Commit remains transport-first and bypasses native Share/Save; canonical scalar fields plus `methodmesh_full_json` remain the ODK contract.
- [x] Clone/Generate method descriptor metadata now advertises the image-first native artefact contract and preset result/payload policies.
- [x] Existing scanner source, scanner contracts, legacy scanner XLSForm and canonical scanner showcase remain present.
- [x] Existing Generate/Clone XLSForms remain single-call, FULL + flat, unprefixed, and capture `methodmesh_status` / `methodmesh_full_json`; no speculative image return field is introduced.

Receiving-repository gates remain mandatory:

```text
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```


## Clone binary return v1.0.2

- [x] `barcode_clone_image_uri` is a declared canonical output and the clone core return.
- [x] The first Share/Copy/Save/Return action materialises one stable caller-readable PNG URI and reuses that action snapshot while live state is unchanged.
- [x] ODK canonical showcase uses an `image` return field for `barcode_clone_image_uri`.
- [x] `barcode_return_text_payload` independently controls the scalar text return; it does not change barcode rendering.
- [x] Canonical ODK showcase defaults the text return to `true`, demonstrating PNG + exact payload + FULL JSON.
- [x] Native Share/Copy are image-first and default to no payload text; payload text can be included without creating a `.txt` sidecar.
- [x] Native Save writes the PNG and optional JSON sidecar only.


## Clone interaction v1.0.3

- [x] Clone no longer requests `CapabilityHostPresentation.Immersive`; standard MethodMesh preamble/preset authoring remains reachable.
- [x] Clone no longer auto-launches the external ZXing capture activity on entry.
- [x] Camera capture is embedded in a window at the top of the capability.
- [x] Scanner window follows device orientation; the screen-relative aim/horizon line remains horizontal in portrait and landscape.
- [x] First decode pauses the embedded camera; **Scan another** resumes it.
- [x] No visible Commit button is used for Clone. Share/Copy/Save/Return are explicit finalization actions and atomically snapshot the current live clone.
- [x] Automatic-return callers use **Return clone** as the finalization/return action; native Share/Save UI remains suppressed there.

## v2.0 capability-owned preset authoring

- Scan, Generate and Clone each expose **Save current setup as preset** from their own native capability screen.
- Preset authoring uses the canonical `CapabilityPreset` / `ProtocolLibraryRepository` store; there is no barcode-private preset format.
- Every typed capability setting can be marked **Fixed** or **Ask when run**. Runtime fields are stored through `methodmesh_runtime_fields`.
- Generate defaults `barcode_payload` to runtime so a one-off token is not silently persisted; the operator can deliberately make it fixed for a reusable card/token preset.
- Authoring exposes returned-data scope (**Result / + Audit / + Full JSON**) and completion (**Return / Share / Save**) independently.
- Barcode presets are operator-facing and reopen the same scanner/generator/clone instrument; a preset run never substitutes a generic settings-only implementation.


## v2.0.1 preset discoverability correction

- [x] Generator preset authoring is in the top preamble rather than below the payload/format controls.
- [x] Scan and Clone preset authoring is likewise placed before the scanner instrument for consistent discoverability.
- [x] `barcode.generate` version bumped to 1.0.3; scanner and clone contracts/versions are unchanged.
- [x] XLSForms are unchanged by this UI-only preset-authoring correction.
