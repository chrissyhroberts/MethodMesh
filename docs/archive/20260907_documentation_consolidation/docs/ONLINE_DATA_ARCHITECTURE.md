# MethodMesh online data architecture

This document records the first implementation direction for online APIs, RSS/Atom feeds, cacheable external data and current-context style presets.

The attached design plan is broadly accepted, with one MethodMesh-specific correction:

- API definition creation, editing, cloning, import/export and response-tree inspection belong in **Workbench**.
- `api.get` is the runtime capability that executes API definitions.
- Preset creation is allowed both from Workbench and from the existing configure/test workflow where that is the natural user path.

The shared UI must remain generic. It should render capability and API-definition metadata; it should not know that a definition is “weather”, “GDACS”, “forex” or “RSS”.

---

## Concepts

### Capability

An executable MethodMesh operation.

Examples:

- `api.get`
- `gps_target_navigator`
- `plus_code.capture`
- `sensor.read`

### API definition

A declarative description of an external data call.

Examples:

- `openmeteo.current_weather`
- `gdacs.nearby`
- `reliefweb.recent`
- `my_project.case_counts`

An API definition is not itself a capability. It is configuration consumed by `api.get`.

### Preset

A reusable execution setup or composition. A preset may call native capabilities, `api.get`, and later tree-aware transforms.

The intended flagship preset is `current_context`: a normal bundled preset that combines GPS, weather, hazards and local deterministic calculations to answer “what is true around here, now?”

---

## First implemented foundation

The first code layer lives in:

```text
app/src/main/java/com/example/methodmesh/core/onlinedata/
```

It currently defines:

- `ApiDefinition`
- `ApiDefinitionRegistry`
- `ApiInputDefinition`
- `ApiAuthDefinition`
- `CachePolicy`
- `CacheRecord`
- `ProviderHealth`
- `ApiExecutionResult`
- `ResultTree`
- `ResultPath`
- `ResultLookup`
- `ApiGetExecutor`
- `HttpUrlConnectionOnlineHttpClient`
- `BundledApiDefinitions`
- `ApiDefinitionCodec`
- `ApiDefinitionRepository`

The most important architectural decision is that `ResultTree` preserves:

- objects;
- arrays;
- scalar leaves;
- nulls.

It does not flatten provider responses into string maps.

`ApiGetExecutor` is the first implementation of the `api.get` runtime path. It currently supports:

- declarative HTTP GET definitions;
- required/default input resolution;
- token replacement in URLs, query parameters and headers;
- credential references for query keys, header keys, bearer tokens and basic auth;
- redacted display URLs for query-string credentials;
- fresh cache reuse;
- stale-cache fallback for `FRESH_PREFERRED`;
- structured HTTP, network, parse and rate-limit errors;
- raw response preservation;
- JSON parsing into `ResultTree`.

The first bundled definitions are:

- `openmeteo.current_weather`
- `gdacs.all_events_geojson`

They are deliberately implemented as API definitions, not bespoke Kotlin capabilities.

`ApiDefinitionRepository` merges bundled definitions with user-created/imported definitions. Bundled definitions remain canonical and cloneable; imported or cloned definitions are saved as user definitions. Export/import uses `.methodmesh-api.json`-style JSON payloads with hash verification and without credential secret values.

---

## Tree path semantics

MethodMesh uses one conceptual path syntax for result references:

```text
${step.data.object.field}
${step.data.array[0].field}
${step.data.object}
```

The first parser accepts the same syntax with or without the `${...}` wrapper.

Lookups explicitly distinguish:

- value found;
- null value;
- missing path;
- type error.

This is important because a real “no records” API response is not the same as a broken request, a missing schema field or a failed network call.

---

## Cache and provenance rules

Every successful API execution should eventually produce a result identity.

Cached data has two broad classes:

- **Disposable cache** — useful for speed and offline browsing; may be evicted.
- **Research-linked result** — used by ODK, a preset output, saved observation, attestation or audit trail; must not be silently removed by normal cache cleanup.

Refreshing a source must not mutate an older research-linked result.

---

## Privacy and credentials

API definitions declare whether they send location or identifiers.

The default for bundled location-aware public API definitions should be rounded disclosure, not exact GPS. The first implemented rounding policy is a 5 km radius. `api.get` applies this before request construction, so Workbench, presets, schedules and external callers inherit the same privacy behaviour.

Credentials are referenced, not embedded:

- no credentials in exported API definitions by default;
- no credentials in provenance;
- no credentials in logs;
- redacted request displays where needed.

Bundled defaults should prefer no-key public sources where possible.

---

## RSS/Atom

RSS and Atom should share registry, network, cache, provenance and health infrastructure, but they need a specialised reader surface.

Feeds require:

- subscription state;
- read/unread/saved state;
- item identity and deduplication;
- incremental refresh;
- media policy;
- offline search.

Video auto-download must be off by default.

---

## Implementation order

1. Data model and tree-path tests. **Started.**
2. `api.get` execution engine: request construction, cache decision, HTTP, parse, raw preservation and structured failure. **Started.**
3. API registry persistence and bundled definition loading. **Started.**
4. Workbench API editor/tester and response-tree picker. **Next.**
5. Preset data-flow upgrades for named steps and tree/subtree references.
6. Initial bundled sources:
   - Open-Meteo current weather;
   - GDACS nearby events;
   - RSS/Atom feed.
7. `current_context` as a normal bundled preset.
8. Feed reader UX and offline search.
9. Provider health and repository watchdog tests.

---

## Guardrails

Do not:

- turn every public API into a bespoke capability;
- make API-definition creation a runtime capability;
- flatten JSON prematurely;
- restrict preset data flow to scalar strings;
- discard raw responses needed for audit/debugging;
- mutate old research-linked results during refresh;
- hard-code `current_context` orchestration;
- auto-subscribe users to bundled feeds;
- auto-download videos by default;
- expose credentials in logs, exports or ODK payloads;
- silently send exact GPS coordinates without showing the user that a remote service receives location.
