# MethodMesh Acoustics capability

Status: **Development**

Canonical module folder:

```text
app/src/main/java/com/example/methodmesh/modules/acoustics/
```

The Acoustics module uses the Android microphone as a local signal-measurement source. It exposes four public methods over one shared capture/DSP engine:

| Method ID | Native name | Purpose |
|---|---|---|
| `acoustic.analyse` | Acoustic analyser | Frequency/pitch, note, waveform, FFT spectrum, amplitude and derived wavelength. |
| `acoustic.tune` | Instrument tuner | Chromatic/instrument tuning with target frequency and cents deviation. |
| `acoustic.level` | Sound-level meter | Relative digital level in dBFS; optionally estimated dB SPL after calibration. |
| `acoustic.compare` | Tone comparator | Compare a stable observed tone against a target with Hz, percent or cents tolerance. |

The module is completely local/offline. Raw microphone audio is processed in memory and is **not retained** by v0.1.

## Design principles

### Frequency is measured; wavelength is derived

The microphone signal is sampled and the fundamental frequency is estimated. Wavelength is then calculated from:

```text
wavelength = speed_of_sound / frequency
```

It is therefore labelled as a **derived wavelength**, not a directly measured microphone quantity.

The default temperature-based speed of sound approximation is:

```text
331.3 + 0.606 × temperature_C   m/s
```

Humidity and atmospheric-pressure corrections are not included in v0.1. A caller can instead supply a fixed speed of sound.

### dBFS and dB SPL are not interchangeable

Uncalibrated microphone level is reported in **dBFS** (decibels relative to the digital full-scale limit). MethodMesh does not label an arbitrary phone-microphone amplitude as dB SPL.

`acoustic.level` supports an optional calibration offset:

```text
dB_SPL = dBFS + calibration_offset_db
```

The native UI can derive the offset from a known reference level and the observed Leq dBFS over the current capture. The offset is only defensible for the same device, microphone/input route, device processing path and physical configuration used during calibration.

Android devices may apply automatic gain control, filtering, noise suppression or other processing. The capture engine requests `UNPROCESSED` audio when Android advertises it; otherwise it falls back to `VOICE_RECOGNITION` and then `MIC`. The actual route and whether unprocessed input was obtained are recorded in the audit JSON.

## DSP implementation

The module deliberately adds no third-party DSP dependency.

- microphone capture: mono PCM16 `AudioRecord`;
- default sample rate: 48 kHz (44.1 kHz also available);
- analysis frame: 4096 samples;
- pitch: YIN cumulative-mean-normalized-difference detector with parabolic lag refinement;
- spectrum: Hann-windowed radix-2 FFT;
- level: RMS and peak amplitude converted to dBFS;
- Leq: energy average across captured RMS frames;
- stability: contiguous pitch observations must satisfy minimum duration, confidence and cents-SD criteria;
- raw audio: not persisted.

Algorithm constants are versioned in `AcousticsAlgorithms.kt`.

## Native workflow

### Acoustic analyser

1. Open **Acoustic analyser**.
2. Configure pitch range, reference A4 and wavelength basis if needed.
3. Press **Start listening**.
4. The screen shows:
   - detected frequency;
   - nearest chromatic note and cents offset;
   - derived wavelength;
   - dBFS level;
   - live oscilloscope trace;
   - live FFT spectrum.
5. Press **Capture result** to return a compact result and audit metadata.

A stable pitch window is preferred for the returned frequency. If no stable pitch exists but audio was captured, the analyser can still succeed as a general acoustic observation with pitch fields blank.

### Instrument tuner

1. Choose **Chromatic** or an instrument preset.
2. Optionally select a particular string; `0` means choose the nearest target automatically.
3. Play a note.
4. The tuner shows measured frequency, target note/frequency and cents deviation.
5. The target zone defaults to ±5 cents and is shown as a green zone.
6. A captured tuner result requires the configured stable-pitch window.

Bundled tuning presets:

