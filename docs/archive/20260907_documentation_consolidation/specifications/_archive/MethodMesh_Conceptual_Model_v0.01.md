# MethodMesh Conceptual Model – Design Notes v0.01

**Status:** Exploratory Design

---

# 1. Introduction

These notes capture the emerging conceptual model behind MethodMesh.

Unlike the Architecture Standard, the Research Intent Language and the JSON Object Model, this document is intentionally exploratory. Its purpose is to record the design thinking that will eventually inform future architectural specifications.

The central question is simple:

> **What kinds of things exist within MethodMesh?**

Earlier versions of MethodMesh focused primarily on execution. Methods, Invocations, Policies and Signals were treated as the core architectural concepts.

The current direction suggests a different perspective.

MethodMesh should not simply execute research activities. It should represent the research world itself.

Execution then becomes the process by which that representation evolves.

---

# 2. The Architectural Shift

The original architecture was execution-centred.

```text
Method
    ↓
Execution
    ↓
Result
```

The emerging architecture is object-centred.

```text
Research Objects
        ↓
     Methods
        ↓
  Observations
        ↓
  Provenance
        ↓
Research Object Graph
```

The runtime maintains a graph representing the current state of the research ecosystem.

Methods no longer exist in isolation.

They create, modify and relate Research Objects.

Execution therefore changes the graph.

---

# 3. Research Objects

The current hypothesis is that every object represented by MethodMesh becomes a **Research Object**.

Every Research Object possesses identity.

A Research Object may also possess:

- a Kind;
- a Mode;
- a Status;
- one or more Traits;
- Properties;
- Relationships;
- Observations;
- Provenance.

Research Objects become the fundamental nodes of the MethodMesh graph.

Everything else exists to describe or transform those nodes.

---

# 4. Entity

Current working definition:

> **An Entity is any distinct object that can become the object of study.**

This definition deliberately avoids restricting Entities to physical objects.

Research is often conducted on organisations, outbreaks, studies, communities, digital artefacts and conceptual constructs as well as tangible objects.

The defining characteristic is therefore not tangibility.

The defining characteristic is that observations can be made about the object.

Examples include:

- participant
- tree
- PCR machine
- camera
- clinic
- household
- organisation
- outbreak
- study
- painting

All are potential objects of study.

# 5. Research Objects

The current hypothesis is that every object managed by MethodMesh is represented as a **Research Object**.

A Research Object is the fundamental unit of representation within the platform.

Research Objects are not limited to participants, specimens or files.

They include any object that the platform must identify, reason about, relate to other objects or record provenance for.

Research Objects provide the stable identity around which observations, methods and provenance are organised.

Every Research Object possesses a unique identity.

Additional characteristics are represented through a small number of orthogonal concepts rather than deep inheritance hierarchies.

The current model proposes that every Research Object is described by:

- Kind
- Mode
- Status
- Traits
- Properties
- Relationships
- Observations
- Provenance

This intentionally separates the identity of an object from its characteristics.

For example, a PCR machine does not become a different class because it is physical, observable and emits signals.

Instead, it is represented as one Research Object with those characteristics attached.

Likewise, a painting may simultaneously be:

- an Entity;
- an Artifact;
- Physical;
- Observable;
- Persistent.

These characteristics are independent.

MethodMesh therefore favours composition over inheritance.

The conceptual model is deliberately designed to minimise the number of primitive concepts while allowing rich descriptions to emerge through combinations of characteristics.

---

# 6. Kind

Kind describes the fundamental conceptual role played by a Research Object.

Kinds should remain few in number and change rarely.

Current candidate Kinds include:

- Entity
- Property
- Observation
- Method
- Signal
- Intent
- Policy
- Provenance

These Kinds represent irreducible concepts within the architecture.

Everything else should preferably be represented through Modes, Traits, Relationships or Properties rather than introducing additional Kinds.

One objective of this design is to avoid creating large taxonomies that become increasingly difficult to maintain.

Instead, MethodMesh aims to describe a wide variety of research domains using a small and stable conceptual vocabulary.

# 7. Mode

Mode describes how a Research Object exists.

Unlike Kind, Mode does not describe what an object fundamentally is.

Instead, it describes the manner in which that object exists.

Current candidate Modes are:

- Physical
- Digital
- Conceptual

Examples include:

| Research Object | Mode |
|-----------------|------|
| Tree | Physical |
| Participant | Physical |
| PCR machine | Physical |
| Painting | Physical |
| JPEG image | Digital |
| Audio recording | Digital |
| Database record | Digital |
| Study | Conceptual |
| Household | Conceptual |
| Protocol | Conceptual |

