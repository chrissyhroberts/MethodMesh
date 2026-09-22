# SVG polygon selector capability

`svg.select` opens an SVG stored in MethodMesh app storage and lets the caller select one polygon, several polygons, an ordered polygon sequence, or repeated-tap heat-map levels.

## Capability behaviour

The selectable SVG is the primary interaction surface. Taps update a live working result on the same capability screen. **Commit selection** is the only finalisation boundary: it freezes the canonical payload once. Native/direct/preset runs remain on the same selector surface and reveal Share, Copy, Save, optional FULL JSON/audit and Done/Edit actions; external/ODK/protocol/schedule automatic-return launches send the committed payload directly back to their caller. Live taps are never exposed to the shared scaffold as `capturedResult`, so they cannot trigger the generic conclude/result panel.

Every selection change is timestamped and included in the audit hash.


## Preset presentation

A manually run single native preset opens `svg.select` as a full-screen instrument surface. The preset does not inherit the external workflow host's narrow scroll/padding region: the SVG receives the flexible centre of the display, while mode/SVG identity, live status, concise current selection, Reset, Cancel and **Commit selection** remain visible around it. After Commit, the same full-screen surface keeps the frozen SVG result visible and exposes the canonical native result actions. Fixed preset settings stay hidden. Protocol/schedule/headless execution is not forced into this presentation.

This is presentation-only. It does not create a preset-specific selector implementation or alter the canonical `svg.select` inputs/outputs.

## SVG storage

SVG files selected from the file picker are copied into the app-private `files/svg` folder. The capability and intent callers refer to the file by filename only; paths and content URIs are not required. The bundled `bodymap_black.svg` is the default example.

## Modes

- `single`: one polygon is selected at a time. Tapping the selected polygon again clears it.
- `multiple`: polygons can be independently selected and removed.
- `sequence`: selections are numbered 1, 2, 3, etc. A selection can only be removed by backstepping from the current last item. Tapping an earlier item records a rejected-backstep audit event and leaves the sequence unchanged.
- `heatmap`: each polygon has an integer level. Repeated taps cycle `0 → 1 → 2 → … → N → 0`, where `0` means unselected/cleared. `N` is configured by `heatmap_levels` and is constrained to 2–9 (default 5). Heat level is shown directly on the SVG using increasing colour intensity.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='svg.select',input_svg_name='bodymap_black.svg',input_selection_mode='heatmap',input_heatmap_levels='5',return_mode='flat')
```

## Inputs

- `input_svg_name` — SVG filename, without a path
- `input_selection_mode` — `single`, `multiple`, `sequence`, or `heatmap`
- `input_heatmap_levels` — optional number of non-zero heat levels, 2–9; default 5

## Canonical outputs

- `svg_name`
- `selection_mode`
- `selected_polygons` — compact structured JSON; sequence modes contain `sequence_index`, heat-map mode contains `level`
- `selected_polygon_ids` — JSON array of selected polygon IDs for convenient downstream use
- `selected_count` — count of polygons currently selected/non-zero
- `selection_summary` — concise human-readable projection (`left_arm, chest`, `1:left_arm → 2:chest`, or `left_arm=3, chest=1`)
- `polygon_levels` — JSON object mapping polygon ID to level in heat-map mode; `{}` otherwise
- `heatmap_max_level` — configured maximum heat level
- `selection_events` — timestamped select/remove/backstep/heat-level events
- `selection_audit_hash`
- `selection_started_at`
- `selection_completed_at`

`selected_polygons` remains the complete canonical structured selection output; the ID/count/summary projections make common native, ODK and protocol uses less dependent on parsing the full object array.

## ODK roundtrip

The example XLSForms call the same `svg.select` method and capture all declared capability returns plus `methodmesh_full_json` in the showcase form. Interactive ODK acquisition uses the normal selector screen; **Commit** returns directly to the calling form and Cancel returns cancellation.

SVG path/polygon/rect elements use their existing `id` as the stable polygon identifier. Elements without an ID receive a deterministic fallback ID for that loaded SVG.

## ODK INTEGRATION

**Capability**  
SVG polygon selector  
`svg.select`

**Tags**  
Maturity: Development  
Connectivity: Offline

**ODK INPUTS**

- `input_svg_name` | text | optional/defaulted | SVG filename in MethodMesh storage
- `input_selection_mode` | text | optional/defaulted | `single`, `multiple`, `sequence`, or `heatmap`
- `input_heatmap_levels` | integer | optional | non-zero heat levels, 2–9, default 5

Interactive acquisition: SVG polygon tapping in the native MethodMesh selector.

**INTENT CALL**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='svg.select',input_svg_name='bodymap_black.svg',input_selection_mode='heatmap',input_heatmap_levels='5',return_mode='flat')
```

