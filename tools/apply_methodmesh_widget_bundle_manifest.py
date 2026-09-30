#!/usr/bin/env python3
"""Idempotently register the MethodMesh widget bundle pop-out Activity.

Run from the MethodMesh repository root after copying the v3.5 widget files.
The script preserves the existing manifest text and inserts only the one
required Activity declaration if it is not already present.
"""

from pathlib import Path
import re
import shutil
import sys

MANIFEST = Path("app/src/main/AndroidManifest.xml")
ACTIVITY_NAME = ".widgets.MethodMeshWidgetBundleActivity"
ACTIVITY_BLOCK = '''        <activity
            android:name=".widgets.MethodMeshWidgetBundleActivity"
            android:excludeFromRecents="true"
            android:exported="false"
            android:taskAffinity=""
            android:theme="@style/Theme.MethodMesh.WidgetPopup" />
'''


def main() -> int:
    if not MANIFEST.exists():
        print(f"ERROR: {MANIFEST} not found. Run this from the MethodMesh repository root.", file=sys.stderr)
        return 2

    text = MANIFEST.read_text(encoding="utf-8")
    if ACTIVITY_NAME in text:
        print("Manifest already contains MethodMeshWidgetBundleActivity; no change needed.")
        return 0

    receiver_pattern = re.compile(
        r'(?m)^\s*<receiver\s*\n\s*android:name="\.widgets\.MethodMeshWidgetProvider"'
    )
    match = receiver_pattern.search(text)
    if not match:
        print(
            "ERROR: Could not locate MethodMeshWidgetProvider receiver in AndroidManifest.xml.",
            file=sys.stderr,
        )
        return 3

    backup = MANIFEST.with_suffix(MANIFEST.suffix + ".pre_widget_bundle_popup")
    if not backup.exists():
        shutil.copy2(MANIFEST, backup)

    updated = text[: match.start()] + ACTIVITY_BLOCK + text[match.start() :]
    MANIFEST.write_text(updated, encoding="utf-8")
    print("Registered MethodMeshWidgetBundleActivity in AndroidManifest.xml")
    print(f"Backup: {backup}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
