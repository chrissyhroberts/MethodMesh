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

## 3. Optical binary modem

Add a faster machine-oriented screen/torch -> camera modem distinct from human Morse:

- preamble/sync;
- Manchester or similarly transition-rich physical symbols;
- camera centre-ROI luminance and automatic thresholding;
- MMS/1 coded frames;
- link-quality and recovered-shard display;
- screen and rear-torch transmit modes;
- empirical device frame-rate/rolling-shutter testing.

## 4. MMS/1-over-Morse

Keep ordinary Morse human-readable, but add an explicit machine packet mode for cases where extreme slowness is acceptable:

- compact restricted alphabet (likely Base32-like) chosen for Morse cost;
- MMS/1 frame/CRC semantics;
- clear frame separators and repetition;
- receiver mode that turns decoded Morse groups back into MMS/1 frames.

This should be a mode, not a silent change to ordinary `signal.morse.transmit` semantics.


## 5. Manual Morse capture-and-infer

Add a distinct receiver mode for human/manual signalling where dot duration is unknown in advance:

- camera auto-lock/Pinpoint ROI acquires a flashing torch or screen;
- record the raw ON/OFF duration series without forcing live character decisions;
- cluster mark durations into dot/dash populations and gaps into intra-character / character / word groups;
- infer the most plausible base unit and decode afterwards;
- expose per-character confidence and retain the raw timing series in the committed provenance record when explicitly requested.

This is intentionally separate from the current live decoder. MethodMesh-generated cycles already carry a distinctive START beacon and can refine repeated copies without needing this inference pass.

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
