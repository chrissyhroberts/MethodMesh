# MethodMesh Master Book v1.07 — XLSForm example policy update

Date: 2026-09-08  
Target: `docs/METHODMESH_MASTER_BOOK.md` v1.06  
Status: normative revision for incorporation into the canonical Master Book

## Decision

Active canonical MethodMesh-supplied XLSForm examples are **single-capability examples**.

One canonical XLSForm example:

- demonstrates exactly one independently callable MethodMesh capability/method;
- contains exactly one MethodMesh capability invocation;
- uses the canonical, unprefixed return-field names for that capability;
- does **not** use `methodmesh_return_namespace`;
- is designed to upload unchanged to ODK Central and KoboToolbox wherever the underlying XLSForm/question features are supported; and
- uses globally unique survey node names so Kobo can import the same workbook without provider-specific renaming.

`methodmesh_return_namespace` remains part of the MethodMesh transport contract, but it is an **advanced composition feature for user-authored forms that deliberately contain multiple MethodMesh calls**. It is not part of the canonical example-form pattern.

Existing multi-capability/broader MethodMesh examples are migration debt. They should be split into single-capability examples. Once replacement coverage exists, they should be removed from the active example library or retained only as explicitly historical/archival material. They do not count toward canonical capability XLSForm coverage.

---

# Required Master Book changes

## Document header

Replace:

```text
Version: v1.06
Status: FINAL - canonical project-wide documentation
Last updated: 2026-09-07
```

with:

```text
Version: v1.07
Status: FINAL - canonical project-wide documentation
Last updated: 2026-09-08
```

Update the YAML front-matter date to `2026-09-08`.

---

## Section 6 — Canonical returned folder structure

Remove the implication that a module should additionally supply broader/multi-capability XLSForms.

The `docs/` example should read conceptually as:

```text
<module_name>/
|-- docs/
|   |-- README_<CapabilityA>.md
|   |-- example_odk_showcase_<capability_a>.xlsx
|   |-- README_<CapabilityB>.md              # if the module exposes another capability
|   |-- example_odk_showcase_<capability_b>.xlsx
|   |-- VALIDATION.md                        # recommended where validation is meaningful
|   |-- ROADMAP_NOTE.md                      # optional
|   |-- THIRD_PARTY_NOTICES.md               # when required
|   `-- ATTRIBUTION.md                       # when required
...
```

Replace the current file-placement rule that permits broader/multi-capability examples with:

> Because every independently callable MethodMesh capability must remain addressable through the ODK/XLSForm roundtrip, provide a focused single-capability workbook for each callable capability. A canonical MethodMesh example workbook demonstrates exactly one capability/method and contains exactly one MethodMesh capability invocation. It uses the capability's canonical unprefixed return fields and does not require `methodmesh_return_namespace`.
>
> Existing broader or multi-capability XLSForms are migration artefacts, not the target example contract. Split them into independently importable single-capability examples. Once equivalent single-capability coverage exists, remove them from the active example library or retain them only as explicitly historical/archival material. They do not substitute for, or count toward, per-capability XLSForm coverage.

---

## Section 10 — ODK/XLSForm contract: Namespaces

Replace the current `## Namespaces` section with:

### Namespaces

`methodmesh_return_namespace` remains a supported transport feature for forms that deliberately compose more than one MethodMesh call.

For example, a user-authored form may request:

```text
methodmesh_return_namespace='photo'
```

and the namespace projector prefixes returned keys:

```text
redacted_image_uri        -> photo_redacted_image_uri
redacted_image_sha256     -> photo_redacted_image_sha256
methodmesh_full_json      -> photo_methodmesh_full_json
```

Namespaces exist to avoid collisions in deliberately composed multi-call forms. They are an advanced form-authoring feature and must remain stable because external forms may rely on them.

**Canonical MethodMesh-supplied example XLSForms MUST NOT require a return namespace.** Each canonical example contains exactly one MethodMesh capability invocation and uses the canonical unprefixed return-field names directly.

Do not make provider-specific namespace customization part of the normal example lifecycle. If a canonical example requires namespacing merely to upload to Kobo, treat that as an example-authoring defect and split the example into one capability per workbook.

---

## Section 10 — Flat + full JSON

Keep the existing flat/full-JSON contract, but clarify that the canonical single-capability example pattern is:

