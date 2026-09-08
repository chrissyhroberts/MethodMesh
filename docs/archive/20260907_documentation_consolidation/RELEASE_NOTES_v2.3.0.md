# MethodMesh v2.3.0

MethodMesh v2.3.0 is a major consolidation release.

A substantial amount of MethodMesh development has taken place since the previous public repository updates, including changes across the application UI, capability runtime, module library, documentation, ODK integration, development tooling and project architecture.

This release therefore establishes a new current baseline rather than attempting to reproduce every intermediate development step as a separate release.

These notes summarise the major changes. They are intentionally not an exhaustive file-by-file changelog.

## Highlights

- Major refresh of the MethodMesh application UI and navigation.
- Significant expansion of the MethodMesh module and capability library.
- Clearer separation between **Production** capabilities and modules still under **Development** or review.
- Continued convergence around the canonical capability model across direct native use, presets, protocols, schedules and ODK/XLSForm.
- Refined native result handling and the live-result → **Commit** workflow.
- Major update of the MethodMesh website and reference library.
- The **MethodMesh Master Book** is now the authoritative project specification.
- Continued development of offline-first field tooling, device integration, sensors, media, spatial tools, data processing and workflow automation.

# Master Book is now authoritative

The most important documentation change in this release is that the **MethodMesh Master Book is now the authority document for MethodMesh architecture and behaviour**.

The Master Book defines the canonical system model, including:

- module ownership;
- capability contracts;
- method IDs;
- direct native execution;
- presets;
- protocols;
- schedules;
- widgets;
- ODK/XLSForm interoperability;
- result and output handling;
- provenance and audit data;
- lifecycle expectations;
- production-readiness expectations;
- documentation and module-review requirements.

Module documentation, source comments, the website and other guides remain important supporting material, but they should be interpreted against the current Master Book where there is ambiguity.

This change is intended to reduce architectural drift as MethodMesh grows.

# Application UI refresh

The Android application has undergone a substantial interface refresh.

The aim is to make MethodMesh behave like a coherent field-tool platform rather than a collection of capability test screens.

Changes across the current interface include:

- revised dashboard and navigation structure;
- cleaner MethodMesh visual identity;
- improved typography, spacing and field readability;
- more capability-specific interfaces where a generic form would be inappropriate;
- clearer separation between the tool itself and its configuration;
- more unified capability configuration and execution;
- improved result presentation;
- persistent working state where appropriate;
- tap-to-copy behaviour for useful field results;
- cleaner native sharing;
- better handling of media results;
- improved preset creation and editing;
- improved protocol and schedule execution;
- more deliberate close-out behaviour;
- additional confirmation and guard rails around destructive actions.

A central design principle is that a capability should remain the same capability regardless of how it is reached.

The dashboard is therefore **one interface to MethodMesh**, not the definition of MethodMesh.

# Canonical capability surfaces

MethodMesh capabilities are increasingly expected to maintain behavioural parity across the supported execution surfaces:

- direct native use;
- Dashboard;
- saved Presets;
- Protocols;
- Schedules;
- ODK/XLSForm calls;
- other supported external launches.

A capability may present differently on each surface, but its method identity, settings, execution semantics and returned values should remain coherent.

This is now a major part of module review.

# Live result and Commit lifecycle

The native execution model has been refined around a distinction between a **live working result** and a **committed result**.

Where appropriate, capabilities now allow the operator to interact with, inspect or refine a result before explicitly committing it.

This supports workflows in which the first measurement, selection, calculation or observation is not necessarily the final study value.

The intended lifecycle is increasingly:

```text
configure → run → live working result → Commit → return/export
```

The precise UI remains capability-specific.

External callers such as ODK must still receive deterministic completion and returned values without being forced through unnecessary native navigation.

# Module and capability expansion

The MethodMesh source tree has expanded substantially.

Many additional modules and capabilities are now present or under active development across areas including:

- measurement;
- navigation and spatial tools;
- environmental sensing;
- Bluetooth and BLE devices;
- ESP32 sensor nodes;
- NFC;
- imaging and image processing;
- document capture;
- barcode and machine-readable code handling;
- audio and acoustics;
- timing;
- psychomotor testing;
- sampling and randomisation;
- scoring systems;
- counters;
- text processing;
- networking;
- online data;
- statistics and field calculations;
- surveying;
- clinical and laboratory utilities;
- emergency field tools;
- communications;
- authentication;
- printing;
- external Android application integration;
- web actions;
- question primitives;
- protocol support tools.

This source expansion **does not mean that every module is now a Production capability**.

A significant number of these modules are deliberately in the Development lane while implementation, physical-device testing, preset behaviour, ODK roundtrip behaviour, state preservation, documentation and user-interface review are completed.

Modules will be formally promoted into Production as they pass the current review process.

