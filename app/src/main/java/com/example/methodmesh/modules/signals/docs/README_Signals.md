# MethodMesh Signals

Version: **0.4.5**  
Module status: **Development**  
Connectivity: **Offline**

Signals is MethodMesh's physical-signal toolkit: observe a signal, encode an explicit payload, transmit it over a locally available physical channel, receive it again, verify the reconstructed object, and return the useful result through the same canonical capability contract used by direct native runs, presets, protocols and ODK/XLSForm.

The first pass deliberately favours channels that can be implemented entirely on an ordinary Android phone. ESP32 radio/magnetic extensions are documented in `ROADMAP_NOTE.md` but are not advertised as working MethodMesh capabilities until a real hardware execution boundary exists.

The module is not a replacement for conventional networking, secure messaging or safety-critical radio systems. It is intended for small, explicit, local transfers and signal diagnostics where ordinary networking is absent, undesirable or simply not the channel being studied.

## Capability inventory

| Method ID | Native capability | Maturity | Connectivity | Primary channel |
|---|---|---|---|---|
| `signal.morse.transmit` | Morse transmitter | Development | Offline | front screen / rear torch / speaker |
| `signal.morse.receive` | Morse receiver | Development | Offline | camera luminance / microphone; light sensor legacy-only |
| `signal.qr.transmit` | QR burst transmitter | Development | Offline | screen -> camera |
| `signal.qr.receive` | QR burst receiver | Development | Offline | camera |
| `signal.audio_fsk.transmit` | Audio FSK transmitter | Development | Offline | speaker -> microphone/radio audio path |
| `signal.audio_fsk.receive` | Audio FSK receiver | Development | Offline | microphone |
| `signal.ultrasonic.transmit` | Near-ultrasonic transmitter | Experimental | Offline | high-frequency phone audio |
| `signal.ultrasonic.receive` | Near-ultrasonic receiver | Experimental | Offline | microphone |
| `signal.surface.transmit` | Tabletop transmitter | Experimental | Offline | speaker -> shared rigid surface |
| `signal.surface.receive` | Tabletop receiver | Experimental | Offline | accelerometer / shared rigid surface |
| `signal.sensor.scope` | Signal sensor scope | Development | Offline | phone sensors |

Every method above is independently registered by `SignalsModule`; the dashboard is only a generic discovery/launch projection. No capability is dashboard-only.


## Native instrument design

The 0.4 physical-channel pass incorporates repeated real two-phone testing. It keeps the polished instrument family but makes the physical links observable and explicitly framed: Morse now uses cyclic acquisition/START/END framing and message-level consensus; live FSK is wired to clock recovery and exposes a microphone spectrum; QR supports segmented transfers up to 1 MiB with explicit end-to-end SHA-256; tabletop calibration is fail-safe; and long-range camera optics remain available. The live tool remains visually dominant and configuration remains below it.

- **Morse** uses an amber telegraph/beacon language. Transmission is looping-only: every cycle sends three acquisition flashes, a distinctive 12-unit START mark, the message, a different 20-unit END mark, then quiet before repeating. The receiver quarantines any pre-START orphan, accepts consensus votes only from complete START→END cycles, and can use an END-anchored orphan later at reduced weight once a bounded copy establishes message geometry. Confidence combines cross-cycle agreement with finite-evidence shrinkage rather than reporting one clean copy as 100%. Camera reception supports real zoom, Full/Focus/Pinpoint ROIs, tap-to-position and temporal-modulation auto-lock. Screen mode uses deliberate underexposure, median ROI luminance and a two-frame stability gate to reject rolling-shutter swipes; torch/point mode underexposes further, prefers a pinpoint ROI and requires three stable frames to suppress flare-decay edges. Optical timing is capped at 10 WPM with 5 WPM recommended; sound-only transmission/reception is capped at 30 WPM. Mark speed and element/letter/word/cycle gaps are separate discrete axes so optical spacing can be relaxed without changing the mark rate.
- **QR burst** uses a cyan optical-modem language: current coded frame, frame/cycle/dwell telemetry, Reed-Solomon geometry and recovery progress. Text and files are wrapped with an object SHA-256; larger files are segmented across independent MMS/1 packets and a second transfer-envelope SHA-256 verifies reassembly. The 1 MiB ceiling is supported but can require thousands of QR frames, so text and smaller files are the practical sweet spot. Active transmission expands to a full-screen maximum-brightness optical surface. QR receive exposes actual hardware optical zoom (1x / 2x / 4x / device maximum).
- **Audible FSK** uses a cyan radio-modem language with MARK/SPACE carrier rail, matched A/B/C/D profiles, live microphone spectrum, peak frequency, tone-confidence, clock/sync/frame diagnostics and receiver shard progress. The live receiver uses preamble-based clock acquisition. v0.4.2 adds in-frame percentage telemetry after SYNC, explicit first-frame/cycle time estimates, lower output amplitude to reduce handset/microphone saturation, and a compact binary physical representation of generated MMS/1 frames; the exact CRC-bearing ASCII MMS/1 frame is reconstructed before transport validation.
- **Near-ultrasonic FSK** uses the same modem grammar, spectrum and A/B/C/D profiles with a violet experimental treatment. Handset testing showed unstable peaks around 14–15 kHz and little useful response above that, so profiles now start at 12/13 kHz and step upward to 15/16 kHz rather than assuming 18/19 kHz is usable.
- **Tabletop transfer** remains an explicitly **Experimental** research channel. It uses low-frequency OOK transmission into a shared rigid surface, accelerometer rest calibration, live vibration trace and clock/sync/frame telemetry, but current phone-pair testing has not produced reliable reception. The capability is retained for experimentation rather than presented as a dependable transfer path.
- **Sensor scope** uses a live oscilloscope trace, large current magnitude/value and axis telemetry rather than a table-only sensor readout.
- **Committed results** use a separate green frozen-record treatment. This is deliberately different from live instrumentation so the Commit boundary is visually obvious.

