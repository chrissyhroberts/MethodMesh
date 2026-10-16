# Send SMS

## Capabilities

`sms.send` sends a short SMS message to one or more phone numbers. Each recipient receives an identical individual SMS; MethodMesh does not create a group message. The calling form or workflow is responsible for constructing the final message text; MethodMesh sends that message and returns an auditable status record.

This capability is intentionally simple. It does not assign identifiers, manage contacts, or interpret the message payload. For example, an XLSForm can concatenate a study message and a generated code, then pass only the final phone number and message to MethodMesh.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='sms.send',input_sms_phone=${phone_number},input_sms_message=${sms_message},return_mode='flat')
```

When called externally, MethodMesh attempts to send immediately after Android SMS permission is available. When opened from the MethodMesh dashboard, it shows the phone number and message first so the operator can test manually.

## Inputs

- `input_sms_phone`: one or more destination phone numbers. Required. Separate recipients with commas, semicolons, or new lines. Duplicate entries are sent only once.
- `input_sms_message`: complete message text to send. Required.

The non-prefixed forms `sms_phone` and `sms_message` are also accepted by the direct dashboard/scheduler path.

## Outputs

- `sms_phone`: original phone-number input.
- `sms_recipients_json`: de-duplicated recipients sent, as a JSON array.
- `sms_recipient_count`: number of individual recipients.
- `sms_recipient_results_json`: per-recipient JSON results, including phone, status, and error.
- `sms_message`: final message text sent.
- `sms_message_sha256`: SHA-256 hash of the message text.
- `sms_status`: `sent`, `partial`, or `failed`.
- `sms_parts`: number of SMS parts sent after Android message splitting.
- `sms_sent_time_iso`: device time when the send result was recorded.
- `sms_error`: failure message if the SMS was not sent.

## ODK example

Open `example_odk_sms_send.xlsx` or `example_odk_showcase_sms_send.xlsx` and install it in ODK Collect. The bundled forms capture phone-number input and an arbitrary field value, calculate the final SMS body inside XLSForm, call `sms.send` with the phone-number input and full message, and map the recipient list, count, and per-recipient results. A single number keeps the original behaviour; multiple comma-, semicolon-, or newline-separated numbers result in separate identical SMS messages.

## Notes

Android requires the `SEND_SMS` runtime permission. MethodMesh requests that permission when needed. The capability records that Android accepted the send request; carrier delivery confirmation is not yet implemented.
