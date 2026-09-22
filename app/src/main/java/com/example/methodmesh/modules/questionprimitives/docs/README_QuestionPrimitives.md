# Question primitives

## Capabilities

`question.text`, `question.number`, `question.select_one` and `question.select_multiple` provide independent native question surfaces. Each capability participates in presets, protocols and external execution.

## Android intent

Use `com.example.methodmesh.EXECUTE_METHOD` with the selected method ID. Capability inputs use `input_` names; request `input_payload_mode='FULL'` to retain execution evidence.

## Inputs

The method-owned descriptor and settings declare prompt, answer constraints and option lists for each question type. Text and number questions collect scalar answers; selection questions use the configured choices. Preserve option identifiers when revising an existing form.

## Outputs

Committed answers return through the capability's declared fields together with `methodmesh_status` and `methodmesh_full_json`. Cancellation is distinct from a committed empty answer. The native screen owns validation before commit.

## ODK example

The four `example_odk_showcase_question_*.xlsx` workbooks each demonstrate one independent capability call. They preserve their own `form_id` and capture the shared execution sidecar.
