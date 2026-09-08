# Acoustics v0.2.0 validation note

Status: Development.

## Checks completed in the packaging environment

- `AcousticsAlgorithms.kt` compiles with Kotlin 1.9.
- Synthetic 440 Hz, 48 kHz, 4096-sample sine test: YIN estimate 440.018 Hz with confidence 0.999986.
- New Hann/FFT dBFS spectrum function exercised on the same synthetic signal; strongest grouped bin approximately -7.19 dBFS for a 0.5 full-scale sine (expected near -6 dBFS, with grouped-bin/window leakage explaining the small difference).
- Source brace/parenthesis balance checked for the capability screen.
- XLSForm v0.2 fields and `methodmesh_full_json` placeholder verified; no spreadsheet formula-error strings detected.
- ZIP integrity and SHA-256 are checked at packaging.

## Still required before Production

- Full Gradle/Android compilation in the MethodMesh repository.
- Physical-device microphone tests on multiple Android devices and input routes.
- Native orientation/preset/runtime-input checks.
- ODK Collect round-trip on a physical device, including large FULL-envelope returns.
- Empirical validation of dBFS spectral scaling and calibrated SPL behaviour against reference instrumentation.

The packaging environment had no outbound DNS/network access, so a fresh MethodMesh repository checkout for the full Android build could not be performed here.
