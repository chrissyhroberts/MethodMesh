# ResearchOS Intent Registry v0.01

**Status:** Draft

The ResearchOS Intent Registry defines the canonical vocabulary of `intent` values used by ResearchOS Execution Requests.

An Intent is the operation requested by an Invocation.

The Intent Registry provides stable, implementation-independent names for these operations.

This specification defines vocabulary only.

It does not define:

- runtime execution behaviour;
- Method implementation details;
- transport protocols;
- user interface behaviour;
- Resource, Type or Policy vocabularies.

Those concerns are defined by companion specifications.

---

# 1. Purpose

The Intent Registry provides a controlled vocabulary for ResearchOS Invocations.

It is used by:

- the ResearchOS Intent Language;
- the ResearchOS JSON Object Model;
- Method manifests;
- Method registries;
- transport bindings;
- SDKs;
- documentation;
- tests and examples.

Every ResearchOS Invocation SHOULD use an Intent defined by this registry or by a recognised extension registry.

Example:

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

In this example, `scan` is the Intent.

---

# 2. Relationship to Other Specifications

The Intent Registry forms part of the ResearchOS specification family.

| Specification | Responsibility |
|---------------|----------------|
| Architecture Standard | Runtime concepts and execution model |
| ResearchOS Intent Language | Human-readable semantics |
| ResearchOS JSON Object Model | Canonical machine-readable object model |
| Intent Registry | Standard action vocabulary |
| Resource Registry | Standard entities and targets |
| Type Registry | Standard refinements |
| Policy Registry | Standard execution policies |
| Method Manifest | Method discovery and implementation metadata |

The Intent Registry defines the standard names of operations.

The Resource Registry defines what those operations act upon.

The Type Registry refines intent-resource combinations.

The Policy Registry defines execution constraints.

---

# 3. Design Principles

## 3.1 Small and Stable

The Intent vocabulary SHOULD remain small.

New functionality SHOULD normally be introduced through new Resources, Types, Policies or Methods rather than new Intents.

---

## 3.2 Technology Independent

Intent names SHALL NOT depend on a particular operating system, hardware platform, programming language or transport mechanism.

---

## 3.3 Single Responsibility

Each Intent SHOULD describe one kind of operation.

If a proposed Intent combines several operations, those operations SHOULD be expressed as separate Invocations.

---

## 3.4 Composable

Intents SHOULD compose naturally with other Intents.

Complex workflows SHOULD be built from multiple Invocations rather than by defining highly specialised Intents.

---

## 3.5 General Rather Than Domain-Specific

Intent names SHOULD be general enough to apply across research domains.

For example, `measure` is preferred over `measure_temperature`.

Temperature is a Resource or Type, not a separate Intent.

---

## 3.6 Verb-Centred

Intent names SHOULD be verbs.

The Intent describes what operation is requested.

The Resource describes what the operation acts upon.

The Type refines the operation or Resource.

---

# 4. Registry Entry Model

Each Intent in the registry is described using a common entry model.

| Field | Description |
|-------|-------------|
| Intent | Canonical machine-readable intent value |
| Category | Broad functional group |
| Meaning | Short definition |
| Typical Resources | Common Resources used with this Intent |
| Notes | Usage guidance |

Registry entries are normative with respect to the canonical Intent value and meaning.

Examples and typical Resources are informative.

---

# 5. Choosing an Intent

Every Intent should answer the question:

> What kind of operation is being requested?

Use the most general Intent that accurately describes the operation.

For example:

```json
{
  "intent": "measure",
  "resource": "temperature"
}
```

is preferred over:

```json
{
  "intent": "measure_temperature"
}
```

Before introducing a new Intent, ask:

> Can this be expressed using an existing Intent with a new Resource, Type, Parameter or Policy?

If yes, a new Intent SHOULD NOT be introduced.

---

# 6. Core Intent Categories

The core registry is organised into seven categories.

| Category | Purpose |
|----------|---------|
| Knowledge Acquisition | Acquire new information |
| Knowledge Management | Organise, persist or transfer information |
| Knowledge Transformation | Derive or modify information |
| Security | Change protection or trust characteristics |
| Workflow | Change execution state |
| Communication | Communicate with people or systems |
| System | Interrogate or administer the runtime |

