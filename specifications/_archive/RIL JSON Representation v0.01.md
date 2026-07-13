# RIL JSON Representation v0.01

**Status:** Draft

This specification defines the canonical JSON representation of the ResearchOS Intent Language (RIL).

It specifies how RIL requests are represented using JSON while preserving the semantics defined by the ResearchOS Intent Language specification.

This specification defines representation only.

It does not define:

- the meaning of RIL requests;
- execution behaviour;
- transport protocols;
- runtime architecture.

Those concerns are defined by companion specifications.

---

# 1. Purpose

The JSON Representation provides a canonical, human-readable representation of RIL suitable for:

- HTTP APIs;
- Android applications;
- web applications;
- local configuration files;
- testing;
- documentation;
- interoperability.

Every valid JSON document corresponds to exactly one valid RIL request.

The same request may be represented using other representations without changing its meaning.

# 2. Design Principles

The JSON representation follows several guiding principles.

## Human-readable

Requests SHOULD remain understandable without specialised tooling.

---

## Canonical

A given request SHOULD have a single preferred JSON representation.

---

## Stable

Representations SHOULD evolve conservatively.

Minor additions SHOULD NOT invalidate existing requests.

---

## Transport-independent

The JSON representation does not define transport.

The same JSON document may be transmitted through:

- HTTP;
- Android Intents;
- local files;
- web callbacks;
- message queues.

---

## Direct Mapping

Every JSON construct corresponds directly to a concept defined by RIL.

No JSON-specific semantics are introduced by this specification.

# 3. Request Structure

Every JSON document represents exactly one RIL Request.

The root object SHALL contain a single `request` object.

```json
{
  "request": {

  }
}
```

The request object contains the five sections defined by the ResearchOS Intent Language.

| Section | Purpose |
|---------|---------|
| what | Requested actions |
| when | Temporal behaviour |
| where | Spatial constraints |
| how | Execution policies |
| result | Returned information |

Only the **what** section is mandatory.

All other sections are optional.

A minimal valid request is therefore:

```json
{
  "request": {
    "what": [
      {
        "intent": "scan",
        "resource": "nfc"
      }
    ]
  }
}
```

The omission of an optional section indicates that default behaviour defined by the corresponding specification applies.

# 4. WHAT

The **what** section defines one or more Actions to be performed.

The value of **what** SHALL be an object.

Each property name is an Action identifier.

Each property value defines the Action.

```json

{

  "what": {

    "identify": {

      "intent": "scan",

      "resource": "nfc"

    }

  }

}

```

The Action identifier SHALL be unique within the Request.

Action identifiers are referenced by the **when**, **where**, **how** and **result** sections.

An Action consists of:

| Property | Required | Description |

|----------|----------|-------------|

| intent | Required | Requested operation |

| resource | Required | Resource upon which the intent operates |

| type | Optional | Resource or intent refinement |

| parameters | Optional | Additional configuration |

Example:

```json
{
  "what": [

    {
      "id": "identify",

      "intent": "scan",

      "resource": "nfc",

      "type": "participant"
    }

  ]
}
```

---

## Multiple Actions

Multiple actions are represented by additional array elements.

```json
{
  "what": [

    {
      "id": "identify",
      "intent": "scan",
      "resource": "nfc"
    },

    {
      "id": "photo",
      "intent": "capture",
      "resource": "image"
    },

    {
      "id": "verify",
      "intent": "verify",
      "resource": "fingerprint"
    }

  ]
}
```

Actions are conceptually independent.

Ordering and dependencies are defined by the **when** section rather than the order in which actions appear.

# 5. WHEN

The **when** section defines the temporal behaviour of one or more Actions.

The **when** section never defines *what* should happen.

It only defines *when* Actions become eligible for execution.

Execution timing is therefore independent of Action definition.

---

## 5.1 Structure

The value of **when** SHALL be an object.

```json
{
  "when": {

  }
}
```

Temporal constraints MAY apply to:

- the entire Request;
- individual Actions;
- groups of Actions.

---

## 5.2 Action References

