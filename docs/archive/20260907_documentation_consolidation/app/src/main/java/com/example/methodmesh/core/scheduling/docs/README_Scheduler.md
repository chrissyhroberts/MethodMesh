# MethodMesh Scheduler

## Capabilities

The scheduler creates local recurring alarms for an ODK/Kobo form, an online web form, or a future MethodMesh process target. It supports daily, weekly, monthly, and nth-weekday rules, with notification reminders and configurable retry intervals.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='scheduler.create',schedule_name='Daily check',schedule_target='ODK_FORM',schedule_target_value='my_form_id',input_project_id='my_project_id',schedule_frequency='DAILY',schedule_time='09:00',schedule_retry_count='2',schedule_retry_interval_minutes='60',return_mode='flat')
```

For a web form use `schedule_target='WEB_FORM'` and put the HTTPS URL in `schedule_target_value`.

## Inputs

- `schedule_name`
- `schedule_target`: `ODK_FORM` or `WEB_FORM`
- `schedule_target_value`
- `schedule_frequency`: `DAILY`, `WEEKLY`, `MONTHLY`, or `CUSTOM`
- `schedule_time`: `HH:mm`
- weekly `schedule_day_of_week` (1=Monday…7=Sunday)
- monthly `schedule_day_of_month` (1–31)
- custom `schedule_ordinal` (1–5) and `schedule_custom_weekday` (1=Monday…5=Friday)
- `schedule_retry_count` and `schedule_retry_interval_minutes`
- `schedule_notification_title` and `schedule_notification_message`
- optional `schedule_chain_id` and numeric `schedule_chain_order` for seamless multi-form sequences

ODK/Kobo targets may also provide `input_project_id` and `input_project_package`.

## Outputs

- `schedule_id`
- `scheduler_status`
- `scheduler_next_run_iso`
- `scheduler_target`
- `scheduler_target_value`
- `scheduler_frequency`
- `scheduler_error`

## ODK example

Import `example_odk_scheduler.xlsx`. The form creates a daily ODK schedule and returns its ID and next run time.

Schedules are local to the device. Notifications open the scheduled target; an ODK target goes through the normal `odk_form_launcher` capability boundary.

Schedules sharing a chain ID are launched in ascending chain order. When an ODK form completes successfully, the next form in the chain opens immediately.
# RIL schedule transfer

Schedules are portable through the core RIL boundary. The bundle is deterministic and includes a SHA-256 digest, so an imported package is validated before any schedule is stored.

Export all schedules:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='scheduler.export',return_mode='flat')
```

Export one schedule:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='scheduler.export',schedule_id='SCHEDULE_ID',return_mode='flat')
```

The result contains `schedule_bundle` and `schedule_bundle_sha256`-compatible payload evidence. The bundle can be encoded into a QR code or written with `nfc_tag_write`.

Import directly:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='scheduler.import',schedule_bundle='URL_SAFE_OR_JSON_BUNDLE',return_mode='flat')
```

Import via QR or NFC uses the same generic dependency boundary as other chained capabilities:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='scheduler.import',schedule_transport='QR',return_mode='flat')
```

or:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='scheduler.import',schedule_transport='NFC',return_mode='flat')
```

The QR/NFC dependency returns its payload to the scheduler importer; the scheduler then verifies the bundle hash and persists the schedules. No QR or NFC implementation is duplicated in the scheduler.
