# MethodMesh Emergency Module

**Version:** 2.00  
**Status:** Development design specification  
**Module:** `emergency`  
**Supersedes:** Emergency v1.00  
**Canonical location:** `app/src/main/java/com/example/methodmesh/modules/emergency/`

## 1. Purpose

Emergency is an offline-first MethodMesh module for preparedness and immediate field use during medical emergencies, severe weather, natural hazards, civil disruption, transport interruption or communications failure.

It is intentionally not a general news reader and not a giant survival encyclopedia.

It answers six operational questions:

1. **What is happening near me?**
2. **How current and complete is that information?**
3. **Where am I?**
4. **What should I do right now?**
5. **Where can I go if I need to leave?**
6. **What critical information remains available if connectivity disappears?**

Guiding rule:

> **When the situation gets worse, the interface gets simpler.**

---

## 2. Two independent status systems

Emergency MUST NOT collapse preparedness and external risk into one light.

The dashboard, widget and shade expose two distinct states.

### 2.1 Local risk state

```text
GREEN  No locally relevant active warning identified under current complete-enough coverage.
AMBER  Locally relevant warning/incident requiring awareness or preparation.
RED    Immediate/high-severity locally relevant warning or authoritative instruction.
GREY   Current local risk cannot be established with sufficient coverage/freshness.
```

Colour is always accompanied by text/iconography for accessibility.

### 2.2 Preparedness state

```text
READY      Critical offline resources for the active region are present and current enough.
PARTIAL    Some useful resources are present but one or more configured components are absent/stale.
MINIMAL    Only the built-in global emergency core is available.
```

Example:

```text
● GREEN  LOCAL RISK
READY    OFFLINE PACK
Updated 07:42
```

This prevents a current low-risk situation from being confused with good emergency preparedness.

---

## 3. Explainable status engine

Emergency does not use a single opaque risk score.

Every candidate warning/event is evaluated across separate dimensions:

1. **hazard severity**;
2. **source authority/confidence class**;
3. **spatial relevance**;
4. **temporal freshness**;
5. **source-specific spatial uncertainty**;
6. **coverage completeness for that hazard domain**.

The final RAG state is produced by explicit rules.

Every displayed status must support **Why this status?**

Example:

```text
AMBER
Severe rain warning intersects your current location.
Source class: authoritative warning
Issued: 13:10
Valid: 14:00-22:00
Retrieved: 13:17
```

or:

```text
GREY
Current civil-security status unavailable.
Weather coverage current.
Natural-hazard coverage current.
Civil-security feed last updated 11 h ago and exceeds freshness policy.
```

---

## 4. Coverage and GREEN semantics

GREEN is a positive claim and therefore requires sufficient current coverage.

The module maintains hazard-domain state independently, for example:

- severe weather;
- flood;
- earthquake/tsunami;
- cyclone;
- wildfire;
- volcanic hazard;
- civil-security incident;
- official evacuation/shelter information.

Each domain may be:

```text
CURRENT_NO_ALERT
CURRENT_ALERT
STALE
UNAVAILABLE
NOT_APPLICABLE
DISABLED_BY_USER
```

Overall GREEN is permitted only when all configured critical domains are either:

- `CURRENT_NO_ALERT`, or
- `NOT_APPLICABLE`, or
- intentionally `DISABLED_BY_USER` and clearly excluded from the overall coverage statement.

A critical configured domain that is `STALE` or `UNAVAILABLE` normally forces overall GREY unless another domain independently produces AMBER/RED.

AMBER/RED therefore outrank GREY, but the UI should still indicate incomplete coverage.

---

## 5. Source authority classes

### Class A - authoritative instruction

Official warning, evacuation order, shelter instruction or equivalent.

May directly produce AMBER/RED when severity and spatial rules are met.

### Class B - authoritative situational information

Government, civil-protection, UN or recognised emergency coordination reporting.

May affect state according to source-specific policy.

