# MethodMesh Geocaching

**Module ID:** `geocaching`  
**Module version:** `1.1.4`  
**Maturity:** Development  
**Connectivity:** Offline-first; selected provider/account operations require a network connection.

MethodMesh Geocaching is a field-oriented geocaching toolkit and durable notebook. It is designed to remain useful without an account or network connection, while optionally adding OpenCaching discovery and account synchronisation.

The module deliberately separates four things that conventional geocaching apps often blur together:

1. **published cache data** — the cache listing and its nominal coordinates;
2. **live field state** — GPS, heading and an active hunt;
3. **the user's durable field ledger** — actual visit positions, notes, photos and trackable observations;
4. **remote provider state** — account profile, provider logs and successful upload receipts.

This separation allows MethodMesh to preserve observations the upstream service may never represent, such as the actual GPS position at which a find was logged.

## Native dashboard

`geocache.dashboard` is the control centre. It summarises:

- active hunt;
- saved cache library;
- total / found / DNF visits;
- geotagged-photo count;
- followed trackables;
- recent find positions on a map;
- OpenCaching account state;
- local visits still awaiting an explicit remote upload.

The dashboard is an aggregation surface only. Every operation remains an independent canonical capability for direct native use, presets, protocols, RIL and ODK.

## Canonical capabilities

| Capability | Purpose | Connectivity | Durable native state |
|---|---|---|---|
| `geocache.dashboard` | Control-centre snapshot | Online/offline | Reads existing state |
| `geocache.library` | Import, browse and export GPX cache records | Offline | Cache library |
| `geocache.nearby` | Find nearby saved or OpenCaching caches | Online/offline | Explicit “save offline” only |
| `geocache.navigate` | Live compass-and-distance navigation | Offline | Optional active hunt |
| `geocache.details` | Listing, hint, waypoints and recent logs | Online/offline | No implicit persistence |
| `geocache.project_waypoint` | Project a coordinate by bearing and distance | Offline | None |
| `geocache.average_coordinates` | Average repeated GPS fixes | Offline | Working samples only |
| `geocache.record_visit` | Found / DNF / Note with actual GPS and media | Offline | Native ledger; external only when requested |
| `geocache.history` | Search, map and export visit history | Offline | Reads ledger; exports are explicit Files |
| `geocache.trackable_record` | Record a trackable observation/movement | Offline | Native ledger; external only when requested |
| `geocache.trackable_history` | View trackable ledger and mapped journey | Offline | Reads ledger |
| `geocache.account` | Inspect/connect OpenCaching accounts | Online/offline | Encrypted OAuth credentials/profile |
| `geocache.sync` | Refresh account, import logs or upload one visit | Online only | Imported records and sync receipts |

## Persistent storage

Structured, queryable data is kept under the module's private app storage:

- `caches.ndjson`
- `visits.ndjson`
- `trackables.ndjson`
- `sync_receipts.ndjson`
- `active_hunt.json`

This is intentionally **not** implemented as a pile of generic Files. Ledgers need efficient filtering, mapping and reconciliation.

Byte-bearing user artefacts use the shared MethodMesh Files architecture (`AndroidArtifacts` / `ArtifactService`), including:

- find photographs;
- exported GPX;
- visit-history CSV;
- visit-history GeoJSON;
- Geocaching-compatible field-note text;
- trackable journey GeoJSON.

The ledger stores internal Artifact identities; canonical capability results do not expose opaque `artifact://` handles. When a binary is returned to another Android caller/ODK, the module materialises a caller-readable `content://` URI so the shared transport can grant access as a real attachment.

## Cache library and GPX

The module accepts ordinary GPX waypoints and Groundspeak-style cache metadata where present. It parses:

- code/name;
- coordinates;
- type and container;
- difficulty/terrain;
- owner;
- short/long description;
- encoded hint;
- attributes;
- recent logs;
- additional waypoints where represented.

Imported files are user-supplied data. MethodMesh does not scrape Geocaching.com or attempt to bypass account or Premium restrictions.

## Find history

A `CacheVisitRecord` can preserve:

- visit ID;
- cache code/name/source;
- Found / DNF / Note;
- timestamp;
- **actual observation latitude/longitude**;
- GPS accuracy;
- **published cache latitude/longitude** separately;
- distance between actual and published point;
- note;
- favourite/recommend intent;
- one or more persistent media Artifact identities;
- remote log ID/state when known;
- whether the record was imported from a provider.

Historical maps plot actual field observations where known. Imported remote historical logs do not fabricate a local observation position.

## Geotagged photos

The visit and trackable-record screens can capture or attach a photo. When a current GPS fix exists, JPEG EXIF latitude/longitude is written before the image is persisted to MethodMesh Files. The persistent ledger links to the immutable managed File.

ODK/media returns expose real attachment-compatible `content://` URIs, never a private filesystem path or an internal Artifact identity.

## Trackables

Trackables have a separate durable event ledger. Supported event classes are:

- Follow
- Discover
- Retrieve
- Grab
- Drop
- Visit cache
- Note
- Own / collection
- Release

Each event may carry a public trackable reference, name, time, cache association, GPS observation, accuracy, note and photo.

**Private trackable tracking codes are deliberately not stored.** Only the public trackable reference intended for ordinary display/history is modelled.

`geocache.trackable_history` calculates recorded journey distance from geolocated observations and draws a connected journey on the map. Missing-location events remain in the chronological ledger but do not create invented geometry.

## Providers, accounts and sync

### Provider registry

Geocaching is not tied to the UK. MethodMesh keeps a persistent registry of OKAPI installations.

