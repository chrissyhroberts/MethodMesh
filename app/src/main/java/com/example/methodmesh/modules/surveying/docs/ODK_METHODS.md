# Surveying ODK / XLSForm intent catalogue

Use each call on a `begin_group` row in `body::intent`. Place read-only named return questions inside the group. Examples below show the stable input names; forms may omit optional inputs.

## `survey.bearing_distance`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.bearing_distance',input_coordinate_mode=${input_coordinate_mode},input_start_easting=${input_start_easting},input_start_northing=${input_start_northing},input_end_easting=${input_end_easting},input_end_northing=${input_end_northing},input_start_latitude=${input_start_latitude},input_start_longitude=${input_start_longitude},input_end_latitude=${input_end_latitude},input_end_longitude=${input_end_longitude},return_mode='flat')
```

## `survey.forward_coordinate`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.forward_coordinate',input_start_easting=${input_start_easting},input_start_northing=${input_start_northing},input_azimuth_deg=${input_azimuth_deg},input_horizontal_distance_m=${input_horizontal_distance_m},return_mode='flat')
```

## `survey.offset_point`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.offset_point',input_start_easting=${input_start_easting},input_start_northing=${input_start_northing},input_end_easting=${input_end_easting},input_end_northing=${input_end_northing},input_chainage_m=${input_chainage_m},input_offset_right_m=${input_offset_right_m},return_mode='flat')
```

## `survey.chainage_offset`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.chainage_offset',input_start_easting=${input_start_easting},input_start_northing=${input_start_northing},input_end_easting=${input_end_easting},input_end_northing=${input_end_northing},input_point_easting=${input_point_easting},input_point_northing=${input_point_northing},return_mode='flat')
```

## `survey.traverse`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.traverse',input_start_point_id=${input_start_point_id},input_start_easting=${input_start_easting},input_start_northing=${input_start_northing},input_traverse_legs=${input_traverse_legs},input_close_easting=${input_close_easting},input_close_northing=${input_close_northing},input_adjustment_mode=${input_adjustment_mode},input_minimum_relative_precision=${input_minimum_relative_precision},return_mode='flat')
```

## `survey.area`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.area',input_coordinates=${input_coordinates},return_mode='flat')
```

## `survey.level_reduce`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.level_reduce',input_start_reduced_level_m=${input_start_reduced_level_m},input_level_observations=${input_level_observations},input_known_close_reduced_level_m=${input_known_close_reduced_level_m},input_distribute_closure=${input_distribute_closure},return_mode='flat')
```

## `survey.grade`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.grade',input_rise_m=${input_rise_m},input_horizontal_distance_m=${input_horizontal_distance_m},return_mode='flat')
```

## `survey.gps_average`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.gps_average',input_gps_fixes=${input_gps_fixes},input_accuracy_weighted=${input_accuracy_weighted},return_mode='flat')
```

The method also accepts a single piped fix as `gps_latitude`, `gps_longitude`, and optional `gps_accuracy_m`.

## `survey.intersection`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.intersection',input_station_a_easting=${input_station_a_easting},input_station_a_northing=${input_station_a_northing},input_bearing_a_deg=${input_bearing_a_deg},input_station_b_easting=${input_station_b_easting},input_station_b_northing=${input_station_b_northing},input_bearing_b_deg=${input_bearing_b_deg},return_mode='flat')
```

## `survey.setout`

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='survey.setout',input_current_easting=${input_current_easting},input_current_northing=${input_current_northing},input_target_easting=${input_target_easting},input_target_northing=${input_target_northing},return_mode='flat')
```

## Dashboard snapshots

`survey.traverse_book` and `survey.levelling_book` are primarily native persistent dashboards. External callers may request an existing local job using `input_survey_job_id`, but ODK data collection should normally use the atomic `survey.traverse` / `survey.level_reduce` methods because ODK owns its own record lifecycle.