# Production and Development lanes

MethodMesh now makes the distinction between implementation and production readiness more explicit.

A capability may exist in source and be useful for development without yet being suitable for deployment as a reviewed Production capability.

Production review considers the whole capability contract rather than simply whether the Kotlin code builds.

Depending on the capability, this includes:

- direct native execution;
- capability-specific UI quality;
- state preservation;
- preset configuration;
- preset execution;
- protocol use;
- scheduled use;
- ODK/XLSForm launch;
- ODK return values;
- compact primary results;
- audit/full JSON;
- attachment handling;
- orientation/configuration changes;
- physical-device behaviour;
- offline behaviour;
- documentation;
- module ownership;
- output and provenance behaviour.

The Development lane allows substantial new functionality to remain visible and testable without overstating its deployment status.

# Presets

Preset handling has continued to evolve from simple saved settings toward reusable configured capability instances.

Current work includes:

- creating presets from the capability's own configuration interface;
- using capability-owned typed setting metadata;
- distinguishing fixed preset values from values supplied at runtime;
- avoiding repeated configuration during ordinary execution;
- clearer preset naming and management;
- direct preset execution;
- use of presets as protocol steps;
- use of presets from schedules;
- preserving the underlying canonical method identity.

Preset configuration should not require a second, independently implemented version of a capability's settings.

# Protocols

Native protocols remain an important MethodMesh execution surface.

Protocols allow configured capability presets to be chained into reusable workflows without requiring ODK to orchestrate every operation.

Protocol handling has continued to improve around:

- ordered multi-step execution;
- shared run state;
- guided progression;
- coherent completion behaviour;
- common submission identity;
- combined output;
- linked attachments;
- final run-level result presentation;
- scheduler integration.

Protocol execution is intended to behave as one coherent workflow rather than a collection of unrelated capability launches.

# Schedules

The MethodMesh scheduler continues to support local recurring and delayed workflows.

Scheduled actions can include MethodMesh capabilities and protocols as well as supported external targets such as ODK/Kobo forms and web resources.

Scheduler development has included:

- chained actions;
- cron-style timing;
- notifications;
- retries;
- pause/resume;
- testing;
- import/export;
- preservation of configured capability settings;
- improved close-out of scheduled native results.

The scheduler remains local-first and is intended to support field workflows even where continuous network connectivity cannot be assumed.

# Results, sharing and outputs

Result handling has continued to move away from debug-style JSON presentation toward a layered result model.

For ordinary native use, MethodMesh should show the useful result first.

Examples include:

- a scanned code;
- a measurement;
- a selected identifier;
- a translated conversation;
- an image;
- a calculated value;
- a navigation result.

The full structured payload remains available where provenance, audit or machine processing requires it.

Native result surfaces increasingly support combinations of:

- tap to copy;
- copy;
- share;
- save;
- save media;
- export a complete audit package;
- Commit;
- close/return to the appropriate launch origin.

Verbose metadata should not obscure the field-facing result.

# Canonical output and provenance

MethodMesh retains the structured output model developed across the v2.1 and v2.2 releases.

Direct runs and protocols can produce coherent output packages containing canonical JSON plus linked attachments.

Submission-level identifiers provide a stable key across:

- the output record;
- protocol steps;
- attachments;
- exported filenames.

Audit and provenance information remain separate from the compact result where appropriate.

The intention is to support both:

1. simple human-facing field use; and
2. reproducible downstream data management and analysis.

# ODK and XLSForm integration

ODK interoperability remains a core MethodMesh contract rather than an optional integration layer.

Capabilities intended for ODK use should provide complete roundtrip behaviour:

```text
ODK/XLSForm
    ↓
launch MethodMesh capability
    ↓
perform operation
    ↓
Commit / complete
    ↓
return values and attachments
    ↓
resume the calling form
```

Current integration principles include:

- canonical method IDs;
- module-owned example XLSForms;
- capability-owned input and output contracts;
- compact main results;
- full JSON/audit information where requested;
- correct media return;
- correct launch-origin-aware completion;
- no unnecessary dashboard navigation during external calls.

ODK examples and forms are increasingly treated as first-class module resources rather than detached examples.

# ODK form library

The wider MethodMesh documentation model now recognises module-owned ODK/XLSForms as a library in their own right.

Forms can be associated with the modules and capabilities they exercise and used as:

- examples;
- test harnesses;
- design templates;
- deployment starting points.

Further tooling is being developed around ODK server integration and rapid deployment/testing of module-owned forms.

# Devices and hardware

MethodMesh continues to expand beyond phone-only capabilities.

Existing and developing device integration includes:

- NFC tags and portable credentials;
- Bluetooth and BLE inspection;
- Bluetooth printers;
- ESP32-C3 sensor nodes;
- environmental sensors;
- mmWave radar/presence sensing;
- device authentication;
- external Android applications;
- phone cameras;
- microphones;
- orientation and motion sensors;
- GPS and location services.

