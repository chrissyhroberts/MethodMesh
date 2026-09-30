# Conversions validation — v0.2.1

Status: **Production**

This is the strict post-production review of the v0.2.0 / calculator-dashboard work before the v0.2.1 patch release. The review was run against the current MethodMesh module/runtime contracts available in the project checkout/reference material. No canonical method ID, input key or output key was changed.

## Capability inventory

- Module: `conversions`
- Canonical capability: `conversion.calculate`
- Capability version: `0.2.1`
- Maturity: `Production`
- Connectivity: `OFFLINE`
- Native screen: immersive, capability-owned calculator/conversion instrument
- Canonical outputs: `conversion_status`, `conversion_value`, `conversion_unit`, `conversion_summary`, `conversion_metadata_json`, `conversion_error`
- ODK shared audit return: `methodmesh_full_json`

## Findings fixed in v0.2.1

1. **Commit could remain enabled for a failed calculation.** Some mathematically invalid but syntactically complete inputs (for example division-by-zero or fractional output requested as binary) produced a failed `ExecutionResult`, but the header treated any non-null result as committable. Commit is now enabled only for `conversion_status=succeeded`, and `commitCurrent()` independently enforces the same boundary.
2. **Automatic launch used presentation mode instead of the canonical start policy.** The screen previously attempted execution for any `IntentLaunch`. It now follows `CapabilityScreenContext.startsImmediately`, and only automatic-returns a successful result. Missing/invalid interactive inputs therefore remain on the instrument rather than returning a synthetic failed capture merely because the presentation is intent-backed.
3. **Generic committed-result projection was too broad for a calculator.** The generic projection can expose several core fields as labelled key/value text. Conversions now uses its canonical human result (`conversion_summary`, falling back to value+unit) as the native Copy/Share/Save beef, while FULL JSON remains optional and off by default. Shared `ResultShare` and `OutputExportRepository` still own transport/persistence.
4. **Date readiness checked shape rather than validity.** `YYYY-MM-DD` strings are now parsed as real `LocalDate` values before the live result is considered ready. Impossible calendar dates no longer create a failed result that appears ready to commit.
5. **Date entry requested a numeric keyboard despite requiring hyphens.** Date fields now request text input so `YYYY-MM-DD` can be entered on normal Android keyboards.
6. **Date arithmetic silently truncated fractional amounts supplied externally.** Date arithmetic now rejects non-whole day/week/month/year amounts rather than converting them with `toLong()` truncation.
7. **Age could return negative completed years.** An `at` date before the birth date now fails explicitly.
8. **Production metadata was incomplete.** The descriptor now declares canonical `maturity=Production`, `connectivity=OFFLINE`, interaction lifecycle and ODK metadata-return hints. Module/capability release identity is sourced from one `ConversionsModule.VERSION` constant.
9. **Legacy active XLSForm was behind the current ODK return contract.** The retained `example_odk_conversion_calculate.xlsx` now captures `methodmesh_status`, `conversion_metadata_json` and `methodmesh_full_json`, requests FULL payload metadata, and keeps the deployed `form_id` unchanged. XML companions were brought into parity.
10. **Documentation drift.** Native layout documentation now describes the actual four-column mode matrix, the current Master Book is referenced without inventing a new book version, and a complete ODK Integration Card is included.
11. **Committed header still exposed Cancel.** After Commit the top `×` could still invoke the cancellation callback even though a canonical payload had already been frozen. Cancel is now suppressed in committed state; closeout goes through the committed Done/Share/Save rail.
12. **Unit swap changed meaning rather than direction.** The physical-unit swap previously exchanged only the From/To labels, so `1000 m → 1 km` became `1000 km → 1,000,000 m`. A successful result is now carried forward as the new source value, matching the already-correct Number-mode reverse-conversion behaviour.
13. **Headless/direct calls could silently treat missing numeric inputs as zero.** The native UI guarded Commit, but the canonical method itself converted blank `value`/`value2`/`value3` strings to `0.0`. Direct, preset, protocol or ODK execution could therefore produce a plausible-looking success from an incomplete request. Required inputs are now validated inside the method for every category before calculation.
14. **Invalid Number representation and Ratio operation values were too forgiving.** An explicitly invalid Number `from_unit`/`to_unit` could silently fall back to defaults, and an unknown Ratio operation fell back to A÷B. Explicit invalid contract values now fail clearly; defaults are used only when optional selectors are genuinely absent.
15. **README contract details had small drift.** The retained legacy XLSForm filename was documented with the wrong punctuation, and From/To were described as unconditionally required even though the canonical method supplies category defaults when they are omitted. The integration card now matches the shipped files and method behaviour.
16. **A user-facing geometry error still named an obsolete module version.** The triangle-operation error embedded `v0.1.0` even though version identity belongs in canonical metadata/provenance. The error is now version-neutral.
17. **Scientific/engineering/SI formatting could cross a rounding boundary without renormalising.** Values such as `9.999` at two decimal places could render as `10e0`, and an SI mantissa could become `1000 k` rather than moving to the next exponent/prefix. Notation rendering now rechecks the rounded mantissa and advances the exponent/prefix when required.
18. **Production maturity metadata was duplicated through both canonical and legacy keys.** The current resolver treats descriptor `status` only as a migration fallback when `maturity` is absent. v0.2.1 therefore declares the single authoritative `maturity=Production` tag plus `connectivity=OFFLINE`, rather than carrying both maturity keys forward.
19. **Headless numeric inputs had no defensive size bound.** Native entry is already short, but a direct/ODK caller could supply a very long expression or extreme BigDecimal exponent and force excessive parsing/string expansion. The pure method now caps expression/number input at 256 characters and Number magnitude at decimal exponent ±1000, returning a normal failed calculation rather than risking unbounded work.

