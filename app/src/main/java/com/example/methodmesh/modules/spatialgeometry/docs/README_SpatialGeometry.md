# Spatial geometry

## Capabilities

This standalone module provides reusable field geometry measurements driven by the phone's orientation sensors:

- `tree_height_measurement` — estimates object height from a measured horizontal distance, observer eye height, and sightline angles to the base and top.
- `slope_inclination_measurement` — reports inclination, grade percentage, and slope ratio.
- `geometry_distance_estimation` — estimates distance when a reference object's height and its angular size are known.

The tree-height workflow is a triangulation aid. The phone can measure sightline angles, but it cannot infer arbitrary horizontal distance from an IMU alone; supply a measured distance, or add a future camera/depth/rangefinder source.

## Android intent

All methods use the standard MethodMesh execution boundary:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='tree_height_measurement',input_horizontal_distance_m=12.5,input_observer_height_m=1.65,input_base_angle_deg=-2.0,input_top_angle_deg=31.4,return_mode='flat')
```

Use `slope_inclination_measurement` with `input_slope_angle_deg`, or `geometry_distance_estimation` with `input_reference_height_m` and `input_angular_size_deg`.

## Inputs

Tree height:

- `input_horizontal_distance_m` — measured horizontal distance to the object.
- `input_observer_height_m` — eye or device height above the same ground reference.
- `input_base_angle_deg` — signed angle from the horizontal to the base.
- `input_top_angle_deg` — signed angle from the horizontal to the top.

Slope:

- `input_slope_angle_deg` — signed inclination angle.

Distance estimation:

- `input_reference_height_m` — known physical height of the reference object.
- `input_angular_size_deg` — angle subtended by that object.

The standalone screens can capture pitch from the rotation-vector sensor, with an accelerometer/magnetometer fallback, and allow values to be reviewed before calculation.

## Outputs

All results are returned as compact flat fields, including:

- `measurement_type`, `measurement_valid`, and `measured_time_iso`;
- input values used by the calculation;
- `object_height_m` for tree/object height;
- `slope_angle_deg`, `grade_percent`, and `slope_ratio` for inclination;
- `estimated_distance_m` for angular-size distance estimation;
- `sensor_source` and a human-readable `formula` for auditability.

Invalid or incomplete inputs produce a failed execution with `measurement_valid=false`; no plausible value is substituted.

## ODK example

See [`example_odk_spatialgeometry.xlsx`](example_odk_spatialgeometry.xlsx). It demonstrates the tree-height input set and the explicit `tree_height_measurement` intent. The same pattern can be adapted for slope and distance estimation.

