# Suggested `000_Roadmap.md` note — Surveying

### Surveying — Development

- Added self-contained `surveying` module prototype.
- Atomic methods: bearing/distance, forward coordinate, chainage/offset both directions, traverse + closure + Bowditch/transit adjustment, area/perimeter/centroid, levelling reduction, grade/rise-fall, repeated GPS-fix averaging, bearing intersection and local-grid set-out.
- Added persistent Traverse field book and Levelling field book dashboards using shared calculation engines.
- Keeps GPS/navigation separate from survey-grade local-grid maths; reuses existing GPS target navigation rather than duplicating it.
- Pure Kotlin calculation smoke checks pass.
- Still Development pending Android Gradle build, device/preset/ODK tests and real-world verification against independent survey computations.
- Follow-ons: DMS/bearing parser; slope-distance reduction; observed-angle traverse/angular closure; two-peg test; distance/bearing intersection variants; 2D Helmert transform; vetted CRS/projection support; grid-to-ground corrections; curves; field-book import/export; external GNSS/NMEA; least-squares networks.
