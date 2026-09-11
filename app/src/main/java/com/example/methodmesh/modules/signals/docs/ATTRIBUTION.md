# Attribution

Signals uses Android platform APIs and dependencies already resolved by the MethodMesh host application.

## CameraX

Camera luminance reception uses AndroidX CameraX `Preview` and `ImageAnalysis` APIs for local frame analysis. Camera frames are not sent to a remote service by this module.

## Android sensors and audio

Light, magnetic-field, accelerometer, gyroscope, pressure and proximity observations use MethodMesh's shared Android sensor boundary. Audio transmit/receive uses Android `AudioTrack` / `AudioRecord`; rear-torch transmission uses Android camera/torch APIs.

## ZXing / JourneyApps

QR rendering uses ZXing QR encoding APIs. Continuous QR receive uses the JourneyApps ZXing Android Embedded camera view already present in MethodMesh.

See `THIRD_PARTY_NOTICES.md` for the applicable third-party project/licence summary.


## AprilTag tag16h5

Screen AprilTag Burst uses the published AprilRobotics `tag16h5` family constants for local fiducial rendering/decoding. The native AprilTag detector is not bundled; MethodMesh uses a constrained Kotlin camera analyser for the full-screen signalling case. See `THIRD_PARTY_NOTICES.md`.
