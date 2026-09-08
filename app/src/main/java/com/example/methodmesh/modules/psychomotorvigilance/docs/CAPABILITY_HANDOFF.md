# Capability handoff

Canonical delivery folder: `psychomotorvigilance/`

Copy this folder unchanged to:

`app/src/main/java/com/example/methodmesh/modules/psychomotorvigilance/`

The module exposes:

- module ID: `psychomotorvigilance`
- method ID: `psychomotor.vigilance.run`
- native screen: `PsychomotorVigilanceCapabilityScreen`
- status: Development
- protocols: `pvt_10_standard`, `pvt_b_3`

No shared UI or central method registry edit is intended. The current MethodMesh build discovers `*Module.kt` implementations automatically.

Documentation, third-party attribution, ODK example and validation notes are under `docs/`.

Suggested repository-level roadmap text is in `ROADMAP_NOTE.md`.
