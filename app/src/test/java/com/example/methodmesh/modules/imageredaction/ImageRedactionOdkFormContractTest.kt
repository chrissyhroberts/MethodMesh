package com.example.methodmesh.modules.imageredaction

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ImageRedactionOdkFormContractTest {
    @Test
    fun `image redaction example returns redacted media and audit json`() {
        val docs = listOf(
            File("src/main/java/com/example/methodmesh/modules/imageredaction/docs"),
            File("app/src/main/java/com/example/methodmesh/modules/imageredaction/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate image-redaction docs")
        val workbook = File(docs, "example_odk_image.redact.xlsx")

        assertTrue("Expected example_odk_image.redact.xlsx", workbook.isFile)

        val xml = ZipFile(workbook).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".xml") }
                .joinToString("\n") { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
        }

        assertTrue("Workbook must invoke image.redact", "method_id='image.redact'" in xml)
        assertTrue("Workbook must request full payload metadata", "input_payload_mode='FULL'" in xml)
        assertTrue("Workbook must return the redacted image URI", ">redacted_image_uri<" in xml)
        assertTrue("Workbook must return the background audit JSON", ">methodmesh_full_json<" in xml)
        assertTrue("Workbook must save the canonical form title", ">image.redact<" in xml)
    }
}
