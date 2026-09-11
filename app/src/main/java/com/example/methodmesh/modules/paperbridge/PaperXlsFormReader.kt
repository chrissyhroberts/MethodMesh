package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.net.Uri
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

internal data class PaperOdkField(
    val name: String,
    val label: String,
    val type: PaperFieldType,
    val required: Boolean,
    val requiredExpression: String? = null,
    val relevanceExpression: String? = null,
    val constraintExpression: String? = null,
    val constraintMessage: String? = null,
    val options: List<Pair<String, String>> = emptyList(),
    val regex: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null
)

internal data class PaperOdkSchema(
    val formId: String,
    val title: String,
    val version: String,
    val fields: List<PaperOdkField>,
    val warnings: List<String>
)

/**
 * Tiny dependency-free XLSX survey/choices reader used only by Paper Bridge's designer.
 *
 * It reads the Open XML container directly and deliberately extracts only the
 * fixed XLSForm-compatible subset needed for paper mapping: survey
 * type/name/label/required/constraint plus choices list_name/name/label.
 * ODK is not required: any ordinary .xlsx authored with these sheets/columns
 * can be used. It is not intended to be a general XLSX engine.
 */
internal object PaperXlsFormReader {
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun read(context: Context, uri: Uri): PaperOdkSchema {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not open survey workbook.")
        return readBytes(bytes)
    }

