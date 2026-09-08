# Emergency baked-data pipeline

The global baked core is intentionally **small and strategic**. It is not a
worldwide POI database.

`build_emergency_core.py` converts a reviewed OurAirports `airports.csv`
snapshot into the MethodMesh sparse exit schema. By default it retains only
records declaring scheduled civilian service. Optional pre-normalised reviewed
inputs can add major passenger ports/ferries, official land crossings and
substantive diplomatic missions.

Dense local classes (hospitals, pharmacies, AEDs, police/fire stations,
shelters, fuel, local transport) are explicitly excluded and belong in regional
packs.

The script writes a deterministic CSV plus a checksum/source manifest and fails
when the configured baked-size budget is exceeded. It performs no network
access, so release provenance is based on the exact reviewed input snapshot.

OurAirports states that its downloadable data are Public Domain and updated
nightly. The release process must record the actual snapshot/version used.

Do not ingest OpenStreetMap-derived ports/borders/diplomatic data into the baked
core until the ODbL redistribution/derived-database obligations have been
reviewed for the exact packaging approach.
