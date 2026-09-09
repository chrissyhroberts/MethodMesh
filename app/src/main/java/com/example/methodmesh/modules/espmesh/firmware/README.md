# MethodMesh ESP mesh firmware boundary

Firmware is intentionally kept module-owned. The first Android slice defines the generic envelope/provider boundary and the versioned bridge contract; ESP-IDF firmware implementation follows here.

Required firmware responsibilities:

- stable node identity separate from radio addresses
- authenticated provisioning and membership
- ESP-NOW frame forwarding with bounded TTL and deduplication
- durable store-and-forward queues
- BLE gateway bridge using the module protocol
- sensor observations represented as MethodMesh payloads

The firmware must never be required by the generic Android core to compile or run.
