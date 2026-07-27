package com.example.researchos.modules.choiceexperiment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ChoiceOdkFormContractTest {
    @Test
    fun `choice forms carry static configuration in namespaced intent inputs`() {
        val docs = listOf(
            File("src/main/java/com/example/researchos/modules/choiceexperiment/docs"),
            File("app/src/main/java/com/example/researchos/modules/choiceexperiment/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate choice-experiment docs")

        val methodByWorkbook = mapOf(
            "example_odk_dce.pairwise.xlsx" to "dce.pairwise",
            "example_odk_dce.maxdiff.xlsx" to "dce.maxdiff",
            "example_odk_dce.ranking.xlsx" to "dce.ranking",
            "example_odk_dce.points.xlsx" to "dce.points",
            "example_odk_dce.conjoint.xlsx" to "dce.conjoint"
        )
        val workbooks = methodByWorkbook.map { (name, methodId) ->
            val workbook = File(docs, name)
            assertTrue("Expected $name", workbook.isFile)
            workbook to methodId
        }

        workbooks.forEach { (workbook, methodId) ->
            val xml = ZipFile(workbook).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".xml") }
                    .joinToString("\n") { entry ->
                        zip.getInputStream(entry).bufferedReader().readText()
                    }
            }
            assertTrue(workbook.name, "com.example.researchos.EXECUTE_METHOD" in xml)
            assertTrue(
                "${workbook.name} must route to its fixed method",
                "com.example.researchos.EXECUTE_METHOD(method_id='$methodId'," in xml
            )
            assertTrue("${workbook.name} must encode the static item/class list", "input64_" in xml)
            assertTrue("${workbook.name} must namespace scalar settings", "input_" in xml)
            assertFalse(workbook.name, "\${items_input}" in xml)
            assertFalse(workbook.name, "\${classes_input}" in xml)
        }
    }
}
