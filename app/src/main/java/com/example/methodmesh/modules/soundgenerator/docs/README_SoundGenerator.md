# Sound generator

Capability: `sound.play`  
Status: **Development**  
Module folder: `modules/soundgenerator/`

Generate a synthetic digital audio stimulus locally and play it through Android's audio stack. v0.1 supports tones, noise and frequency sweeps with explicit digital amplitude, channel routing, finite duration, fades, pulse gating, output-device preference and playback provenance.

The capability is deliberately a **digital stimulus generator**, not an audiometer. `sound_level_dbfs` is a digital full-scale quantity. It is not dB SPL at the ear and is not dB HL. Acoustic claims require a separate calibration model for the complete device / DAC / amplifier / headphone or speaker chain.

## Native workflow

1. Choose **Tone**, **Noise** or **Frequency sweep**.
2. Configure the stimulus.
3. Choose an audio output or leave routing on **System default**.
4. Choose the system-volume policy.
5. Press **Play sound**.
6. Playback requests transient exclusive audio focus and records the route actually reported by Android.
7. The compact result shows status, a human-readable stimulus summary, the generated PCM SHA-256 and the routed device.

A **Stop** button terminates playback early. An early stop returns `sound_status=stopped` and is represented as a cancelled transformation rather than pretending the configured stimulus completed.

Completed result fields are saved through the capability screen so the result survives a normal configuration change. Development validation is still required for rotating the device during active playback; the current screen stops active playback when the screen is disposed rather than allowing an orphaned AudioTrack to continue.

## Presets

All static stimulus controls are declared through `capabilitySettings()` and can be fixed in a MethodMesh preset. Fixed fields are hidden during native preset runs by `settingShouldBeShown(...)`.

`output_device_id` is treated as an operational control and remains available during a preset run because an audio route may be disconnected or replaced between runs.

A native preset still requires the operator to press **Play sound**. Audio playback is meaningful physical-world work and should not surprise the operator merely because a preset was opened.

## ODK / XLSForm

ODK calls use the normal MethodMesh group-intent pattern and start immediately. v0.1 is finite-duration only, so an external call has an unambiguous completion point.

Example tone call:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='sound.play',input_stimulus_type='tone',input_waveform='sine',input_frequency_hz='1000',input_level_dbfs='-30',input_duration_ms='1000',input_channel='both',input_fade_ms='10',input_gate_mode='steady',input_sample_rate_hz='48000',input_system_volume_policy='preserve',return_mode='flat')
```

Example fixed-seed pink-noise call:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='sound.play',input_stimulus_type='noise',input_noise_type='pink',input_noise_seed_mode='fixed_seed',input_noise_seed='study001',input_level_dbfs='-30',input_duration_ms='3000',input_channel='left',input_fade_ms='20',input_sample_rate_hz='48000',input_system_volume_policy='require_percent',input_system_volume_percent='50',return_mode='flat')
```

See `example_odk_SoundGenerator.xlsx` for an importable example using an XLSForm group intent.

## Inputs

| Input | Meaning | v0.1 values / limits |
|---|---|---|
| `input_stimulus_type` | stimulus family | `tone`, `noise`, `sweep` |
| `input_waveform` | periodic waveform | `sine`, `square`, `triangle`, `sawtooth` |
| `input_frequency_hz` | tone frequency | positive and below Nyquist |
| `input_noise_type` | noise colour | `white`, `pink`, `brown` |
| `input_sweep_start_hz` | sweep start | positive and below Nyquist |
| `input_sweep_end_hz` | sweep end | positive and below Nyquist |
| `input_sweep_scale` | frequency interpolation | `logarithmic`, `linear` |
| `input_level_dbfs` | **peak digital amplitude** | `-80` to `0` dBFS |
| `input_duration_ms` | finite total stimulus duration | `20` to `60000` ms |
| `input_channel` | stereo assignment | `both`, `left`, `right` |
| `input_fade_ms` | linear fade in/out | `0` to `5000`, no more than half duration |
| `input_gate_mode` | steady or pulsed | `steady`, `pulsed` |
| `input_pulse_on_ms` | audible portion of pulse cycle | `5` to `60000` ms |
| `input_pulse_off_ms` | silent portion of pulse cycle | `0` to `60000` ms |
| `input_sample_rate_hz` | synthesis sample rate | `48000`, `44100` |
| `input_noise_seed_mode` | noise reproducibility | `secure_random`, `fixed_seed` |
| `input_noise_seed` | fixed noise seed | arbitrary text |
| `input_output_device_id` | preferred Android audio-device ID | blank = system default |
| `input_system_volume_policy` | system media-volume handling | `preserve`, `require_percent`, `temporary_set_percent` |
| `input_system_volume_percent` | required / temporary stream volume | `0` to `100` |

