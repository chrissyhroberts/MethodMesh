#!/usr/bin/env python3
"""Host-side checks for the compact authenticated live-voice ESP-NOW envelope."""
import ast
import binascii
import hashlib
import hmac
from pathlib import Path

ROOT = Path(__file__).resolve().parents[7]
MAIN = ROOT / "assets" / "firmware" / "esp32c3_espnow_mesh" / "main.py"
source = MAIN.read_text()
tree = ast.parse(source)
needed = {"sh", "hr", "lw", "lu", "li"}
module = ast.Module(body=[n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in needed], type_ignores=[])
ns = {"hashlib": hashlib, "binascii": binascii}
exec(compile(module, str(MAIN), "exec"), ns)

assert ns["hr"]("transport-secret", b"known-message") == hmac.new(b"transport-secret", b"known-message", hashlib.sha256).digest()
payload = b"MV" + bytes(range(204))  # 206 bytes: current maximum Android live-voice packet.
wrapped = ns["lw"](payload, "fieldnet", "transport-secret", 2)
assert wrapped is not None
assert len(wrapped) <= 250
assert ns["lu"](wrapped, "fieldnet", "transport-secret") == (2, payload)
assert ns["lu"](wrapped, "fieldnet", "wrong-key") is None
assert ns["lu"](wrapped, "other-network", "transport-secret") is None

mutated = bytearray(wrapped)
mutated[-1] ^= 1
assert ns["lu"](bytes(mutated), "fieldnet", "transport-secret") is None

relayed = ns["lw"](payload, "fieldnet", "transport-secret", 1)
assert ns["lu"](relayed, "fieldnet", "transport-secret") == (1, payload)
assert ns["li"](payload) == ns["li"](payload)
assert ns["li"](payload) != ns["li"](payload[:-1] + b"x")
print("ESP mesh live-voice radio self-test: PASS")
