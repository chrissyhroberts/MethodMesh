# ESP32 sensor framework installer

Public capability ID:

- `esp32.sensor_profile_install`

The ESP32 sensor framework now uses one image installer for all supported MethodMesh ESP32-C3 roles. The former standalone `esp32.mesh_install` surface has been retired; ESP-NOW mesh nodes are selected as an image in the same installer used for BLE sensor nodes.

## Workbench workflow

The **ESP32 sensor framework** should expose two setup tools:

1. **Install ESP32 image** — erase and install one complete bundled ESP32-C3 image.
2. **Provision BLE sensor node** — configure and register sensor images after installation.

ESP mesh transport/gateway capabilities remain in the ESP mesh module because they operate the running field network rather than install firmware.

## Installable images

Use `esp32.sensor_profile_install` and select one image:

- `aht20` — AHT20 temperature/humidity on GPIO 8 SDA / GPIO 9 SCL.
- `ld2410c` — LD2410C mmWave presence on the firmware-defined UART pins.
- `espnow_mesh` — BLE-provisioned MethodMesh ESP-NOW relay/gateway node.

Then:

1. Put the ESP32-C3 into ROM bootloader mode: hold **BOOT**, tap **RESET**, then release **BOOT**.
2. Refresh USB devices.
3. Select the ESP32-C3.
4. Tap **Confirm bootloader mode**.
5. Confirm that the board may be erased.
6. Tap **Erase and install** for the selected image.

The ROM flasher is transport-aware. Espressif native USB uses the direct native bulk path. Boards connected through CP210x, CH340/CH9102 or FTDI bridges use the Android USB-serial driver already bundled with MethodMesh, with the raw bulk path retained only as a fallback. This is required for fresh boards whose USB bridge is not USB CDC. The live install log states which transport was selected.

The installer uses the ESP32-C3 ROM protocol rather than the ESP8266/legacy packet shape. In particular, C3 `FLASH_BEGIN` uses the extended parameter block and ROM command status is read from the status/error bytes before the two reserved trailing bytes. The installer MUST fail on any ROM-reported flash error.

A write acknowledgement is not sufficient evidence of installation. After every complete image write, MethodMesh asks the ESP32-C3 ROM to calculate the MD5 of the written flash region and compares it with the bundled source image. **Installed/success MUST NOT be reported unless this verification matches.** A blank/erased `0xFF` region, malformed image, partial write or ROM command error therefore ends as a failed install rather than a false-positive success.

Bundled full-image assets:

- `firmware/esp32c3_images/methodmesh_esp32c3_aht20.bin`
- `firmware/esp32c3_images/methodmesh_esp32c3_ld2410c.bin`
- `firmware/esp32c3_images/methodmesh_esp32c3_espnow_mesh.bin`

Each is a complete 4 MB field image. The normal install path therefore does not depend on copying Python files through the MicroPython REPL.

## After installation

For `aht20` or `ld2410c`, reset normally and use **Provision BLE sensor node** to set identity/sampling parameters and save the node to the MethodMesh device registry.

For `espnow_mesh`, reset normally and open **ESP mesh transport** in Workbench. Select the node over BLE, configure the ESP-NOW network ID/transport key, and configure the phone-only E2E group key. Phones that should decrypt one another's field traffic must share the same E2E group key; that key is wrapped by Android Keystore and is never sent to the ESP. Peer MAC addresses are not required because peerless nodes use authenticated ESP-NOW broadcast. Once enabled, the foreground transport service reconnects to the configured ESP whenever it is in BLE range, including battery-powered deployments where there is no USB cable. A fresh node can be provisioned without a token; later reconfiguration requires its per-node token where enforced by firmware.

## ESP-NOW image source

The canonical mesh-node MicroPython runtime bundled into Android is:

`main/assets/firmware/esp32c3_espnow_mesh/main.py`

The generated full image is:

`main/assets/firmware/esp32c3_images/methodmesh_esp32c3_espnow_mesh.bin`

A dependency-free rebuild helper is retained with this module at:

`firmware/build_espnow_mesh_image.py`

The helper verifies the checked-in current LittleFS image layout before replacing the `main.py` data payload. It refuses to build if that baseline layout changes. When the base image/filesystem layout is regenerated, use the repository's full ESP32 image tooling and then update or retire the helper rather than guessing new offsets.

## Intent examples

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',input_sensor_profile='aht20',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',input_sensor_profile='ld2410c',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',input_sensor_profile='espnow_mesh',return_mode='flat')
```

## Outputs

| Field | Description |
|---|---|
| `firmware_install_status` | `installed`, `erased`, or `failed`. |
| `firmware_board` | Target board family, currently `ESP32-C3`. |
| `firmware_name` | Bundled firmware/image installed. |
| `firmware_version` | MethodMesh firmware version. |
| `firmware_bytes` | Size of written firmware/image payload. |
| `usb_device` | Android USB device label. |
| `firmware_install_error` | Error message if installation failed. |
| `firmware_installed_time_iso` | Time the result was recorded. |