```text
methodmesh_status + capability-relevant declared return fields + methodmesh_full_json
```

These fields remain unprefixed in canonical MethodMesh examples. Capability-specific fields are included only where they are actually declared/resolved by that capability/shared transport contract.

---

## Section 10 — ODK Forms library ownership and discovery

Add the following normative text after the one-to-many catalogue rule:

> The catalogue is one-to-many because a module may expose many capabilities, not because one workbook should aggregate many MethodMesh calls. The active canonical target is one independently importable XLSForm example per independently callable capability.
>
> During migration, structurally valid legacy multi-capability workbooks may still be discovered so that they are not silently lost. They should be flagged as migration findings, do not count toward canonical per-capability coverage, and should be split/retired rather than made provider-portable through hidden namespace rewriting.

Replace wording that says existing broader forms and dedicated showcases may coexist indefinitely with:

> Existing broader/multi-capability workbooks may coexist temporarily during migration, but the canonical endpoint is single-capability examples only. New canonical examples must not introduce multi-capability MethodMesh calls.

---

## Section 10 — ODK Central rapid-test deployment

Add:

> MethodMesh-supplied forms are disposable examples/test forms, not live production study forms. Advisory provider warnings must not block rapid-test upload when the form is syntactically valid and the provider supports an explicit ignore-warnings mode. True conversion/validation errors and hard server failures remain blocking.

---

## Section 10 — Kobo rapid-test deployment

Add the following after the provider API description:

> Canonical MethodMesh example XLSForms are authored to be provider-portable and should be sent to Kobo unchanged. The canonical example contract therefore requires globally unique XLSForm survey node names even where another XLSForm engine could disambiguate repeated leaf names by group path.
>
> Kobo's `DuplicateNameException` / `Duplicate node name: <name>` is a real import failure, not a same-title/project collision and not an advisory warning. For a canonical MethodMesh example, such a failure is an authoring/portability defect. If it arises because a workbook contains several MethodMesh calls with repeated canonical return fields, split the workbook into one capability per example. Do not silently rewrite the canonical example with provider-specific namespaces.
>
> Provider-specific namespacing/repair may remain available as an advanced aid for user-authored multi-call XLSForms, but it is not part of the MethodMesh canonical example lifecycle and must never silently alter the module-owned source workbook.

---

## Section 10 — XLSForm batch validation

Replace/extend the duplicate-name rule with:

> Generic MethodMesh XLSForm structural lint is scope-aware. `SURVEY_NAME_DUPLICATE` is a structural error when the same node name is reused within the same sibling group/repeat scope. Reuse in distinct group paths is not, by itself, a generic XLSForm syntax error.
>
> Provider portability is checked separately. Kobo currently requires survey node names to be globally unique across the workbook. A repeated name in different groups therefore produces a provider-compatibility finding such as `KOBO_GLOBAL_NODE_NAME_COLLISION`. For an active canonical MethodMesh example, that finding is blocking because canonical examples are expected to upload unchanged to both ODK Central and Kobo where the used feature set is supported.
>
> This separation prevents the generic linter from pretending Kobo's stricter importer rule is universal XLSForm syntax while still enforcing the MethodMesh portability contract for canonical examples.

Also add:

> Validation-result navigation should be form- and finding-specific. Selecting a validation count/error for one form opens the relevant findings for that form (and relevant severity) rather than dropping the user at the top of a long batch report. The full batch report remains reachable explicitly.

---

## Section 10 — MethodMesh XLSForm naming convention

Replace the current distinction that describes `example_odk_<purpose>.xlsx` as a broader/multi-capability form and `example_odk_showcase_<purpose>.xlsx` as a focused form with:

> Module-owned active examples are single-capability workbooks. `example_odk_showcase_<purpose>.xlsx` remains the preferred focused capability-coverage name in the current repository. Existing `example_odk_<purpose>.xlsx` filenames may be retained during migration where renaming would create needless identity/deployment churn, but their content must converge on the same single-capability rule.
>
> New MethodMesh example workbooks must not be multi-capability showcases. Filename family does not relax the content invariant: one canonical example = one MethodMesh capability invocation.

Retain the existing rules for `form_id`, human-readable title, versioning and stable deployed identity.

Add:

> Splitting one historical multi-capability workbook into several capability examples creates several new example form definitions. Give each new form its own stable `form_id`; do not pretend the separate forms are the same remote form merely because they came from one historical workbook. Preserve the old remote identity only where the old workbook is deliberately retained as a legacy artefact.

---

## Section 10 — Reviewed 2026-09-07 module XLSForm baseline

Retain the 2026-09-07 numbers as historical evidence, but add at the start or end of the subsection:

> **Historical baseline note (v1.07):** this audit records the repository state on 2026-09-07; it is not the v1.07 target structure. The 147 broader/existing forms identified in that audit now require classification. Any active multi-capability MethodMesh example is migration debt to be split into single-capability examples or retired from the active example library. The 276 focused capability showcases are closer to the v1.07 target but must also satisfy the no-namespace and cross-provider global-node-uniqueness rules.

---

## Section 20 — Documentation rules

Replace the wording that allows broader examples to coexist as normal active examples with:

> For a module exposing several capabilities, documentation is capability-addressable: each method must be identifiable in the docs and each independently callable capability must have its own independently importable single-capability XLSForm example.
>
> Minimum for an independently callable capability:
>
> - `docs/README_<Capability>.md`
> - `docs/example_odk_showcase_<purpose>.xlsx`
>
> Historical broader/multi-capability examples may be retained only during migration or for explicit archival provenance. They are not canonical examples and do not satisfy capability coverage.

---

## Section 20 — Example XLSForms

Replace the section with:

### Example XLSForms

Canonical MethodMesh-supplied example XLSForms MUST:

- demonstrate exactly one resolved canonical MethodMesh method/capability contract;
- contain exactly one MethodMesh capability invocation;
- use a grouped intent call and the canonical MethodMesh execute action;
- use only declared/relevant input parameters for that capability;
- request `input_payload_mode=FULL` where the resolved contract supports it;
- use canonical unprefixed return-field names;
- **not** require `methodmesh_return_namespace`;
- capture `methodmesh_status` and `methodmesh_full_json`;
- capture capability-specific scalar, media, status, time and JSON returns only where those fields are actually declared/resolved for that capability;
- avoid unrelated module JSON leaves and synthetic uniformity fields;
- keep survey node names globally unique so the same workbook is portable to Kobo;
- avoid return/input field-name collisions;
- use attachment-compatible ODK question types for returned media;
- use human-readable titles, labels and hints while retaining the canonical method ID for traceability;
- remain independently importable/testable; and
- demonstrate realistic usage of that one capability rather than a synthetic debug-only call.

An example workbook may contain ordinary XLSForm questions, calculations, groups, explanatory notes and supporting form logic around the demonstrated capability. The restriction is on MethodMesh composition: a canonical example contains one MethodMesh capability call.

A canonical example is an executable demonstration of the existing capability contract, not a second schema. If a desirable field is absent from the runtime contract, record the gap; do not manufacture the field in the workbook.

`methodmesh_return_namespace` is documented as an advanced composition feature for form authors who intentionally place multiple MethodMesh calls in their own forms. Canonical MethodMesh examples do not teach or depend on that complexity.

Existing multi-capability/broader MethodMesh examples should be split into single-capability examples. Once replacement coverage exists, remove the multi-capability workbook from the active example library or retain it only as explicitly historical/archival material.

Do not solve a canonical-example portability problem by silently producing a Kobo-specific renamed copy. Fix the source example so one unchanged workbook expresses the portable single-capability contract.

---

## Section 20A — ODK/XLSForm review

Replace the relevant paragraph with:

> The supplied XLSForms are part of the module contract. A module is not complete merely because native execution works. For every independently callable capability, verify that a dedicated single-capability example exists, contains exactly one MethodMesh call, uses canonical unprefixed return fields, does not require `methodmesh_return_namespace`, and is structurally/provider-portably valid for ODK Central and Kobo where the feature set is supported.
>
> Run batch XLSForm validation and address genuine errors. Generic duplicate-name lint is scope-aware; Kobo global-name compatibility is a separate provider portability check. A canonical example that triggers a Kobo global node-name collision must be repaired at source, normally by removing accidental duplicate nodes or splitting an old multi-capability workbook, not by silently namespacing the canonical example.

---

## Section 20A — Example XLSForms during review

Replace the section with:

### Example XLSForms during review

Where ODK use is plausible, include working module-owned XLSForm examples.

