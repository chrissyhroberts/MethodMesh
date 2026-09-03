# Psychomotor vigilance test (PVT)

Capability: `psychomotor.vigilance.run`  
Module: `psychomotorvigilance`  
Version: `0.1.0`  
Status: **Development**

## What this capability does

Runs a classic simple visual reaction-time / sustained-attention task based on the **Psychomotor Vigilance Test (PVT)**. It is intended for repeated behavioural alertness measurements where a short, simple task with little learning burden is useful.

Two published configurations are implemented:

| Protocol | Task duration | Random inter-stimulus interval | Lapse definition |
|---|---:|---:|---:|
| `pvt_10_standard` | 10 min | 2–10 s | RT ≥500 ms |
| `pvt_b_3` | 3 min | 1–4 s | RT ≥355 ms |

Responses below 100 ms, or responses made before a visible stimulus, are classified as false starts. The response timeout is 30 s. A 1 s RT feedback period is included in the next inter-stimulus interval.

The 10-minute configuration follows the standard PVT paradigm described in the literature. The 3-minute PVT-B was developed and validated as a briefer sleep-loss-sensitive variant; it is **not** treated as interchangeable with the 10-minute PVT. The protocol key is therefore always retained in the output and audit record.

## Validation status: important distinction

The **PVT paradigm** is well established and clinically/research used. This specific **MethodMesh Android implementation is not independently clinically validated and is not yet physically calibrated on each device**.

That distinction matters because PVT differences of only a few milliseconds can be meaningful. Basner et al. proposed system timing bias within ±5 ms and latency SD ≤10 ms as maximally allowable calibration margins for PVT systems. Android software timestamps alone cannot prove that a particular display/touchscreen stack meets those limits.

For that reason this capability remains **Development** until representative physical-device timing characterisation has been completed. The audit JSON explicitly records `device_calibrated=false` / `calibration_status=not_characterised` rather than implying equivalence to dedicated validated PVT hardware.

## Native workflow

1. Choose **Standard PVT — 10 minutes** or **PVT-B — 3 minutes**.
2. Optionally set a short pre-test countdown (default 3 s).
3. Press **Start**.
4. The active test locks the current screen orientation and keeps the display awake.
5. A red target box remains visible. After a random delay, a yellow millisecond counter appears.
6. Tap the screen as soon as the counter appears. MethodMesh uses `ACTION_DOWN`, not finger release, as the response event.
7. The measured reaction time is displayed briefly, then the next random interval begins. Premature responses are recorded as false starts.
8. At completion, MethodMesh shows a compact result and retains trial-level timing data in audit JSON.

Recommended participant instruction:

> Keep your finger ready and tap the screen as quickly as possible whenever the yellow counter appears. Do not anticipate the stimulus. If you tap too early, wait for the next one.

## Timing implementation

The implementation is independently written for MethodMesh rather than copied from an external PVT repository.

- Scheduling uses Android `Handler`, which uses the monotonic `SystemClock.uptimeMillis()` time base.
- The software stimulus onset marker is captured on the first `View.onDraw` that paints the active counter.
- Touch response time is taken from `MotionEvent.ACTION_DOWN.eventTime`, which Android documents as using the same `SystemClock.uptimeMillis()` time base.
- The screen is kept awake during the test, so `uptimeMillis()` does not cross device deep sleep.
- Raw software onset and response timestamps are retained per trial.

This avoids wall-clock adjustments during a reaction-time interval. It does **not** remove device-specific display scan-out, rendering, touchscreen sampling, or input-stack latency. Those require external calibration hardware if absolute cross-device comparability at millisecond scale is needed.

## Scoring

Primary/compact outputs include:

- **response speed**: mean reciprocal reaction time, `mean(1000 / RT_ms)`, in s⁻¹;
- **lapses** using the protocol-specific threshold;
- **false starts**;
- **lapses + false starts**;
- **valid responses**;
- **performance score**: `100 × [1 − (lapses + false starts)/(valid responses + false starts)]`.

The audit JSON also includes mean and median RT, fastest 10% mean RT, slowest 10% response speed, lapse probability, timeouts, protocol parameters and every trial.

A timeout is classified as a lapse and stored with a 30,000 ms RT for scoring/audit consistency. This choice is explicit in the protocol metadata.

## Inputs/settings

- `protocol`
  - `pvt_10_standard` (default)
  - `pvt_b_3`
