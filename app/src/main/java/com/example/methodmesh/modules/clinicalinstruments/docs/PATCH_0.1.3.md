# Clinical Instruments v0.1.3 — Focus-mode question runner

## Purpose

Clinical instrument questions now run on a deliberately stripped-down focus surface.

## Changed

- The active question runner is no longer wrapped in `CapabilityScreenScaffold`.
- Hidden during question entry:
  - MethodMesh capability title and requested action ID
  - Library / Active / New tabs
  - capability description
  - subject/session metadata
  - instrument version and definition hash
  - source / citation / rights information
  - technical-detail disclosure
  - generic result/export controls
- Retained during question entry:
  - small instrument name
  - `current / total` progress
  - slim progress bar
  - question wording
  - protocol hint, only where the instrument definition explicitly contains one
  - answer control
  - validation message when needed
  - Back / Next (or Complete)
  - Save and exit for resumability

## Rationale

Bedside and screening use needs a low-noise data-entry surface. Provenance and technical details remain available before/after the run and in structured outputs, but are not part of the active questioning interface.

## Scope

No changes to:

- scoring/calculation logic
- YAML schema
- definition hashing/versioning
- autosave/resumability
- ODK return fields
- native result ordering (headline result, then individual answers)
