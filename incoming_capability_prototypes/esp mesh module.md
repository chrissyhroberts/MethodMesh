# MethodMesh ESP Mesh Module

## Objective

Design and implement a MethodMesh module that provides resilient offline messaging and data exchange using ESP32 hardware and ESP-NOW, integrated with the existing MethodMesh ESP sensor framework and the new generic core transport substrate.

The module should make a low-cost ESP32 act as a MethodMesh radio/node/gateway that can:

- join a MethodMesh mesh network
- exchange messages with nearby ESP nodes
- relay packets between nodes
- store and forward packets when peers are temporarily unavailable
- bridge data between MethodMesh Android and ESP-NOW
- expose attached sensors and actuators through MethodMesh
- receive capability/data/observation messages from other MethodMesh devices
- send locally generated MethodMesh results and observations across the network
- be provisioned securely with minimal field friction, preferably by NFC-assisted enrolment

This is not merely a chat application.

The goal is a reusable **MethodMesh field transport and node framework**.

---

# Core constraint

The generic transport substrate in MethodMesh core is authoritative for:

- generic message envelopes
- durable inbox/outbox
- dispatch
- idempotency
- transport registration
- generic transport lifecycle

This module must implement against those contracts.

Do not duplicate them.

Do not modify core unless a genuinely missing generic capability is discovered.

If a core change appears necessary:

1. stop before introducing an ESP-specific workaround
2. determine whether the missing feature is transport-agnostic
3. make the smallest generic change possible
4. document why it belongs in core
5. keep all ESP-specific behaviour in this module/firmware

---

# First task: inspect current repository state

Before implementation:

- pull the latest repository
- inspect the landed transport substrate
- inspect the current ESP sensor framework
- inspect NFC-related MethodMesh code
- inspect module discovery conventions
- inspect capability/result/Commit lifecycle
- inspect existing BLE/USB/device communication abstractions
- inspect ODK roundtrip contracts
- inspect module documentation rules
- inspect current Master Book / architecture standard

Do not implement against assumptions from an earlier design if the actual landed API differs.

Adapt to the repository.

---

# Architectural model

The intended dependency chain is:

```text
MethodMesh capability / ODK / protocol / user
                  ↓
        generic core transport API
                  ↓
          ESP Mesh transport
                  ↓
       Android ↔ ESP gateway link
                  ↓
        MethodMesh ESP firmware
                  ↓
               ESP-NOW
                  ↓
          neighbouring ESP nodes
```

The module must not bypass the generic core transport subsystem.

---

# Major components

The implementation should be separated into at least these conceptual layers.

## 1. Android MethodMesh module

Responsible for:

- mesh/network management UI
- ESP node discovery
- provisioning workflow
- network membership
- node status
- gateway selection
- messaging capability
- sensor/node capability exposure
- transport integration
- diagnostics

## 2. Phone ↔ ESP gateway protocol

Responsible for reliable communication between Android MethodMesh and a nearby ESP32.

Prefer BLE initially unless repository/hardware analysis strongly supports another mechanism.

The protocol must be explicitly versioned.

It should support:

- handshake
- node identity
- capabilities
- network status
- inbound mesh packet delivery
- outbound mesh packet submission
- provisioning
- status/health
- sensor readings
- configuration
- firmware/protocol version

Do not conflate this protocol with the ESP-NOW packet protocol.

## 3. ESP firmware

Responsible for:

- ESP-NOW radio
- packet reception
- packet transmission
- forwarding
- deduplication
- TTL handling
- store-and-forward
- peer discovery where needed
- BLE or other phone bridge
- persistent network configuration
- sensor framework integration
- node capability declaration
- diagnostics
- safe recovery from reboot/power loss

## 4. MethodMesh mesh protocol

Responsible for application-level mesh semantics independent of one radio frame.

This is the protocol carried across ESP-NOW.

It must support:

