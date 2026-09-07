# MethodMesh Emergency module

**Status:** Development  
**Module ID:** `emergency`  
**Version:** `0.2.0`  
**Persistent notification shade:** deliberately deferred in this build

## Purpose

Emergency is an offline-first MethodMesh capability for situations in which the
network, local transport or normal information environment may be degraded. It
combines:

- an explainable local risk state (`GREEN`, `AMBER`, `RED`, `GREY`);
- a separate preparedness state (`READY`, `PARTIAL`, `MINIMAL`);
- GPS + full Plus Code emergency location capture;
- sparse offline strategic exit POIs;
- versioned emergency reference cards;
- regional emergency-pack manifests/install state;
- an encrypted on-device emergency document vault.

It is not a general news reader and it must never interpret missing/stale data
as evidence of safety.

## Golden-rule boundary

All Emergency-specific implementation lives in this module folder. It does not
add Emergency branches to HomeScreen, the module registry or the shared widget
provider.

The shared MethodMesh widget can already launch an Emergency preset. A richer
live-status home-screen widget and the persistent notification shade require a
generic host surface contract and are intentionally deferred rather than being
hard-coded into the shell.

## Methods

### `emergency.status`

Returns cached/aggregated local risk and preparedness.

Core outputs:

- `emergency_status`
- `emergency_preparedness`
- `emergency_primary_alert`
- `emergency_incomplete_critical_coverage`
- `emergency_checked_at_iso`
- `emergency_status_explanation`
- `emergency_status_audit_json`

Safety rule: `GREEN` is possible only when at least one critical domain is
configured and every configured critical domain is current-no-alert,
not-applicable or explicitly disabled. No coverage, stale coverage or an
unavailable critical source yields `GREY` unless a credible AMBER/RED alert
outranks it. The incomplete-coverage flag remains visible in that case.

### `emergency.location`

Captures GPS coordinates and calculates a full Open Location Code locally using
the existing MethodMesh Plus Code implementation.

Main native value: Plus Code. Coordinates, accuracy, capture time and audit JSON
are also returned.

### `emergency.exit.find`

Ranks deliberately sparse offline strategic POIs near the current location.
Default selection is up to:

- 3 airports;
- 2 passenger ports/ferry terminals;
- 2 official land crossings;
- 1 relevant embassy/consular mission when nationality is supplied.

An offline POI means only **known location**. `operational_status=unknown` is the
default. The module must not imply that a route, airport, port or border is open
or safe without separate live evidence.

### `emergency.reference.open`

Opens an offline reference card with four explicit layers:

1. NOW
2. 30 SEC
3. FULL GUIDE
4. source/version/review metadata

The current development core contains one populated adult CPR/AED adaptation
based on Resuscitation Council UK 2025 guidance. All other cards are deliberately
unpopulated Development slots rather than invented or unreviewed emergency
advice. Content review is a Production gate.

### `emergency.pack.prepare`

Inspects regional emergency-pack readiness. The storage/manifests and component
contract are implemented; no provider-specific map/POI network downloader is
silently faked in this Development build.

A regional pack is intended to contain richer local data such as hospitals,
pharmacies, AEDs, shelters and offline map/routing resources. Those dense
classes do **not** belong in the baked global core.

## Native dashboard

The Emergency status screen acts as the module control centre and exposes:

- SOS / Location
- CPR
- First aid
- Exit strategy
- Prepare this location
- My documents
- cached status refresh
- Why this status?

The result philosophy remains MethodMesh-standard: beef first, audit second.
Plus Codes and coordinates are copy/share oriented; provenance remains available
without dominating the primary screen.

## Monitoring sessions

The module stores the operator's monitoring intent:

- `OFF`
- `TRAVEL`
- `FIELD_DEPLOYMENT`
- `ACTIVE_WARNING`

This build does **not** create a foreground service or persistent notification
shade. That host/UI change is deferred by design.

## Live source adapters

`EmergencySourceAdapter` is the module boundary for weather, disaster and civil
security sources. Each adapter must declare:

- source ID;
- authority class A-D;
- supported hazard domains;
- freshness policy;
- source-specific spatial/corroboration semantics.

Source authority classes:

- A — authoritative instruction;
- B — authoritative situational source;
- C — curated event feed;
- D — open/unverified reporting.

