# Online API GET

## Capabilities

`api.get` runs a declared API definition through the shared online-data service. The module owns the native configuration screen and result projection. Definitions, cache behavior and HTTP execution belong to `core/onlinedata`.

## Android intent

Invoke `com.example.methodmesh.EXECUTE_METHOD` with `method_id='api.get'`. Supply capability settings through `input_` names and use `input_payload_mode='FULL'` for the execution sidecar.

## Inputs

`definition_id` selects a registered API definition. `result_path` selects the result value; definition-specific inputs are passed by name. Credentials and online connectivity requirements depend on the chosen definition.

## Outputs

`api_status`, `api_value`, `api_values_json`, `api_response_json`, provider/cache/retrieval metadata and `api_error` describe the outcome. `methodmesh_status` and `methodmesh_full_json` provide the shared execution result and evidence.

## ODK example

Use `example_odk_showcase_api_get.xlsx`. Its stable external form identity is independent of the filename. Review the definition and destination before sending data.