### Digital-level semantics

The synthesis amplitude is:

```text
linear_peak_amplitude = 10^(sound_level_dbfs / 20)
```

The generated PCM is signed 16-bit stereo. `0 dBFS` therefore means a waveform may reach digital full scale. It says nothing by itself about sound pressure at the ear.

For a sine wave, the setting describes **peak** amplitude. RMS relationships are intentionally not hidden behind the setting name; audit metadata records the exact level semantics.

## Sound generation

### Tone

A periodic waveform at one frequency with phase continuity across the complete buffer.

### Sweep

The oscillator phase is integrated sample-by-sample while instantaneous frequency changes linearly or logarithmically between the configured start and end frequency. This avoids the discontinuity produced by simply substituting a changing frequency into `sin(2πft)`.

### White noise

Uniform pseudo-random values in `[-1,1]` from a module-owned deterministic xorshift64* stream when a seed is recorded.

### Pink noise

Filtered white noise using the classic multi-pole Paul Kellet approximation, versioned here as part of the MethodMesh synthesis contract. The implementation is included directly in `SoundSynthesis.kt`; no external library or network service is used.

### Brown noise

Integrated / damped white noise with bounded output. The algorithm is versioned as MethodMesh behaviour rather than represented as a clinical or standards-defined stimulus.

### Pulse gating and fades

`gate_mode=pulsed` alternates `pulse_on_ms` and `pulse_off_ms`. The configured fade is also applied around pulse edges where possible to reduce broadband clicks. Finite stimuli always receive the configured overall fade-in and fade-out.

## Reproducibility and hashes

The module returns two hashes with different meanings:

- `sound_pcm_sha256`: SHA-256 of the **complete generated stereo PCM buffer**, encoded as little-endian signed 16-bit samples. This identifies the intended digital stimulus.
- `sound_written_pcm_sha256`: SHA-256 of samples successfully accepted by `AudioTrack.write(...)`. If playback is stopped or interrupted this may describe only a prefix of the planned stimulus.

Neither hash proves the exact analogue waveform produced by the DAC or the sound pressure reaching a participant. Calibration and physical measurement remain separate concerns.

Secure-random noise records the generated 256-bit seed so the same PCM can later be regenerated. Fixed-seed noise is deterministic for the same MethodMesh synthesis algorithm/version, settings and seed.

## Output routing

Native UI lists the currently available Android output devices. A requested device is supplied to `AudioTrack.setPreferredDevice(...)`.

Android documents a preferred device as a preference rather than a guarantee. The capability therefore records both the requested device and `AudioTrack.getRoutedDevice()` while playback is active.

Important Development limitation: Android API 36 can report multiple simultaneous routed devices through `getRoutedDevices()`. MethodMesh currently targets a minimum API below that and v0.1 records the legacy single routed device. Multi-route provenance is a v0.2 candidate.

## System media-volume policies

### `preserve` — default

Do not alter Android's media stream volume. Record the current volume index and maximum index.

### `require_percent`

Convert the requested percentage to the nearest Android media-volume index. Playback fails if the current volume index does not match it. This is useful in controlled protocols because MethodMesh does not silently alter the operator's device.

### `temporary_set_percent`

Set the Android media stream to the requested index immediately before playback and restore the previous index afterwards.

This policy requires the manifest permission:

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

The module cannot own the application manifest inside its canonical module folder, so this is the one required framework-level installation exception. `DROP_IN.md` documents it explicitly.

System media-volume indexes and Android-reported gain values are **not** acoustic calibration.

## Audio focus and interruption

