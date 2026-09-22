# MethodMesh v1.25 drop-in

This package is an **overlay**, not a replacement application tree.

Use `./INSTALL_V1_25.sh /path/to/MethodMesh` from this package. The installer copies each file individually and backs up every overwritten file under `backups_for_rollbacks/`. This avoids the Finder/macOS behaviour that can replace an entire module directory when only a partial folder was intended as an overlay.

## What this release adds

- Workbench **Time Assurance** inspector with policy-neutral wall/trusted/anchor/continuity/anomaly evidence.
- Workbench **Sync trusted time** action through the existing hardened RFC 3161 -> Clock Assurance path; it never sets Android system time.
- Discreet Home recency/evidence chip linking to Workbench.
- Universal typed module/capability maturity (`Production`, `Development`, `Experimental`) with shared badges.
- Universal module and capability versions in shared UI.
- Canonical FULL envelope schema v2 with frozen `module` and `capability` identity/version/maturity plus universal `time_assurance`.
- Capability/module software provenance is captured when execution completes, so later upgrades cannot rewrite historical sidecars.
- Multi-capability combined workflows retain each distinct producing capability in `capabilities`.
- Legacy `Prototype`, `Preview`, `Stable` and uppercase maturity declarations touched by the conformance sweep were normalized to the canonical vocabulary.
- Master Book v1.25 and Trial Platform v1.0 2026-09-22 r2 alignment.

## Important provenance rule

Capability version in a FULL sidecar is the version that **actually produced the execution**, not the version currently installed when someone later exports it. `ExecutionResult` now freezes software provenance alongside the frozen Clock Assurance snapshot.

## Module version migration rule

Capability versions remain authoritative for reproducibility. Older modules that do not yet declare an independent module semantic version receive a deterministic display/audit module version from the highest contained capability version. A module can override this when it adopts its own release version.

## Build limitation in this environment

The supplied `main.zip` contains `app/src/main` rather than a complete Gradle project/wrapper, so a genuine `:app:compileDebugKotlin`/APK build cannot be run here. Targeted Kotlin compilation and source-wide contract checks were run; see `VALIDATION_REPORT.md`. Run `./gradlew :app:compileDebugKotlin` in the real repository after installation.

## Changed/new source files

- `modified` `java/com/example/methodmesh/MethodMeshApplication.kt`
- `modified` `java/com/example/methodmesh/modules/MethodMeshModule.kt`
- `new` `java/com/example/methodmesh/modules/ModuleMetadata.kt`
- `modified` `java/com/example/methodmesh/ui/HomeScreen.kt`
- `modified` `java/com/example/methodmesh/transport/OutputFormatter.kt`
- `modified` `java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt`
- `modified` `java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt`
- `new` `java/com/example/methodmesh/ui/timeassurance/TimeAssuranceUi.kt`
- `new` `java/com/example/methodmesh/ui/components/MetadataBadges.kt`
- `modified` `java/com/example/methodmesh/core/methodmesh/ExecutionObjects.kt`
- `new` `java/com/example/methodmesh/core/methodmesh/ExecutionSoftwareProvenance.kt`
- `modified` `java/com/example/methodmesh/core/scheduling/SchedulerMethod.kt`
- `new` `java/com/example/methodmesh/core/timeassurance/TrustedTimeRefresh.kt`
- `modified` `java/com/example/methodmesh/core/timeassurance/AndroidClockAssurance.kt`
- `modified` `java/com/example/methodmesh/core/timeassurance/ClockAssuranceService.kt`
- `modified` `java/com/example/methodmesh/core/timeassurance/ClockAssuranceModels.kt`
- `modified` `java/com/example/methodmesh/core/methodmesh/runtime/MethodMeshExecutionEngine.kt`
- `modified` `java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampModule.kt`
- `new` `java/com/example/methodmesh/modules/trustedtimestamp/TrustedTimestampTimeSyncProvider.kt`
- `modified` `java/com/example/methodmesh/modules/star_spectrum/StarSpectrumReferenceMethod.kt`
- `modified` `java/com/example/methodmesh/modules/star_spectrum/StarSpectrumAnalyseMethod.kt`
- `modified` `java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryMethod.kt`
- `modified` `java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryEmailMethod.kt`
- `modified` `java/com/example/methodmesh/modules/referencelibrary/ReferenceLibraryPeerMethod.kt`
- `modified` `java/com/example/methodmesh/modules/bluetoothinspector/BluetoothInspectorMethod.kt`
- `modified` `java/com/example/methodmesh/modules/espmesh/EspMeshMethod.kt`
- `modified` `java/com/example/methodmesh/modules/espmesh/EspMeshDiagnosticsMethod.kt`
- `modified` `java/com/example/methodmesh/modules/espmesh/EspMeshGatewayMethod.kt`
- `modified` `java/com/example/methodmesh/modules/appinspector/AppInspectorMethod.kt`
- `modified` `java/com/example/methodmesh/modules/textdocuments/TextDocumentsMethod.kt`
- `modified` `java/com/example/methodmesh/modules/sensorfirmwareinstaller/SensorFirmwareInstallerMethod.kt`
