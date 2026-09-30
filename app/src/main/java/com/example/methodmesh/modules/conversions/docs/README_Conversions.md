# Conversions / General Calculator

Version: **0.2.1**  
Status: **Production**  
Connectivity: **Offline**

A dependency-light, completely offline conversion and calculation module with direct physical-unit and number-representation conversion. The native surface is a consolidated calculator dashboard: conversion families and calculator modes are visible as first-class mode buttons, the selected mode stays prominent, inputs are tailored to the task, and deterministic results update in place without a separate Calculate/results step.

## Capability

- `conversion.calculate` — physical-unit conversion, number-representation conversion, percentages, ratios/proportions, date difference/arithmetic, age calculation and simple geometry.

The capability contract is intentionally unchanged by the dashboard refresh. Direct native use, presets, protocols and ODK/XLSForm all invoke the same canonical method.

### Direct conversion modes

Length, area, volume, mass, temperature, speed, pressure, energy, power, angle, data size and **Number**.

**Number** converts directly between Decimal, Scientific, Engineering, SI-prefix, Binary, Octal and Hexadecimal representations. Decimal/scientific/engineering entry can use the same safe arithmetic-expression parser as ordinary unit conversion; binary/octal/hex use base-specific on-screen keypads; SI entry exposes direct prefix keys (`p`, `n`, `µ`, `m`, `k`, `M`, `G`, `T`). Binary/octal/hex outputs require an integer value.

### Calculator modes

Percentage, ratio/proportion, date difference, date arithmetic, age and simple geometry.

## Native dashboard behaviour

- A readable four-column mode matrix keeps every calculation family visible at once; it reflows vertically instead of shrinking labels into a tiny horizontal strip.
- The current mode is strongly indicated and the mode can be changed without leaving the working screen when `category` is a runtime setting.
- Direct conversions place **From** and **To** as two adjacent rows beneath the result; both sets remain visible simultaneously and the To row includes a direct swap action. Number representation uses the same interaction model.
- Numeric modes expose **Decimals − n +** immediately beside the result. Precision can be stepped from 0–10 places while looking at the answer; changes update the answer and working immediately and remain available as the canonical `decimal_places` setting for presets/protocols/ODK.
- Ordinary unit and number-representation entry uses a capability-owned calculator keypad rather than the Android software keyboard. The expression readout accepts digits, decimal point, `+`, `−`, `×`, `÷`, parentheses, backspace and clear. Results calculate live, so there is deliberately no redundant equals key; the former equals position is a prominent backspace/edit key.
- `input_value` may be either a plain number or a safe arithmetic expression. Expressions are evaluated locally with normal operator precedence before the selected conversion runs. For example, `20*44` with `from_unit=mm` is evaluated as `880 mm` and then converted. No arbitrary-code evaluation is used.
- Number representation adapts the keypad to the source representation: Decimal/Scientific/Engineering expose arithmetic plus `EXP`, SI exposes prefix keys, and Binary/Octal/Hex expose only valid digits.
- Percentage, ratio, date and geometry modes use human-readable, task-specific labels rather than exposing raw operation identifiers.
- Results are calculated live on the same screen as soon as the required inputs are valid and stay near the top of the calculator surface, calculator-style. The refreshed surface deliberately uses larger type, larger selectors and larger keypad targets; density is achieved by hierarchy rather than microscopic controls.
- The displayed primary answer is tap-to-copy and copies the complete usable answer (value plus unit where the unit is part of the answer), not its UI label.
- A separate visible **Working** line shows the formula/working together with the final answer; tapping it copies that full working string. The canonical `conversion_summary` output carries the same working+answer projection, while `conversion_value` and `conversion_unit` remain the structured answer fields.
- Working input state uses saveable Compose state, so ordinary activity recreation restores the working configuration and the result is regenerated from it.
- The native instrument projects the ordinary MethodMesh lifecycle as compact in-panel Back/Cancel/Commit controls while still returning through the shared launch-origin closeout contract; it does not introduce a private result screen.
- On manual/native **Commit**, the current result is frozen in-place and the keypad/working region becomes a compact post-commit action bay with **Copy**, **Share**, **Save**, **Full JSON**, **Edit**, and **Done**. Share/Save use the capability’s canonical human result (`conversion_summary`, falling back to answer value+unit) through the shared MethodMesh transport services; the module does not build receiver-specific Android intents.
- ODK/external automatic-return runs do not expose this native action bay: Commit returns the canonical payload directly to the caller, preserving the external-roundtrip contract.


## ODK Integration Card

