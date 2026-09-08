# MethodMesh Reference Library

Version: **0.3.0**  
Status: **Development**  
Connectivity: **Offline**

Reference Library is MethodMesh's offline-first shelf for useful documents, maps, manuals, study materials and other files that should remain available on the device. The module is reading-first: ordinary native use is a persistent library surface rather than a capture/result workflow.

The module owns three canonical capabilities:

- `reference.library.open` — browse/resolve/return a library document;
- `reference.library.email` — prepare an email containing library and/or piped document attachments;
- `reference.library.peer.manage` — run a temporary local web manager for batch upload and shelf maintenance.

The dashboard is one projection of these contracts. Each capability remains independently discoverable for direct native runs, presets, protocols and ODK/XLSForm. No Reference Library behaviour is implemented by special-casing `HomeScreen`.

## Library dashboard

Direct native use supports:

- search by title, source or shelf;
- built-in and user-created shelves;
- uniform Continue Reading tiles;
- read -> external viewer -> Back -> library, with no generic Result page;
- favourite/unfavourite;
- rename or move an entry;
- **Share this document** through the Android share sheet;
- explicit **Use this document** only when a manual transactional run needs a returned document;
- remove a library entry without deleting an externally owned source file;
- scan paper pages through the public `document.scan` capability and persist the resulting document;
- **Add files** through Android's multi-document picker for ordinary local batch import;
- **Nearby** to open the canonical `reference.library.peer.manage` capability.

### Built-in shelves

Stable built-in shelf IDs are:

`first_aid`, `medical`, `safety`, `fieldwork`, `equipment`, `travel`, `personal`.

Custom shelves are local user-owned metadata. Deleting a custom shelf never deletes its documents: entries are reassigned to `personal`.

## Storage model

External documents selected through Android's Storage Access Framework retain their original `content://` URI when a persistable read grant is available. Batch-import items whose provider cannot grant durable access are copied into MethodMesh-managed storage instead.

Scanner outputs are copied into `filesDir/reference_library/scans`. Nearby uploads are copied into `filesDir/reference_library/nearby`. Managed files are exposed to Android through the app's existing FileProvider.

Reference Library metadata contains title, shelf, content URI, MIME type, source label, optional version, favourite state and recency. The module does not upload library contents to cloud storage.

---

# Capability: return/open a library document

Canonical method ID: `reference.library.open`

Native reading and MethodMesh result selection are intentionally different operations. In manual library UI, tapping an item reads it and never creates a result. Automatic-return callers such as ODK return a selected/resolved document. Manual transactional runs expose **Use this document** as the explicit selection action.

## Inputs

| Key | Type | Required | Meaning |
|---|---|---:|---|
| `document_id` | text | No | Stable local library document ID. If supplied by an external caller, resolve that item directly. |
| `query` | text | No | Initial native search text. |
| `shelf` | choice/text | No | Initial shelf filter; built-in IDs are stable, existing local custom IDs are also accepted by the runtime. |

## Declared outputs

| Key | Meaning |
|---|---|
| `library_status` | `succeeded`, `not_found`, `unavailable` or other resolution status. |
| `library_document_id` | Stable local library ID. |
| `library_document_title` | Human title. |
| `library_document_uri` | Canonical document media output; shared ODK transport must project this as an actual submission attachment. |
| `library_document_mime` | MIME type. |
| `library_shelf` | Shelf ID. |
| `library_source` | Library source/provenance label. |
| `library_version` | Optional document version label. |
| `library_error` | Resolution error when applicable. |

## ODK Integration Card — `reference.library.open`

**ODK INTEGRATION**

**Capability**  
Return/open a reference document  
`reference.library.open`

**Tags**  
Maturity: Development  
Connectivity: Offline

**ODK INPUTS**

| Canonical input | ODK type | Required | Meaning |
|---|---|---:|---|
| `document_id` | text | optional | Known local library ID. Leave blank for interactive selection. |
| `query` | text | optional | Initial search text for interactive selection. |
| `shelf` | select/text | optional | Initial shelf. |

Interactive acquisition: polished Reference Library picker when `document_id` is not supplied.

