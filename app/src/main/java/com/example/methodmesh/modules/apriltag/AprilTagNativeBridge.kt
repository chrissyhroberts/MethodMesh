package com.example.methodmesh.modules.apriltag

import org.json.JSONObject

/**
 * Narrow module-owned JNI boundary around AprilRobotics/AprilTag 3.
 *
 * The Kotlin module compiles without the native library. At runtime the UI fails
 * closed with a clear backend-unavailable state until `libmethodmesh_apriltag.so`
 * is integrated. See native/README.md. No detector result is fabricated.
 */
object AprilTagNativeBridge {
    private val supportedFamilies = setOf(
        "tagStandard41h12",
        "tag36h11",
        "tag25h9",
        "tag16h5",
        "tagCircle21h7",
        "tagCircle49h12",
        "tagStandard52h13"
    )

    val isAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("methodmesh_apriltag") }.isSuccess
    }

    data class DetectorConfig(
        val family: String = AprilTagContractMetadata.DEFAULT_FAMILY,
        val threads: Int = 2,
        val quadDecimate: Double = 1.0,
        val refineEdges: Boolean = true,
        val tagSizeMeters: Double = 0.0
    )

    fun detect(
        gray: ByteArray,
        width: Int,
        height: Int,
        config: DetectorConfig,
        intrinsics: CameraIntrinsics?
    ): Result<List<AprilTagDetection>> {
        if (config.family !in supportedFamilies) return Result.failure(
            IllegalArgumentException("Unsupported AprilTag family: ${config.family}")
        )
        if (!isAvailable) return Result.failure(
            IllegalStateException("AprilTag native detector is not installed. See apriltag/native/README.md.")
        )
        return runCatching {
            val usable = intrinsics?.takeIf { it.usableForPose && config.tagSizeMeters > 0.0 }
            val text = nativeDetect(
                gray = gray,
                width = width,
                height = height,
                family = config.family,
                threads = config.threads.coerceIn(1, 8),
                quadDecimate = config.quadDecimate.coerceIn(1.0, 4.0),
                refineEdges = config.refineEdges,
                tagSizeMeters = if (usable == null) 0.0 else config.tagSizeMeters,
                fx = usable?.fx ?: 0.0,
                fy = usable?.fy ?: 0.0,
                cx = usable?.cx ?: 0.0,
                cy = usable?.cy ?: 0.0
            )
            val json = JSONObject(text)
            val arr = json.getJSONArray("detections")
            (0 until arr.length()).map { index -> AprilTagDetection.fromJson(arr.getJSONObject(index)) }
        }
    }

    @JvmStatic
    private external fun nativeDetect(
        gray: ByteArray,
        width: Int,
        height: Int,
        family: String,
        threads: Int,
        quadDecimate: Double,
        refineEdges: Boolean,
        tagSizeMeters: Double,
        fx: Double,
        fy: Double,
        cx: Double,
        cy: Double
    ): String
}
