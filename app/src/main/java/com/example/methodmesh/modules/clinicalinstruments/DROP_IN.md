# Clinical Instruments drop-in installation

Copy this entire `clinicalinstruments` folder to:

```text
app/src/main/java/com/example/methodmesh/modules/clinicalinstruments/
```

No central capability registration edit should be required: `ClinicalInstrumentsModule.kt` follows the current MethodMesh module-discovery convention.

This prototype writes only to app-private `filesDir/clinical_instruments`; it does not require a Manifest permission, FileProvider path or external asset directory. Core runtime definitions are embedded inside the module to preserve the one-folder handoff rule. Human-review YAML copies are under `docs/core_definitions/`.

Then run:

```bash
./gradlew :app:assembleDebug
```

Keep the module **Development** while doing the checks in `docs/VALIDATION.md`.

The first recommended integration test is the included `docs/example_odk_ClinicalInstruments.xlsx`: choose qSOFA in ODK, let MethodMesh ask the three qSOFA inputs, and confirm ODK receives the individual response JSON, derived criteria, score, classification, instrument version and definition SHA-256.

`docs/ROADMAP_NOTE.md` contains the suggested central roadmap entry so this drop-in does not modify repository-level files it does not own.

## v0.1.1 launch patch

The initial v0.1 prototype used a page-level `verticalScroll` inside the capability body. MethodMesh host surfaces already own vertical scrolling; the nested unconstrained scroller could crash during Compose measurement on launch. v0.1.1 removes the inner page scroll and delegates scrolling to the host. See `docs/PATCH_0.1.1.md`.

## v0.1.2 detail/output patch

v0.1.2 fixes creation of new local instruments when `source_url` or `citation` are blank, and changes the normal completion/share surface to show the headline score/classification followed by every individual answer in instrument order. The structured ODK/audit contract remains available, with an additional `clinical_answers` human-readable field. See `docs/PATCH_0.1.2.md`.
