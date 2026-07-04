# ResearchOS graph migration notes

This patch moves the Android prototype further away from legacy flat Method outputs and toward the AS/ResearchOS graph model.

## Implemented

- Added AS-native storage to `ResearchGraph`:
  - entities
  - attributes
  - observations
  - relationships
  - classifications
  - states
  - transformations
  - execution results
- Added `ResearchSession.record(ExecutionResult)` as the canonical recording entry point.
- Preserved legacy `Entity`, `Observation` and relationship storage for the current Compose UI and transport previews.
- Updated the graph screen to show AS observations and transformations before legacy records.
- Updated NFC read/write recording so NFC bundles record their AS `ExecutionResult` into the live session.
- Updated canonical NFC read/write helper methods to return AS-native observations rather than legacy observations.
- Added NFC tag entities (`entityType = NfcTag`) and links from NFC observations to tag subjects.
- Updated GPS recording so settings-based capture also records the AS `ExecutionResult`.
- Added GPS spatial target entities (`entityType = SpatialTarget`), WGS84 spatial context and navigation state records.
- Updated biometric verification so successful/failed authentication signals are recorded as AS execution results.
- Extended `As100ExecutionEngine.complete(...)` to accept all AS knowledge object families, not just observations/entities/states.
- Removed packaged source artefacts (`modules.zip`, `gpstargetnavigator.zip`) and `.DS_Store` files from the source tree.

## Deliberately retained

- Legacy `Method`, `MethodOutput`, `core.Observation`, `MethodRegistry` and `MethodCard` remain as compatibility shells.
- ODK/transport preview generation remains untouched and still consumes flat `MethodOutput`.

## Remaining risks / next slice

- The project upload did not include a Gradle wrapper/project root, so this patch was not Android-built end-to-end.
- Core non-Android graph/session classes were compiled with `kotlinc` successfully.
- The next clean slice should make `MethodCard` call AS methods directly and render `ExecutionResult` previews, using flat `MethodOutput` only as a transport projection.

## v0.4 schema refactor notes

This pass refactors declared output schemas so they are no longer only flat transport field lists.

Changes:
- `MethodOutputSchema` now carries `graphOutputs` alongside legacy `fields`.
- `GraphOutput` declares the ResearchOS knowledge object produced by a capability: Entity, Observation, State, etc.
- `GraphField` maps a transport/display field back to a graph path such as `Observation.values.distance_m` or `State.values.arrived`.
- `RequiredWhen` replaces the misleading required/optional-only UI wording with lifecycle-aware requirements:
  - `Always`
  - `OnSuccessfulCapture`
  - `IfAvailable`
  - `PreviewOnly`
  - `TransportOnly`
- The UI now shows graph outputs first, then flat transport fields.
- The validator only enforces fields that are genuinely required for deterministic preview/runtime validation. Interactive NFC fields are no longer treated as missing during preview.

Capability-specific fixes:
- GPS now declares target entity, target-navigation observation, arrival state, and navigation-outcome observation.
- GPS navigation-outcome fields are included in the flat schema so saved/aborted sessions are not undeclared outputs.
- GPS `update_count` is now emitted as an integer rather than a float-shaped counter.
- Calibrated scale now declares `use_range`, matching the actual output returned by `buildOutput()`.
- NFC read/write schemas now mark tag/envelope fields as `OnSuccessfulCapture` rather than required during preview.
- Admin fingerprint now declares its biometric verification as an `attestation.biometric_verification` observation.

Known limitation:
- The schema layer is now graph-aware for knowledge objects, but `Transformation` remains represented through graph paths and execution results rather than as its own `GraphOutput.objectType`, because the current `KnowledgeObjectType` enum intentionally excludes transient transformation records.

## Three-panel capability/execution/graph refactor

Added `ui/components/ResearchOSPanels.kt` and wired it into each `MethodCard`.

The method UI now separates three concerns:

1. **Capability** — reads `MethodOutputSchema.graphOutputs` and describes the ResearchOS knowledge objects the method declares it can produce.
2. **Execution** — shows the current flat return payload, validation status, latest recorded execution for this method, and includes a `Record current invocation to graph` action for debug/development use.
3. **Graph** — reads `ResearchRuntime.session.graph()` and shows the entities, observations, states, transformations and relationships created by recorded executions of the selected capability.

The existing transport preview has been retained under `Transport/debug output` so ODK/intent work is still accessible but no longer masquerades as the ResearchOS graph itself.

The debug execution currently injects a demo ODK-like caller context:

- `caller = odk`
- `context_entity_type = participant`
- `context_entity_id = participant/P001`
- `subject_id = participant/P001`

This is deliberately explicit so the next transport pass can replace the hard-coded demo context with fields parsed from the incoming ODK/Android intent.

## v10 ODK invocation-context pass

This pass makes the first operational ODK → ResearchOS → graph flow explicit.

### Added

- `InvocationContext` in `core/researchos/InvocationContext.kt`.
  - Captures caller (`odk`), `entity_type`, `entity_id`, `visit_id`, `form_id`, and `operator_id`.
  - Normalises IDs to canonical graph IDs such as `participant/P001`.
  - Can emit both a caller context map for `ExecutionRequest` and a subject `Entity` / `ArchitectureRef` for the graph.

- Runtime context tagging in `ResearchGraph.record(ExecutionResult)`.
  - Each recorded execution now upserts the caller-supplied context entity.
  - It creates AS graph relationships from that entity to produced entities, observations, states, and transformations:
    - `context.associated_entity`
    - `context.has_observation`
    - `context.has_state`
    - `context.has_transformation`
  - These relationships carry execution, method, caller, visit, form, and operator attributes.

- Three-panel UI now includes an ODK invocation context block.
  - Default demo context is `participant/P001`, `visit_1`, `odk_demo_form`, `operator_1`.
  - Recording a method execution now passes that context into the AS method request.
  - The graph panel filters to objects created for the selected context entity.

- Subject tagging for methods where the natural subject is the caller entity.
  - Calibrated scale observations now use the invocation context subject when provided.
  - Biometric verification observations now use the invocation context subject when provided.

- NFC live-path context overloads.
  - `As100NfcReadMethod.read/readBundle(...)` and `As100NfcWriteMethod.write/writeBundle(...)` now accept optional `InvocationContext`.
  - This allows future ODK-triggered NFC flows to tag live tag reads/writes to the participant/specimen/visit that requested them while preserving the tag as a domain entity.

### Operational model now implemented

```text
ODK/form context
    entity_type + entity_id + visit/form/operator
        ↓
ResearchOS ExecutionRequest.context
        ↓
Capability execution
        ↓
ResearchGraph.record(result)
        ↓
Context entity linked to observations/states/transformations/entities
        ↓
Flat return payload remains available for ODK transport/debug
```

The important distinction is preserved: a capability may still produce a domain subject such as an NFC tag or spatial target, but the caller's stable study entity is now queryable as the invocation context that requested and owns the work.
