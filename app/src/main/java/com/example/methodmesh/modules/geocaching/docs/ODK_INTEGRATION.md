# ODK integration — MethodMesh Geocaching

ODK/XLSForm is a first-class invocation surface for the same canonical capabilities used by native MethodMesh, presets, protocols and RIL.

## Shared rules

- Canonical showcase workbooks contain **one MethodMesh invocation each**.
- Use canonical unprefixed capability return names.
- Every handled roundtrip captures `methodmesh_status` and `methodmesh_full_json`.
- Canonical examples do **not** set `methodmesh_return_namespace`.
- Image/file outputs are real caller-readable `content://` attachments; never treat `artifact://` as an ODK attachment.
- ODK owns its form/submission data. Visit/trackable calls do not append the personal MethodMesh ledger unless `input_persist_to_ledger=true` is explicit.
- Interactive Commit returns directly to the calling form.

## Integration Card — `geocache.dashboard`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_geocaching_dashboard.xlsx`  
**Acquisition:** Read-only snapshot; native dashboard also launches the underlying capabilities.  
**Persistence:** No new data; reads durable native state.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.dashboard',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- No capability-specific input is required for the snapshot.

**Canonical returns**

- `geocache_dashboard_status` — XLSForm `text`
- `geocache_dashboard_result` — XLSForm `text`
- `geocache_dashboard_active_hunt_code` — XLSForm `text`
- `geocache_dashboard_active_hunt_name` — XLSForm `text`
- `geocache_dashboard_cache_count` — XLSForm `integer`
- `geocache_dashboard_visit_count` — XLSForm `integer`
- `geocache_dashboard_found_count` — XLSForm `integer`
- `geocache_dashboard_dnf_count` — XLSForm `integer`
- `geocache_dashboard_photo_count` — XLSForm `integer`
- `geocache_dashboard_followed_trackable_count` — XLSForm `integer`
- `geocache_dashboard_opencache_connected` — XLSForm `text`
- `geocache_dashboard_opencache_username` — XLSForm `text`
- `geocache_dashboard_pending_upload_count` — XLSForm `integer`
- `geocache_dashboard_captured_time_iso` — XLSForm `text`
- `geocache_dashboard_audit_json` — XLSForm `text`
- `geocache_dashboard_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.library`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_cache_library.xlsx`  
**Acquisition:** Native file picker/import and library browsing; text GPX input is available for advanced direct calls.  
**Persistence:** Native import/save is persistent. External calls do not silently persist merely by being invoked.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.library',input_export_gpx='false',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_cache_code` — Select cache code
- `input_export_gpx` — Return GPX attachment (showcase default `false`)
- `input_gpx_text` — advanced GPX text payload
- `input_export_gpx=true` — return GPX attachment

**Canonical returns**

- `geocache_library_status` — XLSForm `text`
- `geocache_library_result` — XLSForm `text`
- `geocache_library_cache_count` — XLSForm `integer`
- `geocache_library_cache_codes` — XLSForm `text`
- `geocache_library_selected_cache_code` — XLSForm `text`
- `geocache_library_selected_cache_name` — XLSForm `text`
- `geocache_library_source` — XLSForm `text`
- `geocache_library_gpx_export_uri` — XLSForm `file` — attachment
- `geocache_library_captured_time_iso` — XLSForm `text`
- `geocache_library_audit_json` — XLSForm `text`
- `geocache_library_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.nearby`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_nearby_caches.xlsx`  
**Acquisition:** Native GPS can acquire current position; supplied latitude/longitude are also accepted.  
**Persistence:** Discovery is transient; saving a discovered cache offline is an explicit native action.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.nearby',input_source='library',input_radius_km='10',input_limit='50',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_source` — `library` or a provider ID from the persistent provider registry (showcase default `library`)
- `input_latitude` — Current latitude (optional)
- `input_longitude` — Current longitude (optional)
- `input_radius_km` — Radius (km) (showcase default `10`)
- `input_limit` — Maximum results (showcase default `50`)

**Canonical returns**

