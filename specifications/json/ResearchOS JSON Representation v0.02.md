# ResearchOS JSON Representation v0.02

## 1. Introduction

**Status:** Draft

The ResearchOS JSON Representation defines the canonical JSON object model for ResearchOS Execution Requests.

It provides a machine-readable representation of the execution model defined by the ResearchOS Architecture Standard and the semantics defined by the ResearchOS Intent Language (RIL).

This specification defines representation only.

It does not define execution behaviour, runtime architecture or transport protocols.

Those concerns are defined by companion specifications.

## 2. Purpose

The JSON Representation provides a canonical representation suitable for:

- HTTP APIs
- Android applications
- Local configuration files
- Testing
- Documentation
- Future transport bindings

Every valid JSON document represents exactly one ResearchOS Execution Request.

Alternative representations may exist without changing the meaning of the request.

### Design Goal

The JSON Representation is designed to be:

- human-readable;

- machine-readable;

- deterministic;

- transport-independent;

- directly mappable to the ResearchOS execution model.

The representation intentionally favours clarity over compactness.

## 3. Relationship to other specifications

The JSON Representation forms one member of the ResearchOS specification family.

| Specification | Responsibility |
|---------------|----------------|
| Architecture Standard | Runtime concepts and execution model |
| ResearchOS Intent Language | Human-readable semantics |
| ResearchOS JSON Representation | Canonical object representation |
| Android Intent Representation | Android transport |
| URL Representation | URL transport |
| HTTP Representation | HTTP transport |

This specification introduces no new execution semantics.

Every JSON construct maps directly to concepts defined by the Architecture Standard and the ResearchOS Intent Language.

## 4. Design Principles

The JSON Representation follows several guiding principles.

### Object-oriented

Requests are represented as collections of independent Invocation Objects.

### Human-readable

JSON SHOULD remain understandable without specialised tooling.

### Canonical

Equivalent requests SHOULD have a single preferred representation.

### Transport-independent

The representation is independent of HTTP, Android, URLs or any other transport.

### Stable

Minor revisions SHOULD preserve backwards compatibility wherever possible.

### Direct Mapping

Every JSON construct corresponds directly to a ResearchOS concept.

No JSON-specific execution semantics are introduced.

# 5. Object Model

The JSON Representation models a ResearchOS Execution Request as a collection of named Invocation Objects.

Each Invocation Object represents a single requested operation.

An Invocation Object combines:

- the requested Intent;
- the target Resource;
- optional execution constraints;
- optional execution policies;
- optional return definitions.

The JSON Representation is therefore object-oriented rather than section-oriented.

The human-readable ResearchOS Intent Language (RIL) groups concepts into the sections WHAT, WHEN, WHERE, HOW and RESULT.

The JSON Representation instead groups these concepts by Invocation Object, placing all information relating to a single operation within one object.

This improves readability, reduces cross-referencing and mirrors the internal execution model of the ResearchOS runtime.

---

## 5.1 Execution Request

Every JSON document SHALL contain exactly one Execution Request.

```json
{
  "request": {

  }
}
```

The `request` object contains one or more named Invocation Objects.

Each property name is an Invocation Identifier that is unique within the Request.

Example:

```json
{
  "request": {
    "identify": {
      "intent": "scan",
      "resource": "nfc"
    },
    "photo": {
      "intent": "capture",
      "resource": "image"
    }
  }
}
```

The property names (`identify`, `photo`) identify individual invocations within the Request.

They do not define executable Methods.

They are locally unique identifiers used for referencing relationships between Invocations.

---

## 5.2 Invocation Object

An Invocation Object SHALL contain an `intent` and a `resource`.

All other properties are optional.

Conceptually, an Invocation Object has the following structure.

```text
Invocation

├── intent
├── resource
├── type
├── parameters
├── when
├── where
├── how
└── result
```

The meaning of each property is defined by the ResearchOS Intent Language.

The JSON Representation specifies only how these properties are represented.

