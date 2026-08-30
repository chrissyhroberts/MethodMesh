# Capability documentation standard

Every independently discoverable MethodMesh capability module owns its documentation. Documentation travels with the implementation and is reviewed whenever its public contract changes.

## Required module structure

```text
modules/<module>/
├── <ModuleName>Module.kt
├── ...
└── docs/
    ├── README_<CapabilityModule>.md
    ├── example_odk_<Capability>.xlsx
    └── ...
```

The module guide must have exactly one self-identifying filename matching `README_*.md`. A module that exposes several related capabilities documents all of them in that one guide.

## Required guide content

Every guide must include:

- `## Capabilities` — public method IDs and their purpose;
- `## Android intent` — complete, copyable invocation examples;
- `## Inputs` — accepted names, types, defaults, constraints, and policies;
- `## Outputs` — caller-facing result fields and their interpretation;
- `## ODK example` — what the bundled workbook demonstrates and how to use it.

The guide must distinguish:

- manual/debug behaviour from external invocation behaviour;
- native MethodMesh behaviour from ODK/external caller behaviour;
- configuration fields from runtime input fields;
- required inputs from optional inputs;
- canonical current fields from deprecated fields;
- successful, cancelled, and failed outcomes;
- direct work from capability dependencies.

## Required I/O contract

Every capability must document five field classes:

- **Runtime inputs**: user-facing values supplied at execution time, such as question answers, SMS text, translation text, destination text, or picked media. Presets must not permanently store these values unless the capability is explicitly a fixed-message/fixed-value tool.
- **Configuration inputs**: stable setup choices saved in presets, such as language pair, sensor/device choice, barcode format, camera/source policy, validation regex, or output mode.
- **Core outputs**: the small set of values most users care about: temperature, humidity, presence, answer, translated text, barcode payload, file name/URI, generated number, etc.
- **Audit-priority outputs**: ALCOA-relevant fields that are useful as individual return fields when requested, including execution ID, status, timestamps, device identifiers, hashes, selected source, errors and warnings.
- **Full JSON output**: a single JSON field containing the complete auditable return package. This must be returned only when explicitly requested; it must not be expanded into many individual fields by default.

The default external and native preview behaviour is:

1. run the action;
2. show/return the core outputs;
3. optionally include audit-priority fields;
4. optionally include one full JSON field for complete audit/debug capture.

ODK examples should normally store core outputs in normal fields and, when audit capture is required, store the full JSON in one spare text field.

## Required XLSForm example

The module must have one self-identifying workbook matching
`example_odk_*.xlsx` for every public capability it exposes. Each must be a real
XLSForm workbook containing sheets named:

- `survey`;
- `choices`;
- `settings`.

The canonical capability ID is the naming authority. A workbook is named
`example_odk_<method-id>.xlsx`, and its `settings.form_title` is exactly
`<method-id>`. A workbook demonstrating a verification or presentation variant
uses the same suffix in both places, for example
`example_odk_attestation.create_Fingerprint.xlsx` with
`form_title=attestation.create_Fingerprint`.

Each workbook should exercise one public capability. Intent-driven examples must:

- use `com.example.methodmesh.EXECUTE_METHOD`;
- identify the requested method explicitly;
- place the action on a `begin_group` with `appearance` set to `field-list` when several values are returned;
- include child fields whose names match returned intent extras;
- prefix every capability input with `input_`; unprefixed fields are outputs;
- place static configuration directly in `body::intent` so it does not appear
  as a question or stored field in the study dataset;
- use `input64_` with URL-safe Base64 without padding for static structured
  values whose punctuation is unsafe in XLSForm intent syntax;
- follow a known-working ODK pattern for those return fields, including read-only
  display fields where the deployed Collect version supports them;
- demonstrate the smallest useful input contract while retaining any safety-critical policy.

## Enforcement

`CapabilityDocumentationTest` discovers modules from their `*Module.kt` files. It fails when:

- the module has no `docs` package;
- the guide is missing or ambiguously duplicated;
- a required guide section is absent;
- no example workbook exists or a workbook lacks a required XLSForm sheet.

The test is intentionally generic. Adding a module must not require adding its name to the runtime, dashboard, documentation checker, or any other central list.

## Change rule

A change to a capability's method ID, accepted intent, input policy, dependency, result field, or caller lifecycle is incomplete until both its module guide and XLSForm example are updated.
