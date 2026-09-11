#!/usr/bin/env python3
"""Host-side regression checks for the MicroPython-compatible ESP-NOW codec."""
import importlib.util, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[7]
CODEC = ROOT / "assets" / "firmware" / "esp32c3_espnow_mesh" / "radio_codec.py"
spec = importlib.util.spec_from_file_location("radio_codec", CODEC)
codec = importlib.util.module_from_spec(spec); spec.loader.exec_module(codec)

wire = {
    "v": 1, "id": "12345678-1234-1234-1234-123456789abc", "kid": "0123456789abcdef",
    "op": "phone-a", "sk": "mesh-phone", "s": "phone-a", "dk": "logical", "d": "field-group",
    "c": 1, "ttl": 8, "n": "nonce", "ct": "ciphertext-" + "x" * 1600,
}
text = json.dumps({"k": "D", "w": wire}, separators=(",", ":"))
frames = codec.fragment(text, "fieldnet", "transport-secret", 250)
assert frames and max(map(len, frames)) <= 250
r = codec.Reassembler(); recovered = None
for raw in frames:
    recovered = r.accept(json.loads(raw), "fieldnet", "transport-secret") or recovered
assert recovered == text

# Wrong network key must not authenticate/reassemble.
r = codec.Reassembler(); bad = None
for raw in frames:
    bad = r.accept(json.loads(raw), "fieldnet", "wrong-key") or bad
assert bad is None

# Radio control MAC changes if protected content changes.
p = {"methodmesh": 2, "kind": "S", "network_id": "fieldnet", "message_id": "abc"}
a = codec.ap(p, "transport-secret")
p["message_id"] = "def"
assert codec.ap(p, "transport-secret") != a
print("ESP mesh radio codec self-test: PASS")

# Oversized fragment indexes/data are rejected before unbounded reassembly.
r = codec.Reassembler()
assert r.accept({"f":1,"n":codec.nt("fieldnet"),"i":"x","s":999,"z":0,"d":"x","a":"y"}, "fieldnet", "transport-secret") is None
assert r.accept({"f":1,"n":codec.nt("fieldnet"),"i":"x","s":0,"z":0,"d":"x"*200,"a":"y"}, "fieldnet", "transport-secret") is None
