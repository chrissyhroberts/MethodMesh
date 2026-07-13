# Architecture Standard (AS) v1.01

Status: Draft

Version: AS1.01

Editors:
Chrissy H. Roberts

Companion Specifications:

- ResearchOS Intent Language (RIL)
- Core Verbs
- Core Resources
- Core Types
- Core Policies

Reference Implementation:

The Architecture Standard (AS) defines the conceptual architecture of the ResearchOS platform.

It specifies the architectural domains, execution model, core abstractions and design principles that all conforming ResearchOS implementations shall preserve.

This specification is implementation-independent.

It defines **concepts**, not software implementations.

Accordingly, this specification does not define:

- programming languages;
- operating systems;
- software frameworks;
- transport mechanisms;
- storage technologies;
- user interfaces.

These concerns are addressed by companion specifications and reference implementations.

---

# Specification Family

The Architecture Standard forms the foundation of the ResearchOS specification family.

```text
Architecture Standard (AS)
        ↓
ResearchOS Intent Language (RIL)
        ↓
Transport Bindings
        ↓
Reference Runtime
        ↓
Applications
```

Each specification has a single responsibility.

| Specification | Responsibility |
|--------------|----------------|
| Architecture Standard | Defines the conceptual architecture of ResearchOS. |
| ResearchOS Intent Language (RIL) | Defines how research operations are requested. |
| Core Verbs | Defines the standard vocabulary of actions. |
| Core Resources | Defines the entities upon which actions operate. |
| Core Types | Defines refinements of intents and resources. |
| Core Policies | Defines standard execution policies. |
| Transport Bindings | Define how RIL requests are represented using JSON, Android Intents, HTTP and other transports. |
| Reference Runtime | Implements the Architecture Standard and companion specifications. |

---

# 1. Purpose

The Architecture Standard defines the conceptual model underlying ResearchOS.

Its purpose is to provide a stable engineering foundation for reusable research software that is independent of any particular programming language, operating system or application domain.

The standard describes:

- the architectural domains that organise the platform;
- the relationships between those domains;
- the canonical execution model;
- the architectural building blocks from which implementations are constructed.

The Architecture Standard describes **how ResearchOS is organised**.

It does not define:

- research protocols;
- application workflows;
- user interfaces;
- transport formats;
- programming interfaces.

These concerns are defined by companion specifications.

---

# 2. Scope

This specification applies to all conforming ResearchOS implementations.

It defines concepts rather than implementation technologies.

Conforming implementations MAY use different programming languages, storage technologies, operating systems and communication mechanisms provided they preserve the architectural concepts and relationships defined by this standard.

---

# 3. Design Philosophy

ResearchOS is founded upon five architectural principles.

1. Separation of concerns.
2. Explicit execution.
3. Composition over specialisation.
4. Platform independence.
5. Extensibility through stable abstractions.

These principles guide every subsequent architectural decision within the platform.

---

# 4. Architectural Domains

ResearchOS is organised around five orthogonal architectural domains.

```text
Knowledge

Methods

Time

Space

Transformation
```

Each domain answers a distinct architectural question.

| Domain | Fundamental Question |
|---------|----------------------|
| Knowledge | What exists? |
| Methods | What can happen? |
| Time | When does it exist? |
| Space | Where does it exist? |
| Transformation | How does it change? |

The domains are conceptual rather than software layers.

Together they provide a complete architectural description of the platform.


# 5. Architectural Domains

The Architecture Standard organises ResearchOS into five independent architectural domains.

Each domain represents a distinct aspect of the platform and answers a single fundamental question.

| Domain | Question |
|----------|----------|
| Knowledge | What exists? |
| Methods | What can happen? |
| Time | When does it exist? |
| Space | Where does it exist? |
| Transformation | How does it change? |

The domains are **conceptual abstractions**, not software modules.

A single implementation component may participate in multiple domains simultaneously while preserving the responsibilities of each domain.

---

## 5.1 Separation of Concerns

The purpose of separating the architecture into domains is to prevent independent concepts from becoming tightly coupled.

For example:

- Knowledge should not contain executable behaviour.
- Methods should not own persistent knowledge.
- Time should not be represented solely as timestamps attached to observations.
- Space should not be reduced to GPS coordinates.
- Transformations should not be hidden within application logic.

