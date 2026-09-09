# MethodMesh Core Transport Substrate

## Objective

Implement and push the smallest generic MethodMesh core extension required to support asynchronous, durable, bidirectional communication between MethodMesh and external transports/devices.

This work is a prerequisite for a later ESP-based mesh networking module, but **the core implementation must contain no ESP-NOW-specific, ESP32-specific, LoRa-specific, BLE-specific, NFC-specific, or mesh-routing-specific concepts**.

The new substrate must be generic enough to support future transports such as:

- ESP-backed radio gateways
- BLE peers
- USB/serial devices
- LAN peers
- LoRa gateways
- internet relays
- other future external transports

The goal is not to implement any of those transports now.

## Repository

Work directly in the current MethodMesh repository.

Inspect the existing architecture before changing anything.

Do not assume the names proposed below are appropriate if equivalent abstractions already exist. Prefer extending an existing canonical abstraction over introducing a parallel one.

Preserve existing IDs, module contracts, capability contracts, navigation behaviour, presets, protocols, ODK behaviour, commit semantics and public interfaces unless a change is genuinely required.

## Architectural principle

Core should understand:

> A MethodMesh message was sent or received through a transport.

Core should **not** understand:

> This packet came from ESP-NOW, BLE, LoRa, Wi-Fi, NFC or a particular radio topology.

Transport implementations belong outside the generic core contract.

The dependency direction must remain:

```text
modules / transport implementations
            ↓
generic core transport API
            ↓
core persistence / dispatch
```

Core must never depend on a specific transport implementation.

---

# Phase 1 — Architecture review

Before modifying code, inspect the repository for existing mechanisms covering:

- capability invocation
- capability results
- live-working-result → Commit lifecycle
- module IDs and capability IDs
- cross-module dispatch
- background services
- persistent queues
- WorkManager
- Android services / foreground services
- repositories or event buses
- external-device abstractions
- intents/deep links
- ODK handoff / result return
- protocol execution
- preset execution
- serialisation formats
- Room/database persistence
- notifications
- application-level coroutine scopes

Explicitly determine whether the required functionality can already be constructed from existing abstractions.

## Gate A

Attempt to implement this feature with **zero core changes** conceptually.

If the repository already contains suitable generic contracts, reuse them and only add the missing pieces.

Do not introduce new abstractions solely because the names in this specification differ from existing names.

Document the decision briefly in the commit/PR description.

---

# Required functional model

At minimum, MethodMesh needs to support this lifecycle:

```text
external transport
      ↓
transport implementation
      ↓
generic MethodMesh inbound envelope
      ↓
durable persistence
      ↓
validation / deduplication
      ↓
destination resolution
      ↓
module / capability / system consumer
```

and the reverse:

```text
module / capability / system producer
      ↓
generic outbound envelope
      ↓
durable persistence
      ↓
appropriate registered transport
      ↓
external transport
```

The transport does not need to be connected at the time a message is produced.

---

# Generic message envelope

Introduce or extend a stable serialisable envelope representing transported MethodMesh data.

Exact class names may follow repository conventions.

Conceptually it must be capable of expressing:

```text
messageId
schemaVersion

source
destination

messageType

moduleId        optional
capabilityId    optional

createdAt
expiresAt       optional

correlationId   optional
replyTo         optional

payloadType
payload

metadata        bounded / extensible
```

Do not over-design this.

## IDs

`messageId` must be globally collision-resistant.

Use existing MethodMesh ID conventions where appropriate.

Do not use transport-level addresses such as MAC addresses as MethodMesh identity.

Transport addressing belongs in transport-specific implementations or bindings.

## Source and destination

Source/destination should allow future addressing of things such as:

- local MethodMesh installation
- another MethodMesh installation
- external node/device
- module
- capability
- logical group
- broadcast/system endpoint

Do not hard-code today's expected ESP use case into the address model.

If a simpler generic endpoint abstraction already exists, use it.

---

# Message types

Do not attempt to enumerate every possible application message in core.

Core should support a small generic classification mechanism.

Likely examples include:

```text
COMMAND
RESULT
OBSERVATION
DATA
EVENT
STATUS
ACKNOWLEDGEMENT
```

These are examples, not mandatory enum values.

Prefer an extensible representation if a closed enum would force core changes whenever a module introduces a new semantic message type.

Transport acknowledgements and application acknowledgements must remain conceptually distinct.

---

