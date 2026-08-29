"""
MethodMesh ESP32-C3 BLE sensor node.

Copy this file to an ESP32-C3 running MicroPython as main.py. It exposes a
generic MethodMesh sensor GATT service with a manifest characteristic, a latest
reading characteristic, and a command characteristic. Sensor drivers are
reported through the manifest; this bundle includes an AHT20 I2C driver.
"""

import json
import os
import time
from machine import I2C, Pin, unique_id

try:
    from sensor_drivers.aht20 import AHT20
except Exception:
    AHT20 = None

try:
    from sensor_drivers.ld2410c import LD2410C
except Exception:
    LD2410C = None

try:
    import bluetooth
except ImportError:
    import ubluetooth as bluetooth


# Adjust these for your ESP32-C3 board if needed. Many ESP32-C3 dev boards use
# GPIO 8/9 for I2C, but boards vary.
FIRMWARE_VERSION = "methodmesh-sensor-0.1.2"
DEFAULT_DEVICE_NAME = "MethodMesh-Sensor"
DEFAULT_DEVICE_ID = "esp32c3-" + "".join("%02x" % b for b in unique_id()[-3:])
CONFIG_FILE = "methodmesh_sensor_config.json"
DEFAULT_SENSOR_PROFILE = "aht20"
SUPPORTED_SENSOR_PROFILES = ["aht20", "ld2410c"]
I2C_SDA_PIN = 8
I2C_SCL_PIN = 9
I2C_FREQUENCY = 100000
DEFAULT_SAMPLE_INTERVAL_MS = 5000


# MethodMesh environmental sensor service.
SERVICE_UUID = bluetooth.UUID("b6f2a900-9b8f-4f4e-9a1f-4f37a0010000")
MANIFEST_UUID = bluetooth.UUID("b6f2a901-9b8f-4f4e-9a1f-4f37a0010000")
READING_UUID = bluetooth.UUID("b6f2a902-9b8f-4f4e-9a1f-4f37a0010000")
COMMAND_UUID = bluetooth.UUID("b6f2a903-9b8f-4f4e-9a1f-4f37a0010000")

FLAG_READ = bluetooth.FLAG_READ
FLAG_WRITE = bluetooth.FLAG_WRITE
FLAG_NOTIFY = bluetooth.FLAG_NOTIFY

IRQ_CENTRAL_CONNECT = 1
IRQ_CENTRAL_DISCONNECT = 2
IRQ_GATTS_WRITE = 3


def advertising_payload(name=None, services=None):
    payload = bytearray()

    def add(ad_type, value):
        payload.extend((len(value) + 1, ad_type))
        payload.extend(value)

    add(0x01, b"\x06")
    if name:
        name_bytes = name.encode("utf-8")
        add(0x09 if len(name_bytes) <= 26 else 0x08, name_bytes[:26])
    for uuid in services or ():
        raw = bytes(uuid)
        if len(raw) == 16:
            add(0x07, raw)
    return payload


def sha256_hex(text):
    try:
        import hashlib
        digest = hashlib.sha256(text.encode("utf-8")).digest()
        return "".join("%02x" % b for b in digest)
    except Exception:
        return ""


def load_config():
    config = {
        "device_id": DEFAULT_DEVICE_ID,
        "device_name": DEFAULT_DEVICE_NAME,
        "sample_interval_ms": DEFAULT_SAMPLE_INTERVAL_MS,
        "sensor_profile": DEFAULT_SENSOR_PROFILE,
        "provisioned": False,
    }
    try:
        with open(CONFIG_FILE, "r") as handle:
            stored = json.loads(handle.read())
        for key in config:
            if key in stored:
                config[key] = stored[key]
    except Exception:
        pass
    return normalize_config(config)


def save_config(config):
    with open(CONFIG_FILE, "w") as handle:
        handle.write(json.dumps(normalize_config(config)))


def reset_config():
    try:
        os.remove(CONFIG_FILE)
    except OSError:
        pass
    return load_config()


