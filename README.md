# ResearchOS

> **An open architecture for scientific knowledge, interoperable research methods and reusable digital research infrastructure.**

ResearchOS is an open, modular architecture for designing, executing and preserving scientific research.

It separates scientific knowledge from software implementation, allowing methods, applications and services to evolve independently while remaining interoperable.

Rather than replacing existing research software, ResearchOS provides a common conceptual model and execution architecture that can be embedded within existing ecosystems such as ODK, KoBoToolbox, REDCap and custom research applications.

ResearchOS is designed around a simple principle:

> **Research should outlive the software used to perform it.**

---

# Vision

Research software has traditionally been built as isolated applications that combine user interface, workflow, storage and scientific logic into a single system.

ResearchOS instead treats research as a collection of interoperable concepts:

- things being studied;
- scientific observations;
- evidence-supported assertions;
- repeatable methods;
- declarative intent.

Applications become interchangeable views onto a shared knowledge architecture rather than isolated data silos.

---

# Architecture

ResearchOS is organised into complementary layers.

```text
Research Philosophy
        │
Conceptual Model
        │
Registry Specifications
        │
Architecture Standard
        │
JSON Object Model
        │
ResearchOS Intent Language (RIL)
        │
Applications • Services • Methods
        │
Android • iOS • Desktop • Web • Server • Embedded
```

Each layer has a distinct responsibility and evolves independently.

---

# Knowledge Model

Scientific knowledge is represented using a small number of core concepts.

```text
Intent
    │
requests
    ▼
Method
    │
produces
    ▼
Observation
    │
may support
    ▼
Assertion
    │
describes
    ▼
Entity
```

This separation enables reproducible methods, interoperable applications and long-term preservation of scientific knowledge independently of software implementation.

---

# Current Focus

The conceptual architecture of ResearchOS is now considered sufficiently stable for implementation.

Current development focuses on validating the architecture through working reference implementations rather than further redesign of the core knowledge model.

Immediate priorities are:

- JSON Object Model
- ResearchOS Orchestrator
- Device Services
- Native Methods
- Android interoperability discovery and reusable integration definitions
- Bluetooth device discovery and endpoint assay
- Android reference implementation
- ResearchOS Intent Language (RIL)

The objective of this phase is to demonstrate that the architecture can support complete research workflows from protocol definition through data collection, analysis and reporting.

---

# Roadmap

## Phase 1 — Foundation ✅

- Philosophy
- Conceptual Model
- Registry Specifications
- Architecture Standard
- JSON Object Model

## Phase 2 — Reference Implementation (Current)

- Orchestrator
- Device Services
- Native Methods
- Android Runtime
- Intent execution

## Phase 3 — Proof of Architecture

Demonstrate that ResearchOS can:

1. Represent a complete research design.
2. Represent a complete research protocol.
3. Create, analyse and interpret a study dataset using only the ResearchOS graph.

Successful completion of these milestones will provide the first end-to-end validation of the ResearchOS architecture.

---

# Repository Structure

## Foundation

- Philosophy
- Conceptual Model

## Registry Specifications

- Entity Registry
- Observation Registry
- Assertion Registry
- Intent Registry
- Trait Registry

## Architecture

- Architecture Standard
- JSON Object Model

## Interoperability

- ResearchOS Intent Language
- Core Verbs

## Reference Implementation

- Android Runtime
- Orchestrator
- Device Services
- Native Methods

## Capability modules

Each standalone module under `app/src/main/java/com/example/researchos/modules/` owns both its implementation and its integration documentation. Its `docs` folder contains a module-named implementation guide and a working example ODK XLSForm. See [Capability documentation standard](docs/CAPABILITY_DOCUMENTATION_STANDARD.md).

The Android app inspector is a deliberately conservative interoperability tool. It lists installed launchable applications, inspects exported components, probes common public intent filters, targets exported activities explicitly, captures returned data, and can save a tested package/component/action/URI/extras combination as a local integration definition. It does not bypass non-exported components, permissions, authentication, or undocumented application internals. An app may expose more useful behavior than Android can discover generically; in that case the inspector is a test harness for combinations found in the app's documentation, source, or other authoritative references.

The Bluetooth device inspector applies the same pattern to nearby hardware: it scans BLE devices, connects to a selected device, enumerates GATT services and characteristics, reads endpoints or listens for notifications on request, identifies paired classic-Bluetooth serial candidates, and saves discovered profiles into the device registry. It is limited to normal Android Bluetooth permissions and explicit user-selected interactions.

## Current XLSForm capabilities

The Android reference implementation currently exposes the following standalone capabilities to ODK Collect, KoboToolbox-compatible callers, scheduled workflows, and other RIL clients. Each is invoked through the common `com.example.researchos.EXECUTE_METHOD(...)` intent boundary; the module owns its settings, UI, outputs, and example form.

