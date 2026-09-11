# AprilTag module changelog

## 0.2.0 — live HUD and capture rails

- unified camera-first live HUD across all six capabilities;
- reliable camera tapping through a Compose gesture layer above `PreviewView`;
- all detected tags labelled in the live image with target/reference/moving roles;
- `apriltag.detect` target `-1` now captures all visible tags, while retaining the strongest tag in scalar selected-tag fields;
- tap-to-target range/pose;
- tap-to-assign reference and target tags for relative pose;
- planar working points re-projected into the live image with line/polygon overlays, explicit next-step instructions and failed-tap explanations;
- tracking rewritten as an explicit moving-tag time-series workflow with recommended fixed-reference-tag mode, fixed-camera fallback, readiness status and live XYZ/path/sample HUD;
- fixed stale camera-analysis callback behaviour during calibration/tracking by using the latest Compose callback;
- changed default `quad_decimate` from `2.0` to `1.0` to favour robust oblique/small-tag detection over CPU economy;
- expanded capability documentation and XLSForm launch guidance.

Canonical method IDs and output field names are unchanged from 0.1.0.
