# Provision BLE sensor node

Capability ID: `sensor_node_provisioner`

This capability provisions an already-flashed MethodMesh BLE sensor node. The first bundled firmware target is an ESP32-C3 MicroPython node with a generic MethodMesh BLE sensor contract and selectable sensor profiles, currently including AHT20 temperature/humidity and LD2410C mmWave presence.

The provisioner scans for nearby MethodMesh sensor nodes, connects over BLE, reads the node manifest, tests a measurement, writes a persistent configuration command, reads a confirmation sample, and saves the resulting device profile to the MethodMesh device registry.

## Intended workflow

1. Flash the generic MethodMesh MicroPython firmware to an ESP32-C3.
2. Power the node. Attached sensors are optional during provisioning.
3. Open this capability in MethodMesh.
4. Tap **Scan for MethodMesh sensors**.
5. Select the node and tap **Connect**.
6. Tap **Test measurement**.
7. Set the device ID, display name, sample interval, and attached sensor profile.
8. Tap **Save sensor to registry**.
9. Confirm that the saved sensor appears in the provisioned sensors list.

The node can be provisioned before sensors are attached. Missing drivers are reported in the manifest and confirmation reading rather than preventing BLE discovery.

## Sensor profiles

| Profile | Hardware | Default wiring | Returned fields |
|---|---|---|---|
| `aht20` | AHT20 temperature/humidity | I2C SDA GPIO 8, SCL GPIO 9 | `temperature_c`, `relative_humidity_pct` |
| `ld2410c` | LD2410C mmWave radar | UART TX GPIO 21, RX GPIO 4, 256000 baud | `presence`, `target_state`, `moving_distance_cm`, `moving_energy`, `stationary_distance_cm`, `stationary_energy`, `detection_distance_cm` |

The LD2410C profile is hardware-specific and should be treated as a first bundled radar driver rather than a universal radar abstraction. If a board uses different UART pins, update the firmware constants before installation.

## Intent example

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='sensor_node_provisioner',input_sensor_device_id='clinic_room_01_sensor',input_sensor_device_name='Clinic room 01 sensor',input_sensor_sample_interval_ms='60000',return_mode='flat')
```

## Inputs

| Field | Description |
|---|---|
| `input_sensor_device_id` | Persistent MethodMesh device identifier to write to the node. |
| `input_sensor_device_name` | BLE display name to write to the node. |
| `input_sensor_sample_interval_ms` | Sampling interval in milliseconds. |
| `input_sensor_profile` | Sensor profile to configure, currently `aht20` or `ld2410c`. |

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
| `registry_device_id` | Local MethodMesh device-registry ID. |
| `sensor_provisioning_error` | Error message when provisioning fails. |
| `sensor_provisioned_time_iso` | Time the provisioning result was recorded. |

## BLE contract

The expected firmware contract is documented in `firmware/esp32c3_aht20_ble/README.md`.

MethodMesh service UUID:

```text
b6f2a900-9b8f-4f4e-9a1f-4f37a0010000
```

Characteristics:

```text
b6f2a901-9b8f-4f4e-9a1f-4f37a0010000  manifest JSON, read
b6f2a902-9b8f-4f4e-9a1f-4f37a0010000  latest reading JSON, read/notify
b6f2a903-9b8f-4f4e-9a1f-4f37a0010000  command JSON, read/write
```
