# sensorread live/freeze drop-in

This folder replaces the UI files for the existing MethodMesh `sensorread`
module. It is intentionally self-contained: no HomeScreen or global Settings
changes are required.

## Install

Copy/merge this `sensorread/` folder over:

`app/src/main/java/com/example/methodmesh/modules/sensorread/`

**Do not delete the destination folder first**, because the existing unchanged
`SensorReadMethod.kt` and `docs/` should remain in place.

The files in this ZIP overwrite/add:

- `SensorReadCapabilityScreen.kt`
- `SensorReadModule.kt`
- `LiveSensorPanel.kt`

Then build:

```bash
cd /Users/icrucrob/AndroidStudioProjects/MethodMesh
./gradlew :app:assembleDebug
```

## Where the new UI appears

Only here:

**Capabilities → ESP32 sensor framework → Read sensor → Test → Read mode → Live / freeze**

Nothing is added to the main app Settings screen.

## Behaviour

Live mode continuously re-reads the normal sensor BLE characteristic and keeps
the latest 30 readings in memory. The refresh cadence is:

1. `sample_interval_ms` from the ESP32 manifest, if present;
2. otherwise the sensor profile's existing `defaultSampleIntervalMs`.

The live card shows the latest scalar fields and whether `sample_time_ms`
changed between consecutive Android reads. Press **Freeze** to stop acquisition,
then choose:

- Use current reading
- Use window summary
- Use trace
- Resume live

Existing `single`, `trace`, `average`, and `discover` modes are retained.
The typed preset schema intentionally does not expose `live`, because live/freeze
requires an operator and should not be used by headless ODK/preset execution.
