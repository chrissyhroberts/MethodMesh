# MethodMesh v2.9.0 — Alpha

Released 2026-09-22. App version 2.9.0 (version code 10); canonical Master Book remains **v1.25**.

## Architecture, provenance and time assurance

- Consolidates the v1.25 implementation and subsequent UI/widget patches. FULL execution envelopes carry policy-neutral time evidence and captured module/capability identity, version and maturity. Historical exports retain the producing implementation's identity after upgrades; legacy results explicitly report missing provenance.
- Shared Clock Assurance advances verified timestamp anchors using same-boot monotonic time, exposes uncertainty and reboot/unanchored states, and preserves wall-clock rollback/forward-divergence evidence. RFC 3161 anchoring checks signer identity, configured trust, certificate chain, validity and timestamp usage.
- Workbench adds Time Assurance inspection and manual trusted-time refresh. Home shows anchor recency and signed Android clock offset, with a warning above 60 seconds. This threshold is presentation only; it does not change assurance policy or Android system time.
- Corrects unequal forward/backward clock-tolerance handling while retaining rollback detection. Shared location, language, sensor-profile and reference-library utilities now live in the platform layer; cross-capability game/API calls use the existing execution registry.

## Module maturity and user experience

- Universal version and maturity badges use a canonical resolver, tolerate legacy/unknown status text, and provide compact, aligned module rows and capability metadata.
- Updated conversions with worked explanations and precision controls, FieldStats/LabBench dashboards, Network Tools, and barcode scanning, generation and cloning with preset integration.
- Includes File Lab inspection/conversion and the Text documents workspace for text, Markdown, JSON and JSON Lines; retains star-spectrum calibration and browser-return bridge work since v2.8.0.

## Widget regression fix

- Application startup calls `MethodMeshWidgetProvider.updateAll(this)` after module and scheduler initialization, rebuilding existing widgets from their saved configuration so icons, labels, colours and target-specific taps return after restart/update.
- Preset and protocol IDs, schedule toggles, widget launch origin and return-to-launcher routing are preserved. Saved-config regression tests cover preset appearance, legacy defaults, protocol and schedule targets; provider/dispatch routing was reviewed and remains unchanged.

## Repository and form cleanup

- Preserves useful prototype/patch history, module sources, integration specifications and screenshots on GitHub. Local rollback snapshots, obsolete source ZIPs, firmware patch backups, flash dumps and IDE device settings are excluded; recoverable local copies remain available.
- Repairs a malformed Gradle property, removes machine-specific paths from generated catalogue metadata, and archives the superseded transport overview. Historical prototypes and cross-system design documents are explicitly separate from the sole current Master Book.
- Repairs scheduler/Tamagotchi settings while preserving their original form IDs, resolves duplicate duration fields, and adds three ESP mesh XLSForm examples plus missing API GET/question documentation. The regenerated catalogue contains 411 forms and zero generator errors.
- Refreshes stale tests to the v1.25 documentation rules: multiple capability READMEs, human-readable titles, independent form IDs, optional choices sheets and supported namespace controls.

## Validation

- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`: passed; **240 JVM tests**, no failures or skips.
- Artifact compiler: **3 tests passed**. Documentation hygiene: **0 errors**. XLSForm catalogue lint: **0 errors**.
- New coverage includes clock continuity/rollback tolerances, immutable software provenance, maturity fallback, legacy widget configuration and deterministic cross-capability composition.
- Existing advisory findings remain: 33 documentation-placement warnings and 133 XLSForm revision warnings/style findings. Authoritative pyxform/ODK Validate and interactive device tests were not run. A read-only check confirmed that the connected phone's existing widgets have RemoteViews; this does not replace testing the new APK on-device.

The attached APK is a debug-signed alpha build. Source archives correspond to this tag.
