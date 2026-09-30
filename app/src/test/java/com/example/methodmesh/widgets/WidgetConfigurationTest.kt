package com.example.methodmesh.widgets

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigurationTest {
    private fun decode(raw: String): MethodMeshWidgetConfig {
        val decoder = MethodMeshWidgetRepository::class.java.getDeclaredMethod(
            "decode",
            JSONObject::class.java
        )
        decoder.isAccessible = true
        return decoder.invoke(
            MethodMeshWidgetRepository,
            JSONObject(raw)
        ) as MethodMeshWidgetConfig
    }

    @Test
    fun `legacy widget keeps solid appearance`() {
        val config = decode(
            """{"appWidgetId":113,"label":"Compass","targetType":"PRESET","targetId":"saved-compass","iconKey":"COMPASS","colour":"TEAL"}"""
        )

        assertEquals(MethodMeshWidgetAppearance.SOLID, config.appearance)
        assertEquals(MethodMeshWidgetTargetType.PRESET, config.targetType)
        assertEquals("saved-compass", config.targetId)
    }

    @Test
    fun `glass migrates to frosted`() {
        val config = decode(
            """{"appWidgetId":7,"targetType":"PRESET","targetId":"p1","appearance":"GLASS"}"""
        )

        assertEquals("FROSTED", config.appearance.name)
    }

    @Test
    fun `transparent and minimal migrate to compact`() {
        listOf("TRANSPARENT", "MINIMAL").forEach { legacyName ->
            val config = decode(
                """{"appWidgetId":8,"targetType":"PRESET","targetId":"p1","appearance":"$legacyName"}"""
            )
            assertEquals("COMPACT", config.appearance.name)
        }
    }

    @Test
    fun `bundle preserves heterogeneous explicit order`() {
        val config = decode(
            """{"appWidgetId":9,"label":"Field tools","targetType":"BUNDLE","targetId":"","appearance":"FROSTED","bundleTargets":[{"type":"SCHEDULE","id":"s1","fallbackLabel":"Rounds"},{"type":"PRESET","id":"p1","fallbackLabel":"Compass"},{"type":"PROTOCOL","id":"r1","fallbackLabel":"Survey"}]}"""
        )

        assertEquals(MethodMeshWidgetTargetType.BUNDLE, config.targetType)
        assertEquals("FROSTED", config.appearance.name)
        assertEquals(
            listOf(
                MethodMeshWidgetTargetType.SCHEDULE,
                MethodMeshWidgetTargetType.PRESET,
                MethodMeshWidgetTargetType.PROTOCOL
            ),
            config.bundleTargets.map { it.type }
        )
    }

    @Test
    fun `invalid nested bundles and duplicate members are rejected during recovery`() {
        val config = decode(
            """{"appWidgetId":10,"targetType":"BUNDLE","bundleTargets":[{"type":"BUNDLE","id":"nested"},{"type":"PRESET","id":"p1"},{"type":"PRESET","id":"p1"},{"type":"PRESET","id":""}]}"""
        )

        assertEquals(1, config.bundleTargets.size)
        assertEquals("p1", config.bundleTargets.single().id)
    }

    @Test
    fun `unknown appearance migrates to solid`() {
        val config = decode(
            """{"appWidgetId":11,"targetType":"PRESET","targetId":"coin","appearance":"UNKNOWN"}"""
        )

        assertEquals(MethodMeshWidgetAppearance.SOLID, config.appearance)
    }
}
