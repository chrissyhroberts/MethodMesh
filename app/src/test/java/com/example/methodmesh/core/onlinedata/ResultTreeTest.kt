package com.example.methodmesh.core.onlinedata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTreeTest {
    private val sample = ResultTree.objectNode(
        "weather" to ResultTree.objectNode(
            "data" to ResultTree.objectNode(
                "current" to ResultTree.objectNode(
                    "temperature_2m" to ResultTree.number(18.4),
                    "relative_humidity_2m" to ResultTree.number(71),
                    "condition" to ResultTree.string("cloudy")
                ),
                "hourly" to ResultTree.arrayNode(
                    ResultTree.objectNode("temperature_2m" to ResultTree.number(17.9)),
                    ResultTree.objectNode("temperature_2m" to ResultTree.number(18.1))
                ),
                "nullable" to ResultTree.NullNode
            ),
            "meta" to ResultTree.objectNode(
                "from_cache" to ResultTree.bool(false)
            )
        )
    )

    @Test
    fun resolvesScalarLeaf() {
        val result = ResultPath.parse("\${weather.data.current.temperature_2m}").resolve(sample)

        assertTrue(result is ResultLookup.Value)
        assertEquals(18.4, ((result as ResultLookup.Value).value as ResultTree.NumberNode).value, 0.0)
    }

    @Test
    fun resolvesObjectSubtree() {
        val result = ResultPath.parse("weather.data.current").resolve(sample)

        assertTrue(result is ResultLookup.Value)
        assertTrue((result as ResultLookup.Value).value is ResultTree.ObjectNode)
    }

    @Test
    fun resolvesArrayElement() {
        val result = ResultPath.parse("weather.data.hourly[1].temperature_2m").resolve(sample)

        assertTrue(result is ResultLookup.Value)
        assertEquals(18.1, ((result as ResultLookup.Value).value as ResultTree.NumberNode).value, 0.0)
    }

    @Test
    fun distinguishesNullFromMissing() {
        val nullResult = ResultPath.parse("weather.data.nullable").resolve(sample)
        val missingResult = ResultPath.parse("weather.data.not_here").resolve(sample)

        assertTrue(nullResult is ResultLookup.NullValue)
        assertTrue(missingResult is ResultLookup.Missing)
    }

    @Test
    fun distinguishesWrongTypeFromMissing() {
        val result = ResultPath.parse("weather.data.current.condition.language").resolve(sample)

        assertTrue(result is ResultLookup.TypeError)
    }

    @Test
    fun rejectsMalformedArrayIndexes() {
        val result = runCatching { ResultPath.parse("weather.data.hourly[-1]") }

        assertTrue(result.isFailure)
    }
}