```text
ODK INTEGRATION

Capability
Conversions / General Calculator
conversion.calculate

Tags
Maturity: Production
Connectivity: Offline

ODK INPUTS
category | text | optional (default length) | conversion/calculator family
value | text | required where applicable | primary number or safe arithmetic expression
value2 | text | conditional | secondary numeric value
value3 | text | conditional | third numeric value for solve_proportion
from_unit | text | optional when a category default is acceptable | source unit/number representation
to_unit | text | optional when a category default is acceptable | target unit/number representation
operation | text | conditional | percentage/ratio/date/geometry operation
date1 | date/text YYYY-MM-DD | conditional | first/base/birth date
date2 | date/text YYYY-MM-DD | conditional | second/at date
shape | text | conditional | rectangle, triangle or circle
decimal_places | integer | optional (default 4) | output precision 0-10
Interactive acquisition: optional native calculator UI; no sensor/device acquisition

INTENT CALL
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='length',input_value='20*44',input_from_unit='mm',input_to_unit='ft',return_mode='flat',input_payload_mode='FULL')

MODIFIERS
None beyond the declared optional canonical inputs above.

CANONICAL RETURNS
conversion_status | text | always | succeeded/failed
conversion_value | text | on success | rendered result value
conversion_unit | text | on success | target unit/representation identifier
conversion_summary | text | on success | human-readable working plus final answer
conversion_metadata_json | text/JSON | on success | category/operation/unit/precision/expression metadata
conversion_error | text | on failure | human-readable error
methodmesh_full_json | text/JSON | always on handled ODK roundtrip | canonical execution/audit envelope

RETURN FIELD PLACEMENT
Each canonical return key is a child leaf of the single MethodMesh intent group.
Canonical example: unprefixed return keys, one MethodMesh call, no return namespace.

FILE RETURN SEMANTICS
None. Conversions returns scalar/text/JSON only.

RUNTIME
Inputs: canonical settings/runtime fields listed above.
Beef: conversion_value + conversion_unit; conversion_summary is the readable working projection.
Metadata: conversion_metadata_json and canonical FULL JSON are secondary/audit projections.
```

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='length',input_value='20*44',input_from_unit='mm',input_to_unit='ft',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='number',input_value='255',input_from_unit='decimal',input_to_unit='hex',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='percentage',input_operation='percent_change',input_value='80',input_value2='100',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversion.calculate',input_category='date_arithmetic',input_date1='2026-09-05',input_value='14',input_operation='add_days',return_mode='flat')
```

## Inputs

- `input_category` — one of the documented calculation families.
- `input_value` — primary numeric value **or arithmetic expression**; also the date-arithmetic amount or geometry A/radius. Supported arithmetic operators are `+`, `-`, `*`/`×`, `/`/`÷`, unary negative and parentheses.
- `input_value2` — secondary numeric value.
- `input_value3` — third value used by `solve_proportion` for `A:B = C:X`.
- `input_from_unit`, `input_to_unit` — source/target identifiers for direct conversion. For `category=number`: `decimal`, `scientific`, `engineering`, `si`, `binary`, `octal`, `hex`.
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
- `example_odk_conversion_calculate.xlsx` is retained as a legacy/migration example.

The UI refresh does not alter existing canonical input or output field names, so existing ODK calls remain compatible. `input_value` is now treated as text so ODK may pass either a plain number or an arithmetic expression. `input_decimal_places` remains optional; ODK forms may expose it when controlled precision is required.

## Permissions and offline behaviour

No permissions and no network access. Constants are embedded in the module. Number representation is also entirely local and does not depend on a scientific service or chemistry catalogue. Data-size conversion distinguishes decimal (`KB`, `MB`, `GB`) and binary (`KiB`, `MiB`, `GiB`) units.

## Known limitations

- Geometry is intentionally simple: rectangle area/perimeter, triangle area from base/height, circle area/circumference from radius.
- Date arithmetic uses ISO local dates and calendar arithmetic; it does not represent time zones or times of day.
- The module handoff does not contain the full Android app/Gradle project, so app-context compilation must be run after reintegration.

## Native instrument UI

The native capability follows the instrument-dashboard standard now incorporated into the MethodMesh Master Book. It is a full-bleed single-surface instrument that replaces generic step/version/form chrome for this capability, keeps all calculation families visible in a readable reflowing matrix, keeps the live result high like a calculator display, uses adjacent From/To rows, reserves the lower instrument region for an integrated calculator keypad, keeps precision beside the result, and supports tap-to-copy for both answer and full working. The current pass intentionally increases text sizes and touch targets because the available screen area should be spent on legibility rather than extreme visual compression.

On manual/native **Commit**, the canonical result is frozen on the same surface. The keypad/working region becomes a compact post-Commit action bay exposing **Copy**, **Share**, **Save to Downloads**, **Include full JSON / audit**, **Edit / new run** and origin-aware **Done/finish** actions. Share and Save delegate to the shared MethodMesh result transport/persistence services; ODK/external automatic-return runs suppress this native action bay and return the canonical payload directly to the caller.
