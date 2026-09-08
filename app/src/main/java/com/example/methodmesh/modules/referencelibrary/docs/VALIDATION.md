# Reference Library v0.3.0 validation

Status: **Development**

## Static review completed

- Existing method IDs preserved: `reference.library.open`, `reference.library.email`.
- New independent capability: `reference.library.peer.manage`.
- All three methods expose one `DEVELOPMENT` maturity tag and one `OFFLINE` connectivity tag.
- All three methods are listed by `ReferenceLibraryModule.as100Methods()` and have capability screens/settings.
- Dashboard Nearby is a projection over `reference.library.peer.manage`, not a private implementation.
- Existing document read/back behaviour remains non-transactional in manual library UI.
- Local batch import uses Android multi-document picker.
- Peer uploads are copied into MethodMesh-managed storage using sanitised generated targets.
- Peer HTTP service has no existing-document download endpoint.
- Peer HTTP requests require a random session token and reject non-local remote addresses.
- Local-hotspot credentials/token are not declared outputs or persisted library metadata.
- Web remove and custom-shelf deletion require browser confirmation; custom-shelf deletion rehomes documents to Personal.
- Fixed native-preset peer settings are not redundantly shown as runtime controls.
- Active peer-session socket/hotspot state is held only in process memory across ordinary Activity recreation.

## ODK/XLSForm review

Canonical v1.07 showcases are split one capability per workbook:

- `example_odk_showcase_reference_library_open.xlsx`
- `example_odk_showcase_reference_library_email.xlsx`
- `example_odk_showcase_reference_library_peer_manage.xlsx`

Each showcase:

- contains exactly one grouped MethodMesh intent call;
- uses `com.example.methodmesh.EXECUTE_METHOD` with the canonical method ID;
- sets `input_payload_mode='FULL'` and `return_mode='flat'`;
- does not set `methodmesh_return_namespace`;
- captures shared `methodmesh_status` and `methodmesh_full_json`;
- captures only outputs belonging to that capability;
- uses globally unique input node names within that workbook.

The open showcase uses an attachment-compatible `file` leaf for `library_document_uri` so the receiving transport can import the returned bytes into ODK rather than leaving a raw MethodMesh URI as the useful submission value.

## Receiving-repository checks still required

The standalone execution environment did not contain the complete Android repository/dependency graph, so a real Gradle compile was not possible here. Run at least:

```text
./gradlew :app:compileDebugKotlin
./gradlew test
```

Then smoke-test on an Android 13+ device:

1. Confirm `NEARBY_WIFI_DEVICES` is declared in the app manifest and runtime prompt appears when required.
2. Start **Nearby library -> Local hotspot**.
3. Connect a second device to the displayed SSID/password.
4. Open the displayed URL and batch-upload several different file types into a chosen shelf.
5. Create a custom shelf, move/rename/favourite an item, and confirm removal semantics.
6. Rotate the MethodMesh device during an active session; confirm the same session remains available.
7. Stop the session; confirm the site is no longer reachable and the hotspot closes.
8. Repeat with **Current Wi-Fi** on a trusted LAN.
9. Run all three canonical XLSForms through MethodMesh local validation, ODK Central and Kobo where available.
10. For `reference.library.open`, verify the returned document lands in ODK as an actual attachment.

## Shared integration

The receiving app must contain:

```xml
<uses-permission
    android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />
```

in `app/src/main/AndroidManifest.xml` for Android 13+ LocalOnlyHotspot operation.
