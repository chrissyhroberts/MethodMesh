# Calibrate AprilTag measurements — `apriltag.calibrate_focal`

**Tags:** Experimental · Offline

## Purpose

Calibrate the real-world scale of AprilTag pose measurements from a known camera-to-tag distance. The capability samples the **raw uncorrected pose**, averages it, and calculates a dimensionless distance adjustment:

`distance_adjustment = true_distance / mean_raw_distance`

All later pose-based measurements use:

`corrected XYZ/range = raw pose × distance_adjustment`

Because AprilTag translation is linear in the configured tag size, this is equivalent to an effective pose tag size of:

`effective_pose_tag_size = physical_tag_size × distance_adjustment`

The physical `tag_size_mm` is still retained unchanged for planar measurements.

## Workflow

1. Measure the AprilTag detection-edge size accurately.
2. Put the tag approximately front-facing and centred in the camera.
3. Measure the true distance from the camera optical centre to the tag centre.
4. Enter that distance, select the tag, and collect stable samples.
5. The HUD shows raw range, variability, the proposed adjustment, corrected distance and effective pose size.
6. **Save calibration + Commit** persists the adjustment as this device's AprilTag default.
7. Range/pose, relative pose and tracking automatically load the stored adjustment unless a preset/protocol/ODK call explicitly supplies another value.

The adjustment remains directly editable in those capabilities and is included when saving a preset. Recalibrate if the camera/lens, zoom, camera model/intrinsics, or tag-size definition changes.

## Camera model

The capability still reports the older approximate pinhole focal estimate for audit/backward compatibility. That estimate assumes a fronto-parallel tag and does not model lens distortion. The **distance adjustment** is the operational calibration used for real-world XYZ/range scaling.

## Inputs/settings

`tag_family`, `target_tag_id`, `tag_size_mm`, `known_distance_mm`, `calibration_samples`, `distance_scale`, camera intrinsics and detector tuning.

## Key outputs

`apriltag_distance_scale`, `apriltag_raw_distance_m`, `apriltag_corrected_distance_m`, `apriltag_effective_tag_size_mm`, `apriltag_range_cv`, plus the existing focal/profile outputs and audit fields.

## ODK integration

Direct native, preset, protocol and ODK launches use the same immersive calibration capability. A supplied `distance_scale` may override the device default; leaving it absent/blank allows the persisted device calibration to be used.
