# Read sensor

Capability ID: `sensor.read`

`sensor.read` connects to a registered ResearchOS BLE sensor node and returns
single, trace, or averaged measurements. It is the measurement-facing part of
the ESP32 sensor framework.

## Intent examples

Read a specific registered device once:

```text
com.example.researchos.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_sensor_read_mode='single',return_mode='flat')
```

Average over one minute:

```text
com.example.researchos.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_sensor_profile='aht20',input_sensor_read_mode='average',input_duration_seconds='60',input_sample_interval_seconds='5',return_mode='flat')
```

Trace over two minutes:

```text
com.example.researchos.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_sensor_id='radar_1',input_sensor_read_mode='trace',input_duration_seconds='120',input_sample_interval_seconds='1',return_mode='flat')
```

Strict device matching:

```text
com.example.researchos.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_device_match_policy='strict',return_mode='flat')
```

## Device fallback

If a specific `device_id` is supplied but that registered device cannot be
found, the default `fallback` policy opens a chooser of nearby ResearchOS sensor
nodes. The result records both the requested and actual device:

```text
requested_device_id
actual_device_id
device_selection_mode
device_substitution
device_substitution_reason
```

Use `input_device_match_policy='strict'` when substituting a nearby device is
not acceptable.

## Modes

| Mode | Behaviour |
|---|---|
| `single` | Read the latest sensor sample once. |
| `trace` | Read repeatedly for the configured duration and return `sensor_trace_json`. |
| `average` | Read repeatedly and return `sensor_summary_json` plus flat summary fields where possible. |
| `discover` | Scan registered sensors and report which are currently nearby. |

## Key outputs

| Field | Description |
|---|---|
| `sensor_read_status` | `succeeded`, `failed`, or `discovered`. |
| `actual_device_id` | Device ID used for the read. |
| `sensor_profile` | Sensor profile, for example `aht20`. |
| `actual_sensor_id` | Sensor ID from the reading, where supplied by firmware. |
| `temperature_c` | AHT20 temperature value when present. |
| `relative_humidity_pct` | AHT20 humidity value when present. |
| `sensor_reading_json` | Raw latest reading JSON. |
| `sensor_trace_json` | Raw time series for trace mode. |
| `sensor_summary_json` | Summary object for average mode. |
| `payload_sha256` | Firmware-provided payload hash when present. |
