# Signals v0.4.5 validation

Status: **Development**  
Near-ultrasonic capabilities: **Experimental**

Review basis: current `docs/METHODMESH_MASTER_BOOK.md` on MethodMesh `master`, **v1.08 FINAL, updated 2026-09-08**.

## Pass A — contract correctness

- Module ID is `signals`; the handoff root is one self-contained `signals/` folder.
- Eleven independently callable methods are registered by `SignalsModule.as100Methods()`.
- All eleven have matching `CapabilityScreenSpec` entries and module-owned settings.
- Every method declares exactly one maturity value through descriptor `maturity` metadata and exactly one `OFFLINE` connectivity value.
- Development: Morse TX/RX, QR TX/RX, audio FSK TX/RX, sensor scope.
- Experimental: near-ultrasonic TX/RX and tabletop surface TX/RX.
- No ESP32/ESP-NOW/magnetic-coil method is registered without a real Android/hardware execution path.
- Existing Acoustics IDs/contracts are not moved or duplicated.
- No shared MethodMesh UI/source modification is required for the module handoff.
- Physical methods intentionally return `Unsupported` from headless `execute()` because the observable camera/audio/torch/sensor operation must run through the canonical interactive capability screen. The method contract itself remains the same across direct, preset, protocol and ODK surfaces.

## Pass B — interaction quality

- Tool/instrument surface appears before settings.
- Working result and committed result are distinct.
- Commit freezes the canonical payload.
- Automatic-return origins return only at Commit.
- Displayed useful scalar/text results are tap-to-copy.
- Committed text results expose Share, Save and Done on the same capability surface rather than detouring through a generic result page.
- Fixed preset settings use `CapabilityScreenContext.settingShouldBeShown()`.
- Walkie-talkie audio FSK exposes an explicit PTT/squelch lead-in and explains the manual PTT/VOX use case.
- Near-ultrasonic UI explicitly states that high-frequency audio is device-specific and not guaranteed inaudible.
- Morse front-screen transmission uses a full-screen black/white emitter surface, raises the activity brightness request to maximum for the live transmission, restores the prior brightness on exit, and retains an explicit Stop control.
- Physical TX/RX channel/timing controls are disabled while active so the visible configuration cannot drift from the running hardware session.
- The 0.2 instrument redesign uses capability-specific live surfaces: amber telegraph Morse, cyan optical QR modem, cyan audible FSK modem, violet near-ultrasonic modem and channel-accented sensor oscilloscope. Settings remain subordinate/theme-native.
- QR transmission expands to a full-screen optical surface and requests maximum activity brightness while active; Stop remains explicitly available.
- Morse camera receive uses the same visible ROI that drives luminance analysis, now with Full/Focus/Pinpoint sizes, tap-to-position manual placement, real CameraX zoom and temporal-modulation auto-lock. The lock scores frame-to-frame modulation after removing frame-wide exposure change, then tracks locally around the acquired source.
- Auto-lock deliberately withholds Morse transitions until a source is locked; acquiring/reacquiring the ROI resets the working decoder boundary so search motion cannot be mistaken for Morse.
- QR receive exposes real camera optical zoom through JourneyApps camera parameters (1x / 2x / 4x / device maximum) without changing the QR/MMS contract.
- Tabletop transfer presents contact-modem TX plus accelerometer rest calibration, seismograph trace and SIGNAL -> CLOCK -> SYNC -> FRAME -> MMS telemetry.
- QR text/file reception is Commit-eligible only after end-to-end SHA-256 verification of the reconstructed useful object. Multi-segment transfers also verify a transfer-envelope SHA-256. Verified files expose Open, Share, Save, frozen checksum sidecar data and provenance JSON.
- Audible/high-band FSK receive presents a live microphone spectrum, peak frequency, MARK/SPACE confidence and CLOCK/SYNC/FRAME/MMS telemetry so physical acquisition can be separated from packet failure. Matched A/B/C/D channel profiles set carrier pair and bit timing together while preserving the underlying canonical settings.
- Morse-generated cycles are looping-only and self-framed as acquisition flashes -> 12-unit START -> message -> 20-unit END -> silence. Pre-START orphans have zero initial voting weight; END-anchored suffixes can contribute only after a later complete bounded cycle establishes alignment. The native UI explains `dot_ms = 1200 / WPM` (5 WPM = 240 ms).
- Sensor scope presents a bounded rolling live trace plus large magnitude/value and axis telemetry.
- Committed result cards use a separate green frozen-record treatment to make live-versus-committed state visually explicit.
- Native tuning is discrete and reconciled with presets/ODK: Morse optical 5/8/10 WPM, Morse audio 5/10/20/30 WPM, Morse tones 500/700/1200/2200 Hz, independent element/letter/word spacing presets, dBFS −78/−66/−54/−42, FSK/high-band A–D matched carrier+bit profiles, PTT lead 0/120/300/600 ms, QR shard/dwell presets, and Experimental tabletop A–D matched profiles.

