# Install ESP32 sensor firmware

Capability ID: `sensor_firmware_installer`

This capability installs the bundled MethodMesh ESP32-C3 sensor-node firmware stack from inside the Android app. It is intended to remove the need for `esptool`, `mpremote`, or a laptop during field provisioning.

## Current scope

Supported now:

- MethodMesh carries the official MicroPython `ESP32_GENERIC_C3-20260406-v1.28.0.bin` as an Android asset.
- MethodMesh carries the bundled sensor-node `main.py` and sensor driver files
  as Android assets.
- The phone lists connected USB devices.
- The user grants USB permission.
- MethodMesh only enables raw flashing for recognised Espressif or USB-serial targets.
- MethodMesh requires explicit confirmation before overwriting firmware.
- MethodMesh speaks a small ESP32 ROM bootloader protocol to write MicroPython
  at flash address `0`.
- After the board has restarted into MicroPython, MethodMesh uploads the bundled
  MethodMesh Python files over the friendly REPL.

The implementation is intentionally conservative: it does not attempt arbitrary USB writes to unknown devices.

## Field workflow

1. Connect the ESP32-C3 over USB/OTG.
2. Open `sensor_firmware_installer`.
3. Select the USB device.
4. Hold **BOOT**, tap **RESET**, release **BOOT**, then tick the overwrite confirmation.
5. Tap **Install MicroPython to blank board**.
6. When flashing succeeds, reset or replug the board without holding **BOOT** so
   it starts MicroPython normally.
7. Tap **Install MethodMesh main.py**. This uploads `main.py` and the bundled
   sensor driver files.
8. After the board restarts, open `sensor_node_provisioner` to configure device
   identity and BLE registry details.

The workflow is intentionally split because ESP32-C3 boards move between ROM
bootloader mode and MicroPython REPL mode. Keeping those steps explicit is more
reliable than trying to do both operations in one hidden sequence.

## Intent example

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='sensor_firmware_installer',return_mode='flat')
```

## Outputs

| Field | Description |
|---|---|
| `firmware_install_status` | `installed` or `failed`. |
| `firmware_board` | Target board family, currently `ESP32-C3`. |
| `firmware_name` | Bundled firmware file installed. |
| `firmware_version` | MethodMesh firmware version. |
| `firmware_bytes` | Size of installed `main.py`. |
| `usb_device` | Android USB device label. |
| `firmware_install_error` | Error message if installation failed. |
| `firmware_installed_time_iso` | Time the result was recorded. |