Each domain provides an independent viewpoint from which the platform can be understood, queried and extended.

---

## 5.2 Relationships Between Domains

Although conceptually independent, the domains interact through a small number of stable relationships.

- Methods operate upon Knowledge.
- Methods execute through Transformations.
- Knowledge exists within Time.
- Knowledge exists within Space.
- Transformations modify Knowledge.
- Time constrains Knowledge and Methods.
- Space constrains Knowledge and Methods.

These relationships remain constant regardless of implementation technology.

Conceptually the architecture may be visualised as:

```text
                Time
                  ▲
                  │
                  │
Space ◄──── Knowledge ────► Methods
                  │               │
                  │               │
                  ▼               │
           Transformation ◄───────┘
```

---

## 5.3 Domain Independence

The architectural domains are intentionally orthogonal.

Changes within one domain should not require structural changes within another.

For example:

- introducing a new Device Service should not modify the Knowledge Domain;
- adding a new spatial reference system should not affect Method execution;
- introducing a new workflow should not require changes to the Time Domain.

This separation enables long-term architectural stability.

---

## 5.4 Architectural Completeness

Every architectural component within ResearchOS belongs primarily to one domain.

Examples include:

| Component | Primary Domain |
|-----------|----------------|
| Participant | Knowledge |
| Observation | Knowledge |
| Method | Methods |
| Device Service | Methods |
| Workflow | Methods |
| Schedule | Time |
| Event | Time |
| GPS Location | Space |
| Body Map Region | Space |
| State Transition | Transformation |
| Execution | Transformation |
| Provenance | Transformation |

Components may interact across domains while preserving a single primary responsibility.

---

## 5.5 Design Principle

New architectural concepts SHOULD be introduced by extending an existing domain rather than creating new domains.

The five domains provide the stable conceptual foundation upon which the remainder of the platform is built.

# 6. Knowledge Domain

The Knowledge Domain defines everything the platform knows.

Knowledge represents entities, observations, relationships, classifications and state independently of how that knowledge is acquired, displayed or processed.

Knowledge describes **what exists**, not **how it is used**.

---

## 6.1 Responsibility

The Knowledge Domain is responsible for representing:

- entities;
- observations;
- attributes;
- relationships;
- classifications;
- state.

Knowledge objects provide the semantic foundation upon which all other architectural domains operate.

---

## 6.2 Design Principles

Knowledge SHALL be:

- identifiable;
- typed;
- relational;
- contextual;
- versionable.

Knowledge SHOULD remain independent of execution logic.

---

## 6.3 Core Concepts

The Knowledge Domain contains five primary concepts.

### Entity

Represents something that exists.

Examples include:

- participant
- specimen
- questionnaire
- protocol
- workflow
- device

---

### Observation

Represents knowledge acquired about an entity.

Observations are immutable.

Corrections SHOULD create new observations linked through provenance.

---

### Attribute

Represents a property of an entity.

Examples include:

- age
- battery level
- protocol version

---

### Relationship

Represents semantic links between knowledge objects.

Relationships are first-class architectural concepts.

---

### Classification

Represents conceptual categorisation.

Examples include:

- diagnosis
- participant status
- workflow stage
- specimen type

---

## 6.4 Knowledge Graph

Collectively these concepts form the ResearchOS Knowledge Graph.

The Architecture Standard does not prescribe any storage mechanism.

Conforming implementations MAY use relational, document, graph or hybrid storage technologies while preserving the conceptual model.

---

## 6.5 Separation of Concerns

Knowledge describes reality.

It does not describe behaviour.

Examples:

- a participant is knowledge;
- a specimen is knowledge;
- a workflow definition is knowledge;
- a protocol is knowledge.

The operations performed upon these objects belong to the Method Domain.

# 7. Method Domain

The Method Domain defines everything the platform can do.

While the Knowledge Domain represents what exists, the Method Domain represents the executable behaviour that operates upon that knowledge.

Methods consume knowledge, evaluate knowledge, create observations, coordinate execution and initiate transformations.

A Method is therefore the fundamental executable unit within the ResearchOS architecture.

---

## 7.1 Responsibility

The Method Domain is responsible for:

- execution;
- interpretation;
- calculation;
- validation;
- coordination;
- communication.

Methods define behaviour.

They do not define persistent knowledge.

---

## 7.2 Design Principles

Methods SHALL be:

- composable;
- reusable;
- implementation-independent;
- presentation-independent;
- signal-independent.

Methods SHOULD communicate through defined interfaces rather than direct implementation dependencies.

---

## 7.3 Method

A Method is an executable architectural component.

Every executable operation within ResearchOS is represented as a Method.

Examples include:

- measuring temperature;
- reading an NFC tag;
- calculating BMI;
- validating eligibility;
- capturing a photograph;
- asking a questionnaire item;
- launching another application.

Complex behaviour is constructed by composing Methods.

---

## 7.4 Method Specialisations

The Architecture Standard recognises several common classes of Method.

These are architectural roles rather than separate execution mechanisms.

### Rule

A Rule evaluates knowledge and produces a decision.

Rules SHOULD be side-effect free.

Examples include:

- eligibility;
- validation;
- protocol compliance;
- branching.

---

### Calculation

A Calculation derives new knowledge from existing knowledge.

Calculations SHOULD be deterministic wherever practical.

Examples include:

- BMI;
- age;
- composite scores;
- laboratory conversions.

---

### Workflow

A Workflow coordinates multiple Methods into a larger operational procedure.

Examples include:

- participant enrolment;
- clinic visit;
- laboratory processing;
- follow-up.

A Workflow is itself a Method.

---

### Device Service

A Device Service exposes an external device or operating system service through a standard architectural interface.

Examples include:

- NFC;
- Bluetooth;
- GPS;
- camera;
- USB;
- accelerometer.

Device Services acquire signals.

They do not interpret those signals.

---

### Signal Interpreter

A Signal Interpreter converts transient signals into architectural observations.

Examples include:

- NFC UID → participant identification;
- BLE advertisement → proximity observation;
- image touch → body-map region;
- GPS fix → location observation.

Signal interpretation is separate from signal acquisition.

---

### Presentation Method

Presentation Methods provide controlled interaction with users.

Examples include:

- display image;
- ask question;
- select body region;
- capture signature;
- display notification.

Presentation is therefore represented using the same execution model as every other Method.

---

## 7.5 Composition

Methods may invoke other Methods.

For example:

```text
Participant Visit
↓
Workflow
↓
Identify Participant
↓
Read NFC
↓
Validate Identity
↓
Capture Photograph
↓
Record Observation
↓
Update Participant State
```

There is no distinction between "simple" and "complex" Methods.

Complex behaviour emerges through composition.

---

## 7.6 Separation of Concerns

Methods operate upon knowledge.

They do not own knowledge.

Similarly:

- Device Services acquire signals.
- Signal Interpreters create observations.
- Rules evaluate knowledge.
- Workflows coordinate execution.
- Transformations update state.

Each Method has a single architectural responsibility.

---

## 7.7 Architectural Principle

Every executable behaviour within ResearchOS SHOULD be representable as a Method.

New functionality SHOULD be introduced by defining new Methods rather than modifying the execution engine.

# 8. Time Domain

The Time Domain defines the temporal context within which knowledge exists, methods execute and transformations occur.

Time is an architectural domain rather than metadata.

It determines when information is valid, when actions occur and how state evolves.

---

## 8.1 Responsibility

The Time Domain is responsible for representing:

- events;
- intervals;
- schedules;
- validity;
- recurrence;
- temporal relationships;
- version history.

Time provides context for every other architectural domain.

---

## 8.2 Design Principles

Temporal information SHALL be:

- explicit;
- ordered;
- queryable;
- historical;
- versionable.

Temporal assumptions SHOULD never be inferred from application logic.

---

## 8.3 Core Concepts

### Event

Represents something that occurs at a point in time.

Examples include:

- participant enrolled;
- questionnaire completed;
- NFC tag detected;
- workflow resumed.

---

### Interval

Represents a period over which something remains true.

Examples include:

- clinic visit;
- admission;
- treatment course;
- follow-up window.

---

### Schedule

Represents intended future execution.

Examples include:

- reminders;
- protocol timelines;
- recurring observations.

Schedules describe planned execution rather than completed execution.

---

### Validity

Represents the period during which knowledge or methods are applicable.

Examples include:

- protocol versions;
- calibration periods;
- eligibility windows.

---

### Version

Represents evolution of architectural definitions through time.

Historical execution SHALL remain interpretable.

---

## 8.4 Temporal Relationships