- **Calibrated scale** (`calibrated_scale`) — presents a physically calibrated horizontal or vertical continuum, including single-value, minimum/maximum, and range modes. It returns selected values with the configured prompt, hint, labels, and measurement metadata.
- **Discrete choice experiments** — provides five reusable study tasks: pairwise comparison (`dce.pairwise`), MaxDiff/best-worst (`dce.maxdiff`), ranking (`dce.ranking`), points allocation (`dce.points`), and conjoint selection (`dce.conjoint`). Rounds, items, classes, profiles, points, and seeds can be supplied by the form or caller.
- **GPS target navigation** (`gps_target_navigator`) — guides a participant or operator toward a latitude/longitude target, reporting location, bearing, distance, arrival status, and optional camera-based AR guidance.
- **NFC tag read** (`nfc_tag_read`) — reads arbitrary NDEF records and tag metadata, including UID, technology, capacity, writability, text/URI records, and raw record JSON.
- **NFC tag write** (`nfc_tag_write`) — writes caller-supplied text, URI, JSON, or other NDEF content with explicit replace or blank-only policy and read-back verification.
- **NFC tag wipe** (`nfc_tag_wipe`) — removes user NDEF content from a tag and reports the resulting tag state.
- **NFC credential provisioning** (`nfc_credential_provisioning`) — creates a portable PIN-protected field-team credential, writes it to NFC, and verifies the write on a confirmation tap without returning the secret.
- **NFC credential verification** (`nfc_credential_verification`) — reads a portable NFC credential, requests its PIN, verifies the issuer signature and credential integrity, and returns a compact verification result.
- **Code scanner** (`qr.scan`) — captures QR, Data Matrix, and supported one-dimensional barcodes with automatic format detection, returning the decoded payload, format, and evidence hash.
- **Traceable attestation** (`attestation.create`) — creates a device-signed, hash-chained event attestation. It can invoke fingerprint/device credential, QR, NFC, or study-password verification and can optionally obtain an RFC 3161 trusted timestamp.
- **Attestation chain anchor** (`attestation.anchor_bundle`) — exports the current chain head and signed public evidence for an independent ODK/server receipt or nightly study anchor.
- **Local device authentication** (`admin_fingerprint_confirmation`) — performs standalone biometric, PIN, pattern, password, or combined device-credential authentication for access control; unlike formal attestation, it does not claim to prove a research event.
- **ODK form launcher** (`odk_form_launcher`) — discovers stored ODK/Kobo projects and forms, lets the operator select the correct project/form, and opens the selected form in the appropriate collection app.
- **Android app inspector** (`android_app_inspector`) — lists installed applications and exported public components, tests documented or user-supplied intent actions, captures returned data, and saves tested integration definitions. It does not bypass permissions or private components.
- **Bluetooth device inspector** (`bluetooth_device_inspector`) — scans nearby BLE devices, discovers and groups GATT services, reads endpoints, samples readable characteristics, subscribes to notification streams through CCCD, decodes printable values, and saves profiles to the device registry.

For implementation details, intent examples, input settings, output fields, and an example XLSForm, open the capability-specific guide in each module's `docs` folder. The naming and packaging rules are defined in [Capability documentation standard](docs/CAPABILITY_DOCUMENTATION_STANDARD.md).

## Scheduler and task orchestration

ResearchOS also includes a core scheduler for organising recurring field tasks. Schedules use five-field cron expressions and can run hourly, daily, weekly, monthly, or on any other supported cron pattern. A scheduled item may launch an XLSForm in ODK Collect or Kobo Collect, open a web form, invoke a ResearchOS capability directly, or publish capability output to a reusable destination such as the clipboard. Tasks can be chained so several actions run as one workflow, with configurable ordering, retries, retry windows, custom notifications, pause/resume controls, and completion tracking. The scheduler is part of the runtime workflow layer rather than a study-specific capability: future action types can be added without changing the scheduling model. Schedule chains can also be exported and imported as integrity-checked bundles through text, files, QR, or NFC.

Build and test the Android reference implementation with:

```text
./gradlew testDebugUnitTest assembleDebug
```

---

# Design Principles

ResearchOS is:

- Knowledge-first
- Registry-driven
- Service-oriented
- Technology-independent
- Extensible
- Interoperable
- Open by design

---

# Licence

ResearchOS is an open project intended to support reusable scientific infrastructure across disciplines, organisations and platforms.
### Using the scheduler

Open **Scheduler** on the ResearchOS home screen and choose **Create schedule**. Give the schedule a name, then configure each action in order: an XLSForm, web form, direct ResearchOS capability, or reusable output such as clipboard. XLSForm actions use the discovered project/form list; web-form actions use a URL; capability actions expose the selected capability's normal settings. Add further actions to create a chain; they run in displayed order after the preceding action succeeds.

Schedules use five cron fields: minute, hour, day of month, month, weekday. Examples are `0 9 * * *` (daily at 09:00), `0 * * * *` (hourly), `*/5 * * * *` (every five minutes), and `0 9 * * 1` (every Monday at 09:00). Configure notification text, retries, retry interval, and retry window before saving. Use **Test** to run immediately, or pause/resume the schedule from its central card.

Scheduled notifications launch the configured action directly; ODK/Kobo forms return to the scheduled workflow and chained actions continue automatically. Schedules can be edited, removed, or exported individually. Export supports copying, saving, sharing, and QR; import accepts pasted text or QR/NFC data and adds schedules without replacing unrelated schedules.