The custom dark instrument chassis is confined to the live/frozen instrument surfaces. Ordinary configuration controls remain Material-theme native for accessibility and consistency with the host application.

### Commit-integrity rails added in 0.2

The visual review also tightened working-state semantics. Changing any transmission setting that affects the actual emitted Morse/QR/FSK signal invalidates completed-cycle evidence before another Commit. Changing Morse/FSK receiver tuning or the QR message filter clears only the **working** decode/shard collection. An already committed result remains frozen and its displayed carrier/timing values come from committed fields rather than current live controls.

Native physical-channel tuning is discrete rather than slider-based. TX and RX draw from shared A/B/C/D channel catalogues, while genuinely independent axes such as Morse speed, element gap, letter gap, word gap, dBFS threshold and QR dwell use their own finite preset sets. Preset/protocol settings and the canonical XLSForms expose the same discrete choices.

## Working-result -> Commit lifecycle

The physical instrument is at the top of each native surface. Settings live below it. Useful live scalar/text values are tappable to copy.

For transactional capabilities the lifecycle is:

```text
configure / listen / transmit
    -> live working result
    -> Commit
    -> frozen canonical result
    -> copy / share / save / origin-aware Done
```

Commit freezes the canonical return payload. Editing settings or continuing live sensor activity after Commit does not silently mutate the committed record. Automatic-return origins such as ODK return the committed result at the Commit boundary.

Active camera, microphone, speaker, torch and accelerometer sessions temporarily lock the current device orientation. This prevents an Activity recreation from silently killing a live physical session. When the session stops, the host orientation policy is restored. Working and committed evidence retain the normal MethodMesh state/Commit separation.

---

# MMS/1: MethodMesh Signal Protocol v1

QR, audible FSK, near-ultrasonic FSK and tabletop transfer carry the same transport-independent **MMS/1** frames.

MMS/1 is designed for lossy physical links where a receiver may:

- miss whole frames;
- start listening halfway through a loop;
- receive frames out of order;
- receive the same frame repeatedly;
- reject frames corrupted by a noisy physical channel.

## Integrity and erasure recovery

For a payload split into `k` source shards, MMS/1 creates systematic Reed-Solomon coded shards over GF(256). The first `k` coding rows are the unmodified source shards; additional rows are parity shards from a systematic Vandermonde generator matrix.

Any `k` **distinct valid** coded rows are sufficient to reconstruct the original `k` source shards, provided the advertised message geometry is consistent. This is erasure recovery: an analogue demodulator either yields a complete frame whose CRC passes, or that frame is discarded rather than silently fed into reconstruction.

Each ASCII MMS/1 frame contains:

```text
MMS1 | message_id | data_shards | payload_length | message_crc32 |
       coding_row | base64url_shard | frame_crc32
```

Two CRC layers are used:

1. **frame CRC32** rejects a damaged transport frame before it reaches Reed-Solomon reconstruction;
2. **whole-message CRC32** verifies the reconstructed MMS/1 byte payload.

For user-delivered text/files, v0.3 adds a content envelope carrying the **SHA-256 of the original useful object**. After Reed-Solomon reconstruction the receiver hashes the reconstructed content and compares it with the transmitted digest. Successful delivery therefore records the broadcast checksum, reconstructed checksum, verified result and full MethodMesh provenance JSON. CRC/FEC answers whether the transport reconstructed correctly; SHA-256 answers whether the final object is byte-for-byte the object the sender committed to before transmission.

