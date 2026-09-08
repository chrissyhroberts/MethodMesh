# MethodMesh Emergency Module

**Version:** 1.00  
**Status:** Development design specification  
**Module:** `emergency`  
**Canonical location:** `app/src/main/java/com/example/methodmesh/modules/emergency/`

## 1. Purpose

Emergency is an offline-first MethodMesh capability module for situations in which communications, transport, public safety, weather, health, or local infrastructure may be disrupted.

Its purpose is not to become a news application or a general survival encyclopedia. It should give a field user a small number of immediately useful answers:

1. **What is happening near me?**
2. **Where am I?**
3. **What should I do right now?**
4. **How can I get help?**
5. **Where can I go if I need to leave?**
6. **What critical information do I still have if the network disappears?**

Emergency should remain useful with no network connection, while connected services add current situational awareness where authoritative or appropriately caveated sources exist.

The guiding UX rule is:

> **When the situation gets worse, the interface gets simpler.**

---

## 2. Product principles

Emergency inherits MethodMesh doctrine:

- offline-first;
- beef first, audit/provenance second;
- minimal native UI;
- no unnecessary storage;
- no capability-specific logic in the shared MethodMesh shell;
- clean use from native, presets, protocols, schedules and widgets where applicable;
- explicit source, age and confidence for live information;
- clear failure when current information cannot be established.

Emergency adds five module-specific principles.

### 2.1 Grey is not green

Failure to retrieve current information MUST NOT silently yield a reassuring status.

The status system therefore supports:

- **GREEN** - no relevant active warning identified from available current sources;
- **AMBER** - a credible warning or incident is locally relevant or plausibly consequential;
- **RED** - an authoritative or very high-confidence hazard is immediately relevant to the user's location;
- **GREY** - current status cannot be established reliably, including stale or unavailable data.

### 2.2 Local means spatially local

Emergency notifications should be driven primarily by geographic relevance rather than country-level news interest.

A warning that intersects the current position, configured area, route corridor or explicit alert radius is more important than a severe but distant event.

### 2.3 Static guidance should remain available offline

First aid, CPR, safety and emergency reference content should be cached locally and versioned. Where an established authoritative source maintains guidance, MethodMesh should adapt, package or link to that guidance rather than inventing its own clinical doctrine.

### 2.4 Exit infrastructure is different from dense local services

The base application may ship with a compact global index of sparse strategic exit infrastructure. Dense POI categories such as hospitals, pharmacies and police stations should not be globally baked in.

### 2.5 Operational status is not inferred from existence

An airport, port or border crossing being present in the offline database means only that it exists at the last dataset refresh. It MUST NOT be represented as open, safe, reachable or operational unless current evidence supports that claim.

---

## 3. Module boundary

Emergency should be implemented as one self-contained MethodMesh module.

Recommended shape:

```text
modules/emergency/
  docs/
    README_Emergency.md
    example_odk_emergency.xlsx
    VALIDATION.md
    ROADMAP_NOTE.md
    ATTRIBUTION.md
    THIRD_PARTY_NOTICES.md
  data/
    emergency_reference_manifest.json
    exit_poi_core.*
  EmergencyModule.kt
  EmergencyCapabilityScreen.kt
  EmergencyDashboard.kt
  EmergencyStatusRepository.kt
  EmergencyReferenceRepository.kt
  EmergencyExitRepository.kt
  EmergencyVaultRepository.kt
  EmergencyWidgetProvider.kt
  EmergencyShadeService.kt
  EmergencyModels.kt
  EmergencySources.kt
  EmergencyStorage.kt
  ...module-owned helpers
```

Exact implementation details may differ, but the module must not require the shared HomeScreen or dashboard to learn special facts about Emergency.

---

## 4. User-facing surfaces

Emergency has six primary surfaces.

### 4.1 Emergency dashboard

The native Emergency dashboard is the module control centre.

At minimum it shows:

- current RAG/Grey local status;
- current location and Plus Code;
- time and age of last successful situational update;
- urgent shortcuts;
- local warnings/events where present;
- offline preparedness state;
- exit-strategy entry point;
- reference entry point;
- vault entry point.

Suggested top-level actions:

- **SOS / location**
- **First aid**
- **CPR**
- **Exit strategy**
- **Offline map**
- **My documents**

Main returned values should follow normal MethodMesh output behaviour. Tapping a scalar such as a Plus Code or coordinate copies the useful value alone. Images/maps should expose share/save actions. Maps should support smooth pan/zoom and full-screen use.

### 4.2 Home-screen widget

The widget is a first-class Emergency surface.

Recommended sizes:

**Compact**

- RAG/Grey status;
- one emergency action;
- last update age.

**Medium**

- RAG/Grey status;
- most important local warning if present;
- current Plus Code/location shortcut;
- First Aid / Exit / Map actions.

**Large**

- status;
- current warning summary;
- offline pack readiness;
- nearest strategic exit POIs;
- user-configured emergency contact/action.

The widget should not expose sensitive vault content.

Widget-launched one-shot actions should return to the Android desktop after completion.

### 4.3 Persistent notification shade

Emergency may optionally run a persistent status notification during travel, field deployment, or an active local warning.

Normal state example:

```text
MethodMesh Emergency  ● GREEN
No active local warning
Nearest airport: 18 km NE
Offline pack: ready
Updated: 07:42
```

Warning state example:

```text
MethodMesh Emergency  ● AMBER
Flood warning intersects current area
14:00-22:00
VIEW · MAP · LOCATION
```

The persistent shade must be user-enabled and easy to disable. It should not imply continuous precise-location disclosure to third parties.

### 4.4 30-second emergency guidance

Every critical guide should support three levels:

1. **NOW** - immediate imperative actions;
2. **30 SEC** - concise illustrated algorithm;
3. **FULL GUIDE** - detailed reference and source information.

This applies especially to CPR, choking, major bleeding, burns, seizures, stroke, anaphylaxis, heat/cold emergencies and similar time-critical subjects.

### 4.5 Exit Strategy

Exit Strategy provides offline access to strategic routes out of an area.

The baked-in global dataset should remain deliberately sparse.

Core categories:

- civilian airports;
- major regional/international airfields;
- major seaports and ferry terminals;
- official land-border crossings;
- embassies and consulates.

Potential later inclusion, only if compact and well sourced:

- strategically important national rail termini;
- strategically important intercity bus termini.

Explicitly NOT part of the globally baked-in core:

- hospitals;
- pharmacies;
- AEDs;
- police stations;
- fire stations;
- shelters;
- fuel stations;
- routine local transport stops.

These belong in regional or user-downloaded packs.

Exit Strategy should normally show several alternatives rather than a single recommendation. Suggested default:

- nearest 3 airports;
- nearest 2 official land crossings where relevant;
- nearest 2 ports/ferry terminals where relevant;
- nearest embassy/consulate relevant to the user's configured nationality where available.

Each POI detail view should provide:

- name;
- type;
- coordinates;
- Plus Code;
- straight-line distance;
- bearing;
- offline map location;
- copy/share;
- navigation if an offline or available navigator can accept the target;
- source dataset;
- data age;
- explicit distinction between **known location** and **known operational status**.

### 4.6 Personal emergency vault

The vault stores user-selected emergency material locally and encrypted.

Possible content:

- passport identity page image;
- visa/residence permit;
- travel insurance details;
- driving licence;
- emergency contacts;
- user-entered medical/emergency information;
- prescriptions or medication list;
- vaccination records;
- booking/transport details;
- embassy/consular contact details;
- free-form emergency notes.

Vault principles:

- encrypted at rest;
- biometric/PIN protected where supported;
- no cloud sync by default;
- no inclusion in routine MethodMesh audit payloads;
- no exposure through ODK unless a future, explicit, narrow export function is designed;
- no sensitive content on widgets or notifications;
- user controls deletion and export.

