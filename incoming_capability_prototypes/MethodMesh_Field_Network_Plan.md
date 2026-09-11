# MethodMesh Field Network — Technical Plan

**Status:** Design handover / implementation plan
**Purpose:** Define a low-cost, offline-first field network for MethodMesh using ESP-NOW as the initial backbone transport and BLE as the primary Android access transport.

## 1. Vision

MethodMesh Field Network is a local digital infrastructure layer intended to operate without internet, routers, servers, or permanently present phones.

The central design principle is:

> **Nodes belong to the network; capabilities have permissions; users temporarily consume routes.**

A handset does not permanently pair with or own an ESP node. Any nearby authorised node capable of handset access may temporarily become that handset's gateway. As the user moves, access may hand over between nodes without changing application semantics.

The target environment includes field research, emergency response, refugee/displacement settings, clinics, camps, remote communities, expeditions, and other places where conventional network infrastructure is absent, fragile, expensive, or inappropriate.

The economic model is deliberately simple: inexpensive ESP-class devices can simultaneously be endpoints and infrastructure. A chip attached to a water tank, e-ink display, sensor, dog collar, clinic wall, vehicle, or dedicated relay can contribute to network coverage while performing its primary function.

## 2. Architectural principles

1. **No internet dependency.** Core network functions must work completely offline.
2. **No central server dependency.** A server or internet gateway may enhance the network but must not define it.
3. **No permanent handset-to-node ownership.** Handset access is an ephemeral service.
4. **Transport independence above the network layer.** ESP-NOW is the first backbone transport, not the application contract.
5. **BLE for Android access.** This leaves handset Wi-Fi available for internet, institutional Wi-Fi, hotspots, captive portals, or other purposes.
6. **Opportunistic infrastructure.** Any suitably capable node may relay traffic or offer handset access, subject to policy and resources.
7. **Store and forward.** Temporary partitions are normal rather than exceptional.
8. **Capability-based design.** Sensors, actuators, displays, voice, messaging, location and telemetry use a common network envelope.
9. **Security independent of routing.** A node being permitted to carry a packet does not imply permission to read its payload or invoke protected capabilities.
10. **Graceful degradation.** Loss of internet, gateway, GPS, particular relays, or individual phones should reduce functionality rather than collapse the system.

## 3. Layer model

```text
+----------------------------------------------------+
| Applications                                       |
| messaging | voice | tracking | telemetry | ODK     |
| emergency | counters | automation | protocols      |
+----------------------------------------------------+
| Capabilities                                       |
| sensor | actuator | display | location | message   |
| voice | storage | time | identity | telemetry      |
+----------------------------------------------------+
| MethodMesh Field Network                           |
| identity | discovery | routing | ACK | TTL         |
| deduplication | store/forward | auth | permissions  |
+----------------------------------------------------+
| Transports                                         |
| ESP-NOW backbone | BLE handset access | future ... |
+----------------------------------------------------+
| Hardware                                           |
| ESP nodes | Android | sensors | actuators | displays|
+----------------------------------------------------+
```

Future transports could include LoRa, wired serial, IP, satellite-connected gateways or other radios without requiring application-level redesign.

## 4. Node roles

Roles describe services rather than mutually exclusive hardware classes. One physical node may advertise several roles.

### 4.1 Endpoint

Provides or consumes a capability. Examples include sensors, buttons, counters, RFID/NFC readers, displays and actuators.

### 4.2 Relay

Forwards Field Network packets. Relays are not associated with a particular handset or user. They implement TTL/hop limits, deduplication, routing policy and resource limits.

### 4.3 Handset gateway

Offers BLE access between Android and the Field Network. A gateway may simultaneously be a relay, sensor, display or other endpoint.

### 4.4 Store node

Provides durable store-and-forward queues. It may retain messages, telemetry, configuration or other permitted payloads until a destination or suitable gateway becomes reachable.

### 4.5 Beacon / anchor