- `geocache_nearby_status` — XLSForm `text`
- `geocache_nearby_result` — XLSForm `text`
- `geocache_nearby_current_latitude` — XLSForm `decimal`
- `geocache_nearby_current_longitude` — XLSForm `decimal`
- `geocache_nearby_radius_km` — XLSForm `decimal`
- `geocache_nearby_source` — XLSForm `text`
- `geocache_nearby_cache_count` — XLSForm `integer`
- `geocache_nearby_caches_json` — XLSForm `text`
- `geocache_nearby_selected_cache_code` — XLSForm `text`
- `geocache_nearby_selected_distance_m` — XLSForm `decimal`
- `geocache_nearby_selected_bearing_deg` — XLSForm `decimal`
- `geocache_nearby_captured_time_iso` — XLSForm `text`
- `geocache_nearby_audit_json` — XLSForm `text`
- `geocache_nearby_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.navigate`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_cache_navigation.xlsx`  
**Acquisition:** Interactive live GPS/heading acquisition. Target may be supplied by cache code or explicit coordinates.  
**Persistence:** An active hunt may persist across app restarts when the operator chooses Keep active.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.navigate',input_arrival_radius_m='15',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_cache_code` — Cache code (optional if target coordinates supplied)
- `input_cache_name` — Cache name
- `input_target_latitude` — Target latitude
- `input_target_longitude` — Target longitude
- `input_arrival_radius_m` — Arrival radius (m) (showcase default `15`)

**Canonical returns**

- `geocache_navigate_status` — XLSForm `text`
- `geocache_navigate_result` — XLSForm `text`
- `geocache_navigate_cache_code` — XLSForm `text`
- `geocache_navigate_cache_name` — XLSForm `text`
- `geocache_navigate_target_latitude` — XLSForm `decimal`
- `geocache_navigate_target_longitude` — XLSForm `decimal`
- `geocache_navigate_current_latitude` — XLSForm `decimal`
- `geocache_navigate_current_longitude` — XLSForm `decimal`
- `geocache_navigate_accuracy_m` — XLSForm `decimal`
- `geocache_navigate_distance_m` — XLSForm `decimal`
- `geocache_navigate_bearing_deg` — XLSForm `decimal`
- `geocache_navigate_heading_deg` — XLSForm `text`
- `geocache_navigate_relative_bearing_deg` — XLSForm `decimal`
- `geocache_navigate_arrived` — XLSForm `text`
- `geocache_navigate_arrival_radius_m` — XLSForm `text`
- `geocache_navigate_started_at_iso` — XLSForm `text`
- `geocache_navigate_captured_time_iso` — XLSForm `text`
- `geocache_navigate_audit_json` — XLSForm `text`
- `geocache_navigate_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.details`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_cache_details.xlsx`  
**Acquisition:** Can use local cache data; native UI may retrieve provider data when an OpenCaching account/key is available.  
**Persistence:** No implicit persistence.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.details',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_cache_code` — Cache code
- The capability settings also accept supplied listing fields (`input_cache_name`, coordinates, type, size, difficulty, terrain, owner, description, hint, JSON attributes/logs/waypoints, provider URL and source).

**Canonical returns**

- `geocache_details_status` — XLSForm `text`
- `geocache_details_result` — XLSForm `text`
- `geocache_details_code` — XLSForm `text`
- `geocache_details_name` — XLSForm `text`
- `geocache_details_source` — XLSForm `text`
- `geocache_details_latitude` — XLSForm `decimal`
- `geocache_details_longitude` — XLSForm `decimal`
- `geocache_details_type` — XLSForm `text`
- `geocache_details_size` — XLSForm `text`
- `geocache_details_difficulty` — XLSForm `text`
- `geocache_details_terrain` — XLSForm `text`
- `geocache_details_owner` — XLSForm `text`
- `geocache_details_description` — XLSForm `text`
- `geocache_details_hint` — XLSForm `text`
- `geocache_details_attributes_json` — XLSForm `text`
- `geocache_details_recent_logs_json` — XLSForm `text`
- `geocache_details_waypoints_json` — XLSForm `text`
- `geocache_details_provider_url` — XLSForm `text`
- `geocache_details_captured_time_iso` — XLSForm `text`
- `geocache_details_audit_json` — XLSForm `text`
- `geocache_details_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.project_waypoint`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_project_waypoint.xlsx`  
**Acquisition:** Direct calculation; no sensor interaction required.  
**Persistence:** No persistence.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.project_waypoint',input_bearing_deg='0',input_distance_m='100',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_origin_latitude` — Origin latitude
- `input_origin_longitude` — Origin longitude
- `input_bearing_deg` — Bearing (degrees) (showcase default `0`)
- `input_distance_m` — Distance (m) (showcase default `100`)

