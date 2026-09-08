# MethodMesh ODK Forms host fix v1.01

Corrected version of v1.00. The v1.00 `.patch` had malformed bare `@@` hunk headers and could not be parsed by `git apply`.

From the MethodMesh repository root:

```sh
git apply --check methodmesh-odk-forms.patch
git apply methodmesh-odk-forms.patch
```

Changes:

- ODK Central XLSForm create/update calls add `?ignoreWarnings=true`, so a form that is valid with conversion warnings can still be deployed; conversion errors still fail.
- The checkbox is only the test App User access/deployment toggle.
- Changed deployed templates get a distinct `Update` action which uploads, republishes and keeps the App User assignment.

Targets current MethodMesh `master` files:

- `app/src/main/java/com/example/methodmesh/ui/odkcentral/OdkCentralClient.kt`
- `app/src/main/java/com/example/methodmesh/ui/odk/OdkTemplateLibrary.kt`
