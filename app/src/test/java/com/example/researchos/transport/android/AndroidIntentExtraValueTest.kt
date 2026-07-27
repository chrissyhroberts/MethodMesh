package com.example.researchos.transport.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidIntentExtraValueTest {
    @Test
    fun `numeric ODK extras retain their value`() {
        assertEquals("50", androidExtraValue(50))
        assertEquals("50.5", androidExtraValue(50.5))
    }

    @Test
    fun `boolean and text ODK extras retain their value`() {
        assertEquals("true", androidExtraValue(true))
        assertEquals("Rate your pain", androidExtraValue("Rate your pain"))
    }
}
