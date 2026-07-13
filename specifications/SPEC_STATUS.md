# ResearchOS Specification Status

**Last Updated:** July 13, 2026

This document defines which specifications are current, their version numbers, and their relationships.

## Current Specifications (Source of Truth)

### Foundation Layer
- **Philosophy**: `ResearchOS_Philosophy_0.01.md` (Stable)
  - Core vision and principles
  - Not versioned — foundational document

### Architecture & Design
- **Conceptual Model**: `design/ResearchOS_Conceptual_Model_v0.03.md` (Current)
  - Defines Entity → Assertion → Observation → Method framework
  - **Previous versions**: See `_archive/` (v0.01, v0.02)

- **Architecture Standard**: `architecture-standard/Architecture_Standard_v1.02.md` (Current)
  - System layering and component relationships
  - **Previous version**: `_archive/Architecture_Standard_v1.01.md`

### Language & Protocol
- **ResearchOS Intent Language (RIL)**: `ril/RIL_ResearchOS_Intent_Language_v0.03.md` (Current)
  - Declares WHAT, WHEN, WHERE, HOW, RESULT
  - Foundation for method execution
  - **Previous versions**: `_archive/RIL_Core_Verbs_v0.01.md`

- **RIL Core Verbs**: `core-verbs/RIL_Core_Verbs_v0.02.md` (Current)
  - Extends RIL specification with concrete verbs
  - **Previous version**: `_archive/RIL_Core_Verbs_v0.01.md`

- **JSON Representation**: `json/ResearchOS JSON Representation v0.02.md` (Current)
  - How RIL is serialized to JSON
  - **Previous version**: `_archive/RIL JSON Representation v0.01.md`

### Registries
All registries define canonical knowledge objects and their schemas.

- **Entities**: `registries/entities/ResearchOS_Entity_Registry_0.02.md`
  - Defines things being studied (person, place, instrument, etc.)
  - **Previous version**: `_archive/ResearchOS_Entity_Registry_0.01.md`

- **Observations**: `registries/observations/ResearchOS_Observations_Registry_0.01.md`
  - Evidence collected during research
  - **Status**: First release, expecting v0.02

- **Assertions**: `registries/assertions/ResearchOS_Assertions_Registry_0.02.md`
  - Claims about entities
  - **Previous version**: `_archive/ResearchOS_Assertions_Registry_0.01.md`

- **Intents**: `registries/intents/ResearchOS Intent_Registry_v0.02.md`
  - Declares what research is trying to accomplish
  - **Previous version**: `_archive/ResearchOS Intent_Registry_v0.01.md`

- **Traits**: `registries/traits/ResearchOS_Trait_Registry_0.02.md`
  - Attributes and properties of entities
  - **Previous version**: v0.01 (archived)

## Deprecated / Archive

See `_archive/` for:
- `ResearchOS_Conceptual_Model_v0.01.md`, `v0.02_draft.md`
- `Architecture_Standard_v1.01.md`
- `RIL_Core_Verbs_v0.01.md`, `RIL JSON Representation v0.01.md`
- `ResearchOS Intent_Registry_v0.01.md`
- `ResearchOS_Assertions_Registry_0.01.md`
- `ResearchOS_Entity_Registry_0.01.md`
- `XLSFormLab_Capability_Archetype.md` (exploratory, not maintained)
- `ResearchOS_Specification_v0_1.zip` (old bundle)

## Specification Dependencies

```
Philosophy (foundational)
    ↓
Conceptual Model (v0.03)
    ↓
┌─────────────────────────┬──────────────┐
│                         │              │
RIL (v0.03)          Architecture     Entity Registry
│                    Standard (v1.02)  (v0.02)
├─ Core Verbs (v0.02)      ↓          Assertion Registry
├─ JSON Repr. (v0.02)    Defines      (v0.02)
│                      layering      Trait Registry
└─ Intent Registry (v0.02)             (v0.02)
   Observations (v0.01)
   Assertions (v0.02)
```

## Version Numbering Scheme

- **Major version** (0, 1, 2...): Breaking changes to structure or semantics
- **Minor version** (v0.01 → v0.02): Additions or clarifications, backward compatible
- **Draft status**: Marked explicitly (e.g., `v0.02_draft.md`)

## Next Steps

- [ ] Observation Registry → v0.02 (in progress)
- [ ] Protocol Definition Language (coming)
- [ ] Extended RIL examples (see `/examples/`)
- [ ] Integration Guide (for existing platforms like ODK, KoBoToolbox)

---

**When to update this file:**
- Every time you publish a new specification version
- When creating new specifications
- When deprecating specifications
- Include the date and nature of change in git commit