An optional lock-screen-safe Emergency Card may contain only fields explicitly selected by the user for disclosure.

---

## 5. Data architecture

Emergency uses four distinct data classes.

### 5.1 Static authoritative reference

Examples:

- first aid;
- CPR/AED;
- choking;
- bleeding;
- burns;
- seizures;
- stroke;
- heat/cold illness;
- emergency preparedness;
- natural hazard actions.

Each packaged guide should carry:

- source organisation;
- source title/identifier;
- source URL or canonical reference;
- source publication/revision date where available;
- MethodMesh retrieval/build date;
- content version;
- licensing/attribution requirements;
- next review/refresh policy.

### 5.2 Baked-in global strategic POI core

This is a compact offline index designed to answer "where are plausible ways out?" even on a fresh install with no network and no prepared local pack.

A minimal record may contain:

```text
poi_id
name
category
country_code
latitude
longitude
optional_iata_icao_or_official_code
source_id
source_dataset_version
```

Avoid unnecessary descriptive metadata in the baked-in asset.

### 5.3 Refreshable regional cache / downloadable emergency pack

A regional pack can be richer and may include:

- hospitals/emergency departments;
- pharmacies;
- AEDs where suitable datasets exist;
- police/fire/emergency services;
- shelters;
- fuel;
- detailed transport nodes;
- road network/offline routing data;
- detailed offline map data;
- local emergency numbers;
- country-specific official guidance;
- embassy/consular information;
- richer strategic POI metadata.

The user should be able to **Prepare this location** or configure saved regions before travel.

### 5.4 Live situational data

Potential classes include:

- severe weather warnings;
- floods;
- earthquakes;
- cyclones;
- wildfire;
- tsunami;
- volcanic hazards;
- civil disorder/political violence;
- official evacuation/shelter instructions;
- transport or border operational notices where reliable sources exist.

Each event should preserve:

- event ID;
- category;
- severity;
- geometry or location;
- start/end/issued times;
- source;
- source confidence/authority class;
- retrieval time;
- expiry/staleness policy;
- original source link or reference;
- reason for local relevance.

---

## 6. Source confidence model

Emergency must not present all feeds as equivalent.

Suggested source classes:

### A. Authoritative instruction

Examples: national weather warning, civil protection evacuation order, official hazard agency alert.

May drive RED/AMBER directly according to severity and spatial relevance.

### B. Authoritative situational report

Government, UN, recognised emergency coordination body or equivalent factual report.

May affect status but should preserve wording and scope.

### C. Verified structured event feed

Reputable maintained incident datasets or professionally curated event feeds.

May contribute to situational awareness. Should not be rendered as an official instruction.

### D. Open-source/unverified report

May be shown only with explicit caveats and conservative notification behaviour.

Should not independently produce a RED state without corroboration or a strong user-configured policy.

---

## 7. Local relevance engine

The local status engine evaluates events against:

- current location;
- user-defined home/work/hotel/field sites;
- saved travel regions;
- optional route corridor;
- user-defined alert radius where appropriate;
- source geometry such as warning polygons.

Where third-party API calls require location, MethodMesh should use the shared location-privacy rule and avoid silently disclosing exact GPS. Polygon intersection should preferably be calculated locally after retrieving regional/event geometry.

Every alert shown to the user should be able to explain:

> **Why you are seeing this**

Examples:

- current location intersects warning polygon;
- event reported 1.8 km from current location;
- event falls inside configured 5 km radius;
- warning intersects saved hotel region;
- route corridor intersects warning area.

---

## 8. Notification policy

Emergency should resist alert fatigue.

Notify when:

- a new RED event becomes locally relevant;
- a materially changed AMBER event becomes locally relevant;
- an existing warning escalates;
- a local evacuation/shelter instruction is issued;
- a severe weather/hazard polygon newly intersects a configured location;
- live status becomes unavailable during an active monitored emergency and the loss of currency matters.

Do not notify merely because:

