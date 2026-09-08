# Visual acuity drop-in installation

Copy this entire `visualacuity` folder to:

```text
app/src/main/java/com/example/methodmesh/modules/visualacuity/
```

Do not add a central registration entry. `VisualAcuityModule.kt` follows MethodMesh's generated `*Module.kt` discovery convention.

No new Android runtime permission is required. The capability uses the ambient-light sensor when present; Android does not require a dangerous permission for that sensor. The module uses the existing global `CalibrationRepository` and does not create another calibration store.

Then run:

```bash
./gradlew :app:assembleDebug
```

Keep the capability in **Development** and perform these device checks before promotion:

1. Open global MethodMesh screen calibration and verify it against a physical millimetre ruler.
2. At 2 m mode, ruler-check the rendered E at 1.0, 0.5 and 0.0 logMAR against the physical-size table in `README_VisualAcuity.md`.
3. Confirm the 1.0-logMAR crowding boundary fits in landscape without any scaling-to-fit.
4. Confirm a swipe right/down/left/up is classified correctly.
5. Confirm every tested level shows exactly five presentations before the staircase changes level.
6. Confirm no correct/incorrect feedback is exposed during the sequence.
7. Confirm screen brightness is temporarily 100% during the test and restores afterwards.
8. With a light sensor, confirm >1000 lux blocks a new test by default and mean lux is recorded.
9. Rotate the device mid-test and verify level, item number, target orientation and prior trial trace survive.
10. Rotate after completion and verify the result survives.
11. Create a native preset with fixed and runtime fields; verify fixed values disappear at runtime.
12. Import `docs/example_odk_VisualAcuity.xlsx` into ODK and confirm all expected return fields.
13. Compare physical geometry and scores with an accepted ETDRS/Peek reference before any equivalence claim.

The pure `VisualAcuityEngine.kt` logic has been compiled/smoke-tested independently in the packaging environment; the full Android build still needs the complete MethodMesh checkout and Android SDK.
