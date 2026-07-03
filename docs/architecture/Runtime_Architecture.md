# ResearchOS Architecture

ResearchOS is an AS1.00-aligned research execution runtime hosted inside an Android app. The Android package name currently remains `com.example.xlsformlab` for compatibility, but the architectural identity of the project is ResearchOS.

## Core idea

ResearchOS separates research behaviour into a small set of architectural concepts:

- **Methods**: executable research operations.
- **Execution Engine**: the canonical entry point for running methods.
- **Method Registry**: the catalogue of available methods.
- **Signals**: transient inputs from devices, users, transports or services.
- **Device Services**: Android or hardware adapters that produce signals or perform effects.
- **Knowledge Objects**: observations, entities, states, transformations and provenance records.
- **Presentation**: Compose screens and UI components that invoke methods but do not own method logic.

The target flow is:

```text
Presentation
  -> Execution Engine
  -> Method Registry
  -> Method
  -> Signals / Device Services
  -> Knowledge Objects / Method Output
```

## Current native methods

The following methods are now native ResearchOS / AS1.00 methods:

- NFC Read
- NFC Write
- Calibrated Scale
- GPS / Locate Target
- Verify Fingerprint / Device Credential

The current interactive feature set is now represented as native ResearchOS / AS1.00 methods. Some compatibility shells remain where the current UI and transport preview still depend on the legacy Method interface.

## Compatibility policy

The Android package name and intent package are `com.example.researchos`.aking installed app identity, ODK intent examples, shared preferences, backups, signing assumptions and future deep links.

ResearchOS appearance strings should use:

```text
researchos(method=<method_id>;return_mode=<mode>;...)
```

The parser still accepts the older `xlsformlab(...)` prefix as a compatibility alias.

## Naming policy

Use **Method** for executable research operations. Do not introduce new architectural classes named `Capability`.

Use **ResearchOS** for user-facing and documentation identity. Do not use XLSFormLab for new user-facing labels, schemas or provenance keys unless maintaining backwards compatibility.