---

## 5.3 Relationships

Invocation Objects may reference one another.

Relationships are expressed using Invocation Identifiers.

For example:

```json
{
  "request": {
    "identify": {
      "intent": "scan",
      "resource": "nfc"
    },
    "photo": {
      "intent": "capture",
      "resource": "image",
      "when": {
        "after": "identify"
      }
    }
  }
}
```

In this example, the `photo` Invocation becomes eligible for execution only after successful completion of the `identify` Invocation.

The relationship is expressed directly within the Invocation Object, avoiding the need for separate execution graphs or cross-reference tables.

---

## 5.4 Independence

Invocation Objects are conceptually independent.

If no relationships are defined, the runtime MAY execute Invocations in any order or concurrently.

Applications SHALL NOT rely upon the order of properties within a JSON object to determine execution behaviour.

Execution ordering SHALL be defined explicitly through Invocation relationships.

# 6. Invocation Properties

Every Invocation Object SHALL contain an `intent` and a `resource`.

All remaining properties are optional.

An Invocation Object MAY therefore be represented by the following conceptual structure.

```text
Invocation

├── intent
├── resource
├── type
├── parameters
├── when
├── where
├── how
└── result
```

The properties are described in the following sections.

## 6.1 intent

The `intent` property specifies the operation requested by the Invocation.

The value SHALL be a valid ResearchOS Intent defined by the ResearchOS Intent Language.

Example

```json
{
  "identify": {
    "intent": "scan",
    "resource": "nfc"
  }
}
```

The JSON Representation assigns no additional meaning to Intent values.

Intent semantics are defined exclusively by the ResearchOS Intent Language.

## 6.2 resource

The `resource` property specifies the primary Resource upon which the Intent operates.

The value SHALL identify a valid ResearchOS Resource.

Example

```json
{
  "photo": {
    "intent": "capture",
    "resource": "image"
  }
}
```

Resource definitions are maintained by the ResearchOS Resource Registry.

The JSON Representation defines only how Resources are represented.

## 6.3 type

The optional `type` property refines the meaning of the Resource or Intent.

The interpretation of a Type depends upon the associated Intent and Resource.

Example

```json
{
  "photo": {
    "intent": "capture",
    "resource": "image",
    "type": "thermal"
  }
}
```

Types are defined by the corresponding Resource specification.

## 6.4 parameters

The optional `parameters` property supplies additional configuration for an Invocation.

Parameters are interpreted by the selected Method.

Example

```json
{
  "temperature": {
    "intent": "measure",
    "resource": "temperature",
    "parameters": {
      "unit": "celsius",
      "precision": 0.1
    }
  }
}
```

Parameter names are Method-specific.

The JSON Representation imposes no restrictions upon parameter structure beyond valid JSON.

# 7. Execution Properties

The remaining Invocation properties define how, when and where an Invocation executes, together with the information returned following execution.

These properties influence execution behaviour without changing the meaning of the requested operation.

Execution properties comprise:

- `when`
- `where`
- `how`
- `result`

Conceptually, an Invocation therefore consists of two parts.

```text
Invocation

├── Definition
│   ├── intent
│   ├── resource
│   ├── type
│   └── parameters
│
└── Execution
    ├── when
    ├── where
    ├── how
    └── result
```

Definition properties describe the requested operation.

Execution properties describe the circumstances under which the operation executes and the information returned.

## 7.1 when

The optional `when` property specifies temporal constraints governing Invocation execution.

If omitted, the Invocation becomes immediately eligible for execution.

Example

```json
{
  "photo": {
    "intent": "capture",
    "resource": "image",
    "when": {
      "after": "identify"
    }
  }
}
```

Temporal relationships are defined by the ResearchOS Intent Language.

The JSON Representation specifies only their representation.

## 7.2 where

The optional `where` property specifies spatial constraints governing Invocation execution.

Example