- identity
- message IDs
- source
- destination
- TTL
- message type
- fragmentation where needed
- authentication/integrity
- encrypted payloads where required
- acknowledgement where appropriate
- expiry
- deduplication

---

# Do not blindly adopt an existing ESP project

Review existing open-source work, especially:

- Espressif ESP-NOW component/examples
- ESP-NOW mesh/messenger implementations
- store-and-forward ESP-NOW projects
- ESP-NOW + LoRa bridge implementations
- relevant lightweight mesh libraries

Reuse established radio/provisioning/security code where technically and legally appropriate.

But do not wholesale import an application architecture that conflicts with MethodMesh.

Before selecting a dependency, inspect:

- licence
- maintenance
- ESP-IDF compatibility
- Arduino dependency assumptions
- memory footprint
- routing model
- encryption model
- reliability model
- peer limits
- fragmentation behaviour
- testability

Prefer Espressif-maintained functionality for lower-level ESP-NOW features where possible.

Document third-party components and licences.

---

# Network roles

Do not define fundamentally different firmware images unless necessary.

Prefer one MethodMesh node firmware capable of assuming roles dynamically.

Possible roles:

```text
NODE
RELAY
GATEWAY
SENSOR
ACTUATOR
STORE_FORWARD
```

These should be capabilities/behaviours, not mutually exclusive hardware identities.

One ESP32 may simultaneously be:

```text
gateway + relay + sensor
```

A blank ESP32 should still be useful as:

```text
relay
gateway
store-and-forward node
```

---

# Node identity

Every ESP node needs a stable MethodMesh node identity generated at first boot or provisioning.

Do not use MAC address as the canonical MethodMesh identity.

MAC may be transport metadata only.

Node identity must survive reboot.

Conceptually:

```text
nodeId
deviceKeyPair / node secret
firmwareVersion
hardwareType
capabilities
networkMemberships
```

Avoid exposing hardware identifiers unnecessarily.

---

# Network identity

A MethodMesh mesh network must have a stable logical identity.

Conceptually:

```text
networkId
displayName
keyEpoch
network policy
radio parameters
```

The network ID is not the radio channel.

Network identity must remain valid if radio configuration changes.

---

# Provisioning UX

Provisioning should be extremely simple in the field.

Target workflow:

```text
Create network in MethodMesh
        ↓
Tap NFC / provision node
        ↓
Node enrols
        ↓
Node becomes visible
        ↓
Ready
```

The user should not normally type:

- MAC addresses
- radio channels
- cryptographic keys
- BLE UUIDs
- ESP peer identifiers

---

# NFC provisioning

NFC is an enrolment mechanism, not the transport itself.

Where supported, NFC provisioning should allow MethodMesh to convey a short-lived enrolment credential/token that bootstraps the node into the network.

Do not permanently store the full network root secret in plaintext on an NFC tag.

Prefer:

```text
one-time or short-lived enrolment token
          ↓
authenticated enrolment
          ↓
node receives derived membership credentials
```

If NFC hardware cannot directly provision a chosen ESP board, support an Android-mediated workflow in which NFC selects/authorises the network and BLE transfers the credentials.

Design this cleanly rather than pretending every ESP32 has NFC hardware.

---

# Key hierarchy

Do not use one permanent shared plaintext password for every node.

Design a proper but pragmatic hierarchy.

Conceptually:

```text
network root secret
       ↓
derived network epoch key
       ↓
node-specific membership material
       ↓
message/session keys where appropriate
```

Nodes should be removable without requiring permanent trust in that node.

Support future key rotation.

Do not over-engineer PKI unless justified.

---

# Compromised/lost node

Design for real field loss.

It should eventually be possible to:

- revoke a node
- rotate network credentials
- prevent a revoked node from receiving future traffic
- preserve unaffected node membership where practical

Record this in the protocol even if first implementation provides only basic rotation.

---

# ESP-NOW security

