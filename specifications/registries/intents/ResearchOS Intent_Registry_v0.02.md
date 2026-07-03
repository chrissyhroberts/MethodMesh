# ResearchOS Intent Registry

**Version:** 0.02 (Draft)

---

# 1. Purpose

The Intent Registry defines the canonical vocabulary used by ResearchOS to represent requested actions.

An Intent expresses what a user, application or automated system wishes ResearchOS to do.

Intent belongs to the orchestration layer of the ResearchOS conceptual model.

An Intent requests one or more Methods.

Methods produce Observations.

Observations may support Assertions.

Assertions describe Entities.

The Intent Registry defines the canonical Intent vocabulary recognised by ResearchOS.

It does not prescribe implementation, execution behaviour or transport mechanisms.

# 2. The Role of Intent

Research begins with an intention to perform work.

Within ResearchOS, that intention is represented as an Intent.

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

Within the ResearchOS conceptual model:

- Intent expresses requested action.

- Methods execute requested work.

- Observations provide scientific evidence.

- Assertions represent scientific understanding.

- Entities are the subjects of scientific investigation.

ResearchOS therefore separates orchestration from scientific knowledge.

Intent belongs to the orchestration layer.

Entities, Observations and Assertions belong to the knowledge layer.

# 3. Definition

An Intent is defined as:

> **A declarative request for one or more research actions to be performed.**

An Intent expresses *what* is requested rather than *how* it should be performed.

ResearchOS interprets an Intent by selecting one or more appropriate Methods capable of fulfilling the requested action.

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

An Intent participates in the ResearchOS conceptual model by providing the entry point through which research activities are requested and orchestrated.

# 4. Design Principles

The Intent Registry follows the following principles.

## Declarative

An Intent describes what work is requested rather than how it should be performed.

The selection and execution of appropriate Methods is the responsibility of ResearchOS.

---

## Independent

An Intent is independent of any particular implementation, workflow engine or execution environment.

The same Intent may be fulfilled using different Methods under different circumstances.

---

## Composable

Complex research activities should be expressed through combinations of simple Intents rather than increasingly specialised instructions.

---

## Reproducible

Equivalent Intents executed under equivalent conditions should produce comparable outcomes through equivalent Methods.

---

## Extensible

The Intent Registry defines a stable conceptual framework capable of supporting research activities across scientific disciplines.

New Intent types may be added without modification of the underlying conceptual model.

---

## Traceable

Each Intent should remain linked to the Methods selected to fulfil it and the resulting Observations that were produced.

ResearchOS therefore preserves a complete record of requested work and its execution.

---

## Implementation-independent

The conceptual definition of an Intent is independent of programming language, storage technology, workflow engine or communication protocol.

The same Intent model may be represented using JSON, APIs, message queues or other implementation technologies.

# 5. Intent Model

Every Intent represented within ResearchOS follows a common conceptual structure.

An Intent expresses a requested action independently of how that action is ultimately fulfilled.

Each Intent contains the following information.

| Field | Description |
|--------|-------------|
| **Intent ID** | Unique identifier for the Intent |
| **Verb** | Canonical RIL verb describing the requested action |
| **Target** | Entity, Method or other ResearchOS object to which the Intent applies |
| **Parameters** | Additional information required to fulfil the Intent |
| **Policy** | Optional constraints governing execution |
| **Priority** | Optional execution priority |
| **Requester** | Person, system or workflow that created the Intent |
| **Timestamp** | Time the Intent was created |
| **Status** | Current execution status |
| **Result** | Reference to the resulting Method execution or outcome |

The Intent Model is declarative rather than procedural.

It specifies what work is requested without prescribing how that work should be carried out.

ResearchOS remains responsible for selecting appropriate Methods capable of fulfilling the Intent while respecting any applicable Policies.

# 6. Relationships

Intent forms the entry point to the ResearchOS orchestration layer.

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

ResearchOS preserves these relationships together with their associated provenance.

---

# 7. Open Questions

The following areas remain under consideration.

- Should Intent cancellation and supersession be represented explicitly?
- Should Intents be immutable once submitted?
- How should long-running or continuously executing Intents be represented?
- Which execution metadata belongs to the Intent and which belongs to Method execution?

---

# 8. Summary

Intent represents requested work.

It belongs to the orchestration layer of the ResearchOS conceptual model.

Intent expresses what should happen.

Methods determine how the requested work is performed.

Methods may produce Observations.

Observations may support Assertions.

Assertions describe Entities.

Together these concepts separate research orchestration from scientific knowledge while preserving complete traceability between requested actions and the knowledge they ultimately produce.


