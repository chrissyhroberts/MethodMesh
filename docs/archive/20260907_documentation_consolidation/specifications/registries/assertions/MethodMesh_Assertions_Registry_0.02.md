# MethodMesh Assertion Registry

**Version:** 0.02 (Draft)

---

# 1. Purpose

The Assertion Registry defines the canonical language used by MethodMesh to represent scientific understanding.

Assertions describe Entities.

They express the current scientific understanding of those Entities based upon available evidence.

Observations may support Assertions.

This registry defines the classes of Assertions recognised by MethodMesh.

It does not prescribe implementation, storage technology or execution behaviour.

# 2. The Role of Assertions

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

# 3. Definition

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

# 4. Design Principles

The Assertion Registry follows the following principles.

## Scientific

Assertions represent scientific understanding.

They describe what is currently understood about an Entity based upon the available evidence.

---

## Evidence-based

Assertions should be supported by one or more Observations wherever appropriate.

The strength of an Assertion depends upon the quality and quantity of the supporting evidence rather than the Assertion itself.

---

## Temporal

Assertions exist at a point in time.

Scientific understanding evolves through the creation of new Assertions rather than modification of previous Assertions.

MethodMesh preserves this historical record.

---

## Independent

Assertions are conceptually independent of the Observations that may support them.

An Assertion may be supported by multiple Observations.

An Observation may support multiple Assertions.

---

## Minimal

Each Assertion should express a single scientific claim.

Complex understanding should emerge from multiple Assertions rather than compound statements.

---

## Extensible

The Assertion Registry defines a stable conceptual framework capable of representing scientific understanding across disciplines.

New Assertion types may be added without modification of the underlying conceptual model.

---

## Implementation-independent

The conceptual definition of an Assertion is independent of programming language, storage technology or software architecture.

The same Assertion model may be represented using relational databases, graph databases, document stores or other implementation technologies.

# 5. Registry Entry Model

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

# 6. Canonical Assertion Types

The Assertion Registry defines a canonical vocabulary of Assertion types used to represent scientific understanding within MethodMesh.

These definitions provide a common conceptual language for describing Entities across scientific disciplines while remaining sufficiently general to support extension within individual research projects.

The categories described below are informative rather than restrictive.

Each Assertion represents a single scientific claim concerning one or more Entities.

Assertion types may be supported by different forms of Observation and may be applied across multiple domains of research.

The following sections define the current canonical Assertion types recognised by MethodMesh.

# 7. Relationships

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

# 8. Open Questions

The following areas remain under consideration.

- Should confidence be represented as part of an Assertion or separately from the Assertion itself?
- Should MethodMesh define a canonical vocabulary for common scientific Assertions?
- How should conflicting Assertions concerning the same Entity be represented?
- Should assertions always retain explicit links to their supporting Observations?

---

# 9. Summary

Assertions represent scientific understanding.

They describe the characteristics, states, relationships and interpretations associated with one or more Entities.

Assertions do not represent immutable truth.

They represent the current state of scientific understanding based upon the available evidence.

Methods produce Observations.

Observations may support Assertions.

Assertions describe Entities.

Together these concepts provide the knowledge layer of the MethodMesh conceptual model.
