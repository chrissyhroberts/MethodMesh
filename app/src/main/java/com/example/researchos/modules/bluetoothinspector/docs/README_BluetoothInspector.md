# Bluetooth device inspector

The Bluetooth device inspector is a user-directed discovery and assay tool for nearby Bluetooth devices. It is the first transport-specific client for the shared device registry.

## Capabilities

- Scan for nearby BLE devices.
- Show device name, address, and signal strength.
- Connect to a selected BLE device.
- Discover GATT services and characteristics.
- Group discovered endpoints by service, with standard Bluetooth UUIDs labelled where known.
- Report readable, writable, and notification-capable endpoints.
- Read readable characteristics on request.
- Probe an individual readable characteristic once or sample it for ten one-second intervals.
- Subscribe to notification/indication streams by writing the standard Client Characteristic Configuration Descriptor (CCCD) where supported.
- Show paired classic-Bluetooth devices as serial-profile candidates.
- Select and assay devices that are already paired, without requiring a fresh scan.
- Save a tested BLE profile or paired classic-Bluetooth profile into the central device registry.

The prototype does not perform packet interception, password guessing, hidden-service access, or unauthorised writes.

Endpoint probing is deliberately read-only. **Read** performs one GATT read; **Sample** performs ten reads at one-second intervals; **Subscribe** enables notifications or indications through the endpoint's CCCD and records pushed values. Values are shown as hexadecimal bytes and, when safely printable, an accompanying UTF-8 interpretation. Write probing is not enabled in the inspector.

Paired classic-Bluetooth devices are handled differently from BLE devices. Android
can expose their bonded identity and advertised UUIDs (including the Serial Port
Profile/RFCOMM candidate) without a GATT connection. The inspector records those
transport hints and saves them as `BLUETOOTH_CLASSIC` registry profiles. This is
the useful first step for devices such as paired mini label printers; a later
printer capability can use the saved address/profile to open an authorised
RFCOMM connection and send a device-specific print payload.

## Android intent

```text
com.example.researchos.EXECUTE_METHOD(method_id='bluetooth_device_inspector',return_mode='flat')
```

The standalone screen requests the Android Bluetooth permissions required for scanning and connection when needed.

## Inputs

The first version is interactive and has no required input fields. The user can
select either a nearby scanned device or an already-paired device. BLE devices
can then be connected and explicitly read/sampled/subscribed. Paired classic
devices expose their bonded UUIDs and serial-profile candidacy for later
transport-specific capabilities.

## Outputs

The result includes:

- `scan_results` — nearby device names, addresses, and RSSI values;
- `selected_device` — the selected device identity;
- `connection_status` — scan, connection, and discovery status;
- `gatt_endpoints` — services and characteristic properties;
- `captured_data` — read values, read status/error codes, CCCD subscription status, and notification payloads as hexadecimal bytes;
- `serial_candidates` — paired classic-Bluetooth devices that may provide serial profiles;
- `registry_profile` — the discovered endpoint profile suitable for saving in the device registry.

## ODK example

The accompanying `example_odk_bluetooth_device_inspector.xlsx` demonstrates invoking the capability and returning its flat discovery result. For normal use this is primarily a dashboard or scheduled device utility; an ODK form can store the returned discovery record when a study requires an audit trail.
