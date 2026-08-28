# Scaled photo selector capability

## Capabilities

`scaled_photo.capture` opens a CameraX capture surface with a physical-ruler HUD, preserves the original photograph, and optionally presents a configurable grid for region selection. The selected cells are written onto a separate annotated image and returned with a compact JSON selection record.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='scaled_photo.capture',input_ruler_length_mm='50',input_grid_rows='4',input_grid_columns='4',input_show_grid='true',return_mode='flat')
```

The caller can set `input_ruler_length_mm`, `input_hud_scale_ratio` (`1`, `2`, `3`, or `4`), `input_capture_orientation` (`portrait` or `landscape`), `input_grid_rows`, `input_grid_columns`, `input_show_grid`, and `input_macro_mode`. The default overlay is 10 × 10. The HUD line remains the calibrated on-screen reference length; the ratio changes the real-world ruler it represents. For example, a 50 mm line at ratio `2` is labelled as a 100 mm ruler target, while the HUD itself remains 50 mm. The HUD is vertical and mounted against the left edge of the camera view, where the physical ruler should be placed. Its pixel length uses the systemwide calibration value (`dp/mm`) stored by MethodMesh.

## Inputs

- `input_ruler_length_mm`: physical calibration ruler length represented by the HUD
- `input_grid_rows`, `input_grid_columns`: overlay dimensions; both default to 10
- `input_macro_mode`: closer focus/zoom target for close-up subjects
- `input_show_grid`: whether the grid is drawn on the annotated image

## Outputs

- `original_image_uri`: untouched captured image
- `annotated_image_uri`: copy with selected cells and optional grid
- `grid_selection_json`: selected cell IDs such as `r2c3`
- `grid_selection_hash`
- `ruler_length_mm`: physical length of the on-screen HUD line
- `ruler_target_length_mm`: physical ruler to align against after applying the HUD ratio
- `hud_display_length_mm`: on-screen HUD line length
- `calibration_pixels_per_mm`
- `photo_captured_at`, `overlay_completed_at`

The annotated image includes a visible `r1c1` marker at the top-left origin. Captures can be explicitly set to portrait or landscape; the selected orientation is normalized before the grid is shown and written to the annotated image.

The original and annotated files are app-private content URIs exposed through the existing MethodMesh FileProvider; the grid selection data is returned separately for analysis.

## ODK example

Import `example_odk_scaled_photo.capture.xlsx`. It demonstrates a 50 mm HUD, portrait capture, a 10 × 10 grid, and maps the original and annotated outputs to ODK `image` questions. MethodMesh returns those files through Android `ClipData` with temporary read permission; ODK stores the attachments in the form submission rather than storing the internal MethodMesh URI as text.