MMS/1 CRC is error detection, **not authentication**. A malicious party can deliberately construct a new valid CRC. Sensitive/authenticated messaging needs a cryptographic layer above MMS/1.

## Reliability profiles

| Profile | Parity rows generated | Intended use |
|---|---:|---|
| Fast | `max(1, ceil(k/4))` | cleaner short links where speed matters |
| Robust | `max(2, k)` | default; parity roughly equals the source-shard count |
| Extreme | `max(4, 2k)` | very lossy channels where transmission time is acceptable |

A looping sender cycles all coded frames. A receiver can therefore join mid-stream and accumulate independent rows until reconstruction succeeds.

Morse currently remains the deliberately human-readable signalling route rather than wrapping every character in MMS/1. Its first-pass robustness comes from configurable looping/repetition and adaptive receive timing. `MMS/1-over-Morse` is on the roadmap for machine-to-machine Morse where a longer coded transmission is acceptable.

---

# Morse transmitter — `signal.morse.transmit`

Transmit ordinary text as International Morse timing using any supported combination of:

- **front screen flash** — a full-screen black/white emitter surface with the live window brightness request raised to maximum and restored when transmission stops;
- rear-camera torch;
- audible tone.

The full-screen emitter keeps an explicit **Stop** control available and channel/timing controls remain locked while a physical transmission is active.

Unsupported characters are skipped by the Morse encoder. The visible Morse notation updates immediately from the working message and is tap-to-copy.

## Inputs

| Key | Type | Default | Meaning |
|---|---|---|---|
| `payload` | text | `SOS` | Text to encode. |
| `route` | choice | `screen` | `screen`, `torch`, `sound`, `screen_sound`, `torch_sound`, `all`. |
| `wpm` | choice | `5` | Discrete speed: optical 5/8/10 WPM; sound additionally 20/30 WPM. |
| `tone_frequency_hz` | choice | `700` | Shared TX/RX audio carrier: 500/700/1200/2200 Hz. |
| `element_gap_units` | choice | `1.25` | Independent gap between dot/dash elements. |
| `letter_gap_units` | choice | `4` | Independent gap between letters. |
| `word_gap_units` | choice | `9` | Independent gap between words. |
| `loop_mode` | choice | `continuous` | `count` or `continuous`; single-shot transmission is intentionally not offered. |
| `repeat_count` | choice | `3` | Counted looping: 2/3/5/10 cycles. |
| `loop_gap_units` | choice | `15` | Cycle gap: 10/15/24 Morse units. |

## Declared outputs

`signal_morse_result`, `signal_morse_payload`, `signal_morse_notation`, `signal_morse_route`, `signal_morse_wpm`, `signal_morse_repetitions`, `signal_morse_status`, `signal_morse_error`.

## ODK Integration Card — `signal.morse.transmit`

**Tags** — Development / Offline  
**Interactive acquisition** — yes: MethodMesh performs the observable physical transmission and the operator commits after at least one complete cycle.

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.morse.transmit',
  input_payload=${morse_tx_payload},
  input_route=${morse_tx_route},
  input_wpm=${morse_tx_wpm},
  input_tone_frequency_hz=${morse_tx_tone_hz},
  input_element_gap_units=${morse_tx_element_gap},
  input_letter_gap_units=${morse_tx_letter_gap},
  input_word_gap_units=${morse_tx_word_gap},
  input_loop_mode=${morse_tx_loop_mode},
  input_repeat_count=${morse_tx_repeat_count},
  input_loop_gap_units=${morse_tx_loop_gap},
  input_payload_mode='FULL',return_mode='flat')
