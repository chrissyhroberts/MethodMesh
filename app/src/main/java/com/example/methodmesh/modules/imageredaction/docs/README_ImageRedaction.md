# Image redaction

Capability ID: `image.redact`

This capability masks selected image regions and returns a redacted image attachment. It is intended for cases where identifying features, labels, faces, or other sensitive regions must be removed before export.

## Inputs

| Field | Description |
|---|---|
| `input_source` | `camera` or `file_picker`. |
| `input_grid_rows` | Number of mask-grid rows. |
| `input_grid_columns` | Number of mask-grid columns. |
| `input_redaction_style` | `black` or `white`. |

## Intent examples

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='image.redact',input_source='camera',input_grid_rows='10',input_grid_columns='10',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='image.redact',input_source='file_picker',input_grid_rows='12',input_grid_columns='12',input_redaction_style='black',return_mode='flat')
```

## Outputs

| Field | Description |
|---|---|
| `image_redaction_status` | `succeeded` when the redacted image was created. |
| `redacted_image_uri` | URI for the redacted image attachment. |
| `redacted_image_name` | Suggested filename for ODK/file export. |
| `redaction_mask_json` | Selected cell IDs. |
| `redacted_cells` | Number of cells masked. |
| `redaction_grid_rows` | Grid rows used. |
| `redaction_grid_columns` | Grid columns used. |
| `redaction_style` | Mask colour used. |
| `redaction_created_time_iso` | Creation time. |

The original image is used transiently for editing and is not returned as a capability output.
