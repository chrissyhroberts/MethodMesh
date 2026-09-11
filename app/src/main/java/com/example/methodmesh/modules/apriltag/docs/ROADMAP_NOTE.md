# Roadmap note

The v0.2 module deliberately stops at operations that can be expressed and audited defensibly with one phone camera plus printed AprilTags.

Potential next work:

- full camera calibration from a multi-tag/checkerboard target with lens-distortion coefficients;
- persistent named camera profiles with explicit user save/select lifecycle;
- printable tag-sheet generation with measured detection-edge metadata and “do not scale” marks;
- automated subject segmentation/tracking inside a calibrated planar arena (manual taps remain the v0.2 ground-truth-friendly method);
- multi-tag planar boards for larger working areas and robust occlusion handling;
- global 3-D tag-network mapping with graph optimisation / bundle adjustment and residual reporting;
- repeated-photo registration and change/growth measurement against fixed landmarks;
- uncertainty propagation / bootstrap summaries for derived distances and trajectories;
- optional synchronized multi-camera tracking;
- tag-map import/export as a MethodMesh knowledge object once a stable cross-module spatial-frame contract exists.

Do not collapse these into `apriltag.range_pose` merely because they use the same detector. Admit new independently callable methods only when their input/output contracts and validation model are clear.