### Class C - professionally curated structured event feed

Useful for situational awareness but not equivalent to an official instruction.

May produce AMBER under conservative rules. RED usually requires corroboration or explicit source-specific authority.

### Class D - open/unverified reporting

May be displayed as unverified context. It should not independently produce RED.

For civil/political violence, each adapter must define:

- location precision/uncertainty;
- reporting delay assumptions;
- maximum alertable event age;
- duplicate/corroboration logic;
- whether point-distance alerts are permitted;
- minimum confidence for notification.

---

## 6. Primary user surfaces

### 6.1 Emergency dashboard

The module dashboard is the main control centre.

Top section:

```text
EMERGENCY
● GREEN  Local risk
READY    Offline preparedness
Last checked 07:42
```

Primary actions:

- **SOS / Location**
- **First Aid**
- **CPR**
- **Exit Strategy**
- **Prepare this location**
- **Offline Map**
- **My Documents**

When a warning exists, the alert replaces low-priority dashboard content rather than being buried below it.

Scalar outputs are tappable and copy their useful value alone. Maps/images expose share/save where relevant and support smooth full-screen pan/zoom.

### 6.2 Home-screen widget

Emergency's widget is first-class but must not teach the shared MethodMesh shell anything about Emergency.

Preferred architecture:

- keep Emergency-specific widget rendering, actions and data inside the module;
- use only generic module discovery/lifecycle hooks from shared code;
- if a generic `ModuleStatusSurface` contract is introduced, it MUST be capability-neutral and demonstrably reusable by other modules.

Widget sizes:

**Compact**

```text
● GREEN
Emergency
07:42
```

Tap opens Emergency. Optional configurable secondary action: Location or CPR.

**Medium**

```text
● GREEN  LOCAL
READY    OFFLINE
No current warning
[LOCATION] [FIRST AID] [EXIT]
```

**Large**

Adds:

- primary local warning;
- strategic exit summary;
- pack readiness;
- user-selected emergency contact/action;
- current Plus Code shortcut.

No vault content is ever rendered directly.

### 6.3 Persistent notification shade

The shade operates only during an explicit **Emergency Monitoring Session**.

Monitoring modes:

```text
OFF
TRAVEL
FIELD DEPLOYMENT
ACTIVE WARNING
```

The user explicitly starts/stops Travel or Field Deployment. Active Warning may be offered when a relevant warning is received, but should not silently create permanent monitoring.

Each mode defines a power-aware refresh policy. Exact cadence is implementation/policy configuration, not hard-coded doctrine.

Example:

```text
MethodMesh Emergency  ● AMBER
Flood warning intersects current area
Offline pack READY
Updated 6 min ago
[VIEW] [MAP] [LOCATION]
```

Persistent notification content must avoid sensitive identity/vault information.

---

## 7. Built-in global emergency core

A fresh installation with no network and no downloaded packs must still provide a small high-value global core.

The core contains:

1. static emergency reference essentials;
2. country/territory emergency telephone-number table;
3. sparse strategic exit POIs;
4. local GPS/Plus Code capability integration;
5. minimal preparedness/check-in tools.

The base asset has an explicit size budget. Dataset growth must be reviewed rather than allowed to expand opportunistically.

---

## 8. Baked-in emergency numbers

Emergency numbers are sufficiently small and important to ship globally.

Minimal record:

```text
jurisdiction_code
service_type
number
notes_code
source_id
source_dataset_version
```

Service types may include:

- general emergency;
- police;
- fire;
- ambulance/medical;
- coastguard where nationally relevant;
- other nationally standard emergency service.

The module should clearly handle jurisdictions where:

- multiple numbers coexist;
- 112 is supported but not primary;
- mobile/landline behaviour differs;
- regional exceptions exist.

The UI should not imply universal reachability when the source carries caveats.

---

## 9. Baked-in strategic exit POI dataset

### 9.1 Purpose