Every independently callable capability receives its own independently importable single-capability example. A canonical example demonstrates exactly one MethodMesh method/capability and contains exactly one MethodMesh invocation.

Use the existing runtime contract: canonical method ID, declared inputs, canonical unprefixed return fields, `methodmesh_status`, `methodmesh_full_json`, and only capability-specific returns actually declared/resolved for that call. Do not invent success/time/JSON fields for uniformity.

Canonical examples do not require `methodmesh_return_namespace`. Namespace projection remains an advanced runtime/transport feature and should be tested separately from example-form coverage.

During review, identify broader/multi-capability examples as migration work. Split them into single-capability examples and retire them from the active example library once equivalent coverage exists. Do not preserve multi-call showcase complexity merely because a historical workbook used it.

Validate the same canonical workbook for both ODK Central and Kobo compatibility where feasible. Kobo global node-name collisions are provider-portability defects in canonical examples, not ignorable warnings.

---

## Section 21 — Testing rules

Change capability-promotion guidance from retaining broader/legacy examples as additional workflow coverage to:

> exercise each canonical single-capability XLSForm example where possible; verify one callable capability maps to one independently importable example; verify each canonical example contains exactly one MethodMesh invocation, contains no required `methodmesh_return_namespace`, uses canonical unprefixed return fields and remains portable to the supported ODK Central/Kobo test providers.

Namespace projection remains a contract-boundary/unit test because external/user-authored multi-call forms may depend on it. It is no longer a canonical-example requirement.

### XLSForm validation tests

Add:

- generic duplicate-name lint is group/repeat-scope aware;
- canonical examples contain exactly one MethodMesh invocation;
- canonical examples do not require `methodmesh_return_namespace`;
- canonical examples use globally unique survey node names for Kobo portability;
- every independently callable ODK-capable capability has its own canonical example or an explicit exemption;
- legacy multi-capability examples do not satisfy that coverage assertion.

---

## Section 25 — Troubleshooting: An XLSForm will not validate/upload

Replace/extend with:

> Use the batch XLSForm validator first, then inspect provider diagnostics. A Central/Kobo HTTP failure is not a substitute for local validation. Preserve exact form/module/error attribution and a copyable diagnostic report.
>
> Distinguish structural validity from provider compatibility. A generic duplicate-name error applies only to an actual duplicate in the same group/repeat scope. Kobo additionally rejects globally repeated survey node names and reports `DuplicateNameException` / `Duplicate node name: <name>`.
>
> For a canonical MethodMesh example, a Kobo global-name collision is an example-authoring defect. If the collision comes from several MethodMesh calls reusing canonical return names in different groups, the canonical remedy is to split the workbook into one capability per example. Do not namespace the canonical example merely to satisfy Kobo.
>
> For a user-authored form that intentionally composes multiple MethodMesh calls, `methodmesh_return_namespace` remains the supported advanced solution where the form author wants namespaced returns.

---

## Section 26 — Contributor/AI-chat quick rules

Replace rule 6 with:

> 6. Put docs and ODK XLSForms inside that folder. For each independently callable capability, provide one focused independently importable single-capability example. A canonical example contains exactly one MethodMesh capability invocation, uses canonical unprefixed return fields and does not require `methodmesh_return_namespace`. Do not create new multi-capability MethodMesh showcase workbooks.

Replace/extend rule 14 with:

> 14. Make every declared capability and every declared output available to the ODK/XLSForm roundtrip; examples are not allow-lists. Each canonical example demonstrates one capability only and must not invent success/time/JSON or other returns merely for uniformity. Namespace projection is an advanced composition feature, not a canonical-example pattern.

Add to final self-review:

> Verify that every ODK-capable method has its own single-capability example, no canonical example contains multiple MethodMesh calls, no canonical example requires `methodmesh_return_namespace`, and canonical examples satisfy Kobo global node-name uniqueness.

---

## Section 27 — MethodMesh taste test

Add to the ODK questions:

> Does every independently callable ODK-capable capability have one simple independently importable example? Does each canonical example invoke only that capability, use ordinary canonical return names and upload unchanged to both ODK Central and Kobo where the used XLSForm features are supported? Have we kept namespace composition out of the example library?

---

## Section 28 — Normative requirement register: XLSForm

