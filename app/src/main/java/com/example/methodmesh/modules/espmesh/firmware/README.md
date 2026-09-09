# MethodMesh ESP mesh firmware boundary

Firmware is intentionally kept module-owned. The first Android slice defines the generic envelope/provider boundary and the versioned bridge contract; ESP-IDF firmware implementation follows here.

Required firmware responsibilities:

- stable node identity separate from radio addresses
- authenticated provisioning and membership
- ESP-NOW frame forwarding with bounded TTL and deduplication
- durable store-and-forward queues
- BLE gateway bridge using the module protocol
- sensor observations represented as MethodMesh payloads

`methodmesh_gateway_protocol.h` is the shared bridge constant header for an ESP-IDF implementation. It is intentionally limited to the Android↔gateway service and characteristic contract; node identity, provisioning credentials, ESP-NOW packet headers and routing state remain firmware-owned.

`methodmesh_mesh_node.py` is the current MicroPython reference node for the repository’s existing ESP32-C3 firmware workflow. It supports the first relay slice: BLE HELLO/OUTBOUND/INBOUND frames, opaque envelope forwarding, bounded TTL, duplicate suppression, authenticated ESP-NOW packets and a bounded JSONL queue. It refuses radio traffic until a network ID and key have been provisioned through an authorised CONFIG frame. Payload encryption and authenticated provisioning UX remain follow-up work.

The firmware must never be required by the generic Android core to compile or run.
