# MethodMesh ESP mesh firmware boundary

The ESP mesh module owns the runtime transport protocol and Android↔node bridge. Firmware installation itself is consolidated into the **ESP32 sensor framework** Workbench installer.

Required firmware responsibilities include:

- stable node identity separate from radio addresses;
- BLE discovery/provisioning of network identity and key;
- ESP-NOW frame forwarding with bounded TTL and duplicate suppression;
- durable store-and-forward queues for data;
- ephemeral priority live-voice relay with bounded TTL/deduplication and no voice persistence;
- BLE gateway bridge using the module protocol;
- sensor/other MethodMesh observations carried as opaque transport payloads where applicable.

`methodmesh_gateway_protocol.h` remains the shared bridge constant header for a future ESP-IDF implementation.

The canonical current MicroPython mesh-node runtime is no longer duplicated in this module folder. It is bundled at:

`main/assets/firmware/esp32c3_espnow_mesh/main.py`

and installed as the full image:

`main/assets/firmware/esp32c3_images/methodmesh_esp32c3_espnow_mesh.bin`

Select **ESP-NOW mesh node** inside **Install ESP32 image**. After flashing and resetting the board, use **ESP mesh gateway** to scan and provision it over BLE.

The firmware must never be required by the generic Android core to compile or run.

## Live voice boundary

Firmware `methodmesh-espmesh-0.4.0` treats walkie-talkie audio as an ephemeral traffic class. Android sends only compact E2E ciphertext voice packets. The ESP wraps them in a separately authenticated network envelope, relays them immediately, and never appends them to LittleFS. `VOICE_LISTEN` controls only BLE delivery to the associated phone; the ESP continues radio relay while the phone is muted or absent. Durable backlog retries are deferred while live voice is active and resume automatically afterwards.
