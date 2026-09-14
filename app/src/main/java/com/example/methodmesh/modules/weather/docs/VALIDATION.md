# Weather v0.2.6 validation record

**Review authority:** MethodMesh Master Book v1.08 FINAL, updated 2026-09-08.  
**Status:** Development module; `weather.atmosphere` and `weather.model_compare` are explicitly Experimental.

## Pass A — capability and architecture contract

- One self-contained module entry point: `WeatherModule`.
- Eleven independently callable canonical methods.
- Eleven matching native capability screens generated from the canonical method inventory.
- Eleven RIL bindings.
- Capability settings are module-owned.
- Every capability has explicit maturity and connectivity metadata through `WeatherMethodBase`.
- Dashboard is a canonical aggregation capability (`weather.dashboard`) rather than a dashboard-only bypass.
- No shared MethodMesh source file is required by this handoff.
- Weather provider logic uses generic MethodMesh online-data types and execution rather than a second ad-hoc HTTP architecture.
- `WeatherMethodBase.execute()` performs the same Weather runtime/provider resolution used by native capture, allowing supplied-input direct/headless execution.

## Pass B — output contract

Static contract audit over the final source found:

- 213 unique canonical Weather output fields across the 11 methods;
- no Weather UI reference to a capability return field absent from the declared output inventory;
- no declared non-status/non-error/non-audit output left without an explicit population path;
- no calculation output key outside the declared Weather output inventory;
- zero `TODO` / `FIXME` markers in the active Kotlin module.

The single `weather_code` string used inside chart/series JSON is an internal JSON member name, not a phantom MethodMesh return field.

## Pass C — interaction review

- Native tool/instrument view appears before advanced settings/result metadata.
- GPS and manual coordinates are available on the Weather dashboard itself.
- Individual capabilities retain their own native surfaces rather than becoming dashboard-only tools.
- Working result remains editable/refreshable until Commit.
- Commit returns the resolved canonical payload.
- External automatic-return origins return immediately once the canonical result is resolved.
- Displayed useful scalar/text values are tap-to-copy.
- Preset-fixed settings use `CapabilityScreenContext.settingShouldBeShown()`.
- Radar has a timeline scrubber across observed history and genuine provider-nowcast frames, Play/Stop animation and an in-map time/source overlay.
- Radar defaults to page-scroll-safe mode; map pan/zoom is enabled deliberately with **Move map**, which disallows parent scroll interception until the operator returns to **Scroll page** mode.
- On the canonical radar screen, choosing another frame or stopping playback re-runs resolution for that frame before Commit; displayed and returned frame time therefore agree.
- Dashboard headline metrics use a compact two-column Now / Today layout and remove the prior repeated Today card.
- Rainfall, hourly temperature and meteogram charts expose numeric y-axis labels and time labels on the x axis.
- The dashboard now includes Sun & daylight, detailed atmosphere, model comparison and current research Weather Snapshot panels in addition to current conditions, rainfall, radar, hourly temperature, daily forecast and meteogram.
- `weather.snapshot` accepts direct ISO-8601 entry and a native calendar/time picker; picker output is normalized to UTC ISO-8601.
- Forecast precipitation is visibly distinguished from radar.

## Pass D — data/provenance review

- Open-Meteo coordinate disclosure uses MethodMesh `LocationDisclosureMode.ROUNDED` with a 5,000 m radius.
- Requested and disclosed/query coordinates are separate outputs/audit values.
- Open-Meteo provider-grid coordinates are separately returned where relevant.
- Cached results expose cache/freshness fields rather than masquerading as fresh data.
- Research snapshot distinguishes `REANALYSIS`, `HISTORICAL_FORECAST` and `FORECAST`.
- Historical/reanalysis values are not labelled station observations.
- RainViewer provider `past` frames map to `RADAR_OBSERVATION`.
- RainViewer provider `nowcast` maps to `NOWCAST` only if supplied.
- Radar geographic coverage is reported as `not_evaluated` rather than inferred merely from timeline availability.
- Ensemble spread is explicitly not described as a calibrated confidence interval.

## Pass E — Kotlin compile-oriented review

Current MethodMesh `master` interfaces were inspected directly before final packaging, including `MethodMeshModule`, `As100Method`, `MethodDescriptor`, `MethodContract`, `CapabilityScreenSpec`, `CapabilityScreenContext`, `MethodSetting`, online-data models/executor, Google Play Services location usage and the repository's MapLibre dependency.

The v0.2.3 Weather sources had previously passed an expanded `kotlinc` compile-oriented stub-host check modelling the then-current MethodMesh, Android/Compose/GMS and MapLibre signatures. That pass found and corrected four genuine integration defects before the v0.2.x handoff:

- a malformed dashboard Refresh callback;
- an inferred public `as100Methods()` return type that exposed the internal `WeatherMethodBase`;
- nullable `MethodDescriptor.description` assigned to non-null `CapabilityScreenSpec.description`;
- an unsupported/unresolved `matchParentSize` usage in the radar map; replaced with `fillMaxSize()` for host Compose compatibility.

For v0.2.4, the current host definitions for `CapabilityScreenContext`, `MethodSetting`, `CapabilityPreset`, `ProtocolLibraryRepository`, `AndroidArtifacts`, `OutputFormatter` and `OutputExportRepository` were re-inspected before implementation. The new `WeatherPresetSupport.kt` surface was separately type-checked with focused Kotlin stubs matching those signatures and passed with no compiler errors. A parser-oriented pass over the complete v0.2.4 Kotlin tree found no syntax errors. The two integration edits in `WeatherCapabilityScreens.kt` and `WeatherDashboardScreen.kt` use only host members verified in the current repository (`isNativePresetRun`, `runtimeInputFields`, `settingShouldBeShown`, `onSettingsChanged` and the existing result callbacks).

Earlier static review also corrected a missing local `Double.fmt()` helper and an invalid regex escape. The v0.2.0 dashboard/radar/chart/date-picker pass had completed with exit status 0 against the expanded compile-oriented host stubs.

Additional final hardening invalidates an old working result immediately when manual coordinates change, uses one broad dashboard meteorological fetch plus radar retrieval before projecting canonical dashboard/forecast/precipitation/meteogram views, and removes fixed-width header/metric layouts that could crowd narrow phones.

This is deliberately **not** represented as a successful `:app:compileDebugKotlin` or Android Gradle build. The complete Android repository/dependency graph was not available as a clonable local checkout in this packaging runtime, so the real host Gradle gates remain receiving-repository checks.

## Pass F — XLSForm structural audit

Eleven canonical v1.08 showcase workbooks were generated with `artifact_tool` and re-imported for verification.

For **every** workbook the audit found:

- exactly one MethodMesh `body::intent` invocation;
- expected canonical method ID;
- `input_payload_mode='FULL'`;
- `return_mode='flat'`;
- no `methodmesh_return_namespace`;
- all declared method-specific canonical output fields included;
- `methodmesh_status` included;
- `methodmesh_full_json` included;
- no duplicate survey node names;
- zero matched spreadsheet error tokens (`#REF!`, `#DIV/0!`, `#VALUE!`, `#NAME?`, `#N/A`).

`pyxform` is not installed in this packaging environment (`ModuleNotFoundError`), so pyxform/JavaRosa/ODK Validate and real provider upload checks are not claimed.

## Receiving-repository build and smoke checks

Before promotion beyond Development, run the host repository's normal gates, at minimum:

```text
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew generateMethodMeshOdkTemplateAssets
```

Then exercise:

1. module auto-discovery/indexing;
2. native dashboard on a physical Android device;
3. GPS denial -> manual-coordinate fallback;
4. fresh and cache-only Open-Meteo execution;
5. requested/query coordinate separation;
6. RainViewer map loading, attribution and timeline scrubbing;
7. provider with zero nowcast frames;
8. direct/headless invocation of representative capabilities;
9. Preset and multi-step Protocol invocation;
10. ODK roundtrip using the canonical showcase workbooks;
11. rotation/process recreation and state preservation;
12. dark mode, large font and narrow-device layout.

## Known limitations / non-Production items

- The packaging environment could not perform a full repository Gradle build because it did not have a clonable host checkout/dependency graph.
- Shared online-data cache is disposable runtime cache, not a durable downloaded meteorological archive.
- `weather.atmosphere` depends on model-variable availability and remains Experimental.
- `weather.model_compare` exposes ensemble spread, not calibrated forecast probability/confidence.
- Radar availability/coverage is provider-dependent and not guaranteed globally.
- Weather alerts are not claimed in v0.2.0.
- No synthetic future radar is generated.

## v0.2.1 patch verification

- Radar default zoom changed from `6.0` to `5.0` consistently in the canonical method fallback, module setting default, native-screen fallback and dashboard invocation.
- Explicit caller/preset/ODK `input_zoom` values continue to override the default.
- User pan/zoom after map interaction is not reset by the patch.
- No canonical method ID, input name, output name, XLSForm contract or provider definition changed.
- Static search found no remaining Weather radar default of `6` / `6.0`.



## v0.2.4 Master Book preset/Commit alignment

