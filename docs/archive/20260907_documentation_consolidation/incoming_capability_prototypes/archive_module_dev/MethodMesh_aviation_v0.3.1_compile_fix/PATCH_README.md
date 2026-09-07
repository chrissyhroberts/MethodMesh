# MethodMesh aviation v0.3 patch

This archive overlays the existing MethodMesh repository with the Development aviation capability pack.

v0.3 keeps the v0.2 persistent aviation dashboard and adds a polished persistent emergency/reference instrument panel:

```text
aviation.emergency.instruments
```

## Emergency panel

The panel is deliberately source-explicit rather than a pretend certified six-pack. It provides:

- GNSS groundspeed (`GS`), track (`TRK`), altitude and estimated vertical speed;
- mounted-device pitch/roll reference with explicit level calibration;
- device magnetic heading;
- phone-barometer standard-pressure altitude where a pressure sensor exists;
- sensor/fix quality states including `ATT FALLBACK`, `ATT UNCAL`, `ATT DYNAMIC`, `GPS FAIR/POOR/STALE` and `MAG LOW`;
- nearest-airfield positional reference from the existing cached OurAirports repository;
- responsive portrait/landscape flight-deck presentation;
- no editable fields in the live instrument view;
- non-blocking reference-data failure;
- a permanent safety band distinguishing phone-derived values from aircraft flight instruments.

It reuses MethodMesh's existing `PhoneSensorRepository` and aviation engines rather than creating a second sensor stack or invoking other capability screens.

## Persistent behaviour

- native/browser: stays on the live panel;
- native preset: stays live until **Finish**;
- `intent_test`: stays interactive;
- external/ODK: briefly stabilises sensors, captures once and automatically returns structured data;
- live updates do not themselves commit graph history.

## Overlay

Copy the archive contents over the root of a current MethodMesh checkout. The module remains self-contained under:

```text
app/src/main/java/com/example/methodmesh/modules/aviation/
```

No shared scaffold/dashboard modification is included.

## Validate

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.methodmesh.modules.aviation.*'
./gradlew :app:assembleDebug
```

Then complete the device checklist in:

```text
app/src/main/java/com/example/methodmesh/modules/aviation/docs/BUILD_REPORT.md
```

The capability remains **Development** until those checks and aviation-domain UX review are complete.
