# Module review alignment — Trusted timestamp

Reviewed against `METHODMESH_MASTER_BOOK.md` v1.06 as amended 2026-09-08.

## Declared owner contract

- ODK input: direct file or text, or MethodMesh interactive file picker/text entry; optional TSA URL and timeout.
- ODK output: proof ZIP plus metadata JSON; source is returned only when MethodMesh acquired it on ODK's behalf.
- Runtime input: file or text, TSA URL and timeout.
- Runtime output: source first, proof ZIP second, all supporting metadata contractually available, JSON optional in presentation.
- Maturity: Development (preserved; no explicit promotion requested).
- Connectivity: Online only.

## Review changes

- preserved method ID `integrity.trusted_timestamp`;
- preserved existing ODK `form_id` identities;
- added explicit maturity/connectivity metadata;
- declared direct file input and conditional source returns;
- implemented direct ODK text/file auto-execution and interactive external roundtrip when source is omitted;
- suppressed native Save/Share/Export controls during ODK external-roundtrip mode;
- retained historical `trusted_timestamp_proof_uri` contract key but projected it into attachment-compatible ODK fields;
- added conditional `trusted_timestamp_source_uri` and `trusted_timestamp_source_text` returns for MethodMesh-acquired sources;
- removed private proof URI/path from capability-owned metadata JSON;
- reorganised native UX into source (part 1) and proof ZIP (part 2) on one capability dashboard;
- made displayed scalar/text results tap-to-copy;
- added source/proof file actions for native runtime;
- added module-owned ODK Integration Card content and copyable intent templates;
- updated broader and focused XLSForms to the 2026-09-08 contract;
- removed stale generated XML projections from the clean handoff.

## Validation boundary

Static module review and XLSForm structural inspection were completed in the review environment. `pyxform` / ODK Validate were not available. A full Android app build cannot be run from the module-only ZIP because the shared MethodMesh project is not part of the handoff input.
