# qrcode v1.20 refresh validation

Review date: 2026-09-12

Authority: MethodMesh Master Book v1.20 (2026-09-12).

## Contract preservation

- [x] Module ID remains `barcode`.
- [x] Canonical method ID remains `barcode.scan`.
- [x] Canonical setting key remains `barcode_formats`.
- [x] All nine established capability output fields remain declared with unchanged names and meanings.
- [x] Historical `token` payload alias is retained.
- [x] Evidence recipe remains `barcode_payload_utf8_sha256_v1`.
- [x] Method implementation version advanced from `1.1.1` to `1.1.2`; this is an implementation/provenance increment, not a contract rename.

## Current status metadata

- [x] Maturity: `Production`.
- [x] Connectivity: `Offline` for the core scan/decode operation.
- [x] Capability descriptor exposes `maturity`, `connectivity`, `interactive`, `core_return` and `odk_metadata_return` metadata.
- [x] Module-owned `maturityTag` / `connectivityTag` values follow the currently reviewed-module pattern while the host `MethodMeshModule` interface still lacks typed status properties.

## Native UX review

- [x] Camera scanner is the dominant capability instrument.
- [x] Continuous scanning produces an in-place working result.
- [x] Current payload is displayed as a floating HUD over the camera.
- [x] Current payload is directly tap-to-copy.
- [x] Safe HTTP/HTTPS payloads show **Open link** directly in the live HUD beside **Commit**.
- [x] Scanned links are never auto-opened.
- [x] Commit freezes the canonical result rather than navigating to a generic result page.
- [x] Manual committed state remains on the same capability surface.
- [x] Post-Commit Share, Copy, Save to Downloads, optional full JSON/audit, Technical details, Home/Done and New scan are available.
- [x] Format selection is compact/progressive rather than a full stack of large configuration buttons.
- [x] Fixed preset settings remain suppressible through `settingShouldBeShown`.
- [x] Working and committed execution identity/state remain saveable across ordinary activity recreation.
- [x] Automatic-return origins return only on Commit.

## Canonical surface parity

- [x] `QrCodeModule.as100Methods()` exposes the canonical method.
- [x] `capabilityScreens()` exposes the direct native surface.
- [x] `capabilitySettings()` exposes the same setting contract to presets/protocol configuration.
- [x] RIL/discovery bindings still resolve to `barcode.scan`.
- [x] No dashboard-only, preset-only or ODK-only scanner implementation was introduced.
- [x] Schedule/widget use remains through the same method/preset/closeout framework.

## ODK/XLSForm review

- [x] Canonical `example_odk_showcase_barcode_scan.xlsx` contains exactly one `barcode.scan` invocation.
- [x] Canonical showcase uses unprefixed canonical return keys and no return namespace.
- [x] Canonical showcase requests `input_payload_mode='FULL'` and `return_mode='flat'`.
- [x] Canonical showcase captures `methodmesh_status` and `methodmesh_full_json`.
- [x] Canonical showcase captures every declared barcode capability output, with URL conditionality represented in the form.
- [x] Legacy `example_odk_barcode_scan.xlsx` preserves its stable `form_id` and has been reconciled to the same single-call/unprefixed contract.
- [x] ODK Integration Card in `README_QrCode.md` matches the method/XLSForms.
- [x] No binary/file return exists for this capability, so attachment transport is not applicable.

## Spreadsheet verification performed

The two supplied XLSForms were imported, rewritten and re-inspected using the MethodMesh artifact workflow. Key survey/settings ranges were checked after modification. Their stable `form_id` values were preserved:

- showcase: `qrcode_barcode_scan`;
- legacy: `barcode_scan`.

Provider-side ODK Central/Kobo upload validation was not available in this standalone module workspace.

## Generated XML projections

The previously supplied `.xml` XForm projections were generated copies of older XLSForm state and were stale relative to the refreshed workbook contract. They are removed from this handoff rather than retained as competing documentation/projections. The `.xlsx` files are the module-owned source examples.

## Build/test status

The supplied task archive contains only the module folder, not a complete Gradle project, so a truthful `:app:assembleDebug` / unit-test build cannot be executed from this workspace.

The current host repository contracts were checked against the module shape before editing. No shared-framework patch is required by this refresh.

Required receiving-repository checks:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then exercise on device:

1. direct native scan -> live HUD -> tap payload to copy -> Commit;
2. HTTP/HTTPS scan -> **Open link** is visible inside the live HUD; non-URL -> action absent;
3. scan code A then code B before Commit -> B replaces the working result;
4. rotate portrait/landscape before and after Commit;
5. camera permission deny/retry path;
6. preset with fixed `barcode_formats` and preset with runtime `barcode_formats`;
7. protocol and schedule automatic-return closeout;
8. widget preset closeout where applicable;
9. canonical ODK workbook roundtrip, including every declared output, `methodmesh_status` and `methodmesh_full_json`;
10. upload/validate canonical XLSForm unchanged in ODK Central and Kobo.

No production build/device validation claim is made by this standalone handoff until those receiving-repository checks pass.
