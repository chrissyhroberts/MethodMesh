# Data tools

Status: **Development**

Offline data-format and encoding utilities.

## Capabilities

- `data.tools` — JSON pretty-print/minify/validation; Base64, URL and hex encode/decode; Unix timestamp ↔ ISO-8601 time; CSV ↔ JSON.

YAML/XML conversion is intentionally deferred until a dependency case is justified.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='data.tools',input_operation='json_pretty',input_data='{"a":1,"b":[2,3]}',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='data.tools',input_operation='unix_to_iso',input_data='1788642000',return_mode='flat')
```

## Inputs

- `input_operation` — `json_pretty`, `json_minify`, `json_validate`, `base64_encode`, `base64_decode`, `url_encode`, `url_decode`, `hex_encode`, `hex_decode`, `unix_to_iso`, `iso_to_unix`, `csv_to_json`, `json_to_csv`.
- `input_data` — runtime payload.

## Outputs

Core outputs:

- `datatool_output`
- `datatool_valid`
- `datatool_detected_type`

Audit-priority outputs:

- `datatool_status`
- `datatool_error`

Full metadata:

- `datatool_metadata_json`

## ODK example

`example_odk_data.tools.xlsx` demonstrates Base64 encoding and captures the output/status fields.

## Permissions and offline behaviour

No permissions and no network access. UTF-8 is used for text/Base64/hex transformations.

## Known limitations

- CSV support is intentionally lightweight: quoted fields, escaped quotes, commas and line breaks are handled, but dialect detection and schema typing are not.
- `json_to_csv` expects a top-level JSON array of objects.
- Unix timestamp input is interpreted as milliseconds when larger than 10,000,000,000; otherwise seconds.
