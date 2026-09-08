# Ham Radio v0.4.0

## Added

- `ham.dashboard` as the ninth public Ham Radio capability.
- Native live operating dashboard modelled on the Astronomy dashboard-as-capability pattern.
- Automatic GPS QTH with local conversion to a six-character Maidenhead locator and manual locator fallback.
- Best-band hero result, ranked alternatives, NOAA space-weather metrics, QTH provenance and optional PSK Reporter activity.
- Quick operating-range controls: Regional 500 km, Continental 1500 km, DX 2500 km and Long DX 7000 km.
- Quick mode controls: General, SSB, CW and FT8.
- Explicit **Use this snapshot** behavior in MethodMesh Dashboard presentation: live Refresh remains preview state until the operator commits a snapshot.
- `show ham radio dashboard` RIL binding.
- XLSForm dashboard operation and dashboard core return fields.

## Provider behavior

- A dashboard refresh fetches NOAA once and reuses the returned Kp/F10.7/R values for local band ranking, avoiding duplicate NOAA calls.
- PSK Reporter remains optional and retains the repository-level hard minimum of 420 seconds between upstream attempts.
- Range/mode changes recalculate locally and do not themselves trigger a provider request.

## Validation

- Dashboard method/repository/calculation sources compile against focused current-contract Kotlin stubs.
- Dashboard Compose screen compiles against focused workflow/Android/GMS stubs.
- Module declaration compiles against current MethodMesh module/settings stubs.
- XLSForm was re-exported with `artifact_tool`, visually inspected, and scanned with no spreadsheet formula-error cells found.

## Still Development

A complete MethodMesh checkout is still required for `./gradlew :app:assembleDebug`, device/emulator GPS/provider testing, rotation testing and an actual ODK Collect round trip.
