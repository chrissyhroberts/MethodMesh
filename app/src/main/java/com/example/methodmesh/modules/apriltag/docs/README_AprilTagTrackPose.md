# Track tagged object — `apriltag.track_pose`

**Tags:** Experimental · Offline

## Purpose

Record a moving rigid AprilTag through time and derive path length, displacement and speed. The preferred design uses a simultaneously visible fixed reference tag; a fixed-camera frame is supported when no reference tag is configured.

## Inputs/settings

`moving_tag_id`, `reference_tag_id` (`-1` = camera frame), `tag_family`, `tag_size_mm`, camera model, `sample_interval_ms`, `max_samples`, detector tuning.

## Canonical outputs

`apriltag_status`, `apriltag_result`, `apriltag_moving_tag_id`, `apriltag_reference_tag_id`, `apriltag_family`, `apriltag_tag_size_mm`, `apriltag_reference_frame`, `apriltag_sample_count`, `apriltag_duration_s`, `apriltag_path_length_m`, `apriltag_displacement_m`, `apriltag_mean_speed_m_s`, `apriltag_max_speed_m_s`, start/end XYZ fields, `apriltag_samples_json`, `apriltag_intrinsics_source`, `apriltag_geometry_valid`, `apriltag_backend`, `apriltag_captured_time_iso`, `apriltag_audit_json`, `apriltag_warning`, `apriltag_error`.

## ODK INTEGRATION

**Capability:** Track tagged object — `apriltag.track_pose`  
**Tags:** Experimental · Offline

**ODK INPUTS:** `moving_tag_id`, `reference_tag_id`, `tag_family`, `tag_size_mm`, camera-model modifiers, `sample_interval_ms`, `max_samples`.  
Interactive acquisition: Start/Stop recording in the native camera surface, then Commit.

**INTENT CALL**  
`com.example.methodmesh.EXECUTE_METHOD(method_id='apriltag.track_pose',input_moving_tag_id=${moving_tag_id},input_reference_tag_id=${reference_tag_id},input_tag_family=${tag_family},input_tag_size_mm=${tag_size_mm},input_sample_interval_ms=${sample_interval_ms},input_max_samples=${max_samples},input_payload_mode='FULL',return_mode='flat')`

**MODIFIERS**  
Detector tuning, camera-model fields, `sample_interval_ms` and `max_samples` control acquisition quality/performance without changing the canonical output semantics.

**CANONICAL RETURNS:** all declared outputs + `methodmesh_full_json`.  
**RETURN FIELD PLACEMENT:** one `run_apriltag_track_pose` group; canonical unprefixed keys.  
**FILE RETURN SEMANTICS:** None.  
**RUNTIME:** primary beef is trajectory summary; `apriltag_samples_json` carries complete timestamped XYZ observations.

## Validity

When `reference_tag_id=-1`, the camera must remain physically fixed throughout the recording. If the camera moves, camera-frame displacement becomes a mixture of object and camera motion. A fixed reference tag removes that ambiguity as long as both tags remain detectable.

## Native interaction and setup

This capability records the 3-D position of one **MOVING** tag through time. The recommended setup is:

1. attach the moving tag rigidly to the object;
2. place a second tag somewhere fixed in the scene;
3. choose **Set moving** and tap the moving tag;
4. choose **Set reference** and tap the fixed tag;
5. keep both visible and press **Start recording**;
6. stop, review sample count/path/duration, then Commit.

The HUD continuously reports tag readiness, current XYZ in the selected frame, recording status, sample count and accumulated path. If a required tag is lost, no sample is fabricated and the HUD explains that acquisition is paused for that frame.

**Fixed camera** mode removes the reference-tag requirement, but the phone must remain physically stationary for the entire recording. The UI labels this mode explicitly.