## Pass C — field robustness

- Transmitter message IDs are saved working state; Activity recreation cannot silently generate a new QR/FSK MMS/1 identity while retaining completed-cycle state.
- Active camera/microphone/speaker/torch/accelerometer sessions lock the current orientation, preventing rotation-triggered Activity recreation from silently killing the live physical session. The host orientation policy is restored after Stop.
- QR receive retains complete distinct MMS/1 frames across recreation while the physical camera-listening flag is deliberately not restored as live.
- Changing a QR receiver message filter clears working shards without deleting an already committed result.
- Changing Morse receiver source/timing/tone thresholds clears working decode evidence without deleting an already committed result.
- Changing FSK receiver carriers/bit period/noise threshold clears working shards and partial physical framing while preserving any committed result.
- FSK transmit/receive committed cards read MARK/SPACE/bit values from frozen canonical fields, not current live controls.
- FSK receive retains complete MMS/1 frames but discards partial physical bit runs on restart.
- Morse microphone reception is module-local and does not depend on another module's private capture implementation.
- Near-ultrasonic receive prefers `UNPROCESSED` where Android advertises it, then `MIC`, before speech-oriented input.
- Raw microphone audio and camera frames are not persisted.
- No network service is used.

## MMS/1 / pure Kotlin smoke tests

The pure codec sources were compiled with the installed Kotlin compiler and exercised outside Android.

### Reed-Solomon recovery

`SignalPacketCodec` smoke test passed for:

- empty, short, multi-shard and UTF-8 text payloads;
- Fast, Robust and Extreme profiles;
- corrupted frame CRC rejection;
- shuffled selection of exactly `k` frames from the coded set, repeated across multiple random selections;
- every consecutive `k`-frame rotation through a looping coded frame set;
- whole-message CRC verification after reconstruction.

Result: **PASS**. The prior standalone v0.4.3 smoke run completed **20,638 assertions** across MMS/1 Reed-Solomon recovery, explicit Morse START framing/repeat consensus, compact FSK physical framing/clock acquisition, SHA-256 text/file envelopes and segmented QR transfers including the 1 MiB support ceiling.

The generator is systematic GF(256) Reed-Solomon based on a Vandermonde matrix transformed so the first `k` rows are identity rows. The test verified the intended first-pass invariant that any `k` distinct generated rows reconstruct the original payload for the tested geometry.

### Morse

Smoke tests passed for:

- text -> Morse notation -> text;
- exact timing simulation;
- adaptive timing with jitter;
- dot durations 80/120/200 ms with initial timing estimates at approximately 0.7x/1.0x/1.4x actual;
- mixed traffic and dash-only `OOO`, dot-only `EEEE`, and `TTT`;
- cyclic acquisition/12-unit START/20-unit END framing across repeated `HELLO` cycles, yielding one bounded `HELLO` best guess rather than concatenation; finite-evidence confidence is deliberately below 100%;
- mid-cycle join/orphan handling: an END-anchored suffix cannot vote before START, then contributes only as a reduced-weight right-aligned observation after a complete bounded cycle;
- two-sample optical edge debounce rejecting one-frame transition artefacts before they reach the timing decoder.

The first adaptive implementation drifted on dash-only traffic. It was changed to normalise a decoded dash to `duration / 3` before updating the dot estimate. Retest: **PASS**.

### FSK physical framing

Smoke tests passed for:

- MMS/1 ASCII frame -> physical FSK bit frame -> arbitrary bitstream sync scan -> exact original MMS/1 frame;
- FSK run decoding sampled at 10 ms intervals for supported 10 ms-aligned bit durations 20, 30, 40, 50, 60, 80, 100 and 150 ms;
- preamble-driven clock acquisition with 30 ms symbols and sparse wrong 10 ms tone-decision windows, verified to recover the exact physical frame;
- live-screen integration review confirming `SignalFskCapabilityScreen` now feeds 10 ms tone decisions into `FskClockRecoveryDecoder` rather than the earlier run-length prototype;
- receiver telemetry reporting sync detection and completed physical frames.

Result: **PASS**.

These are codec/unit smoke tests, not evidence that every phone/radio acoustic path will demodulate successfully.

### FSK timing alignment correction

A deeper receiver smoke test identified that the first draft allowed 25 ms and 35 ms bit periods while the dominant-tone receiver emits decisions in 10 ms windows. That quantisation can over/under-count a run. The module contract/UI remains 20–250 ms in 10 ms steps. Physical testing then moved the practical defaults to 60 ms for audible FSK and 100 ms for near-ultrasonic, with matched A/B/C/D profiles available on both TX and RX.

## Kotlin static compile-oriented review

The sandbox does not contain a network-clonable MethodMesh repository or the full Android/Compose dependency graph, so a true `:app:compileDebugKotlin` run could not be executed here.

Current MethodMesh `master` interfaces were inspected directly before finalising the module, including:

- `MethodMeshModule` / auto-discovery contract;
- `MethodSetting` constructors;
- `CapabilityScreenSpec`, `CapabilityScreenContext` and preset setting visibility;
- current CameraX/ZXing host dependencies;
- current manifest `CAMERA` and `RECORD_AUDIO` permissions;
- current `PhoneSensorRepository` sensor IDs/units.

A standalone Kotlin parse/compile attempt over the complete module reported the expected unresolved Android/Compose/MethodMesh references in the stripped sandbox; no Kotlin parser `expecting ...` syntax error was identified. A final lexical delimiter/TODO hygiene pass covered all 17 Kotlin source files. Pure non-Android sources compile and run as described above. The FSK clock decoder retains its acquired preamble candidate while waiting for length/payload windows, avoiding drift to a later equivalent preamble phase. The v0.4 review additionally found that v0.3 had left the live receiver wired to the old run-length decoder despite the newer decoder passing tests; the live screen is now explicitly connected to `FskClockRecoveryDecoder`.

## ODK/XLSForm review

Canonical v1.08 showcase workbooks:

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

All eleven generated workbooks were re-imported with `artifact_tool` and inspected during final packaging. The audit found, for every workbook:

- exactly one MethodMesh intent invocation;
- the expected canonical method ID;
- `input_payload_mode='FULL'`;
- `return_mode='flat'`;
- no `methodmesh_return_namespace`;
- direct group-child canonical return names;
- shared `methodmesh_status` and `methodmesh_full_json`;
- no duplicate survey node names;
- human-readable title, lower-snake-case `form_id`, and `2026090905` version;
- no spreadsheet formula-error tokens in the inspected workbook.

Cell/formula error search matched 0 entries across the eleven canonical workbooks.

The capability-return inventory had already been cross-checked against the declared method outputs when the workbooks were generated; no XLSForm-only capability return field was added.

`pyxform` is **not installed in this packaging environment** (`ModuleNotFoundError`), so pyxform/ODK Validate and actual ODK Central/Kobo import are explicitly still receiving-environment checks. The structural audit is not presented as provider validation.

## Receiving-repository checks required before Production promotion

Run at least:

```text
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew generateMethodMeshOdkTemplateAssets
```

Then run the MethodMesh XLSForm batch validator and, where available, pyxform/ODK Validate plus provider upload checks.

## Physical-device smoke matrix

