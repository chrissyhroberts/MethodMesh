# MethodMesh reviewed XLSForm installer — 2026-09-07

This package contains only the reviewed XLSForm payload from
`MethodMesh_modules_ODK_clean_names_20260907.zip`, plus an installer and manifest.

Payload:
- 423 XLSForms
- 276 focused capability showcase forms
- 147 broader/legacy examples
- 66 modules
- zero files outside `modules/<module>/docs/*.xlsx`

The installer targets:

`app/src/main/java/com/example/methodmesh/modules/<module>/docs/*.xlsx`

It does not install any Kotlin, Markdown, generated assets, or other files from
the reviewed bundle.

From the MethodMesh repository root:

```bash
python3 /path/to/MethodMesh_ODK_XLSForm_Installer_20260907/install_reviewed_xlsforms.py
```

Safety behaviour:
- Existing identical XLSForms are left alone.
- Existing clean but different reviewed XLSForms are backed up outside the repo and replaced.
- Locally modified/staged/untracked reviewed XLSForms are skipped by default.
- Extra XLSForms not in the reviewed set are reported but never deleted.
- Use `--dry-run` to preview.
- Use `--overwrite-dirty` only after deciding the reviewed copy should replace a local XLSForm; the local copy is backed up first.
