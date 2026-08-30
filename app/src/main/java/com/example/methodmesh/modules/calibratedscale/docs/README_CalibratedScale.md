# Calibrated scale measurement

Collects a scalar or two-ended range using a physically calibrated on-screen line. The line length is calculated from the requested millimetres and the device calibration stored by MethodMesh.

## Capabilities

### `calibrated_scale`

Displays a horizontal or vertical visual analogue scale and returns the selected value or range.

An intent opens directly on a focused measurement screen. It displays the
caller-supplied `prompt` and calibrated scale without exposing configuration or
debug controls. The participant must move the scalar marker, or both markers
for a range, and then select **Use this measurement**. ODK receives a result
only after that explicit interaction.

```text
scale_length_dp = vas_length_mm × calibrated_dp_per_mm
```

Horizontal lines retain their calibrated size and scroll when wider than the display; they are never silently shortened.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='calibrated_scale',input_prompt='Rate your pain',input_hint='0 means no pain; 100 means the worst pain you can imagine',input_vas_length_mm='50',return_mode='flat')
```

The ODK examples keep static configuration in `body::intent` under the
`input_*` namespace. Unprefixed group children are return fields only. The
participant therefore sees the requested scale interaction rather than
configuration fields such as orientation, physical length, or range mode.

## Inputs

| Input | Required | Description |
|---|---:|---|
| `vas_length_mm` | No | Physical line length, 40–200 mm; default 100 mm. |
| `vertical_mode` | No | `true` for vertical display; otherwise horizontal. |
| `minimum`, `maximum` | No | Numeric measurement range. |
| `use_range` | No | Display linked lower and upper scales. |
| `prompt`, `hint`, `lower_label`, `upper_label` | No | User-facing question, explanatory hint, and scale labels. |
| `show_endpoint_labels`, `show_current_score` | No | Display controls. |

## Outputs

Core return is deliberately small:

- scalar mode returns `value`
- range mode returns `lower_value` and `upper_value`

The unused alternative is omitted rather than populated with a default. Audit
and full JSON returns include `minimum`, `maximum`, `use_range`,
`scale_length_mm`, `scale_length_dp`, `dp_per_mm`, and `vertical_mode`, so the
selected value can be interpreted against the configured range and physical
screen calibration.

The live current-value label always shows decimal precision (`5.0`, `5.4`, or
`0.25` for a 0–1 scale) so the interaction is visibly continuous.

## ODK examples

Each workbook is independently importable:

- [`example_odk_calibrated_scale.xlsx`](example_odk_calibrated_scale.xlsx) — calibrated 50 mm, 0–100 horizontal scale.
- [`example_odk_calibrated_scale_Range.xlsx`](example_odk_calibrated_scale_Range.xlsx) — linked lower and upper scales.
- [`example_odk_calibrated_scale_MinMax.xlsx`](example_odk_calibrated_scale_MinMax.xlsx) — fixed 0–10 bounds.
- [`example_odk_calibrated_scale_Vertical.xlsx`](example_odk_calibrated_scale_Vertical.xlsx) — calibrated 50 mm vertical scale.

All four send a caller-defined prompt, wait for substantive marker movement,
and return the selected measurement. Use audit/full payload mode when the form
also needs the physical calibration evidence as a JSON field.