Implementations SHOULD support relationships such as:

- before;
- after;
- during;
- overlaps;
- starts;
- finishes;
- recurring.

---

## 8.5 Separation of Concerns

Time provides context.

It does not perform execution.

Methods execute within time.

Knowledge exists within time.

Transformations occur through time.

---
# 9. Space Domain

The Space Domain defines the spatial context within which knowledge exists, methods execute and transformations occur.

Space is an architectural domain rather than metadata.

It determines where information is located, where execution occurs and how spatial relationships are represented.

Space is not limited to geographic location.

---

## 9.1 Responsibility

The Space Domain is responsible for representing:

- spatial reference systems;
- locations;
- regions;
- paths;
- networks;
- spatial relationships;
- spatial validity.

Space provides context for knowledge, methods and transformations.

---

## 9.2 Design Principles

Spatial information SHALL be:

- explicit;
- reference-based;
- queryable;
- composable;
- extensible.

Spatial assumptions SHOULD NOT be inferred from application logic or presentation state.

---

## 9.3 Core Concepts

### Spatial Reference System

A Spatial Reference System defines how spatial position is represented.

Examples include:

- geographic coordinates;
- body maps;
- lesion maps;
- image coordinate systems;
- microscopy slides;
- laboratory plate layouts;
- household structures;
- floor plans;
- contact networks.

ResearchOS does not privilege any single spatial system.

GPS coordinates, body-map polygons and image coordinates are all spatial representations.

---

### Location

A Location represents a position within a Spatial Reference System.

Examples include:

- latitude and longitude;
- body region;
- image coordinate;
- room identifier;
- laboratory bench;
- household identifier.

---

### Region

A Region represents a bounded or named spatial area.

Examples include:

- study site;
- clinic;
- anatomical region;
- polygon on an image;
- agricultural plot;
- laboratory work area.

Regions MAY contain locations or other regions.

---

### Path

A Path represents movement through space.

Examples include:

- GPS track;
- specimen transport route;
- participant travel;
- clinic pathway;
- workflow route.

---

### Network

A Network represents spatial structure through relationships rather than geometric distance.

Examples include:

- household network;
- contact network;
- transmission network;
- referral network;
- Bluetooth proximity network.

---

## 9.4 Spatial Relationships

Implementations SHOULD support relationships such as:

- inside;
- outside;
- contains;
- intersects;
- overlaps;
- adjacent to;
- connected to;
- upstream;
- downstream;
- within distance.

These relationships SHOULD be interpreted relative to the relevant Spatial Reference System.

---

## 9.5 Multiple Spatial Contexts

A single Knowledge object MAY have multiple spatial contexts.

For example, a lesion may be associated with:

- a participant's home location;
- a body-map region;
- an image coordinate;
- a clinic location.

These representations are complementary.

They SHOULD NOT be treated as competing descriptions unless a Method explicitly defines such a comparison.

---

## 9.6 Separation of Concerns

Space provides context.

It does not acquire location.

Methods acquire spatial signals.

Signal Interpreters convert spatial signals into Observations.

The Space Domain represents the resulting spatial knowledge.

Examples:

- GPS is a Method or Device Service.
- Camera calibration is a Method.
- Touchscreen interaction is a Method.
- A latitude/longitude observation belongs to Knowledge and Space.
- A body-map region belongs to Knowledge and Space.

---

## 9.7 Design Principle

New spatial systems SHOULD be introduced as Spatial Reference Systems rather than by altering the architecture.

This allows ResearchOS to represent geographic, anatomical, image-based, laboratory, household and network spaces using a common conceptual model.

---
# 10. Transformation Domain

The Transformation Domain defines how the state of the ResearchOS platform changes over time.

While the Knowledge Domain describes what exists, the Transformation Domain describes how that knowledge evolves through execution.

Transformations are the consequence of Method execution.

They provide the architectural bridge between execution and knowledge.

---

## 10.1 Responsibility

The Transformation Domain is responsible for:

- state transitions;
- execution;
- transactions;
- provenance;
- lifecycle management;
- consistency.

Transformations describe change.

They do not describe the methods that caused the change.

---

## 10.2 Design Principles

Transformations SHALL be:

- explicit;
- reproducible;
- traceable;
- atomic where practical;
- auditable.

Every completed transformation SHOULD be represented within provenance.

---