The general architectural direction is to expose reusable hardware functions as normal MethodMesh capabilities rather than embedding study-specific logic into device integrations.

# ESP32 and sensor framework

The ESP32 sensor framework remains an important development area.

MethodMesh can provision supported sensor-node firmware and communicate with BLE sensor nodes through a common framework.

Development has included:

- phone-driven firmware installation;
- bundled sensor images;
- BLE provisioning;
- sensor manifests;
- device registry integration;
- reusable sensor drivers;
- AHT20 temperature/humidity support;
- LD2410C mmWave radar/presence work;
- fresh-sample commands;
- diagnostic and decode information.

Sensor support remains modular so additional hardware profiles can be added without redesigning the Android-side framework.

# Online data

MethodMesh now includes a declared online-data framework for capabilities that legitimately need remote data.

The framework was introduced with support for auditable API GET requests, selected result fields, caching and Workbench testing.

Bundled integrations have included sources such as:

- Open-Meteo;
- GDACS;
- USGS;
- World Bank indicators;
- Frankfurter exchange rates;
- GBIF occurrence data.

The wider MethodMesh architecture nevertheless remains **offline-first**.

Online services should enhance a capability where appropriate rather than turning network access into an implicit requirement for unrelated functionality.

# Workbench and development tooling

Development and diagnostic functions increasingly live in a Workbench/tooling context rather than being presented as ordinary study-facing capabilities.

These include areas such as:

- Android application inspection;
- Bluetooth endpoint inspection;
- API testing;
- sensor provisioning and diagnostics;
- hardware integration testing.

This keeps powerful development tools available while maintaining a cleaner distinction between deployment-facing capabilities and engineering surfaces.

# Website refresh

The MethodMesh website has been substantially rewritten.

The previous site reflected a much smaller capability library and could no longer serve as an adequate description of the current system.

The refreshed website is intended to act as a navigable reference library covering:

- MethodMesh architecture;
- the canonical capability model;
- modules;
- capabilities and method IDs;
- Production and Development status;
- presets;
- protocols;
- schedules;
- widgets;
- ODK/XLSForm integration;
- forms;
- outputs and provenance;
- online data;
- devices;
- shared services;
- RIL;
- module authoring;
- testing and release expectations;
- module-owned supporting documentation.

The website is a derived reference and discovery surface.

The **Master Book remains authoritative**.

# Documentation model

Documentation is increasingly organised around module ownership.

A well-formed module should carry the material needed to understand and test it, including as appropriate:

- implementation documentation;
- canonical methods;
- settings and results;
- ODK examples;
- XLSForms;
- validation notes;
- limitations;
- roadmap/review notes.

This is intended to prevent the code, website, examples and architecture documentation from evolving independently.

# Architecture continuity

Despite the scale of this update, the central MethodMesh idea has not changed.

MethodMesh remains a modular field-capability runtime in which small, reusable operations can be invoked directly or composed into larger workflows.

Those workflows may be driven by:

- a person using MethodMesh directly;
- a saved preset;
- a native protocol;
- a schedule;
- an ODK/XLSForm;
- another supported Android integration.

The reusable capability is the stable unit beneath those interfaces.

# Migration and repository catch-up

This release also represents a substantial synchronization of the public repository with ongoing development work.

Not every included change corresponds to a separately published GitHub release or release-note file.

Accordingly:

- this release should be treated as the new repository baseline;
- earlier release notes remain useful historical records;
- source-level Development modules should not be assumed to have Production status;
- current architectural questions should be resolved against the current Master Book;
- capability status should be taken from the current reviewed module metadata/documentation rather than inferred from when a source file first appeared.

# Validation

Individual modules have been built and tested throughout development, including a mixture of:

- Android debug builds;
- unit tests;
- module-specific contract tests;
- ODK/XLSForm tests;
- physical-device testing;
- manual UI review.

Because this release consolidates a large body of development work, the presence of a module in the repository does not itself constitute a claim that all Production review checks have been completed.

Production status remains explicit.

# Summary

MethodMesh v2.3.0 establishes the current project baseline after a substantial period of development.

The key changes are not any single new capability, but the maturation of MethodMesh as a system:

- a much broader module ecosystem;
- a cleaner and more field-oriented interface;
- increasingly consistent behaviour across all capability surfaces;
- stronger native workflow composition;
- explicit Production versus Development status;
- deeper ODK interoperability;
- richer output and provenance handling;
- expanding hardware and online-data support;
- a comprehensive new website/reference library;
- and a single authoritative architectural specification in the MethodMesh Master Book.

Development will continue module by module, with additional capabilities promoted to Production as they complete formal review.
