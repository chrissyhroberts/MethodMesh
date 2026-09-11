# Signals roadmap

This file records module-specific next steps only. The MethodMesh Master Book remains authoritative for project-wide architecture.

## 1. ESP32 Signal Node

A small optional ESP32 peripheral is the most useful hardware extension because it can expose physical channels that ordinary Android hardware cannot directly control.

### ESP-NOW / ESP-NOW-LR gateway

Proposed architecture:

```text
MethodMesh phone
    <-> explicit local bridge (BLE / USB / supported Wi-Fi control)
ESP32 Signal Node
    <-> ESP-NOW / ESP-NOW-LR broadcast
other Signal Nodes
```

Do not claim that ordinary Android phones are native ESP-NOW peers. The ESP32 is the radio boundary. MMS/1 should remain the payload/recovery layer above ESP-NOW so duplicate/out-of-order/lost application frames have the same semantics as QR/audio links.

Potential capabilities once a real bridge is implemented and tested:

- `signal.espnow.transmit`
- `signal.espnow.receive`
- channel/range survey and RSSI diagnostics
- optional acknowledgement/ARQ on top of the one-way MMS/1 baseline

Long-range performance must be documented from the exact ESP32 family, antenna, regulatory configuration and environment actually tested; no universal range claim belongs in the capability contract.

### External LoRa / sub-GHz radio

Use an appropriate compliant radio module rather than attempting to radiate arbitrary RF from GPIO. MMS/1 can be transported over an external LoRa/FSK modem once the device registry/bridge has a real execution path.

## 2. ESP32 magnetic coil link

Proposed short-range near-field transmitter:

```text
ESP32 GPIO/PWM -> MOSFET/H-bridge -> current-limited coil
                                      )) magnetic field ))
Android magnetometer <----------------------- receiver
```

Required engineering before exposure as a MethodMesh capability:

- coil resistance/current/temperature limits;
- flyback protection and appropriate driver topology;
- field strength versus distance/orientation;
- Android magnetometer sample-rate/device variability;
- baseline subtraction and ambient-field rejection;
- symbol rate/Manchester or other DC-balanced encoding;
- MMS/1 frame transport above the physical symbols;
- explicit warning that a test coil can affect compasses/magnetic media/devices nearby.

A bidirectional node could add its own magnetometer/coil rather than pretending phone-to-phone magnetic transmit is generally available.

## 3. Optical modem — Development baseline v0.5.0, field-test repair v0.5.1–v0.5.3

The machine-oriented optical modem is now a canonical phone-only capability family distinct from human Morse:

- `signal.optical_screen.transmit` / `signal.optical_screen.receive`: whole-screen 4-PAM plus v0.5.2 AprilTag16h5 Burst. The legacy `grid` token is retained for compatibility but the luminance lattice is retired; every changing tag is its own localisation/orientation symbol with block parity and loop recovery;
- `signal.optical_torch_ppm.transmit` / `signal.optical_torch_ppm.receive`: compact single-flash 8-PPM with seven-flash clock acquisition and profile-defined parity;
- MMS/1 remains available on general links; v0.5.1 adds a compact short-text CRC frame for low-bitrate optical modes to avoid disproportionate framing overhead;
- Long / Balanced / Fast matched profiles with Long explicitly prioritising range;
- camera ROI, hardware zoom and underexposure controls;
- unstable camera observations treated as erasures rather than guessed symbols.

Remaining work is empirical device characterisation: maximum practical range by phone pair/environment, camera frame-rate limits, screen PWM interactions and whether a separate rolling-shutter mode is portable enough to expose as Experimental. Rolling-shutter exploitation is not part of the canonical v0.5.2 contract.

## 4. MMS/1-over-Morse

Keep ordinary Morse human-readable, but add an explicit machine packet mode for cases where extreme slowness is acceptable:

- compact restricted alphabet (likely Base32-like) chosen for Morse cost;
- MMS/1 frame/CRC semantics;
- clear frame separators and repetition;
- receiver mode that turns decoded Morse groups back into MMS/1 frames.

This should be a mode, not a silent change to ordinary `signal.morse.transmit` semantics.


## 5. Manual Morse capture-and-infer — implemented v0.5.1, large-control UI v0.5.2

`signal.morse.receive` now exposes a **Manual** source for human-observed signalling:

- the operator presses START SIGNAL on the observed MethodMesh start marker, then DOT/DASH for each observed mark; in v0.5.2 the live decode occupies the upper instrument area and DOT/DASH are deliberately very large side-by-side controls for eyes-on-signal tapping;
- the next START closes that observation and immediately anchors the next copy;
- no manual letter/word/end controls are required;
- taps before START are quarantined;
- raw tap timestamps for the current observation are re-segmented as the inferred cadence changes, so the configured WPM is only a weak prior;
- onset-style and end-of-mark recognition tapping are both fitted; complete START-bounded copies feed the existing probabilistic repeat-consensus chain.

Future refinement may expose per-character timing confidence and optional committed raw tap provenance, but the basic human receiver is no longer roadmap-only.

## 6. DTMF and additional acoustic channels

Potential next acoustic modems:

- DTMF data groups for telephone/radio compatibility;
- multi-frequency or chirp acquisition;
- optional calibration sweep before near-ultrasonic use;
- automatic selection from the current live spectrum rather than manual A/B/C/D matching;
- measured channel profile showing usable carrier bands and SNR;
- faster FSK serialization using compact binary MMS/1 wire format rather than the first-pass ASCII frame representation.

## 7. Surface / vibration link — implemented Experimental baseline in v0.3

`signal.surface.transmit` and `signal.surface.receive` now provide the first phone-only tabletop channel: low-frequency speaker OOK into a shared rigid surface, accelerometer rest calibration and MMS/1 recovery with SHA-256 content verification.

Future work remains:

- compare speaker coupling with haptic-motor transmission where hardware permits;
- optional microphone/accelerometer diversity reception;
- automatic bit-rate/carrier calibration by surface;
- characterise useful range and error rates on representative wood/metal/plastic structures.

The current capability remains Experimental until that physical characterisation exists.

## 8. Sensor-scope expansion

Candidate diagnostics:

- scrolling light/magnetic/motion traces;
- capture-window statistics;
- FFT/spectral view where meaningful;
- trigger threshold and edge timing;
- export of explicitly committed diagnostic windows rather than accidental continuous logs.

The existing Acoustics analyser remains the richer audio scientific instrument; avoid duplicating it merely to make the Signals dashboard look comprehensive.

## 9. ESP32 firmware packaging

If Signal Node firmware becomes part of MethodMesh:

- keep firmware source/binary and exact board support module-owned only where the current architecture supports module-local loading;
- provision/install through an appropriate Workbench capability;
- expose firmware version/protocol compatibility in the device registry;
- preserve phone-only Signals capabilities when no external node exists.


## 6. Colour-assisted screen Morse — implemented v0.5.3

Screen Morse now carries redundant mark identity in colour while preserving ordinary Morse timing: WHITE dots/acquisition and RED dashes/START/END. Camera Auto mode calibrates those colours from the known preamble/START sequence and fuses chroma with duration probabilistically. Weak colour is ignored, preserving monochrome interoperability.
