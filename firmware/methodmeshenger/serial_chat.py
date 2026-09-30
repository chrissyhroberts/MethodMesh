"""Laptop serial console for a MethodMeshenger node."""

import argparse
import json
import sys
import threading

import serial


def reader(port):
    while True:
        line = port.readline()
        if line:
            try:
                print(json.dumps(json.loads(line.decode(errors="replace")), indent=2), flush=True)
            except ValueError:
                print(line.decode(errors="replace"), end="", flush=True)


parser = argparse.ArgumentParser(description="Chat with an ESP-NOW node")
parser.add_argument("port")
parser.add_argument("--baud", type=int, default=115200)
args = parser.parse_args()

with serial.Serial(args.port, args.baud, timeout=1) as port:
    threading.Thread(target=reader, args=(port,), daemon=True).start()
    print("Connected. Type a message and press Enter; Ctrl-D exits.")
    for line in sys.stdin:
        port.write((line.rstrip("\n") + "\n").encode())
