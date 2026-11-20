# MethodMesh v2.15.1 — Alpha

Released 2026-10-16. App version 2.15.1 (version code 17). This patch release updates the bundled SMS ODK forms.

## SMS ODK form mapping

- Labels the bundled phone field for comma-, semicolon-, or newline-separated recipients.
- Maps `sms_recipients_json`, `sms_recipient_count`, and `sms_recipient_results_json` in the bundled SMS forms.
- Keeps the existing `input_sms_phone` intent mapping, so single-recipient forms remain compatible.

## Validation

- Updated both bundled SMS XLSForm workbooks with the multi-recipient input and outputs.
- Debug APK build: passed.

The APK is debug-signed and intended for development, testing and controlled field evaluation.
