package com.example.researchos.modules.calibratedscale

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CalibratedScaleOdkFormContractTest {
    @Test
    fun `calibrated scale examples cover supported configurations`() {
        val docs = listOf(
            File("src/main/java/com/example/researchos/modules/calibratedscale/docs"),
            File("app/src/main/java/com/example/researchos/modules/calibratedscale/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate calibrated-scale docs")
        val requiredIntentByWorkbook = mapOf(
            "example_odk_CalibratedScale.xlsx" to
                "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',return_mode='flat')",
            "example_odk_CalibratedScaleRange.xlsx" to
                "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',return_mode='flat')",
            "example_odk_CalibratedScaleMinMax.xlsx" to
                "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',return_mode='flat')",
            "example_odk_CalibratedScaleVertical.xlsx" to
                "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',return_mode='flat')"
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
                "$fileName must route directly to calibrated_scale",
                requiredIntent in xml
            )
            assertFalse(
                "$fileName must not depend on an interpolated method ID",
                "\${method_id}" in xml
            )
            assertTrue(
                "$fileName must send a caller-editable prompt",
                "Question shown above the scale" in xml && "Rate your pain" in xml
            )
            assertTrue(
                "$fileName must use the current example version",
                "2026072706" in xml
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

        val rangeXml = workbookXml("example_odk_CalibratedScaleRange.xlsx")
        assertTrue("Range example must supply labels", "lower_label" in rangeXml && "upper_label" in rangeXml)
        assertTrue(
            "Range example must send use_range as an ordinary group field",
            "Use two scales" in rangeXml && "select_one boolean_value" in rangeXml
        )
        assertFalse(
            "Range configuration must not depend on a body-intent parameter",
            "use_range='true'" in rangeXml
        )

        val minMaxXml = workbookXml("example_odk_CalibratedScaleMinMax.xlsx")
        assertTrue("Min/max example must supply both bounds", "Minimum value" in minMaxXml && "Maximum value" in minMaxXml)

        val verticalXml = workbookXml("example_odk_CalibratedScaleVertical.xlsx")
        assertTrue(
            "Vertical example must send vertical_mode as an ordinary group field",
            "Vertical orientation" in verticalXml && "select_one boolean_value" in verticalXml
        )
        assertFalse(
            "Vertical configuration must not depend on a body-intent parameter",
            "vertical_mode='true'" in verticalXml
        )
    }
}