    fun readBytes(bytes: ByteArray): PaperOdkSchema {
        val entries = unzip(bytes)
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetPaths = workbookSheetPaths(entries)
        val surveyPath = sheetPaths.entries.firstOrNull { it.key.equals("survey", true) }?.value
            ?: throw IllegalArgumentException("Workbook has no survey sheet.")
        val survey = parseSheet(entries[surveyPath] ?: error("survey worksheet missing"), shared)
        val choices = sheetPaths.entries.firstOrNull { it.key.equals("choices", true) }?.value
            ?.let { path -> entries[path]?.let { parseSheet(it, shared) } }
            .orEmpty()
        val settings = sheetPaths.entries.firstOrNull { it.key.equals("settings", true) }?.value
            ?.let { path -> entries[path]?.let { parseSheet(it, shared) } }
            .orEmpty()

        val choiceMap = choices
            .filter { it["list_name"].orEmpty().isNotBlank() && it["name"].orEmpty().isNotBlank() }
            .groupBy { it["list_name"].orEmpty() }
            .mapValues { (_, rows) -> rows.map { row -> row["name"].orEmpty() to labelValue(row, row["name"].orEmpty()) } }

        val warnings = mutableListOf<String>()
        val fields = mutableListOf<PaperOdkField>()
        val inheritedRelevance = mutableListOf<String>()
        var repeatDepth = 0

        survey.forEach { row ->
            val rawType = row["type"].orEmpty().trim()
            val name = row["name"].orEmpty().trim()
            if (rawType.isBlank()) return@forEach
            val lower = rawType.lowercase()
            val structural = lower.replace(' ', '_')

            if (structural.startsWith("begin_group")) {
                inheritedRelevance += row["relevant"].orEmpty().trim()
                return@forEach
            }
            if (structural.startsWith("end_group")) {
                if (inheritedRelevance.isNotEmpty()) inheritedRelevance.removeAt(inheritedRelevance.lastIndex)
                return@forEach
            }
            if (structural.startsWith("begin_repeat")) {
                inheritedRelevance += row["relevant"].orEmpty().trim()
                repeatDepth++
                return@forEach
            }
            if (structural.startsWith("end_repeat")) {
                if (inheritedRelevance.isNotEmpty()) inheritedRelevance.removeAt(inheritedRelevance.lastIndex)
                repeatDepth = (repeatDepth - 1).coerceAtLeast(0)
                return@forEach
            }

            if (name.isBlank()) return@forEach
            if (name.startsWith("paper_") || name.startsWith("methodmesh_")) return@forEach
            if (structural in setOf("note", "calculate", "hidden", "start", "end")) return@forEach

            val mappedType: PaperFieldType
            val options: List<Pair<String, String>>
            when {
                lower == "text" -> { mappedType = PaperFieldType.OCR_TEXT; options = emptyList() }
                lower == "integer" -> { mappedType = PaperFieldType.OCR_INTEGER; options = emptyList() }
                lower == "decimal" -> { mappedType = PaperFieldType.OCR_DECIMAL; options = emptyList() }
                lower == "barcode" -> { mappedType = PaperFieldType.BARCODE; options = emptyList() }
                lower == "image" -> { mappedType = PaperFieldType.IMAGE; options = emptyList() }
                lower.startsWith("select_one ") -> {
                    mappedType = PaperFieldType.OMR_SINGLE
                    val listName = rawType.substringAfter(' ').trim()
                    options = choiceMap[listName].orEmpty()
                    if (options.isEmpty()) warnings += "$name: choice list '$listName' was not found or is empty."
                }
                lower.startsWith("select_multiple ") -> {
                    mappedType = PaperFieldType.OMR_MULTIPLE
                    val listName = rawType.substringAfter(' ').trim()
                    options = choiceMap[listName].orEmpty()
                    if (options.isEmpty()) warnings += "$name: choice list '$listName' was not found or is empty."
                }
                else -> {
                    warnings += "$name: survey type '$rawType' is not directly supported by Paper Bridge and was skipped."
                    return@forEach
                }
            }

            val constraint = row["constraint"].orEmpty().trim()
            val minimum = Regex("\\.\\s*>=\\s*(-?\\d+(?:\\.\\d+)?)").find(constraint)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?: Regex("(-?\\d+(?:\\.\\d+)?)\\s*<=\\s*\\.").find(constraint)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val maximum = Regex("\\.\\s*<=\\s*(-?\\d+(?:\\.\\d+)?)").find(constraint)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?: Regex("\\.\\s*<\\s*(-?\\d+(?:\\.\\d+)?)").find(constraint)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val regex = Regex("regex\\s*\\(\\s*\\.\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)", RegexOption.IGNORE_CASE)
                .find(constraint)?.groupValues?.getOrNull(1)

            val requiredRaw = row["required"].orEmpty().trim()
            val requiredLower = requiredRaw.lowercase()
            val requiredLiteral = requiredLower in setOf("yes", "true", "1")
            val requiredExpression = requiredRaw.takeIf {
                it.isNotBlank() && requiredLower !in setOf("yes", "true", "1", "no", "false", "0")
            }

            val localRelevance = row["relevant"].orEmpty().trim()
            val relevanceParts = inheritedRelevance.filter(String::isNotBlank) + listOfNotNull(localRelevance.takeIf(String::isNotBlank))
            val effectiveRelevance = relevanceParts.takeIf { it.isNotEmpty() }
                ?.joinToString(" and ") { "($it)" }

            if (repeatDepth > 0) {
                warnings += "$name: field is inside a repeat; Paper Bridge evaluates imported relevance/constraints as a single paper instance only."
            }

            val resolvedLabel = labelValue(row, name)
            if (row["label"].orEmpty().isBlank() && row.keys.none { it.startsWith("label::") && row[it].orEmpty().isNotBlank() }) {
                warnings += "$name: no question label was supplied; colour-template OCR cannot auto-match this field by question text."
            }
            fields += PaperOdkField(
                name = name,
                label = resolvedLabel,
                type = mappedType,
                required = requiredLiteral,
                requiredExpression = requiredExpression,
                relevanceExpression = effectiveRelevance,
                constraintExpression = constraint.takeIf(String::isNotBlank),
                constraintMessage = messageValue(row, "constraint_message"),
                options = options,
                regex = regex,
                minimum = minimum,
                maximum = maximum
            )
        }

        require(fields.isNotEmpty()) { "No Paper Bridge-compatible fields were found in the survey sheet." }
        val settingsRow = settings.firstOrNull().orEmpty()
        return PaperOdkSchema(
            formId = settingsRow["form_id"].orEmpty().ifBlank { "paper_form" },
            title = settingsRow["form_title"].orEmpty().ifBlank { "Paper form" },
            version = settingsRow["version"].orEmpty().ifBlank { "1" },
            fields = fields,
            warnings = warnings.distinct()
        )
    }

    private fun labelValue(row: Map<String, String>, fallback: String): String {
        row["label"]?.takeIf { it.isNotBlank() }?.let { return it }
        return row.entries.firstOrNull { (key, value) -> key.startsWith("label::") && value.isNotBlank() }?.value ?: fallback
    }

