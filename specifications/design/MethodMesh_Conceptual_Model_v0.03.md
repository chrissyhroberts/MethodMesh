# MethodMesh Conceptual Model

**Version:** 0.03 (Draft)

---

# 1. Purpose

The MethodMesh Conceptual Model defines the fundamental concepts used to represent scientific research independently of implementation, programming language or storage technology.

MethodMesh is not primarily a workflow engine or data collection platform.

It is a conceptual framework for representing scientific understanding.

The model distinguishes between:

- the world being studied;
- the claims made about that world;
- the evidence supporting those claims.

All higher-level components of MethodMesh derive from these concepts.

---

# 2. The Scientific Model

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

# 3. Conceptual Layers

The MethodMesh conceptual model is organised into five layers.

## Layer 1 – Reality

Reality consists of the entities that become the subjects of research.

MethodMesh does not create these entities.

It represents them.

## Layer 2 – Knowledge

Knowledge consists of assertions concerning entities.

Assertions describe current scientific understanding.

Observations provide the evidence supporting those assertions.

Knowledge evolves through the continual refinement of assertions as additional evidence becomes available.

## Layer 3 – Acquisition

Methods obtain observations.

Methods do not create knowledge directly.

They create evidence from which knowledge is derived.

## Layer 4 – Orchestration

Research activities are coordinated through operational concepts including:

- Intent
- Policy
- Signals

These concepts govern execution rather than scientific understanding.

## Layer 5 – Trust

Provenance explains the origin, history and justification of observations and assertions.

It provides transparency, traceability and reproducibility throughout the research lifecycle.

---

# 4. Core Concepts

## Entity

An Entity is defined as:

> **A distinct object that may become the subject of observation, description, measurement or reasoning within a research context.**

Entities possess identity.

Entities may exist in one of three Modes:

- Physical
- Digital
- Conceptual

Entities may participate in assertions.

---

## Assertion

An Assertion is defined as:

> **A timestamped claim describing a characteristic, state or relationship concerning one or more Research Entities.**

Assertions represent scientific understanding rather than immutable truth.

Assertions may be supported, strengthened, revised, superseded or withdrawn as new observations become available.

---

## Observation

An Observation is defined as:

> **Evidence obtained through the application of a Method.**

Observations support assertions.

Observations should retain sufficient provenance to permit independent interpretation and verification.

---

## Method

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

# 5. Supporting Concepts

The following concepts support the core scientific model.

| Concept | Purpose |
|----------|---------|
| Intent | Requests one or more Methods |
| Policy | Constrains the execution of Methods |
| Provenance | Explains the origin and history of Observations and Assertions |
| Signal | Communicates changes to other components |

These concepts support the generation, governance and communication of scientific knowledge but are not themselves part of the core knowledge model.

---

# 6. Concept Relationships

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

# 7. Design Principles

The MethodMesh Conceptual Model follows the following principles.

## Scientific

The model represents scientific understanding rather than software implementation.

## Entity-centred

Research begins with identifiable entities.

Everything else exists to describe, understand or investigate those entities.

## Evidence-based

Assertions should be supported by observations wherever appropriate.

## Temporal

Scientific understanding changes over time.

MethodMesh records the evolution of understanding rather than replacing previous knowledge.

## Minimal

The model defines the smallest set of concepts necessary to represent scientific research.

## Extensible

New scientific disciplines should be representable without modification of the conceptual model.

## Implementation-independent

The conceptual model is independent of storage technology, programming language and execution environment.

---

# 8. Open Questions

The following areas remain under active development.

- Should Properties remain a separate primitive or become specialised Assertions?
- What canonical assertion vocabulary should MethodMesh define?
- How should uncertainty, confidence and evidence strength be represented?
- Which concepts belong to the conceptual model and which belong exclusively to the execution architecture?

---

# 9. Summary

MethodMesh represents scientific understanding rather than scientific data.

Research begins with Entities.

Researchers make Assertions about those Entities.

Observations provide evidence supporting those Assertions.

Methods obtain Observations.

The continual accumulation of evidence-supported Assertions represents the evolving scientific understanding of the research world.
