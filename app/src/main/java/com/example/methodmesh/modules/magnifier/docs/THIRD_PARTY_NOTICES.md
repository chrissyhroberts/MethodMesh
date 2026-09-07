# Third-party notices — Magnifier

Capability: `visual.magnifier.capture`

## AndroidX CameraX

Provider: Android Open Source Project / AndroidX.

Use in this module: camera preview, capture, zoom, torch control and focus/metering.

Components already used by MethodMesh include:

- `androidx.camera:camera-camera2`
- `androidx.camera:camera-lifecycle`
- `androidx.camera:camera-view`

Licence: AndroidX libraries are distributed under the Apache License 2.0 unless a component states otherwise. The MethodMesh repository should continue to preserve its normal dependency licence/notice handling.

Network dependency: none.

Data sent off-device: none.

Credentials/API keys: none.

## Android platform APIs

The module also uses Android framework image, EXIF, permission, system-bar and `FileProvider` APIs. These introduce no external data provider or remote service.

## Privacy

Camera frames and result images remain on-device. The capability itself performs no upload, telemetry call, remote inference or background synchronization.