- a country-level news event exists;
- a distant incident is severe;
- a feed refreshed with no meaningful local change;
- the same unchanged alert has been repeatedly polled.

Deduplicate by event identity and material state change.

---

## 9. Offline behaviour

With no network, Emergency should still provide:

- static emergency reference;
- CPR/first-aid quick guides;
- last cached local status clearly marked with age;
- current GPS where device services allow;
- Plus Code calculation;
- baked-in strategic exit POIs;
- downloaded regional emergency pack;
- offline map/routing if available;
- vault;
- emergency contacts;
- location sharing through any locally available Android share/SMS mechanism;
- previously downloaded local emergency numbers and guidance.

If live status is stale beyond policy, the status becomes **GREY**, not GREEN.

---

## 10. Emergency location card

Emergency should expose a compact, shareable location result containing user-selected components such as:

```text
51.50742, -0.12784
9C3XGV2C+J4
14:32 local
```

Default copy behaviour should copy the primary selected location value only. Sharing may use a concise human-readable card.

Potential actions:

- Copy coordinates;
- Copy Plus Code;
- Share location;
- Navigate back here;
- Set rendezvous point.

---

## 11. Rendezvous and check-in

Users may configure named emergency points:

- Home;
- Work;
- Hotel;
- Field base;
- Meeting Point A;
- Meeting Point B;
- Embassy;
- custom point.

Emergency may provide a one-tap check-in such as:

```text
I'm safe at 14:32.
Location: 9C3XGV2C+J4
```

and a request-help variant containing location plus a user-selected short status.

MethodMesh does not require a server for this. It uses the Android share surface/SMS-capable apps.

---

## 12. Power and communications resilience

Emergency should support a low-power mode or at minimum power-conscious behaviour:

- avoid unnecessary continuous GPS;
- use event-driven or scheduled location refresh appropriate to active monitoring;
- avoid rapid background polling;
- expose age of data;
- preserve cached information;
- allow manual refresh;
- favour compact text/vector guidance over media-heavy content;
- avoid video auto-download.

Optional manual tools may include:

- torch;
- screen beacon;
- audible alarm/whistle;
- compass/bearing.

Broadcasting light/sound must never be automatic.

---

## 13. Method IDs and output contracts

Initial module methods may include:

### `emergency.status`

Core result:

```text
emergency_status = GREEN | AMBER | RED | GREY
```

Optional useful fields:

```text
primary_alert_title
primary_alert_category
primary_alert_distance_m
primary_alert_source
status_updated_at
status_age_seconds
```

Audit/full JSON contains source list, event IDs, geometries/relevance rule, retrieval times and diagnostics.

### `emergency.location`

Core useful outputs:

```text
latitude
longitude
plus_code
```

### `emergency.exit.find`

Core useful output is a small ranked collection of strategic exit POIs.

Each result contains at minimum:

```text
name
category
latitude
longitude
distance_m
bearing_deg
```

### `emergency.reference.open`

Primarily a native/preset action opening a selected offline guide. External/ODK use may be unnecessary in v1.

### Vault methods

The vault should not initially expose a generic external intent method. Keep it module-internal until a specific safe use case exists.

---

## 14. Presets, protocols, schedules and external callers

Useful presets may include:

- Emergency status at current location;
- Share my location;
- Open CPR;
- Open first aid;
- Find nearest exit options;
- Navigate to Meeting Point A;
- Check status around saved field site.

Schedules may support periodic status refresh during an explicitly enabled deployment/travel period.

ODK/XLSForm support is plausible for `emergency.location`, `emergency.status` and possibly `emergency.exit.find`, but should remain narrow. Emergency should not turn ODK into a live emergency-monitoring frontend.

External return should follow MethodMesh convention: useful scalar/result first, optional `methodmesh_full_json` second.

---

## 15. Settings

Settings should use `MethodSetting` and fixed-choice controls.

Potential settings:

