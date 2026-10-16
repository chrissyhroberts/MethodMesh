package com.example.methodmesh.modules.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsCapabilityScreenTest {
    @Test
    fun parseSmsRecipients_acceptsSeparatorsAndDeduplicates() {
        assertEquals(
            listOf("+441234567890", "+441234567891", "+441234567892"),
            parseSmsRecipients(" +441234567890, +441234567891;\n+441234567890\r\n+441234567892 ")
        )
    }

    @Test
    fun parseSmsRecipients_ignoresBlankEntries() {
        assertEquals(
            listOf("01234567890"),
            parseSmsRecipients(", ;\n 01234567890 ;; \r\n")
        )
    }
}
