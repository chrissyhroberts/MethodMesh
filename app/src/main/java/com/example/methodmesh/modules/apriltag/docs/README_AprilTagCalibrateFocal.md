# Calibrate AprilTag range — `apriltag.calibrate_focal`

**Tags:** Experimental · Offline

## Purpose

Estimate a practical pinhole focal length in pixels from repeated observations of a known-size, front-facing tag at a measured camera-to-tag distance.

This is intentionally a **field approximation**. It assumes a fronto-parallel tag, principal point near image centre and unmodelled lens distortion. For validation-grade 6-DoF work, prefer a proper camera calibration profile supplied through manual intrinsics.

## Surface parity

Direct native, presets, protocols and ODK all invoke the same capability. The native screen collects stable samples, shows variation, and Commit freezes one calibration profile.

## Inputs/settings

`tag_family`, `target_tag_id`, `tag_size_mm`, `known_distance_mm`, `calibration_samples`, plus detector tuning.

## Canonical outputs

`apriltag_status`, `apriltag_result`, `apriltag_calibration_tag_id`, `apriltag_family`, `apriltag_tag_size_mm`, `apriltag_known_distance_mm`, `apriltag_calibration_sample_count`, `apriltag_mean_edge_px`, `apriltag_edge_cv`, `apriltag_fx_px`, `apriltag_fy_px`, `apriltag_cx_px`, `apriltag_cy_px`, `apriltag_intrinsics_width_px`, `apriltag_intrinsics_height_px`, `apriltag_intrinsics_source`, `apriltag_calibration_profile_json`, `apriltag_geometry_valid`, `apriltag_backend`, `apriltag_captured_time_iso`, `apriltag_audit_json`, `apriltag_warning`, `apriltag_error`.

## ODK INTEGRATION

**Capability:** Calibrate AprilTag range — `apriltag.calibrate_focal`  
**Tags:** Experimental · Offline

**ODK INPUTS**  
`tag_family` | text | optional  
`target_tag_id` | int | optional  
`tag_size_mm` | decimal | required for meaningful calibration  
`known_distance_mm` | decimal | required  
`calibration_samples` | int | optional

Interactive acquisition: live camera sampling.

**INTENT CALL**  
`com.example.methodmesh.EXECUTE_METHOD(method_id='apriltag.calibrate_focal',input_tag_family=${tag_family},input_target_tag_id=${target_tag_id},input_tag_size_mm=${tag_size_mm},input_known_distance_mm=${known_distance_mm},input_calibration_samples=${calibration_samples},input_payload_mode='FULL',return_mode='flat')`

**MODIFIERS**  
`detector_threads`, `quad_decimate`, `refine_edges` are optional detector tuning; `calibration_samples` controls the repeated-observation target.

**CANONICAL RETURNS**  
All declared `apriltag_*` outputs above plus `methodmesh_full_json`.

**RETURN FIELD PLACEMENT**  
One `run_apriltag_calibrate_focal` group, canonical unprefixed keys, one invocation.

**FILE RETURN SEMANTICS:** None.  
**RUNTIME:** Beef is the scalar/profile camera model; JSON is secondary audit material.
