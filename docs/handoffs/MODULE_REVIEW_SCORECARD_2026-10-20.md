# MethodMesh all-module review scorecard

Started 20 October 2026; updated 28 October 2026. Active checkout: `/Users/icrucrob/AndroidStudioProjects/MethodMesh`; Master Book v1.31. This is a review snapshot, not a new normative standard or capability-promotion record.

## Coverage and limits

All **80 discovered modules / 381 module capabilities** are in scope. The generated build index was loaded by `CapabilityRegistryParityTest`; its five tests passed after correcting two shared-parser invocation failures. The inventory grew from 380 by the user-requested `pdf.redact` addition on 21 October. Method references/descriptors/contracts and versions are consistent; screens/settings/language bindings have no orphan references. There are 381 declared native screens. All module capability IDs pass the flat transport adapter, and all declared flat output names are representable by the generic return projection using synthetic values. Preset/protocol checks establish data-model representability, not end-to-end UI execution. Shared core scheduling methods are additional to these module counts.

The counts below come from that test run. Generic-settings entries count method-keyed schemas, not whether all operational inputs are configurable; zero is not automatically a defect. **Full module-specific runtime/UX review remains incomplete for every row.** Random Number, Conversions, Calibrated Scale, Multicounter, API GET, Weather, Network Tools and the Astronomy dashboard have the limited runtime/recreation checks described below. No row is certified for production or clinical-trial use by this inventory. Hardware, network, permission, actual output emission/type correctness, every Commit workflow and real ODK delivery were not exercised by these metadata tests.

| Module ID | Capabilities | Native screens | Generic-settings entries |
| --- | ---: | ---: | ---: |
| acoustics | 4 | 4 | 4 |
| adminfingerprint | 2 | 2 | 2 |
| apiget | 1 | 1 | 1 |
| appinspector | 1 | 1 | 1 |
| apriltag | 6 | 6 | 6 |
| aquaticfield | 16 | 16 | 16 |
| arcade | 1 | 1 | 1 |
| astronomy | 12 | 12 | 12 |
| attestation | 2 | 2 | 2 |
| aviation | 6 | 6 | 6 |
| bluetoothinspector | 1 | 1 | 0 |
| bluetoothprinter | 1 | 1 | 1 |
| calibratedscale | 1 | 1 | 1 |
| chance | 6 | 6 | 6 |
| choiceexperiment | 5 | 5 | 5 |
| clinicalinstruments | 1 | 1 | 1 |
| compass | 1 | 1 | 1 |
| conversationtranslate | 2 | 2 | 2 |
| conversions | 1 | 1 | 1 |
| cryptography | 20 | 20 | 17 |
| datatools | 1 | 1 | 1 |
| digitalsigning | 1 | 1 | 1 |
| display | 4 | 4 | 4 |
| diving | 18 | 18 | 18 |
| documentscanner | 1 | 1 | 1 |
| earthscience | 12 | 12 | 12 |
| electrical | 1 | 1 | 1 |
| emergency | 5 | 5 | 5 |
| espmesh | 3 | 3 | 0 |
| expenses | 1 | 1 | 0 |
| externaldisplay | 1 | 1 | 1 |
| fieldstats | 10 | 10 | 10 |
| filelab | 2 | 2 | 2 |
| gamedeck | 1 | 1 | 1 |
| geocaching | 13 | 13 | 13 |
| gpstargetnavigator | 1 | 1 | 1 |
| hamradio | 9 | 9 | 9 |
| imageredaction | 2 | 2 | 2 |
| labbench | 13 | 13 | 13 |
| livestreamtranslate | 3 | 3 | 3 |
| magnifier | 1 | 1 | 1 |
| media | 6 | 6 | 6 |
| mlkittranslate | 1 | 1 | 1 |
| mlkitvision | 1 | 1 | 1 |
| multicounter | 1 | 1 | 1 |
| music | 34 | 34 | 34 |
| networktools | 1 | 1 | 1 |
| nfc | 12 | 12 | 9 |
| odkformlauncher | 1 | 1 | 1 |
| paperbridge | 2 | 2 | 2 |
| pluscodecapture | 1 | 1 | 1 |
| providercommands | 1 | 1 | 1 |
| psychomotorvigilance | 1 | 1 | 1 |
| barcode | 3 | 3 | 3 |
| questionprimitives | 4 | 4 | 4 |
| randomnumber | 1 | 1 | 1 |
| referencelibrary | 3 | 3 | 3 |
| sampling | 1 | 1 | 1 |
| scaledphoto | 1 | 1 | 1 |
| scoring | 11 | 11 | 11 |
| sensorfirmwareinstaller | 1 | 1 | 1 |
| sensorprovisioner | 1 | 1 | 1 |
| sensorread | 1 | 1 | 1 |
| signals | 17 | 17 | 17 |
| sms | 1 | 1 | 1 |
| soundgenerator | 1 | 1 | 1 |
| spatialgeometry | 3 | 3 | 3 |
| speechtranscription | 1 | 1 | 1 |
| star_spectrum | 2 | 2 | 2 |
| surveying | 13 | 13 | 13 |
| svgselector | 1 | 1 | 1 |
| tabletoputilities | 1 | 1 | 1 |
| tamagotchi | 9 | 9 | 9 |
| textdocuments | 1 | 1 | 1 |
| texttools | 10 | 10 | 10 |
| time_tools | 8 | 8 | 7 |
| trustedtimestamp | 2 | 2 | 2 |
| visualacuity | 1 | 1 | 1 |
| weather | 11 | 11 | 11 |
| webactions | 17 | 17 | 15 |

