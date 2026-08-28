# MethodMesh ESP32-C3 BLE sensor-node firmware

This is the first MethodMesh generic environmental sensor node firmware. It runs on an ESP32-C3 with MicroPython and exposes a generic MethodMesh BLE GATT contract.

The bundled firmware includes an AHT20 temperature/humidity driver as the first concrete sensor driver and an LD2410C profile scaffold for future radar/presence work. The node still boots and advertises without the selected sensor attached; the manifest and readings report the driver as missing until the sensor is wired.

## Hardware

- ESP32-C3 board running MicroPython.
- Optional AHT20 temperature/humidity sensor.
- I2C wiring:
  - AHT20 `VIN` to ESP32-C3 `3V3`.
  - AHT20 `GND` to ESP32-C3 `GND`.
  - AHT20 `SDA` to GPIO `8` by default.
  - AHT20 `SCL` to GPIO `9` by default.

If your ESP32-C3 board uses different I2C pins, edit these constants in `main.py`:

```python
I2C_SDA_PIN = 8
I2C_SCL_PIN = 9
```

## Install

Flash MicroPython to the ESP32-C3, then copy `main.py` and the `sensor_drivers/`
folder to the board.

Using `mpremote`, the usual workflow is:

```text
mpremote connect auto fs mkdir :sensor_drivers
mpremote connect auto fs cp sensor_drivers/__init__.py :sensor_drivers/__init__.py
mpremote connect auto fs cp sensor_drivers/aht20.py :sensor_drivers/aht20.py
mpremote connect auto fs cp sensor_drivers/ld2410c.py :sensor_drivers/ld2410c.py
mpremote connect auto fs cp main.py :main.py
mpremote connect auto reset
```

The board should advertise over BLE as:

```text
MethodMesh-Sensor
```

## BLE contract

Service UUID:

```text
b6f2a900-9b8f-4f4e-9a1f-4f37a0010000
```

Characteristics:

```text
b6f2a901-9b8f-4f4e-9a1f-4f37a0010000  manifest JSON, read
b6f2a902-9b8f-4f4e-9a1f-4f37a0010000  latest reading JSON, read/notify
b6f2a903-9b8f-4f4e-9a1f-4f37a0010000  command JSON, read/write
```

The manifest advertises device identity, firmware version, sensor fields, and the UUIDs needed by MethodMesh.

The reading characteristic returns JSON like:

```json
{
  "methodmesh_sensor_reading_version": "1",
  "device_id": "esp32c3-a1b2c3",
  "device_name": "MethodMesh-Sensor",
  "firmware_version": "methodmesh-sensor-0.1.1",
  "sensor_id": "aht20_1",
  "sensor_type": "AHT20",
  "sample_time_ms": 123456,
  "status": "ok",
  "temperature_c": 22.61,
  "relative_humidity_pct": 54.21,
  "payload_sha256": "..."
}
```

## Provisioning

MethodMesh should act as the provisioner by connecting over BLE and writing JSON commands to the command characteristic.

Sample immediately:

```json
{"command":"sample"}
```

Configure and persist a node identity:

```json
{
  "command": "configure",
  "device_id": "clinic_room_01_sensor",
  "device_name": "Clinic room 01 sensor",
  "sensor_profile": "aht20",
  "sample_interval_ms": 60000
}
```

Read the command characteristic afterwards to get the command response.

Reset the stored configuration:

```json
{"command":"reset_config"}
```

The firmware stores provisioning data in:

```text
methodmesh_sensor_config.json
```

## App-side expectation

The MethodMesh Android provisioner should eventually provide:

- scan for unprovisioned `MethodMesh-Sensor` nodes;
- read manifest;
- select the sensor profile;
- set device ID, display name, and sample interval;
- read a confirmation sample;
- save the device profile into the MethodMesh device registry.

The Android app now provides the intended field workflow: install MicroPython,
upload the bundled MethodMesh Python files, then provision the BLE sensor node.
