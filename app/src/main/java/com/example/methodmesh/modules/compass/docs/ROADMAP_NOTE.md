### Compass

- Added Development-stage `compass.read` capability.
- Flat magnetic compass plus vertical rear-camera-axis sighting mode.
- North or manually configured target bearing; configurable alignment tolerance; thick reticle turns green when aligned.
- Reuses shared `PhoneSensorRepository` and optional `LiveCameraPreview`; no duplicated orientation stack.
- v0.1 is explicitly magnetic north and does not request location.
- Open validation: full Gradle build, multi-device heading checks, orientation recreation, preset run, camera-denial path and ODK round trip.
- Future: optional true north via explicit public location fix + geomagnetic declination; haptic/audio alignment cue; composition with GPS target bearing.
