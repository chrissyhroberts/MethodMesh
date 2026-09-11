# AprilTag native backend integration

The Kotlin module deliberately compiles without an AprilTag binary and **fails closed at runtime** if `libmethodmesh_apriltag.so` is missing. The Master Book requires a module handoff to stay inside its own module folder, so this handoff does not silently patch the app's root Gradle/CMake configuration.

## Required integration

1. Obtain the official `AprilRobotics/apriltag` source at a reviewed/pinned revision.
2. Keep the upstream licence/copyright notices.
3. Place or reference that source from a CMake target. `CMakeLists.txt.example` is a starting point; confirm its source list against the pinned upstream revision because upstream file lists can change.
4. Configure the Android app's existing `android { externalNativeBuild { cmake { ... } } }` block to build a shared library named `methodmesh_apriltag`.
5. Include `apriltag_jni.cpp` in that target.
6. Build for the app's supported ABIs and verify `System.loadLibrary("methodmesh_apriltag")` succeeds on-device.
7. Run the validation experiments in `docs/VALIDATION.md` before changing the module maturity tag.

No OpenCV dependency is required. The bridge calls AprilTag 3's detector and `estimate_tag_pose()` directly.

## JNI contract

The native function accepts an upright 8-bit grayscale image, detector settings, optional known tag size and optional camera intrinsics. It returns JSON containing tag IDs, image-space corners/centres, hamming/decision-margin evidence and, when metric inputs are available, translation/rotation plus AprilTag's pose error.

The Kotlin layer never substitutes a synthetic detection when the native backend is absent.
