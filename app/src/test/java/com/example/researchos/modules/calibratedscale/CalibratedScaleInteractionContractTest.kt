package com.example.researchos.modules.calibratedscale

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibratedScaleInteractionContractTest {
    @Test
    fun `scalar capture requires the scalar marker to be touched`() {
        assertEquals(setOf("value"), requiredScaleSelections(useRange = false))
    }

    @Test
    fun `range capture requires both range markers to be touched`() {
        assertEquals(
            setOf("lower_value", "upper_value"),
            requiredScaleSelections(useRange = true)
        )
    }
}
