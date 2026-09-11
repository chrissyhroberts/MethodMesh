package com.example.methodmesh.modules.apriltag

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AprilTagModuleBridgeTest {
    @Test fun nativeBridgeDetectsAndReturnsPose() {
        val data = InstrumentationRegistry.getInstrumentation().context.assets.open("tagStandard41h12-id23.gray").use { it.readBytes() }
        assertTrue(AprilTagNativeBridge.isAvailable)
        repeat(10) {
            val tags = AprilTagNativeBridge.detect(data,400,320,
                AprilTagNativeBridge.DetectorConfig(tagSizeMeters=0.1),
                CameraIntrinsics(500.0,500.0,200.0,160.0,400,320,"test")).getOrThrow()
            val tag = tags.single()
            assertEquals(23,tag.id)
            assertEquals(4,tag.corners.size)
            val pose = requireNotNull(tag.pose)
            assertTrue(pose.translation.z.isFinite() && pose.translation.z > 0)
            assertEquals(9,pose.rotation.size)
            assertTrue(pose.rotation.all { it.isFinite() })
        }
        assertTrue(AprilTagNativeBridge.detect(ByteArray(1),400,320,AprilTagNativeBridge.DetectorConfig(),null).isFailure)
        assertTrue(AprilTagNativeBridge.detect(data,400,320,AprilTagNativeBridge.DetectorConfig(family="invalid"),null).isFailure)
    }
}
