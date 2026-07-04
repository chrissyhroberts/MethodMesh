# ResearchOS ODK invocation context refactor

This patch makes the first ODK/ResearchOS operating model explicit.

## Operational model

ODK or another caller supplies a stable subject context, for example:

```json
{
  "caller": "odk",
  "context_entity_type": "participant",
  "context_entity_id": "participant/P001",
  "visit_id": "visit_1",
  "form_id": "odk_demo_form",
  "operator_id": "operator_1"
}
```

ResearchOS then executes a capability, records graph objects, and attaches those
objects back to the caller's subject entity.

## Main changes

- Added `InvocationContext` in `core/researchos/Contexts.kt`.
- Added an active invocation context to `ResearchSession`.
- `ResearchSession.record()` now enriches execution results with the active
  context when the incoming request does not already contain one.
- `ResearchGraph.record()` creates/updates the context subject entity and writes
  explicit `context.has_observation`, `context.has_state`,
  `context.has_transformation`, and `context.associated_entity` relationships.
- The ResearchOS panels now have a caller-context panel before Capability,
  Execution and Graph.
- The Graph panel is filtered by method and subject, so testing `P001` and
  `P002` no longer mixes graph objects.

## Intended first workflow

1. ODK form knows `participant_id` / `specimen_id` / `visit_id`.
2. ODK invokes a ResearchOS capability.
3. ResearchOS receives the invocation context.
4. Capability scans NFC, verifies identity, records GPS, etc.
5. Graph objects are linked to the context entity.
6. ODK receives only flat transport fields plus an execution identifier.

