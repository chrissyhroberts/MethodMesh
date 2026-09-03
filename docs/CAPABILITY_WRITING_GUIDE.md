# MethodMesh capability writing guide

Repository: <https://github.com/chrissyhroberts/MethodMesh>

This guide is for AI chats or contributors asked to prototype a new MethodMesh capability from the public GitHub repository. Assume you do not have project memory beyond the repo and this document.

MethodMesh is not a normal “add a screen and wire a button” Android app. It is a modular capability runtime. A capability must own its implementation, settings, outputs, documentation and examples. The shared UI should remain generic.

The golden rule:

> The shared MethodMesh UI knows nothing about individual capabilities.

If you are adding a barcode scanner, translation tool, printer, sensor reader, API call, photo tool or anything else, do not teach `HomeScreen` or another shared screen about that specific capability unless you are deliberately changing the generic framework for every capability.

## What a capability is

A MethodMesh capability is a reusable action that can be run in several contexts:

- natively in the MethodMesh app;
- from a saved preset;
- as one step in a protocol;
- from a schedule;
- from ODK/XLSForm through an Android intent;
- sometimes from another capability as a dependency.

The same capability should behave coherently in all of those contexts.

Native users usually want the beef, not the salad: the main result, clearly shown, easy to share, with minimal metadata in the way.

ODK/XLSForm callers usually want the main result plus a structured JSON/audit payload returned into fields or attachments.

## Where capability code lives

Put all capability-specific code under one module folder:

```text
app/src/main/java/com/example/methodmesh/modules/<module_name>/
```

The canonical delivery is a single folder. Do not return loose Kotlin files, a separate top-level docs folder, or a zip that expects the maintainer to guess where files go.

Use this shape:

```text
app/src/main/java/com/example/methodmesh/modules/<module_name>/
├── docs/
│   ├── README_<Capability>.md
│   └── example_odk_<capability>.xlsx
├── <Capability>CapabilityScreen.kt
├── <Capability>Method.kt
├── <Capability>Module.kt
└── <Capability>Repository.kt          # only if local persistence/state is needed
```

Additional helper files are fine, but they must live inside the same module folder unless there is a genuine shared-framework change.

The module folder is the handoff unit. A reviewer should be able to copy or inspect one folder and find:

- the module declaration;
- the method implementation;
- the capability screen/UI;
- any repositories/helpers owned by the capability;
- documentation;
- an example ODK/XLSForm workbook.

Use existing modules as patterns. Good reference modules include:

- `modules/randomnumber` for a simple pure Kotlin capability;
- `modules/qrcode` for a camera/scanner-style capability;
- `modules/mlkittranslate` for runtime text input and ML Kit service handling;
- `modules/documentscanner` for media outputs;
- `modules/pluscodecapture` for a richer full-screen operator flow;
- `modules/apiget` for declared online data, selected fields and audit JSON.

## Module discovery: do not add a central registration

Each module exposes one object implementing `MethodMeshModule`.

The build generates a module index automatically from files named `*Module.kt` under the modules folder. The app discovers those modules on startup.

Do not add a one-off registration list for your capability.

Minimal shape:

```kotlin
object ExampleModule : MethodMeshModule {
    override val moduleId = "example"
    override val displayName = "Example capability"
    override val summary = "Short plain-language summary."

    override fun as100Methods() = listOf(As100ExampleMethod)
    override fun capabilityScreens() = listOf(ExampleCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100ExampleMethod.ID to listOf(
            MethodSetting.TextSetting("input_text", "Text", defaultValue = ""),
            MethodSetting.BooleanSetting("speak_aloud", "Speak aloud", defaultValue = false)
        )
    )
}
```

## Capability ownership

The capability module owns:

- its method ID and descriptors;
- its Android UI screen, if it needs one;
- its inputs/settings metadata;
- its output fields;
- result formatting decisions for its own domain;
- Android permission requests needed by its own function;
- integrations with device services, sensors, Bluetooth, camera, ML Kit, storage, network, etc.;
- docs and example XLSForms;
- unit tests or instrumentation hooks where feasible.

The shared framework owns:

- dashboard navigation;
- generic capability lists;
- generic preset creation/runtime handling;
- generic setting rendering;
- generic result screen actions;
- generic output projection;
- ODK/external intent transport;
- protocol and schedule orchestration.