Starter profiles are included for OpenCache UK and OpenCaching PL, but they are ordinary editable profiles rather than hard-coded network branches. The operator can:

- add another OKAPI installation;
- edit a provider display name;
- edit the HTTPS site/base URL;
- enter or replace that provider's OKAPI consumer key;
- optionally store the consumer secret when OAuth account sign-in is needed;
- choose the default provider used by Nearby caches and other online lookups;
- remove custom provider profiles;
- keep several providers configured at the same time.

Provider credentials are stored per provider. Existing `opencache_uk` and `opencaching_pl` credentials from earlier module versions continue to resolve because those starter IDs are preserved.

A provider profile and a user account are separate concepts. Public OKAPI searches use Level 1 authentication and require only the configured consumer key. The consumer secret is used for signed OAuth requests and is only required when connecting a user account or performing account-specific operations such as personal history and uploading logs.

The canonical `source` / `provider` inputs accept provider IDs as text rather than a fixed country enumeration. This keeps presets, protocols and ODK compatible with custom installations.

### OpenCaching OAuth accounts

For any configured OKAPI provider, the account surface uses OAuth 1.0a. MethodMesh:

- stores no provider password;
- opens the provider's own authorization page;
- uses the official out-of-band/PIN OAuth flow;
- encrypts consumer secrets, access tokens and token secrets with Android Keystore-backed AES-GCM;
- can refresh the authorised profile;
- can import the authorised user's remote log history;
- can explicitly upload one selected local visit;
- records durable upload receipts per provider to avoid casually offering a successfully-uploaded visit again.

Provider actions are granular. “Sync” is not a hidden bidirectional merge, and a successful upload receipt for one provider does not imply that the visit is synced to another provider.

### Geocaching.com

Geocaching.com remains represented as a future official integration, but live sign-in is intentionally disabled until MethodMesh has approved Geocaching HQ API credentials. The module does **not** collect a Geocaching.com password or implement scraping as a substitute.

## Native persistence versus ODK persistence

The visit and trackable capabilities have an independent native persistence reason, so native dashboard use commits to the durable personal ledger.

An external/ODK invocation defaults to **return-only**. It writes the native personal ledger only when `persist_to_ledger=true` is deliberately supplied. This prevents MethodMesh from duplicating study data merely because ODK invoked a capability.

Account sync is inherently persistent because the requested operation is itself “refresh/import/upload”.

## Working result → Commit

Purpose-built screens keep the live tool visible. Commit freezes the current canonical return payload on that same surface.

- Editing after Commit does not mutate the frozen payload.
- Committed payloads are serialized into saveable JSON state for ordinary recreation.
- Native use exposes Done after Commit.
- External/ODK runs return the committed payload directly through the caller's existing result channel.

Useful displayed scalar/text values such as coordinates, codes, visit IDs and distances use tap-to-copy surfaces.

## Maps

Maps use the MapLibre dependency already present in MethodMesh. Online street map rendering uses OpenFreeMap when data connectivity is allowed. The history/journey content itself remains available offline; a blank/offline plotting mode is used where appropriate rather than making the underlying history dependent on tiles.

Trackable journey maps draw both event points and their recorded connecting line.

## Dependencies

No new Maven dependency is required by this module. It reuses MethodMesh's existing:

- Jetpack Compose / Material 3;
- fused location provider;
- CameraX / Android activity-result contracts;
- Android `ExifInterface`;
- MapLibre;
- FileProvider;
- MethodMesh Artifact Service;
- MethodMesh canonical execution/workflow/ODK transport.

## ODK

See [ODK_INTEGRATION.md](ODK_INTEGRATION.md).

Thirteen canonical single-invocation showcase workbooks are included under `docs/`. Each:

- calls exactly one canonical capability;
- captures `methodmesh_status`;
- captures every declared capability return;
- captures `methodmesh_full_json`;
- uses canonical unprefixed return names;
- does not set `methodmesh_return_namespace`;
- uses image/file question types for attachment returns.

The showcase forms are examples, not allow-lists. The canonical capability contract remains the source of truth.

## Privacy and security

- No provider password is collected or stored.
- OAuth/access secrets are encrypted with Android Keystore-backed AES-GCM.
- Consumer/access secrets are never returned in capability results.
- Private trackable tracking codes are not modelled.
- Location history exists because the user explicitly chose a durable geocaching field notebook; actual and published positions remain distinct.
- Deleting/clearing durable user data should be presented as an explicit destructive action with confirmation.
- External GPX and remote provider data remain subject to the user's/provider's applicable terms and permissions.

## Validation status

See [VALIDATION.md](VALIDATION.md) for the exact checks performed and the limits of this handoff.


### v1.1.2 Nearby provider flow

- Tapping an unconfigured provider in **Nearby caches** opens that provider's credential editor directly.
- Tapping a configured provider immediately searches that exact provider; provider selection no longer risks reusing the previous Compose state value.
- Native Nearby **Commit** is hidden until at least one cache has actually been returned. Empty offline/provider searches remain explicit empty states rather than committable results.
- Fresh Nearby configuration defaults to a 10 km radius. Existing user/preset settings remain authoritative when supplied.

### v1.1.5 Chosen search centre

Nearby caches can now search around either the device's current location or a user-chosen place. The native **Choose place** flow accepts a place name, postcode or address, shows matching geocoder candidates, and also provides manual latitude/longitude entry as a fallback. The chosen centre remains active when the provider or radius is changed and resolves into the existing canonical latitude/longitude contract, so ODK/protocol integrations remain compatible.
