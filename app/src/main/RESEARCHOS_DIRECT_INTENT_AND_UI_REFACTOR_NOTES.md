# Direct intent execution and workflow UI refactor

This iteration applies the following runtime rules across the external workflow surface:

- An intent-invoked capability enters its active operation immediately.
- The shared capability frame no longer presents a redundant Start / Retry gate before first execution.
- Retry is available only after a result exists.
- NFC scanning starts as soon as the capability screen opens.
- biometric/device verification starts as soon as the capability screen opens.
- GPS result capture and generic AS100 method execution start on entry.
- QR payload execution starts immediately when a payload is supplied by the caller.
- attestation starts its selected verification dependency on entry; the verification method can be supplied by intent settings.
- nightly attestation anchor generation starts on entry.
- capabilities continue to return canonical ExecutionResult objects and preserve dependency evidence.

The external workflow UI was also simplified and restyled:

- task-focused heading instead of a debug dashboard;
- rounded capability surface and clearer hierarchy;
- visible progress and capture state;
- result preview appears only after capture;
- technical identifiers are retained behind a disclosure control;
- consistent Cancel, Retry, Back, Continue and Use result actions.

Interactive tasks such as DCEs still require substantive participant/operator choices. Android permission, biometric and device-credential prompts remain because they are intrinsic platform requirements rather than redundant activation steps.