Has a known identity and optionally a known location, authoritative clock or other fixed metadata. Anchors can contribute to coarse local positioning while also acting as relays.

### 4.6 External gateway

Bridges the Field Network to another transport or service, for example internet/IP, LoRa, USB/serial, MQTT or satellite connectivity.

## 5. Handset access model

Android handsets normally enter the network over BLE.

```text
Android -- BLE -- nearby gateway -- ESP-NOW -- Field Network
```

There must be no normal user-facing pairing ceremony. MethodMesh should discover Field Network access advertisements, authenticate where necessary, select a gateway, exchange traffic and move to another gateway when appropriate.

Three access patterns should coexist:

- **Personal access:** an optional pocket/wearable ESP gateway provides predictable access where infrastructure is sparse.
- **Opportunistic access:** MethodMesh temporarily uses any nearby authorised gateway-capable node.
- **Infrastructure access:** fixed nodes deliberately provide coverage at clinics, water points, shelters, distribution sites and similar locations.

### 5.1 BLE traffic modes

**Advertising** should be used for discovery and small public/network-state announcements where possible.

**Burst sessions** should be the default for messaging, queue synchronisation, sensor uploads and similar low-duty traffic:

```text
discover -> authenticate -> upload queue -> download queue -> disconnect
```

**Live sessions** maintain a BLE connection for latency-sensitive workloads such as walkie-talkie audio or live interactive telemetry.

Implementation should initially target approximately **three reliable simultaneous sustained BLE handset sessions per ESP gateway**, while treating higher controller limits as implementation-specific rather than a network guarantee. Burst access allows a gateway to service substantially more intermittent users over time.

### 5.2 Gateway selection

Do not select solely by RSSI. Gateway advertisements should expose enough state to estimate suitability, potentially including:

```text
gateway_id
network_id
rssi
active_ble_sessions
ble_capacity
queue_depth
power_state / battery
backbone_quality
capabilities
gateway_priority
```

The handset computes a gateway score and should avoid a strong but saturated gateway when a slightly weaker uncongested gateway is available.

Handover must not change user/application identity. The gateway is a route, not the user's network identity.

## 6. Core packet envelope

Exact binary encoding is an implementation decision, but all Field Network traffic should map to a stable logical envelope.

```text
protocol_version
network_id
message_id
source_id
destination / group / broadcast
source_type
capability
payload_type
payload
timestamp
time_source
sequence
priority
ttl
hop_count
ack_policy
security_metadata
```

Optional transport observations such as RSSI should not be confused with application payload data.

Message IDs plus source identity must permit robust deduplication across multipath forwarding.

## 7. Capability namespace

Capabilities should be extensible and machine-readable. Illustrative names:

```text
temperature.read
humidity.read
button.event
counter.increment
location.beacon
location.report
message.text
voice.frame
relay.status
battery.read
actuator.switch
actuator.level
display.update
alarm.raise
time.sync
telemetry.report
```

Applications should depend on capability contracts, not specific boards or sensor models.

## 8. Sensor framework

Sensor nodes should expose common metadata alongside readings:

```text
sensor_id
capability
value
unit
timestamp
time_source
quality/status
calibration metadata (where relevant)
battery/power state
```

Candidate classes include temperature, humidity, pressure, lux, sound level, CO2, particulates, rainfall, soil moisture, water level, acceleration/vibration and magnetic contacts.

The same framework should support equipment telemetry such as cold-chain temperature, door state, mains availability, generator state, battery voltage, pump state, tank level, fuel level and equipment run detection.

## 9. Actuator framework

Actuators make the network bidirectional: **sense -> communicate -> decide -> act**.

Candidate capabilities include relay switching, LEDs/beacons, buzzers, vibration, servos, motors, pumps, valves, latches and displays.

Actuation requires stronger permission and safety semantics than passive telemetry. The protocol should distinguish at minimum:

- observation/read permission;
- command permission;
- command acknowledgement;
- requested versus confirmed physical state;
- timeout/failsafe state;
- locally enforced safety rules.

