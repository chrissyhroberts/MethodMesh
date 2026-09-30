#!/usr/bin/env python3
"""Build the complete mesh image using littlefs-python (repository firmware venv).

Combine the hardware-verified MicroPython runtime with the existing 4 MB
partition/VFS layout. Replace main.py and the unused AHT20 driver with the mesh
runtime and radio codec via LittleFS, then verify the complete filesystem.
"""
from pathlib import Path
import struct
from littlefs import LittleFS, UserContext

MAIN = Path(__file__).resolve().parents[7]
ASSETS = MAIN / "assets" / "firmware"
BASE_IMAGE = ASSETS / "esp32c3_images/methodmesh_esp32c3_aht20.bin"
RUNTIME_IMAGE = Path(__file__).resolve().parent / "vendor/ESP32_GENERIC_C3-20260824-v1.29.0.bin"
OUTPUT = ASSETS / "esp32c3_images/methodmesh_esp32c3_espnow_mesh.bin"
VFS_OFFSET = 0x200000
VFS_SIZE = 0x200000
PARTITION_TABLE_OFFSET = 0x8000
PARTITION_TABLE_SIZE = 0x1000


def mount(image):
    fs = LittleFS(context=UserContext(buffer=bytearray(image[VFS_OFFSET:])),
                  block_size=4096, block_count=512, mount=False)
    fs.mount()  # Never auto-format an unexpected baseline.
    return fs


def files(fs, directory="/"):
    result = {}
    for name in fs.listdir(directory):
        path = directory.rstrip("/") + "/" + name
        if fs.stat(path).type == 2:
            result.update(files(fs, path))
        else:
            with fs.open(path, "rb") as handle:
                result[path] = handle.read()
    return result


def main():
    previous = BASE_IMAGE.read_bytes()
    runtime = RUNTIME_IMAGE.read_bytes()
    assert len(previous) == 4 * 1024 * 1024, "Unexpected flash size"
    assert len(runtime) < VFS_OFFSET, "MicroPython runtime overlaps VFS"
    combined = bytearray(b"\xff" * len(previous))
    combined[:len(runtime)] = runtime
    combined[PARTITION_TABLE_OFFSET:PARTITION_TABLE_OFFSET + PARTITION_TABLE_SIZE] = previous[PARTITION_TABLE_OFFSET:PARTITION_TABLE_OFFSET + PARTITION_TABLE_SIZE]
    combined[VFS_OFFSET:] = previous[VFS_OFFSET:]
    base = bytes(combined)
    entries = [struct.unpack("<HBBII16sI", base[i:i+32]) for i in range(0x8000, 0x8C00, 32)]
    assert any(e[0] == 0x50AA and e[3:5] == (VFS_OFFSET, VFS_SIZE) and e[5].rstrip(b"\0") == b"vfs" for e in entries), "Unexpected VFS partition"
    fs = mount(base)
    expected = files(fs)
    assert expected["/main.py"] == (ASSETS / "esp32c3_aht20_ble/main.py").read_bytes(), "Unexpected baseline runtime"
    assert expected["/sensor_drivers/aht20.py"] == (ASSETS / "esp32c3_aht20_ble/sensor_drivers/aht20.py").read_bytes(), "Unexpected baseline codec"
    expected["/main.py"] = (ASSETS / "esp32c3_espnow_mesh/main.py").read_bytes()
    expected["/boot.py"] = (ASSETS / "esp32c3_espnow_mesh/boot.py").read_bytes()
    expected["/methodmesh_coex.py"] = (ASSETS / "esp32c3_espnow_mesh/methodmesh_coex.py").read_bytes()
    expected["/sensor_drivers/aht20.py"] = (ASSETS / "esp32c3_espnow_mesh/radio_codec.py").read_bytes()
    for path in ("/main.py", "/boot.py", "/methodmesh_coex.py", "/sensor_drivers/aht20.py"):
        with fs.open(path, "wb") as handle:
            handle.write(expected[path])
    fs.unmount()
    image = base[:VFS_OFFSET] + bytes(fs.context.buffer)
    verify = mount(image)
    assert files(verify) == expected, "Firmware filesystem verification failed"
    verify.unmount()
    assert len(image) == len(base) and image[:VFS_OFFSET] == base[:VFS_OFFSET]
    OUTPUT.write_bytes(image)
    print(f"Wrote and remount-verified {OUTPUT} ({len(image)} bytes)")


if __name__ == "__main__":
    main()