Categories are organisational only.

They do not alter execution semantics.

---

# 7. Knowledge Acquisition Intents

Knowledge Acquisition Intents change what ResearchOS knows by obtaining new information.

| Intent | Meaning | Typical Resources | Notes |
|--------|---------|-------------------|-------|
| `ask` | Obtain information from a person. | question, questionnaire, choice_set, dce | Used for participant-facing or operator-facilitated questions. |
| `observe` | Record a qualitative observation. | symptom, sign, event, behaviour | Used when recording observed facts without measurement. |
| `measure` | Quantify a property of the world. | temperature, weight, height, distance, loudness, light | Used for numeric or calibrated observations. |
| `capture` | Acquire raw media or sensor data. | image, audio, video, trace, time_series | Used when the output is primary data rather than interpreted information. |
| `scan` | Acquire or decode an external identifier or signal. | nfc, qr, barcode, ble, rfid | Used for reading tags, codes, broadcasts or similar signals. |
| `identify` | Determine what an entity is. | participant, specimen, device, operator | Used when resolving an entity identity. |
| `verify` | Confirm that a condition is satisfied. | fingerprint, pin, signature, identity, consent | Used for confirmation, authentication or validation-like checks. |
| `locate` | Determine the spatial position of an entity. | location, participant, specimen, target | Used for spatial position, navigation or target finding. |

---

# 8. Knowledge Management Intents

Knowledge Management Intents change how knowledge is organised, persisted or transferred.

| Intent | Meaning | Typical Resources | Notes |
|--------|---------|-------------------|-------|
| `create` | Create a new entity or Resource. | participant, specimen, record, protocol | Used when a new object comes into existence. |
| `find` | Search for entities matching criteria. | participant, specimen, record, method | Use when the desired object is not yet known. |
| `retrieve` | Obtain a known entity or previously stored information. | record, dataset, file, result | Use when the object is already known. |
| `store` | Persist information locally. | result, file, observation, provenance | Used for local persistence. |
| `update` | Modify an existing entity or Resource. | participant, record, state, protocol | Used when an existing object changes. |
| `link` | Create a relationship between entities. | participant, household, specimen, contact | Used for graph relationships and associations. |
| `unlink` | Remove a relationship between entities. | participant, specimen, household | Removes a relationship without necessarily deleting entities. |
| `copy` | Duplicate an entity or Resource. | file, dataset, template, configuration | Used when both original and duplicate persist. |
| `move` | Relocate an entity or Resource. | file, specimen, dataset, container | Used when location or container changes. |
| `delete` | Remove an entity or Resource. | file, record, draft, cache | Implementations SHOULD treat deletion carefully and record provenance where appropriate. |
| `submit` | Transfer information to another system. | form, dataset, result, report | Used for outbound transfer or handoff. |

---

# 9. Knowledge Transformation Intents

Knowledge Transformation Intents produce new information from existing information.

| Intent | Meaning | Typical Resources | Notes |
|--------|---------|-------------------|-------|
| `transform` | Produce new information from existing information. | dataset, image, signal, record | General transformation Intent. |
| `annotate` | Add descriptive information. | image, audio, video, map, record | Used for markup, comments and labels. |
| `classify` | Assign a category. | lesion, symptom, specimen, image | Used for categorical interpretation. |
| `compare` | Evaluate similarities or differences. | image, sequence, record, dataset | Used for pairwise or set comparison. |
| `filter` | Select a subset. | dataset, record_set, signal, trace | Used for selection by rule or criterion. |
| `aggregate` | Combine multiple observations. | observations, dataset, time_series | Used for totals, groups and pooled summaries. |
| `summarise` | Produce a condensed representation. | report, dataset, visit, trace | British spelling is canonical. |
| `merge` | Combine compatible entities or datasets. | dataset, record, identity | Used when separate objects become one logical object. |
| `split` | Divide an entity or dataset. | dataset, specimen, record | Used when one object becomes multiple logical objects. |
| `convert` | Change representation or format. | file, image, audio, dataset | Used for representation changes. |

---

# 10. Security Intents

Security Intents change the protection or trust characteristics of information.

