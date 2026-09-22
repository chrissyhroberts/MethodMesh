# MethodMesh v1.25 validation and picky-boss review

## Review pass 1 — architecture/provenance

**Rejected initial export-time metadata lookup.** Looking up capability version while formatting JSON would allow an app upgrade to rewrite historical provenance. Fixed by adding frozen software identity to `ExecutionResult` and capturing it in `As100ExecutionEngine.complete`.

**Kept universal clock evidence policy-neutral.** The FULL sidecar uses `ClockEvidenceSnapshot`; it does not call a hidden `ClockAssurancePolicy`. Time-sensitive methods continue to apply explicit policies separately.

**Kept Clock Assurance core-owned.** Trusted Timestamp supplies validated anchors through a provider/bridge; Workbench is only the inspection/control surface. Manual sync never changes Android system time.

## Review pass 2 — metadata/UI/conformance

**Removed hardcoded Home lifecycle tables.** UI/search/badges now resolve maturity centrally.

**Normalized maturity source values.** Conformance scan found legacy `Stable`, `Preview`, `Prototype` and uppercase `DEVELOPMENT` declarations; these were mapped to the canonical typed vocabulary. Registry installation now fails fast if a declared `status`/`maturity` is not exactly `Production`, `Development` or `Experimental`.

**Avoided narrow-screen badge crowding.** Module/capability version+maturity badges sit under titles/descriptions rather than competing with favourite/details controls on the trailing edge.

**Kept Home recency policy-neutral.** It displays anchor age/evidence state and wall-clock anomaly indication without inventing a universal freshness threshold.

## Executed checks

1. **Clock Assurance engine harness:** PASS — policy-neutral snapshot and explicit-policy separation, including unanchored, trusted same-boot, rollback/anomaly separation, explicit policy evaluation and reboot discontinuity.
2. **Module/capability metadata Kotlin harness:** PASS — semantic version selection, mixed module maturity, capability maturity and frozen software registry.
3. **FULL envelope Kotlin harness using the actual `OutputFormatter.kt`:** PASS — envelope schema v2 contains frozen capability/module version+maturity and `time_assurance`.
4. **Manual trusted-time provider Kotlin harness:** PASS — provider compiles against the core refresh contract and follows request -> validate -> publish flow.
5. **Descriptor conformance sweep:** PASS — 129 MethodDescriptor implementations; no missing capability versions; no noncanonical literal maturity declarations after normalization.
6. **Compose/source parser smoke check:** no Kotlin parser/structural diagnostics in the changed Home/Time Assurance/badge files. Full semantic Compose compilation is not possible without the Android/Compose dependency graph.
7. **Installer simulation:** PASS — starting from the reconstructed v1.23.1 source baseline, `INSTALL_V1_25.sh` reproduced the reviewed v1.25 `app/src/main` tree byte-for-byte and created rollback copies of overwritten files.

## Not claimed

No full Gradle Android build is claimed. The supplied source snapshot does not include `gradlew`, Gradle settings/build files, or the dependency graph. The authoritative next check on the real project is:

```bash
./gradlew :app:compileDebugKotlin
```

Then run the app and verify: Home Time chip -> Workbench; manual sync; module badges; direct capability header badges; and one ODK/external FULL return containing envelope schema v2, frozen software provenance and `time_assurance`.
