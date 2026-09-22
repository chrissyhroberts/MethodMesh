package com.example.methodmesh.modules

import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.modules.nfc.As100NfcReadMethod
import org.junit.Assert.*
import org.junit.Test

class ModuleMetadataTest {
    private fun method(parameters: Map<String, String>) = object : As100Method by As100NfcReadMethod {
        override val descriptor = As100NfcReadMethod.descriptor.copy(parameters = parameters)
    }
    @Test fun `legacy aliases whitespace and case normalize`() {
        assertEquals(MaturityStatus.Production, MaturityStatus.parse(" PROD "))
        assertEquals(MaturityStatus.Development, MaturityStatus.parse("Dev"))
        assertEquals(MaturityStatus.Experimental, MaturityStatus.parse("experiment"))
        assertNull(MaturityStatus.parse("unknown"))
    }
    @Test fun `unknown maturity falls back without crashing`() {
        assertEquals(MaturityStatus.Production, MethodMeshMetadataResolver.capabilityMaturity(
            method(mapOf("maturity" to "beta", "status" to "prod")), null))
        assertEquals(MaturityStatus.Development, MethodMeshMetadataResolver.capabilityMaturity(
            method(mapOf("maturity" to "beta", "status" to "future")), null))
    }
    @Test fun `explicit maturity takes precedence over legacy status`() {
        assertEquals(MaturityStatus.Experimental, MethodMeshMetadataResolver.capabilityMaturity(
            method(mapOf("maturity" to "experimental", "status" to "production")), null))
    }
}