The baked-in POI core answers:

> **Where are plausible physical exit points if I have no network and prepared nothing?**

It is not a complete POI database.

### 9.2 Included categories

- civilian airports with scheduled or recognised public passenger function;
- major regional/international civilian-capable airports according to the selected source rules;
- major seaports/ferry terminals with public passenger relevance;
- official land-border crossing points where a suitable global/regional source can be legally redistributed;
- embassies and substantive consular missions where suitable data exist.

Military-only facilities should not be included merely because they have runways.

Honorary consulates may be omitted from the global core or separately typed so they are never presented as equivalent to a full diplomatic mission.

### 9.3 Excluded global categories

Do NOT bake in globally:

- hospitals;
- pharmacies;
- AEDs;
- police/fire stations;
- shelters;
- fuel stations;
- ordinary rail/bus stops;
- routine local transport POIs.

### 9.4 Deterministic dataset build

The module repository should include a reproducible build description for the baked asset:

```text
source dataset(s)
source version/date
licence
inclusion rules
exclusion rules
normalisation steps
record count by category/country
resulting file size
checksum
build timestamp
```

The generated asset should have a manifest and checksum so releases can identify exactly what shipped.

### 9.5 POI semantics

An offline record establishes only:

- location;
- category;
- last dataset provenance.

It does NOT establish:

- current opening;
- flight/ferry service;
- border openness;
- road accessibility;
- safety;
- visa/admission eligibility.

UI wording should therefore use **Known location** and **Operational status unknown** unless current live evidence is available.

---

## 10. Exit Strategy

Exit Strategy shows multiple options and lets the user choose; it does not claim to calculate a safe evacuation route.

Default ranking should favour useful breadth rather than a single nearest POI.

Suggested list:

- nearest 3 airports;
- nearest 2 relevant ports/ferry terminals;
- nearest 2 official land crossings;
- nearest relevant embassy/consulate.

Categories absent from the region are simply omitted.

Each result provides:

```text
name
category
coordinates
Plus Code
straight-line distance
bearing
source/version
known-location age
operational status: unknown/currently reported value
```

Actions:

- **Map**
- **Navigate**
- **Copy location**
- **Share**

Navigation is explicitly navigation to a target, not a guarantee that the route is safe or open.

If no offline road data exist, the fallback is bearing + distance + coordinates/Plus Code rather than a fabricated route.

---

## 11. Prepare this location

`Prepare this location` is a primary preparedness workflow.

The user selects or confirms a region, then sees a compact pack plan before download.

Possible components:

- offline vector map;
- offline routing graph where supported;
- hospitals/emergency departments;
- pharmacies;
- AEDs where suitable;
- police/fire/emergency service POIs;
- shelters where authoritative data exist;
- fuel/transport POIs if useful;
- richer airport/port/border metadata;
- embassy/consular metadata;
- local emergency telephone details;
- country/region-specific emergency guidance;
- current authoritative hazard source adapters/configuration.

The user sees estimated size before download.

Pack controls:

- download;
- refresh;
- delete;
- last updated;
- source list;
- attribution;
- readiness state.

Saved travel/field regions should be manageable in Settings/shared offline-map infrastructure where appropriate, while Emergency-specific pack composition remains module-owned.

---

## 12. Pack manifests

### 12.1 `EmergencyPoiPack`

Every baked-in or downloaded POI pack has:

```text
pack_id
schema_version
region
created_at
source_versions[]
category_counts
bbox_or_region_definition
licences[]
attribution[]
file_checksums[]
pack_checksum
```

### 12.2 `EmergencyContentPack`

Every static guidance pack has:

```text
pack_id
schema_version
jurisdiction_or_scope
content_items[]
source_organisations[]
source_publication_versions[]
methodmesh_adaptation_version
validated_at
review_due_at
licences[]
attribution[]
file_checksums[]
pack_checksum
```

