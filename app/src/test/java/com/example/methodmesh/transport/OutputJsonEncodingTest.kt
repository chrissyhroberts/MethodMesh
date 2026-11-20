package com.example.methodmesh.transport

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OutputJsonEncodingTest {
    private val result = ExecutionResult(
        ExecutionRequest(action = "test", method = ArchitectureRef(ArchitectureId("test"), "Method")),
        TransformationStatus.Succeeded
    )

    @Test fun `all control characters round trip in keys and nested values`() {
        val controls = (0..31).map(Int::toChar).joinToString("")
        val text = "Quoted \"text\" \\ Unicode £ 🧭 $controls"
        val fields = mapOf(text to text, "nested" to mapOf("items" to listOf(text)))
        val encoded = OutputFormatter.projectFields(fields, OutputFormatter.PayloadMode.FULL)["methodmesh_full_json"] as String
        assertTrue("JSON must not contain literal control bytes", encoded.none { it.code < 32 && it != '\n' })
        val decoded = JSONObject(encoded)
        assertEquals(text, decoded.getString(text))
        assertEquals(text, decoded.getJSONObject("nested").getJSONArray("items").getString(0))
    }

    @Test fun `canonical full envelope encodes caller context safely`() {
        val context = "visit\tname\r\nwith\u0000control"
        val encoded = OutputFormatter.format(result.copy(request = result.request.copy(context = mapOf("visit_id" to context))), ReturnMode.Json)
        assertEquals(context, JSONObject(encoded).getString("visit_id"))
    }

    @Test fun `nonfinite structured numbers are JSON null while finite values keep their type`() {
        val fields = mapOf("numbers" to listOf(Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 2.5, 7))
        val encoded = OutputFormatter.projectFields(fields, OutputFormatter.PayloadMode.FULL)["methodmesh_full_json"] as String
        val numbers = JSONObject(encoded).getJSONArray("numbers")
        (0..2).forEach { assertTrue(numbers.isNull(it)) }
        assertEquals(2.5, numbers.getDouble(3), 0.0)
        assertEquals(7, numbers.getInt(4))
    }
}