Use native ESP-NOW security primitives where useful, but do not assume they solve the complete MethodMesh threat model.

Native link encryption and MethodMesh application-level security are different layers.

The system must remain safe when:

- messages are forwarded
- broadcast frames are used
- packets traverse nodes that are not the final destination
- traffic is stored temporarily by relay nodes

Where confidentiality is promised, relay nodes should not need plaintext application payloads.

---

# Message addressing

Support logical destinations rather than radio addresses.

At minimum consider:

```text
specific node
specific MethodMesh endpoint
group
network broadcast
gateway/system
```

Do not make capability IDs or phone identities synonymous with ESP MAC addresses.

---

# Packet model

Define a compact versioned ESP mesh frame.

Conceptually:

```text
protocolVersion
networkId
messageId
sourceNodeId
destination
fragmentInfo
ttl
flags
createdAt / age
payloadType
payload
authentication
```

Keep frame overhead low.

Do not copy the generic core envelope byte-for-byte if that is inefficient.

The ESP protocol may encode a compact representation that reconstructs a generic MethodMesh envelope at the gateway.

Document the mapping exactly.

---

# Fragmentation

ESP-NOW payload size is constrained.

Large MethodMesh messages therefore need an explicit strategy.

Implement or design:

```text
message
  ↓
fragments
  ↓
independent transmission
  ↓
reassembly
  ↓
validation
  ↓
delivery
```

Fragments must include enough information to:

- identify parent message
- identify fragment number
- identify total fragments
- detect corruption/missing data
- expire incomplete assemblies

Bound:

- maximum message size
- simultaneous incomplete messages
- memory used by reassembly
- timeout

Do not permit unbounded allocations from received metadata.

---

# Reliability model

ESP-NOW radio delivery is not equivalent to end-to-end application delivery.

Distinguish:

```text
radio transmitted
radio acknowledged
next node accepted
destination received
application consumed
```

Do not expose a simplistic “delivered” status when only a local radio send succeeded.

Implement the minimum useful acknowledgement scheme without producing acknowledgement storms.

---

# Routing model — first release

Prefer a simple robust routing model over sophisticated route discovery.

Initial target:

```text
controlled flooding + TTL + deduplication
```

Each node:

1. receives packet
2. authenticates/validates
3. checks message/fragment dedupe cache
4. consumes if addressed locally
5. decrements TTL
6. forwards when allowed

Avoid rebroadcast loops.

Use jitter/backoff to avoid every relay transmitting simultaneously.

---

# Duplicate suppression

Every node requires a bounded recent-message cache.

Potential key:

```text
networkId + messageId + fragment index
```

The cache must:

- survive long enough to prevent loops
- remain bounded
- expire old entries
- avoid excessive flash writes

Prefer RAM for short dedupe state unless persistence is demonstrably required.

---

# Store-and-forward

This is a major requirement.

A node should be able to retain eligible traffic when forwarding is currently impossible.

Messages must have:

```text
expiry
priority
attempt count
last attempt
```

Store-and-forward should survive reboot where appropriate.

Do not write every transient packet synchronously to flash.

Use sensible batching/queue design to protect flash endurance.

---

# Queue prioritisation

At least distinguish:

```text
CONTROL
ALERT
NORMAL
BULK
```

Do not let a large bulk transfer starve:

- control traffic
- acknowledgements
- emergency messages
- node status

The exact names may differ.

---

# Congestion control

Flooding can collapse under load.

Add pragmatic protections:

- bounded outbound queues
- forwarding jitter
- duplicate suppression
- retry limits
- TTL limits
- rate limits
- priority
- bulk-transfer throttling

Avoid implementing a complicated dynamic routing protocol in v1.

---

# Radio channel behaviour

ESP-NOW channel constraints must be addressed explicitly.

Nodes must know which channel the network currently uses.

Provisioning should configure this automatically.

Design recovery for a node with stale channel information.

Do not expose radio channels prominently in normal UX.

