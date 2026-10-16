package com.example.methodmesh.modules.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

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

    @Test
    fun bundledOdkForms_mapMultiRecipientOutputs() {
        val docs = File("src/main/java/com/example/methodmesh/modules/sms/docs")
        listOf("example_odk_sms_send.xlsx", "example_odk_showcase_sms_send.xlsx").forEach { name ->
            ZipFile(File(docs, name)).use { workbook ->
                val survey = workbook.getInputStream(workbook.getEntry("xl/worksheets/sheet1.xml"))
                    .bufferedReader().use { it.readText() }
                assertTrue(name, survey.contains("Phone numbers (comma, semicolon, or newline separated)"))
                assertTrue(name, survey.contains("sms_recipients_json"))
                assertTrue(name, survey.contains("sms_recipient_count"))
                assertTrue(name, survey.contains("sms_recipient_results_json"))
            }
        }
    }
}