def normalize_config(config):
    device_id = str(config.get("device_id") or DEFAULT_DEVICE_ID).strip()[:48]
    device_name = str(config.get("device_name") or DEFAULT_DEVICE_NAME).strip()[:26]
    try:
        interval = int(config.get("sample_interval_ms", DEFAULT_SAMPLE_INTERVAL_MS))
    except Exception:
        interval = DEFAULT_SAMPLE_INTERVAL_MS
    sensor_profile = str(config.get("sensor_profile") or DEFAULT_SENSOR_PROFILE).strip().lower()
    if sensor_profile not in SUPPORTED_SENSOR_PROFILES:
        sensor_profile = DEFAULT_SENSOR_PROFILE
    return {
        "device_id": device_id or DEFAULT_DEVICE_ID,
        "device_name": device_name or DEFAULT_DEVICE_NAME,
        "sample_interval_ms": max(1000, min(interval, 3600000)),
        "sensor_profile": sensor_profile,
        "provisioned": bool(config.get("provisioned", False)),
    }


class MethodMeshSensorNode:
    def __init__(self):
        self.config = load_config()
        self.i2c = None
        self.sensor = None
        self.sensor_error = ""
        self.ble = bluetooth.BLE()
        self.ble.active(True)
        self.connections = set()
        self.latest_reading = {}

        self.ble.irq(self._irq)
        ((self.manifest_handle, self.reading_handle, self.command_handle),) = self.ble.gatts_register_services((
            (
                SERVICE_UUID,
                (
                    (MANIFEST_UUID, FLAG_READ),
                    (READING_UUID, FLAG_READ | FLAG_NOTIFY),
                    (COMMAND_UUID, FLAG_READ | FLAG_WRITE),
                ),
            ),
        ))
        self.ble.gatts_set_buffer(self.manifest_handle, 768)
        self.ble.gatts_set_buffer(self.reading_handle, 512)
        self.ble.gatts_set_buffer(self.command_handle, 512)
        self._init_sensor()
        self._write_manifest()
        self.sample()
        self._advertise()

    def _irq(self, event, data):
        if event == IRQ_CENTRAL_CONNECT:
            conn_handle, _, _ = data
            self.connections.add(conn_handle)
        elif event == IRQ_CENTRAL_DISCONNECT:
            conn_handle, _, _ = data
            self.connections.discard(conn_handle)
            self._advertise()
        elif event == IRQ_GATTS_WRITE:
            conn_handle, value_handle = data
            if value_handle == self.command_handle:
                command = self.ble.gatts_read(self.command_handle).decode("utf-8").strip()
                self.handle_command(command)

    def _advertise(self):
        self.ble.gap_advertise(
            250000,
            adv_data=advertising_payload(services=[SERVICE_UUID]),
            resp_data=advertising_payload(name=self.config["device_name"]),
        )

    def _init_sensor(self):
        try:
            profile = self.config["sensor_profile"]
            if profile == "aht20":
                if AHT20 is None:
                    raise OSError("AHT20 driver file missing")
                self.i2c = I2C(0, sda=Pin(I2C_SDA_PIN), scl=Pin(I2C_SCL_PIN), freq=I2C_FREQUENCY)
                self.sensor = AHT20(self.i2c)
                self.sensor_error = "" if self.sensor.present else "AHT20 not found at I2C address 0x38"
            elif profile == "ld2410c":
                if LD2410C is None:
                    raise OSError("LD2410C driver file missing")
                self.sensor = LD2410C()
                self.sensor_error = "LD2410C driver placeholder; UART implementation not bundled yet."
            else:
                raise OSError("Unsupported sensor profile: %s" % profile)
        except Exception as error:
            self.i2c = None
            self.sensor = None
            self.sensor_error = str(error)

    def handle_command(self, raw_command):
        command = raw_command.strip()
        command_lower = command.lower()
        if command_lower in ("sample", "read", "now"):
            self.sample(notify=True)
            return
        try:
            payload = json.loads(command)
        except Exception as error:
            self.write_command_status("error", "Invalid command JSON: %s" % error)
            return
        action = str(payload.get("command", "")).strip().lower()
        if action == "sample":
            self.sample(notify=True)
        elif action == "configure":
            updated = dict(self.config)
            for key in ("device_id", "device_name", "sample_interval_ms", "sensor_profile"):
                if key in payload:
                    updated[key] = payload[key]
            updated["provisioned"] = True
            self.config = normalize_config(updated)
            self._init_sensor()
            save_config(self.config)
            self._write_manifest()
            self._advertise()
            self.write_command_status("ok", "configured")
            self.sample(notify=True)
        elif action == "reset_config":
            self.config = reset_config()
            self._write_manifest()
            self._advertise()
            self.write_command_status("ok", "configuration reset")
            self.sample(notify=True)
        elif action == "status":
            self.write_command_status("ok", "status")
        else:
            self.write_command_status("error", "Unknown command: %s" % action)

    def write_command_status(self, status, message):
        response = {
            "methodmesh_sensor_command_version": "1",
            "status": status,
            "message": message,
            "device_id": self.config["device_id"],
            "device_name": self.config["device_name"],
            "sample_interval_ms": self.config["sample_interval_ms"],
            "sensor_profile": self.config["sensor_profile"],
            "provisioned": self.config["provisioned"],
        }
        self.ble.gatts_write(self.command_handle, json.dumps(response).encode("utf-8"))

    def _write_manifest(self):
        manifest = {
            "methodmesh_sensor_manifest_version": "1",
            "device_id": self.config["device_id"],
            "device_name": self.config["device_name"],
            "firmware_version": FIRMWARE_VERSION,
            "provisioned": self.config["provisioned"],
            "sample_interval_ms": self.config["sample_interval_ms"],
            "sensor_profile": self.config["sensor_profile"],
            "transport": "ble_gatt",
            "service_uuid": str(SERVICE_UUID),
            "manifest_uuid": str(MANIFEST_UUID),
            "reading_uuid": str(READING_UUID),
            "command_uuid": str(COMMAND_UUID),
            "sample_command": "sample",
            "provisioning_commands": ["configure", "reset_config", "status", "sample"],
            "supported_sensor_profiles": SUPPORTED_SENSOR_PROFILES,
            "sensors": [
                self.sensor.manifest(self.sensor_error) if self.sensor else {
                    "sensor_id": self.config["sensor_profile"] + "_1",
                    "sensor_type": self.config["sensor_profile"],
                    "present": False,
                    "status": "error",
                    "error": self.sensor_error,
                    "fields": [],
                }
            ],
        }
        manifest_json = json.dumps(manifest)
        self.ble.gatts_write(self.manifest_handle, manifest_json.encode("utf-8"))

    def sample(self, notify=False):
        now_ms = time.ticks_ms()
        reading = {
            "methodmesh_sensor_reading_version": "1",
            "device_id": self.config["device_id"],
            "device_name": self.config["device_name"],
            "firmware_version": FIRMWARE_VERSION,
            "sensor_profile": self.config["sensor_profile"],
            "sample_time_ms": now_ms,
            "status": "ok",
        }
        try:
            if self.sensor is None:
                self._init_sensor()
            if self.sensor is None:
                raise OSError(self.sensor_error or "Sensor interface not available")
            reading.update(self.sensor.read())
        except Exception as error:
            reading.update({
                "status": "error",
                "error": str(error),
            })

        reading_json = json.dumps(reading)
        reading["payload_sha256"] = sha256_hex(reading_json)
        reading_json = json.dumps(reading)
        self.latest_reading = reading
        self.ble.gatts_write(self.reading_handle, reading_json.encode("utf-8"))
        if notify:
            for conn_handle in tuple(self.connections):
                try:
                    self.ble.gatts_notify(conn_handle, self.reading_handle, reading_json.encode("utf-8"))
                except Exception:
                    pass
        return reading

    def run(self):
        last_sample = time.ticks_ms()
        while True:
            if time.ticks_diff(time.ticks_ms(), last_sample) >= self.config["sample_interval_ms"]:
                self.sample(notify=bool(self.connections))
                last_sample = time.ticks_ms()
            time.sleep_ms(200)


node = MethodMeshSensorNode()
print("MethodMesh sensor node started")
print("Device:", node.config["device_name"], node.config["device_id"])
try:
    print("I2C scan:", node.i2c.scan() if node.i2c else [])
except Exception as error:
    print("I2C scan failed:", error)
node.run()