Advanced diagnostics may expose them.

---

# Phone gateway

The MethodMesh phone communicates with one or more nearby ESP nodes acting as gateways.

The Android layer should not assume the phone itself speaks ESP-NOW.

A gateway ESP should expose:

```text
mesh status
network membership
node identity
queued traffic
received MethodMesh traffic
outbound injection
sensor capabilities
diagnostics
```

---

# Gateway redundancy

Do not hard-wire one permanently designated gateway.

If multiple provisioned ESPs are available to the phone, the system should be capable of selecting one.

Initially this may simply prefer:

```text
connected
same network
healthy
strongest/recent connection
```

Do not create duplicate MethodMesh delivery when switching gateways.

Core `messageId` idempotency should help here.

---

# BLE bridge

If BLE is selected for phone↔ESP:

Design a versioned GATT protocol rather than arbitrary characteristic writes.

Consider:

```text
control
inbound stream
outbound stream
status
provisioning
capabilities
```

Use framing and length checks.

Support reconnect.

Android should tolerate:

- ESP reboot
- phone Bluetooth cycling
- app process restart
- moving out of range
- gateway replacement

Do not couple BLE connection lifecycle to one capability screen.

---

# Background listening

The device must remain capable of receiving mesh traffic when the Mesh UI is closed, subject to Android platform constraints.

Use the new generic transport substrate correctly.

The ESP transport should own any Android behaviour specifically required to maintain the BLE/device link.

Do not force all MethodMesh transports to use a permanent foreground service.

If Android requires a foreground service for continuous operation, implement it transparently and correctly with:

- appropriate notification
- user-visible state
- clean shutdown
- restart/reconnection handling
- no busy loops

---

# Sensor framework integration

This must integrate with the existing MethodMesh ESP sensor framework rather than create a second ESP ecosystem.

A node should self-describe its hardware capabilities.

For example:

```json
{
  "nodeId": "…",
  "capabilities": [
    "sensor.temperature",
    "sensor.humidity",
    "gpio.output"
  ]
}
```

Use existing MethodMesh canonical capability semantics if already defined.

The module should support:

```text
read now
observe/stream
configure
receive observation
send observation onward
```

Do not invent separate sensor naming if the ESP Sensor framework already defines it.

---

# Remote commands

The protocol should support safe addressed commands such as:

```text
READ
CONFIGURE
ACTUATE
STATUS
```

but do not expose unrestricted arbitrary code execution or raw remote shell functionality.

Commands must be:

- schema-defined
- validated
- authorised
- bounded

Dangerous actuator operations should require explicit capability policy.

---

# MethodMesh Messaging capability

Provide a first-class human messaging capability as one application of the transport.

It should support at least:

```text
message composition
recipient / group
send
queued state
received state
failure state
timestamps
```

Do not make this the architecture.

Messaging is one consumer of the mesh transport.

---

# Data/observation transport

Support transport of MethodMesh observations/capability results.

A received remote observation should map into canonical MethodMesh structures where possible.

Examples:

```text
temperature observation
GPS point
counter result
measurement
status
```

Do not automatically commit remote results into an active capability session unless canonical MethodMesh semantics explicitly permit this.

---

# ODK

ODK integration must be designed from the beginning.

The mesh should eventually support transfer of:

- ODK-relevant observations
- form-associated data
- queued submissions or submission fragments where appropriate

For the first implementation, only implement ODK flows that are already supported safely by the core APIs.

Do not invent an independent “mesh ODK” format.

Use canonical form/module/capability identifiers.

Any offline ODK transfer must preserve provenance and avoid representing a mesh hop as successful Central/Kobo submission.

---

# Provenance

Every remotely received datum should retain provenance sufficient to answer:

```text
which originating node/device?
which MethodMesh endpoint?
when created?
when received?
through which transport?
message ID?
network ID?
```

Transport hops may be optional diagnostics.

Do not overwrite origin with the gateway identity.