1. **Morse screen/torch -> camera**: test looping only at 5 WPM first, then 8 and 10 WPM. Join reception midway through a cycle and confirm the orphan remains excluded until a later complete START→END frame. Confirm START and END are separately recognised, the displayed result remains one message, and confidence rises across agreeing complete cycles. Optical routes must not permit >10 WPM.
2. **Morse camera range/front end**: exercise 1x / 2x / 4x / Max zoom, Full/Focus/Pinpoint ROI, tap-to-position and auto-lock. Compare Screen profile against a flashing display with visible rolling-shutter swipe, then Torch/point profile against a point LED with flare decay. Verify transition frames do not create extra Morse edges.
3. **Morse audio**: confirm sustained beeps rather than onset ticks; test 5, 10, 20 and 30 WPM and verify PARIS timing (`dot = 1200 / WPM`). Compare microphone decode with optical decode.
4. **Audio FSK phone-to-phone**: select the same A/B/C/D profile on both phones. Before expecting packets, confirm the spectrum visibly contains peaks near both selected carriers, then observe CLOCK -> SYNC -> FRAME -> MMS progression. Test both directions between handset models.
5. **Walkie-talkie FSK**: repeat the matched-profile test through radio microphone/speaker paths; test clipping, squelch/VOX and PTT lead values.
6. **Near-ultrasonic**: begin with profile A (12/13 kHz), then step through B–D only if the spectrum shows both tones. Treat absence of peaks in the spectrum as a handset/audio-path limitation rather than packet failure. Record usable bands by handset pair.
7. **QR text blast**: use the packaged long example text, start the receiver mid-loop, drop/obstruct frames, confirm RS recovery and visible BROADCAST vs REBUILT SHA-256 match.
8. **QR files**: test small files first, then progressively larger files up to 1 MiB. Multi-segment transfers must show segment progress, transfer-envelope SHA-256 verification, object SHA-256 verification, then Open/Share/Save. Inspect the saved verification sidecar and `methodmesh_full_json`.
9. **Tabletop**: press Calibrate with transmitter off; SAMPLES must begin increasing or a no-samples error must surface in about 1.2 s. Calibration errors must remain in the capability UI rather than crash the app. Then transmit on a rigid surface and observe VIB/SAMPLES/THRESH plus CLOCK/SYNC/MMS progression while TX bit progress remains live. Repeat on wood, metal and a box/case.
10. **Rotation/recreation**: attempt to rotate during each active TX/RX/sensor session; the current orientation must remain locked and the physical session must continue. Stop the session and confirm the host orientation policy is restored.
11. **Preset/protocol/ODK**: verify the same underlying canonical settings/returns across all surfaces, including the 11 single-call showcase XLSForms.

## Known limitations / intentionally non-Production items

- Near-ultrasonic behaviour is hardware-specific and remains Experimental.
- MMS/1 is error detection/recovery, not cryptographic authentication/encryption.
- Acoustic FSK/tabletop links compact normal generated MMS/1 frames for the physical wire and reconstruct the exact CRC-bearing ASCII frame before transport validation. They remain deliberately low-throughput.
- Ordinary Morse uses repetition/looping and adaptive timing, not MMS/1 Reed-Solomon packet mode yet.
- `light_sensor` remains accepted for backwards compatibility but is hidden from ordinary Morse receiver selection; camera and microphone are the supported native paths.
- A future manual-Morse capture/infer mode is not yet implemented; current live decoding still begins from an approximate dot/WPM estimate unless a MethodMesh START beacon is present.
- ESP-NOW, ESP32-LR and magnetic-coil modems remain roadmap work. Tabletop/surface transfer is retained as explicitly Experimental; current phone-pair testing has not produced reliable reception.
- QR supports files up to 1 MiB but this does not imply high throughput: a 1 MiB object can require thousands of displayed QR frames with the current ASCII MMS/1 wire format.
- Full Android Gradle compilation and physical hardware validation must be completed in the receiving repository/device environment before Production promotion.

## v0.4 physical-test findings incorporated

- First phone tests showed optical QR was strong, optical Morse was usable, FSK receivers flashed MARK/SPACE without ever producing a frame, high-band audio often collapsed to clicks, and tabletop calibration could crash. Those observations directly drove the v0.4 changes rather than being documented as unexplained failures.
- FSK now has a spectrum view and the live receiver is wired to the tested preamble/clock decoder. Audio generation uses continuous phase and short attack/release ramps.
- Morse repetitions are framed and combined at message level. The result surface reports cycle count and best-guess confidence.
- QR file handling uses segmented MMS/1 above the one-matrix Reed-Solomon limit, with a 1 MiB object ceiling and two integrity layers: transfer-envelope SHA-256 plus original-object SHA-256.
- Tabletop sensor startup/calibration callbacks are guarded; a device/sensor/calculation failure is surfaced as a recoverable UI error.
- Compose weight import compatibility remains fixed: no explicit `androidx.compose.foundation.layout.weight` import is present.


## v0.4.2 focused device retest

