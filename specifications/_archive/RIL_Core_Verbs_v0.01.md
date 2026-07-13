# ResearchOS Intent Language (RIL)
## Core Verbs v0.01

## Purpose

The ResearchOS Intent Language (RIL) is built around a deliberately small and stable vocabulary of **verbs**.

Each verb represents a fundamental operation that ResearchOS can perform. New functionality should normally be introduced by defining new **types**, rather than inventing new verbs.

For example:

```json
{
  "intent": "measure",
  "type": "blood_pressure"
}
```

```json
{
  "intent": "measure",
  "type": "temperature"
}
```

Both use the same verb while operating on different types.

---

# Design Principles

Every verb should satisfy the following principles:

- Technology independent
- Single responsibility
- Composable with other verbs
- General rather than domain-specific
- Relevant to research workflows
- Stable over time

Before introducing a new verb, ask:

> **Can this be expressed using an existing verb with a new type?**

If the answer is yes, a new verb should **not** be introduced.

---

# Choosing the Right Verb

Every RIL verb answers one question:

> **"What kind of change does this action make?"**

Examples:

- **measure** changes knowledge by quantifying something.
- **ask** changes knowledge by obtaining information from a person.
- **capture** changes knowledge by acquiring raw data.
- **identify** changes knowledge by determining what something is.
- **verify** changes confidence by confirming a condition.
- **transform** changes information into new information.
- **store** changes persistence by making information durable.
- **submit** changes ownership by transferring information elsewhere.
- **notify** changes what another person or system knows.
- **wait** changes execution by advancing time.
- **encrypt** changes representation by protecting information.

If a proposed verb does not clearly describe a single kind of change, it probably belongs as a **type**, **policy**, or **option**, rather than as a verb.

---

# 1. Knowledge Acquisition

*Changes what the system knows by acquiring new information.*

| Verb | Meaning |
|------|---------|
| **ask** | Obtain information from a person. |
| **observe** | Record a qualitative observation. |
| **measure** | Quantify a property of the world. |
| **capture** | Acquire raw media or sensor data. |
| **scan** | Acquire or decode an external identifier or signal. |
| **identify** | Determine what an entity is. |
| **verify** | Confirm that a condition is satisfied. |
| **locate** | Determine the spatial position of an entity. |

---

# 2. Knowledge Management

*Changes how knowledge is organised or persisted.*

| Verb | Meaning |
|------|---------|
| **create** | Create a new entity or resource. |
| **find** | Search for entities matching criteria. |
| **retrieve** | Obtain a known entity or previously stored information. |
| **store** | Persist information locally. |
| **update** | Modify an existing entity or resource. |
| **link** | Create a relationship between entities. |
| **unlink** | Remove a relationship between entities. |
| **copy** | Duplicate an entity or resource. |
| **move** | Relocate an entity or resource. |
| **delete** | Remove an entity or resource. |
| **submit** | Transfer information to another system. |

### Find vs Retrieve

Use **find** when the desired object is not yet known.

```text
find participant where surname="Smith"
```

Use **retrieve** when the object is already known.

```text
retrieve participant p001
```

---

# 3. Knowledge Transformation

*Changes information into new information.*

| Verb | Meaning |
|------|---------|
| **transform** | Produce new information from existing information. |
| **annotate** | Add descriptive information. |
| **classify** | Assign a category. |
| **compare** | Evaluate similarities or differences. |
| **filter** | Select a subset. |
| **aggregate** | Combine multiple observations. |
| **summarise** | Produce a condensed representation. |
| **merge** | Combine compatible entities or datasets. |
| **split** | Divide an entity or dataset. |
| **convert** | Change representation or format. |

---

# 4. Security

*Changes the protection or trust characteristics of information.*

| Verb | Meaning |
|------|---------|
| **encrypt** | Protect information by encryption. |
| **decrypt** | Recover encrypted information. |
| **hash** | Produce a cryptographic digest. |
| **sign** | Produce a digital signature. |
| **verify_signature** | Validate a digital signature. |
| **anonymise** | Remove identifying information. |
| **pseudonymise** | Replace identifiers while preserving linkage. |
| **redact** | Remove selected information. |

---

# 5. Workflow

*Changes the execution state of a workflow or protocol.*

| Verb | Meaning |
|------|---------|
| **open** | Launch a resource or application. |
| **wait** | Suspend execution until a defined condition. |
| **schedule** | Arrange future execution. |
| **begin** | Start a workflow or task. |
| **pause** | Temporarily suspend execution. |
| **resume** | Continue a suspended workflow. |
| **repeat** | Execute again. |
| **complete** | Mark a task as completed. |
| **cancel** | Terminate execution. |

---

# 6. Communication

*Changes what people or external systems know.*

| Verb | Meaning |
|------|---------|
| **notify** | Send a notification. |
| **message** | Send asynchronous communication. |
| **call** | Initiate real-time communication. |
| **broadcast** | Send information to multiple recipients. |
| **share** | Make information available to another party. |
| **report** | Communicate structured findings or results. |

---

# 7. System

*Interrogates or administers the ResearchOS runtime.*

| Verb | Meaning |
|------|---------|
| **discover** | Find available resources or services. |
| **describe** | Return metadata about a resource. |
| **list** | Enumerate resources of a given type. |
| **check** | Evaluate the current state of something. |
| **validate** | Test whether something conforms to rules. |
| **ping** | Test availability or responsiveness. |
| **import** | Bring external resources into ResearchOS. |
| **export** | Transfer resources out of ResearchOS. |

---

# Candidate Future Verbs

These are promising additions but are intentionally excluded from Core Verbs v0.01 until clear use-cases emerge.

| Verb | Potential purpose |
|------|-------------------|
| **invoke** | Request another executable component (method, workflow or protocol) to perform work. |

---

# Reserved Words

The following common computing terms are intentionally **not** part of the core RIL vocabulary.

| Word | Preferred verb |
|------|----------------|
| read | retrieve |
| write | store / update / submit |
| save | store |
| load | retrieve |
| print | report / export |

---

# Philosophy

The RIL vocabulary should remain intentionally small.

ResearchOS should grow by introducing new **types**, **policies** and **modules**, rather than continually adding new verbs.

A stable verb vocabulary makes the language easier to learn, easier to document, easier to implement and more interoperable across applications and platforms.

The verb describes **what action is performed**.

The type describes **what the action operates on**.

Everything else is expressed through timing, location, execution policies, user-interface policies, permissions, provenance and return definitions.