Mode is independent of Kind.

For example, a protocol may be represented as a Conceptual Research Object, while a printed copy of the same protocol may be represented as a Physical Research Object.

Similarly, a photograph may exist simultaneously as a Physical print and a Digital file.

Future versions of the model may introduce additional Modes if required.

The intention is to keep the number of Modes small and stable.

---

# 8. Status

Status describes the current lifecycle or epistemic state of a Research Object.

Unlike Mode, Status may change over time.

Current candidate Status values include:

- Planned
- Actual
- Historical
- Hypothetical
- Fictional
- Simulated

Examples include:

| Research Object | Mode | Status |
|-----------------|------|--------|
| Participant enrolled in a study | Physical | Actual |
| Planned participant | Conceptual | Planned |
| Completed study visit | Conceptual | Historical |
| Simulated outbreak | Conceptual | Simulated |
| Predicted epidemic trajectory | Conceptual | Hypothetical |
| Training patient | Conceptual | Fictional |

Status allows MethodMesh to represent future activities, historical events and simulated scenarios using the same conceptual framework as real-world observations.

---

# 9. Traits

Traits describe characteristics that may be shared by many different Research Objects.

Traits are intentionally orthogonal.

Unlike Kind, Traits are not mutually exclusive.

A Research Object may possess any combination of Traits.

Current candidate Traits include:

- Artifact
- Observable
- Measurable
- Identifiable
- Persistent
- Mutable
- Versioned
- Container
- Signal Emitter
- Mobile
- Locatable

Traits are expected to evolve over time as the platform grows.

For example, a PCR machine may be represented as:

```text
Kind
    Entity

Mode
    Physical

Status
    Actual

Traits

    Artifact
    Observable
    Measurable
    Identifiable
    Persistent
    Signal Emitter
```

A painting might be represented as:

```text
Kind
    Entity

Mode
    Physical

Status
    Actual

Traits

    Artifact
    Observable
    Persistent
```

A JPEG image might be represented as:

```text
Kind
    Research Object

Mode
    Digital

Status
    Actual

Traits

    Artifact
    Observable
    Persistent
    Versioned
```

Representing characteristics as Traits rather than subclasses avoids deep inheritance hierarchies and allows Research Objects to evolve simply by acquiring or losing characteristics.

This composition-based approach provides greater flexibility while maintaining a small conceptual vocabulary.

# 10. Relationships

Research Objects do not exist in isolation.

They form a connected graph through explicit relationships.

Relationships describe how Research Objects are associated with one another.

Current candidate relationship types include:

- has_property
- about
- observed_by
- measured_by
- created_by
- derived_from
- contains
- part_of
- located_in
- owned_by
- emitted_by
- triggers
- executed_by
- implements
- fulfils

Relationships are directional.

Each relationship has a source object, a relationship type and a target object.

For example:

```text
PCR Machine

    has_property

Operating Temperature
```

```text
Temperature Observation

    about

PCR Machine
```

```text
Temperature Observation

    created_by

Measure Temperature Method
```

Relationships are expected to become first-class architectural concepts.

This allows MethodMesh to support graph traversal, dependency analysis, provenance tracing and visualisation.

An open question remains whether relationships themselves should become Research Objects capable of possessing their own provenance and metadata.

---

# 11. Observations

Observation represents the acquisition of knowledge about a Research Object.

Unlike a Property, which describes a characteristic that exists independently of measurement, an Observation records a particular determination of that Property.

For example:

```text
Entity

    PCR Machine

Property

    Operating Temperature

Observation

    37.2 °C

Method

    Measure Temperature

Time

    2026-07-02T10:15:00Z
```

Observations therefore connect:

- the object being studied;
- the Property being observed;
- the value obtained;
- the Method used;
- the execution context;
- the provenance record.

Observations accumulate over time.

Together they form the evidence from which knowledge is derived.

Current thinking suggests that Observation may become a fundamental Kind within the MethodMesh conceptual model.

---

# 12. Signals

Signals represent detectable events or state changes produced by Research Objects.

Examples include:

- NFC tag detected
- GPS position updated
- Button pressed
- Image captured
- Temperature changed
- Door opened

Signals are emitted by Research Objects.

Earlier versions of the architecture introduced the concept of Signal Sources.

Current thinking suggests this abstraction is unnecessary.

Instead, any Entity possessing the appropriate Traits may emit Signals.

For example:

```text
Camera

    emits

Image Captured
```

```text
Participant

    emits

Heartbeat
```

```text
GPS Receiver

    emits

Location Updated
```

Signals commonly trigger Method execution.