Do not put capability-specific special cases into shared code unless there is no module-level alternative and the change is genuinely generic.

## The method object

Every capability needs an `As100Method` implementation. It declares stable identity, outputs and metadata.

Use a stable method ID. Do not rename it casually after examples or presets exist.

Common pattern:

```kotlin
object ExampleFields {
    const val STATUS = "example_status"
    const val MAIN_VALUE = "example_value"
    const val METADATA_JSON = "example_metadata_json"
    const val ERROR = "example_error"

    val outputs = listOf(STATUS, MAIN_VALUE, METADATA_JSON, ERROR)
}

object As100ExampleMethod : As100Method {
    const val ID = "example.run"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Example run")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Example",
        version = VERSION,
        description = "Do one clear thing.",
        outputs = ExampleFields.outputs,
        graphOutputs = listOf("example.run"),
        parameters = mapOf("category" to "Development", "status" to "Development")
    )
}
```

New capabilities should start as `Development`. Only promote to `Production` after the production checklist below has passed.

## Settings: declare them, do not hard-code UI controls in the shell

Settings must be declared in `capabilitySettings()`.

Use the available `MethodSetting` types:

- `BooleanSetting` for toggles;
- `IntSetting` / `FloatSetting` for numeric values;
- `TextSetting` for free text;
- `ChoiceSetting` for a single choice/dropdown;
- `MultiChoiceSetting` for checkbox groups.

Do not use free-text fields for obvious enumerations such as barcode formats, languages, output modes, source selectors, yes/no choices, or fixed option lists. Use choices or multi-choice settings.

Settings divide into three conceptual types:

1. Fixed configuration: safe to save into a preset and hide at runtime.
2. Runtime input: varies each run and should be asked for when a native preset runs.
3. Operational controls: sometimes must remain visible even if a preset fixed most configuration, e.g. choosing or reconnecting a Bluetooth device.

Use `CapabilityScreenContext.settingShouldBeShown(settingId)` to hide fixed preset values during native preset runs.

Use `settingShouldBeShown(settingId, alwaysShow = true)` only for controls that genuinely must always be available.

## Presets: fixed values must disappear at runtime

Preset workflow should feel like this:

1. Configure/test the capability in the capability’s normal screen.
2. Press “save as preset”.
3. Choose which settings are fixed in the preset and which are asked at runtime.
4. Name the preset.
5. Running the preset natively shows only runtime inputs and required operational controls.
6. The capability runs automatically where appropriate and shows a clear result.

If a value is fixed in a native preset, do not show it again in the preset run.

If a value is not fixed, ask for it before the action runs.

ODK is different: ODK/XLSForm calls supply values through intent extras and handle their own UI. Do not force ODK callers through native dialogs.

## Native UX rules

Native MethodMesh is a toolbox, not primarily a database app.

For native runs:

- show the main useful result clearly;
- make the result easy to share;
- default sharing to the main result only;
- keep audit JSON/details in the background or under an optional details/export path;
- do not auto-save to files unless the user or preset result action explicitly asks for saving;
- preserve result state across orientation changes;
- include a Home route after result completion;
- use a confirm/submit action before doing irreversible or meaningful work;
- avoid long walls of JSON on the primary result screen.

Examples:

- barcode scan share = barcode payload text only;
- Plus Code share = the Plus Code only;
- translation share = translated text only;
- image redaction share = redacted image only;
- document scanner share = chosen document/PDF/text output, not verbose metadata;
- sensor read native result = temperature/humidity or presence values, not the whole manifest.

## ODK/XLSForm rules

ODK/XLSForm integration uses Android intents.

Important rules:

- Intent calls should be made via XLSForm groups.
- The XLSForm should provide input values through intent extras.
- The capability should return main result fields plus a JSON/audit field where appropriate.
- Media outputs should return attachment/file URIs where the capability produces images, PDFs or other files.
- The example XLSForm must include the relevant output fields.
- Do not make ODK depend on native MethodMesh dialogs.

The normal pattern is:

```text
ODK group -> Android intent -> MethodMesh capability -> main fields + metadata JSON + media attachments
```

Each capability doc should list:

- method ID;
- input fields;
- output fields;
- main/native result;
- ODK return fields;
- example intent structure;
- known limitations.

## Output design: beef first, audit second

Every capability should decide:

- the main result field;
- optional additional core fields;
- ALCOA/audit-priority fields;
- full JSON metadata field, if needed;
- media outputs, if any;
- error/status fields.

Core output should be compact and useful. Audit output may be verbose.

Avoid making `*_json` the only useful output. If the user wants temperature, return `temperature_c`. If the user wants translated text, return `mlkit_translate_text`. If the user wants a PDF, return the PDF URI.

Do not expose implementation noise as the main result.

## Result screen behaviour

Use `CapabilityScreenScaffold` unless there is a strong reason not to.

The scaffold handles common result actions and preset closeout logic. Capability screens should pass:

- `capturedResult`;
- a compact `resultPreview`;
- `onRetry`;
- `onConfirm`;
- `onCancel`.

When launched from an external intent or automatic preset, capabilities often need to start immediately. Use:

- `context.startsImmediately`;
- `context.submitsImmediately`;
- `CapabilityPresentationMode.IntentLaunch`;
- `CapabilityCompletionMode.AutomaticReturn`.

Do not add a second unnecessary “start” gate for intent/ODK calls.

For native preset runs, ask only for runtime inputs, then run.

## Close-out contract for presets, protocols and schedules

Every capability run must be able to finish cleanly. This matters most when a preset is used as one step in a protocol or schedule: the orchestrator needs to know that the step is done, whether it produced a useful payload, and what fields should be carried forward.

The generic external workflow and scheduler layers return a small close-out envelope:

- `methodmesh_closeout_status`: `completed` or `cancelled`;
- `methodmesh_closeout_step_count`: number of completed capability steps;
- `methodmesh_closeout_has_payload`: `true` when there is a useful non-metadata result;
- `value`: formatted user-facing output, when there is one;
- declared output fields as individual extras;
- media URIs in extras and clip data where applicable.

Capability modules should not invent their own protocol-completion machinery. They should produce normal `ExecutionResult` fields and let the shared scaffold/orchestrator close the run.

Protocol and scheduled runs can include steps that do not return “beef”, such as posting to a web form or triggering a side effect. Those still need a clean done/cancelled signal. Do not force every step to fabricate a fake text payload just so a sequence can continue.

## Pipes and chained values

Later steps in a protocol or schedule can receive fields from earlier completed steps as runtime inputs. The orchestrator exposes prior outputs using these names:

- `step_1_temperature_c`, `step_2_barcode_text`, etc. for a specific previous step;
- `previous_temperature_c`, `previous_barcode_text`, etc. for the most recent value of that field;
- `temperature_c`, `barcode_text`, etc. as a convenient unprefixed value.

This is a low-level runtime contract, not yet a full visual pipe editor. If you build a capability that could consume piped values, declare the relevant runtime input settings normally. Do not hard-code dependencies on another module’s internals.

Example: a later maths/API step may accept `input_temperature_c`. In a chained run, the scheduler can supply that from an AHT20 sensor read as `previous_temperature_c` or `step_1_temperature_c`.

## State and orientation changes

Many capabilities have previously lost payloads on portrait/landscape rotation. Do not repeat that bug.

Use `rememberSaveable` for UI state that must survive configuration changes:

- selected options;
- entered runtime values;
- selected file/image URI strings;
- output/result summaries where feasible;
- launch state, so the capability does not unexpectedly restart.

For complex result objects that cannot be saved directly, save enough stable strings/URIs to reconstruct the display.

## Permissions and Android services

If a capability uses camera, microphone, location, Bluetooth, NFC, files, speech recognition, ML Kit, or network, the capability module should own the boundary and the user-facing failure messages.

Do not assume permissions already exist.

For online services:

- explain what provider is contacted;
- disclose location sharing if any;
- round/coarsen location where required by policy;
- cache where appropriate;
- fall back gracefully when offline if cached data exists;
- include provider attribution when required.

For ML Kit:

- use shared language-pack services where possible;
- do not create duplicate per-capability language-pack stores;
- show a route to download a missing language pack.

For map tiles:

- tiles can be online or cached presentation aids;
- location encoding logic such as Plus Codes must remain locally calculable if that is the capability’s design.

## Dependencies between capabilities

If a capability needs another capability, call or depend on it through its public method/screen boundary. Do not copy its internals.

Example: an attestation capability can consume QR or NFC evidence. It should not reimplement the QR scanner or NFC reader inside itself.

Declare module dependencies with `dependencies()` when helpful.

