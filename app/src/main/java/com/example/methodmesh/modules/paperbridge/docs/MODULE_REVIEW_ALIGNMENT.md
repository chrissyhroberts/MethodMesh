# Paper Bridge — MethodMesh review alignment

Initial review 10 September 2026; committed-export continuation 23 October 2026 against canonical Master Book v1.31.

## Compatibility decisions

- Stable method IDs remain `paper.form.transcribe` and `paper.form.design`.
- Existing stable input/output names are preserved; legacy source/rectified return switches remain accepted for compatibility.
- New paper schemas default to APRILTAG8 while QR4/BULLSEYE4 remain readable.
- Manifest-declared dynamic field names remain exact; Paper Bridge does not rename XLSForm variables to satisfy transport heuristics.

## Review repairs

- Commit no longer implies automatic transcription export/persistence.
- Native committed states expose explicit Share/Save/full-JSON/Done/Edit actions.
- ODK/external Commit returns directly to the caller and avoids native result/export detours.
- Caller-owned source attachments are not gratuitously duplicated.
- Attachment metadata no longer embeds local URI strings.
- Dynamic image outputs render as attachments rather than raw URI text.
- Designer working state is saveable; the original committed execution is retained in activity memory across ordinary recreation. Lost evidence is not fabricated. Large working images are reloaded from source identity.
- Designer lifecycle is SAVE SCHEMA → TEST EMPTY → TEST DATA → COMMIT.
- Schema deletion is confirmed.
- Canonical single-call ODK showcase workbooks and Integration Cards are supplied for both methods.
- Non-canonical designer-input workbook was moved to `support/` so `docs/**/*.xlsx` contains only active MethodMesh ODK showcases.

## Committed Share/Downloads continuation

Both committed screens now use canonical shared Share and Save to Downloads; reusable schema/workspace saves are unchanged. Native transcription omits the redundant raw page while preserving source evidence/external fields. Preset Share/Save performs the action before confirmation and stays open on failure. Thirty JVM cases and 15 Android cases were verified with the two-run/platform limitations in repository handoff docs/handoffs/PAPER_COMMITTED_EXPORTS_2026-10-23.md. Real capture/caller/recipient and legacy-Android permission acceptance remain open.

## Shared framework dependency (integration-history note)

Dynamic XLSForm image variables can have arbitrary names such as `site_sketch`. The current shared return projector uses field-name heuristics to decide whether a `content://` value enters Android `ClipData`. A generic transport fix is therefore required so returned `content://` values are attachments irrespective of key spelling. See `SHARED_FRAMEWORK_DEPENDENCY.md` and the separately delivered `METHODMESH_shared_content_uri_attachment.patch`.

## Remaining validation gate

Paper Bridge remains **Development** until the clean module and generic transport patch are integrated into the current host, the app build/tests run, and the canonical ODK round-trips are exercised on representative devices/servers. These are explicit integration/device validation gates rather than hidden module behaviour.

- Paper Bridge now preserves and checks supported XLSForm relevance/required/constraint logic during transcription, provides confirmed NA override for irreconcilable values, and emits a standalone manual-edit audit ledger plus logic audit outputs.
