# ResearchOS Entity Registry

**Version:** 0.02 (Draft)

---

# 1. Purpose

The Entity Registry defines the canonical vocabulary used by ResearchOS to represent the things that become the subject of scientific research.

Entities form the foundation of the ResearchOS knowledge model.

Observations provide evidence concerning Entities.

Assertions describe Entities.

This registry defines the classes of Entities recognised by ResearchOS.

It does not prescribe implementation, storage technology or execution behaviour.

# 2. The Role of Entities

Research begins with entities.

An Entity represents a distinct thing that exists, has existed, may exist, or may be conceived within the scope of scientific enquiry.

Entities form the foundation of the ResearchOS knowledge model.

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

Within the ResearchOS conceptual model:

- Entities are the subjects of research.
- Observations provide evidence concerning Entities.
- Assertions describe Entities.
- Methods obtain Observations concerning Entities.

Entities exist independently of the observations or assertions made about them.

ResearchOS therefore represents Entities separately from the scientific knowledge accumulated about them.

# 3. Definition

An Entity is defined as:

> **A distinct thing that exists, has existed, may exist, or may be conceived, and which may become the subject of scientific observation, description, measurement or reasoning.**

Entities possess identity independently of any observations or assertions made about them.

ResearchOS represents Entities separately from the scientific knowledge accumulated about them.

Entities may exist in one of three Modes:

- Physical
- Digital
- Conceptual

Entities may possess one or more Traits describing their characteristics.

Entities may become the subject of one or more Observations.

One or more Assertions may describe an Entity.

Entities participate in the ResearchOS knowledge model by serving as the subjects about which scientific understanding is developed.

# 4. Design Principles

The Entity Registry follows the following principles.

## Reality

Entities exist independently of the observations or assertions made about them.

ResearchOS represents Entities rather than creating them.

---

## Identity

Every Entity possesses a persistent identity that distinguishes it from every other Entity.

Identity is independent of names, labels or descriptions.

---

## Entity-centred

Research begins with Entities.

Observations provide evidence concerning Entities.

Assertions describe Entities.

Scientific understanding develops by accumulating evidence-supported assertions about Entities.

---

## Mode-independent

Entities may exist in different Modes without changing their conceptual identity.

ResearchOS currently recognises three Entity Modes:

- Physical
- Digital
- Conceptual

Mode describes how an Entity exists rather than what it is.

---

## Observable

An Entity is capable of becoming the subject of scientific observation, description, measurement or reasoning.

Not every Entity can be observed directly, but every Entity may become the subject of scientific investigation.

---

## Extensible

The Entity Registry defines a stable conceptual framework capable of representing entities from any scientific discipline.

New Entity types may be added without modification of the underlying conceptual model.

---

## Implementation-independent

The conceptual definition of an Entity is independent of programming language, storage technology or software architecture.

The same Entity model may be represented using relational databases, graph databases, document stores or other implementation technologies.

# 5. Registry Entry Model

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

Research projects may extend Entity definitions with additional Traits, Assertions or implementation-specific metadata provided these extensions remain consistent with the ResearchOS conceptual model.

# 6. Canonical Entity Types

The Entity Registry defines a canonical vocabulary of Entity types that may become the subject of scientific research.

These definitions provide a common conceptual language across disciplines while remaining sufficiently general to support extension within individual research projects.

The categories described below are informative rather than restrictive.

An individual Entity belongs to one canonical Entity type.

Entity types may share common Traits, participate in similar Assertions and be investigated using similar Methods.

The following sections define the current canonical Entity types recognised by ResearchOS.

# 7. Relationships

Entities occupy the central position within the ResearchOS knowledge model.

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

ResearchOS preserves these relationships together with their associated provenance.

---

# 8. Open Questions

The following areas remain under consideration.

- Should additional Entity Modes be recognised in future versions?
- Should the canonical Entity taxonomy evolve through extension registries or revision of the core registry?
- Which Traits should remain universal and which should be domain-specific?
- Should Entity lifecycle states become a separate registry?

---

# 9. Summary

Entities represent the things that become the subject of scientific research.

They exist independently of observations, assertions and scientific interpretation.

ResearchOS represents Entities separately from the knowledge accumulated about them.

Methods obtain Observations concerning Entities.

Observations may support Assertions.

Assertions describe Entities.

Together these concepts provide the foundation of the ResearchOS knowledge model.
