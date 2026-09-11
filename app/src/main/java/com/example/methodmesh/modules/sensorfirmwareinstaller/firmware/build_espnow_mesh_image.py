#!/usr/bin/env python3
"""Rebuild the bundled MethodMesh ESP32-C3 ESP-NOW mesh-node image.

The checked-in AHT20 full image contains the same MicroPython base and LittleFS
slots used by all current MethodMesh ESP32-C3 images. This builder verifies the
known baseline layout, replaces the `main.py` CTZ data payload with the mesh-node
runtime, and repurposes the unused AHT20 driver slot as the mesh radio-fragment
codec. Both files are padded with valid Python comment bytes to preserve the
existing LittleFS metadata and file sizes.

If the baseline filesystem layout changes, this script refuses to build. At that
point regenerate all images with the repository's full LittleFS image tooling
rather than guessing new offsets.
"""

from pathlib import Path

HERE = Path(__file__).resolve()
MAIN = HERE.parents[7]  # .../main
ASSETS = MAIN / "assets" / "firmware"
BASE_IMAGE = ASSETS / "esp32c3_images" / "methodmesh_esp32c3_aht20.bin"
BASE_MAIN = ASSETS / "esp32c3_aht20_ble" / "main.py"
MESH_MAIN = ASSETS / "esp32c3_espnow_mesh" / "main.py"
BASE_CODEC = ASSETS / "esp32c3_aht20_ble" / "sensor_drivers" / "aht20.py"
MESH_CODEC = ASSETS / "esp32c3_espnow_mesh" / "radio_codec.py"
OUTPUT = ASSETS / "esp32c3_images" / "methodmesh_esp32c3_espnow_mesh.bin"
CODEC_OFFSET = 0x365000

# LittleFS CTZ data spans for main.py in the current 4 MB checked-in image.
MAIN_DATA_SPANS = (
    (0x23A000, 4096),
    (0x23B004, 4092),
    (0x23C008, 4088),
)


def spans_for_size(size: int):
    fixed = sum(length for _, length in MAIN_DATA_SPANS)
    if size < fixed:
        raise RuntimeError(f"Unexpected baseline main.py size: {size}")
    return MAIN_DATA_SPANS + ((0x23D004, size - fixed),)



def padded(source: bytes, target_size: int) -> bytes:
    if len(source) > target_size:
        raise RuntimeError(f"Replacement file is {len(source)} bytes; slot is {target_size} bytes")
    padding = target_size - len(source)
    if padding == 0:
        return source
    if padding < 3:
        return source + (b" " * padding)
    return source + b"\n#" + (b" " * (padding - 3)) + b"\n"

def main() -> None:
    image = bytearray(BASE_IMAGE.read_bytes())
    baseline_main = BASE_MAIN.read_bytes()
    mesh_main = MESH_MAIN.read_bytes()
    baseline_codec = BASE_CODEC.read_bytes()
    mesh_codec = MESH_CODEC.read_bytes()
    spans = spans_for_size(len(baseline_main))

    baseline_in_image = b"".join(image[offset:offset + length] for offset, length in spans)
    if baseline_in_image != baseline_main:
        raise RuntimeError("Baseline LittleFS layout changed; refusing to patch the image")
    if len(mesh_main) > len(baseline_main):
        raise RuntimeError(
            f"Mesh main.py is {len(mesh_main)} bytes; current LittleFS slot is {len(baseline_main)} bytes"
        )

    payload = padded(mesh_main, len(baseline_main))
    if image[CODEC_OFFSET:CODEC_OFFSET + len(baseline_codec)] != baseline_codec:
        raise RuntimeError("Baseline AHT20 driver slot changed; refusing to patch mesh codec")
    codec_payload = padded(mesh_codec, len(baseline_codec))

    cursor = 0
    for offset, length in spans:
        image[offset:offset + length] = payload[cursor:cursor + length]
        cursor += length

    image[CODEC_OFFSET:CODEC_OFFSET + len(codec_payload)] = codec_payload
    check = b"".join(image[offset:offset + length] for offset, length in spans)
    if check != payload:
        raise RuntimeError("Image verification failed after writing mesh runtime")
    if image[CODEC_OFFSET:CODEC_OFFSET + len(codec_payload)] != codec_payload:
        raise RuntimeError("Image verification failed after writing radio codec")

    OUTPUT.write_bytes(image)
    print(f"Wrote {OUTPUT} ({len(image)} bytes; mesh main.py {len(mesh_main)} bytes; radio codec {len(mesh_codec)} bytes)")


if __name__ == "__main__":
    main()
