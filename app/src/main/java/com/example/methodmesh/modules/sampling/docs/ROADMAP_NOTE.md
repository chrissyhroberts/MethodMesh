# Sampling roadmap entry

Suggested addition to the repository-level `000_Roadmap.md` after dropping the
module into the app:

## Sampling — Development (v0.1.1 alignment)

- [x] manual list population
- [x] arbitrary CSV input and configurable field mapping
- [x] downloadable CSV template
- [x] numeric sequence generation
- [x] bundled/versioned random-word dictionary
- [x] simple sampling with/without replacement
- [x] weighted sampling
- [x] stratified sampling
- [x] systematic sampling
- [x] shuffle
- [x] balanced random partition
- [x] annotated-population output (default)
- [x] selected-records-only output
- [x] configurable output fields with collision protection
- [x] CSV/JSON output plus companion manifest for file workflows
- [x] stable seed/RNG/sampling algorithm provenance
- [x] input/population/result/output/provenance SHA-256 commitments
- [x] public dependency on `attestation.create` for TSA-backed provenance
- [x] ODK structured-roster route and example XLSForm
- [x] capability-owned `sampling_audit_json` return in XLSForm
- [x] native preset runtime-input guard and fixed-setting hiding
- [x] shared AutomaticReturn close-out (no capability double-submit)
- [ ] run `./gradlew :app:assembleDebug` in the full Android project
- [ ] device/emulator UI validation
- [ ] validate ODK round-trip on a real ODK Collect form
- [ ] validate Sampling → Attestation protocol chain including TSA token
- [ ] generic shared result-screen CSV/JSON attachment sharing
- [ ] Production promotion review