**INTENT CALL**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='reference.library.open',input_document_id=${rl_open_document_id},input_query=${rl_open_query},input_shelf=${rl_open_shelf},input_payload_mode='FULL',return_mode='flat')
```

**MODIFIERS**

| Modifier | Type | Default/semantics | Meaning |
|---|---|---|---|
| `payload_mode` | text | canonical showcase uses `FULL` | Requests shared complete metadata/audit projection. |
| `return_mode` | text | canonical showcase uses `flat` | Flat ODK return projection. |

**CANONICAL RETURNS**

`library_status`, `library_document_id`, `library_document_title`, `library_document_uri`, `library_document_mime`, `library_shelf`, `library_source`, `library_version`, `library_error`, plus shared `methodmesh_status` and `methodmesh_full_json`.

**RETURN FIELD PLACEMENT**

The canonical showcase places the call on one group. Return fields are direct group children with the canonical unprefixed names. It contains one MethodMesh invocation and no return namespace.

**FILE RETURN SEMANTICS**

`library_document_uri` is a stable canonical URI-named output, but the ODK-visible result must be an actual attachment. The showcase therefore captures it in an attachment-compatible `file` leaf. Shared MethodMesh transport owns ClipData/read-grant/copy semantics; the XLSForm should not preserve an obscure private URI string as the useful result.

**RUNTIME**

Inputs: document ID/search/shelf.  
Beef: selected document attachment plus title/MIME metadata.  
Metadata: `methodmesh_full_json` always captured by the canonical ODK showcase.

---

# Capability: email library documents

Canonical method ID: `reference.library.email`

This capability resolves one or more library documents and/or accepts attachment URIs piped from earlier MethodMesh steps, prepares an Android mail intent, and opens an installed mail application for user review. It does not silently send email.

The entire native interaction remains on one compose card: select documents by shelf/title, enter recipient/message, open the mail app, return, then confirm **Yes — sent** or **No — not sent**. It deliberately does not detour through the generic MethodMesh Result card.

## Inputs

| Key | Required | Meaning |
|---|---:|---|
| `document_ids` | No | Comma/semicolon/newline-delimited stable library IDs. Native users choose by human-readable dropdowns instead. |
| `attachment_uris` | No | Additional/piped attachment URIs from previous MethodMesh/ODK steps. |
| `recipient` | Yes for useful handoff | To address(es). |
| `cc` | No | Cc address(es). |
| `bcc` | No | Bcc address(es). |
| `subject` | No | Email subject. |
| `body` | No | Arbitrary caller-supplied body text. |
| `chooser_title` | No | Android chooser title; defaults to `Send document copies`. |

## Declared outputs

`library_email_status`, `library_email_recipient`, `library_email_subject`, `library_email_document_ids_json`, `library_email_attachment_uris_json`, `library_email_attachment_count`, `library_email_missing_document_ids_json`, `library_email_handoff`, `library_email_user_confirmed_sent`, `library_email_delivery_confirmed`, `library_email_error`.

`library_email_user_confirmed_sent` is the operator's explicit assertion after returning from the mail application. `library_email_delivery_confirmed` remains `false`; Android handoff cannot prove provider/server delivery.

## ODK Integration Card — `reference.library.email`

**ODK INTEGRATION**

**Capability**  
Email reference documents  
`reference.library.email`

**Tags**  
Maturity: Development  
Connectivity: Offline

**ODK INPUTS**

| Canonical input | ODK type | Required | Meaning |
|---|---|---:|---|
| `document_ids` | text | optional | Library IDs to attach. |
| `attachment_uris` | text | optional | Piped/additional attachment URIs. |
| `recipient` | text | required for useful handoff | To address(es). |
| `cc` | text | optional | Cc. |
| `bcc` | text | optional | Bcc. |
| `subject` | text | optional | Subject. |
| `body` | text | optional | Message body, including ODK-calculated/personalised text. |
| `chooser_title` | text | optional | Android chooser heading. |

Interactive acquisition: MethodMesh may add/remove library documents on the compose card; the user completes the send in the installed mail application and then confirms sent/not-sent on return.

**INTENT CALL**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='reference.library.email',input_document_ids=${rl_email_document_ids},input_attachment_uris=${rl_email_attachment_uris},input_recipient=${rl_email_recipient},input_cc=${rl_email_cc},input_bcc=${rl_email_bcc},input_subject=${rl_email_subject},input_body=${rl_email_body},input_chooser_title=${rl_email_chooser_title},input_payload_mode='FULL',return_mode='flat')
```

**MODIFIERS**