```

Canonical returns are the declared fields above plus shared `methodmesh_status` and `methodmesh_full_json`. The showcase uses one intent group, direct group children, unprefixed canonical return keys and no `methodmesh_return_namespace`.

---

# Morse receiver — `signal.morse.receive`

Decode an on/off Morse envelope from camera luminance or a configured microphone carrier. `light_sensor` remains accepted only for backwards compatibility with old presets and is hidden from ordinary native selection because real-device testing did not justify it as a useful Morse path.

Camera luminance is a relative signal metric, not calibrated photometry. Microphone detection uses a module-local Goertzel carrier detector and prefers an unprocessed/MIC input path where Android exposes one. MethodMesh-generated Morse is a cyclic framed signal: three acquisition flashes → 12-unit START → message → 20-unit END → repeat. A receiver joining halfway through a cycle may decode an END-anchored orphan suffix, but that observation has zero voting weight until a later complete START→END cycle establishes alignment. With PARIS timing, `dot_ms = 1200 / WPM`, so 5 WPM corresponds to a 240 ms dot.

For camera reception, locked timing is the default because rolling shutter and torch flare can bias measured ON and OFF durations differently. Auto timing remains available and uses the acquisition/marker observations plus normalized dots/dashes; microphone reception defaults to auto timing.

## Inputs

| Key | Type | Default | Meaning |
|---|---|---|---|
| `source` | choice | `camera` | `camera`, `microphone`; legacy `light_sensor` remains accepted for old presets. |
| `dot_ms` | choice | `240` camera / `60` microphone | Discrete nominal speed: 240/150/120/60/40 ms = 5/8/10/20/30 WPM. |
| `auto_timing` | boolean | `false` camera / `true` microphone | Experimental adaptation of the base Morse unit; locked timing is recommended optically. |
| `optical_profile` | choice | `screen` | `screen` or `torch`; controls exposure reduction, ROI statistic and stable-frame edge gating. |
| `microphone_tone_hz` | choice | `700` | Same 500/700/1200/2200 Hz catalogue as TX. |
| `microphone_tolerance_hz` | choice | `120` | 80/120/220/400 Hz tolerance. |
| `microphone_min_dbfs` | choice | `-54` | −78/−66/−54/−42 dBFS. More negative accepts quieter signals. |
| `element_gap_units` | choice | `1.25` | Expected element gap, matched to TX. |
| `letter_gap_units` | choice | `4` | Expected letter gap, matched to TX. |
| `word_gap_units` | choice | `9` | Expected word gap, matched to TX. |

## Declared outputs

`signal_morse_received_text`, `signal_morse_received_notation`, `signal_morse_source`, `signal_morse_dot_ms`, `signal_morse_transitions`, `signal_morse_copies`, `signal_morse_consensus_confidence`, `signal_morse_status`, `signal_morse_error`.

## ODK Integration Card — `signal.morse.receive`

**Tags** — Development / Offline  
**Interactive acquisition** — required. MethodMesh listens until the operator has a useful decoded working result and presses Commit.

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.morse.receive',
  input_source=${morse_rx_source},
  input_dot_ms=${morse_rx_dot_ms},
  input_auto_timing=${morse_rx_auto_timing},
  input_optical_profile=${morse_rx_optical_profile},
  input_microphone_tone_hz=${morse_rx_tone_hz},
  input_microphone_tolerance_hz=${morse_rx_tolerance_hz},
  input_microphone_min_dbfs=${morse_rx_min_dbfs},
  input_element_gap_units=${morse_rx_element_gap},
  input_letter_gap_units=${morse_rx_letter_gap},
  input_word_gap_units=${morse_rx_word_gap},
  input_payload_mode='FULL',return_mode='flat')
```

Canonical returns are the declared fields above plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# QR burst transmitter — `signal.qr.transmit`

Wrap text or a file up to 1 MiB in a checksum-bearing content envelope and display the transfer as a QR blast. A content SHA-256 travels with the original object. Transfers that exceed one MMS/1 Reed-Solomon matrix are split into independently recoverable MMS/1 segments and also carry a transfer-envelope SHA-256. QR supplies per-symbol error correction; MMS/1 supplies recovery **between** QR frames; the receiver verifies both reassembly and the final reconstructed object before Commit.

The message ID is saved as working state so Activity recreation cannot silently change the identity of a burst whose completed-cycle count is being retained. Changing payload, shard geometry or reliability intentionally creates a new message ID.

## Inputs

`content_mode`, `payload`, `file_uri`, `file_name`, `file_mime`, `robustness`, `shard_bytes`, `frame_duration_ms`, `loop`.

## Declared outputs

`signal_qr_result`, `signal_qr_payload`, `signal_qr_content_type`, `signal_qr_file_name`, `signal_qr_file_mime`, `signal_qr_file_bytes`, `signal_qr_checksum_sha256`, `signal_qr_message_id`, `signal_qr_data_shards`, `signal_qr_parity_frames`, `signal_qr_frame_count`, `signal_qr_message_crc32`, `signal_qr_robustness`, `signal_qr_cycles`, `signal_qr_status`, `signal_qr_error`.

## ODK Integration Card — `signal.qr.transmit`

**Tags** — Development / Offline

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.qr.transmit',
  input_content_mode=${qr_tx_content_mode},
  input_payload=${qr_tx_payload},
  input_file_uri=${qr_tx_file_uri},
  input_file_name=${qr_tx_file_name},
  input_file_mime=${qr_tx_file_mime},
  input_robustness=${qr_tx_robustness},
  input_shard_bytes=${qr_tx_shard_bytes},
  input_frame_duration_ms=${qr_tx_frame_ms},
  input_loop=${qr_tx_loop},
  input_payload_mode='FULL',return_mode='flat')
