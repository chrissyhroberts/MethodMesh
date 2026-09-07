# MethodMesh JSON Object Model

**Version:** 0.01 (Draft)

---

# 1. Purpose

The MethodMesh JSON Object Model defines the canonical JSON representation of core MethodMesh objects.

It translates the MethodMesh Conceptual Model and Registry specifications into implementation-facing structures that can be exchanged between applications, services, methods and storage systems.

The Object Model does not redefine conceptual meaning.

Conceptual meaning is defined by the Philosophy, Conceptual Model and Registry specifications.

This specification defines how those concepts are represented in JSON for interoperability.

---

# 2. Scope

This specification defines JSON structures for:

- Entity
- Observation
- Assertion
- Intent
- Method reference
- Provenance reference
- Policy reference
- Registry reference

It also defines shared conventions for:

- identifiers;
- timestamps;
- object types;
- metadata;
- references;
- provenance links;
- extension fields.

This specification does not define storage technology, API behaviour, database schema or runtime execution.

---

# 3. Design Principles

## Conceptual alignment

Every JSON object must correspond to a concept defined in the MethodMesh Conceptual Model or Registry specifications.

## Interoperability

Objects should be exchangeable between applications, services and storage systems without loss of conceptual meaning.

## Minimal core

Each object should define a small required core with optional extension fields.

## Explicit references

Relationships between objects should be represented using explicit object references rather than implicit naming conventions.

## Provenance-ready

Objects should support links to provenance without requiring every provenance model detail to be embedded directly.

## Extensible

Domain-specific fields should be added through namespaced extensions rather than modification of the core object model.

# 4. Shared Object Envelope

All MethodMesh JSON objects use a common envelope.

The envelope provides consistent identity, typing, versioning and extension behaviour across all object types.

Individual object specifications define the contents of the `attributes`, `relationships` and `extensions` fields.

```json
{
  "id": "ros:entity:participant-001",
  "object_type": "entity",
  "schema_version": "0.01",
  "created_at": "2026-07-03T14:30:00Z",
  "updated_at": "2026-07-03T14:30:00Z",
  "attributes": {},
  "relationships": {},
  "provenance": [],
  "extensions": {}
}
```

### 4.1 Common Object Fields

Every MethodMesh object contains a common set of fields that provide identity, typing and lifecycle information.

| Field | Required | Description |
|---------|:--------:|-------------|
| **id** | Yes | Unique identifier for the object. |
| **object_type** | Yes | Canonical MethodMesh object type. |
| **schema_version** | Yes | Version of the JSON Object Model used by the object. |
| **created_at** | Yes | Timestamp when the object was created. |
| **updated_at** | No | Timestamp when the object was last modified. |
| **attributes** | Yes | Object-specific descriptive properties. |
| **relationships** | No | References to other MethodMesh objects. |
| **provenance** | No | References describing the origin or history of the object. |
| **extensions** | No | Additional namespaced fields defined outside the core specification. |

The common object fields provide a consistent structure across all MethodMesh object types while allowing each object type to define its own attributes and relationships.

---

### 4.2 Canonical Object Types

The JSON Object Model defines canonical representations for the core concepts of the MethodMesh conceptual model.

The initial object types are:

| Object Type | Represents |
|-------------|------------|
| **Entity** | A thing that may become the subject of scientific investigation. |
| **Observation** | Evidence acquired about one or more Entities. |
| **Assertion** | Scientific understanding describing one or more Entities. |
| **Intent** | A declarative request for research work to be performed. |
| **Method** | A repeatable procedure capable of fulfilling an Intent. |
| **Policy** | Rules or constraints governing execution or behaviour. |
| **Provenance** | Information describing the origin, lineage or history of an object. |
| **Registry Entry** | A canonical definition contained within a MethodMesh Registry. |

Additional object types may be introduced in future versions provided they remain consistent with the MethodMesh Conceptual Model and Architecture Standard.

# 5. Canonical Object Definitions

The following sections define the canonical JSON representation of each MethodMesh object type.

The examples are illustrative rather than exhaustive.

Implementations may include additional fields provided they remain consistent with this specification and the MethodMesh Architecture Standard.

---

## 5.1 Entity Object

The Entity Object represents a single Entity within the MethodMesh knowledge graph.

It provides identity together with the descriptive attributes and relationships required to reference the Entity from other MethodMesh objects.

### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"entity"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Entity properties. |
| relationships | No | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

### Minimal Representation

```json
{
  "id": "entity:participant-001",
  "object_type": "entity",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:30:00Z",
  "attributes": {
    "entity_type": "Person"
  }
}
```

### Notes

Scientific knowledge describing an Entity is represented through Assertion objects rather than embedded directly within the Entity itself.

## 5.2 Observation Object

The Observation Object represents evidence acquired, derived or simulated within MethodMesh.

It records observation content together with the Entity being observed, the property or phenomenon of interest, and the Method used to obtain the Observation.

### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"observation"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Observation-specific fields. |
| relationships | Yes | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

### Minimal Representation

```json
{
  "id": "observation:height-001",
  "object_type": "observation",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:35:00Z",
  "attributes": {
    "observation_mode": "direct",
    "property": "height",
    "content": {
      "value": 172,
      "unit": "cm"
    }
  },
  "relationships": {
    "entity": "entity:participant-001",
    "method": "method:height-measurement"
  }
}
```