---

# Time

ESP nodes may lack trustworthy wall-clock time.

Do not require globally accurate RTC for correctness.

Prefer:

- message IDs
- monotonic age
- receive timestamp
- optional wall-clock timestamp
- synchronisation when a trusted phone is connected

Distinguish:

```text
origin timestamp
gateway receive timestamp
MethodMesh ingest timestamp
```

where relevant.

---

# Power-aware operation

ESP nodes may run from battery.

Provide configurable operating profiles such as:

```text
always-on relay
normal
low-power sensor
gateway
```

A sleeping sensor cannot be treated as an always-available relay.

Routing/status must reflect this.

Do not make an always-on radio mandatory for every sensor node.

---

# Persistent node state

Persist only necessary durable state:

```text
node identity
network membership
keys
configuration
store-forward queue
firmware metadata
```

Transient neighbour/routing state should not create excessive flash writes.

---

# Factory reset

Provide a well-defined node reset path.

Factory reset must remove:

- network membership
- network credentials
- queued confidential traffic
- user configuration

while retaining only what is appropriate for firmware identity.

The UI must make destructive reset explicit.

---

# Firmware update

Do not implement an elaborate mesh OTA system unless already supported cleanly by selected Espressif components.

However, design firmware metadata so future OTA is possible.

Expose:

```text
firmware version
protocol version
hardware target
```

Avoid protocol assumptions that require all nodes to update simultaneously.

---

# Protocol compatibility

Version every externally persisted or transmitted protocol.

Handle unknown future versions gracefully.

Do not crash or reinterpret unknown payloads.

Aim for:

```text
old nodes coexist with newer nodes
```

where practical.

---

# Mesh network UI

Create a polished MethodMesh module dashboard appropriate to field networking.

Likely sections:

```text
Network
Nodes
Messages
Data
Diagnostics
Settings
```

The dashboard is not the only interaction path.

Canonical individual capabilities must remain separately exposed for:

- direct native use
- presets
- protocols
- ODK where applicable

Follow MethodMesh module architecture rules.

---

# Network dashboard

Show useful operational state rather than radio jargon.

For example:

```text
Field Team Network

Connected gateway
Nearby nodes
Known nodes
Messages queued
Messages received
Network health
```

Advanced diagnostics can show:

```text
RSSI
radio channel
queue size
packet retries
forwarding count
firmware
```

---

# Node detail

A node page should expose:

```text
display name
node ID
last seen
reachable state
capabilities
battery where available
firmware
network membership
gateway/relay status
queued traffic
```

Actions might include:

```text
send message
read sensors
configure
remove/revoke
diagnostics
```

Do not display cryptographic secrets.

---

# Pairing/provisioning UI

Target a very low-friction flow.

Possible UI:

```text
Add node
  ↓
Tap NFC / scan nearby node
  ↓
Confirm node
  ↓
Provisioning…
  ↓
Node joined
```

Avoid multi-screen wizard sprawl.

Keep provisioning visually unified.

---

# States must be explicit

Never conflate:

```text
known
nearby
connected to phone
member of network
reachable through mesh
last seen
online
gateway
relay
```

These are distinct.

Do not show “connected” for a node merely because another relay saw it recently.

---

# Diagnostics capability

Create a proper technical diagnostics view.

Include:

```text
transport state
gateway state
known nodes
last receive
last send
inbound queue
outbound queue
mesh queue
dedupe stats
radio errors
protocol errors
firmware versions
```

Support tap-to-copy for IDs/diagnostic values consistent with MethodMesh rules.

Never expose keys.

---

# Field failure scenarios

Explicitly test/design for:

## Gateway disappears

Phone should reconnect to another available provisioned node if possible.

## Node reboots

Network membership should recover automatically.

## Phone reboots

Persistent queued messages and network state should recover.

## Duplicate relays

Destination should consume once.

## Partitioned network

Messages remain queued until expiry or reconnection.