## Checks performed

- The complete `ConversionsMethod.kt` and `ConversionsModule.kt` were compiled against minimal contract stubs matching the current MethodMesh interfaces: **PASS**.
- Module inventory checks confirmed the single canonical method ID, exact canonical output set, v0.2.1 release identity, all 18 category choices and coverage of every physical/temperature/number representation unit in the module settings: **PASS**.
- Canonical calculation tests exercised unit conversion, expression conversion, temperature, Number representations, percentage, ratio, date gap/arithmetic, age, geometry, missing-input rejection, invalid selector rejection, division by zero, fractional date rejection, negative-age rejection, fractional radix rejection and scientific/engineering/SI rounding-boundary normalisation: **PASS**.
- Every pair of units in each linear conversion family was exercised in forward/reverse round-trip tests at a scale chosen to avoid deliberate display-precision loss; all conversion-factor paths passed. Temperature C/F/K pairwise round trips also passed: **PASS**.
- Exact pure-Kotlin parser/number-representation objects were additionally exercised for arithmetic precedence, unary negative, exponent entry, decimal↔hex, binary↔decimal, hexadecimal↔binary, SI-prefix rendering and expression→hex: **PASS**.
- All module-owned XML examples parsed successfully as XML: **PASS**.
- Both XLSForms were imported and inspected with the spreadsheet tooling; required `survey`, `choices` and `settings` sheets are present. The canonical showcase already carried the complete return set; the retained legacy workbook was upgraded to the same current return contract: **PASS**.
- ZIP/handoff root and module-owned file placement are checked during final packaging.
- Current MethodMesh interfaces were cross-checked for `MethodMeshModule.version`, `CapabilityHostPresentation.Immersive`, `CapabilityScreenContext.startsImmediately`, canonical maturity resolution and shared result/export services.
- Standalone Compose compilation is not meaningful without the Android/Compose/app classpath. A parser smoke pass found no Kotlin syntax-specific defect; unresolved Android/project symbols are expected outside the app checkout.

## Reviewed trade-offs retained

- Top mode tiles remain visually 40 dp high so the full calculator fits one handset screen; the release does not silently enlarge them again after the user-approved compact pass.
- Percentage, ratio, geometry and date tools retain their capability-specific field controls. The custom keypad is used where it is the natural direct-conversion input surface rather than being forced onto date/text entry.
- Full Android Gradle and physical-device validation remain an integration gate because this handoff is a module folder, not a complete app checkout.

## Integration gate

After dropping the folder into the MethodMesh project, run at minimum:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then smoke-test direct native conversion, Number mode, Commit → Copy/Share/Save/Edit/Done, a native preset, a protocol step and one ODK → MethodMesh → ODK roundtrip. Production status does not waive those repository/device integration checks.
