# Roadmap handoff note — Acoustics v0.1.0

Add under the relevant measurement/signal capability section of `000_Roadmap.md`:

- **Acoustics — Development**
  - Added `acoustic.analyse`: local microphone frequency/pitch analysis, waveform, FFT spectrum, amplitude/dBFS and derived wavelength.
  - Added `acoustic.tune`: chromatic and instrument presets for guitar, drop-D guitar, ukulele high/low G, violin, viola, cello, bass and mandolin.
  - Added `acoustic.level`: uncalibrated dBFS/Leq/peak plus optional device/input-specific dB SPL offset calibration.
  - Added `acoustic.compare`: stable-tone comparison against target frequency using Hz, percentage or cents tolerance.
  - Shared local DSP: PCM16 AudioRecord, YIN pitch detection, Hann/radix-2 FFT, stable-window acceptance, no retained raw audio.
  - Requests Android unprocessed microphone input when advertised; falls back to voice-recognition/MIC and records the actual path in audit metadata.
  - Example ODK workbook covers all four methods.
  - Remains Development pending Android build/device validation, ODK round-trip testing, orientation/preset tests, frequency-range validation and calibrated sound-level repeatability checks.
