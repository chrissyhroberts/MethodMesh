# Weather module v0.2.6 drop-in

Target location:

```text
app/src/main/java/com/example/methodmesh/modules/weather/
```

The folder is self-contained module-owned source, documentation and canonical XLSForm showcases. No shared MethodMesh source edit is intended for discovery; MethodMesh module discovery should pick up `WeatherModule` using the normal module contract.

After copying this `weather/` root into a current MethodMesh checkout, run:

```text
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew generateMethodMeshOdkTemplateAssets
```

Then perform the device/ODK smoke matrix in `docs/VALIDATION.md`.

v0.2.3 changes only user-facing date/time presentation: chart ticks use UK-style `d MMM HH:mm` and daily cards use `EEE d MMM`. ISO-8601 remains the canonical interchange format.


v0.2.3 is a compile-compatibility patch only: `WeatherCapabilityScreens.kt` now formats generic `TemporalAccessor` values through `DateTimeFormatter.format(...)`, avoiding the unresolved `format` reference seen in the receiving Android build.


## v0.2.4 alignment patch

This release restores the Master Book preset-authoring route inside Weather's immersive UI without changing the canonical Weather contract. Direct/manual Weather use now supports fixed/runtime preset selection, payload/result behaviour, optional persistent log and preset naming through the shared MethodMesh preset repository. Native presets with runtime fields pause for those fields before running. Direct/manual Commit now freezes a same-screen canonical payload with Copy/Share/Save/full-JSON/Done actions.

ODK/XLSForm contracts are unchanged from v0.2.3. Preset authoring is hidden from automatic-return callers.

## v0.2.5 preset-launch patch

- Weather root scrolling is conditional on `CapabilityPresentationMode.Dashboard`; preset/ODK/protocol intent launches use the existing `ExternalWorkflowActivity` scroll container.
- Native-preset HOME closeout follows the shared MethodMesh task-return behaviour and explicitly returns to `MainActivity`.
- No canonical Weather or XLSForm contract changed.


## v0.2.6 radar patch

Radar now receives its initial camera before first render and swaps only the radar raster overlay when stepping through frames. This removes the visible world/default-camera-to-Weather-camera adjustment and avoids full basemap reloads during Play.
