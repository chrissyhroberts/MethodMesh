# Astronomy calculators and durable sessions — 29 October 2026

This bounded continuation covers astronomy.exposure_limit, astronomy.image_scale and astronomy.session in /Users/icrucrob/AndroidStudioProjects/MethodMesh, branch feature/esp-mesh-transport, against canonical Master Book v1.31. Polar-alignment/NASA code was inspected for the next checkpoint but is not remediated here. The whole-app review remains active.

## Confirmed fixes

- Exposure limit rebuilt executions from saved output JSON; image scale lost completed results and could calculate before native runtime inputs. Both now retain the exact original execution/FULL, require explicit recovery when unavailable and wait for declared runtime values. Declared defaults are used; typed values are preserved and checked against finite/range constraints. NaN no longer reaches the image-scale slider.
- Image scale keeps its optical readout card and editable sliders for a new run, with canonical result actions after calculation. New-run editing is explicit; final preset Share/Save use the existing success gate and intermediate Continue does not invoke final exports. Automatic callers receive the original through the shared original-ID guard.
- Saved UI state for exposure/session contains only declared inputs. Unrelated caller/audit payloads remain in the original request instead of being copied into saved input state.
- Session Stop previously removed unfinished data before the screen had a retained completed execution, and history defaulted on although save_session is declared off. The UI now follows the declared false default, constructs the exact return record before persistence, and returns it only after checked storage success.
- Session history and unfinished-record removal are one SharedPreferences editor commit. False/throwing writes trigger an exact rollback attempt for active_session/sessions, preserving unrelated calibration preferences; the UI retains the active session and exposes explicit retry. Prepared-return failure does not remove unfinished data.
- A module-owned Activity ViewModel serializes load/edit/start/pause/resume/stop/discard work on IO. Rotation during a blocked Stop keeps the operation and original completion; workers retain application storage and immutable request data, not old Activity/navigation callbacks.
- Starts cannot overwrite an existing or unreadable unfinished session. Malformed history is not silently reset. Duplicate/stale finish or metadata writes cannot recreate a completed session or duplicate history. Metadata updates use authoritative stored timer state, preventing stale edits from resuming a paused timer or resetting its accumulated time.
- Existing unfinished target/equipment/notes are loaded before preset defaults can be autosaved over them. Explicit reload is available; input updates are serialized, and Stop uses the inputs frozen at its trigger. Discard now requires confirmation; Keep session preserves the record.
- Completed sessions retain the exact execution in their run state. Missing original memory requires explicit recovery and does not rebuild the old execution or rerun Stop.

The session store uses an opaque saved run key and keeps operation/result state in Activity memory. Workspace/history remain module-local preferences. Existing session IDs/JSON keys, numeric history duration representation and history bound are retained; canonical session output JSON still comes from the unchanged method engine. No scientific formulas, public method IDs/settings/output fields/versions, maturity, Clock Assurance or transport policy changed.

## Verification

Both debug APK builds pass. Final focused JVM run: 83 passed, no failures/errors/skips:

- 37 Astronomy cases: 13 routing, six acquisition-controller, ten frame/camera ownership and eight local-input/default/state-isolation cases.
- Four retained-original store cases.
- Five native closeout-policy cases.
- Five registry-parity cases (80 modules / 381 capabilities).
- Five ResultShare cases.
- 27 protected NFC contract/evidence/credential/overwrite/one-shot/protocol/compiler cases.

Fifty distinct Android cases were verified across runs on the disposable read-only API 37 ARM emulator:

- 16 new local calculator/session screen and repository cases.
- 11 existing Astronomy measurement cases.
- 10 shared-scaffold action cases.
- Seven actual Home/confirmation/log-order cases.
- Three actual separate-UID PDF/OCR/ZIP delivery cases.
- Three native Share contract cases.

The combined 50-case run passed 49 cases; an existing compiler-delivery-ZIP fixture reread a label while its accessibility tree changed. Its helper now retains a checked node and refreshes the actual clickable target. The final affected 26-case run (all 16 local cases and all ten shared-scaffold cases) passed after the final saved-input isolation change. Together these runs verify all 50 distinct cases; there is no claim of a single final clean 50-case run.

Initial new-fixture timeouts were corrected for restored-session readiness and stale/offscreen accessibility targets. An intervening affected-case rerun was invalidated by Android's UiAutomationService already-registered/disconnected state and test-runner finish crash. Rebooting only the disposable emulator cleared it; the final 26-case run passed. This was test infrastructure rather than a session implementation crash.

Sixteen new production-screen/repository cases exercise asked/fixed calculator inputs, exact original/FULL/Done retention, missing-original recovery, non-finite optics input, unfinished metadata restoration, controlled false-after-memory-mutation writes, atomic blocked Stop through rotation, the omitted history default/audit, stale metadata/timer safety, malformed-record preservation, second-start/preparation protection, confirmed discard, automatic return once, failed final Share/retry and explicit reload. Test preferences are isolated under unique names; they do not clear production session/calibration preferences. Chooser dispatch is controlled.

## Protected boundaries

ResultShare.kt was not edited. Its SHA-256 remains:

6fbfc7a405ead303a0b1cba27fbc3012416ab10384b015350bc62eedd610015e

The working file-first scanner/timestamp/WhatsApp transport and shared result-action implementation are unchanged. NFC/Sentinel/clinical-trial, digital-signing, timestamp implementations and source forms were not edited. The NFC suite is contract regression evidence, not real-card/Collect/Sentinel acceptance.

## Outstanding risks and next work

- Session duration still uses wall time. Monotonic/boot-aware accounting, clock-change handling and real process/power-loss durability remain open; checked commits and controlled rollback tests are not a promise of recovery from every storage failure.
- Manual start/end-date and headless history-persistence parity still need review; this pass repairs the existing interactive timer and completed-result paths.
- Polar alignment still needs cancellable location, frozen-input/fixed-field handling and removal of saved-value execution reconstruction.
- NASA/light-pollution download/import still has saved-busy/cancellation/late-callback concerns and needs cache publication, bounded SAF reads, map restoration and live-provider acceptance. No NASA network requests were made in this pass.
- Real presets/protocols/ODK/Collect recipients, older Android storage/provider faults, physical cameras/sensors/NFC, actual WhatsApp/TSA and large caller/session/log payloads remain acceptance work.
- Full-suite historical failures and the broader all-module scorecard are not superseded by focused checks.
- Disposable read-only emulator only; no physical phone installation, commit, push, tag or release.