Temporal relationships reference Actions using their `id`.

Example:

```json
{
  "what": [

    {
      "id": "identify",
      "intent": "scan",
      "resource": "nfc"
    },

    {
      "id": "photo",
      "intent": "capture",
      "resource": "image"
    }

  ],

  "when": {

    "photo": {

      "after": "identify"

    }

  }

}
```

The order of Actions within the **what** array has no semantic meaning.

Execution order is defined exclusively by **when**.

---

## 5.3 Temporal Relationships

The following relationships are defined by RIL.

| Property | Description |
|----------|-------------|
| immediately | Execute without delay |
| after | Execute after another Action |
| before | Execute before another Action |
| at | Execute at a specified time |
| during | Execute during an interval |
| until | Continue until a condition becomes true |
| repeat | Repeat execution |
| every | Fixed execution interval |
| once | Execute exactly once |
| on | Execute when an event occurs |

Future specifications MAY define additional temporal relationships.

---

## 5.4 Conditions

Execution MAY depend upon one or more conditions.

Example:

```json
{
  "when": {

    "capture": {

      "after": "identify",

      "on": "fingerprint_verified"

    }

  }

}
```

Conditions evaluate architectural Signals or Knowledge rather than user interface state.

---

## 5.5 Scheduling

Scheduling information MAY be supplied.

Example:

```json
{
  "when": {

    "survey": {

      "at": "2026-08-14T09:00:00Z"

    }

  }

}
```

or

```json
{
  "when": {

    "survey": {

      "every": "24h"

    }

  }

}
```

The interpretation of schedules is implementation dependent.

---

## 5.6 Parallel Execution

Actions without temporal dependencies MAY execute concurrently.

Example:

```json
{
  "what": [

    {
      "id":"photo",
      "intent":"capture",
      "resource":"image"
    },

    {
      "id":"gps",
      "intent":"measure",
      "resource":"location"
    }

  ]

}
```

Since neither Action references the other, both are immediately eligible for execution.

---

## 5.7 Default Behaviour

If the **when** section is omitted:

- all Actions become immediately eligible;
- execution order is implementation dependent.

Applications SHOULD NOT rely upon array order.

Temporal behaviour SHOULD always be expressed explicitly where ordering is significant.

# 6. WHERE

The **where** section defines the spatial context within which one or more Actions may execute.

The **where** section never defines *what* should happen.

It only constrains *where* execution is permitted or expected.

Spatial behaviour is therefore independent of Action definition.

---

## 6.1 Structure

The value of **where** SHALL be an object.

```json
{
  "where": {

  }
}
```

Spatial constraints MAY apply to:

- the entire Request;
- individual Actions;
- groups of Actions.

---

## 6.2 Action References

Spatial constraints reference Actions using their `id`.

Example:

```json
{
  "where": {

    "photo": {

      "within": {

        "resource": "clinic"

      }

    }

  }

}
```

---

## 6.3 Spatial Relationships

The following relationships are defined by RIL.

| Property | Description |
|----------|-------------|
| at | Execute at a location |
| within | Execute within a region |
| near | Execute near a reference |
| connected | Execute when connected to a resource |
| intersects | Execute where regions overlap |
| outside | Execute outside a region |

Future specifications MAY define additional spatial relationships.

---

## 6.4 Spatial References

Spatial relationships reference Resources rather than coordinate systems.

Example:

```json
{
  "where": {

    "gps": {

      "within": {

        "resource": "study_site"

      }

    }

  }

}
```

The interpretation of the Resource is defined by the corresponding Spatial Reference System.

Examples include:

- geographic coordinates;
- body maps;
- image regions;
- laboratory layouts;
- household structures;
- network topology.

---

## 6.5 Connected Resources

Spatial relationships may also describe logical connectivity.

Example:

```json
{
  "where": {

    "sync": {

      "connected": {

        "resource": "wifi"

      }

    }

  }

}
```

or

```json
{
  "where": {

    "ble": {

      "connected": {

        "resource": "beacon"

      }

    }

  }

}
```

