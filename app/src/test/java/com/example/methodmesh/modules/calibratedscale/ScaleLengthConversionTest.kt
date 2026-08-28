package com.example.methodmesh.modules.calibratedscale

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleLengthConversionTest {
    @Test
    fun `five centimetres at calibrated 7 point 55 dp per mm is 377 point 5 dp`() {
        assertEquals(377.5f, scaleLengthDp(lengthMm = 50f, dpPerMm = 7.55f), 0.0001f)
    }

    @Test
    fun `conversion is orientation independent`() {
        val horizontal = scaleLengthDp(lengthMm = 50f, dpPerMm = 7.55f)
        val vertical = scaleLengthDp(lengthMm = 50f, dpPerMm = 7.55f)
        assertEquals(horizontal, vertical, 0f)
    }
}
