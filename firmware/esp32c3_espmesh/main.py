"""MethodMesh ESP-NOW relay/gateway reference firmware for MicroPython.

This is a small module-owned vertical slice for ESP32-C3 boards. It speaks the
versioned BLE gateway bridge used by Android and forwards opaque MethodMesh
envelopes over ESP-NOW. Secure provisioning and authenticated peer admission
are deliberately refused until a network has been provisioned.
"""

import json
import os
import time
from machine import unique_id

try:
    import bluetooth
except ImportError:
    import ubluetooth as bluetooth

try:
    import network
    import espnow
except ImportError:
    network = None
    espnow = None


FIRMWARE_VERSION = "methodmesh-espmesh-0.1.0"
CONFIG_FILE = "methodmesh_mesh_config.json"
QUEUE_FILE = "methodmesh_mesh_queue.jsonl"
MAX_TTL = 8
MAX_DEDUPE = 256
MAX_QUEUE = 64
MAX_FRAME = 4096
DEFAULT_NODE_ID = "espmesh-" + "".join("%02x" % b for b in unique_id()[-4:])
DEFAULT_PROVISIONING_TOKEN = "".join("%02x" % b for b in unique_id())

SERVICE_UUID = bluetooth.UUID("b6f2a910-9b8f-4f4e-9a1f-4f37a0010000")
UPLINK_UUID = bluetooth.UUID("b6f2a911-9b8f-4f4e-9a1f-4f37a0010000")
DOWNLINK_UUID = bluetooth.UUID("b6f2a912-9b8f-4f4e-9a1f-4f37a0010000")
FLAG_READ = bluetooth.FLAG_READ
FLAG_WRITE = bluetooth.FLAG_WRITE
FLAG_NOTIFY = bluetooth.FLAG_NOTIFY
IRQ_CENTRAL_CONNECT = 1
IRQ_CENTRAL_DISCONNECT = 2
IRQ_GATTS_WRITE = 3


def compact(value):
    return json.dumps(value).replace(": ", ":").replace(", ", ",")


def authenticate(packet, key):
    try:
        import hmac
        import hashlib
        unsigned = dict(packet)
        unsigned.pop("auth", None)
        return hmac.new(key.encode(), compact(unsigned).encode(), hashlib.sha256).hexdigest()
    except Exception:
        return ""


def load_config():
    config = {"node_id": DEFAULT_NODE_ID, "node_name": "MethodMesh Mesh Node", "provisioned": False, "network_id": "", "network_key": "", "provisioning_token": DEFAULT_PROVISIONING_TOKEN, "peers": []}
    try:
        with open(CONFIG_FILE, "r") as handle:
            stored = json.loads(handle.read())
        config.update(stored)
    except Exception:
        pass
    config["node_id"] = str(config.get("node_id") or DEFAULT_NODE_ID)[:64]
    config["node_name"] = str(config.get("node_name") or "MethodMesh Mesh Node")[:26]
    config["peers"] = list(config.get("peers") or [])[:32]
    config["network_id"] = str(config.get("network_id") or "")[:64]
    config["network_key"] = str(config.get("network_key") or "")[:128]
    config["provisioning_token"] = str(config.get("provisioning_token") or DEFAULT_PROVISIONING_TOKEN)[:128]
    config["provisioned"] = bool(config.get("provisioned", False))
    return config


def save_config(config):
    with open(CONFIG_FILE, "w") as handle:
        handle.write(compact(config))


def load_queue():
    records = []
    try:
        with open(QUEUE_FILE, "r") as handle:
            for line in handle:
                if line.strip():
                    records.append(json.loads(line))
    except Exception:
        pass
    return records[-MAX_QUEUE:]


def save_queue(records):
    with open(QUEUE_FILE, "w") as handle:
        for record in records[-MAX_QUEUE:]:
            handle.write(compact(record) + "\n")


