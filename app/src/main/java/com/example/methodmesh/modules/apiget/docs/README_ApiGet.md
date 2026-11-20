# Online API GET

## Capabilities

`api.get` runs a declared API definition through the shared online-data service. The module owns the native configuration screen and result projection. Definitions, cache behavior and HTTP execution belong to `core/onlinedata`.

## Android intent

Invoke `com.example.methodmesh.EXECUTE_METHOD` with `method_id='api.get'`. Supply capability settings through `input_` names and use `input_payload_mode='FULL'` for the execution sidecar.

## Inputs

`definition_id` selects a registered API definition. `result_path` selects the result value; definition-specific inputs are passed by name. Credentials and online connectivity requirements depend on the chosen definition.

## Outputs

`api_status`, `api_value`, `api_values_json`, `api_response_json`, provider/cache/retrieval metadata and `api_error` describe the outcome. `methodmesh_status` and `methodmesh_full_json` provide the shared execution result and evidence.

## Reliability and cached data

Provider retrieval time and data age are left blank when no response was
retrieved; failed/offline requests do not claim freshly updated data. Cached
responses retain their original retrieval timestamp and explicit cache/stale flags.
Canonical execution timing remains available separately in the FULL envelope.

The shared executor honours `NO_STORE` on either the definition or request:
it neither reads nor writes the response cache. A network-policy override cannot
weaken a no-store definition. Existing cached entries are not deleted by a no-store
request, and fresh-required execution does not silently fall back to cached data.

Completed executions retain their exact identity/FULL evidence across ordinary
activity recreation, and edited input strings are saved. An interrupted in-flight
request does not restart automatically or leave the screen permanently busy; the
operator may explicitly retry. The blocking HTTP call may still finish or time out
after coroutine cancellation, so this is not durable/exactly-once background execution.
Unknown definition IDs fail locally rather than choosing another provider. Native
presets wait for declared Ask-when-run inputs before sending a request.

## ODK example

Use `example_odk_showcase_api_get.xlsx`. Its stable external form identity is independent of the filename. Review the definition and destination before sending data.
