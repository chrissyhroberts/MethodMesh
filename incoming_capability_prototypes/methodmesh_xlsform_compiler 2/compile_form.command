#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
if [ ! -x .venv/bin/python ]; then
  echo "First-time setup: creating local Python environment..."
  python3 -m venv .venv
  .venv/bin/python -m pip install --upgrade pip
  .venv/bin/python -m pip install -e .
fi
SOURCE="$1"
if [ -z "$SOURCE" ]; then
  echo "Drag an XLSForm onto this file, or enter its path:"
  read -r SOURCE
fi
if [ ! -f "$SOURCE" ]; then
  echo "File not found: $SOURCE"
  exit 2
fi
OUTDIR="${SOURCE%/*}/methodmesh_build"
mkdir -p "$OUTDIR"
exec .venv/bin/mmxls compile "$SOURCE" -o "$OUTDIR" --overwrite
