# Sound generator — drop-in installation

Copy this entire `soundgenerator` folder to:

```text
app/src/main/java/com/example/methodmesh/modules/soundgenerator/
```

No central module registration is required. `SoundGeneratorModule.kt` follows MethodMesh automatic `*Module.kt` discovery.

## Required application-manifest exception

The capability's optional `temporary_set_percent` media-volume policy needs Android's `MODIFY_AUDIO_SETTINGS` permission. Add this alongside the existing `<uses-permission>` declarations in:

```text
app/src/main/AndroidManifest.xml
```

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

This is the only required file change outside the canonical module folder. Without that manifest declaration, tone/noise/sweep generation and the `preserve` / `require_percent` volume policies still work, but `temporary_set_percent` will fail with an Android permission error rather than silently pretending the volume changed.

## Build

From the complete MethodMesh checkout:

```bash
./gradlew :app:assembleDebug
```

## Minimum device checks before considering Production

1. Play a 1000 Hz sine tone at `-30 dBFS`, 1000 ms, both channels.
2. Play left-only and right-only tones through headphones and verify channel assignment.
3. Play white, pink and brown noise.
4. Run fixed-seed pink noise twice and verify identical `sound_pcm_sha256`.
5. Run secure-random noise twice and verify different recorded seeds / PCM hashes.
6. Run linear and logarithmic sweeps.
7. Verify 10 ms fades remove obvious start/stop clicks for a normal sine tone.
8. Verify pulsed playback and pulse-edge fades.
9. Select a wired / USB / Bluetooth route where available and compare requested vs actual routed device.
10. Verify `preserve` leaves system media volume unchanged.
11. Verify `require_percent` refuses playback at the wrong media-volume index.
12. With the manifest permission installed, verify `temporary_set_percent` changes and then restores the original volume.
13. Interrupt playback with another audio-focus claimant and verify `sound_status=interrupted`.
14. Press Stop mid-run and verify `sound_status=stopped`, partial frame counts and separate planned/written hashes.
15. Rotate after a completed result and verify the result is retained.
16. Exercise `docs/example_odk_SoundGenerator.xlsx` through ODK Collect and verify flattened return fields.

Keep the capability in **Development** until the full Android build and physical-device checks pass.