## Two partitions merge

Duplicate suppression prevents replay storms.

## Full queue

Low-priority messages should not evict critical control traffic blindly.

## Corrupt packet

Drop safely.

## Wrong key/network

Reject safely without revealing useful cryptographic information.

## Stale node

UI must distinguish last-known state from current reachability.

## Power failure during persistence

Queue/state should remain consistent.

---

# Testing

Tests are mandatory on both Android and firmware where feasible.

## Android tests

Test:

- transport registration
- gateway reconnect
- inbound generic envelope mapping
- outbound mapping
- duplicate delivery
- persistence/restart
- malformed bridge frames
- unknown node
- provisioning state
- removal/revocation
- queue states

## Firmware unit/component tests

Test:

- frame encode/decode
- fragmentation/reassembly
- TTL decrement
- dedupe
- forwarding decision
- retry behaviour
- queue eviction
- expiry
- persistence recovery
- malformed frames
- wrong network
- authentication failure

## Simulated mesh test

Build a host-side or firmware test harness where practical.

Simulate:

```text
A → B → C
```

Then test:

```text
duplicate packet
B unavailable
C unavailable
partition
partition recovery
TTL exhaustion
fragment loss
retry
```

Do not require physical hardware for all protocol correctness tests.

---

# Hardware validation

Where hardware is available, validate at least:

```text
Android phone
   ↕
ESP gateway
   ↕ ESP-NOW
ESP relay
   ↕ ESP-NOW
ESP destination
```

Test:

- message delivery
- delayed delivery
- node reboot
- relay reboot
- phone reconnect
- duplicate relay paths
- range loss/recovery

If only limited hardware is available, document which parts were simulated.

---

# Performance expectations

Do not optimise for high-bandwidth networking.

Optimise for:

```text
robustness
low power
low cost
field simplicity
small messages
sensor observations
commands
human messages
delay tolerance
```

Bulk files are secondary.

Set realistic payload and queue limits.

---

# Safety and abuse resistance

External nodes are untrusted input.

Reject or bound:

- invalid lengths
- malformed fragments
- excessive fragment counts
- replay floods
- impossible TTL
- huge metadata
- repeated pairing requests
- queue exhaustion attempts

Rate-limit where needed.

Never permit remote arbitrary filesystem access or code execution.

---

# Documentation

Add module documentation covering:

- purpose
- architecture
- supported hardware
- firmware installation
- provisioning
- network creation/joining
- node roles
- message routing
- store-and-forward
- sensor integration
- security model
- failure semantics
- queue semantics
- ODK semantics
- troubleshooting
- protocol versions
- limitations

Include a diagram such as:

```text
┌──────────────┐
│ MethodMesh A │
└──────┬───────┘
       │ BLE
┌──────▼───────┐
│ ESP Gateway A│
└──────┬───────┘
       │ ESP-NOW
       ▼
┌──────────────┐
│ ESP Relay    │
└──────┬───────┘
       │ ESP-NOW
       ▼
┌──────────────┐
│ ESP Gateway B│
└──────┬───────┘
       │ BLE
┌──────▼───────┐
│ MethodMesh B │
└──────────────┘
```

---

# Several review/iteration passes are mandatory

Do not stop after first implementation.

## Review 1 — Architecture

Ask whether anything duplicates the core transport substrate.

Remove duplicate functionality.

## Review 2 — Protocol

Attack:

- IDs
- fragmentation
- TTL
- acknowledgements
- queueing
- retries
- dedupe
- expiry

Simplify or correct weak designs.

## Review 3 — Security

Assume:

- node stolen
- malicious packet sender
- replay
- rogue provisioning attempt
- relay node compromised
- old key retained

Correct obvious weaknesses.

## Review 4 — Field reality

Assume:

- no internet
- poor phone battery
- ESP powered from cheap battery
- intermittent radio
- users wearing gloves
- multiple teams
- nodes accidentally turned off
- nobody knows MAC addresses
- no laptop available

