# Capability documentation standard

Every independently discoverable ResearchOS capability module owns its documentation. Documentation travels with the implementation and is reviewed whenever its public contract changes.

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
- required inputs from optional inputs;
- canonical current fields from deprecated fields;
- successful, cancelled, and failed outcomes;
- direct work from capability dependencies.

## Required XLSForm example

The module must have one self-identifying workbook matching
`example_odk_*.xlsx` for every public capability it exposes. Each must be a real
XLSForm workbook containing sheets named:

- `survey`;
- `choices`;
- `settings`.

Each workbook should exercise one public capability. Intent-driven examples must:

- use `com.example.researchos.EXECUTE_METHOD`;
- identify the requested method explicitly;
- place the action on a `begin_group` with `appearance` set to `field-list` when several values are returned;
- include child fields whose names match returned intent extras;
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
