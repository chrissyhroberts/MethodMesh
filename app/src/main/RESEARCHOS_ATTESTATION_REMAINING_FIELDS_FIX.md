# External attestation remaining-fields fix

- External invocation maps are merged without allowing blank ODK return fields to overwrite non-blank intent controls.
- `study_id` and `event_type` supplied by the caller therefore remain authoritative.
- `attestation_version=3` is returned as a compatibility alias for existing forms.
- `attestation_schema_version=3` remains the canonical specification field.