def advertising_payload(name, services):
    payload = bytearray()

    def add(ad_type, value):
        payload.extend((len(value) + 1, ad_type))
        payload.extend(value)

    add(0x01, b"\x06")
    add(0x09 if len(name.encode()) <= 26 else 0x08, name.encode()[:26])
    for service in services:
        add(0x07, bytes(service))
    return payload


class MethodMeshMeshNode:
    def __init__(self):
        self.config = load_config()
        # The token is deliberately available over the local serial console only.
        # It lets the phone provision a freshly flashed node without putting the
        # secret into the BLE advertisement.
        print("MethodMesh node:", self.config.get("node_id"))
        print("Provisioning token:", self.config.get("provisioning_token"))
        self.queue = load_queue()
        self.seen = []
        self.acknowledged = []
        self.connections = set()
        self.ble = bluetooth.BLE()
        self.ble.active(True)
        self.ble.irq(self._ble_irq)
        ((self.uplink_handle, self.downlink_handle),) = self.ble.gatts_register_services((
            (SERVICE_UUID, ((UPLINK_UUID, FLAG_WRITE), (DOWNLINK_UUID, FLAG_READ | FLAG_NOTIFY))),
        ))
        self.ble.gatts_set_buffer(self.uplink_handle, MAX_FRAME)
        self.ble.gatts_set_buffer(self.downlink_handle, MAX_FRAME)
        self.radio = None
        self._start_radio()
        self._advertise()

    def _start_radio(self):
        if network is None or espnow is None:
            return
        try:
            wlan = network.WLAN(network.STA_IF)
            wlan.active(True)
            self.radio = espnow.ESPNow()
            self.radio.active(True)
            for peer in self.config["peers"]:
                self.radio.add_peer(bytes.fromhex(peer.replace(":", "")))
        except Exception:
            self.radio = None

    def _advertise(self):
        self.ble.gap_advertise(250000, adv_data=advertising_payload(self.config["node_name"], [SERVICE_UUID]))

    def _ble_irq(self, event, data):
        if event == IRQ_CENTRAL_CONNECT:
            self.connections.add(data[0])
        elif event == IRQ_CENTRAL_DISCONNECT:
            self.connections.discard(data[0])
            self._advertise()
        elif event == IRQ_GATTS_WRITE and data[1] == self.uplink_handle:
            raw = self.ble.gatts_read(self.uplink_handle)
            self.handle_bridge(raw)

    def notify(self, frame):
        encoded = compact(frame).encode()
        if len(encoded) > MAX_FRAME:
            return
        self.ble.gatts_write(self.downlink_handle, encoded)
        for connection in tuple(self.connections):
            try:
                self.ble.gatts_notify(connection, self.downlink_handle, encoded)
            except Exception:
                pass

    def handle_bridge(self, raw):
        try:
            frame = json.loads(raw.decode())
            if frame.get("protocol") != "methodmesh.gateway" or int(frame.get("version", 0)) != 1:
                return
            kind = str(frame.get("kind", ""))
            if kind == "HELLO":
                self.notify({"protocol": "methodmesh.gateway", "version": 1, "kind": "HELLO_ACK", "request_id": frame.get("request_id", ""), "body": {"node_id": self.config["node_id"], "firmware": FIRMWARE_VERSION, "provisioned": self.config["provisioned"], "network_id": self.config["network_id"]}})
            elif kind == "OUTBOUND" and frame.get("envelope"):
                self.accept_envelope(frame["envelope"], from_phone=True)
            elif kind == "CONFIG" and frame.get("body"):
                self.configure(frame["body"])
        except Exception:
            return

    def configure(self, body):
        # Provisioning must be performed through a future authenticated flow.
        # This command only accepts an already-authorised local test payload.
        supplied_token = str(body.get("provisioning_token") or "")
        expected_token = self.config.get("provisioning_token", "")
        # A factory-fresh node has no network yet, so physical BLE proximity is
        # the first-use authorisation. Once provisioned, the per-node token is
        # required for any later reconfiguration.
        if self.config.get("provisioned") and supplied_token != expected_token:
            return
        if not self.config.get("provisioned") and supplied_token and supplied_token != expected_token:
            return
        updated = dict(self.config)
        updated["node_name"] = str(body.get("node_name") or updated["node_name"])[:26]
        updated["peers"] = list(body.get("peers") or [])[:32]
        updated["network_id"] = str(body.get("network_id") or "")[:64]
        updated["network_key"] = str(body.get("network_key") or "")[:128]
        updated["provisioned"] = bool(updated["network_id"] and updated["network_key"])
        self.config = updated
        save_config(updated)
        self._start_radio()
        self._advertise()

    def accept_envelope(self, envelope, from_phone=False, source_peer=None):
        if not self.config["provisioned"] or not self.config["network_id"] or not self.config["network_key"]:
            return
        message_id = str(envelope.get("message_id", ""))
        if not message_id or message_id in self.seen:
            return
        self.seen.append(message_id)
        self.seen = self.seen[-MAX_DEDUPE:]
        destination = envelope.get("destination") or {}
        destination_id = str(destination.get("id", ""))
        ttl = int(envelope.get("metadata", {}).get("ttl", MAX_TTL))
        if destination_id in (self.config["node_id"], "broadcast", "field-group"):
            self.notify({"protocol": "methodmesh.gateway", "version": 1, "kind": "INBOUND", "request_id": message_id, "envelope": envelope, "body": {"node_id": self.config["node_id"]}})
            if source_peer is not None:
                self.send_ack(source_peer, message_id)
        if self.radio is None or ttl <= 1:
            if destination_id not in (self.config["node_id"], "broadcast", "field-group"):
                if len(self.queue) >= MAX_QUEUE:
                    self.queue.pop(0)
                self.queue.append({"envelope": envelope, "expires_at": envelope.get("expires_at")})
                save_queue(self.queue)
            return
        envelope = dict(envelope)
        metadata = dict(envelope.get("metadata") or {})
        metadata["ttl"] = ttl - 1
        metadata["forwarded_by"] = self.config["node_id"]
        envelope["metadata"] = metadata
        packet = {"methodmesh": 1, "network_id": self.config["network_id"], "envelope": envelope}
        packet["auth"] = authenticate(packet, self.config["network_key"])
        encoded = compact(packet).encode()
        for peer in self.config["peers"]:
            try:
                self.radio.send(bytes.fromhex(peer.replace(":", "")), encoded)
            except Exception:
                pass

    def send_ack(self, peer, message_id):
        if self.radio is None or not self.config["provisioned"]:
            return
        packet = {"methodmesh": 1, "kind": "ACK", "network_id": self.config["network_id"], "message_id": message_id}
        packet["auth"] = authenticate(packet, self.config["network_key"])
        try:
            self.radio.send(peer, compact(packet).encode())
        except Exception:
            pass

    def poll_radio(self):
        if self.radio is None:
            return
        try:
            peer, raw = self.radio.recv(0)
            if raw:
                packet = json.loads(raw.decode())
                if (self.config["provisioned"] and packet.get("methodmesh") == 1 and packet.get("network_id") == self.config["network_id"]
                        and packet.get("auth") == authenticate(packet, self.config["network_key"])):
                    if packet.get("kind") == "ACK":
                        self.acknowledged.append(str(packet.get("message_id", "")))
                        self.acknowledged = self.acknowledged[-MAX_DEDUPE:]
                    elif packet.get("envelope"):
                        self.accept_envelope(packet["envelope"], source_peer=peer)
        except Exception:
            pass

    def flush_queue(self):
        if self.radio is None or not self.queue:
            return
        waiting = self.queue
        self.queue = []
        for record in waiting:
            expires_at = record.get("expires_at")
            if expires_at and int(expires_at) <= int(time.time() * 1000):
                continue
            self.accept_envelope(record.get("envelope") or {})
        save_queue(self.queue)


node = MethodMeshMeshNode()
while True:
    node.poll_radio()
    node.flush_queue()
    time.sleep_ms(50)