## 10.3 Core Concepts

### Execution Request

An Execution Request represents work submitted to the ResearchOS runtime.

Execution Requests originate from:

- ResearchOS Intent Language (RIL);
- internal workflows;
- schedulers;
- automation;
- external APIs.

Execution Requests are architectural inputs.

They are not themselves transformations.

---

### Execution

Execution is the process of resolving and invoking one or more Methods.

Execution consumes an Execution Request and produces one or more Transformations.

---

### Transformation

A Transformation represents an intentional change to architectural state.

Examples include:

- creating an observation;
- updating participant state;
- completing a workflow;
- storing a specimen;
- recording a consent decision.

---

### Transaction

A Transaction groups one or more related Transformations into a single logical operation.

Implementations MAY support transactional rollback where appropriate.

---

### Provenance

Every Transformation contributes to architectural provenance.

Provenance records:

- execution history;
- timestamps;
- participating Methods;
- validation outcomes;
- authentication events;
- protocol deviations.

The Architecture Standard defines provenance conceptually.

The structure of provenance is defined by companion specifications.

---

## 10.4 Execution Pipeline

Conceptually, execution proceeds through the following stages.

```text
Execution Request
        ↓
Execution Engine
        ↓
Method Resolution
        ↓
Method Execution
        ↓
Signal Acquisition (optional)
        ↓
Observation Creation
        ↓
Rule Evaluation (optional)
        ↓
Transformation
        ↓
Knowledge Update
        ↓
Provenance Recording
```

Individual implementations MAY optimise or parallelise these stages while preserving their architectural meaning.

---

## 10.5 Separation of Concerns

Execution Requests initiate execution.

Methods perform execution.

Signals provide transient observations.

Transformations modify architectural state.

Knowledge stores resulting state.

Provenance records how those changes occurred.

Each concept has a single architectural responsibility.

---

## 10.6 Design Principle

Architectural state SHOULD change only through Transformations.

This ensures that execution remains reproducible, auditable and independent of implementation technology.

# 11. Execution Model

The Architecture Standard defines a canonical execution model for all ResearchOS implementations.

The execution model describes how architectural domains interact during execution.

It does not prescribe implementation technology, programming language or software structure.

Every conforming implementation SHALL preserve the conceptual behaviour defined by this model.

---

## 11.1 Purpose

The execution model provides a common framework for executing research operations.

Regardless of whether execution is initiated by:

- a user;
- a scheduled workflow;
- an external API;
- a Device Service;
- the ResearchOS Orchestrator;
- another Method;

execution follows the same conceptual model.

This consistency enables interoperability, provenance, auditability and predictable behaviour across implementations.

---

## 11.2 Canonical Execution Pipeline

Conceptually, every execution follows the same sequence.

```text
Execution Request
        ↓
Method Resolution
        ↓
Method Execution
        ↓
Signal Acquisition (optional)
        ↓
Signal Interpretation (optional)
        ↓
Observation Creation
        ↓
Rule Evaluation (optional)
        ↓
Transformation
        ↓
Knowledge Update
        ↓
State Update
        ↓
Provenance Recording
        ↓
Result
```

Implementations MAY optimise or parallelise execution provided this conceptual model is preserved.

---

## 11.3 Execution Request

Execution begins with an Execution Request.

Execution Requests may originate from:

- the ResearchOS Intent Language (RIL);
- the ResearchOS Orchestrator;
- internal workflows;
- Device Services;
- scheduled events;
- external systems.

Execution Requests are architectural inputs.

They are not themselves transformations.

---

## 11.4 Method Resolution

The runtime identifies one or more Methods capable of satisfying the Execution Request.

Method discovery SHOULD occur through the Method Registry.

Method resolution is independent of the originating transport.

---

## 11.5 Method Execution

Methods perform the requested work.

Methods MAY:

- invoke other Methods;
- access Knowledge;
- interact with Device Services;
- generate Signals;
- create Observations;
- initiate Transformations.

Methods SHALL NOT directly modify architectural state.

State changes occur only through Transformations.

---

## 11.6 Signal Processing

Where execution involves external systems, Device Services generate transient Signals.

Signal Interpreters convert those Signals into architectural Observations.

Signals are transient.

Observations are persistent.

---

## 11.7 Rule Evaluation

Rules evaluate available Knowledge and Observations.

