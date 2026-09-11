# Third-party notices

## AprilTag 3

This module is designed to use the AprilTag 3 C library from the April Robotics Laboratory / University of Michigan project (`AprilRobotics/apriltag`). The upstream project is distributed under a BSD-style two-clause licence/copyright notice.

The upstream AprilTag source is **not redistributed in this handoff**. `native/apriltag_jni.cpp` is MethodMesh integration code that expects a separately obtained, reviewed and pinned upstream source tree. When integrating/bundling AprilTag, retain the upstream copyright and licence notices in source and binary distributions as required by that licence.

Default/recommended family in this module: `tagStandard41h12`.

## Android / CameraX

The module uses Android platform Camera2 metadata and MethodMesh's existing AndroidX CameraX dependencies. No additional online service is used.
