#!/usr/bin/env python3
"""Build complete MethodMesh ESP32-C3 sensor images.

The phone-side installer works best when it can flash one complete image rather
than flash MicroPython and then push files over a fragile REPL connection. This
script takes the bundled ESP32-C3 MicroPython image, adds a real filesystem
partition to its partition table, formats that filesystem, and writes the
MethodMesh runtime, sensor config, and sensor drivers into it.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path


FLASH_SIZE_BYTES = 4 * 1024 * 1024
VFS_OFFSET = "0x200000"
VFS_SIZE = "0x200000"

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
ASSET_ROOT = REPO_ROOT / "app" / "src" / "main" / "assets" / "firmware"
BASE_DIR = ASSET_ROOT / "esp32c3_aht20_ble"
BASE_IMAGE = BASE_DIR / "ESP32_GENERIC_C3-20260406-v1.28.0.bin"
MAIN_PY = BASE_DIR / "main.py"
DRIVERS_DIR = BASE_DIR / "sensor_drivers"
OUTPUT_DIR = ASSET_ROOT / "esp32c3_images"


PROFILES = {
    "aht20": {
        "label": "AHT20 temperature/humidity",
        "sensor_profile": "aht20",
        "sample_interval_ms": 5000,
        "output": "methodmesh_esp32c3_aht20.bin",
    },
    "ld2410c": {
        "label": "LD2410C mmWave presence",
        "sensor_profile": "ld2410c",
        "sample_interval_ms": 1000,
        "output": "methodmesh_esp32c3_ld2410c.bin",
    },
}


def find_image_tool() -> Path:
    env_path = os.environ.get("MP_IMAGE_TOOL_ESP32")
    candidates = [
        Path(env_path) if env_path else None,
        REPO_ROOT / ".venv-firmware-tools" / "bin" / "mp-image-tool-esp32",
        shutil.which("mp-image-tool-esp32"),
    ]
    for candidate in candidates:
        if candidate is None:
            continue
        path = Path(candidate)
        if path.exists():
            return path
    raise SystemExit(
        "Could not find mp-image-tool-esp32. Install with:\n"
        "  python3 -m venv .venv-firmware-tools\n"
        "  .venv-firmware-tools/bin/pip install mp-image-tool-esp32"
    )


def run(tool: Path, *args: str) -> None:
    command = [str(tool), *args]
    print(" ".join(command))
    subprocess.run(command, check=True)


def pad_with_erased_flash_bytes(path: Path) -> None:
    data = path.read_bytes()
    if len(data) > FLASH_SIZE_BYTES:
        raise SystemExit(f"{path} is larger than the target 4 MB flash size")
    path.write_bytes(data + (b"\xff" * (FLASH_SIZE_BYTES - len(data))))


def write_profile_main_py(source: Path, target: Path, sensor_profile: str) -> None:
    text = source.read_text(encoding="utf-8")
    old = 'DEFAULT_SENSOR_PROFILE = "aht20"'
    new = f'DEFAULT_SENSOR_PROFILE = "{sensor_profile}"'
    if old not in text:
        raise SystemExit(f"Could not find {old!r} in {source}")
    target.write_text(text.replace(old, new), encoding="utf-8")


def build_profile(tool: Path, key: str, spec: dict[str, object]) -> Path:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_path = OUTPUT_DIR / str(spec["output"])

    with tempfile.TemporaryDirectory(prefix=f"methodmesh_{key}_") as tmp_name:
        tmp = Path(tmp_name)
        image_with_vfs = tmp / "micropython_with_vfs.bin"
        full_image = tmp / "methodmesh_full.bin"
        payload = tmp / "payload"
        payload_drivers = payload / "sensor_drivers"

        run(
            tool,
            str(BASE_IMAGE),
            "--add",
            f"vfs:fat:{VFS_OFFSET}:{VFS_SIZE}",
            "-o",
            str(image_with_vfs),
        )
        shutil.copyfile(image_with_vfs, full_image)
        pad_with_erased_flash_bytes(full_image)

        run(tool, str(full_image), "--fs", "mkfs")

        payload.mkdir()
        payload_drivers.mkdir()
        write_profile_main_py(MAIN_PY, payload / "main.py", str(spec["sensor_profile"]))
        shutil.copyfile(DRIVERS_DIR / "__init__.py", payload_drivers / "__init__.py")
        shutil.copyfile(DRIVERS_DIR / "aht20.py", payload_drivers / "aht20.py")
        shutil.copyfile(DRIVERS_DIR / "ld2410c.py", payload_drivers / "ld2410c.py")

        config = {
            "device_id": "methodmesh_sensor",
            "device_name": "MethodMesh-Sensor",
            "provisioned": False,
            "sensor_profile": spec["sensor_profile"],
            "sample_interval_ms": spec["sample_interval_ms"],
            "image_profile": key,
            "image_label": spec["label"],
        }
        (payload / "methodmesh_sensor_config.json").write_text(
            json.dumps(config, separators=(",", ":")),
            encoding="utf-8",
        )

        run(tool, str(full_image), "--fs", "put", str(payload / "main.py"), "vfs:")
        run(
            tool,
            str(full_image),
            "--fs",
            "put",
            str(payload / "methodmesh_sensor_config.json"),
            "vfs:",
        )
        run(tool, str(full_image), "--fs", "mkdir", "vfs:sensor_drivers")
        for driver in sorted(payload_drivers.iterdir()):
            run(tool, str(full_image), "--fs", "put", str(driver), "vfs:sensor_drivers")

        run(tool, str(full_image), "--fs", "ls", "vfs:")
        run(tool, str(full_image), "--fs", "cat", "vfs:methodmesh_sensor_config.json")

        shutil.copyfile(full_image, output_path)

    print(f"Built {spec['label']}: {output_path} ({output_path.stat().st_size} bytes)")
    return output_path


def main() -> None:
    required = [BASE_IMAGE, MAIN_PY, DRIVERS_DIR / "__init__.py", DRIVERS_DIR / "aht20.py", DRIVERS_DIR / "ld2410c.py"]
    missing = [path for path in required if not path.exists()]
    if missing:
        raise SystemExit("Missing firmware source files:\n" + "\n".join(str(path) for path in missing))

    tool = find_image_tool()
    for key, spec in PROFILES.items():
        build_profile(tool, key, spec)


if __name__ == "__main__":
    main()
