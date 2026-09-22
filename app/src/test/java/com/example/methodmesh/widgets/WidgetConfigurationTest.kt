package com.example.methodmesh.widgets

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WidgetConfigurationTest {
    private fun decode(raw: String): MethodMeshWidgetConfig {
        val decoder = MethodMeshWidgetRepository::class.java.getDeclaredMethod("decode", JSONObject::class.java)
        decoder.isAccessible = true
        return decoder.invoke(MethodMeshWidgetRepository, JSONObject(raw)) as MethodMeshWidgetConfig
    }
    @Test fun `existing preset target and custom appearance survive decoding`() {
        val c = decode("""{"appWidgetId":113,"label":"Compass","targetType":"PRESET","targetId":"saved-compass","iconKey":"COMPASS","colour":"TEAL"}""")
        assertEquals(113, c.appWidgetId); assertEquals("saved-compass", c.targetId)
        assertEquals(MethodMeshWidgetTargetType.PRESET, c.targetType)
        assertEquals(MethodMeshWidgetIconKey.COMPASS, c.iconKey)
        assertEquals("Compass", c.label)
    }
    @Test fun `legacy widgets without a colour retain their target`() {
        val c = decode("""{"appWidgetId":50,"targetType":"PRESET","targetId":"coin","iconKey":"RANDOM"}""")
        assertEquals("coin", c.targetId); assertEquals(MethodMeshWidgetColour.TEAL, c.colour)
        assertEquals(MethodMeshWidgetIconKey.RANDOM, c.iconKey)
    }
    @Test fun `protocol and schedule target types remain distinct`() {
        for (type in listOf(MethodMeshWidgetTargetType.PROTOCOL, MethodMeshWidgetTargetType.SCHEDULE)) {
            val c = decode("""{"appWidgetId":42,"targetType":"${type.name}","targetId":"target-${type.name}"}""")
            assertEquals(type, c.targetType); assertEquals("target-${type.name}", c.targetId)
        }
    }
}
