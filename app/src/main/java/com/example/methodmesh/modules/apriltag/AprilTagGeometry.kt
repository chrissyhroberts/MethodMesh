package com.example.methodmesh.modules.apriltag

import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object AprilTagGeometry {
    fun relativePose(referenceInCamera: Pose3, targetInCamera: Pose3): Pose3 {
        require(referenceInCamera.rotation.size == 9 && targetInCamera.rotation.size == 9)
        val rrT = transpose(referenceInCamera.rotation)
        val r = matrixMultiply(rrT, targetInCamera.rotation)
        val delta = doubleArrayOf(
            targetInCamera.translation.x - referenceInCamera.translation.x,
            targetInCamera.translation.y - referenceInCamera.translation.y,
            targetInCamera.translation.z - referenceInCamera.translation.z
        )
        val t = matrixVectorMultiply(rrT, delta)
        return Pose3(Vec3(t[0], t[1], t[2]), r, null)
    }

    /** ZYX yaw/pitch/roll decomposition, returned in degrees. */
    fun eulerDegrees(rotation: DoubleArray): Triple<Double, Double, Double> {
        require(rotation.size == 9)
        val r00 = rotation[0]
        val r10 = rotation[3]
        val r20 = rotation[6]
        val r21 = rotation[7]
        val r22 = rotation[8]
        val sy = sqrt(r00 * r00 + r10 * r10)
        val singular = sy < 1e-7
        val yaw: Double
        val pitch: Double
        val roll: Double
        if (!singular) {
            yaw = atan2(r10, r00)
            pitch = atan2(-r20, sy)
            roll = atan2(r21, r22)
        } else {
            yaw = atan2(-rotation[1], rotation[4])
            pitch = atan2(-r20, sy)
            roll = 0.0
        }
        return Triple(radToDeg(yaw), radToDeg(pitch), radToDeg(roll))
    }

    fun distanceFromCamera(pose: Pose3): Double = sqrt(
        pose.translation.x * pose.translation.x +
            pose.translation.y * pose.translation.y +
            pose.translation.z * pose.translation.z
    )

    fun calibrateFocalPx(meanEdgePx: Double, knownDistanceMm: Double, tagSizeMm: Double): Double =
        meanEdgePx * knownDistanceMm / tagSizeMm

    fun coefficientOfVariation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        if (mean == 0.0) return Double.NaN
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance) / mean
    }

    /**
     * Maps an image point to the physical plane of one square tag.
     * Tag coordinates put the tag centre at (0,0), +x to image corner 1 from 0,
     * +y to corner 3 from 0, with edge length tagSizeMm.
     */
    fun imageToTagPlane(point: PixelPoint, detection: AprilTagDetection, tagSizeMm: Double): PixelPoint? {
        if (detection.corners.size != 4 || tagSizeMm <= 0) return null
        val h = homography(
            src = detection.corners,
            dst = listOf(
                PixelPoint(-tagSizeMm / 2.0, -tagSizeMm / 2.0),
                PixelPoint(tagSizeMm / 2.0, -tagSizeMm / 2.0),
                PixelPoint(tagSizeMm / 2.0, tagSizeMm / 2.0),
                PixelPoint(-tagSizeMm / 2.0, tagSizeMm / 2.0)
            )
        ) ?: return null
        return applyHomography(h, point)
    }

    fun tagToWorld(local: PixelPoint, tagId: Int, layoutJson: String): Pair<PixelPoint, String> {
        if (layoutJson.isBlank()) return local to "tag:$tagId"
        val obj = runCatching { JSONObject(layoutJson) }.getOrNull() ?: return local to "tag:$tagId"
        val tag = obj.optJSONObject(tagId.toString()) ?: return local to "tag:$tagId"
        val originX = tag.optDouble("x_mm", 0.0)
        val originY = tag.optDouble("y_mm", 0.0)
        val yaw = tag.optDouble("yaw_deg", 0.0) * PI / 180.0
        val x = originX + cos(yaw) * local.x - sin(yaw) * local.y
        val y = originY + sin(yaw) * local.x + cos(yaw) * local.y
        return PixelPoint(x, y) to obj.optString("frame", "layout")
    }

    fun worldToTag(world: PixelPoint, tagId: Int, layoutJson: String): PixelPoint? {
        if (layoutJson.isBlank()) return world
        val obj = runCatching { JSONObject(layoutJson) }.getOrNull() ?: return null
        val tag = obj.optJSONObject(tagId.toString()) ?: return null
        val originX = tag.optDouble("x_mm", 0.0)
        val originY = tag.optDouble("y_mm", 0.0)
        val yaw = tag.optDouble("yaw_deg", 0.0) * PI / 180.0
        val dx = world.x - originX
        val dy = world.y - originY
        return PixelPoint(
            cos(yaw) * dx + sin(yaw) * dy,
            -sin(yaw) * dx + cos(yaw) * dy
        )
    }

    fun tagPlaneToImage(local: PixelPoint, detection: AprilTagDetection, tagSizeMm: Double): PixelPoint? {
        if (detection.corners.size != 4 || tagSizeMm <= 0) return null
        val canonical = listOf(
            PixelPoint(-tagSizeMm / 2.0, -tagSizeMm / 2.0),
            PixelPoint(tagSizeMm / 2.0, -tagSizeMm / 2.0),
            PixelPoint(tagSizeMm / 2.0, tagSizeMm / 2.0),
            PixelPoint(-tagSizeMm / 2.0, tagSizeMm / 2.0)
        )
        val h = homography(canonical, detection.corners) ?: return null
        return applyHomography(h, local)
    }

    fun detectionAt(point: PixelPoint, detections: List<AprilTagDetection>): AprilTagDetection? {
        val containing = detections.firstOrNull { it.corners.size == 4 && pointInPolygon(point, it.corners) }
        if (containing != null) return containing
        return detections
            .map { it to hypot(point.x - it.center.x, point.y - it.center.y) }
            .filter { (det, distance) -> distance <= det.meanEdgePx.coerceAtLeast(24.0) }
            .minByOrNull { it.second }
            ?.first
    }

    fun pathLength2d(points: List<PlanarPoint>): Double = points.zipWithNext().sumOf { (a, b) ->
        hypot(a.xMm - b.xMm, a.yMm - b.yMm)
    }

    fun polygonArea(points: List<PlanarPoint>): Double {
        if (points.size < 3) return 0.0
        return abs(points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            a.xMm * b.yMm - b.xMm * a.yMm
        }) / 2.0
    }

    fun polygonPerimeter(points: List<PlanarPoint>): Double {
        if (points.size < 2) return 0.0
        return points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            hypot(a.xMm - b.xMm, a.yMm - b.yMm)
        }
    }

    fun pathLength3d(samples: List<PoseSample>): Double = samples.zipWithNext().sumOf { (a, b) ->
        a.position.distanceTo(b.position)
    }

    fun maxSpeed3d(samples: List<PoseSample>): Double = samples.zipWithNext().mapNotNull { (a, b) ->
        val dt = (b.timeMs - a.timeMs) / 1000.0
        if (dt > 0) a.position.distanceTo(b.position) / dt else null
    }.maxOrNull() ?: 0.0

    private fun pointInPolygon(point: PixelPoint, polygon: List<PixelPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val intersects = ((a.y > point.y) != (b.y > point.y)) &&
                point.x < (b.x - a.x) * (point.y - a.y) / ((b.y - a.y).takeIf { abs(it) > 1e-12 } ?: 1e-12) + a.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun homography(src: List<PixelPoint>, dst: List<PixelPoint>): DoubleArray? {
        if (src.size != 4 || dst.size != 4) return null
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = src[i].x
            val y = src[i].y
            val u = dst[i].x
            val v = dst[i].y
            a[2 * i][0] = x
            a[2 * i][1] = y
            a[2 * i][2] = 1.0
            a[2 * i][6] = -u * x
            a[2 * i][7] = -u * y
            a[2 * i][8] = u
            a[2 * i + 1][3] = x
            a[2 * i + 1][4] = y
            a[2 * i + 1][5] = 1.0
            a[2 * i + 1][6] = -v * x
            a[2 * i + 1][7] = -v * y
            a[2 * i + 1][8] = v
        }
        for (col in 0 until 8) {
            var pivot = col
            for (row in col + 1 until 8) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            if (abs(a[pivot][col]) < 1e-10) return null
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val p = a[col][col]
            for (j in col until 9) a[col][j] /= p
            for (row in 0 until 8) {
                if (row == col) continue
                val f = a[row][col]
                for (j in col until 9) a[row][j] -= f * a[col][j]
            }
        }
        return doubleArrayOf(a[0][8], a[1][8], a[2][8], a[3][8], a[4][8], a[5][8], a[6][8], a[7][8], 1.0)
    }

    private fun applyHomography(h: DoubleArray, p: PixelPoint): PixelPoint? {
        val d = h[6] * p.x + h[7] * p.y + h[8]
        if (abs(d) < 1e-12) return null
        return PixelPoint(
            (h[0] * p.x + h[1] * p.y + h[2]) / d,
            (h[3] * p.x + h[4] * p.y + h[5]) / d
        )
    }

    private fun transpose(m: DoubleArray): DoubleArray = doubleArrayOf(
        m[0], m[3], m[6],
        m[1], m[4], m[7],
        m[2], m[5], m[8]
    )

    private fun matrixMultiply(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(9) { index ->
        val r = index / 3
        val c = index % 3
        (0..2).sumOf { k -> a[r * 3 + k] * b[k * 3 + c] }
    }

    private fun matrixVectorMultiply(a: DoubleArray, v: DoubleArray): DoubleArray = DoubleArray(3) { r ->
        (0..2).sumOf { k -> a[r * 3 + k] * v[k] }
    }

    private fun radToDeg(value: Double): Double = value * 180.0 / PI
}