If review is overdue, the content remains available offline but displays **Review due** in details. Safety-critical guidance is not silently deleted because connectivity is absent.

---

## 13. Emergency reference library

The first release should remain curated and small.

Priority quick guides:

- adult CPR/AED;
- choking;
- severe bleeding;
- burns;
- seizure;
- stroke recognition/action;
- heart attack recognition/action;
- anaphylaxis;
- drowning;
- heat illness;
- hypothermia;
- immediate flood/earthquake/wildfire/cyclone safety actions.

Each critical guide follows:

```text
NOW
30 SEC
FULL GUIDE
SOURCE / VERSION
```

The short layers are MethodMesh adaptations of authoritative guidance and require content review/validation before production promotion.

---

## 14. Local relevance engine

Emergency evaluates event geometry locally whenever practical.

Inputs may include:

- current GPS position;
- saved home/work/hotel/field location;
- active prepared region;
- explicit alert radius;
- route corridor if the user has chosen one;
- warning polygon or event point/uncertainty geometry.

For third-party APIs, MethodMesh follows the shared location privacy rule and should avoid silently sending exact GPS. Prefer queries by region/bounding area and local intersection where the provider allows it.

Every event stores a `relevance_reason`, e.g.:

```text
CURRENT_LOCATION_INTERSECTS_POLYGON
EVENT_WITHIN_RADIUS
SAVED_LOCATION_INTERSECTS_POLYGON
ROUTE_CORRIDOR_INTERSECTS_POLYGON
REGION_LEVEL_ALERT
```

For imprecise point-event feeds, distance should account for source uncertainty rather than pretending the point is exact.

---

## 15. Notification policy

Notifications should be rare and consequential.

Notify on:

- new locally relevant RED event;
- new materially relevant AMBER event meeting source-specific threshold;
- escalation in severity;
- authoritative evacuation/shelter instruction;
- warning geometry newly intersecting current/saved/active region;
- major material change to an active local warning;
- critical loss of current coverage during an already active monitoring session where stale information itself is important.

Do not notify on:

- distant country-level events;
- unchanged feed refresh;
- duplicates;
- old political-violence incidents outside the source-specific age policy;
- low-confidence point events lacking required corroboration;
- preparedness-pack update availability unless the user has opted into maintenance reminders.

---

## 16. Emergency location and check-in

`emergency.location` returns useful location data:

```text
latitude
longitude
plus_code
captured_at
```

Native UI keeps capture metadata behind details.

Actions:

- Copy coordinates;
- Copy Plus Code;
- Share location;
- Save as rendezvous;
- Navigate to saved rendezvous.

Check-in should use the Android share surface and require no MethodMesh server.

Example beef:

```text
I'm safe at 14:32.
9C3XGV2C+J4
```

A separate user-selected help status may be appended, e.g. `injured`, `stranded`, `need collection`, without requiring free-form typing in the urgent path.

---

## 17. Rendezvous points

Users may save:

- Home;
- Work;
- Hotel;
- Field base;
- Meeting Point A;
- Meeting Point B;
- Embassy;
- custom point.

These are local user data. They should be easy to copy/share/export and easy to delete.

---

## 18. Vault v2 scope

The secure vault is deliberately staged.

Initial v2 scope:

- passport identity-page image slot;
- travel insurance document/image slot;
- one user-defined critical document slot;
- emergency contact card;
- optional lock-screen-safe Emergency Card containing only explicitly disclosed fields.

Requirements:

- encrypted at rest using Android-supported secure key storage;
- biometric/PIN gate where available;
- no cloud sync by default;
- no ODK/external intent exposure;
- no routine audit JSON inclusion;
- no notification/widget preview;
- explicit export and delete;
- temporary decrypted artefacts removed promptly;
- sensitive screens can request screenshot suppression.

Future versions may expand document categories only after backup/key-loss/recovery semantics are deliberately designed.

---

## 19. Offline behaviour and graceful degradation

