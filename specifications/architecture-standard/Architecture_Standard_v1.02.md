# ResearchOS Architecture Standard

**Version:** 1.02 (Draft)

---

# 1. Purpose

The ResearchOS Architecture Standard defines the engineering principles governing the implementation of ResearchOS.

It complements, but does not replace, the ResearchOS Philosophy, Conceptual Model and Registry specifications.

Where those documents define *what* ResearchOS represents, the Architecture Standard defines *how* those concepts should be implemented.

This standard establishes architectural consistency across applications, services and libraries forming part of the ResearchOS ecosystem.

---

# 2. Relationship to Other Standards

The ResearchOS documentation is organised into complementary layers.

```text
ResearchOS Philosophy
        │
ResearchOS Conceptual Model
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

# 3. Scope

This standard defines:

- architectural principles;
- service boundaries;
- implementation patterns;
- interoperability requirements;
- extension mechanisms;
- engineering conventions.

This standard does not redefine scientific concepts already specified elsewhere.

# 4. Architectural Principles

All ResearchOS implementations shall conform to the following architectural principles.

## Knowledge-first

ResearchOS is fundamentally a knowledge system.

Applications, user interfaces, workflows and services exist to create, transform, query and communicate scientific knowledge.

The core knowledge model consists of:

- Entities
- Observations
- Assertions

These concepts are defined by their respective Registry specifications and shall not be redefined by implementations.

---

## Separation of Concerns

ResearchOS separates conceptual knowledge from orchestration and implementation.

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

## Registry-driven

Canonical behaviour shall be defined by Registry specifications rather than embedded within application code.

Implementations should consume Registry definitions rather than duplicate conceptual knowledge.

---

## Service-oriented

ResearchOS capabilities shall be implemented as independent services wherever practical.

Services communicate through canonical ResearchOS objects rather than direct application-specific interfaces.

Services should remain independently deployable and reusable across projects.

---

## Technology-independent

The conceptual architecture shall remain independent of programming language, database technology, cloud provider or deployment model.

Alternative implementations should remain interoperable provided they conform to the Registry specifications and Architecture Standard.

---

## Extensible

ResearchOS shall support extension through new Registry entries, Methods, Services and Applications without modification of the core conceptual architecture.

Extensions should compose with the existing architecture rather than replace it.

# 5. Architectural Components

ResearchOS implementations are constructed from a small number of architectural component types.

Each component has a clearly defined responsibility and communicates through canonical ResearchOS objects.

## Applications

Applications provide user-facing functionality.

Applications may create Intents, display knowledge, visualise data and coordinate user interaction.

Applications should not embed scientific knowledge or implementation-specific business logic that belongs elsewhere.

---

## Services

Services implement discrete capabilities.

A Service accepts one or more canonical ResearchOS objects as input and produces one or more canonical ResearchOS objects as output.

Services should remain independently deployable and reusable.

---

## Methods

Methods define repeatable scientific or operational procedures.

Methods fulfil Intents by performing work that may produce one or more Observations.

Methods are implementation-independent and may be realised by software, hardware, people or combinations thereof.

---

## Registries

Registries define the canonical vocabulary used throughout ResearchOS.

Applications and Services should consume Registry definitions rather than duplicate them.

Registries form the authoritative source of conceptual meaning.

---

## Knowledge Store

The Knowledge Store preserves ResearchOS objects and their relationships.

Implementations may use relational, document, graph or hybrid storage technologies provided the conceptual model is preserved.

The storage technology is an implementation decision rather than an architectural requirement.

---

## Interfaces

Interfaces provide communication between architectural components.

Interfaces should exchange canonical ResearchOS objects rather than application-specific data structures.

Implementations may expose APIs, message queues, files or other transport mechanisms without changing the underlying conceptual architecture.


# 6. Interoperability

ResearchOS is designed as an interoperable ecosystem rather than a single application.

Independent applications, services and organisations should be able to exchange knowledge and capabilities without requiring shared implementation technologies.

## Canonical Objects

All communication between architectural components should use canonical ResearchOS objects.

Canonical objects preserve conceptual meaning independently of implementation.

---

## Stable Interfaces

Public interfaces should remain stable across implementation revisions wherever practical.

Evolution should occur through extension rather than breaking existing interfaces.

---

## Loose Coupling

Applications should communicate through canonical interfaces rather than direct implementation dependencies.

No application should require knowledge of another application's internal architecture.

---

## Capability Discovery

Services should expose their capabilities through declarative metadata.

Applications should discover available capabilities dynamically rather than relying on hard-coded integrations.

---

## Registry Conformance

Applications and services shall interpret canonical concepts consistently with the relevant Registry specifications.

Registries remain the authoritative source of conceptual meaning.

---

## Extensible Ecosystem

Third-party applications, methods and services should be able to participate within the ResearchOS ecosystem without modification of the core architecture.

Compliance with the Architecture Standard and Registry specifications is sufficient for interoperability.

# 7. Extension Model

ResearchOS is designed to evolve through extension rather than modification of its core architecture.

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

# 8. Conformance

An implementation conforms to the ResearchOS Architecture Standard if it:

- preserves the ResearchOS Conceptual Model;
- interprets Registry specifications consistently;
- exchanges canonical ResearchOS objects;
- maintains separation between knowledge, orchestration and implementation;
- supports interoperability through stable interfaces;
- remains extensible without modification of the core architecture.

Conformance does not require any particular programming language, database technology, operating system or deployment model.

Alternative implementations are encouraged provided they preserve the architectural principles defined by this standard.

---

# 9. Summary

The ResearchOS Architecture Standard defines the engineering principles governing the implementation of ResearchOS.

Conceptual meaning is defined by the Philosophy, Conceptual Model and Registry specifications.

The Architecture Standard defines how those concepts should be realised as interoperable software systems.

ResearchOS separates:

- conceptual knowledge;
- orchestration;
- implementation.

This separation enables independent evolution of scientific concepts, engineering infrastructure and software implementations while preserving interoperability across the ResearchOS ecosystem.
