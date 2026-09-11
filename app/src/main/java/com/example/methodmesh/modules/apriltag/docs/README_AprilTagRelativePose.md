# Relative tag pose — `apriltag.relative_pose`

**Tags:** Experimental · Offline

## Purpose

Measure a target tag in the coordinate frame of a simultaneously visible reference tag. This is useful for fixed landmarks, apparatus alignment and building pairwise edges for a later tag-network map.

## Inputs/settings

`reference_tag_id`, `target_tag_id`, `tag_family`, `tag_size_mm`, detector tuning and camera intrinsics.

## Canonical outputs

`apriltag_status`, `apriltag_result`, `apriltag_reference_tag_id`, `apriltag_target_tag_id`, `apriltag_family`, `apriltag_tag_size_mm`, `apriltag_relative_x_m`, `apriltag_relative_y_m`, `apriltag_relative_z_m`, `apriltag_relative_distance_m`, `apriltag_relative_yaw_deg`, `apriltag_relative_pitch_deg`, `apriltag_relative_roll_deg`, `apriltag_reference_pose_error`, `apriltag_target_pose_error`, `apriltag_intrinsics_source`, `apriltag_geometry_valid`, `apriltag_backend`, `apriltag_captured_time_iso`, `apriltag_audit_json`, `apriltag_warning`, `apriltag_error`.

## ODK INTEGRATION

**Capability:** Relative tag pose — `apriltag.relative_pose`  
**Tags:** Experimental · Offline

**ODK INPUTS:** `reference_tag_id` (int), `target_tag_id` (int), `tag_family`, `tag_size_mm`, camera-model modifiers.  
Interactive acquisition: keep both tags visible and Commit.

**INTENT CALL**  
`com.example.methodmesh.EXECUTE_METHOD(method_id='apriltag.relative_pose',input_reference_tag_id=${reference_tag_id},input_target_tag_id=${target_tag_id},input_tag_family=${tag_family},input_tag_size_mm=${tag_size_mm},input_payload_mode='FULL',return_mode='flat')`

**MODIFIERS**  
Detector tuning and camera-model fields are optional modifiers; manual intrinsics are used when `intrinsics_mode=manual`.

**CANONICAL RETURNS:** all declared outputs + `methodmesh_full_json`.  
**RETURN FIELD PLACEMENT:** one `run_apriltag_relative_pose` group; canonical unprefixed keys.  
**FILE RETURN SEMANTICS:** None.  
**RUNTIME:** primary beef is relative translation/distance/orientation; per-tag pose errors and camera-model source are metadata.

## Native interaction

The camera HUD distinguishes **REF** and **TARGET** tags and displays their current relative separation, XYZ and orientation. Use **Set reference** or **Set target**, then tap the relevant visible tag. Reference and target cannot be the same ID. Commit remains unavailable until both tags have simultaneous metric pose solutions.
