# MethodMesh

**Do Stuff.**

MethodMesh is an offline-first Android toolbox for fieldwork, research workflows and practical device services. It lets a phone run useful local capabilities — scanning documents, reading barcodes, navigating to targets, translating conversations, capturing Plus Codes, redacting images, checking local authentication, reading sensors, printing, and more — while still returning structured outputs to systems such as ODK Collect.

The project has two jobs:

- Make common field tasks simple when used directly as a native Android toolbox.
- Provide a clean capability runtime that ODK/XLSForm, protocols and presets can call without each integration having to reinvent the app.

The short version: MethodMesh tries to give users the beef, not the salad. Native runs show and share the main result. ODK-style calls receive the main result plus a structured JSON audit payload in the background.

---

## Current shape of the app

MethodMesh is organised around a few top-level areas.

- **Dashboard** — shortcuts, recent activity, active schedules and the main “find a capability / find a preset” entry point.
- **Presets** — saved capability setups. Presets can hard-code some settings and ask for others at runtime.
- **Protocols** — chained field workflows and scheduled actions.
- **Capabilities** — production-ready primitives for real use.
- **Workbench** — development, hardware, inspector and diagnostic tools.
- **Device registry** — known local devices and sensor nodes.
- **Settings** — shared services such as ML Kit language packs, output storage and app preferences.

The UI is deliberately being pushed toward a simple rule:

> Configure it once, test it in the same screen, save it as a preset if useful, and show only the result when it runs.

---

## Production capabilities

The currently polished production set is:

| Capability | Method ID | Main native result |
| --- | --- | --- |
| Barcode scanner | `qr.scan` | Decoded barcode or QR payload |
| Calibrated scale | `calibrated_scale` | Selected value or range |
| Document scanner | `document.scan` | PDF/searchable PDF and extracted text |
| GPS target navigator | `gps_target_navigator` | Arrival/navigation result |
| Plus Code capture | `plus_code.capture` | Full global Plus Code |
| Image redaction | `image.redact` | Redacted image |
| Local device authentication | `admin_fingerprint_confirmation` | Authentication result |
| Conversation translator | `conversation.translate` | Conversation transcript and translated speech |

Production capabilities are expected to meet the same four checks:

1. Native runs preserve their result across orientation changes.
2. Preset setup uses appropriate controls such as toggles, checkboxes and dropdowns, not raw text boxes unless free text is genuinely needed.
3. Presets honour fixed settings and only ask for runtime inputs.
4. Sharing returns the main useful result, while ODK/XLSForm returns the main result plus JSON metadata.

Other modules may exist in development or workbench areas while they are being reviewed.

---

## Presets, runtime inputs and ODK calls

Every capability declares its own settings and outputs. The shared UI renders those settings generically; it does not hard-code knowledge about individual capabilities.

Preset creation follows this workflow:

1. Open a capability.
2. Configure and test it in the normal capability screen.
3. Save the current setup as a preset.
4. Choose which settings are fixed in the preset and which should be asked at runtime.
5. Name the preset.

When a preset runs natively, fixed settings are hidden. Runtime inputs appear first, then the action runs and presents a clear result screen. The result screen supports sharing, saving/exporting when wanted, retrying, or returning home.

When called from ODK or another external workflow, the caller supplies its own values and receives structured outputs through the MethodMesh intent boundary. The normal pattern is:

- one or more main output fields for the thing the user actually cares about;
- an optional JSON field containing audit metadata, configuration, provenance and supporting values;
- media attachments when the result is a file, image, PDF or similar.

---

## Offline-first field use

MethodMesh is designed for field conditions where network access may be absent, expensive or unreliable.

Examples:

- **Plus Code capture** calculates Open Location Code locally. Online map tiles can help the user identify buildings, but the code itself does not require Google APIs, geocoding or a lookup service.
- **ML Kit translation and conversation translation** use downloaded on-device language packs. Shared language packs are managed in Settings.
- **Document scanner and image redaction** work as local phone tasks and return local files.
- **GPS navigator** runs against latitude/longitude or Plus Code targets and includes an AR camera guidance view.
- **BLE sensor tools** support MethodMesh ESP32 sensor images, provisioning, live reads and diagnostics.

