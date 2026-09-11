# Planar AprilTag measurement — `apriltag.planar_measure`

**Tags:** Experimental · Offline

## Purpose

Convert operator taps in the camera image into millimetre coordinates on the physical plane of a known-size tag. Modes are `point`, `distance`, `area` and `trajectory`.

This is the first practical **arena-tracking** method: fixed tags provide scale/reference and the researcher can repeatedly tap a snail/insect/other object without attaching anything to the subject.

## Reference frames

Without a layout, the selected tag centre is `(0,0)` and the tag edges define local axes. An optional `anchor_layout_json` can place multiple tag IDs into a shared plane:

```json
{
  "frame": "arena_A",
  "12": {"x_mm": 0, "y_mm": 0, "yaw_deg": 0},
  "13": {"x_mm": 800, "y_mm": 0, "yaw_deg": 0}
}
```

When either tag is visible, tapped local coordinates are transformed into the same `arena_A` frame.

## Validity assumption

Every tapped point must lie on the **same physical plane** as the reference tag/layout. A tag on the wall does not calibrate an object a metre in front of it. This assumption is returned explicitly as `apriltag_assumption=coplanar_with_reference`.

## Inputs/settings

`measurement_mode`, `tag_family`, `target_tag_id`, `tag_size_mm`, optional `anchor_layout_json`, detector tuning.

## Canonical outputs

`apriltag_status`, `apriltag_result`, `apriltag_reference_tag_id`, `apriltag_family`, `apriltag_tag_size_mm`, `apriltag_measurement_mode`, `apriltag_reference_frame`, `apriltag_point_count`, `apriltag_point_x_mm`, `apriltag_point_y_mm`, `apriltag_distance_mm`, `apriltag_area_mm2`, `apriltag_perimeter_mm`, `apriltag_path_length_mm`, `apriltag_duration_s`, `apriltag_mean_speed_mm_s`, `apriltag_points_json`, `apriltag_assumption`, `apriltag_geometry_valid`, `apriltag_backend`, `apriltag_captured_time_iso`, `apriltag_audit_json`, `apriltag_warning`, `apriltag_error`.

## ODK INTEGRATION

**Capability:** Planar AprilTag measurement — `apriltag.planar_measure`  
**Tags:** Experimental · Offline

**ODK INPUTS:** `measurement_mode`, `tag_family`, `target_tag_id`, `tag_size_mm`, optional `anchor_layout_json`.  
Interactive acquisition: camera + operator taps.

**INTENT CALL**  
`com.example.methodmesh.EXECUTE_METHOD(method_id='apriltag.planar_measure',input_measurement_mode=${measurement_mode},input_tag_family=${tag_family},input_target_tag_id=${target_tag_id},input_tag_size_mm=${tag_size_mm},input_anchor_layout_json=${anchor_layout_json},input_payload_mode='FULL',return_mode='flat')`

**MODIFIERS**  
Detector tuning is optional. `anchor_layout_json` is optional and promotes tag-local coordinates into a named shared planar frame. With `target_tag_id=-1`, the first accepted tag is locked for the run until Clear.

**CANONICAL RETURNS:** all declared outputs + `methodmesh_full_json`.  
**RETURN FIELD PLACEMENT:** one `run_apriltag_planar_measure` group; canonical unprefixed keys.  
**FILE RETURN SEMANTICS:** None.  
**RUNTIME:** primary beef varies by mode; `apriltag_points_json` is the complete timestamped point set.

## Native interaction and capture rails

The live camera itself is the tap surface. A transparent Compose gesture layer sits above `PreviewView`, avoiding the Android-view event interception that previously made the instruction “tap the object/point” ineffective.

The HUD shows the locked reference tag, working metric result, point count and mode-specific next action. Captured points are transformed back into the current image and drawn as numbered marks; distance/trajectory points are connected and area points are displayed as a closed polygon while the reference tag remains visible. A failed tap explains why no point was recorded (for example, reference tag lost).

Mode rails:

- `point`: one tap; a later tap replaces it.
- `distance`: exactly two endpoints.
- `area`: three or more boundary points in order.
- `trajectory`: repeated timestamped taps of the object through time.

Undo and Clear operate on the working result only. Commit freezes the current metric points and derived measures.
