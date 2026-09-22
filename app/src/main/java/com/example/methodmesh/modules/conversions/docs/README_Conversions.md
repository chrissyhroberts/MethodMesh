# Conversions / General Calculator

Status: **Development**

A dependency-light, completely offline conversion and calculation module. The native surface is a consolidated calculator dashboard: conversion families and calculator modes are visible as first-class mode buttons, the selected mode stays prominent, inputs are tailored to the task, and deterministic results update in place without a separate Calculate/results step.

## Capability

- `conversion.calculate` — unit conversion, percentages, ratios/proportions, date difference/arithmetic, age calculation and simple geometry.

The capability contract is intentionally unchanged by the dashboard refresh. Direct native use, presets, protocols and ODK/XLSForm all invoke the same canonical method.

### Unit-conversion modes

Length, area, volume, mass, temperature, speed, pressure, energy, power, angle and data size.

### Calculator modes

Percentage, ratio/proportion, date difference, date arithmetic, age and simple geometry.

## Native dashboard behaviour

- A dense mode matrix keeps every calculation family visible at once; there is no horizontal family carousel or mode navigation.
- The current mode is strongly indicated and the mode can be changed without leaving the working screen when `category` is a runtime setting.
- Unit conversions place **From** and **To** as two adjacent compact rows above the value field; both unit sets remain visible simultaneously and the To row includes a direct swap action.
- Numeric modes expose **Decimals − n +** immediately beside the result. Precision can be stepped from 0–10 places while looking at the answer; changes update the answer and working immediately and remain available as the canonical `decimal_places` setting for presets/protocols/ODK.
- Percentage, ratio, date and geometry modes use human-readable, task-specific labels rather than exposing raw operation identifiers.
- Results are calculated live on the same screen as soon as the required inputs are valid and occupy the final/bottom section of the calculator surface.
- The displayed primary answer is tap-to-copy and copies the complete usable answer (value plus unit where the unit is part of the answer), not its UI label.
- A separate visible **Working** line shows the formula/working together with the final answer; tapping it copies that full working string. The canonical `conversion_summary` output carries the same working+answer projection, while `conversion_value` and `conversion_unit` remain the structured answer fields.
- Working input state uses saveable Compose state, so ordinary activity recreation restores the working configuration and the result is regenerated from it.
- The shared MethodMesh shell still owns Commit/Cancel and launch-origin closeout; the module does not introduce a private result screen.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='length',input_value='3.5',input_from_unit='km',input_to_unit='mi',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='percentage',input_operation='percent_change',input_value='80',input_value2='100',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='date_arithmetic',input_date1='2026-09-05',input_value='14',input_operation='add_days',return_mode='flat')
```

## Inputs

- `input_category` — one of the documented calculation families.
- `input_value` — primary numeric value; also the date-arithmetic amount or geometry A/radius.
- `input_value2` — secondary numeric value.
- `input_value3` — third value used by `solve_proportion` for `A:B = C:X`.
- `input_from_unit`, `input_to_unit` — unit identifiers for unit conversion.
- `input_operation` — operation within percentage/ratio/date/geometry modes.
- `input_date1`, `input_date2` — ISO local dates (`YYYY-MM-DD`).
- `input_shape` — `rectangle`, `triangle`, `circle`.
- `input_decimal_places` — numeric display/output precision from `0` to `10`; defaults to `4` when omitted.

Important operation names remain `percent_of`, `what_percent`, `percent_change`, `increase_by_percent`, `decrease_by_percent`, `a_to_b`, `solve_proportion`, `add_days`, `add_weeks`, `add_months`, `add_years`, `subtract_days`, `area`, `perimeter`, `circumference`.

## Outputs

Normally useful/native result:

- `conversion_value`
- `conversion_unit`
- `conversion_summary` — human-readable working/formula including the final answer

Contractually available status/audit outputs:

- `conversion_status`
- `conversion_error`
- `conversion_metadata_json`

The shared MethodMesh transport additionally provides `methodmesh_full_json` on handled ODK roundtrips according to the project-wide contract.

## ODK examples

- `example_odk_showcase_conversion_calculate.xlsx` is the canonical single-invocation showcase.
- `example_odk_conversion.calculate.xlsx` is retained as a legacy/migration example.

The UI refresh does not alter existing canonical input or output field names, so existing ODK calls remain compatible. `input_decimal_places` is additive and optional; ODK forms may expose it when controlled precision is required.

## Permissions and offline behaviour

No permissions and no network access. Constants are embedded in the module. Data-size conversion distinguishes decimal (`KB`, `MB`, `GB`) and binary (`KiB`, `MiB`, `GiB`) units.

## Known limitations

- Geometry is intentionally simple: rectangle area/perimeter, triangle area from base/height, circle area/circumference from radius.
- Date arithmetic uses ISO local dates and calendar arithmetic; it does not represent time zones or times of day.
- The module handoff does not contain the full Android app/Gradle project, so app-context compilation must be run after reintegration.
