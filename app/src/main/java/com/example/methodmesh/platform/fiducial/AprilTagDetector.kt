package com.example.methodmesh.platform.fiducial

import androidx.annotation.Keep
import java.nio.ByteBuffer

/** Families provided by the pinned upstream AprilTag 3 distribution. */
enum class AprilTagFamily {
    tag16h5, tag25h9, tag36h10, tag36h11, tagCircle21h7, tagCircle49h12,
    tagCustom48h12, tagStandard41h12, tagStandard52h13
}

data class AprilTagPoint(val x: Double, val y: Double)
data class AprilTagDetection(
    val id: Int,
    val family: AprilTagFamily,
    /** Upstream tag-relative order (-1,+1), (+1,+1), (+1,-1), (-1,-1).
     * Never sort these by screen position: order carries tag orientation. */
    val corners: List<AprilTagPoint>,
    val centre: AprilTagPoint,
    val hamming: Int,
    val decisionMargin: Double
)
data class AprilTagFrame(val width: Int, val height: Int, val detections: List<AprilTagDetection>)

/** Synchronous worker-thread API. One instance serializes detect/close; use {} closes it.
 * Reads a direct grayscale/Y buffer from its current position without changing it.
 * With pixelStride=1 there is no full-frame JNI copy. The caller retains the buffer
 * until detect returns. No smoothing is applied to the borrowed camera buffer.
 */
@Keep
class AprilTagDetector private constructor(val family: AprilTagFamily, private var handle: Long) : AutoCloseable {
    companion object {
        private val loaded: Result<Unit> by lazy {
            try { System.loadLibrary("methodmesh_apriltag"); Result.success(Unit) }
            catch (e: LinkageError) { Result.failure(e) }
            catch (e: SecurityException) { Result.failure(e) }
        }
        /** Creation/load/configuration failures are returned to the caller. */
        fun create(family: AprilTagFamily = AprilTagFamily.tagStandard41h12, decimate: Double = 2.0): Result<AprilTagDetector> =
            loaded.fold(onSuccess = {
                runCatching {
                    require(decimate.isFinite() && decimate in 1.0..8.0) { "Decimation must be 1–8" }
                    AprilTagDetector(family, nativeCreate(family.name, decimate))
                }
            }, onFailure = { Result.failure(it) })
        @JvmStatic private external fun nativeCreate(family: String, decimate: Double): Long
    }

    @Synchronized
    fun detect(y: ByteBuffer, width: Int, height: Int, rowStride: Int = width,
               pixelStride: Int = 1, rotationDegrees: Int = 0): Result<AprilTagFrame> = runCatching {
        check(handle != 0L) { "Detector is closed" }
        require(y.isDirect) { "A direct grayscale buffer is required" }
        require(width in 8..8192 && height in 8..8192 && width.toLong() * height <= 16_777_216) { "Invalid image dimensions" }
        require(pixelStride in 1..4 && rowStride.toLong() >= (width - 1L) * pixelStride + 1) { "Invalid image stride" }
        require((height - 1L) * rowStride + (width - 1L) * pixelStride + 1 <= y.remaining()) { "Truncated image buffer" }
        require(rotationDegrees in listOf(0, 90, 180, 270)) { "Rotation must be 0, 90, 180 or 270" }
        val raw = nativeDetect(handle, y.slice(), width, height, rowStride, pixelStride)
        check(raw.size % 13 == 0) { "Malformed native detection" }
        fun point(x: Double, yValue: Double): AprilTagPoint = when (rotationDegrees) {
            90 -> AprilTagPoint(height - 1.0 - yValue, x)
            180 -> AprilTagPoint(width - 1.0 - x, height - 1.0 - yValue)
            270 -> AprilTagPoint(yValue, width - 1.0 - x)
            else -> AprilTagPoint(x, yValue)
        }
        val detections = raw.indices.step(13).map { i ->
            AprilTagDetection(raw[i].toInt(), family,
                (0..3).map { k -> point(raw[i + 5 + k * 2], raw[i + 6 + k * 2]) },
                point(raw[i + 3], raw[i + 4]), raw[i + 1].toInt(), raw[i + 2])
        }
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        AprilTagFrame(if (swapped) height else width, if (swapped) width else height, detections)
    }

    @Synchronized override fun close() {
        if (handle != 0L) { nativeDestroy(handle); handle = 0 }
    }
    private external fun nativeDetect(handle: Long, buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): DoubleArray
    private external fun nativeDestroy(handle: Long)
}