Playback requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`. If Android does not grant focus the run fails rather than knowingly mixing the research stimulus with another app. If focus is lost during playback the run stops and returns `sound_status=interrupted`.

Audio focus is cooperative at the Android application layer; it cannot prove that every possible hardware or external acoustic source was silent.

## Core outputs

| Field | Meaning |
|---|---|
| `sound_status` | `played`, `stopped`, `interrupted`, or `failed` |
| `sound_summary` | compact human-readable playback result |
| `sound_pcm_sha256` | hash of complete generated PCM stimulus |
| `sound_written_pcm_sha256` | hash of PCM accepted by AudioTrack |
| `sound_noise_seed` | actual seed used for reproducible noise |
| `sound_routed_device` | Android-reported output route during playback |
| `sound_frames_planned` | generated frame count |
| `sound_frames_written` | frames accepted by AudioTrack |
| `sound_frames_played` | playback-head frame count observed by MethodMesh |
| `sound_started_time_iso` | playback-attempt start time |
| `sound_finished_time_iso` | playback-attempt finish time |
| `sound_audit_json` | full structured metadata |
| `sound_error` | diagnostic message on incomplete / failed runs |

The method also declares the configured signal parameters as flattened fields for XLSForm/protocol use.

## Main native result

The native result preview intentionally contains only:

- status;
- stimulus summary;
- generated PCM SHA-256;
- actual routed device;
- error, where applicable.

Full configuration and playback provenance remain in the declared fields and `sound_audit_json` rather than becoming the primary screen.

## Permissions and services

No microphone, camera, location, internet or external service is used.

Core playback uses Android `AudioTrack`, `AudioManager` and `AudioDeviceInfo` locally. No generated sound or metadata is sent off-device.

`MODIFY_AUDIO_SETTINGS` is needed only for `temporary_set_percent`.

## Pure synthesis regression test

`docs/SoundSynthesisSmoke.kt` pins representative PCM hashes for synthesis algorithm `1.0.0`. It can be run without Android:

```bash
kotlinc SoundSynthesis.kt docs/SoundSynthesisSmoke.kt -include-runtime -d /tmp/sound-smoke.jar
java -jar /tmp/sound-smoke.jar
```

See `VALIDATION.md` for what was and was not validated in the packaging environment.

## Known Development limitations

1. **Not acoustically calibrated.** No dB SPL or dB HL claim is made.
2. **Finite stimuli only.** v0.1 intentionally caps duration at 60 seconds. A native continuous/until-stop mode is deferred so the initial ODK and provenance contract remains deterministic.
3. **16-bit PCM.** This is broadly compatible and hashable but is not intended to be the final word on very-low-level laboratory stimuli.
4. **Single routed-device field.** Android 36 can expose multi-route playback; v0.1 records the legacy primary route for compatibility with older supported Android versions.
5. **Active-playback rotation needs device validation.** Completed result state is saveable. The current Development screen stops active playback when disposed rather than allowing an unowned track to continue through activity recreation.
6. **Android routing remains partly advisory.** A preferred device request may not be the route actually used; the actual route is therefore separately recorded.
7. **System volume is not calibration.** `50%` or `7/15` is not a physical sound level.
8. **No formal audiometry workflow.** Threshold algorithms, test sequencing, ambient-noise checks, calibration profiles and dB HL conversion belong in separate higher-level capabilities.

## Proposed v0.2 directions

- native continuous / stop-controlled playback with explicit stop-envelope semantics;
- narrow-band noise;
- warble / FM tones;
- calibration-profile dependency through a public MethodMesh calibration capability;
- multi-route reporting on API 36+;
- optional WAV export of the exact generated stimulus;
- richer sequences / interval patterns without turning this primitive into the hearing-test workflow itself.

## References / platform APIs

Android platform documentation:

- AudioTrack: https://developer.android.com/reference/android/media/AudioTrack
- AudioManager: https://developer.android.com/reference/android/media/AudioManager
- AudioDeviceInfo: https://developer.android.com/reference/android/media/AudioDeviceInfo
- Audio focus: https://developer.android.com/media/optimize/audio-focus

The platform explicitly distinguishes a preferred route from the actual routed device. MethodMesh records both rather than assuming `setPreferredDevice(...)` succeeded acoustically.