A routing node must never gain actuator authority merely because it forwarded the command.

## 10. E-ink / persistent display nodes

ESP + e-paper nodes should be first-class display endpoints. Their persistent, near-zero-power static display makes them suitable for public information infrastructure.

Examples include clinic status and queues, water-point status, distribution schedules, emergency instructions, navigation, local announcements and missing-person information.

Display nodes may simultaneously provide BLE handset access and ESP-NOW relay service.

Updates should be versioned and acknowledged so a controller can distinguish *message delivered to node* from *display successfully updated*.

## 11. Messaging and walkie-talkie integration

The existing ESP-NOW messaging work should become an application/capability over Field Network rather than define the Field Network architecture.

Messaging should support addressed, group and broadcast delivery, acknowledgements, priority, expiry and store-and-forward.

Walkie-talkie traffic should use the same identities/routing framework but requires distinct QoS treatment because audio is latency-sensitive and relatively high duty-cycle. Live BLE handset sessions should therefore be treated separately from burst messaging sessions.

The Field Network implementation should consume the messaging system's proven framing/routing components where compatible rather than duplicate them.

## 12. Store and forward

Network partitions are expected. Suitable nodes may retain encrypted packets until a route becomes available.

Example:

```text
sensor -> relay -> village/store node
                    [offline for 36 h]
                         |
                    visiting handset
                         |
                    external sync
```

Store nodes should implement bounded queues, expiry, priority, duplicate detection and clear storage-pressure behaviour.

Public bulletin/mailbox functionality can build on the same mechanism for local notices, tasks, protocol/configuration updates and short messages.

## 13. Local positioning

Fixed Field Network nodes can optionally be registered as location anchors with known coordinates or site-relative positions.

Mobile nodes emit heartbeats. Multiple anchors observe the same transmission and report RSSI and timestamp. The location service estimates the mobile node's position.

RSSI must **not** be treated as precise physical distance. Buildings, tents, people, vehicles, antenna orientation and multipath cause substantial variation.

Positioning should therefore be staged:

1. **Nearest/strongest anchor** — coarse zone detection.
2. **Weighted centroid** — combine observations from multiple anchors.
3. **Site fingerprinting** — record characteristic RSSI vectors at known locations and estimate position by similarity.
4. **Tracking filter** — combine successive observations and motion constraints to suppress implausible jumps; optional IMU information may improve this.

The UI may then present an operations map showing people, equipment, vehicles or animals as moving nodes. Location estimates must expose uncertainty/quality rather than imply GPS precision.

Anchors should preferably also provide useful relay/network infrastructure rather than constitute a separate positioning installation.

## 14. Mobile tags and dog-collar demonstrator

A collar/tag is a strong reference implementation because it exercises identity, power management, telemetry, tracking, relaying and alerting.

Possible capabilities:

```text
asset.location
asset.last_seen
asset.motion
asset.stationary_for
asset.battery
asset.temperature
asset.escape_alarm
```

Within a covered site, anchor-derived location can avoid continuous GNSS use. A GNSS-equipped tag may wake GNSS when leaving known coverage, periodically acquire coordinates, and forward them when network contact becomes available.

The same design generalises to equipment, specimen carriers, vehicles, livestock and authorised personnel safety tags. Tracking of people must be explicit, permissioned and auditable; the infrastructure should not silently create a population-surveillance system.

## 15. Presence and proximity

Nodes may advertise signed/authorised heartbeats to support coarse presence functions:

- equipment present/missing;
- station occupancy;
- team separation alerts;
- specimen/container proximity;
- required-kit checks;
- last-seen state.

Presence is a network observation and should retain timestamp and observing-node provenance.

## 16. Distributed counters and physical input

Field Network should support inexpensive human-input endpoints such as push buttons, rotary encoders, foot pedals, tally buttons, yes/no/unknown controls, keypads, barcode readers and RFID/NFC readers.

Multiple nodes may contribute events to a common MethodMesh capability, e.g. distributed gate counts, patient flow, queue events, wildlife observations or sports/event scoring.

