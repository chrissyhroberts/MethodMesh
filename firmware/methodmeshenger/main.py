"""MethodMeshenger v0.1: the smallest useful ESP-NOW chat node.

Serial input is broadcast over ESP-NOW. Received frames are printed as JSON.
This deliberately has no BLE, provisioning, encryption or MethodMesh code.
"""

import binascii
import json
import time
from machine import unique_id

import network
import espnow


VERSION = 1
NODE_ID = "node-" + binascii.hexlify(unique_id()[-4:]).decode()
BROADCAST = b"\xff\xff\xff\xff\xff\xff"
NODE_1_MAC = b"\x44\xb1\x76\x02\x16\xe0"
NODE_2_MAC = b"\x44\xb1\x76\x07\x91\x30"
MAX_PAYLOAD = 180
RADIO_CHANNEL = 6
seen = []
sequence = 0


def emit(event, **body):
    record = {"event": event, "node_id": NODE_ID, "ts_ms": time.ticks_ms()}
    record.update(body)
    print(json.dumps(record))


def mac_text(mac):
    return ":".join("%02x" % byte for byte in mac)


def checksum(frame):
    # Do not hash serialized JSON: MicroPython may emit dictionary keys in a
    # different order on another device. Use an explicit, stable field order.
    raw = "|".join((
        str(frame.get("v", "")),
        str(frame.get("id", "")),
        str(frame.get("from", "")),
        str(frame.get("seq", "")),
        str(frame.get("type", "")),
        str(frame.get("payload", "")),
    )).encode()
    return "%08x" % (binascii.crc32(raw) & 0xffffffff)


def make_frame(text):
    global sequence
    sequence += 1
    frame = {
        "v": VERSION,
        "id": NODE_ID + "-" + str(sequence),
        "from": NODE_ID,
        "seq": sequence,
        "type": "text",
        "payload": text[:MAX_PAYLOAD],
    }
    frame["crc"] = checksum(frame)
    return frame


def valid_frame(frame):
    return (
        isinstance(frame, dict)
        and frame.get("v") == VERSION
        and frame.get("type") == "text"
        and isinstance(frame.get("id"), str)
        and isinstance(frame.get("payload"), str)
        and frame.get("crc") == checksum(frame)
    )


def remember(message_id):
    if message_id in seen:
        return False
    seen.append(message_id)
    del seen[:-128]
    return True


def process_incoming(host, raw):
    if raw is None:
        return
    emit("received_raw", peer=mac_text(host), bytes=len(raw))
    try:
        frame = json.loads(raw.decode())
        if not valid_frame(frame):
            emit("invalid", peer=mac_text(host), frame=frame, expected_crc=checksum(frame), received_crc=frame.get("crc"))
        elif remember(frame["id"]):
            emit("message", frame=frame, peer=mac_text(host), channel=RADIO_CHANNEL)
    except Exception as error:
        emit("error", stage="receive", detail=str(error), peer=mac_text(host))


def setup_radio():
    # ESP-NOW peers must share the same Wi-Fi channel. Reset both virtual
    # interfaces so a previous soft-reset or Wi-Fi connection cannot leave a
    # board on an unexpected channel.
    wlan = network.WLAN(network.WLAN.IF_STA)
    network.WLAN(network.WLAN.IF_AP).active(False)
    wlan.active(False)
    wlan.active(True)
    try:
        wlan.disconnect()
    except Exception:
        pass
    wlan.config(channel=RADIO_CHANNEL)
    try:
        wlan.config(pm=wlan.PM_NONE)
    except Exception:
        pass
    radio = espnow.ESPNow()
    radio.active(True)
    local_mac = wlan.config("mac")
    peer_mac = NODE_2_MAC if local_mac == NODE_1_MAC else NODE_1_MAC
    peer_registered = False
    try:
        radio.add_peer(peer_mac, channel=RADIO_CHANNEL)
        peer_registered = True
    except Exception:
        pass
    return radio, local_mac, peer_mac, peer_registered


radio, local_mac, peer_mac, peer_registered = setup_radio()
emit("ready", mode="unicast", local_mac=mac_text(local_mac), peer=mac_text(peer_mac), peer_registered=peer_registered, channel=RADIO_CHANNEL, max_payload=MAX_PAYLOAD)


def radio_receive_callback(radio_instance):
    while True:
        host, raw = radio_instance.irecv(0)
        if host is None:
            return
        process_incoming(host, raw)


radio.irq(radio_receive_callback)

while True:
    try:
        import uselect
        poll = uselect.poll()
        poll.register(__import__("sys").stdin, uselect.POLLIN)
        if poll.poll(0):
            line = __import__("sys").stdin.readline().strip()
            if line:
                frame = make_frame(line)
                sent = radio.send(peer_mac, json.dumps(frame).encode(), True)
                emit("sent", frame=frame, local_mac=mac_text(local_mac), peer=mac_text(peer_mac), peer_registered=peer_registered, radio_ok=sent, configured_channel=RADIO_CHANNEL, live_channel=network.WLAN(network.WLAN.IF_STA).config("channel"))
    except Exception as error:
        emit("error", stage="send", detail=str(error))

    time.sleep_ms(20)