- enable persistent shade;
- live alert categories;
- alert radius where source geometry is not available;
- saved monitoring locations;
- nationality/consular preference for embassy ranking;
- downloaded emergency packs;
- automatic regional refresh policy;
- background refresh frequency/power policy;
- lock-screen Emergency Card enabled;
- low-power mode;
- source-class display preferences.

Sensitive values must not leak into logs, JSON or external returns.

---

## 16. Privacy and security

Emergency may process particularly sensitive location and identity data.

Requirements:

- exact location should not be silently sent to third-party providers;
- prefer regional/bounded queries and local geometry intersection;
- vault content encrypted at rest;
- no vault content in analytics/debug logs;
- no vault content in widgets/notification shade;
- no automatic cloud backup unless explicitly designed and user-enabled;
- document backup/restore behaviour clearly;
- remove temporary decrypted files promptly;
- screenshots of sensitive vault screens should be suppressible;
- exports require explicit user action.

---

## 17. Attribution and content maintenance

Emergency depends on maintained third-party guidance and data. Module documentation MUST list for every bundled/queried source:

- provider;
- dataset/content name;
- licence/terms;
- attribution requirement;
- offline/online role;
- data sent off-device;
- refresh cadence;
- staleness policy;
- geographic coverage;
- known limitations.

Static medical and emergency guidance requires a documented update process. Content that cannot be legally redistributed may instead be represented through locally authored navigation/summary material that does not reproduce restricted content, with source links and user-download options as appropriate.

---

## 18. Failure semantics

Examples:

**Network unavailable**  
Use cached data; show cache age. If freshness threshold exceeded, status GREY.

**Location unavailable**  
Show status for last known/saved location only if explicitly labelled. Do not imply it is current.

**Source unavailable**  
Continue with remaining sources and reduce confidence if policy requires. Preserve diagnostics in details.

**Offline POI dataset old**  
POIs remain usable as locations but display dataset version/age in details.

**No route data**  
Provide straight-line distance, bearing and coordinates. Do not fabricate a road route.

**Airport operational state unknown**  
Display `Operational status: unknown`, never `Open`.

---

## 19. Development sequence

### Phase A - offline core

- module shell;
- Emergency dashboard;
- emergency.location;
- static emergency reference pack framework;
- CPR/first-aid quick guide framework;
- baked-in strategic exit POI core;
- Exit Strategy list/map;
- encrypted vault skeleton;
- home-screen widget static/offline state;
- persistent shade with local/offline readiness state.

### Phase B - live hazards

- weather warning source adapter;
- global disaster/hazard source adapter;
- source confidence model;
- local relevance engine;
- RAG/Grey engine;
- deduplicated notifications;
- cache/freshness semantics.

### Phase C - richer preparedness

- downloadable regional emergency packs;
- dense local POIs;
- offline routing;
- rendezvous/check-in;
- emergency card;
- travel/deployment monitoring mode;
- additional national data adapters.

---

## 20. Acceptance criteria for Development admission

Emergency v1 is acceptable as a Development capability when:

- it lives entirely under one module folder except for genuinely generic framework contracts;
- no capability-specific HomeScreen/dashboard logic is added;
- the module builds;
- native UX is coherent and state survives rotation;
- offline core works without network;
- strategic POI core is available on a fresh install;
- live-data failure produces GREY/stale state rather than false GREEN;
- widget and persistent shade never expose vault content;
- copy/share actions return useful values first;
- map surfaces are pan/zoom/full-screen capable;
- source, age and confidence are available in details;
- operational status is never inferred from POI existence;
- third-party attribution/licensing is documented;
- tests cover status freshness, local relevance, POI ranking, privacy boundaries and sensitive-data exclusion.

---

## 21. Initial design judgement

Emergency should feel less like a collection of emergency features and more like a resilient **control surface for a bad day**.

Its strongest property is graceful degradation:

```text
Live authoritative data
        ↓
Cached situational data
        ↓
Downloaded regional emergency pack
        ↓
Baked-in global exit infrastructure
        ↓
Static emergency reference + local vault + GPS/Plus Code
```

The module remains useful at every layer.
