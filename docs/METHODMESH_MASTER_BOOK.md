---
title: "MethodMesh Master Book"
subtitle: "Canonical architecture, capability runtime, integration, UX and review standard"
date: "2026-09-07"
---

Version: v1.06
Status: FINAL - canonical project-wide documentation
Last updated: 2026-09-07
Authority: sole normative project-wide MethodMesh documentation resource

This edition consolidates the previously separate Master Book, architecture and conceptual specifications, capability-writing guidance, module-review manual, UI/UX standards, ODK/XLSForm integration guidance, provider notes, scheduling guidance, testing guidance and current project-wide implementation doctrine into one resource.

Where older project-wide MethodMesh documents conflict with this book, this book takes precedence. Older documents may be retained in the repository archive for provenance and history, but they are not active authorities.

# Contents

- [1. MethodMesh in one page](#1-methodmesh-in-one-page)
- [2. Authority, normative language and documentation governance](#2-authority-normative-language-and-documentation-governance)
  - [2.1 Sole project-wide authority](#21-sole-project-wide-authority)
  - [2.2 Normative language](#22-normative-language)
  - [2.3 Authority below the Master Book](#23-authority-below-the-master-book)
  - [2.4 Documentation lifecycle](#24-documentation-lifecycle)
- [3. Identity and product character](#3-identity-and-product-character)
- [4. Top-level app model](#4-top-level-app-model)
  - [Dashboard](#dashboard)
  - [Presets](#presets)
  - [Protocols](#protocols)
  - [Capabilities](#capabilities)
  - [ODK Forms](#odk-forms)
  - [Workbench](#workbench)
  - [Device registry](#device-registry)
  - [Settings](#settings)
  - [Widgets](#widgets)
- [4A. Getting started and installation](#4a-getting-started-and-installation)
  - [Choose an entry point](#choose-an-entry-point)
  - [A normal native run](#a-normal-native-run)
  - [Build from source](#build-from-source)
  - [Development-device smoke check](#development-device-smoke-check)
- [5. The golden rule](#5-the-golden-rule)
- [5A. Conceptual and architectural foundation](#5a-conceptual-and-architectural-foundation)
  - [Scientific model](#scientific-model)
  - [Conceptual layers](#conceptual-layers)
  - [Architectural principles](#architectural-principles)
  - [Core implementation components](#core-implementation-components)
- [6. Capability module contract](#6-capability-module-contract)
  - [Auto-discovery](#auto-discovery)
  - [Module-owned metadata](#module-owned-metadata)
  - [Canonical capability contract](#canonical-capability-contract)
  - [Icon key](#icon-key)
- [7. Capability lanes](#7-capability-lanes)
  - [Incoming prototype](#incoming-prototype)
  - [Development](#development)
  - [Production](#production)
  - [Workbench](#workbench)
- [8. Settings and runtime inputs](#8-settings-and-runtime-inputs)
  - [Fixed, runtime and operational inputs](#fixed-runtime-and-operational-inputs)
  - [Preset authoring](#preset-authoring)
- [9. Native run UX](#9-native-run-ux)
  - [Capability-owned, relevant UI](#capability-owned-relevant-ui)
  - [Live current result](#live-current-result)
  - [Tap-to-copy invariant](#tap-to-copy-invariant)
  - [Commit is the finalisation boundary](#commit-is-the-finalisation-boundary)
  - [Sharing and saving after Commit](#sharing-and-saving-after-commit)
  - [Origin-aware Home, Done and closeout](#origin-aware-home-done-and-closeout)
  - [Native run requirements](#native-run-requirements)
  - [Destructive actions](#destructive-actions)
- [10. ODK/XLSForm contract](#10-odkxlsform-contract)
  - [ODK capability parity](#odk-capability-parity)
  - [Namespaces](#namespaces)
  - [Flat + full JSON](#flat-full-json)
  - [Binary artefacts](#binary-artefacts)
  - [No MethodMesh storage on ODK return](#no-methodmesh-storage-on-odk-return)
  - [ODK Forms library ownership and discovery](#odk-forms-library-ownership-and-discovery)
  - [ODK Central rapid-test deployment](#odk-central-rapid-test-deployment)
  - [Kobo rapid-test deployment](#kobo-rapid-test-deployment)
  - [XLSForm batch validation](#xlsform-batch-validation)
  - [Deployment diagnostics](#deployment-diagnostics)
- [11. Output contract](#11-output-contract)
  - [Cross-surface output invariant](#cross-surface-output-invariant)
  - [Core result](#core-result)
  - [Audit/ALCOA fields](#auditalcoa-fields)
  - [Full JSON](#full-json)
  - [Hashes](#hashes)
- [12. Capability closeout, protocols and schedules](#12-capability-closeout-protocols-and-schedules)
  - [Single native preset](#single-native-preset)
  - [Protocol step](#protocol-step)
  - [Final protocol result](#final-protocol-result)
  - [Schedules](#schedules)
  - [Scheduler shared-runtime contract](#scheduler-shared-runtime-contract)
- [13. Presets, protocols and pipes](#13-presets-protocols-and-pipes)
- [14. Online data and APIs](#14-online-data-and-apis)
  - [Bundled APIs](#bundled-apis)
  - [Location privacy](#location-privacy)
  - [Credentials](#credentials)
  - [RSS/Atom](#rssatom)
- [15. Offline-first rule](#15-offline-first-rule)
  - [Downloaded offline resources versus disposable cache](#downloaded-offline-resources-versus-disposable-cache)
- [16. Shared device services](#16-shared-device-services)
  - [ML Kit language packs](#ml-kit-language-packs)
  - [Future map tiles](#future-map-tiles)
- [16A. Shared Android capability surfaces](#16a-shared-android-capability-surfaces)
- [17. Location tools](#17-location-tools)
  - [Plus Code capture](#plus-code-capture)
  - [GPS target navigator](#gps-target-navigator)
- [18. Sensors and hardware](#18-sensors-and-hardware)
  - [Device registry target model](#device-registry-target-model)
- [19. Attestation and audit commitments](#19-attestation-and-audit-commitments)
  - [attestation.create](#attestationcreate)
  - [Commitment recipe](#commitment-recipe)
  - [Trusted timestamp](#trusted-timestamp)
- [20. Documentation rules](#20-documentation-rules)
  - [Example XLSForms](#example-xlsforms)
- [20A. Module Review and Refresh Standard](#20a-module-review-and-refresh-standard)
  - [Review input](#review-input)
  - [Preserve established contracts](#preserve-established-contracts)
  - [Capability inventory](#capability-inventory)
  - [Canonical surface parity review](#canonical-surface-parity-review)
  - [Native UX review](#native-ux-review)
  - [Working result and Commit review](#working-result-and-commit-review)
  - [Tap-to-copy review](#tap-to-copy-review)
  - [Preset review](#preset-review)
  - [Protocol review](#protocol-review)
  - [ODK/XLSForm review](#odkxlsform-review)
  - [Widget/schedule review](#widgetschedule-review)
  - [Media/maps review](#mediamaps-review)
  - [Persistent-state review](#persistent-state-review)
  - [Module-local ownership review](#module-local-ownership-review)
  - [Shared-framework changes](#shared-framework-changes)
  - [Handoff structure](#handoff-structure)
  - [Review severity](#review-severity)
  - [Review passes](#review-passes)
  - [Documentation update during review](#documentation-update-during-review)
  - [Example XLSForms during review](#example-xlsforms-during-review)
  - [Do not over-redesign](#do-not-over-redesign)
  - [Definition of Done](#definition-of-done)
  - [Required final self-review](#required-final-self-review)
  - [Required module-review response format](#required-module-review-response-format)
- [21. Testing rules](#21-testing-rules)
  - [Cross-surface parity tests](#cross-surface-parity-tests)
  - [XLSForm validation tests](#xlsform-validation-tests)
- [22. Build, release and git hygiene](#22-build-release-and-git-hygiene)
  - [Dirty worktree rule](#dirty-worktree-rule)
  - [Destructive cleanup](#destructive-cleanup)
- [23. Third-party and attribution rules](#23-third-party-and-attribution-rules)
- [24. Implementation status and roadmap](#24-implementation-status-and-roadmap)
  - [24.1 Status is dynamic](#241-status-is-dynamic)
  - [24.2 Project-wide roadmap directions](#242-project-wide-roadmap-directions)
  - [24.3 Capability packaging direction](#243-capability-packaging-direction)
  - [24.4 Known cross-project implementation gaps](#244-known-cross-project-implementation-gaps)
- [25. Troubleshooting and diagnostic reasoning](#25-troubleshooting-and-diagnostic-reasoning)
  - [A capability exists but is missing from a surface](#a-capability-exists-but-is-missing-from-a-surface)
  - [ODK returns the wrong fields](#odk-returns-the-wrong-fields)
  - [An XLSForm will not validate/upload](#an-xlsform-will-not-validateupload)
  - [Binary output cannot be opened](#binary-output-cannot-be-opened)
  - [Done goes to the wrong place](#done-goes-to-the-wrong-place)
  - [A module is present in source but not Production](#a-module-is-present-in-source-but-not-production)
  - [Documentation conflicts](#documentation-conflicts)
- [26. Contributor/AI-chat quick rules](#26-contributorai-chat-quick-rules)
- [27. The MethodMesh taste test](#27-the-methodmesh-taste-test)
- [28. Normative requirement register](#28-normative-requirement-register)
  - [Documentation](#documentation)
  - [Architecture](#architecture)
  - [Capability](#capability)
  - [Surfaces](#surfaces)
  - [UX](#ux)
  - [Safety](#safety)
  - [ODK](#odk)
  - [XLSForm](#xlsform)
  - [Outputs](#outputs)
  - [Offline](#offline)
  - [Online data](#online-data)
  - [Android surfaces](#android-surfaces)
  - [Protocols](#protocols)
  - [Scheduling](#scheduling)
  - [Modules](#modules)
  - [Review](#review)
  - [Testing](#testing)
- [29. Source-specification reconciliation notes](#29-source-specification-reconciliation-notes)
- [30. Absorbed conceptual and interoperability specifications](#30-absorbed-conceptual-and-interoperability-specifications)
- [Appendix A. MethodMesh Philosophy (absorbed source v0.01)](#appendix-a-methodmesh-philosophy-absorbed-source-v001)
- [Appendix B. MethodMesh Conceptual Model (absorbed source v0.03)](#appendix-b-methodmesh-conceptual-model-absorbed-source-v003)
- [Appendix C. MethodMesh Architecture Standard (absorbed source v1.02)](#appendix-c-methodmesh-architecture-standard-absorbed-source-v102)
- [Appendix D. Canonical JSON Object Model (absorbed current source labelled v0.02)](#appendix-d-canonical-json-object-model-absorbed-current-source-labelled-v002)
- [Appendix E. Research Intent Language - RIL (absorbed source v0.03)](#appendix-e-research-intent-language-ril-absorbed-source-v003)
- [Appendix F. RIL Core Verbs (absorbed source v0.02)](#appendix-f-ril-core-verbs-absorbed-source-v002)
- [Appendix G. Entity Registry (absorbed source v0.02)](#appendix-g-entity-registry-absorbed-source-v002)
- [Appendix H. Observation Registry (absorbed source v0.01)](#appendix-h-observation-registry-absorbed-source-v001)
- [Appendix I. Assertion Registry (absorbed source v0.02)](#appendix-i-assertion-registry-absorbed-source-v002)
- [Appendix J. Intent Registry (absorbed source v0.02)](#appendix-j-intent-registry-absorbed-source-v002)
- [Appendix K. Trait Registry status](#appendix-k-trait-registry-status)
- [Appendix L. v1.06 documentation consolidation record](#appendix-l-v106-documentation-consolidation-record)
- [Appendix M. Version history](#appendix-m-version-history)

# 1. MethodMesh in one page

MethodMesh is an offline-first Android toolbox and capability runtime.

It has two jobs:

1. Let a person do useful field tasks directly on a phone.

2. Let external systems such as ODK/XLSForm, presets, protocols, schedules and widgets

call those same tasks through a consistent capability contract.

The product rule is:

Do stuff. Return the beef, keep the salad available when explicitly
wanted.

“Beef” means the main useful result:

barcode text; translated text; redacted image; searchable PDF and OCR
text; Plus Code; temperature/humidity; navigation result; selected scale
value; signed attestation hash.

“Salad” means metadata, provenance, diagnostics, raw JSON, manifests,
traces, internal IDs and audit fields. These matter, but they should not
dominate the everyday native UI.

# 2. Authority, normative language and documentation governance

## 2.1 Sole project-wide authority

**MM-DOC-001.** This Master Book is the sole normative project-wide documentation resource for MethodMesh.

Project-wide architecture, UX, integration, transport, ODK/XLSForm, module-authoring, module-review, testing, release and interoperability doctrine belongs here. Do not create a new standalone project-wide manual when the material can be maintained in this book.

The maintained documentation model is:

```text
README.md
    -> short project introduction and pointer to this book

docs/MethodMesh_Master_Book.md
    -> all normative project-wide doctrine

app/src/main/java/com/example/methodmesh/modules/<module>/docs/
    -> module-specific implementation docs, validation notes and XLSForms

docs/archive/
    -> superseded/historical project-wide material
```

Module-local documentation is authoritative only for facts specific to that module: its method IDs, settings, outputs, hardware requirements, examples, validation state and implementation notes. Module docs do not override this book's cross-project architecture.

Generated HTML, documentation-site pages, packaged runtime assets, copied reference libraries and build outputs are projections, not independent sources of truth.

## 2.2 Normative language

Within this book:

- **MUST / MUST NOT** identifies a conformance requirement.
- **SHOULD / SHOULD NOT** identifies a strong default that may be departed from for a documented reason.
- **MAY** identifies an optional pattern.
- Sections explicitly labelled **Informative**, **Roadmap** or **Open question** are not conformance requirements.

Requirement IDs such as `MM-CAP-003` are stable references for reviews, tests and migration reports. The prose remains normative even where an individual sentence has not been assigned an ID.

## 2.3 Authority below the Master Book

When implementation evidence conflicts with this book, treat the discrepancy as a defect or a proposed architectural change; do not silently redefine the standard from current behaviour.

For module-local facts, use this order:

1. this Master Book for project-wide rules;
2. current source and tests for implementation facts;
3. the owning module's current `docs/` material;
4. release notes and roadmap notes as historical/status context;
5. archived specifications and prototype handoff notes as historical context only.

A newer deliberate architectural decision should be incorporated into this book before it becomes the new project-wide standard.

## 2.4 Documentation lifecycle

**MM-DOC-002.** Every documentation-like artefact has one conceptual home:

- **A - project-wide:** useful normative content belongs in this Master Book;
- **B - module-owned:** it belongs under the owning module's `docs/` directory;
- **C - archive/legacy/irrelevant projection:** it is historical, superseded, duplicated, generated, runtime-projected or otherwise not an active documentation authority.

Class C does not mean "delete the file". Runtime assets and generated outputs may need to remain physically present. Classification and filesystem disposition are separate decisions.

**MM-DOC-003.** Project-wide updates modify this book. Module-specific updates modify the owning module's docs. Superseded project-wide material is archived rather than kept as a competing standard.

# 3. Identity and product character

MethodMesh should feel:

practical; minimal; focused; modular; reliable; field-ready.

The visual identity is:

MethodMesh; “Do Stuff”; clean cards; restrained typography;

one accent colour; clear MethodMesh-styled icons; no unnecessary
verbosity.

The native app should feel like a toolbox, not a database management
platform.

When run natively, a user often wants to:

scan something; translate something; redact and share a photo; capture a
location; read a sensor; toss a coin; launch a preset from a widget; get
a result and leave.

Do not force database/storage/audit concepts into that path unless the
user asks for them.

# 4. Top-level app model

The app is organised around:

Dashboard; Capabilities; Presets; Protocols; ODK Forms; Workbench; Device registry; Settings; Widgets.

## Dashboard

### Dashboard result interaction

The dashboard is the operational control centre for MethodMesh. It
should make useful outputs immediately actionable while still providing
fast access to the underlying capability when more control is needed.

Any dashboard value that is meaningfully calculated or returnable should
support direct tap-to-copy. The clipboard payload is the primary useful
value — the beef — rather than the card label, field name or decorative
presentation.

- Plus Code: copy only the Plus Code, not text such as “Plus Code:
  8FVC9G8F+6W”.

- Temperature, dew point, humidity, distance and similar scalar results:
  tapping the displayed result copies the useful value.

- Where a result naturally supports Android sharing, provide a nearby
  share action without forcing the user through a verbose details
  screen.

Labels, units, provenance and contextual information may remain visible,
but they should not contaminate the primary clipboard return unless the
capability explicitly defines them as part of the useful result.

### Media, maps and visual outputs

Dashboard components that display an image, map, rendered document,
chart or other visual artefact should expose a small, visually
restrained action beside the component for sharing and, where
appropriate, saving to the device.

Maps and other spatial views must be smoothly pan- and zoom-friendly and
should open into a dedicated full-screen view. Returning from full
screen should preserve dashboard state.

For map-backed capabilities, the dashboard may provide the compact
operational view, current result and common controls while full-screen
mode provides the larger manipulation surface. The shared dashboard must
consume generic capability-declared result and action contracts rather
than learning Plus Code-, navigation- or provider-specific behaviour.

### Dashboard as module control centre

To the extent sensible and feasible, a dashboard component should act as
the control centre for its module rather than merely a passive summary.
It may expose capability-declared quick actions, current state,
refresh/run controls, preset entry points, active schedule controls and
recent useful results. Deeper configuration should open the capability
or appropriate top-level page rather than expanding the dashboard into a
long settings form.

The dashboard remains a dashboard, not a long scrolling substitute for
real top-level pages. Prefer compact cards, progressive disclosure and
direct manipulation. A tired fieldworker should be able to see the
state, obtain or copy the useful result, share an artefact, rerun or
refresh where appropriate, or open the full capability with minimal
navigation.

### Dashboard polish requirement

Dashboard interaction quality is a product requirement. Avoid clunky
controls, oversized button rows, duplicated labels, modal churn,
unnecessary confirmation, raw JSON, dense metadata and inconsistent
interaction patterns. Touch targets must remain accessible, but controls
should be visually quiet. Transitions into full-screen maps or media and
back should preserve state and feel deliberate.

Preferred hierarchy: result first; tap-to-copy or immediate primary
action; compact share/save control where applicable; quick operational
controls where useful; full capability/details on demand;
audit/provenance only when explicitly opened or exported.

The dashboard is for shortcuts and current activity:

MethodMesh header/logo; find a capability; find a preset; common tasks;
active schedules with pause/resume toggles; recent useful activity where
appropriate.

The dashboard is not a long scrolling substitute for real top-level
pages.

## Presets

Presets are saved capability setups.

A preset stores configuration choices and runtime-input policy. It
should not store accidental user text or run-specific values unless that
is genuinely the intended fixed value.

## Protocols

Protocols are chains of presets/capabilities.

They need rails. A user should understand:

current step; previous step outcome; next step; whether they need to do
anything; how to retry, cancel or continue; final combined result.

## Capabilities

### One capability contract, multiple interaction surfaces

The dashboard is mandatory, but it is only one interaction surface. It
must never become the implementation boundary for a capability.

Every MethodMesh capability is defined once by a canonical module-owned
contract. Direct native runs, the dashboard, preset creation, protocol
creation, schedules/widgets where applicable, and ODK/XLSForm are
adapters or projections over that same contract. They are not separate
implementations and they must not drift into different feature sets.

The invariant is: if MethodMesh can perform an operation or produce a
declared output, that operation and output remain addressable through
the canonical capability contract regardless of which surface invokes
it.

### Required surface parity

Every module must provide a useful dashboard presence appropriate to the
module. The dashboard may aggregate, summarise and expose quick
controls, but dashboard aggregation must never hide or replace the
individual capabilities.

Every individual capability must remain independently discoverable and
selectable when creating presets and protocols. A module with a rich
dashboard is not exempt from exposing its underlying primitives for
composition.

ODK/XLSForm must be able to invoke every capability and request every
declared output that MethodMesh itself can produce, including obscure or
rarely used outputs. Example XLSForms may demonstrate only common
outputs; the transport contract must not be limited to the examples.

Transport-specific presentation is allowed. For example, a dashboard may
render a map, a preset may ask for runtime inputs, and ODK may use
namespaced return fields and URI grants. The underlying method ID, input
semantics, output field names, types and meanings remain canonical.

### Forbidden architectural shortcuts

Do not create dashboard-only capability logic or dashboard-only outputs.
Do not create preset/protocol-only functionality that ODK cannot invoke.
Do not maintain a second output schema for ODK. Do not reimplement
capability behaviour separately in each surface.

Capabilities are production-ready primitives for real use and the
canonical units of invocation and composition.

Every individual capability must remain exposed and coherent when run:

directly; as presets; in protocols; in schedules; from widgets; from
ODK/XLSForm.


### Minimal shell and discovery

The shared shell is deliberately quieter than capability screens: **minimal shell, maximal capability**.

The Dashboard should normally contain the MethodMesh hero identity, the small product line **Do Stuff**, one plain-language search entry such as **What do you want to do?**, ranked results across capabilities/presets/protocols/ODK Forms, and current operational state only when there is something worth showing.

Do not duplicate the sidebar/library hierarchy on the Dashboard merely to fill space.

Search must work offline. A deterministic local search/synonym layer is the baseline. Module-owned discovery metadata such as verbs, tags, accepted inputs and produced outputs may improve ranking. A future local semantic layer may enhance discovery, but cloud/LLM availability must not be required to find a capability.

### Back navigation

Android Back from a nested flow unwinds that flow first.

Android Back from a top-level section other than Dashboard returns to Dashboard.

Android Back from Dashboard may exit to the Android desktop normally.

## ODK Forms

ODK Forms is a first-class top-level MethodMesh surface.

It is a searchable library of module-owned XLSForm design templates and mirrors module organisation in the same way as Capabilities and Presets.

A module may own zero, one or many XLSForms. Form count does not have to match capability count.

The source of truth is the module's own `docs/` directory. Canonical example names use `example_odk_<purpose>.xlsx`; discovery must flag rather than silently hide structurally valid XLSForms with legacy/non-standard names.

Each library row may expose Save/export, Share, validation status, independent ODK Central deployment/access state and independent Kobo deployment state.

Local design state, remote server deployment state, user access state and collector-device state are distinct and must never be presented as if they are the same thing.

## Workbench

Workbench is for development, device, inspector and “hackerish” tools.
These may be very useful but are not usually things a field user saves
as a normal research preset.

Examples:

ESP32 firmware installation; BLE/Bluetooth inspection;

Android app inspection; API definition editing and testing; hardware
diagnostics; prototype/development capabilities.

## Device registry

The device registry should eventually be a live useful view, not only a
static list.

For sensors it should support refreshing and showing current key values,
such as:

AHT20: temperature and humidity; LD2410C/LR radar: presence, target
state, distances and energy values.

## Settings

Settings is for shared services and app-level controls, not
capability-specific special cases.

Examples:

downloaded ML Kit language packs; output/export preferences; future
offline map tile packs; shared device/service controls.

## Widgets

Android desktop widgets should support:

1×1 preset trigger; 1×1 protocol trigger; 1×1 schedule toggle.

Preset/protocol widgets run the thing. Schedule widgets toggle on/off.

Widget launch is an origin, not a different capability implementation. The same canonical capability contract is used, but closeout is origin-aware.

For one-shot widget launches, successful commit/completion should finish cleanly and return to the Android desktop. Do not strand the user inside MethodMesh navigation, a generic result page, or a preset-launch screen. Cancel should also unwind cleanly to the desktop unless the capability must surface a blocking error.

If the widget launches an interactive capability, the capability may open its normal polished native screen. The screen should preserve the widget launch origin so Home/Done/closeout returns to the Android desktop rather than the MethodMesh dashboard.

# 4A. Getting started and installation

This section is operational guidance rather than a substitute for the capability contracts later in the book.

## Choose an entry point

| Need | Best starting point |
|---|---|
| Run one task now | **Capabilities** or a dashboard quick action |
| Reuse the same setup | **Preset** |
| Chain several steps with rails | **Protocol** |
| Browse/test module-owned forms | **ODK Forms** |
| Run/toggle something from the Android desktop | **Widget** |
| Trigger work on a cadence | **Schedule** |
| Call a capability from a research form | **ODK/XLSForm** |
| Inspect hardware, APIs or prototypes | **Workbench** |
| Manage shared app-level services | **Settings** |

## A normal native run

1. Open the relevant capability or preset.
2. Configure only settings that matter for the run.
3. Perform the task in a capability-specific screen.
4. Observe the live current result in place.
5. Tap useful displayed scalar/text results to copy them.
6. Press **Commit** when the working result is the result to finalise.
7. Use post-commit copy/share/save/full-JSON actions where exposed.
8. Press **Done/Home**; MethodMesh routes closeout according to launch origin.

The live result is working state. Commit freezes the canonical payload. A generic result screen MUST NOT be inserted merely for framework convenience.

## Build from source

For development/testing, build from a trusted checkout of the current repository. Let Gradle resolve the project before treating dependency-resolution failures as capability defects.

Standard debug build:

```bash
./gradlew :app:assembleDebug
```

Test plus build:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is normally under `app/build/outputs/apk/debug/`.

Install to a development device with Android Studio or ADB:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Only approve USB debugging on trusted computers. Replacing an app signed with a different key may require uninstalling the previous build, which removes its local app data.

## Development-device smoke check

After installation:

1. confirm the Dashboard loads;
2. open the capability catalogue;
3. grant only permissions required by the capability under test;
4. run at least one direct native capability;
5. exercise a representative preset/protocol where relevant;
6. test at least one ODK -> MethodMesh -> ODK roundtrip;
7. repeat relevant tests in airplane mode where offline behaviour is expected.

A successful Android build is necessary but does not establish Production readiness.

# 5. The golden rule

**MM-ARCH-001.** The shared MethodMesh UI knows nothing about individual capabilities.

This is the most important engineering rule.

The core app may know about:

capability discovery; generic settings rendering; preset creation;
runtime inputs; result actions; ODK/external transport; protocols;
schedules; widgets; shared services; broad categories and icon keys.

The core app must not learn bespoke facts such as:

barcode scanners have format choices; image redaction has grid rows;
printers need paper width; translation has source and target language;
Plus Code capture has satellite mode; sensors have AHT20 or LD2410C
fields.

Those belong inside the capability module.

If the shared UI needs to change, ask:

Is this a generic framework improvement that helps all capabilities?

If yes, it belongs in shared code. If no, keep it in the module.

# 5A. Conceptual and architectural foundation

MethodMesh has an Android toolbox implementation, but its conceptual model is implementation-independent.

## Scientific model

The foundational relationship is:

```text
Entity
  <- described by Assertion
  <- may be supported by Observation
  <- produced by Method

Intent -> requests Method
Policy -> constrains Method
Provenance -> explains Observation / Assertion lineage
Signal -> communicates change
```

- **Entity:** a distinct thing that exists, has existed, may exist, or may be conceived and can become the subject of research.
- **Assertion:** a timestamped claim describing a characteristic, state or relationship concerning one or more Entities.
- **Observation:** timestamped evidence obtained through observation, measurement, computation or simulation concerning one or more Entities.
- **Method:** a repeatable procedure used to obtain Observations.
- **Intent:** a declarative request for one or more actions; it expresses what is requested rather than how it is performed.
- **Policy:** constraints governing execution or behaviour.
- **Provenance:** origin, lineage and history sufficient to interpret and reproduce evidence/results.
- **Signal:** a notification of change to another component.

**MM-ARCH-002.** Implementations MUST preserve the distinction between reality/entities, evidence/observations, knowledge/assertions and orchestration/intents/methods. UI or transport convenience MUST NOT redefine conceptual meaning.

## Conceptual layers

The current conceptual model distinguishes:

1. **Reality** - Entities.
2. **Knowledge** - Assertions, supported by Observations.
3. **Acquisition** - Methods obtain Observations.
4. **Orchestration** - Intent, Policy and Signals coordinate work.
5. **Trust** - Provenance supplies traceability and reproducibility.

## Architectural principles

**MM-ARCH-003.** MethodMesh is knowledge-first: applications and services exist to create, transform, query or communicate evidence/knowledge without redefining the core concepts.

**MM-ARCH-004.** Separation of concerns is mandatory between knowledge, orchestration and implementation layers.

**MM-ARCH-005.** Capability discovery and canonical vocabulary are registry/metadata driven; shared surfaces SHOULD discover capabilities dynamically rather than rely on hand-written capability lists.

**MM-ARCH-006.** Public contracts SHOULD evolve through extension rather than casual breaking changes.

**MM-ARCH-007.** The conceptual architecture is technology-independent. Android/Kotlin, storage engines, APIs and deployment models are implementation choices.

The earlier Architecture Standard described capabilities as independently deployable services "where practical". In the current Android application many services are in-process module implementations. Independent deployment is an extension direction, not a requirement that every Android module be a separately installed service.

## Core implementation components

MethodMesh uses the following conceptual component types:

- **Applications** - user-facing interaction and orchestration;
- **Services/capabilities** - discrete reusable operations;
- **Methods** - repeatable procedures capable of fulfilling Intents;
- **Registries** - canonical vocabulary/metadata;
- **Knowledge store** - optional persistence of canonical objects/relationships;
- **Interfaces/transports** - Android intents, APIs, files, message-oriented transports or other mechanisms that preserve canonical semantics.

The Android runtime maps these principles into discovered `MethodMeshModule` objects, canonical `As100Method` implementations, shared transport/orchestration and capability-owned UI.

# 6. Capability module contract

Every capability lives under:

app/src/main/java/com/example/methodmesh/modules/\<module_name\>/

The canonical handoff unit is one folder.

Returned module designs and implementation handoffs must contain the
module folder itself, not a reconstructed tree for the whole MethodMesh
app or repository. The receiving chat or Work-mode review should be able
to drop, inspect or adapt that one folder without separating it from
synthetic app scaffolding.

Do not reproduce app/, project-level Gradle files, HomeScreen, shared
navigation, repository root files or other unrelated trees merely to
show where the module would live. If a genuine shared-framework change
is required, describe it explicitly as a separate integration
requirement; do not smuggle it into the returned module tree.

### Canonical returned folder structure

The returned artefact must have **exactly one top-level folder**, named
for the module:

```text
<module_name>/
|-- docs/
|   |-- README_<CapabilityA>.md
|   |-- example_odk_<capability_a>.xlsx
|   |-- README_<CapabilityB>.md              # if the module exposes another capability
|   |-- example_odk_<capability_b>.xlsx      # one example per capability
|   |-- VALIDATION.md                        # recommended where validation is meaningful
|   |-- ROADMAP_NOTE.md                      # optional
|   |-- THIRD_PARTY_NOTICES.md               # when required
|   `-- ATTRIBUTION.md                       # when required
|-- <ModuleName>Module.kt                    # required: MethodMeshModule entry point
|-- <CapabilityA>Method.kt                   # required: one canonical method implementation
|-- <CapabilityA>CapabilityScreen.kt         # required when the capability has native interaction
|-- <CapabilityB>Method.kt                   # if the module exposes another capability
|-- <CapabilityB>CapabilityScreen.kt         # if that capability needs its own native screen
|-- <Capability>Repository.kt                # only when state/data access justifies it
|-- <ModuleName>DashboardCard.kt             # only if generic dashboard rendering is insufficient
`-- <other module-owned helpers>.kt          # only when genuinely module-owned
```

The folder shown above is the **handoff root**. When admitted to the
repository, that folder belongs at:

```text
app/src/main/java/com/example/methodmesh/modules/<module_name>/
```

Do **not** include `app/`, `src/`, `main/`, `java/`, `com/`, `example/`,
`methodmesh/` or `modules/` as wrapper directories in the returned
artefact. The receiver already knows the destination.

If the handoff is zipped, the ZIP must open to exactly one top-level
`<module_name>/` directory. Do not add an extra wrapper such as
`MethodMesh/`, `<module_name>_handoff/`, `app/` or `modules/`.

### File placement rules

- `<ModuleName>Module.kt` is the single module entry point and owns
  discovery-facing module metadata.
- Each independently invokable capability must have a clearly
  identifiable canonical method implementation inside the module.
- Native screens, dashboard components, repositories and helpers stay
  beside the module code. They are implementation details of that
  module, not shared-app files.
- `docs/` contains **all documentation and ODK examples for the module**.
  Do not return a second documentation tree elsewhere.
- Because every MethodMesh capability must be accessible through the
  ODK/XLSForm roundtrip, provide an ODK example for every individual
  capability. A combined example workbook is acceptable only if it
  clearly demonstrates every method; otherwise use one
  `example_odk_<capability>.xlsx` per capability.
- For a multi-capability module, repeat the per-capability README,
  ODK example, method implementation and native screen as appropriate.
  Do not collapse independent capabilities into a single private
  dashboard implementation.
- A custom dashboard file is optional. The **dashboard presence is not
  optional**. If generic shared rendering can provide that presence from
  module metadata, no custom dashboard file is needed.
- `Repository.kt` is optional. Do not create repository/service/helper
  layers merely to make the folder look architecturally elaborate.
- Module-specific helpers belong in this folder. Shared framework
  changes do not.

### Non-code artefacts and exceptional integration

If a capability genuinely requires a bundled non-code artefact such as
firmware, a model, a template or static data, include it inside a clearly
named module-local subfolder **only if the current MethodMesh
architecture already supports loading that artefact from the module**.

If current architecture requires a file to live elsewhere in the
repository, do not fabricate that repository tree in the handoff.
Describe the required placement as an explicit integration requirement
outside the returned folder.

The same rule applies to genuinely necessary shared-framework changes:
state the change separately in the handoff notes. Do not return modified
`HomeScreen`, shared navigation, project Gradle files or other central
files as though they were part of the module.

### Handoff cleanliness

Do not hand back:

- loose Kotlin files;
- a separate top-level `docs/` folder;
- a reconstructed whole-app or repository tree;
- duplicated wrapper folders;
- unrelated app/shared/framework files included merely for context;
- APKs, build outputs, Gradle caches or generated intermediates;
- IDE/editor metadata such as `.idea/`;
- operating-system metadata such as `.DS_Store` or `__MACOSX/`;
- a ZIP whose extraction location or intended module root requires
  guessing;
- a module that needs central UI special-casing;
- a module with an individual capability that cannot be selected for
  presets/protocols or invoked through ODK.

## Auto-discovery

Modules expose one object implementing MethodMeshModule .

The app discovers modules automatically. Do not add a one-off central
registration list.

## Module-owned metadata

A module owns:

method IDs; descriptors; settings; output fields; capability screens;
docs; examples; dependencies; icon hint; tests where possible.

## Canonical capability contract

A capability declares its method ID, inputs, settings, runtime-input
policy, outputs, result types, closeout semantics and applicable actions
once in module-owned code/metadata. Shared surfaces consume that
declaration.

The declaration is the source of truth for dashboard actions, direct
native execution, preset authoring/execution, protocol composition,
schedules/widgets where applicable, and ODK/XLSForm transport.

A surface may choose how to present or transport a value, but it must
not silently remove capability functionality. If a declared output
exists, generic result projection must make it addressable to ODK and to
other MethodMesh composition surfaces even when it is not normally shown
in the everyday UI.

Output field names, types and semantics are canonical. ODK namespaces,
media attachment handling, clipboard formatting and dashboard rendering
are projections of those canonical fields, not new field definitions.

## Icon key

Modules may expose an iconKey for generic surfaces such as widgets.

Good broad keys:

document ; location ; language ; hardware ; random ; schedule ; tool .

This is still generic. It does not teach the dashboard about a specific
capability.

# 7. Capability lanes

Capabilities move through lanes:

## Incoming prototype

Prototype code from another chat or contributor that has not been
reviewed.

Location:

incoming_capability_prototypes/

Rules:

do not auto-discover it; do not assume it builds; do not trust its
architecture; treat docs as suggestions.

## Development

Builds and is admitted into modules/ , but not yet polished for
production.

Development capabilities may be visible in Workbench/development areas.

They must not claim production status unless they pass the production
checklist.

## Production

A production capability:

builds; runs; has native UX checked; has preset UX checked; has
ODK/XLSForm checked; preserves state across rotation where relevant;
returns beef-first outputs; has docs and example XLSForm; does not
violate the golden rule; has any required attribution/licence notes; has
a clear production method status.

## Workbench

Workbench contains tools that are useful, technical, diagnostic or
operational, but not normal field primitives.

Examples:

firmware installer; sensor provisioner; app inspector; Bluetooth
inspector; API definition editor; debug tools.

Workbench items can be stable without being “production capabilities” in
the end-user sense.

# 8. Settings and runtime inputs

Capability settings must be declared through MethodSetting .

Use:

BooleanSetting for toggles; ChoiceSetting for dropdown/radio-style
choices; MultiChoiceSetting for checkbox groups; IntSetting and
FloatSetting for numbers; TextSetting only for genuine free text.

Do not use raw text boxes for:

barcode formats;

language choices; output modes; yes/no settings; fixed enumerations;
source selectors; known device/service modes.

## Fixed, runtime and operational inputs

Each setting should conceptually be one of:

1. Fixed preset configuration.

2. Runtime input.

3. Operational control.

Fixed preset configuration is hidden when the preset runs natively.

Runtime input is asked before the action runs.

Operational controls may remain visible when genuinely necessary, such
as changing or reconnecting a Bluetooth device.

## Preset authoring

Preferred flow:

configure/test → pretty config screen → test
configure/test → same pretty config screen → save as preset → choose
fixed/runtime settings → name preset

The preset screen should not be a second inferior text-box version of
the test screen.

# 9. Native run UX

Native MethodMesh is a toolbox. A production capability should feel like a small, purpose-built instrument, calculator, viewer, picker or control surface - not an automatically generated settings form followed by an automatically generated results form.

The preferred interaction model is:

**configure/interact -> see live current result -> Commit -> share/save/finish**

The capability should normally remain on one primary working screen while the user adjusts inputs and observes the current output. A second generic "results page" is not the default pattern.

## Capability-owned, relevant UI

The module owns the meaningful interaction surface for its capability. Use controls and visualisations that make sense for the task: steppers, sliders, segmented choices, pickers, maps, camera views, touch surfaces, charts, music notation, diagrams, previews, meters or other domain-appropriate representations.

The generic MethodSetting renderer is useful infrastructure and may be sufficient for genuinely simple capabilities, but it is not the target production UX for a capability whose task benefits from a richer interface. Do not expose a production capability as a row of raw text fields merely because the inputs can technically be represented that way.

Known enumerations should use suitable selectors rather than free text. Small bounded numbers should generally use steppers/sliders or another constrained control rather than requiring keyboard entry. Human-readable labels belong in the main UI; method IDs, raw field names, manifests and implementation detail belong under Technical details.

A direct single-capability run should not display protocol theatre such as "Step 1 of 1". Generic statuses such as "Ready" or "In progress" should appear only when they convey useful operational state.

The shared app may provide a consistent shell for generic actions such as Home, Cancel, Commit, share/save and Technical details. The module owns the capability-specific body. This preserves the golden rule: shared UI knows the action contract, not the bespoke facts of music, maps, sensors, scanners or any other module.

## Live current result

Where outputs can be calculated, previewed or acquired during interaction, show the current result on the same screen and update it in real time or immediately after the relevant user action. Do not make the user press Calculate/Go merely to navigate to a second screen containing values that could have been shown in place.

Examples:

- changing a musical root, scale or accidental preference updates the scale/chord display immediately;
- changing polyrhythm pulse counts updates the ratio, pulse structure and visualisation on the same screen;
- changing calculator inputs updates derived values in place;
- moving a map or selector updates the current selected coordinate/Plus Code in place;
- sensor refresh updates the visible readings in place;
- a scan/capture action inserts the acquired result into the current capability screen rather than forcing an unrelated generic result layout.

Explicit Run/Scan/Measure/Refresh/Calculate buttons remain appropriate when the operation is expensive, asynchronous, permission-gated, physically meaningful, externally observable, or should not happen continuously. Even then, the result should normally appear in the same capability screen.

Live values are **working/current results** until committed. They may change as the user changes the configuration or repeats the operation.

## Tap-to-copy invariant

Any calculated or returned scalar/text value presented as a result in native MethodMesh should be directly tappable to copy its primary clipboard value. This applies on capability screens as well as dashboard cards.

Copy the useful value, not the UI label or debug field name. For example, tapping a Plus Code copies the Plus Code itself, not `Plus Code: ...`. The capability may define whether a unit is part of the useful clipboard representation, but labels and surrounding prose are not.

Provide subtle confirmation such as a snackbar/toast or equivalent accessible feedback. Do not require a separate Copy button for every individual value when direct tap-to-copy is available. A generic Copy action may still copy the capability's primary committed result.

## Commit is the finalisation boundary

`Commit` is the normal native action that says: **this is the result I want to keep/return from this execution**. It is distinct from Run/Scan/Calculate, which may update the working result.

Before Commit, the result may remain mutable. On Commit, MethodMesh freezes the canonical payload for that execution, performs any final result construction needed by the capability, records final hashes/timestamps/audit material where applicable, and enters the committed state.

Commit should normally **not navigate to a second generic result page**. The same screen should transition cleanly into its committed state and reveal the appropriate post-commit actions:

- Share;
- Save to Downloads where meaningful;
- Copy primary result where meaningful;
- Include full JSON/audit toggle or equivalent advanced export control;
- Done/Home;
- Retry/Edit/New run where meaningful.

The committed primary result should remain visible. Technical details and JSON remain secondary/progressive disclosure.

If the user edits inputs after commitment, do not silently mutate the already committed payload. Either return to an explicit editable/new-run state or require Retry/Edit/New run so the distinction between the committed result and the next working result remains clear.

## Sharing and saving after Commit

Share only the beef by default.

Examples:

barcode scan shares only the decoded payload; Plus Code capture shares only the Plus Code; translation shares only the translated text; image redaction shares only the redacted image; document scanner shares the chosen document/text output; sensor read shares the primary readings; conversation translator shares the transcript.

If the user enables full JSON/audit inclusion:

share includes beef + relevant media + JSON; save to Downloads includes beef/text + media + JSON; generic Copy includes beef text + JSON text where that is a sensible clipboard representation.

With the option off:

share/save/copy include only the beef and relevant media.

Do not automatically save internal archive copies merely because the user committed a result. Commit finalises the execution; Share/Save are explicit persistence/export actions.

## Origin-aware Home, Done and closeout

Home/Done/Commit closeout is determined by the launch origin, while the capability implementation remains the same:

- **Normal app/direct/preset launch:** Home or Done returns to the MethodMesh dashboard.
- **Widget launch:** Home/Done after the action returns to the Android desktop.
- **ODK/external roundtrip:** Commit returns the canonical result through the external transport and finishes back to the caller; Cancel returns cancellation to the caller. Do not route through the dashboard or generic native share/save flow.
- **Protocol launch:** Commit closes the current step and returns control to the protocol runner so it can advance or present the next rail.
- **Schedule launch:** closeout returns control to the schedule engine; do not leave the schedule stuck on a capability result screen unless manual confirmation is explicitly required.

The old generic `Submit` label is not right for ordinary native completion. Use `Commit` for finalising the current capability result and `Done`/Home for leaving an already committed execution. Use domain-specific verbs such as Scan, Measure, Translate, Generate or Calculate for the operation itself where useful.

## Native run requirements

Native runs should:

- ask only what is needed;
- use a polished, capability-relevant interaction surface;
- show current results in-place as soon as they exist;
- preserve working and committed state across orientation changes;
- make displayed calculated/returned values tap-to-copy;
- use Commit as the explicit finalisation boundary;
- reveal share/save/export actions after commitment rather than forcing a generic result page;
- hide raw settings, method IDs, JSON and diagnostics behind Technical details where useful;
- avoid automatic internal saves;
- route Home/Done according to launch origin.

Native runs should not:

- default to settings page -> Go -> generic results page when an integrated live screen is feasible;
- dump huge JSON on the user by default;
- show raw output field names such as `music_polyrhythm_ratio_a` as the primary human-facing label;
- show fixed preset settings again unless the user explicitly enters Edit;
- make buttons act like checkboxes;
- use long verbose explanatory text where labels/visual structure suffice;
- display `Step 1 of 1` rails for an ordinary single capability run;
- jump to the wrong destination after closeout.


## Destructive actions

Deletion of persistent user/project objects requires explicit confirmation, including presets, protocols, schedules, registered devices, stored configuration, server-side form deletion and other irreversible persistent objects.

The dialog names the object and describes the consequence accurately. Cancel is the safe/default action. Back or tapping outside cancels.

Do not add destructive confirmation to reversible operations such as Archive, Hide, Disable, Pause, provider-specific Undeploy or access revocation unless that operation itself discards data.

Bulk deletion states the number of objects affected. If an object is referenced by protocols, schedules, widgets or other persistent objects, show the dependency before deletion.

# 10. ODK/XLSForm contract

ODK calls MethodMesh through Android intents, normally from an XLSForm
group.

Rules:

intent calls should be made via groups; inputs use input\_\* intent
parameters; return fields are group children; blank return fields must
not overwrite request parameters; ODK supplies its own UI and values;
MethodMesh should not force ODK callers through native setup dialogs;
MethodMesh should store nothing merely because it is handling an ODK
contract; MethodMesh returns data to ODK and lets ODK own the
submission.

ODK has its own route through the same capability contract. If ODK supplies all required inputs and no MethodMesh-side interaction is needed, execute directly and return the result without showing native setup or post-commit share/save UI.

If the capability requires MethodMesh-side interaction - for example a camera, map, drawing surface, hardware interaction or other visual selection - ODK may launch the normal polished capability screen in **external-roundtrip mode**. That screen uses the same live-result and Commit semantics, but Commit returns the canonical payload directly to ODK and finishes the MethodMesh activity. It does not send the user to the dashboard. Cancel returns cancellation cleanly to ODK.

ODK owns form persistence and submission. Native Share/Save controls are normally suppressed in external-roundtrip mode unless an explicit contract calls for them.

## ODK capability parity

ODK is a first-class invocation surface for the same MethodMesh
capability contract. It is not a reduced API containing only the outputs
currently used by example forms.

Anything MethodMesh can do through a declared capability must be
invokable from ODK where Android/ODK transport can represent the
interaction. Anything a declared capability can return must be
requestable/returnable to ODK, however obscure the field may be and
regardless of whether a real-world form is expected to use it.

This requirement applies to scalar values, structured values, hashes,
diagnostics/audit fields, media/file URIs, calculated values and other
declared outputs. Where a type requires transport machinery, such as
ClipData and read grants for binary artefacts, the transport layer
provides that machinery without changing capability semantics.

Example XLSForms are examples, not allow-lists. Adding a new capability
output does not require a bespoke ODK code path; the generic
projection/transport layer must expose it from the canonical output
contract.

## Namespaces

ODK forms may use:

methodmesh_return_namespace='photo'

The namespace projector prefixes returned keys:

redacted_image_uri → photo_redacted_image_uri redacted_image_sha256 →
photo_redacted_image_sha256 methodmesh_full_json →
photo_methodmesh_full_json

Do not redesign namespace handling casually. It exists to avoid field
collisions when several MethodMesh calls appear in one form.

## Flat + full JSON

The ODK default pattern is:

main useful field(s) + methodmesh_full_json

For media-producing capabilities:

main media URI + useful scalar fields + methodmesh_full_json

Example:

redacted_image_uri redacted_image_sha256 methodmesh_full_json

## Binary artefacts

For returned binary artefact `content://` URIs:

- keep the namespaced string extra;
- add the URI to the returned Intent `ClipData`;
- add the Android read-URI grant flag `FLAG_GRANT_READ_URI_PERMISSION`;
- do this centrally in transport, not in the capability.

This lets ODK import files as real attachments rather than only
receiving URI text.

## No MethodMesh storage on ODK return

When handling ODK/external-app contracts, MethodMesh should not create
extra output- folder saves. It should return the result/URI/grant and
let the caller own persistence.

Temporary/cache files needed to expose an artefact through FileProvider
are acceptable as artefact source files. Extra “just in case” MethodMesh
archive copies are not.


## ODK Forms library ownership and discovery

Example XLSForms are module-owned artefacts.

Canonical source:

```text
app/src/main/java/com/example/methodmesh/modules/<module>/docs/example_odk*.xlsx
```

The build may generate a packaged runtime catalogue/projection, but generated assets are not authoritative sources and must never replace the module copy.

The catalogue is one-to-many: `module -> zero/one/many XLSForms`.

Catalogue records preserve at least module ID, filename/source path, `form_id`, form title, version and content hash. Where possible, `form_id`, title and version are read from the XLSForm `settings` sheet rather than inferred from the filename.

### Build-time form catalogue projection

Android does not automatically package XLSForms stored beneath Kotlin source/module `docs/` directories. The build therefore projects module-owned forms into runtime assets.

Canonical source discovery includes:

```text
src/main/java/com/example/methodmesh/modules/<module>/docs/**/*.xlsx
```

Canonical `example_odk*.xlsx` names are preferred. The generator may also include other workbooks that structurally look like XLSForms as a safety net, while emitting naming findings rather than silently hiding them.

Generated runtime projection:

```text
src/main/assets/methodmesh/odk_templates/index.json
src/main/assets/methodmesh/odk_templates/<module>/<form>.xlsx
```

The index includes source path, module ID, form metadata, SHA-256 and validation findings. Runtime module display names resolve against the canonical module registry rather than a second hand-maintained name list.

The app-module build hook is:

```kotlin
apply(from = "src/main/java/com/example/methodmesh/ui/integration/odk-template-assets.gradle.kts")
```

A repository build may force regeneration with:

```bash
./gradlew generateMethodMeshOdkTemplateAssets
```

The generator MUST run before Android merges assets. Catalogue parity tests SHOULD verify that every index entry has a packaged XLSX, multiple forms owned by one module remain distinct, settings metadata is projected where present, and ODK-plausible modules have an example or an explicit exemption.

## ODK Central rapid-test deployment

MethodMesh may hold an ODK Central connection profile for rapid capability/XLSForm testing.

The administrative API is authenticated as a Central web user. A password is used transiently to create a session and is never persisted. Persisted session material uses Android secure/Keystore-backed storage. The current session route is `POST /v1/sessions` with bearer authentication. The UI requires an HTTPS Central URL before accepting the password. Central deployments that require OpenID Connect/SSO need a future compatible connector rather than pretending password-session authentication will work.

The password field remains a password-class input to Android/IME even while the user explicitly reveals its contents. A restrained eye/eye-off action controls MethodMesh rendering; reveal state MUST NOT turn the field into ordinary composing text, enable suggestions/autocorrect, or cause the password to be persisted.

A connection profile identifies Central server, authenticated web-user identity, project and selected test App User.

For Central, the ordinary form-library toggle means **available to the selected test App User**.

Checking a form creates/updates its Draft where required, publishes it and grants the selected App User form access. Rapid-test publication MAY use a generated `mm-<timestamp>` test version so local iteration is not blocked solely because a module example's version has not yet been manually bumped. MethodMesh records the returned remote form identity and local SHA-256 so checked forms can be resynchronised.

Unchecking revokes that App User's access. It does not delete the form or submissions. If MethodMesh changes the selected test App User, it should revoke the previous MethodMesh-managed assignment before assigning the new tester.

Permanent remote deletion is a separate destructive action with explicit confirmation and, where available, submission/dependency context.

## Kobo rapid-test deployment

KoboToolbox is a separate provider with separate state. Authentication uses the Kobo API token. Where MethodMesh retrieves that token using a transient username/password login, the password is never stored and the returned token is encrypted at rest. The current provider integration uses the Kobo v2 asset/import/deployment model rather than pretending Central and Kobo share one server API.

Checking the Kobo control imports/updates and deploys/activates the form for collection.

Unchecking undeploys/deactivates the remote form and verifies the resulting server state. It does not delete the Kobo project or submissions.

KoboCollect normally uses manual blank-form download. A blank form already downloaded to a collector device remains local after server undeployment until it is removed on that device (unless the collector is deliberately configured for a server-mirroring update mode). Therefore MethodMesh MUST describe unchecking as **undeployed/not available for new server download**, not as "removed from KoboCollect". Collector-device cleanup is a separate device-side concern.

Permanent Kobo server deletion is a separate confirmed destructive action.

Provider state is independent so a form can be local-only, Central-only, Kobo-only or active on both. Share/Save always operate on the local module-owned template and are independent of either server copy.

SurveyCTO and CommCare use ODK Collect-compatible invocation/collection patterns and benefit from the canonical XLSForm/ODK transport contract. Future non-Collect ecosystems such as Survey123 should use explicit provider/collector adapters rather than capability-specific special cases.

## XLSForm batch validation

The ODK Forms surface includes a top-level batch validation function.

Validation operates over all discovered module-owned forms and produces clean/warning/error status, module/source attribution, individually copyable findings, per-form diagnostic reports and an exportable batch report.

MethodMesh structural/convention checks include workbook/sheet structure, duplicate or missing names, group/repeat balance, `${field}` reference integrity, choice-list integrity, duplicate `form_id` detection, settings metadata, known JavaRosa/XPath incompatibilities and MethodMesh naming conventions.

ODK-compatible validation should use pyxform/ODK Validate where available and surface Central/Kobo server diagnostics when deployment fails.

Do not turn validation warnings into silent automatic rewrites. In particular, do not silently change a deployed `form_id` merely to satisfy naming style.

### MethodMesh XLSForm naming convention

Preferred filename:

```text
example_odk_<purpose>.xlsx
```

Use lower snake case for new filenames.

`form_id` is a stable external identity. Once deployed, treat it as a contract.

Titles are human-readable. Versions change when the form definition changes.

Legacy/non-standard filenames are migration warnings, not grounds for excluding a valid form from the library.

## Deployment diagnostics

Provider failures are attached to the exact form that failed.

A useful diagnostic report includes provider, HTTP status, provider/API error code, request method/endpoint, human-readable message, structured validation/import details, warnings and raw server response as fallback.

The complete report is copyable. On the current Forms surface, tapping **Why did this fail?** should both expand the diagnostic and copy the complete report to the clipboard; the explicit **Copy report** action remains available. Batch operations preserve separate diagnostics for each failed form and expand/flag modules containing failures.

# 11. Output contract

Every capability should define:

core result fields; optional audit/ALCOA fields; full JSON
representation; media/file fields where relevant; error fields.

## Cross-surface output invariant

Every declared output belongs to the capability contract, not to a UI
surface. A field may be hidden from the ordinary native result screen,
omitted from a compact dashboard card, or uncommon in presets, but it
remains addressable for protocols, pipes and ODK projection.

Beef-first governs presentation and defaults; it does not authorise
deleting salad or obscure outputs from the contract. Presentation can be
selective. Capability availability cannot.

## Core result

The core result is what most users care about.

Examples:

barcode_payload ; plus_code ; mlkit_translate_text ; redacted_image_uri
; redacted_image_sha256 ; document_scan_searchable_pdf_uri ;
document_scan_ocr_text ; lower_value , upper_value ;
conversation_transcript .

## Audit/ALCOA fields

Audit fields support:

attributable; legible; contemporaneous; original; accurate.

Common audit fields include:

time; method ID; execution ID; device/source information; configuration
values; selected cell/region definitions; hashes; manifests; status;
diagnostics.

## Full JSON

Full JSON is the complete auditable payload.

It should be available:

as methodmesh_full_json for ODK where requested; as opt-in export/share
metadata for native runs; as background data for verification workflows.

It should not be the primary native result screen.

## Hashes

Hashes should represent the actual object being committed.

Do:

hash final image bytes for image artefacts; hash exact UTF-8 bytes for
text commitments; hash exact recipe strings where the recipe itself is
part of the commitment; stream large files where possible.

Do not:

hash a URI string when the claim is about file bytes; hash
pretty-printed JSON if the commitment is to exact supplied JSON;
silently normalize, trim or rewrite commitment text unless the contract
says so.

# 12. Capability closeout, protocols and schedules

Capabilities need a closeout contract. Commit finalises a capability result; closeout hands that committed result back to the launch origin. Those are related but not identical operations.

A capability step should end with one of:

completed with payload; completed without payload; failed with diagnostic; cancelled; externally completed and manually confirmed; retry requested.

The launch context must be preserved so closeout can return to the correct owner: dashboard/app navigation, widget/desktop, ODK/external caller, protocol runner or schedule engine.

## Single native preset

A single manually run preset should run the same capability UI without protocol rails. Fixed preset settings remain hidden; runtime inputs and operational controls appear where needed. Current outputs appear in-place.

Commit freezes the preset execution result on that same screen and reveals the applicable actions: Share; Copy; Save to Downloads; include full JSON/audit if desired; Done/Home; Retry/Edit/New run where useful.

Do not insert a generic result-only page merely because the launch came from a preset.

## Protocol step

Protocols should guide the user:

Previous step completed: barcode scanned Next step: random number Go

For external apps with no callback:

Next step: complete web form Go ... Did you complete the web form? Yes /
No / Cancel

If Yes, continue.

If No, offer retry.

If Cancel, confirm before killing the whole chain.

## Final protocol result

The final protocol result should collate the beef from all steps:

barcode: DKJDALSD999 random number: 0.233214 photo: redacted_image.jpg

Metadata should be available behind details/export, not tangled into the
main result list.

## Schedules

Schedules using presets must use the same closeout contract. They should
not get stuck on a preset result screen unless the schedule explicitly
requires manual confirmation.

Schedule widgets toggle schedules on/off.


## Scheduler shared-runtime contract

The current shared scheduler contributes `scheduler.create`, `scheduler.export` and `scheduler.import` methods/screens alongside discovered module methods.

Current `scheduler.create` inputs include:

- `schedule_name`;
- `schedule_target` (currently `ODK_FORM` or `WEB_FORM` in the scheduler source contract);
- `schedule_target_value`;
- `schedule_frequency` (`DAILY`, `WEEKLY`, `MONTHLY`, `CUSTOM`);
- `schedule_time` (`HH:mm`);
- weekly/monthly/custom day fields;
- retry count and retry interval;
- notification title/message;
- optional chain ID/order;
- optional ODK/Kobo project identifiers.

Current outputs include `schedule_id`, `scheduler_status`, `scheduler_next_run_iso`, target/frequency fields and `scheduler_error`.

Schedules are local to the device. ODK targets cross the normal `odk_form_launcher` boundary. Export/import uses a deterministic bundle with SHA-256 validation and can use generic QR/NFC dependencies without duplicating QR/NFC implementations in the scheduler.

A roadmap preference proposes moving toward **protocols own what; scheduler owns when**. That is a design direction, not a statement that the current ODK/web-form scheduler targets no longer exist.

# 13. Presets, protocols and pipes

Presets and protocols compose canonical capability contracts; they do not define alternate private implementations.

A future protocol-pipes layer may bind outputs from one step to inputs of another. The semantic model is defined before any visual editor.

Bindings may be Literal, RuntimeInput, ResultRef, Transform or Default/Coalesce.

Outputs and inputs use typed ports/sockets such as number, text, boolean, location, media, object and arrays/collections where required.

Compatible values connect directly:

```text
AHT20.temperature -> astronomy.temperature
AHT20.humidity    -> astronomy.humidity
```

Where schemas differ, use a bounded declarative transform palette such as join/split, text template, numeric formatting, type conversion, unit conversion, arithmetic, JSON/object construction and defaults/coalesce.

Do not permit arbitrary Kotlin, JavaScript or other general-purpose code execution as a protocol transform.

`ResultTree`/canonical structured outputs and stable result paths are the substrate. Do not flatten rich responses prematurely merely to make the first visual editor easier.

A later Scratch-like editor is a visual projection of this graph:

```text
[preset A] -> [transform] -> [preset B]
```

The visual canvas must not become the semantic architecture.


# 14. Online data and APIs

api.get is the runtime capability for declared API definitions.

Individual API providers should usually be data definitions, not bespoke
capabilities.

The API definition editor/tester belongs in Workbench.

## Bundled APIs

Bundled APIs are useful where mature and stable.

They should:

be declarative; expose sensible inputs; show available result paths;
support selected/multi-field returns; show source/update age; cache
responses; refresh from network by default; fall back to cache if
offline or failed where policy allows.

## Location privacy

Where API calls use location, default to rounded disclosure when
appropriate.

Current accepted rule:

5 km rounded coordinates for declared third-party API providers

Do not silently send exact GPS to remote services.

## Credentials

Credential rules:

reference credentials, do not embed them in exported definitions; redact
credentials in display URLs/logs; do not include secrets in ODK
payloads, provenance or debug logs.

## RSS/Atom

RSS and Atom should share API/cache infrastructure but need a
specialised reader surface:

subscriptions; read/unread/saved; item identity; deduplication; offline
search; media policy.

Video auto-download is off by default.

# 15. Offline-first rule

MethodMesh should work offline where practically possible.

Offline-first does not mean online is forbidden. It means the core
useful operation should not depend unnecessarily on a proprietary or
remote service.

Examples:

Plus Codes are calculated locally. Conversation translation uses
downloaded ML Kit language packs. Image redaction is local. Document
scanning is local where ML Kit/device services allow. GPS navigation
works from coordinates or Plus Codes.

Sensor read/provisioning is local BLE/device work.

Online services may improve context:

map tiles; satellite imagery; weather/hazard APIs; TSA timestamping;
language pack downloads.

But if online fails, the app should fail clearly or fall back to
cached/local behaviour where possible.

## Downloaded offline resources versus disposable cache

MethodMesh SHOULD distinguish deliberately downloaded offline resources from disposable cache. Translation models, firmware, scientific rasters and future offline map/resource packs are user-selected resources, not merely "cache" in the product UX.

A shared resource view may report application size, downloaded resource classes and disposable cache separately, and provide management actions without deleting research-linked results or intentionally installed offline resources.

Specialist modules MAY initially own resource packs locally where necessary, but the long-term shared model is a generic resource-pack registry consumed by modules without capability-specific `HomeScreen` logic.

# 16. Shared device services

Settings owns shared device services.

## ML Kit language packs

Language packs are shared between language capabilities:

translation; conversation translation; future language/NLP tools.

Settings should show:

Downloaded English (En) \[size\] trash French (Fr) \[size\] trash

Tap to download Afrikaans (Af) down arrow Albanian (Sq) down arrow ...

Downloaded languages appear at the top.

Language names should be human-readable first, code second:

English (En) French (Fr) Swahili (Sw)

Use canonical ML Kit-supported language codes. Some language labels,
such as Chinese variants, may need careful canonical mapping rather than
assuming a colloquial label is a valid pack ID.

Download UI should show activity and debug/progress state clearly enough
to tell the difference between “network off” and “stuck”.

Respect Google attribution and terms for ML Kit features.

## Future map tiles

Offline map tile management should eventually follow a similar
shared-service pattern:

download; delete; region; size; attribution; online fallback; offline
availability.


# 16A. Shared Android capability surfaces

Capabilities may publish generic semantic state/actions for Android surfaces without teaching shared infrastructure capability-specific facts.

Potential shared surfaces include notification shade, heads-up notifications, foreground/ongoing notifications, progress/chronometer notifications, widgets, launcher shortcuts, Quick Settings tiles where appropriate, bubbles or picture-in-picture where appropriate, sound/vibration, deep links and managed-device/kiosk integration where explicitly supported.

Security/operational status may use semantic states such as **good**, **attention** and **alert**, rendered as green/amber/red where the Android surface permits. Do not promise arbitrary coloured status-bar indicators that Android does not expose.

Modules declare generic descriptors, state and actions. Shared Android infrastructure owns platform APIs, permissions, lifecycle and policy requirements.

# 17. Location tools

## Plus Code capture

Principle:

GPS → local/offline-capable map view → OLC grid → user selects cell →
store full Plus Code

Rules:

Open Location Code calculation must be local and deterministic. Do not
depend on Google APIs or what3words. Store full globally self-contained
Plus Codes. Basemaps are contextual only. Online maps may be default
while offline maps are not yet implemented. Street/satellite/grid modes
can help selection. Satellite imagery is useful for building selection.
Selected cell should map correctly to real-world OLC geometry, not
merely screen projection. ODK return should be Plus Code plus metadata
JSON.

Native share should be only the Plus Code.

## GPS target navigator

Rules:

target may be lat/lon or Plus Code; preset mode should ask for lat/lon
or Plus Code then start navigation; navigator points to target, not
north; distance must show current distance; AR camera should be a
separate view with crosshair/HUD; orientation should work upright as
required for AR, not only flat.

# 18. Sensors and hardware

Hardware/device tools belong mostly in Workbench unless they are
polished field-facing capabilities.

ESP32 sensor work includes:

firmware installer; sensor provisioner; sensor read; device registry;
live diagnostics.

Rules:

sensor firmware and app protocol must agree on installed profiles;
provisioning must not retain stale names or stale profiles after proper
reset; live sensor read should show key values rather than huge
manifests; device registry should have refresh/live view; firmware
images that are core to the system should be tracked; manual docs videos
need not be tracked if updated outside the repo.

## Device registry target model

The Device Registry SHOULD behave as a live registry rather than a static manifest. Known sensors should expose refresh/current values and detected/not-detected state where technically feasible.

Firmware profile, provisioning state, app protocol and registry identity MUST agree. Reset/reprovision workflows SHOULD avoid stale names or stale sensor-profile metadata.

Hardware-specific permissions and constraints remain module-owned documentation and should surface only when the task requires them.

# 19. Attestation and audit commitments

The current attestation direction is:

ODK constructs canonical commitment string ODK hashes it ODK sends
event_payload_hash + commitment_recipe to MethodMesh MethodMesh
signs/timestamps those commitments Verifier reconstructs later

MethodMesh should not require ODK to send every raw field value into
attestation.create .

## attestation.create

Inputs:

event_payload_hash ; commitment_recipe ; verification method/evidence;
timestamp policy; optional study/operator/subject/event metadata.

Rules:

ODK owns payload construction. MethodMesh validates event_payload_hash
as SHA-256 hex. MethodMesh validates commitment recipe JSON. MethodMesh
calculates commitment_recipe_sha256 . The signed canonical attestation
binds event_payload_hash and commitment_recipe_sha256 . Large objects do
not pass through attestation. Media artefacts are represented by byte
hashes, such as redacted_image_sha256 .

## Commitment recipe

Recipe schema:

methodmesh.commitment_recipe.v1

Canonicalization:

ordered-kv-v1

Hash:

SHA-256

Supported commitment types:

value ; artifact-bytes-sha256 ; text-utf8-sha256 ; json-utf8-sha256 .

The recipe is declarative. Do not execute expressions, XPath,
JavaScript, ODK calculations or arbitrary code inside it.

## Trusted timestamp

RFC 3161 trusted timestamping may be:

disabled; preferred; required.

If required and unavailable, the attestation should fail clearly rather
than silently creating an incomplete trusted timestamp claim.

# 20. Documentation rules

The single-resource rule in §2A is normative. Do not create new standalone project-wide manuals when the content belongs here.

Module-specific documentation stays with the owning module and should be concise, current and implementation-facing.

Every admitted capability should have module-local docs. All of those
documents live inside the returned module's `docs/` directory; there is
no parallel top-level documentation handoff.

For a module exposing several capabilities, documentation is
capability-addressable: each method must be identifiable in the docs and
each capability must have an ODK example that exercises its canonical
contract.

Minimum:

- `docs/README_<Capability>.md`
- `docs/example_odk_<capability>.xlsx`

Recommended:

- `docs/VALIDATION.md`
- `docs/ROADMAP_NOTE.md`
- `docs/THIRD_PARTY_NOTICES.md`
- `docs/ATTRIBUTION.md`

Capability README should cover:

purpose; method ID;

lane/status; native UX; preset behaviour; ODK/XLSForm behaviour;
inputs/settings; runtime inputs; outputs; core result fields; audit
fields; full JSON field; media/attachment rules; permissions;
offline/online behaviour; dependencies; examples; validation notes;
attribution/licensing.

Capability documentation must explicitly state that the dashboard is one
projection of the capability contract and must identify how each
individual capability is exposed for direct native use, presets,
protocols and ODK/XLSForm.

README output tables should distinguish “normally shown” from
“contractually available” where helpful, but all declared outputs must
remain part of the canonical contract.

## Example XLSForms

Example XLSForms should:

use grouped intent calls; avoid return/input field name collisions;
include main output fields; include methodmesh_full_json where audit
metadata is expected; include media fields as real ODK attachment types
where relevant; demonstrate realistic usage, not just a synthetic debug
call.

Do not rewrite all example forms during architecture experiments unless
explicitly asked. First prove the transport/contract works.


# 20A. Module Review and Refresh Standard

This section absorbs and supersedes the separate **MethodMesh v1.05 Module Review and Refresh Manual**.

The same rules used to author a new module are used to review an existing module. Review is a migration/quality task, not a greenfield redesign.

## Review input

Normally review receives the existing module folder, this Master Book, and optional build errors/screenshots/known issues/reference artefacts.

Begin by understanding the module. Do not immediately rewrite it.

## Preserve established contracts

Preserve stable module IDs, method IDs, input keys, output keys, semantics and external contracts unless they are genuinely broken, unsafe or internally inconsistent.

Aesthetic preference is not sufficient reason to break a contract.

If a breaking change is unavoidable, document the reason and migration impact explicitly.

## Capability inventory

Before editing, inventory module ID/display name, all canonical methods, settings, runtime inputs, outputs, native screens, dashboard exposure, preset/protocol/ODK behaviour, schedules/widgets where applicable, state requirements, permissions/dependencies and module-owned XLSForms/docs.

Do not accidentally lose a less-visible capability while polishing the main dashboard.

## Canonical surface parity review

For every capability verify the same canonical contract remains reachable through all applicable surfaces: direct native use, module dashboard, presets, protocols, ODK/XLSForm, schedules, widgets and supported external invocation.

A rich dashboard is an aggregation surface, not an implementation boundary.

If an operation or output exists in MethodMesh, it remains independently addressable through the capability contract where technically representable.

## Native UX review

Production native UI should look and behave like the task. A compass should look like a compass; a navigator like a navigation instrument; a scanner like a scanner; a counter like a counter.

Generic `MethodSetting` forms are infrastructure/fallbacks, not the default production aesthetic.

Put the tool/interacting surface first and configuration beneath or around it as appropriate.

Remove framework clutter such as raw method IDs, machine field names, `Step 1 of 1`, redundant transport wording and generic result-page detours from normal production UI.

## Working result and Commit review

Verify the lifecycle:

```text
configure/interact
    -> live current result
    -> Commit
    -> post-commit actions / origin-aware closeout
```

Working state and committed state are distinct. Rotation/recomposition preserves meaningful working state. Commit freezes the canonical return payload. Editing after Commit does not silently mutate the committed payload.

## Tap-to-copy review

Every displayed calculated/returned scalar or text result exposes its useful clipboard projection. Copy the value, not the decorative label.

## Preset review

Intentional fixed settings persist; fixed settings do not reappear as runtime questions; runtime inputs remain runtime; one-off run text is not accidentally saved; direct preset launch uses the same canonical capability.

## Protocol review

Protocols invoke canonical methods/presets rather than private copies; current step/prior outcome/next action are explicit; retry/cancel/continue rails exist where needed; step results remain available; final combined results do not destroy individual canonical outputs; launch-origin context survives.

## ODK/XLSForm review

ODK parity is mandatory.

For every declared input/output verify intent key/name, type/semantics, return namespace/projection, binary attachment handling where relevant, `methodmesh_full_json` where full audit payload is expected, and correct caller return after Commit/Cancel.

The supplied XLSForms are part of the module contract. A module is not complete merely because native execution works.

Run batch XLSForm validation and address genuine errors. Naming/convention warnings may be migrated deliberately, but deployed identities are never changed silently.

## Widget/schedule review

Where applicable, widgets/schedules invoke the same canonical capability/preset/protocol contract and preserve launch-origin closeout.

Widget completion normally returns to Android desktop. Scheduled execution returns to the schedule engine/appropriate flow.

## Media/maps review

Media, documents, charts and maps expose appropriate share/save/full-screen actions. Maps support pan/zoom and preserve state. Binary returns use correct Android URIs/grants.

## Persistent-state review

Long-running or multi-entity tools may need temporary persistent state. Persistence solves a real operational need and does not become an accidental duplicate data store.

## Module-local ownership review

Capability-specific UI, settings, algorithms, examples, dependencies, docs and XLSForms belong in the module.

Shared infrastructure may know generic contracts and surface descriptors but must not gain special-case knowledge of a capability.

## Shared-framework changes

A module migration should normally be deliverable as one self-contained module folder.

If a genuine shared-framework change is required, keep the module self-contained as far as possible, document the shared dependency separately, do not return a whole repository tree merely to deliver one module, and do not solve a module problem by hard-coding the module into shared UI.

## Handoff structure

Canonical handoff is exactly one module root:

```text
<module_name>/
    ...
    docs/
        README_*.md
        example_odk*.xlsx
        validation/review notes where genuinely useful
```

The ZIP opens directly to that module root.

Do not include `app/`/repository wrappers, `build/`, `.gradle/`, IDE metadata, APKs, unrelated modules or generated/intermediate files.

## Review severity

- **Blocking** — build/runtime failure, data loss, contract break, unsafe behaviour or impossible required roundtrip.
- **Major** — missing surface parity, broken lifecycle/state, materially misleading UI or invalid supplied XLSForm.
- **Minor** — polish, naming/convention or non-blocking documentation/UX cleanup.

Do not over-redesign a stable module merely to make it look different.

## Review passes

Use three passes rather than polishing before contract failures are understood.

**Pass A - contract correctness:** find dashboard-only capabilities, missing preset/protocol/ODK exposure, duplicated implementations, broken IDs and inconsistent outputs. Fix these first.

**Pass B - interaction quality:** find generic settings -> result detours, deterministic results hidden behind unnecessary Calculate actions, machine labels, unclear Commit semantics, missing tap-to-copy and poor task-specific visual hierarchy.

**Pass C - field robustness:** find rotation/session loss, accidental storage, unclear offline failure, media URI/grant problems, navigation traps, widget/ODK closeout failures, inaccessible controls and excessive verbosity.

## Documentation update during review

The module README SHOULD describe actual current behaviour, including purpose, method IDs/capability list, lane/status, dashboard/direct/preset/protocol/ODK behaviour, widgets/schedules where relevant, settings/runtime inputs, outputs (primary/secondary/audit/full JSON/media), offline/online behaviour, permissions, persistence, dependencies, attribution/licensing and validation status.

Do not document aspirational behaviour as implemented.

## Example XLSForms during review

Where ODK use is plausible, include working example XLSForm(s). Multi-capability modules SHOULD demonstrate the independently callable capabilities adequately; one dashboard-oriented example does not substitute for the underlying contracts. Examples use grouped intent calls, correct input/return fields, namespace handling, media fields where relevant and `methodmesh_full_json` where appropriate.

## Do not over-redesign

A refresh does not change the module's purpose by default. Do not invent unrelated capabilities, remove niche capabilities because they seem obscure, simplify away outputs, replace local/offline functionality with cloud services, introduce central special-casing, rename stable contracts gratuitously or redesign the whole app unless explicitly requested. New scope belongs in module roadmap notes or a separate project decision.

## Definition of Done

A reviewed module is done when it builds in target context where build access exists; stable contracts are preserved or migrations documented; every capability remains discoverable; dashboard/direct/preset/protocol/ODK parity is verified; schedule/widget parity is verified where applicable; native UI is capability-appropriate; live-result -> Commit is correct where relevant; launch-origin closeout works; meaningful working state survives ordinary lifecycle changes; displayed scalar/text outputs are tap-to-copy; full canonical outputs remain available; ODK attachments use correct transport; module-owned XLSForms are discovered and validate or intentional warnings are documented; module docs reflect actual behaviour; shared shell special-casing has not been introduced unnecessarily; handoff is one clean module folder.

## Required final self-review

Before handoff check:

1. Did I remove or rename any established method/input/output contract?
2. Can each individual capability still run without going through the dashboard?
3. Can each capability be used in presets and protocols?
4. Can ODK call it and receive every declared output?
5. Do supplied XLSForms validate?
6. Is native UI task-specific rather than a generic settings/result detour?
7. Does Commit freeze the actual canonical payload?
8. Does Done/Cancel return to the correct launch origin?
9. Does rotation/state restoration behave sensibly?
10. Can displayed useful values be copied directly?
11. Did I add capability-specific knowledge to shared code?
12. Is the handoff exactly one clean module folder?

## Required module-review response format

For a module migration/review task, the principal deliverable is the updated module folder. Keep the final explanation concise. A short note may identify compatibility decisions, a genuine shared-framework dependency or anything that could not be tested, but do not substitute a long report or whole-app tree for the folder.

For a large migration programme, maintain a scorecard with at least: module, capability count, contract parity, UI migration, ODK/XLSForm verification, persistence/state verification, tests/build status and any shared-framework dependency.

# 21. Testing rules

Run tests proportional to risk.

For focused changes:

add focused tests at the contract boundary; run those tests; run
:app:assembleDebug .

Examples of contract-boundary tests:

every registered capability is exposed to preset discovery; every
registered capability is exposed to protocol discovery; every registered
capability is representable to ODK transport; every declared output can
be projected by ODK; output names/types/semantics are unchanged across
projections; dashboard aggregation does not suppress underlying
capability discovery; namespace projection preserves keys; binary
artefact returns include ClipData/read grant; hash is computed over
final bytes; recipe validation rejects malformed input; preset fixed
settings hide at runtime; live result remains on the capability screen; Commit freezes the canonical payload; post-commit actions are revealed without a mandatory generic results page; displayed result values expose a clipboard projection; origin-aware closeout routes app/widget/ODK/protocol/schedule launches correctly; orientation preserves working and committed state.

For capability promotion:

Contract-parity tests should be generic wherever possible: enumerate
registered methods and declared outputs, then assert that dashboard
discovery, preset discovery, protocol discovery and ODK projection are
derived from the same canonical metadata rather than separate
hand-maintained lists.

build debug APK; exercise the dashboard presence; exercise direct native run; verify the production screen is capability-relevant rather than a raw generic form where richer UI is warranted; verify current results update in-place; verify tapping displayed results copies the intended value; verify Commit freezes the payload and reveals post-commit actions without forcing a generic result page; verify Home/Done routing for app and widget origins; verify ODK interactive and non-interactive routes return cleanly to ODK; verify every individual capability appears in preset creation;
verify every individual capability appears in protocol creation;
exercise native preset run; verify ODK can invoke each method and
project every declared output; exercise the example XLSForm where
possible; check canonical field names/types/semantics match across
surfaces; check share/copy/save behaviour; check no golden-rule or
contract-parity violation.


## Cross-surface parity tests

Where feasible, tests verify that the same canonical method ID/input/output semantics are observed through direct execution, presets, protocols and ODK adapters.

## XLSForm validation tests

The repository-level form validator is runnable in batch and makes invalid module-owned examples visible before deployment.

A failing Central/Kobo upload is not an acceptable primary validation workflow; server diagnostics are a useful second line and remain visible/copyable.

# 22. Build, release and git hygiene

Common build:

./gradlew :app:assembleDebug

Common test/build:

./gradlew :app:testDebugUnitTest ./gradlew :app:assembleDebug

Release process normally includes:

update docs/release notes; run relevant tests; build debug; commit;
push; tag; GitHub release.

## Dirty worktree rule

The MethodMesh worktree is often busy.

Before committing:

inspect status; stage only the coherent intended set; do not
accidentally stage prototypes, .idea , firmware backups or unrelated
docs; preserve user/other-chat changes unless explicitly asked to clean
them up.

## Destructive cleanup

Do not delete, reset or archive broad sets of files without explicit
confirmation.

Archiving old docs should be done as a deliberate pass after this Master
Book is reviewed.

# 23. Third-party and attribution rules

Capabilities using third-party libraries, data or services must
document:

provider; licence/terms; attribution requirements; offline/online
dependency; data sent off-device; credentials/API keys; privacy
implications.

Important known areas:

Google ML Kit translation/vision/document scanning; Open Location Code /
Plus Codes; OpenFreeMap; Esri World Imagery; OpenStreetMap-derived
tiles; Open-Meteo; GDACS; World Bank Indicators; exchange-rate
providers; RFC 3161 timestamp authorities.

OpenStreetMap volunteer tile servers should not be used in a way that
violates tile usage policy. Prefer appropriate tile providers or offline
packs.

# 24. Implementation status and roadmap

This chapter is informative. It records project-wide status/direction and MUST NOT be used to infer that a particular module is Production-ready. Production/Development status is derived from current method metadata and review evidence; module-specific issues belong in that module's docs.

## 24.1 Status is dynamic

Presence in source is not Production status. A module is promoted only after the applicable contract, UX, parity, hardware/physical-device and ODK/XLSForm checks have passed.

The Master Book deliberately does not maintain a hand-written list of every Production method because such a list would drift from runtime metadata.

## 24.2 Project-wide roadmap directions

Current project-wide directions distilled from the former roadmap are:

- keep the Dashboard minimal and use module control-centres only where they remain compact;
- continue improving plain-language capability/preset/protocol/ODK-form discovery;
- finish robust origin-aware closeout for protocols and schedules;
- implement typed protocol pipes on top of canonical structured results before building a visual editor;
- make Device Registry more live/observable;
- improve shared offline-resource management and distinguish intentional downloads from disposable cache;
- continue Workbench API-definition/editor tooling and shared `ResultTree`-based online-data infrastructure;
- keep Android desktop widgets as first-class launch origins;
- evaluate sideload-first capability packs only after measuring APK-size drivers and without undermining the single capability contract;
- consider a future declarative capability-package format for capabilities expressible without new Android code;
- keep online provider definitions generic rather than turning each public API into a bespoke capability.

## 24.3 Capability packaging direction

A future modular-delivery design may use a small permanent core plus signed/version-compatible capability packs distributed outside Google Play. Such packs would be precompiled; compilation on the Android device is not part of the design. Any split-APK solution must respect Android package/signing/version constraints.

This is a roadmap direction, not the current module handoff contract. Current module development continues to use one self-contained module folder in the app source tree.

## 24.4 Known cross-project implementation gaps

Known cross-project concerns retained from current documentation include:

- capability lane/status displays must derive consistently from current metadata;
- Home/Done routing has had regressions and remains subject to the launch-origin rules in this book;
- protocol/schedule completion should achieve the same clean final-result semantics as single-capability runs;
- online-data/provider UX continues to evolve around the generic API-definition model;
- documentation sprawl is being retired by this v1.06 consolidation.

Old module-specific bugs and feature ideas from the former root roadmap are not reproduced here; they belong with their owning module when still relevant.

# 25. Troubleshooting and diagnostic reasoning

## A capability exists but is missing from a surface

Treat this as a parity/discovery problem first. Dashboard, direct capability discovery, presets, protocols and ODK SHOULD derive from the same canonical module/method metadata rather than separate lists.

## ODK returns the wrong fields

Check the canonical method ID, requested return namespace, field-name collisions, `methodmesh_full_json`, and whether the module actually declares the output. Example XLSForms are demonstrations, not a second schema.

## An XLSForm will not validate/upload

Use the batch XLSForm validator first, then inspect provider diagnostics. A Central/Kobo HTTP failure is not a substitute for local validation. Preserve the exact form/module/error attribution and copyable diagnostic report.

## Binary output cannot be opened

Verify a content URI is returned with `ClipData` and read grants and that the XLSForm target field is an attachment-compatible question type.

## Done goes to the wrong place

Check launch-origin preservation: app/preset -> Dashboard; widget -> desktop; ODK -> ODK; protocol/schedule -> their runners.

## A module is present in source but not Production

That can be intentional. Presence in source does not imply promotion. Inspect current method metadata and review evidence.

## Documentation conflicts

The Master Book wins for project-wide doctrine. Current source/tests determine implementation facts. Module docs determine module-local facts. Archived/website/prototype material is not a competing authority.

# 26. Contributor/AI-chat quick rules

If another AI chat is writing a capability, give it these rules:

1. Work from the GitHub repo and this Master Book.

2. Return exactly one module folder - not a whole-app/repository tree, not app scaffolding, and not unrelated shared files. State any genuinely required framework integration separately.

3. Make that returned folder itself `<module_name>/`. If zipped, the ZIP must contain exactly that one top-level folder - no `app/`, `modules/`, repository wrapper, or second handoff wrapper.

4. Follow the canonical return structure in Section 6: module entry point and capability code at the folder root; all documentation and ODK examples under `docs/`; optional repositories/helpers only when justified; no build/IDE/OS junk.

5. For a multi-capability module, include clearly identifiable per-capability method implementations and documentation/ODK coverage. Dashboard aggregation must not collapse those capabilities into a single inaccessible private implementation.

6. Put docs and ODK example XLSForm(s) inside that folder.

7. Do not edit HomeScreen for capability-specific logic.

8. Do not add central capability registration.

9. Declare settings with MethodSetting .

10. Use toggles/dropdowns/checkboxes for fixed choices.

11. Implement one canonical capability contract and project it into every required surface; do not implement separate dashboard/native/preset/protocol/ODK versions.

12. Always provide a dashboard presence, but never make the dashboard the only way to invoke a capability.

13. Expose every individual capability independently for preset creation and protocol creation, even when the dashboard aggregates several capabilities.

14. Make every declared capability and every declared output available to the ODK/XLSForm roundtrip; examples are not allow-lists.

15. Keep method IDs and input/output field names, types and semantics canonical across all surfaces; only presentation/transport may differ.

16. Return beef first, JSON/audit second; presentation selectivity must not remove obscure outputs from the contract.

17. Give production capabilities a polished, task-relevant native interface. Generic MethodSetting forms are infrastructure, not an excuse for debug-looking UI where the task benefits from a purpose-built surface.

18. Keep current calculated/acquired results on the same capability screen and update them live or immediately after the relevant action. Do not default to settings -> Go -> generic result page.

19. Make every displayed calculated/returned scalar or text value tap-to-copy using its primary clipboard projection.

20. Use Commit as the native finalisation boundary. Commit freezes the canonical payload and reveals share/save/copy/JSON/Done actions on the same screen where feasible.

21. Preserve launch origin. App/preset Home/Done returns to the MethodMesh dashboard; widget closeout returns to the Android desktop; ODK Commit/Cancel returns to ODK; protocol/schedule closeout returns to its runner.

22. Preserve working and committed state across rotation where relevant and do not auto-save internal files by default.

23. Include attribution/permissions/offline notes.

24. Start as Development unless explicitly promoted.

25. If it cannot build the app, put the result in incoming_capability_prototypes/ .

26. Expect Work-mode review before admission.

27. Before handoff, verify dashboard + direct capability + preset + protocol + ODK are all projections of the same contract and that none has a private feature or output schema.

# 27. The MethodMesh taste test

Before merging any change, ask:

Is there a dashboard presence without making the dashboard the
implementation boundary? Can every individual capability still be
selected for a preset? Can every individual capability still be selected
as a protocol step? Can ODK invoke the same method and request every
declared output? Are all of those surfaces using one canonical contract
rather than parallel schemas? Is the handoff itself exactly one
self-contained `<module_name>/` folder with code at its root and all
module docs/ODK examples beneath `docs/`? If zipped, does it extract to
exactly that one top-level folder? Are exceptional shared-framework
changes called out separately rather than embedded in a fake whole-app
tree?

Does the native capability look and behave like the task it performs rather than a generated debug form? Are current outputs visible in-place while the user works? Can every visible calculated/returned value be tapped to copy? Does Commit finalise the current payload and reveal export actions without an unnecessary generic result page? Does Home/Done return to the correct destination for app, widget, ODK, protocol and schedule origins?

Does this make the phone more useful in the field? Does it keep the UI
simple? Does it return the beef first? Does it keep audit data available
without making it obnoxious? Does it work offline where it reasonably
should? Does it respect ODK’s constraints? Does it avoid storing things
MethodMesh does not need to store? Does it preserve the golden rule?
Does it compose with presets, protocols, schedules and widgets? Would a
tired fieldworker understand what to press next?

If yes, it probably belongs.

If no, it probably needs more MethodMesh-ing.


# 28. Normative requirement register

These IDs are stable anchors for module reviews, migration scorecards and tests. They summarize - rather than replace - the detailed normative prose.

## Documentation

- **`MM-DOC-001`** - Master Book is the sole normative project-wide documentation resource.
- **`MM-DOC-002`** - Documentation-like artefacts are classified as project-wide, module-owned, or archive/legacy/projection.
- **`MM-DOC-003`** - Project-wide changes update the Master Book; module-local changes stay with the module.

## Architecture

- **`MM-ARCH-001`** - Shared MethodMesh UI/infrastructure contains no capability-specific special cases unless the change is genuinely generic.
- **`MM-ARCH-002`** - Implementations preserve the Entity/Observation/Assertion/Method/Intent conceptual distinctions.
- **`MM-ARCH-003`** - MethodMesh remains knowledge-first.
- **`MM-ARCH-004`** - Knowledge, orchestration and implementation concerns remain separated.
- **`MM-ARCH-005`** - Capability discovery is metadata/registry driven rather than a hand-written central list.
- **`MM-ARCH-006`** - Public contracts evolve through extension rather than casual breaking changes.
- **`MM-ARCH-007`** - Conceptual meaning remains implementation/technology independent.

## Capability

- **`MM-CAP-001`** - One canonical capability contract is projected through all applicable surfaces.
- **`MM-CAP-002`** - Stable method IDs, input/output names, types and semantics are preserved unless a breaking migration is explicitly justified.
- **`MM-CAP-003`** - Dashboard aggregation never becomes the only invocation path for an individual capability.
- **`MM-CAP-004`** - Capability-specific code, UI, settings, docs and examples remain module-owned.

## Surfaces

- **`MM-SURF-001`** - Every applicable capability remains discoverable for direct use, presets, protocols and ODK/XLSForm.
- **`MM-SURF-002`** - Top-level Back from a non-Dashboard section returns to Dashboard; nested Back unwinds nested state first.

## UX

- **`MM-UX-001`** - Production UI is task-specific where the task benefits from a purpose-built surface.
- **`MM-UX-002`** - Current working results are displayed in-place/live where feasible.
- **`MM-UX-003`** - Useful displayed scalar/text return values are tap-to-copy.
- **`MM-UX-004`** - Commit freezes the canonical payload and separates working from committed state.
- **`MM-UX-005`** - Editing after Commit does not silently mutate the committed payload.
- **`MM-UX-006`** - Meaningful working/committed state survives ordinary lifecycle changes.
- **`MM-UX-007`** - Closeout returns to the correct launch origin.

## Safety

- **`MM-SEC-001`** - Irreversible deletion of persistent objects requires explicit confirmation.
- **`MM-SEC-002`** - Passwords/secrets are not embedded in exports/logs/docs/results; provider passwords are not persisted when a secure session/token mechanism is available.

## ODK

- **`MM-ODK-001`** - ODK/XLSForm invokes the same canonical method contract as native/preset/protocol use.
- **`MM-ODK-002`** - Every declared output remains addressable through ODK transport where technically representable.
- **`MM-ODK-003`** - Interactive ODK launches return directly to the calling form after Commit/Cancel.
- **`MM-ODK-004`** - Binary returns use proper content URIs, ClipData/read grants and attachment-compatible form fields.
- **`MM-ODK-005`** - ODK owns returned study data unless a capability has an independent persistence reason.

## XLSForm

- **`MM-XLS-001`** - Module-owned docs may contain zero, one or many XLSForms; each discovered form is catalogued independently.
- **`MM-XLS-002`** - Canonical example filename is example_odk_<purpose>.xlsx; legacy names are warned, not silently excluded.
- **`MM-XLS-003`** - form_id is a stable external identity and is never silently renamed for style.
- **`MM-XLS-004`** - Batch validation reports form/module-specific structural, JavaRosa/ODK and naming findings and supports copy/export.
- **`MM-XLS-005`** - Central access state, Kobo deployment state and collector-device state are represented separately.

## Outputs

- **`MM-OUT-001`** - Native presentation may hide metadata but cannot remove canonical outputs from the contract.
- **`MM-OUT-002`** - methodmesh_full_json remains available where complete structured/audit return is expected.
- **`MM-OUT-003`** - Hashes identify the final bytes/canonical content they claim to identify.

## Offline

- **`MM-OFF-001`** - Capabilities work offline where reasonably possible; online dependencies are explicit.

## Online data

- **`MM-API-001`** - External APIs are declarative definitions consumed by generic execution infrastructure rather than bespoke shared-UI special cases.
- **`MM-API-002`** - Structured provider responses remain ResultTree-like structures and are not prematurely flattened.
- **`MM-API-003`** - Credentials are referenced/secured and not exported or logged in plaintext.
- **`MM-API-004`** - Location disclosure is minimized to the precision required by the task.

## Android surfaces

- **`MM-AND-001`** - Modules publish generic surface state/actions; shared Android infrastructure owns platform rendering/lifecycle/permissions.
- **`MM-AND-002`** - Persistent/urgent surfaces remain quiet by default and require deliberate configuration/eligibility.

## Protocols

- **`MM-PROT-001`** - Protocols invoke canonical capabilities/presets and preserve per-step results and explicit operator rails.
- **`MM-PROT-002`** - Protocol dataflow uses typed references/transforms; arbitrary code execution is not an allowed transform mechanism.

## Scheduling

- **`MM-SCHED-001`** - Schedules use canonical targets/contracts and preserve origin-aware completion.

## Modules

- **`MM-MOD-001`** - Canonical handoff is exactly one clean <module_name>/ root, not a whole application tree.
- **`MM-MOD-002`** - New/unverified prototypes do not break auto-discovery; admission follows build/review.

## Review

- **`MM-REV-001`** - Review begins with capability inventory and preserves established contracts unless genuinely broken.
- **`MM-REV-002`** - Review verifies dashboard/direct/preset/protocol/ODK parity plus schedules/widgets where applicable.
- **`MM-REV-003`** - Supplied XLSForms are part of module completeness and must be discovered/validated.

## Testing

- **`MM-TEST-001`** - Contract-boundary tests verify parity and output semantics generically where feasible.
- **`MM-TEST-002`** - Promotion requires representative native/preset/protocol/ODK and relevant device/hardware validation.

# 29. Source-specification reconciliation notes

This edition absorbs the July 2026 specification set identified by `SPEC_STATUS.md`. The following source inconsistencies are recorded rather than silently invented away:

- `MethodMesh JSON Representation v0.02.md` is named as v0.02 by `SPEC_STATUS.md` and its filename, but its internal version line says **0.01 (Draft)** and its shared-envelope example includes an `ros:` identifier prefix. v1.06 adopts the object structures as the current source snapshot while recording that metadata/prefix inconsistency for later cleanup.
- `MethodMesh_Trait_Registry_0.02.md` in the reviewed corpus is empty. This book therefore does not invent a Trait schema. Traits remain a referenced supporting concept pending an explicit registry definition.
- `RIL_Core_Verbs_v0.02.md` fully defines `CREATE` but leaves `OBSERVE` and `MEASURE` as placeholders. RIL v0.03 lists typical action examples but states that the complete permitted vocabulary comes from the Core Verbs specification. The core-verb registry is therefore incomplete; implementations MUST NOT treat this book as inventing the missing definitions.
- Several absorbed specification documents still label themselves Draft. Their conceptual content is retained here because `SPEC_STATUS.md` marked those versions current. The Master Book v1.06, not the old standalone draft label, is now the project-wide authority.

Open questions preserved from those specifications remain informative research/design questions rather than hidden conformance requirements.

# 30. Absorbed conceptual and interoperability specifications

The following material was previously maintained as separate project-wide specifications. It is reproduced inside the Master Book so those standalone files can be archived without losing their substantive content. Where introductory wording in an absorbed source referred to another separate specification as authoritative, v1.06 supersedes that document hierarchy: all of the material now sits under this book.

# Appendix A. MethodMesh Philosophy (absorbed source v0.01)

## Purpose

MethodMesh is founded on the principle that scientific research is fundamentally concerned with understanding the world through evidence.

Rather than modelling research as a collection of software workflows or datasets, MethodMesh models the process by which scientific understanding is created, refined and preserved.

This document describes the philosophical principles that underpin the platform.

---

## The Scientific Process

Research begins with things that exist in the world.

Researchers make claims about those things.

Those claims are supported by evidence obtained through observation.

Scientific understanding evolves as new evidence strengthens, refines or replaces previous claims.

MethodMesh represents this process directly.

```text
                    Entity
                  (the thing)

                       │
             described by

                       ▼

                  Assertion
                 (the claim)

                       ▲
            supported by

                       │

                 Observation
                 (the evidence)
```

Methods obtain observations.

Observations support assertions.

Assertions describe entities.

Knowledge emerges through the continual accumulation of evidence-supported assertions.

---

## Principles

### Reality precedes knowledge

Research begins with entities that exist independently of the researcher.

MethodMesh represents those entities rather than creating them.

---

### Knowledge is provisional

Scientific understanding is never absolute.

Assertions represent the current state of knowledge rather than immutable truth.

Assertions may be strengthened, revised, superseded or withdrawn as new evidence becomes available.

---

### Evidence underpins knowledge

Assertions should be supported by observations wherever appropriate.

The quality of scientific knowledge depends upon the quality of the supporting evidence.

---

### Methods generate evidence

Methods do not create knowledge directly.

They obtain observations from which knowledge is derived.

---

### Provenance creates trust

Every observation and assertion should retain sufficient provenance to permit independent interpretation, verification and reproduction.

Trust arises through transparency rather than authority.

---

### Knowledge evolves

MethodMesh records the evolution of scientific understanding.

Knowledge grows through the accumulation of evidence rather than replacement of previous records.

Historical understanding remains an important part of the scientific record.

---

### Implementation is secondary

The conceptual model is independent of programming language, storage technology and software architecture.

Graphs, relational databases, document stores and append-only logs are implementation choices rather than conceptual foundations.

---

## Vision

MethodMesh is not intended simply to execute research.

Its purpose is to represent scientific understanding in a form that is transparent, reproducible and extensible across disciplines.

By separating entities, assertions and observations, MethodMesh provides a common conceptual language capable of supporting laboratory science, epidemiology, social science, environmental research, engineering and future disciplines that have yet to emerge.

The platform therefore aims to become not only an operating system for research, but a durable representation of scientific knowledge itself.

# Appendix B. MethodMesh Conceptual Model (absorbed source v0.03)

## 1. Purpose

The MethodMesh Conceptual Model defines the fundamental concepts used to represent scientific research independently of implementation, programming language or storage technology.

MethodMesh is not primarily a workflow engine or data collection platform.

It is a conceptual framework for representing scientific understanding.

The model distinguishes between:

- the world being studied;
- the claims made about that world;
- the evidence supporting those claims.

All higher-level components of MethodMesh derive from these concepts.

---

## 2. The Scientific Model

Scientific research begins with things that exist in the world.

Researchers make claims about those things.

Those claims are supported by observations obtained using scientific methods.

MethodMesh represents this process directly.

```text
                    ENTITY
                  (the thing)

                       │
             described by

                       ▼

                  ASSERTION
                 (the claim)

                       ▲
            supported by

                       │

                 OBSERVATION
                 (the evidence)
```

The scientific process extends this model by obtaining observations through repeatable methods.

```text
                 OBSERVATION

                       ▲

               produced by

                       │

                    METHOD
```

MethodMesh therefore distinguishes four fundamental concepts:

- **Entities** represent the things being studied.
- **Assertions** represent what is currently believed about those things.
- **Observations** provide evidence supporting those beliefs.
- **Methods** obtain observations.

Scientific knowledge emerges through the accumulation of evidence-supported assertions.

---

## 3. Conceptual Layers

The MethodMesh conceptual model is organised into five layers.

### Layer 1 – Reality

Reality consists of the entities that become the subjects of research.

MethodMesh does not create these entities.

It represents them.

### Layer 2 – Knowledge

Knowledge consists of assertions concerning entities.

Assertions describe current scientific understanding.

Observations provide the evidence supporting those assertions.

Knowledge evolves through the continual refinement of assertions as additional evidence becomes available.

### Layer 3 – Acquisition

Methods obtain observations.

Methods do not create knowledge directly.

They create evidence from which knowledge is derived.

### Layer 4 – Orchestration

Research activities are coordinated through operational concepts including:

- Intent
- Policy
- Signals

These concepts govern execution rather than scientific understanding.

### Layer 5 – Trust

Provenance explains the origin, history and justification of observations and assertions.

It provides transparency, traceability and reproducibility throughout the research lifecycle.

---

## 4. Core Concepts

### Entity

An Entity is defined as:

> **A distinct object that may become the subject of observation, description, measurement or reasoning within a research context.**

Entities possess identity.

Entities may exist in one of three Modes:

- Physical
- Digital
- Conceptual

Entities may participate in assertions.

---

### Assertion

An Assertion is defined as:

> **A timestamped claim describing a characteristic, state or relationship concerning one or more Research Entities.**

Assertions represent scientific understanding rather than immutable truth.

Assertions may be supported, strengthened, revised, superseded or withdrawn as new observations become available.

---

### Observation

An Observation is defined as:

> **Evidence obtained through the application of a Method.**

Observations support assertions.

Observations should retain sufficient provenance to permit independent interpretation and verification.

---

### Method

A Method is defined as:

> **A repeatable procedure used to obtain observations.**

Methods may involve:

- human activity;
- laboratory procedures;
- computational analysis;
- simulation;
- data transformation.

Methods create observations.

Observations support assertions.

Assertions describe entities.

---

## 5. Supporting Concepts

The following concepts support the core scientific model.

| Concept | Purpose |
|----------|---------|
| Intent | Requests one or more Methods |
| Policy | Constrains the execution of Methods |
| Provenance | Explains the origin and history of Observations and Assertions |
| Signal | Communicates changes to other components |

These concepts support the generation, governance and communication of scientific knowledge but are not themselves part of the core knowledge model.

---

## 6. Concept Relationships

The central MethodMesh model is summarised below.

```text
                    Entity
                       ▲
               described by
                       │
                  Assertion
                       ▲
               supported by
                       │
                 Observation
                       ▲
                produced by
                       │
                    Method
```

Supporting concepts interact with this model as follows.

```text
Intent ─────────────► Method

Policy ─────────────► Method

Provenance ─────────► Observation
               └────► Assertion

Signal ─────────────► External systems
```

---

## 7. Design Principles

The MethodMesh Conceptual Model follows the following principles.

### Scientific

The model represents scientific understanding rather than software implementation.

### Entity-centred

Research begins with identifiable entities.

Everything else exists to describe, understand or investigate those entities.

### Evidence-based

Assertions should be supported by observations wherever appropriate.

### Temporal

Scientific understanding changes over time.

MethodMesh records the evolution of understanding rather than replacing previous knowledge.

### Minimal

The model defines the smallest set of concepts necessary to represent scientific research.

### Extensible

New scientific disciplines should be representable without modification of the conceptual model.

### Implementation-independent

The conceptual model is independent of storage technology, programming language and execution environment.

---

## 8. Open Questions

The following areas remain under active development.

- Should Properties remain a separate primitive or become specialised Assertions?
- What canonical assertion vocabulary should MethodMesh define?
- How should uncertainty, confidence and evidence strength be represented?
- Which concepts belong to the conceptual model and which belong exclusively to the execution architecture?

---

## 9. Summary

MethodMesh represents scientific understanding rather than scientific data.

Research begins with Entities.

Researchers make Assertions about those Entities.

Observations provide evidence supporting those Assertions.

Methods obtain Observations.

The continual accumulation of evidence-supported Assertions represents the evolving scientific understanding of the research world.

# Appendix C. MethodMesh Architecture Standard (absorbed source v1.02)

## 1. Purpose

The MethodMesh Architecture Standard defines the engineering principles governing the implementation of MethodMesh.

It complements, but does not replace, the MethodMesh Philosophy, Conceptual Model and Registry specifications.

Where those documents define *what* MethodMesh represents, the Architecture Standard defines *how* those concepts should be implemented.

This standard establishes architectural consistency across applications, services and libraries forming part of the MethodMesh ecosystem.

---

## 2. Relationship to Other Standards

The MethodMesh documentation is organised into complementary layers.

```text
MethodMesh Philosophy
        │
MethodMesh Conceptual Model
        │
Registry Specifications
(Entity, Observation, Assertion, Intent, ...)
        │
Architecture Standard
        │
JSON Object Model
        │
Software Implementations
```

Each layer depends upon the layers above it.

Architectural decisions shall remain consistent with the Conceptual Model and Registry specifications.

Implementation details shall not redefine conceptual meaning.

---

## 3. Scope

This standard defines:

- architectural principles;
- service boundaries;
- implementation patterns;
- interoperability requirements;
- extension mechanisms;
- engineering conventions.

This standard does not redefine scientific concepts already specified elsewhere.

## 4. Architectural Principles

All MethodMesh implementations shall conform to the following architectural principles.

### Knowledge-first

MethodMesh is fundamentally a knowledge system.

Applications, user interfaces, workflows and services exist to create, transform, query and communicate scientific knowledge.

The core knowledge model consists of:

- Entities
- Observations
- Assertions

These concepts are defined by their respective Registry specifications and shall not be redefined by implementations.

---

### Separation of Concerns

MethodMesh separates conceptual knowledge from orchestration and implementation.

```text
Knowledge Layer
---------------
Entity
Observation
Assertion

Orchestration Layer
-------------------
Intent
RIL
Method Selection

Implementation Layer
--------------------
Services
Applications
Storage
Transport
User Interfaces
```

Each layer communicates through well-defined interfaces.

Responsibilities shall not leak between layers.

---

### Registry-driven

Canonical behaviour shall be defined by Registry specifications rather than embedded within application code.

Implementations should consume Registry definitions rather than duplicate conceptual knowledge.

---

### Service-oriented

MethodMesh capabilities shall be implemented as independent services wherever practical.

Services communicate through canonical MethodMesh objects rather than direct application-specific interfaces.

Services should remain independently deployable and reusable across projects.

---

### Technology-independent

The conceptual architecture shall remain independent of programming language, database technology, cloud provider or deployment model.

Alternative implementations should remain interoperable provided they conform to the Registry specifications and Architecture Standard.

---

### Extensible

MethodMesh shall support extension through new Registry entries, Methods, Services and Applications without modification of the core conceptual architecture.

Extensions should compose with the existing architecture rather than replace it.

## 5. Architectural Components

MethodMesh implementations are constructed from a small number of architectural component types.

Each component has a clearly defined responsibility and communicates through canonical MethodMesh objects.

### Applications

Applications provide user-facing functionality.

Applications may create Intents, display knowledge, visualise data and coordinate user interaction.

Applications should not embed scientific knowledge or implementation-specific business logic that belongs elsewhere.

---

### Services

Services implement discrete capabilities.

A Service accepts one or more canonical MethodMesh objects as input and produces one or more canonical MethodMesh objects as output.

Services should remain independently deployable and reusable.

---

### Methods

Methods define repeatable scientific or operational procedures.

Methods fulfil Intents by performing work that may produce one or more Observations.

Methods are implementation-independent and may be realised by software, hardware, people or combinations thereof.

---

### Registries

Registries define the canonical vocabulary used throughout MethodMesh.

Applications and Services should consume Registry definitions rather than duplicate them.

Registries form the authoritative source of conceptual meaning.

---

### Knowledge Store

The Knowledge Store preserves MethodMesh objects and their relationships.

Implementations may use relational, document, graph or hybrid storage technologies provided the conceptual model is preserved.

The storage technology is an implementation decision rather than an architectural requirement.

---

### Interfaces

Interfaces provide communication between architectural components.

Interfaces should exchange canonical MethodMesh objects rather than application-specific data structures.

Implementations may expose APIs, message queues, files or other transport mechanisms without changing the underlying conceptual architecture.


## 6. Interoperability

MethodMesh is designed as an interoperable ecosystem rather than a single application.

Independent applications, services and organisations should be able to exchange knowledge and capabilities without requiring shared implementation technologies.

### Canonical Objects

All communication between architectural components should use canonical MethodMesh objects.

Canonical objects preserve conceptual meaning independently of implementation.

---

### Stable Interfaces

Public interfaces should remain stable across implementation revisions wherever practical.

Evolution should occur through extension rather than breaking existing interfaces.

---

### Loose Coupling

Applications should communicate through canonical interfaces rather than direct implementation dependencies.

No application should require knowledge of another application's internal architecture.

---

### Capability Discovery

Services should expose their capabilities through declarative metadata.

Applications should discover available capabilities dynamically rather than relying on hard-coded integrations.

---

### Registry Conformance

Applications and services shall interpret canonical concepts consistently with the relevant Registry specifications.

Registries remain the authoritative source of conceptual meaning.

---

### Extensible Ecosystem

Third-party applications, methods and services should be able to participate within the MethodMesh ecosystem without modification of the core architecture.

Compliance with the Architecture Standard and Registry specifications is sufficient for interoperability.

## 7. Extension Model

MethodMesh is designed to evolve through extension rather than modification of its core architecture.

Extensions shall conform to the Conceptual Model, Registry specifications and Architecture Standard.

Extensions may include:

- new Applications;
- new Services;
- new Methods;
- new Registry entries;
- new Entity types;
- new Observation types;
- new Assertion types;
- new RIL verbs and domain vocabularies where appropriate.

Extensions shall not alter the conceptual meaning of existing canonical concepts.

Backward compatibility should be maintained wherever practical.

---

## 8. Conformance

An implementation conforms to the MethodMesh Architecture Standard if it:

- preserves the MethodMesh Conceptual Model;
- interprets Registry specifications consistently;
- exchanges canonical MethodMesh objects;
- maintains separation between knowledge, orchestration and implementation;
- supports interoperability through stable interfaces;
- remains extensible without modification of the core architecture.

Conformance does not require any particular programming language, database technology, operating system or deployment model.

Alternative implementations are encouraged provided they preserve the architectural principles defined by this standard.

---

## 9. Summary

The MethodMesh Architecture Standard defines the engineering principles governing the implementation of MethodMesh.

Conceptual meaning is defined by the Philosophy, Conceptual Model and Registry specifications.

The Architecture Standard defines how those concepts should be realised as interoperable software systems.

MethodMesh separates:

- conceptual knowledge;
- orchestration;
- implementation.

This separation enables independent evolution of scientific concepts, engineering infrastructure and software implementations while preserving interoperability across the MethodMesh ecosystem.

# Appendix D. Canonical JSON Object Model (absorbed current source labelled v0.02)

## 1. Purpose

The MethodMesh JSON Object Model defines the canonical JSON representation of core MethodMesh objects.

It translates the MethodMesh Conceptual Model and Registry specifications into implementation-facing structures that can be exchanged between applications, services, methods and storage systems.

The Object Model does not redefine conceptual meaning.

Conceptual meaning is defined by the Philosophy, Conceptual Model and Registry specifications.

This specification defines how those concepts are represented in JSON for interoperability.

---

## 2. Scope

This specification defines JSON structures for:

- Entity
- Observation
- Assertion
- Intent
- Method reference
- Provenance reference
- Policy reference
- Registry reference

It also defines shared conventions for:

- identifiers;
- timestamps;
- object types;
- metadata;
- references;
- provenance links;
- extension fields.

This specification does not define storage technology, API behaviour, database schema or runtime execution.

---

## 3. Design Principles

### Conceptual alignment

Every JSON object must correspond to a concept defined in the MethodMesh Conceptual Model or Registry specifications.

### Interoperability

Objects should be exchangeable between applications, services and storage systems without loss of conceptual meaning.

### Minimal core

Each object should define a small required core with optional extension fields.

### Explicit references

Relationships between objects should be represented using explicit object references rather than implicit naming conventions.

### Provenance-ready

Objects should support links to provenance without requiring every provenance model detail to be embedded directly.

### Extensible

Domain-specific fields should be added through namespaced extensions rather than modification of the core object model.

## 4. Shared Object Envelope

All MethodMesh JSON objects use a common envelope.

The envelope provides consistent identity, typing, versioning and extension behaviour across all object types.

Individual object specifications define the contents of the `attributes`, `relationships` and `extensions` fields.

```json
{
  "id": "ros:entity:participant-001",
  "object_type": "entity",
  "schema_version": "0.01",
  "created_at": "2026-07-03T14:30:00Z",
  "updated_at": "2026-07-03T14:30:00Z",
  "attributes": {},
  "relationships": {},
  "provenance": [],
  "extensions": {}
}
```

#### 4.1 Common Object Fields

Every MethodMesh object contains a common set of fields that provide identity, typing and lifecycle information.

| Field | Required | Description |
|---------|:--------:|-------------|
| **id** | Yes | Unique identifier for the object. |
| **object_type** | Yes | Canonical MethodMesh object type. |
| **schema_version** | Yes | Version of the JSON Object Model used by the object. |
| **created_at** | Yes | Timestamp when the object was created. |
| **updated_at** | No | Timestamp when the object was last modified. |
| **attributes** | Yes | Object-specific descriptive properties. |
| **relationships** | No | References to other MethodMesh objects. |
| **provenance** | No | References describing the origin or history of the object. |
| **extensions** | No | Additional namespaced fields defined outside the core specification. |

The common object fields provide a consistent structure across all MethodMesh object types while allowing each object type to define its own attributes and relationships.

---

#### 4.2 Canonical Object Types

The JSON Object Model defines canonical representations for the core concepts of the MethodMesh conceptual model.

The initial object types are:

| Object Type | Represents |
|-------------|------------|
| **Entity** | A thing that may become the subject of scientific investigation. |
| **Observation** | Evidence acquired about one or more Entities. |
| **Assertion** | Scientific understanding describing one or more Entities. |
| **Intent** | A declarative request for research work to be performed. |
| **Method** | A repeatable procedure capable of fulfilling an Intent. |
| **Policy** | Rules or constraints governing execution or behaviour. |
| **Provenance** | Information describing the origin, lineage or history of an object. |
| **Registry Entry** | A canonical definition contained within a MethodMesh Registry. |

Additional object types may be introduced in future versions provided they remain consistent with the MethodMesh Conceptual Model and Architecture Standard.

## 5. Canonical Object Definitions

The following sections define the canonical JSON representation of each MethodMesh object type.

The examples are illustrative rather than exhaustive.

Implementations may include additional fields provided they remain consistent with this specification and the MethodMesh Architecture Standard.

---

### 5.1 Entity Object

The Entity Object represents a single Entity within the MethodMesh knowledge graph.

It provides identity together with the descriptive attributes and relationships required to reference the Entity from other MethodMesh objects.

#### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"entity"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Entity properties. |
| relationships | No | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

#### Minimal Representation

```json
{
  "id": "entity:participant-001",
  "object_type": "entity",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:30:00Z",
  "attributes": {
    "entity_type": "Person"
  }
}
```

#### Notes

Scientific knowledge describing an Entity is represented through Assertion objects rather than embedded directly within the Entity itself.

### 5.2 Observation Object

The Observation Object represents evidence acquired, derived or simulated within MethodMesh.

It records observation content together with the Entity being observed, the property or phenomenon of interest, and the Method used to obtain the Observation.

#### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"observation"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Observation-specific fields. |
| relationships | Yes | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

#### Minimal Representation

```json
{
  "id": "observation:height-001",
  "object_type": "observation",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:35:00Z",
  "attributes": {
    "observation_mode": "direct",
    "property": "height",
    "content": {
      "value": 172,
      "unit": "cm"
    }
  },
  "relationships": {
    "entity": "entity:participant-001",
    "method": "method:height-measurement"
  }
}
```

### 5.3 Assertion Object

The Assertion Object represents a timestamped claim describing one or more Entities.

It records the claim being made, the Entity or Entities described by that claim, and any Observations that may support it.

#### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"assertion"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Assertion-specific fields. |
| relationships | Yes | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

#### Minimal Representation

```json
{
  "id": "assertion:height-001",
  "object_type": "assertion",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:40:00Z",
  "attributes": {
    "assertion_type": "has_value",
    "property": "height",
    "claim": {
      "value": 172,
      "unit": "cm"
    }
  },
  "relationships": {
    "entity": "entity:participant-001",
    "supported_by": [
      "observation:height-001"
    ]
  }
}
```
### 5.4 Intent Object

The Intent Object represents a declarative request for research work to be performed.

An Intent expresses *what* is requested rather than *how* it should be carried out. MethodMesh fulfils an Intent by selecting and executing one or more appropriate Methods.

#### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"intent"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Intent-specific fields. |
| relationships | No | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

#### Minimal Representation

```json
{
  "id": "intent:measure-height-001",
  "object_type": "intent",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:45:00Z",
  "attributes": {
    "verb": "measure",
    "target": "entity:participant-001",
    "property": "height"
  }
}
```

#### Notes

An Intent does not itself perform work.

Execution is performed by one or more Method objects capable of fulfilling the requested action.

The outcome of an Intent may include one or more Observation objects, which in turn may support one or more Assertions.

### 5.5 Method Object

The Method Object represents a repeatable procedure capable of fulfilling one or more Intents.

A Method defines how work may be performed. Method execution may produce one or more Observation objects.

#### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"method"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Method-specific fields. |
| relationships | No | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

#### Minimal Representation

```json
{
  "id": "method:height-measurement",
  "object_type": "method",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:50:00Z",
  "attributes": {
    "method_type": "measurement",
    "name": "Height measurement"
  }
}
```

## 6. Common Relationship Patterns

MethodMesh objects are connected through explicit object identifiers.

Relationships should be represented using stable field names inside the `relationships` object.

The following relationship names are canonical for the initial JSON Object Model.

| Relationship | Source Object | Target Object | Meaning |
|---|---|---|---|
| `entity` | Observation, Assertion, Intent | Entity | The Entity being observed, described or targeted. |
| `method` | Observation | Method | The Method used to produce the Observation. |
| `supported_by` | Assertion | Observation | Observations that may support the Assertion. |
| `requests` | Intent | Method | Method requested or selected to fulfil the Intent. |
| `produces` | Method | Observation | Observation produced by a Method. |
| `supersedes` | Assertion, Intent | Assertion, Intent | Earlier object replaced or superseded by this object. |
| `derived_from` | Observation | Observation | Source Observation used to derive another Observation. |

Relationships should use object identifiers rather than embedded objects.

This keeps objects independently serialisable while allowing the MethodMesh graph to be reconstructed from object references.

### Example

```json
{
  "relationships": {
    "entity": "entity:participant-001",
    "method": "method:height-measurement"
  }
}
```

## 7. Implementation Considerations

The JSON Object Model defines a canonical representation of MethodMesh objects for interoperability.

It does not prescribe implementation technology, storage architecture or transport protocols.

Implementations may:

- store objects in relational, document, graph or hybrid databases;
- exchange objects using REST, GraphQL, message queues, files or other protocols;
- extend objects through the `extensions` field;
- introduce additional object types consistent with the MethodMesh Conceptual Model.

Implementations should not alter the semantic meaning of canonical object types or relationships defined by this specification.

---

## 8. Conformance

An implementation conforms to the MethodMesh JSON Object Model if it:

- represents canonical MethodMesh concepts using the object structures defined in this specification;
- preserves object identity through stable identifiers;
- maintains explicit object relationships;
- preserves compatibility with the MethodMesh Conceptual Model, Registry specifications and Architecture Standard;
- supports extension without modification of the core object definitions.

Conformance does not require any particular programming language, database, messaging protocol or software framework.

---

## 9. Summary

The MethodMesh JSON Object Model provides a canonical JSON representation of the core MethodMesh concepts.

It bridges the gap between the conceptual architecture and software implementation by defining how MethodMesh objects are serialised for storage, exchange and processing.

The Object Model preserves the separation between:

- **Entities**, representing the things under study;
- **Observations**, representing scientific evidence;
- **Assertions**, representing scientific understanding;
- **Intents**, representing requested work; and
- **Methods**, representing repeatable procedures.

Together with the Registry specifications and Architecture Standard, the JSON Object Model provides the implementation foundation for interoperable MethodMesh applications and services.

# Appendix E. Research Intent Language - RIL (absorbed source v0.03)

## Abstract

The Research Intent Language (RIL) is a platform-independent language for requesting research operations.

Rather than describing how an operation should be performed, RIL describes **what** should happen, **when** it should occur, **where** it should occur, **how** execution should be governed, and **what result** should be returned.

RIL forms the interoperability layer of the MethodMesh platform. It provides a common contract between research workflows, applications, services and the MethodMesh execution engine, allowing research operations to be expressed independently of programming language, operating system or transport mechanism.

The language is intentionally declarative, human-readable and machine-parseable.

---

## 1. Introduction

MethodMesh is a platform for executing digital research workflows.

The platform separates research execution into a number of complementary layers, each with a distinct responsibility.

The Architecture Standard (AS1.00) defines the internal execution model of MethodMesh.

The Research Intent Language (RIL) defines the external language used to request research operations.

Future specifications, including the Protocol Definition Language (PDL), will define workflow orchestration and protocol execution.

Together these specifications provide a platform-independent framework for digital research.

```
      RIL (Research Intent Language)

                 WHAT

                 WHEN

                WHERE

                 HOW

                RESULT

                  ↓

                Action

                Intent

               Resource

                 Type

              Parameters
                  ↓

           MethodMesh Runtime

                  ↓

             Provenance
```

## 2. Definitions

The following definitions apply throughout this specification.

### 2.1 Action

An **Action** is the smallest executable unit within a RIL request.

Each action represents a single research operation and is executed independently.

Actions may be chained together to form more complex workflows.

---

### 2.2 Intent

An **Intent** is the verb describing the operation to be performed.

Examples include:

- measure
- capture
- ask
- verify
- retrieve
- submit

The complete vocabulary of permitted intents is defined by the **Core Verbs Specification**.

---

### 2.3 Resource

A **Resource** is the entity upon which an intent operates.

Resources represent research entities, system entities or external objects.

Examples include:

- participant
- sample
- image
- device
- protocol
- dataset
- notification

The complete vocabulary of resources is defined by the **Core Resources Specification**.

---

### 2.4 Type

A **Type** refines the meaning of an intent, a resource, or both.

Types provide additional specificity without changing the underlying intent.

Examples include:

- thermal
- fingerprint
- temperature
- QR
- enrolled

The complete vocabulary of types is defined by the **Core Types Specification**.

---

### 2.5 Parameter

A **Parameter** is a named configuration value associated with an action.

Parameters modify the behaviour of an action without changing its semantic meaning.

Examples include:

- duration
- accuracy
- timeout
- cadence
- resolution

Parameter definitions are specific to individual intents, resources or types.

---

### 2.6 Policy

A **Policy** defines a constraint governing execution.

Policies specify how MethodMesh should execute an action, including execution behaviour, validation requirements, authentication, storage and error handling.

Policies do not alter the meaning of an action.

---

### 2.7 Signal

A **Signal** is a discrete observation generated by a Method, Device Service, the operating system, or another component of MethodMesh.

Signals represent changes in state that may be consumed by actions, workflows or execution policies.

Examples include:

- NFC tag detected
- fingerprint verified
- GPS updated
- battery level changed
- motion detected
- workflow completed

Signals are defined by the Architecture Standard (AS1.00).

---

### 2.8 Trigger

A **Trigger** is a temporal condition that initiates execution.

Triggers evaluate one or more signals and begin execution when defined conditions become true.

Examples include:

- NFC tag detected
- battery level > 20%
- previous action completed
- scheduled time reached
- motion detected

Triggers are defined within the **WHEN** section of a RIL request.

---

### 2.9 Provenance

**Provenance** is the record describing how a result was produced.

Provenance may include execution history, timestamps, validation outcomes, authentication events, protocol deviations, environmental context and other information required to support reproducibility, traceability and audit.

MethodMesh records provenance independently of whether provenance is returned to the calling application.

### 2.10 Request

A **Request** is the complete unit of work submitted to MethodMesh.

A request consists of one or more actions together with their temporal, spatial, execution and result specifications.

Every request is composed of five sections:

- WHAT
- WHEN
- WHERE
- HOW
- RESULT

---

## 3. Scope

This specification defines the semantics of the Research Intent Language.

Specifically, it defines:

- the conceptual model of a RIL request;
- the responsibilities of each section of a request;
- the execution model;
- the action model;
- provenance behaviour;
- transport independence;
- implementation conformance.

This specification does **not** define:

- JSON syntax;
- Android Intent structures;
- REST endpoints;
- SDK interfaces;
- platform-specific implementation details.

These are defined by separate binding specifications.

---

## 4. Relationship to the MethodMesh Platform

MethodMesh is composed of a family of complementary specifications.

```text
Research Protocol
        ↓
Protocol Definition Language (PDL)
        ↓
MethodMesh Orchestrator
        ↓
Research Intent Language (RIL)
        ↓
Architecture Standard (AS1.00)
        ↓
Platform Runtime
        ↓
Android • iOS • Desktop • Server • Embedded
```

Each layer has a single responsibility.

| Layer | Responsibility |
|--------|----------------|
| Research Protocol | Defines the scientific protocol. |
| PDL | Defines workflow behaviour. |
| MethodMesh Orchestrator | Executes protocol workflows. |
| RIL | Defines research operations. |
| AS1.00 | Executes research operations. |
| Platform Runtime | Provides operating-system services. |

The specifications are intentionally independent.

Changes to one specification should require minimal or no changes to the others.

---

## 5. Purpose

MethodMesh is an execution engine for research workflows.

RIL is the language used to request work from that engine.

Every RIL request answers five questions.

| Question | Responsibility |
|----------|----------------|
| **What** | What operations should be performed? |
| **When** | Under what temporal conditions should they execute? |
| **Where** | Under what spatial or contextual constraints should they execute? |
| **How** | How should execution be governed? |
| **Result** | What should be returned to the caller? |

These five concepts form the semantic foundation of the language.

Everything within RIL exists to answer one of these five questions.

---

## 6. Design Philosophy

The Research Intent Language follows several fundamental design principles.

### Declarative

Requests describe the desired outcome rather than the implementation steps required to achieve it.

For example:

> Measure GPS position with an accuracy of 10 metres.

rather than:

> Enable GPS, collect fixes every second until accuracy is less than 10 metres, then stop.

MethodMesh determines how the requested outcome is achieved.

---

### Platform Independent

RIL describes research operations rather than operating-system APIs.

The same request should execute consistently across Android, iOS, desktop and server implementations.

---

### Transport Independent

RIL is independent of transport mechanism.

The same request may be conveyed using Android Intents, HTTP APIs, QR codes, NFC, deep links, audio signalling or future transports without changing its meaning.

---

### Human Readable

Requests should be understandable by researchers without specialist programming knowledge.

---

### Machine Parseable

Requests shall have a deterministic representation suitable for automated execution.

---

### Provenance by Design

Execution provenance is an integral part of MethodMesh.

All actions may generate provenance.

Callers determine how much provenance is returned.

---

### Stable Vocabulary

RIL defines deliberately small and stable vocabularies of intents, resources and types.

New functionality should normally be introduced through new resources, types, policies or modules rather than by creating additional intents.

---

### Orthogonality

RIL separates distinct concepts into independent components.

These include:

- intents;
- resources;
- types;
- parameters;
- execution context;
- execution policy.

Each concept has a single responsibility and should evolve independently.

---

### Composability

Complex workflows are created by chaining simple actions.

---

### Extensibility

The language shall be extensible without breaking existing requests.

---

## 7. Conceptual Model

Every RIL request consists of five orthogonal sections.

```text
WHAT
↓
WHEN
↓
WHERE
↓
HOW
↓
RESULT
```

Each section has exactly one responsibility.

| Section | Responsibility |
|----------|----------------|
| WHAT | Defines the requested actions. |
| WHEN | Defines temporal behaviour. |
| WHERE | Defines spatial and contextual constraints. |
| HOW | Defines execution policies. |
| RESULT | Defines the information returned to the caller. |

Changing one section must not alter the semantics of the others.

This separation is fundamental to the design of the language.

### The WHAT Grammar

The WHAT section itself follows a simple grammar.

```text
WHAT
↓
Action
↓
Intent
↓
Resource
↓
Type
↓
Parameters
```

Each component has a distinct responsibility.

| Component | Responsibility |
|-----------|----------------|
| Action | A single executable operation. |
| Intent | The operation to perform (verb). |
| Resource | The entity operated upon (noun). |
| Type | Refines the meaning of the intent, the resource, or both. |
| Parameters | Configuration values controlling execution. |

This grammar allows MethodMesh to express complex behaviour while maintaining a small and stable language.

---
## 8. Language Grammar
```
Request
↓
WHAT
WHEN
WHERE
HOW
RESULT
```

```
WHAT
↓
Action+
↓
Intent
↓
Resource+
↓
Type?
↓
Parameters*
```
```
* + = one or more
* * = zero or more
* ? = optional
```

---



---

### Separation of Concerns

RIL separates actions from their execution context.

The **WHAT** section defines *what* should happen.

The **WHEN**, **WHERE** and **HOW** sections define the temporal, spatial and execution context in which those actions should occur.

The **RESULT** section defines the information returned to the caller.

This separation of concerns is fundamental to the design of the language.


## 9. WHAT

The WHAT section defines the research operations requested of MethodMesh.

It describes what work should be performed independently of when, where or how that work is executed.

The WHAT section consists of one or more ordered actions.

---

### 9.1 Actions

An action is the fundamental executable unit of RIL.

Each action represents a single research operation.

Each action consists of:

- an intent;
- one or more resources;
- an optional type;
- optional parameters;
- optional execution metadata.

Each action SHOULD have a single responsibility.

Complex behaviour SHOULD be expressed by chaining multiple actions rather than creating specialised compound actions.

---

### 9.2 Intents

The intent defines the operation to perform.

An intent is a verb describing a fundamental research operation.

Typical examples include...

```text
measure

capture

scan

identify

verify

retrieve

submit
```

The complete vocabulary of permitted intents is defined by the Core Verbs Specification.

Implementations MUST NOT invent new intents without extending that specification.

---

### 9.3 Resources

Resources are the entities upon which intents operate.

Typical examples include...

```text
participant

sample

household

visit

protocol

workflow

timeline

dataset

image

device

location

notification
```

Some intents operate on a single resource.

Others naturally involve multiple resources.

For example:

```text
link

participant

household
```

or

```text
compare

dataset A

dataset B
```

---

### 9.4 Types

Types refine the meaning of an intent, a resource, or both.

Typical examples include...

```text
measure

resource = environment

type = temperature
```

```text
capture

resource = image

type = thermal
```

```text
retrieve

resource = participant

type = enrolled
```

New functionality SHOULD normally be introduced through new types rather than new intents.

---

### 9.5 Parameters

Parameters provide action-specific configuration.

Typical examples include...

- accuracy;
- duration;
- cadence;
- timeout;
- units;
- limits;
- resolution.

Parameter definitions are resource and type specific.

Unknown parameters SHOULD be ignored unless declared mandatory by the implementation.

---

### 9.6 Action Chains

Actions execute sequentially by default.

Outputs produced by one action may be consumed by subsequent actions.

Example:

```text
scan
↓
identify
↓
verify
↓
ask
↓
measure
↓
submit
```

Complex workflows SHOULD be composed from small reusable actions.

---

### 9.7 Inputs and Outputs

Each action may:

- consume outputs from previous actions;
- produce outputs for subsequent actions;
- return outputs directly to the caller.

The mechanism used to bind outputs between actions is implementation-independent.

Concrete bindings are defined by transport specifications.

---

### 9.8 Execution Order

Unless otherwise specified:

- actions execute sequentially;
- later actions begin only after earlier actions complete successfully;
- failures terminate execution.

Alternative behaviour is defined within the HOW section.

---

### 9.9 Atomicity

Each action is considered atomic.

MethodMesh SHOULD either:

- complete the action successfully; or
- report failure.

Partial completion SHOULD NOT occur unless explicitly permitted by execution policy.

---

### 9.10 Action Identity

Each action SHOULD have a stable internal identifier throughout execution.

Action identifiers support:

- provenance;
- logging;
- error reporting;
- dependency resolution;
- result mapping.

Action identifiers are implementation details and need not be exposed to callers.

---

### Informative Note

RIL follows a deliberately simple linguistic model.

- The **intent** is the verb.
- The **resource** is the noun.
- The **type** refines the resource or operation.
- **Parameters** configure execution.

Everything else belongs elsewhere in the language:

- **WHEN** defines temporal behaviour.
- **WHERE** defines spatial constraints.
- **HOW** defines execution policy.
- **RESULT** defines the information returned to the caller.

Keeping these concerns separate allows the language to remain compact, extensible and easy to understand while supporting a wide variety of research workflows.

## 10. WHEN

The **WHEN** section defines the temporal behaviour of a RIL request.

It specifies when actions become eligible for execution, what events initiate execution, how frequently execution should occur, and under what conditions execution should terminate.

The WHEN section governs **time**, not location or execution policy.

---

### 10.1 Temporal Model

The temporal model consists of five independent concepts.

```text
Activation
↓
Schedule
↓
Trigger
↓
Cadence
↓
Completion
```

Each concept has a single responsibility.

| Component | Responsibility |
|----------|----------------|
| Activation | How execution begins. |
| Schedule | When execution is eligible to occur. |
| Trigger | Events that initiate execution. |
| Cadence | How frequently execution repeats. |
| Completion | When execution ends. |

---

### 10.2 Activation

Activation defines how execution begins.

Supported activation modes include:

- immediate
- manual
- scheduled
- triggered
- continuous

Examples:

```text
immediate
```

```text
manual
```

```text
after previous_action
```

---

### 10.3 Schedule

Schedules define when execution is permitted.

Typical examples include...

- specific date
- specific time
- recurring schedule
- relative time
- protocol day
- visit number

Examples:

```text
day 7
```

```text
every Monday
```

```text
08:00 daily
```

---

### 10.4 Triggers

Triggers initiate execution when one or more conditions become true.

Triggers are signal-based.

Signals may originate from:

- device services;
- methods;
- previous actions;
- workflow state;
- external requests.

Typical examples include...

```text
signal
    motion.detected
```

```text
signal
    nfc.tag_present
```

```text
signal
    bluetooth.rssi > -60
```

```text
signal
    wifi.connected
```

```text
signal
    battery.level > 20
```

```text
signal
    sound.level > 80 dB
```

```text
signal
    previous_action.completed
```

Multiple trigger conditions MAY be combined according to implementation policy.

---

### 10.5 Cadence

Cadence specifies how frequently observations occur.

Examples:

```text
every 5 minutes
```

```text
every second
```

Cadence is independent of duration.

---

### 10.6 Duration

Some actions execute over a period of time.

Examples:

```text
10 hours
```

```text
30 seconds
```

---

### 10.7 Completion

Completion defines when execution terminates.

Completion conditions include:

- duration reached;
- required accuracy achieved;
- target count reached;
- workflow completed;
- participant cancelled;
- timeout exceeded.

---

### 10.8 Design Principles

WHEN defines temporal behaviour only.

It MUST NOT define:

- spatial constraints;
- execution policy;
- returned information.

These are defined separately by WHERE, HOW and RESULT.

---

### Informative Note

The same action may execute:

- immediately;
- tomorrow morning;
- every five minutes;
- when a participant scans an NFC tag;
- whenever motion is detected;
- until ten hours have elapsed.

The action itself does not change.

Only its temporal behaviour changes.

---

## 11. WHERE

The **WHERE** section defines the spatial constraints under which a RIL request may execute.

WHERE answers one question:

> **Where may this action execute?**

It does not describe time, execution policy or device capability.

---

### 11.1 Spatial Relationships

RIL defines a small set of geometric relationships.

| Relationship | Description |
|--------------|-------------|
| inside | Execute only inside a region. |
| outside | Execute only outside a region. |
| within_distance | Execute within a specified distance of an object or location. |
| beyond_distance | Execute only beyond a specified distance. |

These relationships are implementation-independent.

---

### 11.2 Spatial Objects

Spatial relationships operate on one or more spatial objects.

Typical examples include...

- point
- polygon
- circle
- study site
- clinic
- participant home
- household
- healthcare facility
- named region

---

### 11.3 Relative Location

Relationships may reference dynamic objects.

Typical examples include...

```text
within_distance

5 m

participant
```

```text
inside

household
```

```text
inside

study_site
```

---

### 11.4 Spatial Quality

Spatial requests may specify minimum positioning quality.

Typical examples include...

- horizontal accuracy;
- vertical accuracy;
- confidence radius.

Required positioning quality is an execution policy and is therefore defined in the HOW section.

---

### 11.5 Constraint Evaluation

Spatial constraints evaluate to one of three states.

- satisfied
- not satisfied
- unknown

Unknown states include:

- location unavailable;
- positioning unavailable;
- insufficient positioning quality.

---

### 11.6 Constraint Behaviour

When spatial constraints are not satisfied, MethodMesh may:

- wait;
- retry;
- request override;
- terminate execution.

The selected behaviour is defined in the HOW section.

---

### 11.7 Design Principles

WHERE defines spatial relationships only.

It MUST NOT define:

- execution policy;
- temporal behaviour;
- returned information.

---

### Informative Note

Typical examples include...

```text
inside study_site
```

```text
inside participant_home
```

```text
within_distance 10 m of participant
```

```text
outside exclusion_zone
```

These constraints describe where execution is permitted.

Signals such as Wi-Fi connectivity, Bluetooth signal strength, battery level and NFC detection are not spatial relationships and are therefore represented as trigger conditions within the WHEN section.

---

## 12. HOW

The **HOW** section defines the execution constraints governing a RIL request.

Execution constraints determine how MethodMesh performs the requested action, without changing the action itself.

The HOW section governs execution only.

It does not define:

- what action is requested;
- when execution occurs;
- where execution occurs;
- what information is returned.

---

### 12.1 Execution Model

Execution constraints are organised into four independent groups.

```text
Requirements

↓

Execution

↓

Interaction

↓

Outcomes
```

Each group has a single responsibility.

| Group | Responsibility |
|------|----------------|
| Requirements | Conditions that must be satisfied before execution. |
| Execution | Rules governing how execution proceeds. |
| Interaction | How users participate in execution. |
| Outcomes | What should happen after execution. |

---

## 12.2 Requirements

Requirements define prerequisites for execution.

If requirements are not satisfied, execution behaviour is determined by the configured execution policies.

Requirement policies include:

- Permission
- Authentication
- Validation
- Device
- Connectivity

#### Permission Policy

Examples:

- request automatically;
- explain requirement;
- open system settings;
- fail immediately.

---

#### Authentication Policy

Examples:

- fingerprint;
- PIN;
- facial recognition;
- operator authentication;
- participant confirmation.

---

#### Validation Policy

Defines minimum acceptance criteria.

Examples:

- GPS accuracy < 5 m;
- image resolution;
- minimum recording duration;
- barcode checksum;
- signal quality.

---

#### Device Policy

Examples:

- NFC required;
- camera required;
- microphone required;
- battery > 20%;
- charging required.

---

#### Connectivity Policy

Examples:

- online required;
- offline permitted;
- Wi-Fi preferred;
- cellular permitted.

---

## 12.3 Execution

Execution policies govern how actions are performed.

#### Execution Policy

Examples:

- timeout;
- retries;
- retry interval;
- sequential;
- parallel;
- stop on first error;
- continue on failure.

---

#### Acquisition Policy

Defines how observations are acquired.

Typical examples include...

- first valid;
- stable value;
- average;
- median;
- rolling average;
- maximum;
- minimum.

Applies equally to measurements, media capture and sensor observations.

---

#### Sampling Policy

Defines sampling behaviour for repeated observations.

Typical examples include...

- fixed interval;
- adaptive interval;
- event-driven;
- burst sampling.

---

## 12.4 Interaction

Interaction policies define how users participate in execution.

#### Actor Policy

Examples:

- participant;
- operator;
- supervised;
- unattended.

---

#### User Interface Policy

Examples:

- show progress;
- hide progress;
- allow cancel;
- disable cancel;
- allow retry;
- require confirmation;
- fullscreen;
- background execution.

---

#### Accessibility Policy

Examples:

- high contrast;
- large controls;
- voice prompts;
- vibration.

---

## 12.5 Outcomes

Outcome policies define what happens following execution.

#### Storage Policy

Examples:

- temporary;
- persistent;
- encrypted;
- retain until submission;
- retain until expiry.

---

#### Provenance Policy

Examples:

- record locally only;
- return summary;
- return full provenance;
- include timestamps;
- include execution history.

---

#### Override Policy

Examples:

- never allow;
- require reason;
- require supervisor approval;
- continue with warning.

Protocol deviations SHOULD be included within provenance.

---

#### Error Policy

Examples:

- retry;
- abort;
- skip;
- continue;
- request operator decision.

---

#### Security Policy

Examples:

- encrypt outputs;
- signed execution;
- trusted caller only;
- secure transport required.

---

### 12.6 Design Principles

Execution constraints modify execution behaviour only.

They MUST NOT redefine:

- requested actions;
- temporal behaviour;
- spatial constraints;
- returned information.

Implementations MAY introduce additional policy groups provided they preserve the conceptual model.

---

### Informative Note

The action

capture
resource = image
type = thermal

may execute under many different execution constraints.

For example:

- fingerprint authentication;
- GPS accuracy below 5 metres;
- background execution;
- retries on failure;
- encrypted local storage;
- return full provenance.

The requested action remains identical.

Only the execution constraints change.

---

## 13. RESULT

The **RESULT** section defines the information returned to the caller following execution.

It specifies what outputs should be returned and the level of detail required.

The RESULT section governs returned information only.

It does not influence how execution occurs.

---

### 13.1 Result Model

Results are organised into five independent components.

```text
Status

↓

Data

↓

Provenance

↓

Metadata

↓

Diagnostics
```

Each component has a single responsibility.

| Component | Responsibility |
|-----------|----------------|
| Status | Whether execution succeeded. |
| Data | Requested outputs. |
| Provenance | How those outputs were produced. |
| Metadata | Execution identifiers and descriptive information. |
| Diagnostics | Errors, warnings and protocol deviations. |

---

### 13.2 Status

Status describes the overall outcome of execution.

Typical values include:

- success
- partial_success
- failed
- cancelled
- skipped
- timed_out

Implementations MAY define additional status values.

---

### 13.3 Data

Data are the primary outputs requested by the caller.

Data may include:

- scalar values;
- text;
- media;
- files;
- vectors;
- traces;
- time series;
- structured objects.

The caller defines which outputs should be returned.

Example

```text
return

temperature

participant_id

photo
```

---

### 13.4 Provenance

Provenance records how results were obtained.

MethodMesh records provenance independently of whether it is returned.

Callers specify how much provenance should be returned.

Typical levels include:

- none;
- summary;
- full.

Returned provenance MAY include:

- timestamps;
- execution history;
- authentication events;
- operator identity;
- signals;
- validation results;
- overrides;
- protocol deviations.

Example

```text
provenance

summary
```

---

### 13.5 Metadata

Metadata describe the execution rather than the research data.

Typical examples include...

- execution identifier;
- request identifier;
- workflow identifier;
- protocol identifier;
- duration;
- software version;
- method version.

Metadata SHOULD remain stable across implementations where possible.

---

### 13.6 Diagnostics

Diagnostics describe problems encountered during execution.

Typical examples include...

- errors;
- warnings;
- retries;
- validation failures;
- unmet requirements;
- skipped actions.

Diagnostics SHOULD provide sufficient information for reproducibility and debugging.

---

### 13.7 Output Mapping

Callers define how returned values are mapped into the receiving system.

Example

```text
temperature

↓

body_temperature_c
```

```text
execution_id

↓

methodmesh_execution_id
```

Output mappings are transport-specific and are therefore defined by binding specifications.

---

### 13.8 Data Shapes

Returned data may have different structures.

Typical examples include...

- scalar;
- list;
- object;
- vector;
- trace;
- time series;
- media.

Example

```text
temperature

scalar
```

```text
accelerometer

time series
```

```text
photo

media
```

---

### 13.9 Design Principles

The RESULT section defines returned information only.

It MUST NOT redefine:

- requested actions;
- temporal behaviour;
- spatial constraints;
- execution behaviour.

MethodMesh SHOULD separate returned research data from provenance and execution metadata.

---

### Informative Note

The same action may produce very different results depending upon caller requirements.

For example:

```text
measure

resource = environment

type = temperature
```

may return:

- only the temperature value;
- the temperature and timestamp;
- the temperature with full provenance;
- the complete execution record including validation, authentication and diagnostics.

The requested action remains identical.

Only the returned information differs.

# Appendix F. RIL Core Verbs (absorbed source v0.02)

### Core Verbs v0.02

---

## 1. Purpose

The Research Intent Language defines the canonical verbs used to express requests for action within MethodMesh.

RIL sits in the orchestration layer of the MethodMesh conceptual model.

An Intent requests one or more Methods.

Methods produce Observations.

Observations may support Assertions.

Assertions describe Entities.

RIL therefore does not define scientific knowledge directly.

It defines the action language through which research activities are requested, coordinated and executed.

The Core Verbs Registry defines the stable vocabulary of verbs recognised by RIL.

New functionality should normally be introduced by defining new types, targets, parameters or policies rather than inventing new verbs.

## 2. The Role of Core Verbs

Core verbs express what a user, system or Method is asking MethodMesh to do.

They are part of the orchestration layer rather than the scientific knowledge model.

RIL verbs do not themselves create scientific understanding.

They request actions that may lead to Method execution.

Methods may then produce Observations.

Observations may support Assertions.

Assertions describe Entities.

```text
Intent
    requests
Method
    produces
Observation
    may support
Assertion
    describes
Entity
```

Within the MethodMesh conceptual model:

- Intents express requested action.
- Core verbs define the canonical action language.
- Methods perform the requested work.
- Observations, Assertions and Entities remain part of the scientific knowledge model.

RIL therefore separates the language of action from the language of scientific understanding.

## 3. Design Principles

The RIL Core Verbs Registry follows the following principles.

### Minimal

The core verb vocabulary should remain as small as possible.

New capabilities should normally be expressed through targets, parameters, policies or methods rather than by introducing additional verbs.

---

### Consistent

Each core verb should have a single, well-defined meaning.

A verb should not perform different conceptual operations depending on context.

---

### Composable

Complex research activities should be expressed by combining multiple simple intents rather than creating increasingly specialised verbs.

---

### Domain-independent

Core verbs should remain applicable across all scientific disciplines.

They describe actions rather than discipline-specific concepts.

---

### Human-readable

RIL statements should be understandable by researchers without requiring knowledge of implementation details.

The language should resemble ordinary scientific instruction wherever practical.

---

### Machine-interpretable

Every core verb should possess a precise operational meaning that can be interpreted consistently by MethodMesh.

---

### Extensible

The Core Verbs Registry provides a stable foundation upon which higher-level domain vocabularies may be constructed without modifying the core language itself.

## 4. Core Verb Registry

The RIL Core Verb Registry defines the stable vocabulary of actions recognised by MethodMesh.

Each verb represents a single conceptual operation.

The meaning of a verb is independent of the Entity, Method or scientific discipline to which it is applied.

Additional behaviour is expressed through targets, parameters, policies and composition rather than by introducing new verbs.

Each registry entry contains the following fields.

| Field | Description |
|--------|-------------|
| **Verb** | Canonical RIL verb |
| **Purpose** | Conceptual meaning of the verb |
| **Typical Target** | Entity, Method, Observation, Assertion or other MethodMesh object |
| **Parameters** | Common parameters accepted by the verb |
| **Result** | Expected outcome of successful execution |
| **Examples** | Informative examples |

The Core Verb Registry is descriptive rather than prescriptive.

It defines the meaning of each verb independently of any particular implementation.

The following sections define the canonical RIL verbs recognised by MethodMesh.

## 5. Core Verb Definitions

Each RIL core verb is defined using a common structure.

| Field | Description |
|--------|-------------|
| **Verb** | Canonical RIL verb |
| **Purpose** | Conceptual meaning of the verb |
| **Behaviour** | Expected behaviour when executed |
| **Typical Targets** | Common MethodMesh objects acted upon |
| **Typical Parameters** | Common parameters accepted |
| **Result** | Expected outcome |
| **Examples** | Informative examples |

The following sections define the canonical RIL verbs.

---

### CREATE

**Purpose**

Create a new MethodMesh object.

**Behaviour**

Creates a new object of the requested type and returns its identifier.

**Typical Targets**

- Entity
- Method
- Observation
- Assertion
- Project
- Dataset

**Typical Parameters**

- Type
- Parent
- Metadata

**Result**

A new object exists within MethodMesh.

**Examples**

```ril
CREATE ENTITY Person
CREATE PROJECT "Malaria Study"
```

---

### OBSERVE

...

---

### MEASURE

...

## 6. Composition

Complex research activities should be expressed through combinations of simple verbs rather than introducing additional specialised verbs.

Examples...

---

## 7. Summary

The RIL Core Verbs Registry defines the stable action vocabulary of MethodMesh.

Core verbs express intent.

Methods execute intent.

Scientific knowledge remains represented through Entities, Observations and Assertions.

The core verb vocabulary should remain stable and minimal.

# Appendix G. Entity Registry (absorbed source v0.02)

## 1. Purpose

The Entity Registry defines the canonical vocabulary used by MethodMesh to represent the things that become the subject of scientific research.

Entities form the foundation of the MethodMesh knowledge model.

Observations provide evidence concerning Entities.

Assertions describe Entities.

This registry defines the classes of Entities recognised by MethodMesh.

It does not prescribe implementation, storage technology or execution behaviour.

## 2. The Role of Entities

Research begins with entities.

An Entity represents a distinct thing that exists, has existed, may exist, or may be conceived within the scope of scientific enquiry.

Entities form the foundation of the MethodMesh knowledge model.

Research seeks to understand Entities by making observations and developing evidence-supported assertions concerning them.

```text
                Entity
             (the thing)

                   ▲
             described by

              Assertion

                   ▲
             supported by

             Observation
```

Within the MethodMesh conceptual model:

- Entities are the subjects of research.
- Observations provide evidence concerning Entities.
- Assertions describe Entities.
- Methods obtain Observations concerning Entities.

Entities exist independently of the observations or assertions made about them.

MethodMesh therefore represents Entities separately from the scientific knowledge accumulated about them.

## 3. Definition

An Entity is defined as:

> **A distinct thing that exists, has existed, may exist, or may be conceived, and which may become the subject of scientific observation, description, measurement or reasoning.**

Entities possess identity independently of any observations or assertions made about them.

MethodMesh represents Entities separately from the scientific knowledge accumulated about them.

Entities may exist in one of three Modes:

- Physical
- Digital
- Conceptual

Entities may possess one or more Traits describing their characteristics.

Entities may become the subject of one or more Observations.

One or more Assertions may describe an Entity.

Entities participate in the MethodMesh knowledge model by serving as the subjects about which scientific understanding is developed.

## 4. Design Principles

The Entity Registry follows the following principles.

### Reality

Entities exist independently of the observations or assertions made about them.

MethodMesh represents Entities rather than creating them.

---

### Identity

Every Entity possesses a persistent identity that distinguishes it from every other Entity.

Identity is independent of names, labels or descriptions.

---

### Entity-centred

Research begins with Entities.

Observations provide evidence concerning Entities.

Assertions describe Entities.

Scientific understanding develops by accumulating evidence-supported assertions about Entities.

---

### Mode-independent

Entities may exist in different Modes without changing their conceptual identity.

MethodMesh currently recognises three Entity Modes:

- Physical
- Digital
- Conceptual

Mode describes how an Entity exists rather than what it is.

---

### Observable

An Entity is capable of becoming the subject of scientific observation, description, measurement or reasoning.

Not every Entity can be observed directly, but every Entity may become the subject of scientific investigation.

---

### Extensible

The Entity Registry defines a stable conceptual framework capable of representing entities from any scientific discipline.

New Entity types may be added without modification of the underlying conceptual model.

---

### Implementation-independent

The conceptual definition of an Entity is independent of programming language, storage technology or software architecture.

The same Entity model may be represented using relational databases, graph databases, document stores or other implementation technologies.

## 5. Registry Entry Model

Each Entity definition within the Entity Registry follows a common structure.

The registry defines the canonical characteristics of each Entity class rather than individual instances.

Each registry entry contains the following fields.

| Field | Description |
|--------|-------------|
| **Name** | Canonical Entity name |
| **Description** | Short definition |
| **Typical Mode** | Typical Entity Mode (Physical, Digital or Conceptual) |
| **Typical Status** | Typical lifecycle state (optional) |
| **Typical Traits** | Common characteristics associated with the Entity |
| **Common Assertions** | Assertions frequently used to describe the Entity (informative only) |
| **Examples** | Informative examples |

The Registry Entry Model is descriptive rather than prescriptive.

It defines the common characteristics of an Entity class without restricting how individual Entities may be represented within specific research projects.

Research projects may extend Entity definitions with additional Traits, Assertions or implementation-specific metadata provided these extensions remain consistent with the MethodMesh conceptual model.

## 6. Canonical Entity Types

The Entity Registry defines a canonical vocabulary of Entity types that may become the subject of scientific research.

These definitions provide a common conceptual language across disciplines while remaining sufficiently general to support extension within individual research projects.

The categories described below are informative rather than restrictive.

An individual Entity belongs to one canonical Entity type.

Entity types may share common Traits, participate in similar Assertions and be investigated using similar Methods.

The following sections define the current canonical Entity types recognised by MethodMesh.

## 7. Relationships

Entities occupy the central position within the MethodMesh knowledge model.

They participate in the conceptual model as follows.

```text
              Entity
                 ▲
          described by
                 │
            Assertion
                 ▲
          may be supported by
                 │
           Observation
                 ▲
          produced by
                 │
              Method
```

An Entity may be described by zero, one or many Assertions.

An Assertion may describe one or more Entities.

An Entity may become the subject of zero, one or many Observations.

Methods obtain Observations concerning Entities.

MethodMesh preserves these relationships together with their associated provenance.

---

## 8. Open Questions

The following areas remain under consideration.

- Should additional Entity Modes be recognised in future versions?
- Should the canonical Entity taxonomy evolve through extension registries or revision of the core registry?
- Which Traits should remain universal and which should be domain-specific?
- Should Entity lifecycle states become a separate registry?

---

## 9. Summary

Entities represent the things that become the subject of scientific research.

They exist independently of observations, assertions and scientific interpretation.

MethodMesh represents Entities separately from the knowledge accumulated about them.

Methods obtain Observations concerning Entities.

Observations may support Assertions.

Assertions describe Entities.

Together these concepts provide the foundation of the MethodMesh knowledge model.

# Appendix H. Observation Registry (absorbed source v0.01)

## 1. Purpose

The Observation Registry defines the canonical model used by MethodMesh to represent scientific evidence.

Observations are the evidential foundation of scientific knowledge.

They provide the evidence upon which assertions are made, tested and refined.

This registry defines what constitutes an Observation and how observations relate to the wider MethodMesh conceptual model.

---

## 2. The Role of Observations

MethodMesh distinguishes between three fundamental concepts.

```text
                Entity
             (the thing)

                   │
         described by claims

                   ▼

              Assertion
             (the claim)

                   ▲
        supported by evidence

                   │

            Observation
            (the evidence)
```

Methods obtain observations.

Observations may support Assertions.

Assertions describe entities.

Scientific understanding therefore emerges from evidence rather than from observations alone.

---

## 3. Definition

An Observation is defined as:

> **A timestamped record of evidence obtained through observation, measurement, computation or simulation concerning one or more Research Entities.**

Observations are evidence.

They are not scientific conclusions.

Observations may be direct, indirect, derived or simulated depending upon how they relate to the phenomenon being studied.

An Observation may represent a direct measurement, an indirect proxy, a derived quantity or a simulated output, provided its method and provenance are preserved.

---

## 4. Principles

The Observation Registry follows the following principles.

### Evidence

Observations provide evidence rather than interpretation.

### Method-based

Every Observation should originate from one or more Methods.

### Temporal

Observations occur at a specific point or interval in time.

### Reproducible

Equivalent methods applied under equivalent conditions should produce comparable observations.

### Persistent

Observations form part of the permanent scientific record.

### Independent

Observations remain valid independently of the assertions they support.

---

## 5. Observation Modes

Every Observation represents evidence concerning a phenomenon of interest.

Observation Mode describes how directly that evidence relates to the phenomenon being investigated.

The Observation Mode characterises the evidential pathway rather than the Observation itself.

MethodMesh currently recognises four Observation Modes.

---

### 5.1 Direct

A Direct Observation records the phenomenon of interest without requiring an intermediate proxy.

Examples include:

- Height measured using a tape measure.
- Weight measured using calibrated scales.
- Geographic coordinates obtained using GNSS.
- Molecule counts measured using digital PCR.

---

### 5.2 Indirect

An Indirect Observation records evidence through a measurable proxy.

The recorded value is an observation.

Interpretation is required to infer the underlying phenomenon.

Examples include:

- PCR cycle threshold (Ct).
- ELISA optical density.
- Blood pressure.
- Fluorescence intensity.
- Questionnaire response.
- Accelerometer counts.

Indirect observations remain observations.

Only their interpretation differs.

---

### 5.3 Derived

A Derived Observation is calculated from one or more existing observations using a defined method.

The resulting value remains evidence provided the derivation process is transparent and reproducible.

Examples include:

- Viral load estimated from Ct.
- Body Mass Index.
- Household wealth index.
- Composite clinical scores.
- Mean daily temperature.
- Risk scores.

Derived observations should retain links to the observations from which they were calculated.

---

### 5.4 Simulated

A Simulated Observation is generated by a computational model rather than direct interaction with the phenomenon.

Examples include:

- Forecast disease incidence.
- Climate projections.
- Agent-based simulation outputs.
- Counterfactual scenarios.

Simulated observations provide evidence within the assumptions of the generating model.

Their provenance should include the model, parameters and input observations.

## 6. Observation Metadata

Every Observation should be capable of recording the following metadata.

| Field | Description |
|--------|-------------|
| **Observation ID** | Unique identifier for the Observation |
| **Entity** | The Entity being observed |
| **Property** | The characteristic or phenomenon being observed |
| **Value** | The observed or computed value |
| **Data Type** | The representation of the Observation Content. |
| **Units** | Units of measurement where applicable |
| **Observation Mode** | Direct, Indirect, Derived or Simulated |
| **Method** | Method used to obtain the Observation |
| **Observation Source** |Person, instrument, software or system responsible for producing the Observation. |
| **Timestamp** | Time or interval during which the Observation was obtained |
| **Location** | Optional spatial context of the Observation |
| **Conditions** | Relevant environmental or experimental conditions |
| **Provenance** | Information describing origin and processing history |
| **Quality Indicators** | Optional indicators of quality, uncertainty or confidence |

Observation metadata should provide sufficient information to allow the Observation to be interpreted, reproduced and evaluated independently.

The minimum metadata required for any Observation are:

- Entity
- Property
- Observation Content
- Observation Mode
- Method
- Timestamp


## 7. Relationships

Observations participate in the MethodMesh conceptual model as follows.

```text
Method

    produces

Observation

    may support

Assertion

    describes

Entity
```

An Observation may support zero, one or many Assertions.

An Assertion may be supported by zero, one or many Observations.

Observations may also be used as inputs to the derivation of other Observations.

MethodMesh preserves these relationships together with their associated provenance.

## 8. Open Questions

The following areas remain under consideration.

- Should uncertainty be represented as Observation metadata or as a separate Observation?
- Should all quantitative observations include explicit uncertainty?
- Should Derived Observations retain explicit links to all source Observations?
- Should Observations be immutable once recorded?
- How should streaming or continuously updated Observations be represented?

## 9. Summary

Observations represent scientific evidence.

MethodMesh recognises four Observation Modes:

- Direct
- Indirect
- Derived
- Simulated

Observation Mode describes how the recorded evidence relates to the phenomenon being investigated.

Methods produce Observations.

Observations may support Assertions.

Assertions describe Entities.

Together these concepts provide the evidential foundation of the MethodMesh conceptual model.

# Appendix I. Assertion Registry (absorbed source v0.02)

## 1. Purpose

The Assertion Registry defines the canonical language used by MethodMesh to represent scientific understanding.

Assertions describe Entities.

They express the current scientific understanding of those Entities based upon available evidence.

Observations may support Assertions.

This registry defines the classes of Assertions recognised by MethodMesh.

It does not prescribe implementation, storage technology or execution behaviour.

## 2. The Role of Assertions

Research seeks to understand Entities.

This understanding is represented through Assertions.

An Assertion expresses a claim concerning one or more Entities.

Observations may provide evidence supporting those Assertions.

```text
                Entity
             (the thing)
                   ▲
             described by
              Assertion
                   ▲
          may be supported by
             Observation
```

Within the MethodMesh conceptual model:

- Assertions describe Entities.
- Assertions represent the current scientific understanding of those Entities.
- Observations may provide evidence supporting Assertions.
- Methods obtain Observations from which Assertions may be developed or refined.

Assertions do not represent immutable truth.

They represent the current state of scientific understanding based upon the available evidence.

MethodMesh therefore represents Assertions separately from both the Entities they describe and the Observations that may support them.

## 3. Definition

An Assertion is defined as:

> **A timestamped claim describing a characteristic, state or relationship concerning one or more Entities.**

Assertions represent scientific understanding rather than immutable truth.

They express what is currently understood about an Entity based upon the available evidence.

Assertions may be supported, strengthened, revised, superseded or withdrawn as additional Observations become available.

Assertions do not replace previous understanding.

Instead, MethodMesh preserves the evolution of scientific understanding through the accumulation of timestamped Assertions.

Assertions may describe:

- characteristics;
- states;
- relationships;
- classifications;
- interpretations.

Assertions participate in the MethodMesh knowledge model by describing Entities and linking scientific understanding to the Observations that may support it.

## 4. Design Principles

The Assertion Registry follows the following principles.

### Scientific

Assertions represent scientific understanding.

They describe what is currently understood about an Entity based upon the available evidence.

---

### Evidence-based

Assertions should be supported by one or more Observations wherever appropriate.

The strength of an Assertion depends upon the quality and quantity of the supporting evidence rather than the Assertion itself.

---

### Temporal

Assertions exist at a point in time.

Scientific understanding evolves through the creation of new Assertions rather than modification of previous Assertions.

MethodMesh preserves this historical record.

---

### Independent

Assertions are conceptually independent of the Observations that may support them.

An Assertion may be supported by multiple Observations.

An Observation may support multiple Assertions.

---

### Minimal

Each Assertion should express a single scientific claim.

Complex understanding should emerge from multiple Assertions rather than compound statements.

---

### Extensible

The Assertion Registry defines a stable conceptual framework capable of representing scientific understanding across disciplines.

New Assertion types may be added without modification of the underlying conceptual model.

---

### Implementation-independent

The conceptual definition of an Assertion is independent of programming language, storage technology or software architecture.

The same Assertion model may be represented using relational databases, graph databases, document stores or other implementation technologies.

## 5. Registry Entry Model

Each Assertion definition within the Assertion Registry follows a common structure.

The registry defines the canonical characteristics of each Assertion class rather than individual assertions recorded within research projects.

Each registry entry contains the following fields.

| Field | Description |
|--------|-------------|
| **Name** | Canonical Assertion name |
| **Description** | Short definition |
| **Assertion** | Human-readable statement expressed by the Assertion |
| **Source Entity Type** | Entity types from which the Assertion may originate |
| **Target** | Target Entity types or literal values described by the Assertion |
| **Evidence** | Typical Observation types that may support the Assertion |
| **Examples** | Informative examples |

The Registry Entry Model is descriptive rather than prescriptive.

It defines the common characteristics of an Assertion class without restricting how individual Assertions may be represented within specific research projects.

Research projects may extend Assertion definitions with additional metadata or domain-specific semantics provided these extensions remain consistent with the MethodMesh conceptual model.

## 6. Canonical Assertion Types

The Assertion Registry defines a canonical vocabulary of Assertion types used to represent scientific understanding within MethodMesh.

These definitions provide a common conceptual language for describing Entities across scientific disciplines while remaining sufficiently general to support extension within individual research projects.

The categories described below are informative rather than restrictive.

Each Assertion represents a single scientific claim concerning one or more Entities.

Assertion types may be supported by different forms of Observation and may be applied across multiple domains of research.

The following sections define the current canonical Assertion types recognised by MethodMesh.

## 7. Relationships

Assertions occupy the central position within the MethodMesh knowledge model.

They connect scientific understanding with both the Entities being studied and the Observations that provide supporting evidence.

```text
              Entity
                 ▲
          described by
                 │
            Assertion
                 ▲
       may be supported by
                 │
           Observation
                 ▲
          produced by
                 │
              Method
```

An Assertion may describe one or more Entities.

An Entity may be described by zero, one or many Assertions.

An Assertion may be supported by zero, one or many Observations.

An Observation may support zero, one or many Assertions.

MethodMesh preserves these relationships together with their associated provenance.

---

## 8. Open Questions

The following areas remain under consideration.

- Should confidence be represented as part of an Assertion or separately from the Assertion itself?
- Should MethodMesh define a canonical vocabulary for common scientific Assertions?
- How should conflicting Assertions concerning the same Entity be represented?
- Should assertions always retain explicit links to their supporting Observations?

---

## 9. Summary

Assertions represent scientific understanding.

They describe the characteristics, states, relationships and interpretations associated with one or more Entities.

Assertions do not represent immutable truth.

They represent the current state of scientific understanding based upon the available evidence.

Methods produce Observations.

Observations may support Assertions.

Assertions describe Entities.

Together these concepts provide the knowledge layer of the MethodMesh conceptual model.

# Appendix J. Intent Registry (absorbed source v0.02)

## 1. Purpose

The Intent Registry defines the canonical vocabulary used by MethodMesh to represent requested actions.

An Intent expresses what a user, application or automated system wishes MethodMesh to do.

Intent belongs to the orchestration layer of the MethodMesh conceptual model.

An Intent requests one or more Methods.

Methods produce Observations.

Observations may support Assertions.

Assertions describe Entities.

The Intent Registry defines the canonical Intent vocabulary recognised by MethodMesh.

It does not prescribe implementation, execution behaviour or transport mechanisms.

## 2. The Role of Intent

Research begins with an intention to perform work.

Within MethodMesh, that intention is represented as an Intent.

An Intent expresses a requested action.

It does not itself perform work or create scientific knowledge.

Instead, an Intent requests one or more Methods.

Methods perform the requested work.

Methods may produce Observations.

Observations may support Assertions.

Assertions describe Entities.

```text

Intent

    requests

Method

    produces

Observation

    may support

Assertion

    describes

Entity

```

Within the MethodMesh conceptual model:

- Intent expresses requested action.

- Methods execute requested work.

- Observations provide scientific evidence.

- Assertions represent scientific understanding.

- Entities are the subjects of scientific investigation.

MethodMesh therefore separates orchestration from scientific knowledge.

Intent belongs to the orchestration layer.

Entities, Observations and Assertions belong to the knowledge layer.

## 3. Definition

An Intent is defined as:

> **A declarative request for one or more research actions to be performed.**

An Intent expresses *what* is requested rather than *how* it should be performed.

MethodMesh interprets an Intent by selecting one or more appropriate Methods capable of fulfilling the requested action.

An Intent may originate from:

- a researcher;
- a participant;
- an automated workflow;
- an external system;
- another Method.

An Intent does not itself produce scientific evidence.

Instead, it initiates work that may result in one or more Observations.

Those Observations may support Assertions.

Those Assertions contribute to scientific understanding of Entities.

An Intent participates in the MethodMesh conceptual model by providing the entry point through which research activities are requested and orchestrated.

## 4. Design Principles

The Intent Registry follows the following principles.

### Declarative

An Intent describes what work is requested rather than how it should be performed.

The selection and execution of appropriate Methods is the responsibility of MethodMesh.

---

### Independent

An Intent is independent of any particular implementation, workflow engine or execution environment.

The same Intent may be fulfilled using different Methods under different circumstances.

---

### Composable

Complex research activities should be expressed through combinations of simple Intents rather than increasingly specialised instructions.

---

### Reproducible

Equivalent Intents executed under equivalent conditions should produce comparable outcomes through equivalent Methods.

---

### Extensible

The Intent Registry defines a stable conceptual framework capable of supporting research activities across scientific disciplines.

New Intent types may be added without modification of the underlying conceptual model.

---

### Traceable

Each Intent should remain linked to the Methods selected to fulfil it and the resulting Observations that were produced.

MethodMesh therefore preserves a complete record of requested work and its execution.

---

### Implementation-independent

The conceptual definition of an Intent is independent of programming language, storage technology, workflow engine or communication protocol.

The same Intent model may be represented using JSON, APIs, message queues or other implementation technologies.

## 5. Intent Model

Every Intent represented within MethodMesh follows a common conceptual structure.

An Intent expresses a requested action independently of how that action is ultimately fulfilled.

Each Intent contains the following information.

| Field | Description |
|--------|-------------|
| **Intent ID** | Unique identifier for the Intent |
| **Verb** | Canonical RIL verb describing the requested action |
| **Target** | Entity, Method or other MethodMesh object to which the Intent applies |
| **Parameters** | Additional information required to fulfil the Intent |
| **Policy** | Optional constraints governing execution |
| **Priority** | Optional execution priority |
| **Requester** | Person, system or workflow that created the Intent |
| **Timestamp** | Time the Intent was created |
| **Status** | Current execution status |
| **Result** | Reference to the resulting Method execution or outcome |

The Intent Model is declarative rather than procedural.

It specifies what work is requested without prescribing how that work should be carried out.

MethodMesh remains responsible for selecting appropriate Methods capable of fulfilling the Intent while respecting any applicable Policies.

## 6. Relationships

Intent forms the entry point to the MethodMesh orchestration layer.

It participates in the conceptual model as follows.

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

An Intent may request one or more Methods.

A Method may fulfil one or more Intents.

The execution of an Intent may produce zero, one or many Observations.

MethodMesh preserves these relationships together with their associated provenance.

---

## 7. Open Questions

The following areas remain under consideration.

- Should Intent cancellation and supersession be represented explicitly?
- Should Intents be immutable once submitted?
- How should long-running or continuously executing Intents be represented?
- Which execution metadata belongs to the Intent and which belongs to Method execution?

---

## 8. Summary

Intent represents requested work.

It belongs to the orchestration layer of the MethodMesh conceptual model.

Intent expresses what should happen.

Methods determine how the requested work is performed.

Methods may produce Observations.

Observations may support Assertions.

Assertions describe Entities.

Together these concepts separate research orchestration from scientific knowledge while preserving complete traceability between requested actions and the knowledge they ultimately produce.

# Appendix K. Trait Registry status

`MethodMesh_Trait_Registry_0.02.md` was listed as current by `SPEC_STATUS.md` but the file in the reviewed corpus contains no text. v1.06 therefore records only the source-supported fact that Entities may possess Traits describing characteristics. No canonical Trait entry model or registry vocabulary is specified here.

This is an explicit documentation gap to be resolved by a future Master Book revision rather than by inference.

# Appendix L. v1.06 documentation consolidation record

v1.06 was produced from the reviewed 2026-09-07 documentation corpus. The corpus contained 1,456 documentation candidates. Semantic review reduced the active project-wide set substantially by distinguishing source documentation from module-local material, generated/reference projections and historical/ResearchOS-era files.

Project-wide source families absorbed into this book include:

- Master Book v1.05;
- Module Review and Refresh Manual v1.05;
- capability-writing and capability-review guidance;
- current Architecture/Conceptual/RIL/JSON/registry specifications identified by `SPEC_STATUS.md`;
- online-data architecture;
- scheduler shared-runtime documentation;
- Dashboard/search/Android-surface UI design notes;
- ODK Forms template-library, Central/Kobo deployment, validation and diagnostics notes;
- protocol-pipes design;
- current website source pages for getting started, installation, surfaces, native lifecycle, output contract, ODK/XLSForm, presets/protocols, schedules/widgets, module authoring, testing and troubleshooting.

The former root roadmap was split conceptually: project-wide roadmap directions were retained in Chapter 24; capability-specific issue lists belong with their modules rather than in the canonical project-wide book.

Generated website output, packaged XLSForm assets, mirrored module reference pages and third-party environment documentation are not active documentation authorities and are not reproduced as doctrine here.

Standalone source documents should only be archived after the repository reorganisation dry-run confirms their final disposition.

# Appendix M. Version history

## v1.06 - 2026-09-07

- established the Master Book as the sole normative project-wide resource;
- absorbed the module-review manual and capability-writing guidance;
- added ODK Forms as a first-class top-level surface;
- added ODK Central and Kobo rapid-test deployment semantics;
- added XLSForm discovery, naming, batch validation and diagnostics standards;
- added destructive-action confirmation rules;
- formalised minimalist shell/search/back-navigation direction;
- formalised shared Android capability surfaces;
- formalised typed protocol-pipes design direction;
- integrated getting-started, build/install and troubleshooting guidance;
- absorbed the current conceptual/architecture/RIL/JSON/registry specification set;
- recorded known source-specification inconsistencies instead of silently correcting them;
- removed the hand-maintained Production capability list from normative doctrine;
- moved module-specific roadmap issues out of project-wide doctrine in principle.

## v1.05 - 2026-09-06

Introduced the live-working-result -> Commit lifecycle, tap-to-copy, origin-aware completion, capability-specific production UI and strengthened cross-surface/ODK parity.

## v1.04 and earlier

Established the module-folder handoff contract, one canonical capability contract across multiple interaction surfaces, ODK transport/output rules and the earlier consolidated MethodMesh architecture baseline. Historical copies remain archival provenance only.
