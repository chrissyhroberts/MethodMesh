# MethodMesh module contract audit — v1.22

Date: 2026-09-14

## Scope

Static source review of all 76 admitted module directories in the supplied `main.zip`, against the v1.21 preset/scheduler concepts and the v1.22 clarifications introduced by this review. Existing capability IDs and established capability behaviour were preserved unless a shared-contract defect was clear.

This pass checked: canonical result projection; Result / + Audit / + Full JSON; preset Fixed versus Ask when run semantics; Auto / Show UI / Background compatibility; Return / Save / Share completion; launch-origin closeout; custom preset editors; intent auto-run behaviour; and obviously weak native surfaces. Hardware/network behaviour was not claimed as device-tested in this environment.

## Shared findings and changes

- **Result projection was too name-driven.** `OutputFormatter` could hide useful values simply because names contained `summary`, `duration`, `selection`, `interval`, `mode`/`model`, or ended in `_json`. Result projection is now semantic: practical capability outputs stay in Result; explicit provenance/audit material is added by + Audit; + Full JSON adds the complete envelope.
- **Ask when run needed capability ownership.** `MethodSetting` now has a backward-compatible `runtimeInputAllowed` property (default `true`, appended to constructors so positional call sites retain their argument order). The central preset editor enforces it.
- **Native preset auto-run now respects runtime questions.** `CapabilityScreenContext.startsImmediately` no longer starts an interactive native preset while declared runtime inputs are still outstanding, and direct intent auto-run was corrected in API GET and Random Number where it bypassed that shared signal.
- **Return now means return.** Shared `CapabilityScreenScaffold` and every custom `PresetResultAction` implementation found in modules now unwind through the workflow host instead of clearing the Android task stack to force MethodMesh Home. Share/Save policies are executed before closeout.
- **UI refreshes were selective.** Data Tools, Random Number and AprilTag preset authoring were polished because their old surfaces were notably generic/inconsistent. Rich established instruments were deliberately left alone.
- **No new method IDs were added.** The audit found more value in correcting shared semantics than expanding the public capability surface. This avoids destabilising existing ODK/preset/protocol contracts.

## Module-by-module result

| Module | Result | Notes |
|---|---|---|
| `acoustics` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `adminfingerprint` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `apiget` | **Updated** | Contract: API definition/return-path/fallback are fixed preset configuration; latitude/longitude remain runtime-capable. Intent auto-run now waits when a preset has runtime questions. |
| `appinspector` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `apriltag` | **Updated** | Preset UI refresh to v1.22 concepts; detector/calibration policy marked fixed configuration where clear; existing six capability IDs preserved. |
| `aquaticfield` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `arcade` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `astronomy` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `attestation` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `aviation` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `bluetoothinspector` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `bluetoothprinter` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `calibratedscale` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `chance` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `choiceexperiment` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `clinicalinstruments` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `compass` | **Updated** | Custom preset completion now honours Return/Save/Share through workflow closeout instead of forcing MethodMesh Home. |
| `conversationtranslate` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `conversions` | **Updated** | Preset semantics: calculation category, units, operation and geometry shape are configuration; numeric/date values remain runtime-capable. |
| `cryptography` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `datatools` | **Updated** | UI refresh; operation grouped and treated as fixed configuration, data remains runtime-capable; auto-run waits for runtime input. |
| `digitalsigning` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `diving` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `documentscanner` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `earthscience` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `electrical` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `emergency` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `espmesh` | **Reviewed** | Reviewed. Diagnostic/workbench surface is intentionally transport-specific; no public contract change made. |
| `expenses` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `fieldstats` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `fingerprints` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `gamedeck` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `geocaching` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `gpstargetnavigator` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `hamradio` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `imageredaction` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `labbench` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `livestreamtranslate` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `magnifier` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `media` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `mlkittranslate` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `mlkitvision` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `multicounter` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `music` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `networktools` | **Updated** | Preset semantics: operation, timeout and traceroute hop limit are fixed configuration; target host/port/CIDR can remain runtime inputs. |
| `nfc` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `odkformlauncher` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `paperbridge` | **Updated** | Designer/transcription custom completion now honours Return/Save/Share and launch-origin closeout. |
| `pluscodecapture` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `providercommands` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `psychomotorvigilance` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `qrcode` | **Updated** | Barcode custom completion now honours Return/Save/Share and launch-origin closeout. |
| `questionprimitives` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `randomnumber` | **Updated** | UI refresh with clearer Draw/Randomness grouping; fixed preset fields are hidden; intent auto-run waits for runtime questions. |
| `referencelibrary` | **Reviewed** | Reviewed custom preset creation. Default Auto launch mode is appropriate; no private result-action closeout path found. |
| `sampling` | **Updated** | Sampling policy/output-schema fields marked fixed configuration; population/sample values and seed remain available as runtime data where meaningful. |
| `scaledphoto` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `scoring` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `sensorfirmwareinstaller` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `sensorprovisioner` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `sensorread` | **Reviewed** | Reviewed conservatively. Complex live/freeze and hardware semantics retained; no source-level contract rewrite made without device validation. |
| `signals` | **Updated** | Shared Signals committed-result completion now honours Return/Save/Share and origin-aware closeout. |
| `sms` | **Reviewed** | Reviewed runtime semantics. Recipient/message are legitimate runtime values and existing permission-gated send flow already waits for them. |
| `soundgenerator` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `spatialgeometry` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `speechtranscription` | **Reviewed** | Reviewed. Language/prompt/offline preference are valid invocation inputs; Android recognizer interaction remains operator-facing. |
| `surveying` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `svgselector` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `tabletoputilities` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `tamagotchi` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. Settings are retained as-is because the screen either owns specialised interaction or no unambiguous configuration/runtime split was safe to impose statically. |
| `texttools` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `time_tools` | **Reviewed** | Reviewed. Timer notification opening MainActivity is an explicit timer-control navigation action, not preset Return semantics; left intact. |
| `trustedtimestamp` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `visualacuity` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |
| `weather` | **Updated** | Meaningful-rain threshold no longer becomes a dashboard runtime question; custom completion follows Return semantics; preset authoring remains aligned to Auto/Show UI/Background. |
| `webactions` | **Reviewed** | No suspect shared return/preset contract requiring a conservative source edit was found. |