## 5.3 Assertion Object

The Assertion Object represents a timestamped claim describing one or more Entities.

It records the claim being made, the Entity or Entities described by that claim, and any Observations that may support it.

### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"assertion"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Assertion-specific fields. |
| relationships | Yes | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

### Minimal Representation

```json
{
  "id": "assertion:height-001",
  "object_type": "assertion",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:40:00Z",
  "attributes": {
    "assertion_type": "has_value",
    "property": "height",
    "claim": {
      "value": 172,
      "unit": "cm"
    }
  },
  "relationships": {
    "entity": "entity:participant-001",
    "supported_by": [
      "observation:height-001"
    ]
  }
}
```
## 5.4 Intent Object

The Intent Object represents a declarative request for research work to be performed.

An Intent expresses *what* is requested rather than *how* it should be carried out. MethodMesh fulfils an Intent by selecting and executing one or more appropriate Methods.

### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"intent"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Intent-specific fields. |
| relationships | No | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

### Minimal Representation

```json
{
  "id": "intent:measure-height-001",
  "object_type": "intent",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:45:00Z",
  "attributes": {
    "verb": "measure",
    "target": "entity:participant-001",
    "property": "height"
  }
}
```

### Notes

An Intent does not itself perform work.

Execution is performed by one or more Method objects capable of fulfilling the requested action.

The outcome of an Intent may include one or more Observation objects, which in turn may support one or more Assertions.

## 5.5 Method Object

The Method Object represents a repeatable procedure capable of fulfilling one or more Intents.

A Method defines how work may be performed. Method execution may produce one or more Observation objects.

### Fields

| Field | Required | Type | Description |
|---------|:--------:|------|-------------|
| id | Yes | String | Unique identifier. |
| object_type | Yes | String | Always `"method"`. |
| schema_version | Yes | String | JSON Object Model version. |
| created_at | Yes | Timestamp | Creation timestamp. |
| updated_at | No | Timestamp | Last modification timestamp. |
| attributes | Yes | Object | Method-specific fields. |
| relationships | No | Object | Links to related objects. |
| provenance | No | Array | Provenance references. |
| extensions | No | Object | Additional namespaced fields. |

### Minimal Representation

```json
{
  "id": "method:height-measurement",
  "object_type": "method",
  "schema_version": "0.01",
  "created_at": "2026-07-03T15:50:00Z",
  "attributes": {
    "method_type": "measurement",
    "name": "Height measurement"
  }
}
```

# 6. Common Relationship Patterns

MethodMesh objects are connected through explicit object identifiers.

Relationships should be represented using stable field names inside the `relationships` object.

The following relationship names are canonical for the initial JSON Object Model.

| Relationship | Source Object | Target Object | Meaning |
|---|---|---|---|
| `entity` | Observation, Assertion, Intent | Entity | The Entity being observed, described or targeted. |
| `method` | Observation | Method | The Method used to produce the Observation. |
| `supported_by` | Assertion | Observation | Observations that may support the Assertion. |
| `requests` | Intent | Method | Method requested or selected to fulfil the Intent. |
| `produces` | Method | Observation | Observation produced by a Method. |
| `supersedes` | Assertion, Intent | Assertion, Intent | Earlier object replaced or superseded by this object. |
| `derived_from` | Observation | Observation | Source Observation used to derive another Observation. |

Relationships should use object identifiers rather than embedded objects.

This keeps objects independently serialisable while allowing the MethodMesh graph to be reconstructed from object references.

## Example

```json
{
  "relationships": {
    "entity": "entity:participant-001",
    "method": "method:height-measurement"
  }
}
```

# 7. Implementation Considerations

The JSON Object Model defines a canonical representation of MethodMesh objects for interoperability.

It does not prescribe implementation technology, storage architecture or transport protocols.

Implementations may:

- store objects in relational, document, graph or hybrid databases;
- exchange objects using REST, GraphQL, message queues, files or other protocols;
- extend objects through the `extensions` field;
- introduce additional object types consistent with the MethodMesh Conceptual Model.

Implementations should not alter the semantic meaning of canonical object types or relationships defined by this specification.

---

# 8. Conformance

An implementation conforms to the MethodMesh JSON Object Model if it:

- represents canonical MethodMesh concepts using the object structures defined in this specification;
- preserves object identity through stable identifiers;
- maintains explicit object relationships;
- preserves compatibility with the MethodMesh Conceptual Model, Registry specifications and Architecture Standard;
- supports extension without modification of the core object definitions.

Conformance does not require any particular programming language, database, messaging protocol or software framework.

---

# 9. Summary

The MethodMesh JSON Object Model provides a canonical JSON representation of the core MethodMesh concepts.

It bridges the gap between the conceptual architecture and software implementation by defining how MethodMesh objects are serialised for storage, exchange and processing.

The Object Model preserves the separation between:

- **Entities**, representing the things under study;
- **Observations**, representing scientific evidence;
- **Assertions**, representing scientific understanding;
- **Intents**, representing requested work; and
- **Methods**, representing repeatable procedures.

Together with the Registry specifications and Architecture Standard, the JSON Object Model provides the implementation foundation for interoperable MethodMesh applications and services.