Class D cannot independently produce RED. Curated class C RED requires explicit
adapter permission plus corroboration. Exact GPS should not be sent to a provider
unless its contract genuinely requires it and the user has chosen that behaviour.

No live adapter is installed in this first checked-in Development build; this
correctly produces GREY rather than a false GREEN.

## Baked strategic POI data

`data/build_emergency_core.py` is the deterministic release pipeline.

The initial airport source contract is a reviewed OurAirports `airports.csv`
snapshot. The build keeps scheduled civilian airport records and writes a compact
sorted CSV plus checksum/source manifest. Optional reviewed normalized sources can
add major passenger ports, official land crossings and diplomatic missions.

The baked layer explicitly excludes hospitals, pharmacies, AEDs, police/fire,
shelters, fuel and ordinary local transport.

Do not ingest OSM-derived extras into the baked core until the exact ODbL
redistribution/derived-database obligations for the packaging model have been
reviewed.

## Emergency document vault

The staged v2 vault supports:

- passport identity page;
- travel insurance;
- one critical document;
- emergency card.

Payloads are AES-GCM encrypted using an Android Keystore key and stored in the
app-private files area. The native UI requires biometric/device-credential
authentication before import/replace/delete operations and uses `FLAG_SECURE`
while the vault pane is displayed.

Vault bytes are never returned through AS1.00 observations/result JSON and must
not be surfaced in widgets, logs or the deferred shade.

Production review should additionally test backup/restore semantics, key loss,
large-file handling, export/delete UX and authenticated read/export flows.

## ODK / XLSForm

Example: `docs/example_odk_Emergency.xlsx`

ODK uses a `begin_group` with `appearance=field-list` and a `body::intent` such as:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='emergency.status',return_mode='flat')
```

Inputs use `input_*` names. Output children use the declared MethodMesh output
field names.

Example exit call:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='emergency.exit.find',input_nationality_iso2=${input_nationality_iso2},return_mode='flat')
```

## Validation already included

The pure Kotlin self-test covers:

- no coverage => GREY;
- complete current critical coverage => GREEN;
- stale critical coverage => GREY;
- authoritative RED outranking incomplete coverage while retaining the flag;
- open/unverified RED downgrade to AMBER;
- uncorroborated curated RED downgrade to AMBER;
- per-category exit ranking limits;
- correct short-path distance across the international dateline.

The baked-data builder has a fixture test demonstrating scheduled-service
filtering, exclusion of heliports/non-scheduled fields, deterministic output,
checksum creation and size-budget enforcement.

## Development limitations / Production gates

Do not promote this module to Production until all of the following are complete:

- Android build and instrumentation tests pass in the real repository;
- authoritative live source adapters are selected, licensed and tested;
- source freshness and location-relevance tests include false-positive and
  false-GREEN cases;
- the full global strategic POI core is generated from reviewed snapshots and
  size-checked;
- ports/borders/diplomatic datasets have explicit source/licence review;
- the global emergency-number table is sourced and jurisdictionally reviewed;
- every populated medical/survival card has content/safety review and a review
  schedule;
- regional pack download/install/delete/refresh is implemented against real
  providers;
- vault security/privacy review is complete;
- native, preset, protocol, schedule and ODK close-out behaviour is tested;
- home-screen live status widget is implemented through a generic host contract
  if adopted;
- persistent notification shade remains a separate host/UI work item.

## Offline degradation contract

The intended order of degradation is:

`live source -> cached source -> regional pack -> baked global core -> static reference/GPS/vault`

At zero connectivity the module must still provide static emergency references,
GPS/Plus Code, installed strategic exit POIs, locally stored regional resources
and the private emergency vault.


## v2.03 UI consolidation

- Every Emergency capability now carries the same module-level control-centre navigation so direct native capability launches can return to the consolidated Emergency dashboard without changing canonical method exposure for presets, protocols, schedules or ODK.
- Nationality is an optional ISO 3166-1 alpha-2 country selector with human-readable country names. It is used only to prioritise relevant embassy/consulate candidates; it does not suppress general exit infrastructure.
- Prepare Emergency Pack uses the same country selector. The selected country is stored canonically as its alpha-2 code and is propagated through `onSettingsChanged` for preset configuration.
