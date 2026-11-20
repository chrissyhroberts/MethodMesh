package com.example.methodmesh.modules.astronomy

import org.junit.Assert.*
import org.junit.Test

class AstronomyCalculationInputTest {
    private fun defaults(method: AstronomyMethodBase) =
        AstronomyModule.capabilitySettings().getValue(method.id).associate { it.id to it.autoDefault() }

    @Test fun declaredDefaultsAreValidForBothLocalCalculators() {
        listOf(As100ImageScaleMethod, As100ExposureLimitMethod).forEach { validateAstronomyCalculation(it, defaults(it)) }
    }
    @Test fun nonFiniteValuesAreRejectedBeforeTheyReachSlidersOrResults() {
        for (value in listOf("NaN", "Infinity", "-Infinity")) {
            assertThrows(IllegalArgumentException::class.java) {
                validateAstronomyCalculation(As100ImageScaleMethod, defaults(As100ImageScaleMethod) + ("focal_length_mm" to value))
            }
        }
    }
    @Test fun incompleteInputCannotSilentlyFallBackToADifferentCalculation() {
        assertThrows(IllegalArgumentException::class.java) {
            validateAstronomyCalculation(As100ExposureLimitMethod, defaults(As100ExposureLimitMethod) + ("pixel_size_micron" to ""))
        }
    }
    @Test fun validTypedOpticsOutsideTheConvenienceSliderRangeRemainAccepted() {
        validateAstronomyCalculation(As100ImageScaleMethod, defaults(As100ImageScaleMethod) + ("focal_length_mm" to "5000"))
    }
    @Test fun outOfContractRangesAreRejectedWithoutClampingEvidence() {
        for (value in listOf("0", "-1", "20001")) assertThrows(IllegalArgumentException::class.java) {
            validateAstronomyCalculation(As100ImageScaleMethod, defaults(As100ImageScaleMethod) + ("focal_length_mm" to value))
        }
    }
    @Test fun validExactValuesRemainUnchanged() {
        val fields = defaults(As100ImageScaleMethod).toMutableMap().apply { put("focal_length_mm", "401.2345") }
        validateAstronomyCalculation(As100ImageScaleMethod, fields)
        assertEquals("401.2345", fields["focal_length_mm"])
    }
    @Test fun savedInputsExcludeUnrelatedCallerAuditWithoutMutatingTheRequest() {
        val raw = mapOf("input_target" to "M31", "methodmesh_full_json" to "opaque caller audit",
            "previous_payload" to "large unrelated value")
        val ui = astronomyEditableInputs(As100SessionMethod, raw)
        assertEquals("M31", ui["target"])
        assertFalse(ui.containsKey("methodmesh_full_json"))
        assertFalse(ui.containsKey("previous_payload"))
        assertEquals("opaque caller audit", raw["methodmesh_full_json"])
    }
    @Test fun absentHistoryInputUsesTheDeclaredOffDefault() {
        val ui = astronomyEditableInputs(As100SessionMethod, emptyMap())
        assertEquals("false", ui["save_session"])
    }
}
