# Geocaching module validation

Validated against the local `METHODMESH_MASTER_BOOK.md` v1.07 and the current MethodMesh repository contracts available on 2026-09-08.

## Module boundary

- Handoff contains one module-owned `geocaching/` folder only.
- No Gradle, manifest, central registry, application, or whole-app files are included.
- The module uses only dependencies already present in MethodMesh.

## Canonical parity

Static inventory check:

- 13 canonical `As100Method` objects;
- 13 capability screens;
- 13 capability-settings entries;
- 13 unique canonical method IDs;
- 13 RIL bindings;
- 13 one-call XLSForm showcase workbooks.

The dashboard is an aggregation/composition surface. It does not replace the independently exposed cache library, discovery, navigation, details, projection, averaging, visit, history, trackable, account, or sync capabilities.

## Persistence review

Persistent native state has an independent geocaching reason:

- cache library;
- active hunt;
- visit/find ledger;
- trackable event ledger;
- provider sync receipts;
- encrypted account/provider credentials and cached account profile.

ODK/external visit and trackable calls default to return-only. They append the personal MethodMesh ledger only when `persist_to_ledger=true` is explicitly supplied.

Byte-bearing user artefacts use the shared MethodMesh Artifact Service / Files boundary. Canonical return contracts do not expose `artifact://` identities. Binary returns are materialised as caller-readable `content://` URIs.

## ODK/XLSForm validation

All 13 `.xlsx` workbooks were imported and inspected with `artifact_tool`.

For every workbook:

- exactly one `com.example.methodmesh.EXECUTE_METHOD(...)` invocation is present;
- the invocation uses the canonical method ID;
- `input_payload_mode='FULL'` and `return_mode='flat'` are used;
- `methodmesh_return_namespace` is absent;
- `methodmesh_status` is captured;
- `methodmesh_full_json` is captured;
- every output declared by the corresponding Kotlin contract is represented as a child return field;
- no spreadsheet formula-error token (`#REF!`, `#DIV/0!`, `#VALUE!`, `#NAME?`, `#N/A`) was found.

Attachment return typing was specifically checked:

- cache-library GPX export — `file`;
- visit photo — `image`;
- selected historical photo — `image`;
- visit-history CSV — `file`;
- visit-history GeoJSON — `file`;
- Geocaching field-note export — `file`;
- trackable-event photo — `image`;
- trackable-journey GeoJSON — `file`.

## OpenCaching provider review

The OpenCaching implementation was checked against current upstream OKAPI service documentation/source.

- OAuth uses the provider authorization flow rather than collecting a provider password.
- User-history import uses `services/logs/userlogs` with the authenticated user's UUID.
- The userlogs response is treated as a JSON array.
- Log submission uses the documented Found / Didn't find / Comment classes.
- Uploads are explicit single-visit operations.
- Successful and failed attempts produce durable local sync receipts.
- A previously successful local-visit/provider pair is not casually offered for repeat upload.

Geocaching.com authentication remains deliberately unavailable until MethodMesh has official approved API credentials. No password collection or scraping substitute is included.

## Kotlin/static validation

A parser-oriented `kotlinc` pass was run over all module Kotlin files in this isolated environment.

The standalone environment does not contain the Android/Compose/MethodMesh application classpath, so a full Android compilation is not possible here and unresolved Android/Compose/MethodMesh references are expected. The pass was used to identify lexical/parser failures independent of that classpath. Remaining literal-token syntax defects found during the pass were corrected; the final pass reports no `expecting`, `unexpected tokens`, or unsupported literal-prefix/suffix parser errors.

The current MethodMesh repository interfaces used by this module were cross-checked directly, including:

- `MethodMeshModule` discovery/registration contract;
- `MethodSetting` constructor signatures;
- `CapabilityScreenSpec` / `CapabilityScreenContext` lifecycle semantics;
- `InvocationContext` propagation;
- shared Files / `ArtifactService` persistence;
- Android returned-content URI projection.

## Lifecycle / result semantics

Purpose-built interactive screens retain the working instrument/data surface while editing. Commit freezes a canonical values snapshot in saveable JSON state. External/ODK runs return at the Commit boundary; native dashboard use can remain on the capability surface and finish explicitly.

## Manual device checks still required after integration

The following require an actual Android build/device and therefore are not claimed as executed by this module-only handoff:

- `:app:compileDebugKotlin` against the user's exact current checkout;
- CameraX capture and EXIF GPS write on physical hardware;
- foreground/background GPS behaviour and Android permission flows;
- MapLibre tile/network rendering;
- FileProvider attachment roundtrip into ODK Collect;
- real OAuth credentials and provider write operations against each configured OKAPI installation;
- provider-specific cache/log edge cases.

These are integration/device tests, not omitted module features.

## Compile-fix pass — 2026-09-08

Corrected `GeocachingCapabilityUi.kt` after integration build feedback:

- added the missing `androidx.compose.ui.platform.LocalContext` import;
- replaced unsupported `android.media.ExifInterface.setLatLong(...)` calls with explicit standard GPS EXIF latitude/longitude tags;
- re-ran focused source parsing; no Kotlin syntax errors were found in the corrected module.

A full Android Gradle compile must still be performed in the MethodMesh app checkout because this isolated handoff directory does not contain the host Android/Compose classpath.