They therefore provide the bridge between the physical or conceptual research world and the execution engine.

---

# 13. The Research Object Graph

The emerging architecture suggests that MethodMesh maintains a graph representing the research ecosystem.

Nodes within the graph include Research Objects such as:

- Entities;
- Properties;
- Observations;
- Methods;
- Signals;
- Policies;
- Provenance records.

Edges represent explicit relationships between those objects.

Unlike a traditional workflow engine, MethodMesh does not simply execute a sequence of operations.

Instead, execution changes the state of the graph.

Methods create new objects.

Observations extend existing objects.

Relationships connect objects.

Provenance records explain how those changes occurred.

The graph therefore becomes the primary representation of the research ecosystem.

Execution is one mechanism by which the graph evolves.

This graph-based representation naturally supports:

- graph visualisation;
- hierarchical tree views;
- dependency tracing;
- provenance navigation;
- AI-assisted reasoning;
- semantic search;
- workflow exploration.

Different visualisations become alternative views of the same underlying graph rather than independent data structures.

# 14. Implications for MethodMesh

The emerging conceptual model has significant implications for the future architecture of MethodMesh.

## 14.1 Architecture Standard

The Architecture Standard should evolve from an execution-centred architecture towards an object-centred architecture.

The primary architectural concept becomes the Research Object Graph.

Methods no longer simply execute.

They create, modify and relate Research Objects.

Execution therefore becomes graph transformation.

---

## 14.2 Registries

The Conceptual Model suggests that a single Resource Registry is insufficient.

Instead, registries should extend the Conceptual Model.

Potential registries include:

- Entity Registry
- Property Registry
- Trait Registry
- Relationship Registry
- Signal Registry
- Method Registry
- Policy Registry

Each registry extends a well-defined conceptual class rather than contributing to a single flat vocabulary.

---

## 14.3 Orchestrator

The orchestrator becomes responsible for more than Method execution.

It also becomes responsible for maintaining the Research Object Graph.

Future responsibilities may include:

- registering Research Objects;
- updating object characteristics;
- managing relationships;
- maintaining graph consistency;
- exposing graph queries;
- coordinating Method execution.

---

## 14.4 Visualisation

Because the runtime maintains an explicit graph, visualisation becomes a natural consequence rather than a separate subsystem.

Possible visualisations include:

- hierarchical tree views;
- relationship graphs;
- protocol flow diagrams;
- infrastructure maps;
- provenance graphs;
- dependency graphs.

All are simply different projections of the same underlying graph.

---

## 14.5 Artificial Intelligence

The explicit representation of Research Objects and their relationships provides a structured foundation for AI-assisted reasoning.

Rather than reasoning over isolated records, AI systems may reason over:

- object identity;
- properties;
- observations;
- provenance;
- relationships;
- execution history.

This supports richer navigation, explanation and discovery.

---

# 15. Open Questions

The following questions remain intentionally unresolved.

## Primitive concepts

- What is the smallest irreducible set of Research Object Kinds?
- Should Observation become a core Kind?
- Should Relationships become first-class Research Objects?

## Identity

- Should every Research Object possess globally unique identity?
- Can Properties possess independent identity?

## Traits

- Is Artifact correctly represented as a Trait?
- Are additional Traits required?
- Can Traits themselves evolve over time?

## Graph

- How should graph persistence be represented?
- How should graph versioning operate?
- How should graph queries be expressed?

## Runtime

- How should Research Objects be registered?
- How should Methods declare the Kinds and Traits they operate upon?
- How should graph updates be validated?

---

# 16. Working Summary

Current thinking suggests that MethodMesh is fundamentally a graph of Research Objects.

Each Research Object possesses:

- identity;
- Kind;
- Mode;
- Status;
- Traits;
- Properties;
- Relationships;
- Observations;
- Provenance.

Methods operate upon Research Objects.

Signals trigger Methods.

Observations extend Research Objects.

Relationships connect Research Objects.

Provenance explains how those relationships evolved.

The runtime therefore maintains a living representation of the research ecosystem.

Execution becomes one mechanism by which that representation changes.

---

# 17. Next Steps

These design notes are expected to inform the following future work:

- MethodMesh Architecture Standard v1.02;
- MethodMesh Conceptual Model specification;
- Research Object registration;
- Graph persistence model;
- Entity, Property and Trait registries;
- Graph query language;
- Graph visualisation framework;
- AI-assisted exploration tools.

These notes should remain non-normative until the conceptual model stabilises.

Once the primitive concepts have been agreed, they should be incorporated into the Architecture Standard and formalised within a dedicated MethodMesh Conceptual Model specification.
