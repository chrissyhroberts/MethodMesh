# MethodMesh v2.1.7 Release Notes

MethodMesh v2.1.7 is a focused protocol-output fix release. It corrects how protocol runs are exported so that a protocol behaves as one coherent submission rather than a set of separate per-step exports.

## Highlights

### Single-folder protocol exports

Protocol runs now create the shared protocol output folder before launching the first step.

Previously, the first step of a protocol could be treated like a standalone capability run. That produced a separate JSON file and folder before the later steps entered the protocol export path.

This is now fixed. A protocol run should export as:

- one protocol output folder;
- one canonical protocol JSON file;
- one shared `methodmesh_submission_id`;
- a `steps` array containing all completed protocol steps;
- any attachments linked to the same protocol run.

### Clearer protocol folder naming

Protocol output folders now use the convention:

```text
protocol_name__UUID___timestamp
```

For example:

```text
pain_protocol__7b7a6b6d-8db1-4c2f-b0a2-c2c94f07a4d2___2026-08-15T22_41_03.123Z
```

This keeps the human-readable protocol name first, makes the submission UUID easy to identify, and still preserves the run time in the filename.

### Website/user-guide update

The Quarto user guide has been updated and re-rendered. It now includes:

- v2.1.6 release notes in the website reference section;
- protocol-output and submission-ID guidance;
- a question primitives capability page;
- a random number generator capability page;
- scheduler documentation describing protocol runs and canonical output packages.

## Validation

Built with:

```text
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

The Quarto website was also rendered successfully with:

```text
quarto render
```

## Notes

This release does not add new study-facing capabilities. It stabilises the output behaviour introduced in v2.1.6 so protocol exports are easier to manage and parse in R.
