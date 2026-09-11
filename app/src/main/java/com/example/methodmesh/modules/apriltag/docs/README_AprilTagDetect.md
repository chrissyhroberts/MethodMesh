# Detect AprilTag tags — `apriltag.detect`

**Tags:** Experimental · Offline

## Purpose

Live AprilTag identification with image-space geometry and detector quality evidence. This capability does not require a camera calibration because it does not claim metric depth.

## Surface parity

- **Direct native:** live camera with tag outline, ID and detector evidence; Commit freezes one frame.
- **Presets:** family, target ID and detector tuning can be fixed or left runtime-editable.
- **Protocols:** independently callable observation step.
- **ODK/XLSForm:** launches the same interactive camera screen and returns to the form after Commit/Cancel.

## Inputs/settings

`tag_family`, `target_tag_id` (`-1` = all visible tags), `detector_threads`, `quad_decimate`, `refine_edges`.

## Canonical outputs

`apriltag_status`, `apriltag_result`, `apriltag_tag_count`, `apriltag_tag_ids`, `apriltag_selected_tag_id`, `apriltag_family`, `apriltag_apparent_edge_px`, `apriltag_center_x_px`, `apriltag_center_y_px`, `apriltag_decision_margin`, `apriltag_hamming`, `apriltag_detections_json`, `apriltag_image_width_px`, `apriltag_image_height_px`, `apriltag_backend`, `apriltag_captured_time_iso`, `apriltag_audit_json`, `apriltag_warning`, `apriltag_error`.

## ODK INTEGRATION

**Capability**  
Detect AprilTag tags  
`apriltag.detect`

**Tags**  
Maturity: Experimental  
Connectivity: Offline

**ODK INPUTS**  
`tag_family` | text | optional | AprilTag family  
`target_tag_id` | int | optional | requested ID; -1 captures all visible tags  
`detector_threads` | int | optional | detector workers  
`quad_decimate` | decimal | optional | detector speed/detail  
`refine_edges` | boolean/text | optional | edge refinement

Interactive acquisition: MethodMesh live rear-camera detector.

**INTENT CALL**

`com.example.methodmesh.EXECUTE_METHOD(method_id='apriltag.detect',input_tag_family=${tag_family},input_target_tag_id=${target_tag_id},input_payload_mode='FULL',return_mode='flat')`

**MODIFIERS**  
Detector tuning inputs above are optional.

**CANONICAL RETURNS**  
All fields listed under Canonical outputs are addressable; `methodmesh_full_json` is always captured for handled ODK roundtrips.

**RETURN FIELD PLACEMENT**  
Canonical example uses unprefixed return keys inside one `run_apriltag_detect` group; one MethodMesh invocation; no return namespace.

**FILE RETURN SEMANTICS**  
None.

**RUNTIME**  
Inputs: detector settings.  
Beef: detected ID/image geometry and detector evidence.  
Metadata: detections JSON, audit JSON and standard full MethodMesh JSON.

## Validation notes

The live HUD outlines and labels every detection. In all-visible mode, `apriltag_tag_count`, `apriltag_tag_ids` and `apriltag_detections_json` represent the full frame; `apriltag_selected_tag_id` is retained as the strongest detection for scalar compatibility. Tap a tag to switch to a specific target.

Detection success is not equivalent to metric validity. Decision margin and Hamming distance are evidence, not universal pass/fail thresholds.
