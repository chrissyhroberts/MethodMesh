package com.example.methodmesh.modules.geocaching

import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus
import org.junit.Assert.*
import org.junit.Test

class GeocachingCommittedRecordTest {
    private fun result() = ExecutionResult(ExecutionRequest(action = "geocache.project_waypoint", method = ArchitectureRef(ArchitectureId("geocache.project_waypoint"), "Method")), TransformationStatus.Succeeded)
    @Test fun sameSnapshotRetainsOneExactRecordWithoutFactoryReplay() {
        val slot = GeocachingCommittedRecord(); val original = result(); var creates = 0
        val factory = { creates++; original }
        repeat(3) { assertSame(original, slot.resolve(true, mapOf("value" to "frozen"), it == 0, factory)) }
        assertEquals(1, creates)
    }
    @Test fun explicitChangedSnapshotCreatesNewRecordAndClearReleasesOldOne() {
        val slot = GeocachingCommittedRecord(); val original = result(); val next = result()
        assertSame(original, slot.resolve(true, "a", true) { original })
        assertSame(next, slot.resolve(true, "b", false) { next })
        assertNull(slot.resolve(false, "b", false) { error("Replayed") })
        assertNull(slot.result)
    }
    @Test fun missingPreviouslyCommittedRecordNeverReconstructsWithoutExplicitChoice() {
        val slot = GeocachingCommittedRecord(); var creates = 0
        assertNull(slot.resolve(true, "saved", false) { creates++; result() })
        assertEquals(0, creates)
        assertNotNull(slot.resolve(true, "saved", true) { creates++; result() })
        assertEquals(1, creates)
    }
}