Event identity and deduplication are essential: retransmission must never cause a physical button press to be counted twice.

## 17. Time

Nodes need an explicit time model because cheap devices drift and some may reboot without reliable wall-clock time.

Potential authoritative sources include GNSS, Android, internet-connected gateway and RTC-equipped anchor. Time synchronisation messages should retain source and quality.

Application records should distinguish device observation time from network receipt/synchronisation time where this matters.

## 18. Security and trust model

The network is intentionally cooperative at the routing layer, but cooperation must not imply trust at the application layer.

Design requirements include:

- network membership/authentication;
- stable node identity;
- end-to-end protection for private payloads where appropriate;
- capability-level authorisation;
- replay protection;
- message integrity;
- key rotation/revocation strategy;
- lost/stolen node handling;
- public versus restricted traffic classes;
- auditable actuator commands;
- denial-of-service/resource exhaustion controls.

A node may be allowed to forward ciphertext it cannot decrypt.

High-sensitivity functions such as personal tracking, clinical data and actuator control must not rely solely on possession of the shared Field Network transport key.

## 19. Routing requirements

The first implementation does not need an academically perfect mesh-routing protocol. It does need predictable behaviour under loops, duplicate paths and intermittent nodes.

Minimum mechanisms:

- unique message IDs;
- TTL/hop limit;
- recent-message deduplication cache;
- broadcast/group/unicast semantics;
- acknowledgements where requested;
- retry limits/backoff;
- priority classes;
- queue limits;
- basic route/path quality observations;
- loop resistance;
- store-and-forward integration.

Routing policy should later be able to consider power state, congestion, link quality and node role.

## 20. Power model

Nodes have different obligations depending on power source. A mains/solar relay may advertise high forwarding availability. A battery collar or sensor may sleep aggressively and refuse general relay duty.

Suggested power classes:

```text
INFRASTRUCTURE   always-on / forwarding preferred
DUTY_CYCLED      periodic wake / bounded forwarding
ENDPOINT_LOWPOWER minimal radio availability
CRITICAL_BATTERY no opportunistic forwarding
```

Routing and gateway selection must respect these states.

## 21. Emergency mode

Field Network naturally supports an offline emergency profile including SOS, medical assistance, evacuation messages, all-stations broadcast, last-known position, welfare checks, team status, acknowledgements and emergency beaconing.

Emergency priority must not mean unrestricted bypass of authentication or actuator safety controls.

## 22. MethodMesh integration

Field Network should be a **first-class MethodMesh subsystem**, not merely an ESP-NOW capability module.

Individual MethodMesh modules should consume network capabilities through stable contracts. Examples:

```text
ODK <- sensor observation
ODK <- physical input
Protocol -> actuator
Counter <- remote event
Timer -> remote buzzer/display
Emergency <- SOS/location
GPS Navigator <- anchor/tag position
Telemetry -> dashboard
Messaging/voice -> Field Network transport
```

This preserves MethodMesh's requirement that capabilities remain usable directly, through presets/protocols and through ODK where appropriate.

## 23. User experience

The normal user should not manage radios, MAC addresses or peer lists.

A Field Network status surface should look more like network status than Bluetooth settings, for example:

```text
FIELD NETWORK
Online
Nearby nodes: 7
Current access: good
Backbone path: 2 hops
Internet gateway: unavailable
Queued outgoing: 0
```

Advanced diagnostics may expose current gateway, RSSI, routes, queues and neighbouring nodes in Workbench/developer surfaces.

There should be no ordinary **PAIR** or **CONNECT** workflow for opportunistic gateways.

## 24. Implementation sequence after messaging handover

### Phase 1 — extract the network substrate

Use the completed ESP-NOW messaging implementation as evidence. Identify and stabilise reusable primitives: node identity, packet framing, ESP-NOW send/receive, acknowledgements, deduplication, TTL, discovery and security assumptions.

Do not rewrite working messaging solely to fit this document.

### Phase 2 — Field Network core

