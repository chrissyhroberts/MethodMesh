# MethodMesh v2.2.1

MethodMesh v2.2.1 is a capability-polish release. It promotes the automatic barcode scanner and image redaction tools toward production use, tightens native result sharing, and updates ODK/XLSForm examples for the newer compact-result-plus-audit-JSON pattern.

## Highlights

### Image redaction promoted

`image.redact` is now marked as a production capability.

- Camera captures are decoded with EXIF orientation before display, so portrait and landscape captures should appear in the orientation in which they were taken.
- The redaction result survives Android orientation changes instead of dropping back to the trigger screen.
- Native preset runs inherit their saved source/grid/mask settings and go straight to the configured camera or image picker.
- The preset editor uses typed choices for source, grid rows, grid columns, and mask style.
- Native sharing is media-first: sharing an image-redaction result sends only the redacted image.
- The ODK example returns the redacted image URI as the main result and `methodmesh_full_json` as the audit/metadata payload.

### Barcode scanner production cleanup

`barcode.scan` is now the production code-scanning capability.

- The legacy `qr.scan` capability and old QR XLSForm examples were removed.
- Barcode format setup uses checkbox-style choices rather than free text.
- Scanner results survive Android orientation changes.
- Native sharing sends only the decoded barcode payload text.

### Preset and XLSForm refinements

- GPS navigator and Plus Code example XLSForms were updated to include the current Plus Code workflow.
- Local device authentication preset setup now uses the same style of typed controls as the runtime test screen.
- Capability examples continue moving toward the pattern: main result first, full audit JSON available when requested.

## Validation

Built and checked with:

```text
./gradlew :app:testDebugUnitTest --tests com.example.methodmesh.transport.OutputFormatterTest
./gradlew :app:testDebugUnitTest --tests com.example.methodmesh.modules.imageredaction.ImageRedactionOdkFormContractTest
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

The broad capability documentation contract test still reports older pre-existing documentation gaps in unrelated development modules. The new `image.redact` workbook and focused image-redaction contract are covered by this release.
