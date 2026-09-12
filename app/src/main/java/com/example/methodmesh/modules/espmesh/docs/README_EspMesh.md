# ESP mesh persistent field transport

The ESP mesh module is a **Workbench transport subsystem**, not a screen-scoped messaging capability. Once enabled, the Android service and selected ESP gateway continue listening, retrying and reconciling in the background whether the gateway is USB-powered, battery-powered in a rucksack, or otherwise independent of the phone.

## Canonical path

```text
MethodMesh producer
    ↓
phone E2E encryption (AES-256-GCM)
    ↓
encrypted durable Android outbox
    ↓ BLE
local ESP persistent LittleFS spool
    ↓ ESP-NOW
remote ESP persistent LittleFS spool
    ↓ BLE
remote encrypted Android inbox
    ↓ authenticate/decrypt
MethodMesh consumer
```

The ESPs never receive the E2E group key. They persist and forward `EspMeshSecureWire` ciphertext plus the minimum routing metadata required for store-and-forward delivery. The ESP transport/network key is a separate credential used for radio-network admission and radio-packet authentication.

## Background lifecycle

Selecting a gateway enables `EspMeshGatewayService`, a persistent foreground service. It starts with the `connectedDevice` type only; while background speech is actually being received/played it dynamically adds `mediaPlayback`, then drops that additional type when playback ends. The service is not tied to the gateway or test UI. It reconnects directly to the configured BLE device with bounded backoff and resumes after normal process/service restart and device boot where Android permits. USB attachment is not a lifecycle requirement or presence signal. Microphone capture is never a standing background-service function: PTT begins only from the visible press-and-hold control.

Manual BLE scanning is used only to select/change a gateway. The persistent service does not run a continuous scan loop. A configured gateway that goes out of range leaves encrypted queues intact; when it returns to BLE range the provider reconnects and runs the v2 HELLO/SYNC reconciliation flow automatically.

Force-stopping the Android app remains an operating-system override: Android does not permit an app to resurrect itself after a user force-stop until the user launches it again.

## Durable delivery semantics

Delivery is deliberately **at least once + message-ID deduplication**, not an impossible exactly-once promise.

States have distinct meanings:

1. **Phone queued** — ciphertext is durable in the sending phone's encrypted mesh outbox.
2. **LOCAL_STORED** — the local ESP has durably appended the ciphertext to LittleFS.
3. **REMOTE_STORED** — at least one remote ESP has durably stored the ciphertext.
4. **PHONE_STORED** — the receiving phone has durably recorded the encrypted wire and the remote ESP may release its phone-bound spool copy.
5. **E2E delivery ACK** — the receiving phone has authenticated/decrypted the message and emitted an authenticated encrypted ACK. Only this completes the original sender's authoritative outbox record.

The phone retains the authoritative outbound record until the authenticated E2E delivery ACK. Remote-stored records are retried at a slower interval so loss of a remote ESP cannot create permanent silent loss. Duplicate messages and ACKs are safe.

## ESP persistent spool

Firmware `methodmesh-espmesh-0.4.0` uses an atomic bounded LittleFS JSONL spool. Radio-bound and phone-bound ciphertext survive BLE absence and ESP power cycles. The firmware refuses new storage when the bounded spool is full; it does **not** silently discard an undelivered record to make space. If persisted spool JSON becomes unreadable, the node preserves the affected file and reports `esp_spool_unreadable` rather than replacing it with an empty queue.

An ESP continues ESP-NOW store-and-forward operation while its phone is absent. A remote phone can therefore leave its ESP powered in a field location, return later, reconnect over BLE and automatically drain the backlog.

The ESP has no trustworthy wall clock by default, so message expiration remains phone-authoritative. The ESP stores the authenticated visible expiry metadata but does not discard records based on an uninitialised RTC.

## End-to-end encryption

The phone E2E group key is a random 256-bit key. On Android it is wrapped at rest by a non-exportable Android Keystore AES key. The explicit **Copy current key** action exists only to enrol another trusted phone in the same field group.

Payload encryption uses AES-256-GCM. The complete canonical `MethodMeshTransportEnvelope` is ciphertext. Visible routing fields (`message_id`, key ID, originating phone transport identity, source, destination, timestamps and hop budget) are authenticated as AEAD associated data, so an outsider cannot silently rewrite them without decryption failing. The originating phone identity gives delivery ACKs a transport-level return route even when the encrypted application envelope uses another source endpoint.

The E2E group key is **never** included in BLE `CONFIG`, firmware configuration, the ESP spool or ESP-NOW packets. Compromising an ESP therefore exposes ciphertext and routing metadata, not message plaintext. A shared group key protects against outsiders/interception; it does not protect one authorised group member from another authorised group member. Pairwise/member-specific keys are a separate future trust model.


## Live walkie-talkie traffic

ESP mesh also carries **ephemeral live voice** as a separate priority traffic class. Live voice is not a `MethodMeshTransportEnvelope`, is never added to the Android durable inbox/outbox, and is never written to the ESP LittleFS spool. If the destination phone is not listening at the time of broadcast, the speech is intentionally missed rather than replayed later. Durable data delivery continues independently.

The global in-app push-to-talk control is press-and-hold: microphone capture begins only while the operator is visibly holding **TALK** and stops on release. Received broadcasts can continue through the persistent mesh service while other MethodMesh screens are open or the app is backgrounded. The persistent notification exposes a **Mute voice / Listen** action. No received or transmitted voice is automatically recorded.

