# ResearchOS

> **An open architecture for scientific knowledge, interoperable research methods and reusable digital research infrastructure.**

ResearchOS is an open, modular platform for designing, executing and preserving scientific research. It separates scientific knowledge from software implementation, enabling methods, applications and services to evolve independently while remaining fully interoperable.

Rather than replacing existing research software, ResearchOS provides a conceptual framework and execution architecture that integrates with existing ecosystems including ODK, KoBoToolbox, REDCap and custom research applications.

**Core principle:**
> **Research should outlive the software used to perform it.**

---

## Table of Contents

1. [Philosophy and Vision](#philosophy-and-vision)
2. [Conceptual Model](#conceptual-model)
3. [Architecture](#architecture)
4. [Android Application](#android-application)
5. [Capabilities](#capabilities)
6. [Scheduler and Orchestration](#scheduler-and-orchestration)
7. [Getting Started](#getting-started)
8. [Development](#development)
9. [Project Structure](#project-structure)
10. [License](#license)

---

# Philosophy and Vision

## The Problem

Research software has traditionally been built as isolated applications that tightly couple user interface, workflow logic, data storage and scientific methods into monolithic systems. This creates several fundamental problems:

- **Data silos**: Research data becomes trapped within specific applications
- **Method lock-in**: Scientific procedures are embedded in proprietary code
- **Software obsolescence**: When software becomes unmaintained, research becomes inaccessible
- **Limited interoperability**: Different tools cannot easily exchange knowledge
- **Reproducibility challenges**: Methods cannot be reliably reproduced across systems

## The ResearchOS Approach

ResearchOS treats research as a collection of **interoperable knowledge concepts** rather than application-specific data:

- **Entities** — things being studied
- **Observations** — evidence obtained through measurement
- **Assertions** — claims supported by evidence
- **Methods** — repeatable procedures for obtaining observations
- **Intent** — declarative requests for research operations

By separating these concerns, ResearchOS enables:

- **Knowledge preservation** independent of software implementation
- **Method reusability** across different applications and platforms
- **Interoperability** through shared conceptual vocabulary
- **Long-term reproducibility** through explicit provenance
- **Extensibility** without breaking existing workflows

## Scientific Foundation

ResearchOS is built on the scientific method itself:

1. **Reality precedes knowledge** — Research begins with entities that exist independently
2. **Knowledge is provisional** — Scientific understanding evolves as new evidence emerges
3. **Evidence underpins knowledge** — Assertions should be supported by observations
4. **Methods generate evidence** — Repeatable procedures obtain observations
5. **Provenance creates trust** — Transparent records enable verification and reproduction

## Vision

ResearchOS aims to become not just an operating system for conducting research, but a **durable representation of scientific knowledge itself** — enabling research to remain accessible, verifiable and reusable long after the original software systems become obsolete.

---

# Conceptual Model

ResearchOS represents scientific understanding using four fundamental concepts that mirror the scientific process.

## Core Concepts

### Entity

An **Entity** is anything that may become the subject of scientific study.

> **Definition:** A distinct object that may be observed, described, measured or reasoned about within a research context.

Entities possess identity and may exist in three modes:
- **Physical** (e.g., a person, device, location)
- **Digital** (e.g., a dataset, algorithm, digital twin)
- **Conceptual** (e.g., a theory, protocol, research design)

### Observation

An **Observation** is evidence obtained through the application of a method.

> **Definition:** Evidence obtained through the systematic application of a repeatable procedure.

Observations:
- Support assertions
- Retain provenance for verification
- May be quantitative or qualitative
- Include measurement metadata

### Assertion

An **Assertion** is a claim about one or more entities.

> **Definition:** A timestamped claim describing a characteristic, state or relationship concerning one or more entities.

Assertions:
- Represent current scientific understanding
- Are supported by observations
- May be strengthened, revised or superseded
- Are provisional rather than absolute

### Method

A **Method** is a repeatable procedure for obtaining observations.

> **Definition:** A systematic, repeatable procedure used to generate evidence.

Methods:
- Define how observations are obtained
- May involve human activity, laboratory procedures, computation or combinations
- Create observations, not knowledge directly
- Should be sufficiently documented for reproduction

## The Knowledge Graph

These concepts form a directed graph representing scientific understanding:

```text
        Entity (the thing)
           ▲
           │
    described by
           │
        Assertion (the claim)
           ▲
           │
    supported by
           │
        Observation (the evidence)
           ▲
           │
     produced by
           │
        Method (the procedure)
           ▲
           │
    requested by
           │
        Intent (the request)
```

## Supporting Concepts

Additional concepts support orchestration and governance:

| Concept | Purpose |
|---------|---------|
| **Intent** | Declarative request for one or more methods |
| **Policy** | Constraints governing method execution |
| **Provenance** | Origin and history of observations and assertions |
| **Signal** | Communication of state changes to external systems |
| **Trait** | Reusable characteristics applicable to multiple entities |

---

# Architecture

ResearchOS implements a layered architecture that separates concerns and enables independent evolution of each layer.

## Architectural Layers

```text
┌─────────────────────────────────────────┐
│      Research Philosophy                │
├─────────────────────────────────────────┤
│      Conceptual Model                   │
├─────────────────────────────────────────┤
│      Registry Specifications            │
│  (Entity, Observation, Assertion, ...)  │
├─────────────────────────────────────────┤
│      Architecture Standard              │
├─────────────────────────────────────────┤
│      JSON Object Model                  │
├─────────────────────────────────────────┤
│  ResearchOS Intent Language (RIL)       │
├─────────────────────────────────────────┤
│  Applications • Services • Methods      │
├─────────────────────────────────────────┤
│ Android • iOS • Desktop • Web • Server  │
└─────────────────────────────────────────┘
```

Each layer has distinct responsibilities and can evolve independently while maintaining consistency with layers above it.

## Architectural Principles

### 1. Knowledge-First

ResearchOS is fundamentally a **knowledge system**. Applications and services exist to create, transform, query and communicate scientific knowledge rather than simply process data.

### 2. Separation of Concerns

Three distinct layers are strictly separated:

```text
Knowledge Layer       → Entity, Observation, Assertion
Orchestration Layer   → Intent, RIL, Method Selection
Implementation Layer  → Services, Storage, Transport, UI
```

### 3. Registry-Driven

Canonical behavior is defined by **Registry specifications** rather than embedded in code. Implementations consume registry definitions instead of duplicating conceptual knowledge.

### 4. Service-Oriented

Capabilities are implemented as independent services that:
- Accept canonical ResearchOS objects as input
- Produce canonical ResearchOS objects as output
- Remain independently deployable
- Compose with other services

### 5. Technology-Independent

The conceptual architecture is independent of:
- Programming language
- Database technology
- Operating system
- Cloud provider
- Deployment model

Alternative implementations remain interoperable if they conform to registry specifications and architectural standards.

### 6. Extensible

The platform supports extension through:
- New registry entries
- New methods and services
- New applications
- Domain-specific vocabularies

Extensions **compose** with existing architecture rather than replacing it.

## Interoperability

ResearchOS enables interoperability through:

1. **Canonical Objects** — Standard representations of entities, observations, assertions
2. **Stable Interfaces** — Public APIs remain backward-compatible
3. **Loose Coupling** — Components communicate through canonical interfaces
4. **Capability Discovery** — Services expose capabilities through declarative metadata
5. **Registry Conformance** — Consistent interpretation of core concepts

## ResearchOS Intent Language (RIL)

RIL is a platform-independent language for requesting research operations. Rather than describing **how** to perform operations, RIL describes:

- **WHAT** should happen
- **WHEN** it should occur
- **WHERE** it should occur  
- **HOW** execution should be governed
- **WHAT** result should be returned

RIL provides the common contract between workflows, applications, services and the execution engine, allowing research operations to be expressed independently of implementation technology.

---

# Android Application

The ResearchOS Android reference implementation demonstrates how the conceptual architecture can be realized as a working platform for digital research.

## Overview

The Android app provides:

1. **Standalone Dashboard** — Direct access to all capabilities for testing and manual operations
2. **External Integration** — Public intent interface for ODK, KoboToolbox and custom applications
3. **Task Scheduling** — Automated execution of recurring research activities
4. **Data Export** — JSON-based output with linked attachments
5. **Modular Architecture** — Self-contained capability modules with clear boundaries

## How It Works

### Execution Model

ResearchOS uses a canonical execution model:

```text
External Request (Android Intent or RIL)
    ↓
Method ID Resolution
    ↓
Capability Screen (if interaction required)
    ↓
Method Execution
    ↓
ExecutionResult (with canonical graph objects)
    ↓
Compact Return to Caller
```

### Integration with ODK/Kobo

Forms can invoke ResearchOS capabilities using Android intents in the `body::intent` column with `field-list` appearance:

```text
com.example.researchos.EXECUTE_METHOD(
    method_id='attestation.create',
    input_event_payload_hash=${form_hash},
    input_verification_method='Fingerprint',
    return_mode='flat'
)
```

ResearchOS executes the requested capability and returns results directly to the calling form.

### Dashboard Usage

From the ResearchOS home screen:

1. **Browse Capabilities** — View all available methods grouped by module
2. **Search** — Filter capabilities by name or description
3. **Configure** — Set input parameters for selected capability
4. **Execute** — Run the capability and view results
5. **Export** — Save results as timestamped JSON with attachments

### Output Export

Dashboard executions provide an **Export** function that:
- Writes timestamped JSON containing selected return fields
- Includes manifest of linked attachments (images, files, etc.)
- Saves files with common base name for easy bundling
- Default location: `Documents/ResearchOS/outputs`
- Configurable output directory via Storage Access Framework

---

# Capabilities

The Android implementation currently includes the following capabilities. Each is a self-contained module accessible through the common `com.example.researchos.EXECUTE_METHOD` intent interface.

## Data Collection and Capture

### Calibrated Scale (`calibrated_scale`)

Presents a physically calibrated horizontal or vertical continuum for measuring subjective responses with precise scaling.

**Features:**
- Single-value, minimum/maximum, and range selection modes
- Configurable labels, prompts and hints
- Physical calibration metadata
- Custom measurement units

**Use Cases:** Pain scales, quality ratings, preference intensity, visual analog scales

---

### SVG Polygon Selector (`svg.select`)

Loads named SVG diagrams and enables interactive region selection with full audit trails.

**Features:**
- Single selection, multi-selection, or strict ordered polygon selection
- Timestamped audit events for each selection
- Backstep-only removal (forward-only selection)
- Custom SVG assets loaded from app storage

**Use Cases:** Body part selection, anatomical diagrams, facility mapping, spatial selection

---

### Scaled Photo Capture (`scaled_photo.capture`)

Captures photographs with ruler-based physical calibration and optional grid-based region selection.

**Features:**
- Ruler calibration for physical measurements
- Configurable grid overlay for region selection
- Returns original image, annotated image, and selection data
- Grid selection metadata with coordinates

**Use Cases:** Lesion measurement, crop assessment, spatial documentation, calibrated imaging

---

### Code Scanner (`qr.scan`)

Scans and decodes QR codes, Data Matrix and supported 1D barcodes.

**Features:**
- Automatic format detection
- Multiple barcode format support
- Returns decoded payload, format and evidence hash
- Camera-based real-time scanning

**Use Cases:** Sample tracking, participant IDs, inventory management, asset tracking

---

### GPS Target Navigator (`gps_target_navigator`)

Guides users toward a target latitude/longitude coordinate with real-time navigation feedback.

**Features:**
- Real-time distance and bearing updates
- Arrival detection with configurable radius
- Optional camera-based AR guidance overlay
- Location history and path tracking

**Use Cases:** Site revisit, geolocation verification, spatial sampling, field navigation

---

## Spatial Measurement

### Spatial Geometry Module

Provides three sensor-based spatial measurement capabilities using phone orientation sensors.

#### Tree Height Measurement (`tree_height_measurement`)

Estimates vertical object height using angular measurements.

**Inputs:** Distance to object, angle measurements
**Returns:** Calculated height, formula, sensor readings

#### Slope Inclination Measurement (`slope_inclination_measurement`)

Measures ground slope and grade using device accelerometer.

**Returns:** Slope angle, grade percentage, sensor orientation

#### Distance Estimation (`geometry_distance_estimation`)

Estimates distance from known object size and angular measurements.

**Inputs:** Known object width/height, angular measurements
**Returns:** Estimated distance, calculation method, sensor data

**Use Cases:** Field surveys, environmental assessment, agricultural measurements

---

## Discrete Choice Experiments

Five validated study task designs for preference elicitation and decision research:

### Pairwise Comparison (`dce.pairwise`)

Presents two alternatives; participant selects preferred option.

### MaxDiff/Best-Worst (`dce.maxdiff`)

Presents multiple items; participant selects best and worst.

### Ranking (`dce.ranking`)

Presents items for rank-ordering by preference.

### Points Allocation (`dce.points`)

Distributes fixed points across alternatives to indicate strength of preference.

### Conjoint Selection (`dce.conjoint`)

Presents multi-attribute profiles for preference-based selection.

**Common Features:**
- Configurable rounds, items, classes, profiles
- Randomization with seed control
- Timestamped response capture
- Audit trail for all selections

**Use Cases:** Preference elicitation, willingness-to-pay, policy research, product design

---

## NFC Capabilities

### NFC Tag Read (`nfc_tag_read`)

Reads NDEF records and tag metadata from NFC tags.

**Returns:**
- Tag UID
- Technology type
- Memory capacity and writability
- Text/URI/JSON records
- Raw NDEF record data

---

### NFC Tag Write (`nfc_tag_write`)

Writes caller-supplied content to NFC tags.

**Features:**
- Text, URI, JSON or custom NDEF content
- Replace or blank-only write policy
- Read-back verification
- Write status confirmation

---

### NFC Tag Wipe (`nfc_tag_wipe`)

Removes user NDEF content from tags and reports resulting state.

---

### NFC Credential Provisioning (`nfc_credential_provisioning`)

Creates PIN-protected portable field credentials and writes them to NFC tags.

**Features:**
- PIN-protected credential creation
- Cryptographic signature for verification
- Write verification without exposing secret
- Portable credential format

---

### NFC Credential Verification (`nfc_credential_verification`)

Reads and verifies NFC credentials.

**Features:**
- PIN request and validation
- Issuer signature verification
- Credential integrity check
- Compact verification result

---

### Protocol NFC Tracking

Suite of capabilities for maintaining protocol progress on participant NFC cards:

#### `protocol_nfc_provision`
Provisions a new protocol definition onto an NFC card.

#### `protocol_nfc_check`
Checks participant eligibility before starting a protocol step.

#### `protocol_nfc_complete`
Marks a protocol step as complete on the card.

#### `protocol_nfc_reconstruct`
Reconstructs a lost card from authoritative records.

#### `protocol_nfc_override`
Applies justified flag/bit overrides with authorization.

**Features:**
- Compact offline protocol state storage
- Definition hash for integrity
- Preserves unrelated NDEF records
- JSON or UI-based definition loading
- Study-specific workflow support

**Use Cases:** Multi-visit studies, longitudinal research, protocol compliance tracking

---

## Security and Verification

### Traceable Attestation (`attestation.create`)

Creates device-signed, hash-chained event attestations for research provenance.

**Verification Methods:**
- Fingerprint/device credential
- QR code scan
- NFC tag read
- Study password

**Features:**
- Optional RFC 3161 trusted timestamp
- Device key signature
- Hash chain linking
- Compact verification evidence

**Returns:**
- Public key
- Signature
- Chain link
- Verification evidence
- Optional full RFC 3161 proof

**Use Cases:** Form submission proof, event logging, data integrity, audit trails

---

### Attestation Chain Anchor (`attestation.anchor_bundle`)

Exports current attestation chain head and signed public evidence for independent verification.

**Use Cases:** Nightly study anchors, independent verification, audit preparation

---

### Local Device Authentication (`admin_fingerprint_confirmation`)

Standalone biometric or device credential authentication for access control.

**Supported Methods:**
- Fingerprint
- Device PIN
- Pattern
- Password
- Combined device credential

**Note:** Unlike attestation, this provides local access control without claiming to prove a research event.

---

## Communication

### SMS Sender (`sms.send`)

Sends text messages to specified phone numbers with delivery confirmation.

**Inputs:**
- Phone number
- Message text (constructed by caller)

**Returns:**
- Send status
- Message hash
- SMS part count
- Sent timestamp

**Use Cases:** Participant notifications, appointment reminders, study communications

---

## Workflow Integration

### ODK Form Launcher (`odk_form_launcher`)

Discovers installed ODK/Kobo projects and forms, enables selection, and launches chosen form.

**Features:**
- Automatic project/form discovery
- Multi-project support
- Proper package routing
- Returns launch confirmation

**Use Cases:** Multi-form workflows, form chaining, cross-application orchestration

---

## System Inspection and Discovery

### Android App Inspector (`android_app_inspector`)

Safe discovery and testing tool for Android integration points.

**Capabilities:**
- Lists installed launchable applications
- Inspects exported components (activities, services, receivers, providers)
- Probes common public intent actions and URI schemes
- Tests explicit component/action combinations
- Captures result codes, URIs, and returned extras
- Saves tested integration definitions locally

**Boundaries:**
- Respects non-exported components
- Honors permission requirements
- Does not bypass authentication
- Reports only public API surfaces

**Use Cases:** Integration discovery, third-party app testing, interoperability research

---

### Bluetooth Device Inspector (`bluetooth_device_inspector`)

Discovery and profiling tool for Bluetooth hardware.

**Capabilities:**
- Scans nearby BLE devices
- Connects to selected devices
- Enumerates GATT services and characteristics
- Reads readable endpoints
- Subscribes to notification streams via CCCD
- Decodes printable values
- Reports paired classic Bluetooth serial candidates
- Saves discovered profiles to device registry

**Use Cases:** Sensor discovery, device profiling, hardware integration, IoT research

---

### Bluetooth Printer (`bluetooth_printer`)

Connects to Bluetooth thermal printers and prints text content.

**Features:**
- Bluetooth device connection
- Text-based printing
- Print status confirmation

**Use Cases:** Receipt printing, label generation, field documentation

---

## Sensor Integration

### Sensor Firmware Installer (`sensor_firmware_installer`)

Installs firmware onto ESP32-based research sensors via Bluetooth.

**Features:**
- Wireless firmware updates
- ESP32-C3 support
- Update status tracking
- Version management

---

### Sensor Provisioner (`sensor_provisioner`)

Configures and provisions research sensors for field deployment.

**Features:**
- Sensor configuration interface
- Deployment parameters
- Sensor registration
- Readiness verification

---

# Scheduler and Orchestration

ResearchOS includes a core scheduler for organizing recurring field tasks.

## Scheduler Features

### Scheduling Capabilities

- **Cron-based scheduling**: Five-field cron expressions (minute, hour, day, month, weekday)
- **Pre-defined patterns**: Hourly, daily, weekly, monthly schedules
- **Flexible targets**:
  - XLSForm in ODK Collect or Kobo Collect
  - Web forms (HTTPS URLs)
  - ResearchOS capabilities directly
  - Clipboard and other reusable outputs

### Task Chains

Tasks can be chained together to create workflows:
- Configurable execution order
- Automatic continuation after success
- Retry logic with configurable attempts
- Retry windows and intervals
- Custom notifications
- Pause/resume controls
- Completion tracking

### Schedule Management

From the **Scheduler** screen:

1. **Create Schedule**
   - Name the schedule
   - Configure actions in sequence
   - Set cron pattern or select preset
   - Configure notifications
   - Set retry policy

2. **Manage Schedules**
   - View all active schedules
   - Pause/resume individual schedules
   - Test immediate execution
   - Edit existing schedules
   - Delete schedules

3. **Import/Export**
   - Export individual or all schedules
   - Copy, save, or share bundles
   - Generate QR codes for transfer
   - Write to NFC tags
   - Import from text, QR, or NFC
   - Integrity verification via SHA-256

### Schedule Examples

**Daily at 9 AM:**
```
0 9 * * *
```

**Every hour:**
```
0 * * * *
```

**Every 5 minutes:**
```
*/5 * * * *
```

**Every Monday at 9 AM:**
```
0 9 * * 1
```

### Integration

Scheduled notifications launch configured actions directly. ODK/Kobo forms return to the scheduled workflow automatically, and chained actions continue in sequence.

---

# Getting Started

## Prerequisites

- Android device running Android 7.0 (API 24) or higher
- For development: Android Studio Arctic Fox or later
- For ODK integration: ODK Collect or KoboCollect installed

## Installation

### From Source

1. Clone the repository:
```bash
git clone https://github.com/chrissyhroberts/ResearchOS.git
cd ResearchOS
```

2. Build and test:
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

3. Install on device:
```bash
./gradlew installDebug
```

Or open the project in Android Studio and run normally.

## Basic Usage

### Using the Dashboard

1. Open ResearchOS app
2. Browse available capabilities by category
3. Select a capability to configure
4. Set input parameters
5. Execute the capability
6. View results
7. Export if needed

### Integrating with ODK

1. Create an XLSForm with a `field-list` group
2. Add intent column with method invocation:
```
com.example.researchos.EXECUTE_METHOD(method_id='qr.scan',return_mode='flat')
```
3. Add output fields to receive results
4. Deploy form to ODK Collect
5. Fill form — ResearchOS capabilities launch automatically

### Creating Schedules

1. Open **Scheduler** from home screen
2. Tap **Create schedule**
3. Name your schedule
4. Select target type (ODK form, web form, or capability)
5. Configure schedule pattern
6. Set notification preferences
7. Configure retry policy
8. Save schedule

---

# Development

## Project Structure

```
app/src/main/java/com/example/researchos/
├── core/
│   ├── researchos/          # Canonical architecture objects
│   │   └── runtime/         # Method contract and execution engine
│   └── scheduling/          # Scheduler implementation
├── modules/                 # Self-contained capability modules
│   ├── attestation/
│   ├── calibratedscale/
│   ├── choiceexperiment/
│   ├── nfc/
│   ├── qrcode/
│   └── [...]
├── platform/                # Android and device service boundaries
├── transport/               # RIL parsing and Android intent routing
└── ui/                      # Compose dashboard and presentation
```

## Adding a New Capability

1. Create module folder: `modules/<capability>/`

2. Implement `As100Method`:
```kotlin
class MyMethod : As100Method {
    override val methodId = "my.capability"
    override val descriptor = MethodDescriptor(...)
    
    override fun execute(request: ExecutionRequest): ExecutionResult {
        // Implementation
    }
}
```

3. Create `ResearchOSModule`:
```kotlin
object MyCapabilityModule : ResearchOSModule {
    override val methods = listOf(MyMethod())
    override val screens = listOf(MyCapabilityScreen())
    // RIL bindings, dependencies, etc.
}
```

4. Add capability screen if interaction required

5. Add tests:
   - Method output verification
   - RIL resolution
   - Return formatting
   - Invalid request handling

6. Document in `modules/<capability>/docs/README_<Capability>.md`

7. Add example XLSForm

## Testing

### Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build
```bash
./gradlew assembleDebug
```

### Device Testing

Device-dependent features require physical device testing:
- Camera capabilities
- NFC read/write
- Biometric authentication
- GPS/location
- Bluetooth
- ODK integration

## Architectural Guidelines

### DO

- ✅ Use canonical `ExecutionRequest`/`ExecutionResult`
- ✅ Consume registry definitions
- ✅ Implement capabilities as independent modules
- ✅ Return canonical graph objects with provenance
- ✅ Use existing device service boundaries
- ✅ Follow naming conventions from capability documentation standard

### DON'T

- ❌ Create alternative execution models
- ❌ Embed conceptual knowledge in implementation code
- ❌ Bypass module boundaries
- ❌ Duplicate device access code
- ❌ Create capability-specific transport parsers
- ❌ Hard-code registry concepts

---

# Project Structure

## Documentation

- `specifications/` — Philosophy, conceptual model, architecture standard, registries
  - `design/` — Conceptual model specifications
  - `architecture-standard/` — Implementation architecture
  - `registries/` — Entity, observation, assertion, intent, trait registries
  - `ril/` — ResearchOS Intent Language specification
  - `json/` — JSON object model
  - `core-verbs/` — RIL verb vocabulary

- `docs/` — Developer guides
  - `ANDROID_DEVELOPER_GUIDE.md` — Android implementation details
  - `CAPABILITY_DOCUMENTATION_STANDARD.md` — Module documentation requirements

- `app/src/main/java/com/example/researchos/modules/*/docs/` — Per-capability documentation with examples

## Examples

- `examples/` — Sample workflows and integration examples
- `xlsforms/` — Example XLSForms demonstrating capabilities

## Firmware

- `firmware/` — Embedded sensor firmware
  - `esp32c3_aht20_ble/` — ESP32-C3 temperature/humidity sensor with BLE

---

# Roadmap

## Phase 1 — Foundation ✅ (Complete)

- ✅ Research Philosophy
- ✅ Conceptual Model
- ✅ Registry Specifications
- ✅ Architecture Standard
- ✅ JSON Object Model

## Phase 2 — Reference Implementation (Current)

- ✅ Android Runtime
- ✅ Orchestrator and execution engine
- ✅ Device Services (NFC, Bluetooth, GPS, sensors)
- ✅ Native Methods (20+ capabilities)
- ✅ Intent execution and RIL support
- 🔄 Android interoperability discovery
- 🔄 Extended Bluetooth device profiling

## Phase 3 — Proof of Architecture (Planned)

Demonstrate that ResearchOS can:

1. Represent a complete research design
2. Represent a complete research protocol
3. Create, analyze and interpret a study dataset using only the ResearchOS graph

Successful completion will provide end-to-end validation of the ResearchOS architecture.

## Future Directions

- iOS reference implementation
- Web-based runtime
- Server-side orchestrator
- Extended registry vocabularies
- Cross-platform knowledge store
- Protocol Definition Language (PDL)
- Enhanced analysis capabilities

---

# Design Principles

ResearchOS adheres to the following principles:

- **Knowledge-first** — Represents scientific understanding, not just data
- **Registry-driven** — Canonical behavior defined by specifications
- **Service-oriented** — Capabilities as composable, independent services  
- **Technology-independent** — Concepts transcend implementation choices
- **Extensible** — New capabilities without modifying core architecture
- **Interoperable** — Standard interfaces enable ecosystem integration
- **Open by design** — Transparent, verifiable, reusable

---

# Contributing

ResearchOS is an open project intended to support reusable scientific infrastructure across disciplines, organizations and platforms.

Contributions are welcome in:

- New capability modules
- Platform implementations (iOS, web, desktop)
- Registry extensions
- Documentation improvements
- Example workflows
- Testing and validation
- Bug reports and feature requests

Please ensure contributions:
- Follow architectural principles
- Maintain registry conformance
- Include appropriate tests
- Provide clear documentation
- Preserve backward compatibility where possible

---

# License

ResearchOS is an open project intended to support reusable scientific infrastructure across disciplines, organisations and platforms.

[License details to be specified]

---

# Citation

If you use ResearchOS in your research, please cite:

```
ResearchOS: An open architecture for scientific knowledge, 
interoperable research methods and reusable digital research infrastructure.
https://github.com/chrissyhroberts/ResearchOS
```

---

# Contact and Support

- **Repository:** https://github.com/chrissyhroberts/ResearchOS
- **Issues:** https://github.com/chrissyhroberts/ResearchOS/issues
- **Discussions:** https://github.com/chrissyhroberts/ResearchOS/discussions

---

# Acknowledgments

ResearchOS builds on concepts from digital epidemiology, research data management, scientific workflow systems, and the broader open science community.

Special recognition to the ODK, KoboToolbox, and REDCap communities for pioneering interoperable research data collection.
