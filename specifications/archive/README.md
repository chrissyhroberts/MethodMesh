
# ResearchOS

ResearchOS is an open, modular platform for building, executing and orchestrating digital research workflows.

Rather than being another electronic data capture (EDC) system, ResearchOS provides reusable research capabilities that can be embedded within existing ecosystems such as ODK, KoBoToolbox, REDCap, custom web applications and future platforms.

The project is built around a family of open specifications that separate architecture, execution, interoperability and protocol orchestration. This separation allows research methods to be implemented once and reused across studies, organisations and software platforms.

---

# Vision

Research software is frequently rewritten for every study despite solving the same underlying problems: identifying participants, capturing observations, interacting with hardware, collecting measurements and maintaining provenance.

ResearchOS aims to provide a common execution platform for these reusable operations.

Applications remain responsible for study design, questionnaires and data management, while ResearchOS provides specialised research capabilities through a stable execution runtime and a platform-independent intent language.

---

# Architecture

ResearchOS is organised into complementary layers.

```text
Research Protocol
        │
        ▼
Protocol Definition Language (planned)
        │
        ▼
ResearchOS Orchestrator (planned)
        │
        ▼
ResearchOS Intent Language (RIL)
        │
        ▼
Architecture Standard (AS1.00)
        │
        ▼
Platform Runtime
        │
        ▼
Android • iOS • Desktop • Server • Embedded
```

Each layer has a single responsibility.

- **Architecture Standard (AS1.00)** defines the execution model.
- **Runtime** implements that model.
- **RIL** defines a platform-independent language for requesting research operations.
- **Bindings** define how RIL is represented over different transports.
- **The Orchestrator** executes protocols over time.

---

# Project Status

| Component | Status |
|-----------|--------|
| Architecture Standard (AS1.00) | ✅ Stable |
| Runtime 1.x | ✅ Implemented |
| Native Methods | ✅ Initial implementation |
| ResearchOS Intent Language (RIL v0.03) | ✅ Conceptually complete |
| Core Verbs | ✅ Initial specification |
| JSON Binding | 🚧 Planned |
| Android Intent Binding | 🚧 Planned |
| HTTP Binding | 🚧 Planned |
| ResearchOS Orchestrator | 🚧 Planned |
| Protocol Definition Language | 📋 Planned |

---

# Runtime 1.x

The current implementation is the reference implementation of **Architecture Standard v1.00 (AS1.00)**.

Implemented components include:

- Execution engine
- Method registry
- Device Service framework
- Signal model
- Presentation separation
- Native ResearchOS methods

Current native methods include:

- NFC Read
- NFC Write
- Calibrated Scale
- GPS / Locate Target

These demonstrate that observations, interventions, measurements and hardware integrations all execute through the same runtime model.

---

# ResearchOS Intent Language (RIL)

RIL is the interoperability layer of ResearchOS.

Every request is expressed using five independent concerns:

- **WHAT** — requested actions
- **WHEN** — temporal behaviour
- **WHERE** — spatial constraints
- **HOW** — execution policies
- **RESULT** — returned information

This separation allows the same request to execute consistently regardless of transport mechanism or operating system.

## Language, not API

ResearchOS deliberately separates **language** from **transport**.

A RIL request has the same meaning whether it is carried using:

- Android Intents
- JSON
- HTTP APIs
- URLs and deep links
- QR codes
- NFC tags
- Web callbacks
- Future transports

Bindings describe how requests are represented.

RIL defines what those requests mean.

---

# Interoperability

ResearchOS is designed to complement existing software rather than replace it.

External systems can request specialised operations while continuing to manage their own workflows and data models.

ResearchOS internally maintains canonical methods, execution records and provenance, while callers remain free to map returned values into their own field names and schemas.

---

# Roadmap

## Phase 1 — Runtime

- Architecture Standard
- Runtime implementation
- Native methods
- Device Services

**Status:** Complete

## Phase 2 — Interoperability

- ResearchOS Intent Language
- JSON binding
- Android binding
- HTTP binding
- Core registries (Resources, Types, Policies)

**Status:** RIL complete, bindings in progress

## Phase 3 — Orchestration

The ResearchOS Orchestrator will execute complete research protocols.

Responsibilities include:

- participant timelines
- reminders and notifications
- protocol scheduling
- workflow progression
- retries and missed windows
- event-driven execution
- protocol branching

Typical workflow:

1. Participant enrols.
2. Protocol is installed.
3. Activities are scheduled.
4. Notifications launch the appropriate application.
5. External applications invoke ResearchOS through RIL.
6. Results and provenance are returned.
7. Progress is tracked throughout the study.

---

# Specification Family

ResearchOS is defined by a family of open specifications.

Current:

- Architecture Standard (AS1.00)
- ResearchOS Intent Language (RIL)
- Core Verbs

Planned:

- Core Resources
- Core Types
- Core Policies
- Protocol Definition Language
- RIL JSON Binding
- Android Intent Binding
- HTTP Binding
- ResearchOS Orchestrator Specification

Together these specifications define the platform independently of any programming language, operating system or transport mechanism.

---

# Contributing

ResearchOS is developed in the open.

The project welcomes discussion around architecture, interoperability, reusable research methods and open standards for digital research.
