# ESP32 sensor framework installer

Capability IDs:

- `esp32.sensor_profile_install`

This capability installs the MethodMesh ESP32-C3 sensor stack from inside the Android app. Field installation uses prebuilt, sensor-specific full flash images so the phone does not need to copy Python files through the MicroPython REPL.

## Recommended workflow

Use `esp32.sensor_profile_install`.

1. Select the sensor image to flash:
   - `aht20` — AHT20 temperature/humidity on GPIO 8 SDA / GPIO 9 SCL.
   - `ld2410c` — LD2410C mmWave presence on TX GPIO 21 / RX GPIO 4.
2. Put the ESP32-C3 into ROM bootloader mode: hold **BOOT**, tap **RESET**, then release **BOOT**.
3. Refresh USB devices.
4. Select the ESP32-C3.
5. Tap **Confirm bootloader mode**.
6. Confirm that the board may be erased.
7. Tap **Erase and install** for the selected image.

Bundled full image assets:

- `firmware/esp32c3_images/methodmesh_esp32c3_aht20.bin`
- `firmware/esp32c3_images/methodmesh_esp32c3_ld2410c.bin`

These images contain the MicroPython firmware plus a `vfs` filesystem partition with `main.py`, sensor drivers, and the selected default sensor config.

Installing a sensor image replaces the board runtime and active sensor config. This means changing from one sensor profile to another is an overwrite operation, not an additive one.

Older split wipe/runtime/profile screens are no longer registered as app capabilities. The code path is retained only for recovery/debug work while the image-based installer stabilises.

## Rebuilding bundled images

From the repository root:

```text
.venv-firmware-tools/bin/python firmware/esp32c3_aht20_ble/build_sensor_images.py
```

The script uses `mp-image-tool-esp32` to add and format a real `vfs` filesystem partition, writes the per-sensor MethodMesh files into it, then stores the resulting 4 MB flash images in Android assets.

## Intent examples

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