When connectivity disappears, Emergency continues down this stack:

```text
Live authoritative data
        ↓
Cached situational data, with visible age
        ↓
Downloaded EmergencyPoiPack / EmergencyContentPack
        ↓
Baked-in global exit POIs + emergency numbers
        ↓
Static emergency essentials + GPS/Plus Code + vault
```

There is no transition from unavailable data to false reassurance.

If freshness policy is exceeded, affected hazard domains become STALE and overall state may become GREY.

---

## 20. Power policy

Emergency should not run continuous high-frequency GPS or polling by default.

Monitoring Sessions allow power-aware behaviour appropriate to context.

Principles:

- use coarse/last-known location where sufficient;
- request precise fixes only when needed;
- retrieve warning regions efficiently;
- calculate intersection locally;
- back off on stable conditions;
- increase attention only during an active local warning where Android policy permits;
- manual refresh always available;
- display data age rather than hiding slower refresh.

Optional emergency tools such as torch/beacon/alarm remain manually triggered.

---

## 21. Method IDs

### `emergency.status`

Core result:

```text
emergency_status = GREEN | AMBER | RED | GREY
```

Useful secondary fields:

```text
preparedness_status = READY | PARTIAL | MINIMAL
primary_alert_title
primary_alert_category
primary_alert_source
primary_alert_distance_m
status_checked_at
```

Full JSON includes:

- hazard-domain coverage states;
- source classes;
- source issue/retrieval times;
- freshness decisions;
- event IDs;
- relevance reasons;
- data-readiness details;
- diagnostics.

### `emergency.location`

```text
latitude
longitude
plus_code
captured_at
```

### `emergency.exit.find`

Returns a small ranked collection of strategic POIs with useful display fields plus structured full JSON.

### `emergency.reference.open`

Native/preset-oriented action to open a selected packaged guide.

### `emergency.pack.prepare`

Native/preset-oriented workflow for preparing a specified region. ODK use is not a v2 priority.

Vault remains module-internal in v2.

---

## 22. Presets, protocols, schedules and widgets

Useful presets:

- Share my location;
- Open CPR;
- Open severe-bleeding guide;
- Find exit options;
- Check local status;
- Navigate to Meeting Point A;
- Prepare current travel region.

Protocols may chain Emergency primitives with other MethodMesh capabilities.

Schedules may trigger refresh/check actions only with explicit user configuration. They use normal MethodMesh closeout semantics.

Widget-launched one-shot actions return to desktop when complete.

ODK support should focus narrowly on scalar/location/status return use cases. `methodmesh_full_json` remains optional audit detail, never the primary field result.

---

## 23. Settings

Use MethodSetting controls.

Potential settings:

- persistent shade enabled;
- monitoring mode/session controls;
- hazard categories enabled;
- alert radius where appropriate;
- saved monitored locations;
- nationality/consular preference;
- prepared regions/packs;
- auto-refresh policy;
- low-power policy;
- lock-screen Emergency Card enabled;
- source-detail verbosity.

Fixed choices use toggles/dropdowns/checkboxes, not raw text.

---

## 24. Privacy

Emergency has a stricter privacy posture than most MethodMesh capabilities.

Requirements:

- exact GPS not silently disclosed to remote providers;
- nationality/consular preference remains local unless explicitly needed by a user-requested service;
- sensitive vault content excluded from logs, JSON, clipboard and external intents unless the user explicitly exports it;
- event-source credentials are referenced, not embedded in exported definitions;
- location history is not retained merely because monitoring occurred;
- alert evaluation should avoid creating a detailed movement history when only current relevance is required.

---

## 25. Failure semantics

### Current status unavailable

Show GREY with the failed/stale domains and last successful update age.

### Device location unavailable

Offer saved/last-known location with an explicit label. Never present it as current.

### One source fails

Continue other domains. Coverage state reflects the failure.

### POI dataset is old

