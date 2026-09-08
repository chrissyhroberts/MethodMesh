# MethodMesh legacy XLSForm cleaner — 2026-09-07

Use this after installing the 423 reviewed XLSForms.

The cleaner does **not** delete an old workbook just because its filename looks
legacy. It requires the old workbook's XLSForm `settings.form_id` to match
exactly one reviewed canonical workbook in the same module.

It also refuses to remove locally staged, modified, or untracked legacy
workbooks.

First run a dry run:

```bash
python3 /path/to/MethodMesh_ODK_Legacy_Cleaner_20260907/clean_legacy_xlsforms.py
```

Review the counts. If the mappings are correct:

```bash
python3 /path/to/MethodMesh_ODK_Legacy_Cleaner_20260907/clean_legacy_xlsforms.py --apply
```

Every removed workbook is backed up outside the repository before deletion.
Canonical reviewed XLSForms are never modified by this cleaner.
