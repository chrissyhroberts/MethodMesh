#!/usr/bin/env python3
"""Build a MethodMesh astronomy regional light-pollution cache from World_Atlas_2015.tif.

Input dataset:
  Falchi et al. (2016), Supplement to: The New World Atlas of Artificial Night Sky Brightness
  DOI: 10.5880/GFZ.1.4.2016.001
  Licence: CC BY-NC 4.0

The output JSON is intentionally simple so the Android astronomy capability can own,
import, query and remove regional caches without requiring a core MethodMesh resource registry.
"""

import argparse
import json
import math
from datetime import datetime, timezone
from pathlib import Path

import rasterio
from rasterio.windows import from_bounds

EARTH_RADIUS_KM = 6371.0088


def distance_km(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(min(1.0, max(0.0, a))))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("tif", type=Path)
    ap.add_argument("output", type=Path)
    ap.add_argument("--lat", type=float, required=True)
    ap.add_argument("--lon", type=float, required=True)
    ap.add_argument("--radius-km", type=float, required=True)
    ap.add_argument("--stride", type=int, default=1, help="Sample every Nth raster cell; 1 keeps native atlas resolution.")
    ap.add_argument("--name", default="World Atlas 2015 regional cache")
    args = ap.parse_args()

    if args.radius_km <= 0:
        raise SystemExit("--radius-km must be positive")
    stride = max(1, args.stride)
    lat_pad = args.radius_km / 111.0
    lon_pad = args.radius_km / max(1e-6, 111.0 * math.cos(math.radians(args.lat)))

    with rasterio.open(args.tif) as src:
        if src.crs is None or src.crs.to_epsg() != 4326:
            raise SystemExit(f"Expected EPSG:4326 World Atlas raster, got {src.crs}")
        window = from_bounds(args.lon - lon_pad, args.lat - lat_pad, args.lon + lon_pad, args.lat + lat_pad, src.transform)
        window = window.round_offsets().round_lengths()
        data = src.read(1, window=window, masked=True)
        transform = src.window_transform(window)
        points = []
        for row in range(0, data.shape[0], stride):
            for col in range(0, data.shape[1], stride):
                value = data[row, col]
                if getattr(value, "mask", False):
                    continue
                value = float(value)
                if not math.isfinite(value):
                    continue
                lon, lat = rasterio.transform.xy(transform, row, col, offset="center")
                if distance_km(args.lat, args.lon, lat, lon) <= args.radius_km:
                    points.append({"latitude": round(lat, 7), "longitude": round(lon, 7), "value": value})

        resolution_m = abs(src.transform.a) * 111_320.0 * max(1, stride)

    payload = {
        "schema": "methodmesh.astronomy.light_pollution_region.v1",
        "region_id": f"world_atlas_2015_{args.lat:.4f}_{args.lon:.4f}_{args.radius_km:g}km",
        "name": args.name,
        "dataset_id": "falchi_world_atlas_2015",
        "dataset_date": "2015",
        "source": "https://doi.org/10.5880/GFZ.1.4.2016.001",
        "licence": "CC BY-NC 4.0",
        "unit": "mcd/m²",
        "resolution_m": resolution_m,
        "center": {"latitude": args.lat, "longitude": args.lon},
        "radius_km": args.radius_km,
        "created_at_iso": datetime.now(timezone.utc).isoformat(),
        "points": points,
    }
    args.output.write_text(json.dumps(payload, separators=(",", ":")), encoding="utf-8")
    print(f"Wrote {len(points):,} points to {args.output}")


if __name__ == "__main__":
    main()
