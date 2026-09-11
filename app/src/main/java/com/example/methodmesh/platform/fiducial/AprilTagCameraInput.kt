package com.example.methodmesh.platform.fiducial

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

/** For use in LiveCameraPreview.onAnalysisFrame. The camera surface owns/ closes
 * ImageProxy. Call synchronously before returning the callback. Output coordinates
 * cover the complete image rotated clockwise by imageInfo.rotationDegrees; any
 * PreviewView crop/mirroring or module ROI transform remains with the consumer.
 */
fun AprilTagDetector.detect(image: ImageProxy): Result<AprilTagFrame> {
    if (image.format != ImageFormat.YUV_420_888 || image.planes.isEmpty())
        return Result.failure(IllegalArgumentException("A YUV camera frame is required"))
    val y = image.planes[0]
    return detect(y.buffer, image.width, image.height, y.rowStride, y.pixelStride,
        image.imageInfo.rotationDegrees)
}
