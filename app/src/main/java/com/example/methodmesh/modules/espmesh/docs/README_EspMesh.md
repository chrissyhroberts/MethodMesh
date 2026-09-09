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

The initial protocol target is a versioned gateway bridge carrying the MethodMesh envelope as opaque JSON. Later firmware can add fragmentation, TTL, acknowledgements, deduplication and store-and-forward without changing the core envelope.

`EspMeshBridgeFrame` currently defines the Android-side frame shape (`methodmesh.gateway`, version 1) and bounds decoded bridge frames. It deliberately does not encode ESP-NOW packet headers, radio addresses or cryptographic membership state; those are firmware and gateway concerns.

No keys, MAC addresses or radio-specific routing values are exposed as MethodMesh identity.