## Findings and next passes

- **Fixed, Barcode scan/Compass committed actions (28 October):** final native preset Share now actually dispatches before confirmation; Save gates on complete persistence; intermediate labels/actions match; module Home fallbacks are replaced by owner confirmation. Existing primary payload/heading and technical displays remain, with explicit text-only projections, canonical optional audit, IO saves and synchronous acquisition locks. URI-looking barcode payloads do not become file attachments. Both builds/37 JVM checks pass; 44 distinct Android cases verified across two runs (42/44 initially, all 13 targeted reruns pass). Actual Compass retention/Recommit/host log order is covered; real camera/physical accuracy/Collect acceptance remains open. Shared file transport unchanged. See `BARCODE_COMPASS_RESULT_ACTIONS_2026-10-28.md`.

- **Corrected after user-reported regression, file-bearing native Share (28 October):** PDFs/ZIPs/media now precede summary text, ClipData advertises every stream type, and files no longer compete with an EXTRA_TEXT filename/summary message. Text-only message/text-file equivalence and opt-in audit remain. This supersedes the blanket message-plus-file wording of earlier native Share entries below. Both builds/12 focused JVM tests and a clean 32-case Android run pass, including separate-UID PDF/ZIP reads/hashes. The user reported "seems fixed now"; not all receiver/acquisition/clinical routes are certified. See `FILE_SHARE_REGRESSION_2026-10-28.md`.

- **Fixed/verified, Compass and Signals sensor-scope Commit evidence:** both retain the original execution/FULL JSON through recreation and Done, explicitly replace it on Recommit and require a fresh capture when its retained slot is missing. Sensor scope no longer constructs a second native envelope or reuses a JSON-keyed envelope for identical readings. Fourteen focused JVM checks and 13 Android cases pass in one focused Android run, including four actual-screen cases per capability and five Signals closeout regressions. Missing-slot recovery is simulated, not real process death; Compass camera sight/physical accuracy and the other 16 Signals physical-signal capabilities remain unqualified. See `SENSOR_COMMIT_RETENTION_2026-10-22.md`.

- **Added/verified, protocol favourites:** active protocols now have library/search stars and persistent dashboard shortcuts using the existing protocol dispatcher. Saved-ID favourites survive editing/renaming, remain independent of preset/capability favourites and exclude archived versions. Both builds and ten focused JVM checks pass; 15 focused preset/protocol Android cases were verified across a 14-pass/one-scroll-fixture-failure run and a successful targeted rerun, plus a separate cold-start write/read probe. Protocol steps, versions, bundle contracts and protected NFC/signing sources are unchanged. See `PROTOCOL_FAVOURITES_2026-10-22.md` for archive-copy semantics and coverage limits.

- **Added/verified, preset favourites:** presets now have stars in the Preset library and dashboard search, and saved-ID dashboard shortcuts alongside capability favourites. Renaming retains a favourite; confirmed preset deletion cleans up only its own shortcut; starring never launches or edits the preset. Ten focused JVM checks, seven Android cases (six passes plus a targeted assertion-correction rerun), and a separate write/stop/read cold-start probe pass. Existing capability favourites, preset/bundle contracts and NFC/signing sources are preserved. See `PRESET_FAVOURITES_2026-10-22.md`; no physical-phone installation or full-suite certification.

