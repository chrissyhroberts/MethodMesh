package com.example.methodmesh.modules.calibratedscale

import androidx.compose.runtime.saveable.SaverScope
import com.example.methodmesh.settings.SettingsState
import org.junit.Assert.*
import org.junit.Test

class CalibratedScaleSavedStateTest {
    private val schema = CalibratedScaleInteraction().settings
    private val scope = SaverScope { true }

    @Test fun scalarAndConfigurationRestoreWithTheirOriginalTypes() {
        val original = SettingsState(schema).apply {
            setFloat("value", 62.25f)
            setFloat("minimum", -5f)
            setFloat("maximum", 75f)
            setFloat("vas_length_mm", 50f)
            setBoolean("vertical_mode", true)
            setString("prompt", "Fixture question")
        }
        val saver = calibratedScaleSettingsSaver(schema)
        val restored = saver.restore(with(saver) { scope.save(original) }!!)!!
        assertEquals(original.asMap(), restored.asMap())
        assertEquals(62.25f, restored.getFloat("value"), 0f)
        assertTrue(restored.getBoolean("vertical_mode"))
    }

    @Test fun rangeSelectionSnapshotDoesNotChangeWithLaterEdits() {
        val original = SettingsState(schema).apply {
            setBoolean("use_range", true)
            setFloat("lower_value", 25f)
            setFloat("upper_value", 80f)
        }
        val saver = calibratedScaleSettingsSaver(schema)
        val saved = with(saver) { scope.save(original) }!!
        original.setFloat("lower_value", 40f)
        val restored = saver.restore(saved)!!
        assertTrue(restored.getBoolean("use_range"))
        assertEquals(25f, restored.getFloat("lower_value"), 0f)
        assertEquals(80f, restored.getFloat("upper_value"), 0f)
    }

    @Test fun restorationDoesNotPretendToBeNewOperatorInput() {
        val changed = mutableListOf<String>()
        val saver = calibratedScaleSettingsSaver(schema) { key, _ -> changed += key }
        val original = SettingsState(schema).apply { setFloat("value", 42f) }
        val restored = saver.restore(with(saver) { scope.save(original) }!!)!!
        assertTrue(changed.isEmpty())
        restored.setFloat("value", 43f)
        assertEquals(listOf("value"), changed)
    }

    @Test fun accessibleSelectionIsContinuousAndClampedToTheConfiguredBounds() {
        assertEquals(0.25f, accessibleScaleValue(0.25f, 0f, 1f)!!, 0f)
        assertEquals(-5f, accessibleScaleValue(-6f, -5f, 5f)!!, 0f)
        assertEquals(5f, accessibleScaleValue(6f, -5f, 5f)!!, 0f)
    }

    @Test fun accessibleSelectionRejectsNonFiniteInputAndBounds() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(accessibleScaleValue(invalid, 0f, 100f))
            assertNull(accessibleScaleValue(50f, invalid, 100f))
            assertNull(accessibleScaleValue(50f, 0f, invalid))
        }
    }

    @Test fun accessibleSelectionRejectsAnEmptyOrReversedRange() {
        assertNull(accessibleScaleValue(5f, 5f, 5f))
        assertNull(accessibleScaleValue(5f, 10f, 0f))
    }
}
