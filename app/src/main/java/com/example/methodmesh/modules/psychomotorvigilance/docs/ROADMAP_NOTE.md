# Repository roadmap note — Psychomotor vigilance test v0.1.0

Suggested addition to repository `000_Roadmap.md`:

## Development — Psychomotor vigilance / reaction time

- Added self-contained `psychomotorvigilance` module exposing `psychomotor.vigilance.run`.
- Implements published Standard PVT (10 min, 2–10 s ISI, lapse ≥500 ms) and PVT-B (3 min, 1–4 s ISI, lapse ≥355 ms).
- Offline/local execution; no permissions or external service.
- Monotonic software timing uses `SystemClock.uptimeMillis()` and `MotionEvent.ACTION_DOWN.eventTime`; raw trials and device/timing metadata are retained in audit JSON.
- Scientific references and open-source implementation review/attribution are documented in module docs.
- Example ODK/XLSForm included and invokes MethodMesh through a `begin_group` external-app intent.

### Keep in Development until

1. Run `./gradlew :app:assembleDebug` in the complete MethodMesh checkout.
2. Add/run focused JVM tests for PVT scoring and protocol constants.
3. Validate native 10-minute and 3-minute runs on physical Android hardware.
4. Validate saved preset fixed/runtime settings and scheduled/protocol close-out.
5. Validate ODK Collect round trip with `example_odk_psychomotor_vigilance.xlsx`.
6. Confirm result survives post-test orientation changes; active test orientation lock behaves correctly across representative devices.
7. Physically characterise display/input latency on representative devices. Basner et al. (2021) propose PVT system bias within ±5 ms and latency SD ≤10 ms as calibration targets.
8. Decide whether calibrated device profiles / measured latency corrections should be a generic MethodMesh service before any claim of cross-device timing equivalence.

Do not describe the MethodMesh implementation itself as clinically validated until validation/calibration appropriate to the intended use has been completed.