Remove workflows that are unrealistic.

## Review 5 — Android reality

Challenge:

- background restrictions
- BLE reconnect
- process death
- permissions
- notification requirements
- Bluetooth disabled
- app upgrade

Correct lifecycle issues.

## Review 6 — Congestion

Simulate or reason through multiple relays hearing every broadcast.

Ensure forwarding does not produce pathological packet storms.

## Review 7 — MethodMesh parity

Verify:

- dashboard is not the only entry point
- individual capabilities remain canonical
- presets work where applicable
- protocols work
- ODK integration follows MethodMesh semantics
- Commit lifecycle is not bypassed
- tap-to-copy rules are respected
- launch-origin completion is preserved
- state is persistent where appropriate

## Review 8 — Simplification

Delete unnecessary complexity.

The target is a reliable first-generation transport, not a research project in ad-hoc routing.

---

# First-release scope

The first complete version should prioritise:

```text
network creation
node provisioning
secure membership
BLE Android ↔ ESP bridge
ESP-NOW multi-hop forwarding
TTL
dedupe
basic acknowledgements
durable store-and-forward
generic MethodMesh envelope transport
human text messages
sensor observation transport
node status
diagnostics
```

Do not allow optional ambitions to prevent this vertical slice from working end-to-end.

---

# Explicit deferred features

Unless extremely straightforward after the above works, defer:

- sophisticated route discovery
- geographic routing
- LoRa
- Wi-Fi Direct
- internet federation
- very large file transfer
- mesh-wide firmware OTA
- complex PKI
- distributed consensus
- voice
- images
- arbitrary remote shell
- opportunistic internet gateways

Design interfaces so these are not blocked.

---

# Deliverables

Return the completed MethodMesh module and ESP firmware source required for the feature.

Follow repository conventions for location.

If MethodMesh treats firmware as module-owned support material, keep it there.

Do not return an entire unrelated application tree when only the module/associated firmware is requested.

Include:

- Android module code
- firmware
- protocol definitions
- tests
- docs
- sample/config files if required
- build instructions
- dependency/licence notices

---

# Build

Build/test both sides where possible.

Android:

```text
compile relevant Kotlin
run relevant tests
build module/app as repository allows
```

ESP:

```text
build supported ESP-IDF target(s)
run host/component tests
```

Do not leave knowingly broken generated code.

---

# Git workflow

Work on an appropriate feature branch.

Before committing:

- inspect diff
- remove generated junk
- verify secrets absent
- build/test
- inspect documentation
- inspect git status

Use clear commits.

Possible themes:

```text
esp-mesh: add MethodMesh ESP transport
esp-mesh: add ESP-NOW node firmware
esp-mesh: add secure provisioning and relay
esp-mesh: add messaging and sensor integration
```

Push the finished branch.

Do not create a release unless explicitly required.

---

# Completion report

Report:

- branch
- commit hash(es)
- Android files added/changed
- firmware files added/changed
- protocol version
- supported ESP targets
- third-party code/dependencies
- licence implications
- build outcome
- test outcome
- hardware validation performed
- simulated topology tested
- known limitations
- deferred work

Answer explicitly:

```text
Does the module use the generic core transport API?      YES
Does it duplicate durable core inbox/outbox logic?       NO
Is ESP-NOW-specific code confined outside core?          YES
Can a blank ESP32 operate as a relay/gateway?             YES
Can sensor nodes expose existing MethodMesh semantics?   YES
Can messages survive temporary partitions?               YES
Are duplicate forwarded messages suppressed?             YES
Can the Android app receive with Mesh UI closed?          YES
Can nodes be provisioned without typing keys/MACs?        YES
Does remote receipt bypass MethodMesh Commit semantics?   NO
```

The final result should behave like a **MethodMesh-native delay-tolerant field network**, not an ESP demo bolted onto the app.