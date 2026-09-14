package com.example.methodmesh.platform.fiducial

import androidx.annotation.Keep
import java.nio.ByteBuffer

/** Families provided by the pinned upstream AprilTag 3 distribution. */
enum class AprilTagFamily {
    tag16h5, tag25h9, tag36h10, tag36h11, tagCircle21h7, tagCircle49h12,
    tagCustom48h12, tagStandard41h12, tagStandard52h13
}

data class AprilTagPoint(val x: Double, val y: Double)
data class AprilTagVector3(val x: Double, val y: Double, val z: Double)

/** Optional metric pose returned by the generic detector when a tag size and
 * calibrated camera intrinsics are supplied. Rotation is row-major 3x3 and
 * maps tag-frame axes into camera-frame axes. */
data class AprilTagPose(
    val translation: AprilTagVector3,
    val rotation: DoubleArray,
    val error: Double?
)

data class AprilTagPoseRequest(
    val tagSizeMeters: Double,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double
) {
    internal fun requireValid() {
        require(tagSizeMeters.isFinite() && tagSizeMeters > 0.0) { "Tag size must be positive" }
        require(fx.isFinite() && fy.isFinite() && fx > 0.0 && fy > 0.0) { "Focal lengths must be positive" }
        require(cx.isFinite() && cy.isFinite()) { "Principal point must be finite" }
    }
}

data class AprilTagDetection(
    val id: Int,
    val family: AprilTagFamily,
    /** Upstream tag-relative order (-1,+1), (+1,+1), (+1,-1), (-1,-1).
     * Never sort these by screen position: order carries tag orientation. */
    val corners: List<AprilTagPoint>,
    val centre: AprilTagPoint,
    val hamming: Int,
    val decisionMargin: Double,
    val pose: AprilTagPose? = null
)
data class AprilTagFrame(val width: Int, val height: Int, val detections: List<AprilTagDetection>)

/** Synchronous worker-thread API. One instance serializes detect/close; use {} closes it.
 * Reads a direct grayscale/Y buffer from its current position without changing it.
 * With pixelStride=1 there is no full-frame JNI copy. The caller retains the buffer
 * until detect returns. No smoothing is applied to the borrowed camera buffer.
 *
 * Pose estimation is optional and generic: callers may supply physical tag edge
 * length plus camera intrinsics. No MethodMesh capability or experiment semantics
 * exist below this boundary.
 */
@Keep
class AprilTagDetector private constructor(val family: AprilTagFamily, private var handle: Long) : AutoCloseable {
    companion object {
        private val loaded: Result<Unit> by lazy {
            try { System.loadLibrary("methodmesh_apriltag"); Result.success(Unit) }
            catch (e: LinkageError) { Result.failure(e) }
            catch (e: SecurityException) { Result.failure(e) }
        }

        val isAvailable: Boolean
            get() = loaded.isSuccess

        fun backendError(): Throwable? = loaded.exceptionOrNull()

        /** Creation/load/configuration failures are returned to the caller. */
        fun create(
            family: AprilTagFamily = AprilTagFamily.tagStandard41h12,
            decimate: Double = 2.0,
            threads: Int = 1,
            refineEdges: Boolean = true
        ): Result<AprilTagDetector> = loaded.fold(onSuccess = {
            runCatching {
                require(decimate.isFinite() && decimate in 1.0..8.0) { "Decimation must be 1–8" }
                require(threads in 1..8) { "Threads must be 1–8" }
                val nativeHandle = nativeCreate(family.name, decimate, threads, refineEdges)
                check(nativeHandle != 0L) { "AprilTag detector could not be created" }
                AprilTagDetector(family, nativeHandle)
            }
        }, onFailure = { Result.failure(it) })

        @JvmStatic
        private external fun nativeCreate(family: String, decimate: Double, threads: Int, refineEdges: Boolean): Long

        private const val RECORD_SIZE = 27
    }

    @Synchronized
    fun detect(
        y: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int = width,
        pixelStride: Int = 1,
        rotationDegrees: Int = 0,
        poseRequest: AprilTagPoseRequest? = null
    ): Result<AprilTagFrame> = runCatching {
        check(handle != 0L) { "Detector is closed" }
        require(y.isDirect) { "A direct grayscale buffer is required" }
        require(width in 8..8192 && height in 8..8192 && width.toLong() * height <= 16_777_216) { "Invalid image dimensions" }
        require(pixelStride in 1..4 && rowStride.toLong() >= (width - 1L) * pixelStride + 1) { "Invalid image stride" }
        require((height - 1L) * rowStride + (width - 1L) * pixelStride + 1 <= y.remaining()) { "Truncated image buffer" }
        require(rotationDegrees in listOf(0, 90, 180, 270)) { "Rotation must be 0, 90, 180 or 270" }
        poseRequest?.requireValid()

        val raw = nativeDetect(
            handle,
            y.slice(),
            width,
            height,
            rowStride,
            pixelStride,
            poseRequest?.tagSizeMeters ?: 0.0,
            poseRequest?.fx ?: 0.0,
            poseRequest?.fy ?: 0.0,
            poseRequest?.cx ?: 0.0,
            poseRequest?.cy ?: 0.0
        )
        check(raw.size % RECORD_SIZE == 0) { "Malformed native detection" }

        fun point(x: Double, yValue: Double): AprilTagPoint = when (rotationDegrees) {
            90 -> AprilTagPoint(height - 1.0 - yValue, x)
            180 -> AprilTagPoint(width - 1.0 - x, height - 1.0 - yValue)
            270 -> AprilTagPoint(yValue, width - 1.0 - x)
            else -> AprilTagPoint(x, yValue)
        }

        val detections = raw.indices.step(RECORD_SIZE).map { i ->
            val pose = if (raw[i + 13] > 0.5) {
                val rotation = DoubleArray(9) { k -> raw[i + 18 + k] }
                val tx = raw[i + 15]
                val ty = raw[i + 16]
                val tz = raw[i + 17]
                if (rotation.all { it.isFinite() } && tx.isFinite() && ty.isFinite() && tz.isFinite()) {
                    AprilTagPose(
                        translation = AprilTagVector3(tx, ty, tz),
                        rotation = rotation,
                        error = raw[i + 14].takeIf(Double::isFinite)
                    )
                } else null
            } else null

            AprilTagDetection(
                id = raw[i].toInt(),
                family = family,
                corners = (0..3).map { k -> point(raw[i + 5 + k * 2], raw[i + 6 + k * 2]) },
                centre = point(raw[i + 3], raw[i + 4]),
                hamming = raw[i + 1].toInt(),
                decisionMargin = raw[i + 2],
                pose = pose
            )
        }
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        AprilTagFrame(if (swapped) height else width, if (swapped) width else height, detections)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0
        }
    }

    private external fun nativeDetect(
        handle: Long,
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        tagSizeMeters: Double,
        fx: Double,
        fy: Double,
        cx: Double,
        cy: Double
    ): DoubleArray

    private external fun nativeDestroy(handle: Long)

}