1. Audible FSK A/A: confirm RX spectrum shows both carriers, SYNC increments, then FRAME displays an increasing percentage rather than appearing stuck at zero. Test at moderate phone volume before maximum volume.
2. Audible FSK C/C: use the 40 ms fast-test profile after A works and compare first-frame recovery time/error rate.
3. Morse optical: confirm screen/torch cannot be configured above 10 WPM and test 5, 8 and 10 WPM.
4. Morse audio: test 20 then 30 WPM; 30 WPM corresponds to a 40 ms dot and is the common TX/RX sound-only cap.
5. High-band A/A: start at 12/13 kHz. Only move upward if both target peaks are visible.
6. Tabletop RX: Calibrate must immediately increment SAMPLES, complete baseline calibration, or surface a no-samples error in ~1.2 s.
7. Tabletop TX: UI BIT progress must continue throughout the physical frame and Stop must remain responsive.


## v0.4.3 focused Morse framing retest

1. Start camera RX midway through a looping message. It may report an END-anchored orphan but **CYCLES must remain 0** and Commit must remain disabled.
2. Allow the next full cycle through START and END. CYCLES becomes 1; the bounded message becomes the best guess. Any orphan contribution must be suffix/right-anchored only.
3. Allow at least three complete cycles. The best guess must remain one message, not concatenate, and confidence should increase when copies agree.
4. Deliberately occlude one cycle or corrupt one character. Confidence should fall locally rather than allowing the whole message to slide.
5. Screen profile at 5 WPM: observe diagonal rolling-shutter swipe frames in preview and confirm the two-frame stability gate prevents them becoming extra edges.
6. Torch/point profile at 5 WPM: use Pinpoint ROI/auto-lock and confirm stronger underexposure plus three-frame stability improves ON/OFF separation despite starburst decay.
7. Compare locked 5 WPM timing with Auto. Locked timing is the reference optical mode; Auto is experimental and should never be required for a normal MethodMesh loop.


## v0.4.4 focused physical-channel retest

Device evidence prompting this revision:

- Audible FSK successfully recovered a message on real phones; former C/D frequency pairs were less reliable and the output was unnecessarily loud.
- Near-ultrasonic reception reached full physical-frame progress, including a 15/16 kHz D-profile frame in loud music, but a completed rejected frame could disappear back to 0% without a persistent outcome.
- Tabletop RX reported that a 0-microsecond sampling request requires `HIGH_SAMPLING_RATE_SENSORS`; this revision instead requests 10,000 microseconds / 100 Hz.
- Tabletop TX could continue its blocking physical send after the Compose job reference was cleared because the old nullable predicate evaluated null as continue; v0.4.4 uses an explicit atomic continuation flag.
- Optical Morse was demonstrated across approximately 30 m at night, but dynamic LIVE/QUIET header width caused vertical layout movement during decoding.

Pure-code validation additionally covers transparent acoustic DEFLATE round-trip and compact A2 FSK frame reconstruction. Final v0.4.4 smoke result: **20,645 assertions PASS**. Full Android/device validation remains authoritative for microphone, speaker and accelerometer behaviour.


## v0.4.5 discrete preset / rotation retest

1. Confirm there are no tuning sliders anywhere in the Signals native module.
2. FSK and near-ultrasonic: select A/B/C/D on TX and RX and confirm each letter resolves to identical MARK/SPACE/bit timing on both devices.
3. Morse sound: confirm both TX and RX offer the same 5/10/20/30 WPM set and the same 500/700/1200/2200 Hz tones; 40 WPM must not be offered.
4. Morse spacing: independently vary element, letter and word gaps and confirm the receiver exposes the same choices.
5. dBFS: expand the help text and confirm it explains that more-negative values represent quieter levels / greater sensitivity.
6. During every active camera, microphone, speaker, torch and surface/sensor session, attempt rotation. The Activity must remain in its current orientation and the session must continue. After Stop, normal rotation must return.
7. Feed a corrupted/incomplete FSK physical frame and confirm the UI reports `PHY retry: <reason>` without presenting it as a fatal MMS/1 result.
8. Tabletop TX/RX must be visibly labelled Experimental in both capability surfaces and documentation.
9. Presets/protocols and all 11 canonical XLSForms must use the same discrete preset vocabulary as native direct use.

Pure-code v0.4.5 regression: **20,649 assertions PASS**, including shared A–D profile catalogue invariants and the common 30 WPM Morse audio ceiling.
