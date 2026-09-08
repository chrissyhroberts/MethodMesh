package com.example.methodmesh.modules.pluscodecapture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class PlusCodeCaptureOdkFormContractTest {
    @Test
    fun `plus code example returns plus code and background json`() {
        val docs = listOf(
            File("src/main/java/com/example/methodmesh/modules/pluscodecapture/docs"),
            File("app/src/main/java/com/example/methodmesh/modules/pluscodecapture/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate Plus Code docs")
        val workbook = File(docs, "example_odk_plus_code.capture.xlsx")
        assertTrue("Expected Plus Code XLSForm", workbook.isFile)

        val xml = ZipFile(workbook).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".xml") }
                .joinToString("\n") { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
        }

        assertTrue("Workbook must invoke plus_code.capture", "method_id='plus_code.capture'" in xml)
        assertTrue("Workbook must request main result plus background metadata JSON", "input_payload_mode='FULL'" in xml)
        assertTrue("Workbook must return the full Plus Code", ">plus_code<" in xml)
        assertTrue("Workbook must return MethodMesh status", ">methodmesh_status<" in xml)
        assertTrue("Workbook must return background audit JSON", ">methodmesh_full_json<" in xml)
        assertTrue("Workbook must keep capture settings as choices", "select_one code_length" in xml && "select_one basemap_mode" in xml)
        assertFalse("Workbook must not expose GPS latitude as a separate ODK return column", ">plus_code_gps_latitude<" in xml)
        assertFalse("Workbook must not expose audit JSON as a second legacy field", ">plus_code_audit_json<" in xml)
    }
}