Online services may be used where they improve the user experience, such as online map tiles, but capability logic should remain as local and deterministic as practical.

---

## Capabilities and the “golden rule”

Capabilities own their own behaviour.

The core app provides:

- capability discovery;
- generic configuration rendering;
- preset creation and runtime handling;
- result presentation;
- scheduling/protocol orchestration;
- ODK-style intent execution;
- shared services such as language packs and storage.

The core UI should not need to know that a particular capability is a barcode scanner, document scanner, translator, printer or sensor reader. A capability declares settings, outputs and screens; the shared runtime renders and executes them.

This keeps MethodMesh modular enough that new capabilities can be added without turning the dashboard into a pile of special cases.

For contributors or AI chats prototyping new capabilities, read [docs/CAPABILITY_WRITING_GUIDE.md](docs/CAPABILITY_WRITING_GUIDE.md) before changing code. It captures the capability ownership rules, preset/runtime requirements, ODK/XLSForm expectations and production checklist.

---

## Workbench and development tools

Workbench contains tools that are useful for building, debugging and integrating with hardware or other Android apps, but are not usually things a field user would save as a research preset.

Examples include:

- ESP32 sensor firmware installation and diagnostics;
- Bluetooth device inspection;
- Android app inspection;
- sensor provisioning and live sensor read tools;
- development capabilities that are not yet promoted to production.

Workbench tools may be powerful and practical, but they are separated from production capabilities because their purpose is different.

---

## ODK and XLSForm integration

MethodMesh capabilities can be called from XLSForms through Android intents. Example forms live with the capability documentation under each module’s `docs` folder.

The intended XLSForm pattern is:

- call the MethodMesh capability through a grouped intent section;
- pass form values into MethodMesh where needed;
- receive the main result into normal ODK fields;
- receive the full MethodMesh JSON payload into a spare audit field when desired;
- receive media files as attachments where the capability produces images, PDFs or other files.

The capability-specific guide should document:

- method ID;
- user-facing purpose;
- settings;
- runtime inputs;
- returned fields;
- ALCOA/audit fields;
- example ODK/XLSForm usage;
- native run behaviour.

The common documentation rules are in `docs/CAPABILITY_DOCUMENTATION_STANDARD.md`.

---

## Repository layout

```text
app/
  src/main/java/com/example/methodmesh/
    core/                 Shared runtime, scheduling and workflow code
    modules/              Capability modules
    settings/             Shared settings and capability setting metadata
    transport/            Intent/workflow transport and result handling
    ui/                   Dashboard, navigation and shared UI
  src/main/assets/
    firmware/             Bundled ESP32 firmware assets

firmware/
  esp32c3_aht20_ble/      MicroPython firmware source and sensor images

docs/                     Cross-project documentation standards

scripts/                  Utility scripts
```

Each module should keep its implementation, documentation and example forms close together.

---

## Build and test

From the repository root:

```bash
./gradlew :app:assembleDebug
```

For the broader local test pass:

```bash
./gradlew testDebugUnitTest assembleDebug
```

ESP32 sensor image changes require rebuilding the firmware images before flashing devices:

```bash
python3 firmware/esp32c3_aht20_ble/build_sensor_images.py
```

---

## Attribution and third-party services

MethodMesh uses a mixture of Android platform services, open standards and third-party libraries/services.

Important examples include:

- Google ML Kit for on-device language, vision and document-scanning features;
- Open Location Code / Plus Codes for offline location identifiers;
- OpenFreeMap and Esri World Imagery as optional online map tile sources where enabled;
- ZXing/ML Kit barcode support depending on capability path;
- Android Bluetooth, NFC, camera, biometric and speech-recognition APIs.

Modules that depend on external services or libraries should document their attribution, licensing and network/offline behaviour in their own `docs` folder.

---

## Status

MethodMesh is an active Android reference implementation. The architecture is intentionally modular and still evolving, but the production capabilities listed above are the current “works and tested” set.

Near-term work includes:

- capability grouping by task type, such as location, language, documents, messaging and devices;
- Android home-screen widgets for presets, protocols and schedule toggles;
- central offline map/tile management;
- additional ML Kit capabilities such as entity extraction, image labelling, pose detection and summarisation;
- continued review of development capabilities before promotion to production.
