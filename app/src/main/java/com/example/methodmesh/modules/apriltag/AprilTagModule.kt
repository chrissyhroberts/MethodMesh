package com.example.methodmesh.modules.apriltag

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object AprilTagModule : MethodMeshModule {
    override val moduleId = "apriltag"
    override val displayName = "AprilTag"
    override val summary = "Use printed AprilTags and the camera for reproducible spatial measurements, planar experiments, and motion tracking."
    override val iconKey = "tool"

    val maturityTag = AprilTagContractMetadata.MATURITY
    val connectivityTag = AprilTagContractMetadata.CONNECTIVITY

    override fun as100Methods() = listOf(
        As100AprilTagDetectMethod,
        As100AprilTagCalibrateFocalMethod,
        As100AprilTagRangePoseMethod,
        As100AprilTagRelativePoseMethod,
        As100AprilTagPlanarMeasureMethod,
        As100AprilTagTrackPoseMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("detect AprilTag", As100AprilTagDetectMethod.id, "Detect and identify AprilTags with image-space geometry"),
        RilBinding("detect april tag", As100AprilTagDetectMethod.id, "Detect and identify AprilTags with image-space geometry"),
        RilBinding("calibrate tag range", As100AprilTagCalibrateFocalMethod.id, "Estimate focal length from a known-size tag at a known distance"),
        RilBinding("measure tag range", As100AprilTagRangePoseMethod.id, "Estimate range and 6-DoF pose of a known-size tag"),
        RilBinding("measure relative tag pose", As100AprilTagRelativePoseMethod.id, "Measure one tagged frame relative to another"),
        RilBinding("measure on tagged plane", As100AprilTagPlanarMeasureMethod.id, "Capture metric points, distances, areas, or trajectories on a tagged plane"),
        RilBinding("track tagged object", As100AprilTagTrackPoseMethod.id, "Record metric movement of a tagged rigid object through time")
    )

    override fun capabilityScreens() = listOf(
        AprilTagDetectCapabilityScreen,
        AprilTagCalibrateCapabilityScreen,
        AprilTagRangePoseCapabilityScreen,
        AprilTagRelativePoseCapabilityScreen,
        AprilTagPlanarMeasureCapabilityScreen,
        AprilTagTrackPoseCapabilityScreen
    )

    private val detectorSettings = listOf(
        MethodSetting.ChoiceSetting(
            AprilTagInputs.TAG_FAMILY,
            "Tag family",
            "tagStandard41h12 is the default general-purpose AprilTag 3 family.",
            "Detector",
            AprilTagContractMetadata.DEFAULT_FAMILY,
            listOf("tagStandard41h12", "tag36h11", "tag25h9", "tag16h5", "tagCircle21h7", "tagCircle49h12", "tagStandard52h13")
        ),
        MethodSetting.IntSetting(AprilTagInputs.TARGET_TAG_ID, "Target tag ID", "-1 leaves selection to the capability: detection captures all visible tags; pose tools use the strongest visible tag unless you tap a target.", "Detector", -1, -1, 100000),
        MethodSetting.IntSetting(AprilTagInputs.THREADS, "Detector threads", "AprilTag detector worker threads.", "Detector", 2, 1, 8),
        MethodSetting.FloatSetting(AprilTagInputs.QUAD_DECIMATE, "Quad decimation", "1 is most detailed and is the robust default for oblique/smaller tags; higher values are faster but reduce detection margin.", "Detector", 1f, 1f, 4f, 0.25f, decimals = 2),
        MethodSetting.BooleanSetting(AprilTagInputs.REFINE_EDGES, "Refine edges", "Refine detected quadrilateral edges before decoding.", "Detector", true)
    )

    private val tagGeometrySettings = listOf(
        MethodSetting.FloatSetting(AprilTagInputs.TAG_SIZE_MM, "Tag detection-edge size", "Physical distance between AprilTag detection corners; not paper outer size.", "Geometry", 100f, 5f, 2000f, 1f, "mm", 2)
    )

    private val intrinsicsSettings = listOf(
        MethodSetting.ChoiceSetting(AprilTagInputs.INTRINSICS_MODE, "Camera model", "Use Android factory metadata when available, or supply a calibrated manual profile.", "Camera model", "auto", listOf("auto", "manual")),
        MethodSetting.FloatSetting(AprilTagInputs.FX_PX, "fx", "Manual horizontal focal length in pixels.", "Manual camera model", 0f, 0f, 100000f, 1f, "px", 3),
        MethodSetting.FloatSetting(AprilTagInputs.FY_PX, "fy", "Manual vertical focal length in pixels.", "Manual camera model", 0f, 0f, 100000f, 1f, "px", 3),
        MethodSetting.FloatSetting(AprilTagInputs.CX_PX, "cx", "Manual principal point x in pixels.", "Manual camera model", 0f, 0f, 100000f, 1f, "px", 3),
        MethodSetting.FloatSetting(AprilTagInputs.CY_PX, "cy", "Manual principal point y in pixels.", "Manual camera model", 0f, 0f, 100000f, 1f, "px", 3),
        MethodSetting.IntSetting(AprilTagInputs.INTRINSICS_WIDTH_PX, "Calibration image width", "Pixel width to which the manual camera model applies.", "Manual camera model", 0, 0, 20000, 1, "px"),
        MethodSetting.IntSetting(AprilTagInputs.INTRINSICS_HEIGHT_PX, "Calibration image height", "Pixel height to which the manual camera model applies.", "Manual camera model", 0, 0, 20000, 1, "px")
    )

    override fun capabilitySettings() = mapOf(
        As100AprilTagDetectMethod.id to detectorSettings,
        As100AprilTagCalibrateFocalMethod.id to detectorSettings + tagGeometrySettings + listOf(
            MethodSetting.FloatSetting(AprilTagInputs.KNOWN_DISTANCE_MM, "Known camera-to-tag distance", "Measure from the camera optical centre as closely as practical.", "Calibration", 1000f, 50f, 20000f, 1f, "mm", 1),
            MethodSetting.IntSetting(AprilTagInputs.CALIBRATION_SAMPLES, "Samples", "Number of stable front-facing observations to average.", "Calibration", 15, 3, 100)
        ),
        As100AprilTagRangePoseMethod.id to detectorSettings + tagGeometrySettings + intrinsicsSettings,
        As100AprilTagRelativePoseMethod.id to detectorSettings.filterNot { it.id == AprilTagInputs.TARGET_TAG_ID } + tagGeometrySettings + intrinsicsSettings + listOf(
            MethodSetting.IntSetting(AprilTagInputs.REFERENCE_TAG_ID, "Reference tag ID", "Fixed tag defining the coordinate frame.", "Tags", 0, 0, 100000),
            MethodSetting.IntSetting(AprilTagInputs.TARGET_TAG_ID, "Target tag ID", "Tag whose pose is measured relative to the reference.", "Tags", 1, 0, 100000)
        ),
        As100AprilTagPlanarMeasureMethod.id to detectorSettings + tagGeometrySettings + listOf(
            MethodSetting.ChoiceSetting(AprilTagInputs.MEASUREMENT_MODE, "Measurement", "Choose what tapping points on the tagged plane means.", "Experiment", "point", listOf("point", "distance", "area", "trajectory")),
            MethodSetting.TextSetting(AprilTagInputs.ANCHOR_LAYOUT_JSON, "Optional planar tag layout", "JSON mapping tag IDs to known x_mm, y_mm and yaw_deg in a shared planar frame.", "Experiment", "")
        ),
        As100AprilTagTrackPoseMethod.id to detectorSettings.filterNot { it.id == AprilTagInputs.TARGET_TAG_ID } + tagGeometrySettings + intrinsicsSettings + listOf(
            MethodSetting.IntSetting(AprilTagInputs.MOVING_TAG_ID, "Moving tag ID", "Tag attached to the rigid object being tracked.", "Tracking", 1, 0, 100000),
            MethodSetting.IntSetting(AprilTagInputs.REFERENCE_TAG_ID, "Fixed reference tag ID", "Use -1 for a fixed-camera coordinate frame; otherwise keep this tag visible.", "Tracking", -1, -1, 100000),
            MethodSetting.IntSetting(AprilTagInputs.SAMPLE_INTERVAL_MS, "Sample interval", "Minimum time between recorded pose samples.", "Tracking", 200, 50, 10000, 50, "ms"),
            MethodSetting.IntSetting(AprilTagInputs.MAX_SAMPLES, "Maximum samples", "Stops recording before an unbounded in-memory trajectory develops.", "Tracking", 3000, 2, 100000)
        )
    )

    override fun examples() = listOf(
        ModuleExample("Range from a 100 mm tag", "WHAT; measure tag range; RESULT; return apriltag_distance_m, apriltag_pose_error; format json"),
        ModuleExample("Snail arena point", "WHAT; measure on tagged plane; RESULT; return apriltag_point_x_mm, apriltag_point_y_mm; format json", "Use a fixed tag or planar layout and tap the animal position."),
        ModuleExample("Track a tagged object", "WHAT; track tagged object; RESULT; return apriltag_path_length_m, apriltag_samples_json; format json")
    )
}
