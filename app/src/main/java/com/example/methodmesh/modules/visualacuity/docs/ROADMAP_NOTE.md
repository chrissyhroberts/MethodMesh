# Suggested `000_Roadmap.md` entry

## Visual acuity — Development

Added `visual_acuity.measure`, a local calibrated tumbling-E measurement capability based on the published Peek Acuity / V@home / WHOeyes design lineage.

Implemented:

- global MethodMesh physical-screen calibration (`dpPerMm`) as a hard prerequisite;
- 5×5 tumbling E in four random orientations;
- Peek-described crowding bars;
- 2 m distance and 40 cm near geometry;
- explicit 4-of-5, five-presentation staircase;
- published coarse sequence 1.0 / 0.8 / 0.5 / 0.2 / 0.0 logMAR;
- versioned MethodMesh 0.1-logMAR bracket refinement where the publication does not expose every internal branch;
- logMAR, metric Snellen, imperial Snellen and decimal output;
- reversible 100% window brightness;
- optional ambient-light warning/recording at the Peek >1000 lux threshold;
- full per-presentation audit trace;
- native/preset and ODK intent surfaces;
- example XLSForm in module docs.

Before Production:

- run full Android debug build;
- ruler-check multiple optotype sizes on physical devices;
- verify crowding geometry against a validated reference implementation;
- test portrait/landscape persistence and largest-stimulus fit;
- test native preset runtime-field hiding;
- complete ODK Collect round trip;
- decide whether to add automated distance measurement;
- decide whether to add N notation for near VA;
- decide whether to add standardized count-fingers / hand-movement / light-perception extensions;
- conduct or adopt an appropriate prospective equivalence/repeatability validation before making a clinical equivalence claim;
- assess intended-use medical-device/regulatory requirements.