| Intent | Meaning | Typical Resources | Notes |
|--------|---------|-------------------|-------|
| `encrypt` | Protect information by encryption. | file, result, dataset, provenance | Used to protect data. |
| `decrypt` | Recover encrypted information. | file, result, dataset | Requires appropriate key material or authority. |
| `hash` | Produce a cryptographic digest. | file, record, payload | Used for integrity checks and fingerprints. |
| `sign` | Produce a digital signature. | record, payload, consent, result | Used to assert authenticity or approval. |
| `verify_signature` | Validate a digital signature. | signature, record, payload | Distinct from general `verify`. |
| `anonymise` | Remove identifying information. | dataset, record, image, transcript | Used when identifiers are removed. |
| `pseudonymise` | Replace identifiers while preserving linkage. | dataset, participant, record | Used when linkage remains possible under controlled conditions. |
| `redact` | Remove selected information. | document, image, transcript, record | Used for selective removal. |

---

# 11. Workflow Intents

Workflow Intents change execution state within workflows, protocols or applications.

| Intent | Meaning | Typical Resources | Notes |
|--------|---------|-------------------|-------|
| `open` | Launch a Resource or application. | form, app, url, record, protocol | Used for opening external or internal interfaces. |
| `wait` | Suspend execution until a defined condition. | signal, event, time, condition | Used for temporal or conditional suspension. |
| `schedule` | Arrange future execution. | invocation, task, visit, notification | Used for planned future activity. |
| `begin` | Start a workflow or task. | workflow, protocol, visit, task | Used for lifecycle start. |
| `pause` | Temporarily suspend execution. | workflow, task, protocol | Used when execution may resume. |
| `resume` | Continue a suspended workflow. | workflow, task, protocol | Used after pause or interruption. |
| `repeat` | Execute again. | invocation, task, measurement | Used for repeated execution. |
| `complete` | Mark a task as completed. | task, visit, workflow, protocol | Used for lifecycle completion. |
| `cancel` | Terminate execution. | task, workflow, schedule, request | Used when execution should not continue. |

---

# 12. Communication Intents

Communication Intents change what people or external systems know.

| Intent | Meaning | Typical Resources | Notes |
|--------|---------|-------------------|-------|
| `notify` | Send a notification. | participant, operator, device, system | Used for alerts and reminders. |
| `message` | Send asynchronous communication. | participant, operator, group, system | Used for text-like messaging. |
| `call` | Initiate real-time communication. | participant, operator, service | Used for voice, video or other real-time channels. |
| `broadcast` | Send information to multiple recipients. | group, devices, network | Used for one-to-many communication. |
| `share` | Make information available to another party. | file, report, dataset, result | Used for controlled sharing. |
| `report` | Communicate structured findings or results. | report, event, result, summary | Used for formal or structured communication. |

---

# 13. System Intents

System Intents interrogate or administer the ResearchOS runtime.

| Intent | Meaning | Typical Resources | Notes |
|--------|---------|-------------------|-------|
| `discover` | Find available Resources, Methods or services. | method, resource, device_service | Used for runtime discovery. |
| `describe` | Return metadata about a Resource. | method, resource, policy, device | Used for introspection. |
| `list` | Enumerate Resources of a given type. | methods, resources, devices, protocols | Used for listing known objects. |
| `check` | Evaluate the current state of something. | permission, connectivity, battery, state | Used for state checks. |
| `validate` | Test whether something conforms to rules. | request, form, record, protocol | Used for conformance and validity checks. |
| `ping` | Test availability or responsiveness. | service, device, endpoint, runtime | Used for liveness checks. |
| `import` | Bring external Resources into ResearchOS. | file, dataset, protocol, configuration | Used for inbound transfer. |
| `export` | Transfer Resources out of ResearchOS. | file, dataset, report, provenance | Used for outbound extraction. |

---

# 14. Reserved and Excluded Terms

Some common computing terms are intentionally excluded from the core Intent vocabulary.

| Excluded Term | Preferred Intent |
|---------------|------------------|
| `read` | `retrieve` |
| `write` | `store`, `update` or `submit` |
| `save` | `store` |
| `load` | `retrieve` |
| `print` | `report` or `export` |

The excluded terms are not invalid in extension registries, but they SHOULD NOT be used in core ResearchOS specifications.