    private fun messageValue(row: Map<String, String>, base: String): String? {
        row[base]?.takeIf { it.isNotBlank() }?.let { return it }
        return row.entries.firstOrNull { (key, value) -> key.startsWith("$base::") && value.isNotBlank() }?.value
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size <= 25 * 1024 * 1024) { "Survey workbook is too large for the Paper Bridge schema importer." }
        val entries = linkedMapOf<String, ByteArray>()
        var totalExpanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= 256) { "XLSX contains an implausible number of ZIP entries." }
                if (!entry.isDirectory) {
                    val buffer = ByteArray(8192)
                    val output = ByteArrayOutputStream()
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalExpanded += read
                        require(entryBytes <= 12L * 1024 * 1024) { "XLSX entry '${entry.name}' is unexpectedly large." }
                        require(totalExpanded <= 40L * 1024 * 1024) { "Expanded XLSX is too large." }
                        output.write(buffer, 0, read)
                    }
                    entries[entry.name] = output.toByteArray()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun workbookSheetPaths(entries: Map<String, ByteArray>): Map<String, String> {
        val workbook = parseXml(entries["xl/workbook.xml"] ?: throw IllegalArgumentException("Invalid XLSX: workbook.xml missing."))
        val rels = parseXml(entries["xl/_rels/workbook.xml.rels"] ?: throw IllegalArgumentException("Invalid XLSX: workbook relationships missing."))
        val targets = mutableMapOf<String, String>()
        val relNodes = rels.getElementsByTagNameNS("*", "Relationship")
        for (i in 0 until relNodes.length) {
            val e = relNodes.item(i) as? Element ?: continue
            val id = e.getAttribute("Id")
            val target = e.getAttribute("Target")
            if (id.isNotBlank() && target.isNotBlank()) {
                targets[id] = if (target.startsWith("/")) target.removePrefix("/") else "xl/${target.removePrefix("../")}".replace("xl/xl/", "xl/")
            }
        }
        val output = linkedMapOf<String, String>()
        val sheets = workbook.getElementsByTagNameNS("*", "sheet")
        for (i in 0 until sheets.length) {
            val e = sheets.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            val relId = e.getAttributeNS(REL_NS, "id").ifBlank { e.getAttribute("r:id") }
            targets[relId]?.let { output[name] = it }
        }
        return output
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = parseXml(bytes)
        val nodes = doc.getElementsByTagNameNS("*", "si")
        return (0 until nodes.length).map { nodes.item(it).textContent.orEmpty() }
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Map<String, String>> {
        val doc = parseXml(bytes)
        val rows = doc.getElementsByTagNameNS("*", "row")
        val matrices = mutableListOf<Map<Int, String>>()
        for (r in 0 until rows.length) {
            val row = rows.item(r) as? Element ?: continue
            val cells = row.getElementsByTagNameNS("*", "c")
            val values = linkedMapOf<Int, String>()
            for (c in 0 until cells.length) {
                val cell = cells.item(c) as? Element ?: continue
                val ref = cell.getAttribute("r")
                val col = columnIndex(ref)
                val type = cell.getAttribute("t")
                val value = when (type) {
                    "s" -> cell.childText("v").toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> cell.getElementsByTagNameNS("*", "is").item(0)?.textContent.orEmpty()
                    else -> cell.childText("v")
                }
                values[col] = value
            }
            matrices += values
        }
        val header = matrices.firstOrNull().orEmpty()
        if (header.isEmpty()) return emptyList()
        val headerByIndex = header.mapValues { it.value.trim().lowercase() }
        return matrices.drop(1).map { row ->
            headerByIndex.mapNotNull { (index, name) ->
                if (name.isBlank()) null else name to row[index].orEmpty().trim()
            }.toMap()
        }.filter { row -> row.values.any(String::isNotBlank) }
    }

    private fun Element.childText(localName: String): String {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0)?.textContent.orEmpty() else ""
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile(Char::isLetter)
        if (letters.isBlank()) return 0
        var value = 0
        letters.uppercase().forEach { ch -> value = value * 26 + (ch - 'A' + 1) }
        return (value - 1).coerceAtLeast(0)
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}
