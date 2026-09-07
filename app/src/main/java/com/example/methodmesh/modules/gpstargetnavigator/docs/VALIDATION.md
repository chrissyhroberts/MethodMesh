# GPS target navigator — v1.05 validation

Date: 2026-09-06

## Migration review

### Contract parity

- Canonical method ID remains `gps_target_navigator`.
- Module discovery still exposes exactly one AS1.00 capability and one capability screen.
- Capability settings include both target routes: `target_plus_code` and latitude/longitude.
- Descriptor output list now includes every field emitted by the live result and committed navigation outcome.
- Plus Code target resolution is implemented in canonical method logic as well as the native setup screen, preventing the Plus Code route from being native-UI-only.
- Presets/protocols/ODK continue to address the same method ID and settings contract.
- ODK example uses grouped intent invocation, namespace projection and full JSON.

### Native UX

- Replaced settings-form/result-page emphasis with a navigation dashboard control surface.
- Active-navigation visual order is target compass -> distance/status -> target location.
- Target setup is consolidated behind **Set location**, with latitude/longitude, Plus Code and full-screen map-picker routes.
- The map picker pans/zooms around a fixed centre crosshair and confirms the selected coordinates back into the existing canonical target fields; it does not create a second capability contract.
- Live distance, target vector, bearing, turn instruction, accuracy and arrival state update on the working screen.
- Full-screen AR remains a separate view and returns to the dashboard state.
- Commit freezes a navigation outcome; live updates are stopped for the committed execution.
- Stop without result does not create an implicit saved/recorded result.
- Main scalar/text results are tap-to-copy.
- Raw research-session field dumps were removed from normal UI; diagnostics are progressively disclosed under Technical details.

### State

- Destination/setup fields use `rememberSaveable`.
- Committed result fields are serialised into saveable screen state.
- Active navigation state is stored module-locally and can survive rotation/backgrounding/process recreation where Android restores the capability.
- Active-session store is cleared on Commit/Stop and on target mismatch.

### ODK

- Example XLSForm contains all canonical returnable capability fields plus transport status/execution ID and `methodmesh_full_json`.
- Example demonstrates `methodmesh_return_namespace='nav'`.
- Typical ODK route remains interactive: supplied target -> navigator -> Commit -> caller.
- No module code creates a secondary ODK archive copy on Commit.

## Checks performed in this handoff environment

- ZIP/module structure inspected and cleaned.
- Kotlin sources checked for basic parse/bracket structure with the local Kotlin compiler; Android/Compose/MethodMesh classes are not available in this isolated module environment, so unresolved-reference output is expected and is not a substitute for a real app build.
- Canonical output list was compared against the committed-outcome field construction.
- XLSForm workbook was regenerated with `survey`, `choices`, and `settings` sheets and inspected after export.

## Integration checks still required in the MethodMesh repository

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then exercise:

1. direct coordinate navigation;
2. direct full Plus Code navigation;
3. preset with fixed destination;
4. preset with runtime destination;
5. protocol step completion/Retry/Cancel rails;
6. ODK roundtrip with namespace and full JSON;
7. rotation during active navigation and after Commit;
8. background/process recreation of an active navigation;
9. widget-origin Done returning to Android desktop;
10. app/preset-origin Done returning to MethodMesh dashboard;
11. AR permission granted and denied;
12. tap-to-copy for distance, Plus Code, coordinates, bearing and final result;
13. map picker pan/zoom, centre-on-current-position, crosshair selection and **Use this location**;
14. map picker failure/offline fallback to latitude/longitude or Plus Code setup.

Production status should be confirmed only after those repository-level build and device checks pass.


## Unified destination editor refresh

- Destination setup no longer replaces the navigator with a separate setup flow.
- Initial setup retains the compass → distance → target-location hierarchy.
- **Set location / Change location** expands the editor inside the same navigator dashboard.
- Latitude/longitude and Plus Code entry are inline; the map picker is a state-preserving overlay rather than a navigation destination.
- Applying a changed target clears the old active-session snapshot and keys a fresh navigation working session without changing the canonical method contract.


## Compass-dashboard / preset-target refresh

- Reworked the hero compass using the supplied Compass module as the visual benchmark: dark instrument face, dense degree ticks, cardinal marks, red north needle, distinct target vector, heading/target/turn tiles and tap-to-copy values.
- Preserved the navigation invariant: the primary arrow is the target direction relative to device heading, not a north pointer.
- Main dashboard now shows **Current location latitude and longitude** explicitly in the distance card.
- Target card now shows **Target latitude and longitude** explicitly as separate tap-to-copy values, plus Plus Code when that route is used.
- Map picker now displays the live crosshair coordinates and a locally calculated full Plus Code while panning.
- New preset configuration seeds target identity/location fields into `methodmesh_runtime_fields`, so a tested/hard-coded target does not become fixed by default. The generic preset editor can still deliberately mark any target field fixed.
- No canonical method ID, input key or output key changed.