## Production vs Development vs Workbench

New capabilities start in Development.

Production means:

- behaviour has been tested;
- native UX is clean;
- preset creation and preset runtime work;
- ODK/XLSForm behaviour is documented and has an example form;
- outputs follow the “main result plus audit JSON” rule;
- result state survives orientation changes;
- sharing sends the main useful result only;
- docs and examples are updated.

Workbench is different. Workbench tools are useful for setup, debugging, hardware flashing, inspectors, API exploration, device provisioning, and “hackerish” utility work. They may be mature and reliable, but they are not always normal primitives a field user would save into a protocol.

Do not promote a setup/debug tool into the normal capability lane just because it works.

## Documentation required for every capability

Each capability must include module-owned docs under its module folder:

```text
app/src/main/java/com/example/methodmesh/modules/<module>/docs/README_<Capability>.md
app/src/main/java/com/example/methodmesh/modules/<module>/docs/example_odk_<Capability>.xlsx
```

Do not place capability docs or XLSForms only in the repository-level `docs/` directory. Repository-level docs may link to module docs, but the canonical capability package is the module folder itself.

The README should include:

- what the capability does;
- method ID;
- whether it is Development, Production or Workbench;
- native workflow;
- preset workflow;
- ODK/XLSForm workflow;
- input fields;
- output fields;
- main result;
- audit JSON/full metadata field;
- permissions/services required;
- offline/online behaviour;
- known limitations.

If the capability uses external services, include attribution and offline behaviour.

If it uses ODK, include an example XLSForm. Remember: intent calls need to be made via groups.

## Roadmap bookkeeping

The repo contains `000_Roadmap.md` as a live project notebook.

When adding a capability:

- add a note under the relevant capability or feature heading;
- record open issues or known limitations;
- move resolved items to the Done section when fixed.

Do not leave important caveats only in chat.

## Tests and validation

At minimum, run:

```bash
./gradlew :app:assembleDebug
```

Add focused unit tests for pure logic:

- parsers;
- output projection;
- settings translation;
- API definitions;
- calculations;
- state machines.

For Android boundary capabilities, include as much testable pure logic as possible outside the UI/service boundary.

Before asking to promote to Production, check:

1. Native run works.
2. Native preset creation works.
3. Native preset run hides fixed settings and asks runtime inputs.
4. ODK/XLSForm example exists and returns the correct fields.
5. Main share action sends only the useful result.
6. Full/audit JSON is available where needed.
7. Orientation changes do not lose the result.
8. Permission denial and missing service states show useful messages.
9. Docs and roadmap are updated.
10. Debug build passes.

When handing the work back, identify one canonical delivery folder:

```text
app/src/main/java/com/example/methodmesh/modules/<module_name>/
```

That folder must contain the capability code and its nested `docs/` folder. If Android genuinely requires an asset or resource to live somewhere else, document that exception in the module README.

## Things not to do

Do not:

- put capability-specific logic into the shared dashboard;
- add a central manual module registry entry;
- use buttons for settings that should be checkboxes, toggles or dropdowns;
- use free-text settings for fixed lists;
- show fixed preset values again during native preset runs;
- force native dialogs during ODK calls;
- dump giant JSON as the primary result;
- auto-save native outputs unless requested;
- lose results on orientation change;
- duplicate language-pack, map-tile, device-registry or other shared services;
- scatter capability files across unrelated folders;
- return docs or XLSForms outside the capability module folder;
- silently send precise location to third parties;
- copy another module’s private internals instead of depending on its public boundary;
- mark a capability Production just because it compiles.

## Practical contributor brief

If another AI chat is asked to create a prototype, give it this brief:

> Build a self-contained MethodMesh module under `app/src/main/java/com/example/methodmesh/modules/<name>/`. Return exactly that module folder as the canonical delivery. It must contain the module, method, capability screen, any capability-owned helpers/repositories, and a nested `docs/` folder with `README_<Capability>.md` and an example ODK/XLSForm workbook. Do not scatter files elsewhere unless Android requires it, and document any exception in the module README. Do not edit the shared dashboard or generic UI unless a framework-level change is explicitly requested. The capability must support native runs, native presets, ODK/XLSForm intent calls, compact main-result sharing, and full audit metadata where appropriate. Start it as Development, add roadmap notes, and run `./gradlew :app:assembleDebug`.
