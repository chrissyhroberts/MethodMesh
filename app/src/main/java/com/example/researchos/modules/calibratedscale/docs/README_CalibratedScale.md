# Calibrated scale measurement

Collects a scalar or two-ended range using a physically calibrated on-screen line. The line length is calculated from the requested millimetres and the device calibration stored by ResearchOS.

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
com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',return_mode='flat')
```

The ODK example uses a multi-field intent group. The fixed method route above
is placed in `body::intent`; `prompt`, `vas_length_mm`, and the return fields
are children of the same `field-list` group. ODK sends the child values as
Android extras. No dynamic XPath is embedded in `body::intent`.

Capability configuration also travels through ordinary child fields. In
particular, the range example sends `use_range=true` and the vertical example
sends `vertical_mode=true` as visible Yes/No fields. Do not place these flags
inside `body::intent`: some ODK group-intent paths retain the method route but
discard additional configuration parameters.

## Inputs

| Input | Required | Description |
|---|---:|---|
| `vas_length_mm` | No | Physical line length, 40–200 mm; default 100 mm. |
| `vertical_mode` | No | `true` for vertical display; otherwise horizontal. |
| `minimum`, `maximum` | No | Numeric measurement range. |
| `use_range` | No | Display linked lower and upper scales. |
| `prompt`, `lower_label`, `upper_label` | No | User-facing labels. |
| `show_endpoint_labels`, `show_current_score` | No | Display controls. |

## Outputs

`value`, `minimum`, `maximum`, `lower_value`, `upper_value`, `use_range`, `scale_length_mm`, `scale_length_dp`, `dp_per_mm`, and `vertical_mode`.

## ODK examples

Each workbook is independently importable:

- [`example_odk_CalibratedScale.xlsx`](example_odk_CalibratedScale.xlsx) — calibrated 50 mm, 0–100 horizontal scale.
- [`example_odk_CalibratedScaleRange.xlsx`](example_odk_CalibratedScaleRange.xlsx) — linked lower and upper scales with editable labels.
- [`example_odk_CalibratedScaleMinMax.xlsx`](example_odk_CalibratedScaleMinMax.xlsx) — editable custom minimum and maximum, initially 0–10.
- [`example_odk_CalibratedScaleVertical.xlsx`](example_odk_CalibratedScaleVertical.xlsx) — calibrated 50 mm vertical scale.

All four send an editable `prompt`, wait for substantive marker movement, and
return the selected measurement plus physical calibration evidence.
