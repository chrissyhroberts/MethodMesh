# MethodMesh ESP mesh node

This MicroPython reference image is the firmware-side companion to the Android `espmesh` module. Copy `main.py` to an ESP32-C3 running MicroPython with the `espnow` module available.

The node exposes the MethodMesh gateway BLE service, accepts versioned HELLO/CONFIG/OUTBOUND bridge frames, and forwards authenticated opaque MethodMesh envelopes over ESP-NOW. It includes bounded TTL, duplicate suppression, basic acknowledgements, expiry-aware store-and-forward, and a persistent queue.

Provision a network ID, network key, peer list, and the per-device provisioning token before radio traffic is accepted. Payload encryption and production authenticated enrolment remain separate hardening work.