**Canonical returns**

- `geocache_project_status` — XLSForm `text`
- `geocache_project_result` — XLSForm `text`
- `geocache_project_origin_latitude` — XLSForm `decimal`
- `geocache_project_origin_longitude` — XLSForm `decimal`
- `geocache_project_bearing_deg` — XLSForm `decimal`
- `geocache_project_distance_m` — XLSForm `decimal`
- `geocache_project_projected_latitude` — XLSForm `decimal`
- `geocache_project_projected_longitude` — XLSForm `decimal`
- `geocache_project_captured_time_iso` — XLSForm `text`
- `geocache_project_audit_json` — XLSForm `text`
- `geocache_project_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.average_coordinates`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_average_coordinates.xlsx`  
**Acquisition:** Native repeated fresh GPS fixes or supplied sample list.  
**Persistence:** Working sample state only; committed result returns to caller.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.average_coordinates',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_samples` — Optional latitude,longitude,accuracy samples

**Canonical returns**

- `geocache_average_status` — XLSForm `text`
- `geocache_average_result` — XLSForm `text`
- `geocache_average_sample_count` — XLSForm `integer`
- `geocache_average_mean_latitude` — XLSForm `decimal`
- `geocache_average_mean_longitude` — XLSForm `decimal`
- `geocache_average_mean_accuracy_m` — XLSForm `decimal`
- `geocache_average_min_accuracy_m` — XLSForm `decimal`
- `geocache_average_max_accuracy_m` — XLSForm `decimal`
- `geocache_average_captured_time_iso` — XLSForm `text`
- `geocache_average_audit_json` — XLSForm `text`
- `geocache_average_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.record_visit`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_record_visit.xlsx`  
**Acquisition:** Interactive GPS/photo acquisition or supplied coordinates. Commit is the write boundary.  
**Persistence:** Native dashboard use persists. External/ODK is return-only unless `input_persist_to_ledger=true`.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.record_visit',input_visit_type='found',input_favorite='false',input_persist_to_ledger='false',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_cache_code` — Cache code
- `input_cache_name` — Cache name
- `input_visit_type` — Visit type (showcase default `found`)
- `input_note` — Field note
- `input_favorite` — Favourite/recommend (showcase default `false`)
- `input_actual_latitude` — Actual find latitude (optional; native GPS otherwise)
- `input_actual_longitude` — Actual find longitude (optional; native GPS otherwise)
- `input_accuracy_m` — GPS accuracy (m)
- `input_published_latitude` — Published cache latitude
- `input_published_longitude` — Published cache longitude
- `input_persist_to_ledger` — Also append MethodMesh personal ledger (showcase default `false`)
- `input_visit_id`, `input_source`, `input_timestamp`, `input_photo_uri` are also canonical advanced inputs.

**Canonical returns**

- `geocache_visit_status` — XLSForm `text`
- `geocache_visit_result` — XLSForm `text`
- `geocache_visit_id` — XLSForm `text`
- `geocache_visit_cache_code` — XLSForm `text`
- `geocache_visit_cache_name` — XLSForm `text`
- `geocache_visit_type` — XLSForm `text`
- `geocache_visit_timestamp` — XLSForm `text`
- `geocache_visit_actual_latitude` — XLSForm `decimal`
- `geocache_visit_actual_longitude` — XLSForm `decimal`
- `geocache_visit_accuracy_m` — XLSForm `decimal`
- `geocache_visit_published_latitude` — XLSForm `decimal`
- `geocache_visit_published_longitude` — XLSForm `decimal`
- `geocache_visit_published_offset_m` — XLSForm `decimal`
- `geocache_visit_note` — XLSForm `text`
- `geocache_visit_favorite` — XLSForm `text`
- `geocache_visit_photo_count` — XLSForm `integer`
- `geocache_visit_photo_uri` — XLSForm `image` — attachment
- `geocache_visit_persisted_to_ledger` — XLSForm `text`
- `geocache_visit_remote_state` — XLSForm `text`
- `geocache_visit_captured_time_iso` — XLSForm `text`
- `geocache_visit_audit_json` — XLSForm `text`
- `geocache_visit_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.history`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_find_history.xlsx`  
**Acquisition:** Read/query ledger; optional file materialisation for CSV/GeoJSON/field-note outputs.  
**Persistence:** Read-only ledger query. Save-to-Files is explicit; requested return attachments are caller-owned materialisations.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.history',input_visit_type='all',input_export_format='none',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_query` — Search text
- `input_visit_type` — Visit type (showcase default `all`)
- `input_cache_code` — Cache code filter
- `input_since_iso` — Since ISO time/date
- `input_until_iso` — Until ISO time/date
- `input_selected_visit_id` — Selected visit ID
- `input_export_format` — Return attachment (showcase default `none`)