- guitar, standard;
- guitar, drop D;
- ukulele, high G;
- ukulele, low G;
- violin;
- viola;
- cello;
- bass;
- mandolin;
- chromatic.

A4 defaults to 440 Hz and is configurable.

### Sound-level meter

1. Start in **uncalibrated** mode for dBFS measurement.
2. The screen shows current level, Leq and peak.
3. For calibrated use, expose MethodMesh and a trusted/reference meter to the same stable sound.
4. Enter the reference meter value in dB SPL.
5. Press **Calibrate from current level**.
6. MethodMesh calculates and stores the calibration offset in the current capability settings; save it in a preset when reuse is appropriate.
7. Calibrated outputs are clearly marked and the audit records the offset/reference and microphone route.

v0.1 does **not** implement A-weighting, C-weighting or IEC sound-level-meter class claims.

### Tone comparator

1. Enter a target frequency.
2. Choose tolerance mode:
   - `hz`;
   - `percent`;
   - `cents`.
3. Enter the tolerance value.
4. Play or expose the microphone to the test tone.
5. MethodMesh waits for a stable pitch window.
6. It returns the measured frequency, deviations in all three units and a `PASS`/`FAIL` style verdict through `acoustic_compare_within_tolerance`.

The comparator never accepts a single instantaneous crossing of the target as a valid stable result.

## Preset workflow

All settings are declared in `AcousticsModule.capabilitySettings()`.

1. Configure/test the relevant Acoustics method.
2. Save as a MethodMesh preset.
3. Mark settings as fixed or runtime inputs.
4. Fixed values disappear from native preset execution.
5. Runtime values are requested before capture starts.
6. Once runtime inputs are complete, a native preset performs a timed one-shot capture and shows the normal compact result.

Calibration offsets can therefore be saved in a device-specific sound-level preset rather than hidden in module code.

## ODK / XLSForm workflow

The example XLSForm uses one dynamic group Android intent. The selected operation resolves to one of the four stable method IDs; ODK supplies the relevant inputs, MethodMesh performs microphone capture, and MethodMesh returns the method-specific core fields plus audit metadata and the standard `methodmesh_full_json` envelope.

Use:

```text
docs/example_odk_Acoustics.xlsx
```

The workbook exercises all four public methods through the same intent group, keeping the standard `methodmesh_full_json` return field exact rather than inventing method-specific aliases.