Connectivity is interpreted by the relevant Device Service.

---

## 6.6 Default Behaviour

If the **where** section is omitted, no additional spatial constraints are applied.

# 7. HOW

The **how** section defines execution policies.

Policies influence how execution occurs without changing the requested Actions.

Execution policy is independent of Action definition.

---

## 7.1 Structure

The value of **how** SHALL be an object.

```json
{
  "how": {

  }
}
```

Policies MAY apply to:

- the entire Request;
- individual Actions;
- groups of Actions.

---

## 7.2 Common Policies

Examples include:

| Property | Description |
|----------|-------------|
| authenticate | Require authentication |
| verify | Require verification |
| retry | Retry failed execution |
| timeout | Maximum execution duration |
| encrypt | Encrypt resulting data |
| confirm | Require user confirmation |
| silent | Suppress user interaction |
| provenance | Requested provenance level |

---

## 7.3 Authentication

Example:

```json
{
  "how": {

    "identify": {

      "authenticate": "fingerprint"

    }

  }

}
```

---

## 7.4 Retry

Example:

```json
{
  "how": {

    "upload": {

      "retry": 3

    }

  }

}
```

---

## 7.5 Encryption

Example:

```json
{
  "how": {

    "capture": {

      "encrypt": true

    }

  }

}
```

---

## 7.6 Provenance

Example:

```json
{
  "how": {

    "capture": {

      "provenance": "full"

    }

  }

}
```

---

## 7.7 Default Behaviour

If the **how** section is omitted, implementation defaults apply.

Default policies SHOULD be documented by the implementation.

# 8. RESULT

The **result** section specifies the information requested by the caller following successful execution.

The **result** section never influences execution.

It only defines the information returned to the caller.

---

## 8.1 Structure

The value of **result** SHALL be an object.

```json
{
  "result": {

  }
}
```

Returned values MAY originate from:

- Actions;
- Observations;
- Transformations;
- Provenance;
- Runtime metadata.

---

## 8.2 Returned Fields

The simplest form requests one or more named values.

Example:

```json
{
  "result": {

    "return": [

      "participant_id",
      "tag_uid"

    ]

  }

}
```

---

## 8.3 Action References

Returned values MAY reference specific Actions.

Example:

```json
{
  "result": {

    "identify": [

      "participant_id",
      "tag_uid"

    ]

  }

}
```

This avoids ambiguity where multiple Actions produce similar outputs.

---

## 8.4 Aliases

Returned values MAY be renamed by the caller.

Example:

```json
{
  "result": {

    "identify": {

      "participant_id": "participant",

      "tag_uid": "nfc_uid"

    }

  }

}
```

The runtime returns:

```json
{
  "participant": "...",

  "nfc_uid": "..."
}
```

This allows callers to adapt returned values to existing data models without altering Method behaviour.

---

## 8.5 Provenance

The caller MAY request provenance information.

Example:

```json
{
  "result": {

    "provenance": "full"

  }

}
```

The structure of provenance is defined by the Provenance specification.

---

## 8.6 Default Behaviour

If the **result** section is omitted, implementations SHOULD return:

- execution status;
- Action identifiers;
- implementation-defined metadata.

Implementations MAY return additional information.

# 9. Errors

The JSON Representation does not define runtime error semantics.

Errors are represented by companion specifications.

This specification only defines how requests are represented.

Malformed JSON documents SHALL be rejected before RIL interpretation begins.

Invalid RIL constructs SHALL be reported by the RIL processor.

#10 Examples

## Single Action
```
{
  "request": {

    "what": [

      {
        "intent": "scan",
        "resource": "nfc"
      }

    ]

  }
}
```

## Ordered execution
```
{
  "request": {

    "what": [

      {
        "id":"identify",
        "intent":"scan",
        "resource":"nfc"
      },

      {
        "id":"photo",
        "intent":"capture",
        "resource":"image"
      }

    ],

    "when": {

      "photo": {

        "after":"identify"

      }

    }

  }
}
```
## Authentication
... To be completed
