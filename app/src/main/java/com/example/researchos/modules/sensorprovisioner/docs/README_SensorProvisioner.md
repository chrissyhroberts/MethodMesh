# Provision BLE sensor node

Capability ID: `sensor_node_provisioner`

This capability provisions an already-flashed ResearchOS BLE sensor node. The first bundled firmware target is an ESP32-C3 MicroPython node with a generic ResearchOS BLE sensor contract and an optional AHT20 driver.

The provisioner scans for nearby ResearchOS sensor nodes, connects over BLE, reads the node manifest, writes a persistent configuration command, reads a confirmation sample, and saves the resulting device profile to the ResearchOS device registry.

## Intended workflow

1. Flash the generic ResearchOS MicroPython firmware to an ESP32-C3.
2. Power the node. Attached sensors are optional during provisioning.
3. Open this capability in ResearchOS.
4. Set a device ID, display name, and sample interval.
5. Scan and connect to the node.
6. Tap **Provision and save sensor**.
7. Confirm that a sample reading is returned and the node is saved to the registry.

The node can be provisioned before sensors are attached. Missing drivers are reported in the manifest and confirmation reading rather than preventing BLE discovery.

## Intent example

```text
com.example.researchos.EXECUTE_METHOD(method_id='sensor_node_provisioner',input_sensor_device_id='clinic_room_01_sensor',input_sensor_device_name='Clinic room 01 sensor',input_sensor_sample_interval_ms='60000',return_mode='flat')
```

## Inputs

| Field | Description |
|---|---|
| `input_sensor_device_id` | Persistent ResearchOS device identifier to write to the node. |
| `input_sensor_device_name` | BLE display name to write to the node. |
| `input_sensor_sample_interval_ms` | Sampling interval in milliseconds. |

Aliases accepted by the UI include `sensor_device_id`, `sensor_device_name`, and `sensor_sample_interval_ms`.

## Outputs

| Field | Description |
|---|---|
| `sensor_provisioning_status` | `provisioned` or `failed`. |
| `sensor_device_id` | Configured device ID. |
| `sensor_device_name` | Configured device name. |
| `sensor_device_address` | BLE MAC/address observed by Android. |
| `sensor_sample_interval_ms` | Configured sample interval. |
| `sensor_manifest_json` | Manifest read from the node. |
| `sensor_command_response_json` | Response from the provisioning command characteristic. |
| `sensor_confirmation_reading_json` | Confirmation sensor reading after provisioning. |
| `registry_device_id` | Local ResearchOS device-registry ID. |
| `sensor_provisioning_error` | Error message when provisioning fails. |
| `sensor_provisioned_time_iso` | Time the provisioning result was recorded. |

## BLE contract

The expected firmware contract is documented in `firmware/esp32c3_aht20_ble/README.md`.

ResearchOS service UUID:

```text
b6f2a900-9b8f-4f4e-9a1f-4f37a0010000
```

Characteristics:

```text
b6f2a901-9b8f-4f4e-9a1f-4f37a0010000  manifest JSON, read
b6f2a902-9b8f-4f4e-9a1f-4f37a0010000  latest reading JSON, read/notify
b6f2a903-9b8f-4f4e-9a1f-4f37a0010000  command JSON, read/write
```
