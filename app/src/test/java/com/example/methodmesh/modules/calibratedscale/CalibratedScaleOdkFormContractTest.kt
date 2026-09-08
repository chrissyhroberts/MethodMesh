package com.example.methodmesh.modules.calibratedscale

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CalibratedScaleOdkFormContractTest {
    @Test
    fun `calibrated scale examples cover supported configurations`() {
        val docs = listOf(
            File("src/main/java/com/example/methodmesh/modules/calibratedscale/docs"),
            File("app/src/main/java/com/example/methodmesh/modules/calibratedscale/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate calibrated-scale docs")
        val requiredIntentByWorkbook = mapOf(
            "example_odk_calibrated_scale.xlsx" to "input_vas_length_mm='50'",
            "example_odk_calibrated_scale_Range.xlsx" to "input_use_range='true'",
            "example_odk_calibrated_scale_MinMax.xlsx" to "input_minimum='0',input_maximum='10'",
            "example_odk_calibrated_scale_Vertical.xlsx" to "input_vertical_mode='true'"
        )

        requiredIntentByWorkbook.forEach { (fileName, requiredIntent) ->
            val workbook = File(docs, fileName)
            assertTrue("Expected $fileName", workbook.isFile)
            val xml = ZipFile(workbook).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".xml") }
                    .joinToString("\n") { entry ->
                        zip.getInputStream(entry).bufferedReader().readText()
                    }
            }

            assertTrue(
                "$fileName must include its namespaced configuration",
                requiredIntent in xml
            )
            assertTrue("$fileName must route directly to calibrated_scale", "method_id='calibrated_scale'" in xml)
            assertTrue("$fileName must request main results plus background metadata JSON", "input_payload_mode='FULL'" in xml)
            assertTrue("$fileName must return the background audit JSON", ">methodmesh_full_json<" in xml)
            assertFalse(
                "$fileName must not depend on an interpolated method ID",
                "\${method_id}" in xml
            )
            assertTrue(
                "$fileName must send the prompt without exposing a configuration field",
                "input_prompt='Rate your pain'" in xml
            )
            assertTrue(
                "$fileName must send a participant hint",
                "input_hint='0 means no pain; 100 means the worst pain you can imagine'" in xml
            )
            assertTrue(
                "$fileName must use the current example version",
                "2026072709" in xml
            )
        }

        fun workbookXml(fileName: String): String =
            ZipFile(File(docs, fileName)).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".xml") }
                    .joinToString("\n") { entry ->
                        zip.getInputStream(entry).bufferedReader().readText()
                    }
            }

        val rangeXml = workbookXml("example_odk_calibrated_scale_Range.xlsx")
        assertTrue("Range example must supply labels", "input_lower_label" in rangeXml && "input_upper_label" in rangeXml)
        assertTrue(
            "Range configuration must live in the intent rather than participant fields",
            "input_use_range='true'" in rangeXml
        )
        assertFalse("Range example must not create a scalar value column", ">value<" in rangeXml)
        assertTrue("Range example must return the lower value", ">lower_value<" in rangeXml)
        assertTrue("Range example must return the upper value", ">upper_value<" in rangeXml)
        assertFalse("Range example must not expose minimum as a separate ODK return column", ">minimum<" in rangeXml)
        assertFalse("Range example must not expose calibration as a separate ODK return column", ">dp_per_mm<" in rangeXml)

        val minMaxXml = workbookXml("example_odk_calibrated_scale_MinMax.xlsx")
        assertTrue("Min/max example must supply both bounds", "input_minimum='0'" in minMaxXml && "input_maximum='10'" in minMaxXml)
        assertTrue("Scalar examples must return value", ">value<" in minMaxXml)
        assertFalse("Scalar examples must not create lower-value columns", ">lower_value<" in minMaxXml)
        assertFalse("Scalar examples must not create upper-value columns", ">upper_value<" in minMaxXml)
        assertFalse("Scalar examples must not expose maximum as a separate ODK return column", ">maximum<" in minMaxXml)

        val verticalXml = workbookXml("example_odk_calibrated_scale_Vertical.xlsx")
        assertTrue(
            "Vertical configuration must live in the intent rather than participant fields",
            "input_vertical_mode='true'" in verticalXml
        )
    }
}