```json
{
  "gps": {
    "intent": "measure",
    "resource": "location",
    "where": {
      "within": {
        "resource": "study_site"
      }
    }
  }
}
```

Spatial relationships are defined by the ResearchOS Intent Language.

The JSON Representation specifies only their representation.

## 7.3 how

The optional `how` property specifies execution policies.

Execution policies influence how an Invocation executes without changing the requested operation.

Example

```json
{
  "capture": {
    "intent": "capture",
    "resource": "image",
    "how": {
      "authenticate": "fingerprint",
      "encrypt": true,
      "provenance": "full"
    }
  }
}
```

Execution policies are defined by the ResearchOS Core Policy specification.

## 7.4 result

The optional `result` property specifies the information requested following successful execution.

Returned values may be renamed by the caller.

Example

```json
{
  "identify": {
    "intent": "scan",
    "resource": "nfc",
    "result": {
      "participant_id": "participant",
      "tag_uid": "nfc_uid"
    }
  }
}
```

If omitted, implementation defaults apply.

The JSON Representation does not prescribe default return values.

# 8. Validation

A valid ResearchOS JSON document SHALL satisfy the following requirements.

## Request

- A document SHALL contain exactly one `request` object.
- The `request` object SHALL contain one or more Invocation Objects.

## Invocation Identifiers

- Invocation Identifiers SHALL be unique within a Request.
- Invocation Identifiers SHOULD be meaningful.
- Invocation Identifiers are case-sensitive.

## Required Properties

Every Invocation SHALL contain:

- `intent`
- `resource`

## Optional Properties

The following properties are optional:

- `type`
- `parameters`
- `when`
- `where`
- `how`
- `result`

## References

Properties referencing another Invocation SHALL reference a valid Invocation Identifier within the same Request.

Unresolved references SHALL invalidate the Request.

## Unknown Properties

Implementations MAY ignore unknown properties unless explicitly prohibited by policy.

Future specifications MAY define additional Invocation properties without invalidating existing Requests.

## Reserved Properties

Property names beginning with `_` are reserved for future ResearchOS specifications.

Implementations SHOULD ignore unknown reserved properties unless otherwise specified.

Reserved properties MAY be used to provide metadata, documentation, user interface hints or editor-specific information.

Example:

```json
{
  "identify": {
    "_label": "Identify participant",
    "_description": "Read participant NFC tag",
    "intent": "scan",
    "resource": "nfc"
  }
}

# 9. Canonical Examples

The following examples illustrate the preferred JSON representation of common ResearchOS Execution Requests.

These examples are informative.

They are intended to demonstrate recommended structure and conventions rather than define additional execution semantics.

---

## 9.1 Minimal Request

The simplest valid Execution Request contains a single Invocation.

```json
{
  "request": {
    "identify": {
      "intent": "scan",
      "resource": "nfc"
    }
  }
}
```

---

## 9.2 Sequential Execution

Multiple Invocations may express dependencies using the `when` property.

```json
{
  "request": {
    "identify": {
      "intent": "scan",
      "resource": "nfc"
    },
    "photo": {
      "intent": "capture",
      "resource": "image",
      "when": {
        "after": "identify"
      }
    }
  }
}
```

The `photo` Invocation becomes eligible only after successful completion of `identify`.

---

## 9.3 Authentication and Encryption

Execution policies are attached directly to the Invocation.

```json
{
  "request": {
    "capture": {
      "intent": "capture",
      "resource": "image",
      "how": {
        "authenticate": "fingerprint",
        "encrypt": true,
        "provenance": "full"
      }
    }
  }
}
```

---

## 9.4 Spatial Constraint

Spatial constraints are expressed using the `where` property.

```json
{
  "request": {
    "gps": {
      "intent": "measure",
      "resource": "location",
      "where": {
        "within": {
          "resource": "study_site"
        }
      }
    }
  }
}
```

---

## 9.5 Returned Values

Returned values may be renamed to match the caller's data model.

```json
{
  "request": {
    "identify": {
      "intent": "scan",
      "resource": "nfc",
      "result": {
        "participant_id": "participant",
        "tag_uid": "nfc_uid",
        "timestamp": "scan_time"
      }
    }
  }
}
```

The runtime returns values using the requested field names.

---

## 9.6 Parallel Execution

Invocations without explicit dependencies may execute concurrently.

```json
{
  "request": {
    "gps": {
      "intent": "measure",
      "resource": "location"
    },
    "temperature": {
      "intent": "measure",
      "resource": "temperature"
    },
    "light": {
      "intent": "measure",
      "resource": "illuminance"
    }
  }
}
```

Because no temporal relationships are defined, the runtime MAY execute these Invocations in parallel.

---

## 9.7 Complex Execution Request

A complete Execution Request may combine definition, execution constraints and requested outputs.

```json
{
  "request": {
    "identify": {
      "intent": "scan",
      "resource": "nfc",
      "type": "participant",
      "result": {
        "participant_id": "participant",
        "tag_uid": "nfc_uid"
      }
    },
    "photo": {
      "intent": "capture",
      "resource": "image",
      "type": "thermal",
      "when": {
        "after": "identify"
      },
      "where": {
        "within": {
          "resource": "clinic"
        }
      },
      "how": {
        "authenticate": "fingerprint",
        "encrypt": true,
        "provenance": "full"
      },
      "result": {
        "image": "thermal_image",
        "timestamp": "capture_time"
      }
    }
  }
}
```

This example demonstrates the complete Invocation Object model defined by this specification.

Each Invocation encapsulates:

- its requested operation (`intent`, `resource`, `type`);
- optional execution constraints (`when`, `where`);
- optional execution policies (`how`);
- requested outputs (`result`).

The Execution Request therefore consists of a collection of independent Invocation Objects linked only through explicit relationships.

# 10. Versioning

JSON representations SHOULD declare the specification version.

Example

{
    "version":"0.02"
}

Minor versions SHOULD remain backwards compatible.

Major versions MAY introduce incompatible structural changes.

# Appendix A – Mapping to the ResearchOS Intent Language

The ResearchOS JSON Representation is an object-oriented representation of the execution semantics defined by the ResearchOS Intent Language (RIL).

The two specifications describe the same execution model from different perspectives.

RIL organises concepts according to their semantic role within an execution request.

The JSON Representation organises the same concepts around Invocation Objects.

The correspondence is shown below.

| ResearchOS Intent Language | JSON Representation |
|----------------------------|---------------------|
| WHAT | Invocation Definition (`intent`, `resource`, `type`, `parameters`) |
| WHEN | `when` property |
| WHERE | `where` property |
| HOW | `how` property |
| RESULT | `result` property |

The JSON Representation introduces no additional execution semantics.

Every JSON construct corresponds directly to concepts defined by the ResearchOS Intent Language.

Conversely, every valid ResearchOS Intent Language construct can be represented using the JSON Representation.

The JSON Representation therefore serves as the canonical machine-readable representation of the ResearchOS Intent Language.

# Appendix B – Object Model

The conceptual object model defined by this specification is shown below.

```text
Document
│
├── version
└── request
    │
    ├── invocation
    │   ├── intent
    │   ├── resource
    │   ├── type
    │   ├── parameters
    │   ├── when
    │   ├── where
    │   ├── how
    │   └── result
    │
    ├── invocation
    │   └── ...
    │
    └── ...
```

Each property within the `request` object represents a single Invocation.

The property name forms the Invocation Identifier.

An Invocation Identifier is unique only within the enclosing Execution Request.

Invocation Identifiers are used to express relationships between Invocations through properties such as `when`.

Execution Requests therefore form directed execution graphs whose nodes are Invocation Objects and whose edges are defined by explicit relationships.

Implementations SHOULD derive execution order exclusively from these relationships rather than from the order of properties within the JSON document.

###End of Specification
