# MethodMesh Clock Assurance drop-in

Designed and reviewed against the supplied `main.zip` and Master Book.

## Drop-in files

- `app/src/main/java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt`
- `app/src/main/java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt`
- `app/src/main/java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt`
- `app/src/main/java/com/example/methodmesh/MethodMeshApplication.kt`
- `app/src/main/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampClockAnchor.kt`
- `app/src/main/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampEngine.kt`
- `app/src/main/java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampCapabilityScreen.kt`
- `app/src/main/java/com/example/methodmesh/modules/trustedtimestamp/docs/README_TrustedTimestamp.md`
- `app/src/test/java/com/example/methodmesh/core/timeassurance/ClockAssuranceServiceTest.kt`
- `docs/METHODMESH_MASTER_BOOK.md` — v1.23, 2026-09-22

## Design

Clock Assurance is shared core infrastructure. It separates device wall time, same-boot monotonic elapsed time and externally validated trusted anchors. Trusted current time is represented as lower/upper bounds with acquisition and oscillator uncertainty. Reboot is a hard continuity boundary. Wall-clock rollback/divergence is reported independently of trusted monotonic assurance.

The anchor store uses `noBackupFilesDir`, atomic replacement and an Android-Keystore HMAC. Corrupt/unauthenticated anchors fail closed but can be safely replaced by newly validated external evidence.

Trusted Timestamp refreshes Clock Assurance only for `trusted_registry_match`. The trust path was tightened so the token's embedded signer must match the pinned published signer, the configured chain and generation-time certificate validity must hold, and the signer certificate must have a critical timestamp-only EKU with compatible signing key usage. Custom/unconfigured TSAs do not become clock anchors.

## Review iterations

1. **Architecture/security review:** removed weak reboot inference; added explicit boot-session identity with Android boot count and kernel boot-ID fallback; tightened RFC 3161 signer binding.
2. **Operational review:** allowed fresh externally verified evidence to recover a damaged anchor; measured wall-clock divergence against the trusted interval rather than the midpoint.
3. **Contract/conformance review:** isolated clock-anchor persistence failure from the primary timestamp capability; removed implicit clock policy from the runtime API; kept existing Trusted Timestamp method IDs/returns unchanged; updated v1.23 doctrine only after the design stabilised.

## Verification performed here

- Pure Kotlin Clock Assurance harness: **PASS**, 11 scenario groups covering no-anchor, fresh/aged anchors, rollback, forward divergence, uncertainty overlap, reboot with larger elapsed time, missing boot identity, monotonic reversal, same-boot anchor regression, corrupt-anchor handling and recovery.
- Targeted Kotlin syntax compile of core + Android persistence/runtime using minimal Android API stubs: **PASS**.
- Targeted Kotlin syntax compile of the Trusted Timestamp clock-anchor adapter against the new runtime: **PASS**.

A full `:app:testDebugUnitTest` / `:app:assembleDebug` was not possible from the supplied artifact because `main.zip` contains the `app/src/main` tree rather than the complete Gradle project/wrapper and dependency environment.

## NFC ROSC2 integration point

NFC should consume `ClockAssuranceRuntime.observe(explicitPolicy)` and use the returned trusted bounds for `valid_from` / `valid_until`. It should not read `Instant.now()` as trusted time. The NFC contract should return its own temporal decision plus the material assurance evidence when that decision matters. Clock Assurance itself deliberately does not encode staff/participant or credential-specific policy.
