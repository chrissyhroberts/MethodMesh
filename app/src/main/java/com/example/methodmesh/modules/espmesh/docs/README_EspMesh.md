# ESP mesh field network

The ESP mesh module is the MethodMesh-owned integration point for a delay-tolerant field network. It uses the generic core transport substrate for envelopes, durable queues, deduplication and consumer dispatch.

The Android provider currently exposes the gateway boundary and queues messages safely while no gateway is provisioned. BLE framing, provisioning, ESP-NOW forwarding and node-specific state belong in this module and its firmware support, never in `core/transport`.

## Message path

```text
capability / protocol / ODK
            │ generic envelope
            ▼
core durable outbox + dispatcher
            │ provider contract
            ▼
ESP mesh Android gateway adapter
            │ versioned bridge frames
            ▼
ESP-NOW nodes and relays
```

The gateway bridge carries the MethodMesh envelope as opaque JSON. The current reference node implements TTL, duplicate suppression, bounded store-and-forward, authenticated ESP-NOW packets and basic mesh acknowledgements. BLE bridge frames are bounded to 4 KiB; larger payloads are rejected until a deliberate fragmentation contract is added.

`EspMeshBridgeFrame` currently defines the Android-side frame shape (`methodmesh.gateway`, version 1) and bounds decoded bridge frames. It deliberately does not encode ESP-NOW packet headers, radio addresses or cryptographic membership state; those are firmware and gateway concerns.

No keys, MAC addresses or radio-specific routing values are exposed as MethodMesh identity.

## Provisioning and background operation

The gateway capability scans for the module GATT service, provisions a selected device, and can send a network ID, network key, peer list and device provisioning token. The reference firmware derives a per-device default token from its hardware identity; production provisioning should deliver that token through a physical or NFC-assisted enrolment step rather than exposing it in the radio protocol.

After a gateway is selected, MethodMesh can run the `connectedDevice` foreground service. The service restarts the generic transport runtime so queued outbound messages and inbound envelopes continue to work while the dashboard or capability screen is closed. The diagnostics capability reports provider state and bounded inbox/outbox counts without showing keys or payloads.

The current firmware reference is MicroPython for the repository's existing ESP32-C3 workflow. It is suitable for protocol and bench testing. Encrypted payloads, authenticated multi-node provisioning, route discovery and a production image build remain explicit follow-up work.