## Files with deliberate source changes

- `java/com/example/methodmesh/modules/apiget/ApiGetCapabilityScreen.kt`
- `java/com/example/methodmesh/modules/apiget/ApiGetModule.kt`
- `java/com/example/methodmesh/modules/apriltag/AprilTagModule.kt`
- `java/com/example/methodmesh/modules/apriltag/AprilTagPresetUi.kt`
- `java/com/example/methodmesh/modules/compass/CompassCapabilityScreen.kt`
- `java/com/example/methodmesh/modules/conversions/ConversionsModule.kt`
- `java/com/example/methodmesh/modules/datatools/DataToolsCapabilityScreen.kt`
- `java/com/example/methodmesh/modules/datatools/DataToolsModule.kt`
- `java/com/example/methodmesh/modules/networktools/NetworkToolsModule.kt`
- `java/com/example/methodmesh/modules/paperbridge/PaperFormDesignerCapabilityScreen.kt`
- `java/com/example/methodmesh/modules/paperbridge/PaperFormTranscribeCapabilityScreen.kt`
- `java/com/example/methodmesh/modules/qrcode/QrCodeCapabilityScreen.kt`
- `java/com/example/methodmesh/modules/randomnumber/RandomNumberCapabilityScreen.kt`
- `java/com/example/methodmesh/modules/sampling/SamplingModule.kt`
- `java/com/example/methodmesh/modules/signals/SignalUiSupport.kt`
- `java/com/example/methodmesh/modules/weather/WeatherModule.kt`
- `java/com/example/methodmesh/modules/weather/WeatherPresetSupport.kt`
- `java/com/example/methodmesh/modules/weather/docs/VALIDATION.md`
- `java/com/example/methodmesh/settings/MethodSetting.kt`
- `java/com/example/methodmesh/transport/OutputFormatter.kt`
- `java/com/example/methodmesh/transport/workflow/ui/CapabilityScreenScaffold.kt`
- `java/com/example/methodmesh/ui/HomeScreen.kt`

## Validation

- 76 module directories inventoried.
- Every admitted module in this source snapshot still exposes `capabilityScreens()`; no capability was intentionally removed.
- `MethodSetting.kt` compiles independently with `kotlinc` after the additive constructor change.
- Custom preset completion paths were searched repository-wide; the remaining `MainActivity` use in Time Tools is a timer-notification **open timer controls** action, not preset Return closeout.
- Structural source checks were run on the edited Kotlin files. A full Android/Gradle build cannot be claimed from this upload because it contains the `main/` source tree rather than the complete Gradle project.
- Hardware, camera, sensor, Bluetooth, telephony, notification and external-app flows still require real-device regression testing.

## Suggested device regression set

1. Create one standard preset with a configuration-only field and verify it shows **Fixed configuration**, never **Ask when run**.
2. Create a Random Number preset with one runtime value; verify it waits for the runtime question instead of generating immediately.
3. Run QR/Compass/Paper Bridge/Weather/Signals presets with Return, Share and Save; verify closeout returns to the launching app/desktop.
4. Run a scheduled interactive preset from the launcher and from another app; verify transient closeout reveals the original surface.
5. Run API GET with runtime latitude/longitude and fixed API/return-path configuration.
6. Verify practical JSON-heavy results (AprilTag detections, sampling records, weather/model series, etc.) still appear in **Result** rather than disappearing into + Audit/+ Full JSON.
