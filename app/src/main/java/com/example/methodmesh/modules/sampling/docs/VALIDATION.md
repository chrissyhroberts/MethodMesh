# Sampling drop-in validation

Aligned module version: **0.1.1**

The pure Kotlin sampling core was compiled using Kotlin/JVM 1.9.0.

Smoke-test result: **PASS**.

Tested behaviours:

- fixed-seed replay produces identical selected IDs;
- n-without-replacement produces unique draws;
- annotated output preserves the entire population;
- generated sampling fields are present without changing source columns;
- `(1,100,5)` generates 20 records ending at 96;
- arbitrary CSV ID/weight/stratum/eligibility mappings work;
- ineligible records are not selected;
- quoted comma-containing CSV values survive round-trip;
- selected-only replacement returns one row per draw;
- equal-n stratification produces the requested n in each stratum;
- partition group sizes differ by at most one;
- fixed-seed random-word generation replays exactly;
- population and provenance hashes are deterministic;
- provenance manifest contains the `attestation.create` commitment request.

Example deterministic smoke result for seed `demo-seed` and population
`A,B,C,D,E`, n=3 without replacement:

```text
B
E
C
```

The exact example is useful as a regression vector for the declared
`methodmesh.sha256_counter` RNG v1.0.0 plus `fisher_yates_without_replacement` sampling
algorithm v1.0.0.

## Alignment checks against capability guide (2026-09-03 revision)

- `csv_uri` and `csv_text` are now declared capability settings.
- numeric sequence inputs are declared as numeric (`FloatSetting`) settings.
- native preset runs no longer auto-execute before runtime inputs can be entered.
- fixed preset CSV selection controls are hidden.
- fixed preset operation values are not rewritten by the random-word convenience default.
- advanced output controls disappear when all of those fields are fixed in a preset.
- `sampling_value` provides a compact beef-first scalar result alongside semantic ID fields.
- ODK example explicitly stores `sampling_audit_json` as well as the optional shared `methodmesh_full_json`.
- protocol/schedule and AutomaticReturn close-out remain owned by `CapabilityScreenScaffold` / the shared orchestrator; Sampling does not directly double-submit automatic results.

Remaining Development validation items are the Android `assembleDebug`, device/orientation/preset run, ODK round-trip and Sampling → Attestation TSA chain checks in the full app.
