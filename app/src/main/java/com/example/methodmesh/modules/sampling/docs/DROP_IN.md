# Drop-in installation

Module version: **0.1.1** (capability-guide alignment pass)

Copy this entire `sampling` folder to:

```text
app/src/main/java/com/example/methodmesh/modules/sampling/
```

No central capability registration edit is required. `SamplingModule.kt` follows
the current MethodMesh module-discovery convention.

No Android manifest or FileProvider XML change is required for this version:
the current MethodMesh FileProvider already exposes `cache-path`, and Sampling
writes result/manifest attachments under `cacheDir/sampling`.

After copying:

```bash
./gradlew :app:assembleDebug
```

Then validate in the module browser while the capability remains Development. The 2026-09-03 capability-guide alignment pass keeps the canonical handoff as this one module folder.

Recommended device checks:

1. paste a five-item list and sample two without replacement;
2. generate sequence `1,100,5`;
3. create eight random words;
4. download the CSV template;
5. import a CSV with a non-standard identifier name and remap it;
6. verify annotated output retains every original column;
7. run a fixed-seed sample twice and compare results/hashes;
8. import `docs/example_odk_Sampling.xlsx` into ODK Central/Collect and select
   one household member;
9. chain `sampling_provenance_payload_sha256` into `attestation.create` and
   verify the returned TSA token/timestamp.

`docs/ROADMAP_NOTE.md` contains the suggested central roadmap entry, kept out of
this drop-in so the module does not modify files it does not own.
