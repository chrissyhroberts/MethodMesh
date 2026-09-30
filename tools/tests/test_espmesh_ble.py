import ast
import base64
import binascii
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/assets/firmware/esp32c3_espnow_mesh/main.py"
FIXTURE = ROOT / "app/src/test/resources/espmesh_config_ack.json"


def firmware_namespace():
    tree = ast.parse(SOURCE.read_text())
    # Use the actual framing methods, without booting the hardware loop.
    node = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == "Node")
    compact = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == "compact")
    constants = next(n for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "BLE_PACKET" for t in n.targets))
    chunk = next(n for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "BLE_CHUNK" for t in n.targets))
    ns = dict(json=json, binascii=binascii)
    exec(compile(ast.Module(body=[constants, chunk, compact, node], type_ignores=[]), str(SOURCE), "exec"), ns)
    return ns


def packets(frame, payload=253):
    ns = firmware_namespace()
    class BLE:
        def gatts_write(self, *_): pass
        def gatts_notify(self, connection, characteristic, data):
            assert len(data) <= payload
            output.append(data)
    output = []
    node = ns["Node"].__new__(ns["Node"])
    node.ble = BLE()
    node.down = 1
    node.conn = {1: payload}
    node.tx = 0
    node.notify_raw(frame)
    return output


def config_frame():
    return dict(protocol="methodmesh.gateway", version=2, kind="CONFIG_ACK", request_id="config-bench-01",
                body=dict(node_id="espmesh-01234567", firmware="methodmesh-espmesh-0.4.1", provisioned=True,
                          network_id="bench-test-01", pending_for_radio=3, pending_for_phone=2,
                          spool_error="", last_radio_at_ms=0))


class BleDownlinkTest(unittest.TestCase):
    def test_reproduces_old_253_byte_truncation(self):
        raw = json.dumps(config_frame(), separators=(",", ":")).encode()
        self.assertTrue(253 < len(raw) <= 460)
        with self.assertRaises(json.JSONDecodeError):
            json.loads(raw[:253])

    def test_all_frame_sizes_fit_including_wrapper_and_base64(self):
        for payload in (180, 182, 244, 253, 509):
            for size in (1, 179, 180, 181, 253, 460, 32768, 65535):
                raw = b"x" * size
                encoded = packets(raw, payload)
                self.assertTrue(encoded)
                self.assertLessEqual(max(map(len, encoded)), 180)
                if len(encoded) == 1 and encoded[0] == raw:
                    continue
                parts = [json.loads(p) for p in encoded]
                self.assertLessEqual(len(parts), 2048)
                self.assertEqual(raw, b"".join(base64.b64decode(p["data"]) for p in parts))

    def test_config_fixture_is_produced_by_actual_firmware(self):
        fixture = json.loads(FIXTURE.read_text())
        self.assertEqual(config_frame(), fixture["frame"])
        raw = json.dumps(config_frame(), separators=(",", ":")).encode()
        self.assertEqual(fixture["packets"], [p.decode() for p in packets(raw)])

    def test_no_notifications_before_payload_is_known_or_at_default_mtu(self):
        self.assertEqual([], packets(b"x" * 400, 0))
        self.assertEqual([], packets(b"x" * 400, 20))

    def test_live_voice_and_unicode_remain_opaque(self):
        for frame in (dict(kind="LIVE_VOICE_RX", body={"packet": base64.b64encode(bytes(range(220))).decode()}),
                      dict(kind="DATA", body={"text": "é🧭" * 200})):
            raw = json.dumps(frame, ensure_ascii=False).encode()
            self.assertEqual(raw, b"".join(base64.b64decode(json.loads(p)["data"]) for p in packets(raw)))


if __name__ == "__main__":
    unittest.main()
