# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/conversions/`.
- Lifecycle: Development.
- Stable method ID and input/output contracts are unchanged: `conversion.calculate` remains the sole canonical capability.
- The refreshed native screen is module-local and does not require shared UI special-casing.
- The screen now uses Compose clipboard support for direct tap-to-copy of the working scalar result; no new Android permission is required.
- Conversion mathematics remains pure Kotlin; result rendering now accepts an additive `decimal_places` setting (0–10, default 4) without changing the canonical method ID or output field names.
- The canonical showcase XLSForm now includes the declared `conversion_metadata_json` return in addition to `methodmesh_status` and `methodmesh_full_json`.
- No network, device permission, service or module-external resource dependency is introduced.
- This handoff contains only the module, not the surrounding Android/Gradle project. After reintegration run the repository's normal build plus focused module tests and XLSForm validation before promotion.

- Runtime result projection is deliberately dual: tapping the headline answer copies the answer only; tapping **Working** copies the complete working/formula plus answer. ODK already receives these as `conversion_value`/`conversion_unit` and `conversion_summary` respectively, so no new return field or breaking contract is introduced.

- Decimal precision is available in native use, presets/protocols and ODK as `decimal_places` / `input_decimal_places`; it controls both `conversion_value` rendering and the numeric values shown in `conversion_summary`.
