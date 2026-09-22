# AprilTag platform integration status

The AprilTag module does **not** own or register a native library. It consumes the generic MethodMesh `platform.fiducial` API.

Required dependency direction:

```text
modules.apriltag -> platform.fiducial -> libmethodmesh_apriltag
```

The main app/platform must never import the AprilTag module, name its method IDs, or expose module-specific JNI symbols.

The shared detector provides:

- family selection;
- detector decimation, threads and edge-refinement configuration;
- one-or-many raw tag detections;
- image-space centre/corners, Hamming distance and decision margin;
- optional generic metric pose when tag size and camera intrinsics are supplied.

`AprilTagNativeBridge.kt` adapts those generic platform objects into the module's canonical types. If the shared library cannot load or create a detector, the UI fails closed and no canonical measurement is committed.
