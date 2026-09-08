# MethodMesh ODK Forms host fix v1.00

This is a small host patch, not a module.

It fixes two behaviours in the ODK Forms library:

1. **XLSForms that are valid with conversion warnings**
   - ODK Central's form create/draft endpoints default to rejecting XLSForm conversion warnings.
   - MethodMesh's rapid test deployment now sends `?ignoreWarnings=true` for XLSX create and draft replacement.
   - Conversion **errors still fail**; only warnings cease to masquerade as a sync failure.

2. **Explicit update control**
   - Checkbox remains the test App User access/deployment toggle.
   - A locally changed, already-deployed form gets a real **Update** action.
   - Update shows `Updating…`, uploads/replaces the draft, republishes it, keeps the App User assigned, updates the stored local SHA-256, and then the `update available` state disappears.

Apply from the MethodMesh repository root:

```sh
git apply methodmesh-odk-forms.patch
```

Target files:

- `app/src/main/java/com/example/methodmesh/ui/odkcentral/OdkCentralClient.kt`
- `app/src/main/java/com/example/methodmesh/ui/odk/OdkTemplateLibrary.kt`