Replace the current `MM-XLS-001` to `MM-XLS-010` block with:

- **`MM-XLS-001`** - A module may own zero, one or many XLSForms; each discovered workbook is catalogued independently, and each independently callable ODK-capable capability has its own canonical example or an explicit exemption.
- **`MM-XLS-002`** - Active canonical MethodMesh example XLSForms are single-capability workbooks. Existing `example_odk_showcase_<purpose>.xlsx` and legacy `example_odk_<purpose>.xlsx` filenames may coexist during migration, but filename family does not permit multi-capability content.
- **`MM-XLS-003`** - `form_id` is a stable external identity and is never silently renamed for filename/style cleanup.
- **`MM-XLS-004`** - Batch validation reports form/module-specific structural, provider-compatibility and naming findings and supports focused navigation plus copy/export.
- **`MM-XLS-005`** - Central access state, Kobo deployment state and collector-device state are represented separately.
- **`MM-XLS-006`** - A canonical example XLSForm demonstrates exactly one resolved declared MethodMesh capability contract and contains exactly one MethodMesh capability invocation.
- **`MM-XLS-007`** - Canonical examples use canonical unprefixed return fields and MUST NOT require `methodmesh_return_namespace`; namespace projection remains supported for advanced user-authored multi-call composition.
- **`MM-XLS-008`** - Filename, human-readable title, version and `form_id` are distinct concerns; identity metadata from the standard row-oriented XLSForm `settings` sheet is authoritative where present, and intentional revisions prefer monotonic `YYYYMMDDrr` versions.
- **`MM-XLS-009`** - Multi-capability/broader MethodMesh examples are migration/archival artefacts, do not count toward canonical capability coverage, and should be split/retired rather than made canonical through namespace customization.
- **`MM-XLS-010`** - Canonical examples capture `methodmesh_status`, `methodmesh_full_json` and only capability-specific return fields declared/resolved for that call; unrelated module leaves and synthetic uniformity fields are excluded.
- **`MM-XLS-011`** - Generic duplicate-name lint is group/repeat-scope aware; provider-specific global-name constraints are reported separately rather than mislabelled as universal XLSForm syntax.
- **`MM-XLS-012`** - Canonical MethodMesh examples use globally unique survey node names and are authored for unchanged ODK Central/Kobo portability wherever the underlying XLSForm features are supported.

---

## Appendix M — Version history

Insert above v1.06:

### v1.07 - 2026-09-08

- made the active canonical MethodMesh XLSForm example contract one capability per workbook and one MethodMesh invocation per example;
- removed return-namespace customization and multi-capability composition from the canonical example pattern;
- retained `methodmesh_return_namespace` as a supported advanced transport feature for user-authored multi-call forms;
- required canonical examples to use canonical unprefixed return fields and globally unique survey node names for unchanged ODK Central/Kobo portability;
- separated scope-aware generic duplicate-name lint from Kobo's stricter form-wide node-name compatibility rule;
- classified historical multi-capability/broader examples as migration/archival artefacts to split or retire rather than continuing them as canonical examples;
- clarified that Kobo `DuplicateNameException` is a real import/compatibility failure, not a same-title form collision or ignorable warning; and
- aligned module review, testing, troubleshooting and contributor guidance with the simplified single-capability example contract.

---

# Migration implication

This documentation change deliberately creates migration work against the 2026-09-07 baseline. Do not claim the repository already conforms merely because v1.07 defines the target.

The next repository-wide XLSForm migration should:

1. inventory all active module-owned XLSForms;
2. determine the MethodMesh invocation count in each workbook;
3. retain already-single-capability examples where they meet current contract rules;
4. split every active multi-capability example into separate per-capability workbooks;
5. remove `methodmesh_return_namespace` from canonical examples and restore unprefixed canonical return names;
6. ensure each new workbook has its own stable `form_id`, title and monotonic version;
7. ensure survey node names are globally unique for Kobo portability;
8. run MethodMesh structural lint, ODK/pyxform validation where available, and provider compatibility checks;
9. keep legacy multi-capability workbooks only where explicit archival/provenance value justifies them, outside the active canonical example set; and
10. regenerate the runtime ODK Forms catalogue after migration.

The target invariant is:

> **One canonical MethodMesh XLSForm example = one capability = one MethodMesh call = ordinary canonical return names = no return namespace customization.**