```

Interactive acquisition is required because the screen must actually display at least one complete QR cycle before Commit. Returns are the declared fields plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# QR burst receiver — `signal.qr.receive`

Continuously scan QR codes, reject non-MMS/1 or CRC-invalid frames, retain unique complete frames, and reconstruct once enough independent Reed-Solomon rows are available. Complete received MMS/1 frames are persisted as temporary working state across ordinary Activity recreation; no archive is created unless the caller/preset explicitly requests persistence through shared MethodMesh mechanisms.

## Inputs

`message_id_filter` — optional MMS/1 message ID. Blank accepts the first compatible message geometry encountered.

## Declared outputs

`signal_qr_received_text`, `signal_qr_content_type`, `signal_qr_received_file_uri`, `signal_qr_file_name`, `signal_qr_file_mime`, `signal_qr_file_bytes`, `signal_qr_expected_sha256`, `signal_qr_reconstructed_sha256`, `signal_qr_checksum_verified`, `signal_qr_message_id`, `signal_qr_frames_seen`, `signal_qr_frames_accepted`, `signal_qr_frames_rejected`, `signal_qr_recovered_missing_shards`, `signal_qr_crc_verified`, `signal_qr_status`, `signal_qr_error`.

## ODK Integration Card — `signal.qr.receive`

**Tags** — Development / Offline

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.qr.receive',
  input_message_id_filter=${qr_rx_message_id_filter},
  input_payload_mode='FULL',return_mode='flat')
```

Interactive camera acquisition is required. Returns are the declared fields plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# Audio FSK transmitter — `signal.audio_fsk.transmit`

Send complete MMS/1 ASCII frames through binary frequency-shift keying. Defaults are 1200 Hz MARK and 2200 Hz SPACE with a 60 ms bit. A/B/C/D matched profiles set MARK, SPACE and bit timing together on both phones; direct use, presets/protocols and ODK all select the same profile letter. The output AudioTrack keeps phase continuous across symbols and applies short attack/release ramps to reduce crackle.

A configurable `ptt_lead_ms` steady MARK carrier precedes every framed burst. It is useful for:

- manually keyed walkie-talkies whose PTT/squelch path needs settling time;
- VOX radios that need an audible carrier before framed data begins.

The receiver scans for its own physical sync word, so lead carrier audio is outside the MMS/1 payload.

## Inputs

`payload`, `robustness`, `channel_profile`, `ptt_lead_ms`, `repeat_count`. Legacy raw `mark_hz`, `space_hz` and `bit_ms` inputs remain accepted for backwards compatibility.

## Declared outputs

`signal_fsk_result`, `signal_fsk_payload`, `signal_fsk_message_id`, `signal_fsk_frame_count`, `signal_fsk_mark_hz`, `signal_fsk_space_hz`, `signal_fsk_bit_ms`, `signal_fsk_ptt_lead_ms`, `signal_fsk_robustness`, `signal_fsk_cycles`, `signal_fsk_status`, `signal_fsk_error`.

## ODK Integration Card — `signal.audio_fsk.transmit`

**Tags** — Development / Offline

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.audio_fsk.transmit',
  input_payload=${fsk_tx_payload},
  input_robustness=${fsk_tx_robustness},
  input_channel_profile=${fsk_tx_profile},
  input_ptt_lead_ms=${fsk_tx_ptt_lead_ms},
  input_repeat_count=${fsk_tx_repeat_count},
  input_payload_mode='FULL',return_mode='flat')
```

Interactive transmission and Commit are required. Returns are the declared fields plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# Audio FSK receiver — `signal.audio_fsk.receive`

Use 10 ms microphone analysis windows and two Goertzel detectors to classify MARK/SPACE/uncertain. The receiver searches for the known alternating preamble at every clock phase, majority-votes the configured bit cells, verifies sync and then extracts complete MMS/1 ASCII frames. This is the clock-acquisition path used by the live UI, not the earlier run-length prototype. A live spectrum, peak frequency and tone-confidence meter show whether the microphone is actually hearing the selected carriers before packet framing is blamed.

Complete MMS/1 frames are retained when listening is paused or the Activity is recreated. Partial physical clock state is intentionally reacquired. Frame CRC and whole-message CRC remain authoritative above the physical detector.

## Inputs

`channel_profile`, `minimum_dbfs`. Legacy raw carrier/timing inputs remain accepted.

## Declared outputs

`signal_fsk_received_text`, `signal_fsk_message_id`, `signal_fsk_frames_accepted`, `signal_fsk_frames_rejected`, `signal_fsk_recovered_missing_shards`, `signal_fsk_crc_verified`, `signal_fsk_mark_hz`, `signal_fsk_space_hz`, `signal_fsk_bit_ms`, `signal_fsk_status`, `signal_fsk_error`.

## ODK Integration Card — `signal.audio_fsk.receive`

**Tags** — Development / Offline

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.audio_fsk.receive',
  input_channel_profile=${fsk_rx_profile},
  input_minimum_dbfs=${fsk_rx_min_dbfs},
  input_payload_mode='FULL',return_mode='flat')
```