## v1.0.4 UI host fix

- All purpose-built Geocaching capability screens request `CapabilityHostPresentation.Immersive`.
- Reason: MethodMesh Standard presentation wraps capability content in a vertically scrollable host, which supplies unbounded vertical constraints. The Geocaching `MethodSurface` is a full-screen bounded instrument surface with a weighted working body; under Standard hosting that body can collapse to zero height.
- Immersive presentation gives the capability the bounded dialog surface intended by the shared host contract while retaining MethodMesh execution, Commit and return semantics.
- Dashboard child capabilities are rendered as step 2/2 so child screens expose a functional Back action; the top-level dashboard no longer shows a dead Back action.

## v1.0.5 nearby-caches live-flow fix

- Native `geocache.nearby` now begins location acquisition on entry when coordinates are not already supplied.
- Commit is unavailable until a usable position exists and the selected source has been queried; an empty pre-search snapshot can no longer be committed accidentally.
- Empty offline-library, zero-result, provider-not-configured, and location-permission states are explicit in the capability UI.
- Changing search source or radius invalidates the previous live result until refreshed.
- The nearby map plots the operator position alongside returned cache locations.
- Automatic-return callers retain supplied-coordinate behaviour and return only after their requested search state is ready.


## v1.1.0 portable-provider registry

- Replaced the runtime UK/PL provider switch with persistent `GeocachingProviderRegistry`.
- OpenCache UK and OpenCaching PL remain starter profiles with stable IDs for migration, but are editable like other profiles.
- Custom HTTPS OKAPI installations can be added, edited, selected as default and removed.
- Consumer key/secret, OAuth tokens, account profile and sync receipts are isolated by provider ID.
- `geocache.nearby`, cache-detail online lookup and `geocache.sync` resolve the selected provider dynamically.
- Nearby presents Offline plus every registered provider instead of a fixed country selector.
- Account UI separates provider/application configuration from optional OAuth user sign-in.
- Native capability settings and ODK showcase forms now accept provider IDs as text, preserving custom-provider parity.
- Existing `opencache_uk` / `opencaching_pl` credentials remain usable because their IDs are preserved.


## v1.1.1 visible provider workflow

- Nearby caches now renders the dynamic provider registry directly rather than presenting provider plumbing as a hidden Accounts concern.
- Every provider source displays DEFAULT, KEY SET/NEEDS KEY, runtime VERIFIED state, and SIGNED IN state inline.
- Nearby includes a direct **Manage providers / add another OKAPI site** action opening the canonical provider/account capability.
- Returning from provider management refreshes the source registry and default provider without restarting the capability.
- Dashboard provider card now identifies the actual default provider and whether it is ready for public nearby searches; account sign-in remains explicitly optional.
- Starter OpenCache UK / OpenCaching PL profiles remain editable; arbitrary OKAPI sites remain supported.


## v1.1.2 Nearby interaction correction

- Unconfigured provider rows route directly to the selected provider editor (`open_editor=true`).
- Configured provider selection passes the selected provider ID explicitly to the search function, avoiding stale Compose-state source selection.
- Native Nearby Commit requires a usable position, a completed search, no active loading, and at least one returned cache.
- External/ODK automatic-return semantics are unchanged, including valid zero-result query payloads.
- No method IDs, output fields, ODK return contracts, or persistent data formats changed in this patch.

## v1.1.3 direct OKAPI key signup

- Provider setup now includes a direct **Create API key on provider website** action.
- The link is derived from the configured OKAPI installation base URL as `/okapi/signup.html`; for the built-in OpenCache UK profile this resolves exactly to `https://opencache.uk/okapi/signup.html`.
- An unconfigured selected provider also exposes the direct signup action before editing credentials.
- The browser link only obtains provider/application credentials; OAuth account sign-in remains a separate optional step.
- No method IDs, ODK contracts, result fields, or persistent data formats changed.


## v1.1.4 OKAPI response and diagnostics fix
- Fixed `search_and_retrieve` parsing: with `wrap=true`, OKAPI `services/caches/geocaches` results are a dictionary keyed by cache code, not an array. Successful nearby searches are now parsed into cache records instead of being misreported as zero results.
- Public OKAPI reads now use documented Level 1 `consumer_key` authentication. A consumer secret is not required for public nearby/cache-detail reads; it remains required for OAuth account operations.
- Nearby distinguishes KEY SET from runtime VERIFIED. A successful provider request marks the provider VERIFIED for the current capability session.
- Nearby now distinguishes genuine zero-result searches, HTTP/provider failures, and provider-data parse failures, and exposes the provider diagnostic rather than collapsing them all into `No caches`.
- Provider editor includes `Test public API key`, which makes a lightweight public OKAPI request and reports explicit success/failure before account OAuth is attempted.


## v1.1.5 chosen search centre
- Native `geocache.nearby` supports both device location and a chosen search centre.
- `Choose place` accepts a place/postcode/address via the Android geocoder and presents matching candidates before use.
- Manual latitude/longitude is available as an offline/fallback search-centre path.
- Selecting a place immediately reruns the selected cache source around those coordinates; provider/radius refreshes retain that centre.
- Existing canonical latitude/longitude inputs and outputs are preserved for ODK/protocol compatibility; the native place picker resolves to the same coordinate contract.
