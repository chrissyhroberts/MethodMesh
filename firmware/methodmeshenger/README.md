# MethodMeshenger

The small, serial-first ESP-NOW messenger testbed.

This is intentionally separate from the Android MethodMesh transport. The
first milestone is boring on purpose: flash two ESP32 boards, connect each to
a laptop, and exchange messages over ESP-NOW. BLE, Android and MethodMesh
integration come later.

## First test

1. Flash a MicroPython image suitable for the board.
2. Copy `boot.py` and `main.py` to each board.
3. Open one serial console per board at 115200 baud.
4. Send a line of text in either console.

The node prints JSON events for received messages and accepts plain text for
outgoing broadcast messages. The first version uses broadcast deliberately so
there is no pairing or provisioning ceremony while the radio path is being
proved.

The wire envelope has a version, message ID, sender, sequence number, type,
payload and CRC. Duplicate messages are ignored locally. This is a transport
testbed, not yet a secure or production messenger.

## Laptop tooling

The optional `serial_chat.py` helper provides the same interface from a laptop
when `pyserial` is installed:

```text
python3 serial_chat.py /dev/cu.usbmodemXXXX
```

Use two terminals and two boards. On Windows, pass the relevant `COM` port.
