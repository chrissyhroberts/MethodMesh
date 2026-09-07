# UI iteration notes — 2026-09-07

This pass deliberately separates immediate UI work from framework work.

## Implemented now

- restored the dashboard hero hierarchy: **MethodMesh** with **Do Stuff** as the small relevance line;
- removed the `Collect · calculate · commit` slogan;
- removed the dashboard Library block: the drawer already owns navigation, so the dashboard is reserved for search and active work;
- Android Back now returns any top-level MethodMesh section to Dashboard before the system is allowed to leave the app;
- added a first-class **ODK forms** destination mirroring Capabilities/Presets by module;
- added searchable ODK template UI with Share/Save actions against a generated asset index;
- dashboard search can surface ODK templates as well as capabilities, presets and protocols;
- upgraded deterministic search ranking with a small plain-English synonym layer;
- further reduced global corner radii.

## Designed, not falsely presented as complete

- module-to-asset build projection for ODK XLSForm templates;
- Android notification/status/overlay/Quick Settings surface contract;
- module-owned tags and future local semantic search;
- typed protocol pipes with safe transform nodes and a later Scratch-like editor.

The next repository-level pass should implement the generic contracts/build tooling above rather than placing one-off behaviour in `HomeScreen.kt`.

## Iteration 4 — ODK Central rapid testing and destructive-action safety

- Added Settings → ODK Central connection UI.
- Central password is transient; only an encrypted bearer session is retained.
- ODK Forms checkboxes now deploy/update/publish and assign a selected test App User.
- Unticking revokes the test App User assignment without deleting the remote form.
- Added `Sync checked` and local SHA-256 deployment tracking.
- Added explicit remote Central removal with submission-aware confirmation.
- Added a shared destructive confirmation component.
- Preset deletion now confirms and reports protocol references.
- Registered-device deletion now confirms.
- Offline language-pack removal now confirms.
- Scheduler deletion still lives in the shared scheduler component outside the supplied `ui/` package; that component must adopt the same confirmation contract at repository integration time.

## Iteration 5 — Central password handling + real module form projection

- Central password field now keeps `KeyboardType.Password` even while the user reveals the value, so IMEs such as Gboard are not told that the input is normal composing text.
- Added a restrained eye / eye-off control directly on the password field.
- Password reveal state resets after successful login and disconnect; the password itself is still never persisted.
- ODK template catalogue now explicitly supports one-to-many module ownership: every `docs/example_odk*.xlsx` becomes its own record.
- Runtime module labels resolve against `MethodMeshModuleRegistry` so the forms library mirrors the canonical module names used by Capabilities and Presets.
- Added `formVersion` and `sourceRelativePath` catalogue metadata and made both searchable.
- Added a dependency-free generator that reads XLSForm `settings` metadata and projects module-owned XLSForms into Android assets.
- Included a one-line Gradle build hook (`ui/integration/odk-template-assets.gradle.kts`) so `preBuild` can regenerate the ODK template catalogue automatically from all module `docs/example_odk*.xlsx` files.

## Iteration 6 — dual ODK Central / KoboToolbox rapid-test routes

- Added a second server connection under Settings → KoboToolbox.
- Kobo password is transient; the Kobo API token is encrypted at rest and reused for API calls.
- Reused the shared password field, including eye/eye-off and permanent password-mode IME semantics, for both Central and Kobo login.
- ODK Forms now gives each XLSForm independent **ODK** and **Kobo** deployment controls.
- ODK checkbox semantics remain App User assignment/revocation on Central.
- Kobo checkbox semantics are deploy/activate versus deactivate, preserving the remote project and submissions when unticked.
- Added independent `Sync ODK` and `Sync Kobo` actions.
- Added separately confirmed permanent remote removal actions for Central and Kobo.
- Kept Share/Save tied to the module-owned local XLSForm rather than either server copy.
- Added `XLSFORM_SERVER_PROVIDERS.md` to keep future collection clients/providers adapter-based; Survey123 is explicitly left as a future invocation/provider adapter rather than folded into the ODK contract.

## Iteration 7 — ODK/Kobo form catalogue build fix

Observed symptom: ODK Central and Kobo authentication worked, but the ODK Forms page reported that no templates were packaged. The connection layer was healthy; the generated Android asset catalogue was missing from the APK.

Root cause: copying the isolated `ui/` package into the source tree does not itself modify the app module's `build.gradle.kts`, so the XLSForm projection task was never guaranteed to run. Android does not package `modules/<module>/docs/*.xlsx` source files automatically.

Fixes:

- strengthened the Gradle task so every `merge*Assets` task depends directly on XLSForm generation;
- added an idempotent installer for the single app-module Gradle hook;
- expanded discovery to recurse through each module `docs/` folder;
- retained canonical `example_odk*.xlsx` discovery and added structural XLSForm detection as a safety net for oddly named real forms;
- improved runtime diagnostics to distinguish a missing catalogue asset from an empty/invalid catalogue;
- verified the generator with multiple forms in one module and a non-canonical-but-valid XLSForm filename.

## Iteration 8 — Kobo untick + server diagnostics

- Kobo deactivation now uses the v2 deployment PATCH route rather than reusing the deployment POST route. The response is verified with a GET before local state is marked inactive.
- ODK Central and Kobo API exceptions retain HTTP status, request endpoint and structured/raw provider error payloads.
- Per-form sync errors appear inline with `Why did this fail?` and `Copy report` actions.
- Bulk sync retains a separate diagnostic for every failed form and automatically expands modules containing failures.
- Central form-list errors are no longer swallowed as an empty list, avoiding misleading duplicate-form failures downstream.

## Iteration 9 — diagnostic tap-to-copy

- Tapping `Why did this fail?` now copies the complete provider diagnostic report to the Android clipboard and expands the inline details in the same action.
- Tapping `Hide details` only collapses the report and does not overwrite the clipboard.
- The existing `Copy report` action remains available and now gives the same short `Failure report copied` confirmation.

## Iteration 10 — XLSForm validation library

- Added a top-level **Validate XLSForms** review surface inside ODK Forms.
- Batch validation is generated from the module-owned `docs/*.xlsx` source library.
- Always-on static XLSForm checks cover structure, references, groups/repeats, choice lists,
  duplicate form IDs, common unsupported JavaRosa/XPath functions, and MethodMesh naming standards.
- If `xls2xform` is installed on the build machine, generation also runs pyxform + ODK Validate.
- Validation findings are packaged in `methodmesh/odk_templates/index.json` with each form.
- The UI groups failures by module, supports Errors / Warnings / Naming filters, and lets users tap
  an individual issue to copy its complete diagnostic.
- Added copy, share, and Markdown export for the full batch report.
- Naming deviations are reported, never silently rewritten, because deployed `form_id` values are
  compatibility contracts.
