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

Firmware `methodmesh-espmesh-0.4.1` uses an atomic bounded LittleFS JSONL spool. Radio-bound and phone-bound ciphertext survive BLE absence and ESP power cycles. The firmware refuses new storage when the bounded spool is full; it does **not** silently discard an undelivered record to make space. If persisted spool JSON becomes unreadable, the node preserves the affected file and reports `esp_spool_unreadable` rather than replacing it with an empty queue.

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

## Bench framing and provisioning correction — 0.4.1

Downlink packets are capped at 180 bytes, including the complete typed
`methodmesh.blefrag` v1 JSON/base64 wrapper (54 raw bytes per fragment).
The old 460-byte shortcut could truncate a CONFIG/HELLO/SYNC response at 253
bytes on an Android MTU of 256. MTU IRQ data, or the Android HELLO `att_payload`
when that IRQ is unavailable, gates notifications; no downlink is sent before
a payload budget is known. The Android provider still requires at least 180
ATT bytes. Default MTU 23 cannot carry this typed bridge and is rejected rather
than truncating packets. Transfer IDs are sequential within a node boot; Android
clears partial reassembly on connection replacement/loss. Legacy and modern
Android notification callbacks share the same reassembler.

Selecting **Use** only selects/connects a gateway. A setup completion requires a
parsed CONFIG_ACK matching the pending request ID and exact requested network,
with `provisioned=true`, identity, firmware and nonnegative spool counts. HELLO,
SYNC, stale ACKs, errors, disconnection and timeout never complete provisioning.
A timeout leaves the configuration unconfirmed; the ESP may have saved it, so a
retry can require the node token. The ready panel shows node/network/firmware,
spool counts and the actual foreground-service running state. Connected gateways
may disappear from scans while MethodMesh keeps their BLE connection.

Workbench closeout uses the existing dashboard host callbacks, retaining the
Workbench destination/module expansion; the host close button says **Back to
Workbench**. External/protocol return callbacks remain unchanged. `espmesh.message.send`
remains the test harness; durable radio, encrypted spools, E2E acknowledgement,
voice admission and mute/relay semantics remain on the existing provider.

## Bench radio correction — 0.4.2

Firmware 0.4.2 explicitly places the disconnected station interface and every
ESP-NOW node on Wi-Fi channel 6 before ESP-NOW starts. This removes reliance on
the board's prior/default Wi-Fi channel, which could leave authenticated radio
messages permanently queued even when nodes had the same network name and key.

Gateway telemetry now includes the active radio channel and a short SHA-256 ID
of the ESP transport key. The key itself remains hidden. Both phones can compare
these values to distinguish a channel problem from a key-copy/provisioning
problem. Android accepts older 0.4.1 responses without these optional fields.

The provisioning screen can create and scan a typed `methodmesh.mesh.join` v1
QR enrollment bundle. It carries the network ID, ESP transport key and phone E2E
group key so a second phone can load the complete join configuration in one scan,
then provision its selected fresh ESP through the existing CONFIG/CONFIG_ACK path.
The QR is displayed only on explicit request and is labelled as a secret enrollment
credential. Both exportable mesh keys are wrapped at rest by Android Keystore; QR
payloads are not logged or written to the ESP spool.

The Workbench provisioning surface is a four-stage guided flow: connect a node,
create a named mesh or join by QR, show the join QR, then send/receive test packets.
Creating a mesh generates the ESP transport and E2E group keys without exposing
key fields in the normal path. When one nearby node is found it is selected
automatically; multiple candidates remain an explicit choice. The final test uses
the existing `espmesh.message.send` envelope/runtime path and reports radio
activity, phone backlog, ESP radio spool and received test text in place.

## Fragmented radio burst reliability — 0.4.3

Firmware 0.4.3 increases the ESP-NOW receive buffer before activating the radio
and adds a short delay between durable fragments. This prevents a multi-fragment
encrypted message from overflowing the receiver while MicroPython reassembles it.
Gateway telemetry reports received radio packets and the latest send error.

The guided QR and test stages also unlock for an already configured node when
its live network and ESP key identity match the credentials saved on the phone.
This operational check does not fabricate a provisioning result: Workbench
completion still requires a validated CONFIG_ACK from the current setup request.

## Radio startup fallback — 0.4.4

Bench telemetry from 0.4.3 showed `radio channel unknown` on both ESP32-C3 nodes:
the requested 16 KB receive buffer could not be allocated by the bundled
MicroPython runtime, and the original all-or-nothing startup path disabled
ESP-NOW. Firmware 0.4.4 tries progressively smaller useful buffers down to the
runtime default size, keeping the radio active whenever any supported allocation
succeeds. Durable fragments are paced by 12 ms. Telemetry now exposes the chosen
buffer and the exact startup error instead of presenting a silent unknown channel.

## ESP32-C3 coexistence runtime — 0.4.5

USB diagnosis of a failing 0.4.4 node reported `WiFi Out of Memory`. The bundled
MicroPython 1.28 / ESP-IDF 5.5 runtime could not initialize Wi-Fi after the BLE
gateway had reserved the ESP32-C3 radio heap. Firmware 0.4.5 uses the official
MicroPython 1.29 ESP32-C3 runtime verified on the physical node, initializes
Wi-Fi/ESP-NOW and BLE from `boot.py` before the larger `main.py` is compiled,
and no longer tears down/recreates ESP-NOW during
CONFIG. A clean USB probe confirmed channel 6, ESP-NOW, BLE and the MethodMesh
GATT service active concurrently before this runtime was adopted. The receive
buffer remains at MicroPython's documented 528-byte default to preserve enough
ESP32-C3 controller memory for BLE; 12 ms fragment pacing prevents burst loss.

## BLE discovery and handshake recovery — 0.4.6

Firmware 0.4.6 advertises the mesh service every 100 ms and paces fragmented
BLE notifications by 8 ms so HELLO/SYNC telemetry is not lost as a burst. The
Android provider uses a low-latency service-UUID scan, requests the observed-safe
256-byte MTU, starts service discovery if the MTU callback stalls, validates
notification setup, retries HELLO three times, and reconnects automatically if
the protocol handshake does not complete. The guided screen exposes a discovered
node immediately and auto-selects a sole candidate after 750 ms rather than
waiting for the full scan window.
Manual keys, provisioning-token recovery and protocol diagnostics are contained
under **Advanced recovery and diagnostics**.

The installed runtime is `app/src/main/assets/firmware/esp32c3_espnow_mesh/main.py`.
The older `firmware/esp32c3_espmesh` reference is not the bundled image source.
Rebuild using the repository firmware virtual environment and the installer-owned
`build_espnow_mesh_image.py`. It mounts LittleFS, replaces runtime/codec files,
remounts and verifies every file and the unchanged pre-VFS bytes. The flash image
remains 4 MB; AHT20/LD2410C images and the ROM writer are unchanged.

Host regression: `python3 -m unittest discover -s tools/tests -p test_espmesh_ble.py -v`.
Android regression: `:app:testDebugUnitTest --tests 'com.example.methodmesh.modules.espmesh.*'`.
Hardware validation still requires two reflashed C3s: provision each, verify the
MTU-256 status/CONFIG_ACK panel, close to Workbench, send via the existing harness,
queue while a peer/phone is offline, power-cycle, reconnect and confirm delivery.
Test live voice with a voice-capable MTU, mute/relay and resumed durable backlog.
