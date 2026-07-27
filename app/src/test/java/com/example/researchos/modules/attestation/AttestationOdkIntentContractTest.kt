package com.example.researchos.modules.attestation

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class AttestationOdkIntentContractTest {
    private val docsDirectory: File
        get() = listOf(
            File("src/main/java/com/example/researchos/modules/attestation/docs"),
            File("app/src/main/java/com/example/researchos/modules/attestation/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate attestation docs")

    @Test
    fun `signed attestation examples do not let blank return fields overwrite request parameters`() {
        val failures = signedAttestationExamples().flatMap { workbook ->
            val rows = surveyRows(workbook)
            val headers = rows.first().withIndex().associate { (index, value) -> value to index }
            val typeColumn = headers.getValue("type")
            val nameColumn = headers.getValue("name")
            val intentColumn = headers.getValue("body::intent")
            val beginGroup = rows.indexOfFirst { row ->
                row.getOrElse(typeColumn) { "" } == "begin_group" &&
                    row.getOrElse(nameColumn) { "" } == "attestation"
            }
            require(beginGroup >= 0) { "${workbook.name}: attestation group not found" }
            val endGroup = (beginGroup + 1 until rows.size).first { index ->
                rows[index].getOrElse(typeColumn) { "" } == "end_group"
            }
            val parameterNames = Regex("""(?:\(|,)\s*([A-Za-z][A-Za-z0-9_]*)\s*=""")
                .findAll(rows[beginGroup].getOrElse(intentColumn) { "" })
                .map { it.groupValues[1] }
                .toSet()
            val childNames = rows.subList(beginGroup + 1, endGroup)
                .map { it.getOrElse(nameColumn) { "" } }
                .filter(String::isNotBlank)
                .toSet()
            val collisions = parameterNames.intersect(childNames)
            collisions.map { collision ->
                "${workbook.name}: '$collision' is both a body::intent parameter and a return-group field"
            }
        }

        assertTrue(
            "ODK Collect overwrites colliding body::intent parameters with group-field values:\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun `signed attestation examples expose failure diagnostics`() {
        val failures = signedAttestationExamples().mapNotNull { workbook ->
            val rows = surveyRows(workbook)
            val headers = rows.first().withIndex().associate { (index, value) -> value to index }
            val nameColumn = headers.getValue("name")
            if (rows.any { it.getOrElse(nameColumn) { "" } == "diagnostic_reason" }) {
                null
            } else {
                "${workbook.name}: missing diagnostic_reason return field"
            }
        }
        assertTrue(
            "Attestation examples must make failed execution self-diagnosing:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    @Test
    fun `signed attestation examples return hash-only verification evidence`() {
        val failures = signedAttestationExamples().flatMap { workbook ->
            val rows = surveyRows(workbook)
            val headers = rows.first().withIndex().associate { (index, value) -> value to index }
            val nameColumn = headers.getValue("name")
            val names = rows.map { it.getOrElse(nameColumn) { "" } }.toSet()
            buildList {
                if ("verification_evidence_payload" in names) {
                    add("${workbook.name}: raw verification_evidence_payload must not be returned")
                }
                if ("verification_evidence_format" !in names) {
                    add("${workbook.name}: missing verification_evidence_format")
                }
                if ("verification_evidence_hash" !in names) {
                    add("${workbook.name}: missing verification_evidence_hash")
                }
            }
        }
        assertTrue(
            "Attestation examples must return only reproducible evidence metadata and hashes:\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    private fun signedAttestationExamples(): List<File> = docsDirectory.listFiles().orEmpty()
        .filter {
            it.isFile &&
                it.name.startsWith("example_odk_Attestation") &&
                it.name != "example_odk_AttestationAnchor.xlsx" &&
                it.extension == "xlsx"
        }
        .sortedBy(File::getName)

    private fun surveyRows(workbook: File): List<List<String>> = ZipFile(workbook).use { zip ->
        fun documentBuilder() = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder()
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            zip.getInputStream(entry).use { stream ->
                val document = documentBuilder().parse(stream)
                val strings = document.getElementsByTagNameNS("*", "si")
                (0 until strings.length).map { index -> strings.item(index).textContent }
            }
        }.orEmpty()
        val sheet = zip.getEntry("xl/worksheets/sheet1.xml")
            ?: error("${workbook.name}: survey worksheet is absent")
        zip.getInputStream(sheet).use { stream ->
            val document = documentBuilder().parse(stream)
            val rowNodes = document.getElementsByTagNameNS("*", "row")
            (0 until rowNodes.length).map { rowIndex ->
                val row = rowNodes.item(rowIndex) as Element
                val cells = row.getElementsByTagNameNS("*", "c")
                val values = mutableMapOf<Int, String>()
                var largestColumn = 0
                (0 until cells.length).forEach { cellIndex ->
                    val cell = cells.item(cellIndex) as Element
                    val column = columnIndex(cell.getAttribute("r"))
                    largestColumn = maxOf(largestColumn, column)
                    val raw = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                    values[column] = if (cell.getAttribute("t") == "s") {
                        sharedStrings.getOrElse(raw.toIntOrNull() ?: -1) { "" }
                    } else {
                        raw
                    }
                }
                (0..largestColumn).map { column -> values[column].orEmpty() }
            }
        }
    }

    private fun columnIndex(cellReference: String): Int {
        val letters = cellReference.takeWhile(Char::isLetter)
        return letters.fold(0) { value, letter -> value * 26 + (letter.uppercaseChar() - 'A' + 1) } - 1
    }
}
