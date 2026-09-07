# Reference Library architecture — v0.2.6

## Boundary

`referencelibrary` owns document-library metadata, shelves, dashboard presentation, scanner-to-shelf persistence, local document resolution and the generic document-email handoff. It does not own document scanning internals, electronic-signature logic, PDF merge logic, ODK form data, study-specific consent language, SMTP infrastructure or delivery confirmation.

## Public methods

### `reference.library.open`

Resolves/returns one local reference document. Direct dashboard reading does not create a result; external/preset/protocol/ODK launches use the MethodMesh result contract.

### `reference.library.email`

Resolves library IDs plus optional piped attachment URIs and performs a user-visible Android `ACTION_SEND_MULTIPLE` handoff. It can populate To/Cc/Bcc/subject/body. Return semantics deliberately distinguish **mail-app handoff** from **email delivery**.

## Composition pattern for consent

The intended composition is capability-to-capability rather than monolithic consent code:

`ODK fields -> signature/witness capability -> PDF merge/final document URI -> reference.library.email`

Static study documents can come from library IDs while the participant-specific signed PDF comes from a piped URI. This keeps study configuration in ODK/protocols and document storage in the library.

## UI doctrine

The native dashboard is a persistent control surface. It avoids per-document cards with stacked buttons. v0.2.2 uses one low-contrast grouped document surface, compact file-type badges, generous whitespace, pill shelf filters and larger Continue reading tiles. Rows remain primarily for reading; secondary actions are hidden behind one overflow affordance. Search and shelf filtering dominate the top of the screen. Scan/Add are the only persistent action buttons. The same surface styling is reused by the email compose capability without changing its single-card transaction semantics.

## Storage

External documents retain their Android content URIs. Scanner results are cache-backed and are therefore copied to `filesDir/reference_library/scans` before shelving. FileProvider exposes managed copies through the existing app provider.

## Stable built-in shelves

`first_aid`, `medical`, `safety`, `fieldwork`, `equipment`, `travel`, `personal`.

Custom shelf IDs are persisted locally and are intentionally not declared as fixed `ChoiceSetting` values, because dynamic user-created choices cannot be part of the static capability settings contract. External callers should use known built-in shelf IDs or stable document IDs.


## Email compose surface

`reference.library.email` intentionally bypasses the generic result scaffold. The capability is a single interactive card that owns document selection, message composition, Android mail handoff, and post-handoff operator confirmation. Native document selection is human-readable (shelf/category and title); stable document IDs remain the transport contract for ODK, presets and protocols. Returning from the mail app does not itself imply success: the card asks the operator to confirm **sent** or **not sent** before completing the MethodMesh workflow. `library_email_user_confirmed_sent` records this operator assertion; `library_email_delivery_confirmed` remains false because MethodMesh cannot verify mail-provider delivery.


## v0.2.3 viewer return semantics

Direct dashboard reading launches external document viewers in the same Android task as MethodMesh. The library deliberately does not use `FLAG_ACTIVITY_NEW_TASK` for ordinary viewing. This ensures Android Back returns to the live Reference Library dashboard rather than exposing an older capability/result activity. Native reading still creates no `ExecutionResult`; ODK, presets and protocols retain transactional result behaviour.


## v0.2.4 visual system

Reference Library deliberately avoids duplicating the capability title supplied by the MethodMesh shell. A theme-derived darker reading-room surface creates the library's visual anchor while retaining light/dark theme compatibility. Continue Reading tiles have fixed 172 dp × 148 dp geometry and a consistent internal baseline. The catalogue below is deliberately quieter and flatter. No custom hard-coded brand colours are introduced; surfaces are derived from `MaterialTheme.colorScheme`.


## v0.2.5 read-versus-return contract

Opening a document for reading is now explicitly distinct from selecting a document as a MethodMesh result. In every manual UI, tapping a document records recency, clears any stale capture result and opens the reader without producing an `ExecutionResult`. Returning from Drive Viewer, a PDF reader or another external viewer therefore reveals the library content rather than the generic Result surface. Automatic-return callers such as ODK retain tap-to-select-and-return behaviour. Manual transactional runs that need to return a document expose **Use this document** as an explicit overflow action. Fixed `document_id` preset/intent runs continue to resolve that requested document transactionally.


## v0.2.6 share action

Document sharing is a native library action, separate from result selection. The library passes the existing content URI through Android `ACTION_SEND` with MIME type, `ClipData`, and temporary read permission. No `ExecutionResult` is produced by sharing.
