# qrcode v1.05 migration validation

Review date: 2026-09-06

Authority: MethodMesh Master Book v1.05 and MethodMesh v1.05 Module Review and Refresh Manual.

## Contract review

- Canonical method ID remains `barcode.scan`.
- Module ID remains `barcode`.
- Canonical setting key remains `barcode_formats`.
- All nine established method output fields remain declared with unchanged names.
- `QrCodeModule.as100Methods()`, `capabilityScreens()` and `capabilitySettings()` expose the same capability to generic discovery, so dashboard/direct/preset/protocol/ODK surfaces do not require separate implementations.
- ODK example requests every declared scanner output plus `methodmesh_full_json` through the generic FULL payload projection.
- Return namespace use is demonstrated without changing canonical output names.

## v1.05 UX/state review

Implemented in the module screen:

- purpose-built embedded scanner UI;
- scanner camera is contained in a bounded on-screen window rather than a separate full-screen capture activity for normal capability use;
- scanner layout is orientation-aware and is not portrait-locked; the ZXing viewfinder line remains horizontal after rotation;
- continuous decoding keeps the latest working payload directly beneath the camera window;
- URL payloads expose an explicit **Open link** action while never auto-opening links;
- camera operation remains an explicit physical action;
- captured payload is shown as a live/current working result on the same screen;
- explicit Commit freezes the execution result;
- post-Commit native actions remain on the same screen;
- useful displayed scalar/text values are tappable and copy only the value;
- technical/audit material is progressively disclosed;
- ODK/protocol/schedule automatic-return origins return only after explicit Commit;
- fixed native-preset `barcode_formats` is hidden;
- runtime preset format configuration prevents premature auto-launch;
- scanner cancellation does not silently cancel the surrounding workflow; explicit Cancel remains available;
- working and committed fields are `rememberSaveable`;
- execution, observation, transformation and relationship IDs plus the system timestamp are preserved when rebuilding saved state;
- Commit does not automatically persist an archive copy.

## ODK workbook review

`example_odk_barcode.scan.xlsx` is intended to contain one grouped call to `barcode.scan`, with:

- `input_barcode_formats`;
- `input_payload_mode='FULL'`;
- `input_methodmesh_return_namespace='scan'`;
- namespaced fields for all declared outputs;
- `scan_methodmesh_full_json`.

The workbook is an example, not an allow-list.

## Build/test status

The supplied handoff was a standalone module archive and did not contain the MethodMesh Gradle project. Therefore a truthful full app build could not be executed in this migration workspace.

Required integration verification in the receiving MethodMesh repository:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then exercise:

1. direct native scan -> working result -> Commit -> tap-to-copy -> Share/Save/Home;
2. rotation before and after Commit;
3. preset with fixed `barcode_formats`;
4. preset with runtime `barcode_formats`;
5. protocol/schedule step closeout;
6. widget preset closeout to Android desktop;
7. ODK example roundtrip including namespace and `methodmesh_full_json`;
8. embedded scanner camera permission denial/retry and explicit Cancel paths;
9. portrait -> landscape -> portrait rotation while scanning, confirming the viewfinder remains horizontally aligned;
10. continuous detection of two different codes, confirming the second replaces the uncommitted working payload;
11. HTTP/HTTPS payload -> Open link button appears; non-URL payload -> button absent.

No production-ready build claim is made by this standalone handoff until those repository-level commands pass.
