#!/usr/bin/env python3
"""Build MethodMesh's deliberately sparse global emergency exit POI core.

The script is deterministic given the same inputs and --source-version. It does
not fetch from the network. Release engineering supplies reviewed source files.

Primary airport input: OurAirports airports.csv. Only airports declaring
scheduled_service=yes are included by default, keeping the baked core small and
focused on plausible civilian exit infrastructure.

Optional normalized CSVs for ports, land borders and diplomatic missions use:
id,name,latitude,longitude,country_code,represented_country_codes,code,source_id,source_version

No dense hospital/pharmacy/police/AED data belongs in this global core.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
from typing import Iterable

HEADER = [
    "id", "name", "category", "latitude", "longitude", "country_code",
    "represented_country_codes", "code", "source_id", "source_version",
    "operational_status",
]


def clean(value: str | None) -> str:
    return (value or "").strip()


def airport_rows(path: Path, source_version: str) -> Iterable[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            if clean(row.get("scheduled_service")).lower() != "yes":
                continue
            airport_type = clean(row.get("type")).lower()
            if airport_type in {"closed", "heliport", "balloonport"}:
                continue
            lat = clean(row.get("latitude_deg"))
            lon = clean(row.get("longitude_deg"))
            try:
                float(lat); float(lon)
            except ValueError:
                continue
            code = clean(row.get("iata_code")) or clean(row.get("gps_code")) or clean(row.get("ident"))
            yield {
                "id": f"ourairports:{clean(row.get('id')) or clean(row.get('ident'))}",
                "name": clean(row.get("name")),
                "category": "AIRPORT",
                "latitude": lat,
                "longitude": lon,
                "country_code": clean(row.get("iso_country")).upper(),
                "represented_country_codes": "",
                "code": code,
                "source_id": "ourairports",
                "source_version": source_version,
                "operational_status": "unknown",
            }


def normalized_rows(path: Path, category: str) -> Iterable[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            lat, lon = clean(row.get("latitude")), clean(row.get("longitude"))
            try:
                float(lat); float(lon)
            except ValueError:
                continue
            yield {
                "id": clean(row.get("id")),
                "name": clean(row.get("name")),
                "category": category,
                "latitude": lat,
                "longitude": lon,
                "country_code": clean(row.get("country_code")).upper(),
                "represented_country_codes": clean(row.get("represented_country_codes")).upper(),
                "code": clean(row.get("code")),
                "source_id": clean(row.get("source_id")),
                "source_version": clean(row.get("source_version")),
                "operational_status": "unknown",
            }


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--airports", type=Path, required=True, help="Reviewed OurAirports airports.csv")
    ap.add_argument("--source-version", required=True, help="Source snapshot date/version, e.g. 2026-09-02")
    ap.add_argument("--ports", type=Path)
    ap.add_argument("--borders", type=Path)
    ap.add_argument("--diplomatic", type=Path)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--manifest", type=Path, required=True)
    ap.add_argument("--max-bytes", type=int, default=2_000_000)
    args = ap.parse_args()

    rows = list(airport_rows(args.airports, args.source_version))
    optional = [
        (args.ports, "PORT_OR_FERRY"),
        (args.borders, "LAND_BORDER"),
        (args.diplomatic, "EMBASSY"),
    ]
    for path, category in optional:
        if path:
            rows.extend(normalized_rows(path, category))

    rows = sorted(rows, key=lambda r: (r["category"], r["country_code"], r["name"].casefold(), r["id"]))
    ids = [r["id"] for r in rows]
    if len(ids) != len(set(ids)):
        raise SystemExit("Duplicate POI IDs in generated core.")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=HEADER, lineterminator="\n")
        writer.writeheader(); writer.writerows(rows)

    size = args.output.stat().st_size
    if size > args.max_bytes:
        raise SystemExit(f"Generated core is {size:,} bytes; budget is {args.max_bytes:,} bytes")

    counts: dict[str, int] = {}
    for row in rows:
        counts[row["category"]] = counts.get(row["category"], 0) + 1

    manifest = {
        "pack_id": "methodmesh.emergency.global_exit_core",
        "schema_version": 1,
        "scope": "global_sparse_exit_infrastructure",
        "source_versions": [f"ourairports:{args.source_version}"],
        "category_counts": counts,
        "licences": ["OurAirports: Public Domain"],
        "attribution": ["Airport source: OurAirports (attribution appreciated but not required by source terms)."],
        "operational_status_semantics": "unknown unless separately verified by live evidence",
        "file": args.output.name,
        "bytes": size,
        "sha256": sha256(args.output),
        "determinism": "same input bytes + source version -> same sorted CSV bytes",
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2, sort_keys=True))

if __name__ == "__main__":
    main()
