# ResearchOS v2.1.6 Release Notes

ResearchOS v2.1.6 strengthens the app as a native protocol runner. The main change is a new protocol/output layer: reusable capability presets can be saved, chained into named protocols, run directly from ResearchOS, and exported as coherent JSON packages with linked attachments. This release also adds several native building-block capabilities, including random number generation and basic question primitives, moving ResearchOS closer to being a lightweight standalone data-collection runtime as well as an ODK companion.

## Highlights

### Protocol library

ResearchOS now has a protocol library for reusable native workflows.

You can:

- save a configured capability card as a reusable preset;
- view saved presets in the protocol library;
- run a saved preset directly;
- create named protocols by chaining presets in order;
- run a protocol from the app;
- use protocols from the scheduler.

This makes it possible to define a small reusable workflow once, then run it repeatedly without manually reconfiguring each capability.

### Canonical output packages

Direct capability runs and protocol runs now export into structured output folders.

Each export contains:

- one canonical JSON result file;
- any media/file attachments returned by the capability;
- a consistent `steps` array, even for single-capability runs;
- attachment metadata inside the JSON;
- linked filenames so JSON and attachments remain associated.

This is intended to make downstream parsing in R much easier. A protocol run should increasingly behave like one submission record rather than a scattering of unrelated output fragments.

### Submission UUIDs

Every exported run now has a stable submission identifier:

```text
researchos_submission_id
```

The same UUID is stored:

- in the output folder/filename;
- inside the top-level JSON;
- inside each step record;
- in attachment filenames.

For protocol runs, all steps share the same submission UUID. This gives each protocol execution a single key identifier that can later be used as the form/submission join key in R or other data-management tools.

### Protocol output handling

Protocol runs now write all step outputs into the same protocol output folder.

Intermediate steps no longer produce separate “saved output” notifications. ResearchOS only notifies once the whole protocol run has finished, reducing noise and avoiding the sense that each step is a separate study submission.

### Question primitive capabilities

This release adds native ResearchOS question primitives:

```text
question.text
question.number
question.select_one
question.select_multiple
```

These are basic building blocks for ResearchOS-native protocols that do not need a full XLSForm.

Common controls include:

- question ID;
- prompt;
- hint;
- required/not required;
- regex validation;
- constraint message.

Number questions also support minimum and maximum values. Choice questions support configurable option lists.

Returned fields include the question type, question ID, prompt, answer, validation status, validation rule, error message, and timestamp.

### Additional native capability building blocks

This release also includes several capability additions and refinements that support ResearchOS-native workflows:

- **Random number generator** (`random.number.generate`) — generates one or more random numbers with configurable count, minimum, maximum, step size, secure-random mode, and fixed-seed reproducible mode. This is a foundation for later allocation, block randomisation, and simulation tools.
- **Protocol-ready direct capability runs** — capability cards can be configured, saved as presets, run directly, and used as protocol steps without needing ODK as the handling app.
- **Improved intent-launch preview behaviour** — capability examples can be launched through the same fullscreen path used by external intent calls, making it easier to test what an ODK or scheduler-triggered user will actually see.
- **Output-aware capability execution** — direct runs and protocol runs can now save outputs and attachments into structured folders rather than only copying text to the clipboard.

### Output export UX

The output/export path has been tightened:

- direct capability results can be exported from the shared result panel;
- exports are placed under `Documents/ResearchOS/outputs` by default;
- configured output folders are supported;
- saved-output notifications include an open action;
- the home screen can open the output location.

### Runtime cleanup

This release also continues the cleanup of older compatibility paths. The goal remains that capabilities should be standalone modules, outputs should use the current canonical package structure, and new workflows should not depend on legacy transport shims.

## Validation

Built with:

```text
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

## Notes

The protocol library is now functional, but it is still early-stage UI. The next likely refinements are:

- clearer “run for test” versus “run for real” affordances;
- better protocol result previews;
- richer question-logic/relevance support;
- tighter scheduler integration for protocol runs;
- continued review of the canonical JSON shape against R import workflows.

The new question primitives are deliberately simple. They are intended as reusable ResearchOS-native building blocks, not as a replacement for full XLSForm logic yet.