### Example: tone comparator

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='acoustic.compare',
  input_target_hz=${target_hz},
  input_tolerance_mode=${tolerance_mode},
  input_tolerance_value=${tolerance_value},
  input_capture_seconds='2',
  input_minimum_stable_ms='500',
  input_maximum_sd_cents='3',
  input_minimum_pitch_confidence='0.65',
  input_payload_mode='FULL',
  return_mode='flat'
)
```

Intent calls use groups. ODK does not depend on a MethodMesh configuration dialog; supplied intent settings are used directly and capture starts automatically after Android microphone permission is available.

## Inputs

### `acoustic.analyse`

| Input | Default | Meaning |
|---|---:|---|
| `capture_seconds` | 2.0 | Timed capture length for automatic/intent runs. |
| `sample_rate_hz` | 48000 | `44100` or `48000`. |
| `min_frequency_hz` | 40 | Lowest pitch candidate. |
| `max_frequency_hz` | 5000 | Highest pitch candidate. |
| `reference_a4_hz` | 440 | Reference pitch for note/cents conversion. |
| `speed_of_sound_mode` | `temperature` | `temperature` or `fixed_speed`. |
| `temperature_c` | 20 | Temperature used for derived speed of sound. |
| `speed_of_sound_mps` | 343 | Explicit speed when `fixed_speed` is selected. |
| `minimum_stable_ms` | 500 | Stable pitch window duration. |
| `maximum_sd_cents` | 5 | Maximum pitch SD within stable window. |
| `minimum_pitch_confidence` | 0.60 | Minimum YIN confidence. |

### `acoustic.tune`

| Input | Default | Meaning |
|---|---:|---|
| `instrument` | `chromatic` | Instrument/tuning preset. |
| `string_index` | 0 | 0 = nearest target; positive value selects a string. |
| `reference_a4_hz` | 440 | Reference A4. |
| `green_zone_cents` | 5 | In-tune acceptance/display zone. |
| `capture_seconds` | 2.0 | Automatic capture length. |
| `minimum_stable_ms` | 500 | Required stable duration. |
| `maximum_sd_cents` | 5 | Maximum within-window pitch SD. |
| `minimum_pitch_confidence` | 0.60 | Minimum YIN confidence. |
| `sample_rate_hz` | 48000 | Audio sample rate. |

### `acoustic.level`

| Input | Default | Meaning |
|---|---:|---|
| `capture_seconds` | 3.0 | Measurement duration. |
| `sample_rate_hz` | 48000 | Audio sample rate. |
| `calibration_mode` | `uncalibrated` | `uncalibrated` or `calibrated`. |
| `calibration_offset_db` | 0 | Offset applied only in calibrated mode. |
| `calibration_reference_db_spl` | 94 | Reference level used to derive/document offset. |
| `calibration_note` | blank | Optional meter/profile note. |

### `acoustic.compare`

| Input | Default | Meaning |
|---|---:|---|
| `target_hz` | 1000 | Target frequency. |
| `tolerance_mode` | `hz` | `hz`, `percent`, or `cents`. |
| `tolerance_value` | 5 | Acceptance tolerance in selected unit. |
| `capture_seconds` | 2.0 | Automatic capture length. |
| `minimum_stable_ms` | 500 | Required stable duration. |
| `maximum_sd_cents` | 3 | Maximum within-window pitch SD. |
| `minimum_pitch_confidence` | 0.65 | Minimum YIN confidence. |
| `sample_rate_hz` | 48000 | Audio sample rate. |
| `min_frequency_hz` | 40 | Lowest pitch candidate. |
| `max_frequency_hz` | 5000 | Highest pitch candidate. |

## Outputs

### `acoustic.analyse`

Main result: `acoustic_analysis_result`

Core measurement fields:

- `acoustic_frequency_hz`
- `acoustic_note`
- `acoustic_cents`
- `acoustic_wavelength_m`
- `acoustic_speed_of_sound_mps`
- `acoustic_rms`
- `acoustic_peak`
- `acoustic_dbfs`
- `acoustic_peak_dbfs`
- `acoustic_pitch_confidence`
- `acoustic_frequency_sd_hz`
- `acoustic_frequency_sd_cents`
- `acoustic_stable_duration_ms`
- `acoustic_harmonics_json`

Audit/status:

- `acoustic_status`
- `acoustic_audit_json`
- `acoustic_error`

### `acoustic.tune`

Main result: `acoustic_tuner_result`

- `acoustic_tuner_note`
- `acoustic_tuner_target_label`
- `acoustic_tuner_target_hz`
- `acoustic_tuner_measured_hz`
- `acoustic_tuner_cents`
- `acoustic_tuner_state`
- `acoustic_tuner_confidence`
- `acoustic_tuner_frequency_sd_hz`
- `acoustic_tuner_stable_duration_ms`
- `acoustic_tuner_status`
- `acoustic_tuner_audit_json`
- `acoustic_tuner_error`

### `acoustic.level`

Main result: `acoustic_level_result`

- `acoustic_level_dbfs`
- `acoustic_level_leq_dbfs`
- `acoustic_level_peak_dbfs`
- `acoustic_level_db_spl`
- `acoustic_level_leq_db_spl`
- `acoustic_level_calibrated`
- `acoustic_level_calibration_offset_db`
- `acoustic_level_calibration_reference_db_spl`
- `acoustic_level_status`
- `acoustic_level_audit_json`
- `acoustic_level_error`

The SPL fields are blank in uncalibrated mode.

### `acoustic.compare`

Main result: `acoustic_compare_result`

- `acoustic_compare_target_hz`
- `acoustic_compare_measured_hz`
- `acoustic_compare_difference_hz`
- `acoustic_compare_difference_percent`
- `acoustic_compare_difference_cents`
- `acoustic_compare_tolerance_mode`
- `acoustic_compare_tolerance_value`
- `acoustic_compare_within_tolerance`
- `acoustic_compare_frequency_sd_hz`
- `acoustic_compare_frequency_sd_cents`
- `acoustic_compare_stable_duration_ms`
- `acoustic_compare_confidence`
- `acoustic_compare_status`
- `acoustic_compare_audit_json`
- `acoustic_compare_error`

## Audit JSON

Every method records, as applicable:

- stable method ID/version;
- DSP version;
- pitch and spectrum algorithm IDs;
- capture start/completion times;
- requested/actual sample rate;
- frame size and frame count;
- actual Android audio source;
- whether Android advertised and supplied an unprocessed path;
- device manufacturer/model and Android SDK;
- all method settings used;
- stability/calibration/wavelength assumptions relevant to the method;
- explicit statement that raw audio, waveform and spectrum arrays were not retained.

## Permissions and Android services

Required runtime permission:

```text
android.permission.RECORD_AUDIO
```

The current MethodMesh application manifest already declares this permission. The Acoustics screen owns the runtime permission request and the user-facing denial/failure state.

Android service used:

- `AudioRecord` / microphone input;
- `AudioManager` only to check whether the device advertises an unprocessed source.

No network service is used.

## Offline / online behaviour

**Fully offline.**

The module performs no web request, cloud inference, telemetry upload or remote calibration lookup. ODK/MethodMesh handoff remains local Android intent transport unless a wider protocol separately adds a networked step.

## Known limitations and Development validation

Keep this module in **Development** until at least the following are closed:

1. Run `./gradlew :app:assembleDebug` in a complete current MethodMesh checkout.
2. Validate microphone capture on several Android devices and OS versions.
3. Validate 44.1 kHz and 48 kHz paths against a traceable/known frequency source.
4. Compare pitch estimates against a known tuner/signal generator across the intended frequency range, including bass E1 and violin-range tones.
5. Test octave/harmonic rejection on guitar, ukulele, violin and voice-like complex tones.
6. Validate live oscilloscope/spectrum rendering performance on lower-powered phones.
7. Confirm captured results survive orientation changes; separately test an orientation change during an active timed capture.
8. Test native preset creation, fixed-setting hiding, runtime-input prompting and timed preset completion.
9. Import `example_odk_Acoustics.xlsx` into ODK Central/Collect and verify all four operation selections through the dynamic group intent and their return fields.
10. Validate permission denial/retry and microphone-unavailable states.
11. Validate SPL calibration repeatability across restart and across input-route changes. Treat calibration as invalid if device/input processing changes.
12. Do not claim IEC 61672 sound-level-meter conformance. v0.1 has no A/C weighting, time weighting or class certification.
13. Phone microphone frequency response and gain vary substantially by device; calibrated dB SPL therefore requires device-specific validation.
14. The temperature-only speed-of-sound approximation ignores humidity and pressure.
15. The module does not persist raw audio, so a later auditor can inspect settings/results/provenance but cannot replay the original sound waveform.

## Pure-logic validation performed for this drop-in

`AcousticsAlgorithms.kt` was compiled with Kotlin/JVM 1.9.0 and smoke-tested using a synthetic 48 kHz / 4096-sample 440 Hz sine wave.

Observed smoke-test results:

- pitch estimate: approximately `440.018 Hz`;
- note resolution: `A4`;
- RMS for a 0.6-peak sine: approximately `0.424`;
- derived wavelength at 20 °C: approximately `0.7805 m`;
- 1000 Hz target / 998.7 Hz observed / ±5 Hz comparator: PASS;
- stable-window detector accepted a 1 s low-variance 440 Hz sequence.

The complete Android Gradle project was not available inside the packaging runtime, so an Android build is **not** claimed by this handoff.

## Canonical delivery

Copy the complete folder:

```text
app/src/main/java/com/example/methodmesh/modules/acoustics/
```

No central module registration is required. The module index is generated from `*Module.kt` files.
