# Integration required before live use

The MethodMesh repository currently has CameraX but no native AprilTag build. Consequently:

- the Kotlin module can be reviewed/integrated without adding a compile-time Java dependency;
- live camera screens check `AprilTagNativeBridge.isAvailable`;
- if `libmethodmesh_apriltag.so` is absent, the screen displays an explicit error and no canonical measurement can be committed;
- the app-level native build change must be reviewed separately because the Master Book requires module handoffs not to smuggle project-level configuration into the returned module folder.

See `../native/README.md` for the JNI contract and integration steps.
