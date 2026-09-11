# Paper Bridge — MethodMesh review alignment

Reviewed against the supplied MethodMesh Master Book on 2026-09-10.

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
- Designer working/committed state is saveable across ordinary Activity recreation; large images are reloaded from the saved source identity.
- Designer lifecycle is SAVE SCHEMA → TEST EMPTY → TEST DATA → COMMIT.
- Schema deletion is confirmed.
- Canonical single-call ODK showcase workbooks and Integration Cards are supplied for both methods.
- Non-canonical designer-input workbook was moved to `support/` so `docs/**/*.xlsx` contains only active MethodMesh ODK showcases.

## Shared framework dependency

Dynamic XLSForm image variables can have arbitrary names such as `site_sketch`. The current shared return projector uses field-name heuristics to decide whether a `content://` value enters Android `ClipData`. A generic transport fix is therefore required so returned `content://` values are attachments irrespective of key spelling. See `SHARED_FRAMEWORK_DEPENDENCY.md` and the separately delivered `METHODMESH_shared_content_uri_attachment.patch`.

## Remaining validation gate

Paper Bridge remains **Development** until the clean module and generic transport patch are integrated into the current host, the app build/tests run, and the canonical ODK round-trips are exercised on representative devices/servers. These are explicit integration/device validation gates rather than hidden module behaviour.

- Paper Bridge now preserves and checks supported XLSForm relevance/required/constraint logic during transcription, provides confirmed NA override for irreconcilable values, and emits a standalone manual-edit audit ledger plus logic audit outputs.