Rules MAY:

- permit execution;
- reject execution;
- request additional execution;
- recommend alternative workflows;
- defer execution.

Rules SHOULD remain side-effect free.

---

## 11.8 Transformation

Approved execution produces one or more Transformations.

Transformations are the only mechanism through which architectural state changes.

Each Transformation SHALL record provenance.

---

## 11.9 State Management

Successful Transformations update architectural state.

State updates SHALL be:

- explicit;
- queryable;
- reproducible.

State SHALL NOT be inferred solely from user interface behaviour.

---

## 11.10 Provenance

Every execution contributes to provenance.

Typical provenance includes:

- execution identifiers;
- participating Methods;
- timestamps;
- authentication events;
- validation outcomes;
- protocol deviations;
- software versions.

The Architecture Standard defines provenance conceptually.

Companion specifications define provenance structure and transport.

---

## 11.11 Results

Execution concludes by returning a Result.

The Result returned to the caller is defined by the ResearchOS Intent Language (RIL).

The Architecture Standard does not prescribe the structure or transport of returned results.

---

## 11.12 Design Principles

The execution model is independent of:

- transport mechanisms;
- user interfaces;
- programming languages;
- operating systems;
- storage technologies.

Every ResearchOS implementation SHALL preserve the conceptual execution model regardless of implementation details.

# 12. Reference Runtime Architecture

The Architecture Standard defines a logical reference architecture for ResearchOS implementations.

The reference architecture describes the responsibilities of the principal runtime components.

It does not prescribe software packages, programming languages or deployment models.

Implementations MAY organise software differently provided the architectural responsibilities remain unchanged.

---

## 12.1 Purpose

The reference architecture demonstrates one way in which the conceptual architecture defined by this standard may be realised.

It exists to:

- provide a common implementation vocabulary;
- support interoperability;
- guide reference implementations;
- separate architectural responsibilities.

---

## 12.2 Logical Architecture

A conforming implementation consists of a small number of cooperating runtime components.

```text
Presentation
        ↓
Application
        ↓
Execution Engine
        ↓
 ┌──────┼───────────────┐────────────────┐
 ↓                      ↓                ↓
Knowledge Store   Method Registry   Device Services
 └──────────────────────┼────────────────┘
                        ↓
                   Platform Services
```

The arrangement of software modules is implementation dependent.

The architectural responsibilities remain constant.

---

## 12.3 Presentation

The Presentation layer provides interaction with users.

Examples include:

- questionnaires;
- dashboards;
- body maps;
- image annotation;
- protocol status;
- notifications.

Presentation components generate Execution Requests.

They SHOULD NOT implement execution logic.

---

## 12.4 Application

The Application layer coordinates application-specific behaviour.

Typical responsibilities include:

- navigation;
- authentication;
- configuration;
- session management;
- workflow selection;
- security.

Application components SHOULD compose architectural services rather than replacing them.

---

## 12.5 Execution Engine

The Execution Engine coordinates runtime behaviour.

Responsibilities include:

- receiving Execution Requests;
- resolving Methods;
- coordinating execution;
- invoking Device Services;
- creating Observations;
- initiating Transformations;
- recording provenance.

The Execution Engine is the architectural core of the platform.

---

## 12.6 Knowledge Store

The Knowledge Store maintains persistent architectural knowledge.

Typical responsibilities include:

- entities;
- observations;
- relationships;
- state;
- provenance;
- temporal information;
- spatial information.

The storage technology is outside the scope of this standard.

---

## 12.7 Method Registry

The Method Registry provides discovery of executable Methods.

Responsibilities include:

- Method registration;
- Method discovery;
- Method metadata;
- Method versioning.

The Execution Engine SHOULD discover Methods through the Method Registry rather than through direct implementation dependencies.

---

## 12.8 Device Services

Device Services provide standard interfaces to external hardware and operating system services.

Examples include:

- NFC;
- Bluetooth;
- GPS;
- Camera;
- USB;
- Accelerometer;
- Biometrics;
- Network services.

Device Services expose Signals.

They do not interpret those Signals.

---

## 12.9 Platform Services

Platform Services represent operating system functionality outside the scope of the Architecture Standard.

Examples include:

- Android;
- iOS;
- Desktop operating systems;
- Bluetooth stacks;
- Camera APIs;
- Location providers;
- Notification frameworks.

