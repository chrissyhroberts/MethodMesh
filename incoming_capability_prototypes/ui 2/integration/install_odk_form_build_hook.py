#!/usr/bin/env python3
"""Idempotently enable MethodMesh XLSForm catalogue generation in the app module.

Run from the Android app-module directory (the directory containing build.gradle.kts):
    python3 src/main/java/com/example/methodmesh/ui/integration/install_odk_form_build_hook.py

This appends one `apply(from = ...)` statement if it is not already present.
"""
from pathlib import Path
import sys

HOOK = 'apply(from = "src/main/java/com/example/methodmesh/ui/integration/odk-template-assets.gradle.kts")'

def main() -> int:
    gradle = Path("build.gradle.kts")
    if not gradle.is_file():
        print("ERROR: build.gradle.kts not found. Run this from the app-module directory.", file=sys.stderr)
        return 2
    text = gradle.read_text(encoding="utf-8")
    if HOOK in text:
        print("MethodMesh ODK/Kobo template build hook is already installed.")
        return 0
    with gradle.open("a", encoding="utf-8") as fh:
        if not text.endswith("\n"):
            fh.write("\n")
        fh.write("\n// MethodMesh module-owned XLSForm design-template catalogue\n")
        fh.write(HOOK + "\n")
    print("Installed MethodMesh ODK/Kobo template build hook in build.gradle.kts.")
    print("Rebuild the app; generateMethodMeshOdkTemplateAssets will run before asset merge.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