| Modifier | Type | Default/semantics | Meaning |
|---|---|---|---|
| `payload_mode` | text | `FULL` in showcase | Shared complete audit/metadata projection. |
| `return_mode` | text | `flat` in showcase | Flat ODK return. |

**CANONICAL RETURNS**

All declared `library_email_*` fields above, plus shared `methodmesh_status` and `methodmesh_full_json`.

**RETURN FIELD PLACEMENT**

Direct children of one intent group, canonical unprefixed keys, one MethodMesh call, no return namespace.

**FILE RETURN SEMANTICS**

This capability consumes/forwards attachments to the mail app; it does not return a new binary file to ODK. Attachment URIs in the audit/status fields are transport metadata, not new ODK submission attachments.

**RUNTIME**

Inputs: recipient/message/library IDs/piped attachments.  
Beef: user-visible mail handoff and explicit sent/not-sent confirmation.  
Metadata: `methodmesh_full_json` plus attachment-resolution/handoff fields.

### Consent-delivery composition

A research workflow can remain compositional rather than hard-coded:

`ODK participant fields -> signature/witness capability -> PDF merge/final signed consent URI -> reference.library.email`

The final signed consent can arrive through `attachment_uris`; static participant information/contact sheets can be supplied through `document_ids`; ODK supplies recipient, subject and a calculated personalised message. Study-specific wording and identifiers remain in ODK/protocol configuration rather than this module.

---

# Capability: nearby library manager / batch upload

Canonical method ID: `reference.library.peer.manage`

This is a separate canonical capability, not dashboard-only logic. The library dashboard's **Nearby** button merely projects it.

The capability creates a time-limited local web interface. The recommended mode uses Android `LocalOnlyHotspot`, which creates an isolated WLAN with no internet route. A trusted-current-Wi-Fi mode is available as a fallback.

From another laptop/tablet/phone the operator opens the temporary URL and can:

- choose a destination shelf once and drag/drop multiple files;
- choose multiple files through the browser picker;
- upload PDFs, maps and arbitrary useful file types (the library stores them; interpretation depends on installed viewer apps);
- create custom shelves;
- rename/move/favourite/remove library entries when web edits are enabled;
- delete custom shelves with explicit confirmation; their documents move to Personal.

The web manager intentionally has **no document-download endpoint**. It exposes library metadata and accepts uploads/maintenance actions but does not provide browser access to existing document contents.

## Inputs/settings

| Key | Type | Default | Meaning |
|---|---|---|---|
| `network_mode` | choice | `local_hotspot` | `local_hotspot` or `current_wifi`. |
| `session_minutes` | choice/integer | `30` | Session lifetime; native choices 10/30/60/120 min. |
| `max_file_mb` | choice/integer | `250` | Maximum bytes accepted for one uploaded file. |
| `default_shelf` | shelf ID | `personal` | Initial upload destination. Existing local custom shelf IDs are accepted at runtime. |
| `allow_edits` | boolean | `true` | Allow metadata edits/removal/shelf creation/deletion from the browser. Upload remains available. |

Fixed native-preset settings are not redundantly requested at runtime. Runtime-marked settings remain editable.

## Declared outputs

| Key | Meaning |
|---|---|
| `library_peer_status` | Session completion/error status. |
| `library_peer_network_mode` | `local_hotspot` or `current_wifi`. |
| `library_peer_session_id` | Non-secret session identifier. |
| `library_peer_uploaded_count` | Files successfully imported. |
| `library_peer_updated_count` | Metadata/favourite updates. |
| `library_peer_removed_count` | Library entries removed. |
| `library_peer_shelf_changes_count` | Shelf create/delete operations. |
| `library_peer_bytes_received` | Uploaded bytes received. |
| `library_peer_http_request_count` | Local HTTP request count. |
| `library_peer_started_time_iso` | Session start. |
| `library_peer_finished_time_iso` | Session finish. |
| `library_peer_duration_ms` | Duration. |
| `library_peer_error` | Error when applicable. |

SSID, hotspot passphrase, local IP address, port and the web-session token are operational credentials/details shown only while the live native screen is open. They are deliberately **not** canonical outputs and are not persisted to result/audit state.

## ODK Integration Card — `reference.library.peer.manage`

**ODK INTEGRATION**

**Capability**  
Manage reference library nearby  
`reference.library.peer.manage`

