# ResearchOS v2.1.5 Release Notes

ResearchOS v2.1.5 introduces the first end-to-end ESP32 sensor framework. The app can now install MicroPython onto an ESP32-C3, upload bundled ResearchOS sensor firmware from the phone, provision the node over BLE, and save it into the local device registry. This is the foundation for low-cost custom environmental and field sensors that can be configured without a laptop.

## Highlights

### ESP32 sensor framework

The ESP32 firmware installer and BLE sensor provisioner are now grouped together under:

```text
ESP32 sensor framework
```

This keeps the workflow clearer:

1. Install MicroPython to an ESP32-C3 over USB/OTG.
2. Reset or replug the board.
3. Upload the bundled ResearchOS `main.py` and sensor-driver files.
4. Reset or replug again.
5. Scan for the node over BLE and provision it into the device registry.

### Phone-only firmware installation

ResearchOS now bundles:

- the official ESP32-C3 MicroPython binary;
- the ResearchOS BLE sensor-node firmware;
- sensor-specific MicroPython driver files.

The installer uses a guided two-step process because ESP32-C3 USB mode switching is fragile on Android. This avoids the previous false-success state where MicroPython was installed but `main.py` was not visible to the board.

### BLE sensor provisioning

The provisioner can scan for ResearchOS BLE sensor nodes, connect to the node, read its manifest, configure its identity and sampling interval, request a confirmation sample, and save the resulting profile to the ResearchOS device registry.

Provisioning returns useful audit fields including:

```text
sensor_provisioning_status
sensor_device_id
sensor_device_name
sensor_device_address
sensor_sample_interval_ms
sensor_manifest_json
sensor_command_response_json
sensor_confirmation_reading_json
sensor_registry_device_id
```

### Sensor profile selection

Provisioning now includes a sensor profile selector. The first implemented profile is:

```text
AHT20 temperature/humidity
```

An LD2410C mmWave presence profile has also been scaffolded as an in-development placeholder so future sensor-specific work can be added cleanly.

### Modular sensor-driver firmware

Sensor-specific firmware code has been split into separate MicroPython driver files:

```text
sensor_drivers/aht20.py
sensor_drivers/ld2410c.py
sensor_drivers/__init__.py
```

This means the ESP32 framework is no longer conceptually tied to AHT20. AHT20 is simply the first working driver.

## Validation

Built with:

```text
./gradlew assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

The ESP32-C3 workflow was also tested end to end:

- MicroPython install from ResearchOS succeeded.
- ResearchOS firmware upload from ResearchOS succeeded.
- The board advertised as a ResearchOS sensor node after reset.
- BLE provisioning succeeded and saved the node to the device registry.

## Notes

ESP32-C3 boards may require a manual reset or unplug/replug between USB flashing, firmware upload, and BLE provisioning. The app now treats this as part of the expected workflow rather than as a hidden failure.

The LD2410C profile is scaffolded but not yet a functional radar-driver implementation.
