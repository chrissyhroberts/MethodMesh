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

# Current Status

ResearchOS is currently in the architecture and reference implementation phase.

Current work includes:

- Research philosophy
- Conceptual model
- Registry specifications
- Architecture Standard
- ResearchOS Intent Language
- Android reference implementation
- Device Services
- Orchestrator
- Native Methods

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

