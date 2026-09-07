# UI iteration notes — 2026-09-07

This pass deliberately separates immediate UI work from framework work.

## Implemented now

- restored the dashboard hero hierarchy: **MethodMesh** with **Do Stuff** as the small relevance line;
- removed the `Collect · calculate · commit` slogan;
- removed the dashboard Library block: the drawer already owns navigation, so the dashboard is reserved for search and active work;
- Android Back now returns any top-level MethodMesh section to Dashboard before the system is allowed to leave the app;
- added a first-class **ODK forms** destination mirroring Capabilities/Presets by module;
- added searchable ODK template UI with Share/Save actions against a generated asset index;
- dashboard search can surface ODK templates as well as capabilities, presets and protocols;
- upgraded deterministic search ranking with a small plain-English synonym layer;
- further reduced global corner radii.

## Designed, not falsely presented as complete

- module-to-asset build projection for ODK XLSForm templates;
- Android notification/status/overlay/Quick Settings surface contract;
- module-owned tags and future local semantic search;
- typed protocol pipes with safe transform nodes and a later Scratch-like editor.

The next repository-level pass should implement the generic contracts/build tooling above rather than placing one-off behaviour in `HomeScreen.kt`.

## Iteration 4 — ODK Central rapid testing and destructive-action safety

- Added Settings → ODK Central connection UI.
- Central password is transient; only an encrypted bearer session is retained.
- ODK Forms checkboxes now deploy/update/publish and assign a selected test App User.
- Unticking revokes the test App User assignment without deleting the remote form.
- Added `Sync checked` and local SHA-256 deployment tracking.
- Added explicit remote Central removal with submission-aware confirmation.
- Added a shared destructive confirmation component.
- Preset deletion now confirms and reports protocol references.
- Registered-device deletion now confirms.
- Offline language-pack removal now confirms.
- Scheduler deletion still lives in the shared scheduler component outside the supplied `ui/` package; that component must adopt the same confirmation contract at repository integration time.
