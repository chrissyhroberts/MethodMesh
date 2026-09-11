# Tag range and pose — `apriltag.range_pose`

**Tags:** Experimental · Offline

## Purpose

Estimate metric camera-to-tag distance, translation and orientation from a known-size AprilTag plus a camera model.

## Coordinate frame

Translation uses the camera frame: **+x right, +y down, +z forward**. `apriltag_distance_m` is Euclidean camera-to-tag-centre distance; `apriltag_z_forward_m` is optical-axis depth. Rotation is exposed as yaw/pitch/roll derived from the returned AprilTag rotation matrix.

## Inputs/settings

`tag_family`, `target_tag_id`, `tag_size_mm`, detector tuning, `intrinsics_mode` (`auto`/`manual`), and optional manual `fx_px`, `fy_px`, `cx_px`, `cy_px`, `intrinsics_width_px`, `intrinsics_height_px`.

`tag_size_mm` is the detection-edge size, not sheet outer size.

## Canonical outputs

`apriltag_status`, `apriltag_result`, `apriltag_tag_id`, `apriltag_family`, `apriltag_tag_size_mm`, `apriltag_distance_m`, `apriltag_x_right_m`, `apriltag_y_down_m`, `apriltag_z_forward_m`, `apriltag_yaw_deg`, `apriltag_pitch_deg`, `apriltag_roll_deg`, `apriltag_apparent_edge_px`, `apriltag_decision_margin`, `apriltag_hamming`, `apriltag_pose_error`, `apriltag_intrinsics_source`, `apriltag_fx_px`, `apriltag_fy_px`, `apriltag_cx_px`, `apriltag_cy_px`, `apriltag_image_width_px`, `apriltag_image_height_px`, `apriltag_geometry_valid`, `apriltag_backend`, `apriltag_captured_time_iso`, `apriltag_audit_json`, `apriltag_warning`, `apriltag_error`.

## ODK INTEGRATION

**Capability:** Tag range and pose — `apriltag.range_pose`  
**Tags:** Experimental · Offline

**ODK INPUTS**  
`tag_family` | text | optional  
`target_tag_id` | int | optional  
`tag_size_mm` | decimal | required  
`intrinsics_mode` | text | optional  
manual intrinsics fields | decimal/int | conditional when manual

Interactive acquisition: live camera, operator Commit.

**INTENT CALL**  
`com.example.methodmesh.EXECUTE_METHOD(method_id='apriltag.range_pose',input_tag_family=${tag_family},input_target_tag_id=${target_tag_id},input_tag_size_mm=${tag_size_mm},input_intrinsics_mode=${intrinsics_mode},input_payload_mode='FULL',return_mode='flat')`

**MODIFIERS**  
`detector_threads`, `quad_decimate`, `refine_edges`, plus manual `fx/fy/cx/cy` and calibration image dimensions when `intrinsics_mode=manual`.

**CANONICAL RETURNS:** all declared outputs above + `methodmesh_full_json`.  
**RETURN FIELD PLACEMENT:** one `run_apriltag_range_pose` group; canonical unprefixed keys.  
**FILE RETURN SEMANTICS:** None.  
**RUNTIME:** Beef is metric range/pose; camera/detector/audit fields remain addressable metadata.

## Validity

No metric result is emitted when no usable camera model/pose exists. Android physical-sensor estimates are labelled approximate. Lens distortion is not yet explicitly corrected by the Kotlin layer.

## Native interaction

All detected tags are labelled in the live image. Tap a tag to make it the target. The HUD continuously shows range, camera-frame XYZ, yaw/pitch/roll, apparent edge and decision margin. Commit is disabled when the target is absent or a metric pose solution is unavailable, with the reason shown immediately below the instrument. The robust detector default uses `quad_decimate=1.0`; increase it only when performance requires the trade-off.
