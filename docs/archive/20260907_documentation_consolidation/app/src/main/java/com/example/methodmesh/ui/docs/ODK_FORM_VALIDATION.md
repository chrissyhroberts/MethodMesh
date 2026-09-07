# MethodMesh XLSForm library validation

MethodMesh treats module-owned XLSForms as executable design assets, not incidental documentation.
The Forms library therefore carries a validation report for every workbook discovered under module
`docs/` folders.

## Validation layers

Every build runs the dependency-free **MethodMesh XLSForm lint**. It checks the workbook structure
and the parts of XLSForm that can be checked safely without converting the form, including:

- `survey`, `choices`, and `settings` structure;
- required survey/choice columns;
- blank, duplicated, and whitespace-containing field/choice names;
- balanced groups/repeats;
- `${field}` references to missing fields;
- select questions that reference missing choice lists;
- duplicated `form_id` values across the MethodMesh library;
- known JavaRosa-incompatible XPath functions such as `replace()`;
- MethodMesh filename, `form_id`, title, and version conventions.

When `xls2xform` is installed on the build machine, the same build also runs **pyxform + ODK
Validate** over every form. This is the authoritative compatibility layer used to catch conversion
and JavaRosa/XForm errors that static inspection cannot reliably detect.

One-time build-machine setup:

```bash
python3 -m pip install pyxform
```

The generator does not install software automatically and does not fail the Android build merely
because pyxform is absent. The app clearly reports whether full ODK Validate coverage was present.

## MethodMesh naming convention

Naming findings are deliberately reported rather than automatically rewritten. A deployed
`form_id` is a compatibility contract and must not be renamed casually.

For new or deliberately migrated forms:

```text
modules/<module>/docs/example_odk_<purpose>.xlsx
```

Use lower snake case for `<purpose>`.

Recommended settings:

```text
form_title   Human-readable purpose-specific title
form_id      <module>_<purpose>
version      YYYYMMDDrr
```

Examples:

```text
modules/astronomy/docs/example_odk_dew_risk.xlsx
form_title = Astronomy — Dew risk
form_id    = astronomy_dew_risk
version    = 2026090701
```

The filename is repository organisation; `form_id` is the stable ODK identity. Existing deployed
IDs should be preserved unless a deliberate migration is being performed.

## In-app workflow

**ODK forms → Validate XLSForms → Review** shows the batch validation surface.

It provides:

- total clean/forms-needing-revision counts;
- filters for Errors, Warnings, and Naming;
- module-grouped findings;
- tap any individual finding to copy a self-contained diagnostic;
- copy the whole batch report;
- export the report as Markdown;
- share the report through Android.

Server upload diagnostics remain separate because they may contain provider-specific state such as
an ODK Central version collision or Kobo deployment error. The library validation report is about
the form definition itself and MethodMesh standards.
