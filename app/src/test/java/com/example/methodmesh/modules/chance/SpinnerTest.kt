package com.example.methodmesh.modules.chance

import com.example.methodmesh.settings.MethodSetting
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class SpinnerTest {
    private val original = listOf("Joe", "Jeff", "Jayne")
    private fun round(items: List<String> = original, remove: Boolean = false) = As100SpinnerMethod.generate(mapOf(
        "items" to items.joinToString("\n"), "remove_after_spin" to remove.toString(),
        "rng_mode" to "fixed_seed", "seed" to "spinner-regression"
    ))
    private fun list(json: String): List<String> = JSONArray(json).let { array -> List(array.length()) { array.getString(it) } }

    @Test fun `default preserves existing deterministic choice and all labels`() {
        val values = round()
        val expected = ChanceSelectionEngine.spin(original.joinToString("\n"), "fixed_seed", "spinner-regression")
        assertEquals("succeeded", values[SpinnerFields.STATUS])
        assertEquals(expected.label, values[SpinnerFields.RESULT])
        assertEquals((expected.index + 1).toString(), values[SpinnerFields.SELECTED_INDEX])
        assertEquals(original, list(values.getValue(SpinnerFields.ITEMS_JSON)))
        assertEquals(original, list(values.getValue(SpinnerFields.REMAINING_ITEMS_JSON)))
        assertEquals("false", values[SpinnerFields.REMOVE_AFTER_SPIN])
    }

    @Test fun `removal round returns exact selected occurrence and ordered remaining pool`() {
        val values = round(remove = true)
        val index = values.getValue(SpinnerFields.SELECTED_INDEX).toInt() - 1
        assertEquals(original[index], values[SpinnerFields.RESULT])
        assertEquals(original.filterIndexed { i, _ -> i != index }, list(values.getValue(SpinnerFields.REMAINING_ITEMS_JSON)))
        assertEquals(original, list(values.getValue(SpinnerFields.ITEMS_JSON)))
        assertEquals("true", values[SpinnerFields.REMOVE_AFTER_SPIN])
    }

    @Test fun `duplicate labels remove only the landed segment`() {
        val duplicates = listOf("Joe", "Joe", "Jayne")
        assertEquals(listOf("Joe", "Jayne"), spinnerRemainingItems(duplicates, 0, true))
        assertEquals(listOf("Joe", "Jayne"), spinnerRemainingItems(duplicates, 1, true))
        assertEquals(listOf("Joe", "Joe"), spinnerRemainingItems(duplicates, 2, true))
        assertEquals(duplicates, spinnerRemainingItems(duplicates, 1, false))
    }

    @Test fun `draw without replacement includes last label then stops empty`() {
        var pool = original
        val winners = mutableListOf<String>()
        for (size in 3 downTo 1) {
            val values = round(pool, true)
            assertEquals("succeeded", values[SpinnerFields.STATUS])
            val audit = JSONObject(values.getValue(SpinnerFields.AUDIT_JSON))
            assertEquals(size, audit.getJSONArray("items").length())
            assertTrue(audit.getBoolean("remove_after_spin"))
            winners += values.getValue(SpinnerFields.RESULT)
            pool = list(values.getValue(SpinnerFields.REMAINING_ITEMS_JSON))
            assertEquals(size - 1, pool.size)
        }
        assertEquals(original.toSet(), winners.toSet())
        assertTrue(pool.isEmpty())
        assertEquals("failed", round(pool, true)[SpinnerFields.STATUS])
    }

    @Test fun `single remaining label is allowed only in removal mode`() {
        assertEquals("failed", round(listOf("Jayne"))[SpinnerFields.STATUS])
        val final = round(listOf("Jayne"), true)
        assertEquals("Jayne", final[SpinnerFields.RESULT])
        assertEquals("[]", final[SpinnerFields.REMAINING_ITEMS_JSON])
    }

    @Test fun `invalid and exhausted lists cannot silently become default yes no wheel`() {
        assertEquals("failed", round(emptyList(), true)[SpinnerFields.STATUS])
        assertEquals("failed", round(emptyList(), false)[SpinnerFields.STATUS])
        assertEquals("failed", round(List(61) { "Name $it" }, true)[SpinnerFields.STATUS])
        assertEquals("succeeded", round(List(60) { "Name $it" }, true)[SpinnerFields.STATUS])
    }

    @Test fun `external input prefix has the same removal semantics`() {
        val values = As100SpinnerMethod.generate(mapOf("input_items" to "Joe|Jeff|Jayne",
            "input_remove_after_spin" to " true ", "input_rng_mode" to "fixed_seed", "input_seed" to "spinner-regression"))
        assertEquals(round(remove = true)[SpinnerFields.RESULT], values[SpinnerFields.RESULT])
        assertEquals(round(remove = true)[SpinnerFields.REMAINING_ITEMS_JSON], values[SpinnerFields.REMAINING_ITEMS_JSON])
    }

    @Test fun `removal is declared for every canonical surface and fields are additive`() {
        val settings = ChanceModule.capabilitySettings().getValue(As100SpinnerMethod.ID)
        assertTrue(settings.single { it.id == "remove_after_spin" } is MethodSetting.BooleanSetting)
        assertTrue(SpinnerFields.outputs.containsAll(listOf("spinner_result", "spinner_items_json", "spinner_selected_index", "spinner_selected_value", "spinner_audit_json")))
        assertEquals(SpinnerFields.outputs.toSet(), round().keys)
        assertEquals(SpinnerFields.outputs, As100SpinnerMethod.descriptor.outputs)
        assertEquals(As100SpinnerMethod.VERSION, As100SpinnerMethod.descriptor.version)
        assertEquals(As100SpinnerMethod.VERSION, JSONObject(round().getValue(SpinnerFields.AUDIT_JSON)).getString("method_version"))
    }

    @Test fun `all segment labels have at least WCAG AA normal text contrast`() {
        fun luminance(argb: Long): Double {
            fun component(shift: Int): Double {
                val value = ((argb shr shift) and 255) / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * component(16) + .7152 * component(8) + .0722 * component(0)
        }
        spinnerPalette.forEach { colours ->
            val light = maxOf(luminance(colours.foreground), luminance(colours.background))
            val dark = minOf(luminance(colours.foreground), luminance(colours.background))
            assertTrue("Low-contrast spinner label: $colours", (light + .05) / (dark + .05) >= 4.5)
        }
    }

    @Test fun `adjacent segment backgrounds never merge including cyclic join`() {
        for (count in 2..60) for (index in 0 until count) {
            assertNotEquals("Wheel of $count segments at $index", spinnerSegmentColours(index, count).background,
                spinnerSegmentColours((index + 1) % count, count).background)
        }
    }

    @Test fun `selected segment centre always lands at the pointer`() {
        for (count in 1..60) for (index in 0 until count) {
            val angle = spinnerRotation(index, count, 1f) + (index + .5f) * 360f / count
            assertEquals(2160f, angle, .001f)
            assertEquals(0f, spinnerRotation(index, count, 0f), 0f)
            assertTrue(spinnerRotation(index, count, .8f) > spinnerRotation(index, count, .2f))
        }
    }
}