ResearchOS interacts with Platform Services exclusively through Device Services.

---

## 12.10 Registries

ResearchOS uses registries to discover architectural components dynamically.

Typical registries include:

- Method Registry;
- Device Service Registry;
- Signal Registry;
- Spatial Reference Registry;
- Policy Registry.

Registries allow functionality to be extended without modifying the Execution Engine.

---

## 12.11 Architectural Principles

The runtime architecture follows several fundamental principles.

### Separation of Responsibilities

Each runtime component has a single architectural responsibility.

---

### Discovery over Dependency

Components SHOULD be discovered through registries rather than referenced directly.

---

### Composition over Inheritance

Complex behaviour SHOULD emerge through composition of Methods rather than specialised execution engines.

---

### Platform Independence

Platform-specific functionality SHOULD be isolated behind Device Services.

---

### Stable Interfaces

Architectural interfaces SHOULD evolve more slowly than implementations.

This enables long-term interoperability across implementations.

---
# 13. Extensibility Model

The Architecture Standard is designed to evolve through extension rather than modification.

New functionality SHOULD be introduced by implementing existing architectural abstractions rather than altering the execution model or introducing application-specific exceptions.

The architecture therefore remains stable while methods, resources, workflows and device integrations continue to expand.

---

## 13.1 Design Principle

Every new feature SHOULD answer two questions.

1. Which architectural domain does it belong to?
2. Which existing architectural abstractions does it implement?

If both questions can be answered without modifying the architecture itself, the feature is considered architecturally conformant.

---

## 13.2 Extensible Components

The following architectural components are intended to be extended.

| Component | Examples |
|-----------|----------|
| Methods | NFC, BLE, body map, image annotation, laboratory calculations |
| Device Services | Camera, GPS, USB, Bluetooth, biometrics, sensors |
| Knowledge | New entity types, observations, classifications |
| Spatial Reference Systems | Body maps, lesion maps, microscopy, floor plans |
| Policies | Authentication, storage, validation, provenance |
| Workflows | Enrolment, clinic visits, laboratory processing |

Extending these components SHALL NOT require modification of the Execution Engine.

---

## 13.3 Method Registration

New executable behaviour is introduced by registering Methods.

The Execution Engine discovers available Methods through the Method Registry.

Implementations SHOULD avoid compile-time dependencies between the Execution Engine and individual Methods.

---

## 13.4 Device Services

New hardware integrations SHOULD be implemented as Device Services.

Examples include:

- NFC readers;
- Bluetooth devices;
- cameras;
- laboratory instruments;
- wearable sensors;
- environmental monitors.

Device Services produce Signals through standard interfaces.

Existing Methods SHOULD continue to operate without modification.

---

## 13.5 Spatial Reference Systems

The architecture supports arbitrary spatial representations.

Examples include:

- geographic coordinates;
- body maps;
- lesion maps;
- microscopy images;
- laboratory plates;
- building floor plans;
- agricultural plots;
- network topologies.

New spatial systems SHOULD be introduced by implementing Spatial Reference Systems rather than modifying the architecture.

---

## 13.6 Workflow Composition

Workflows are composed from existing Methods.

Examples include:

- participant enrolment;
- household census;
- vaccination campaigns;
- chain of custody;
- adverse event reporting;
- inventory management.

New workflows SHOULD emerge through composition rather than specialised execution engines.

---

## 13.7 Modular Architecture

Feature-specific modules SHOULD encapsulate domain-specific functionality while depending only upon stable architectural interfaces.

Typical modules include:

- Body Mapping
- Image Annotation
- Randomisation
- Sample Tracking
- Protocol Completeness
- ResearchOS Integration

Modules SHOULD register Methods, Device Services and presentation components without modifying the Execution Engine.

---

## 13.8 Compatibility

Architectural evolution SHOULD prioritise:

- additive interfaces;
- versioned specifications;
- capability discovery;
- graceful degradation.

Optional functionality SHOULD NOT prevent interoperability between conforming implementations.

---

## 13.9 Conformance

A feature is architecturally conformant when it:

- preserves the five architectural domains;
- participates in the canonical execution model;
- exposes standard architectural interfaces;
- maintains separation of concerns;
- records provenance through the standard execution model.

Features requiring modification of the architectural domains or execution model SHOULD be considered proposals for a future revision of the Architecture Standard rather than ordinary extensions.

