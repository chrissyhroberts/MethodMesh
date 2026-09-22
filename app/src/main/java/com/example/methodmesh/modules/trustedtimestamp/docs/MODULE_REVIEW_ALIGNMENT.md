# Module review alignment — Trusted timestamp

Reviewed against `METHODMESH_MASTER_BOOK.md` v1.21 (2026-09-13), including the current Commit and native Share/Save contracts.

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
- retained the existing generated XML projections unchanged as non-authoritative inspection artefacts; the XLSX workbooks remain the form source files.

## 2026-09-15 completion-contract refresh

- separated the mutable proof working result from the committed execution result;
- added an explicit **Commit** boundary after proof creation;
- stopped classifying every `IntentLaunch` presentation as an ODK/external roundtrip — only automatic-return intent runs use that path, so native preset/direct runs retain native completion actions;
- added a module-local committed panel backed by the shared `ResultShare`, `OutputExportRepository` and canonical `OutputFormatter` transports so dashboard launches receive the same post-Commit actions as other native surfaces;
- moved proof Share/Save persistence out of the pre-Commit working UI and onto the canonical post-Commit MethodMesh transport;
- post-Commit native runs now expose Share, Save to Downloads, Copy where meaningful and **Include full JSON / audit** (off by default);
- Save persists the proof artefact and, when the JSON toggle is enabled, the canonical run-specific metadata JSON sidecar;
- Share keeps the proof ZIP attachment and, when requested, appends canonical FULL JSON to the textual share payload rather than replacing/demoting the ZIP;
- ODK/external direct-input runs still auto-return when no MethodMesh interaction is needed; interactive external acquisition requires the on-screen Commit before returning;
- no canonical method IDs, ODK field names or XLSForm identities changed, so the existing XLSForms remain contract-compatible.

## Validation boundary

Static module review and XLSForm structural inspection were completed in the review environment. `pyxform` / ODK Validate were not available. A full Android app build cannot be run from the module-only ZIP because the shared MethodMesh project is not part of the handoff input.
