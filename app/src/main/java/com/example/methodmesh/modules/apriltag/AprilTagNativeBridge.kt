package com.example.methodmesh.modules.apriltag

import com.example.methodmesh.platform.fiducial.AprilTagDetector as PlatformAprilTagDetector
import com.example.methodmesh.platform.fiducial.AprilTagFamily as PlatformAprilTagFamily
import com.example.methodmesh.platform.fiducial.AprilTagPoseRequest
import java.nio.ByteBuffer

/**
 * Module-side adapter onto MethodMesh's generic AprilTag platform facility.
 *
 * This object contains no JNI declarations and does not load a module-specific
 * native symbol. The dependency direction is deliberately one-way:
 *
 *     modules.apriltag -> platform.fiducial -> libmethodmesh_apriltag
 *
 * The shared platform knows nothing about this module or its capability IDs.
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

    val isAvailable: Boolean
        get() = PlatformAprilTagDetector.isAvailable

    val backendName: String = "MethodMesh platform AprilTag 3"

    data class DetectorConfig(
        val family: String = AprilTagContractMetadata.DEFAULT_FAMILY,
        val threads: Int = 2,
        val quadDecimate: Double = 1.0,
        val refineEdges: Boolean = true,
        val tagSizeMeters: Double = 0.0,
        val distanceScale: Double = 1.0
    )

    fun create(config: DetectorConfig): Result<DetectorSession> {
        if (config.family !in supportedFamilies) {
            return Result.failure(IllegalArgumentException("Unsupported AprilTag family: ${config.family}"))
        }
        val family = runCatching { PlatformAprilTagFamily.valueOf(config.family) }
            .getOrElse { return Result.failure(IllegalArgumentException("Unsupported AprilTag family: ${config.family}")) }
        return PlatformAprilTagDetector.create(
            family = family,
            decimate = config.quadDecimate.coerceIn(1.0, 4.0),
            threads = config.threads.coerceIn(1, 8),
            refineEdges = config.refineEdges
        ).map { DetectorSession(it, config) }
    }

    class DetectorSession internal constructor(
        private val detector: PlatformAprilTagDetector,
        private val config: DetectorConfig
    ) : AutoCloseable {
        private var directBuffer: ByteBuffer = ByteBuffer.allocateDirect(0)

        fun detect(
            gray: ByteArray,
            width: Int,
            height: Int,
            intrinsics: CameraIntrinsics?
        ): Result<List<AprilTagDetection>> = runCatching {
            require(width > 0 && height > 0 && gray.size >= width * height) { "Invalid grayscale frame" }
            if (directBuffer.capacity() < gray.size) directBuffer = ByteBuffer.allocateDirect(gray.size)
            directBuffer.clear()
            directBuffer.put(gray)
            directBuffer.flip()

            val poseRequest = intrinsics
                ?.takeIf { it.usableForPose && config.tagSizeMeters > 0.0 }
                ?.let {
                    AprilTagPoseRequest(
                        tagSizeMeters = config.tagSizeMeters,
                        fx = it.fx,
                        fy = it.fy,
                        cx = it.cx,
                        cy = it.cy
                    )
                }

            detector.detect(
                y = directBuffer,
                width = width,
                height = height,
                rowStride = width,
                pixelStride = 1,
                rotationDegrees = 0,
                poseRequest = poseRequest
            ).getOrThrow().detections.map { detection ->
                AprilTagDetection(
                    id = detection.id,
                    family = detection.family.name,
                    hamming = detection.hamming,
                    decisionMargin = detection.decisionMargin,
                    center = PixelPoint(detection.centre.x, detection.centre.y),
                    corners = detection.corners.map { PixelPoint(it.x, it.y) },
                    pose = detection.pose?.let { pose ->
                        val scale = config.distanceScale.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
                        Pose3(
                            translation = Vec3(
                                pose.translation.x * scale,
                                pose.translation.y * scale,
                                pose.translation.z * scale
                            ),
                            rotation = pose.rotation.copyOf(),
                            error = pose.error
                        )
                    }
                )
            }
        }

        override fun close() = detector.close()
    }
}