---

## 13.10 Design Principle

The Architecture Standard SHOULD remain small and stable.

Innovation SHOULD occur through new Methods, Device Services, Resources, Types and Policies rather than continual modification of the architecture itself.

A stable architecture provides the foundation upon which the remainder of the ResearchOS specification family can evolve.

---
# 14. Conformance

This specification defines the conceptual architecture of ResearchOS.

A conforming implementation SHALL preserve the architectural concepts, relationships and execution semantics defined by this standard.

Conformance is determined by architectural behaviour rather than implementation technology.

---

## 14.1 Required Characteristics

A conforming implementation SHALL:

- preserve the five architectural domains;
- implement the canonical execution model;
- maintain separation of concerns between domains;
- support execution through Methods;
- represent state explicitly;
- represent Transformations explicitly;
- maintain architectural provenance.

---

## 14.2 Implementation Independence

Conformance does not require any particular:

- programming language;
- operating system;
- storage technology;
- transport mechanism;
- user interface;
- deployment architecture.

Implementations MAY optimise internal behaviour provided the architectural semantics remain unchanged.

---

## 14.3 Extension

Conforming implementations MAY introduce:

- new Methods;
- new Device Services;
- new Resources;
- new Types;
- new Policies;
- new Workflows.

Such extensions SHALL preserve the architecture defined by this specification.

---
# 15. Companion Specifications

The Architecture Standard forms the conceptual foundation of the ResearchOS specification family.

Companion specifications define complementary aspects of the platform.

| Specification | Responsibility |
|---------------|----------------|
| Architecture Standard (AS) | Conceptual architecture |
| ResearchOS Intent Language (RIL) | Platform-independent request language |
| Core Verbs | Standard action vocabulary |
| Core Resources | Standard research entities |
| Core Types | Resource and Method specialisation |
| Core Policies | Execution policies |
| Transport Bindings | JSON, Android, HTTP and future representations |
| Protocol Definition Language | Declarative protocol specification |
| ResearchOS Orchestrator | Protocol execution over time |

Each specification has a single responsibility.

Together they define the complete ResearchOS platform.

---

# 15. Companion Specifications

The Architecture Standard forms the conceptual foundation of the ResearchOS specification family.

Companion specifications define complementary aspects of the platform.

| Specification | Responsibility |
|---------------|----------------|
| Architecture Standard (AS) | Conceptual architecture |
| ResearchOS Intent Language (RIL) | Platform-independent request language |
| Core Verbs | Standard action vocabulary |
| Core Resources | Standard research entities |
| Core Types | Resource and Method specialisation |
| Core Policies | Execution policies |
| Transport Bindings | JSON, Android, HTTP and future representations |
| Protocol Definition Language | Declarative protocol specification |
| ResearchOS Orchestrator | Protocol execution over time |

Each specification has a single responsibility.

Together they define the complete ResearchOS platform.

---

# Appendix A – Glossary

| Term | Definition |
|------|------------|
| Device Service | Standard interface to an external device or operating system service. |
| Entity | Something that exists within the Knowledge Domain. |
| Event | A point in time at which something occurs. |
| Execution | Invocation of one or more Methods in response to an Execution Request. |
| Execution Engine | Runtime component responsible for coordinating execution. |
| Execution Request | A request submitted to the runtime for execution. |
| Knowledge | Information represented by the platform. |
| Method | Fundamental executable architectural component. |
| Observation | Knowledge acquired about an Entity. |
| Policy | Constraint governing execution behaviour. |
| Provenance | Information describing how execution occurred. |
| Region | A bounded spatial area within a Spatial Reference System. |
| Signal | Transient information generated by a Device Service. |
| Spatial Reference System | Coordinate system describing spatial relationships. |
| State | Current condition of an Entity or Workflow. |
| Transformation | Explicit change to architectural state. |
| Workflow | A Method coordinating multiple Methods. |

---
# Appendix B – Revision History

| Version | Summary |
|---------|---------|
| AS1.00 | Initial Architecture Standard. |
| AS1.01 | Markdown conversion; alignment with ResearchOS terminology; Method terminology replacing Capability terminology; canonical Execution Model; Reference Runtime Architecture; separation of architecture, language and runtime; alignment with the ResearchOS Intent Language (RIL); improved specification structure. |

