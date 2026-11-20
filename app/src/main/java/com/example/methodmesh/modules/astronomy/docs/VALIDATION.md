# Astronomy v0.6.0 validation

## Added / changed in this package

- Dashboard site selection is explicit: **Current GPS**, **Plus Code**, or **Latitude / longitude**. Manual/Plus Code sites remain selected; **◎ GPS** deliberately recentres and switches back to GPS.
- Dashboard continues to use the atomic `As100ImagingWindowMethod.calculate()` engine for the best-window calculation rather than a second copy of the scoring logic.
- `astronomy.light_pollution` now downloads NASA GIBS VIIRS nighttime-radiance WMS imagery around the selected site and stores a georeferenced PNG + metadata as an astronomy-owned regional raster cache.
- The standalone capability displays that raster as a transparent heatmap over a MapLibre street/satellite/dark basemap, using the same map pattern as Plus Code capture.
- The selected site is sampled from the original GIBS raster and returned as an approximate `nW/(cm² sr)` nighttime-radiance proxy; outputs explicitly avoid Bortle/SQM claims.
- Dashboard **Site darkness** uses the same repository and can download a 50 km heatmap directly.
- Legacy point-grid import / `build_light_pollution_region.py` remain fallback compatibility paths rather than the primary native workflow.
- No core `HomeScreen` or global Settings/resource-registry changes are included.
- Polar alignment now has an AR sighting mode using rear-camera-axis azimuth/elevation for a vertically mounted phone/tripod, while preserving ordinary compass mode.
- Sky test is explicitly a relative point-source test against a user-saved clear-night reference; misleading absolute/screen-down claims were removed.
- Focus is renamed visually to **Telescope focus assistant**, supports rear/front camera selection, and uses a rolling-median FWHM metric.
- Image scale now has sliders plus exact numeric text fields.
- Astronomy session now has Start/Pause/Resume/Stop/Discard and astronomy-local resumable active-session persistence.
- Native preset astronomy dashboard refresh remains in the dashboard instead of switching to the generic result renderer. External/ODK dashboard calls still return structured outputs automatically.

## Local checks performed for this package

- `AstronomyMath.kt` + `docs/AstronomyMathSmoke.kt` compile and run as a JVM smoke test.
- Module structural check verifies all 12 registered astronomy methods and all 12 registered screens resolve to declarations.
- Python legacy light-pollution preprocessing helper passes `py_compile` and a synthetic EPSG:4326 GeoTIFF extraction test.
- Raster sampling/heatmap logic is checked with synthetic georeferenced images; live GIBS transport still requires Android/network validation.
- Kotlin source parse pass shows no parser-level `expecting` errors; Android/Compose symbols cannot be fully resolved without the complete MethodMesh Android build environment.
- XLSForm is scanned for formula errors and stale release metadata.

## Still required in the actual repository / on device

1. `./gradlew :app:assembleDebug` in the current full MethodMesh checkout.
2. Open the dashboard from the capability browser and from a native preset; confirm refresh remains on the visual dashboard and **Finish** closes a preset run.
3. Verify dashboard location switching between GPS, Plus Code and manual coordinates, including **◎ GPS** recentring.
4. Verify remote-site manual/Plus Code selection drives weather, imaging window and cached light-pollution lookup for that site.
5. Verify Open-Meteo current/hourly/AQ/GFS 200/250/300 hPa values on a physical phone.
6. Cross-check representative jet values with an independent GFS upper-air chart.
7. AR polar sighting: mount phone vertically, confirm rear-camera azimuth/elevation directionality, declination correction and pole guidance away from ferrous structures.
8. Sky clear-night reference persists and produces sensible relative directionality under deliberately degraded stability/brightness/contrast.
9. Characterise camera auto-exposure/autofocus/OIS effects for sky test.
10. Focus rear/front camera switch works; rolling median is more stable than instantaneous FWHM around best focus.
11. Image-scale sliders and exact fields remain synchronized and return correct calculations at representative rigs.
12. Session survives activity/process restart, pause excludes paused time, resume continues, stop returns the correct active duration, and discard clears unfinished state.
13. Download a 10/25/50/100 km NASA heatmap for a known site; verify street/satellite/dark basemaps, georeferenced alignment, opacity control and persisted cache after app restart.
14. Compare a few sampled points against NASA Worldview/GIBS visual context and verify that transparent/no-data pixels fall back to another covering cached date where available.
15. Verify the dashboard displays the same cached raster and that **Download 50 km heatmap** makes the card populate after refresh.
16. Confirm `astronomy.light_pollution` never labels VIIRS radiance as Bortle/SQM/zenith sky brightness and preserves source/date/resolution/cache kind in audit output.
17. Test GPS permission flow for both **Use cached** and **Load / refresh heatmap**.
18. ODK Collect round-trip for all 12 public methods, including external automatic dashboard return and fetch-if-missing behaviour.

