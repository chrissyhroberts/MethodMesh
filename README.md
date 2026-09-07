# MethodMesh

**Do Stuff.**

MethodMesh is an offline-first Android capability runtime for fieldwork, research, data collection and practical device services.

It turns reusable phone and peripheral functions into **capabilities** that can be used directly by a person, saved as presets, composed into protocols, scheduled, or called from ODK/XLSForm and other supported Android workflows.

A capability should remain the same capability regardless of how it is reached.

```text
                    MethodMesh capability
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
     Native             Preset            Protocol
        │                  │                  │
        ├────────────── Schedule ─────────────┤
        │                                     │
        └──────────── ODK / XLSForm ──────────┘
```

MethodMesh is intended to make useful field operations simple while retaining structured outputs, provenance and repeatability underneath.

---

## Current project status

The repository has undergone a major consolidation and expansion.

Recent development includes:

- a substantial application UI refresh;
- many new and substantially revised modules;
- improved capability-specific native interfaces;
- stronger parity across native use, presets, protocols, schedules and ODK;
- the live-result → **Commit** lifecycle;
- expanded device and hardware support;
- richer output and provenance handling;
- online-data capabilities where network access is genuinely required;
- a dedicated Development lane for modules not yet formally promoted to Production;
- a major website/reference-library refresh;
- adoption of the **MethodMesh Master Book as the authoritative architecture document**.

A large number of modules are currently present in source or active development.

**Presence in the repository does not automatically mean Production status.**

Modules are promoted to Production after review of their complete capability contract.

---

# The capability is the unit

MethodMesh is built around reusable capability contracts rather than screens.

A capability owns its:

- canonical method ID;
- settings;
- runtime inputs;
- execution logic;
- native interface where required;
- result contract;
- ODK/XLSForm inputs and outputs;
- audit/provenance values;
- attachments;
- preset behaviour;
- protocol behaviour;
- lifecycle and completion behaviour.

The dashboard is only one way to interact with MethodMesh.

It must not become a second implementation of the capabilities underneath it.

---

# Capability surfaces

Canonical capabilities may be exposed through several surfaces.

## Direct native use

A user opens a capability and performs the operation directly.

Capabilities should use interfaces appropriate to the task.

A compass should look and behave like a compass.  
A scanner should behave like a scanner.  
A counter should behave like a counter.  
A navigator should behave like a navigation tool.

Generic configuration screens are useful where appropriate, but they are not a substitute for polished capability-specific interaction.

## Presets

Presets are saved configurations of canonical capabilities.

They can:

- fix known settings;
- expose selected values at runtime;
- remove repetitive setup;
- launch directly;
- form steps within protocols;
- be invoked by schedules.

Preset configuration should reuse capability-owned settings rather than introducing a separate implementation of the capability.

## Protocols

Protocols compose configured capabilities into multi-step native workflows.

They support coherent execution across several operations while retaining shared run identity, outputs and provenance.

## Schedules

Schedules allow capabilities, presets, protocols and supported external actions to run or notify at configured times.

MethodMesh scheduling is local-first and intended to remain useful in field environments with unreliable connectivity.

## ODK / XLSForm

ODK interoperability is a first-class MethodMesh contract.

A typical roundtrip is:

```text
ODK Collect
    ↓
launch MethodMesh method
    ↓
perform capability
    ↓
Commit / complete
    ↓
return values + attachments
    ↓
resume form
```

MethodMesh should not force an ODK-launched capability through unnecessary dashboard navigation or unrelated native screens.

---

# Live result → Commit

Many MethodMesh capabilities distinguish between a **working result** and the value that is finally returned or recorded.

The preferred lifecycle is:

```text
configure
    ↓
run
    ↓
live working result
    ↓
Commit
    ↓
return / save / continue
```

This is particularly important for field measurements, selections, observations and other operations where the first displayed value is not necessarily the final study value.

Completion behaviour must also respect launch origin.

A capability launched directly, from a protocol, from a schedule or from ODK may need to return somewhere different after completion.

---

# Results first

Native MethodMesh interfaces should show the useful result first.

Examples might include:

- a measurement;
- decoded barcode content;
- a location;
- a selected identifier;
- an image;
- a document;
- translated speech;
- a calculated value;
- a score;
- a navigation outcome.

Common result actions include:

- tap to copy;
- Copy;
- Share;
- Save;
- Save media;
- export structured/audit output;
- Commit;
- close or return to the calling workflow.

Structured JSON remains important for machines, provenance and downstream analysis, but verbose metadata should not dominate ordinary field use.

---

# Outputs and provenance

MethodMesh supports structured results alongside files and media.

Depending on the capability, an execution may produce:

- a compact primary result;
- structured JSON;
- audit/provenance metadata;
- configuration details;
- timestamps;
- device information;
- attachments;
- images;
- PDFs;
- other exported files.

Submission/run identifiers allow related results and attachments to remain linked.

The goal is to support both:

1. straightforward field interaction; and
2. reproducible, auditable downstream data handling.

---

# Production and Development

MethodMesh explicitly distinguishes between **implemented** and **Production-ready** functionality.

## Production

Production modules have completed the relevant review process for their capability type.

Review may include:

- native execution;
- UI quality;
- state preservation;
- configuration changes;
- presets;
- protocols;
- schedules;
- ODK launch;
- ODK result return;
- attachments;
- provenance;
- offline behaviour;
- physical-device testing;
- documentation;
- module ownership and contracts.

## Development

Development modules may already contain substantial working functionality.

They remain in Development while one or more areas are still being reviewed, hardened, physically tested, documented or brought into parity with the current architecture.

This lets MethodMesh grow rapidly without presenting experimental functionality as reviewed Production tooling.

---

# ODK and XLSForm

MethodMesh is designed to complement ODK rather than replace it.

ODK remains excellent at structured data collection and form workflow.

MethodMesh provides operations that are awkward, impossible or undesirable to implement directly inside a form.

Examples include capabilities involving:

- sensors;
- cameras;
- audio;
- navigation;
- Bluetooth;
- NFC;
- peripherals;
- complex interaction;
- local processing;
- document generation;
- external Android services.

Module-owned XLSForms provide examples, test harnesses and reusable design templates.

The refreshed website includes a reference library linking forms back to their owning modules and capabilities.

---

# Offline first

MethodMesh is designed for environments where connectivity may be unreliable, expensive or absent.

Where practical:

- computation happens locally;
- files remain local;
- device communication is local;
- downloaded models are reused;
- capabilities avoid unnecessary cloud dependencies;
- workflows should degrade gracefully when connectivity disappears.

Some capabilities legitimately use online data.

Those capabilities should declare that requirement explicitly rather than making connectivity an implicit dependency of unrelated MethodMesh functions.

---

# Devices and hardware

MethodMesh is not limited to phone-only operations.

Current and developing integrations include areas such as:

- camera and imaging;
- microphone and audio;
- GPS and orientation sensors;
- NFC;
- Bluetooth and BLE;
- Bluetooth printing;
- ESP32-based sensors;
- environmental sensing;
- presence and radar sensors;
- external Android applications;
- authentication services;
- other local peripherals.

Reusable hardware operations should be exposed through the same MethodMesh capability model wherever possible.

---

# Workbench

Engineering, inspection and development functions live in **Workbench** rather than being presented as ordinary study-facing capabilities.

Workbench may contain tools for:

- hardware provisioning;
- Bluetooth inspection;
- Android application inspection;
- API testing;
- sensor diagnostics;
- firmware installation;
- development-module testing;
- integration debugging.

This separation keeps engineering tools available without confusing them with reviewed Production capabilities.

---

# Documentation

MethodMesh now uses a clear documentation hierarchy.

## 1. MethodMesh Master Book

**The current MethodMesh Master Book is the authority document.**

It defines the canonical architecture, terminology, lifecycle, capability contracts and cross-system expectations.

If another document conflicts with the current Master Book, the Master Book takes precedence unless the discrepancy represents a deliberate newer architectural decision that has not yet been incorporated.

## 2. Module-owned documentation

Each module should keep its implementation documentation, examples, forms, validation information and other relevant references close to the module itself.

## 3. MethodMesh website

The `website/` tree provides the navigable current reference library for:

- architecture;
- modules;
- capabilities;
- method IDs;
- forms;
- presets;
- protocols;
- schedules;
- ODK;
- outputs;
- devices;
- development guidance;
- supporting module documentation.

The website is a discovery and reference surface.

The Master Book remains authoritative.

---

# Repository structure

The repository broadly follows this model:

```text
app/
  src/main/java/.../
    core/                 Shared runtime and contracts
    modules/              MethodMesh modules
    settings/             Shared settings/services
    transport/            External launch and result transport
    ui/                   Shared application UI

  src/main/assets/        Runtime assets and bundled resources

docs/                     Cross-project documentation

firmware/                 Supported peripheral firmware and tooling

scripts/                  Project utilities

website/                  MethodMesh reference website
```

Individual modules should own their capability implementation and associated supporting material wherever practical.

---

# Building MethodMesh

From the repository root:

```bash
./gradlew :app:assembleDebug
```

For a broader local test/build pass:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Individual modules may have additional validation, hardware or integration requirements documented alongside their source.

A successful Android build alone does **not** establish Production readiness.

---

# Contributing and module development

Before adding or substantially modifying a module, read the current **MethodMesh Master Book** and relevant module-development documentation.

New capabilities should avoid creating parallel implementations for different execution surfaces.

The preferred pattern is:

```text
one canonical capability
        ↓
multiple execution surfaces
```

not:

```text
dashboard version
preset version
protocol version
ODK version
```

Method identity and behaviour should remain coherent throughout the system.

---

# Release model

MethodMesh development is active and frequently involves multiple modules at once.

Repository releases therefore describe meaningful project baselines rather than implying that every internal development step has been published independently.

Release notes summarise major changes.

For exact current behaviour:

1. inspect the current source;
2. check module status and documentation;
3. use the current Master Book for architectural interpretation.

---

# Philosophy

MethodMesh exists to make phones and nearby hardware do useful things during real fieldwork.

The user should not need to care which internal subsystem produced the result.

Configure it.  
Run it.  
Get the result.  
Commit it.  
Move on.

**Do Stuff.**
