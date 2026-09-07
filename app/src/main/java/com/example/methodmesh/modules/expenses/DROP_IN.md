# Expenses v0.4.0

Replace the existing `expenses` module folder completely with this folder.

Target:

```text
app/src/main/java/com/example/methodmesh/modules/expenses/
```

Public capability:

```text
expenses.manage
```

v0.4.0 adds:

- delete ledger with confirmation;
- editable ledger name/home currency/currency rates;
- immediate recalculation after FX changes;
- stable row/category/home-amount attachment filenames;
- ML Kit document-scanner PDF attachment;
- multi-select image/PDF file picking.

The current MethodMesh app already includes the ML Kit document-scanner dependency used by the existing `documentscanner` module.

Build with:

```bash
./gradlew :app:assembleDebug
```
