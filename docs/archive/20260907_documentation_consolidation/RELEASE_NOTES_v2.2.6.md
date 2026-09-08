# MethodMesh v2.2.6

This release packages the first online-data/workbench sweep and tidies the production capability lanes.

## Highlights

- Added the online API data framework for declared, auditable GET requests.
- Added bundled online data definitions including Open-Meteo, GDACS, USGS, World Bank indicators, Frankfurter exchange rates and GBIF occurrence lookups.
- Added a shared API result cache so online requests prefer fresh data but can fall back to cached results.
- Added a Workbench online API panel for trying bundled API links and inspecting returned data.
- Added the `api.get` capability for running declared API definitions from MethodMesh, presets and ODK-style calls.

## Online data and API output

- API calls now return compact selected values for native use, with full JSON retained for audit/ODK payloads.
- Added checkbox-based multi-field return selection for manual native API runs.
- Added last-updated / data-age feedback to API result previews.
- Improved GDACS, USGS and GBIF defaults so list-like feeds return lists rather than only the first item.
- Improved World Bank indicator handling so the latest non-empty value is returned with its matching year.
- Added World Bank country and indicator selectors with readable labels.
- Added exchange-rate output fields for rate, amount and converted amount.

## Production capability lanes

- Marked the following reviewed capabilities as Production:
  - Qutie-family Bluetooth printer
  - ML Kit language translation
  - ML Kit vision analysis
  - ODK form launcher
  - Random number generator
- Marked BLE sensor-node provisioning as internally Production while keeping it in the Workbench/tooling area.
- Updated the visible capability Production lane so newly promoted user-facing capabilities appear in the correct section.

## Roadmap

- Added `000_Roadmap.md` as the live project issue/ideas notebook.
- Added a Done section for resolved/promoted work.
- Recorded the remaining exchange-rate picker/value bug for a later pass.

## Known issue

- Frankfurter exchange-rate selection still needs another UX/data pass: some currency codes appear through multiple country labels, and selected result values may not always surface correctly, especially with non-1 optional amounts.

## Validation

- `./gradlew :app:testDebugUnitTest --tests 'com.example.methodmesh.core.onlinedata.*' --tests 'com.example.methodmesh.modules.apiget.*'` passes.
- `./gradlew :app:assembleDebug` passes.
