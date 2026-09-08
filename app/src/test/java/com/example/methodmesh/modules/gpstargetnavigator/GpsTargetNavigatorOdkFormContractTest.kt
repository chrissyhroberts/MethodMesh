package com.example.methodmesh.modules.gpstargetnavigator

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class GpsTargetNavigatorOdkFormContractTest {
    @Test
    fun `gps navigator example supports plus code coordinates and background json`() {
        val docs = listOf(
            File("src/main/java/com/example/methodmesh/modules/gpstargetnavigator/docs"),
            File("app/src/main/java/com/example/methodmesh/modules/gpstargetnavigator/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate GPS navigator docs")
        val workbook = File(docs, "example_odk_gps_target_navigator.xlsx")
        assertTrue("Expected GPS navigator XLSForm", workbook.isFile)

        val xml = ZipFile(workbook).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".xml") }
                .joinToString("\n") { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
        }

        assertTrue("Workbook must route directly to gps_target_navigator", "method_id='gps_target_navigator'" in xml)
        assertTrue("Workbook must support Plus Code destinations", "input_target_plus_code=\${target_plus_code_input}" in xml)
        assertTrue("Workbook must support coordinate destinations", "input_target_latitude=\${target_latitude_input}" in xml && "input_target_longitude=\${target_longitude_input}" in xml)
        assertTrue("Workbook must request main results plus background metadata JSON", "input_payload_mode='FULL'" in xml)
        assertTrue("Workbook must return the background audit JSON", ">methodmesh_full_json<" in xml)
        assertTrue("Workbook must return distance", ">distance_m<" in xml)
        assertTrue("Workbook must return arrival state", ">arrived<" in xml)
    }
}