- **Fixed, shared parser:** `crypto.identity.export` and `tabletop.state.manage` were consumed as result selectors instead of invoked. Explicit invocation now wins over selector heuristics, preserving public IDs.
- **Fixed, shared scheduling:** Android result delivery alone could mark failed work complete and advance dependent steps. Both dispatchers now consult canonical execution status. Eight JVM tests plus controlled emulator plan/protocol failure cases cover this policy. Real field/provider acceptance is still needed. Manual form completion, explicit test-chain override and metadata-free legacy returns retain their previous behavior.
- **Partially fixed, Workbench inspection / open authoring eligibility:** read-only ODK / return contract and Copy ODK intent controls are no longer hidden solely by Workbench classification. Save preset and Run + save restrictions remain unchanged: destructive provisioning/install and protected tools need capability-specific interactive/safety/background policy before blanket exposure. Underlying methods remain callable. See `CANONICAL_ACTIONS_AND_RESULT_RETENTION_2026-10-24.md`.
- **Fixed/verified, shared lifecycle:** both scheduler hosts checkpoint pending results. Controlled emulator tests cover recreation without duplicate child launches, first-chain run/context preservation, two-step success, first-step failure without piping/advancement, and manual form prompt/cancellation/summary restoration. Real OS process-kill and provider-return scenarios remain open.
- **Fixed/verified, Random Number:** the generated execution survives native screen recreation with the same object and full JSON; there is no implicit redraw. Full UX/ODK/provider parity review is not complete.
- **Fixed/verified, Conversions:** a committed result retains its original execution identity/timing/software evidence through recreation rather than rebuilding an envelope from saved values. An unavailable committed execution requires an explicit new run after process loss; actual OS process-kill acceptance is still open.
- **Fixed/verified, Calibrated Scale:** scalar/range working values restore together with selection flags. An untouched range marker stays ineligible. Captured results retain the same execution/calibration/FULL JSON; missing retained evidence requires new explicit selection. Accessible progress actions use the existing marker rules and preserve physical sizing. Six new JVM regressions and three real-screen cases cover this increment; physical/clinical/ODK acceptance remains open.
- **Fixed/verified, Multicounter:** active counts/timers survive recreation; finished evidence retains its original object, audit and FULL JSON. Finished updates/repeated finish are fenced. A missing finished envelope requires an explicit new session. Final audit copies are removed from saved UI state; large active-session state and cross-reboot timers remain unqualified. Three real-screen cases cover counts, timers and simulated envelope loss.
- **Fixed/verified, external attachments/browser boundary:** URI arrays and returned ClipData survive compact returns and receive read grants. Two real separate-UID caller tests read/hash both files through the public router and cannot write or read an audit-only file. Browser publication discovery no longer crawls arbitrary audit JSON. Actual Collect/Enketo acceptance remains open.
- **Fixed/verified, shared native persistence/closeout:** Downloads saves stream and require all selected files, roll back only partial-attempt outputs, and retain producer bytes. Failed Save-and-finish remains unconfirmed with a visible error through rotation. Conversions closes only after successful Save/Share dispatch. Three real storage/scaffold cases pass; provider-specific failures remain open.
- **Fixed/verified, package integrity:** all three legacy package paths reject missing selected attachments and an unavailable configured folder; copies stream. Portable filenames are shared across aliases and declared URI lists/ClipData; canonical JSON and scanner exclusions remain unchanged. Corrupt protocol history is not reset; updates retain a verified recovery copy before overwriting and attempt restoration on failure. Eight projection and five rollback JVM tests plus five actual Android package/history cases pass. Universal SAF atomicity, partial-package cleanup and cloud/legacy provider acceptance are not claimed.
- **Fixed/verified, confirmed opt-in logging:** one confirmation-owned recorder replaces the eager/fallback race; complete FULL evidence, linked media/entry UUIDs and media errors are retained. Re-delivery is idempotent and can repair a failed human summary. Rotation retains pending closeout. Per the user's choice, failure returns the captured result unchanged with a visible warning and additive namespaced `methodmesh_log_warning`; no method status/evidence rewrite. Twelve recorder and one warning-prefix JVM cases, two Android lifecycle cases and two actual separate-UID success/failure return roundtrips pass. Orphan cleanup, large-log scaling, process/power-loss recovery and log-bundle export/share UI remain open.
- **Fixed/verified, native Share:** equivalent message/text-file payloads, textual optional audit, unchanged genuine media/domain files and recipient hint; no broad pre-selection grants; isolated temporary sidecars. Three captured-chooser cases pass without messaging another app. Real receiver consumption and historical grant/cache lifecycle still need acceptance.
- **Fixed/verified, API GET/shared cache:** definition/request no-store bypasses cache reads/writes; fresh-required does not quietly fall back. Failures without a response no longer claim fresh retrieval time/data age. Completed screen evidence and edited inputs survive rotation; interrupted requests allow explicit retry without automatic replay. Unknown fixed definitions fail locally, and native presets respect Ask-when-run inputs. Eleven new cache/evidence/input JVM cases and four cached/loopback screen cases cover the increment; no live provider traffic or durable background-execution claim.
- **Fixed/verified, Weather and Network Tools:** exact committed envelopes survive recreation on focused/dashboard surfaces; missing evidence requires new explicit acquisition. Captured inputs stay linked to working values, interrupted requests release controls, and Weather GPS fences late callbacks. Weather retrieval evidence is not fabricated; runtime latitude keeps fixed longitude hidden and Refresh is no longer clipped at phone width. Failed module Save/Share does not close or confirm. Six Weather JVM cases and 17 Android lifecycle/input/GPS/failure cases cover this increment; live providers/GPS, large working JSON, actual external callers and process death remain open.
- **Fixed, native Home ordering and remaining closeout gates:** shared scaffold, Conversions, Weather and Network already delegated Home to the confirmation/logging host. Compass, Signals, all three Barcode screens and both Paper Bridge screens now honour that ownership too. Compass/Paper Bridge/Signals no longer confirm failed saves; QR Burst now actually attempts preset saves and shows failures. Barcode scan final closeout is last-step-only and honours the audit choice. Thirteen focused JVM cases pass; 14 focused Android cases were verified across an initial 13-pass/one-fixture-failure run and a successful one-case rerun. Six Home/logging screen cases include Barcode generator; Compass/Paper Bridge and Barcode scan/clone edits are source/build checked, not individually fault-injected. See `MODULE_CLOSEOUT_2026-10-22.md`; no blanket all-module runtime certification.
- **Fixed/verified, Astronomy dashboard:** exact completed snapshot retention, explicit lost/interrupted recovery, captured-input provenance, cancellable GPS, runtime-input gate/controls and fixed site fields, honest retrieval time and unchanged automatic external return. Nine synthetic dashboard and three GPS task cases pass. Other Astronomy screens, heatmap visual restoration, NASA/SAF/live API/actual caller and process-death acceptance remain open. Public IDs, scientific scoring and Development maturity are unchanged.
- **Fixed/verified, three automatic Astronomy workflows (28 October):** conditions/dew-risk/imaging-window routing resolves offline/upstream data before GPS, honours native runtime/fixed fields, freezes run inputs and keeps fetched JSON out of saved presets. Single-flight cancellation cannot publish late or create fallback success; exact originals survive recreation, unavailable originals require explicit recovery and automatic confirmation occurs once. Ordinary forecast failure is labelled astronomy-only in visible/shared summaries and audit; canonical failures remain visible. Both builds/38 JVM checks pass; final clean 32-case Android run passes (14 new actual-screen cases, 12 existing Astronomy/GPS, six sharing/file-delivery). No live API/GPS/permission-dialog, physical camera/sensor, real caller/process-death or all-module certification. Shared-scaffold final preset SHARE/synchronous-save limitations remain open. See ASTRONOMY_AUTO_WORKFLOWS_2026-10-28.md.
- **Fixed/verified, shared scaffold preset completion (28 October):** common result controls now reuse the canonical single-flight action handler. Final Share dispatch and complete IO Save gate owner confirmation; failures remain open through recreation. Back/Retry/Cancel are fenced while pending; empty previews cannot bypass exports; intermediate Continue and automatic returns preserve their route; Home delegates to confirmation/logging. Existing preview/media/delivery-ZIP selection and optional canonical audit are retained, and recipient hints are forwarded. Both builds/69 JVM checks (27 NFC) and one clean 69-case Android run pass, including ten new scaffold cases, 14 Astronomy cases, Home/log order and actual file delivery. Prior scaffold closeout caveats are resolved; real recipient/card/legacy-OS/process-death and other module-owned acceptance remains open. See SHARED_SCAFFOLD_CLOSEOUT_2026-10-28.md.
- **Fixed/verified, Astronomy measurement lifecycle (29 October):** sky-test/focus/mount retain exact original/FULL results and recover missing evidence explicitly. Timed sampling stops on interruption; distinct sensor/frame owners fence stale callbacks and late old-source cleanup. Camera leases release owned use cases, including polar AR preview, without global unbinding. The completed clear-night reference action is reachable and retains its exact sample/frozen note. Relevant runtime/fixed fields and shared native actions are preserved; science/public contracts stay unchanged. Both builds/75 JVM (27 NFC) and one clean 53-case Android run pass, including 11 new measurement cases with synthetic hardware inputs and real file-delivery regressions. Physical hardware/CameraX/permissions, polar GPS/results, NASA/SAF and calculators/session acceptance remain open. See ASTRONOMY_MEASUREMENT_LIFECYCLE_2026-10-29.md.
- **Fixed/verified, Astronomy local workflows (29 October):** exposure/image-scale retain exact originals and gate requested inputs; sliders reject non-finite values and image scale uses canonical result actions. Session Stop uses one checked IO history/active commit after canonical preparation, retains unfinished data on controlled write failure and follows the declared history-off default. Restored metadata, serialized/stale-safe edits, explicit reload/confirmed discard and completed-original recovery are verified. Saved UI excludes unrelated caller audit without changing the request. Both builds/83 JVM (27 NFC) pass; 50 distinct Android cases are verified across 49/50 combined and passing final affected 26 cases, with fixture/service limitations documented. Session wall-clock/boot, manual-date/headless and real process/power/disk/caller acceptance remain open, alongside polar GPS/results and NASA/SAF. See ASTRONOMY_LOCAL_WORKFLOWS_2026-10-29.md.
- **Added at user request, photo/document redaction:** independent Development-stage `pdf.redact` in the existing redaction module. Four explicit source actions under two capabilities: new/saved photo (`image.redact`), scanned document/stored PDF (`pdf.redact`). Scans use the existing public `document.scan` contract without OCR/text/timestamp sidecars, then the same multi-page mask editor and fresh raster-only export. PDF-only Share/Downloads and committed preview remain. Saved-photo input normalisation, camera permission/retry, fixed-source/runtime-grid preset acquisition, exact photo evidence retention and actual loaded-source tracking are corrected. Existing IDs, image fields/version/settings tokens and image forms are preserved. Fourteen new JVM cases and 20 Android redaction cases pass, including the earlier eight PDF JVM cases, six new source-contract cases, seven engine cases, seven PDF/scan screen cases and six photo screen cases. Dedicated PDF XLSForm, actual Collect/receiver/preset/protocol, real camera/ML Kit UI and physical-device/memory-stress acceptance remain open. See `PDF_REDACTION_2026-10-21.md`.
- **Fixed/verified, remaining Signals Commit retention:** 14 screen bodies covering 16 physical transmit/receive capabilities no longer rebuild committed executions from saved values. One original execution is used by native display/audit/export/Done and automatic return at Commit; identical-value Recommit creates a new record; missing evidence is visibly unavailable rather than fabricated. The existing framework store is reused unchanged. Seven boundary/wiring JVM cases exercise all 16 contracts; four retained-store, five parity and five closeout JVM cases also pass (21 total). Eight actual-screen QR/QR Burst transmitter cases plus five existing closeout Android cases pass in one 13-case run. No receiver/camera/microphone/torch/contact pairing, real OS process kill, large working-state or real ODK acceptance claim. See `SIGNALS_COMMIT_RETENTION_2026-10-23.md`.
- **Fixed, Paper Bridge/Barcode retention:** five screen bodies use original retained records, not saved-value/partial-ID reconstructions; Paper committed startup skips extraction replay and missing originals require explicit recovery. Scan also retains its working decoded record; clone preserves its action-snapshot UX/PNG URI. Both builds, 21 focused JVM cases and eight Android cases pass (four actual generator workflows, two synthetic Paper post-Commit boundaries, two existing generator closeout regressions). Real design/extraction, scan/clone camera and process death remain unverified. The subsequent Paper Commit consistency increment resolves the mutable-state/hash race described at that checkpoint. See `PAPER_BARCODE_RETENTION_2026-10-23.md`.
- **Fixed, Paper transcription Commit consistency:** one synchronous reviewed-evidence snapshot precedes hashes; single-flight preparation is cancellable, rejects stale working state and cannot return late or unlock a newer commit. New acquisition invalidates the prior session. Both builds, 26 focused JVM cases and seven Android cases pass. Eight controller/wiring JVM cases are new; five Android cases exercise synthetic registered snapshots plus real FileProvider hash bytes, and two existing synthetic post-Commit screen cases pass. Actual captured-page busy/cancel/rotation, camera/OCR and external callers remain unverified. See `PAPER_COMMIT_CONSISTENCY_2026-10-23.md`.
- **Fixed, Paper committed exports:** designer/transcription Save uses shared Downloads; native transcription keeps registered page/ROIs without redundant raw scan while committed/external evidence remains unchanged. Workspace saves are untouched. Both preset Share/Save actions dispatch/persist successfully before confirmation and fail open. Audit stays text-only on Share and optional metadata file on Save; genuine domain JSON/YAML/ZIP remain attachments. Both builds and 30 JVM cases pass; 15 Android cases were verified across an initial 14-pass/one-YAML-metadata-expectation-failure run and one successful focused rerun. API 37 stores generic YAML MIME despite a correct explicit request; suffix/bytes survive. No real capture/receiver/Collect or API 27/28 permission acceptance. See `PAPER_COMMITTED_EXPORTS_2026-10-23.md`.
- **Fixed, shared legacy Downloads and native multi-module exports:** missing API 27/28 storage access now opens private permission-only UI before writing, with explicit retry/no save replay. Modern Android skips it. Weather uses canonical message/text-sidecar Share and marked FULL audit Copy; Weather/Network preset actions and labels are last-step-only. All 15 Signals committed-card sites use one optional-audit action bay; preset Share actually dispatches before confirmation, failures stay open, and typed received files remain real artifacts. Both builds/49 JVM cases pass; 40 distinct Android cases verified over an initial 39-pass/one-label-failure run and three passing targeted reruns. The legacy OS path is not device-accepted; actual receivers/physical channels remain open. See `NATIVE_EXPORTS_AND_LEGACY_DOWNLOADS_2026-10-24.md`.
- **Fixed, four-module common result controls:** all ten Media/Expenses/SVG/Geocaching action sites now receive launch context, gate final native preset Share/Save before confirmation and use single-flight off-UI-thread Downloads. Original completed/committed records survive recreation; SVG no longer re-executes saved display data, Geocaching does not replay equal frozen-snapshot factories and SVG has no bypass header Done. Both builds/27 JVM cases pass; 23 final controlled Android cases verified across targeted runs with fixture/actual-dialog failure-injection limits. Working acquisition/OS process loss/real recipients are not certified. See `CANONICAL_ACTIONS_AND_RESULT_RETENTION_2026-10-24.md`.