Create the transport-independent Field Network envelope, capability registry, node-role advertisement, routing rules, queueing and diagnostics.

Demonstrate ESP-to-ESP multi-hop traffic with relay nodes that have no handset ownership.

### Phase 3 — BLE handset gateway

Implement gateway advertisements, ephemeral Android discovery/authentication, burst exchange and handover. Then add sustained sessions required by voice.

Test multiple Android devices and gateway load selection.

### Phase 4 — generic sensor + telemetry

Implement one simple environmental sensor and one equipment telemetry source using the common capability schema. Confirm routing, store-forward, Android presentation and ODK/MethodMesh consumption.

### Phase 5 — actuator

Implement a low-risk demonstrator such as LED/buzzer/relay with explicit command permissions, acknowledgements and confirmed state.

### Phase 6 — e-ink node

Implement persistent display updates, versioning, acknowledgements and optional relay/BLE-gateway roles.

### Phase 7 — location anchors

Implement anchor registration, multi-anchor RSSI observation, strongest-anchor and weighted-centroid estimates. Add uncertainty/quality reporting before attempting fingerprinting or tracking filters.

### Phase 8 — mobile tag / collar

Build the battery-powered reference tag. Add motion, battery and last-seen telemetry; then optional GNSS and geofence/escape alerts.

### Phase 9 — store-and-forward and external gateways

Harden partition behaviour and introduce bridging to IP/other transports without altering application contracts.

## 25. Acceptance demonstrations

The architecture should eventually pass these concrete demonstrations:

1. Two phones exchange messages without internet through Field Network nodes.
2. A phone walks between gateway nodes and messaging continues without manual reconnection.
3. A relay with no associated phone extends network reach.
4. Several phones share gateway infrastructure without permanent pairing.
5. A sensor observation traverses multiple nodes and appears in MethodMesh.
6. An observation is retained during a partition and delivered later.
7. An authorised actuator command traverses untrusted relays and produces an acknowledged state change.
8. An e-ink node receives and persists a remote display update.
9. Multiple anchors produce a coarse position and uncertainty for a mobile tag.
10. A collar/tag disappears from local coverage and generates a last-seen/escape event.
11. Loss of internet does not interrupt local network operation.
12. Loss of any single ordinary relay does not require re-pairing or manual network repair.
13. An unauthorised handset can neither read protected traffic nor invoke protected actuators despite being physically in radio range.

## 26. Key decisions already made

- Name/concept: **MethodMesh Field Network**.
- ESP-NOW is the initial field backbone.
- BLE is preferred for Android-to-gateway access so handset Wi-Fi remains available for other uses.
- Phones do not require permanently dedicated ESP gateways.
- Gateway relationships are ephemeral and fluid.
- Nodes are infrastructure participants rather than accessories owned by individual phones.
- Relay-only nodes are valid first-class devices.
- Sensor and telemetry support should use generic capability contracts.
- Actuators are first-class capabilities, not an afterthought.
- E-ink displays are persistent network endpoints and may also contribute gateway/relay service.
- Store-and-forward is fundamental.
- Fixed network nodes may double as coarse positioning anchors.
- RSSI positioning must expose uncertainty and must not pretend to provide GPS-like precision.
- The completed messaging system should be reused as the starting implementation substrate.

## 27. Handover instruction

When the ESP-NOW messaging workflow is complete, review its actual implementation before changing architecture. Preserve working identifiers, packet contracts and tested behaviour unless they conflict materially with the Field Network requirements.

Treat this plan as the target architecture. The first task is to map the existing messaging implementation onto it, identifying:

1. components already suitable for Field Network core;
2. messaging-specific components that should remain application-level;
3. assumptions of permanent peer ownership/pairing that must be removed;
4. security primitives that can be retained;
5. changes required for BLE ephemeral handset gateways;
6. the smallest migration that establishes the Field Network substrate without destabilising working messaging.

The implementation should then proceed incrementally through the phases above, with working demonstrations at each boundary.
