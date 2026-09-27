#!/bin/bash
set -e
cd "$(dirname "$0")"
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -e .
echo
echo "Installed. You can now run:"
echo "  ./compile_form.command /path/to/form.xlsx"
echo
read -r -p "Press Enter to close..." _