Phones encode 20 ms, 8 kHz mono speech frames as G.711 mu-law (160 bytes). Each frame is AES-256-GCM encrypted using a domain-separated live-voice key derived from the field-group E2E key. A random talk-session ID plus monotonic sequence forms the GCM nonce. Channel and source tags are keyed pseudonymous tags and are authenticated as E2E metadata. ESPs never possess the voice key and therefore see only opaque ciphertext.

On ESP-NOW, one live-voice packet is kept below the 250-byte radio ceiling and is authenticated again with the separate transport/network key. Live voice is best-effort: there are no retransmissions of stale audio frames. A small phone-side jitter buffer conceals isolated losses with silence. A short radio TTL permits bounded relay while duplicate suppression prevents immediate rebroadcast loops.

The ESP keeps its radio active and continues relaying live-voice packets even when its associated phone has **Listen** turned off. `VOICE_LISTEN` controls only whether that gateway forwards live voice over BLE to its phone. Thus muting a handset does not remove its battery-powered ESP from the field mesh.

Live traffic has priority over durable backlog. Android gives reliable control traffic first priority, live voice next, and durable backlog last. The live BLE queue is short and bounded, so a slow BLE link drops old unsent speech rather than turning it into delayed playback. PTT also requires an MTU large enough for each live bridge frame to fit in one ATT write; it fails visibly instead of switching to high-latency BLE fragmentation. Firmware temporarily defers durable ESP-NOW retries while voice is active. Durable backlog then resumes automatically.

Logical voice channels are phone-side subscriptions (default `field-group`), not Wi-Fi/ESP-NOW channels. The channel tag is derived from the E2E key so an unauthorised radio observer cannot directly read the channel name. The first implementation uses a soft distributed floor: a phone that is currently receiving a talk burst treats the channel as busy and refuses a new PTT press until the burst ends or times out.

## Radio authentication and fragmentation

ESP-NOW radio packets are authenticated with the independently provisioned transport/network key. Full ciphertext wires are fragmented to remain within the current MicroPython ESP-NOW maximum frame size and reassembled before durable remote storage. The current ESP32-C3/MicroPython implementation caps one encrypted mesh wire at 32 KiB so an otherwise core-valid oversized MethodMesh envelope fails before durable mesh acceptance rather than exhausting node RAM during fragmentation/reassembly. Integrity failure or incomplete reassembly is rejected rather than truncated.

No user-entered radio MAC addresses are required for the normal field workflow. Provisioned nodes use ESP-NOW broadcast when no explicit peer list exists. Direct phone destinations are visible routing metadata; a node with an associated phone identity ignores a phone-specific packet addressed to a different phone.

## BLE bridge protocol v2

`methodmesh.gateway` version 2 uses explicit typed frames:

`HELLO`, `HELLO_ACK`, `CONFIG`, `CONFIG_ACK`, `DATA`, `LOCAL_STORED`, `REMOTE_STORED`, `PHONE_STORED`, `SYNC_REQUEST`, `SYNC_ACK`, `VOICE_LISTEN`, `VOICE_LISTEN_ACK`, `LIVE_VOICE`, `LIVE_VOICE_RX`, `RADIO_ERROR`, `ERROR`.

Large logical bridge frames are transparently packetised using `methodmesh.blefrag` v1. Android serialises GATT writes through a single write queue; the firmware reassembles fragmented uplinks. Firmware fragments large downlink notifications and Android reassembles them before gateway-frame parsing.

`HELLO` identifies the local phone and protocol version. `SYNC_REQUEST` causes the ESP to report spool counts and re-offer all phone-bound durable records. At the same time Android retries due encrypted-outbox records. Reconnection therefore drains both sides without a user Retry action.

## Workbench surfaces

**ESP mesh transport** is the control plane: gateway selection, persistent-service state, network provisioning, E2E group-key enrolment, queue counts, pause/resume and rejected-frame diagnostics.

**ESP mesh transport diagnostics** shows only non-secret operational state: phone/gateway identity, E2E key ID, encrypted queue counts and ESP spool counts. It deliberately omits keys and plaintext.

**ESP mesh transport test** retains method ID `espmesh.message.send` for backwards compatibility but is only a Workbench test harness. Closing it has no effect on transport operation or receipt.

## Firmware installation

The ESP-NOW node remains a complete 4 MB ESP32-C3 image installed through **Workbench → ESP32 sensor framework → Install ESP32 image**. It is not provisioned through the BLE sensor-node provisioner. The verified ESP32-C3 ROM writer/MD5 installation contract is unchanged.

## Diagnostics

Rejected BLE frames retain the characteristic UUID, byte length, UTF-8 view, raw hex and exact parser exception and are logged under `MethodMeshEspMesh`. Diagnostics must never log E2E group keys, ESP network keys or decrypted payload plaintext.

## Current integration boundary

The core transport runtime now treats a provider's durable acceptance as a
separate state from link transmission and destination delivery. This provider
declares its traffic classes, frame/object limits, fragmentation and
store-and-forward support through `TransportCapabilities`; callers do not need
to know that the current bearer is BLE plus ESP-NOW. The encrypted provider owns
its ciphertext journal, so the generic runtime never writes a second plaintext
copy and never falls back to the generic journal when an explicitly selected
secure provider is unavailable.

The shared shell receives this module's walkie-talkie control through the
module overlay contract. ESP-specific placement and visibility stay in this
module; the shell does not contain an ESP module ID or an ESP-specific overlay
call. The same contract leaves room for future network status, handover and
diagnostics controls without a new central UI special case.

Queue-full and corrupt-journal conditions fail closed and remain visible to the
provider. A provider start/stop transition is serialized, late registration is
safe, and a consumer failure is retried by a secure provider instead of being
reported as successful dispatch. These are foundations for future heterogeneous
links and route handover; automatic multi-link routing and topology display are
not yet implemented.