**CANONICAL RETURNS**

- `svg_name` | text | always
- `selection_mode` | text | always
- `selected_polygons` | text/JSON | always after a committed selection
- `selected_polygon_ids` | text/JSON array | always after a committed selection
- `selected_count` | integer | always after a committed selection
- `selection_summary` | text | always after a committed selection
- `polygon_levels` | text/JSON object | always; populated for heat-map selections
- `heatmap_max_level` | integer | always
- `selection_events` | text/JSON | always
- `selection_audit_hash` | text | always
- `selection_started_at` | text/ISO timestamp | always
- `selection_completed_at` | text/ISO timestamp | always
- `methodmesh_full_json` | text/JSON | always on handled ODK roundtrip via shared transport

**FILE RETURN SEMANTICS**  
None. This capability returns structured/scalar data, not a binary attachment.

**RUNTIME**  
Inputs: stored SVG name, selection mode, optional heat-map level count.  
Beef: live selection on the SVG plus concise ID/count/summary projections.  
Metadata: complete structured selection, events, audit hash and FULL execution JSON.

## v0.3.1 — single-surface selector lifecycle

- Fixed the regression where the first polygon tap populated `capturedResult` and caused the shared scaffold to enter its conclude/result state.
- Polygon taps now mutate only the live working selection and audit trail.
- Removed the separate full-screen selection dialog: SVG interaction, live selection summary, reset/configuration and Commit remain on one continuous capability surface.
- Commit is now the only point that constructs the final `ExecutionResult` and returns it to the caller.
- Heat-map repeated-tap level cycling and v0.3.0 return projections are preserved.

## v0.3.2 — full-screen standalone preset surface

- A manually run native preset now launches the selector in a true edge-to-edge Compose dialog (`usePlatformDefaultWidth = false`, `decorFitsSystemWindows = false`).
- The SVG expands to consume the flexible centre of the screen rather than remaining inside the external workflow scroller/card region.
- Compact title/status, current selection, Reset, Cancel and Commit controls remain on the same full-screen surface.
- The full-screen branch is limited to standalone manual preset execution; automatic protocol/schedule/headless returns keep their normal host semantics.
- Direct/dashboard configuration behaviour and the canonical method/output contract are unchanged.


## v0.3.3 — canonical post-Commit result actions

- Restored the complete native result lifecycle without reintroducing a separate conclude/result screen.
- **Commit selection** now freezes an immutable committed snapshot for native/direct/preset runs instead of immediately closing the capability.
- The same selector surface then exposes the shared canonical **Share**, **Copy**, **Save to Downloads**, optional **full JSON / audit**, **Done**, and **Edit / new run** actions.
- Editing is disabled while a committed snapshot is active; choosing Edit returns to the working selection so the committed payload cannot silently mutate.
- ODK/external/protocol/schedule automatic-return launches remain origin-aware: Commit returns the canonical payload directly to the caller and suppresses native Share/Save controls.
- Working selection, audit events, commit snapshot and completion timestamp are saveable across activity recreation; the native canvas restores the selected polygons and accumulated audit events.


## v0.3.4 — preset Commit lifecycle fix

- Visible native preset runs no longer use `submitsImmediately` as the Commit closeout decision.
- Native preset Commit freezes the result and remains on the full-screen selector, exposing Share, Copy, Save to Downloads, optional full JSON/audit, Edit/new run and Done.
- Only ODK/external automatic runs and genuine headless/protocol/sequence execution return immediately on Commit.
- Result construction is guarded so a commit/projection error is surfaced in-place rather than tearing down the activity.
- On full-screen presets, the committed SVG preview contracts and the canonical result actions occupy a dedicated scrollable region so Share/Copy/Save/JSON/Done remain reachable on smaller displays.