- Weather remains `CapabilityHostPresentation.Immersive`; the polished dashboard/instrument surfaces are not replaced by the Standard host settings/results wrapper.
- Direct/manual dashboard and individual capability screens expose **Save current setup as preset**.
- Presets are stored through the shared `CapabilityPreset` / `ProtocolLibraryRepository` contract; there is no Weather-private preset store.
- Authoring covers fixed versus runtime settings, Core/Audit/Full payload, Home/Share/Save result action, optional persistent Files log and preset naming.
- Runtime selections are represented by the standard `methodmesh_runtime_fields` setting.
- A native preset with runtime fields pauses before Weather acquisition; a fully fixed native preset may start immediately.
- Fixed fields continue to be hidden with `CapabilityScreenContext.settingShouldBeShown(...)`.
- Direct/manual Commit now freezes both result values and the settings used to resolve them; subsequent edits/refreshes do not mutate the committed payload.
- Same-screen committed actions provide Copy, Share, Save, optional full JSON and Done; Recommit is explicit.
- Automatic-return/ODK code paths remain separate from preset authoring controls.
- `WeatherMethods.kt` differs from v0.2.3 only by the module version constant; no canonical method ID/input/output schema changed.
- The eleven v0.2.3 canonical XLSForms are retained unchanged.

## v0.2.3 date-format patch

- Dashboard/chart time labels use `d MMM HH:mm` with `Locale.UK`.
- Daily forecast cards use `EEE d MMM` with `Locale.UK`.
- Numeric `MM-dd` / `HHh` presentation was removed from Weather UI helpers.
- No canonical capability ID, input, output or XLSForm contract changed.

## v0.2.3 formatter compatibility patch

- Fixed `WeatherCapabilityScreens.kt` date-axis rendering where `parseWeatherDateTime()` returns `TemporalAccessor`.
- Replaced the invalid `TemporalAccessor.format(...)` call with `DateTimeFormatter.format(TemporalAccessor)`.
- User-facing UK-style date/time output is unchanged.
- No canonical method IDs, inputs, outputs or XLSForms changed.

## v0.2.5 preset-launch validation

The user-reported preset run ended without a Java/Kotlin exception in the supplied app-filtered log. Inspection against the current MethodMesh host identified two Weather-specific lifecycle mismatches:

1. `ExternalWorkflowActivity` already wraps intent-launched capability content in a vertical scroll container. Weather v0.2.4 also applied a root `verticalScroll` inside both the dashboard and standalone tool surfaces. v0.2.5 keeps Weather-owned root scrolling only for `CapabilityPresentationMode.Dashboard` and relies on the host scroller for preset, ODK, protocol and other intent launches.
2. Weather's custom committed-result `HOME` action returned only through the transient preset dispatcher. v0.2.5 mirrors the shared `CapabilityScreenScaffold` closeout rule by explicitly reopening `MainActivity` with `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK` unless `methodmesh_finish_to_launcher=true`.

Static checks for this patch:

- no unconditional Weather root `verticalScroll` remains in `WeatherDashboardScreen.kt` or `WeatherCapabilityScreens.kt`;
- the only remaining direct `verticalScroll(rememberScrollState())` in Weather preset support is the bounded preset-authoring dialog;
- current host definitions for `CapabilityPresentationMode`, `MainActivity`, native-preset settings and the standard scaffold closeout behaviour were verified against the repository before patching;
- Kotlin parser scan reports no `expecting` syntax errors in the final Weather source tree;
- all eleven XLSForms are byte-identical to v0.2.4 and therefore preserve the previously audited ODK contracts.

A full receiving-tree `:app:compileDebugKotlin` and device preset smoke test remain the authoritative final host gate.


## v0.2.6 radar camera/render stabilisation

The radar map was reported to render briefly at an inappropriate scale before settling. Inspection showed that v0.2.5 created `MapView` at its default camera and only applied the Weather camera later in `getMapAsync`. During radar animation it also reloaded the entire OpenFreeMap style whenever the RainViewer frame changed.

v0.2.6 changes only radar presentation internals:

- the initial target and zoom are supplied through `MapLibreMapOptions.camera(...)` when `MapView` is constructed, before the first map render;
- Weather's 1–7 radar zoom limits are supplied to the same initial map options;
- radar-frame changes no longer call `setStyle(...)`; the loaded basemap remains intact and only the Weather radar raster layer/source is replaced;
- a module-local `RadarMapViewState` prevents ordinary Compose recomposition and radar playback from reapplying the configured camera after the operator has manually panned or zoomed;
- camera recentering occurs only when the requested latitude, longitude or explicit configured zoom actually changes;
- stale asynchronous frame callbacks are ignored if a newer radar frame has already become current.

Current MapLibre Android documentation was checked for `MapLibreMapOptions.camera`, the programmatic `MapView(context, options)` constructor, and `Style.removeLayer/removeSource`. No Weather capability IDs, inputs, outputs, provider definitions or XLSForms changed.
