# ResearchOS Assertion Registry v0.01

**Status:** Draft

---

# 1. Purpose

The Assertion Registry defines the canonical language used by ResearchOS to represent scientific knowledge.

ResearchOS does not store knowledge as isolated records.

Instead, it stores timestamped assertions describing the current understanding of the research world.

Assertions are supported by observations and accompanied by provenance.

This registry defines the assertion language available to ResearchOS.

It does not prescribe implementation, storage technology or execution behaviour.

---

# 2. The Assertion Model

Research begins with the existence of an Entity.

Researchers make assertions about that Entity.

Observations provide evidence supporting those assertions.

```

```
                 Entity
              (the thing)

                    │
      described by assertions

                    ▼

               Assertion
              (the claim)

                    ▲
      supported by observations

                    │

             Observation
             (the evidence)
```

```markdown

ResearchOS therefore distinguishes between:

- the thing that exists;
- the claims made about it;
- the evidence supporting those claims.

Knowledge emerges from the accumulation of supported assertions.

---

# 3. Definition

An Assertion is defined as:

> **A timestamped claim describing a characteristic, state or relationship concerning one or more Research Entities.**

Assertions do not represent truth.

They represent the current understanding of the research world, supported by available evidence.

Assertions may be strengthened, revised, superseded or withdrawn as new observations become available.

---

# 4. Principles

The Assertion Registry follows the following principles.

## Claims, not truth

Assertions represent scientific claims.

ResearchOS does not distinguish between correct and incorrect assertions.

It records what was asserted, when, by whom and upon what evidence.

## Evidence-based

Assertions should be supported by one or more observations whenever appropriate.

## Temporal

Assertions exist at a point in time.

Understanding evolves through the creation of new assertions rather than modification of previous assertions.

## Minimal

The registry should define the smallest vocabulary capable of representing research knowledge.

## Precise

Each assertion should represent one unique semantic concept.

Synonymous assertions should be avoided.

## Independent

Assertions are independent of storage technology.

The same conceptual model may be implemented using relational databases, graph databases or append-only logs.

---

# 5. Registry Entry Model

Each assertion definition contains the following fields.

| Field | Description |
|--------|-------------|
| **Name** | Canonical assertion name |
| **Description** | Short definition |
| **Assertion** | Human-readable statement |
| **Source Entity Type** | Valid source entity types |
| **Target Entity Type** | Valid target entity types or literal values |
| **Chainable** | Whether the assertion naturally propagates |
| **Evidence** | Typical observations supporting the assertion |
| **Examples** | Informative examples |