**Tags**  
Maturity: Development  
Connectivity: Offline

**ODK INPUTS**

| Canonical input | ODK type | Required | Meaning |
|---|---|---:|---|
| `network_mode` | select_one | optional | Local hotspot or trusted current Wi-Fi. |
| `session_minutes` | select_one/integer | optional | Maximum live session duration. |
| `max_file_mb` | select_one/integer | optional | Per-file upload ceiling. |
| `default_shelf` | select_one/text | optional | Initial shelf. |
| `allow_edits` | select_one/boolean | optional | Whether browser-side maintenance controls are enabled. |

Interactive acquisition: required. MethodMesh presents the live session screen, requests Android nearby-Wi-Fi permission where necessary, shows temporary connection details, and waits for the operator to stop/finish the session.

**INTENT CALL**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='reference.library.peer.manage',input_network_mode=${rl_peer_network_mode},input_session_minutes=${rl_peer_session_minutes},input_max_file_mb=${rl_peer_max_file_mb},input_default_shelf=${rl_peer_default_shelf},input_allow_edits=${rl_peer_allow_edits},input_payload_mode='FULL',return_mode='flat')
```

**MODIFIERS**

| Modifier | Type | Default/semantics | Meaning |
|---|---|---|---|
| `payload_mode` | text | `FULL` in showcase | Shared complete audit/metadata projection. |
| `return_mode` | text | `flat` in showcase | Flat ODK return. |

**CANONICAL RETURNS**

All declared `library_peer_*` fields above, plus shared `methodmesh_status` and `methodmesh_full_json`.

**RETURN FIELD PLACEMENT**

Direct children of one intent group; canonical unprefixed names; one MethodMesh invocation; no namespace.

**FILE RETURN SEMANTICS**

None. Uploaded files become Reference Library content; the peer-session capability returns session statistics/audit, not copies of uploaded files.

**RUNTIME**

Inputs: local-network/session limits/default shelf/edit policy.  
Beef: live local management session and resulting upload/maintenance counts.  
Metadata: timing/request/byte counters plus `methodmesh_full_json`.

## Nearby security and privacy

- **Local hotspot is the recommended mode.** Android creates the WLAN and supplies its SSID/passphrase; no internet service is provided by the hotspot.
- The web server binds only for an explicit live session and accepts only loopback/link-local/site-local clients.
- Every request requires a cryptographically random session token embedded in the temporary URL.
- The token and WLAN password are process-memory/live-screen details, never canonical outputs and never written to module preferences.
- The server sends `Cache-Control: no-store` and exposes no existing-document download route.
- Uploaded filenames are sanitised before MethodMesh-managed storage is created; browser-provided paths are never used directly.
- A per-file size ceiling is enforced before import.
- `current_wifi` is explicitly less private because traffic is plain local HTTP on the existing LAN. Use only on a trusted local network.
- Leaving/stopping the capability closes the server and hotspot. Ordinary configuration recreation keeps an already-live session reachable in process memory; process death ends it.

## Android permission integration

The host app requires the shared app-level permission on Android 13+:

```xml
<uses-permission
    android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />
```

This belongs in `app/src/main/AndroidManifest.xml`, not in the module folder. The app already uses `INTERNET` for sockets and legacy location permissions; Android <=12 uses fine-location permission for LocalOnlyHotspot. The peer capability requests the relevant runtime permission when the operator starts a hotspot.

No third-party web server library is introduced; the temporary HTTP service uses Java/Android platform sockets.

## ODK showcase files

v0.3.0 follows Master Book v1.07's one-call-per-workbook rule:

- `docs/example_odk_showcase_reference_library_open.xlsx`
- `docs/example_odk_showcase_reference_library_email.xlsx`
- `docs/example_odk_showcase_reference_library_peer_manage.xlsx`

The older multi-call `example_odk_reference_library.xlsx` is removed from the active module so it is not mistaken for a canonical v1.07 showcase.

## Validation status

The new capability is **Development**. The standalone handoff was statically reviewed for module ownership, stable existing method IDs, capability/preset/protocol/ODK exposure, local-only security boundaries, workbook structure and ZIP layout. Full `:app:compileDebugKotlin` could not be run in this runtime because the complete MethodMesh repository/dependency graph was not locally available. See `VALIDATION.md` for the receiving-repository checklist.