Interactive microphone acquisition is required. Returns are the declared fields plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# Near-ultrasonic transmitter — `signal.ultrasonic.transmit`

The default near-ultrasonic bit period is 100 ms and uses the same 10 ms-aligned timing rule as audible FSK.

This is an **Experimental** MMS/1 FSK channel using high-frequency phone audio. The shared A–D catalogue spans 12/13 kHz through 15/16 kHz after device testing showed that higher bands can be strongly attenuated.

“Near-ultrasonic” does **not** mean guaranteed inaudible. Some people and animals can hear frequencies in this region. Phone speakers, microphones, codecs, acoustic processing and cases vary substantially; a nominal 48 kHz sample path does not guarantee useful acoustic response near Nyquist.

## Inputs

`payload`, `robustness`, `channel_profile`, `repeat_count`. Legacy raw carrier/timing inputs remain accepted.

## Declared outputs

`signal_ultrasonic_result`, `signal_ultrasonic_payload`, `signal_ultrasonic_message_id`, `signal_ultrasonic_frame_count`, `signal_ultrasonic_mark_hz`, `signal_ultrasonic_space_hz`, `signal_ultrasonic_bit_ms`, `signal_ultrasonic_robustness`, `signal_ultrasonic_cycles`, `signal_ultrasonic_status`, `signal_ultrasonic_error`.

## ODK Integration Card — `signal.ultrasonic.transmit`

**Tags** — Experimental / Offline

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.ultrasonic.transmit',
  input_payload=${ultra_tx_payload},
  input_robustness=${ultra_tx_robustness},
  input_channel_profile=${ultra_tx_profile},
  input_repeat_count=${ultra_tx_repeat_count},
  input_payload_mode='FULL',return_mode='flat')
```

Interactive transmission and Commit are required. Returns are the declared fields plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# Near-ultrasonic receiver — `signal.ultrasonic.receive`

Experimental high-frequency two-tone receiver. The module prefers Android `UNPROCESSED` input where advertised, then `MIC`, before falling back to speech-oriented input. The same live spectrum used by audible FSK makes failure interpretable: if the intended high-band peaks are absent, the phone pair does not currently provide that acoustic channel regardless of packet-code correctness.

## Inputs

`channel_profile`, `minimum_dbfs`. Legacy raw carrier/timing inputs remain accepted.

## Declared outputs

`signal_ultrasonic_received_text`, `signal_ultrasonic_message_id`, `signal_ultrasonic_frames_accepted`, `signal_ultrasonic_frames_rejected`, `signal_ultrasonic_recovered_missing_shards`, `signal_ultrasonic_crc_verified`, `signal_ultrasonic_mark_hz`, `signal_ultrasonic_space_hz`, `signal_ultrasonic_bit_ms`, `signal_ultrasonic_status`, `signal_ultrasonic_error`.

## ODK Integration Card — `signal.ultrasonic.receive`

**Tags** — Experimental / Offline

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.ultrasonic.receive',
  input_channel_profile=${ultra_rx_profile},
  input_minimum_dbfs=${ultra_rx_min_dbfs},
  input_payload_mode='FULL',return_mode='flat')
```

Interactive microphone acquisition is required. Returns are the declared fields plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# Tabletop surface transfer — `signal.surface.transmit` / `signal.surface.receive`

The tabletop modem is retained as an explicitly **Experimental** contact-coupled research channel. Current phone-pair tests have not produced reliable reception. The transmitter emits low-frequency OOK into a shared rigid surface; the receiver samples the accelerometer at 100 Hz, reduces movement to a vibration-RMS envelope, calibrates the resting floor, then feeds ON/OFF decisions into the clock/frame layer.

The receiver must be calibrated with the transmitter off and both phones still. Its threshold is the resting mean plus a configurable sensitivity multiplier times resting standard deviation. Live UI exposes a seismograph trace plus `SIGNAL -> CLOCK -> SYNC -> FRAME -> MMS` telemetry. A completed message is Commit-eligible only when MMS CRC and the content-envelope SHA-256 both verify.

## Transmit inputs

`payload`, `robustness`, `surface_profile`, `repeat_count`. A–D profiles bind carrier and bit timing; legacy raw carrier/bit inputs remain accepted.

## Receive inputs

