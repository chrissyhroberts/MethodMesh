# External execution metadata fix

ODK multi-field intents encode function-style controls in `Intent.action`, for example:

`com.example.methodmesh.EXECUTE_METHOD(method_id='attestation.create',study_id='study_01',event_type='form_submission',trusted_timestamp='required')`

MethodMesh previously parsed Android extras and data URIs but did not parse the parameter block in `Intent.action`. Group child fields therefore reached the app, while controls present only in `body::intent` were lost and the attestation screen used dashboard defaults.

`AndroidIntentRequestReader` now parses the function-style action parameter block, merges it over non-blank extras, and then applies any explicit data URI controls. This makes intent-level `study_id`, `event_type`, `operator_id`, `verification_method`, `trusted_timestamp`, `trusted_timestamp_policy`, and `event_payload_hash` authoritative.
