# MethodMesh v2.2.7

This release tightens native close-out behaviour and admits a first group of reviewed prototype capabilities into the Development lane.

## Highlights

- Added a public Android Downloads close-out action for native preset, protocol and schedule results.
- Added first-pass guided rails for multi-step preset/protocol/schedule runs.
- Improved combined protocol outputs so steps can progress and finish on a shared result screen.
- Reviewed and admitted Acoustics, Compass, Psychomotor Vigilance Test, Sampling and Sound Generator as Development capabilities.
- Hardened ML Kit language handling for conversation translation and shared language-pack settings.

## Close-out and result handling

- Native result screens now include explicit actions for sharing, copying, saving to Downloads, exporting the full audit package and closing.
- “Save to Downloads” writes text/data results as `result.txt` and writes media as files under `Downloads/MethodMesh/...`.
- Full audit export remains separate from simple user-facing sharing and Downloads output.
- Protocol and scheduled runs now collate step outputs into one final run result screen instead of leaving each preset sitting on its own result page.
- Audit-mode output now retains capability-owned `*_audit_json` fields without adding them to compact core sharing.

## Development capabilities admitted

- Added Acoustics for local microphone-based pitch/frequency analysis, tuning, sound level estimation and tone comparison.
- Added Compass for magnetic heading and bearing-sighting workflows.
- Added Psychomotor Vigilance Test (PVT) with 10-minute PVT and 3-minute PVT-B modes, compact results and trial-level audit JSON.
- Added Sampling for reproducible sampling/shuffling/partitioning workflows with auditable outputs.
- Added Sound Generator for local tones, noise and sweeps with playback provenance.

These capabilities remain in Development until their module-specific device, preset and ODK validation items are closed.

## ML Kit language handling

- Canonicalised ML Kit language codes and common aliases, including Chinese, Japanese and Korean.
- Improved conversation-translation speech locale mapping.
- Improved shared language-pack settings feedback and downloaded-language sorting.

## Documentation and roadmap

- Updated the capability writing guide to reinforce the canonical “one module folder with nested docs and XLSForm” delivery contract.
- Updated `000_Roadmap.md` with the admitted Development capabilities, remaining validation notes and completed close-out work.

## Known issues / follow-up

- PVT remains Development because Android device timing/latency has not yet been physically characterised.
- Sampling CSV creation/input still needs real device validation.
- The not-yet-ready prototype queue still contains capabilities that either do not build or need deeper review before admission.
- Exchange-rate selection/value handling remains on the roadmap for a later pass.

## Validation

- `./gradlew :app:assembleDebug` passes.