`surface_profile`, `calibration_ms`, `sensitivity`. The same A–D profile letter is used on TX and RX; legacy raw carrier/bit inputs remain accepted.

## ODK Integration Cards

The module owns `example_odk_showcase_signal_surface_transmit.xlsx` and `example_odk_showcase_signal_surface_receive.xlsx`; each contains exactly one canonical MethodMesh invocation and returns the declared surface fields plus `methodmesh_status` and `methodmesh_full_json`.

---

# Signal sensor scope — `signal.sensor.scope`

A compact live scope over phone sensors already exposed by MethodMesh's shared `PhoneSensorRepository`:

- ambient light — lux;
- magnetic field — X/Y/Z µT and magnitude;
- acceleration — X/Y/Z m/s² and magnitude;
- gyroscope — X/Y/Z rad/s and magnitude;
- pressure — hPa;
- proximity — device-specific proximity distance/unit as reported by Android.

The scope is diagnostic. It does not claim laboratory calibration. It can be used to characterise a prospective signal channel before a future decoder is enabled.

## Input

`sensor` — `light`, `magnetometer`, `accelerometer`, `gyroscope`, `pressure`, `proximity`.

## Declared outputs

`signal_sensor_result`, `signal_sensor_id`, `signal_sensor_value_0`, `signal_sensor_value_1`, `signal_sensor_value_2`, `signal_sensor_magnitude`, `signal_sensor_unit`, `signal_sensor_accuracy`, `signal_sensor_values_json`, `signal_sensor_status`, `signal_sensor_error`.

## ODK Integration Card — `signal.sensor.scope`

**Tags** — Development / Offline

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='signal.sensor.scope',
  input_sensor=${signal_scope_sensor},
  input_payload_mode='FULL',return_mode='flat')
