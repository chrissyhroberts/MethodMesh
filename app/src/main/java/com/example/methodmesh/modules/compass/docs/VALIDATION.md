# Compass validation record — v0.2.0 / MethodMesh v1.05 refresh

## Pass A — contract correctness

- [x] Preserved method ID `compass.read`.
- [x] Preserved existing setting/input keys.
- [x] Preserved every existing `compass_*` output key.
- [x] Module still exposes one canonical `As100CompassMethod`.
- [x] Direct native, dashboard launch, presets and protocols resolve the same method/screen registration.
- [x] `MethodContract.producedFields` is the full `CompassFields.outputs` list, so ODK projection remains contract-derived rather than example-derived.
- [x] Interactive automatic-return routes call `onConfirmed` only at the explicit Commit boundary.
- [x] ODK example includes every declared compass output plus full JSON / closeout fields.

## Pass B — v1.05 interaction quality

- [x] Compass instrument is first on screen; settings are below it.
- [x] Purpose-built compass face replaces the settings-first/generic-result feel.
- [x] Heading, target, angular error, alignment and sensor quality are live working values.
- [x] Commit freezes a result without replacing the instrument with a generic result page.
- [x] Live sensor movement after Commit does not mutate the frozen result.
- [x] Recommit explicitly replaces the committed reading.
- [x] Live heading/target/error and committed scalar/detail values are tap-to-copy.
- [x] Copy/Share/Save/Done and optional full JSON are on the same screen after Commit.
- [x] Technical/audit material is progressively disclosed.
- [x] Full-screen camera sight uses the same explicit Commit semantics.

## Pass C — robustness

- [x] Settings, sight-open state and committed field snapshot use saveable state.
- [x] No automatic permanent output save.
- [x] Camera denial retains dark-background sighting.
- [x] Offline operation remains unchanged.
- [x] App/preset/widget and automatic-return completion branches are handled through existing workflow settings/callbacks.
- [x] Capability content does not own a nested vertical scroll container; dashboard/external host surfaces provide scrolling, preventing Compose infinite-height measurement crashes.

## Runtime crash regression

Fixed the Compose crash reported when opening the compass inside a scroll-owning MethodMesh host/dialog:

```text
IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints
```

Cause: `CompassCapabilityScreen` added its own `Column.verticalScroll(...)` while the surrounding MethodMesh capability host is already vertically scrollable. Compose forbids same-axis nested scroll containers when the inner scrollable is measured with unbounded height.

Fix: the module-level `verticalScroll` was removed. The compass now participates as normal content in the host's scrolling surface; the full-screen sighting dialog remains non-scrollable.

Static regression check: no `.verticalScroll(...)` modifier call or `rememberScrollState` import remains in `CompassCapabilityScreen.kt`.

## Executed smoke test

Command:

```bash
kotlinc CompassMath.kt docs/CompassMathSmoke.kt -include-runtime -d compass-smoke.jar
java -jar compass-smoke.jar
```

Result:

```text
CompassMath smoke test passed
```

Covered:

- degree normalisation including wrap at 360°;
- 16-point cardinal mapping;
- signed shortest-turn wrap-around;
- ±180° convention;
- tolerance boundary;
- left/right instructions;
- heading label formatting.

## Host build not available here

The complete Android checkout is not mounted in the packaging runtime, so the following cannot be claimed as executed:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Before Production promotion, run both in the current MethodMesh checkout and complete physical-device + ODK Collect tests.

## Required device/integration checks

1. Flat heading at known 0/90/180/270° references and intermediate headings.
2. Smooth wrap-around near 359° -> 0°.
3. Camera-axis sight in portrait and supported landscape orientation.
4. Camera grant, denial and retry.
5. Sensor unavailable / unreliable states.
6. Working state and committed snapshot across rotation/activity recreation.
7. Preset fixed/runtime field visibility.
8. App preset Done -> dashboard.
9. Widget Done -> Android launcher.
10. Protocol Commit -> protocol runner continues.
11. ODK Commit -> flat/namespaced fields + `methodmesh_full_json` return with no extra MethodMesh archive save.