- **Next, per module:** Actual API 27/28 permission/Settings/provider acceptance and remaining storage-fault UX; Paper/barcode camera/OCR/process-loss acceptance; Signals physical-session continuity and large working payloads; Compass camera-sight/physical acceptance; remaining module-owned Share/Save conformity beyond reviewed Paper/Signals/Weather/Network, Media/Expenses/SVG/Geocaching and Barcode scan/Compass controls; remaining closeout fault-injection; Astronomy polar GPS/results, NASA/SAF, session clock/manual-date/headless parity and actual measurement-camera/sensor acceptance, remaining module-owned closeout and actual live-provider/caller acceptance; capability-specific Workbench policy; large payload/log limits and live external-app/storage acceptance; further task-specific usability.
- **Protected NFC/Sentinel:** retain the boundary in the engineering-review handoff. No NFC production code or source forms changed. Frozen-evidence projection tests are not a replacement for real-card/Collect/Sentinel acceptance.

The later redaction/zoom record reports the latest full JVM run as **408 total, 404 passed, the same four baseline failures**, with NFC **27/27**. The last fully green broad Android run remains the historical **92/92**; the subsequent 96-case zoom run was not confirmed as a clean full-suite pass. Later focused photo sharing checks passed, and the 22 October closeout increment verified **13 JVM and 14 Android cases** with the two-run limitation described above. These are not a new full-suite result. Memory-loss cases simulate an unavailable envelope, not full OS process death. Both debug builds pass. Existing signing edits/recipient hints are preserved; no commit, push, tag or release. See the dated redaction and closeout handoffs for exact coverage.
