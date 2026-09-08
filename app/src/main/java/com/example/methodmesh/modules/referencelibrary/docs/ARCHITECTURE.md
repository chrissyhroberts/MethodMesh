# Reference Library architecture — v0.3.0

## Ownership boundary

`referencelibrary` owns library metadata, shelves, document resolution, managed copies created by library workflows, dashboard presentation, generic document-email handoff and the temporary nearby-library manager.

It does **not** own document-scanner internals, signature/witness capture, PDF merge, ODK form data, study-specific messaging, SMTP/delivery infrastructure, Android's Wi-Fi implementation or shared MethodMesh transport.

The module exposes three canonical methods and screens through `ReferenceLibraryModule`. Dashboard buttons are projections over those same capability implementations; no shared `HomeScreen` special case is required.

## Canonical methods

### `reference.library.open`

Resolves/returns one local reference document. Manual reading is navigation, not capture. Automatic-return callers use the same contract to select/return a document. Stable existing method/output IDs are preserved.

### `reference.library.email`

Resolves library documents plus optional piped attachment URIs and performs a user-visible Android mail handoff. It keeps compose, handoff and operator sent/not-sent confirmation on one capability surface. Delivery confirmation is never fabricated.

### `reference.library.peer.manage`

Runs a temporary local HTTP management interface over either Android LocalOnlyHotspot or the current trusted Wi-Fi network. The canonical result contains non-secret session statistics only. WLAN credentials, addresses and the temporary web token remain live operational state and are excluded from the canonical result.

## Local peer topology

```text
ReferenceLibraryPeerCapabilityScreen
  -> ReferenceLibraryHotspotController (optional LocalOnlyHotspot)
  -> ReferenceLibraryPeerServer (ephemeral ServerSocket)
  -> ReferenceLibraryRepository
       -> filesDir/reference_library/nearby
       -> SharedPreferences metadata
```

The browser client can upload files and maintain metadata. It cannot download existing library file contents. Requests require a random session token. Only local/site-local clients are accepted. Local-hotspot mode is preferred; current-Wi-Fi mode is an explicitly less-private fallback because local HTTP is unencrypted.

`ReferenceLibraryPeerRuntime` retains only live process-memory objects/credentials so an active session can remain reachable across ordinary Activity recreation. Nothing secret is persisted to disk and process death ends the session.

## Batch import

The library dashboard's **Add files** uses Android `OpenMultipleDocuments`. Persistable provider grants are retained when possible. If durable provider access is unavailable, the file is copied into MethodMesh-managed `reference_library/imports` storage. This is ordinary local batch import and is separate from the P2P capability.

## Storage and deletion

External-source entries retain provider ownership. Scanner/dependency outputs and nearby/batch fallback copies use MethodMesh-managed storage and FileProvider URIs. Removing a library entry removes metadata only; it does not silently delete the source bytes. Deleting a custom shelf reassigns its entries to `personal`.

## Surface parity

All three methods are registered in `as100Methods()`, `capabilityScreens()`, `capabilitySettings()` and RIL bindings. They therefore remain independently discoverable for direct native runs, preset/protocol construction and ODK/XLSForm. The richer library dashboard does not collapse the primitives into private dashboard functions.

The peer manager is also invoked from the dashboard through its canonical capability screen rather than a second P2P implementation.

## ODK/XLSForm

Each v1.07 canonical workbook invokes exactly one method, uses canonical unprefixed return names, requests FULL payload, captures `methodmesh_status` and `methodmesh_full_json`, and does not use a return namespace.

`library_document_uri` remains the stable canonical open-method output name, but ODK should receive the returned document as a real attachment through shared transport, not as a private URI string.

## Shared-framework dependency

Android 13+ LocalOnlyHotspot requires app-level `NEARBY_WIFI_DEVICES` permission. This is a genuine shared Android-manifest integration and is intentionally not bundled inside the returned module folder.