```

Interactive capture is required because Commit freezes the currently visible sensor snapshot. Returns are the declared fields plus shared `methodmesh_status` and `methodmesh_full_json`.

---

# ODK/XLSForm examples

The module owns one v1.08 canonical single-invocation showcase per independently callable capability:

- `example_odk_showcase_signal_morse_transmit.xlsx`
- `example_odk_showcase_signal_morse_receive.xlsx`
- `example_odk_showcase_signal_qr_transmit.xlsx`
- `example_odk_showcase_signal_qr_receive.xlsx`
- `example_odk_showcase_signal_audio_fsk_transmit.xlsx`
- `example_odk_showcase_signal_audio_fsk_receive.xlsx`
- `example_odk_showcase_signal_ultrasonic_transmit.xlsx`
- `example_odk_showcase_signal_ultrasonic_receive.xlsx`
- `example_odk_showcase_signal_surface_transmit.xlsx`
- `example_odk_showcase_signal_surface_receive.xlsx`
- `example_odk_showcase_signal_sensor_scope.xlsx`

Each workbook contains exactly one `com.example.methodmesh.EXECUTE_METHOD` intent call, uses the canonical method ID, requests `input_payload_mode='FULL'`, uses `return_mode='flat'`, captures shared `methodmesh_status` and `methodmesh_full_json`, uses canonical unprefixed return fields, and does not set a return namespace.

# Permissions and platform dependencies

The current MethodMesh host already declares the permissions used by this first pass:

- `CAMERA` — QR receive, camera-luminance Morse receive, rear torch;
- `RECORD_AUDIO` — Morse microphone receive, audible FSK receive, near-ultrasonic receive.

Tabletop receive uses the accelerometer and requires no runtime sensor permission.

No network connection is required for the module's declared core work.

The host already resolves CameraX and JourneyApps/ZXing. Signals uses those existing host dependencies for local camera analysis and QR rendering/scanning; it does not add a cloud service or a new remote dependency. See `ATTRIBUTION.md` and `THIRD_PARTY_NOTICES.md`.

# Relationship to the existing Acoustics module

The existing `acoustic.analyse`, `acoustic.tune`, `acoustic.level` and `acoustic.compare` contracts remain owned by the Acoustics module and are not renamed, duplicated or silently migrated by this first pass.

Signals owns its low-latency carrier demodulation because the communication receiver needs continuous MARK/SPACE/on-off windows rather than a completed Acoustics observation. A future unified Signals/Acoustics presentation can be considered without breaking the established `acoustic.*` method IDs. Moving ownership should only happen as an explicit repository migration that simultaneously removes duplicate registration risk.

# Storage and privacy

Signals is transient by default:

- raw microphone audio is not persisted;
- camera frames are analysed in memory;
- QR/MMS complete frames are held as temporary working state only;
- no signal payload is uploaded by the module;
- ordinary Commit returns the canonical result to the caller rather than creating an extra local archive.

Shared MethodMesh preset-log behaviour remains available when a preset explicitly opts into persistence.

## v0.4 physical-device interpretation

Real-device testing drove this revision. Optical QR is currently the strongest machine link; camera Morse is genuinely useful and benefits from repeated-copy consensus. Audible and high-band FSK must first demonstrate energy at the intended frequencies in the spectrum, then clock/sync acquisition, before MMS/1 recovery can occur. Tabletop transfer remains Experimental and calibration failures are now surfaced as recoverable UI errors instead of escaping the sensor callback.

A future Morse mode remains on the roadmap: capture a manually keyed torch sequence without assuming a dot duration, then infer dot/dash and gap clusters afterwards.

# Validation

See `VALIDATION.md` for codec smoke tests, v1.08 review passes, XLSForm checks, limitations of this execution environment and the receiving-repository/device test matrix.


## v0.4.2 third device-test findings incorporated

- Audible FSK reached SYNC but no completed physical frame. This proves the microphone/tone/clock path is alive; the UI now shows the long post-SYNC physical frame filling as a percentage and displays expected frame time. Generated MMS/1 frames use a more compact acoustic representation, and transmit amplitude is reduced to avoid receiver saturation.
- Optical Morse was reliable at 5 WPM but degraded as timing accelerated. Optical routes are therefore explicitly capped at 10 WPM; audio remains a separate faster physical profile and is capped at 30 WPM on both TX and RX.
- Near-ultrasonic peaks wandered around 14–15 kHz and rarely yielded a valid frame. Profiles now begin at 12/13 kHz and climb conservatively rather than treating nominal ultrasonic output as portable across handsets.
- Tabletop calibration appeared inert and TX appeared frozen. Accelerometer sampling is now isolated from the UI thread with explicit sample telemetry/timeout, while TX has continuous bit progress, gentler OOK edges, a 180 Hz starting carrier and a 60 ms starting bit period.


## v0.4.3 cyclic Morse / optical front-end revision

- Morse transmission is now looping-only. A cycle is acquisition flashes → START → message → END → inter-cycle silence. Counted mode requires at least two cycles; continuous is the native default.
- START and END are deliberately different non-Morse long marks. Missing START no longer permits an arbitrary partial string to slide into consensus: pre-START text is quarantined, an orphan that reaches END is stored as right-anchored evidence, and it can only contribute after a later complete bounded frame establishes message geometry.
- Consensus uses edit alignment for complete copies and reduced-weight right alignment for a recovered orphan suffix. The displayed confidence combines character agreement with finite-evidence shrinkage: one bounded copy is intentionally below certainty and confidence rises as independent cycles agree.
- Camera front-end now has explicit **Screen** and **Torch / point** profiles. Screen uses negative exposure compensation, median ROI luminance and a two-frame state gate to suppress rolling-shutter swipe frames. Torch uses stronger underexposure, a pinpoint-friendly upper-quartile ROI signal and a three-frame state gate to reduce saturated starburst/decay edges.
- Optical reception defaults to locked timing at 5 WPM; auto timing is optional. Audio keeps auto timing by default. Optical transmit gaps are slightly stretched independently by gap class so element and inter-letter silence are not treated as one undifferentiated timing parameter.
- A Morse reception cannot be committed until at least one complete START→END cycle has been received. Working partial/orphan text is visible for diagnostics but cannot become canonical evidence by itself.


## v0.4.4 physical-channel retest revision

- Audible FSK keeps the proven 1.2/2.2 kHz pair and adds a faster 40 ms profile on the same carriers; former high-frequency C/D audible profiles are replaced by lower-band alternatives.
- Acoustic text is transparently DEFLATE-compressed only when compression reduces the wire payload. MMS/1 remains the recovery/CRC layer.
- Physical FSK framing uses a four-byte alternating preamble and a compact A2 payload that recomputes the deterministic MMS frame CRC at RX, reducing per-frame airtime.
- Near-ultrasonic A-C are faster stepped profiles while D preserves the slower 15/16 kHz profile that survived a loud-music device test. Completed-but-rejected physical frames are latched visibly instead of silently returning from 100% to 0%.
- FSK output drive is reduced to avoid speaker/microphone saturation and unnecessary acoustic nuisance.
- Tabletop RX requests 100 Hz accelerometer sampling (10,000 microseconds), intentionally below Android's high-rate-sensor permission threshold. No HIGH_SAMPLING_RATE_SENSORS permission is required.
- Tabletop TX has a real cancellation flag, safer 90-300 Hz carrier bounds and 120/180/240 Hz presets; packet width is adaptive and new sessions default to FAST robustness.
- Morse receiver header layout is stabilised: the instrument badge stays LIVE/IDLE and the kicker is one line, so ON/OFF detection no longer makes the screen jump vertically.
