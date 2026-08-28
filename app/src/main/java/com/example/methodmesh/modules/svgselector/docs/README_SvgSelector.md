# SVG polygon selector capability

`svg.select` opens an SVG stored in MethodMesh app storage and lets the caller choose one polygon, several polygons, or an ordered polygon sequence.

## Capabilities

The module supports single selection, independent multi-selection, and strict ordered sequence selection. Every selection, removal, and rejected backstep is timestamped and included in an audit hash.

## SVG storage

SVG files selected from the file picker are copied into the app-private `files/svg` folder. The capability and intent callers refer to the file by its filename only; paths and content URIs are not required. The bundled `bodymap_black.svg` is copied/available as the default example.

## Modes

- `single`: one polygon is selected at a time. Selecting another polygon replaces the previous selection.
- `multiple`: polygons can be independently selected and removed.
- `sequence`: selections are numbered 1, 2, 3, etc. A selection can only be removed by backstepping from the current last item. Tapping an earlier item records a rejected-backstep audit event and leaves the sequence unchanged.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='svg.select',input_svg_name='bodymap_black.svg',input_selection_mode='sequence',return_mode='flat')
```

`input_svg_name` is the filename in app storage. `input_selection_mode` is `single`, `multiple`, or `sequence`.

## Inputs

- `input_svg_name` — SVG filename, without a path
- `input_selection_mode` — `single`, `multiple`, or `sequence`

## ODK example

Import `example_odk_svg.select.xlsx`. It calls `svg.select` against `bodymap_black.svg` in sequence mode and returns the selection and audit fields to ODK.

## Outputs

- `svg_name`
- `selection_mode`
- `selected_polygons` — compact JSON containing polygon IDs and sequence indexes
- `selection_events` — timestamped select, remove, backstep-remove, and rejected-backstep events
- `selection_audit_hash`
- `selection_started_at`
- `selection_completed_at`

SVG polygon/path/rect/circle/ellipse elements use their existing `id` as the stable polygon identifier. Elements without an ID receive a deterministic position-based fallback ID for that loaded SVG.