# Payloads

Core should transport opaque payloads without needing to understand every payload schema.

A module must be able to own and version its payload schema.

Use an existing MethodMesh serialisation mechanism if one exists.

Avoid Java/Kotlin object serialisation tied to concrete implementation classes.

Payload size limits must be explicit at API boundaries, but **core must not adopt ESP-NOW packet-size limits**.

Fragmentation/reassembly belongs to constrained transport implementations unless repository architecture establishes a better generic layer.

---

# Durable inbox and outbox

Incoming and outgoing messages must survive:

- activity destruction
- navigation away from a capability
- process death
- device restart where appropriate
- temporary transport disconnection

Implement durable persistence using existing repository/database patterns.

Do not build an independent miniature database if the app already has a persistence architecture.

## Outbox states

The model should be able to represent at least conceptually:

```text
QUEUED
IN_PROGRESS
SENT
DELIVERED        where transport/application semantics support this
FAILED_RETRYABLE
FAILED_PERMANENT
EXPIRED
```

Do not pretend `SENT` means `DELIVERED`.

Different transports have different guarantees.

## Inbox states

Likely requirements:

```text
RECEIVED
DISPATCH_PENDING
DISPATCHED
CONSUMED
FAILED
```

Exact representation can be simplified if existing architecture supports equivalent semantics.

---

# Idempotency and deduplication

Inbound processing must be idempotent.

Receiving the same `messageId` repeatedly must not repeatedly perform an application action unless the destination explicitly permits that behaviour.

This is essential because future transports may retry, rebroadcast or use store-and-forward.

Deduplication belongs in the generic ingestion boundary, not every individual module.

Retain enough history to make deduplication useful without allowing unbounded database growth.

Implement an explicit pruning strategy.

---

# Transport contract

Define or identify a generic transport provider interface.

Conceptually it should support operations equivalent to:

```text
transportId
availability/status
start()
stop()
send(envelope)
```

and an inbound delivery mechanism such as:

```text
receive(envelope)
```

or a Flow/callback into the central dispatcher.

Use Kotlin Flow/coroutines if consistent with the existing codebase.

Do not force every transport to expose synchronous connectivity.

A delay-tolerant transport may legitimately be:

```text
available = true
connected = false
```

while still accepting queued outbound work.

---

# Transport registration

Transport implementations should be discoverable/registrable without editing central dispatch logic for every new transport.

Prefer the repository's existing dependency-injection or registry pattern.

Adding a future transport should ideally require:

```text
implement contract
register provider
```

rather than editing a large central `when` statement.

---

# Transport bindings

MethodMesh needs a generic way to associate a logical destination with one or more available transports.

Do not implement sophisticated routing now.

The substrate merely needs to make future routing possible.

A minimal model could support:

```text
logical endpoint
    ↔
transport
    ↔
transport-local address
```

Transport-local addressing must remain opaque to core.

For example, core should not need to know that a future transport address happens to be a Bluetooth UUID, MAC address, serial port, mesh node number or IP address.

---

# Dispatcher

Implement a central inbound dispatcher or extend an existing equivalent.

It must:

1. validate the envelope
2. reject unsupported schema versions safely
3. deduplicate by message ID
4. persist before application delivery where durability is promised
5. identify the logical destination
6. dispatch to an appropriate registered consumer
7. record the outcome
8. avoid crashing the global listener because one consumer fails

A malformed external message must never be able to crash MethodMesh.

---

# Consumer registration

Modules/capabilities need a generic way to receive messages intended for them.

Do not make the dispatcher depend on concrete module classes.

Prefer something equivalent to:

```text
MessageConsumer
```

registered against a canonical MethodMesh destination or message namespace.

A module that does not implement asynchronous messaging must be completely unaffected.

---

# Capability integration

Do not redesign MethodMesh capability semantics.

Transport messages should integrate with existing capability invocation/result contracts where possible.

Important distinction:

```text
transport message received
```

is not automatically:

```text
capability result committed
```

Preserve the existing live-working-result → Commit lifecycle.

An inbound observation or result may populate working state, an inbox, or an explicit destination, but must not silently bypass established Commit semantics unless the existing architecture explicitly defines such behaviour.

---

# ODK integration

Do not implement ESP/mesh ODK behaviour in this phase.

However, the transport substrate must not prevent future messages carrying:

- ODK-compatible observations
- form submission material
- capability results destined for an ODK roundtrip

