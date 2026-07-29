# ODK form launcher

## Capabilities

`odk_form_launcher` searches forms available locally in ODK Collect or Kobo Collect and opens a matching form for completion. The selector may be the form ID or display name. ResearchOS does not download forms; the form must already be present on the device.

## Android intent

Use the canonical ResearchOS action:

```text
com.example.researchos.EXECUTE_METHOD(method_id='odk_form_launcher',input_project_id='my_project_id',input_form_selector='my_form_id',return_mode='flat')
```

External invocation searches and opens the form immediately. When ODK returns, ResearchOS returns the matched form details and, when available, the saved instance URI. A dashboard invocation waits for the operator to press **Use result**.

## Inputs

| Input | Required | Description |
|---|---:|---|
| `input_project_id` | no* | ODK Collect project UUID. Use this when the same form ID exists in more than one project. |
| `input_form_selector` | yes | ODK form ID or exact display name. |

The form must be downloaded and available in ODK Collect or Kobo Collect. ResearchOS uses the supported exported forms-provider interface and does not rely on private activity names. On the dashboard, save the project name and local project UUID to build a reusable project registry; then choose a saved project and one of its forms.

## Outputs

| Field | Meaning |
|---|---|
| `odk_form_id` | Matched ODK form ID. |
| `odk_project_id` | Project ID used to scope the form URI. |
| `odk_form_name` | Matched display name. |
| `odk_form_uri` | ODK content URI used to open the form. |
| `odk_instance_uri` | Returned instance URI when ODK saves or completes the form. |
| `odk_launch_status` | `launched`, `returned`, `cancelled`, or `not_found`. |
| `odk_launch_time_iso` | ResearchOS launch-result time. |
| `odk_launch_error` | Explanation for a failed or cancelled launch. |

## ODK example

The bundled `example_odk_odk_form_launcher.xlsx` demonstrates an intent-driven launch using `input_project_id` and `input_form_selector`. The target form must already be downloaded in ODK Collect before pressing the launcher question.

This capability is intentionally separate from scheduling. A future scheduler can invoke this capability with a form selector when a scheduled task becomes due.
