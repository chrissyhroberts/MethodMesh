# Astronomy v0.3.0 changes

- Corrected the AHT20 integration claim: matching `sensor.read` field names does not constitute a general MethodMesh pipe.
- Removed `sensorread` from astronomy's declared module dependencies because astronomy does not invoke BLE sensor acquisition.
- `astronomy.dew_risk` standalone behaviour is now explicitly GPS + Open-Meteo first, with manual fallback.
- AHT20 values are still accepted when they genuinely arrive from an earlier action in the same multi-step `ExternalWorkflowRequest`, using MethodMesh's existing limited step-field forwarding.
- Replaced ambiguous `piped` wording in native UI/settings with `upstream workflow`, `supplied`, or `manual` language.
- Clarified that `astronomy.conditions` and `astronomy.imaging_window` orchestrate the existing shared `api.get` boundary directly for their automatic weather retrieval.
- Module/method version advanced to `0.3.0`; astronomy calculation algorithm version remains unchanged where no calculation changed.
