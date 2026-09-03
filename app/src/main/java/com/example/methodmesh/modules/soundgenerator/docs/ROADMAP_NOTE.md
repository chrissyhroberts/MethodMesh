## Sound generator — Development

Add `sound.play`: a local digital acoustic-stimulus primitive using Android AudioTrack.

v0.1 scope:

- tone / noise / frequency sweep;
- sine, square, triangle and sawtooth periodic waveforms;
- white / pink / brown noise;
- linear / logarithmic sweeps;
- explicit peak digital level in dBFS;
- finite duration, fade in/out and pulse gating;
- left / right / both channel assignment;
- selectable preferred output device with actual routed-device provenance;
- preserve / require / temporary-set Android media-volume policies;
- transient exclusive audio focus and interruption reporting;
- deterministic fixed-seed noise and recorded secure-random seeds;
- SHA-256 of generated PCM plus the AudioTrack-written prefix;
- native, preset and ODK/XLSForm execution;
- no network dependency.

Explicit boundary: this is **not an audiometer** and does not claim dB SPL or dB HL. A later calibrated hearing/audiometry capability should depend on this primitive plus a separately validated calibration profile.

Open Development items:

- full `./gradlew :app:assembleDebug` in the complete checkout;
- physical route/channel/audio-focus/volume restoration tests;
- ODK round-trip test;
- active-playback orientation/recreation behaviour;
- API 36 multi-route provenance;
- narrow-band noise, warble tones and continuous playback as later extensions.
