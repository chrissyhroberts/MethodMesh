package com.example.methodmesh.platform.fiducial

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class AprilTagDetectorTest {
    private fun image(stride: Int = 400, pixelStride: Int = 1): ByteBuffer {
        val data = InstrumentationRegistry.getInstrumentation().context.assets.open("tagStandard41h12-id23.gray").use { it.readBytes() }
        return ByteBuffer.allocateDirect((319 * stride) + 399 * pixelStride + 1).apply {
            for (y in 0 until 320) for (x in 0 until 400) put(y * stride + x * pixelStride, data[y * 400 + x])
        }
    }
    @Test fun loadCreateDetectOrderedCornersAndDestroy() {
        repeat(20) {
            val detector = AprilTagDetector.create().getOrThrow()
            detector.use {
                val input = image()
                val before = ByteArray(input.capacity()).also { bytes -> input.duplicate().get(bytes) }
                val tag = it.detect(input, 400, 320).getOrThrow().detections.single()
                val after = ByteArray(input.capacity()).also { bytes -> input.duplicate().get(bytes) }
                assertArrayEquals(before, after)
                assertEquals(AprilTagFamily.tagStandard41h12, tag.family)
                assertEquals(23, tag.id); assertEquals(0, tag.hamming)
                assertTrue(tag.decisionMargin > 20)
                assertEquals(4, tag.corners.size)
                val p = tag.corners
                // Upstream order is counterclockwise in image coordinates.
                val area = p.indices.sumOf { i -> p[i].x * p[(i+1)%4].y - p[(i+1)%4].x * p[i].y }
                assertTrue(area < -1000)
                assertTrue(p.all { it.x in 60.0..300.0 && it.y in 40.0..280.0 })
            }
            detector.close()
            assertTrue(detector.detect(image(), 400, 320).isFailure)
        }
    }
    @Test fun paddingPixelStrideOffsetAndRotation() {
        AprilTagDetector.create().getOrThrow().use { detector ->
            val source = image(832, 2)
            val offset = ByteBuffer.allocateDirect(source.capacity()+17).apply { position(17); put(source); position(17) }
            val plain = detector.detect(offset, 400, 320, 832, 2).getOrThrow().detections.single()
            assertEquals(17, offset.position())
            for (rotation in listOf(90,180,270)) {
                val frame = detector.detect(offset,400,320,832,2,rotation).getOrThrow()
                assertEquals(if(rotation==180)400 else 320,frame.width)
                val tag = frame.detections.single(); assertEquals(23,tag.id)
                plain.corners.zip(tag.corners).forEach { (p,q) ->
                    val expected = when(rotation) {90->AprilTagPoint(319-p.y,p.x);180->AprilTagPoint(399-p.x,319-p.y);else->AprilTagPoint(p.y,399-p.x)}
                    assertEquals(expected.x,q.x,0.001);assertEquals(expected.y,q.y,0.001)
                }
            }
        }
    }
    @Test fun familiesAndMalformedFrames() {
        AprilTagFamily.entries.forEach { AprilTagDetector.create(it).getOrThrow().close() }
        assertTrue(AprilTagDetector.create(decimate=Double.NaN).isFailure)
        AprilTagDetector.create().getOrThrow().use {
            assertTrue(it.detect(ByteBuffer.allocate(128000),400,320).isFailure)
            assertTrue(it.detect(ByteBuffer.allocateDirect(10),400,320).isFailure)
            assertTrue(it.detect(image(),400,320,399).isFailure)
            assertTrue(it.detect(image(),400,320,rotationDegrees=45).isFailure)
            val blank = ByteBuffer.allocateDirect(128000)
            assertTrue(it.detect(blank,400,320).getOrThrow().detections.isEmpty())
        }
    }
}
