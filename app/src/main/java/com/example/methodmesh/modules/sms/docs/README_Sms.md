# Send SMS

## Capabilities

`sms.send` sends a short SMS message to a phone number. The calling form or workflow is responsible for constructing the final message text; MethodMesh sends that message and returns an auditable status record.

This capability is intentionally simple. It does not assign identifiers, manage contacts, or interpret the message payload. For example, an XLSForm can concatenate a study message and a generated code, then pass only the final phone number and message to MethodMesh.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='sms.send',input_sms_phone=${phone_number},input_sms_message=${sms_message},return_mode='flat')
```

When called externally, MethodMesh attempts to send immediately after Android SMS permission is available. When opened from the MethodMesh dashboard, it shows the phone number and message first so the operator can test manually.

## Inputs

- `input_sms_phone`: destination phone number. Required.
- `input_sms_message`: complete message text to send. Required.

The non-prefixed forms `sms_phone` and `sms_message` are also accepted by the direct dashboard/scheduler path.

## Outputs

- `sms_phone`: destination phone number used.
- `sms_message`: final message text sent.
- `sms_message_sha256`: SHA-256 hash of the message text.
- `sms_status`: `sent` or `failed`.
- `sms_parts`: number of SMS parts sent after Android message splitting.
- `sms_sent_time_iso`: device time when the send result was recorded.
- `sms_error`: failure message if the SMS was not sent.

## ODK example

Open `example_odk_sms.send.xlsx` and install it in ODK Collect. The workbook captures a phone number and an arbitrary field value, calculates the final SMS body inside XLSForm, then calls `sms.send` with only the phone number and full message.

## Notes

Android requires the `SEND_SMS` runtime permission. MethodMesh requests that permission when needed. The capability records that Android accepted the send request; carrier delivery confirmation is not yet implemented.