**Canonical returns**

- `geocache_history_status` — XLSForm `text`
- `geocache_history_result` — XLSForm `text`
- `geocache_history_record_count` — XLSForm `integer`
- `geocache_history_found_count` — XLSForm `integer`
- `geocache_history_dnf_count` — XLSForm `integer`
- `geocache_history_note_count` — XLSForm `integer`
- `geocache_history_photo_count` — XLSForm `integer`
- `geocache_history_records_json` — XLSForm `text`
- `geocache_history_selected_visit_id` — XLSForm `text`
- `geocache_history_selected_photo_uri` — XLSForm `image` — attachment
- `geocache_history_csv_export_uri` — XLSForm `file` — attachment
- `geocache_history_geojson_export_uri` — XLSForm `file` — attachment
- `geocache_history_field_notes_export_uri` — XLSForm `file` — attachment
- `geocache_history_captured_time_iso` — XLSForm `text`
- `geocache_history_audit_json` — XLSForm `text`
- `geocache_history_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.trackable_record`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_trackable_record.xlsx`  
**Acquisition:** Interactive GPS/photo acquisition or supplied values. Commit is the write boundary.  
**Persistence:** Native dashboard use persists. External/ODK is return-only unless `input_persist_to_ledger=true`.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.trackable_record',input_event_type='discover',input_persist_to_ledger='false',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_trackable_code` — Trackable public reference
- `input_trackable_name` — Trackable name
- `input_event_type` — Event (showcase default `discover`)
- `input_cache_code` — Cache code
- `input_cache_name` — Cache name
- `input_note` — Note
- `input_latitude` — Latitude
- `input_longitude` — Longitude
- `input_accuracy_m` — GPS accuracy (m)
- `input_persist_to_ledger` — Also append MethodMesh personal ledger (showcase default `false`)
- `input_event_id`, `input_timestamp`, `input_photo_uri` are also canonical advanced inputs.

**Canonical returns**

- `geocache_trackable_record_status` — XLSForm `text`
- `geocache_trackable_record_result` — XLSForm `text`
- `geocache_trackable_record_event_id` — XLSForm `text`
- `geocache_trackable_record_trackable_code` — XLSForm `text`
- `geocache_trackable_record_trackable_name` — XLSForm `text`
- `geocache_trackable_record_event_type` — XLSForm `text`
- `geocache_trackable_record_timestamp` — XLSForm `text`
- `geocache_trackable_record_cache_code` — XLSForm `text`
- `geocache_trackable_record_cache_name` — XLSForm `text`
- `geocache_trackable_record_latitude` — XLSForm `decimal`
- `geocache_trackable_record_longitude` — XLSForm `decimal`
- `geocache_trackable_record_accuracy_m` — XLSForm `decimal`
- `geocache_trackable_record_note` — XLSForm `text`
- `geocache_trackable_record_photo_count` — XLSForm `integer`
- `geocache_trackable_record_photo_uri` — XLSForm `image` — attachment
- `geocache_trackable_record_persisted_to_ledger` — XLSForm `text`
- `geocache_trackable_record_captured_time_iso` — XLSForm `text`
- `geocache_trackable_record_audit_json` — XLSForm `text`
- `geocache_trackable_record_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.trackable_history`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_trackable_history.xlsx`  
**Acquisition:** Read/query ledger; optional journey GeoJSON attachment.  
**Persistence:** Read-only ledger query; export is explicit.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.trackable_history',input_export_geojson='false',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_trackable_code` — Trackable public reference
- `input_export_geojson` — Return journey GeoJSON attachment (showcase default `false`)
- `input_selected_event_id` may select an event in richer user-authored forms.

