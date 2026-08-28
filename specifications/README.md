# MethodMesh Specifications

This folder contains all formal specifications for MethodMesh.

## Start Here

**New to MethodMesh?** Start with:
1. `MethodMesh_Philosophy_0.01.md` — Understand the vision (5 min)
2. `design/MethodMesh_Conceptual_Model_v0.03.md` — Learn the core model (10 min)
3. `architecture-standard/Architecture_Standard_v1.02.md` — See how it's organized (10 min)

Then dive into the Android Developer Guide: `../docs/ANDROID_DEVELOPER_GUIDE.md`

## Current Specification Status

See **`SPEC_STATUS.md`** for:
- Which specifications are current vs. archived
- Version numbers and dependencies
- What's in development

## Folder Structure

```
specifications/
├── SPEC_STATUS.md             ← START HERE: Current versions
├── MethodMesh_Philosophy_0.01.md
├── design/                    ← Conceptual model
│   └── MethodMesh_Conceptual_Model_v0.03.md (current)
├── architecture-standard/     ← System architecture
│   └── Architecture_Standard_v1.02.md (current)
├── ril/                       ← Research Intent Language
│   └── RIL_Research_Intent_Language_v0.03.md
├── core-verbs/               ← Extended RIL verbs
│   └── RIL_Core_Verbs_v0.02.md
├── json/                     ← JSON serialization
│   └── MethodMesh JSON Representation v0.02.md
├── registries/               ← Canonical knowledge objects
│   ├── entities/
│   │   └── MethodMesh_Entity_Registry_0.02.md
│   ├── observations/
│   │   └── MethodMesh_Observations_Registry_0.01.md
│   ├── assertions/
│   │   └── MethodMesh_Assertions_Registry_0.02.md
│   ├── intents/
│   │   └── MethodMesh Intent_Registry_v0.02.md
│   └── traits/
│       └── MethodMesh_Trait_Registry_0.02.md
└── _archive/                 ← Old versions (v0.01, v0.02, etc.)
    └── [previous versions]
```

## Key Specifications by Role

### I want to understand MethodMesh
- `MethodMesh_Philosophy_0.01.md` — Why it exists
- `design/MethodMesh_Conceptual_Model_v0.03.md` — How it thinks about research
- `architecture-standard/Architecture_Standard_v1.02.md` — How it's built

### I'm adding a new capability (Android)
- `ril/RIL_Research_Intent_Language_v0.03.md` — How to declare what you want
- `core-verbs/RIL_Core_Verbs_v0.02.md` — What verbs are available
- `registries/observations/MethodMesh_Observations_Registry_0.01.md` — What you can return

See `../docs/ANDROID_DEVELOPER_GUIDE.md` for step-by-step tutorial.

### I'm integrating with another platform (ODK, KoBoToolbox, etc.)
- `ril/RIL_Research_Intent_Language_v0.03.md` — How to generate RIL from your platform
- `json/MethodMesh JSON Representation v0.02.md` — How to serialize
- `registries/` — Map your data to our canonical objects

### I'm defining a new entity/observation/assertion type
- `registries/entities/MethodMesh_Entity_Registry_0.02.md` — Things being studied
- `registries/observations/MethodMesh_Observations_Registry_0.01.md` — Evidence collected
- `registries/assertions/MethodMesh_Assertions_Registry_0.02.md` — Claims about things

## Version Numbers Explained

- `v0.03` = stable, we're using this
- `v0.02` = previous stable, archived for reference
- `v0.01` = original, archived, likely superseded
- `_draft` = work in progress, not ready for implementation

## Finding What Changed Between Versions

Old versions are in `_archive/`. Compare with the current version:
```bash
diff _archive/MethodMesh_Conceptual_Model_v0.02_draft.md \
     design/MethodMesh_Conceptual_Model_v0.03.md
```

## Contributing to Specs

When you update a specification:

1. **Increment version**: v0.02 → v0.03 (if breaking change) or v0.02 → v0.03 (if additive)
2. **Update SPEC_STATUS.md** with new version number
3. **Archive old version**: Move to `_archive/` with old version number
4. **Commit with clear message**: "Spec: Conceptual Model v0.03 — Add relationship types"
5. **Update implementation** to match new spec version

## Examples

See `../examples/` for working example RIL declarations and how they execute end-to-end.

(Examples folder is under active development — check back often)

## Quick Links

- **Android Development**: `../docs/ANDROID_DEVELOPER_GUIDE.md`
- **Implementation Notes**: `../docs/implementation-notes.md`
- **Project Root**: `../README.md`

---

**Last updated**: July 13, 2026