---

# 15. Candidate Future Intents

The following candidate Intents are recognised as possible future additions but are not part of Intent Registry v0.01.

| Candidate Intent | Potential Purpose |
|------------------|-------------------|
| `invoke` | Request another executable component, Method, workflow or protocol to perform work. |

Candidate Intents SHOULD NOT be used in normative examples until accepted into the registry.

---

# 16. Extension Intents

Implementations MAY define extension Intents.

Extension Intents SHOULD:

- follow the same naming conventions as core Intents;
- be technology-independent where possible;
- avoid duplicating existing core Intents;
- be documented in an extension registry;
- be discoverable through Method metadata.

Extension Intents SHOULD use names that are unlikely to conflict with future core Intents.

Domain-specific behaviour SHOULD usually be represented as a Resource or Type rather than as a new Intent.

---

# 17. Naming Rules

Intent names SHALL follow these rules:

- lower case;
- ASCII letters, digits and underscores only;
- start with a letter;
- no spaces;
- no hyphens;
- stable once published.

Examples:

```text
measure
capture
verify_signature
pseudonymise
```

The canonical spelling is the spelling used in this registry.

---

# 18. Use in JSON

In the ResearchOS JSON Object Model, the Intent is represented using the `intent` property of an Invocation Object.

Example:

```json
{
  "request": {
    "capture_photo": {
      "intent": "capture",
      "resource": "image"
    }
  }
}
```

The Invocation Identifier (`capture_photo`) is local to the Request.

The Intent (`capture`) is drawn from this registry.

---

# 19. Use in Method Manifests

Methods SHOULD declare the Intents they implement.

Example:

```yaml
id: researchos.nfc.scan
implements:
  - intent: scan
    resource: nfc
```

A Method MAY implement more than one Intent-resource combination.

The Method Registry SHOULD use Intent metadata to support discovery and resolution.

---

# 20. Versioning

Intent Registry versions are independent of ResearchOS runtime versions.

Minor versions SHOULD be additive.

Existing Intent meanings SHOULD NOT be changed incompatibly within a minor version.

Removing or redefining a core Intent requires a major version change.

Deprecated Intents SHOULD remain documented for compatibility.

---

# Appendix A – Summary Table

| Category | Intents |
|----------|---------|
| Knowledge Acquisition | `ask`, `observe`, `measure`, `capture`, `scan`, `identify`, `verify`, `locate` |
| Knowledge Management | `create`, `find`, `retrieve`, `store`, `update`, `link`, `unlink`, `copy`, `move`, `delete`, `submit` |
| Knowledge Transformation | `transform`, `annotate`, `classify`, `compare`, `filter`, `aggregate`, `summarise`, `merge`, `split`, `convert` |
| Security | `encrypt`, `decrypt`, `hash`, `sign`, `verify_signature`, `anonymise`, `pseudonymise`, `redact` |
| Workflow | `open`, `wait`, `schedule`, `begin`, `pause`, `resume`, `repeat`, `complete`, `cancel` |
| Communication | `notify`, `message`, `call`, `broadcast`, `share`, `report` |
| System | `discover`, `describe`, `list`, `check`, `validate`, `ping`, `import`, `export` |

---

# Appendix B – Decision Heuristics

Use the following heuristics when selecting an Intent.

| Situation | Preferred Intent |
|-----------|------------------|
| Obtaining an answer from a person | `ask` |
| Reading an NFC tag, QR code or barcode | `scan` |
| Acquiring a photograph, audio clip or sensor trace | `capture` |
| Producing a calibrated numeric value | `measure` |
| Determining which entity is present | `identify` |
| Confirming a condition or identity | `verify` |
| Finding unknown matching records | `find` |
| Loading a known record | `retrieve` |
| Saving local data | `store` |
| Sending data to another system | `submit` |
| Starting a future task | `schedule` |
| Opening a form or application | `open` |
| Sending a reminder | `notify` |
| Checking system state | `check` |
| Listing available Methods | `list` |
| Testing availability | `ping` |

---

# Appendix C – Revision History

| Version | Summary |
|---------|---------|
| v0.01 | Initial ResearchOS Intent Registry derived from RIL Core Verbs v0.01. |

---

End of Specification.
