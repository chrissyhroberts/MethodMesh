# Astronomy automatic-planning audit — 28 October 2026

This bounded continuation covers astronomy.conditions, astronomy.dew_risk and astronomy.imaging_window in /Users/icrucrob/AndroidStudioProjects/MethodMesh on feature/esp-mesh-transport, against the canonical Master Book v1.31 including its 28 October file-bearing Share erratum. The existing dirty checkout and earlier audit/signing/sharing work are preserved. It is not completion or production certification of the whole Astronomy module or the app-wide review.

## Confirmed defects and fixes

- Explicit manual mode previously entered automatic GPS/provider routing. Upstream weather JSON could also trigger GPS, and a prior humidity_pct measurement could lose to a preset's relative_humidity_pct default. A module-local immutable routing policy now resolves manual, workflow and supplied API data before any permission request. Upstream JSON is not masked by scalar preset defaults; the humidity alias is normalized without changing public fields.
- The three screens bypassed native Ask when run gates and exposed fixed site/target inputs. They now wait for Run when runtime inputs are declared, derive controls from the existing capability setting definitions and hide fixed settings. Signed inputs retain access to a minus sign; numbers retain units and declared range validation.
- Acquisition closures read mutable inputs and wrote fetched provider JSON back into saved/editable preset state. Each run now owns one input snapshot, including while permission is pending. Fetched payloads belong to the original execution only; later edits survive and are not attributed to the earlier calculation.
- Callback GPS, overlapping provider requests and broad runCatching blocks allowed stale completion and treated cancellation as ordinary failure. The three screens now share a local single-flight controller and the existing cancellable Astronomy GPS bridge. Cancellation invalidates completion; an older blocked request cannot publish, confirm or release a newer request's gate. Interruption exposes explicit retry instead of automatic replay.
- Imaging-window cancellation could create and return a successful astronomy-only fallback. Cancellation now propagates without evidence. Ordinary hourly-provider failure retains the useful astronomy-only calculation, but records data_source=astronomy_only and a forecast_warning. The limitation is visible in the result and its shared summary, and remains in canonical request/audit data.
- Completed results vanished on rotation while attempted/loading state survived. The existing framework store now retains the exact original ExecutionResult/FULL envelope. Missing originals require explicit recovery; no historical evidence is reconstructed from display values and no provider fetch replays automatically.
- Module code and the shared scaffold could both automatically confirm the same execution. Automatic confirmation now comes through the scaffold once, with a saved original-ID delivery guard through recreation.
- Invalid or incomplete supplied sites no longer silently become GPS sites. Manual coordinates, including 0/0, take precedence over stale Plus Codes. Automatic mode preserves supplied valid site precedence and the legacy 0/0 unspecified-site sentinel. Incomplete upstream measurements show their canonical failure text instead of a blank success-looking result.

AstronomyAutoScreens.kt keeps the existing shared helper behavior for other Astronomy screens and delegates these three UIs. New local policy/controller/screen files do not introduce a shared-framework module-specific branch. AstronomyMethod.kt, scientific calculations, descriptors, capability IDs, declared settings/outputs, maturity, API definitions, repositories and source forms are unchanged.

## Verification

Both debug app and Android-test APK builds pass on final sources. git diff --check passes.

Final focused JVM run: 38 passed, zero failed/errors/skipped:

- 13 Astronomy input-routing/site/alias/frozen-input cases.
- 6 single-flight/cancellation/controller cases.
- 4 original retained-store cases.
- 5 native closeout-policy cases.
- 5 registry-parity cases (80 modules / 381 capabilities).
- 5 existing ResultShare cases.

Final clean Android run: 32 passed in one run on the disposable read-only API 37 ARM emulator:

- 14 new actual three-screen lifecycle/input/provider cases, with several cases iterating multiple capabilities/routes.
- 9 existing Astronomy dashboard lifecycle/input cases.
- 3 existing cancellable GPS task cases.
- 3 actual separate-UID chooser/file-delivery cases for scanner PDF/OCR/ZIP and timestamp-proof ZIP/audit.
- 3 existing native Share contract cases.

The preceding 31-case exploratory run passed 29 cases. Two new tests inspected accessibility state before Compose updated (Working and fallback warning). Both fixture waits were corrected, a canonical-error case was added, and the complete final 32-case run passed. These were not additional production failures.

New UI coverage includes runtime numeric controls/fixed-field hiding, offline/manual/upstream routes, supplied forecast/manual-site routing, exact original/FULL retention across recreation for all three screens, missing-store recovery, current/hourly interruption, edits during a blocked fetch, cancellation without fallback, ordinary-error warning/audit, automatic completion once, late old-response rejection and cancelled injected GPS. No real network or GPS measurement was used. Missing-store recovery simulates unavailable evidence rather than real OS process death.

## Sharing and protected boundaries

ResultShare.kt was not edited. Its SHA-256 remains:

6fbfc7a405ead303a0b1cba27fbc3012416ab10384b015350bc62eedd610015e

The user-verified file-first WhatsApp compatibility correction remains intact. The final Android run verifies real PDF/ZIP byte delivery to another UID, not an installed WhatsApp recipient or a live TSA. Existing signing diffs are unchanged. No NFC credential, Sentinel, clinical-trial source/contract, signing or timestamp implementation was changed by this increment.

## Remaining work and limits

- Astronomy camera/sky-test/focus, polar alignment, mount sampling, NASA/SAF/light-pollution and session/interactive lifecycle workflows remain separate review work. The earlier dashboard checkpoint remains valid; this increment does not certify all 12 capabilities.
- Real permission denial/dialog recovery, Open-Meteo availability and payload quality, NASA/maps, physical device accuracy, actual native preset/protocol/ODK/Collect caller behavior and OS process death still require acceptance.
- Cancellation prevents stale evidence/publication; synchronous provider I/O may continue until its own completion/timeout.
- These screens retain the shared scaffold's existing native closeout limitations: final preset SHARE dispatch and synchronous preset Downloads saving still need the broader scaffold closeout review. Explicit result Share remains available. No claim of complete preset Share/Save parity is made here.
- No full-suite green claim replaces the historical baseline failures. Large caller-provided JSON/active-session/log/Binder limits remain open.
- Disposable emulator only; no physical phone installation, commit, push, tag or release.