**Canonical returns**

- `geocache_trackable_history_status` — XLSForm `text`
- `geocache_trackable_history_result` — XLSForm `text`
- `geocache_trackable_history_trackable_code` — XLSForm `text`
- `geocache_trackable_history_trackable_name` — XLSForm `text`
- `geocache_trackable_history_event_count` — XLSForm `integer`
- `geocache_trackable_history_journey_distance_m` — XLSForm `decimal`
- `geocache_trackable_history_current_state` — XLSForm `text`
- `geocache_trackable_history_events_json` — XLSForm `text`
- `geocache_trackable_history_geojson_export_uri` — XLSForm `file` — attachment
- `geocache_trackable_history_captured_time_iso` — XLSForm `text`
- `geocache_trackable_history_audit_json` — XLSForm `text`
- `geocache_trackable_history_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.account`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_geocaching_account.xlsx`  
**Acquisition:** Status is direct. Connect uses provider browser authorization + PIN. Geocaching.com login remains gated.  
**Persistence:** OAuth/account configuration persists because account connection is the requested operation.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.account',input_provider='opencache_uk',input_action='status',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_provider` — Provider ID from the persistent provider registry (showcase default `opencache_uk`; custom IDs are supported)
- `input_action` — Account action (showcase default `status`)
- `input_consumer_key`, `input_consumer_secret` and `input_verifier` are capability settings for account setup. Canonical showcase forms intentionally do not embed provider secrets; prefer native provider setup/browser authorization.

**Canonical returns**

- `geocache_account_status` — XLSForm `text`
- `geocache_account_result` — XLSForm `text`
- `geocache_account_provider` — XLSForm `text`
- `geocache_account_connected` — XLSForm `text`
- `geocache_account_username` — XLSForm `text`
- `geocache_account_user_uuid` — XLSForm `text`
- `geocache_account_profile_url` — XLSForm `text`
- `geocache_account_caches_found` — XLSForm `integer`
- `geocache_account_last_refresh_iso` — XLSForm `text`
- `geocache_account_authorization_url` — XLSForm `text`
- `geocache_account_action` — XLSForm `text`
- `geocache_account_captured_time_iso` — XLSForm `text`
- `geocache_account_audit_json` — XLSForm `text`
- `geocache_account_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.

## Integration Card — `geocache.sync`

**Status tags:** Development · ONLINE_ONLY  
**Showcase:** `example_odk_showcase_geocaching_sync.xlsx`  
**Acquisition:** Explicit network action: refresh account, import history, or upload one selected visit.  
**Persistence:** Sync/import/upload state and receipts persist because persistence is intrinsic to the requested operation.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='geocache.sync',input_provider='opencache_uk',input_operation='refresh_account',input_limit='500',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_provider` — Provider ID from the persistent provider registry (showcase default `opencache_uk`; custom IDs are supported)
- `input_operation` — Operation (showcase default `refresh_account`)
- `input_local_visit_id` — Local visit ID (required for upload_visit)
- `input_limit` — Remote history import limit (showcase default `500`)

**Canonical returns**

- `geocache_sync_status` — XLSForm `text`
- `geocache_sync_result` — XLSForm `text`
- `geocache_sync_provider` — XLSForm `text`
- `geocache_sync_operation` — XLSForm `text`
- `geocache_sync_local_visit_id` — XLSForm `text`
- `geocache_sync_remote_log_id` — XLSForm `text`
- `geocache_sync_imported_count` — XLSForm `integer`
- `geocache_sync_pending_upload_count` — XLSForm `integer`
- `geocache_sync_state` — XLSForm `text`
- `geocache_sync_captured_time_iso` — XLSForm `text`
- `geocache_sync_audit_json` — XLSForm `text`
- `geocache_sync_error` — XLSForm `text`
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The MethodMesh capability owns live acquisition and Commit. External/ODK launches use the existing Android result channel and return after Commit rather than detouring through a generic result screen.
