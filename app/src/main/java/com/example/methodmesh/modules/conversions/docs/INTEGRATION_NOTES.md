# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/conversions/`.
- Release: `0.2.1` · Lifecycle: Production · Connectivity: Offline.
- Stable method ID and input/output contracts are unchanged: `conversion.calculate` remains the sole canonical capability.
- The refreshed native screen is module-local and does not require shared UI special-casing.
- The screen keeps direct tap-to-copy for the live answer and working, and after Commit exposes the canonical native Copy/Share/Save/full-JSON/Edit/Done action set using shared MethodMesh transport services; no new Android permission is required.
- Conversion mathematics remains pure Kotlin. `category=number` adds Decimal/Scientific/Engineering/SI/Binary/Octal/Hex representation conversion using the existing `value`, `from_unit`, `to_unit`, `decimal_places` and output fields; no canonical field or method ID is added or renamed.
- The canonical showcase XLSForm now includes the declared `conversion_metadata_json` return in addition to `methodmesh_status` and `methodmesh_full_json`.
- No network, device permission, service or module-external resource dependency is introduced. Chemistry/molarity is intentionally not added here; that remains a separate Lab-domain concern.
- This handoff contains only the module, not the surrounding Android/Gradle project. After reintegration run the repository's normal build plus focused module tests and XLSForm validation before promotion.

- Runtime result projection is deliberately dual: tapping the headline answer copies the answer only; tapping **Working** copies the complete working/formula plus answer. ODK already receives these as `conversion_value`/`conversion_unit` and `conversion_summary` respectively, so no new return field or breaking contract is introduced.

- Decimal precision is available in native use, presets/protocols and ODK as `decimal_places` / `input_decimal_places`; it controls both `conversion_value` rendering and the numeric values shown in `conversion_summary`.
- Manual/native Commit freezes the canonical payload in-place. The committed state is reconstructed from saved committed fields across activity recreation; Edit/new run explicitly returns to mutable working state.
- Native preset closeout honours the configured Return/Share/Save completion action. ODK/external automatic-return continues to suppress manual post-Commit actions and returns directly to the caller.
- Shipped XML companions now type `input_value` as string consistently with the XLSForms so arithmetic expressions such as `20*44` remain representable across the ODK contract.
- Project-wide instrument-dashboard guidance is canonical in the current MethodMesh Master Book; the module-local `INSTRUMENT_DASHBOARD_UI_SPEC.md` is an implementation snapshot only.


- Accessibility pass: the mode matrix now reflows to four columns, control labels and readouts are larger, selectors have taller touch targets, and keypad typography is substantially larger. The UI should spend spare viewport area on readability before further compression.

- Calculator keypad cleanup: live calculation no longer exposes a redundant `=` key. The prominent bottom-right action is backspace, with duplicate backspace keys removed and editing kept entirely on-screen.
