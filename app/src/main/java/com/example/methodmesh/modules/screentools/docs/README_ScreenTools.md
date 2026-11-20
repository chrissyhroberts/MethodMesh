# Screen Tools

Screen Tools currently contains the first vertical slice of the rewritten
module: `screen.capture`.

## `screen.capture`

The capability requests Android MediaProjection consent, starts a typed
foreground service, and lets the operator capture the current visible screen
from the notification. Captured frames are retained only in process memory,
with a maximum of four frames. The capability returns a
`screen-session://...` reference for the current process only; it is not a
durable image attachment and must not be submitted to ODK as one.

The operator explicitly chooses **Use latest frame** before the result can be
committed. Stopping the service does not persist a screenshot. Process death,
discard, or a new capture session invalidates the in-memory frame reference.

## `screen.ocr`

`screen.ocr` reads the selected volatile frame with the bundled Latin ML Kit
recognizer. It returns the recognized text and a stable JSON array of word
regions using normalized image coordinates in the range `[0,1]`. The OCR
result is tied to the exact in-process frame reference; an expired reference
fails instead of silently selecting a newer frame.

## `screen.visual_select`

`screen.visual_select` lets the operator draw one or more normalized regions
on a captured frame, assign unique names, and commit the selection definition.
The current increment keeps those definitions in the process session only;
encrypted versioned persistence is owned by the upcoming Selection Memory
capability.

## Integration boundary

The module is discovered from `ScreenToolsModule.kt`. The MediaProjection
service is declared in the application manifest because Android requires a
typed foreground service for this operation. No overlay permission is used in
this first slice; capture is exposed through the foreground notification.

Visual selection, selection memory, visual extraction, web capture, app
opening, and image cropping will be added as separate methods after these
capture/OCR contracts pass device and transport validation.
