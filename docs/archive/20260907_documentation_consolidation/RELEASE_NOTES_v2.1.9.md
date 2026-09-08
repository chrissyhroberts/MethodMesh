# MethodMesh v2.1.9 Release Notes

MethodMesh v2.1.9 stabilises the ESP32 sensor framework after live testing with two physical ESP32-C3 nodes: one AHT20 temperature/humidity node and one LD2410C mmWave radar node.

## Highlights

### Prebuilt ESP32 sensor images

The ESP32 installer now works around fragile MicroPython REPL uploads by bundling complete 4 MB flash images:

- AHT20 temperature/humidity image;
- LD2410C mmWave radar/presence image.

The user-facing flow is now centred on choosing the attached sensor image, erasing the board, and installing the selected MethodMesh image in one operation.

### Cleaner sensor provisioning

BLE provisioning has been simplified around the actual field workflow:

- scan for MethodMesh sensors;
- connect to the selected node;
- name the sensor locally;
- test a measurement;
- save the node to the device registry.

Provisioning no longer asks the operator to configure all possible sensor fields. The installed firmware image is authoritative: an AHT20 image reports AHT20 fields, and an LD2410C image reports radar fields.

### Better advertised sensor detection

Sensor profile detection is more robust when Android receives a shortened BLE payload. MethodMesh now recovers the installed sensor profile from partial manifest or reading payloads, so a node should not be reported as "not reported by this firmware" when the profile is present near the start of the packet.

### Sensor read result fix

The sensor reader now treats recoverable partial readings as successful readings. This fixes cases where the result header said failed even though coherent values such as presence, moving distance, stationary distance, and energy values had been captured.

### Firmware protocol v0.1.6

The bundled ESP32 firmware has been updated to `methodmesh-sensor-0.1.6`.

This version keeps BLE manifest, reading, and command payloads compact so Android can read them reliably. It also prevents provisioning from changing the installed sensor profile at runtime; switching from AHT20 to LD2410C now means flashing the corresponding full image.

### Old capability surface cleanup

The ESP32 sensor framework now exposes the current image-based installer path rather than the older mixed wipe/runtime/profile-upload capability set.

## Validation

Built with:

```text
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

## Notes

This release is mainly about making the sensor path less flaky in real field use. The long-term design remains modular: each supported sensor gets its own firmware profile and driver while the Android app uses the same scan, provision, registry, and read flow.
