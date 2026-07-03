# ResearchOS Intent Language (RIL)
## Core Verbs v0.02

---

# 1. Purpose

The ResearchOS Intent Language defines the canonical verbs used to express requests for action within ResearchOS.

RIL sits in the orchestration layer of the ResearchOS conceptual model.

An Intent requests one or more Methods.

Methods produce Observations.

Observations may support Assertions.

Assertions describe Entities.

RIL therefore does not define scientific knowledge directly.

It defines the action language through which research activities are requested, coordinated and executed.

The Core Verbs Registry defines the stable vocabulary of verbs recognised by RIL.

New functionality should normally be introduced by defining new types, targets, parameters or policies rather than inventing new verbs.

# 2. The Role of Core Verbs

Core verbs express what a user, system or Method is asking ResearchOS to do.

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

Within the ResearchOS conceptual model:

- Intents express requested action.
- Core verbs define the canonical action language.
- Methods perform the requested work.
- Observations, Assertions and Entities remain part of the scientific knowledge model.

RIL therefore separates the language of action from the language of scientific understanding.

# 3. Design Principles

The RIL Core Verbs Registry follows the following principles.

## Minimal

The core verb vocabulary should remain as small as possible.

New capabilities should normally be expressed through targets, parameters, policies or methods rather than by introducing additional verbs.

---

## Consistent

Each core verb should have a single, well-defined meaning.

A verb should not perform different conceptual operations depending on context.

---

## Composable

Complex research activities should be expressed by combining multiple simple intents rather than creating increasingly specialised verbs.

---

## Domain-independent

Core verbs should remain applicable across all scientific disciplines.

They describe actions rather than discipline-specific concepts.

---

## Human-readable

RIL statements should be understandable by researchers without requiring knowledge of implementation details.

The language should resemble ordinary scientific instruction wherever practical.

---

## Machine-interpretable

Every core verb should possess a precise operational meaning that can be interpreted consistently by ResearchOS.

---

## Extensible

The Core Verbs Registry provides a stable foundation upon which higher-level domain vocabularies may be constructed without modifying the core language itself.

# 4. Core Verb Registry

The RIL Core Verb Registry defines the stable vocabulary of actions recognised by ResearchOS.

Each verb represents a single conceptual operation.

The meaning of a verb is independent of the Entity, Method or scientific discipline to which it is applied.

Additional behaviour is expressed through targets, parameters, policies and composition rather than by introducing new verbs.

Each registry entry contains the following fields.

| Field | Description |
|--------|-------------|
| **Verb** | Canonical RIL verb |
| **Purpose** | Conceptual meaning of the verb |
| **Typical Target** | Entity, Method, Observation, Assertion or other ResearchOS object |
| **Parameters** | Common parameters accepted by the verb |
| **Result** | Expected outcome of successful execution |
| **Examples** | Informative examples |

The Core Verb Registry is descriptive rather than prescriptive.

It defines the meaning of each verb independently of any particular implementation.

The following sections define the canonical RIL verbs recognised by ResearchOS.

# 5. Core Verb Definitions

Each RIL core verb is defined using a common structure.

| Field | Description |
|--------|-------------|
| **Verb** | Canonical RIL verb |
| **Purpose** | Conceptual meaning of the verb |
| **Behaviour** | Expected behaviour when executed |
| **Typical Targets** | Common ResearchOS objects acted upon |
| **Typical Parameters** | Common parameters accepted |
| **Result** | Expected outcome |
| **Examples** | Informative examples |

The following sections define the canonical RIL verbs.

---

## CREATE

**Purpose**

Create a new ResearchOS object.

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

A new object exists within ResearchOS.

**Examples**

```ril
CREATE ENTITY Person
CREATE PROJECT "Malaria Study"
```

---

## OBSERVE

...

---

## MEASURE

...

# 6. Composition

Complex research activities should be expressed through combinations of simple verbs rather than introducing additional specialised verbs.

Examples...

---

# 7. Summary

The RIL Core Verbs Registry defines the stable action vocabulary of ResearchOS.

Core verbs express intent.

Methods execute intent.

Scientific knowledge remains represented through Entities, Observations and Assertions.

The core verb vocabulary should remain stable and minimal.
