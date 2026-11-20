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

## Integration boundary

The module is discovered from `ScreenToolsModule.kt`. The MediaProjection
service is declared in the application manifest because Android requires a
typed foreground service for this operation. No overlay permission is used in
this first slice; capture is exposed through the foreground notification.

OCR, visual selection, selection memory, web capture, app opening, and image
cropping will be added as separate methods after this capture contract passes
device and transport validation.
