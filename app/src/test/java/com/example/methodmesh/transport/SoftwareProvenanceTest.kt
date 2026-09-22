package com.example.methodmesh.transport

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SoftwareProvenanceTest {
    private fun identity(version: String) = ExecutionCapabilityIdentity(
        "test.capture", "Capture", version, "development",
        ExecutionModuleIdentity("test", "Test", version, "experimental")
    )
    private fun request() = ExecutionRequest(
        action = "test.capture", method = ArchitectureRef(ArchitectureId("test.capture"), "Method")
    )
    @After fun clearRegistry() { ExecutionSoftwareMetadataRegistry.install(emptyList()) }
    @Test fun `later upgrades cannot rewrite historical software identity`() {
        ExecutionSoftwareMetadataRegistry.install(listOf(identity("1.0.0")))
        val result = As100ExecutionEngine.complete(request(), TransformationStatus.Succeeded)
        ExecutionSoftwareMetadataRegistry.install(listOf(identity("2.0.0")))
        val envelope = OutputFormatter.fullEnvelope(result)
        assertEquals("1.0.0", (envelope["module"] as Map<*, *>)["version"])
        assertEquals("development", (envelope["capability"] as Map<*, *>)["maturity"])
        assertTrue(envelope.containsKey("time_assurance"))
        val full = OutputFormatter.fields(result, payloadMode = OutputFormatter.PayloadMode.FULL)["methodmesh_full_json"] as String
        assertTrue(full.contains("1.0.0")); assertFalse(full.contains("2.0.0"))
    }
    @Test fun `legacy result reports unknown provenance rather than installed version`() {
        ExecutionSoftwareMetadataRegistry.install(listOf(identity("2.0.0")))
        val envelope = OutputFormatter.fullEnvelope(ExecutionResult(request(), TransformationStatus.Succeeded))
        assertEquals("unknown", (envelope["capability"] as Map<*, *>)["version"])
        assertEquals("not_captured", (envelope["capability"] as Map<*, *>)["provenance_status"])
    }
    @Test fun `snapshot deduplicates capabilities preserving execution order`() {
        val other = identity("1.0.0").copy(id = "test.other")
        ExecutionSoftwareMetadataRegistry.install(listOf(identity("1.0.0"), other))
        assertEquals(listOf("test.other", "test.capture"), ExecutionSoftwareMetadataRegistry
            .snapshot(listOf("test.other", "test.capture", "test.other")).map { it.id })
    }
    @Test(expected = IllegalArgumentException::class) fun `incomplete metadata is rejected`() {
        ExecutionSoftwareMetadataRegistry.install(listOf(identity("")))
    }
}