Reuse canonical IDs so future ODK routing does not need a second parallel addressing scheme.

No new ODK-specific core subsystem should be created here.

---

# External node/device model

Inspect whether MethodMesh already has a generic representation for external hardware.

If yes, reuse it.

Only add a generic external endpoint/node abstraction if it is genuinely required for transport operation.

If introduced, it must not assume:

- ESP hardware
- sensors
- radio links
- permanent connectivity
- particular addressing formats

A generic external endpoint might eventually represent:

```text
sensor node
relay
phone
gateway
instrument
computer
```

but this phase should implement only what is necessary.

---

# Android service lifecycle

MethodMesh must eventually be capable of receiving transport traffic when no capability screen is open.

Design and implement the minimum generic lifecycle support required for that.

Inspect modern Android restrictions carefully.

Do not create a permanently running foreground service merely because future ESP functionality might want one.

Determine what genuinely belongs in core versus transport-specific service implementations.

Preferred separation:

```text
core:
    message persistence
    dispatcher
    transport registry
    lifecycle hooks/contracts

transport:
    BLE scanning
    sockets
    USB connections
    foreground-service requirement
    radio-specific reconnection behaviour
```

If a generic application-level transport coordinator is justified, implement it.

If Android requires a foreground service only for particular transports, those transports should own that requirement.

---

# Security boundary

Core must treat inbound transport content as untrusted input.

Validate:

- schema version
- required identifiers
- payload length
- metadata limits
- timestamps where relevant
- destination validity

Transport-specific cryptography belongs outside core.

Application-level authentication/encryption may be introduced later, but **do not invent the ESP network security model in this change**.

The generic envelope must be compatible with future authenticated/encrypted payloads.

Never log secrets or opaque encrypted payloads unnecessarily.

---

# Failure behaviour

The architecture must explicitly handle:

- malformed message
- duplicate message
- unsupported version
- unknown destination
- missing transport
- disconnected transport
- transport crashing
- app process restart
- destination module unavailable
- retry exhaustion
- expired message
- storage full/database error

Failure in one transport must not disable other registered transports.

Failure delivering one message must not halt the queue.

Use bounded backoff consistent with existing MethodMesh practices.

---

# Resource limits

Persistent queues must be bounded/prunable.

External input must not permit unbounded:

- payload size
- metadata size
- retry loops
- queue growth
- dedupe history
- log growth

Choose conservative defaults and make important limits explicit.

Do not derive these limits from ESP-NOW.

---

# Observability

Add sufficient diagnostics to inspect:

```text
registered transports
transport state
inbox depth
outbox depth
failed messages
last successful send
last successful receive
```

Use existing Workbench/diagnostic infrastructure if appropriate.

Do not create a polished user-facing Mesh UI in this phase.

Development diagnostics are sufficient.

Never expose secrets in diagnostics.

---

# Tests

Tests are mandatory.

Use the repository's existing test infrastructure correctly.

Do not introduce test dependencies that the project does not contain.

At minimum test:

### Envelope

- encode/decode roundtrip
- unknown/unsupported schema
- malformed envelope
- optional routing fields

### Inbox

- persistence
- duplicate suppression
- process/repository recreation where testable
- successful dispatch
- failing consumer isolation
- unknown destination

### Outbox

- queue while transport unavailable
- successful send
- retryable failure
- permanent failure
- expiry
- ordering where ordering is promised

### Registry

- transport registration
- multiple transports
- transport failure isolation

### Idempotency

Send the same inbound envelope repeatedly and prove the consumer action occurs once.

### Restart/recovery

Where feasible, demonstrate that queued work can be recovered from persistent state.

---

# Fake transport

Implement a small in-memory/fake transport for tests and development.

This is important because it proves the core abstraction is genuinely transport-agnostic.

It should allow tests to:

```text
inject inbound envelopes
capture outbound envelopes
simulate unavailable state
simulate retryable errors
simulate permanent errors
```

Do not implement ESP-NOW, BLE or networking as the test transport.

---

# Backwards compatibility

Existing MethodMesh behaviour must remain unchanged when no transport providers are registered.

Specifically verify that this work does not regress:

- dashboard
- direct capability use
- presets
- protocols
- ODK Forms
- capability result handling
- live result → Commit
- launch-origin completion
- module discovery
- Workbench
- existing persistence

---

# Build and validation

Run the strongest available relevant validation, ideally including:

```text
Gradle build
Kotlin compilation
unit tests
lint/static checks where already configured
```

Fix problems caused by this work.

Do not paper over compilation errors with suppression unless genuinely justified.

Do not introduce new dependencies without a clear need.

Prefer Android/Kotlin/platform functionality and dependencies already used by MethodMesh.

---

# Iterative review requirement

Do not implement the first design and stop.

Perform several explicit internal review passes.

## Review 1 — Necessity

Ask:

> Does each new core abstraction actually need to exist?

Delete redundant abstractions.

## Review 2 — Transport contamination

Search the new core code for assumptions derived from:

```text
ESP
ESP32
ESP-NOW
BLE
LoRa
mesh
radio
MAC address
Wi-Fi channel
NFC
sensor
```

None should appear as architectural assumptions.

A generic comment mentioning possible examples is acceptable; dependencies or data-model assumptions are not.

## Review 3 — Failure model

Walk through:

```text
duplicate delivery
offline destination
process death
transport death
retry
message expiry
malformed external data
database failure
```

Correct weaknesses found.

## Review 4 — MethodMesh architectural parity

Ensure this did not create an alternate subsystem bypassing canonical:

```text
module IDs
capability IDs
result semantics
Commit lifecycle
ODK interfaces
protocol/preset behaviour
```

## Review 5 — Simplification

Try to remove code.

Prefer the smallest stable substrate that enables the next phase.

---

# Documentation

Add concise architecture documentation explaining:

1. what the transport substrate is
2. what belongs in core
3. what belongs in transport implementations
4. envelope lifecycle
5. durability semantics
6. delivery semantics
7. extension procedure for a new transport
8. explicit non-goals

Include an architecture diagram similar to:

```text
┌───────────────────────────────────────┐
│           MethodMesh modules          │
│ capabilities / protocols / ODK / etc │
└───────────────────┬───────────────────┘
                    │
             logical messages
                    │
┌───────────────────▼───────────────────┐
│        Core transport substrate       │
│ envelope • inbox/outbox • dispatch   │
│ registry • durability • idempotency  │
└───────────────────┬───────────────────┘
                    │
             transport contract
                    │
       ┌────────────┼────────────┐
       ▼            ▼            ▼
    future        future       future
     ESP            BLE          LoRa
 transport      transport     transport
```

The future transports are illustrative only and must not be implemented.

---

# Versioning

If the repository maintains architecture-standard or Master Book documentation/versioning that should record a new core contract, update the appropriate canonical documentation following the repository's existing rules.

Do not casually change stable public contract versions.

If a new contract version is necessary, explain why.

---

# Git workflow

Work on a suitable feature branch.

Before pushing:

1. inspect diff carefully
2. run build/tests
3. remove accidental/generated files
4. verify no secrets or local paths
5. ensure documentation matches implementation
6. inspect git status
7. commit with a clear message

Suggested commit theme:

```text
core: add generic durable transport substrate
```

Push the completed branch to GitHub.

If the repository convention is to merge directly to the main development branch and the environment/user permissions permit it, follow the established repository workflow rather than inventing a new one.

Do not create a release/tag unless repository policy clearly requires one for this class of core change.

---

# Completion report

Return a concise report containing:

- branch and commit hash
- files changed
- core abstractions added/reused
- persistence changes
- tests added
- validation/build outcome
- documentation changed
- any deliberate deviations from this spec
- any unresolved limitation that matters to the forthcoming external transport implementation

Most importantly, answer these final architectural checks explicitly:

```text
Does core contain any ESP-NOW-specific logic?        NO
Does core contain any ESP32-specific logic?          NO
Can a future non-ESP transport implement the API?    YES
Can inbound messages arrive without a capability UI? YES
Can outbound messages survive disconnection?         YES
Are duplicate inbound messages idempotent?           YES
Does this bypass the Commit lifecycle?                NO
```

## Non-goals

Do **not** implement in this task:

- ESP32 firmware
- ESP-NOW
- BLE gateway protocol
- NFC provisioning
- mesh routing
- TTL forwarding
- radio packet fragmentation
- ESP sensor discovery
- cryptographic mesh enrolment
- chat UI
- mesh dashboard
- ODK mesh forms
- LoRa
- internet relay
- remote node control

Those belong to the subsequent MethodMesh ESP Mesh work.

The output of this task is the **stable generic core substrate that the subsequent implementation can depend upon**.