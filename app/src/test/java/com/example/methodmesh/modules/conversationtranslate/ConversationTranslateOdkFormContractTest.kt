package com.example.methodmesh.modules.conversationtranslate

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTranslateOdkFormContractTest {
    @Test
    fun `conversation translator example uses group intent and returns transcript plus json`() {
        val docs = listOf(
            File("app/src/main/java/com/example/methodmesh/modules/conversationtranslate/docs"),
            File("src/main/java/com/example/methodmesh/modules/conversationtranslate/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate conversation translator docs")
        val workbook = File(docs, "example_odk_conversation.translate.xlsx")

        assertTrue("Expected example_odk_conversation.translate.xlsx", workbook.isFile)

        val xml = ZipFile(workbook).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("xl/worksheets/") }
                .joinToString("\n") { entry -> zip.getInputStream(entry).bufferedReader().readText() }
        }

        assertTrue("Workbook must invoke conversation.translate", "method_id='conversation.translate'" in xml)
        assertTrue("Workbook must request main transcript plus background metadata JSON", "input_payload_mode='FULL'" in xml)
        assertTrue("Intent must live on a begin_group row", ">begin_group<" in xml)
        assertTrue("Workbook must return the main transcript", ">conversation_transcript<" in xml)
        assertTrue("Workbook must return the full JSON metadata field", ">methodmesh_full_json<" in xml)
        assertFalse("Workbook must not expose turn JSON as a separate ODK return column", ">conversation_turns_json<" in xml)
    }
}
