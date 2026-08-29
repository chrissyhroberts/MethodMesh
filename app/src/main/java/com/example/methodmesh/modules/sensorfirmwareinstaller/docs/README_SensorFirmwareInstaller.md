# ESP32 sensor framework installer

Capability IDs:

- `esp32.board_wipe`
- `esp32.runtime_install`
- `esp32.sensor_profile_install`

These capabilities install the MethodMesh ESP32-C3 sensor stack from inside the Android app. They are split deliberately so each physical board state is explicit and testable, rather than hidden inside one flaky all-in-one flow.

## Recommended workflow

### 1. Wipe old firmware

Use `esp32.board_wipe`.

1. Connect the ESP32-C3 over USB/OTG.
2. Put the board into ROM bootloader mode: hold **BOOT**, tap **RESET**, then release **BOOT**.
3. Refresh USB devices.
4. Select the ESP32-C3.
5. Confirm that the board may be erased.
6. Tap **Wipe old firmware**.

This step is intended for boards that may contain Home Assistant, ESPHome, Arduino, old MethodMesh firmware, or any other previous firmware.

### 2. Install the MethodMesh runtime

Use `esp32.runtime_install`.

1. Keep or put the board in ROM bootloader mode.
2. Refresh USB devices.
3. Select the ESP32-C3.
4. Tap **Install MethodMesh runtime**.

This writes the bundled MicroPython image. When it succeeds, reset or unplug/replug the board normally without holding **BOOT**.

### 3. Install or replace the sensor profile

Use `esp32.sensor_profile_install`.

1. The board should now be in normal MicroPython USB mode, not BOOT mode.
2. Choose the attached sensor profile:
   - `aht20` — AHT20 temperature/humidity on GPIO 8 SDA / GPIO 9 SCL.
   - `ld2410c` — LD2410C mmWave presence on TX GPIO 21 / RX GPIO 4.
3. Refresh USB devices.
4. Select the ESP32-C3.
5. Tap **Check MicroPython connection**.
6. Tap **Upload sensor profile to board**.

Installing a sensor profile replaces `main.py`, shared driver files, and the active sensor config. This means changing from one sensor profile to another is an overwrite operation, not an additive one.

## Intent examples

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.board_wipe',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.runtime_install',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',input_sensor_profile='aht20',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',input_sensor_profile='ld2410c',return_mode='flat')
```

## Outputs

| Field | Description |
|---|---|
| `firmware_install_status` | `installed`, `erased`, or `failed`. |
| `firmware_board` | Target board family, currently `ESP32-C3`. |
| `firmware_name` | Bundled firmware or profile installed. |
| `firmware_version` | MethodMesh firmware version. |
| `firmware_bytes` | Size of written firmware/profile payload. |
| `usb_device` | Android USB device label. |
| `firmware_install_error` | Error message if installation failed. |
| `firmware_installed_time_iso` | Time the result was recorded. |
