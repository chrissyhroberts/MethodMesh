# Emergency source review ledger — Development

## Adult CPR / AED

- Source organisation: Resuscitation Council UK
- Source: Adult Basic Life Support Guidelines 2025
- Publication date/version represented by this build: 2025-10-27
- MethodMesh adaptation: 0.2.0-development
- Content validation: **not yet signed off**
- Production use: **blocked pending review**

The short card encodes only the core adult BLS points needed for an offline
emergency surface. It is not intended to replace dispatcher instructions or
professional training.

## Airports

- Candidate base source: OurAirports open data `airports.csv`
- Release pipeline: `data/build_emergency_core.py`
- Selection: `scheduled_service=yes`; excludes closed/heliport/balloonport rows
- Distribution semantics in MethodMesh: known location only; operational status unknown
- Production snapshot: **not yet baked into this prototype**

## Ports / land borders / diplomatic missions

No source is baked into the Development prototype. Candidate sources must be
reviewed for authority, maintenance, licensing and redistribution before use.
Dense OpenStreetMap extraction is not an automatic choice because the ODbL
packaging obligations need deliberate review.

## Emergency numbers

The code currently contains only a tiny GB development fixture (999/112) so the
offline lookup path can be exercised. A reviewed global/jurisdictional table is a
Production gate.

## Live alerts

No live adapter is installed. Fresh install status is therefore GREY. This is
intentional: absence of a configured source is not evidence of no hazard.