- `countdown_seconds`
  - integer 0–10
  - default `3`

Published task duration, ISI ranges and lapse thresholds are intentionally not exposed as arbitrary user settings in v0.1.0. That reduces accidental creation of a non-standard task while still allowing the two documented protocol variants.

## Outputs

Main result:

- `pvt_result` — compact human-readable summary, e.g. `Response speed 4.213 s⁻¹ · 2 lapses · 0 false starts`

Core scalar fields:

- `pvt_protocol`
- `pvt_response_speed_per_s`
- `pvt_lapses`
- `pvt_false_starts`
- `pvt_lapses_plus_false_starts`
- `pvt_valid_responses`
- `pvt_performance_score_percent`

Background/audit fields:

- `pvt_status`
- `pvt_audit_json`
- `pvt_error`

`pvt_audit_json` contains:

- method/version/status;
- protocol parameters;
- wall-clock session start/end;
- monotonic task duration;
- trial-by-trial ISI, onset, response, RT and outcome;
- secondary PVT metrics;
- device manufacturer/model, Android API level, display refresh rate and screen size;
- timing implementation description;
- device-calibration warning;
- scientific protocol references.

Native sharing should use `pvt_result` as the useful headline rather than dumping the audit JSON.

## Preset workflow

A preset can fix either or both settings. When a setting is fixed, the capability screen uses `settingShouldBeShown(...)` so it is not redundantly shown during a native preset run.

A native protocol/scheduled run can therefore fix the protocol and countdown, then launch directly into the test.

## ODK/XLSForm workflow

The example workbook is `example_odk_psychomotor_vigilance.xlsx`.

The MethodMesh intent is placed on a `begin_group` row with `field-list` appearance, following the ODK multi-field external-app pattern. The child questions are return fields.

Example intent:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='psychomotor.vigilance.run',input_protocol=${pvt_protocol_input},input_countdown_seconds=${countdown_seconds_input},input_payload_mode='FULL',return_mode='flat')
```

ODK receives scalar results plus `methodmesh_full_json`, which provides the full background/audit payload without making the trial list the primary field result.

## Permissions and services

None.

The test itself is fully local/offline. It does not require location, camera, microphone, Bluetooth, storage, a web service or an account. No participant/test content is sent to an external service by this capability.

## Known limitations

1. **Device timing is uncalibrated.** Software timestamps cannot establish display/touch hardware latency.
2. Touchscreen response modality is not identical to every dedicated PVT response-button implementation.
3. Different displays, refresh rates, touch sampling rates and Android devices can produce systematic timing differences.
4. The PVT-B 355 ms lapse threshold was developed for the brief protocol and may itself be hardware-sensitive; its protocol identity must not be discarded.
5. This capability should not be represented as a diagnostic medical device or as independently clinically validated.
6. Production promotion should require physical timing characterisation, native/preset/ODK round-trip tests, orientation testing, and a passing full-project debug build.

## Scientific basis

- Dinges DF, Powell JW. **Microcomputer analyses of performance on a portable, simple visual RT task during sustained operations.** *Behavior Research Methods, Instruments, & Computers.* 1985;17(6):652–655. DOI: https://doi.org/10.3758/BF03200977
- Basner M, Dinges DF. **Maximizing sensitivity of the Psychomotor Vigilance Test (PVT) to sleep loss.** *Sleep.* 2011;34(5):581–591. DOI: https://doi.org/10.1093/sleep/34.5.581
- Basner M, Mollicone D, Dinges DF. **Validity and sensitivity of a brief psychomotor vigilance test (PVT-B) to total and partial sleep deprivation.** *Acta Astronautica.* 2011;69(11–12):949–959. DOI: https://doi.org/10.1016/j.actaastro.2011.07.015
- Basner M, Moore TM, Nasrini J, Gur RC, Dinges DF. **Response speed measurements on the psychomotor vigilance test: how precise is precise enough?** *Sleep.* 2021;44(1):zsaa121. DOI: https://doi.org/10.1093/sleep/zsaa121

## Android timing references

- Android `SystemClock`: https://developer.android.com/reference/android/os/SystemClock
- Android `MotionEvent`: https://developer.android.com/reference/android/view/MotionEvent

## Open-source implementations reviewed / attribution

See `THIRD_PARTY_NOTICES.md`.
