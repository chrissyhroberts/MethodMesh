package com.example.methodmesh.modules.calibratedscale

import com.example.methodmesh.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibratedScaleResultContractTest {
    @Test
    fun `scalar result returns value but omits range defaults`() {
        val settings = SettingsState(CalibratedScaleInteraction().settings).apply {
            setBoolean("use_range", false)
            setFloat("value", 62f)
        }

        val values = As100CalibratedScaleMethod.measurementValues(settings)

        assertEquals(62f, values["value"])
        assertFalse("lower_value" in values)
        assertFalse("upper_value" in values)
    }

    @Test
    fun `range result returns both bounds but omits scalar default`() {
        val settings = SettingsState(CalibratedScaleInteraction().settings).apply {
            setBoolean("use_range", true)
            setFloat("value", 50f)
            setFloat("lower_value", 20f)
            setFloat("upper_value", 80f)
        }

        val values = As100CalibratedScaleMethod.measurementValues(settings)

        assertFalse("value" in values)
        assertEquals(20f, values["lower_value"])
        assertEquals(80f, values["upper_value"])
        assertTrue(values["use_range"] == true)
    }

    @Test
    fun `current value display shows that the scale is continuous`() {
        assertEquals("5.0", formatScaleCurrentValue(5f, 0f, 10f))
        assertEquals("5.4", formatScaleCurrentValue(5.44f, 0f, 10f))
        assertEquals("0.25", formatScaleCurrentValue(0.25f, 0f, 1f))
    }
}