Location remains usable. Details show dataset age/version.

### Operational status unknown

Say exactly that.

### Offline route missing

Return bearing, straight-line distance and destination coordinates/Plus Code.

### Content pack review overdue

Keep guidance available but mark `Review due` in details.

### Monitoring service stopped by Android/system policy

Persistent shade/status must indicate monitoring is no longer active when the app can detect this. Do not leave a stale visual state suggesting active monitoring.

---

## 26. Validation and tests

At minimum test:

### Risk/freshness

- GREEN requires configured critical-domain coverage;
- stale critical source causes GREY;
- RED/AMBER outrank GREY while incomplete coverage remains visible;
- warning expiry behaves correctly across timezone/DST boundaries;
- source issue time and retrieval time remain distinct.

### Spatial relevance

- point-radius decisions;
- polygon intersection;
- saved-location intersection;
- uncertain point-event handling;
- dateline/longitude edge cases where applicable.

### POIs

- deterministic pack regeneration;
- category inclusion/exclusion;
- nearest-option ranking;
- embassy nationality filter/ranking;
- operational state never inferred from existence;
- baked asset stays within approved size budget.

### Offline

- fresh install airplane mode still exposes emergency numbers, reference essentials and strategic POIs;
- downloaded pack works without network;
- stale cache visibly ages into GREY according to policy.

### Privacy/security

- vault content absent from logs/full JSON/widget/shade;
- exact location not sent to providers that only require region-level query;
- external intents cannot invoke vault reads;
- temp decrypted files are cleaned up.

### UX

- rotation/state preservation;
- widget actions return correctly;
- large tap targets;
- status accessible without relying on colour;
- critical guide opens directly to NOW/30 SEC content;
- maps pan/zoom/full-screen cleanly;
- scalar tap copies beef only.

---

## 27. Development sequence

### Phase 1 - resilient offline core

- module shell and metadata;
- Emergency dashboard;
- two-state risk/readiness model;
- emergency.location;
- baked-in emergency-number table;
- baked-in strategic exit POI pack + manifest;
- Exit Strategy;
- small EmergencyContentPack framework;
- CPR/first-aid essential guides;
- module-owned widget;
- monitoring-session model + persistent shade offline/readiness mode;
- staged encrypted vault.

### Phase 2 - live hazards

- first authoritative weather adapter;
- first global hazard adapter;
- hazard-domain coverage model;
- source authority classes;
- freshness rules;
- local relevance engine;
- deduplication;
- notifications;
- explainable `Why this status?` details.

### Phase 3 - prepared regions

- `Prepare this location`;
- EmergencyPoiPack downloads;
- dense local POIs;
- offline map/routing integration;
- regional emergency guidance;
- pack refresh/delete UX;
- preparedness-state calculation.

### Phase 4 - civil-security refinement

- curated civil/political event adapter(s);
- source-specific uncertainty;
- corroboration rules;
- conservative local notification policy;
- additional national official sources.

---

## 28. Production promotion gate

Emergency must not be promoted to Production merely because it builds.

In addition to normal MethodMesh checks, promotion requires:

- authoritative-content review completed;
- source licences/redistribution confirmed;
- offline global POI build reproducible;
- clinical quick-guide content validated against stated source version;
- no false-GREEN cases in defined failure tests;
- Android monitoring/widget behaviour tested under background restrictions;
- battery impact profiled;
- vault threat model reviewed;
- live alerts tested for stale/duplicate/geometry edge cases;
- accessibility review;
- native, preset, schedule/widget and relevant ODK paths exercised;
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

---

## 29. MethodMesh taste test

Emergency v2 should satisfy the fieldworker test:

A tired user unlocks the phone during a bad situation and can immediately see:

```text
● AMBER
Flood warning applies here
READY offline

[LOCATION] [FIRST AID] [EXIT]
```

If the network dies five seconds later, those three buttons still do something useful.

That is the core acceptance criterion.