Status remains **Development**.


## v0.5.1 light-pollution flow regression checks

- Native `intent_test` light-pollution launches remain interactive on cache miss.
- Native preset light-pollution launches remain interactive until the user chooses a valid lookup.
- Genuine external/ODK invocation still auto-returns, including a non-empty main result on cache miss.
- Standalone light-pollution import and dashboard import write to the same `astronomy/light_pollution` repository.
- Dashboard refreshes after a successful region import and re-runs point lookup for the selected site.
- No MethodMesh `HomeScreen`, Settings, widget or other core UI files are included in this package.


## v0.6 live heatmap regression checks

- Capability-browser (`intent_test`) and native preset launches remain interactive instead of auto-closing onto the generic result page.
- External/ODK calls may fetch missing NASA data and auto-return a structured result.
- Download acceptance requires usable pixels near the selected site, not merely somewhere in the WMS rectangle.
- Raster lookup tries all cached rectangles covering the selected point newest-first.
- Standalone and dashboard surfaces use the same `filesDir/astronomy/light_pollution` cache.
- No `HomeScreen`, Settings, widget or other core UI file is included.

## Repository audit supplement — 28 October 2026

The earlier package-only build limitations above are historical. In the full MethodMesh checkout both debug APK builds now pass. This continuation fixes/tests the automatic conditions, dew-risk and imaging-window screens without changing their science or public contracts. Final verification is 38 focused JVM checks and one clean 32-case API 37 Android run: 14 new screen cases, nine existing dashboard cases, three GPS-task cases and six existing native sharing/file-delivery cases. The earlier 31-case run had two accessibility timing assumptions in new tests; corrected waits and the added error-display case pass in the final run.

This verifies controlled routing, runtime-input gates, original evidence retention, cancellation/late-response rejection, frozen inputs, explicit recovery and honest astronomy-only fallback. It is not live Open-Meteo/NASA/GPS/camera/sensor/permission-dialog or actual preset/protocol/Collect acceptance. Remaining shared-scaffold preset SHARE/synchronous-save behavior is explicitly open. The detailed evidence and protected-sharing checksum are in docs/handoffs/ASTRONOMY_AUTO_WORKFLOWS_2026-10-28.md.

The subsequent shared-scaffold pass (docs/handoffs/SHARED_SCAFFOLD_CLOSEOUT_2026-10-28.md) resolves the preset SHARE/synchronous-save limitation recorded above. Both builds, 69 JVM checks including NFC contracts, and a final clean 69-case Android run including these 14 Astronomy cases pass. This expands controlled closeout coverage; it does not add live provider/physical/real-caller certification.

## Measurement lifecycle verification — 29 October 2026

Both debug APK builds and git diff --check pass. All 75 focused JVM cases pass: ten new frame/camera ownership cases, 19 prior Astronomy routing/controller cases, 19 retained-store/closeout/parity/sharing cases and 27 NFC contract cases. A final clean API 37 run passes all 53 Android cases: 11 measurement-screen cases, 26 prior Astronomy/GPS cases, ten shared-scaffold cases and six sharing/file-delivery cases. The earlier ten new screen cases also passed before adding non-cooperative old-source cancellation coverage.

This verifies exact-original/FULL retention, explicit missing-evidence recovery, no timed replay after interruption, frozen inputs, owned focus reset/camera switching, reference saving after rotation, late-source isolation and automatic return once. Frames/timers/sensor sources are injected; CameraX resource lease tests are controlled callbacks. It does not certify physical cameras/sensors/permissions, actual CameraX binding failures, live providers or real caller/OS process-death behavior. The handoff is docs/handoffs/ASTRONOMY_MEASUREMENT_LIFECYCLE_2026-10-29.md.

## Local workflow verification — 29 October 2026

Both debug APK builds, git diff --check and 83 focused JVM checks pass (37 Astronomy, four retained-store, five closeout, five parity, five sharing, 27 NFC). Fifty distinct Android cases are verified across runs: 16 local workflows, 11 measurements, ten shared result controls, seven Home/log order and six file/share cases. The combined run passed 49/50; a shared fixture label reread was repaired. All affected 26 cases pass on final sources after an emulator UiAutomationService reconnection failure was cleared by reboot. This is not a single clean final 50-case/full-suite claim.

Controlled preference failures verify rollback after memory mutation, atomic Stop publication, no premature completion, original retention/recovery and no stale-session resurrection. Test preferences are isolated and production session/calibration preferences are not cleared. Real disk/power/process-loss and clock-change behavior are not certified; timing remains wall-clock based. The detailed record is docs/handoffs/ASTRONOMY_LOCAL_WORKFLOWS_2026-10-29.md.
